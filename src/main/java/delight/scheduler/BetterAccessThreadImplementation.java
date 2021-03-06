package delight.scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import delight.async.callbacks.SimpleCallback;
import delight.concurrency.Concurrency;
import delight.concurrency.schedule.SingleInstanceThread;
import delight.concurrency.schedule.Step;
import delight.concurrency.schedule.ThreadSpace;
import delight.concurrency.wrappers.SimpleAtomicBoolean;
import delight.concurrency.wrappers.SimpleExecutor;
import delight.concurrency.wrappers.SimpleLock;
import delight.concurrency.wrappers.WhenExecutorShutDown;
import delight.simplelog.Log;

public class BetterAccessThreadImplementation implements AccessThread {

    final Concurrency concurrency;
    final SimpleExecutor executor;
    private final SimpleLock lock;

    protected final Queue<Step> queue;

    private final List<SimpleCallback> finalizedListener;

    // Object workerThread;
    final SimpleAtomicBoolean running;

    final SimpleAtomicBoolean shutdownRequested;
    final SimpleAtomicBoolean isShutDown;

    private volatile SimpleCallback shutDowncallback;

    @Override
    public void offer(final Step item) {

        if (isShutDown.get()) {
            Log.warn(this, "WARNING Trying to submit task for shutdown worker [" + item + "]");
            // new Exception("here").printStackTrace();
            item.process();
            return;
            // throw new IllegalStateException(
            // "Cannot submit tasks for a shutdown worker: [" + item + "]");
        }

        if (!queue.offer(item)) {
            throw new IllegalStateException("Queue did not accept new item.");
        }

    }

    @Override
    public void startIfRequired() {

        if (!running.compareAndSet(false, true)) {
            return;
        }

        assert running.get();

        runProtected();

    }

    private void runProtected() {
        if (queue.size() == 0) {
            running.set(false);
            callAllOperationsDoneListener();
            if (shutdownRequested.get()) {
                finalizeShutdown();
                return;
            }
            return;
        }

        executor.execute(new Runnable() {

            @Override
            public void run() {
                BetterAccessThreadImplementation.this.run(new AccessThreadNotifiyer() {

                    @Override
                    public void notifiyFinished() {

                        runProtected();

                    }
                });
            }

        });

    }

    public interface AccessThreadNotifiyer {
        /**
         * This method must be called when all pending operations for this
         * thread are completed.
         */
        public void notifiyFinished();
    }

    protected void run(final AccessThreadNotifiyer callWhenFinished) {

        final List<Step> items = new ArrayList<Step>(queue.size());
        while (queue.size() > 0) {

            Step next;
            while ((next = queue.poll()) != null) {
                items.add(next);
            }

        }

        try {
            acquireMutex(); // TODO must this really be here?
            processItems(items);
        } finally {
            releaseMutex();

            callWhenFinished.notifiyFinished();
        }

    }

    private final void processItems(final List<Step> items) {

        for (final Step item : items) {

            item.process();

        }

    }

    @Override
    public void addAllOperationsDoneListener(final SimpleCallback whenProcessed) {
        if (!this.isRunning() && this.queue.size() == 0) {
            whenProcessed.onSuccess();
            callAllOperationsDoneListener();
            return;
        }

        this.finalizedListener.add(new SimpleCallback() {

            @Override
            public void onSuccess() {
                whenProcessed.onSuccess();
            }

            @Override
            public void onFailure(final Throwable t) {
                whenProcessed.onFailure(t);
            }
        });

        this.startIfRequired();
    }

    @Override
    public void requestShutdown(final SimpleCallback callback) {
        if (shutDowncallback != null) {
            throw new RuntimeException("Shutdown should only be requested once.");
        }
        shutDowncallback = callback;
        this.shutdownRequested.set(true);
        this.startIfRequired();
    }

    private void finalizeShutdown() {
        this.shutdownRequested.set(false);
        isShutDown.set(true);
        this.executor.shutdown(new WhenExecutorShutDown() {

            @Override
            public void onSuccess() {
                shutDowncallback.onSuccess();
                shutDowncallback = null;
            }

            @Override
            public void onFailure(final Throwable t) {
                if (shutDowncallback == null) {
                    throw new RuntimeException("Wanted to report failure but callback was already called.", t);
                }
                shutDowncallback.onFailure(t);
            }
        });

    }

