package com.zeng.lottery.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeng.lottery.data.local.BaziEngine
import com.zeng.lottery.data.model.LunarDate
import com.zeng.lottery.ui.theme.FortuneGold
import com.zeng.lottery.ui.theme.ImperialRed

/**
 * Pure Lunar Date Picker Dialog supporting years 1930 to 2030,
 * leap month validation, 12 lunar months, 29/30 dynamic days, and 12 Shichen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LunarDatePickerDialog(
    currentDate: LunarDate,
    onDismissRequest: () -> Unit,
    onDateSelected: (LunarDate) -> Unit
) {
    var selectedYear by remember { mutableIntStateOf(currentDate.year.coerceIn(1930, 2030)) }
    var selectedMonth by remember { mutableIntStateOf(currentDate.month.coerceIn(1, 12)) }
    var isLeap by remember { mutableStateOf(currentDate.isLeap) }
    var selectedDay by remember { mutableIntStateOf(currentDate.day.coerceIn(1, 30)) }
    var selectedHour by remember { mutableIntStateOf(currentDate.hour.coerceIn(0, 23)) }
    var selectedMinute by remember { mutableIntStateOf(currentDate.minute.coerceIn(0, 59)) }

    var yearDropdownExpanded by remember { mutableStateOf(false) }

    // Validate leap month for the selected year
    val leapMonthOfYear = BaziEngine.getLeapMonth(selectedYear)
    val hasLeapForCurrentMonth = (leapMonthOfYear == selectedMonth && leapMonthOfYear > 0)
    if (!hasLeapForCurrentMonth && isLeap) {
        isLeap = false
    }

    // Days in selected lunar month (29 or 30)
    val maxDaysInMonth = BaziEngine.getDaysInLunarMonth(selectedYear, selectedMonth, isLeap)
    if (selectedDay > maxDaysInMonth) {
        selectedDay = maxDaysInMonth
    }

    val currentGanzhiYear = BaziEngine.getYearGanZhi(selectedYear)
    val currentZodiac = BaziEngine.getZodiac(selectedYear)
    val monthName = (if (isLeap) "闰" else "") + BaziEngine.LUNAR_MONTH_NAMES.getOrElse(selectedMonth - 1) { "${selectedMonth}月" }
    val dayName = BaziEngine.LUNAR_DAY_NAMES.getOrElse(selectedDay - 1) { "${selectedDay}日" }
    val shichenName = BaziEngine.getShichenName(selectedHour)

    val previewText = "${selectedYear}年 (${currentGanzhiYear}${currentZodiac}年) 农历$monthName$dayName $shichenName"
    val tempSolar = try {
        BaziEngine.lunarToSolar(
            LunarDate(
                year = selectedYear,
                month = selectedMonth,
                isLeap = isLeap,
                day = selectedDay,
                hour = selectedHour,
                minute = selectedMinute
            )
        )
    } catch (e: Exception) {
        null
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "选择纯农历生辰",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = ImperialRed
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Real-time Lunar Preview Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(FortuneGold.copy(alpha = 0.15f))
                        .border(1.dp, FortuneGold.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = previewText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ImperialRed,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (tempSolar != null) "对应公历：${tempSolar.year}年${tempSolar.monthValue}月${tempSolar.dayOfMonth}日" else "对应公历年份：${selectedYear}年",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 1. Year Selection (1930 - 2030)
                Text(
                    text = "一、农历年份 (1930 - 2030)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = { if (selectedYear > 1930) selectedYear-- },
                        enabled = selectedYear > 1930
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "上一年")
                    }

                    Box {
                        OutlinedButton(
                            onClick = { yearDropdownExpanded = true },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "${selectedYear}年 (${currentGanzhiYear}${currentZodiac}年)",
                                fontWeight = FontWeight.Bold,
                                color = ImperialRed
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }

                        DropdownMenu(
                            expanded = yearDropdownExpanded,
                            onDismissRequest = { yearDropdownExpanded = false },
                            modifier = Modifier.heightIn(max = 280.dp)
                        ) {
                            for (y in 1930..2030) {
                                val gz = BaziEngine.getYearGanZhi(y)
                                val zd = BaziEngine.getZodiac(y)
                                DropdownMenuItem(
                                    text = { Text("${y}年 ($gz${zd}年)") },
                                    onClick = {
                                        selectedYear = y
                                        yearDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { if (selectedYear < 2030) selectedYear++ },
                        enabled = selectedYear < 2030
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "下一年")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Month Selection with Leap Month Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "二、农历月份",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (leapMonthOfYear > 0) {
                        Text(
                            text = "该年闰${BaziEngine.LUNAR_MONTH_NAMES[leapMonthOfYear - 1]}",
                            style = MaterialTheme.typography.labelSmall,
                            color = FortuneGold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    BaziEngine.LUNAR_MONTH_NAMES.forEachIndexed { index, mName ->
                        val mNum = index + 1
                        val isSelected = (selectedMonth == mNum && !isLeap)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedMonth = mNum
                                isLeap = false
                            },
                            label = { Text(mName, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ImperialRed,
                                selectedLabelColor = Color.White
                            )
                        )
                    }

                    // Show leap chip if current month has a leap month
                    if (hasLeapForCurrentMonth) {
                        FilterChip(
                            selected = isLeap,
                            onClick = { isLeap = !isLeap },
                            label = { Text("闰$monthName", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = FortuneGold,
                                selectedLabelColor = Color.White,
                                containerColor = FortuneGold.copy(alpha = 0.2f),
                                labelColor = ImperialRed
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Lunar Day Selection (1 .. 29 or 30)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "三、农历日期",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (maxDaysInMonth == 30) "本月大月(30天)" else "本月小月(29天)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (d in 1..maxDaysInMonth) {
                        val dLabel = BaziEngine.LUNAR_DAY_NAMES.getOrElse(d - 1) { "${d}日" }
                        val isSelected = (selectedDay == d)
                        Box(
                            modifier = Modifier
                                .size(width = 44.dp, height = 32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isSelected) ImperialRed else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                )
                                .clickable { selectedDay = d },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dLabel,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 4. Twelve Shichen Selection
                Text(
                    text = "四、出生时辰 (十二时辰)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val shichenList = listOf(
                        Triple(0, 0, "子时 (23-01)"),
                        Triple(2, 0, "丑时 (01-03)"),
                        Triple(4, 0, "寅时 (03-05)"),
                        Triple(6, 0, "卯时 (05-07)"),
                        Triple(8, 0, "辰时 (07-09)"),
                        Triple(10, 0, "巳时 (09-11)"),
                        Triple(12, 0, "午时 (11-13)"),
                        Triple(14, 0, "未时 (13-15)"),
                        Triple(16, 0, "申时 (15-17)"),
                        Triple(18, 0, "酉时 (17-19)"),
                        Triple(20, 0, "戌时 (19-21)"),
                        Triple(22, 0, "亥时 (21-23)")
                    )

                    shichenList.forEach { (h, m, label) ->
                        val currentShichenIdx = ((selectedHour + 1) % 24) / 2
                        val targetShichenIdx = ((h + 1) % 24) / 2
                        val isSelected = (currentShichenIdx == targetShichenIdx)

                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedHour = h
                                selectedMinute = m
                            },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ImperialRed,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDateSelected(
                        LunarDate(
                            year = selectedYear,
                            month = selectedMonth,
                            isLeap = isLeap,
                            day = selectedDay,
                            hour = selectedHour,
                            minute = selectedMinute
                        )
                    )
                }
            ) {
                Text("确定", fontWeight = FontWeight.Bold, color = ImperialRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("取消")
            }
        }
    )
}
