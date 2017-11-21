package com.wenhui.prioritizeretrofit

import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import com.wenhui.prioritizeretrofit.helpers.PrioritizedRunnableAdapter
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AsyncCallDispatcherTest {

    private lateinit var dispatcher: AsyncCallDispatcher

    @Before fun setup() {
        dispatcher = AsyncCallDispatcher(1)
    }

    @Test fun dispatch() {
        val isRan = AtomicBoolean(false)
        val countDownLatch = CountDownLatch(1)
        val runnable = PrioritizedRunnableAdapter {
            isRan.set(true)
            countDownLatch.countDown()
        }
        dispatcher.dispatch(runnable)
        countDownLatch.await()
        assertThat(isRan.get()).isTrue()
    }

    @Test fun isIdle() {
        val lock = CountDownLatch(1)
        val runnable1 = PrioritizedRunnableAdapter {
            lock.await(200, TimeUnit.MILLISECONDS)
        }

        val countDownLatch = CountDownLatch(1)
        val runnable2 = PrioritizedRunnableAdapter {
            countDownLatch.countDown()
        }
        dispatcher.dispatch(runnable1)
        dispatcher.dispatch(runnable2)

        try {
            assertThat(dispatcher.isReadyQueueEmpty()).isFalse()
        } finally {
            lock.countDown()
        }

        countDownLatch.await()
        assertThat(dispatcher.isReadyQueueEmpty()).isTrue()
    }

    @Test fun remove() {
        val isRan = AtomicBoolean(false)
        val lock = CountDownLatch(1)
        val runnable1 = PrioritizedRunnableAdapter {
            lock.await(200, TimeUnit.MILLISECONDS)
        }
        val runnable2 = PrioritizedRunnableAdapter {
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
        // runnable1 is already dispatched, so remove should return false
        assertThat(dispatcher.remove(runnable1)).isFalse()
    }

    @Test fun executeOnPriority() {
        val lock = CountDownLatch(1)
        val block = PrioritizedRunnableAdapter {
            lock.await()
        }

        val countDownLatch = CountDownLatch(5)
        val executions = CopyOnWriteArrayList<Int>()
        val runnable0 = PrioritizedRunnableAdapter(Priorities.LOWEST) {
            executions.add(Priorities.LOWEST.value)
            countDownLatch.countDown()
        }
        val runnable1 = PrioritizedRunnableAdapter(Priorities.LOW) {
            executions.add(Priorities.LOW.value)
            countDownLatch.countDown()
        }
        val runnable2 = PrioritizedRunnableAdapter(Priorities.NORMAL) {
            executions.add(Priorities.NORMAL.value)
            countDownLatch.countDown()
        }
        val runnable3 = PrioritizedRunnableAdapter(Priorities.HIGH) {
            executions.add(Priorities.HIGH.value)
            countDownLatch.countDown()
        }
        val runnable4 = PrioritizedRunnableAdapter(Priorities.HIGHEST) {
            executions.add(Priorities.HIGHEST.value)
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
        assertThat(executions[0]).isEqualTo(Priorities.HIGHEST.value)
        assertThat(executions[1]).isEqualTo(Priorities.HIGH.value)
        assertThat(executions[2]).isEqualTo(Priorities.NORMAL.value)
        assertThat(executions[3]).isEqualTo(Priorities.LOW.value)
        assertThat(executions[4]).isEqualTo(Priorities.LOWEST.value)
    }

}