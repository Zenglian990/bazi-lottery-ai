package com.zeng.lottery.data.api

import com.zeng.lottery.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

object ApiClient {

    const val PRODUCTION_CLOUD_URL = "https://bazi-lottery-79709693345.asia-east1.run.app/"
    private const val DEFAULT_BASE_URL = PRODUCTION_CLOUD_URL

    @Volatile
    private var customBaseUrl: String = DEFAULT_BASE_URL

    /**
     * 校验用户输入的服务器地址是否合法。
     * 返回 Pair(是否合法, 规范化 URL 或中文错误提示)。
     */
    fun validateServerUrl(rawUrl: String?): Pair<Boolean, String> {
        val trimmed = rawUrl?.trim() ?: ""
        if (trimmed.isBlank()) {
            return Pair(true, DEFAULT_BASE_URL)
        }
        if (trimmed == "http://" || trimmed == "https://") {
            return Pair(false, "请输入完整的域名或 IP 地址")
        }
        val withScheme = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "https://$trimmed"
        } else {
            trimmed
        }
        val formatted = if (!withScheme.endsWith("/")) "$withScheme/" else withScheme
        val parsed = formatted.toHttpUrlOrNull()
            ?: return Pair(false, "URL 格式非法，请检查协议、主机名与端口")
        if (parsed.scheme == "http") {
            val host = parsed.host.lowercase()
            val isLocalLoopback = host == "10.0.2.2" || host == "127.0.0.1" || host == "localhost"
            if (!isLocalLoopback) {
                return Pair(false, "安全策略仅允许本地地址使用 HTTP，线上地址请使用 HTTPS")
            }
        }
        return Pair(true, formatted)
    }

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

    /**
     * 针对 Cloud Run 冷启动（maxScale=1 缩容至 0 后唤醒）偶发的边缘 500/502/503/504 或短时超时，
     * 对非写库请求执行一次短暂退避重试，避免隔夜首次打开 App 误判为离线。
     */
    private val coldStartRetryInterceptor = Interceptor { chain ->
        val req = chain.request()
        val path = req.url.encodedPath
        val retryable = !path.endsWith("/api/lottery/sync") && !path.endsWith("/api/ai/divination")
        var attempt = 0
        var lastException: IOException? = null
        while (attempt < 2) {
            try {
                val resp: Response = chain.proceed(req)
                val transient5xx = resp.code == 500 || resp.code == 502 || resp.code == 503 || resp.code == 504
                if (!transient5xx || !retryable || attempt >= 1) {
                    return@Interceptor resp
                }
                resp.close()
            } catch (e: IOException) {
                lastException = e
                if (!retryable || attempt >= 1) {
                    throw e
                }
            }
            attempt++
            try {
                Thread.sleep(1500L)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
        throw (lastException ?: IOException("Transient network error"))
    }

    val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        OkHttpClient.Builder()
            .addInterceptor(dynamicInterceptor)
            .addInterceptor(coldStartRetryInterceptor)
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 对指定服务器地址执行真实 /api/health 连通性探测与延迟测量（阻塞 IO，需在 IO 调度器中调用）。
     * 兼容 Cloud Run 缩容至 0 后的冷启动唤醒（18s 超时 + 瞬态 5xx/超时自动重试 1 次）。
     */
    fun pingServer(targetUrl: String): Pair<Boolean, String> {
        val (valid, normalizedOrErr) = validateServerUrl(targetUrl)
        if (!valid) return Pair(false, normalizedOrErr)
        val healthUrl = "${normalizedOrErr.trimEnd('/')}/api/health"
        val probeClient = OkHttpClient.Builder()
            .connectTimeout(18, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .build()
        val startMs = System.currentTimeMillis()
        var lastFailMsg = ""
        for (attempt in 0..1) {
            try {
                val req = Request.Builder().url(healthUrl).get().build()
                probeClient.newCall(req).execute().use { resp ->
                    val elapsed = System.currentTimeMillis() - startMs
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        val version = try {
                            JSONObject(body).optString("version", "")
                        } catch (_: Throwable) {
                            ""
                        }
                        val verPart = if (version.isNotBlank()) " · v$version" else ""
                        val coldTag = if (attempt > 0) " · 已唤醒" else ""
                        return Pair(true, "连接正常 (${elapsed}ms$verPart$coldTag)")
                    }
                    lastFailMsg = "服务响应异常 (HTTP ${resp.code}, ${elapsed}ms)"
                    if (resp.code !in listOf(500, 502, 503, 504)) {
                        return Pair(false, lastFailMsg)
                    }
                }
            } catch (e: Throwable) {
                val elapsed = System.currentTimeMillis() - startMs
                lastFailMsg = "连接失败 (${elapsed}ms): ${e.message ?: e.javaClass.simpleName}"
            }
            if (attempt == 0) {
                try {
                    Thread.sleep(1200L)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }
        return Pair(false, lastFailMsg.ifBlank { "连接超时，请稍后再试" })
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
