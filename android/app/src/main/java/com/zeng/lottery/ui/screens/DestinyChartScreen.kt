package com.zeng.lottery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.zeng.lottery.ui.theme.ImperialRed

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

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // User Name & Destiny Header Card with Four Pillars
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
                        text = "【$userName】专属命盘格局",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = FortuneGold
                    )
                    Text(
                        text = "八字原局",
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

                // Four Pillars Badges Row
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

        // 1. Pentagon Polar Radar Chart Canvas
        WuxingRadarChart(
            scores = destiny.wuxingScores,
            xiYongShen = destiny.displayXiYongShen,
            dayMaster = destiny.dayMaster,
            strength = destiny.strength
        )

        // 2. Day Master Strength & Xi Yong Shen Analysis Card
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

                // Day Master Strength Meter
                val isStrong = destiny.strength == "身强"
                val strengthRatio = (destiny.strengthRatio ?: if (isStrong) 0.68f else 0.42f).coerceIn(0.05f, 0.98f)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "元神能量比重: ${if (isStrong) "亢旺偏强" else "偏弱待补"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${(strengthRatio * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isStrong) FortuneGold else ImperialRed
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { strengthRatio },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = if (isStrong) FortuneGold else ImperialRed,
                        trackColor = Color.Gray.copy(alpha = 0.25f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Xi Yong Shen (喜用神) Chips
                Text(
                    text = "吉神推荐 (喜用神 - 生旺调候):",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    destiny.displayXiYongShen.forEach { elem ->
                        val color = getElementColor(elem)
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    text = "★ 喜神【$elem】",
                                    fontWeight = FontWeight.Bold,
                                    color = color
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = color.copy(alpha = 0.15f)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Lacking Elements (缺失五行)
                if (destiny.displayLackingElements.isNotEmpty()) {
                    Text(
                        text = "命理缺失五行 (急需数理弥补):",
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
                                    Text(
                                        text = "缺失: $elem",
                                        fontWeight = FontWeight.SemiBold,
                                        color = color
                                    )
                                },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = Color.Gray.copy(alpha = 0.15f)
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Ji Shen (忌神)
                if (destiny.displayJiShen.isNotEmpty()) {
                    Text(
                        text = "偏旺忌神 (避讳偏冷):",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        destiny.displayJiShen.forEach { elem ->
                            SuggestionChip(
                                onClick = {},
                                label = {
                                    Text(
                                        text = "忌: $elem",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                        }
                    }
                }
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
    val cleanElem = when {
        element.contains("fire") || element.contains("water") || element.contains("wood") || element.contains("metal") || element.contains("earth") -> {
            element.replace("metal", "金").replace("wood", "木").replace("water", "水").replace("fire", "火").replace("earth", "土")
        }
        else -> element
    }
    val elemZh = BaziEngine.ELEMENT_ZH[cleanElem.lowercase()] ?: cleanElem
    val elemColor = getElementColor(elemZh)
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

