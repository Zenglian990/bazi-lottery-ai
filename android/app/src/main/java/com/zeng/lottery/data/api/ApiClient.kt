package com.zeng.lottery.data.api

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    const val PRODUCTION_CLOUD_URL = "https://bazi-lottery-79709693345.asia-east1.run.app/"
    private const val DEFAULT_BASE_URL = PRODUCTION_CLOUD_URL

    @Volatile
    private var customBaseUrl: String = DEFAULT_BASE_URL

    fun setBaseUrl(url: String?) {
        val trimmed = url?.trim() ?: ""
        if (trimmed.isBlank() || trimmed == "http://" || trimmed == "https://") {
            customBaseUrl = DEFAULT_BASE_URL
            return
        }
        val withScheme = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "https://$trimmed"
        } else {
            trimmed
        }
        val formatted = if (!withScheme.endsWith("/")) "$withScheme/" else withScheme
        customBaseUrl = if (formatted.toHttpUrlOrNull() != null) {
            formatted
        } else {
            DEFAULT_BASE_URL
        }
    }

    fun getBaseUrl(): String = customBaseUrl

    private val dynamicInterceptor = DynamicBaseUrlInterceptor { customBaseUrl }

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        OkHttpClient.Builder()
            .addInterceptor(dynamicInterceptor)
            .addInterceptor(logging)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    val apiService: LotteryApiService by lazy {
        Retrofit.Builder()
            .baseUrl(DEFAULT_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LotteryApiService::class.java)
    }
}
