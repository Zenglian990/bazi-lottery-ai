package com.zeng.lottery

import com.zeng.lottery.ai.BackendAiClient
import com.zeng.lottery.ai.OpenAiClient
import com.zeng.lottery.data.api.ApiClient
import com.zeng.lottery.data.local.BaziEngine
import com.zeng.lottery.data.local.OfflineLotteryEngine
import com.zeng.lottery.data.local.PrizeChecker
import com.zeng.lottery.data.local.UserPreferencesManager
import com.zeng.lottery.data.model.DrawItem
import com.zeng.lottery.data.model.LotteryType
import com.zeng.lottery.data.model.LunarDate
import com.zeng.lottery.data.model.SavedBirthProfile
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime

/**
 * 专项回归测试：自动对奖引擎、HMAC-Nonce 签名、HTTPS 安全校验、端侧算法对齐与多生辰档案。
 */
class PrizeCheckerAndAuditFixesTest {

    @Test
    fun testSsqPrizeCheckerAllTiers() {
        val draw = DrawItem(
            issue = "2026130",
            drawDate = "2026-11-18",
            primaryNumbers = listOf("01", "05", "12", "18", "24", "31"),
            secondaryNumbers = listOf("09")
        )

        val first = PrizeChecker.checkTicket(LotteryType.SSQ, listOf(1, 5, 12, 18, 24, 31), listOf(9), draw)
        assertTrue(first.isWon)
        assertEquals("一等奖", first.prizeLevel)
        assertEquals(6, first.primaryHitCount)
        assertEquals(1, first.secondaryHitCount)

        val second = PrizeChecker.checkTicket(LotteryType.SSQ, listOf(1, 5, 12, 18, 24, 31), listOf(3), draw)
        assertEquals("二等奖", second.prizeLevel)

        val sixth = PrizeChecker.checkTicket(LotteryType.SSQ, listOf(2, 4, 6, 8, 10, 14), listOf(9), draw)
        assertTrue(sixth.isWon)
        assertEquals("六等奖", sixth.prizeLevel)

        val miss = PrizeChecker.checkTicket(LotteryType.SSQ, listOf(2, 4, 6, 8, 10, 14), listOf(3), draw)
        assertFalse(miss.isWon)
        assertEquals("未中奖", miss.prizeLevel)
    }

    @Test
    fun testDltPrizeCheckerTiers() {
        val draw = DrawItem(
            issue = "2026130",
            drawDate = "2026-11-18",
            primaryNumbers = listOf("02", "11", "17", "23", "35"),
            secondaryNumbers = listOf("04", "10")
        )

        val first = PrizeChecker.checkTicket(LotteryType.DLT, listOf(2, 11, 17, 23, 35), listOf(4, 10), draw)
        assertEquals("一等奖", first.prizeLevel)

        val ninthBackOnly = PrizeChecker.checkTicket(LotteryType.DLT, listOf(1, 3, 5, 7, 9), listOf(4, 10), draw)
        assertTrue(ninthBackOnly.isWon)
        assertEquals("九等奖", ninthBackOnly.prizeLevel)

        val miss = PrizeChecker.checkTicket(LotteryType.DLT, listOf(1, 3, 5, 7, 9), listOf(1, 2), draw)
        assertFalse(miss.isWon)
    }

    @Test
    fun testFc3dAndQxcPrizeCheckerPrecision() {
        val draw3d = DrawItem(issue = "2026300", drawDate = "2026-11-18", primaryNumbers = listOf("3", "5", "5"))
        assertEquals("直选中奖", PrizeChecker.checkTicket(LotteryType.FC3D, listOf(3, 5, 5), emptyList(), draw3d).prizeLevel)
        assertEquals("组选三命中", PrizeChecker.checkTicket(LotteryType.FC3D, listOf(5, 3, 5), emptyList(), draw3d).prizeLevel)
        assertFalse(PrizeChecker.checkTicket(LotteryType.FC3D, listOf(3, 3, 5), emptyList(), draw3d).isWon)

        val drawQxc = DrawItem(
            issue = "2026130",
            drawDate = "2026-11-18",
            primaryNumbers = listOf("1", "2", "3", "4", "5", "6"),
            secondaryNumbers = listOf("12")
        )
        // 位置错位不应计为前6位命中
        val shifted = PrizeChecker.checkTicket(LotteryType.QXC, listOf(6, 5, 4, 3, 2, 1), listOf(0), drawQxc)
        assertFalse(shifted.isWon)
        assertEquals(0, shifted.primaryHitCount)

        val lastHit = PrizeChecker.checkTicket(LotteryType.QXC, listOf(0, 0, 0, 0, 0, 0), listOf(12), drawQxc)
        assertTrue(lastHit.isWon)
        assertEquals("六等奖", lastHit.prizeLevel)
    }

