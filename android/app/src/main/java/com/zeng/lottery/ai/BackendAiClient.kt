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
 * Backend relay client for calling /api/ai/divination via SSE.
 */
class BackendAiClient(private val okHttpClient: OkHttpClient) {

    private val gson = Gson()

    fun parseBackendDelta(data: String): Pair<String, Boolean> {
        return try {
            val json = gson.fromJson(data, JsonObject::class.java)
            val delta = json.get("delta")?.asString ?: ""
            val done = json.get("done")?.asBoolean ?: false
            Pair(delta, done)
        } catch (e: Exception) {
            Pair("", false)
        }
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
        val endpoint = "$cleanBase/api/ai/divination"

        val jsonPayload = JsonObject().apply {
            addProperty("lottery_type", lotteryType)
            addProperty("stream", true)
            addProperty("user_name", if (userName.isNullOrBlank()) "缘主" else userName.trim())
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

        val requestBody = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(endpoint)
            .post(requestBody)
            .header("Accept", "text/event-stream")
            .build()

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                val (delta, done) = parseBackendDelta(data)
                if (delta.isNotEmpty()) {
                    trySend(delta)
                }
                if (done) {
                    close()
                }
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                close(t ?: IOException("Backend AI SSE error: ${response?.code}"))
            }
        }

        val eventSource = EventSources.createFactory(okHttpClient).newEventSource(request, listener)
        awaitClose {
            eventSource.cancel()
        }
    }
}
