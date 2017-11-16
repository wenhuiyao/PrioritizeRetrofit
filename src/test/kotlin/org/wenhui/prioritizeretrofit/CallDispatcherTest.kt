package org.wenhui.prioritizeretrofit

import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class CallDispatcherTest {

    private lateinit var dispatcher: CallDispatcher

    @Before fun setup() {
        dispatcher = CallDispatcher(1)
    }

    @Test fun dispatch() {
        val isRan = AtomicBoolean(false)
        val countDownLatch = CountDownLatch(1)
        val runnable = PrioritizedRunnableImpl {
            isRan.set(true)
            countDownLatch.countDown()
        }
        dispatcher.dispatch(runnable)
        countDownLatch.await()
        assertThat(isRan.get()).isTrue()
    }

    @Test fun isIdle() {
        val lock = CountDownLatch(1)
        val runnable1 = PrioritizedRunnableImpl {
            lock.await(200, TimeUnit.MILLISECONDS)
        }
        val runnable2 = PrioritizedRunnableImpl {}
        dispatcher.dispatch(runnable1)
        dispatcher.dispatch(runnable2)

        try {
            assertThat(dispatcher.isIdle()).isFalse()
        } finally {
            lock.countDown()
        }
    }

    @Test fun remove() {
        val isRan = AtomicBoolean(false)
        val lock = CountDownLatch(1)
        val runnable1 = PrioritizedRunnableImpl {
            lock.await(200, TimeUnit.MILLISECONDS)
        }
        val runnable2 = PrioritizedRunnableImpl {
            isRan.set(true)
        }
        dispatcher.dispatch(runnable1)
        dispatcher.dispatch(runnable2)

        try {
            assertThat(dispatcher.remove(runnable2)).isTrue()
        } finally {
            lock.countDown()
        }
        // runnable2 is removed, so it should never be executed
        assertThat(isRan.get()).isFalse()
    }

    @Test fun executeOnPriority() {
        val lock = CountDownLatch(1)
        val block = PrioritizedRunnableImpl {
            lock.await()
        }

        val countDownLatch = CountDownLatch(5)
        val executions = CopyOnWriteArrayList<Int>()
        val runnable0 = PrioritizedRunnableImpl(PRIORITY_LOWEST) {
            executions.add(PRIORITY_LOWEST)
            countDownLatch.countDown()
        }
        val runnable1 = PrioritizedRunnableImpl(PRIORITY_LOW) {
            executions.add(PRIORITY_LOW)
            countDownLatch.countDown()
        }
        val runnable2 = PrioritizedRunnableImpl(PRIORITY_NORMAL) {
            executions.add(PRIORITY_NORMAL)
            countDownLatch.countDown()
        }
        val runnable3 = PrioritizedRunnableImpl(PRIORITY_HIGH) {
            executions.add(PRIORITY_HIGH)
            countDownLatch.countDown()
        }
        val runnable4 = PrioritizedRunnableImpl(PRIORITY_HIGHEST) {
            executions.add(PRIORITY_HIGHEST)
            countDownLatch.countDown()
        }

        dispatcher.dispatch(block)
        dispatcher.dispatch(runnable0)
        dispatcher.dispatch(runnable1)
        dispatcher.dispatch(runnable2)
        dispatcher.dispatch(runnable3)
        dispatcher.dispatch(runnable4)

        lock.countDown()
        countDownLatch.await(400, TimeUnit.MILLISECONDS)

        assertThat(executions.size).isEqualTo(5)
        assertThat(executions[0]).isEqualTo(PRIORITY_HIGHEST)
        assertThat(executions[1]).isEqualTo(PRIORITY_HIGH)
        assertThat(executions[2]).isEqualTo(PRIORITY_NORMAL)
        assertThat(executions[3]).isEqualTo(PRIORITY_LOW)
        assertThat(executions[4]).isEqualTo(PRIORITY_LOWEST)
    }

    private open class PrioritizedRunnableImpl(override val priority: Int = PRIORITY_NORMAL,
                                               private val block: () -> Unit) : PrioritizedRunnable {
        override fun run() {
            block()
        }
    }
}