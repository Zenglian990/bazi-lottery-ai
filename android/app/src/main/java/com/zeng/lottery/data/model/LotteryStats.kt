package com.zeng.lottery.data.model

import com.google.gson.annotations.SerializedName

data class NumberStat(
    val frequency: Int = 0,
    val rate: Float = 0f,
    @SerializedName("is_hot")
    val isHot: Boolean = false,
    @SerializedName("is_cold")
    val isCold: Boolean = false,
    @SerializedName("current_omission")
    val currentOmission: Int = 0,
    @SerializedName("max_omission")
    val maxOmission: Int = 0,
    @SerializedName("avg_omission")
    val avgOmission: Float = 0f
)

data class LotteryStats(
    @SerializedName("lottery_type")
    val lotteryType: String = "",
    @SerializedName("total_draws")
    val totalDraws: Int = 0,
    @SerializedName("last_issue")
    val lastIssue: String = "",
    val stats: Map<String, Map<String, NumberStat>> = emptyMap()
)

data class DrawItem(
    val issue: String = "",
    @SerializedName("draw_date")
    val drawDate: String = "",
    @SerializedName("primary_numbers")
    val primaryNumbers: List<String> = emptyList(),
    @SerializedName("secondary_numbers")
    val secondaryNumbers: List<String> = emptyList(),
    @SerializedName("sales_amount")
    val salesAmount: Long? = null,
    @SerializedName("pool_amount")
    val poolAmount: Long? = null
)

data class HealthResponse(
    val status: String = "",
    val version: String = "",
    val timestamp: String = ""
)

data class LotteryRuleDetail(
    val name: String = "",
    @SerializedName("red_range")
    val redRange: List<Int>? = null,
    @SerializedName("red_count")
    val redCount: Int? = null,
    @SerializedName("blue_range")
    val blueRange: List<Int>? = null,
    @SerializedName("blue_count")
    val blueCount: Int? = null,
    @SerializedName("front_range")
    val frontRange: List<Int>? = null,
    @SerializedName("front_count")
    val frontCount: Int? = null,
    @SerializedName("back_range")
    val backRange: List<Int>? = null,
    @SerializedName("back_count")
    val backCount: Int? = null,
    @SerializedName("digits_count")
    val digitsCount: Int? = null,
    @SerializedName("range_each")
    val rangeEach: List<Int>? = null
)

data class LotteryRulesResponse(
    val rules: Map<String, Any> = emptyMap()
)
