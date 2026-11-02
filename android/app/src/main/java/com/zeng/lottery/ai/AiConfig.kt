package com.zeng.lottery.ai

/**
 * Configuration model for AI Divination dual-mode integration.
 */
data class AiConfig(
    val provider: AiProvider = AiProvider.BACKEND_RELAY,
    val geminiApiKey: String = "",
    val geminiModel: String = "gemini-1.5-flash",
    val openaiApiKey: String = "",
    val openaiBaseUrl: String = "https://api.openai.com/v1",
    val openaiModel: String = "gpt-4o-mini"
)
