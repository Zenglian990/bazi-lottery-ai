package com.zeng.lottery.data.local

import com.zeng.lottery.data.model.DrawItem
import com.zeng.lottery.data.model.LotteryType

/**
 * 单注收藏票自动对奖核验结果。
 */
data class PrizeCheckResult(
    val issue: String,
    val drawDate: String,
    val isWon: Boolean,
    val prizeLevel: String,
    val referencePrize: String,
    val primaryHitCount: Int,
    val secondaryHitCount: Int,
    val matchedPrimaryIndices: Set<Int> = emptySet(),
    val matchedSecondaryIndices: Set<Int> = emptySet()
)

/**
 * 纯 Kotlin 中国四大官方彩种精准自动对奖引擎。
 * 规则依据中国福利彩票（双色球、福彩3D）与中国体育彩票（超级大乐透、七星彩）现行官方中奖条件。
 */
object PrizeChecker {

    fun checkTicket(
        lotteryType: LotteryType,
        ticketPrimary: List<Int>,
        ticketSecondary: List<Int>,
        draw: DrawItem
    ): PrizeCheckResult {
        val drawPrimary = draw.primaryNumbers.mapNotNull { it.trim().toIntOrNull() }
        val drawSecondary = draw.secondaryNumbers.orEmpty().mapNotNull { it.trim().toIntOrNull() }

        return when (lotteryType) {
            LotteryType.SSQ -> checkSsq(ticketPrimary, ticketSecondary, drawPrimary, drawSecondary, draw)
            LotteryType.DLT -> checkDlt(ticketPrimary, ticketSecondary, drawPrimary, drawSecondary, draw)
            LotteryType.FC3D -> checkFc3d(ticketPrimary, drawPrimary, draw)
            LotteryType.QXC -> checkQxc(ticketPrimary, ticketSecondary, drawPrimary, drawSecondary, draw)
        }
    }

    private fun checkSsq(
        ticketRed: List<Int>,
        ticketBlue: List<Int>,
        drawRed: List<Int>,
        drawBlue: List<Int>,
        draw: DrawItem
    ): PrizeCheckResult {
        val drawRedSet = drawRed.toSet()
        val drawBlueSet = drawBlue.toSet()
        val matchedRedIdx = ticketRed.indices.filter { ticketRed[it] in drawRedSet }.toSet()
        val matchedBlueIdx = ticketBlue.indices.filter { ticketBlue[it] in drawBlueSet }.toSet()
        val redHits = matchedRedIdx.size
        val blueHits = matchedBlueIdx.size

        val (level, prize) = when {
            redHits == 6 && blueHits == 1 -> "一等奖" to "浮动最高 1000 万元"
            redHits == 6 && blueHits == 0 -> "二等奖" to "浮动大奖"
            redHits == 5 && blueHits == 1 -> "三等奖" to "3,000 元"
            (redHits == 5 && blueHits == 0) || (redHits == 4 && blueHits == 1) -> "四等奖" to "200 元"
            (redHits == 4 && blueHits == 0) || (redHits == 3 && blueHits == 1) -> "五等奖" to "10 元"
            blueHits == 1 -> "六等奖" to "5 元"
            else -> "未中奖" to "0 元"
        }

        return PrizeCheckResult(
            issue = draw.issue,
            drawDate = draw.drawDate,
            isWon = level != "未中奖",
            prizeLevel = level,
            referencePrize = prize,
            primaryHitCount = redHits,
            secondaryHitCount = blueHits,
            matchedPrimaryIndices = matchedRedIdx,
            matchedSecondaryIndices = matchedBlueIdx
        )
    }

