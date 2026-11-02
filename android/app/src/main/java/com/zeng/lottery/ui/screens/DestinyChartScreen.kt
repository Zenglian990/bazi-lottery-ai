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
import com.zeng.lottery.data.model.BaziDestiny
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
    modifier: Modifier = Modifier
) {
    if (destiny == null) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Pentagon Polar Radar Chart Canvas
        WuxingRadarChart(
            scores = destiny.wuxingScores,
            xiYongShen = destiny.xiYongShen,
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
                val strengthRatio = if (isStrong) 0.68f else 0.42f
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
                    destiny.xiYongShen.forEach { elem ->
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
                if (destiny.lackingElements.isNotEmpty()) {
                    Text(
                        text = "命理缺失五行 (急需数理弥补):",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        destiny.lackingElements.forEach { elem ->
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
                if (destiny.jiShen.isNotEmpty()) {
                    Text(
                        text = "偏旺忌神 (避讳偏冷):",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        destiny.jiShen.forEach { elem ->
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
    return when (elem) {
        "金" -> ElementMetal
        "木" -> ElementWood
        "水" -> ElementWater
        "火" -> ElementFire
        "土" -> ElementEarth
        else -> FortuneGold
    }
}
