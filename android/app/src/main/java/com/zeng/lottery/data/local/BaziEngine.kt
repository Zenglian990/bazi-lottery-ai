package com.zeng.lottery.data.local

import com.zeng.lottery.data.model.BaziDestiny
import com.zeng.lottery.data.model.FourPillars
import com.zeng.lottery.data.model.LunarDate
import com.zeng.lottery.data.model.Pillar
import com.zeng.lottery.data.model.WuxingScores
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Pure Kotlin embedded Bazi astronomical and numerological calculation engine.
 * Computes Four Pillars, 110-point Wuxing energy, Day Master strength, Xi Yong Shen,
 * and Hetu lucky number mapping with 100% offline autonomy.
 */
object BaziEngine {

    val STEMS = listOf("甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸")
    val BRANCHES = listOf("子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥")
    val ZODIAC = listOf("鼠", "牛", "虎", "兔", "龙", "蛇", "马", "羊", "猴", "鸡", "狗", "猪")

    fun getZodiac(year: Int): String {
        val offset = year - 4
        val idx = ((offset % 12) + 12) % 12
        return ZODIAC[idx]
    }

    fun getShichenShortRange(hour: Int): String {
        return when (hour) {
            23, 0 -> "23-01"
            1, 2 -> "01-03"
            3, 4 -> "03-05"
            5, 6 -> "05-07"
            7, 8 -> "07-09"
            9, 10 -> "09-11"
            11, 12 -> "11-13"
            13, 14 -> "13-15"
            15, 16 -> "15-17"
            17, 18 -> "17-19"
            19, 20 -> "19-21"
            21, 22 -> "21-23"
            else -> "09-11"
        }
    }

    fun getShichenRange(hour: Int): String {
        return when (hour) {
            23, 0 -> "23:00-01:00"
            1, 2 -> "01:00-03:00"
            3, 4 -> "03:00-05:00"
            5, 6 -> "05:00-07:00"
            7, 8 -> "07:00-09:00"
            9, 10 -> "09:00-11:00"
            11, 12 -> "11:00-13:00"
            13, 14 -> "13:00-15:00"
            15, 16 -> "15:00-17:00"
            17, 18 -> "17:00-19:00"
            19, 20 -> "19:00-21:00"
            21, 22 -> "21:00-23:00"
            else -> "09:00-11:00"
        }
    }

    val STEM_ELEMENTS = mapOf(
        "甲" to "wood", "乙" to "wood",
        "丙" to "fire", "丁" to "fire",
        "戊" to "earth", "己" to "earth",
        "庚" to "metal", "辛" to "metal",
        "壬" to "water", "癸" to "water"
    )

    val BRANCH_ELEMENTS = mapOf(
        "子" to "water", "丑" to "earth", "寅" to "wood", "卯" to "wood",
        "辰" to "earth", "巳" to "fire", "午" to "fire", "未" to "earth",
        "申" to "metal", "酉" to "metal", "戌" to "earth", "亥" to "water"
    )

    val BRANCH_HIDDEN_STEMS: Map<String, List<Pair<String, Float>>> = mapOf(
        "子" to listOf("癸" to 1.00f),
        "丑" to listOf("己" to 0.60f, "癸" to 0.30f, "辛" to 0.10f),
        "寅" to listOf("甲" to 0.60f, "丙" to 0.30f, "戊" to 0.10f),
        "卯" to listOf("乙" to 1.00f),
        "辰" to listOf("戊" to 0.60f, "乙" to 0.30f, "癸" to 0.10f),
        "巳" to listOf("丙" to 0.60f, "戊" to 0.30f, "庚" to 0.10f),
        "午" to listOf("丁" to 0.70f, "己" to 0.30f),
        "未" to listOf("己" to 0.60f, "丁" to 0.30f, "乙" to 0.10f),
        "申" to listOf("庚" to 0.60f, "壬" to 0.30f, "戊" to 0.10f),
        "酉" to listOf("辛" to 1.00f),
        "戌" to listOf("戊" to 0.60f, "辛" to 0.30f, "丁" to 0.10f),
        "亥" to listOf("壬" to 0.70f, "甲" to 0.30f)
    )

