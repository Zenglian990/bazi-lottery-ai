package com.zeng.lottery

import com.zeng.lottery.data.local.BaziEngine
import com.zeng.lottery.data.local.OfflineLotteryEngine
import com.zeng.lottery.data.model.LotteryType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelTest {

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
    fun testOfflineStatsComputation() {
        val engine = OfflineLotteryEngine(context = null)
        val stats = engine.computeStats(LotteryType.SSQ)
        assertNotNull(stats)
        assertEquals("ssq", stats.lotteryType)
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
}
