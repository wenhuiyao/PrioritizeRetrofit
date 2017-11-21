package com.wenhui.prioritizeretrofit.helpers

import okhttp3.MediaType
import okhttp3.RequestBody
import retrofit2.Retrofit
import okhttp3.ResponseBody
import retrofit2.Converter
import java.lang.reflect.Type


class ToStringConverterFactory : Converter.Factory() {

    companion object {
        internal val MEDIA_TYPE = MediaType.parse("text/plain")
    }

    override fun responseBodyConverter(type: Type, annotations: Array<Annotation>,
                                       retrofit: Retrofit): Converter<ResponseBody, *>? {
        return if (String::class.java == type) {
            Converter<ResponseBody, String> { value -> value.string() }
        } else null
    }

    override fun requestBodyConverter(type: Type,
                                      parameterAnnotations: Array<Annotation>,
                                      methodAnnotations: Array<Annotation>,
                                      retrofit: Retrofit): Converter<*, RequestBody>? {
        return if (String::class.java == type) {
            Converter<String, RequestBody> { value -> RequestBody.create(MEDIA_TYPE, value) }
        } else null
    }

}