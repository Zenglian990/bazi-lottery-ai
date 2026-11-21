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
    @SerializedName("hit_rate")
    val hitRate: Float = 0f,
    @SerializedName("is_hot")
    val isHot: Boolean = false,
    @SerializedName("is_cold")
    val isCold: Boolean = false,
    @SerializedName("current_omission")
    val currentOmission: Int = 0,
    @SerializedName("max_omission")
    val maxOmission: Int = 0,
    @SerializedName("avg_omission")
    val avgOmission: Float = 0f,
    @SerializedName("desire_index")
    val desireIndex: Float = 0f
)

@JsonAdapter(LotteryStatsAdapter::class)
data class LotteryStats(
    @SerializedName("lottery_type")
    val lotteryType: String = "",
    @SerializedName("total_draws", alternate = ["draws_analyzed"])
    val totalDraws: Int = 0,
    @SerializedName("last_issue", alternate = ["latest_issue"])
    val lastIssue: String = "",
    @SerializedName("latest_draw_date")
    val latestDrawDate: String = "",
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
        val lotteryType = obj.get("lottery_type")?.takeIf { !it.isJsonNull }?.asString ?: ""
        val totalDraws = when {
            obj.has("total_draws") && !obj.get("total_draws").isJsonNull -> obj.get("total_draws").asInt
            obj.has("draws_analyzed") && !obj.get("draws_analyzed").isJsonNull -> obj.get("draws_analyzed").asInt
            else -> 0
        }
        val lastIssue = when {
            obj.has("last_issue") && !obj.get("last_issue").isJsonNull -> obj.get("last_issue").asString
            obj.has("latest_issue") && !obj.get("latest_issue").isJsonNull -> obj.get("latest_issue").asString
            else -> ""
        }
        val latestDrawDate = obj.get("latest_draw_date")?.takeIf { !it.isJsonNull }?.asString ?: ""

        val statsMap = mutableMapOf<String, Map<String, NumberStat>>()

        // 兼容模式 1：根对象直接包含 "stats" 映射
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
                            numberMap[numKey] = parseNumberStat(statElem.asJsonObject)
                        }
                    }
                    statsMap[zoneKey] = numberMap
                }
            }
        }

        // 兼容模式 2：FastAPI 返回的 primary_zone / secondary_zone 列表结构
        if (obj.has("primary_zone") && obj.get("primary_zone").isJsonObject) {
            statsMap["primary"] = parseZoneStats(obj.getAsJsonObject("primary_zone"))
        }
        if (obj.has("secondary_zone") && obj.get("secondary_zone").isJsonObject) {
            statsMap["secondary"] = parseZoneStats(obj.getAsJsonObject("secondary_zone"))
        }

        return LotteryStats(
            lotteryType = lotteryType,
            totalDraws = totalDraws,
            lastIssue = lastIssue,
            latestDrawDate = latestDrawDate,
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
                    val number = item.get("number")?.takeIf { !it.isJsonNull }?.asString ?: ""
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
            item.has("total_count") && !item.get("total_count").isJsonNull -> item.get("total_count").asInt
            item.has("frequency") && item.get("frequency").isJsonPrimitive &&
                item.getAsJsonPrimitive("frequency").isNumber -> {
                val f = item.get("frequency").asFloat
                if (f > 1.0f) f.toInt() else item.get("frequency").asInt
            }
            else -> 0
        }

        val rate = when {
            item.has("rate") && !item.get("rate").isJsonNull -> item.get("rate").asFloat
            item.has("frequency") && item.get("frequency").isJsonPrimitive &&
                item.getAsJsonPrimitive("frequency").isNumber -> {
                val f = item.get("frequency").asFloat
                if (f <= 1.0f) f else 0f
            }
            else -> 0f
        }

        val hitRate = item.get("hit_rate")?.takeIf { !it.isJsonNull }?.asFloat ?: rate.coerceAtMost(1f)
        val hotStatus = item.get("hot_status")?.takeIf { !it.isJsonNull }?.asString ?: ""
        val isHot = item.get("is_hot")?.takeIf { !it.isJsonNull }?.asBoolean
            ?: hotStatus.equals("hot", ignoreCase = true)
        val isCold = item.get("is_cold")?.takeIf { !it.isJsonNull }?.asBoolean
            ?: hotStatus.equals("cold", ignoreCase = true)

        val currentOmission = item.get("current_omission")?.takeIf { !it.isJsonNull }?.asInt ?: 0
        val maxOmission = item.get("max_omission")?.takeIf { !it.isJsonNull }?.asInt ?: 0
        val avgOmission = item.get("avg_omission")?.takeIf { !it.isJsonNull }?.asFloat ?: 0f
        val desireIndex = item.get("desire_index")?.takeIf { !it.isJsonNull }?.asFloat
            ?: if (avgOmission > 0f) currentOmission.toFloat() / avgOmission else 0f

        return NumberStat(
            frequency = freq,
            rate = rate,
            hitRate = hitRate,
            isHot = isHot,
            isCold = isCold,
            currentOmission = currentOmission,
            maxOmission = maxOmission,
            avgOmission = avgOmission,
            desireIndex = desireIndex
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
        obj.addProperty("latest_draw_date", src.latestDrawDate)
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
    val timestamp: String = "",
    @SerializedName("latest_draws")
    val latestDraws: Map<String, Map<String, String>>? = null
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
