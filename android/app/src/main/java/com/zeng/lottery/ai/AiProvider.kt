package com.zeng.lottery.ai

/**
 * Supported AI LLM providers for intelligent divination.
 */
enum class AiProvider(val displayName: String, val defaultModel: String) {
    BACKEND_RELAY("后端安全中继 (默认推荐)", "auto"),
    DIRECT_DEEPSEEK("DeepSeek 深度求索 (国内直连)", "deepseek-chat"),
    DIRECT_GEMINI("Google Gemini (直连)", "gemini-2.5-flash"),
    DIRECT_OPENAI("OpenAI 兼容接口 (直连)", "gpt-4o-mini");

    companion object {
        fun fromName(name: String?): AiProvider {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: BACKEND_RELAY
        }
    }
}
