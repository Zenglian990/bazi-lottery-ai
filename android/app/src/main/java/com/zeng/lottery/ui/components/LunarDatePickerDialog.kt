package com.zeng.lottery.ui.components

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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * 农历 / 公历双向实时互转生辰选择器（支持 1900 ~ 2100 年、精确闰月与早/晚子时区分）。
 * 严格遵循纯派生状态原则，禁止在组合期间直接修改 MutableState。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LunarDatePickerDialog(
    currentDate: LunarDate,
    onDismissRequest: () -> Unit,
    onDateSelected: (LunarDate) -> Unit
) {
    val initSolar = BaziEngine.lunarToSolar(currentDate)

    // Tab 0 = 农历模式，Tab 1 = 公历模式
    var calendarMode by rememberSaveable { mutableIntStateOf(0) }

    // 农历状态
    var selectedYear by rememberSaveable { mutableIntStateOf(currentDate.year.coerceIn(1900, 2100)) }
    var selectedMonth by rememberSaveable { mutableIntStateOf(currentDate.month.coerceIn(1, 12)) }
    var isLeap by rememberSaveable { mutableStateOf(currentDate.isLeap) }
    var selectedDay by rememberSaveable { mutableIntStateOf(currentDate.day.coerceIn(1, 30)) }

    // 公历状态
    var solarYear by rememberSaveable { mutableIntStateOf(initSolar.year.coerceIn(1900, 2100)) }
    var solarMonth by rememberSaveable { mutableIntStateOf(initSolar.monthValue.coerceIn(1, 12)) }
    var solarDay by rememberSaveable { mutableIntStateOf(initSolar.dayOfMonth.coerceIn(1, 31)) }

    // 共用时辰状态（精确区分 0=早子时 与 23=晚子时）
    var selectedHour by rememberSaveable { mutableIntStateOf(currentDate.hour.coerceIn(0, 23)) }
    var selectedMinute by rememberSaveable { mutableIntStateOf(currentDate.minute.coerceIn(0, 59)) }

    var yearDropdownExpanded by rememberSaveable { mutableStateOf(false) }

    // 纯函数派生有效农历值（绝不在组合期间覆写 state 变量）
    val leapMonthOfYear = BaziEngine.getLeapMonth(selectedYear)
    val hasLeapForCurrentMonth = (leapMonthOfYear == selectedMonth && leapMonthOfYear > 0)
    val effectiveIsLeap = isLeap && hasLeapForCurrentMonth
    val maxLunarDays = BaziEngine.getDaysInLunarMonth(selectedYear, selectedMonth, effectiveIsLeap)
    val effectiveLunarDay = selectedDay.coerceIn(1, maxLunarDays)

    val maxSolarDays = YearMonth.of(solarYear.coerceIn(1900, 2100), solarMonth.coerceIn(1, 12)).lengthOfMonth()
    val effectiveSolarDay = solarDay.coerceIn(1, maxSolarDays)

    val effectiveLunarDate = if (calendarMode == 0) {
        LunarDate(
            year = selectedYear,
            month = selectedMonth,
            isLeap = effectiveIsLeap,
            day = effectiveLunarDay,
            hour = selectedHour,
            minute = selectedMinute
        )
    } else {
        BaziEngine.solarToLunar(
            LocalDateTime.of(solarYear, solarMonth, effectiveSolarDay, selectedHour, selectedMinute)
        )
    }

    val effectiveSolarDt = try {
        if (calendarMode == 0) {
            BaziEngine.lunarToSolar(effectiveLunarDate)
        } else {
            LocalDateTime.of(solarYear, solarMonth, effectiveSolarDay, selectedHour, selectedMinute)
        }
    } catch (_: Exception) {
        null
    }

    val baseMonthName = BaziEngine.LUNAR_MONTH_NAMES.getOrElse(selectedMonth - 1) { "${selectedMonth}月" }
    val previewText = effectiveLunarDate.displayFullText

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "选择生辰日期",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                OutlinedButton(
                    onClick = {
                        val now = LocalDateTime.now()
                        val nowLunar = BaziEngine.solarToLunar(now)
                        selectedYear = nowLunar.year.coerceIn(1900, 2100)
                        selectedMonth = nowLunar.month.coerceIn(1, 12)
                        isLeap = nowLunar.isLeap
                        selectedDay = nowLunar.day.coerceIn(1, 30)
                        solarYear = now.year.coerceIn(1900, 2100)
                        solarMonth = now.monthValue
                        solarDay = now.dayOfMonth
                        selectedHour = now.hour
                        selectedMinute = now.minute
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = FortuneGold)
                ) {
                    Icon(
                        imageVector = Icons.Default.Update,
                        contentDescription = "对齐当前时间",
                        modifier = Modifier.size(13.dp),
                        tint = FortuneGold
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("对齐当前", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // 农历 / 公历 双向切换栏
                TabRow(
                    selectedTabIndex = calendarMode,
                    containerColor = Color.Transparent,
                    contentColor = FortuneGold
                ) {
                    Tab(
                        selected = calendarMode == 0,
                        onClick = {
                            if (calendarMode != 0) {
                                val converted = BaziEngine.solarToLunar(
                                    LocalDateTime.of(solarYear, solarMonth, effectiveSolarDay, selectedHour, selectedMinute)
                                )
                                selectedYear = converted.year.coerceIn(1900, 2100)
                                selectedMonth = converted.month
                                isLeap = converted.isLeap
                                selectedDay = converted.day
                                calendarMode = 0
                            }
                        },
                        text = { Text("农历选择", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    )
                    Tab(
                        selected = calendarMode == 1,
                        onClick = {
                            if (calendarMode != 1) {
                                val converted = BaziEngine.lunarToSolar(effectiveLunarDate)
                                solarYear = converted.year.coerceIn(1900, 2100)
                                solarMonth = converted.monthValue
                                solarDay = converted.dayOfMonth
                                calendarMode = 1
                            }
                        },
                        text = { Text("公历直选", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 实时双向预览横幅
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(FortuneGold.copy(alpha = 0.14f))
                        .border(1.dp, FortuneGold.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = previewText,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (effectiveSolarDt != null) {
                                "对应公历：${effectiveSolarDt.year}年${effectiveSolarDt.monthValue}月${effectiveSolarDt.dayOfMonth}日 ${String.format("%02d:%02d", effectiveSolarDt.hour, effectiveSolarDt.minute)}"
                            } else {
                                "对应公历年份：${effectiveLunarDate.year}年"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (calendarMode == 0) {
                    // ============ 农历模式 ============
                    Text(
                        text = "一、农历年份 (1900 - 2100)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    val curGz = BaziEngine.getYearGanZhi(selectedYear)
                    val curZodiac = BaziEngine.getZodiac(selectedYear)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = { if (selectedYear > 1900) selectedYear-- }, enabled = selectedYear > 1900) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "上一年")
                        }
                        Box {
                            OutlinedButton(onClick = { yearDropdownExpanded = true }, shape = RoundedCornerShape(10.dp)) {
                                Text(
                                    text = "${selectedYear}年 (${curGz}${curZodiac}年)",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "展开年份列表")
                            }
                            DropdownMenu(
                                expanded = yearDropdownExpanded,
                                onDismissRequest = { yearDropdownExpanded = false },
                                modifier = Modifier.heightIn(max = 280.dp)
                            ) {
                                for (y in 1900..2100) {
                                    DropdownMenuItem(
                                        text = { Text("${y}年 (${BaziEngine.getYearGanZhi(y)}${BaziEngine.getZodiac(y)}年)") },
                                        onClick = {
                                            selectedYear = y
                                            yearDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { if (selectedYear < 2100) selectedYear++ }, enabled = selectedYear < 2100) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "下一年")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("二、农历月份", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
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
                            val isSelected = (selectedMonth == mNum && !effectiveIsLeap)
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

                        // 修复 "闰闰X月" Bug：始终用不含前缀的 baseMonthName 拼接
                        if (hasLeapForCurrentMonth) {
                            FilterChip(
                                selected = effectiveIsLeap,
                                onClick = { isLeap = !effectiveIsLeap },
                                label = { Text("闰$baseMonthName", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = FortuneGold,
                                    selectedLabelColor = Color(0xFF1A1200),
                                    containerColor = FortuneGold.copy(alpha = 0.2f),
                                    labelColor = FortuneGold
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("三、农历日期", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (maxLunarDays == 30) "大月 (30天)" else "小月 (29天)",
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
                        for (d in 1..maxLunarDays) {
                            val dLabel = BaziEngine.LUNAR_DAY_NAMES.getOrElse(d - 1) { "${d}日" }
                            val isSelected = (effectiveLunarDay == d)
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
                } else {
                    // ============ 公历直选模式 ============
                    Text(
                        text = "一、公历年份 (1900 - 2100)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = { if (solarYear > 1900) solarYear-- }, enabled = solarYear > 1900) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "上一年")
                        }
                        Box {
                            OutlinedButton(onClick = { yearDropdownExpanded = true }, shape = RoundedCornerShape(10.dp)) {
                                Text("${solarYear}年", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "展开公历年份")
                            }
                            DropdownMenu(
                                expanded = yearDropdownExpanded,
                                onDismissRequest = { yearDropdownExpanded = false },
                                modifier = Modifier.heightIn(max = 280.dp)
                            ) {
                                for (y in 1900..2100) {
                                    DropdownMenuItem(
                                        text = { Text("${y}年") },
                                        onClick = {
                                            solarYear = y
                                            yearDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { if (solarYear < 2100) solarYear++ }, enabled = solarYear < 2100) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "下一年")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("二、公历月份", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (m in 1..12) {
                            FilterChip(
                                selected = solarMonth == m,
                                onClick = { solarMonth = m },
                                label = { Text("${m}月", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ImperialRed,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("三、公历日期 (${maxSolarDays}天)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (d in 1..maxSolarDays) {
                            val isSelected = (effectiveSolarDay == d)
                            Box(
                                modifier = Modifier
                                    .size(width = 40.dp, height = 32.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isSelected) ImperialRed else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    )
                                    .clickable { solarDay = d },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = String.format("%02d", d),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 四、传统十二时辰（精准区分早子时 00-01 与晚子时 23-24）
                Text(
                    text = "四、出生时辰 (含早/晚子时区分)",
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
                    val shichenOptions = listOf(
                        Triple(0, 30, "早子 (00-01)"),
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
                        Triple(22, 0, "亥时 (21-23)"),
                        Triple(23, 30, "晚子 (23-24)")
                    )

                    shichenOptions.forEach { (h, m, label) ->
                        val isSelected = when (h) {
                            0 -> selectedHour == 0
                            23 -> selectedHour == 23
                            else -> selectedHour == h || selectedHour == h - 1
                        }
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
            TextButton(onClick = { onDateSelected(effectiveLunarDate) }) {
                Text("确定", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("取消")
            }
        }
    )
}
