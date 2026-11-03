package com.zeng.lottery.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeng.lottery.data.local.BaziEngine
import com.zeng.lottery.data.model.DrawItem
import com.zeng.lottery.data.model.LotteryStats
import com.zeng.lottery.data.model.LotteryType
import com.zeng.lottery.ui.components.BallColorType
import com.zeng.lottery.ui.components.LotteryBall
import com.zeng.lottery.ui.components.TrendStatsCard
import com.zeng.lottery.ui.theme.FortuneGold
import com.zeng.lottery.ui.theme.ImperialRed

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(
    stats: LotteryStats?,
    historyDraws: List<DrawItem> = emptyList(),
    lotteryType: LotteryType,
    onRefreshStats: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedSubTab by remember { mutableIntStateOf(0) } // 0: 往期开奖明细, 1: 冷热遗漏走势

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // SubTab Navigation: 往期开奖明细 vs 冷热遗漏走势
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            TabRow(
                selectedTabIndex = selectedSubTab,
                containerColor = Color.Transparent,
                contentColor = FortuneGold,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedSubTab]),
                        color = FortuneGold,
                        height = 3.dp
                    )
                }
            ) {
                Tab(
                    selected = selectedSubTab == 0,
                    onClick = { selectedSubTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FormatListNumbered,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (selectedSubTab == 0) FortuneGold else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "往期历史开奖 (${historyDraws.size}期)",
                                fontWeight = if (selectedSubTab == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedSubTab == 0) FortuneGold else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                    }
                )
                Tab(
                    selected = selectedSubTab == 1,
                    onClick = { selectedSubTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ShowChart,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (selectedSubTab == 1) FortuneGold else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "冷热遗漏走势",
                                fontWeight = if (selectedSubTab == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedSubTab == 1) FortuneGold else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                    }
                )
            }
        }

        when (selectedSubTab) {
            0 -> {
                // 1. 往期开奖明细列表
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${lotteryType.title} 历史开奖明细",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "最近收录 ${historyDraws.size} 期真实官方中奖号码与五行归属",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                Toast.makeText(context, "正在同步最新开奖数据...", Toast.LENGTH_SHORT).show()
                                onRefreshStats()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = FortuneGold)
                        ) {
                            Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("同步", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                if (historyDraws.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = FortuneGold,
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "正在调取往期历史开奖明细...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = onRefreshStats,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = FortuneGold)
                            ) {
                                Text("点击重新加载")
                            }
                        }
                    }
                } else {
                    // History Draws Card List
                    historyDraws.forEach { draw ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                // Issue & Draw Date Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .border(
                                                width = 1.dp,
                                                color = FortuneGold.copy(alpha = 0.6f),
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .background(
                                                color = FortuneGold.copy(alpha = 0.12f),
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "第 ${draw.issue} 期",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = FortuneGold
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarMonth,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = draw.drawDate,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // 3D Spherical Lottery Balls with Wuxing Tag
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    // Primary balls
                                    draw.primaryNumbers.forEach { rawStr ->
                                        val numStr = if (lotteryType == LotteryType.SSQ || lotteryType == LotteryType.DLT) rawStr.padStart(2, '0') else rawStr
                                        val num = numStr.toIntOrNull() ?: 0
                                        val elem = BaziEngine.getHetuElementZh(num)
                                        LotteryBall(
                                            number = numStr,
                                            colorType = BallColorType.RED,
                                            wuxingElement = elem,
                                            size = 36.dp,
                                            animate = false
                                        )
                                    }

                                    // Secondary balls (Blue / Gold)
                                    val secNumbers = draw.secondaryNumbers ?: emptyList()
                                    if (secNumbers.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .height(36.dp)
                                                .width(1.5.dp)
                                                .align(Alignment.CenterVertically)
                                                .background(Color.Gray.copy(alpha = 0.35f))
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))

                                        val secColor = if (lotteryType == LotteryType.QXC) BallColorType.GOLD else BallColorType.BLUE
                                        secNumbers.forEach { rawStr ->
                                            val numStr = if (lotteryType == LotteryType.SSQ || lotteryType == LotteryType.DLT) rawStr.padStart(2, '0') else rawStr
                                            val num = numStr.toIntOrNull() ?: 0
                                            val elem = BaziEngine.getHetuElementZh(num)
                                            LotteryBall(
                                                number = numStr,
                                                colorType = secColor,
                                                wuxingElement = elem,
                                                size = 36.dp,
                                                animate = false
                                            )
                                        }
                                    }
                                }

                                // Sales / Pool info if available
                                val pool = draw.poolAmount ?: 0L
                                val sales = draw.salesAmount ?: 0L
                                if (pool > 0L || sales > 0L) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        val infoText = buildString {
                                            if (pool > 0L) {
                                                append("奖池滚存: ${formatYuan(pool)}")
                                            }
                                            if (sales > 0L) {
                                                if (isNotEmpty()) append("  |  ")
                                                append("全国销量: ${formatYuan(sales)}")
                                            }
                                        }
                                        Text(
                                            text = infoText,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                // 2. 冷热遗漏走势
                if (stats == null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = FortuneGold,
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "正在调取大数据走势图谱...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    Toast.makeText(context, "正在连接云端同步开奖库...", Toast.LENGTH_SHORT).show()
                                    onRefreshStats()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = FortuneGold)
                            ) {
                                Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("点击同步开奖库")
                            }
                        }
                    }
                } else {
                    // Trend Stats Card (Hot, Cold, Omission)
                    TrendStatsCard(stats = stats)

                    // Historical Big Data Summary Card
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
                                    text = "数据仓库同步状态",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                OutlinedButton(
                                    onClick = {
                                        Toast.makeText(context, "正在同步最新开奖数据...", Toast.LENGTH_SHORT).show()
                                        onRefreshStats()
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = FortuneGold)
                                ) {
                                    Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("刷新走势", style = MaterialTheme.typography.labelSmall)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = "当前彩种: ${lotteryType.title} (${lotteryType.code.uppercase()})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = FortuneGold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val issueText = if (stats.lastIssue.isNotBlank()) "第 ${stats.lastIssue} 期" else "最新现行期"
                                Text(
                                    text = "最新开奖期号: $issueText | 累计收录: ${stats.totalDraws} 期开奖数据",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "统计算法: 滑动窗口 (30期) 冷热划分 + Efraimidis-Spirakis 加权无重复蓄水池抽样",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatYuan(amount: Long): String {
    return when {
        amount >= 100_000_000L -> {
            val yi = amount / 100_000_000.0
            String.format(java.util.Locale.CHINA, "%.2f 亿元", yi)
        }
        amount >= 10_000L -> {
            val wan = amount / 10_000.0
            String.format(java.util.Locale.CHINA, "%.2f 万元", wan)
        }
        else -> "$amount 元"
    }
}
