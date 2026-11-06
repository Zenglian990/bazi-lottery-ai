package com.zeng.lottery.ui.screens

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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeng.lottery.data.local.BaziEngine
import com.zeng.lottery.data.model.BaziDestiny
import com.zeng.lottery.data.model.LunarDate
import com.zeng.lottery.ui.components.LunarDatePickerDialog
import com.zeng.lottery.ui.components.ShichenPickerDialog
import com.zeng.lottery.ui.components.TodayLiuRiCard
import com.zeng.lottery.ui.theme.FortuneGold
import com.zeng.lottery.ui.theme.ImperialRed
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthInputScreen(
    lunarDate: LunarDate,
    isMale: Boolean,
    destiny: BaziDestiny?,
    userName: String = "缘主",
    onUserNameChanged: (String) -> Unit = {},
    onLunarDateChanged: (LunarDate) -> Unit,
    onResetCurrentTime: () -> Unit = {},
    onGenderChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showShichenPicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Today's Real-time LiuRi & Celestial Fortune Card
        TodayLiuRiCard(destiny = destiny)

        // 2. User Birth Details & Four Pillars Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header Row: Pure Lunar Title without Gregorian Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "生辰八字天干地支排盘",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "农历干支体系",
                        style = MaterialTheme.typography.labelSmall,
                        color = FortuneGold,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // User Name & Set Current Time Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = userName,
                        onValueChange = onUserNameChanged,
                        label = { Text("缘主姓名", style = MaterialTheme.typography.labelSmall) },
                        placeholder = { Text("未填写时缺省为'缘主'", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1.1f),
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = FortuneGold,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )

                    OutlinedButton(
                        onClick = onResetCurrentTime,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(54.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = FortuneGold)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Update,
                            contentDescription = "设为当前时间",
                            modifier = Modifier.size(16.dp),
                            tint = FortuneGold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "设为当前农历",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Pure Lunar Date / Shichen / Gender Selector Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pure Lunar Date picker trigger button
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.weight(1.4f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "选择农历日期",
                            modifier = Modifier.size(14.dp),
                            tint = FortuneGold
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = lunarDate.displayButtonDate,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            maxLines = 1,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // 12 Shichen Picker trigger button
                    OutlinedButton(
                        onClick = { showShichenPicker = true },
                        modifier = Modifier.weight(0.9f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "十二时辰",
                            modifier = Modifier.size(14.dp),
                            tint = FortuneGold
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "${lunarDate.shichenZh} (${BaziEngine.getShichenShortRange(lunarDate.hour)})",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            maxLines = 1,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Gender Chip
                    FilterChip(
                        selected = isMale,
                        onClick = { onGenderChanged(!isMale) },
                        label = {
                            Text(
                                text = if (isMale) "乾(男)" else "坤(女)",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ImperialRed.copy(alpha = 0.25f),
                            selectedLabelColor = ImperialRed
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Four Pillars Live Badges
                destiny?.let { d ->
                    val fp = d.fourPillars
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
                        PillarBadge(label = "年柱", value = fp.year.fullText, element = fp.year.element)
                        PillarBadge(label = "月柱", value = fp.month.fullText, element = fp.month.element)
                        PillarBadge(label = "日柱 (元神)", value = fp.day.fullText, element = fp.day.element, isDayMaster = true)
                        PillarBadge(label = "时柱", value = fp.hour.fullText, element = fp.hour.element)
                    }
                }
            }
        }
    }

    // Lunar Date Picker Dialog
    if (showDatePicker) {
        LunarDatePickerDialog(
            currentDate = lunarDate,
            onDismissRequest = { showDatePicker = false },
            onDateSelected = { newLunar ->
                onLunarDateChanged(newLunar)
                showDatePicker = false
            }
        )
    }

    // 12 Shichen Picker Dialog
    if (showShichenPicker) {
        ShichenPickerDialog(
            currentHour = lunarDate.hour,
            onDismissRequest = { showShichenPicker = false },
            onShichenSelected = { hour, minute ->
                onLunarDateChanged(lunarDate.copy(hour = hour, minute = minute))
                showShichenPicker = false
            }
        )
    }
}

/**
 * Backward compatibility overload for existing callers passing LocalDateTime
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthInputScreen(
    selectedDateTime: LocalDateTime,
    isLunar: Boolean = true,
    isMale: Boolean,
    destiny: BaziDestiny?,
    userName: String = "缘主",
    onUserNameChanged: (String) -> Unit = {},
    onDateTimeChanged: (LocalDateTime) -> Unit,
    onLunarChanged: (Boolean) -> Unit = {},
    onGenderChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val lunar = remember(selectedDateTime) { BaziEngine.solarToLunar(selectedDateTime) }
    BirthInputScreen(
        lunarDate = lunar,
        isMale = isMale,
        destiny = destiny,
        userName = userName,
        onUserNameChanged = onUserNameChanged,
        onLunarDateChanged = { newLunar ->
            val solar = BaziEngine.lunarToSolar(newLunar)
            onDateTimeChanged(solar)
        },
        onResetCurrentTime = {
            onDateTimeChanged(LocalDateTime.now())
        },
        onGenderChanged = onGenderChanged,
        modifier = modifier
    )
}

@Composable
private fun PillarBadge(
    label: String,
    value: String,
    element: String,
    isDayMaster: Boolean = false
) {
    val displayElem = when {
        element.contains("fire") || element.contains("water") || element.contains("wood") || element.contains("metal") || element.contains("earth") -> {
            element.replace("metal", "金").replace("wood", "木").replace("water", "水").replace("fire", "火").replace("earth", "土")
        }
        else -> element
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isDayMaster) FortuneGold else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = if (isDayMaster) FortuneGold else MaterialTheme.colorScheme.onSurface
        )
        if (displayElem.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = displayElem,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDayMaster) FortuneGold else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
