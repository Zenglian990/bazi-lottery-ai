package com.zeng.lottery

import com.google.gson.Gson
import com.zeng.lottery.data.local.BaziEngine
import com.zeng.lottery.data.local.OfflineLotteryEngine
import com.zeng.lottery.data.model.LotteryStats
import com.zeng.lottery.data.model.LotteryType
import com.zeng.lottery.data.model.displayJiShen
import com.zeng.lottery.data.model.displayLackingElements
import com.zeng.lottery.data.model.displayXiYongShen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelTest {

    private val gson = Gson()

    @Test
    fun testOfflineLotteryRecommendationWorkflow() = runTest {
        val dt = LocalDateTime.of(1990, 8, 15, 8, 30)
        val destiny = BaziEngine.analyzeDestiny(dt, isMale = true)
        val engine = OfflineLotteryEngine(context = null)

        val ssqRec = engine.generateRecommendation(
            destiny = destiny,
            lotteryType = LotteryType.SSQ,
            count = 1,
            strategy = "命理吉数优先"
        )
        assertNotNull(ssqRec)
        assertEquals(1, ssqRec.recommendations.size)
        assertEquals(6, ssqRec.recommendations.first().primaryNumbers.size)
        assertEquals(1, ssqRec.recommendations.first().secondaryNumbers.size)

        val dltRec = engine.generateRecommendation(
            destiny = destiny,
            lotteryType = LotteryType.DLT,
            count = 1,
            strategy = "稳健大数据"
        )
        assertNotNull(dltRec)
        assertEquals(5, dltRec.recommendations.first().primaryNumbers.size)
        assertEquals(2, dltRec.recommendations.first().secondaryNumbers.size)
    }

    @Test
    fun testMultiBetRecommendationWorkflow() {
        val dt = LocalDateTime.of(1990, 8, 15, 8, 30)
        val destiny = BaziEngine.analyzeDestiny(dt, isMale = true)
        val engine = OfflineLotteryEngine(context = null)

        val rec1 = engine.generateRecommendation(destiny, LotteryType.SSQ, count = 1)
        assertEquals(1, rec1.recommendations.size)

        val rec5 = engine.generateRecommendation(destiny, LotteryType.SSQ, count = 5)
        assertEquals(5, rec5.recommendations.size)
        rec5.recommendations.forEach { item ->
            assertEquals(6, item.primaryNumbers.size)
            assertEquals(1, item.secondaryNumbers.size)
        }

        val rec10 = engine.generateRecommendation(destiny, LotteryType.DLT, count = 10)
        assertEquals(10, rec10.recommendations.size)
        rec10.recommendations.forEach { item ->
            assertEquals(5, item.primaryNumbers.size)
            assertEquals(2, item.secondaryNumbers.size)
        }
    }

    @Test
    fun testOfflineStatsComputation() {
        val engine = OfflineLotteryEngine(context = null)
        val stats = engine.computeStats(LotteryType.SSQ)
        assertNotNull(stats)
        assertEquals("ssq", stats.lotteryType)
        assertTrue("totalDraws should be 100 from offline seed", stats.totalDraws >= 100)
        assertTrue("stats.lastIssue should be present", stats.lastIssue.isNotBlank())
        val primary = stats.stats["primary"]
        assertNotNull(primary)
        assertEquals(33, primary?.size)
        val secondary = stats.stats["secondary"]
        assertNotNull(secondary)
        assertEquals(16, secondary?.size)
    }

    @Test
    fun testOfflineHistoryDrawsRetrieval() {
        val engine = OfflineLotteryEngine(context = null)
        
        // 1. SSQ
        val ssqHistory = engine.getHistoryDraws(LotteryType.SSQ, limit = 50)
        assertNotNull(ssqHistory)
        assertTrue("SSQ should have at least 50 history draws", ssqHistory.size >= 50)
        val firstSsq = ssqHistory.first()
        assertTrue("firstSsq.issue should be present", firstSsq.issue.isNotBlank())
        assertTrue("firstSsq.drawDate should be present", firstSsq.drawDate.isNotBlank())
        assertEquals(6, firstSsq.primaryNumbers.size)
        assertEquals(1, firstSsq.secondaryNumbers?.size)

        // 2. DLT
        val dltHistory = engine.getHistoryDraws(LotteryType.DLT, limit = 30)
        assertNotNull(dltHistory)
        assertEquals(30, dltHistory.size)
        val firstDlt = dltHistory.first()
        assertEquals(5, firstDlt.primaryNumbers.size)
        assertEquals(2, firstDlt.secondaryNumbers?.size)

        // 3. FC3D
        val fc3dHistory = engine.getHistoryDraws(LotteryType.FC3D, limit = 20)
        assertNotNull(fc3dHistory)
        assertEquals(20, fc3dHistory.size)
        val firstFc3d = fc3dHistory.first()
        assertEquals(3, firstFc3d.primaryNumbers.size)

        // 4. QXC
        val qxcHistory = engine.getHistoryDraws(LotteryType.QXC, limit = 10)
        assertNotNull(qxcHistory)
        assertEquals(10, qxcHistory.size)
        val firstQxc = qxcHistory.first()
        assertEquals(6, firstQxc.primaryNumbers.size)
        assertEquals(1, firstQxc.secondaryNumbers?.size)
    }

    @Test
    fun testDestinyPillarConsistency() {
        val dt = LocalDateTime.of(1990, 8, 15, 8, 30)
        val d1 = BaziEngine.analyzeDestiny(dt, isMale = true)
        val d2 = BaziEngine.analyzeDestiny(dt, isMale = true)

        assertEquals(d1.fourPillars.year.fullText, d2.fourPillars.year.fullText)
        assertEquals(d1.fourPillars.month.fullText, d2.fourPillars.month.fullText)
        assertEquals(d1.fourPillars.day.fullText, d2.fourPillars.day.fullText)
        assertEquals(d1.fourPillars.hour.fullText, d2.fourPillars.hour.fullText)
        assertEquals(d1.dayMaster, d2.dayMaster)
        assertEquals(d1.strength, d2.strength)
    }

    @Test
    fun testChineseLocalizationOfWuxing() {
        val dt = LocalDateTime.of(1990, 8, 15, 8, 30)
        val destiny = BaziEngine.analyzeDestiny(dt, isMale = true)

        val allowedElements = listOf("金", "木", "水", "火", "土")

        val xiYong = destiny.displayXiYongShen
        assertTrue("xiYongShen must not be empty", xiYong.isNotEmpty())
        assertTrue("All xiYongShen elements must be pure Chinese", xiYong.all { it in allowedElements })
        assertFalse("No English element tokens allowed", xiYong.any { it in listOf("wood", "fire", "earth", "metal", "water") })

        val ji = destiny.displayJiShen
        assertTrue("All jiShen elements must be pure Chinese", ji.all { it in allowedElements })

        val lacking = destiny.displayLackingElements
        assertTrue("All lackingElements must be pure Chinese", lacking.all { it in allowedElements })
    }

    @Test
    fun testLotteryStatsDualContractDeserialization() {
        // Contract 1: Backend FastAPI response format
        val backendJson = """
            {
                "lottery_type": "ssq",
                "draws_analyzed": 100,
                "latest_issue": "2026125",
                "latest_draw_date": "2026-11-01",
                "primary_zone": {
                    "zone_name": "红球区",
                    "zone_code": "primary",
                    "stats": [
                        {"number": "01", "total_count": 18, "frequency": 0.18, "hot_status": "hot", "current_omission": 2, "max_omission": 14, "avg_omission": 5.2},
                        {"number": "02", "total_count": 8, "frequency": 0.08, "hot_status": "cold", "current_omission": 11, "max_omission": 20, "avg_omission": 11.5}
                    ]
                },
                "secondary_zone": {
                    "zone_name": "蓝球区",
                    "zone_code": "secondary",
                    "stats": [
                        {"number": "01", "total_count": 10, "frequency": 0.10, "hot_status": "warm", "current_omission": 5, "max_omission": 18, "avg_omission": 9.0}
                    ]
                }
            }
        """.trimIndent()

        val parsedFromBackend = gson.fromJson(backendJson, LotteryStats::class.java)
        assertEquals("ssq", parsedFromBackend.lotteryType)
        assertEquals(100, parsedFromBackend.totalDraws)
        assertEquals("2026125", parsedFromBackend.lastIssue)
        assertTrue(parsedFromBackend.stats.containsKey("primary"))
        assertEquals(2, parsedFromBackend.stats["primary"]?.size)
        assertEquals(18, parsedFromBackend.stats["primary"]?.get("01")?.frequency)
        assertTrue(parsedFromBackend.stats["primary"]?.get("01")?.isHot == true)
        assertTrue(parsedFromBackend.stats["primary"]?.get("02")?.isCold == true)
        assertTrue(parsedFromBackend.stats.containsKey("secondary"))
        assertEquals(1, parsedFromBackend.stats["secondary"]?.size)

        // Contract 2: Client Map format
        val clientJson = """
            {
                "lottery_type": "ssq",
                "total_draws": 100,
                "last_issue": "2026125",
                "stats": {
                    "primary": {
                        "01": {"frequency": 18, "rate": 0.18, "is_hot": true, "is_cold": false, "current_omission": 2, "max_omission": 14, "avg_omission": 5.2}
                    }
                }
            }
        """.trimIndent()

        val parsedFromClient = gson.fromJson(clientJson, LotteryStats::class.java)
        assertEquals("ssq", parsedFromClient.lotteryType)
        assertEquals(100, parsedFromClient.totalDraws)
        assertEquals("2026125", parsedFromClient.lastIssue)
        assertEquals(1, parsedFromClient.stats["primary"]?.size)
        assertEquals(18, parsedFromClient.stats["primary"]?.get("01")?.frequency)
    }

    @Test
    fun testMultiBetTicketDeduplicationAndHexagramRotation() {
        val dt = LocalDateTime.of(1990, 8, 15, 8, 30)
        val destiny = BaziEngine.analyzeDestiny(dt, isMale = true)
        val engine = OfflineLotteryEngine(context = null)

        val rec10 = engine.generateRecommendation(
            destiny = destiny,
            lotteryType = LotteryType.SSQ,
            count = 10,
            strategy = "命理吉数优先",
            userName = "缘主"
        )
        assertEquals(10, rec10.recommendations.size)

        // Verify all 10 tickets are distinct (no duplicate tickets)
        val ticketSignatures = rec10.recommendations.map {
            it.primaryNumbers.joinToString(",") + "|" + it.secondaryNumbers.joinToString(",")
        }.toSet()
        assertEquals("All 10 generated bets must be distinct", 10, ticketSignatures.size)

        // Verify hexagram rotation: 10 bets should have varying hexagrams, not all identical
        val hexagrams = rec10.recommendations.mapNotNull { it.classicsEvidence?.chapter }.toSet()
        assertTrue("Hexagrams across 10 bets should rotate (expected > 1 distinct hexagrams, found ${hexagrams.size})", hexagrams.size > 1)

        // Verify copyText contains 缘主
        assertNotNull(rec10.copyText)
        assertTrue("copyText must contain 缘主", rec10.copyText!!.contains("缘主"))
    }
}
