package org.wenhui.prioritizeretrofit

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import java.io.IOException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class PrioritizedCallTest {

    private lateinit var retrofit: Retrofit
    @Rule @JvmField val server = MockWebServer()

    @Before fun setup() {
        retrofit = Retrofit.Builder().baseUrl("http://www.example.com")
                .addCallAdapterFactory(PrioritizedCallAdapterFactory.create())
                .addConverterFactory(ToStringConverterFactory())
                .build()

        val requestDispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest?): MockResponse {
                return MockResponse().setResponseCode(200)
            }
        }

        server.setDispatcher(requestDispatcher)
    }

    @Test fun enqueue() {
        val countDownLatch = CountDownLatch(1)
        val service = retrofit.create(ExampleService::class.java)
        val call = service.getExamples()

        val responseReceived = AtomicBoolean(false)
        call.enqueue(object : CallbackAdapter<String>() {
            override fun onResponse(c: Call<String>?, response: Response<String>?) {
                // Make sure not the realCall enqueue is called.
                assertThat(c).isSameAs(call)

                responseReceived.set(true)
                countDownLatch.countDown()
            }
        })

        countDownLatch.await(1, TimeUnit.MINUTES)
        assertThat(responseReceived.get()).isTrue()
    }

    @Test fun clone() {
        val service = retrofit.create(ExampleService::class.java)
        val call = service.getExamples()
        assertThat(call.clone()).isNotSameAs(call)
    }

    @Test fun cancelBeforeEnqueue() {
        val service = retrofit.create(ExampleService::class.java)
        val call = service.getExamples()
        call.cancel()

        val countDownLatch = CountDownLatch(1)
        val exp = AtomicReference<Throwable>()
        call.enqueue(object : CallbackAdapter<String>() {
            override fun onFailure(c: Call<String>?, t: Throwable?) {
                // Make sure not the realCall enqueue is called.
                assertThat(c).isSameAs(call)

                countDownLatch.countDown()
                exp.set(t)
            }
        })

        countDownLatch.await(1, TimeUnit.SECONDS)
        assertThat(exp.get()).isNotNull()
        assertThat(exp.get()).isInstanceOf(IOException::class.java)
        assertThat(call.isCanceled).isTrue()
    }

    @Test fun dispatchResponseToCallbackExecutor() {
        var callbackExecuted = false
        val callbackExecutor = Executor {
            callbackExecuted = true
            it.run()
        }
        val retrofit = this.retrofit.newBuilder().callbackExecutor(callbackExecutor).build()

        val call = retrofit.create(ExampleService::class.java).getExamples()
        val countDownLatch = CountDownLatch(1)
        call.enqueue(object : CallbackAdapter<String>() {
            override fun onResponse(call: Call<String>?, response: Response<String>?) {
                countDownLatch.countDown()
            }
        })

        countDownLatch.await(1, TimeUnit.SECONDS)
        assertThat(callbackExecuted).isTrue()
    }

    @Test fun dispatchErrorToCallbackExecutor() {
        var callbackExecuted = false
        val callbackExecutor = Executor {
            callbackExecuted = true
            it.run()
        }
        val retrofit = this.retrofit.newBuilder().callbackExecutor(callbackExecutor).build()

        val call = retrofit.create(ExampleService::class.java).getExamples()
        call.cancel()
        val countDownLatch = CountDownLatch(1)
        call.enqueue(object : CallbackAdapter<String>() {
            override fun onFailure(call: Call<String>?, t: Throwable?) {
                countDownLatch.countDown()
            }
        })

        countDownLatch.await(1, TimeUnit.SECONDS)
        assertThat(callbackExecuted).isTrue()
    }

    @Test fun dispatchCancelBeforeExecuteCallback() {
        val countDownLatch = CountDownLatch(1)

        var callbackRunnable: Runnable? = null
        val callbackExecutor = Executor {
            callbackRunnable = it
            countDownLatch.countDown()
        }

        val retrofit = this.retrofit.newBuilder().callbackExecutor(callbackExecutor).build()
        val call = retrofit.create(ExampleService::class.java).getExamples()
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

    private interface ExampleService {

        @Priority(PRIORITY_HIGH)
        @GET("/")
        fun getExamples(): Call<String>

    }

}