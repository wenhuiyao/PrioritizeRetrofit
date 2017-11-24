package com.wenhui.prioritizeretrofit

import junit.framework.Assert.fail
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import com.wenhui.prioritizeretrofit.Priorities.HIGH
import com.wenhui.prioritizeretrofit.helpers.CallbackAdapter
import com.wenhui.prioritizeretrofit.helpers.ToStringConverterFactory
import com.wenhui.prioritizeretrofit.helpers.any
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import com.wenhui.prioritizeretrofit.Priorities.NORMAL
import com.wenhui.prioritizeretrofit.helpers.PrioritizedRunnableAdapter
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger


class PrioritizedCallFactoryTest {

    private lateinit var callDispatcher: AsyncCallDispatcher
    private lateinit var factory: PrioritizedCallFactory
    private lateinit var retrofit: Retrofit
    @Rule @JvmField val server = MockWebServer()

    @Before fun setup() {
        callDispatcher = spy(AsyncCallDispatcher(1))
        factory = PrioritizedCallFactory.create(callDispatcher)
        retrofit = Retrofit.Builder().baseUrl("http://www.example.com")
                .addCallAdapterFactory(PrioritizedCallAdapterFactory.create())
                .addConverterFactory(ToStringConverterFactory())
                .build()

        val dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return MockResponse().setResponseCode(200)
            }
        }

        server.setDispatcher(dispatcher)
    }

    @Test fun createNewCallThatIsNotTheSameCall() {
        val mockCall = mock(Call::class.java)
        val newCall = factory.createCall(mockCall, NORMAL, null)
        assertThat(mockCall).isNotSameAs(newCall)
    }

    @Test fun enqueue() {
        val oldCall = spy(retrofit.create(ExampleService::class.java).getExamples())
        val newCall = factory.createCall(oldCall, NORMAL, null)
        newCall.enqueue(CallbackAdapter<String>())

        // Doesn't call the oldCall's enqueue method
        verify(oldCall, times(0)).enqueue(null)

        // make sure it is dispatched
        verify(callDispatcher, times(1)).dispatch(any())
    }

    @Test fun clone() {
        val oldCall = retrofit.create(ExampleService::class.java).getExamples()
        val call = factory.createCall(oldCall, NORMAL, null)
        assertThat(call.clone()).isNotSameAs(call)
    }

    @Test fun cancel() {
        val oldCall = retrofit.create(ExampleService::class.java).getExamples()
        val call = factory.createCall(oldCall, NORMAL, null)

        // Cancel should remove the call from callDispatcher
        call.cancel()
        assertThat(oldCall.isCanceled).isTrue()
        assertThat(call.isCanceled).isTrue()
    }

    @Test fun execute() {
        val oldCall = spy(retrofit.create(ExampleService::class.java).getExamples())
        val call = factory.createCall(oldCall, NORMAL, null)

        // Cancel should remove the call from callDispatcher
        call.execute()
        verify(oldCall, times(1)).execute()
    }

    @Test fun request() {
        val oldCall = retrofit.create(ExampleService::class.java).getExamples()
        val call = factory.createCall(oldCall, NORMAL, null)

        val request = call.request()
        assertThat(oldCall.request()).isSameAs(request)
    }

    @Test fun dispatchResponseToCallbackExecutor() {
        var callbackExecuted = false
        val callbackExecutor = Executor {
            callbackExecuted = true
            it.run()
        }
        val rCall = retrofit.create(ExampleService::class.java).getExamples()
        val call = factory.createCall(rCall, NORMAL, callbackExecutor)
        val countDownLatch = CountDownLatch(1)

        val onResponseCalled = AtomicBoolean(false)
        val onFailureCalled = AtomicBoolean(false)

        call.enqueue(object : CallbackAdapter<String>() {
            override fun onResponse(call: Call<String>?, response: Response<String>?) {
                onResponseCalled.set(true)
                countDownLatch.countDown()
            }

            override fun onFailure(call: Call<String>?, t: Throwable?) {
                onFailureCalled.set(true)
                countDownLatch.countDown()
            }
        })

        countDownLatch.await(1, TimeUnit.SECONDS)

        assertThat(onResponseCalled.get()).isTrue()
        assertThat(onFailureCalled.get()).isFalse()
        assertThat(callbackExecuted).isTrue()
    }

    @Test fun dispatchCancelOnCallbackExecutor() {
        val countDownLatch = CountDownLatch(1)

        var callbackRunnable: Runnable? = null
        val callbackExecutor = Executor {
            callbackRunnable = it
            countDownLatch.countDown()
        }

        val rCall = retrofit.create(ExampleService::class.java).getExamples()
        val call = factory.createCall(rCall, NORMAL, callbackExecutor)

        val onResponseCalled = AtomicBoolean(false)
        val onFailureCalled = AtomicBoolean(false)
        call.enqueue(object : CallbackAdapter<String>() {
            override fun onResponse(call: Call<String>?, response: Response<String>?) {
                onResponseCalled.set(true)
            }

            override fun onFailure(call: Call<String>?, t: Throwable?) {
                onFailureCalled.set(true)
            }
        })

        countDownLatch.await(10, TimeUnit.SECONDS)
        assertThat(callbackRunnable).isNotNull()

        call.cancel()
        callbackRunnable?.run()

        assertThat(onResponseCalled.get()).isFalse()
        assertThat(onFailureCalled.get()).isTrue()
    }

    @Test fun makeSureExceptionAtOnResponseCallbackWontTriggerOnFailure() {
        val rCall = retrofit.create(ExampleService::class.java).getExamples()
        val call = factory.createCall(rCall, NORMAL, retrofit.callbackExecutor())

        val countDownLatch = CountDownLatch(1)
        val onResponseCalled = AtomicBoolean(false)
        val onFailureCalled = AtomicBoolean(false)
        call.enqueue(object : CallbackAdapter<String>() {
            override fun onResponse(call: Call<String>?, response: Response<String>?) {
                onResponseCalled.set(true)
                countDownLatch.countDown()
                throw RuntimeException("error")
            }

            override fun onFailure(call: Call<String>?, t: Throwable?) {
                onFailureCalled.set(true)
                countDownLatch.countDown()
            }
        })

        countDownLatch.await()
        assertThat(onResponseCalled.get()).isTrue()
        assertThat(onFailureCalled.get()).isFalse()
    }

    @Test fun dispatchErrorOnCallbackExecutor() {
        val mockCall = spy(retrofit.create(ExampleService::class.java).getExamples())
        Mockito.`when`(mockCall.execute()).thenThrow(RuntimeException())

        val lock = CountDownLatch(1)
        var callbackRunnable: Runnable? = null
        val call = factory.createCall(mockCall, NORMAL, Executor {
            callbackRunnable = it
            lock.countDown()
        })

        val onResponseCalled = AtomicBoolean(false)
        val onFailureCalled = AtomicBoolean(false)
        call.enqueue(object : CallbackAdapter<String>() {
            override fun onResponse(call: Call<String>?, response: Response<String>?) {
                onResponseCalled.set(true)
            }

            override fun onFailure(call: Call<String>?, t: Throwable?) {
                onFailureCalled.set(true)
            }
        })

        lock.await()
        assertThat(callbackRunnable).isNotNull()
        callbackRunnable?.run()
        assertThat(onResponseCalled.get()).isFalse()
        assertThat(onFailureCalled.get()).isTrue()
    }

    @Test fun dispatchResponseSynchronouslyWhenCallbackExecutorNull() {
        val rCall = retrofit.create(ExampleService::class.java).getExamples()
        val call = factory.createCall(rCall, NORMAL, null)

        val countDownLatch = CountDownLatch(1)
        val callbackExecuted = AtomicBoolean(false)
        call.enqueue(object : CallbackAdapter<String>() {
            override fun onResponse(call: Call<String>?, response: Response<String>?) {
                callbackExecuted.set(true)
                countDownLatch.countDown()
            }
        })

        countDownLatch.await(1, TimeUnit.SECONDS)
        assertThat(callbackExecuted.get()).isTrue()
    }

    @Test fun dispatchErrorSynchronouslyOnCallbackExecutor() {
        val mockCall = spy(retrofit.create(ExampleService::class.java).getExamples())
        Mockito.`when`(mockCall.execute()).thenThrow(RuntimeException())

        val countDownLatch = CountDownLatch(1)
        val call = factory.createCall(mockCall, NORMAL, null)

        val onFailureCalled = AtomicBoolean(false)
        call.enqueue(object : CallbackAdapter<String>() {
            override fun onFailure(call: Call<String>?, t: Throwable?) {
                onFailureCalled.set(true)
                countDownLatch.countDown()
            }
        })

        countDownLatch.await()
        assertThat(onFailureCalled.get()).isTrue()
    }

    @Test fun dispatchCancelRunnableWhenCancel() {
        val lock = CountDownLatch(1)
        val block = PrioritizedRunnableAdapter {
            lock.await()
        }
        callDispatcher.dispatch(block)

        val rCall = retrofit.create(ExampleService::class.java).getExamples()
        val call = factory.createCall(rCall, HIGH, null)
        val callToCancel = factory.createCall(rCall.clone(), NORMAL, null)

        val cancelRunnableCalled = AtomicBoolean(false)
        val error = AtomicInteger(0)
        val countDownLatch = CountDownLatch(1)
        call.enqueue(object: CallbackAdapter<String>(){
            override fun onResponse(call: Call<String>?, response: Response<String>?) {
                if (!cancelRunnableCalled.get()){
                    error.set(1)
                }
                countDownLatch.countDown()
            }

            override fun onFailure(call: Call<String>?, t: Throwable?) {
                if (!cancelRunnableCalled.get()){
                    error.set(2)
                }
                countDownLatch.countDown()
            }

        })
        callToCancel.enqueue(object: CallbackAdapter<String>(){
            override fun onResponse(call: Call<String>?, response: Response<String>?) {
                error.set(3)
            }

            override fun onFailure(call: Call<String>?, t: Throwable?) {
                cancelRunnableCalled.set(true)
            }
        })
        try {
            assertThat(callDispatcher.isReadyQueueEmpty()).isFalse()
            callToCancel.cancel()
        } finally {
            lock.countDown()
        }

        countDownLatch.await(100, TimeUnit.MILLISECONDS)
        if (error.get() > 0) {
            fail("Don't expect callback(${error.get()}) to call")
        }
    }

    @Test fun nameThreadBaseOnPriority() {
        val oldCall = spy(retrofit.create(ExampleService::class.java).getExamples())
        val countDownLatch = CountDownLatch(1)
        var threadName = ""
        val callWrapper = object: Call<String> by oldCall {
            override fun execute(): Response<String> {
                threadName = Thread.currentThread().name
                try {
                    return oldCall.execute()
                } finally {
                    countDownLatch.countDown()
                }
            }
        }

        val newCall = factory.createCall(callWrapper, NORMAL, null)
        newCall.enqueue(CallbackAdapter<String>())
        countDownLatch.await()

        assertThat(threadName).isEqualTo("PrioritizedCall: http://www.example.com/...")
    }

    private interface ExampleService {
        @Priority(Priorities.HIGH)
        @GET("/")
        fun getExamples(): Call<String>
    }

}