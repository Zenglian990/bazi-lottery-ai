package com.zeng.lottery.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeng.lottery.ai.AiConfig
import com.zeng.lottery.ai.AiProvider
import com.zeng.lottery.ui.theme.ImperialRed
import com.zeng.lottery.ui.theme.LocalAccentGold
import com.zeng.lottery.ui.theme.MysticIndigo

/**
 * 易道大师实时流式 AI 解盘底部面板。
 * 支持：多注切换解盘、透明降级原因横幅、DeepSeek 推理思考链折叠、自动滚底与随时停止生成。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiDivinationSheet(
    text: String,
    reasoningText: String = "",
    errorMessage: String? = null,
    isStreaming: Boolean,
    aiConfig: AiConfig,
    totalBets: Int = 1,
    selectedBetIndex: Int = 0,
    onSelectBet: (Int) -> Unit = {},
    onStopStreaming: () -> Unit = {},
    onDismiss: () -> Unit,
    onRedivine: () -> Unit,
    onOpenSettings: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val context = LocalContext.current
    val accentGold = LocalAccentGold.current
    var showReasoning by rememberSaveable { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "cursorBlink")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )

    val channelBadge = when (aiConfig.provider) {
        AiProvider.BACKEND_RELAY -> "云端安全中继 (HMAC 签名)"
        AiProvider.DIRECT_DEEPSEEK -> "DeepSeek: ${aiConfig.deepseekModel}"
        AiProvider.DIRECT_GEMINI -> "Gemini: ${aiConfig.geminiModel}"
        AiProvider.DIRECT_OPENAI -> "OpenAI 兼容: ${aiConfig.openaiModel}"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(18.dp)
        ) {
            // 顶栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = accentGold,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "易道彩经大师 · 智能解盘",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = accentGold
                        )
                        Text(
                            text = "推理通道: $channelBadge",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "AI通道设置",
                            tint = accentGold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭解盘面板",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 多注切换条（支持逐注深度解盘）
            if (totalBets > 1) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (i in 0 until totalBets) {
                        val label = if (i < 26) "第[${'A' + i}]注" else "第${i + 1}注"
                        FilterChip(
                            selected = selectedBetIndex == i,
                            onClick = { onSelectBet(i) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentGold.copy(alpha = 0.25f),
                                selectedLabelColor = accentGold
                            )
                        )
                    }
                }
            }

            // 透明错误 / 本地兜底状态提醒横幅（绝不静默伪装云端成功）
            if (!errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(accentGold.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                        .border(0.8.dp, accentGold.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "通道提示",
                        tint = accentGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 正文滚动容器（智能跟滚：仅当用户停留在底部附近且未手动上滑时才跟随，绝不抢滚动条）
            val scrollState = rememberScrollState()
            var autoFollowBottom by remember { mutableStateOf(true) }
            LaunchedEffect(selectedBetIndex, isStreaming) {
                if (isStreaming && text.isEmpty()) {
                    autoFollowBottom = true
                }
            }
            LaunchedEffect(scrollState.value, scrollState.maxValue, scrollState.isScrollInProgress) {
                val distToBottom = (scrollState.maxValue - scrollState.value).coerceAtLeast(0)
                if (scrollState.isScrollInProgress && distToBottom > 120) {
                    autoFollowBottom = false
                } else if (distToBottom <= 60) {
                    autoFollowBottom = true
                }
            }
            LaunchedEffect(text.length, isStreaming) {
                val distToBottom = (scrollState.maxValue - scrollState.value).coerceAtLeast(0)
                if (isStreaming && autoFollowBottom && !scrollState.isScrollInProgress && scrollState.maxValue > 0 && distToBottom <= 300) {
                    scrollState.scrollTo(scrollState.maxValue)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.background,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(1.dp, accentGold.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                if (text.isEmpty() && reasoningText.isEmpty() && isStreaming) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = accentGold,
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "正在感应干支气场，推演乾坤数理...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = accentGold
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 可折叠深度推理思考链（DeepSeek R1 等推理模型专属）
                        if (reasoningText.isNotBlank()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showReasoning = !showReasoning },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "大师推演思路 (${reasoningText.length}字)",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = accentGold
                                    )
                                    Icon(
                                        imageVector = if (showReasoning) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = "展开或收起推演思路",
                                        tint = accentGold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                AnimatedVisibility(visible = showReasoning) {
                                    Text(
                                        text = reasoningText,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                            }
                        }

                        if (text.isNotEmpty()) {
                            RenderDivinationSections(text = text)
                        }

                        if (isStreaming) {
                            Text(
                                text = " ▌",
                                color = accentGold,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.alpha(cursorAlpha)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 底部操作栏：复制 / 停止生成或再求一卦
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("易道解盘", text))
                        Toast.makeText(context, "解盘卦辞已复制至剪贴板", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = text.isNotEmpty(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "复制",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("复制卦辞", fontSize = 13.sp)
                }

                if (isStreaming) {
                    Button(
                        onClick = onStopStreaming,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = accentGold
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "停止生成",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("停止生成", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onRedivine,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ImperialRed,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "再求一卦",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("再求一卦", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderDivinationSections(text: String) {
    val accentGold = LocalAccentGold.current
    val sections = text.split(Regex("(?=【[^】]+】)"))

    for (sec in sections) {
        val trimmed = sec.trim()
        if (trimmed.isEmpty()) continue

        if (trimmed.startsWith("【理性倡议】")) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = accentGold.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(8.dp),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(accentGold.copy(alpha = 0.45f))
                )
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "理性倡议",
                        tint = accentGold,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "【理性倡议】",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = accentGold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = trimmed.removePrefix("【理性倡议】").trim(),
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        } else {
            val headerMatch = Regex("^【([^】]+)】").find(trimmed)
            val header = headerMatch?.value ?: ""
            val body = if (header.isNotEmpty()) trimmed.removePrefix(header).trim() else trimmed

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                if (header.isNotEmpty()) {
                    val headerColor = when {
                        header.contains("乾坤") || header.contains("玄机") -> accentGold
                        header.contains("五行") || header.contains("吉言") -> MysticIndigo
                        else -> accentGold
                    }
                    Text(
                        text = header,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = headerColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
