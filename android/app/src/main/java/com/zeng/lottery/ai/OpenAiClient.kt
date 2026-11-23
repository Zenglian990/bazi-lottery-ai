package com.zeng.lottery.ai

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
 * OpenAI 兼容接口（DeepSeek / OpenAI / Groq / SiliconFlow / 本地 Ollama）SSE 直连客户端。
 * 支持分离 reasoning_content（推理思考链）与 content（正式解盘正文），并在发包前校验 HTTPS 安全策略。
 */
data class OpenAiStreamEvent(
    val content: String = "",
    val reasoning: String = "",
    val done: Boolean = false
)

class OpenAiClient(private val okHttpClient: OkHttpClient) {

    private val gson = Gson()

    companion object {
        const val REASONING_TAG = "\u0002REASON\u0003"

        /**
         * 校验直连 Base URL 是否满足 HTTPS 安全要求（仅放行本机/模拟器回环明文地址）。
         */
        fun validateDirectBaseUrl(baseUrl: String): Pair<Boolean, String> {
            val trimmed = baseUrl.trim().trimEnd('/')
            if (trimmed.isBlank()) return Pair(false, "Base URL 不能为空")
            val parsed = trimmed.toHttpUrlOrNull()
                ?: return Pair(false, "Base URL 格式无效，请检查协议与域名")
            if (parsed.scheme == "http") {
                val host = parsed.host.lowercase()
                val isLocal = host == "10.0.2.2" || host == "127.0.0.1" || host == "localhost"
                if (!isLocal) {
                    return Pair(false, "安全拦截：禁止通过明文 HTTP 发送 API Key，请改用 HTTPS")
                }
            }
            return Pair(true, trimmed)
        }
    }

    /**
     * 结构化解析 OpenAI / DeepSeek SSE 数据块，区分正文与思考链。
     */
    fun parseOpenAiEvent(data: String): OpenAiStreamEvent {
        val trimmed = data.trim()
        if (trimmed == "[DONE]") {
            return OpenAiStreamEvent(done = true)
        }
        return try {
            val json = gson.fromJson(trimmed, JsonObject::class.java)
            val choices = json.getAsJsonArray("choices")
            if (choices != null && choices.size() > 0) {
                val choice = choices.get(0).asJsonObject
                val delta = choice.getAsJsonObject("delta")
                val content = if (delta != null && delta.has("content") && !delta.get("content").isJsonNull) {
                    delta.get("content").asString
                } else ""
                val reasoning = if (delta != null && delta.has("reasoning_content") && !delta.get("reasoning_content").isJsonNull) {
                    delta.get("reasoning_content").asString
                } else ""
                OpenAiStreamEvent(content = content, reasoning = reasoning, done = false)
            } else {
                OpenAiStreamEvent()
            }
        } catch (_: Exception) {
            OpenAiStreamEvent()
        }
    }

    /**
     * 向后兼容旧版解析接口（保留单测契约）。
     */
    fun parseOpenAiDelta(data: String): Pair<String, Boolean> {
        val evt = parseOpenAiEvent(data)
        if (evt.done) return Pair("", true)
        val text = evt.content.ifEmpty { evt.reasoning }
        return Pair(text, false)
    }

    fun streamDivination(
        systemPrompt: String,
        userPrompt: String,
        apiKey: String,
        baseUrl: String,
        model: String
    ): Flow<String> = callbackFlow {
        val (valid, cleanBaseOrErr) = validateDirectBaseUrl(baseUrl)
        if (!valid) {
            close(IOException(cleanBaseOrErr))
            return@callbackFlow
        }
        val endpoint = if (cleanBaseOrErr.endsWith("/v1")) {
            "$cleanBaseOrErr/chat/completions"
        } else {
            "$cleanBaseOrErr/v1/chat/completions"
        }
        val targetModel = model.ifBlank { "gpt-4o-mini" }

        val isReasoningModel = targetModel.contains("reasoner", ignoreCase = true) ||
            targetModel.startsWith("o1", ignoreCase = true) ||
            targetModel.startsWith("o3", ignoreCase = true) ||
            targetModel.startsWith("o4", ignoreCase = true)

        val jsonPayload = JsonObject().apply {
            addProperty("model", targetModel)
            addProperty("stream", true)
            if (!isReasoningModel) {
                addProperty("temperature", 0.7)
            }
            addProperty("max_tokens", if (isReasoningModel) 8192 else 4096)

            val messagesArray = JsonArray()
            messagesArray.add(JsonObject().apply {
                addProperty("role", "system")
                addProperty("content", systemPrompt)
            })
            messagesArray.add(JsonObject().apply {
                addProperty("role", "user")
                addProperty("content", userPrompt)
            })
            add("messages", messagesArray)
        }

        val requestBody = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(endpoint)
            .post(requestBody)
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "text/event-stream")
            .build()

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                val evt = parseOpenAiEvent(data)
                if (evt.reasoning.isNotEmpty()) {
                    trySend(REASONING_TAG + evt.reasoning)
                }
                if (evt.content.isNotEmpty()) {
                    trySend(evt.content)
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
                val detail = when (code) {
                    401 -> "直连 API Key 无效或已过期 (HTTP 401)"
                    402 -> "直连账户余额不足 (HTTP 402)"
                    404 -> "模型名称或接口路径不存在 (HTTP 404)"
                    429 -> "直连 API 限流或配额不足 (HTTP 429)"
                    else -> t?.message ?: "直连大模型 SSE 连接异常 (HTTP ${code ?: "未知"})"
                }
                close(IOException(detail, t))
            }
        }

        val eventSource = EventSources.createFactory(okHttpClient).newEventSource(request, listener)
        awaitClose {
            eventSource.cancel()
        }
    }
}
