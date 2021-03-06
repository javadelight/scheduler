package delight.scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import delight.async.AsyncCommon;
import delight.async.Operation;
import delight.async.Value;
import delight.async.callbacks.ValueCallback;
import delight.concurrency.Concurrency;
import delight.concurrency.wrappers.SimpleAtomicBoolean;
import delight.concurrency.wrappers.SimpleAtomicInteger;
import delight.concurrency.wrappers.SimpleAtomicLong;
import delight.concurrency.wrappers.SimpleExecutor;
import delight.functional.Closure;
import delight.functional.Success;
import delight.simplelog.Field;
import delight.simplelog.Log;

public final class SequentialOperationScheduler {

	private static final boolean ENABLE_TRACE = false;
	private static final boolean ENABLE_METRICS = false;

	@SuppressWarnings("rawtypes")
	private final Queue<OperationEntry> scheduled;
	private final SimpleExecutor operationExecutor;
	// private final SimpleExecutor executorForTimeouts;

	private final Concurrency concurrency;

	private final SimpleAtomicBoolean shuttingDown;
	private final SimpleAtomicBoolean shutDown;
	private final SimpleAtomicInteger suspendCount;
	private final SimpleAtomicBoolean operationInProgress;
	private final SimpleAtomicLong lastRun;
	private final Value<OperationEntry<Object>> currentOperation;

	private boolean enforceOwnThread;

	private final Value<ValueCallback<Success>> shutdownCallback;

	private int timeout;

	private final SimpleExecutor callbackExecutor;

	private final Object owner;

	private volatile long totalRuntime;

	private boolean enableLog;

	public boolean isRunning() {

		return operationInProgress.get();

	}

	/**
	 * If its NOT running and CAN be suspended, suspend and return true.
	 * 
	 * @return
	 */
	public boolean suspendIfPossible() {

		if (!operationInProgress.get()) {
			suspend();
			return true;
		}

		return false;

	}

	public void suspend() {
		suspendCount.incrementAndGet();
	}

	public void resume() {
		suspendCount.decrementAndGet();
		runIfRequired(enforceOwnThread);
	}

	@SuppressWarnings("unchecked")
	public <R> void schedule(final Operation<R> operation, final ValueCallback<R> callback) {

		if (shuttingDown.get()) {
			throw new IllegalStateException("Trying to schedule operation for shutting down scheduler.");
		}

		if (ENABLE_TRACE || enableLog) {
			Log.println(this, "Add operation " + operation);
		}
		scheduled.add(new OperationEntry<Object>((Operation<Object>) operation, 0, new ValueCallback<Object>() {

			@Override
			public void onFailure(final Throwable t) {
				callback.onFailure(t);
			}

			@Override
			public void onSuccess(final Object value) {
				callback.onSuccess((R) value);
			}
		}));
		
//		int pending = scheduled.size();
//		if (pending > 400) {
//			Log.info(this, "Many operations pending for "+this.owner+": "+pending, Field.define("pendingOperations", ""+pending));
//		}
		
		runIfRequired(enforceOwnThread);

	}

	private final void runIfRequired(final boolean forceOwnThread) {

		if (suspendCount.get() > 0) {
			if (ENABLE_TRACE || enableLog) {
				Log.println(this, "Is suspended ...");
			}
			return;
		}

		if (ENABLE_TRACE || enableLog) {
			Log.println(this, "Test run required. Is in progress: " + operationInProgress.get());
		}

		if (!operationInProgress.compareAndSet(false, true)) {
			final long lastRunVal = lastRun.get();
			if (ENABLE_TRACE || enableLog) {
				Log.println(this, "Last Run " + lastRunVal);
			}
			if (lastRunVal != -1) {
				final long now = System.currentTimeMillis();

				if (ENABLE_TRACE || enableLog) {
					Log.println(this, "Duration of last pending operation " + (now - lastRunVal));
				}

				if (now - lastRunVal > timeout) {
					operationInProgress.set(false);
					lastRun.set(-1);

					Log.warn(this, "Scheduler timed out [owner: " + this.owner + ", currentOperation: "
							+ this.currentOperation.get().operation + "]");

					currentOperation.get().callback.onFailure(new Exception("Operation timed out."));
					currentOperation.set(null);

					performRun();
				}
			}

			return;
		}
		performRun();

	}

