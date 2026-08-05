package com.easyradio.core.network.radiobrowser

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

object RadioBrowserApiFactory {

    // radio-browser.info publishes several mirrored hosts; de1 is used directly here.
    // A follow-up could resolve the fastest mirror via DNS SRV lookup as the API
    // documentation recommends, but a fixed mirror is sufficient for this phase.
    private const val BASE_URL = "https://de1.api.radio-browser.info/"

    fun create(): RadioBrowserApi {
        val json = Json { ignoreUnknownKeys = true }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
            )
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        return retrofit.create(RadioBrowserApi::class.java)
    }
}
