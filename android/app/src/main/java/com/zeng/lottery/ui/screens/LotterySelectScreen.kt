package com.zeng.lottery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeng.lottery.data.model.LotteryType
import com.zeng.lottery.ui.theme.FortuneGold
import com.zeng.lottery.ui.theme.ImperialRed

@Composable
fun LotterySelectScreen(
    selectedType: LotteryType,
    onTypeSelected: (LotteryType) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "目标彩种规约与玩法选择",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Scrollable Tab Row for SSQ, DLT, FC3D, QXC
            ScrollableTabRow(
                selectedTabIndex = selectedType.ordinal,
                edgePadding = 0.dp,
                containerColor = Color.Transparent,
                contentColor = FortuneGold,
                divider = {}
            ) {
                LotteryType.entries.forEach { type ->
                    val isSelected = selectedType == type
                    Tab(
                        selected = isSelected,
                        onClick = { onTypeSelected(type) },
                        text = {
                            Text(
                                text = type.title,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                                color = if (isSelected) FortuneGold else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Rules & Odds Summary Card
            val (schedule, oddsSummary, poolDesc) = when (selectedType) {
                LotteryType.SSQ -> Triple(
                    "每周二、四、日 21:15 开奖",
                    "一等奖理论概率: 1 / 17,721,088 | 返奖率 49% + 1% 调节基金",
                    "全国主流大盘彩，奖池常年逾 10 亿元人民币"
                )
                LotteryType.DLT -> Triple(
                    "每周一、三、六 21:25 开奖",
                    "一等奖理论概率: 1 / 21,425,712 | 追加投注奖金提升 80%",
                    "超级大乐透支持浮动追加奖，封顶 1800 万元"
                )
                LotteryType.FC3D -> Triple(
                    "每天 21:15 开奖",
                    "单选理论概率: 1 / 1,000 | 固定单注奖金 1040 元",
                    "高频经典排列彩，适合小额防守与五行定胆"
                )
                LotteryType.QXC -> Triple(
                    "每周二、五、日 21:25 开奖",
                    "一等奖理论概率: 1 / 15,000,000 | 前6位0-9 + 第7位0-14",
                    "国家体彩大奖彩种，五行穿透号码深度契合"
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedType.title} · ${selectedType.subtitle}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ImperialRed
                    )
                    Text(
                        text = schedule,
                        style = MaterialTheme.typography.labelSmall,
                        color = FortuneGold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = oddsSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = poolDesc,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    fontSize = 11.sp
                )
            }
        }
    }
}
