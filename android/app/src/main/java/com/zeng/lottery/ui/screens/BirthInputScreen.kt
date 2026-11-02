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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeng.lottery.data.model.BaziDestiny
import com.zeng.lottery.ui.components.ShichenPickerDialog
import com.zeng.lottery.ui.components.TodayLiuRiCard
import com.zeng.lottery.ui.theme.FortuneGold
import com.zeng.lottery.ui.theme.ImperialRed
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthInputScreen(
    selectedDateTime: LocalDateTime,
    isLunar: Boolean,
    isMale: Boolean,
    destiny: BaziDestiny?,
    userName: String = "曾先生",
    onUserNameChanged: (String) -> Unit = {},
    onDateTimeChanged: (LocalDateTime) -> Unit,
    onLunarChanged: (Boolean) -> Unit,
    onGenderChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
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
                // Header Row
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

                    // Solar / Lunar Switch
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isLunar) "农历" else "公历",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Switch(
                            checked = isLunar,
                            onCheckedChange = onLunarChanged,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = FortuneGold,
                                checkedTrackColor = FortuneGold.copy(alpha = 0.4f)
                            )
                        )
                    }
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
                        onClick = { onDateTimeChanged(LocalDateTime.now()) },
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
                            text = "设为当前时间",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Date / Time / Shichen / Gender Selector Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Date picker trigger button
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.weight(1.1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "选择日期",
                            modifier = Modifier.size(14.dp),
                            tint = FortuneGold
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = selectedDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
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
                        val shichenName = when (selectedDateTime.hour) {
                            0 -> "早子时"
                            1, 2 -> "丑时"
                            3, 4 -> "寅时"
                            5, 6 -> "卯时"
                            7, 8 -> "辰时"
                            9, 10 -> "巳时"
                            11, 12 -> "午时"
                            13, 14 -> "未时"
                            15, 16 -> "申时"
                            17, 18 -> "酉时"
                            19, 20 -> "戌时"
                            21, 22 -> "亥时"
                            23 -> "晚子时"
                            else -> "时辰"
                        }
                        Text(
                            text = shichenName,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Precise Time picker trigger button
                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        modifier = Modifier.weight(0.8f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = selectedDateTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
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

    // Material 3 DatePickerDialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val newLocalDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                            onDateTimeChanged(LocalDateTime.of(newLocalDate, selectedDateTime.toLocalTime()))
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("确定", color = FortuneGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("取消")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // 12 Shichen Picker Dialog
    if (showShichenPicker) {
        ShichenPickerDialog(
            currentHour = selectedDateTime.hour,
            onDismissRequest = { showShichenPicker = false },
            onShichenSelected = { hour, minute ->
                val newLocalTime = LocalTime.of(hour, minute)
                onDateTimeChanged(LocalDateTime.of(selectedDateTime.toLocalDate(), newLocalTime))
            }
        )
    }

    // Material 3 TimePickerDialog
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedDateTime.hour,
            initialMinute = selectedDateTime.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newLocalTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                        onDateTimeChanged(LocalDateTime.of(selectedDateTime.toLocalDate(), newLocalTime))
                        showTimePicker = false
                    }
                ) {
                    Text("确定", color = FortuneGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("取消")
                }
            },
            text = {
                TimePicker(state = timePickerState)
            }
        )
    }
}

@Composable
private fun PillarBadge(
    label: String,
    value: String,
    element: String,
    isDayMaster: Boolean = false
) {
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
    }
}
