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

## Maven Dependency

```xml
<dependency>
    <groupId>org.javadelight</groupId>
	<artifactId>delight-scheduler</artifactId>
	<version>[latest version]</version>
</dependency>
```

This artifact is available on [Maven Central](https://search.maven.org/#search%7Cga%7C1%7Cdelight-scheduler) and 
[BinTray](https://bintray.com/javadelight/javadelight/delight-scheduler).

[![Maven Central](https://img.shields.io/maven-central/v/org.javadelight/delight-scheduler.svg)](https://search.maven.org/#search%7Cga%7C1%7Cdelight-scheduler)
