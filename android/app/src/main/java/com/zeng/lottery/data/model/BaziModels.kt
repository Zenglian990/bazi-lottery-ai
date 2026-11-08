package com.zeng.lottery.data.model

import com.google.gson.annotations.SerializedName

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

data class Pillar(
    val stem: String,
    val branch: String,
    val element: String = "",
    @SerializedName("hide_stems")
    val hideStems: List<String> = emptyList()
) {
    val fullText: String get() = "$stem$branch"
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

data class BaziDestiny(
    @SerializedName("solar_datetime")
    val solarDatetime: String = "",
    @SerializedName("lunar_datetime")
    val lunarDatetime: String = "",
    @SerializedName("four_pillars")
    val fourPillars: FourPillars,
    @SerializedName("wuxing_scores")
    val wuxingScores: WuxingScores,
    @SerializedName("wuxing_percentage")
    val wuxingPercentage: Map<String, Float> = emptyMap(),
    @SerializedName("day_master")
    val dayMaster: String = "",
    val strength: String = "",
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
    @SerializedName("dayun_list")
    val dayunList: List<String>? = emptyList()
)

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
    val wBazi: Float = 0.5f,
    @SerializedName("w_freq")
    val wFreq: Float = 0.3f,
    @SerializedName("w_omission")
    val wOmission: Float = 0.2f,
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

data class BaziRecommendationResponse(
    @SerializedName("lottery_type")
    val lotteryType: String,
    val strategy: String,
    val recommendations: List<RecommendationItem>,
    @SerializedName("bazi_summary")
    val baziSummary: BaziDestiny? = null,
    @SerializedName("copy_text")
    val copyText: String? = null,
    @SerializedName("user_name")
    val userName: String? = null
)
