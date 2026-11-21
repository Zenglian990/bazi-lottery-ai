package com.zeng.lottery

import com.zeng.lottery.data.local.BaziEngine
import com.zeng.lottery.data.local.OfflineLotteryEngine
import com.zeng.lottery.data.model.LotteryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class LotteryRuleTest {

    private val offlineEngine = OfflineLotteryEngine(context = null)
    private val sampleDestiny = BaziEngine.analyzeDestiny(LocalDateTime.of(1990, 8, 15, 8, 30))

    @Test
    fun testSSQRuleAndRecommendation() {
        val type = LotteryType.SSQ
        assertEquals(1..33, type.primaryRange)
        assertEquals(6, type.primaryCount)
        assertEquals(1..16, type.secondaryRange)
        assertEquals(1, type.secondaryCount)

        val res = offlineEngine.generateRecommendation(sampleDestiny, type, count = 1)
        val rec = res.recommendations.first()

        assertEquals(6, rec.primaryNumbers.size)
        assertEquals(6, rec.primaryNumbers.distinct().size)
        rec.primaryNumbers.forEach { numStr ->
            val num = numStr.toInt()
            assertTrue("SSQ red ball $num must be in 1..33", num in 1..33)
        }

        assertEquals(1, rec.secondaryNumbers.size)
        val blueNum = rec.secondaryNumbers.first().toInt()
        assertTrue("SSQ blue ball $blueNum must be in 1..16", blueNum in 1..16)
    }

    @Test
    fun testDLTRuleAndRecommendation() {
        val type = LotteryType.DLT
        assertEquals(1..35, type.primaryRange)
        assertEquals(5, type.primaryCount)
        assertEquals(1..12, type.secondaryRange)
        assertEquals(2, type.secondaryCount)

        val res = offlineEngine.generateRecommendation(sampleDestiny, type, count = 1)
        val rec = res.recommendations.first()

        assertEquals(5, rec.primaryNumbers.size)
        assertEquals(5, rec.primaryNumbers.distinct().size)
        rec.primaryNumbers.forEach { numStr ->
            val num = numStr.toInt()
            assertTrue("DLT front ball $num must be in 1..35", num in 1..35)
        }

        assertEquals(2, rec.secondaryNumbers.size)
        assertEquals(2, rec.secondaryNumbers.distinct().size)
        rec.secondaryNumbers.forEach { numStr ->
            val num = numStr.toInt()
            assertTrue("DLT back ball $num must be in 1..12", num in 1..12)
        }
    }

    @Test
    fun testFC3DRuleAndRecommendation() {
        val type = LotteryType.FC3D
        assertEquals(0..9, type.primaryRange)
        assertEquals(3, type.primaryCount)

        val res = offlineEngine.generateRecommendation(sampleDestiny, type, count = 1)
        val rec = res.recommendations.first()

        assertEquals(3, rec.primaryNumbers.size)
        rec.primaryNumbers.forEach { numStr ->
            val num = numStr.toInt()
            assertTrue("FC3D digit $num must be in 0..9", num in 0..9)
        }
    }

    @Test
    fun testQXCRuleAndRecommendation() {
        val type = LotteryType.QXC
        assertEquals(0..9, type.primaryRange)
        assertEquals(6, type.primaryCount)
        assertEquals(0..14, type.secondaryRange)
        assertEquals(1, type.secondaryCount)

        val res = offlineEngine.generateRecommendation(sampleDestiny, type, count = 1)
        val rec = res.recommendations.first()

        assertEquals(6, rec.primaryNumbers.size)
        rec.primaryNumbers.forEach { numStr ->
            val num = numStr.toInt()
            assertTrue("QXC front digit $num must be in 0..9", num in 0..9)
        }

        assertEquals(1, rec.secondaryNumbers.size)
        val lastNum = rec.secondaryNumbers.first().toInt()
        assertTrue("QXC last digit $lastNum must be in 0..14", lastNum in 0..14)
    }

    @Test
    fun testAllStrategiesProduceValidRecommendations() {
        listOf("命理吉数优先", "稳健大数据", "冷热追号").forEach { strategy ->
            val res = offlineEngine.generateRecommendation(
                destiny = sampleDestiny,
                lotteryType = LotteryType.SSQ,
                strategy = strategy
            )
            assertNotNull("Response should not be null for $strategy", res)
            assertEquals(1, res.recommendations.size)
            assertTrue(res.recommendations.first().scoreRationale.isNotBlank())
        }
    }
}
