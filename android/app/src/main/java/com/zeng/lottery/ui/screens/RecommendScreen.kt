package com.zeng.lottery.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.zeng.lottery.data.local.SavedTicket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeng.lottery.data.local.BaziEngine
import com.zeng.lottery.data.model.BaziRecommendationResponse
import com.zeng.lottery.data.model.LotteryType
import com.zeng.lottery.data.model.RecommendationItem
import com.zeng.lottery.ui.components.BallColorType
import com.zeng.lottery.ui.components.ClassicsEvidenceCard
import com.zeng.lottery.ui.components.LotteryBall
import com.zeng.lottery.ui.theme.FortuneGold
import com.zeng.lottery.ui.theme.ImperialRed
import java.time.LocalDateTime

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecommendScreen(
    lotteryType: LotteryType,
    recommendationResponse: BaziRecommendationResponse?,
    selectedStrategy: String,
    betCount: Int = 5,
    userName: String = "缘主",
    isLoading: Boolean,
    onStrategySelected: (String) -> Unit,
    onBetCountSelected: (Int) -> Unit = {},
    onReroll: () -> Unit,
    onOpenDivination: () -> Unit = {},
    savedTickets: List<SavedTicket> = emptyList(),
    onSaveTicket: (RecommendationItem, Int) -> Unit = { _, _ -> },
    onSaveAllTickets: () -> Unit = {},
    onDeleteSavedTicket: (String) -> Unit = {},
    onClearAllSavedTickets: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val strategies = listOf("命理吉数优先", "稳健大数据", "冷热追号")
    val recommendations = recommendationResponse?.recommendations ?: emptyList()
    var showHistoryDialog by remember { mutableStateOf(false) }

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
            // Header Row: User Name & Lottery Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "【$userName】专属吉数机选 (${lotteryType.title})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = selectedStrategy,
                    style = MaterialTheme.typography.labelSmall,
                    color = FortuneGold,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bet Count Selector Chips (1注 / 5注 / 10注)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                listOf(
                    Pair(1, "1注 (¥2)"),
                    Pair(5, "5注 (¥10 · 标准票)"),
                    Pair(10, "10注 (¥20)")
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
                            selectedContainerColor = ImperialRed.copy(alpha = 0.25f),
                            selectedLabelColor = ImperialRed
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Strategy Toggle Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
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

            Spacer(modifier = Modifier.height(14.dp))

            // Multi-Bet Ticket Cards List
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
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                // Single Bet Card Header with Copy Button
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
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                onSaveTicket(item, index)
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = FortuneGold)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.BookmarkAdd,
                                                contentDescription = "收藏单注",
                                                modifier = Modifier.size(12.dp),
                                                tint = FortuneGold
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("收藏", fontSize = 11.sp)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                val text = item.formattedTicket?.takeIf { it.isNotBlank() } ?: formatSingleBet(lotteryType, item, index)
                                                clipboardManager.setText(AnnotatedString(text))
                                                Toast.makeText(context, "已复制单注: $text", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = FortuneGold)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "单注复制",
                                                modifier = Modifier.size(12.dp),
                                                tint = FortuneGold
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("单注复制", fontSize = 11.sp)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // 3D Balls Row with responsive sizing for >=6 balls
                                val totalBalls = primaryBalls.size + secondaryBalls.size
                                val ballSize = if (totalBalls >= 6) 36.dp else 44.dp

                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    primaryBalls.forEachIndexed { pIdx, numStr ->
                                        val num = numStr.toIntOrNull() ?: 0
                                        val elem = BaziEngine.getHetuElementZh(num)
                                        LotteryBall(
                                            number = numStr,
                                            colorType = BallColorType.RED,
                                            wuxingElement = elem,
                                            size = ballSize,
                                            delayMs = (pIdx * 30L)
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
                                            val num = numStr.toIntOrNull() ?: 0
                                            val elem = BaziEngine.getHetuElementZh(num)
                                            LotteryBall(
                                                number = numStr,
                                                colorType = secColor,
                                                wuxingElement = elem,
                                                size = ballSize,
                                                delayMs = ((primaryBalls.size + sIdx) * 30L)
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
                                        item.wuxingDistribution.forEach { (elem, count) ->
                                            val zh = BaziEngine.ELEMENT_ZH[elem.lowercase()] ?: elem
                                            Text(
                                                text = "$zh:$count",
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

            // Rationale Card (from first bet)
            recommendations.firstOrNull()?.let { item ->
                if (item.scoreRationale.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(12.dp)
                            )
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

                // Ancient Classics Grounding Evidence
                item.classicsEvidence?.let { evidence ->
                    Spacer(modifier = Modifier.height(10.dp))
                    ClassicsEvidenceCard(evidence = evidence)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Full Ticket Copy Button & History Collection Actions
            if (recommendations.isNotEmpty()) {
                OutlinedButton(
                    onClick = {
                        val fullTicketText = recommendationResponse?.copyText?.takeIf { it.isNotBlank() }
                            ?: buildFullTicketString(lotteryType, recommendations, userName)
                        clipboardManager.setText(AnnotatedString(fullTicketText))
                        Toast.makeText(context, "已复制全部 ${recommendations.size} 注彩票号码至剪贴板", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = FortuneGold)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "全票复制",
                        modifier = Modifier.size(16.dp),
                        tint = FortuneGold
                    )
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
                        Icon(
                            imageVector = Icons.Default.BookmarkAdd,
                            contentDescription = "收藏全票",
                            modifier = Modifier.size(15.dp),
                            tint = FortuneGold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "收藏本组灵感",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedButton(
                        onClick = { showHistoryDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = FortuneGold)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "历史灵感记录",
                            modifier = Modifier.size(15.dp),
                            tint = FortuneGold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "历史灵感 (${savedTickets.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            } else {
                OutlinedButton(
                    onClick = { showHistoryDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = FortuneGold)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "历史灵感记录",
                        modifier = Modifier.size(16.dp),
                        tint = FortuneGold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "查看历史灵感记录 (${savedTickets.size})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            // One-click Smart Re-Roll Button
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
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("乾坤推演中...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(
                        imageVector = Icons.Default.Casino,
                        contentDescription = "重新摇号",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "一键灵感机选 (重新摇号)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // AI Master Divination Button
            Button(
                onClick = onOpenDivination,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FortuneGold,
                    contentColor = Color.Black
                )
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "易道大师 AI 解盘",
                    modifier = Modifier.size(20.dp),
                    tint = Color.Black
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "易道大师 AI 解盘",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.Black
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "公益彩票 · 理性购彩 · 量力而行 · 未满18周岁禁止购彩",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showHistoryDialog) {
        HistoryTicketsDialog(
            savedTickets = savedTickets,
            onDismiss = { showHistoryDialog = false },
            onDeleteTicket = onDeleteSavedTicket,
            onClearAll = onClearAllSavedTickets
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryTicketsDialog(
    savedTickets: List<SavedTicket>,
    onDismiss: () -> Unit,
    onDeleteTicket: (String) -> Unit,
    onClearAll: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "缘主灵感收藏 (${savedTickets.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ImperialRed
                )
                if (savedTickets.isNotEmpty()) {
                    TextButton(
                        onClick = onClearAll,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "清空全部",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("清空全部", fontSize = 11.sp)
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
                        text = "暂无保存的灵感记录，\n在机选推荐中点击“收藏”即可随时回顾！",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    savedTickets.forEachIndexed { idx, ticket ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${idx + 1}. ${ticket.lotteryType.title}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = ImperialRed
                                        )
                                        Text(
                                            text = dateFormat.format(Date(ticket.timestamp)),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        val priList = (ticket.primaryNumbers as? List<*>)?.mapNotNull { (it as? Number)?.toInt() ?: it?.toString()?.toIntOrNull() } ?: emptyList()
                                        val secList = (ticket.secondaryNumbers as? List<*>)?.mapNotNull { (it as? Number)?.toInt() ?: it?.toString()?.toIntOrNull() } ?: emptyList()

                                        IconButton(
                                            onClick = {
                                                val pri = priList.joinToString(" ") { it.toString().padStart(2, '0') }
                                                val sec = if (secList.isNotEmpty()) " + " + secList.joinToString(" ") { it.toString().padStart(2, '0') } else ""
                                                val text = "${ticket.lotteryType.title}: $pri$sec"
                                                clipboardManager.setText(AnnotatedString(text))
                                                Toast.makeText(context, "已复制: $text", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "复制",
                                                modifier = Modifier.size(14.dp),
                                                tint = FortuneGold
                                            )
                                        }

                                        IconButton(
                                            onClick = { onDeleteTicket(ticket.id) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "删除",
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Number balls preview
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    val priList = (ticket.primaryNumbers as? List<*>)?.mapNotNull { (it as? Number)?.toInt() ?: it?.toString()?.toIntOrNull() } ?: emptyList()
                                    val secList = (ticket.secondaryNumbers as? List<*>)?.mapNotNull { (it as? Number)?.toInt() ?: it?.toString()?.toIntOrNull() } ?: emptyList()

                                    priList.forEach { num ->
                                        val numStr = num.toString().padStart(2, '0')
                                        val elem = BaziEngine.getHetuElementZh(num)
                                        LotteryBall(
                                            number = numStr,
                                            colorType = BallColorType.RED,
                                            wuxingElement = elem,
                                            size = 28.dp
                                        )
                                    }

                                    if (secList.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        val secColor = if (ticket.lotteryType == LotteryType.QXC) BallColorType.GOLD else BallColorType.BLUE
                                        secList.forEach { num ->
                                            val numStr = num.toString().padStart(2, '0')
                                            val elem = BaziEngine.getHetuElementZh(num)
                                            LotteryBall(
                                                number = numStr,
                                                colorType = secColor,
                                                wuxingElement = elem,
                                                size = 28.dp
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
