package com.zeng.lottery.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeng.lottery.data.local.BaziEngine
import com.zeng.lottery.data.model.LotteryStats
import com.zeng.lottery.ui.theme.FortuneGold
import com.zeng.lottery.ui.theme.ImperialRed

@Composable
fun TrendStatsCard(
    stats: LotteryStats,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("🔥 热门号码", "❄️ 极冷潜伏", "⏳ 极限遗漏")

    val primaryStats = stats.stats["primary"] ?: emptyMap()

    // 1. Hot numbers sorted by frequency descending
    val hotList = primaryStats.entries
        .sortedByDescending { it.value.frequency }
        .take(6)

    // 2. Cold numbers sorted by frequency ascending
    val coldList = primaryStats.entries
        .sortedBy { it.value.frequency }
        .take(6)

    // 3. Omission list sorted by current omission descending
    val omissionList = primaryStats.entries
        .sortedByDescending { it.value.currentOmission }
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
                Text(
                    text = "大数据开奖走势排行",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "样本数: ${stats.totalDraws}期",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = FortuneGold,
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
                                color = if (selectedTab == index) FortuneGold else MaterialTheme.colorScheme.onSurfaceVariant
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
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

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            LotteryBall(
                                number = entry.key,
                                colorType = if (selectedTab == 0) BallColorType.RED else if (selectedTab == 1) BallColorType.BLUE else BallColorType.GOLD,
                                wuxingElement = elem,
                                size = 36.dp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = metricText,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (selectedTab == 0) ImperialRed else FortuneGold
                            )
                        }
                    }
                }
            }
        }
    }
}
