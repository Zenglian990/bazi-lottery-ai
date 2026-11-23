package com.zeng.lottery.data.model

import com.google.gson.annotations.SerializedName
import java.util.UUID

data class LunarDate(
    val year: Int = 1990,
    val month: Int = 6,
    val isLeap: Boolean = false,
    val day: Int = 24,
    val hour: Int = 8,
    val minute: Int = 30
) {
    val isLeapMonth: Boolean get() = isLeap
    val ganzhiYear: String get() = com.zeng.lottery.data.local.BaziEngine.getYearGanZhi(year)
    val zodiac: String get() = com.zeng.lottery.data.local.BaziEngine.getZodiac(year)
    val monthZh: String get() = (if (isLeap) "闰" else "") + com.zeng.lottery.data.local.BaziEngine.LUNAR_MONTH_NAMES.getOrElse(month - 1) { "${month}月" }
    val dayZh: String get() = com.zeng.lottery.data.local.BaziEngine.LUNAR_DAY_NAMES.getOrElse(day - 1) { "${day}日" }
    val shichenZh: String get() = com.zeng.lottery.data.local.BaziEngine.getShichenName(hour)
    val displayButtonDate: String get() = "${year}年 (${ganzhiYear}${zodiac}年) 农历$monthZh$dayZh"
    val displayFullText: String get() = "${year}年 (${ganzhiYear}${zodiac}年) 农历$monthZh$dayZh $shichenZh"
    val displayChinese: String get() = displayFullText
}

data class SavedBirthProfile(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "本人",
    val userName: String = "缘主",
    val lunarDate: LunarDate = LunarDate(),
    val isMale: Boolean = true,
    val longitude: Double = 120.0
)

data class Pillar(
    val stem: String,
    val branch: String,
    val element: String = "",
    @SerializedName("hide_stems")
    val hideStems: List<String> = emptyList()
) {
    val fullText: String get() = "$stem$branch"
    val stemElement: String get() = com.zeng.lottery.data.local.BaziEngine.STEM_ELEMENTS[stem] ?: ""
}

data class FourPillars(
    val year: Pillar,
    val month: Pillar,
    val day: Pillar,
    val hour: Pillar
)

data class WuxingScores(
    val metal: Float = 0f,
    val wood: Float = 0f,
    val water: Float = 0f,
    val fire: Float = 0f,
    val earth: Float = 0f
) {
    val total: Float get() = metal + wood + water + fire + earth

    fun toNormalizedMap(): Map<String, Float> {
        val sum = if (total > 0f) total else 1f
        return mapOf(
            "metal" to (metal / sum * 100f),
            "wood" to (wood / sum * 100f),
            "water" to (water / sum * 100f),
            "fire" to (fire / sum * 100f),
            "earth" to (earth / sum * 100f)
        )
    }
}

data class DayunDetail(
    @SerializedName("gan_zhi", alternate = ["ganzhi"])
    val ganzhi: String = "",
    @SerializedName("start_age")
    val startAge: Int = 0,
    @SerializedName("end_age")
    val endAge: Int = 0,
    @SerializedName("start_year")
    val startYear: Int = 0,
    @SerializedName("end_year")
    val endYear: Int = 0
)

data class NameGridItem(
    val value: Int = 0,
    val fortune: String = "平",
    @SerializedName("description", alternate = ["meaning"])
    val meaning: String = "",
    @SerializedName("element", alternate = ["wuxing"])
    val wuxing: String = ""
)

