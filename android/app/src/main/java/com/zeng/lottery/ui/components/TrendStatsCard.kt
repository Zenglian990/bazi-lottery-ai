package com.zeng.lottery.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeng.lottery.data.local.BaziEngine
import com.zeng.lottery.data.model.LotteryStats
import com.zeng.lottery.ui.theme.LocalAccentGold

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrendStatsCard(
    stats: LotteryStats,
    modifier: Modifier = Modifier
) {
    val accentGold = LocalAccentGold.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedZoneIndex by rememberSaveable { mutableIntStateOf(0) } // 0: 红球/主区, 1: 蓝球/后区
    val tabs = listOf("🔥 热门号码", "❄️ 极冷潜伏", "⏳ 极限欲出")

    val hasSecondary = !stats.stats["secondary"].isNullOrEmpty()
    val activeZoneKey = if (selectedZoneIndex == 1 && hasSecondary) "secondary" else "primary"
    val activeStats = stats.stats[activeZoneKey] ?: emptyMap()

    val hotList = activeStats.entries
        .sortedByDescending { it.value.frequency }
        .take(6)

    val coldList = activeStats.entries
        .sortedBy { it.value.frequency }
        .take(6)

    val omissionList = activeStats.entries
        .sortedByDescending {
            if (it.value.desireIndex > 0f) it.value.desireIndex else it.value.currentOmission.toFloat()
        }
        .take(6)

    Card(
        modifier = modifier.fillMaxWidth(),
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "大数据开奖走势排行",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (stats.lastIssue.isNotBlank()) {
                        val datePart = if (stats.latestDrawDate.isNotBlank()) " (${stats.latestDrawDate})" else ""
                        Text(
                            text = "最新第 ${stats.lastIssue} 期$datePart · 样本 ${stats.totalDraws} 期",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            color = accentGold
                        )
                    }
                }

                if (hasSecondary) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = activeZoneKey == "primary",
                            onClick = { selectedZoneIndex = 0 },
                            label = { Text("主区", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentGold.copy(alpha = 0.25f),
                                selectedLabelColor = accentGold
                            )
                        )
                        FilterChip(
                            selected = activeZoneKey == "secondary",
                            onClick = { selectedZoneIndex = 1 },
                            label = { Text("蓝/后区", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentGold.copy(alpha = 0.25f),
                                selectedLabelColor = accentGold
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = accentGold,
                divider = {}
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) accentGold else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val currentItems = when (selectedTab) {
                0 -> hotList
                1 -> coldList
                else -> omissionList
            }

            if (currentItems.isEmpty()) {
                Text(
                    text = "暂无统计数据，请先同步开奖库",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                // 使用 FlowRow 彻底消除 320dp 窄屏溢出裁剪
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    currentItems.forEach { entry ->
                        val num = entry.key.toIntOrNull() ?: 0
                        val elem = BaziEngine.getHetuElementZh(num)
                        val stat = entry.value

                        val metricText = when (selectedTab) {
                            0 -> "${stat.frequency}次"
                            1 -> "${stat.frequency}次"
                            else -> "遗漏${stat.currentOmission}期"
                        }
                        val subMetric = when (selectedTab) {
                            2 -> if (stat.desireIndex > 0f) "欲出${String.format("%.1f", stat.desireIndex)}" else ""
                            else -> "${(stat.rate * 100).toInt()}%"
                        }

                        val ballColor = when {
                            activeZoneKey == "secondary" -> BallColorType.BLUE
                            selectedTab == 0 -> BallColorType.RED
                            selectedTab == 1 -> BallColorType.BLUE
                            else -> BallColorType.GOLD
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            LotteryBall(
                                number = entry.key,
                                colorType = ballColor,
                                wuxingElement = elem,
                                size = 34.dp,
                                animate = false
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = metricText,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTab == 0) MaterialTheme.colorScheme.secondary else accentGold
                            )
                            if (subMetric.isNotBlank()) {
                                Text(
                                    text = subMetric,
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
}