    @Test
    fun testBackendHmacNonceHeadersCanonicalParity() {
        val bodyBytes = """{"lottery_type":"ssq"}""".toByteArray(StandardCharsets.UTF_8)
        val headers = BackendAiClient.buildHmacHeaders(
            method = "POST",
            path = "/api/ai/divination",
            bodyBytes = bodyBytes,
            secret = "test-secret-2026",
            timestampSec = 1700000000L,
            nonce = "abc123nonce"
        )
        assertEquals("1700000000", headers["X-App-Timestamp"])
        assertEquals("abc123nonce", headers["X-App-Nonce"])
        val expectedBodyHash = BackendAiClient.sha256Hex(bodyBytes)
        val expectedSig = BackendAiClient.hmacSha256Hex(
            "test-secret-2026",
            "1700000000:abc123nonce:POST:/api/ai/divination:$expectedBodyHash"
        )
        assertEquals(expectedSig, headers["X-App-Signature"])
        assertTrue(BackendAiClient.buildHmacHeaders("POST", "/api/ai/divination", bodyBytes, "").isEmpty())
    }

    @Test
    fun testHttpsEnforcementAndReasoningSplit() {
        assertFalse(OpenAiClient.validateDirectBaseUrl("http://api.deepseek.com/v1").first)
        assertTrue(OpenAiClient.validateDirectBaseUrl("https://api.deepseek.com/v1").first)
        assertTrue(OpenAiClient.validateDirectBaseUrl("http://10.0.2.2:11434/v1").first)

        assertFalse(ApiClient.validateServerUrl("http://evil.example.com").first)
        assertTrue(ApiClient.validateServerUrl("https://bazi-lottery-79709693345.asia-east1.run.app").first)

        val client = OpenAiClient(OkHttpClient())
        val reasoningEvt = client.parseOpenAiEvent("""{"choices":[{"delta":{"reasoning_content":"思考中"}}]}""")
        assertEquals("思考中", reasoningEvt.reasoning)
        assertEquals("", reasoningEvt.content)
        assertFalse(reasoningEvt.done)
    }

    @Test
    fun testOfflineConstraintsAndBirthProfilesPersistence() {
        val dt = LocalDateTime.of(1990, 8, 15, 8, 30)
        val destiny = BaziEngine.analyzeDestiny(dt, isMale = true, longitude = 104.1)
        assertTrue("Dayun details should be populated", !destiny.dayunDetails.isNullOrEmpty())
        assertNotNull(destiny.strengthLevel)

        val engine = OfflineLotteryEngine(context = null)
        val rec = engine.generateRecommendation(
            destiny = destiny,
            lotteryType = LotteryType.SSQ,
            count = 3,
            strategy = "极冷号反弹",
            lockedPrimary = listOf("07", "19"),
            excludedPrimary = listOf("01", "02", "03"),
            lockedSecondary = listOf("08")
        )
        assertEquals(3, rec.recommendations.size)
        rec.recommendations.forEach { item ->
            assertTrue(item.primaryNumbers.contains("07"))
            assertTrue(item.primaryNumbers.contains("19"))
            assertFalse(item.primaryNumbers.contains("01"))
            assertFalse(item.primaryNumbers.contains("02"))
            assertEquals(listOf("08"), item.secondaryNumbers)
        }

        UserPreferencesManager.resetJvmTestPrefs()
        val prefs = UserPreferencesManager(context = null)
        val p1 = SavedBirthProfile(label = "本人", userName = "曾先生", lunarDate = LunarDate(1990, 6, false, 24, 8, 30), longitude = 113.3)
        prefs.upsertBirthProfile(p1)
        assertEquals(1, prefs.getBirthProfiles().size)
        assertEquals("本人", prefs.getBirthProfiles().first().label)
        prefs.deleteBirthProfile(p1.id)
        assertTrue(prefs.getBirthProfiles().isEmpty())
    }

