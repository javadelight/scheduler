package delight.scheduler.tests;

import delight.async.AsyncCommon;
import delight.async.Operation;
import delight.async.callbacks.ValueCallback;
import delight.async.jre.Async;
import delight.concurrency.jre.ConcurrencyJre;
import delight.functional.Closure;
import delight.functional.Success;
import delight.scheduler.SequentialOperationScheduler;

import org.junit.Test;

public class TestTimeout {

    @Test
    public void test() throws Exception {
        final SequentialOperationScheduler scheduler = new SequentialOperationScheduler(this, ConcurrencyJre.create());
        scheduler.setTimeout(10);

        Async.waitFor(new Operation<Success>() {

            @Override
            public void apply(final ValueCallback<Success> callback) {

                scheduler.schedule(new Operation<Success>() {

                    @Override
                    public void apply(final ValueCallback<Success> callback) {
                        try {
                            Thread.sleep(500);
                        } catch (final InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        callback.onSuccess(Success.INSTANCE);
                    }

                }, AsyncCommon.embed(callback, new Closure<Success>() {

                    @Override
                    public void apply(final Success o) {

                    }
                }));

                try {
                    Thread.sleep(300);
                } catch (final InterruptedException e) {
                    throw new RuntimeException(e);
                }

                scheduler.schedule(new Operation<Success>() {

                    @Override
                    public void apply(final ValueCallback<Success> callback) {
                        callback.onSuccess(Success.INSTANCE);
                    }
                }, callback);

            }

        });

        Async.waitFor(new Operation<Success>() {

            @Override
            public void apply(final ValueCallback<Success> callback) {
                scheduler.shutdown(callback);
            }
        });

    }

}
