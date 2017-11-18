package org.wenhui.prioritizeretrofit

import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Retrofit
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.concurrent.Executor

/**
 * A [retrofit2.CallAdapter.Factory] that adapts a call instance, and return a prioritized call instance.
 *
 * Usage:
 * ```
 * interface FooService {
 *     @Priority(PRIORITY.HIGH) // add priority annotation, that's it
 *     @GET("/foo")
 *     fun getFoo(): Call<String>
 * }
 * ```
 *
 * *NOTE: the asynchronous [Call.enqueue] will no longer go through [okhttp3.Dispatcher], so
 * [okhttp3.Dispatcher.idleCallback] should not be used.*
 *
 */
class PrioritizedCallAdapterFactory private constructor(private val callFactory: PrioritizedCallFactory)
    : CallAdapter.Factory() {

    companion object {
        @JvmStatic fun create(): PrioritizedCallAdapterFactory {
            return PrioritizedCallAdapterFactory(PrioritizedCallFactory.create())
        }

        @JvmStatic fun create(dispatcher: CallDispatcher): PrioritizedCallAdapterFactory {
            return PrioritizedCallAdapterFactory(PrioritizedCallFactory.create(dispatcher))
        }
    }

    override fun get(returnType: Type, annotations: Array<out Annotation>?, retrofit: Retrofit): CallAdapter<*, *>? {
        if (getRawType(returnType) != Call::class.java) {
            return null
        }

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

        return PrioritizeCallAdapter<Any>(priority, responseType, callFactory, retrofit.callbackExecutor())
    }

    private class PrioritizeCallAdapter<R>(private val priority: Int,
                                           private val type: Type,
                                           private val factory: PrioritizedCallFactory,
                                           private val callbackExecutor: Executor?) : CallAdapter<R, Call<R>> {

        override fun responseType(): Type = type

        override fun adapt(call: Call<R>): Call<R> {
            return factory.createCall(call, priority, callbackExecutor)
        }
    }

}



