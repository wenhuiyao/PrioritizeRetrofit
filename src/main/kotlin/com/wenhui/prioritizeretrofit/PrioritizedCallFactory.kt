package com.wenhui.prioritizeretrofit

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import java.util.concurrent.Executor

/**
 * Create prioritized call instance base on [Call] instance from Retrofit, which addes priority to the call instance
 * when calling [Call.enqueue].
 *
 * If return type of service method is [Call], use [PrioritizedCallAdapterFactory] instead
 */
class PrioritizedCallFactory private constructor(private val dispatcher: AsyncCallDispatcher) {

    companion object {
        @JvmStatic fun create() = PrioritizedCallFactory(AsyncCallDispatcher())
        @JvmStatic fun create(dispatcher: AsyncCallDispatcher) = PrioritizedCallFactory(dispatcher)
    }

    /**
     * Create a new [Call] instance from priority annotation.
     *
     * @param annotations An array of annotation that may contains [Priority], passing null will use default [Priorities.NORMAL]
     * @param callbackExecutor An executor that will be used to execute [Callback], if passing null, the callback will be
     *      delivered in the same thread as the request is made
     */
    fun <T> createCall(call: Call<T>, annotations: Array<out Annotation>?, callbackExecutor: Executor?): Call<T> {
        var priority = Priorities.NORMAL
        annotations?.forEach {
            if (it is Priority) {
                priority = it.value
                return@forEach
            }
        }
        return createCall(call, priority, callbackExecutor)
    }

    /**
     * Create a new [Call] instance that will support priority.
     *
     * @param priority
     * @param callbackExecutor An executor that will be used to execute [Callback], if passing null, the callback will be
     *      delivered in the same thread as the request is made
     */
    fun <T> createCall(call: Call<T>, priority: Priorities, callbackExecutor: Executor?): Call<T> {
        return PrioritizedCall(call, priority, dispatcher, callbackExecutor)
    }

}

private class PrioritizedCall<T>(private val realCall: Call<T>,
                                 override val priority: Priorities,
                                 private val dispatcher: AsyncCallDispatcher,
                                 private val callbackExecutor: Executor?) : Call<T> by realCall, PrioritizedRunnable {

    private var callback: Callback<T>? = null

    override fun enqueue(callback: Callback<T>?) {
        this.callback = callback
        dispatcher.dispatch(this)
    }

    override fun cancel() {
        realCall.cancel()
        if (dispatcher.remove(this)) {
            dispatcher.dispatch(object : PrioritizedRunnable {

                // Dispatch cancel exception immediately, so the reference to callback can be removed immediately
                // to avoid any temporarily memory leak
                override val priority = Priorities.HIGHEST

                override fun run() = onFailure(CancelException())
            })
        }
    }

    override fun clone(): Call<T> = PrioritizedCall(realCall.clone(), priority, dispatcher, callbackExecutor)

    override fun run() {
        if (isCanceled) {
            onFailure(CancelException())
            return
        }

        var response: Response<T>? = null
        try {
            response = realCall.execute()
        } catch (error: Throwable) {
            onFailure(error)
        }

        response?.let { onResponse(it) }
    }

    private fun onResponse(response: Response<T>) {
        val cb = callback ?: return
        callbackExecutor?.execute {
            if (realCall.isCanceled) {
                cb.onFailure(this, CancelException())
            } else {
                cb.onResponse(this, response)
            }
        } ?: cb.onResponse(this, response)
    }

    private fun onFailure(error: Throwable) {
        val cb = callback ?: return
        callbackExecutor?.execute {
            cb.onFailure(this, error)
        } ?: cb.onFailure(this, error)
    }

}

private class CancelException : IOException("Cancelled!")