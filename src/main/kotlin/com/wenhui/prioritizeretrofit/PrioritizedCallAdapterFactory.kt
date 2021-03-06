package com.wenhui.prioritizeretrofit

import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Retrofit
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.concurrent.Executor

/**
 * A [CallAdapter.Factory] that adapts a call instance, and return a prioritized call instance.
 *
 * Usage:
 * ```
 * interface FooService {
 *     @Priority(Priorities.HIGH) // <-- add priority annotation, that's it
 *     @GET("/foo")
 *     fun getFoo(): Call<String>
 * }
 * ```
 *
 * *NOTE: only asynchronous call will support priority*
 */
class PrioritizedCallAdapterFactory private constructor(private val callFactory: PrioritizedCallFactory)
    : CallAdapter.Factory() {

    companion object {
        @JvmStatic fun create(): PrioritizedCallAdapterFactory {
            return PrioritizedCallAdapterFactory(PrioritizedCallFactory.create())
        }

        @JvmStatic fun create(dispatcher: AsyncCallDispatcher): PrioritizedCallAdapterFactory {
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
        return PrioritizeCallAdapter<Any>(responseType, callFactory, annotations, retrofit.callbackExecutor())
    }

    private class PrioritizeCallAdapter<R>(private val type: Type,
                                           private val factory: PrioritizedCallFactory,
                                           private val annotations: Array<out Annotation>?,
                                           private val callbackExecutor: Executor?) : CallAdapter<R, Call<R>> {

        override fun responseType(): Type = type

        override fun adapt(call: Call<R>): Call<R> {
            return factory.createCall(call, annotations, callbackExecutor)
        }
    }

}



