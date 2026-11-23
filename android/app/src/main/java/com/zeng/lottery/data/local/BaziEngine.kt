package com.zeng.lottery.data.local

import com.zeng.lottery.data.model.BaziDestiny
import com.zeng.lottery.data.model.DayunDetail
import com.zeng.lottery.data.model.FourPillars
import com.zeng.lottery.data.model.LunarDate
import com.zeng.lottery.data.model.Pillar
import com.zeng.lottery.data.model.TodayFortuneResponse
import com.zeng.lottery.data.model.TodayResonance
import com.zeng.lottery.data.model.WuxingScores
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * 纯 Kotlin 端侧子平八字天文历法与五行数理计算引擎（与后端 lunar_python 同源 6tail 算法）。
 * 支持真太阳时校正（经度差 + 天文均时差）、110分制五行量化、细化身强弱等级、8步大运详表与今日黄历共振。
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

    fun getZodiacFromBranch(branch: String): String {
        val idx = BRANCHES.indexOf(branch)
        return if (idx in ZODIAC.indices) ZODIAC[idx] else "鼠"
    }

    fun getShichenShortRange(hour: Int): String {
        return when (hour) {
            0 -> "00-01"
            23 -> "23-24"
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
            0 -> "00:00-01:00"
            23 -> "23:00-24:00"
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

    // 河图洛书尾数五行：1/6水、2/7火、3/8木、4/9金、5/0土
    val HETU_TAIL_TO_ELEMENT = mapOf(
        1 to "water", 6 to "water",
        2 to "fire", 7 to "fire",
        3 to "wood", 8 to "wood",
        4 to "metal", 9 to "metal",
        5 to "earth", 0 to "earth"
    )

    // 常用城市出生地经度预设（用于真太阳时换算）
    val CITY_LONGITUDE_PRESETS: List<Pair<String, Double>> = listOf(
        "标准东八区 (120.0°E)" to 120.0,
        "北京 (116.4°E)" to 116.4,
        "上海 (121.5°E)" to 121.5,
        "广州/深圳 (113.3°E)" to 113.3,
        "成都/重庆 (104.1°E)" to 104.1,
        "武汉/长沙 (114.3°E)" to 114.3,
        "西安 (108.9°E)" to 108.9,
        "杭州/南京 (120.2°E)" to 120.2,
        "昆明 (102.7°E)" to 102.7,
        "乌鲁木齐 (87.6°E)" to 87.6,
        "哈尔滨 (126.6°E)" to 126.6
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
     * 计算天文均时差（Equation of Time，单位：分钟），与后端 bazi_engine.py 算法同源。
     */
    fun computeEquationOfTimeMinutes(dt: LocalDateTime): Double {
        val dayOfYear = dt.dayOfYear.toDouble()
        val b = 2.0 * PI * (dayOfYear - 81.0) / 365.0
        return 9.87 * sin(2.0 * b) - 7.53 * cos(b) - 1.5 * sin(b)
    }

    /**
     * 真太阳时换算：当经度偏离标准东八区 120.0°E 或显式开启均时差时，叠加经度差与天文均时差。
     */
    fun adjustTrueSolarTime(dt: LocalDateTime, longitude: Double = 120.0, applyEot: Boolean = false): LocalDateTime {
        val lonDeltaMin = (longitude - 120.0) * 4.0
        val useEot = applyEot || kotlin.math.abs(longitude - 120.0) > 1e-6
        val eotMin = if (useEot) computeEquationOfTimeMinutes(dt) else 0.0
        val totalSec = ((lonDeltaMin + eotMin) * 60.0).roundToLong()
        return if (totalSec == 0L) dt else dt.plusSeconds(totalSec)
    }

    /**
     * 根据公历时刻计算精确四柱八字（含可选真太阳时校正）。
     */
    fun calculateFourPillars(dt: LocalDateTime, longitude: Double = 120.0): FourPillars {
        val calcDt = adjustTrueSolarTime(dt, longitude = longitude)
        val solar = com.nlf.calendar.Solar.fromYmdHms(
            calcDt.year,
            calcDt.monthValue,
            calcDt.dayOfMonth,
            calcDt.hour,
            calcDt.minute,
            calcDt.second
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
     * 量化 110 分制五行能量，按原局弱强动态排列喜用神，并输出细化身强弱等级与 8 步大运详表。
     */
    fun analyzeDestiny(dt: LocalDateTime, isMale: Boolean = true, longitude: Double = 120.0): BaziDestiny {
        val calcDt = adjustTrueSolarTime(dt, longitude = longitude)
        val fourPillars = calculateFourPillars(dt, longitude = longitude)

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

        STEM_ELEMENTS[fourPillars.year.stem]?.let { scores[it] = scores.getOrDefault(it, 0f) + weights["year_stem"]!! }
        STEM_ELEMENTS[fourPillars.month.stem]?.let { scores[it] = scores.getOrDefault(it, 0f) + weights["month_stem"]!! }
        STEM_ELEMENTS[fourPillars.day.stem]?.let { scores[it] = scores.getOrDefault(it, 0f) + weights["day_stem"]!! }
        STEM_ELEMENTS[fourPillars.hour.stem]?.let { scores[it] = scores.getOrDefault(it, 0f) + weights["hour_stem"]!! }

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

        val totalScore = scores.values.sum().coerceAtLeast(1f)
        val peerScore = scores.getOrDefault(selfElem, 0f) + scores.getOrDefault(motherElem, 0f)
        val strengthRatio = ((peerScore / totalScore) * 10000f).roundToInt() / 10000f
        val isStrong = strengthRatio >= 0.50f
        val strength = if (isStrong) "身强" else "身弱"
        val strengthLevel = when {
            strengthRatio >= 0.65f -> "极旺"
            strengthRatio >= 0.54f -> "偏强"
            strengthRatio >= 0.46f -> "中和"
            strengthRatio >= 0.35f -> "偏弱"
            else -> "极弱"
        }

        // 与后端 wuxing_engine.py 对齐：喜用神按原局得分从低到高排序（最急需补益的排第一）
        val xiCandidatesEn = if (isStrong) {
            listOfNotNull(DRAIN_ELEMENT[selfElem], WEALTH_ELEMENT[selfElem], CONTROL_ELEMENT[selfElem]).distinct()
        } else {
            listOfNotNull(motherElem, selfElem).distinct()
        }.sortedBy { scores.getOrDefault(it, 0f) }

        val jiCandidatesEn = if (isStrong) {
            listOf(selfElem, motherElem).distinct()
        } else {
            listOfNotNull(CONTROL_ELEMENT[selfElem], WEALTH_ELEMENT[selfElem], DRAIN_ELEMENT[selfElem]).distinct()
        }

        val xiYongShenZh = xiCandidatesEn.mapNotNull { ELEMENT_ZH[it] }
        val jiShenZh = jiCandidatesEn.mapNotNull { ELEMENT_ZH[it] }
        val lackingZh = scores.filter { it.value < 5.0f }.keys.mapNotNull { ELEMENT_ZH[it] }
        val percentages = scores.mapValues { it.value / totalScore }

        val solarObj = com.nlf.calendar.Solar.fromYmdHms(
            calcDt.year, calcDt.monthValue, calcDt.dayOfMonth, calcDt.hour, calcDt.minute, calcDt.second
        )
        val lunarObj = solarObj.lunar
        // 统一使用八字立春年干支及对应地支生肖，防止立春前标题干支与生肖矛盾
        val baziYearGz = fourPillars.year.fullText
        val yearZodiac = getZodiacFromBranch(fourPillars.year.branch)
        val shichenTitle = getShichenName(calcDt.hour)
        val formattedLunar = "${lunarObj.year}年 (${baziYearGz}${yearZodiac}年) 农历${lunarObj.monthInChinese}月${lunarObj.dayInChinese} $shichenTitle"

        val isYangYear = fourPillars.year.stem in listOf("甲", "丙", "戊", "庚", "壬")
        val dayunForward = if (isMale) isYangYear else !isYangYear
        val eightChar = lunarObj.eightChar
        eightChar.sect = 2
        val yun = eightChar.getYun(if (isMale) 1 else 0)
        val dayunStartAge = runCatching { yun.startYear }.getOrNull()
        val rawDaYun = runCatching { yun.daYun }.getOrDefault(emptyArray())
        val dayunDetails = rawDaYun
            .filter { it.ganZhi.isNotBlank() }
            .take(8)
            .map { dy ->
                DayunDetail(
                    ganzhi = dy.ganZhi,
                    startAge = dy.startAge,
                    startYear = dy.startYear,
                    endYear = dy.endYear
                )
            }
        val dayunList = dayunDetails.map { it.ganzhi }

        return BaziDestiny(
            solarDatetime = calcDt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
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
            strengthLevel = strengthLevel,
            strengthRatio = strengthRatio,
            lackingElements = lackingZh,
            xiYongShen = xiYongShenZh,
            jiShen = jiShenZh,
            shichen = shichenTitle,
            gender = if (isMale) "male" else "female",
            dayunForward = dayunForward,
            dayunStartAge = dayunStartAge,
            dayunList = dayunList,
            dayunDetails = dayunDetails
        )
    }

    /**
     * 离线端侧计算今日流日黄历（财神/喜神方位、宜忌、冲煞、纳音与个人流日共振），与后端 /api/bazi/today-fortune 同源。
     */
    fun computeTodayFortuneOffline(
        destiny: BaziDestiny?,
        userName: String = "缘主",
        now: LocalDateTime = LocalDateTime.now()
    ): TodayFortuneResponse {
        val solarNow = com.nlf.calendar.Solar.fromYmdHms(
            now.year, now.monthValue, now.dayOfMonth, now.hour, now.minute, now.second
        )
        val lunarNow = solarNow.lunar
        val ganzhiYear = lunarNow.yearInGanZhiExact
        val ganzhiMonth = lunarNow.monthInGanZhiExact
        val ganzhiDay = lunarNow.dayInGanZhiExact2
        val ganzhiHour = lunarNow.timeInGanZhi

        val dayStem = ganzhiDay.take(1)
        val dayBranch = ganzhiDay.drop(1).take(1)
        val stemElem = STEM_ELEMENTS[dayStem] ?: "earth"
        val branchElem = BRANCH_ELEMENTS[dayBranch] ?: "earth"
        val stemZh = ELEMENT_ZH[stemElem] ?: "土"
        val branchZh = ELEMENT_ZH[branchElem] ?: "土"

        val caiPos = runCatching { lunarNow.dayPositionCaiDesc }.getOrDefault("正东")
        val xiPos = runCatching { lunarNow.dayPositionXiDesc }.getOrDefault("东北")
        val dayYi = runCatching { lunarNow.dayYi.take(8) }.getOrDefault(emptyList())
        val dayJi = runCatching { lunarNow.dayJi.take(8) }.getOrDefault(emptyList())
        val chongSha = runCatching { lunarNow.dayChongDesc }.getOrDefault("")
        val dayNayin = runCatching { lunarNow.dayNaYin }.getOrDefault("")

        val resonance = destiny?.let { d ->
            val xiSet = d.xiYongShen.map { ZH_TO_ELEMENT[it] ?: it.lowercase() }.toSet()
            val jiSet = d.jiShen.map { ZH_TO_ELEMENT[it] ?: it.lowercase() }.toSet()
            val todayElems = listOf(stemElem, branchElem).distinct()
            val directHits = todayElems.filter { it in xiSet }
            val helperHits = if (directHits.isEmpty()) {
                todayElems.filter { e -> (e !in jiSet) && xiSet.any { xi -> MOTHER_ELEMENT[xi] == e } }
            } else emptyList()

            val (harmElems, level, multiplier, score) = when {
                directHits.isNotEmpty() -> Quadruple(directHits, "大吉", 1.20f, 0.88f)
                helperHits.isNotEmpty() -> Quadruple(helperHits, "吉", 1.10f, 0.78f)
                else -> Quadruple(emptyList<String>(), "平吉", 1.00f, 0.65f)
            }
            val harmZh = harmElems.mapNotNull { ELEMENT_ZH[it] }
            val nameTag = if (userName.isBlank()) "缘主" else "【${userName.trim()}】"
            val desc = when (level) {
                "大吉" -> "今日流日$ganzhiDay($stemZh$branchZh)，直合${nameTag}喜用神【${harmZh.joinToString("/")}】气运共振(+20%)，灵感通透，宜从容布局。"
                "吉" -> "今日流日$ganzhiDay($stemZh$branchZh)，流日【${harmZh.joinToString("/")}】温和生助${nameTag}喜用神(+10%)，气运顺畅，稳中见喜。"
                else -> "今日流日$ganzhiDay($stemZh$branchZh)气运平稳中和，未直接引动喜用神，宜依原局五行数理理性选号、量力而行。"
            }

            TodayResonance(
                todayGanzhi = "$ganzhiDay ($stemZh$branchZh)",
                userDayMaster = d.dayMaster,
                userXiYong = d.xiYongShen,
                resonanceScore = score,
                resonanceLevel = level,
                resonanceMultiplier = multiplier,
                harmoniousElements = harmZh,
                description = desc
            )
        }

        return TodayFortuneResponse(
            solarDate = solarNow.toYmd(),
            solarDatetime = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            lunarDate = lunarNow.toString(),
            ganzhiYear = ganzhiYear,
            ganzhiMonth = ganzhiMonth,
            ganzhiDay = ganzhiDay,
            ganzhiHour = ganzhiHour,
            dayNayin = dayNayin,
            caiShenPosition = caiPos,
            xiShenPosition = xiPos,
            dayYi = dayYi,
            dayJi = dayJi,
            chongSha = chongSha,
            resonance = resonance
        )
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

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

    val BASE_SOLAR_DATE: LocalDate = LocalDate.of(1930, 1, 30)

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
        val ly = lunar.year.coerceIn(1900, 2100)
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

    fun calculateFourPillarsFromLunar(lunar: LunarDate, longitude: Double = 120.0): FourPillars {
        val solarDt = lunarToSolar(lunar)
        return calculateFourPillars(solarDt, longitude = longitude)
    }

    fun analyzeDestinyFromLunar(
        lunar: LunarDate,
        isMale: Boolean = true,
        userName: String = "缘主",
        longitude: Double = 120.0
    ): BaziDestiny {
        val solarDt = lunarToSolar(lunar)
        val destiny = analyzeDestiny(solarDt, isMale, longitude = longitude)
        val formattedLunarText = "${lunar.year}年 (${lunar.ganzhiYear}${getZodiac(lunar.year)}年) 农历${lunar.monthZh}${lunar.dayZh} ${lunar.shichenZh}"
        return destiny.copy(
            solarDatetime = solarDt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            lunarDatetime = formattedLunarText,
            userName = userName
        )
    }
}
