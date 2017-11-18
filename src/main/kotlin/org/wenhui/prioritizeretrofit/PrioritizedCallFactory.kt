package org.wenhui.prioritizeretrofit

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import java.util.concurrent.Executor

/**
 * Create prioritized call instance base on [Call] instance from Retrofit, which addes priority to the call instance
 * when calling [Call.enqueue].
 *
 * If you are use [Call] for your service, you should consider [PrioritizedCallAdapterFactory] instead
 *
 * *NOTE: the new call instance will not go through [okhttp3.Dispatcher], instead, it will go through [CallDispatcher],
 * so [okhttp3.Dispatcher] will no longer report the correct queued and running call counts.*
 */
class PrioritizedCallFactory private constructor(private val dispatcher: CallDispatcher) {

    companion object {
        @JvmStatic fun create() = PrioritizedCallFactory(CallDispatcher(4))
        @JvmStatic fun create(dispatcher: CallDispatcher) = PrioritizedCallFactory(dispatcher)
    }

    /**
     * Create a new [Call] instance that will support priority.
     *
     * @param priority See [PRIORITY_HIGHEST], [PRIORITY_HIGH], [PRIORITY_NORMAL], [PRIORITY_LOW], [PRIORITY_LOWEST]
     * @param callbackExecutor An executor that will be used to execute [Callback], if passing null, the callback will be
     *      delivered in the same thread as the request is made
     */
    fun <T> createCall(call: Call<T>, priority: Int, callbackExecutor: Executor?): Call<T> {
        return PrioritizedCall(call, priority, dispatcher, callbackExecutor)
    }
}

private class PrioritizedCall<T>(private val realCall: Call<T>,
                                 override val priority: Int,
                                 private val dispatcher: CallDispatcher,
                                 private val callbackExecutor: Executor?) : Call<T> by realCall, PrioritizedRunnable {

    private var callback: Callback<T>? = null

    override fun enqueue(callback: Callback<T>?) {
        this.callback = callback
        dispatcher.dispatch(this)
    }

    override fun clone(): Call<T> = PrioritizedCall(realCall.clone(), priority, dispatcher, callbackExecutor)

    override fun cancel() {
        realCall.cancel()
        if (dispatcher.remove(this)) {
            // notify callback that call is cancelled
            onFailure(IOException("Cancelled!"))
        }
    }

    override fun run() {
        if (isCanceled) {
            onFailure(IOException("Cancelled!"))
            return
        }

        try {
            onResponse(realCall.execute())
        } catch (error: Throwable) {
            onFailure(error)
        }
    }

    private fun onResponse(response: Response<T>) {
        val cb = callback ?: return
        callbackExecutor?.execute {
            if (realCall.isCanceled) {
                cb.onFailure(this, IOException("Cancelled!"))
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