package delight.scheduler.tests.xtend;

import delight.async.AsyncCommon;
import delight.async.Operation;
import delight.async.callbacks.ValueCallback;
import delight.async.jre.Async;
import delight.concurrency.Concurrency;
import delight.concurrency.jre.ConcurrencyJre;
import delight.functional.Closure;
import delight.functional.Success;
import delight.scheduler.SequentialOperationScheduler;
import org.eclipse.xtext.xbase.lib.Exceptions;
import org.junit.Test;

@SuppressWarnings("all")
public class TestTimeoutAndResume {
  @Test
  public void test() {
    Concurrency _create = ConcurrencyJre.create();
    final SequentialOperationScheduler scheduler = new SequentialOperationScheduler(this, _create);
    scheduler.setTimeout(10);
    scheduler.setEnforceOwnThread(true);
    final Operation<Object> _function = new Operation<Object>() {
      @Override
      public void apply(final ValueCallback<Object> cb) {
        try {
          final Operation<Success> _function = new Operation<Success>() {
            @Override
            public void apply(final ValueCallback<Success> innercb) {
              try {
                Thread.sleep(100);
                innercb.onSuccess(Success.INSTANCE);
              } catch (Throwable _e) {
                throw Exceptions.sneakyThrow(_e);
              }
            }
          };
          scheduler.<Success>schedule(_function, new ValueCallback<Success>() {
            @Override
            public void onSuccess(final Success value) {
              Exception _exception = new Exception("Operation should not succeed.");
              cb.onFailure(_exception);
            }
            
            @Override
            public void onFailure(final Throwable t) {
            }
          });
          Thread.sleep(50);
          final Operation<Success> _function_1 = new Operation<Success>() {
            @Override
            public void apply(final ValueCallback<Success> innercb) {
              innercb.onSuccess(Success.INSTANCE);
            }
          };
          final Closure<Success> _function_2 = new Closure<Success>() {
            @Override
            public void apply(final Success it) {
              cb.onSuccess(Success.INSTANCE);
            }
          };
          scheduler.<Success>schedule(_function_1, AsyncCommon.<Success>embed(cb, _function_2));
        } catch (Throwable _e) {
          throw Exceptions.sneakyThrow(_e);
        }
      }
    };
    Async.<Object>waitFor(_function);
    final Operation<Success> _function_1 = new Operation<Success>() {
      @Override
      public void apply(final ValueCallback<Success> cb) {
        scheduler.shutdown(cb);
      }
    };
    Async.<Success>waitFor(_function_1);
  }
}
