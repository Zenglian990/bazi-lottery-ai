package com.zeng.lottery

import com.zeng.lottery.data.local.BaziEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class BaziEngineTest {

    @Test
    fun testFourPillarsCalculation_standardCase() {
        // 1990-08-15 08:30:00 -> Astronomical truth: 庚午年 甲申月 壬子日 甲辰时
        val dt = LocalDateTime.of(1990, 8, 15, 8, 30)
        val fourPillars = BaziEngine.calculateFourPillars(dt)

        assertEquals("庚", fourPillars.year.stem)
        assertEquals("午", fourPillars.year.branch)
        assertEquals("庚午", fourPillars.year.fullText)

        assertEquals("甲", fourPillars.month.stem)
        assertEquals("申", fourPillars.month.branch)
        assertEquals("甲申", fourPillars.month.fullText)

        assertEquals("壬", fourPillars.day.stem)
        assertEquals("子", fourPillars.day.branch)
        assertEquals("壬子", fourPillars.day.fullText)

        assertEquals("甲", fourPillars.hour.stem)
        assertEquals("辰", fourPillars.hour.branch)
        assertEquals("甲辰", fourPillars.hour.fullText)
    }

    @Test
    fun testMultiDecadeAstronomicalParityWithLunarPython() {
        // 1. 1983 Lunar 5-22 (Solar 1983-07-02 10:00) -> 癸亥 戊午 辛卯 癸巳
        val dt1983 = LocalDateTime.of(1983, 7, 2, 10, 0)
        val fp1983 = BaziEngine.calculateFourPillars(dt1983)
        assertEquals("癸亥", fp1983.year.fullText)
        assertEquals("戊午", fp1983.month.fullText)
        assertEquals("辛卯", fp1983.day.fullText)
        assertEquals("癸巳", fp1983.hour.fullText)

        // 2. 2026-11-06 11:00 -> 丙午 戊戌 甲申 庚午
        val dt2026 = LocalDateTime.of(2026, 11, 6, 11, 0)
        val fp2026 = BaziEngine.calculateFourPillars(dt2026)
        assertEquals("丙午", fp2026.year.fullText)
        assertEquals("戊戌", fp2026.month.fullText)
        assertEquals("甲申", fp2026.day.fullText)
        assertEquals("庚午", fp2026.hour.fullText)

        // 3. Exact LiChun Solar Term boundary on 2024-02-04 (LiChun at 16:27:09)
        val beforeLiChun = BaziEngine.calculateFourPillars(LocalDateTime.of(2024, 2, 4, 10, 0))
        assertEquals("癸卯", beforeLiChun.year.fullText)
        assertEquals("乙丑", beforeLiChun.month.fullText)
        assertEquals("戊戌", beforeLiChun.day.fullText)

        val afterLiChun = BaziEngine.calculateFourPillars(LocalDateTime.of(2024, 2, 4, 17, 0))
        assertEquals("甲辰", afterLiChun.year.fullText)
        assertEquals("丙寅", afterLiChun.month.fullText)
        assertEquals("戊戌", afterLiChun.day.fullText)
    }

    @Test
    fun testWuxingScoreSumAndDayMaster() {
        val dt = LocalDateTime.of(1990, 8, 15, 8, 30)
        val destiny = BaziEngine.analyzeDestiny(dt, isMale = true)

        val totalScore = destiny.wuxingScores.total
        // Positional matrix total is 110.0
        assertTrue("Total Wuxing score should be approximately 110.0, was $totalScore", totalScore in 105f..115f)

        assertEquals("壬", destiny.dayMaster)
        assertNotNull("strengthRatio must be populated", destiny.strengthRatio)
        assertTrue("strengthRatio must be in 0..1", destiny.strengthRatio!! in 0.01f..0.99f)
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
        assertEquals("壬子", fp.day.fullText)
        assertEquals("甲辰", fp.hour.fullText)
    }

    @Test
    fun testLunarMonth29DayClamping() {
        // In 1990, month 1 has 29 days (small month) in 6tail astronomical table
        val daysInMonth1 = BaziEngine.getDaysInLunarMonth(1990, 1, false)
        assertEquals(29, daysInMonth1)

        // Day 30 passed into lunarToSolar for a 29-day month should clamp gracefully to 29
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