    val ELEMENT_ZH = mapOf(
        "wood" to "木",
        "fire" to "火",
        "earth" to "土",
        "metal" to "金",
        "water" to "水"
    )

    val ZH_TO_ELEMENT = mapOf(
        "木" to "wood",
        "火" to "fire",
        "土" to "earth",
        "金" to "metal",
        "水" to "water"
    )

    val MOTHER_ELEMENT = mapOf(
        "wood" to "water",
        "fire" to "wood",
        "earth" to "fire",
        "metal" to "earth",
        "water" to "metal"
    )

    val DRAIN_ELEMENT = mapOf(
        "wood" to "fire",
        "fire" to "earth",
        "earth" to "metal",
        "metal" to "water",
        "water" to "wood"
    )

    val WEALTH_ELEMENT = mapOf(
        "wood" to "earth",
        "fire" to "metal",
        "earth" to "water",
        "metal" to "wood",
        "water" to "fire"
    )

    val CONTROL_ELEMENT = mapOf(
        "wood" to "metal",
        "fire" to "water",
        "earth" to "wood",
        "metal" to "fire",
        "water" to "earth"
    )

    // Hetu tail modulo 10 mapping:
    // 1,6 -> Water; 2,7 -> Fire; 3,8 -> Wood; 4,9 -> Metal; 5,0 -> Earth
    val HETU_TAIL_TO_ELEMENT = mapOf(
        1 to "water", 6 to "water",
        2 to "fire", 7 to "fire",
        3 to "wood", 8 to "wood",
        4 to "metal", 9 to "metal",
        5 to "earth", 0 to "earth"
    )

    fun getHetuElement(number: Int): String {
        val tail = kotlin.math.abs(number) % 10
        return HETU_TAIL_TO_ELEMENT[tail] ?: "earth"
    }

    fun getHetuElementZh(number: Int): String {
        val en = getHetuElement(number)
        return ELEMENT_ZH[en] ?: "土"
    }

    /**
     * Compute Four Pillars for a given local datetime.
     */
    fun calculateFourPillars(dt: LocalDateTime): FourPillars {
        val solar = com.nlf.calendar.Solar.fromYmdHms(
            dt.year,
            dt.monthValue,
            dt.dayOfMonth,
            dt.hour,
            dt.minute,
            dt.second
        )
        val lunar = solar.lunar
        val eightChar = lunar.eightChar
        eightChar.sect = 2

        val yearStem = eightChar.yearGan
        val yearBranch = eightChar.yearZhi
        val monthStem = eightChar.monthGan
        val monthBranch = eightChar.monthZhi
        val dayStem = eightChar.dayGan
        val dayBranch = eightChar.dayZhi
        val hourStem = eightChar.timeGan
        val hourBranch = eightChar.timeZhi

        return FourPillars(
            year = Pillar(
                stem = yearStem,
                branch = yearBranch,
                element = "${ELEMENT_ZH[STEM_ELEMENTS[yearStem]] ?: "金"}${ELEMENT_ZH[BRANCH_ELEMENTS[yearBranch]] ?: "金"}",
                hideStems = BRANCH_HIDDEN_STEMS[yearBranch]?.map { it.first } ?: emptyList()
            ),
            month = Pillar(
                stem = monthStem,
                branch = monthBranch,
                element = "${ELEMENT_ZH[STEM_ELEMENTS[monthStem]] ?: "金"}${ELEMENT_ZH[BRANCH_ELEMENTS[monthBranch]] ?: "金"}",
                hideStems = BRANCH_HIDDEN_STEMS[monthBranch]?.map { it.first } ?: emptyList()
            ),
            day = Pillar(
                stem = dayStem,
                branch = dayBranch,
                element = "${ELEMENT_ZH[STEM_ELEMENTS[dayStem]] ?: "金"}${ELEMENT_ZH[BRANCH_ELEMENTS[dayBranch]] ?: "金"}",
                hideStems = BRANCH_HIDDEN_STEMS[dayBranch]?.map { it.first } ?: emptyList()
            ),
            hour = Pillar(
                stem = hourStem,
                branch = hourBranch,
                element = "${ELEMENT_ZH[STEM_ELEMENTS[hourStem]] ?: "金"}${ELEMENT_ZH[BRANCH_ELEMENTS[hourBranch]] ?: "金"}",
                hideStems = BRANCH_HIDDEN_STEMS[hourBranch]?.map { it.first } ?: emptyList()
            )
        )
    }

