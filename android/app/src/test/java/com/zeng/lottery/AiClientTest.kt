package com.zeng.lottery

import com.zeng.lottery.ai.AiConfig
import com.zeng.lottery.ai.AiPromptBuilder
import com.zeng.lottery.ai.AiProvider
import com.zeng.lottery.ai.BackendAiClient
import com.zeng.lottery.ai.GeminiClient
import com.zeng.lottery.ai.KeyStoreManager
import com.zeng.lottery.ai.OpenAiClient
import com.zeng.lottery.data.local.BaziEngine
import com.zeng.lottery.data.model.LotteryType
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Unit test suite for Android AI Divination Module (com.zeng.lottery.ai).
 * Verifies prompt generation, SSE chunk parsers, KeyStoreManager masking/fallback, and compliance.
 */
class AiClientTest {

    private val dummyClient = OkHttpClient()

    @Test
    fun testAiPromptBuilderGeneration() {
        val dt = LocalDateTime.of(1990, 8, 15, 8, 30)
        val destiny = BaziEngine.analyzeDestiny(dt, isMale = true)
        val primary = listOf("03", "06", "11", "18", "21", "28")
        val secondary = listOf("09")

        val sysPrompt = AiPromptBuilder.buildSystemPrompt()
        val userPrompt = AiPromptBuilder.buildUserPrompt(
            lotteryType = LotteryType.SSQ,
            primaryNumbers = primary,
            secondaryNumbers = secondary,
            destiny = destiny,
            birthDateTime = dt
        )

        // System prompt contains required 5 sections
        for (sec in AiPromptBuilder.MANDATORY_SECTIONS) {
            assertTrue("System prompt must require section $sec", sysPrompt.contains(sec))
        }
        for (kw in AiPromptBuilder.MANDATORY_KEYWORDS) {
            assertTrue("System prompt must require keyword $kw", sysPrompt.contains(kw))
        }

        // User prompt contains bazi details and lottery numbers
        assertTrue(userPrompt.contains("双色球"))
        assertTrue(userPrompt.contains("03, 06, 11, 18, 21, 28"))
        assertTrue(userPrompt.contains("09"))
        assertTrue(userPrompt.contains(destiny.dayMaster))
        assertTrue(userPrompt.contains(destiny.strength))
    }

    @Test
    fun testGeminiSseChunkParsing() {
        val geminiClient = GeminiClient(dummyClient)

        val sampleJson = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "【命盘乾坤】求测者日主生旺。"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val delta = geminiClient.parseGeminiDelta(sampleJson)
        assertEquals("【命盘乾坤】求测者日主生旺。", delta)

        // Empty/Malformed chunk returns empty string without crashing
        assertEquals("", geminiClient.parseGeminiDelta("{}"))
        assertEquals("", geminiClient.parseGeminiDelta("invalid json"))
    }

    @Test
    fun testOpenAiSseChunkParsing() {
        val openAiClient = OpenAiClient(dummyClient)

        val sampleChunk = """
            {
              "id": "chatcmpl-123",
              "choices": [
                {
                  "index": 0,
                  "delta": {
                    "content": "【数理玄机】尾数一六合水。"
                  }
                }
              ]
            }
        """.trimIndent()

        val (delta, isDone) = openAiClient.parseOpenAiDelta(sampleChunk)
        assertEquals("【数理玄机】尾数一六合水。", delta)
        assertFalse(isDone)

        // Test DeepSeek reasoning_content chunk in thinking mode
        val reasoningChunk = """
            {
              "id": "chatcmpl-reasoning-1",
              "choices": [
                {
                  "index": 0,
                  "delta": {
                    "reasoning_content": "推演天干合化，水木相生..."
                  }
                }
              ]
            }
        """.trimIndent()
        val (rDelta, rDone) = openAiClient.parseOpenAiDelta(reasoningChunk)
        assertEquals("推演天干合化，水木相生...", rDelta)
        assertFalse(rDone)

        // Test [DONE] sentinel
        val (doneDelta, doneStatus) = openAiClient.parseOpenAiDelta("[DONE]")
        assertEquals("", doneDelta)
        assertTrue(doneStatus)
    }

