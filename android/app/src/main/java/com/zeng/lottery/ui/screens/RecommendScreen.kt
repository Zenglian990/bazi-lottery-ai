package com.zeng.lottery.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeng.lottery.data.local.BaziEngine
import com.zeng.lottery.data.model.BaziRecommendationResponse
import com.zeng.lottery.data.model.LotteryType
import com.zeng.lottery.ui.components.BallColorType
import com.zeng.lottery.ui.components.LotteryBall
import com.zeng.lottery.ui.theme.FortuneGold
import com.zeng.lottery.ui.theme.ImperialRed

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecommendScreen(
    lotteryType: LotteryType,
    recommendationResponse: BaziRecommendationResponse?,
    selectedStrategy: String,
    isLoading: Boolean,
    onStrategySelected: (String) -> Unit,
    onReroll: () -> Unit,
    onOpenDivination: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strategies = listOf("命理吉数优先", "稳健大数据", "冷热追号")

    val recItem = recommendationResponse?.recommendations?.firstOrNull()
    val primaryBalls = recItem?.primaryNumbers ?: emptyList()
    val secondaryBalls = recItem?.secondaryNumbers ?: emptyList()

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "生辰吉数机选方案 (${lotteryType.title})",
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

            Spacer(modifier = Modifier.height(12.dp))

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

            Spacer(modifier = Modifier.height(18.dp))

            // 3D Lottery Ball Rack with Staggered Spring Animation
            if (primaryBalls.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Primary Balls (Red)
                    primaryBalls.forEachIndexed { index, numStr ->
                        val num = numStr.toIntOrNull() ?: 0
                        val elem = BaziEngine.getHetuElementZh(num)
                        LotteryBall(
                            number = numStr,
                            colorType = BallColorType.RED,
                            wuxingElement = elem,
                            delayMs = (index * 45L)
                        )
                    }

                    // Divider if secondary balls exist
                    if (secondaryBalls.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .width(2.dp)
                                .align(Alignment.CenterVertically)
                                .background(Color.Gray.copy(alpha = 0.35f))
                        )
                        Spacer(modifier = Modifier.width(6.dp))

                        // Secondary Balls (Blue for SSQ/DLT, Gold for QXC)
                        val secColor = if (lotteryType == LotteryType.QXC) BallColorType.GOLD else BallColorType.BLUE
                        secondaryBalls.forEachIndexed { index, numStr ->
                            val num = numStr.toIntOrNull() ?: 0
                            val elem = BaziEngine.getHetuElementZh(num)
                            LotteryBall(
                                number = numStr,
                                colorType = secColor,
                                wuxingElement = elem,
                                delayMs = ((primaryBalls.size + index) * 45L)
                            )
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

            Spacer(modifier = Modifier.height(16.dp))

            // Rationale and Wuxing distribution card
            recItem?.let { item ->
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

                    if (item.wuxingDistribution.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item.wuxingDistribution.forEach { (elem, count) ->
                                Text(
                                    text = "${BaziEngine.ELEMENT_ZH[elem] ?: elem}:$count",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // One-click Smart Re-Roll Button
            Button(
                onClick = onReroll,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
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
                    .height(48.dp),
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
                    fontSize = 15.sp,
                    color = Color.Black
                )
            }
        }
    }
}