    /**
     * Quantify 110-point Wuxing energy distribution and diagnose Day Master strength,
     * Xi Yong Shen (喜用神), and Ji Shen (忌神).
     */
    fun analyzeDestiny(dt: LocalDateTime, isMale: Boolean = true): BaziDestiny {
        val fourPillars = calculateFourPillars(dt)

        // Positional Weighting Matrix (Sum = 110.0)
        val weights = mapOf(
            "year_stem" to 10.0f,
            "year_branch" to 10.0f,
            "month_stem" to 12.0f,
            "month_branch" to 35.0f,
            "day_stem" to 12.0f,
            "day_branch" to 15.0f,
            "hour_stem" to 8.0f,
            "hour_branch" to 8.0f
        )

        val scores = mutableMapOf(
            "metal" to 0f,
            "wood" to 0f,
            "water" to 0f,
            "fire" to 0f,
            "earth" to 0f
        )

        // 1. Stems
        STEM_ELEMENTS[fourPillars.year.stem]?.let { scores[it] = scores.getOrDefault(it, 0f) + weights["year_stem"]!! }
        STEM_ELEMENTS[fourPillars.month.stem]?.let { scores[it] = scores.getOrDefault(it, 0f) + weights["month_stem"]!! }
        STEM_ELEMENTS[fourPillars.day.stem]?.let { scores[it] = scores.getOrDefault(it, 0f) + weights["day_stem"]!! }
        STEM_ELEMENTS[fourPillars.hour.stem]?.let { scores[it] = scores.getOrDefault(it, 0f) + weights["hour_stem"]!! }

        // 2. Branches hidden stems
        fun addBranch(branch: String, weightKey: String) {
            val w = weights[weightKey] ?: 10f
            BRANCH_HIDDEN_STEMS[branch]?.forEach { (hiddenStem, ratio) ->
                val elem = STEM_ELEMENTS[hiddenStem] ?: "earth"
                scores[elem] = scores.getOrDefault(elem, 0f) + (w * ratio)
            }
        }

        addBranch(fourPillars.year.branch, "year_branch")
        addBranch(fourPillars.month.branch, "month_branch")
        addBranch(fourPillars.day.branch, "day_branch")
        addBranch(fourPillars.hour.branch, "hour_branch")

        val dayMaster = fourPillars.day.stem
        val selfElem = STEM_ELEMENTS[dayMaster] ?: "metal"
        val motherElem = MOTHER_ELEMENT[selfElem] ?: "earth"

        val peerScore = scores.getOrDefault(selfElem, 0f) + scores.getOrDefault(motherElem, 0f)
        val isStrong = peerScore >= 55.0f
        val strength = if (isStrong) "身强" else "身弱"

        val xiYongShenZh = mutableListOf<String>()
        val jiShenZh = mutableListOf<String>()

        if (isStrong) {
            // 身强喜克泄耗
            val drain = DRAIN_ELEMENT[selfElem]?.let { ELEMENT_ZH[it] }
            val control = CONTROL_ELEMENT[selfElem]?.let { ELEMENT_ZH[it] }
            val wealth = WEALTH_ELEMENT[selfElem]?.let { ELEMENT_ZH[it] }
            listOfNotNull(drain, wealth, control).forEach { if (it !in xiYongShenZh) xiYongShenZh.add(it) }

            listOfNotNull(ELEMENT_ZH[selfElem], ELEMENT_ZH[motherElem]).forEach { if (it !in jiShenZh) jiShenZh.add(it) }
        } else {
            // 身弱喜生扶
            val mother = ELEMENT_ZH[motherElem]
            val self = ELEMENT_ZH[selfElem]
            listOfNotNull(mother, self).forEach { if (it !in xiYongShenZh) xiYongShenZh.add(it) }

            val drain = DRAIN_ELEMENT[selfElem]?.let { ELEMENT_ZH[it] }
            val control = CONTROL_ELEMENT[selfElem]?.let { ELEMENT_ZH[it] }
            val wealth = WEALTH_ELEMENT[selfElem]?.let { ELEMENT_ZH[it] }
            listOfNotNull(control, wealth, drain).forEach { if (it !in jiShenZh) jiShenZh.add(it) }
        }

        // Lacking elements (< 5.0 points, aligned with backend wuxing_engine.py)
        val lackingZh = scores.filter { it.value < 5.0f }.keys.mapNotNull { ELEMENT_ZH[it] }

        val totalScore = scores.values.sum().coerceAtLeast(1f)
        val percentages = scores.mapValues { it.value / totalScore }
        val strengthRatio = ((peerScore / totalScore) * 10000f).roundToInt() / 10000f

        val solarObj = com.nlf.calendar.Solar.fromYmdHms(dt.year, dt.monthValue, dt.dayOfMonth, dt.hour, dt.minute, dt.second)
        val lunarObj = solarObj.lunar
        val yearZodiac = getZodiac(lunarObj.year)
        val shichenTitle = getShichenName(dt.hour)
        val formattedLunar = "${lunarObj.year}年 (${lunarObj.yearInGanZhi}${yearZodiac}年) 农历${lunarObj.monthInChinese}月${lunarObj.dayInChinese} $shichenTitle"

        val isYangYear = fourPillars.year.stem in listOf("甲", "丙", "戊", "庚", "壬")
        val dayunForward = if (isMale) isYangYear else !isYangYear
        val eightChar = lunarObj.eightChar
        eightChar.sect = 2
        val yun = eightChar.getYun(if (isMale) 1 else 0)
        val dayunList = yun.daYun.mapNotNull { it.ganZhi.takeIf { gz -> gz.isNotBlank() } }.take(8)

        return BaziDestiny(
            solarDatetime = dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            lunarDatetime = formattedLunar,
            fourPillars = fourPillars,
            wuxingScores = WuxingScores(
                metal = ((scores["metal"] ?: 0f) * 10f).roundToInt() / 10f,
                wood = ((scores["wood"] ?: 0f) * 10f).roundToInt() / 10f,
                water = ((scores["water"] ?: 0f) * 10f).roundToInt() / 10f,
                fire = ((scores["fire"] ?: 0f) * 10f).roundToInt() / 10f,
                earth = ((scores["earth"] ?: 0f) * 10f).roundToInt() / 10f
            ),
            wuxingPercentage = percentages,
            dayMaster = dayMaster,
            strength = strength,
            strengthRatio = strengthRatio,
            lackingElements = lackingZh,
            xiYongShen = xiYongShenZh,
            jiShen = jiShenZh,
            gender = if (isMale) "male" else "female",
            dayunForward = dayunForward,
            dayunList = dayunList
        )
    }

