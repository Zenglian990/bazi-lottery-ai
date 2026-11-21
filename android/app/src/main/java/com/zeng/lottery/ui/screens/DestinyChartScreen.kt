package com.zeng.lottery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeng.lottery.data.local.BaziEngine
import com.zeng.lottery.data.model.BaziDestiny
import com.zeng.lottery.data.model.DayunDetail
import com.zeng.lottery.data.model.NameNumerology
import com.zeng.lottery.data.model.displayJiShen
import com.zeng.lottery.data.model.displayLackingElements
import com.zeng.lottery.data.model.displayXiYongShen
import com.zeng.lottery.ui.components.WuxingRadarChart
import com.zeng.lottery.ui.theme.ElementEarth
import com.zeng.lottery.ui.theme.ElementFire
import com.zeng.lottery.ui.theme.ElementMetal
import com.zeng.lottery.ui.theme.ElementWater
import com.zeng.lottery.ui.theme.ElementWood
import com.zeng.lottery.ui.theme.FortuneGold
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DestinyChartScreen(
    destiny: BaziDestiny?,
    userName: String = "缘主",
    modifier: Modifier = Modifier
) {
    if (destiny == null) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "正在推演【$userName】生辰八字命盘...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = FortuneGold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "可前往「生辰排盘」标签页调整您的农历生辰与时辰",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val currentYear = LocalDate.now().year

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. 命盘总览与四柱八字
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "【${userName.ifBlank { "缘主" }}】专属命盘格局",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = FortuneGold
                    )
                    Text(
                        text = if (destiny.gender.equals("female", ignoreCase = true)) "坤造原局" else "乾造原局",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (destiny.lunarDatetime.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = destiny.lunarDatetime,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val fp = destiny.fourPillars
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(vertical = 10.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    ChartPillarBadge(label = "年柱", value = fp.year.fullText, element = fp.year.element)
                    ChartPillarBadge(label = "月柱", value = fp.month.fullText, element = fp.month.element)
                    ChartPillarBadge(label = "日柱 (元神)", value = fp.day.fullText, element = fp.day.element, isDayMaster = true)
                    ChartPillarBadge(label = "时柱", value = fp.hour.fullText, element = fp.hour.element)
                }
            }
        }

        // 2. 五角五行能量雷达图（含外顶点中文标注）
        WuxingRadarChart(
            scores = destiny.wuxingScores,
            xiYongShen = destiny.displayXiYongShen,
            dayMaster = destiny.dayMaster,
            strength = destiny.strength,
            strengthLevel = destiny.strengthLevel
        )

        // 3. 元神旺衰与用神格局诊断
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "元神旺衰与用神格局诊断",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(10.dp))

                val isStrong = destiny.strength == "身强"
                val strengthRatio = (destiny.strengthRatio ?: if (isStrong) 0.65f else 0.42f).coerceIn(0.05f, 0.98f)
                val levelText = destiny.strengthLevel?.takeIf { it.isNotBlank() }
                    ?: if (isStrong) "偏强" else "偏弱"

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "元神能量比重: ${destiny.strength} · $levelText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${(strengthRatio * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isStrong) FortuneGold else MaterialTheme.colorScheme.secondary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { strengthRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = if (isStrong) FortuneGold else MaterialTheme.colorScheme.secondary,
                        trackColor = Color.Gray.copy(alpha = 0.25f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "吉神推荐 (喜用神 · 按原局急需程度排序):",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    destiny.displayXiYongShen.forEachIndexed { idx, elem ->
                        val color = getElementColor(elem)
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    text = if (idx == 0) "★ 首用【$elem】" else "喜神【$elem】",
                                    fontWeight = FontWeight.Bold,
                                    color = color
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = color.copy(alpha = 0.16f)
                            )
                        )
                    }
                }

                if (destiny.displayLackingElements.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "命理缺失五行 (低于 5 分):",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        destiny.displayLackingElements.forEach { elem ->
                            val color = getElementColor(elem)
                            SuggestionChip(
                                onClick = {},
                                label = {
                                    Text("缺失: $elem", fontWeight = FontWeight.SemiBold, color = color)
                                },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = Color.Gray.copy(alpha = 0.15f)
                                )
                            )
                        }
                    }
                }

                if (destiny.displayJiShen.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "偏旺忌神 (选号权重主动避讳):",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        destiny.displayJiShen.forEach { elem ->
                            SuggestionChip(
                                onClick = {},
                                label = { Text("忌: $elem", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            )
                        }
                    }
                }
            }
        }

        // 4. 八步大运时间轴卡片（释放云端与端侧大运详表）
        val dayunItems: List<DayunDetail> = when {
            !destiny.dayunDetails.isNullOrEmpty() -> destiny.dayunDetails
            !destiny.dayunList.isNullOrEmpty() -> destiny.dayunList.mapIndexed { idx, gz ->
                DayunDetail(ganzhi = gz, startAge = (destiny.dayunStartAge ?: 3) + idx * 10)
            }
            else -> emptyList()
        }
        if (dayunItems.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "大运流年时间轴 (八步运程)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val dirText = if (destiny.dayunForward == false) "逆行" else "顺行"
                        val startAgeText = destiny.dayunStartAge?.let { " · ${it}岁起运" } ?: ""
                        Text(
                            text = "$dirText$startAgeText",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = FortuneGold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        dayunItems.forEach { dy ->
                            val isCurrent = dy.startYear > 0 && currentYear in dy.startYear..dy.endYear
                            val stem = dy.ganzhi.take(1)
                            val branch = dy.ganzhi.drop(1).take(1)
                            val stemZh = BaziEngine.ELEMENT_ZH[BaziEngine.STEM_ELEMENTS[stem]] ?: ""
                            val branchZh = BaziEngine.ELEMENT_ZH[BaziEngine.BRANCH_ELEMENTS[branch]] ?: ""

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(74.dp)
                                    .background(
                                        color = if (isCurrent) FortuneGold.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .border(
                                        width = if (isCurrent) 1.5.dp else 0.5.dp,
                                        color = if (isCurrent) FortuneGold else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .padding(vertical = 8.dp, horizontal = 6.dp)
                            ) {
                                Text(
                                    text = if (isCurrent) "当前大运" else "${dy.startAge}岁起",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCurrent) FortuneGold else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = dy.ganzhi,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isCurrent) FortuneGold else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "$stemZh$branchZh",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = getElementColor(stemZh)
                                )
                                if (dy.startYear > 0 && dy.endYear > 0) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${dy.startYear}-${dy.endYear % 100}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. 姓名五格数理剖析卡片（消费后端 name_numerology）
        destiny.nameNumerology?.let { nm ->
            NameNumerologyCard(nm = nm)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NameNumerologyCard(nm: NameNumerology) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "姓名五格数理剖析 (${nm.surname}${nm.givenName})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = nm.strokeStandard.ifBlank { "简体笔画" },
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = FortuneGold
                )
            }

            if (nm.fiveGrids.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                val gridOrder = listOf(
                    "tian_ge" to "天格",
                    "ren_ge" to "人格",
                    "di_ge" to "地格",
                    "wai_ge" to "外格",
                    "zong_ge" to "总格"
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    gridOrder.forEach { (key, title) ->
                        val item = nm.fiveGrids[key]
                        if (item != null) {
                            val fortuneColor = when (item.fortune) {
                                "大吉" -> FortuneGold
                                "吉" -> ElementWood
                                "凶" -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(title, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${item.value}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                                Box(
                                    modifier = Modifier
                                        .background(fortuneColor.copy(alpha = 0.16f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(item.fortune, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = fortuneColor)
                                }
                                if (item.wuxing.isNotBlank()) {
                                    Text(
                                        text = BaziEngine.ELEMENT_ZH[item.wuxing.lowercase()] ?: item.wuxing,
                                        fontSize = 10.sp,
                                        color = getElementColor(item.wuxing)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (nm.nameLuckyNumbers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "姓名吉数尾码:",
                        style = MaterialTheme.typography.labelSmall,
                        color = FortuneGold,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    nm.nameLuckyNumbers.forEach { n ->
                        Box(
                            modifier = Modifier
                                .background(FortuneGold.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = String.format("%02d", n),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = FortuneGold
                            )
                        }
                    }
                }
            }

            if (nm.summary.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = nm.summary,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun getElementColor(elem: String): Color {
    val zh = BaziEngine.ELEMENT_ZH[elem.lowercase()] ?: elem
    return when (zh) {
        "金" -> ElementMetal
        "木" -> ElementWood
        "水" -> ElementWater
        "火" -> ElementFire
        "土" -> ElementEarth
        else -> FortuneGold
    }
}

@Composable
private fun ChartPillarBadge(
    label: String,
    value: String,
    element: String,
    isDayMaster: Boolean = false
) {
    val cleanElem = element
        .replace("metal", "金")
        .replace("wood", "木")
        .replace("water", "水")
        .replace("fire", "火")
        .replace("earth", "土")
    val elemZh = BaziEngine.ELEMENT_ZH[cleanElem.lowercase()] ?: cleanElem
    val elemColor = getElementColor(elemZh.take(1))
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isDayMaster) FortuneGold else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = if (isDayMaster) FortuneGold else MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = elemZh,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = elemColor
        )
    }
}
