package com.zeng.lottery.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeng.lottery.data.local.BaziEngine
import com.zeng.lottery.data.model.DrawItem
import com.zeng.lottery.data.model.LotteryStats
import com.zeng.lottery.data.model.LotteryType
import com.zeng.lottery.data.model.NumberStat
import com.zeng.lottery.ui.components.BallColorType
import com.zeng.lottery.ui.components.LotteryBall
import com.zeng.lottery.ui.components.TrendStatsCard
import com.zeng.lottery.ui.theme.ElementWater
import com.zeng.lottery.ui.theme.FortuneGold
import com.zeng.lottery.ui.theme.ImperialRed
import com.zeng.lottery.ui.theme.LocalAccentGold

/**
 * 往期历史开奖与冷热遗漏走势分析页。
 * 采用 LazyColumn 懒加载避免 50 期 350 个 Canvas 同时渲染导致的卡顿，
 * 并新增 Canvas 频次柱状图、全号盘遗漏热力矩阵与自用一键官网抓取同步。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(
    stats: LotteryStats?,
    historyDraws: List<DrawItem> = emptyList(),
    lotteryType: LotteryType,
    isLoading: Boolean = false,
    isSyncingCloud: Boolean = false,
    lockedPrimary: Set<String> = emptySet(),
    excludedPrimary: Set<String> = emptySet(),
    onLotterySelected: (LotteryType) -> Unit = {},
    onRefreshStats: () -> Unit,
    onTriggerCloudSync: () -> Unit = {},
    onLockPrimary: (String) -> Unit = {},
    onExcludePrimary: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accentGold = LocalAccentGold.current
    var selectedSubTab by rememberSaveable { mutableIntStateOf(0) } // 0: 往期开奖明细, 1: 冷热遗漏走势

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "lottery_selector") {
            LotterySelectScreen(
                selectedType = lotteryType,
                onTypeSelected = onLotterySelected
            )
        }

        item(key = "subtab_bar") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                )
            ) {
                TabRow(
                    selectedTabIndex = selectedSubTab,
                    containerColor = Color.Transparent,
                    contentColor = accentGold,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedSubTab]),
                            color = accentGold,
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
                                    tint = if (selectedSubTab == 0) accentGold else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "往期开奖 (${historyDraws.size}期)",
                                    fontWeight = if (selectedSubTab == 0) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedSubTab == 0) accentGold else MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    tint = if (selectedSubTab == 1) accentGold else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "冷热图表与遗漏矩阵",
                                    fontWeight = if (selectedSubTab == 1) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedSubTab == 1) accentGold else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    )
                }
            }
        }

        if (selectedSubTab == 0) {
            item(key = "history_header") {
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
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${lotteryType.title} 历史开奖明细",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val latestInfo = historyDraws.firstOrNull()?.let { "最新第 ${it.issue} 期 (${it.drawDate})" } ?: "暂无记录"
                                Text(
                                    text = "$latestInfo · 已缓存 ${historyDraws.size} 期",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    Toast.makeText(context, "正在刷新云端榜单...", Toast.LENGTH_SHORT).show()
                                    onRefreshStats()
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !isLoading,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = accentGold)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("刷新榜单", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = onTriggerCloudSync,
                                modifier = Modifier.weight(1f),
                                enabled = !isSyncingCloud,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                if (isSyncingCloud) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = accentGold)
                                } else {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isSyncingCloud) "同步中..." else "同步最新开奖", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            if (historyDraws.isEmpty()) {
                item(key = "history_empty") {
                    EmptyOrLoadingCard(
                        isLoading = isLoading,
                        loadingText = "正在调取往期历史开奖明细...",
                        emptyText = "当前暂未拉取到开奖明细，点击下方按钮重试",
                        onRetry = onRefreshStats
                    )
                }
            } else {
                items(items = historyDraws, key = { "${lotteryType.code}_${it.issue}" }) { draw ->
                    DrawHistoryCard(draw = draw, lotteryType = lotteryType)
                }
            }
        } else {
            if (stats == null) {
                item(key = "stats_empty") {
                    EmptyOrLoadingCard(
                        isLoading = isLoading,
                        loadingText = "正在计算大数据冷热走势图谱...",
                        emptyText = "暂无统计数据，请点击刷新或同步开奖库",
                        onRetry = onRefreshStats
                    )
                }
            } else {
                item(key = "trend_ranking") {
                    TrendStatsCard(stats = stats)
                }

                // 新增：Compose Canvas 主区频次柱状图
                val primaryMap = stats.stats["primary"].orEmpty()
                if (primaryMap.isNotEmpty()) {
                    item(key = "freq_bar_chart") {
                        FrequencyBarChartCard(
                            lotteryType = lotteryType,
                            zoneStats = primaryMap
                        )
                    }
                    item(key = "omission_heatmap") {
                        OmissionHeatmapCard(
                            lotteryType = lotteryType,
                            zoneStats = primaryMap,
                            totalDraws = stats.totalDraws,
                            lockedPrimary = lockedPrimary,
                            excludedPrimary = excludedPrimary,
                            onLockPrimary = onLockPrimary,
                            onExcludePrimary = onExcludePrimary
                        )
                    }
                }

                item(key = "warehouse_summary") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "数据仓库与统计口径说明",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = "当前彩种: ${lotteryType.title} (${lotteryType.code.uppercase()})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = accentGold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val issueText = if (stats.lastIssue.isNotBlank()) "第 ${stats.lastIssue} 期" else "最新现行期"
                                val datePart = if (stats.latestDrawDate.isNotBlank()) " (${stats.latestDrawDate})" else ""
                                Text(
                                    text = "最新开奖: $issueText$datePart | 样本量: ${stats.totalDraws} 期",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "核心引擎: 30期滑动窗口冷热分级 + 理论欲出度 (Desire Index) + 对数空间 A-Res 加权抽样",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyOrLoadingCard(
    isLoading: Boolean,
    loadingText: String,
    emptyText: String,
    onRetry: () -> Unit
) {
    val accentGold = LocalAccentGold.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(34.dp), color = accentGold, strokeWidth = 3.dp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(loadingText, style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onRetry,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accentGold)
                ) {
                    Text("点击重新加载")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DrawHistoryCard(draw: DrawItem, lotteryType: LotteryType) {
    val accentGold = LocalAccentGold.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .border(1.dp, accentGold.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .background(accentGold.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "第 ${draw.issue} 期",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentGold
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

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalArrangement = Arrangement.Center
            ) {
                draw.primaryNumbers.forEach { rawStr ->
                    val numStr = if (lotteryType == LotteryType.SSQ || lotteryType == LotteryType.DLT) rawStr.padStart(2, '0') else rawStr
                    val elem = BaziEngine.getHetuElementZh(numStr.toIntOrNull() ?: 0)
                    LotteryBall(
                        number = numStr,
                        colorType = BallColorType.RED,
                        wuxingElement = elem,
                        size = 34.dp,
                        animate = false
                    )
                }

                val secNumbers = draw.secondaryNumbers.orEmpty()
                if (secNumbers.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .height(34.dp)
                            .width(1.5.dp)
                            .align(Alignment.CenterVertically)
                            .background(Color.Gray.copy(alpha = 0.35f))
                    )
                    Spacer(modifier = Modifier.width(6.dp))

                    val secColor = if (lotteryType == LotteryType.QXC) BallColorType.GOLD else BallColorType.BLUE
                    secNumbers.forEach { rawStr ->
                        val numStr = if (lotteryType == LotteryType.SSQ || lotteryType == LotteryType.DLT) rawStr.padStart(2, '0') else rawStr
                        val elem = BaziEngine.getHetuElementZh(numStr.toIntOrNull() ?: 0)
                        LotteryBall(
                            number = numStr,
                            colorType = secColor,
                            wuxingElement = elem,
                            size = 34.dp,
                            animate = false
                        )
                    }
                }
            }

            val pool = draw.poolAmount ?: 0L
            val sales = draw.salesAmount ?: 0L
            if (pool > 0L || sales > 0L) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    val infoText = buildString {
                        if (pool > 0L) append("奖池滚存: ${formatYuan(pool)}")
                        if (sales > 0L) {
                            if (isNotEmpty()) append("  |  ")
                            append("全国销量: ${formatYuan(sales)}")
                        }
                    }
                    Text(
                        text = infoText,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

/**
 * 全号盘历史出现频次柱状图（原生 Compose Canvas 零三方依赖绘制）。
 */
