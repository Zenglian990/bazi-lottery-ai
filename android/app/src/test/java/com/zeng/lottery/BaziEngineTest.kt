package com.zeng.lottery

import com.zeng.lottery.data.local.BaziEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class BaziEngineTest {

    @Test
    fun testFourPillarsCalculation_standardCase() {
        // 1990-08-15 08:30:00 (庚午年 甲申月 庚戌日 庚辰时)
        val dt = LocalDateTime.of(1990, 8, 15, 8, 30)
        val fourPillars = BaziEngine.calculateFourPillars(dt)

        assertEquals("庚", fourPillars.year.stem)
        assertEquals("午", fourPillars.year.branch)
        assertEquals("庚午", fourPillars.year.fullText)

        assertEquals("甲", fourPillars.month.stem)
        assertEquals("申", fourPillars.month.branch)
        assertEquals("甲申", fourPillars.month.fullText)

        assertEquals("庚", fourPillars.day.stem)
        assertEquals("戌", fourPillars.day.branch)
        assertEquals("庚戌", fourPillars.day.fullText)

        assertEquals("庚", fourPillars.hour.stem)
        assertEquals("辰", fourPillars.hour.branch)
        assertEquals("庚辰", fourPillars.hour.fullText)
    }

    @Test
    fun testWuxingScoreSumAndDayMaster() {
        val dt = LocalDateTime.of(1990, 8, 15, 8, 30)
        val destiny = BaziEngine.analyzeDestiny(dt, isMale = true)

        val totalScore = destiny.wuxingScores.total
        // Positional matrix total is 110.0
        assertTrue("Total Wuxing score should be approximately 110.0, was $totalScore", totalScore in 105f..115f)

        assertEquals("庚", destiny.dayMaster)
        assertTrue(destiny.strength == "身强" || destiny.strength == "身弱")
        assertTrue("Xi Yong Shen should not be empty", destiny.xiYongShen.isNotEmpty())
        assertTrue("Ji Shen should not be empty", destiny.jiShen.isNotEmpty())
    }

    @Test
    fun testHetuTailMapping() {
        // 1, 6 -> Water (水)
        assertEquals("water", BaziEngine.getHetuElement(1))
        assertEquals("water", BaziEngine.getHetuElement(6))
        assertEquals("water", BaziEngine.getHetuElement(16))
        assertEquals("水", BaziEngine.getHetuElementZh(26))

        // 2, 7 -> Fire (火)
        assertEquals("fire", BaziEngine.getHetuElement(2))
        assertEquals("fire", BaziEngine.getHetuElement(7))
        assertEquals("火", BaziEngine.getHetuElementZh(17))

        // 3, 8 -> Wood (木)
        assertEquals("wood", BaziEngine.getHetuElement(3))
        assertEquals("wood", BaziEngine.getHetuElement(8))
        assertEquals("木", BaziEngine.getHetuElementZh(33))

        // 4, 9 -> Metal (金)
        assertEquals("metal", BaziEngine.getHetuElement(4))
        assertEquals("metal", BaziEngine.getHetuElement(9))
        assertEquals("金", BaziEngine.getHetuElementZh(24))

        // 5, 0 -> Earth (土)
        assertEquals("earth", BaziEngine.getHetuElement(5))
        assertEquals("earth", BaziEngine.getHetuElement(10))
        assertEquals("土", BaziEngine.getHetuElementZh(30))
    }

    @Test
    fun testLunarToSolarAndBackRoundtrip() {
        // 1990 Lunar 6-25 corresponds to 1990-08-15
        val solar = BaziEngine.lunarToSolar(1990, 6, 25, false, 8, 30)
        assertEquals(1990, solar.year)
        assertEquals(8, solar.monthValue)
        assertEquals(15, solar.dayOfMonth)

        val lunar = BaziEngine.solarToLunar(solar)
        assertEquals(1990, lunar.year)
        assertEquals(6, lunar.month)
        assertEquals(25, lunar.day)
        assertEquals("庚午", lunar.ganzhiYear)
        assertEquals("辰时", lunar.shichenZh)
    }

    @Test
    fun testCalculateFourPillarsFromLunar() {
        val lunarDate = com.zeng.lottery.data.model.LunarDate(year = 1990, month = 6, isLeap = false, day = 25, hour = 8, minute = 30)
        val fp = BaziEngine.calculateFourPillarsFromLunar(lunarDate)
        assertEquals("庚午", fp.year.fullText)
        assertEquals("甲申", fp.month.fullText)
        assertEquals("庚戌", fp.day.fullText)
        assertEquals("庚辰", fp.hour.fullText)
    }

    @Test
    fun testLunarMonth29DayClamping() {
        // In 1990, month 1 has 29 days (small month)
        val daysInMonth1 = BaziEngine.getDaysInLunarMonth(1990, 1, false)
        assertEquals(29, daysInMonth1)

        // Day 30 passed into lunarToSolar should clamp gracefully to 29
        val solarClamped = BaziEngine.lunarToSolar(1990, 1, 30, false)
        val solar29 = BaziEngine.lunarToSolar(1990, 1, 29, false)
        assertEquals(solar29, solarClamped)
    }

    @Test
    fun testLeapMonthHandling() {
        // 1990 has leap month 5
        val leapMonth1990 = BaziEngine.getLeapMonth(1990)
        assertEquals(5, leapMonth1990)

        // Solar date for normal month 5 vs leap month 5 should differ
        val solarNormal = BaziEngine.lunarToSolar(1990, 5, 1, false)
        val solarLeap = BaziEngine.lunarToSolar(1990, 5, 1, true)
        assertTrue(solarLeap.isAfter(solarNormal))
    }
}
