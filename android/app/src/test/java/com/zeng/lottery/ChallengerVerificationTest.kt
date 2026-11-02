package com.zeng.lottery

import com.google.gson.Gson
import com.zeng.lottery.data.local.OfflineLotteryEngine
import com.zeng.lottery.data.model.DrawItem
import com.zeng.lottery.data.model.LotteryStats
import com.zeng.lottery.data.model.LotteryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Empirical verification suite by Challenger 2.
 * Strictly verifies Offline 100-draw loading, FC3D null secondary_numbers,
 * and dual-contract deserialization.
 */
class ChallengerVerificationTest {

    private val gson = Gson()

    @Test
    fun testOffline100DrawsLoadedForAllFourLotteryTypes() {
        val engine = OfflineLotteryEngine(context = null)

        val types = listOf(
            LotteryType.SSQ,
            LotteryType.DLT,
            LotteryType.FC3D,
            LotteryType.QXC
        )

        for (type in types) {
            val stats = engine.computeStats(type)
            assertNotNull("Stats must not be null for ${type.code}", stats)
            assertEquals("Stats lotteryType must match", type.code, stats.lotteryType)
            assertEquals(
                "Offline lottery ${type.code} must load exactly 100 draws",
                100,
                stats.totalDraws
            )
            assertTrue(
                "Last issue for ${type.code} must not be blank",
                stats.lastIssue.isNotBlank()
            )
            assertTrue(
                "Primary stats for ${type.code} must not be empty",
                stats.stats.containsKey("primary") && stats.stats["primary"]!!.isNotEmpty()
            )

            // Validate expected ball counts in primary stats
            val primaryCount = stats.stats["primary"]!!.size
            val expectedPrimaryCount = type.primaryRange.last - type.primaryRange.first + 1
            assertEquals(
                "Primary numbers range size should match for ${type.code}",
                expectedPrimaryCount,
                primaryCount
            )

            // Secondary stats check if applicable
            if (type.secondaryRange != null) {
                assertTrue(
                    "Secondary stats for ${type.code} must exist",
                    stats.stats.containsKey("secondary") && stats.stats["secondary"]!!.isNotEmpty()
                )
                val secondaryCount = stats.stats["secondary"]!!.size
                val expectedSecondaryCount = type.secondaryRange!!.last - type.secondaryRange!!.first + 1
                assertEquals(
                    "Secondary numbers range size should match for ${type.code}",
                    expectedSecondaryCount,
                    secondaryCount
                )
            } else {
                assertFalse(
                    "Secondary stats for ${type.code} must NOT exist when secondaryRange is null",
                    stats.stats.containsKey("secondary")
                )
            }
        }
    }

    @Test
    fun testFc3dNullableSecondaryNumbersParsesWithoutNpe() {
        // Test parsing JSON directly with secondary_numbers: null
        val fc3dItemJson = """
            {
                "issue": "2026291",
                "draw_date": "2026-11-01",
                "primary_numbers": ["0", "8", "6"],
                "secondary_numbers": null,
                "sales_amount": 99205938,
                "pool_amount": 0
            }
        """.trimIndent()

        val item = gson.fromJson(fc3dItemJson, DrawItem::class.java)
        assertNotNull(item)
        assertEquals("2026291", item.issue)
        assertEquals(listOf("0", "8", "6"), item.primaryNumbers)
        assertNull("secondary_numbers must be null in parsed DrawItem", item.secondaryNumbers)

        // Test running OfflineLotteryEngine on FC3D seed data (all 100 draws have secondary_numbers: null)
        val engine = OfflineLotteryEngine(context = null)
        val fc3dStats = engine.computeStats(LotteryType.FC3D)
        assertEquals(100, fc3dStats.totalDraws)
        assertEquals("2026291", fc3dStats.lastIssue)
        assertEquals(10, fc3dStats.stats["primary"]?.size) // digits 0..9
        assertNull(fc3dStats.stats["secondary"])
    }

