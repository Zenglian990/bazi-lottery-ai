package com.zeng.lottery.ui.components

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
import androidx.compose.material.icons.filled.Explore
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeng.lottery.data.local.BaziEngine
import com.zeng.lottery.data.model.BaziDestiny
import com.zeng.lottery.data.model.TodayFortuneResponse
import com.zeng.lottery.ui.theme.ElementWood
import com.zeng.lottery.ui.theme.FortuneGold
import com.zeng.lottery.ui.theme.ImperialRedBright
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayLiuRiCard(
    destiny: BaziDestiny? = null,
    todayFortune: TodayFortuneResponse? = null,
    modifier: Modifier = Modifier
) {
    // 按今日日期做 key，跨零点自动更新
    val todayKey = LocalDate.now().toString()
    val effectiveFortune = remember(todayFortune, destiny, todayKey) {
        todayFortune ?: BaziEngine.computeTodayFortuneOffline(destiny)
    }

    val now = remember(todayKey) { LocalDateTime.now() }
    val todayPillars = remember(todayKey) { BaziEngine.calculateFourPillars(now) }
    val timeStr = remember(effectiveFortune.solarDatetime) {
        effectiveFortune.solarDatetime.takeLast(8).take(5).ifBlank {
            now.format(DateTimeFormatter.ofPattern("HH:mm", Locale.CHINESE))
        }
    }

    val resonance = effectiveFortune.resonance
    val levelBadgeColor = when (resonance?.resonanceLevel) {
        "大吉" -> FortuneGold
        "吉" -> ElementWood
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Today,
                        contentDescription = "今日气运",
                        tint = FortuneGold,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "今日实时黄历与流日共振",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (resonance != null) {
                    Box(
                        modifier = Modifier
                            .background(levelBadgeColor.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                            .border(0.8.dp, levelBadgeColor.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${resonance.resonanceLevel} ×${String.format("%.2f", resonance.resonanceMultiplier)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = levelBadgeColor
                        )
                    }
                } else {
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = FortuneGold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = buildString {
                    append(effectiveFortune.solarDate)
                    if (effectiveFortune.lunarDate.isNotBlank()) append(" · 农历 ").append(effectiveFortune.lunarDate)
                    if (effectiveFortune.dayNayin.isNotBlank()) append(" · 纳音【").append(effectiveFortune.dayNayin).append("】")
                },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = FortuneGold
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 四柱实时流运徽章
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
                LiuRiBadge("流年", effectiveFortune.ganzhiYear.ifBlank { todayPillars.year.fullText }, todayPillars.year.element)
                LiuRiBadge("流月", effectiveFortune.ganzhiMonth.ifBlank { todayPillars.month.fullText }, todayPillars.month.element)
                LiuRiBadge("流日", effectiveFortune.ganzhiDay.ifBlank { todayPillars.day.fullText }, todayPillars.day.element, isHighlight = true)
                LiuRiBadge("流时", effectiveFortune.ganzhiHour.ifBlank { todayPillars.hour.fullText }, todayPillars.hour.element)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 财神方位 / 喜神方位 / 今日冲煞
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (effectiveFortune.caiShenPosition.isNotBlank()) {
                    DirectionChip("财神位", effectiveFortune.caiShenPosition, FortuneGold)
                }
                if (effectiveFortune.xiShenPosition.isNotBlank()) {
                    DirectionChip("喜神位", effectiveFortune.xiShenPosition, ElementWood)
                }
                if (effectiveFortune.chongSha.isNotBlank()) {
                    DirectionChip("冲煞", effectiveFortune.chongSha, ImperialRedBright)
                }
            }

            // 今日黄历宜忌
            if (effectiveFortune.dayYi.isNotEmpty() || effectiveFortune.dayJi.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (effectiveFortune.dayYi.isNotEmpty()) {
                        Text(
                            text = "【宜】 ${effectiveFortune.dayYi.take(6).joinToString(" · ")}",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            color = ElementWood
                        )
                    }
                    if (effectiveFortune.dayJi.isNotEmpty()) {
                        Text(
                            text = "【忌】 ${effectiveFortune.dayJi.take(6).joinToString(" · ")}",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = resonance?.description?.ifBlank { null }
                    ?: "今日流日【${effectiveFortune.ganzhiDay}】，天道循环，顺势而为，请结合个人八字喜用神理性参考。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun DirectionChip(label: String, value: String, tint: Color) {
    Row(
        modifier = Modifier
            .background(tint.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Explore,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$label: $value",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = tint
        )
    }
}

@Composable
private fun LiuRiBadge(
    label: String,
    ganzhi: String,
    elem: String,
    isHighlight: Boolean = false
) {
    val displayElem = elem
        .replace("metal", "金")
        .replace("wood", "木")
        .replace("water", "水")
        .replace("fire", "火")
        .replace("earth", "土")

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
            color = if (isHighlight) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
        )
        if (displayElem.isNotBlank()) {
            Text(
                text = displayElem,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = FortuneGold
            )
        }
    }
}
