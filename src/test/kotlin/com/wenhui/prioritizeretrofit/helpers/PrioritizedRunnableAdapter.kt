package com.wenhui.prioritizeretrofit.helpers

import com.wenhui.prioritizeretrofit.Priorities
import com.wenhui.prioritizeretrofit.PrioritizedRunnable

open class PrioritizedRunnableAdapter(override val priority: Priorities = Priorities.NORMAL,
                                      private val block: () -> Unit) : PrioritizedRunnable {
    override fun run() {
        block()
    }
}