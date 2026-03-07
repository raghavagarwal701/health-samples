/*
 * Singleton Retrofit client for the Pulse Backend API.
 * Defaults to 10.0.2.2:8000 (Android emulator → host localhost).
 * For physical device testing, change BASE_URL to your machine's LAN IP.
 */
package com.example.healthconnectsample.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // Default: ngrok forwarding URL
    private const val DEFAULT_BASE_URL = "https://f2c7-203-110-242-32.ngrok-free.app/"

    private var baseUrl: String = DEFAULT_BASE_URL

    fun setBaseUrl(url: String) {
        baseUrl = url
        // Reset cached instances so they pick up the new URL
        _retrofit = null
        _apiService = null
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)    // LLM responses can be slow
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var _retrofit: Retrofit? = null
    private val retrofit: Retrofit
        get() = _retrofit ?: Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .also { _retrofit = it }

    private var _apiService: PulseApiService? = null
    val apiService: PulseApiService
        get() = _apiService ?: retrofit.create(PulseApiService::class.java)
            .also { _apiService = it }
}