    @Test
    fun testBackendRelaySseParsing() {
        val backendClient = BackendAiClient(dummyClient)

        val normalChunk = """{"delta": "五行流转生化", "done": false}"""
        val (delta, done) = backendClient.parseBackendDelta(normalChunk)
        assertEquals("五行流转生化", delta)
        assertFalse(done)

        val endChunk = """{"delta": "", "done": true}"""
        val (endDelta, endDone) = backendClient.parseBackendDelta(endChunk)
        assertEquals("", endDelta)
        assertTrue(endDone)
    }

    @Test
    fun testKeyStoreManagerMaskingAndFallback() {
        // Test Key Masking
        assertEquals("sk-••••••••1234", KeyStoreManager.maskApiKey("sk-abcdefghijk1234"))
        assertEquals("AIza••••••••5678", KeyStoreManager.maskApiKey("AIzaSyBw12345678"))
        assertEquals("", KeyStoreManager.maskApiKey(""))

        // Test Pure JVM Encryption / Decryption Roundtrip
        val secretKey = "test-secret-key-gemini-2026"
        val encrypted = KeyStoreManager.encrypt(secretKey)
        assertTrue("Encrypted payload should contain colon IV separator", encrypted.contains(":"))
        val decrypted = KeyStoreManager.decrypt(encrypted)
        assertEquals(secretKey, decrypted)

        // Test Default Model Names in AiConfig
        val defaultConfig = AiConfig()
        assertEquals("deepseek-flash", defaultConfig.deepseekModel)
        assertEquals("gemini-3.8-flash", defaultConfig.geminiModel)
        assertEquals(AiProvider.DIRECT_DEEPSEEK.defaultModel, "deepseek-flash")
        assertEquals(AiProvider.DIRECT_GEMINI.defaultModel, "gemini-3.8-flash")

        // Test Config Save & Load with JVM Fallback
        val testConfig = AiConfig(
            provider = AiProvider.DIRECT_DEEPSEEK,
            deepseekApiKey = "sk-deepseek-test-9999",
            deepseekBaseUrl = "https://api.deepseek.com/v1",
            deepseekModel = "deepseek-flash",
            geminiApiKey = "AIzaSyTestKey9999",
            geminiModel = "gemini-3.8-flash",
            openaiApiKey = "sk-test-openai-key-8888",
            openaiBaseUrl = "https://api.openai.com/v1",
            openaiModel = "gpt-4o-mini"
        )
        KeyStoreManager.saveAiConfig(null, testConfig)
        val loaded = KeyStoreManager.loadAiConfig(null)

        assertEquals(AiProvider.DIRECT_DEEPSEEK, loaded.provider)
        assertEquals("sk-deepseek-test-9999", loaded.deepseekApiKey)
        assertEquals("https://api.deepseek.com/v1", loaded.deepseekBaseUrl)
        assertEquals("deepseek-flash", loaded.deepseekModel)
        assertEquals("AIzaSyTestKey9999", loaded.geminiApiKey)
        assertEquals("gemini-3.8-flash", loaded.geminiModel)
        assertEquals("sk-test-openai-key-8888", loaded.openaiApiKey)
        assertEquals("https://api.openai.com/v1", loaded.openaiBaseUrl)
        assertEquals("gpt-4o-mini", loaded.openaiModel)
    }

    @Test
    fun testFallbackDivinationDisclaimerPresence() {
        val dt = LocalDateTime.of(1988, 5, 20, 10, 0)
        val destiny = BaziEngine.analyzeDestiny(dt, isMale = true)
        val primary = listOf("05", "10", "15", "20", "25")
        val secondary = listOf("01", "06")

        val fallback = AiPromptBuilder.generateLocalFallback(
            lotteryType = LotteryType.DLT,
            primaryNumbers = primary,
            secondaryNumbers = secondary,
            destiny = destiny
        )

        // Strictly verify 5 sections
        for (sec in AiPromptBuilder.MANDATORY_SECTIONS) {
            assertTrue("Fallback text must contain $sec", fallback.contains(sec))
        }

        // Strictly verify mandatory responsible gaming keywords
        for (kw in AiPromptBuilder.MANDATORY_KEYWORDS) {
            assertTrue("Fallback text must contain keyword '$kw'", fallback.contains(kw))
        }
    }
}
