package delight.scheduler.tests.xtend

import delight.async.AsyncCommon
import delight.async.callbacks.ValueCallback
import delight.async.jre.Async
import delight.concurrency.jre.ConcurrencyJre
import delight.functional.Success
import delight.scheduler.SequentialOperationScheduler
import org.junit.Test

class TestTimeoutAndResume {
	
	@Test
	def void test() {
		val scheduler = new SequentialOperationScheduler(this, ConcurrencyJre.create());
		scheduler.setTimeout(10);
		scheduler.enforceOwnThread = true

		Async.waitFor [ cb |

			scheduler.schedule([ innercb |

				Thread.sleep(100)
	
				innercb.onSuccess(Success.INSTANCE)

			], new ValueCallback<Success>() {
				
				override onSuccess(Success value) {
					cb.onFailure(new Exception("Operation should not succeed."));
				}
				
				override onFailure(Throwable t) {
					//cb.onSuccess(Success.INSTANCE)
				}
					
			})

			Thread.sleep(50)

			scheduler.schedule([ innercb |

				innercb.onSuccess(Success.INSTANCE)
			], AsyncCommon.embed(cb, [

				cb.onSuccess(Success.INSTANCE)

			]))

		]

		Async.waitFor [ cb |
			scheduler.shutdown(cb)
		]

	}
}