data class NameNumerology(
    val surname: String = "",
    @SerializedName("given_name")
    val givenName: String = "",
    @SerializedName("stroke_standard")
    val strokeStandard: String = "现代标准简体笔画",
    @SerializedName("is_chinese_name")
    val isChineseName: Boolean = true,
    @SerializedName("surname_strokes")
    val surnameStrokes: List<Int> = emptyList(),
    @SerializedName("given_strokes")
    val givenStrokes: List<Int> = emptyList(),
    @SerializedName("character_strokes")
    val characterStrokes: Map<String, Int> = emptyMap(),
    @SerializedName("tian_ge")
    val tianGe: NameGridItem? = null,
    @SerializedName("ren_ge")
    val renGe: NameGridItem? = null,
    @SerializedName("di_ge")
    val diGe: NameGridItem? = null,
    @SerializedName("wai_ge")
    val waiGe: NameGridItem? = null,
    @SerializedName("zong_ge")
    val zongGe: NameGridItem? = null,
    @SerializedName("primary_element")
    val primaryElement: String = "",
    @SerializedName("five_grids")
    val fiveGrids: Map<String, NameGridItem>? = null,
    @SerializedName("name_wuxing")
    val nameWuxing: Map<String, Int> = emptyMap(),
    @SerializedName("name_lucky_numbers")
    val nameLuckyNumbers: List<Int> = emptyList(),
    val summary: String = ""
) {
    /**
     * 统一读取五格结构：兼容后端扁平字段（tian_ge/ren_ge/di_ge/wai_ge/zong_ge）及旧版 five_grids Map。
     */
    val resolvedFiveGrids: Map<String, NameGridItem>
        get() {
            val map = fiveGrids
            if (!map.isNullOrEmpty()) return map
            val built = LinkedHashMap<String, NameGridItem>(5)
            tianGe?.let { built["tian_ge"] = it }
            renGe?.let { built["ren_ge"] = it }
            diGe?.let { built["di_ge"] = it }
            waiGe?.let { built["wai_ge"] = it }
            zongGe?.let { built["zong_ge"] = it }
            return built
        }
}

data class BaziDestiny(
    @SerializedName("solar_datetime")
    val solarDatetime: String = "",
    @SerializedName("lunar_datetime")
    val lunarDatetime: String = "",
    @SerializedName("four_pillars")
    val fourPillars: FourPillars = FourPillars(
        Pillar("", "", ""), Pillar("", "", ""),
        Pillar("", "", ""), Pillar("", "", "")
    ),
    @SerializedName("wuxing_scores")
    val wuxingScores: WuxingScores = WuxingScores(),
    @SerializedName("wuxing_percentage")
    val wuxingPercentage: Map<String, Float> = emptyMap(),
    @SerializedName("day_master")
    val dayMaster: String = "",
    val strength: String = "",
    @SerializedName("strength_level")
    val strengthLevel: String? = null,
    @SerializedName("strength_ratio")
    val strengthRatio: Float? = null,
    @SerializedName("lacking_elements")
    val lackingElements: List<String> = emptyList(),
    @SerializedName("xi_yong_shen")
    val xiYongShen: List<String> = emptyList(),
    @SerializedName("ji_shen")
    val jiShen: List<String> = emptyList(),
    @SerializedName("xi_yong_shen_zh")
    val xiYongShenZh: List<String>? = null,
    @SerializedName("ji_shen_zh")
    val jiShenZh: List<String>? = null,
    @SerializedName("lacking_elements_zh")
    val lackingElementsZh: List<String>? = null,
    @SerializedName("user_name")
    val userName: String? = null,
    val shichen: String? = null,
    val gender: String = "male",
    @SerializedName("dayun_forward")
    val dayunForward: Boolean? = true,
    @SerializedName("dayun_start_age")
    val dayunStartAge: Int? = null,
    @SerializedName("dayun_list")
    val dayunList: List<String>? = emptyList(),
    @SerializedName("dayun_details")
    val dayunDetails: List<DayunDetail>? = emptyList(),
    @SerializedName("name_numerology")
    val nameNumerology: NameNumerology? = null,
    @SerializedName("calendar_warnings")
    val calendarWarnings: List<String>? = emptyList()
) {
    fun toSummary(): BaziSummary = BaziSummary(
        dayMaster = dayMaster,
        dayMasterElement = com.zeng.lottery.data.local.BaziEngine.STEM_ELEMENTS[dayMaster] ?: fourPillars.day.stemElement,
        strength = strength,
        strengthRatio = strengthRatio,
        lackingElements = lackingElements,
        xiYongShen = xiYongShen,
        jiShen = jiShen
    )
}

