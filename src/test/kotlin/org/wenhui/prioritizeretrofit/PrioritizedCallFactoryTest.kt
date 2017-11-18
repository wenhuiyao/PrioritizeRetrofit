package org.wenhui.prioritizeretrofit

import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.wenhui.prioritizeretrofit.helpers.CallbackAdapter
import org.wenhui.prioritizeretrofit.helpers.PrioritizedRunnableAdapter
import org.wenhui.prioritizeretrofit.helpers.ToStringConverterFactory
import org.wenhui.prioritizeretrofit.helpers.any
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean


class PrioritizedCallFactoryTest {

    private lateinit var callDispatcher: CallDispatcher
    private lateinit var factory: PrioritizedCallFactory
    private lateinit var retrofit: Retrofit

    @Before fun setup() {
        callDispatcher = spy(CallDispatcher(1))
        factory = PrioritizedCallFactory.create(callDispatcher)
        retrofit = Retrofit.Builder().baseUrl("http://www.example.com")
                .addCallAdapterFactory(PrioritizedCallAdapterFactory.create())
                .addConverterFactory(ToStringConverterFactory())
                .build()
    }

    @Test fun createNewCallThatIsNotTheSameCall() {
        val mockCall = mock(Call::class.java)
        val newCall = factory.createCall(mockCall, PRIORITY_NORMAL, null)
        assertThat(mockCall).isNotSameAs(newCall)
    }

    @Test fun enqueue() {
        val oldCall = spy(retrofit.create(ExampleService::class.java).getExamples())
        val newCall = factory.createCall(oldCall, PRIORITY_NORMAL, null)
        newCall.enqueue(null)

        // Doesn't call the oldCall's enqueue method
        verify(oldCall, times(0)).enqueue(null)

        // make sure it is dispatched
        verify(callDispatcher, times(1)).dispatch(any())
    }

    @Test fun clone() {
        val oldCall = retrofit.create(ExampleService::class.java).getExamples()
        val call = factory.createCall(oldCall, PRIORITY_NORMAL, null)
        assertThat(call.clone()).isNotSameAs(call)
    }

    @Test fun cancel() {
        val lock = CountDownLatch(1)
        val blocking = PrioritizedRunnableAdapter { lock.await() }
        callDispatcher.dispatch(blocking)

        try {
            val oldCall = retrofit.create(ExampleService::class.java).getExamples()
            val call = factory.createCall(oldCall, PRIORITY_NORMAL, null)
            call.enqueue(null)
            assertThat(callDispatcher.isIdle()).isFalse()

            // Cancel should remoev the call from callDispatcher
            call.cancel()
            assertThat(callDispatcher.isIdle()).isTrue()
        } finally {
            lock.countDown()
        }
    }

    @Test fun dispatchResponseToCallbackExecutor() {
        var callbackExecuted = false
        val callbackExecutor = Executor {
            callbackExecuted = true
            it.run()
        }
        val rCall = retrofit.create(ExampleService::class.java).getExamples()
        val call = factory.createCall(rCall, PRIORITY_NORMAL, callbackExecutor)
        val countDownLatch = CountDownLatch(1)
        call.enqueue(object : CallbackAdapter<String>() {
            override fun onResponse(call: Call<String>?, response: Response<String>?) {
                countDownLatch.countDown()
            }
        })

        countDownLatch.await(1, TimeUnit.SECONDS)
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
        val call = factory.createCall(rCall, PRIORITY_NORMAL, callbackExecutor)
        val onFailureCalled = AtomicBoolean(false)
        call.enqueue(object : CallbackAdapter<String>() {
            override fun onFailure(call: Call<String>?, t: Throwable?) {
                onFailureCalled.set(true)
            }
        })

        countDownLatch.await(10, TimeUnit.SECONDS)
        assertThat(callbackRunnable).isNotNull()

        call.cancel()
        callbackRunnable?.run()

        assertThat(onFailureCalled.get()).isTrue()
    }

    @Test fun makeSureToRemoveCallFromCallDispatcherWhenCancel() {
        val oldCall = retrofit.create(ExampleService::class.java).getExamples()
        val call = factory.createCall(oldCall, PRIORITY_NORMAL, null)
        call.cancel()
        assertThat(call.isCanceled).isTrue()
    }

    @Test fun dispatchErrorOnCallbackExecutor() {
        val mockCall = spy(retrofit.create(ExampleService::class.java).getExamples())
        Mockito.`when`(mockCall.execute()).thenThrow(RuntimeException())

        val lock = CountDownLatch(1)
        var callbackRunnable: Runnable? = null
        val call = factory.createCall(mockCall, PRIORITY_NORMAL, Executor {
            callbackRunnable = it
            lock.countDown()
        })

        val onFailureCalled = AtomicBoolean(false)
        call.enqueue(object: CallbackAdapter<String>(){
            override fun onFailure(call: Call<String>?, t: Throwable?) {
                onFailureCalled.set(true)
            }
        })

        lock.await()
        assertThat(callbackRunnable).isNotNull()
        callbackRunnable?.run()
        assertThat(onFailureCalled.get()).isTrue()
    }

    @Test fun dispatchResponseSynchronouslyWhenCallbackExecutorNull(){
        val rCall = retrofit.create(ExampleService::class.java).getExamples()
        val call = factory.createCall(rCall, PRIORITY_NORMAL, null)

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
        val call = factory.createCall(mockCall, PRIORITY_NORMAL, null)

        val onFailureCalled = AtomicBoolean(false)
        call.enqueue(object: CallbackAdapter<String>(){
            override fun onFailure(call: Call<String>?, t: Throwable?) {
                onFailureCalled.set(true)
                countDownLatch.countDown()
            }
        })

        countDownLatch.await()
        assertThat(onFailureCalled.get()).isTrue()
    }

    private interface ExampleService {
        @Priority(PRIORITY_HIGH)
        @GET("/")
        fun getExamples(): Call<String>
    }

}