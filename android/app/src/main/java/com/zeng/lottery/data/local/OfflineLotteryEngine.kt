package com.zeng.lottery.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.zeng.lottery.data.model.BaziDestiny
import com.zeng.lottery.data.model.BaziRecommendationResponse
import com.zeng.lottery.data.model.DrawItem
import com.zeng.lottery.data.model.LotteryStats
import com.zeng.lottery.data.model.LotteryType
import com.zeng.lottery.data.model.NumberStat
import com.zeng.lottery.data.model.RecommendationItem
import java.io.InputStreamReader
import kotlin.math.ln
import kotlin.random.Random

/**
 * Embedded offline lottery analytics and recommendation engine.
 * Loads 100+ draws from assets/seed_lottery_data.json, computes frequency/hot-cold/omission stats,
 * and performs Hetu + BigData weighted A-Res reservoir sampling 100% offline.
 */
class OfflineLotteryEngine(private val context: Context? = null) {

    private val gson = Gson()
    private var cachedSeedMap: Map<String, List<DrawItem>>? = null

    /**
     * Load draws from assets or custom JSON string.
     */
    fun loadDrawsFromJson(jsonString: String): Map<String, List<DrawItem>> {
        val type = object : TypeToken<Map<String, List<DrawItem>>>() {}.type
        val map: Map<String, List<DrawItem>> = gson.fromJson(jsonString, type)
        cachedSeedMap = map
        return map
    }

    private fun getDrawsForType(lotteryType: LotteryType): List<DrawItem> {
        if (cachedSeedMap == null) {
            if (context != null) {
                try {
                    context.assets.open("seed_lottery_data.json").use { stream ->
                        val reader = InputStreamReader(stream)
                        val type = object : TypeToken<Map<String, List<DrawItem>>>() {}.type
                        cachedSeedMap = gson.fromJson(reader, type)
                    }
                } catch (e: Exception) {
                    // Fall through to classloader
                }
            }
            if (cachedSeedMap == null) {
                try {
                    val stream = OfflineLotteryEngine::class.java.classLoader?.getResourceAsStream("seed_lottery_data.json")
                        ?: OfflineLotteryEngine::class.java.classLoader?.getResourceAsStream("assets/seed_lottery_data.json")
                    stream?.use { s ->
                        val reader = InputStreamReader(s)
                        val type = object : TypeToken<Map<String, List<DrawItem>>>() {}.type
                        cachedSeedMap = gson.fromJson(reader, type)
                    }
                } catch (e: Exception) {
                    // Return empty if fallback fails
                }
            }
        }
        return cachedSeedMap?.get(lotteryType.code) ?: emptyList()
    }

