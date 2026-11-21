package com.zeng.lottery.ai

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
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

/**
 * Google Gemini v1beta streamGenerateContent SSE 直连客户端。
 */
class GeminiClient(private val okHttpClient: OkHttpClient) {

    private val gson = Gson()

    fun parseGeminiDelta(data: String): String {
        return try {
            val json = gson.fromJson(data, JsonObject::class.java)
            val candidates = json.getAsJsonArray("candidates")
            if (candidates != null && candidates.size() > 0) {
                val candidate = candidates.get(0).asJsonObject
                val content = candidate.getAsJsonObject("content")
                val parts = content?.getAsJsonArray("parts")
                if (parts != null && parts.size() > 0) {
                    val sb = StringBuilder()
                    for (i in 0 until parts.size()) {
                        val partObj = parts.get(i).asJsonObject
                        val text = partObj.get("text")?.takeIf { !it.isJsonNull }?.asString
                        if (!text.isNullOrEmpty()) {
                            sb.append(text)
                        }
                    }
                    return sb.toString()
                }
            }
            ""
        } catch (_: Exception) {
            ""
        }
    }

    fun streamDivination(prompt: String, apiKey: String, model: String): Flow<String> = callbackFlow {
        val targetModel = model.ifBlank { "gemini-2.5-flash" }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$targetModel:streamGenerateContent?alt=sse"

        val jsonPayload = JsonObject().apply {
            val contentsArray = JsonArray()
            val contentObj = JsonObject().apply {
                val partsArray = JsonArray()
                val partObj = JsonObject().apply { addProperty("text", prompt) }
                partsArray.add(partObj)
                add("parts", partsArray)
            }
            contentsArray.add(contentObj)
            add("contents", contentsArray)

            val genConfig = JsonObject().apply {
                addProperty("temperature", 0.7)
                addProperty("maxOutputTokens", 4096)
            }
            add("generationConfig", genConfig)
        }

        val requestBody = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .header("Accept", "text/event-stream")
            .header("x-goog-api-key", apiKey)
            .build()

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                val delta = parseGeminiDelta(data)
                if (delta.isNotEmpty()) {
                    trySend(delta)
                }
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val code = response?.code
                val msg = when (code) {
                    400 -> "Gemini 请求参数或模型名异常 (HTTP 400)"
                    403 -> "Gemini API Key 权限不足或地区受限 (HTTP 403)"
                    429 -> "Gemini API 频率超限 (HTTP 429)"
                    else -> t?.message ?: "Gemini SSE 连接异常 (HTTP ${code ?: "未知"})"
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
