package delight.scheduler;

import delight.async.Operation;
import delight.async.callbacks.SimpleCallback;
import delight.async.callbacks.ValueCallback;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public final class ParallelRequestTimeEnforcer {

    private final List<OperationEntry<Object>> currentOperations;
    private final long timeout;

    private final void checkForTimedOutOperations() {
        // currentOperations should already be synchronized

        final long now = System.currentTimeMillis();

        final List<OperationEntry<Object>> toDelete = new ArrayList<OperationEntry<Object>>(0);

        for (final OperationEntry<Object> currentOperation : currentOperations) {
            final long duration = now - currentOperation.startTime;
            System.out.println(duration);
            if (duration > timeout) {
                toDelete.add(currentOperation);
                currentOperation.callback
                        .onFailure(new Exception("Operation <" + currentOperation.operation + "> has timed out."));
            }
        }

        for (final OperationEntry<Object> deleteOperation : toDelete) {
            currentOperations.remove(deleteOperation);
        }

    }

    public <R> void perform(final Operation<R> operation, final ValueCallback<R> callback) {
        @SuppressWarnings("unchecked")
        final OperationEntry<Object> operationEntry = (OperationEntry<Object>) new OperationEntry<R>(operation,
                System.currentTimeMillis(), callback);

        synchronized (currentOperations) {
            checkForTimedOutOperations();
            currentOperations.add(operationEntry);
        }

        operation.apply(new ValueCallback<R>() {

            @Override
            public void onFailure(final Throwable t) {

                boolean operationExists;
                synchronized (currentOperations) {
                    operationExists = currentOperations.remove(operationEntry);
                }

                if (operationExists) {
                    callback.onFailure(t);
                    return;
                }

                System.err.println(this + ": Operation already terminated. Cannot report failure: " + t.getMessage());
            }

            @Override
            public void onSuccess(final R value) {
                boolean operationExists;
                synchronized (currentOperations) {
                    operationExists = currentOperations.remove(operationEntry);
                }

                if (operationExists) {
                    callback.onSuccess(value);
                    return;
                }

                System.err.println(
                        this + ": Operation already terminated. Cannot report success for " + operationEntry.callback);

            }
        });

    }

    public void shutdown(final SimpleCallback callback) {
        callback.onSuccess();
    }

    public ParallelRequestTimeEnforcer(final long timeout) {
        super();
        this.timeout = timeout;
        this.currentOperations = new LinkedList<OperationEntry<Object>>();
    }

}