    /**
     * Compute full frequency, hot/cold, and omission statistics from historical draws.
     */
    fun computeStats(lotteryType: LotteryType): LotteryStats {
        val draws = getDrawsForType(lotteryType)
        if (draws.isEmpty()) {
            return LotteryStats(lotteryType = lotteryType.code)
        }

        val totalDraws = draws.size
        val lastIssue = draws.firstOrNull()?.issue ?: ""

        val redStats = mutableMapOf<String, NumberStat>()
        val blueStats = mutableMapOf<String, NumberStat>()

        val primaryRange = lotteryType.primaryRange
        val windowSize = minOf(30, totalDraws)
        val recentDraws = draws.take(windowSize)

        // 1. Primary numbers stats
        for (num in primaryRange) {
            val numStr = formatBallNumber(num, lotteryType)
            val appearances = draws.map { it.primaryNumbers.contains(numStr) }
            val freq = appearances.count { it }
            val rate = freq.toFloat() / totalDraws

            val recentFreq = recentDraws.count { it.primaryNumbers.contains(numStr) }
            val expectedRecentFreq = windowSize * (lotteryType.primaryCount.toFloat() / (primaryRange.last - primaryRange.first + 1))
            val isHot = recentFreq >= expectedRecentFreq * 1.25f
            val isCold = recentFreq <= expectedRecentFreq * 0.75f

            var currentOmission = 0
            for (draw in draws) {
                if (draw.primaryNumbers.contains(numStr)) break
                currentOmission++
            }

            var maxOmission = 0
            var curOmission = 0
            for (draw in draws.reversed()) {
                if (draw.primaryNumbers.contains(numStr)) {
                    if (curOmission > maxOmission) maxOmission = curOmission
                    curOmission = 0
                } else {
                    curOmission++
                }
            }
            if (curOmission > maxOmission) maxOmission = curOmission

            val avgOmission = if (freq > 0) (totalDraws - freq).toFloat() / (freq + 1) else totalDraws.toFloat()

            redStats[numStr] = NumberStat(
                frequency = freq,
                rate = rate,
                isHot = isHot,
                isCold = isCold,
                currentOmission = currentOmission,
                maxOmission = maxOmission,
                avgOmission = avgOmission
            )
        }

        // 2. Secondary numbers stats (if applicable)
        lotteryType.secondaryRange?.let { secRange ->
            for (num in secRange) {
                val numStr = String.format("%02d", num)
                val appearances = draws.map { it.secondaryNumbers?.contains(numStr) == true }
                val freq = appearances.count { it }
                val rate = freq.toFloat() / totalDraws

                val recentFreq = recentDraws.count { it.secondaryNumbers?.contains(numStr) == true }
                val expectedRecentFreq = windowSize * (lotteryType.secondaryCount.toFloat() / (secRange.last - secRange.first + 1))
                val isHot = recentFreq >= expectedRecentFreq * 1.25f
                val isCold = recentFreq <= expectedRecentFreq * 0.75f

                var currentOmission = 0
                for (draw in draws) {
                    if (draw.secondaryNumbers?.contains(numStr) == true) break
                    currentOmission++
                }

                var maxOmission = 0
                var curOmission = 0
                for (draw in draws.reversed()) {
                    if (draw.secondaryNumbers?.contains(numStr) == true) {
                        if (curOmission > maxOmission) maxOmission = curOmission
                        curOmission = 0
                    } else {
                        curOmission++
                    }
                }
                if (curOmission > maxOmission) maxOmission = curOmission

                val avgOmission = if (freq > 0) (totalDraws - freq).toFloat() / (freq + 1) else totalDraws.toFloat()

                blueStats[numStr] = NumberStat(
                    frequency = freq,
                    rate = rate,
                    isHot = isHot,
                    isCold = isCold,
                    currentOmission = currentOmission,
                    maxOmission = maxOmission,
                    avgOmission = avgOmission
                )
            }
        }

        val statsMap = mutableMapOf("primary" to redStats)
        if (blueStats.isNotEmpty()) {
            statsMap["secondary"] = blueStats
        }

        return LotteryStats(
            lotteryType = lotteryType.code,
            totalDraws = totalDraws,
            lastIssue = lastIssue,
            stats = statsMap
        )
    }

    /**
     * Generate offline recommendations using Bazi Hetu weights and historical frequency/omissions.
     */
    fun generateRecommendation(
        destiny: BaziDestiny,
        lotteryType: LotteryType,
        count: Int = 1,
        strategy: String = "balanced"
    ): BaziRecommendationResponse {
        val stats = computeStats(lotteryType)
        val (wBazi, wFreq, wOmission) = when (strategy.lowercase()) {
            "bazi", "bazi_priority", "命理吉数优先" -> Triple(0.60f, 0.25f, 0.15f)
            "hot_chase", "冷热追号" -> Triple(0.25f, 0.55f, 0.20f)
            else -> Triple(0.35f, 0.40f, 0.25f) // 稳健大数据
        }

        val primaryStats = stats.stats["primary"] ?: emptyMap()
        val secondaryStats = stats.stats["secondary"] ?: emptyMap()

        val recommendations = mutableListOf<RecommendationItem>()

        for (i in 0 until count) {
            // Sample primary balls
            val primaryBalls = sampleBalls(
                range = lotteryType.primaryRange,
                count = lotteryType.primaryCount,
                allowDuplicates = lotteryType.primaryAllowDuplicate,
                destiny = destiny,
                numberStats = primaryStats,
                wBazi = wBazi,
                wFreq = wFreq,
                wOmission = wOmission,
                isSecondary = false
            )

            // Sample secondary balls
            val secondaryBalls = if (lotteryType.secondaryRange != null && lotteryType.secondaryCount > 0) {
                sampleBalls(
                    range = lotteryType.secondaryRange,
                    count = lotteryType.secondaryCount,
                    allowDuplicates = lotteryType.secondaryAllowDuplicate,
                    destiny = destiny,
                    numberStats = secondaryStats,
                    wBazi = wBazi,
                    wFreq = wFreq,
                    wOmission = wOmission,
                    isSecondary = true
                )
            } else {
                emptyList()
            }

            // Wuxing distribution of selected numbers
            val wuxingDist = mutableMapOf<String, Int>()
            (primaryBalls + secondaryBalls).forEach { numStr ->
                val num = numStr.toIntOrNull() ?: 0
                val elem = BaziEngine.getHetuElement(num)
                wuxingDist[elem] = wuxingDist.getOrDefault(elem, 0) + 1
            }

            val primaryXi = destiny.xiYongShen.firstOrNull() ?: "金"
            val rationale = "命中喜用神【$primaryXi】生扶磁场，融合近30期大数据频率与遗漏加权(A-Res抽样)"

            recommendations.add(
                RecommendationItem(
                    primaryNumbers = primaryBalls,
                    secondaryNumbers = secondaryBalls,
                    wuxingDistribution = wuxingDist,
                    scoreRationale = rationale
                )
            )
        }

        return BaziRecommendationResponse(
            lotteryType = lotteryType.code,
            strategy = strategy,
            recommendations = recommendations,
            baziSummary = destiny
        )
    }

