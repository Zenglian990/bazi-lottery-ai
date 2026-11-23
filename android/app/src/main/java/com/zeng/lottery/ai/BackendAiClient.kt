package com.zeng.lottery.ai

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.zeng.lottery.BuildConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 后端安全中继 AI 解盘 SSE 客户端（对接 /api/ai/divination）。
 * 自动为请求注入带时间戳与一次性 Nonce 的 HMAC-SHA256 防重放签名头。
 */
class BackendAiClient(
    private val okHttpClient: OkHttpClient,
    private val hmacSecretProvider: () -> String = { BuildConfig.APP_HMAC_SECRET }
) {

    private val gson = Gson()

    companion object {
        const val DIVINATION_PATH = "/api/ai/divination"
        const val FALLBACK_META_TAG = "\u0002FALLBACK\u0003"

        private fun bytesToHex(bytes: ByteArray): String {
            val sb = StringBuilder(bytes.size * 2)
            for (b in bytes) {
                val v = b.toInt() and 0xFF
                sb.append("0123456789abcdef"[v ushr 4])
                sb.append("0123456789abcdef"[v and 0x0F])
            }
            return sb.toString()
        }

        fun sha256Hex(data: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(data)
            return bytesToHex(digest)
        }

        fun hmacSha256Hex(secret: String, message: String): String {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
            return bytesToHex(mac.doFinal(message.toByteArray(StandardCharsets.UTF_8)))
        }

        /**
         * 生成与后端 backend/app/core/security.py 完全对齐的 HMAC-SHA256 + Nonce 防重放请求头。
         * Canonical String: "{ts}:{nonce}:{METHOD}:{path}:{body_sha256}"
         */
        fun buildHmacHeaders(
            method: String,
            path: String,
            bodyBytes: ByteArray,
            secret: String,
            timestampSec: Long = System.currentTimeMillis() / 1000L,
            nonce: String = UUID.randomUUID().toString().replace("-", "")
        ): Map<String, String> {
            val trimmedSecret = secret.trim()
            if (trimmedSecret.isEmpty()) return emptyMap()
            val ts = timestampSec.toString()
            val bodyHash = sha256Hex(bodyBytes)
            val canonical = "$ts:$nonce:${method.uppercase()}:$path:$bodyHash"
            val signature = hmacSha256Hex(trimmedSecret, canonical)
            return mapOf(
                "X-App-Timestamp" to ts,
                "X-App-Nonce" to nonce,
                "X-App-Signature" to signature
            )
        }
    }

    data class BackendSseEvent(
        val delta: String = "",
        val done: Boolean = false,
        val provider: String = "",
        val fallbackReason: String = ""
    )

    fun parseBackendEvent(data: String): BackendSseEvent {
        return try {
            val json = gson.fromJson(data, JsonObject::class.java)
            val delta = json.get("delta")?.takeIf { !it.isJsonNull }?.asString ?: ""
            val done = json.get("done")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
            val provider = json.get("provider")?.takeIf { !it.isJsonNull }?.asString ?: ""
            val fallbackReason = json.get("fallback_reason")?.takeIf { !it.isJsonNull }?.asString ?: ""
            BackendSseEvent(delta = delta, done = done, provider = provider, fallbackReason = fallbackReason)
        } catch (_: Exception) {
            BackendSseEvent()
        }
    }

    fun parseBackendDelta(data: String): Pair<String, Boolean> {
        val evt = parseBackendEvent(data)
        return Pair(evt.delta, evt.done)
    }

    fun streamDivination(
        baseUrl: String,
        lotteryType: String,
        primaryNumbers: List<String>,
        secondaryNumbers: List<String>,
        dayMaster: String?,
        strength: String?,
        xiYongShen: List<String>?,
        birthDateTime: String?,
        userName: String? = "缘主"
    ): Flow<String> = callbackFlow {
        val cleanBase = baseUrl.trimEnd('/')
        val endpoint = "$cleanBase$DIVINATION_PATH"

        val safeName = (if (userName.isNullOrBlank()) "缘主" else userName.trim())
            .replace("<", "")
            .replace(">", "")
            .take(20)

        val jsonPayload = JsonObject().apply {
            addProperty("lottery_type", lotteryType)
            addProperty("stream", true)
            addProperty("user_name", safeName)
            if (!birthDateTime.isNullOrBlank()) {
                addProperty("birth_datetime", birthDateTime)
            }

            val numbersObj = JsonObject().apply {
                val pArray = JsonArray()
                primaryNumbers.forEach { pArray.add(it) }
                add("primary_numbers", pArray)

                if (secondaryNumbers.isNotEmpty()) {
                    val sArray = JsonArray()
                    secondaryNumbers.forEach { sArray.add(it) }
                    add("secondary_numbers", sArray)
                }
            }
            add("recommended_numbers", numbersObj)

            if (!dayMaster.isNullOrBlank() || !strength.isNullOrBlank()) {
                val baziObj = JsonObject().apply {
                    if (!dayMaster.isNullOrBlank()) addProperty("day_master", dayMaster)
                    if (!strength.isNullOrBlank()) addProperty("strength", strength)
                    if (!xiYongShen.isNullOrEmpty()) {
                        val xyArray = JsonArray()
                        xiYongShen.forEach { xyArray.add(it) }
                        add("xi_yong_shen", xyArray)
                    }
                }
                add("bazi_summary", baziObj)
            }
        }

        val payloadBytes = jsonPayload.toString().toByteArray(StandardCharsets.UTF_8)
        val requestBody = payloadBytes.toRequestBody("application/json; charset=utf-8".toMediaType())

        val reqBuilder = Request.Builder()
            .url(endpoint)
            .post(requestBody)
            .header("Accept", "text/event-stream")

        val hmacHeaders = buildHmacHeaders(
            method = "POST",
            path = DIVINATION_PATH,
            bodyBytes = payloadBytes,
            secret = hmacSecretProvider()
        )
        hmacHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }

        val request = reqBuilder.build()

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                val evt = parseBackendEvent(data)
                if (evt.provider.equals("fallback", ignoreCase = true) || evt.fallbackReason.isNotBlank()) {
                    val reason = evt.fallbackReason.ifBlank { "云端上游大模型暂不可用，已切换云端典籍算法推演" }
                    trySend(FALLBACK_META_TAG + reason)
                }
                if (evt.delta.isNotEmpty()) {
                    trySend(evt.delta)
                }
                if (evt.done) {
                    close()
                }
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val code = response?.code
                val msg = when (code) {
                    401 -> "云端 AI 鉴权未通过 (HTTP 401)，请确认应用已注入有效签名或切换直连模式"
                    403 -> "云端 AI 拒绝访问 (HTTP 403)"
                    413 -> "请求体超出安全长度限制 (HTTP 413)"
                    429 -> "云端 AI 请求过于频繁 (HTTP 429)，请稍候重试"
                    in 500..599 -> "云端 AI 服务暂时繁忙 (HTTP $code)"
                    else -> t?.message ?: "云端 AI 中继连接异常 (HTTP ${code ?: "未知"})"
                }
                close(IOException(msg, t))
            }
        }

        val eventSource = EventSources.createFactory(okHttpClient).newEventSource(request, listener)
        awaitClose {
            eventSource.cancel()
        }
    }
}