@Composable
private fun FrequencyBarChartCard(
    lotteryType: LotteryType,
    zoneStats: Map<String, NumberStat>
) {
    val accentGold = LocalAccentGold.current
    val sortedEntries = zoneStats.entries.sortedBy { it.key.toIntOrNull() ?: 0 }
    val maxFreq = sortedEntries.maxOfOrNull { it.value.frequency }?.coerceAtLeast(1) ?: 1

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
                    text = "${lotteryType.title} 主区出号频次分布图",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "峰值 ${maxFreq} 次",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentGold
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
            ) {
                val count = sortedEntries.size.coerceAtLeast(1)
                val gap = 2.dp.toPx()
                val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(3f)
                val chartHeight = size.height - 6.dp.toPx()

                sortedEntries.forEachIndexed { idx, (_, stat) ->
                    val ratio = (stat.frequency.toFloat() / maxFreq).coerceIn(0.04f, 1f)
                    val barH = chartHeight * ratio
                    val barColor = when {
                        stat.isHot -> ImperialRed
                        stat.isCold -> ElementWater
                        else -> accentGold
                    }
                    val left = idx * (barWidth + gap)
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(left, size.height - barH),
                        size = Size(barWidth, barH),
                        cornerRadius = CornerRadius(3f, 3f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(sortedEntries.firstOrNull()?.key.orEmpty(), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("红=热号 · 金=温号 · 蓝=冷号", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(sortedEntries.lastOrNull()?.key.orEmpty(), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * 全号盘当前遗漏热力矩阵（支持点击号码查明细，并一键定胆/杀号闭环联动机选）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OmissionHeatmapCard(
    lotteryType: LotteryType,
    zoneStats: Map<String, NumberStat>,
    totalDraws: Int = 0,
    lockedPrimary: Set<String> = emptySet(),
    excludedPrimary: Set<String> = emptySet(),
    onLockPrimary: (String) -> Unit = {},
    onExcludePrimary: (String) -> Unit = {}
) {
    val accentGold = LocalAccentGold.current
    val sortedEntries = zoneStats.entries.sortedBy { it.key.toIntOrNull() ?: 0 }
    val maxDesire = sortedEntries.maxOfOrNull { it.value.desireIndex }?.coerceAtLeast(1f) ?: 1f
    var selectedEntry by remember { mutableStateOf<Pair<String, NumberStat>?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${lotteryType.title} 全号盘遗漏热力矩阵",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "点击任意号码查详情并一键定胆/杀号 · 琥珀高亮为高欲出度",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                sortedEntries.forEach { (numStr, stat) ->
                    val normalizedBall = if (lotteryType.primaryRange.last > 9) {
                        numStr.padStart(2, '0')
                    } else {
                        numStr.toIntOrNull()?.toString() ?: numStr
                    }
                    val isLocked = normalizedBall in lockedPrimary
                    val isExcluded = normalizedBall in excludedPrimary
                    val heat = (stat.desireIndex / maxDesire).coerceIn(0f, 1f)
                    val bgColor = when {
                        isLocked -> FortuneGold
                        isExcluded -> Color.Gray.copy(alpha = 0.30f)
                        heat >= 0.72f -> accentGold.copy(alpha = 0.32f)
                        heat >= 0.45f -> accentGold.copy(alpha = 0.14f)
                        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
                    }
                    val borderColor = when {
                        isLocked -> FortuneGold
                        isExcluded -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        heat >= 0.72f -> accentGold
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    }
                    val titleColor = when {
                        isLocked -> Color(0xFF1A1200)
                        isExcluded -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        heat >= 0.72f -> accentGold
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    val subColor = when {
                        isLocked -> Color(0xFF3A2800)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(bgColor, RoundedCornerShape(8.dp))
                            .border(
                                width = if (isLocked || heat >= 0.72f) 1.2.dp else 0.5.dp,
                                color = borderColor,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedEntry = normalizedBall to stat }
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = when {
                                isLocked -> "$normalizedBall★"
                                isExcluded -> "×$normalizedBall"
                                else -> normalizedBall
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = titleColor
                        )
                        Text(
                            text = "${stat.currentOmission}期",
                            fontSize = 9.sp,
                            color = subColor
                        )
                    }
                }
            }
        }
    }

    selectedEntry?.let { (ballStr, stat) ->
        val numInt = ballStr.toIntOrNull() ?: 0
        val wuxing = BaziEngine.getHetuElementZh(numInt)
        val isLocked = ballStr in lockedPrimary
        val isExcluded = ballStr in excludedPrimary
        val statusLabel = when {
            stat.isHot -> "热号（近期高频）"
            stat.isCold -> "冷号（近期低频）"
            else -> "温号（走势均衡）"
        }
        val hitPercent = if (stat.hitRate > 0f) {
            String.format(java.util.Locale.CHINA, "%.1f%%", stat.hitRate * 100f)
        } else if (totalDraws > 0) {
            String.format(java.util.Locale.CHINA, "%.1f%%", stat.frequency * 100f / totalDraws)
        } else {
            "--"
        }

        AlertDialog(
            onDismissRequest = { selectedEntry = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LotteryBall(
                        number = ballStr,
                        colorType = BallColorType.RED,
                        wuxingElement = wuxing,
                        size = 38.dp,
                        animate = false
                    )
                    Column {
                        Text(
                            text = "${lotteryType.title} 主区号码 $ballStr",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "河图五行属【$wuxing】 · $statusLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = accentGold
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "统计期数出号：${stat.frequency} 次（出现率 $hitPercent）",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "当前遗漏：${stat.currentOmission} 期  |  历史最大遗漏：${stat.maxOmission} 期",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "理论欲出度：${String.format(java.util.Locale.CHINA, "%.2f", stat.desireIndex)}（>1.0 代表超过平均间隔）",
                            style = MaterialTheme.typography.bodySmall,
                            color = accentGold,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                onLockPrimary(ballStr)
                                selectedEntry = null
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isLocked) MaterialTheme.colorScheme.surfaceVariant else FortuneGold,
                                contentColor = if (isLocked) MaterialTheme.colorScheme.onSurface else Color(0xFF1A1200)
                            )
                        ) {
                            Text(if (isLocked) "取消定胆" else "★ 一键定胆", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                onExcludePrimary(ballStr)
                                selectedEntry = null
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (isExcluded) accentGold else MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(if (isExcluded) "取消杀号" else "× 一键杀号", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedEntry = null }) {
                    Text("关闭", color = accentGold, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

private fun formatYuan(amount: Long): String {
    return when {
        amount >= 100_000_000L -> String.format(java.util.Locale.CHINA, "%.2f 亿元", amount / 100_000_000.0)
        amount >= 10_000L -> String.format(java.util.Locale.CHINA, "%.2f 万元", amount / 10_000.0)
        else -> "$amount 元"
    }
}