val BaziDestiny.displayXiYongShen: List<String>
    get() = (xiYongShenZh ?: xiYongShen).map { com.zeng.lottery.data.local.BaziEngine.ELEMENT_ZH[it.lowercase()] ?: it }

val BaziDestiny.displayJiShen: List<String>
    get() = (jiShenZh ?: jiShen).map { com.zeng.lottery.data.local.BaziEngine.ELEMENT_ZH[it.lowercase()] ?: it }

val BaziDestiny.displayLackingElements: List<String>
    get() = (lackingElementsZh ?: lackingElements).map { com.zeng.lottery.data.local.BaziEngine.ELEMENT_ZH[it.lowercase()] ?: it }

data class BaziDestinyRequest(
    @SerializedName("birth_datetime")
    val birthDatetime: String? = null,
    @SerializedName("calendar_type")
    val calendarType: String = "lunar",
    val gender: String = "male",
    val longitude: Double = 120.0,
    @SerializedName("user_name")
    val userName: String? = null,
    val shichen: String? = null,
    @SerializedName("is_leap_month")
    val isLeapMonth: Boolean = false,
    @SerializedName("lunar_year")
    val lunarYear: Int? = null,
    @SerializedName("lunar_month")
    val lunarMonth: Int? = null,
    @SerializedName("lunar_day")
    val lunarDay: Int? = null,
    @SerializedName("lunar_hour")
    val lunarHour: String? = null
)

data class BaziRecommendationRequest(
    @SerializedName("birth_datetime")
    val birthDatetime: String? = null,
    @SerializedName("calendar_type")
    val calendarType: String = "lunar",
    @SerializedName("lottery_type")
    val lotteryType: String,
    val count: Int = 1,
    val strategy: String = "balanced",
    @SerializedName("w_bazi")
    val wBazi: Float? = null,
    @SerializedName("w_freq")
    val wFreq: Float? = null,
    @SerializedName("w_omission")
    val wOmission: Float? = null,
    val gender: String? = "male",
    val longitude: Float? = 120.0f,
    @SerializedName("user_name")
    val userName: String? = null,
    val shichen: String? = null,
    @SerializedName("is_leap_month")
    val isLeapMonth: Boolean = false,
    @SerializedName("lunar_year")
    val lunarYear: Int? = null,
    @SerializedName("lunar_month")
    val lunarMonth: Int? = null,
    @SerializedName("lunar_day")
    val lunarDay: Int? = null,
    @SerializedName("lunar_hour")
    val lunarHour: String? = null,
    @SerializedName("locked_primary")
    val lockedPrimary: List<String>? = null,
    @SerializedName("excluded_primary")
    val excludedPrimary: List<String>? = null,
    @SerializedName("locked_secondary")
    val lockedSecondary: List<String>? = null,
    @SerializedName("excluded_secondary")
    val excludedSecondary: List<String>? = null
)

data class TodayFortuneRequest(
    @SerializedName("birth_datetime")
    val birthDatetime: String? = null,
    @SerializedName("calendar_type")
    val calendarType: String = "lunar",
    val longitude: Double = 120.0,
    @SerializedName("user_name")
    val userName: String? = null,
    val shichen: String? = null,
    @SerializedName("is_leap_month")
    val isLeapMonth: Boolean = false,
    @SerializedName("lunar_year")
    val lunarYear: Int? = null,
    @SerializedName("lunar_month")
    val lunarMonth: Int? = null,
    @SerializedName("lunar_day")
    val lunarDay: Int? = null,
    @SerializedName("lunar_hour")
    val lunarHour: String? = null,
    val gender: String = "male"
)