    val LUNAR_MONTH_NAMES = listOf("正月", "二月", "三月", "四月", "五月", "六月", "七月", "八月", "九月", "十月", "冬月", "腊月")
    val LUNAR_DAY_NAMES = listOf(
        "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
        "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
        "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
    )
    val SHICHEN_NAMES = listOf("子时", "丑时", "寅时", "卯时", "辰时", "巳时", "午时", "未时", "申时", "酉时", "戌时", "亥时")

    fun getShichenName(hour: Int): String {
        return when (hour) {
            0 -> "早子时"
            23 -> "晚子时"
            else -> {
                val idx = ((hour + 1) % 24) / 2
                SHICHEN_NAMES[idx]
            }
        }
    }

    fun getYearGanZhi(year: Int): String {
        val offset = (year - 4)
        val stemIndex = ((offset % 10) + 10) % 10
        val branchIndex = ((offset % 12) + 12) % 12
        return "${STEMS[stemIndex]}${BRANCHES[branchIndex]}"
    }

    // 101-year lunar lookup array (1930 - 2030)
    val LUNAR_INFO = intArrayOf(
        0x06566, 0x0d4a0, 0x0ea50, 0x16a95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950,
        0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557,
        0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5b0, 0x14573, 0x052b0, 0x0a9a8, 0x0e950, 0x06aa0,
        0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0,
        0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6,
        0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570,
        0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x05ac0, 0x0ab60, 0x096d5, 0x092e0,
        0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5,
        0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930,
        0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530,
        0x05aa0
    )

