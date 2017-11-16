package org.wenhui.prioritizeretrofit

import java.util.concurrent.Executor
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit


class CallDispatcher(nThreads: Int) {

    private val queue: PriorityBlockingQueue<Runnable>
    private val executor: Executor

    init {
        if (nThreads < 1) {
            throw IllegalArgumentException("nThreads can't be less than 1")
        }

        queue = PriorityBlockingQueue(nThreads, Comparator<Runnable> { o1, o2 ->
            val left = o1 as PrioritizedRunnable
            val right = o2 as PrioritizedRunnable
            right.priority - left.priority
        })

        executor = ThreadPoolExecutor(nThreads, nThreads, 0, TimeUnit.SECONDS, queue)
    }

    internal fun dispatch(runnable: PrioritizedRunnable) {
        executor.execute(runnable)
    }

    internal fun remove(runnable: PrioritizedRunnable): Boolean {
        return queue.remove(runnable)
    }

    fun isIdle(): Boolean = queue.isEmpty()
}

internal interface PrioritizedRunnable : Runnable {
    val priority: Int
}



