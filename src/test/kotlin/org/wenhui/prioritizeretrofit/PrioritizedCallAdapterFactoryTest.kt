package org.wenhui.prioritizeretrofit

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockWebServer
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.POST
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.assertj.core.api.Assertions
import retrofit2.Callback
import retrofit2.Response
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean


class PrioritizedCallAdapterFactoryTest {

    private lateinit var retrofit: Retrofit
    @Rule @JvmField val server = MockWebServer()

    @Before fun setup()  {
       retrofit = Retrofit.Builder().baseUrl("http://www.example.com")
               .addCallAdapterFactory(PrioritizedCallAdapterFactory.create(CallDispatcher(1)))
               .addConverterFactory(ToStringConverterFactory())
               .build()

    }

    @Test fun testExecuteOrder() {
        val lock = AtomicBoolean(true)
        val dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                while (lock.get()) {
                    Thread.sleep(100)
                }
                if (request.path == "/stub") {
                    return MockResponse().setResponseCode(200)
                } else if (request.path == "/v1") {
                    return MockResponse().setResponseCode(200)
                } else if (request.path == "v2") {
                    return MockResponse().setResponseCode(200)
                } else if (request.path == "/v3") {
                    return MockResponse().setResponseCode(200)
                }
                return MockResponse().setResponseCode(404)
            }
        }

        server.setDispatcher(dispatcher)

        val service = retrofit.create(ExampleService::class.java)
        val executeOrder = CopyOnWriteArrayList<Int>()

        val latch = CountDownLatch(3)
        service.block().enqueue(object: CallbackAdapter<String>() {})

        service.getLowPriorityExamples().enqueue(object: CallbackAdapter<String>() {
            override fun onResponse(call: Call<String>?, response: Response<String>?) {
                executeOrder.add(PRIORITY_LOW)
                latch.countDown()
            }

        })
        service.getNormalPriorityExamples().enqueue(object: CallbackAdapter<String>() {
            override fun onResponse(call: Call<String>?, response: Response<String>?) {
                executeOrder.add(PRIORITY_NORMAL)
                latch.countDown()
            }

        })
        service.getHighPriorityExamples().enqueue(object: CallbackAdapter<String>() {
            override fun onResponse(call: Call<String>?, response: Response<String>?) {
                executeOrder.add(PRIORITY_HIGH)
                latch.countDown()
            }
        })

        latch.await(1, TimeUnit.MINUTES)
        lock.set(false)
        
        Assertions.assertThat(executeOrder.size).isEqualTo(3)
        Assertions.assertThat(executeOrder[0]).isEqualTo(PRIORITY_HIGH)
        Assertions.assertThat(executeOrder[1]).isEqualTo(PRIORITY_NORMAL)
        Assertions.assertThat(executeOrder[2]).isEqualTo(PRIORITY_LOW)
    }

    private interface ExampleService {

        @GET("/stub")
        fun block(): Call<String>

        @Priority(PRIORITY_LOW)
        @GET("/v1")
        fun getLowPriorityExamples(): Call<String>

        @POST("/v2")
        fun getNormalPriorityExamples(): Call<String>

        @Priority(PRIORITY_HIGH)
        @GET("/v3")
        fun getHighPriorityExamples(): Call<String>

    }

}