	@SuppressWarnings("unchecked")
	private void performRun() {
		if (ENABLE_TRACE || enableLog) {
			Log.println(this, "Perform run. Is in progress: " + operationInProgress.get());
		}

		OperationEntry<Object> entry = null;

		entry = scheduled.poll();

		if (entry == null) {
			operationInProgress.set(false);
			tryShutdown();
			return;
		}

		lastRun.set(System.currentTimeMillis());

		if (!enforceOwnThread) {

			executeWithTimeout(entry);
		} else {
			final OperationEntry<Object> entryClosed = entry;
			operationExecutor.execute(new Runnable() {

				@Override
				public void run() {
					executeWithTimeout(entryClosed);
				}

			});
		}
	}

	private final void executeWithTimeout(final OperationEntry<Object> entry) {
		final SimpleAtomicBoolean operationCompleted = concurrency.newAtomicBoolean(false);

		executeOperation(entry, operationCompleted);
		if (operationCompleted.get()) {
			return;
		}

	}

	private void executeOperation(final OperationEntry<Object> entryClosed,
			final SimpleAtomicBoolean operationCompleted) {

		if (ENABLE_TRACE || enableLog) {
			Log.println(this, "Execute operation " + entryClosed.operation);
		}

		long startTmp = 0;

		if (ENABLE_METRICS) {
			startTmp = System.currentTimeMillis();
		}

		final long start = startTmp;
		try {
			currentOperation.set(entryClosed);
			entryClosed.operation.apply(new ValueCallback<Object>() {

				@Override
				public void onFailure(final Throwable t) {
					if (ENABLE_TRACE || enableLog) {
						Log.println(this, "Operation failed: " + entryClosed.operation);
					}

					operationInProgress.set(false);
					// lastRun.set(-1);

					runIfRequired(true);

					callbackExecutor.execute(new Runnable() {

						@Override
						public void run() {
							if (operationCompleted.get()) {
								// System.out.println(this + " FAIL EX");
								Log.warn(this, "Operation [" + entryClosed.operation
										+ "] failed. Callback cannot be triggered, it was already triggered. Error reported: ["+t.getMessage()+"]", t);
								
							}
							operationCompleted.set(true);

							entryClosed.callback.onFailure(t);
						}
					});

				}

				@Override
				public void onSuccess(final Object value) {
					if (ENABLE_TRACE || enableLog) {
						Log.println(
								SequentialOperationScheduler.this, "Operation successful: " + entryClosed.operation);
					}

					if (ENABLE_METRICS) {
						totalRuntime += System.currentTimeMillis() - start;
						// MetricsCommon.get()
						// .record(MetricsCommon.value("scheduler." + owner,
						// System.currentTimeMillis() - start));
					}

					if (currentOperation.get() != entryClosed) {

						Log.println(SequentialOperationScheduler.this,
								"Don't call callback since operation was cancelled and onFailure already called: "
										+ entryClosed.operation);

						return;
					}

					operationInProgress.set(false);

					callbackExecutor.execute(new Runnable() {

						@Override
						public void run() {
							if (operationCompleted.get()) {
								throw new RuntimeException("Operation [" + entryClosed.operation
										+ "] successful. Callback cannot be triggered, it was already triggered.");
							}
							operationCompleted.set(true);

							entryClosed.callback.onSuccess(value);
						}
					});

					runIfRequired(true);

				}
			});
		} catch (final Throwable t) {

			operationInProgress.set(false);

			runIfRequired(true);

			callbackExecutor.execute(new Runnable() {

				@Override
				public void run() {
					operationCompleted.set(true);
					entryClosed.callback.onFailure(t);
				}
			});

		}
	}