    private void callAllOperationsDoneListener() {
        synchronized (finalizedListener) {
            if (finalizedListener.size() > 0) {
                final ArrayList<SimpleCallback> toProcesses = new ArrayList<SimpleCallback>(finalizedListener);
                this.finalizedListener.clear();
                for (final SimpleCallback p : toProcesses) {
                    p.onSuccess();
                }

            }
        }
        if (finalizedListener.size() > 0) {
            callAllOperationsDoneListener();
            return;
        }

        if (queue.size() > 0) {
            this.startIfRequired();
        }

    }

    @Override
    public void shutdown(final SimpleCallback callback) {
        this.requestShutdown(new SimpleCallback() {

            @Override
            public void onSuccess() {

                callback.onSuccess();
            }

            @Override
            public void onFailure(final Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    @Override
    public SimpleExecutor getExecutor() {
        return executor;
    }

    @Override
    public Concurrency getConcurrency() {

        return this.concurrency;
    }

    @Override
    public boolean hasMutex() {

        return this.lock.isHeldByCurrentThread();
    }

    @Override
    public void acquireMutex() {
        this.lock.lock();
    }

    @Override
    public void releaseMutex() {
        this.lock.unlock();
    }

    @Override
    public boolean isRunning() {

        return this.running.get();
    }

    @Override
    public SingleInstanceThread asSingleInstanceThread() {

        return new SingleInstanceThread() {

            @Override
            public void startIfRequired() {
                BetterAccessThreadImplementation.this.startIfRequired();
            }

            @Override
            public void stop(final ThreadStoppedCallback callback) {
                BetterAccessThreadImplementation.this.shutdown(new SimpleCallback() {

                    @Override
                    public void onSuccess() {
                        callback.onSuccess();
                    }

                    @Override
                    public void onFailure(final Throwable t) {
                        callback.onFailure(t);
                    }
                });
            }

            @Override
            public Boolean getIsRunning() {
                return BetterAccessThreadImplementation.this.isRunning();
            }

            @Override
            public void setMaxCallTime(final long maxCallTimeInMs) {

                super.setMaxCallTime(maxCallTimeInMs);
            }

            @Override
            public SimpleExecutor getExecutor() {
                return BetterAccessThreadImplementation.this.getExecutor();
            }

            @Override
            public void run(final Notifiyer callWhenFinished) {
                BetterAccessThreadImplementation.this.run(new AccessThreadNotifiyer() {
                    @Override
                    public void notifiyFinished() {
                        callWhenFinished.notifiyFinished();
                    }
                });
            }

        };
    }

    @Override
    public SingleInstanceQueueWorker<Step> asQueueWorker() {

        return new SingleInstanceQueueWorker<Step>() {

            @Override
            public void offer(final Step item) {
                BetterAccessThreadImplementation.this.offer(item);
            }

            @Override
            public boolean isRunning() {
                return BetterAccessThreadImplementation.this.isRunning();
            }

            @Override
            protected void processItems(final List<Step> item) {
                BetterAccessThreadImplementation.this.processItems(item);
            }
        };
    }

    @Override
    public ThreadSpace asThreadSpace() {

        return new ThreadSpace() {

            @Override
            public synchronized void processSteps() {
                BetterAccessThreadImplementation.this.startIfRequired();
            }

            @Override
            public synchronized void add(final Step s) {
                BetterAccessThreadImplementation.this.offer(s);
            }

        };
    }

    public BetterAccessThreadImplementation(final Concurrency concurrency) {
        super();
        this.concurrency = concurrency;

        this.executor = concurrency.newExecutor().newSingleThreadExecutor(this);

        this.running = concurrency.newAtomicBoolean(false);
        this.isShutDown = concurrency.newAtomicBoolean(false);
        this.shutdownRequested = concurrency.newAtomicBoolean(false);

        this.queue = concurrency.newCollection().newThreadSafeQueue(Step.class);

        this.lock = concurrency.newLock();

        this.finalizedListener = concurrency.newCollection().newThreadSafeList(SimpleCallback.class);

        // this.maxCalltime = -1;

    }

}
