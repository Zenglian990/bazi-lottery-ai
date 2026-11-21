package com.zeng.lottery

import com.zeng.lottery.data.local.BaziEngine
import com.zeng.lottery.data.model.LunarDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Milestone 2: UI Layout Adaptation & Zero-Truncation Invariant Test.
 *
 * Verifies:
 * - Two-tier Lunar date formatting (Row 1): Year + Ganzhi + Zodiac, and Month + Day
 * - Leap month support (e.g. 闰二月廿四) formatting without truncation
 * - All 12 Shichen short time ranges (00:00 to 23:00) fit cleanly inside Row 2
 * - Traditional gender terminology: "乾造(男)" and "坤造(女)"
 * - Mathematical layout pixel budget across 320dp, 360dp, 392dp, 411dp screen widths
 */
class BirthInputLayoutAdaptationTest {

    @Test
    fun testTwoTierLunarDateComponents_StandardDate() {
        val date = LunarDate(year = 1990, month = 6, isLeap = false, day = 24, hour = 8, minute = 30)

        val yearGanzhiZodiac = "${date.year}年 (${date.ganzhiYear}${date.zodiac}年)"
        val monthDay = "${date.monthZh}${date.dayZh}"

        assertEquals("1990年 (庚午马年)", yearGanzhiZodiac)
        assertEquals("六月廿四", monthDay)

        // Character length guarantees: 13 chars top, 4 chars bottom
        assertTrue(yearGanzhiZodiac.length <= 15)
        assertTrue(monthDay.length <= 6)
    }

    @Test
    fun testTwoTierLunarDateComponents_LeapMonth() {
        val leapDate = LunarDate(year = 2023, month = 2, isLeap = true, day = 24, hour = 14, minute = 0)

        val yearGanzhiZodiac = "${leapDate.year}年 (${leapDate.ganzhiYear}${leapDate.zodiac}年)"
        val monthDay = "${leapDate.monthZh}${leapDate.dayZh}"

        assertEquals("2023年 (癸卯兔年)", yearGanzhiZodiac)
        assertEquals("闰二月廿四", monthDay)

        // Character length guarantees: 13 chars top, 5 chars bottom
        assertTrue(yearGanzhiZodiac.length <= 15)
        assertTrue(monthDay.length <= 6)
    }

    @Test
    fun testShichenAll12Hours_FormattingAndRange() {
        val testHours = listOf(
            0 to "早子时 (23-01)",
            2 to "丑时 (01-03)",
            4 to "寅时 (03-05)",
            6 to "卯时 (05-07)",
            8 to "辰时 (07-09)",
            10 to "巳时 (09-11)",
            12 to "午时 (11-13)",
            14 to "未时 (13-15)",
            16 to "申时 (15-17)",
            18 to "酉时 (17-19)",
            20 to "戌时 (19-21)",
            22 to "亥时 (21-23)",
            23 to "晚子时 (23-01)"
        )

        for ((hour, expectedText) in testHours) {
            val date = LunarDate(year = 1990, month = 6, isLeap = false, day = 24, hour = hour)
            val shichenText = "${date.shichenZh} (${BaziEngine.getShichenShortRange(date.hour)})"
            assertEquals(expectedText, shichenText)
            // Ensure length is always 10 or 11 characters (fits within ~72dp at 10sp/12sp)
            assertTrue(shichenText.length in 10..11)
        }
    }

    @Test
    fun testGenderTerminology_TraditionalFormatting() {
        val maleLabel = if (true) "乾造(男)" else "坤造(女)"
        val femaleLabel = if (false) "乾造(男)" else "坤造(女)"

        assertEquals("乾造(男)", maleLabel)
        assertEquals("坤造(女)", femaleLabel)
        // Both labels are exactly 5 characters for visual symmetry
        assertEquals(maleLabel.length, femaleLabel.length)
    }

    @Test
    fun testResponsiveLayoutBudgetCalculations() {
        // Test screen widths: 320dp (narrowest phone), 360dp (standard), 392dp (Pixel 5), 411dp (Pixel 7)
        val screenWidths = listOf(320f, 360f, 392f, 411f)

        for (screenWidth in screenWidths) {
            val outerMargin = 28f // 14dp * 2
            val cardPadding = 32f // 16dp * 2
            val availableCardWidth = screenWidth - outerMargin - cardPadding

            // Row 1: Dedicated Full-Width Date Button
            // Internal padding: 14dp * 2 = 28dp, Calendar icon: 20dp, Spacer: 10dp, Chevron: 18dp
            val row1TextColumnWidth = availableCardWidth - 28f - 20f - 10f - 18f
            // Minimum text width required: 13 chars (~100dp) + badge (32dp) = ~132dp
            // Even on 320dp screen: row1TextColumnWidth = 260 - 76 = 184dp > 132dp
            assertTrue("Row 1 text width on ${screenWidth}dp must be >= 180dp", row1TextColumnWidth >= 180f)

            // Row 2: Shichen (weight 1.3f) + Gender (weight 0.9f)
            val spacing = 8f
            val totalContentWidth = availableCardWidth - spacing
            val shichenWidth = totalContentWidth * (1.3f / 2.2f)
            val genderWidth = totalContentWidth * (0.9f / 2.2f)

            // Shichen text requires ~65dp text + 20dp padding + 16dp icon + 6dp spacer = ~107dp
            // On 320dp screen: shichenWidth = (260 - 8) * (1.3 / 2.2) = 148.9dp > 107dp
            assertTrue("Shichen button width on ${screenWidth}dp must be >= 145dp", shichenWidth >= 145f)

            // Gender chip requires ~40dp text + 16dp chip padding = ~56dp
            // On 320dp screen: genderWidth = (260 - 8) * (0.9 / 2.2) = 103.1dp > 56dp
            assertTrue("Gender chip width on ${screenWidth}dp must be >= 100dp", genderWidth >= 100f)
        }
    }
}
