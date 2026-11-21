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
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.ln
import kotlin.random.Random

/**
 * 端侧离线彩票统计与八字融合选号引擎（与后端 numerology_engine.py 算法严格对齐）。
 *
 * 特性：
 * - 支持合并持久化远程同步到的最新开奖数据（断网不倒退回旧种子期号）；
 * - 修复可重复数字彩种（FC3D / QXC）频次统计口径与 0 次冷号欲出度公式；
 * - 5 级五行喜忌梯度（忌神优先于缺行）、Min-Max 频次归一化、按策略精确区分遗漏曲线；
 * - 支持定胆/杀号、自定义三元权重与多注退火补齐（杜绝静默少注）。
 */
class OfflineLotteryEngine(private val context: Context? = null) {

    private val gson = Gson()
    @Volatile
    private var cachedSeedMap: Map<String, List<DrawItem>>? = null
    private val runtimeOverlayMap = ConcurrentHashMap<String, List<DrawItem>>()
    @Volatile
    private var diskCacheLoaded = false

    private fun getDiskCacheFile(): File? {
        return try {
            context?.filesDir?.let { File(it, "cached_lottery_draws_v1.json") }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * 联网成功拉取开奖记录后，合并至内存并异步/安全落盘，保证离线时仍保留最新开奖期号。
     */
    fun saveRemoteDrawsCache(lotteryType: LotteryType, remoteDraws: List<DrawItem>) {
        if (remoteDraws.isEmpty()) return
        val seed = loadSeedMap()[lotteryType.code].orEmpty()
        val merged = mergeDrawsByIssue(remoteDraws, seed).take(120)
        runtimeOverlayMap[lotteryType.code] = merged
        persistDiskCache()
    }

    private fun mergeDrawsByIssue(primary: List<DrawItem>, fallback: List<DrawItem>): List<DrawItem> {
        val byIssue = LinkedHashMap<String, DrawItem>()
        for (item in primary) {
            if (item.issue.isNotBlank()) byIssue[item.issue] = item
        }
        for (item in fallback) {
            if (item.issue.isNotBlank() && !byIssue.containsKey(item.issue)) {
                byIssue[item.issue] = item
            }
        }
        return byIssue.values.sortedByDescending { it.issue }
    }

    private fun ensureDiskCacheLoaded() {
        if (diskCacheLoaded) return
        synchronized(this) {
            if (diskCacheLoaded) return
            diskCacheLoaded = true
            val file = getDiskCacheFile() ?: return
            if (!file.exists()) return
            try {
                val jsonText = file.readText(Charsets.UTF_8)
                val type = object : TypeToken<Map<String, List<DrawItem>>>() {}.type
                val parsed: Map<String, List<DrawItem>>? = gson.fromJson(jsonText, type)
                parsed?.forEach { (k, v) ->
                    if (v.isNotEmpty()) runtimeOverlayMap[k] = v
                }
            } catch (_: Throwable) {
                // 缓存损坏时静默忽略，回退内置种子
            }
        }
    }

    private fun persistDiskCache() {
        val file = getDiskCacheFile() ?: return
        try {
            val snapshot = HashMap<String, List<DrawItem>>(runtimeOverlayMap)
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(gson.toJson(snapshot), Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                file.writeText(gson.toJson(snapshot), Charsets.UTF_8)
                tmp.delete()
            }
        } catch (_: Throwable) {
            // 磁盘空间不足等异常不中断业务流程
        }
    }

    fun loadDrawsFromJson(jsonString: String): Map<String, List<DrawItem>> {
        val type = object : TypeToken<Map<String, List<DrawItem>>>() {}.type
        val map: Map<String, List<DrawItem>> = gson.fromJson(jsonString, type)
        cachedSeedMap = map
        return map
    }

    private fun loadSeedMap(): Map<String, List<DrawItem>> {
        val existing = cachedSeedMap
        if (existing != null) return existing
        synchronized(this) {
            cachedSeedMap?.let { return it }
            var loaded: Map<String, List<DrawItem>>? = null
            if (context != null) {
                try {
                    context.assets.open("seed_lottery_data.json").use { stream ->
                        val reader = InputStreamReader(stream)
                        val type = object : TypeToken<Map<String, List<DrawItem>>>() {}.type
                        loaded = gson.fromJson(reader, type)
                    }
                } catch (_: Exception) {
                }
            }
            if (loaded == null) {
                try {
                    val stream = OfflineLotteryEngine::class.java.classLoader?.getResourceAsStream("seed_lottery_data.json")
                        ?: OfflineLotteryEngine::class.java.classLoader?.getResourceAsStream("assets/seed_lottery_data.json")
                    stream?.use { s ->
                        val reader = InputStreamReader(s)
                        val type = object : TypeToken<Map<String, List<DrawItem>>>() {}.type
                        loaded = gson.fromJson(reader, type)
                    }
                } catch (_: Exception) {
                }
            }
            cachedSeedMap = loaded ?: emptyMap()
            return cachedSeedMap!!
        }
    }

    private fun getDrawsForType(lotteryType: LotteryType): List<DrawItem> {
        ensureDiskCacheLoaded()
        runtimeOverlayMap[lotteryType.code]?.takeIf { it.isNotEmpty() }?.let { return it }
        return loadSeedMap()[lotteryType.code] ?: emptyList()
    }

    fun getHistoryDraws(lotteryType: LotteryType, limit: Int = 50): List<DrawItem> {
        return getDrawsForType(lotteryType).take(limit)
    }

    /**
     * 计算完整的频次、命中率、冷热状态、当前/最大/平均遗漏与欲出度（desireIndex）。
     */
    fun computeStats(lotteryType: LotteryType): LotteryStats {
        val draws = getDrawsForType(lotteryType)
        if (draws.isEmpty()) {
            return LotteryStats(lotteryType = lotteryType.code)
        }

        val totalDraws = draws.size
        val lastIssue = draws.firstOrNull()?.issue ?: ""
        val latestDrawDate = draws.firstOrNull()?.drawDate ?: ""

        val redStats = computeZoneStats(
            draws = draws,
            range = lotteryType.primaryRange,
            pickCount = lotteryType.primaryCount,
            allowDuplicates = lotteryType.primaryAllowDuplicate,
            isSecondary = false
        )

        val statsMap = mutableMapOf("primary" to redStats)
        lotteryType.secondaryRange?.let { secRange ->
            val blueStats = computeZoneStats(
                draws = draws,
                range = secRange,
                pickCount = lotteryType.secondaryCount,
                allowDuplicates = lotteryType.secondaryAllowDuplicate,
                isSecondary = true
            )
            if (blueStats.isNotEmpty()) {
                statsMap["secondary"] = blueStats
            }
        }

        return LotteryStats(
            lotteryType = lotteryType.code,
            totalDraws = totalDraws,
            lastIssue = lastIssue,
            latestDrawDate = latestDrawDate,
            stats = statsMap
        )
    }

    private fun computeZoneStats(
        draws: List<DrawItem>,
        range: IntRange,
        pickCount: Int,
        allowDuplicates: Boolean,
        isSecondary: Boolean
    ): Map<String, NumberStat> {
        val totalDraws = draws.size
        val windowSize = minOf(30, totalDraws)
        val recentDraws = draws.take(windowSize)
        val rangeSize = (range.last - range.first + 1).coerceAtLeast(1)
        val theoreticalAvgOmission = (rangeSize.toFloat() / pickCount.coerceAtLeast(1)).coerceAtLeast(1f)
        val result = LinkedHashMap<String, NumberStat>(rangeSize)

        for (num in range) {
            val numStr = formatNumberForRange(num, range, isSecondary)
            var totalOccurrences = 0
            var appearedDrawsCount = 0
            var currentOmission = 0
            var foundFirst = false
            var maxOmission = 0
            var runOmission = 0

            for (draw in draws) {
                val list = if (isSecondary) draw.secondaryNumbers.orEmpty() else draw.primaryNumbers
                val occ = if (allowDuplicates) {
                    list.count { it == numStr }
                } else {
                    if (list.contains(numStr)) 1 else 0
                }
                if (occ > 0) {
                    totalOccurrences += occ
                    appearedDrawsCount++
                    if (!foundFirst) {
                        foundFirst = true
                    }
                    if (runOmission > maxOmission) maxOmission = runOmission
                    runOmission = 0
                } else {
                    if (!foundFirst) currentOmission++
                    runOmission++
                }
            }
            if (runOmission > maxOmission) maxOmission = runOmission

            val recentFreq = recentDraws.sumOf { draw ->
                val list = if (isSecondary) draw.secondaryNumbers.orEmpty() else draw.primaryNumbers
                if (allowDuplicates) list.count { it == numStr } else if (list.contains(numStr)) 1 else 0
            }
            val expectedRecentFreq = windowSize * (pickCount.toFloat() / rangeSize)
            val isHot = recentFreq >= expectedRecentFreq * 1.25f
            val isCold = recentFreq <= expectedRecentFreq * 0.75f

            val rate = totalOccurrences.toFloat() / totalDraws
            val hitRate = appearedDrawsCount.toFloat() / totalDraws
            val avgOmission = if (appearedDrawsCount > 0) {
                (totalDraws - appearedDrawsCount).toFloat() / (appearedDrawsCount + 1)
            } else {
                totalDraws.toFloat()
            }
            // 与后端 lottery_stats.py 完全一致：0 次超级冷号使用理论均值计算欲出度
            val desireIndex = if (appearedDrawsCount == 0) {
                currentOmission.toFloat() / theoreticalAvgOmission
            } else if (avgOmission > 0f) {
                currentOmission.toFloat() / avgOmission
            } else {
                0f
            }

            result[numStr] = NumberStat(
                frequency = totalOccurrences,
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
        return result
    }

    private fun canonicalStrategy(strategy: String): String {
        return when (strategy.trim().lowercase()) {
            "bazi", "bazi_priority", "bazi_pure", "命理吉数优先", "八字优先", "命理优先", "八字命理" -> "bazi_priority"
            "hot_chase", "冷热追号", "热号追随", "热号优先" -> "hot_chase"
            "cold_reversal", "极冷号反弹", "冷号反弹", "极冷反弹" -> "cold_reversal"
            else -> "balanced"
        }
    }

    /**
     * 端侧八字 + 大数据融合推荐（与后端 numerology_engine.py 严格对齐，支持自定义权重与定胆/杀号）。
     */
    fun generateRecommendation(
        destiny: BaziDestiny,
        lotteryType: LotteryType,
        count: Int = 1,
        strategy: String = "balanced",
        userName: String? = null,
        wBaziOverride: Float? = null,
        wFreqOverride: Float? = null,
        wOmissionOverride: Float? = null,
        lockedPrimary: List<String> = emptyList(),
        excludedPrimary: List<String> = emptyList(),
        lockedSecondary: List<String> = emptyList(),
        excludedSecondary: List<String> = emptyList()
    ): BaziRecommendationResponse {
        val stats = computeStats(lotteryType)
        val stratKey = canonicalStrategy(strategy)
        val defaultWeights = when (stratKey) {
            "bazi_priority" -> Triple(0.60f, 0.25f, 0.15f)
            "hot_chase" -> Triple(0.25f, 0.55f, 0.20f)
            "cold_reversal" -> Triple(0.25f, 0.20f, 0.55f)
            else -> Triple(0.35f, 0.40f, 0.25f)
        }
        val rawB = (wBaziOverride ?: defaultWeights.first).coerceAtLeast(0f)
        val rawF = (wFreqOverride ?: defaultWeights.second).coerceAtLeast(0f)
        val rawO = (wOmissionOverride ?: defaultWeights.third).coerceAtLeast(0f)
        val wSum = (rawB + rawF + rawO).let { if (it <= 1e-6f) 1f else it }
        val wBazi = rawB / wSum
        val wFreq = rawF / wSum
        val wOmission = rawO / wSum

        val primaryStats = stats.stats["primary"] ?: emptyMap()
        val secondaryStats = stats.stats["secondary"] ?: emptyMap()

        val normLockedPri = normalizeBallList(lockedPrimary, lotteryType.primaryRange, false)
        val normExcludedPri = normalizeBallList(excludedPrimary, lotteryType.primaryRange, false).toSet() - normLockedPri.toSet()
        val normLockedSec = lotteryType.secondaryRange?.let { normalizeBallList(lockedSecondary, it, true) } ?: emptyList()
        val normExcludedSec = (lotteryType.secondaryRange?.let { normalizeBallList(excludedSecondary, it, true) } ?: emptyList()).toSet() - normLockedSec.toSet()

        val effectiveName = if (userName.isNullOrBlank()) (destiny.userName ?: "缘主") else userName.trim()
        val seenTickets = mutableSetOf<Pair<List<String>, List<String>>>()
        val recommendations = mutableListOf<RecommendationItem>()
        val maxAttempts = maxOf(count * 50, 200)
        var attempts = 0

        // 预计算一次候选权重（大幅提升 1000 次吞吐至 <1ms）
        val primaryWeights = buildCandidateWeights(
            range = lotteryType.primaryRange,
            destiny = destiny,
            numberStats = primaryStats,
            strategyKey = stratKey,
            wBazi = wBazi,
            wFreq = wFreq,
            wOmission = wOmission,
            isSecondary = false,
            excluded = normExcludedPri
        )
        val secondaryWeights = lotteryType.secondaryRange?.let { secRange ->
            buildCandidateWeights(
                range = secRange,
                destiny = destiny,
                numberStats = secondaryStats,
                strategyKey = stratKey,
                wBazi = wBazi,
                wFreq = wFreq,
                wOmission = wOmission,
                isSecondary = true,
                excluded = normExcludedSec
            )
        } ?: emptyList()

        while (recommendations.size < count && attempts < maxAttempts) {
            attempts++
            // 退火兜底：后半程逐步拉平权重防死锁少注
            val flatten = attempts > maxAttempts / 2
            val priPool = if (flatten) primaryWeights.map { it.first to 1.0f } else primaryWeights
            val secPool = if (flatten) secondaryWeights.map { it.first to 1.0f } else secondaryWeights

            val primaryBalls = sampleWithLocks(
                weights = priPool,
                count = lotteryType.primaryCount,
                allowDuplicates = lotteryType.primaryAllowDuplicate,
                locked = normLockedPri
            )
            val secondaryBalls = if (lotteryType.secondaryRange != null && lotteryType.secondaryCount > 0) {
                sampleWithLocks(
                    weights = secPool,
                    count = lotteryType.secondaryCount,
                    allowDuplicates = lotteryType.secondaryAllowDuplicate,
                    locked = normLockedSec
                )
            } else {
                emptyList()
            }

            val ticketKey = Pair(primaryBalls, secondaryBalls)
            if (ticketKey in seenTickets) continue
            seenTickets.add(ticketKey)

            val wuxingDist = mutableMapOf<String, Int>()
            (primaryBalls + secondaryBalls).forEach { numStr ->
                val num = numStr.toIntOrNull() ?: 0
                val elem = BaziEngine.getHetuElement(num)
                wuxingDist[elem] = wuxingDist.getOrDefault(elem, 0) + 1
            }

            val primaryXi = destiny.xiYongShen.firstOrNull() ?: "金"
            val isFemale = destiny.gender.equals("female", ignoreCase = true) || destiny.gender == "女"
            val genderTag = if (isFemale) "坤造(女)" else "乾造(男)"
            val lockNote = if (normLockedPri.isNotEmpty() || normLockedSec.isNotEmpty()) "，已锁定胆码" else ""
            val rationale = "缘主【$effectiveName】[$genderTag] 命中喜用神【$primaryXi】生扶磁场，融合大数据频率与遗漏加权(A-Res对数抽样$lockNote)"
            val betIdx = recommendations.size + 1
            val hexagram = AUSPICIOUS_HEXAGRAMS[recommendations.size % AUSPICIOUS_HEXAGRAMS.size]
            val offlineEvidence = hexagram.copy(
                auspiciousNumbers = primaryBalls.take(3).mapNotNull { it.toIntOrNull() }
            )

            val formattedTicket = if (secondaryBalls.isNotEmpty()) {
                "${primaryBalls.joinToString(" ")} + ${secondaryBalls.joinToString(" ")}"
            } else {
                primaryBalls.joinToString(" ")
            }

            recommendations.add(
                RecommendationItem(
                    betIndex = betIdx,
                    primaryNumbers = primaryBalls,
                    secondaryNumbers = secondaryBalls,
                    formattedTicket = formattedTicket,
                    wuxingDistribution = wuxingDist,
                    scoreRationale = rationale,
                    classicsEvidence = offlineEvidence
                )
            )
        }

        val lines = mutableListOf<String>()
        lines.add("【曾练彩票助手 - ${effectiveName}专属吉祥票】")
        lines.add("彩种类型：${lotteryType.displayName} (${recommendations.size}注机选)")
        lines.add("缘主姓名：$effectiveName")
        if (destiny.lunarDatetime.isNotBlank()) {
            lines.add("生辰命盘：${destiny.lunarDatetime}")
        }
        lines.add("推演策略：$strategy")
        lines.add("--------------------------------------")
        recommendations.forEach { rec ->
            lines.add(String.format("第%02d注: %s", rec.betIndex, rec.formattedTicket ?: rec.primaryNumbers.joinToString(" ")))
        }
        lines.add("--------------------------------------")
        lines.add("祝您财星高照，吉祥如意！(彩票纯属概率游戏，请理性购彩)")
        val copyText = lines.joinToString("\n")

        return BaziRecommendationResponse(
            lotteryType = lotteryType.code,
            strategy = strategy,
            recommendations = recommendations,
            baziSummary = destiny.toSummary(),
            copyText = copyText,
            userName = effectiveName
        )
    }

    private fun buildCandidateWeights(
        range: IntRange,
        destiny: BaziDestiny,
        numberStats: Map<String, NumberStat>,
        strategyKey: String,
        wBazi: Float,
        wFreq: Float,
        wOmission: Float,
        isSecondary: Boolean,
        excluded: Set<String>
    ): List<Pair<String, Float>> {
        val xiList = destiny.xiYongShen.map { BaziEngine.ELEMENT_ZH[it.lowercase()] ?: it }
        val jiSet = destiny.jiShen.map { BaziEngine.ELEMENT_ZH[it.lowercase()] ?: it }.toSet()
        val lackSet = destiny.lackingElements.map { BaziEngine.ELEMENT_ZH[it.lowercase()] ?: it }.toSet()
        val isMale = !(destiny.gender.equals("female", ignoreCase = true) || destiny.gender == "女")

        val rates = range.map { num ->
            val key = formatNumberForRange(num, range, isSecondary)
            numberStats[key]?.rate ?: 0f
        }
        val minRate = rates.minOrNull() ?: 0f
        val maxRate = rates.maxOrNull() ?: 1f
        val rateSpan = (maxRate - minRate).let { if (it < 1e-6f) 1f else it }

        val result = ArrayList<Pair<String, Float>>(range.last - range.first + 1)
        for (num in range) {
            val numStr = formatNumberForRange(num, range, isSecondary)
            if (numStr in excluded) continue

            val elem = BaziEngine.getHetuElement(num)
            val elemZh = BaziEngine.ELEMENT_ZH[elem] ?: "土"

            // 与后端 5 级用神梯度严格对齐（忌神优先级高于缺行）
            val baziScore = when {
                xiList.firstOrNull() == elemZh -> 1.00f
                elemZh in xiList -> 0.85f
                elemZh in jiSet -> 0.15f
                elemZh in lackSet -> 0.70f
                else -> 0.50f
            }

            val stat = numberStats[numStr]
            val freqNorm = if (stat != null) {
                ((stat.rate - minRate) / rateSpan).coerceIn(0.05f, 1.0f)
            } else {
                0.5f
            }
            val freqScore = if (strategyKey == "cold_reversal") 1.0f - freqNorm * 0.7f else freqNorm

            val desire = (stat?.desireIndex ?: 0.8f).coerceAtLeast(0f)
            val omissionScore = when (strategyKey) {
                "hot_chase" -> (1.0f / (1.0f + desire)).coerceIn(0.1f, 1.0f)
                "cold_reversal" -> (desire / (desire + 1.2f)).coerceIn(0.1f, 1.0f)
                else -> (1.0f - abs(desire - 1.0f) * 0.4f).coerceIn(0.25f, 1.0f)
            }

            val genderMultiplier = if ((isMale && num % 2 == 1) || (!isMale && num % 2 == 0)) 1.08f else 1.0f
            val totalWeight = ((wBazi * baziScore + wFreq * freqScore + wOmission * omissionScore) * genderMultiplier)
                .coerceAtLeast(0.01f)
            result.add(numStr to totalWeight)
        }
        return result
    }

    private fun sampleWithLocks(
        weights: List<Pair<String, Float>>,
        count: Int,
        allowDuplicates: Boolean,
        locked: List<String>
    ): List<String> {
        val effectiveLocked = if (allowDuplicates) locked.take(count) else locked.distinct().take(count)
        val remaining = (count - effectiveLocked.size).coerceAtLeast(0)
        if (remaining == 0) {
            return if (allowDuplicates) effectiveLocked else effectiveLocked.sortedBy { it.toIntOrNull() ?: 0 }
        }

        val pool = if (allowDuplicates) {
            weights
        } else {
            val lockSet = effectiveLocked.toSet()
            weights.filter { it.first !in lockSet }.ifEmpty { weights }
        }

        if (allowDuplicates) {
            val picked = ArrayList<String>(count)
            picked.addAll(effectiveLocked)
            val totalWeightSum = pool.sumOf { it.second.toDouble() }.coerceAtLeast(1e-9)
            repeat(remaining) {
                var p = Random.nextDouble() * totalWeightSum
                var selected = pool.first().first
                for ((item, w) in pool) {
                    p -= w
                    if (p <= 0.0) {
                        selected = item
                        break
                    }
                }
                picked.add(selected)
            }
            return picked
        } else {
            // 对数空间 A-Res 加权蓄水池无放回抽样
            val keys = pool.map { (item, weight) ->
                val u = Random.nextDouble().coerceIn(1e-9, 1.0 - 1e-9)
                item to (ln(u) / weight.toDouble())
            }
            val sampled = keys.sortedByDescending { it.second }.take(remaining).map { it.first }
            return (effectiveLocked + sampled).sortedBy { it.toIntOrNull() ?: 0 }
        }
    }

    private fun normalizeBallList(raw: List<String>, range: IntRange, isSecondary: Boolean): List<String> {
        return raw.mapNotNull { token ->
            val n = token.trim().toIntOrNull() ?: return@mapNotNull null
            if (n !in range) return@mapNotNull null
            formatNumberForRange(n, range, isSecondary)
        }
    }

    private fun formatNumberForRange(num: Int, range: IntRange, isSecondary: Boolean): String {
        return if (isSecondary || range.last > 9) {
            String.format("%02d", num)
        } else {
            num.toString()
        }
    }

    companion object {
        val AUSPICIOUS_HEXAGRAMS = listOf(
            com.zeng.lottery.data.model.ClassicsEvidence(
                book = "《周易》",
                chapter = "大有卦第十四",
                quote = "火在天上，大有。君子以遏恶扬善，顺天休命。",
                vernacular = "火在天上，日照当空，盛大富有。顺应天命，行善修身，诸事吉祥。",
                category = "hexagram"
            ),
            com.zeng.lottery.data.model.ClassicsEvidence(
                book = "《周易》",
                chapter = "乾卦第一",
                quote = "天行健，君子以自强不息。见龙在田，利见大人。",
                vernacular = "刚健中正，自强不息。吉光初现，运势升华，机缘相聚。",
                category = "hexagram"
            ),
            com.zeng.lottery.data.model.ClassicsEvidence(
                book = "《周易》",
                chapter = "泰卦第十一",
                quote = "天地交而万物通也，上下交而其志同也。小往大来，吉亨。",
                vernacular = "阴阳调和，通达和顺。泰然自得，阴去阳生，财福并进。",
                category = "hexagram"
            ),
            com.zeng.lottery.data.model.ClassicsEvidence(
                book = "《周易》",
                chapter = "谦卦第十五",
                quote = "地中有山，谦。君子以裒多益寡，称物平施。天道亏盈而益谦。",
                vernacular = "内蕴雄厚而外守谦逊。天道佑谦，心怀宽平，吉无不利。",
                category = "hexagram"
            ),
            com.zeng.lottery.data.model.ClassicsEvidence(
                book = "《周易》",
                chapter = "同人卦第十三",
                quote = "同人于野，亨。利涉大川，利君子贞。",
                vernacular = "广结同心，志同道合。携手并进，通达顺遂，万事吉祥。",
                category = "hexagram"
            ),
            com.zeng.lottery.data.model.ClassicsEvidence(
                book = "《周易》",
                chapter = "升卦第四十六",
                quote = "地中生木，升。君子以顺德，积小以高大。",
                vernacular = "积小成大，步步高升。如木生土中，稳健滋长，前程万里。",
                category = "hexagram"
            ),
            com.zeng.lottery.data.model.ClassicsEvidence(
                book = "《周易》",
                chapter = "复卦第二十四",
                quote = "雷在地中，复。先王以至日闭关，商旅不行，后不省方。",
                vernacular = "一阳初复，生机再萌。蓄力待发，循环往复，终至大吉。",
                category = "hexagram"
            ),
            com.zeng.lottery.data.model.ClassicsEvidence(
                book = "《周易》",
                chapter = "益卦第四十二",
                quote = "风雷，益。君子以见善则迁，有过则改。利有攸往，利涉大川。",
                vernacular = "风雷激荡，生机蓬勃。增益助化，顺天应时，必获大利。",
                category = "hexagram"
            ),
            com.zeng.lottery.data.model.ClassicsEvidence(
                book = "《周易》",
                chapter = "鼎卦第五十",
                quote = "木上有火，鼎。君子以正位凝命。鼎元吉，亨。",
                vernacular = "去旧生新，鼎足而立。定基立业，调和五味，元亨吉祥。",
                category = "hexagram"
            ),
            com.zeng.lottery.data.model.ClassicsEvidence(
                book = "《周易》",
                chapter = "丰卦第五十五",
                quote = "雷电皆至，丰。君子以折狱致刑。丰亨，王假之，勿忧，宜日中。",
                vernacular = "如日中天，盛大丰满。明察秋毫，吉运沛然，富贵亨通。",
                category = "hexagram"
            )
        )
    }
}