    private fun sampleBalls(
        range: IntRange,
        count: Int,
        allowDuplicates: Boolean,
        destiny: BaziDestiny,
        numberStats: Map<String, NumberStat>,
        wBazi: Float,
        wFreq: Float,
        wOmission: Float,
        isSecondary: Boolean
    ): List<String> {
        val candidates = range.toList()
        val candidateWeights = candidates.map { num ->
            val numStr = if (range.last > 9) String.format("%02d", num) else num.toString()
            val elem = BaziEngine.getHetuElement(num)
            val elemZh = BaziEngine.ELEMENT_ZH[elem] ?: "土"

            val baziScore = when {
                destiny.xiYongShen.contains(elemZh) -> 1.00f
                destiny.lackingElements.contains(elemZh) -> 0.70f
                destiny.jiShen.contains(elemZh) -> 0.20f
                else -> 0.45f
            }

            val stat = numberStats[numStr]
            val freqScore = stat?.rate?.coerceIn(0.01f, 1.0f) ?: 0.3f
            val omissionScore = (stat?.currentOmission?.toFloat() ?: 5f).coerceIn(1f, 50f) / 50f

            val totalWeight = (wBazi * baziScore + wFreq * freqScore + wOmission * omissionScore).coerceAtLeast(0.01f)
            Pair(numStr, totalWeight)
        }

        if (allowDuplicates) {
            // Discrete weighted choice for each slot (FC3D, QXC front digits)
            val result = mutableListOf<String>()
            val totalWeightSum = candidateWeights.sumOf { it.second.toDouble() }
            for (i in 0 until count) {
                var p = Random.nextDouble() * totalWeightSum
                var selected = candidateWeights.first().first
                for ((item, weight) in candidateWeights) {
                    p -= weight
                    if (p <= 0.0) {
                        selected = item
                        break
                    }
                }
                result.add(selected)
            }
            return result
        } else {
            // Efraimidis-Spirakis A-Res weighted reservoir sampling without replacement
            val keys = candidateWeights.map { (item, weight) ->
                val u = Random.nextDouble().coerceIn(1e-9, 1.0 - 1e-9)
                val key = ln(u) / weight.toDouble()
                Pair(item, key)
            }
            // Sort by key descending and take top k
            val selected = keys.sortedByDescending { it.second }.take(count).map { it.first }
            // Sort ascending for standard lottery ticket presentation (SSQ, DLT)
            return selected.sortedBy { it.toIntOrNull() ?: 0 }
        }
    }

    private fun formatBallNumber(num: Int, lotteryType: LotteryType): String {
        return if (lotteryType.primaryRange.last > 9) {
            String.format("%02d", num)
        } else {
            num.toString()
        }
    }
}