    @Test
    fun v141BackendDtoContractsAndAiFiltersWorkAccurately() {
        val gson = com.google.gson.Gson()
        // 1. 验证后端扁平 name_numerology 与 gan_zhi 大运字段反序列化
        val backendDestinyJson = """
            {
              "day_master": "辛",
              "strength": "身弱",
              "dayun_details": [
                {"gan_zhi": "乙酉", "start_age": 9, "end_age": 18, "start_year": 1998, "end_year": 2007}
              ],
              "name_numerology": {
                "surname": "曾",
                "given_name": "练",
                "character_strokes": {"曾": 12, "练": 8},
                "tian_ge": {"value": 13, "element": "火", "fortune": "大吉", "description": "天赋吉运"},
                "ren_ge": {"value": 20, "element": "水", "fortune": "凶", "description": "多逢考验"},
                "di_ge": {"value": 9, "element": "水", "fortune": "凶", "description": "沉潜蓄势"},
                "wai_ge": {"value": 2, "element": "木", "fortune": "凶", "description": "两仪之数"},
                "zong_ge": {"value": 20, "element": "水", "fortune": "凶", "description": "多逢考验"},
                "name_lucky_numbers": [1, 6, 10],
                "summary": "五格摘要"
              }
            }
        """.trimIndent()
        val parsed = gson.fromJson(backendDestinyJson, com.zeng.lottery.data.model.BaziDestiny::class.java)
        assertEquals("乙酉", parsed.dayunDetails?.firstOrNull()?.ganzhi)
        assertEquals(18, parsed.dayunDetails?.firstOrNull()?.endAge)
        val grids = parsed.nameNumerology?.resolvedFiveGrids.orEmpty()
        assertEquals(5, grids.size)
        assertEquals(13, grids["tian_ge"]?.value)
        assertEquals("火", grids["tian_ge"]?.wuxing)
        assertEquals("天赋吉运", grids["tian_ge"]?.meaning)

        // 2. 验证 Gemini 2.5 thought=true 思考块过滤
        val geminiClient = com.zeng.lottery.ai.GeminiClient(okhttp3.OkHttpClient())
        val thoughtChunk = """
            {"candidates":[{"content":{"parts":[{"text":"Thinking in English...","thought":true},{"text":"【命盘乾坤】正式卦辞"}]}}]}
        """.trimIndent()
        val delta = geminiClient.parseGeminiDelta(thoughtChunk)
        assertEquals("【命盘乾坤】正式卦辞", delta)

        // 3. 验证后端 SSE 降级元事件解析
        val backendClient = com.zeng.lottery.ai.BackendAiClient(okhttp3.OkHttpClient()) { "secret" }
        val metaEvt = backendClient.parseBackendEvent(
            """{"delta":"","done":false,"provider":"fallback","fallback_reason":"云端 Gemini 配额受限 (HTTP 429)"}"""
        )
        assertEquals("fallback", metaEvt.provider)
        assertTrue(metaEvt.fallbackReason.contains("429"))

        // 4. 验证早/晚子时文案区分与立春地支生肖映射
        assertEquals("00-01", BaziEngine.getShichenShortRange(0))
        assertEquals("23-24", BaziEngine.getShichenShortRange(23))
        assertEquals("龙", BaziEngine.getZodiacFromBranch("辰"))
        assertEquals("蛇", BaziEngine.getZodiacFromBranch("巳"))
    }

    @Test
    fun v150PublicReleaseHardeningAndUxRegressionChecks() {
        // 1. 公开版 APK 严禁在 BuildConfig 中暴露 ADMIN_SYNC_TOKEN
        val hasAdminField = BuildConfig::class.java.declaredFields.any { it.name == "ADMIN_SYNC_TOKEN" }
        assertFalse("Public APK BuildConfig must NOT contain ADMIN_SYNC_TOKEN", hasAdminField)

        // 2. 大运起运年龄与首步大运详情口径完全一致（虚岁）
        val dt = LocalDateTime.of(1990, 8, 15, 8, 30)
        val destiny = BaziEngine.analyzeDestiny(dt, isMale = true)
        assertEquals(destiny.dayunDetails?.firstOrNull()?.startAge, destiny.dayunStartAge)

        // 3. 本地兜底解盘区分身强与身弱话术
        val weakDestiny = destiny.copy(strength = "身弱")
        val weakText = com.zeng.lottery.ai.AiPromptBuilder.generateLocalFallback(
            lotteryType = LotteryType.SSQ,
            primaryNumbers = listOf("01", "02", "03", "04", "05", "06"),
            secondaryNumbers = listOf("07"),
            destiny = weakDestiny,
            userName = "曾先生🎉"
        )
        assertFalse(weakText.contains("生逢生旺之令，气盛神足"))
        assertTrue(weakText.contains("气机清灵内敛"))

        val strongDestiny = destiny.copy(strength = "身强")
        val strongText = com.zeng.lottery.ai.AiPromptBuilder.generateLocalFallback(
            lotteryType = LotteryType.SSQ,
            primaryNumbers = listOf("01", "02", "03", "04", "05", "06"),
            secondaryNumbers = listOf("07"),
            destiny = strongDestiny,
            userName = "曾先生"
        )
        assertTrue(strongText.contains("生逢生旺之令，气盛神足"))

        // 4. 收藏夹防重与 200 条上限保护
        com.zeng.lottery.data.local.UserPickHistoryStore.resetJvmTestStore()
        val store = com.zeng.lottery.data.local.UserPickHistoryStore(context = null)
        val sample = com.zeng.lottery.data.local.SavedTicket(
            lotteryType = LotteryType.SSQ,
            primaryNumbers = listOf(1, 2, 3, 4, 5, 6),
            secondaryNumbers = listOf(7)
        )
        assertTrue(store.saveUniqueTicketWithCap(sample))
        assertFalse(store.saveUniqueTicketWithCap(sample.copy(id = "another-id")))
        assertEquals("Duplicate ticket should be deduplicated", 1, store.getAllTickets().size)
        val overflow = (1..215).map { idx ->
            com.zeng.lottery.data.local.SavedTicket(
                id = "cap-$idx",
                timestamp = 1700000000000L + idx,
                lotteryType = LotteryType.SSQ,
                primaryNumbers = listOf((idx % 28) + 1, ((idx + 1) % 28) + 2, ((idx + 2) % 28) + 3, 29, 30, 31),
                secondaryNumbers = listOf((idx % 16) + 1)
            )
        }
        store.saveUniqueTicketsWithCap(overflow, maxTickets = 200)
        assertTrue("Ticket count must be capped at 200", store.getAllTickets().size <= 200)
        store.clearAll()
    }
}
