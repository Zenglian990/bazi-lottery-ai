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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeng.lottery.data.local.BaziEngine
import com.zeng.lottery.data.model.BaziDestiny
import com.zeng.lottery.data.model.displayLackingElements
import com.zeng.lottery.data.model.displayXiYongShen
import com.zeng.lottery.ui.theme.FortuneGold
import com.zeng.lottery.ui.theme.ImperialRed
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TodayLiuRiCard(
    destiny: BaziDestiny? = null,
    modifier: Modifier = Modifier
) {
    val now = remember { LocalDateTime.now() }
    val todayPillars = remember(now) { BaziEngine.calculateFourPillars(now) }

    val todayLunar = remember(now) { BaziEngine.solarToLunar(now) }
    val lunarDateStr = remember(todayLunar, todayPillars) {
        "农历 ${todayPillars.year.fullText}年 ${todayLunar.monthZh}${todayLunar.dayZh} (${todayPillars.day.fullText}日)"
    }
    val timeStr = remember(now) {
        now.format(DateTimeFormatter.ofPattern("HH:mm", Locale.CHINESE))
    }

    val dayElementEn = BaziEngine.STEM_ELEMENTS[todayPillars.day.stem] ?: "earth"
    val dayElementZh = BaziEngine.ELEMENT_ZH[dayElementEn] ?: "土"

    val harmonyDescription = remember(destiny, dayElementZh, todayPillars) {
        if (destiny != null) {
            val xiYong = destiny.displayXiYongShen
            val lacking = destiny.displayLackingElements
            when {
                xiYong.contains(dayElementZh) ->
                    "今日流日【${todayPillars.day.fullText}】，当令五行【$dayElementZh】正值缘主喜用神磁场大旺之期！天地生扶元神，机选祥瑞得吉星高照。"
                lacking.contains(dayElementZh) ->
                    "今日流日【${todayPillars.day.fullText}】，五行【$dayElementZh】恰补缘主原局所缺之气，天地补全，气韵流转，利于纳福选号。"
                else ->
                    "今日流日【${todayPillars.day.fullText}】，流时【${todayPillars.hour.fullText}】，五行【$dayElementZh】坐镇日宫。命理结合今日时空气运综合推演，调和中正。"
            }
        } else {
            "今日流日【${todayPillars.day.fullText}】，五行【$dayElementZh】当值。天道循环，地载万物，机选号码契合天干地支五行数理。"
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Real-time Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Today,
                        contentDescription = "今日气运",
                        tint = FortuneGold,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "今日实时干支与黄历气运",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = FortuneGold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = lunarDateStr,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = FortuneGold
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 4 LiuRi Pillars Badges
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(vertical = 10.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                LiuRiBadge(label = "流年", ganzhi = todayPillars.year.fullText, elem = todayPillars.year.element)
                LiuRiBadge(label = "流月", ganzhi = todayPillars.month.fullText, elem = todayPillars.month.element)
                LiuRiBadge(label = "流日", ganzhi = todayPillars.day.fullText, elem = todayPillars.day.element, isHighlight = true)
                LiuRiBadge(label = "流时", ganzhi = todayPillars.hour.fullText, elem = todayPillars.hour.element)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Elemental Harmony Explanation
            Text(
                text = harmonyDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun LiuRiBadge(
    label: String,
    ganzhi: String,
    elem: String,
    isHighlight: Boolean = false
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isHighlight) FortuneGold else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = ganzhi,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = if (isHighlight) ImperialRed else MaterialTheme.colorScheme.onSurface
        )
        if (elem.isNotBlank()) {
            Text(
                text = elem,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = FortuneGold
            )
        }
    }
}
