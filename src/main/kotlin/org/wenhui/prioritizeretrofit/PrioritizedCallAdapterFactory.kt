package org.wenhui.prioritizeretrofit

import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import java.io.IOException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.concurrent.Executor


class PrioritizedCallAdapterFactory private constructor(private val dispatcher: CallDispatcher) : CallAdapter.Factory() {

    companion object {
        @JvmStatic fun create() = PrioritizedCallAdapterFactory(CallDispatcher(4))

        @JvmStatic fun create(dispatcher: CallDispatcher) = PrioritizedCallAdapterFactory(dispatcher)
    }

    override fun get(returnType: Type, annotations: Array<out Annotation>?, retrofit: Retrofit): CallAdapter<*, *>? {
        if (getRawType(returnType) != Call::class.java) {
            return null
        }

        /*
         * getParameterUpperBound (below) requires a parametrized type. It will get the upper
         * bound of the generic parameter of returnType
         */
        if (returnType !is ParameterizedType) {
            throw IllegalStateException("Call must be parametrized, e.g. Call<Foo>")
        }

        val responseType = CallAdapter.Factory.getParameterUpperBound(0, returnType)

        var priority = PRIORITY_NORMAL
        annotations?.forEach {
            if (it is Priority) {
                priority = it.value
                return@forEach
            }
        }

        return PrioritizeCallAdapter<Any>(priority, responseType, dispatcher, retrofit.callbackExecutor())
    }

    private class PrioritizeCallAdapter<R>(private val priority: Int,
                                           private val type: Type,
                                           private val dispatcher: CallDispatcher,
                                           private val callbackExecutor: Executor?) : CallAdapter<R, Call<R>> {

        override fun responseType(): Type = type

        override fun adapt(call: Call<R>): Call<R> {
            return PrioritizedCall(priority, call, dispatcher, callbackExecutor)
        }
    }

}

private class PrioritizedCall<T>(override val priority: Int,
                                 private val realCall: Call<T>,
                                 private val dispatcher: CallDispatcher,
                                 private val callbackExecutor: Executor?) : Call<T> by realCall, PrioritizedRunnable {

    private var callback: Callback<T>? = null

    override fun enqueue(callback: Callback<T>?) {
        this.callback = callback
        dispatcher.dispatch(this)
    }

    override fun clone(): Call<T> = PrioritizedCall(priority, realCall.clone(), dispatcher, callbackExecutor)

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
        if (callbackExecutor != null) {
            callbackExecutor.execute {
                if (realCall.isCanceled) {
                    cb.onFailure(this, IOException("Cancelled!"))
                } else {
                    cb.onResponse(this, response)
                }
            }
        } else {
            cb.onResponse(this, response)
        }
    }

    private fun onFailure(error: Throwable) {
        val cb = callback ?: return
        if (callbackExecutor != null) {
            callbackExecutor.execute {
                cb.onFailure(this, error)
            }
        } else {
            cb.onFailure(this, error)
        }
    }

}

