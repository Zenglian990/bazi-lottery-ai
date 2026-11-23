package com.zeng.lottery.ai

import android.content.Context
import com.zeng.lottery.data.api.ApiClient
import com.zeng.lottery.data.model.BaziDestiny
import com.zeng.lottery.data.model.LotteryType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * 统一 AI 深度解盘路由门面。
 * 支持后端安全中继、DeepSeek / Gemini / OpenAI 直连，并在上游不可用时透明报告真实降级原因（绝不静默伪装成功）。
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
     * 以 Flow 形式流式返回易道深度解盘正文。
     * - [onReasoningDelta] 接收 DeepSeek R1 等推理模型的思考链，不混入五章节正文。
     * - [onFallback] 在发生密钥缺失、401/429 或网络故障降级本地卦辞时明确通知 UI 展示原因横幅。
     */
    fun streamDivination(
        context: Context?,
        lotteryType: LotteryType,
        primaryNumbers: List<String>,
        secondaryNumbers: List<String>,
        destiny: BaziDestiny?,
        birthDateTime: LocalDateTime?,
        configOverride: AiConfig? = null,
        userName: String = "缘主",
        onReasoningDelta: ((String) -> Unit)? = null,
        onFallback: ((String) -> Unit)? = null
    ): Flow<String> = flow {
        val config = configOverride ?: KeyStoreManager.loadAiConfig(context)

        val upstreamFlow: Flow<String>? = when (config.provider) {
            AiProvider.DIRECT_DEEPSEEK -> {
                if (config.deepseekApiKey.isBlank()) {
                    onFallback?.invoke("未配置 DeepSeek API Key，当前展示本地典籍卦辞；可点击右上角设置图标填入密钥。")
                    null
                } else {
                    openAiClient.streamDivination(
                        systemPrompt = AiPromptBuilder.buildSystemPrompt(),
                        userPrompt = AiPromptBuilder.buildUserPrompt(
                            lotteryType, primaryNumbers, secondaryNumbers, destiny, birthDateTime, userName
                        ),
                        apiKey = config.deepseekApiKey,
                        baseUrl = config.deepseekBaseUrl.ifBlank { "https://api.deepseek.com/v1" },
                        model = config.deepseekModel.ifBlank { "deepseek-chat" }
                    )
                }
            }

            AiProvider.DIRECT_GEMINI -> {
                if (config.geminiApiKey.isBlank()) {
                    onFallback?.invoke("未配置 Gemini API Key，当前展示本地典籍卦辞；可点击右上角设置图标填入密钥。")
                    null
                } else {
                    val prompt = "${AiPromptBuilder.buildSystemPrompt()}\n\n${
                        AiPromptBuilder.buildUserPrompt(
                            lotteryType, primaryNumbers, secondaryNumbers, destiny, birthDateTime, userName
                        )
                    }"
                    geminiClient.streamDivination(
                        prompt = prompt,
                        apiKey = config.geminiApiKey,
                        model = config.geminiModel.ifBlank { "gemini-2.5-flash" }
                    )
                }
            }

            AiProvider.DIRECT_OPENAI -> {
                if (config.openaiApiKey.isBlank()) {
                    onFallback?.invoke("未配置 OpenAI 兼容 API Key，当前展示本地典籍卦辞；可点击右上角设置图标填入密钥。")
                    null
                } else {
                    openAiClient.streamDivination(
                        systemPrompt = AiPromptBuilder.buildSystemPrompt(),
                        userPrompt = AiPromptBuilder.buildUserPrompt(
                            lotteryType, primaryNumbers, secondaryNumbers, destiny, birthDateTime, userName
                        ),
                        apiKey = config.openaiApiKey,
                        baseUrl = config.openaiBaseUrl,
                        model = config.openaiModel
                    )
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
                )
            }
        }

        if (upstreamFlow == null) {
            emitAllFallback(lotteryType, primaryNumbers, secondaryNumbers, destiny, userName)
            return@flow
        }

        var receivedContentChars = 0
        try {
            upstreamFlow.collect { chunk ->
                if (chunk.startsWith(OpenAiClient.REASONING_TAG)) {
                    val reasoningText = chunk.removePrefix(OpenAiClient.REASONING_TAG)
                    if (reasoningText.isNotEmpty()) {
                        onReasoningDelta?.invoke(reasoningText)
                    }
                } else if (chunk.startsWith(BackendAiClient.FALLBACK_META_TAG)) {
                    val reason = chunk.removePrefix(BackendAiClient.FALLBACK_META_TAG)
                    if (reason.isNotBlank()) {
                        onFallback?.invoke(reason)
                    }
                } else if (chunk.isNotEmpty()) {
                    receivedContentChars += chunk.length
                    emit(chunk)
                }
            }
            if (receivedContentChars == 0) {
                onFallback?.invoke("大模型返回空响应，已自动切换本地典籍卦辞兜底。")
                emitAllFallback(lotteryType, primaryNumbers, secondaryNumbers, destiny, userName)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            val reason = e.message?.takeIf { it.isNotBlank() } ?: "网络或上游服务不可用"
            if (receivedContentChars < 40) {
                onFallback?.invoke("$reason，已切换本地典籍卦辞兜底。")
                emitAllFallback(lotteryType, primaryNumbers, secondaryNumbers, destiny, userName)
            } else {
                onFallback?.invoke("流式传输在收尾阶段中断：$reason")
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
        val segments = fallbackText.split(Regex("(?<=[，。；！\n])"))
        for (seg in segments) {
            if (seg.isNotEmpty()) {
                emit(seg)
                delay(18)
            }
        }
    }
}
