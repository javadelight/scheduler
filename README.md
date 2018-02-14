[![Build Status](https://travis-ci.org/javadelight/scheduler.svg?branch=master)](https://travis-ci.org/javadelight/scheduler)

# Scheduler

Tools for scheduling asynchronous operations in sequence or in parallel.

## Usage

Creating a scheduler and performing an operation on it:

```java
scheduler = new SequentialOperationScheduler(this, ConcurrencyJre.create());
scheduler.setTimeout(10);

scheduler.schedule(new Operation<Success>() {
	public void apply(ValueCallback<Success> callback) {
		// perform operations required ...
		callback.onSuccess(Success.INSTANCE);
	}
}, new ValueCallback<Success>() {
	
	public void onSuccess(Success success) {
		// operation completed successfully
	}
	
	public void onFailure(Throwable t) {
		// error during execution
	}
	
});

// Don't forget to shut down scheduler to free resources
scheduler.shutdown(new ValueCallback<Success>() {
	
	public void onSuccess(Success success) {
		// scheduler shut down successfully
	}
	
	public void onFailure(Throwable t) {
		// error during shutdown
	}
	
});

```
