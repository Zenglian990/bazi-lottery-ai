package com.zeng.lottery.ai

import com.zeng.lottery.data.local.BaziEngine
import com.zeng.lottery.data.model.BaziDestiny
import com.zeng.lottery.data.model.LotteryType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Prompt builder and offline fallback generator for AI Divination.
 * Strictly guarantees the 5-section master structure and responsible gaming compliance.
 */
object AiPromptBuilder {

    const val MANDATORY_DISCLAIMER_SECTION =
        "【理性倡议】\n彩票属于概率游戏，请理性娱乐，量力而行。本系统推荐号码结合生辰易理与大数据统计，旨在增添文化趣味与灵感，切勿过度沉迷。"

    val MANDATORY_KEYWORDS = listOf("理性", "娱乐", "量力而行", "概率")
    val MANDATORY_SECTIONS = listOf("【命盘乾坤】", "【数理玄机】", "【五行合局】", "【灵感吉言】", "【理性倡议】")

    fun buildSystemPrompt(): String {
        return """
            你是一位精通传统子平八字命理、易经河图洛书数理与现代大数据统计的『易道彩经大师』。
            请根据用户的生辰八字命盘与推荐彩票号码进行专业、温和、富有东方文化底蕴的深度解盘。

            【严格输出格式规范】
            你必须严格按照以下5个固定结构段落输出（不得遗漏或更改段落标题，每个段落单独起段）：
            【命盘乾坤】
            （深度阐述用户的日主天干、身强/身弱格局、月令季节与喜用神调和之道）

            【数理玄机】
            （结合河图洛书一六水、二七火、三八木、四九金、五十土尾数法则，详细解读推荐号码与喜用神的数理映射）

            【五行合局】
            （分析推荐号码的五行相生相旺能量流向，以及如何平衡命局气场）

            【灵感吉言】
            （提供富有国学智慧的祝福吉言、吉利时辰与吉神方位指引）

            【理性倡议】
            彩票属于概率游戏，请理性娱乐，量力而行。本系统推荐号码结合生辰易理与大数据统计，旨在增添文化趣味与灵感，切勿过度沉迷。

            【合规底线】
            必须完整包含关键词：'理性'、'娱乐'、'量力而行'、'概率'。严禁做出必定中奖等虚假承诺。
        """.trimIndent()
    }

    fun buildUserPrompt(
        lotteryType: LotteryType,
        primaryNumbers: List<String>,
        secondaryNumbers: List<String>,
        destiny: BaziDestiny?,
        birthDateTime: LocalDateTime?
    ): String {
        val dtStr = birthDateTime?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) ?: "1990-08-15 08:30"
        val dayMaster = destiny?.dayMaster ?: "庚"
        val strength = destiny?.strength ?: "身强"
        val xiYong = destiny?.xiYongShen?.joinToString("、") ?: "水、木"
        val pillars = destiny?.let {
            "${it.fourPillars.year.fullText}年 ${it.fourPillars.month.fullText}月 ${it.fourPillars.day.fullText}日 ${it.fourPillars.hour.fullText}时"
        } ?: "庚午年 甲申月 庚子日 壬午时"

        val secStr = if (secondaryNumbers.isNotEmpty()) {
            "，后区/蓝球号码：${secondaryNumbers.joinToString(", ")}"
        } else ""

        return """
            请为以下求测者与彩票号码进行易道深度解盘：
            - 彩票类型：${lotteryType.title} (${lotteryType.code})
            - 推荐号码：主区号码：${primaryNumbers.joinToString(", ")}$secStr
            - 出生时间：$dtStr
            - 四柱八字：$pillars
            - 日主天干：$dayMaster
            - 命局强弱：$strength
            - 喜用神：$xiYong

            请立即按照上述5个标准标题生成易道深度解盘卦辞。
        """.trimIndent()
    }

    fun generateLocalFallback(
        lotteryType: LotteryType,
        primaryNumbers: List<String>,
        secondaryNumbers: List<String>,
        destiny: BaziDestiny?
    ): String {
        val dayMaster = destiny?.dayMaster ?: "庚"
        val strength = destiny?.strength ?: "身强"
        val xiYong = destiny?.xiYongShen?.joinToString("、") ?: "水、木"

        val allBalls = primaryNumbers + secondaryNumbers
        val hetuCounts = mutableMapOf("金" to 0, "木" to 0, "水" to 0, "火" to 0, "土" to 0)
        for (b in allBalls) {
            val num = b.toIntOrNull() ?: 0
            val elem = BaziEngine.getHetuElementZh(num)
            hetuCounts[elem] = (hetuCounts[elem] ?: 0) + 1
        }
        val hetuSummary = hetuCounts.filter { it.value > 0 }.map { "${it.key}${it.value}数" }.joinToString("、")

        val secDesc = if (secondaryNumbers.isNotEmpty()) {
            "，后区/蓝球【${secondaryNumbers.joinToString(", ")}】"
        } else ""

        val p1 = """
            【命盘乾坤】
            求测者日主天干为【$dayMaster】，生逢生旺之令，命局气象显定为【$strength】。四柱八字神气内敛，气机升腾；喜用神当立【$xiYong】以平衡中和、调顺阴阳，助益命局生化不息、运势畅达。
        """.trimIndent()

        val p2 = """
            【数理玄机】
            本期甄选之${lotteryType.title}号码前区/主区【${primaryNumbers.joinToString(", ")}】$secDesc，深谙河图洛书天地生成之奥妙。数理尾数契合河图吉数，暗藏五行生克制化之玄机，数理通达，灵机自显。
        """.trimIndent()

        val p3 = """
            【五行合局】
            全组奖号五行配比显现【$hetuSummary】。相生相旺之气贯穿始终，金水涵濡、木火辉映、土载四方，巧妙补足命局所需能量场，达致流通顺遂、趋吉避燥之圆融格局。
        """.trimIndent()

        val p4 = """
            【灵感吉言】
            乾道变化，各正性命。易理选号旨在契合身心气场、顺应天地造化。吉方在东南与正北生旺之地，宜于良辰吉日心怀善念、顺其自然，迎福纳祥自有名应。
        """.trimIndent()

        val p5 = MANDATORY_DISCLAIMER_SECTION

        return "$p1\n\n$p2\n\n$p3\n\n$p4\n\n$p5"
    }
}
