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
 * Direct client for OpenAI-compatible /v1/chat/completions API via SSE.
 * Compatible with OpenAI, DeepSeek, Groq, SiliconFlow, and local Ollama.
 */
class OpenAiClient(private val okHttpClient: OkHttpClient) {

    private val gson = Gson()

    fun parseOpenAiDelta(data: String): Pair<String, Boolean> {
        val trimmed = data.trim()
        if (trimmed == "[DONE]") {
            return Pair("", true)
        }
        return try {
            val json = gson.fromJson(trimmed, JsonObject::class.java)
            val choices = json.getAsJsonArray("choices")
            if (choices != null && choices.size() > 0) {
                val choice = choices.get(0).asJsonObject
                val delta = choice.getAsJsonObject("delta")
                val content = when {
                    delta != null && delta.has("content") && !delta.get("content").isJsonNull -> delta.get("content").asString
                    delta != null && delta.has("reasoning_content") && !delta.get("reasoning_content").isJsonNull -> delta.get("reasoning_content").asString
                    else -> ""
                }
                Pair(content, false)
            } else {
                Pair("", false)
            }
        } catch (e: Exception) {
            Pair("", false)
        }
    }

    fun streamDivination(
        systemPrompt: String,
        userPrompt: String,
        apiKey: String,
        baseUrl: String,
        model: String
    ): Flow<String> = callbackFlow {
        val cleanBase = baseUrl.trimEnd('/')
        val endpoint = if (cleanBase.endsWith("/v1")) {
            "$cleanBase/chat/completions"
        } else {
            "$cleanBase/v1/chat/completions"
        }
        val targetModel = model.ifBlank { "gpt-4o-mini" }

        val jsonPayload = JsonObject().apply {
            addProperty("model", targetModel)
            addProperty("stream", true)
            addProperty("temperature", 0.7)
            addProperty("max_tokens", 4096)

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
                val (delta, isDone) = parseOpenAiDelta(data)
                if (delta.isNotEmpty()) {
                    trySend(delta)
                }
                if (isDone) {
                    close()
                }
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                close(t ?: IOException("OpenAI SSE error: ${response?.code}"))
            }
        }

        val eventSource = EventSources.createFactory(okHttpClient).newEventSource(request, listener)
        awaitClose {
            eventSource.cancel()
        }
    }
}
