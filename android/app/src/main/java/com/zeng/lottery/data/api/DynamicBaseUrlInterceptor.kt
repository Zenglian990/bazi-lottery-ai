package com.zeng.lottery.data.api

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Interceptor that dynamically replaces the scheme, host, and port of the request URL
 * based on the user-configured server URL (emulator 10.0.2.2, LAN IP, or Cloud Run).
 */
class DynamicBaseUrlInterceptor(
    private val baseUrlProvider: () -> String
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        return try {
            val currentBaseUrlStr = baseUrlProvider()
            val newBaseUrl = currentBaseUrlStr.toHttpUrlOrNull() ?: return chain.proceed(originalRequest)

            val newUrl = originalRequest.url.newBuilder()
                .scheme(newBaseUrl.scheme)
                .host(newBaseUrl.host)
                .port(newBaseUrl.port)
                .build()

            val newRequest = originalRequest.newBuilder()
                .url(newUrl)
                .build()

            chain.proceed(newRequest)
        } catch (e: Throwable) {
            chain.proceed(originalRequest)
        }
    }
}
