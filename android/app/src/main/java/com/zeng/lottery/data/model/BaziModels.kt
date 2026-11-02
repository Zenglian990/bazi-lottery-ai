package com.zeng.lottery.data.model

import com.google.gson.annotations.SerializedName

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
    @SerializedName("lacking_elements")
    val lackingElements: List<String> = emptyList(),
    @SerializedName("xi_yong_shen")
    val xiYongShen: List<String> = emptyList(),
    @SerializedName("ji_shen")
    val jiShen: List<String> = emptyList()
)

data class BaziDestinyRequest(
    @SerializedName("birth_datetime")
    val birthDatetime: String,
    @SerializedName("calendar_type")
    val calendarType: String = "solar",
    val gender: String = "male",
    val longitude: Double = 120.0
)

data class BaziRecommendationRequest(
    @SerializedName("birth_datetime")
    val birthDatetime: String,
    @SerializedName("calendar_type")
    val calendarType: String = "solar",
    @SerializedName("lottery_type")
    val lotteryType: String,
    val count: Int = 1,
    val strategy: String = "balanced",
    @SerializedName("w_bazi")
    val wBazi: Float = 0.5f,
    @SerializedName("w_freq")
    val wFreq: Float = 0.3f,
    @SerializedName("w_omission")
    val wOmission: Float = 0.2f
)

data class RecommendationItem(
    @SerializedName("primary_numbers")
    val primaryNumbers: List<String>,
    @SerializedName("secondary_numbers")
    val secondaryNumbers: List<String> = emptyList(),
    @SerializedName("wuxing_distribution")
    val wuxingDistribution: Map<String, Int> = emptyMap(),
    @SerializedName("score_rationale")
    val scoreRationale: String = ""
)

data class BaziRecommendationResponse(
    @SerializedName("lottery_type")
    val lotteryType: String,
    val strategy: String,
    val recommendations: List<RecommendationItem>,
    @SerializedName("bazi_summary")
    val baziSummary: BaziDestiny? = null
)
