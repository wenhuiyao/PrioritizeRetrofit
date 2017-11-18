package org.wenhui.prioritizeretrofit.helpers

import org.wenhui.prioritizeretrofit.PRIORITY_NORMAL
import org.wenhui.prioritizeretrofit.PrioritizedRunnable

open class PrioritizedRunnableAdapter(override val priority: Int = PRIORITY_NORMAL,
                                      private val block: () -> Unit) : PrioritizedRunnable {
    override fun run() {
        block()
    }
}