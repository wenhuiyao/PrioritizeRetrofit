package com.wenhui.prioritizeretrofit

import android.os.Build
import android.os.Process
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

internal val PLATFORM = findPlatform()

internal open class Platform {
    open fun corePoolSize(): Int = 10
    open fun threadFactory(): ThreadFactory = Executors.defaultThreadFactory()
}

private fun findPlatform(): Platform {
    try {
        Class.forName("android.os.Build")
        if (Build.VERSION.SDK_INT != 0) {
            return Android()
        }
    } catch (ignored: ClassNotFoundException) {
    }

    return Platform()
}

private class Android: Platform() {

    override fun corePoolSize(): Int {
        val cpuCount = Runtime.getRuntime().availableProcessors()
        return Math.max(3, Math.min(cpuCount - 1, 5))
    }

    override fun threadFactory(): ThreadFactory = AndroidThreadFactory()

    private class AndroidThreadFactory: ThreadFactory {
        override fun newThread(r: Runnable?): Thread = AndroidThread(r)
    }

    private class AndroidThread(r: Runnable?): Thread(r) {
        override fun run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            super.run()
        }
    }
}


