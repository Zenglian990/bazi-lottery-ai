package com.zeng.lottery.data.model

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type

data class NumberStat(
    @SerializedName("frequency", alternate = ["total_count"])
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

@JsonAdapter(LotteryStatsAdapter::class)
data class LotteryStats(
    @SerializedName("lottery_type")
    val lotteryType: String = "",
    @SerializedName("total_draws", alternate = ["draws_analyzed"])
    val totalDraws: Int = 0,
    @SerializedName("last_issue", alternate = ["latest_issue"])
    val lastIssue: String = "",
    val stats: Map<String, Map<String, NumberStat>> = emptyMap()
)

class LotteryStatsAdapter : JsonDeserializer<LotteryStats>, JsonSerializer<LotteryStats> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): LotteryStats {
        if (json == null || !json.isJsonObject) {
            return LotteryStats()
        }
        val obj = json.asJsonObject
        val lotteryType = obj.get("lottery_type")?.asString ?: ""
        val totalDraws = when {
            obj.has("total_draws") -> obj.get("total_draws").asInt
            obj.has("draws_analyzed") -> obj.get("draws_analyzed").asInt
            else -> 0
        }
        val lastIssue = when {
            obj.has("last_issue") -> obj.get("last_issue").asString
            obj.has("latest_issue") -> obj.get("latest_issue").asString
            else -> ""
        }

        val statsMap = mutableMapOf<String, Map<String, NumberStat>>()

        // Case 1: Root contains "stats" map
        if (obj.has("stats") && obj.get("stats").isJsonObject) {
            val statsObj = obj.getAsJsonObject("stats")
            for (zoneKey in statsObj.keySet()) {
                val zoneElem = statsObj.get(zoneKey)
                if (zoneElem != null && zoneElem.isJsonObject) {
                    val zoneObj = zoneElem.asJsonObject
                    val numberMap = mutableMapOf<String, NumberStat>()
                    for (numKey in zoneObj.keySet()) {
                        val statElem = zoneObj.get(numKey)
                        if (statElem != null && statElem.isJsonObject) {
                            val stat = parseNumberStat(statElem.asJsonObject)
                            numberMap[numKey] = stat
                        }
                    }
                    statsMap[zoneKey] = numberMap
                }
            }
        }

        // Case 2: Root contains "primary_zone" and optional "secondary_zone"
        if (obj.has("primary_zone") && obj.get("primary_zone").isJsonObject) {
            val priObj = obj.getAsJsonObject("primary_zone")
            val priMap = parseZoneStats(priObj)
            statsMap["primary"] = priMap
        }
        if (obj.has("secondary_zone") && obj.get("secondary_zone").isJsonObject) {
            val secObj = obj.getAsJsonObject("secondary_zone")
            val secMap = parseZoneStats(secObj)
            statsMap["secondary"] = secMap
        }

        return LotteryStats(
            lotteryType = lotteryType,
            totalDraws = totalDraws,
            lastIssue = lastIssue,
            stats = statsMap
        )
    }

    private fun parseZoneStats(zoneObj: JsonObject): Map<String, NumberStat> {
        val map = mutableMapOf<String, NumberStat>()
        if (zoneObj.has("stats") && zoneObj.get("stats").isJsonArray) {
            val array = zoneObj.getAsJsonArray("stats")
            for (elem in array) {
                if (elem.isJsonObject) {
                    val item = elem.asJsonObject
                    val number = item.get("number")?.asString ?: ""
                    if (number.isNotBlank()) {
                        map[number] = parseNumberStat(item)
                    }
                }
            }
        }
        return map
    }

    private fun parseNumberStat(item: JsonObject): NumberStat {
        val freq = when {
            item.has("total_count") -> item.get("total_count").asInt
            item.has("frequency") && item.get("frequency").isJsonPrimitive &&
                    item.getAsJsonPrimitive("frequency").isNumber -> {
                val f = item.get("frequency").asFloat
                if (f > 1.0f) f.toInt() else item.get("frequency").asInt
            }
            else -> 0
        }

        val rate = when {
            item.has("rate") -> item.get("rate").asFloat
            item.has("frequency") && item.get("frequency").isJsonPrimitive &&
                    item.getAsJsonPrimitive("frequency").isNumber -> {
                val f = item.get("frequency").asFloat
                if (f <= 1.0f) f else 0f
            }
            else -> 0f
        }

        val hotStatus = item.get("hot_status")?.asString ?: ""
        val isHot = item.get("is_hot")?.asBoolean ?: (hotStatus.equals("hot", ignoreCase = true))
        val isCold = item.get("is_cold")?.asBoolean ?: (hotStatus.equals("cold", ignoreCase = true))

        val currentOmission = item.get("current_omission")?.asInt ?: 0
        val maxOmission = item.get("max_omission")?.asInt ?: 0
        val avgOmission = item.get("avg_omission")?.asFloat ?: 0f

        return NumberStat(
            frequency = freq,
            rate = rate,
            isHot = isHot,
            isCold = isCold,
            currentOmission = currentOmission,
            maxOmission = maxOmission,
            avgOmission = avgOmission
        )
    }

    override fun serialize(
        src: LotteryStats?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        val obj = JsonObject()
        if (src == null) return obj
        obj.addProperty("lottery_type", src.lotteryType)
        obj.addProperty("total_draws", src.totalDraws)
        obj.addProperty("last_issue", src.lastIssue)
        context?.serialize(src.stats)?.let { obj.add("stats", it) }
        return obj
    }
}

data class DrawItem(
    val issue: String = "",
    @SerializedName("draw_date")
    val drawDate: String = "",
    @SerializedName("primary_numbers")
    val primaryNumbers: List<String> = emptyList(),
    @SerializedName("secondary_numbers")
    val secondaryNumbers: List<String>? = emptyList(),
    @SerializedName("sales_amount")
    val salesAmount: Long? = null,
    @SerializedName("pool_amount")
    val poolAmount: Long? = null
)

data class HistoryResponse(
    @SerializedName("lottery_type")
    val lotteryType: String = "",
    val page: Int = 1,
    val limit: Int = 50,
    val total: Int = 0,
    val records: List<DrawItem> = emptyList()
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
