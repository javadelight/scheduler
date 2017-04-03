package delight.scheduler;

import delight.async.Operation;
import delight.async.callbacks.ValueCallback;

public class OperationEntry<R> {

    public final Operation<R> operation;
    public final ValueCallback<R> callback;
    public final long startTime;

    public OperationEntry(final Operation<R> operation, final long startTime, final ValueCallback<R> callback) {
        super();
        this.operation = operation;
        this.callback = callback;
        this.startTime = startTime;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((callback == null) ? 0 : callback.hashCode());
        result = prime * result + ((operation == null) ? 0 : operation.hashCode());
        result = prime * result + (int) (startTime ^ (startTime >>> 32));
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final OperationEntry other = (OperationEntry) obj;
        if (callback == null) {
            if (other.callback != null) {
                return false;
            }
        } else if (callback != other.callback) {
            return false;
        }
        if (operation == null) {
            if (other.operation != null) {
                return false;
            }
        } else if (operation != other.operation) {
            return false;
        }
        if (startTime != other.startTime) {
            return false;
        }
        return true;
    }

}
