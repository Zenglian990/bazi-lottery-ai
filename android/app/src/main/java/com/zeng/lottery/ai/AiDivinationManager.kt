package com.zeng.lottery.ai

import android.content.Context
import com.zeng.lottery.data.api.ApiClient
import com.zeng.lottery.data.model.BaziDestiny
import com.zeng.lottery.data.model.LotteryType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Unified dispatch facade for AI Divination.
 * Routes to Backend Relay, Direct Gemini, or Direct OpenAI, with seamless fallback.
 */
class AiDivinationManager(
    private val okHttpClient: OkHttpClient = defaultClient()
) {

    private val geminiClient = GeminiClient(okHttpClient)
    private val openAiClient = OpenAiClient(okHttpClient)
    private val backendAiClient = BackendAiClient(okHttpClient)

    companion object {
        fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }

    /**
     * Stream divination卦辞 as Flow of delta strings.
     */
    fun streamDivination(
        context: Context?,
        lotteryType: LotteryType,
        primaryNumbers: List<String>,
        secondaryNumbers: List<String>,
        destiny: BaziDestiny?,
        birthDateTime: LocalDateTime?,
        configOverride: AiConfig? = null,
        userName: String = "缘主"
    ): Flow<String> {
        val config = configOverride ?: KeyStoreManager.loadAiConfig(context)

        return when (config.provider) {
            AiProvider.DIRECT_DEEPSEEK -> {
                if (config.deepseekApiKey.isNotBlank()) {
                    val systemPrompt = AiPromptBuilder.buildSystemPrompt()
                    val userPrompt = AiPromptBuilder.buildUserPrompt(
                        lotteryType, primaryNumbers, secondaryNumbers, destiny, birthDateTime, userName
                    )
                    openAiClient.streamDivination(
                        systemPrompt = systemPrompt,
                        userPrompt = userPrompt,
                        apiKey = config.deepseekApiKey,
                        baseUrl = config.deepseekBaseUrl.ifBlank { "https://api.deepseek.com/v1" },
                        model = config.deepseekModel.ifBlank { "deepseek-chat" }
                    ).catch {
                        emitAllFallback(lotteryType, primaryNumbers, secondaryNumbers, destiny, userName)
                    }
                } else {
                    emitFallbackFlow(lotteryType, primaryNumbers, secondaryNumbers, destiny, userName)
                }
            }

            AiProvider.DIRECT_GEMINI -> {
                if (config.geminiApiKey.isNotBlank()) {
                    val prompt = "${AiPromptBuilder.buildSystemPrompt()}\n\n${
                        AiPromptBuilder.buildUserPrompt(lotteryType, primaryNumbers, secondaryNumbers, destiny, birthDateTime, userName)
                    }"
                    geminiClient.streamDivination(prompt, config.geminiApiKey, config.geminiModel.ifBlank { "gemini-3.8-flash" })
                        .catch {
                            emitAllFallback(lotteryType, primaryNumbers, secondaryNumbers, destiny, userName)
                        }
                } else {
                    emitFallbackFlow(lotteryType, primaryNumbers, secondaryNumbers, destiny, userName)
                }
            }

            AiProvider.DIRECT_OPENAI -> {
                if (config.openaiApiKey.isNotBlank()) {
                    val systemPrompt = AiPromptBuilder.buildSystemPrompt()
                    val userPrompt = AiPromptBuilder.buildUserPrompt(
                        lotteryType, primaryNumbers, secondaryNumbers, destiny, birthDateTime, userName
                    )
                    openAiClient.streamDivination(
                        systemPrompt,
                        userPrompt,
                        config.openaiApiKey,
                        config.openaiBaseUrl,
                        config.openaiModel
                    ).catch {
                        emitAllFallback(lotteryType, primaryNumbers, secondaryNumbers, destiny, userName)
                    }
                } else {
                    emitFallbackFlow(lotteryType, primaryNumbers, secondaryNumbers, destiny, userName)
                }
            }

            AiProvider.BACKEND_RELAY -> {
                val dtStr = birthDateTime?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                val baseUrl = ApiClient.getBaseUrl()
                backendAiClient.streamDivination(
                    baseUrl = baseUrl,
                    lotteryType = lotteryType.code,
                    primaryNumbers = primaryNumbers,
                    secondaryNumbers = secondaryNumbers,
                    dayMaster = destiny?.dayMaster,
                    strength = destiny?.strength,
                    xiYongShen = destiny?.xiYongShen,
                    birthDateTime = dtStr,
                    userName = userName
                ).catch {
                    emitAllFallback(lotteryType, primaryNumbers, secondaryNumbers, destiny, userName)
                }
            }
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.emitAllFallback(
        lotteryType: LotteryType,
        primaryNumbers: List<String>,
        secondaryNumbers: List<String>,
        destiny: BaziDestiny?,
        userName: String = "缘主"
    ) {
        val fallbackText = AiPromptBuilder.generateLocalFallback(
            lotteryType, primaryNumbers, secondaryNumbers, destiny, userName
        )
        // Emit in natural typewriter segments
        val segments = fallbackText.split(Regex("(?<=[，。；！\n])"))
        for (seg in segments) {
            if (seg.isNotEmpty()) {
                emit(seg)
                delay(20)
            }
        }
    }

    private fun emitFallbackFlow(
        lotteryType: LotteryType,
        primaryNumbers: List<String>,
        secondaryNumbers: List<String>,
        destiny: BaziDestiny?,
        userName: String = "缘主"
    ): Flow<String> = flow {
        emitAllFallback(lotteryType, primaryNumbers, secondaryNumbers, destiny, userName)
    }
}