    @Test
    fun testDualContractDeserializationFastApiVsClient() {
        // 1. Raw Cloud Run FastAPI payload (draws_analyzed, primary_zone.stats)
        val fastApiPayload = """
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

        // 2. Legacy Android client JSON payload (total_draws, stats map)
        val legacyClientPayload = """
            {
                "lottery_type": "ssq",
                "total_draws": 100,
                "last_issue": "2026125",
                "stats": {
                    "primary": {
                        "01": {"frequency": 18, "rate": 0.18, "is_hot": true, "is_cold": false, "current_omission": 2, "max_omission": 14, "avg_omission": 5.2},
                        "02": {"frequency": 8, "rate": 0.08, "is_hot": false, "is_cold": true, "current_omission": 11, "max_omission": 20, "avg_omission": 11.5}
                    },
                    "secondary": {
                        "01": {"frequency": 10, "rate": 0.10, "is_hot": false, "is_cold": false, "current_omission": 5, "max_omission": 18, "avg_omission": 9.0}
                    }
                }
            }
        """.trimIndent()

        val objFastApi = gson.fromJson(fastApiPayload, LotteryStats::class.java)
        val objLegacy = gson.fromJson(legacyClientPayload, LotteryStats::class.java)

        // Assert basic properties
        assertEquals("ssq", objFastApi.lotteryType)
        assertEquals("ssq", objLegacy.lotteryType)
        assertEquals(100, objFastApi.totalDraws)
        assertEquals(100, objLegacy.totalDraws)
        assertEquals("2026125", objFastApi.lastIssue)
        assertEquals("2026125", objLegacy.lastIssue)

        // Assert non-empty stats["primary"]
        assertTrue(objFastApi.stats.containsKey("primary"))
        assertTrue(objLegacy.stats.containsKey("primary"))
        assertEquals(2, objFastApi.stats["primary"]!!.size)
        assertEquals(2, objLegacy.stats["primary"]!!.size)

        // Assert identical contents between the two representations
        val priFastApi = objFastApi.stats["primary"]!!
        val priLegacy = objLegacy.stats["primary"]!!

        assertEquals(priLegacy["01"]!!.frequency, priFastApi["01"]!!.frequency)
        assertEquals(priLegacy["01"]!!.rate, priFastApi["01"]!!.rate, 0.001f)
        assertEquals(priLegacy["01"]!!.isHot, priFastApi["01"]!!.isHot)
        assertEquals(priLegacy["01"]!!.isCold, priFastApi["01"]!!.isCold)
        assertEquals(priLegacy["01"]!!.currentOmission, priFastApi["01"]!!.currentOmission)
        assertEquals(priLegacy["01"]!!.maxOmission, priFastApi["01"]!!.maxOmission)
        assertEquals(priLegacy["01"]!!.avgOmission, priFastApi["01"]!!.avgOmission, 0.001f)

        assertEquals(priLegacy["02"]!!.frequency, priFastApi["02"]!!.frequency)
        assertEquals(priLegacy["02"]!!.rate, priFastApi["02"]!!.rate, 0.001f)
        assertEquals(priLegacy["02"]!!.isHot, priFastApi["02"]!!.isHot)
        assertEquals(priLegacy["02"]!!.isCold, priFastApi["02"]!!.isCold)
        assertEquals(priLegacy["02"]!!.currentOmission, priFastApi["02"]!!.currentOmission)

        // Secondary assertions
        assertTrue(objFastApi.stats.containsKey("secondary"))
        assertTrue(objLegacy.stats.containsKey("secondary"))
        val secFastApi = objFastApi.stats["secondary"]!!
        val secLegacy = objLegacy.stats["secondary"]!!

        assertEquals(secLegacy["01"]!!.frequency, secFastApi["01"]!!.frequency)
        assertEquals(secLegacy["01"]!!.rate, secFastApi["01"]!!.rate, 0.001f)
        assertEquals(secLegacy["01"]!!.currentOmission, secFastApi["01"]!!.currentOmission)
    }
}
