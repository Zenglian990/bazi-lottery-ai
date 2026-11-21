package com.zeng.lottery.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeng.lottery.data.local.BaziEngine
import com.zeng.lottery.data.local.PrizeCheckResult
import com.zeng.lottery.data.local.SavedTicket
import com.zeng.lottery.data.model.BaziRecommendationResponse
import com.zeng.lottery.data.model.LotteryType
import com.zeng.lottery.data.model.RecommendationItem
import com.zeng.lottery.ui.components.BallColorType
import com.zeng.lottery.ui.components.ClassicsEvidenceCard
import com.zeng.lottery.ui.components.LotteryBall
import com.zeng.lottery.ui.theme.ElementWood
import com.zeng.lottery.ui.theme.FortuneGold
import com.zeng.lottery.ui.theme.ImperialRed
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecommendScreen(
    lotteryType: LotteryType,
    recommendationResponse: BaziRecommendationResponse?,
    selectedStrategy: String,
    betCount: Int = 5,
    userName: String = "缘主",
    isLoading: Boolean,
    useCustomWeights: Boolean = false,
    customWBazi: Float = 0.45f,
    customWFreq: Float = 0.35f,
    customWOmission: Float = 0.20f,
    lockedPrimary: Set<String> = emptySet(),
    excludedPrimary: Set<String> = emptySet(),
    lockedSecondary: Set<String> = emptySet(),
    excludedSecondary: Set<String> = emptySet(),
    onStrategySelected: (String) -> Unit,
    onBetCountSelected: (Int) -> Unit = {},
    onToggleCustomWeights: (Boolean) -> Unit = {},
    onUpdateCustomWeights: (Float, Float, Float) -> Unit = { _, _, _ -> },
    onTogglePrimaryConstraint: (String) -> Unit = {},
    onToggleSecondaryConstraint: (String) -> Unit = {},
    onClearConstraints: () -> Unit = {},
    onReroll: () -> Unit,
    onOpenDivination: (Int) -> Unit = {},
    savedTickets: List<SavedTicket> = emptyList(),
    onSaveTicket: (RecommendationItem, Int) -> Unit = { _, _ -> },
    onSaveAllTickets: () -> Unit = {},
    onDeleteSavedTicket: (String) -> Unit = {},
    onClearAllSavedTickets: () -> Unit = {},
    onCheckTicketPrize: (SavedTicket) -> PrizeCheckResult? = { null },
    onOpenHistory: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    // 补全全部 4 种策略（含极冷号反弹）
    val strategies = listOf("命理吉数优先", "稳健大数据", "冷热追号", "极冷号反弹")
    val recommendations = recommendationResponse?.recommendations ?: emptyList()
    var showHistoryDialog by rememberSaveable { mutableStateOf(false) }
    var showAdvancedPanel by rememberSaveable { mutableStateOf(false) }

    val displayUserName = userName.ifBlank { "缘主" }
    val constraintCount = lockedPrimary.size + excludedPrimary.size + lockedSecondary.size + excludedSecondary.size

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 标题栏：左权重防长姓名挤爆
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "【$displayUserName】专属吉数 (${lotteryType.title})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = selectedStrategy,
                    style = MaterialTheme.typography.labelSmall,
                    color = FortuneGold,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 注数选择（1/5/10/20注，FlowRow 防窄屏挤压）
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    1 to "1注 (¥2)",
                    5 to "5注 (¥10)",
                    10 to "10注 (¥20)",
                    20 to "20注 (¥40)"
                ).forEach { (count, label) ->
                    val isSelected = betCount == count
                    FilterChip(
                        selected = isSelected,
                        onClick = { onBetCountSelected(count) },
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f),
                            selectedLabelColor = MaterialTheme.colorScheme.secondary
                        )
                    )
                }
            }
            if (lotteryType == LotteryType.DLT) {
                Text(
                    text = "大乐透基础 ¥2/注（可选 ¥3/注 追加投注，浮动奖金提升 80%）",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 四大选号策略（FlowRow 自适应换行）
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                strategies.forEach { strategy ->
                    val isSelected = selectedStrategy == strategy
                    FilterChip(
                        selected = isSelected,
                        onClick = { onStrategySelected(strategy) },
                        label = {
                            Text(
                                text = strategy,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = FortuneGold.copy(alpha = 0.25f),
                            selectedLabelColor = FortuneGold
                        )
                    )
                }
            }

            // 可折叠高级选号条件（自定义三元权重 + 定胆/杀号矩阵）
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAdvancedPanel = !showAdvancedPanel },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = FortuneGold, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = buildString {
                                append("高级条件：定胆杀号 & 三元权重")
                                if (constraintCount > 0) append(" (已设${constraintCount}球)")
                                if (useCustomWeights) append(" · 自定义权重")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = FortuneGold
                        )
                    }
                    Icon(
                        imageVector = if (showAdvancedPanel) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "展开或收起高级条件",
                        tint = FortuneGold,
                        modifier = Modifier.size(18.dp)
                    )
                }

                AnimatedVisibility(visible = showAdvancedPanel) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. 三元自定义权重滑块
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("启用自定义三元权重", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Switch(checked = useCustomWeights, onCheckedChange = onToggleCustomWeights)
                        }

                        if (useCustomWeights) {
                            WeightSliderRow("八字喜用权重", customWBazi) { onUpdateCustomWeights(it, customWFreq, customWOmission) }
                            WeightSliderRow("历史频次权重", customWFreq) { onUpdateCustomWeights(customWBazi, it, customWOmission) }
                            WeightSliderRow("遗漏欲出权重", customWOmission) { onUpdateCustomWeights(customWBazi, customWFreq, it) }
                        }

                        // 2. 定胆/杀号点击矩阵
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "主区点选：1次=定胆(金) · 2次=杀号(灰) · 3次=清除",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            if (constraintCount > 0) {
                                TextButton(onClick = onClearConstraints, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                                    Text("清空", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            for (n in lotteryType.primaryRange) {
                                val label = if (lotteryType.primaryRange.last > 9) String.format("%02d", n) else n.toString()
                                val isLocked = label in lockedPrimary
                                val isExcluded = label in excludedPrimary
                                ConstraintBallChip(
                                    text = label,
                                    isLocked = isLocked,
                                    isExcluded = isExcluded,
                                    onClick = { onTogglePrimaryConstraint(label) }
                                )
                            }
                        }

                        lotteryType.secondaryRange?.let { secRange ->
                            Text(
                                text = "蓝/后区点选（定胆/杀号）：",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                for (n in secRange) {
                                    val label = String.format("%02d", n)
                                    ConstraintBallChip(
                                        text = label,
                                        isLocked = label in lockedSecondary,
                                        isExcluded = label in excludedSecondary,
                                        onClick = { onToggleSecondaryConstraint(label) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 推荐注单列表（注数 > 5 时跳过逐球延迟弹簧动效确保丝滑零掉帧）
            val enableBallAnim = recommendations.size <= 5
            if (recommendations.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    recommendations.forEachIndexed { index, item ->
                        val ticketLabel = if (index < 26) ('A' + index).toString() else "${index + 1}"
                        val primaryBalls = (item.primaryNumbers as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                        val secondaryBalls = (item.secondaryNumbers as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "第 [$ticketLabel] 注",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = FortuneGold
                                    )

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = { onOpenDivination(index) },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 7.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = FortuneGold)
                                        ) {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = "AI解此注", modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text("解盘", fontSize = 10.sp)
                                        }

                                        OutlinedButton(
                                            onClick = { onSaveTicket(item, index) },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 7.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = FortuneGold)
                                        ) {
                                            Icon(Icons.Default.BookmarkAdd, contentDescription = "收藏单注", modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text("收藏", fontSize = 10.sp)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                val text = item.formattedTicket?.takeIf { it.isNotBlank() }
                                                    ?: formatSingleBet(lotteryType, item, index)
                                                clipboardManager.setText(AnnotatedString(text))
                                                Toast.makeText(context, "已复制单注: $text", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 7.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = FortuneGold)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "复制单注", modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text("复制", fontSize = 10.sp)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                val totalBalls = primaryBalls.size + secondaryBalls.size
                                val ballSize = if (totalBalls >= 6) 36.dp else 44.dp

                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    primaryBalls.forEachIndexed { pIdx, numStr ->
                                        val elem = BaziEngine.getHetuElementZh(numStr.toIntOrNull() ?: 0)
                                        LotteryBall(
                                            number = numStr,
                                            colorType = BallColorType.RED,
                                            wuxingElement = elem,
                                            size = ballSize,
                                            delayMs = if (enableBallAnim) pIdx * 25L else 0L,
                                            animate = enableBallAnim
                                        )
                                    }

                                    if (secondaryBalls.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .height(ballSize)
                                                .width(1.5.dp)
                                                .align(Alignment.CenterVertically)
                                                .background(Color.Gray.copy(alpha = 0.35f))
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))

                                        val secColor = if (lotteryType == LotteryType.QXC) BallColorType.GOLD else BallColorType.BLUE
                                        secondaryBalls.forEachIndexed { sIdx, numStr ->
                                            val elem = BaziEngine.getHetuElementZh(numStr.toIntOrNull() ?: 0)
                                            LotteryBall(
                                                number = numStr,
                                                colorType = secColor,
                                                wuxingElement = elem,
                                                size = ballSize,
                                                delayMs = if (enableBallAnim) (primaryBalls.size + sIdx) * 25L else 0L,
                                                animate = enableBallAnim
                                            )
                                        }
                                    }
                                }

                                if (item.wuxingDistribution.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        item.wuxingDistribution.forEach { (elem, cnt) ->
                                            val zh = BaziEngine.ELEMENT_ZH[elem.lowercase()] ?: elem
                                            Text(
                                                text = "$zh:$cnt",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "点击下方按钮开启生辰八字智能机选",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            }

            // 选号依据与典籍印证卡
            recommendations.firstOrNull()?.let { item ->
                if (item.scoreRationale.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "选号玄机与大数据依据:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = FortuneGold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.scoreRationale,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
                item.classicsEvidence?.let { evidence ->
                    Spacer(modifier = Modifier.height(10.dp))
                    ClassicsEvidenceCard(evidence = evidence)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 全票复制与收藏/历史自动对奖复盘入口
            if (recommendations.isNotEmpty()) {
                OutlinedButton(
                    onClick = {
                        val fullTicketText = recommendationResponse?.copyText?.takeIf { it.isNotBlank() }
                            ?: buildFullTicketString(lotteryType, recommendations, displayUserName)
                        clipboardManager.setText(AnnotatedString(fullTicketText))
                        Toast.makeText(context, "已复制全部 ${recommendations.size} 注号码至剪贴板", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = FortuneGold)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "全票复制", modifier = Modifier.size(16.dp), tint = FortuneGold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "全票复制 (全部 ${recommendations.size} 注 · ¥${recommendations.size * 2})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSaveAllTickets,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = FortuneGold)
                    ) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = "收藏全票", modifier = Modifier.size(15.dp), tint = FortuneGold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("收藏本组灵感", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = {
                            onOpenHistory()
                            showHistoryDialog = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = FortuneGold)
                    ) {
                        Icon(Icons.Default.History, contentDescription = "收藏与自动对奖", modifier = Modifier.size(15.dp), tint = FortuneGold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("收藏对奖 (${savedTickets.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            } else {
                OutlinedButton(
                    onClick = {
                        onOpenHistory()
                        showHistoryDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = FortuneGold)
                ) {
                    Icon(Icons.Default.History, contentDescription = "收藏与自动对奖", modifier = Modifier.size(16.dp), tint = FortuneGold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("收藏夹与一键自动对奖 (${savedTickets.size})", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // 主行动按钮：一键重新摇号
            Button(
                onClick = onReroll,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ImperialRed,
                    contentColor = Color.White
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("乾坤推演中...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.Casino, contentDescription = "重新摇号", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("一键灵感机选 (重新摇号)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 易道大师 AI 解盘按钮
            Button(
                onClick = { onOpenDivination(0) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FortuneGold,
                    contentColor = Color(0xFF1A1200)
                )
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = "易道大师 AI 解盘", modifier = Modifier.size(20.dp), tint = Color(0xFF1A1200))
                Spacer(modifier = Modifier.width(8.dp))
                Text("易道大师 AI 解盘", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1A1200))
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "公益彩票 · 理性购彩 · 量力而行 · 未满18周岁禁止购彩",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showHistoryDialog) {
        HistoryTicketsDialog(
            savedTickets = savedTickets,
            onCheckPrize = onCheckTicketPrize,
            onDismiss = { showHistoryDialog = false },
            onDeleteTicket = onDeleteSavedTicket,
            onClearAll = onClearAllSavedTickets
        )
    }
}

@Composable
private fun WeightSliderRow(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
            Text("${(value * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = FortuneGold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(thumbColor = FortuneGold, activeTrackColor = FortuneGold)
        )
    }
}

@Composable
private fun ConstraintBallChip(
    text: String,
    isLocked: Boolean,
    isExcluded: Boolean,
    onClick: () -> Unit
) {
    val bgColor = when {
        isLocked -> FortuneGold
        isExcluded -> Color.Gray.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    }
    val txtColor = when {
        isLocked -> Color(0xFF1A1200)
        isExcluded -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = Modifier
            .size(width = 34.dp, height = 28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isLocked) "$text★" else if (isExcluded) "×$text" else text,
            fontSize = 10.sp,
            fontWeight = if (isLocked) FontWeight.ExtraBold else FontWeight.Medium,
            color = txtColor
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryTicketsDialog(
    savedTickets: List<SavedTicket>,
    onCheckPrize: (SavedTicket) -> PrizeCheckResult?,
    onDismiss: () -> Unit,
    onDeleteTicket: (String) -> Unit,
    onClearAll: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }
    var showPrizeSummary by rememberSaveable { mutableStateOf(true) }

    val prizeResults = remember(savedTickets) {
        savedTickets.associate { it.id to onCheckPrize(it) }
    }
    val wonCount = prizeResults.values.count { it?.isWon == true }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "收藏夹与自动对奖 (${savedTickets.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
                if (savedTickets.isNotEmpty()) {
                    TextButton(
                        onClick = { showClearConfirm = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "清空全部", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("清空", fontSize = 12.sp)
                    }
                }
            }
        },
        text = {
            if (savedTickets.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无保存的灵感记录，\n在机选推荐中点击“收藏”后可自动对照最新官方开奖复盘！",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 顶部自动对奖汇总横幅
                    if (showPrizeSummary) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = if (wonCount > 0) FortuneGold.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (wonCount > 0) FortuneGold else ElementWood,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (wonCount > 0) {
                                    "对照最新一期官方开奖：共核验 ${savedTickets.size} 注，命中中奖 $wonCount 注！"
                                } else {
                                    "已自动核验全部 ${savedTickets.size} 注（对照各自彩种最新一期开奖，金环标出命中球）"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    savedTickets.forEachIndexed { idx, ticket ->
                        val prize = prizeResults[ticket.id]
                        val priList = (ticket.primaryNumbers as? List<*>)?.mapNotNull {
                            (it as? Number)?.toInt() ?: it?.toString()?.toIntOrNull()
                        } ?: emptyList()
                        val secList = (ticket.secondaryNumbers as? List<*>)?.mapNotNull {
                            (it as? Number)?.toInt() ?: it?.toString()?.toIntOrNull()
                        } ?: emptyList()

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (prize?.isWon == true) Modifier.border(1.2.dp, FortuneGold, RoundedCornerShape(10.dp))
                                    else Modifier
                                ),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${idx + 1}. ${ticket.lotteryType.title} · ${dateFormat.format(Date(ticket.timestamp))}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (prize != null) {
                                            Text(
                                                text = if (prize.isWon) {
                                                    "🎉 第${prize.issue}期 ${prize.prizeLevel} (参考 ${prize.referencePrize})"
                                                } else {
                                                    "第${prize.issue}期：命中 ${prize.primaryHitCount}+${prize.secondaryHitCount} (${prize.prizeLevel})"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 10.sp,
                                                fontWeight = if (prize.isWon) FontWeight.ExtraBold else FontWeight.Normal,
                                                color = if (prize.isWon) FortuneGold else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        IconButton(
                                            onClick = {
                                                val pri = priList.joinToString(" ") { it.toString().padStart(2, '0') }
                                                val sec = if (secList.isNotEmpty()) " + " + secList.joinToString(" ") { it.toString().padStart(2, '0') } else ""
                                                val text = "${ticket.lotteryType.title}: $pri$sec"
                                                clipboardManager.setText(AnnotatedString(text))
                                                Toast.makeText(context, "已复制: $text", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "复制此注", modifier = Modifier.size(16.dp), tint = FortuneGold)
                                        }

                                        IconButton(
                                            onClick = { onDeleteTicket(ticket.id) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "删除此注", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    priList.forEachIndexed { pIdx, num ->
                                        val numStr = if (ticket.lotteryType.primaryRange.last > 9) num.toString().padStart(2, '0') else num.toString()
                                        LotteryBall(
                                            number = numStr,
                                            colorType = BallColorType.RED,
                                            wuxingElement = BaziEngine.getHetuElementZh(num),
                                            size = 28.dp,
                                            animate = false,
                                            highlighted = prize?.matchedPrimaryIndices?.contains(pIdx) == true
                                        )
                                    }

                                    if (secList.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        val secColor = if (ticket.lotteryType == LotteryType.QXC) BallColorType.GOLD else BallColorType.BLUE
                                        secList.forEachIndexed { sIdx, num ->
                                            val numStr = num.toString().padStart(2, '0')
                                            LotteryBall(
                                                number = numStr,
                                                colorType = secColor,
                                                wuxingElement = BaziEngine.getHetuElementZh(num),
                                                size = 28.dp,
                                                animate = false,
                                                highlighted = prize?.matchedSecondaryIndices?.contains(sIdx) == true
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", fontWeight = FontWeight.Bold, color = FortuneGold)
            }
        }
    )

    // 防误触：清空全部历史收藏二次确认
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("确认清空所有收藏？", fontWeight = FontWeight.Bold) },
            text = { Text("将永久删除全部 ${savedTickets.size} 条历史灵感收藏，操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAll()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("确认清空", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

private fun formatSingleBet(
    lotteryType: LotteryType,
    item: RecommendationItem,
    index: Int
): String {
    val label = if (index < 26) ('A' + index).toString() else "${index + 1}"
    val pri = (item.primaryNumbers as? List<*>)?.mapNotNull { it?.toString() }?.joinToString(" ") ?: ""
    val secList = (item.secondaryNumbers as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
    val sec = if (secList.isNotEmpty()) " + " + secList.joinToString(" ") else ""
    return "${lotteryType.title} [$label] $pri$sec"
}

private fun buildFullTicketString(
    lotteryType: LotteryType,
    items: List<RecommendationItem>,
    userName: String
): String {
    val effectiveName = if (userName.isBlank()) "缘主" else userName.trim()
    val now = LocalDateTime.now()
    val todayPillars = BaziEngine.calculateFourPillars(now)
    val todayLunar = BaziEngine.solarToLunar(now)

    val sb = StringBuilder()
    sb.appendLine("【曾练彩票助手 - ${effectiveName}专属吉祥票】")
    sb.appendLine("缘主: $effectiveName")
    sb.appendLine("彩种: ${lotteryType.title} (${items.size}注 · ¥${items.size * 2})")
    sb.appendLine("择吉干支: 农历${todayPillars.year.fullText}年 ${todayLunar.monthZh}${todayLunar.dayZh} (${todayPillars.day.fullText}日 ${todayPillars.hour.fullText}时)")
    sb.appendLine("--------------------------------")
    items.forEachIndexed { idx, item ->
        val label = if (idx < 26) ('A' + idx).toString() else "${idx + 1}"
        val pri = (item.primaryNumbers as? List<*>)?.mapNotNull { it?.toString() }?.joinToString(" ") ?: ""
        val secList = (item.secondaryNumbers as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
        val sec = if (secList.isNotEmpty()) " + " + secList.joinToString(" ") else ""
        sb.appendLine("[$label] $pri$sec")
    }
    sb.appendLine("--------------------------------")
    sb.appendLine("祝缘主【$effectiveName】福星高照，吉祥如意，理性投注！")
    return sb.toString()
}