    val BASE_SOLAR_DATE: LocalDate = LocalDate.of(1930, 1, 30) // Lunar 1930-01-01

    fun getLeapMonth(year: Int): Int {
        if (year !in 1900..2100) return 0
        return com.nlf.calendar.LunarYear.fromYear(year).leapMonth
    }

    fun getDaysInLunarMonth(year: Int, month: Int, isLeap: Boolean): Int {
        if (year !in 1900..2100) return 30
        val mClamped = month.coerceIn(1, 12)
        val actualM = if (isLeap && getLeapMonth(year) == mClamped) -mClamped else mClamped
        return com.nlf.calendar.LunarMonth.fromYm(year, actualM)?.dayCount ?: 29
    }

    fun lunarToSolar(lunar: LunarDate): LocalDateTime {
        val ly = lunar.year.coerceIn(1930, 2030)
        val targetM = lunar.month.coerceIn(1, 12)
        val actualLeap = lunar.isLeap && getLeapMonth(ly) == targetM
        val actualM = if (actualLeap) -targetM else targetM
        val maxDays = getDaysInLunarMonth(ly, targetM, actualLeap)
        val clampedDay = lunar.day.coerceIn(1, maxDays)
        val h = lunar.hour.coerceIn(0, 23)
        val m = lunar.minute.coerceIn(0, 59)
        val lObj = com.nlf.calendar.Lunar.fromYmdHms(ly, actualM, clampedDay, h, m, 0)
        val sObj = lObj.solar
        return LocalDateTime.of(sObj.year, sObj.month, sObj.day, sObj.hour, sObj.minute, sObj.second)
    }

    fun lunarToSolar(year: Int, month: Int, day: Int, isLeap: Boolean = false, hour: Int = 12, minute: Int = 0): LocalDateTime {
        return lunarToSolar(LunarDate(year, month, isLeap, day, hour, minute))
    }

    fun solarToLunar(solarDt: LocalDateTime): LunarDate {
        val sObj = com.nlf.calendar.Solar.fromYmdHms(
            solarDt.year,
            solarDt.monthValue,
            solarDt.dayOfMonth,
            solarDt.hour,
            solarDt.minute,
            solarDt.second
        )
        val lObj = sObj.lunar
        val rawMonth = lObj.month
        val isLeap = rawMonth < 0
        val absMonth = kotlin.math.abs(rawMonth)
        return LunarDate(
            year = lObj.year,
            month = absMonth,
            isLeap = isLeap,
            day = lObj.day,
            hour = solarDt.hour,
            minute = solarDt.minute
        )
    }

    fun calculateFourPillarsFromLunar(lunar: LunarDate): FourPillars {
        val solarDt = lunarToSolar(lunar)
        return calculateFourPillars(solarDt)
    }

    fun analyzeDestinyFromLunar(lunar: LunarDate, isMale: Boolean = true, userName: String = "缘主"): BaziDestiny {
        val solarDt = lunarToSolar(lunar)
        val destiny = analyzeDestiny(solarDt, isMale)
        val formattedLunarText = "${lunar.year}年 (${lunar.ganzhiYear}${getZodiac(lunar.year)}年) 农历${lunar.monthZh}${lunar.dayZh} ${lunar.shichenZh}"
        return destiny.copy(
            solarDatetime = solarDt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            lunarDatetime = formattedLunarText,
            userName = userName
        )
    }
}