data class TodayResonance(
    @SerializedName("today_ganzhi")
    val todayGanzhi: String = "",
    @SerializedName("user_day_master")
    val userDayMaster: String = "",
    @SerializedName("user_xi_yong")
    val userXiYong: List<String> = emptyList(),
    @SerializedName("resonance_score")
    val resonanceScore: Float = 0.65f,
    @SerializedName("resonance_level")
    val resonanceLevel: String = "平吉",
    @SerializedName("resonance_multiplier")
    val resonanceMultiplier: Float = 1.0f,
    @SerializedName("harmonious_elements")
    val harmoniousElements: List<String> = emptyList(),
    val description: String = ""
)

data class TodayFortuneResponse(
    @SerializedName("solar_date")
    val solarDate: String = "",
    @SerializedName("solar_datetime")
    val solarDatetime: String = "",
    @SerializedName("lunar_date")
    val lunarDate: String = "",
    @SerializedName("ganzhi_year")
    val ganzhiYear: String = "",
    @SerializedName("ganzhi_month")
    val ganzhiMonth: String = "",
    @SerializedName("ganzhi_day")
    val ganzhiDay: String = "",
    @SerializedName("ganzhi_hour")
    val ganzhiHour: String = "",
    @SerializedName("day_nayin")
    val dayNayin: String = "",
    @SerializedName("cai_shen_position")
    val caiShenPosition: String = "",
    @SerializedName("xi_shen_position")
    val xiShenPosition: String = "",
    @SerializedName("day_yi")
    val dayYi: List<String> = emptyList(),
    @SerializedName("day_ji")
    val dayJi: List<String> = emptyList(),
    @SerializedName("chong_sha")
    val chongSha: String = "",
    val resonance: TodayResonance? = null
)

data class SyncLotteryRequest(
    @SerializedName("lottery_type")
    val lotteryType: String = "all",
    @SerializedName("force_reload")
    val forceReload: Boolean = false
)

data class SyncLotteryResponse(
    val status: String = "",
    @SerializedName("synced_counts")
    val syncedCounts: Map<String, Int> = emptyMap(),
    val message: String = ""
)

data class ClassicsEvidence(
    val book: String = "",
    val chapter: String = "",
    val quote: String = "",
    val vernacular: String = "",
    @SerializedName("auspicious_numbers")
    val auspiciousNumbers: List<Int> = emptyList(),
    val category: String = "hexagram"
)

data class RecommendationItem(
    @SerializedName("bet_index")
    val betIndex: Int = 1,
    @SerializedName("primary_numbers")
    val primaryNumbers: List<String>,
    @SerializedName("secondary_numbers")
    val secondaryNumbers: List<String> = emptyList(),
    @SerializedName("formatted_ticket")
    val formattedTicket: String? = null,
    @SerializedName("wuxing_distribution")
    val wuxingDistribution: Map<String, Int> = emptyMap(),
    @SerializedName("score_rationale")
    val scoreRationale: String = "",
    @SerializedName("classics_evidence")
    val classicsEvidence: ClassicsEvidence? = null
)

data class BaziSummary(
    @SerializedName("day_master")
    val dayMaster: String = "",
    @SerializedName("day_master_element")
    val dayMasterElement: String = "",
    val strength: String = "",
    @SerializedName("strength_ratio")
    val strengthRatio: Float? = null,
    @SerializedName("lacking_elements")
    val lackingElements: List<String> = emptyList(),
    @SerializedName("xi_yong_shen")
    val xiYongShen: List<String> = emptyList(),
    @SerializedName("ji_shen")
    val jiShen: List<String> = emptyList()
)

data class BaziRecommendationResponse(
    @SerializedName("lottery_type")
    val lotteryType: String,
    val strategy: String,
    val recommendations: List<RecommendationItem>,
    @SerializedName("bazi_summary")
    val baziSummary: BaziSummary? = null,
    @SerializedName("copy_text")
    val copyText: String? = null,
    @SerializedName("user_name")
    val userName: String? = null
)
