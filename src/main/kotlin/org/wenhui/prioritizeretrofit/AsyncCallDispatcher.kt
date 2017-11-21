package org.wenhui.prioritizeretrofit

import java.util.concurrent.ExecutorService
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Policy on when a call will be executed
 */
class AsyncCallDispatcher(threadPoolSize: Int) {

    constructor(): this(PLATFORM.corePoolSize())

    private val queue: PriorityBlockingQueue<Runnable>
    private val executor: ExecutorService

    init {
        if (threadPoolSize < 1) {
            throw IllegalArgumentException("threadPoolSize can't be less than 1")
        }

        queue = PriorityBlockingQueue(threadPoolSize, Comparator<Runnable> { o1, o2 ->
            val left = o1 as PrioritizedRunnable
            val right = o2 as PrioritizedRunnable
            right.priority.value - left.priority.value
        })

        executor = ThreadPoolExecutor(threadPoolSize, threadPoolSize, 0, TimeUnit.SECONDS, queue, PLATFORM.threadFactory())
    }

    internal fun dispatch(runnable: PrioritizedRunnable) {
        executor.execute(runnable)
    }

    internal fun remove(runnable: PrioritizedRunnable): Boolean {
        return queue.remove(runnable)
    }

    internal fun isReadyQueueEmpty(): Boolean = queue.isEmpty()
}

internal interface PrioritizedRunnable : Runnable {
    val priority: Priorities
}