	public void shutdown(final ValueCallback<Success> cb) {

		if (shuttingDown.get()) {
			throw new IllegalStateException("Called shutdown for already shut down scheduler");
		}

		shuttingDown.set(true);

		shutdownCallback.set(cb);

		tryShutdown();
	}

	private final void tryShutdown() {

		if (ENABLE_TRACE || enableLog) {
			Log.println(this, "->" + owner + ": Attempting shutdown .. ");
		}

		if (!shuttingDown.get()) {
			return;
		}

		if (ENABLE_TRACE || enableLog) {
			Log.println(
					this, "->" + owner + ": Attempting shutdown; running state: " + operationInProgress.get());
		}
		if (operationInProgress.get() == false) {

			if (ENABLE_TRACE || enableLog) {
				Log.println(this, "->" + owner + ": Attempting shutdown; still scheduled: " + scheduled.size());
			}
			if (scheduled.isEmpty()) {
				performShutdown();
				return;
			}

		}

	}

	private final void performShutdown() {

		final List<Operation<Success>> ops = new ArrayList<Operation<Success>>(4);

		ops.add(new Operation<Success>() {

			@Override
			public void apply(final ValueCallback<Success> callback) {
				operationExecutor.shutdown(AsyncCommon.asSimpleCallback(callback));
			}
		});

		ops.add(new Operation<Success>() {

			@Override
			public void apply(final ValueCallback<Success> callback) {
				callbackExecutor.shutdown(AsyncCommon.asSimpleCallback(callback));
			}

		});

		ops.add(new Operation<Success>() {

			@Override
			public void apply(final ValueCallback<Success> callback) {

				callback.onSuccess(Success.INSTANCE);

			}

		});

		AsyncCommon.sequential(ops, AsyncCommon.embed(shutdownCallback.get(), new Closure<List<Success>>() {

			@Override
			public void apply(final List<Success> o) {
				if (ENABLE_METRICS) {
					Log.println(this, "Runtime for " + owner + " = " + totalRuntime);
				}

				if (shutDown.compareAndSet(false, true)) {

					shutdownCallback.get().onSuccess(Success.INSTANCE);
				}

			}
		}));

	}

	public void setTimeout(final int timeoutInMs) {
		this.timeout = timeoutInMs;
	}

	public void setEnforceOwnThread(final boolean value) {
		this.enforceOwnThread = value;
	}

	public void setEnableLog(final boolean enableLog) {
		this.enableLog = enableLog;
	}

	public int scheduledCount() {
		return scheduled.size();
	}

	public Concurrency getConcurrency() {
		return this.concurrency;
	}

	public SequentialOperationScheduler(final Object owner, final Concurrency concurrency) {
		super();
		assert concurrency != null;
		this.owner = owner;
		this.concurrency = concurrency;
		this.scheduled = concurrency.newCollection().newThreadSafeQueue(OperationEntry.class);

		this.shuttingDown = concurrency.newAtomicBoolean(false);
		this.shutdownCallback = new Value<ValueCallback<Success>>(null);

		this.operationExecutor = concurrency.newExecutor().newSingleThreadExecutor(owner);
		this.callbackExecutor = concurrency.newExecutor().newParallelExecutor(0, 5, owner);

		this.suspendCount = concurrency.newAtomicInteger(0);
		this.operationInProgress = concurrency.newAtomicBoolean(false);
		this.shutDown = concurrency.newAtomicBoolean(false);
		this.timeout = 3000;
		this.lastRun = concurrency.newAtomicLong(-1);
		this.currentOperation = new Value<OperationEntry<Object>>(null);

		this.enforceOwnThread = false;

	}

}
