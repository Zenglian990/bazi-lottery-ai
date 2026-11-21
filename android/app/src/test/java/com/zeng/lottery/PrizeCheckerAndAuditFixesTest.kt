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
}
