package org.wenhui.prioritizeretrofit.helpers

import org.wenhui.prioritizeretrofit.Priorities
import org.wenhui.prioritizeretrofit.PrioritizedRunnable

open class PrioritizedRunnableAdapter(override val priority: Priorities = Priorities.NORMAL,
                                      private val block: () -> Unit) : PrioritizedRunnable {
    override fun run() {
        block()
    }
}