    private fun checkDlt(
        ticketFront: List<Int>,
        ticketBack: List<Int>,
        drawFront: List<Int>,
        drawBack: List<Int>,
        draw: DrawItem
    ): PrizeCheckResult {
        val drawFrontSet = drawFront.toSet()
        val drawBackSet = drawBack.toSet()
        val matchedFrontIdx = ticketFront.indices.filter { ticketFront[it] in drawFrontSet }.toSet()
        val matchedBackIdx = ticketBack.indices.filter { ticketBack[it] in drawBackSet }.toSet()
        val f = matchedFrontIdx.size
        val b = matchedBackIdx.size

        val (level, prize) = when {
            f == 5 && b == 2 -> "一等奖" to "浮动最高 1000 万元"
            f == 5 && b == 1 -> "二等奖" to "浮动大奖"
            f == 5 && b == 0 -> "三等奖" to "10,000 元"
            f == 4 && b == 2 -> "四等奖" to "3,000 元"
            f == 4 && b == 1 -> "五等奖" to "300 元"
            f == 3 && b == 2 -> "六等奖" to "200 元"
            f == 4 && b == 0 -> "七等奖" to "100 元"
            (f == 3 && b == 1) || (f == 2 && b == 2) -> "八等奖" to "15 元"
            (f == 3 && b == 0) || (f == 2 && b == 1) || (f == 1 && b == 2) || (f == 0 && b == 2) -> "九等奖" to "5 元"
            else -> "未中奖" to "0 元"
        }

        return PrizeCheckResult(
            issue = draw.issue,
            drawDate = draw.drawDate,
            isWon = level != "未中奖",
            prizeLevel = level,
            referencePrize = prize,
            primaryHitCount = f,
            secondaryHitCount = b,
            matchedPrimaryIndices = matchedFrontIdx,
            matchedSecondaryIndices = matchedBackIdx
        )
    }

    private fun checkFc3d(
        ticketDigits: List<Int>,
        drawDigits: List<Int>,
        draw: DrawItem
    ): PrizeCheckResult {
        if (ticketDigits.size != 3 || drawDigits.size != 3) {
            return PrizeCheckResult(draw.issue, draw.drawDate, false, "未中奖", "0 元", 0, 0)
        }
        val positionalMatched = (0 until 3).filter { ticketDigits[it] == drawDigits[it] }.toSet()
        val exactMatch = positionalMatched.size == 3
        val sortedMatch = ticketDigits.sorted() == drawDigits.sorted()
        val distinctCount = drawDigits.toSet().size

        val (level, prize, matchedIdx) = when {
            exactMatch -> Triple("直选中奖", "1,040 元", setOf(0, 1, 2))
            sortedMatch && distinctCount == 2 -> Triple("组选三命中", "346 元", setOf(0, 1, 2))
            sortedMatch && distinctCount == 3 -> Triple("组选六命中", "173 元", setOf(0, 1, 2))
            else -> Triple("未中奖", "0 元", positionalMatched)
        }

        return PrizeCheckResult(
            issue = draw.issue,
            drawDate = draw.drawDate,
            isWon = level != "未中奖",
            prizeLevel = level,
            referencePrize = prize,
            primaryHitCount = if (sortedMatch) 3 else positionalMatched.size,
            secondaryHitCount = 0,
            matchedPrimaryIndices = matchedIdx,
            matchedSecondaryIndices = emptySet()
        )
    }

    private fun checkQxc(
        ticketFront: List<Int>,
        ticketLast: List<Int>,
        drawFront: List<Int>,
        drawLast: List<Int>,
        draw: DrawItem
    ): PrizeCheckResult {
        // 新规则七星彩：前6位按位置严格对齐比对 + 第7位(0-14)单独比对
        val frontCount = minOf(ticketFront.size, drawFront.size)
        val matchedFrontIdx = (0 until frontCount).filter { ticketFront[it] == drawFront[it] }.toSet()
        val matchedLastIdx = if (ticketLast.isNotEmpty() && drawLast.isNotEmpty() && ticketLast[0] == drawLast[0]) {
            setOf(0)
        } else {
            emptySet()
        }

        val f = matchedFrontIdx.size
        val b = matchedLastIdx.size

        val (level, prize) = when {
            f == 6 && b == 1 -> "一等奖" to "浮动最高 500 万元"
            f == 6 && b == 0 -> "二等奖" to "浮动大奖"
            f == 5 && b == 1 -> "三等奖" to "3,000 元"
            (f == 5 && b == 0) || (f == 4 && b == 1) -> "四等奖" to "500 元"
            (f == 4 && b == 0) || (f == 3 && b == 1) -> "五等奖" to "30 元"
            (f == 3 && b == 0) || (f == 2 && b == 1) || (f == 1 && b == 1) || (f == 0 && b == 1) -> "六等奖" to "5 元"
            else -> "未中奖" to "0 元"
        }

        return PrizeCheckResult(
            issue = draw.issue,
            drawDate = draw.drawDate,
            isWon = level != "未中奖",
            prizeLevel = level,
            referencePrize = prize,
            primaryHitCount = f,
            secondaryHitCount = b,
            matchedPrimaryIndices = matchedFrontIdx,
            matchedSecondaryIndices = matchedLastIdx
        )
    }
}
