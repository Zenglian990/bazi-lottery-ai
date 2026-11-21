package com.zeng.lottery.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeng.lottery.data.local.BaziEngine
import com.zeng.lottery.data.model.BaziDestiny
import com.zeng.lottery.data.model.LunarDate
import com.zeng.lottery.data.model.SavedBirthProfile
import com.zeng.lottery.data.model.TodayFortuneResponse
import com.zeng.lottery.ui.components.LunarDatePickerDialog
import com.zeng.lottery.ui.components.ShichenPickerDialog
import com.zeng.lottery.ui.components.TodayLiuRiCard
import com.zeng.lottery.ui.theme.FortuneGold
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthInputScreen(
    lunarDate: LunarDate,
    isMale: Boolean,
    destiny: BaziDestiny?,
    todayFortune: TodayFortuneResponse? = null,
    userName: String = "缘主",
    birthLongitude: Double = 120.0,
    birthProfiles: List<SavedBirthProfile> = emptyList(),
    onUserNameChanged: (String) -> Unit = {},
    onLunarDateChanged: (LunarDate) -> Unit,
    onResetCurrentTime: () -> Unit = {},
    onGenderChanged: (Boolean) -> Unit,
    onLongitudeChanged: (Double) -> Unit = {},
    onSaveProfile: (String) -> Unit = {},
    onApplyProfile: (SavedBirthProfile) -> Unit = {},
    onDeleteProfile: (String) -> Unit = {},
    showTodayLiuRi: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showShichenPicker by rememberSaveable { mutableStateOf(false) }
    var showLongitudeMenu by remember { mutableStateOf(false) }
    var showSaveProfileDialog by rememberSaveable { mutableStateOf(false) }
    var profileLabelInput by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (showTodayLiuRi) {
            TodayLiuRiCard(destiny = destiny, todayFortune = todayFortune)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
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
                    Text(
                        text = "生辰八字天干地支排盘",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "农历 / 公历互转",
                        style = MaterialTheme.typography.labelSmall,
                        color = FortuneGold,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // 多亲友生辰档案快捷切换条
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            profileLabelInput = if (userName.isBlank() || userName == "缘主") "本人" else userName
                            showSaveProfileDialog = true
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = FortuneGold)
                    ) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = "存为生辰档案", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("存为档案", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    birthProfiles.forEach { profile ->
                        InputChip(
                            selected = profile.lunarDate == lunarDate && profile.userName == userName && profile.isMale == isMale,
                            onClick = { onApplyProfile(profile) },
                            label = { Text(profile.label, fontSize = 11.sp, maxLines = 1) },
                            trailingIcon = {
                                IconButton(
                                    onClick = { onDeleteProfile(profile.id) },
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "删除档案", modifier = Modifier.size(12.dp))
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 缘主姓名 + 一键对齐当前农历
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = if (userName == "缘主") "" else userName,
                        onValueChange = { raw ->
                            val clean = raw.replace("<", "").replace(">", "").take(20)
                            onUserNameChanged(clean)
                        },
                        label = { Text("缘主姓名 (用于五格数理)", style = MaterialTheme.typography.labelSmall) },
                        placeholder = { Text("未填写时默认“缘主”", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1.15f),
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
                        onClick = {
                            onResetCurrentTime()
                            try {
                                val nowLunar = BaziEngine.solarToLunar(LocalDateTime.now())
                                Toast.makeText(
                                    context,
                                    "已对齐当前农历：${nowLunar.year}年${nowLunar.monthZh}${nowLunar.dayZh} ${nowLunar.shichenZh}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } catch (_: Throwable) {
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(54.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = FortuneGold)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Update,
                            contentDescription = "设为当前农历",
                            modifier = Modifier.size(16.dp),
                            tint = FortuneGold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "当前农历",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 第 1 行：全宽日期选择按钮（支持农历/公历双模式弹窗）
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "选择农历或公历生辰",
                        modifier = Modifier.size(20.dp),
                        tint = FortuneGold
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${lunarDate.year}年 (${lunarDate.ganzhiYear}${lunarDate.zodiac}年)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Text(
                                text = "农历",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = FortuneGold,
                                modifier = Modifier
                                    .background(FortuneGold.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${lunarDate.monthZh}${lunarDate.dayZh}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "修改日期",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 第 2 行：时辰选择 + 乾造/坤造切换
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { showShichenPicker = true },
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "十二时辰",
                            modifier = Modifier.size(16.dp),
                            tint = FortuneGold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${lunarDate.shichenZh} (${BaziEngine.getShichenShortRange(lunarDate.hour)})",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    FilterChip(
                        selected = isMale,
                        onClick = { onGenderChanged(!isMale) },
                        modifier = Modifier.weight(0.9f),
                        shape = RoundedCornerShape(10.dp),
                        label = {
                            Text(
                                text = if (isMale) "乾造(男)" else "坤造(女)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f),
                            selectedLabelColor = MaterialTheme.colorScheme.secondary
                        )
                    )
                }

                // 第 3 行：出生地真太阳时经度选择
                Spacer(modifier = Modifier.height(8.dp))
                val currentCityLabel = BaziEngine.CITY_LONGITUDE_PRESETS
                    .minByOrNull { kotlin.math.abs(it.second - birthLongitude) }
                    ?.takeIf { kotlin.math.abs(it.second - birthLongitude) < 0.25 }
                    ?.first
                    ?: "东经 ${String.format("%.1f", birthLongitude)}°"

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { showLongitudeMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "出生地经度",
                            tint = FortuneGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "真太阳时出生地：$currentCityLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        val offsetMin = ((birthLongitude - 120.0) * 4.0).toInt()
                        Text(
                            text = if (offsetMin == 0) "标准时" else "${if (offsetMin > 0) "+" else ""}${offsetMin}分",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = FortuneGold
                        )
                    }

                    DropdownMenu(
                        expanded = showLongitudeMenu,
                        onDismissRequest = { showLongitudeMenu = false },
                        modifier = Modifier.heightIn(max = 280.dp)
                    ) {
                        BaziEngine.CITY_LONGITUDE_PRESETS.forEach { (cityName, lon) ->
                            DropdownMenuItem(
                                text = { Text(cityName, fontSize = 13.sp) },
                                onClick = {
                                    onLongitudeChanged(lon)
                                    showLongitudeMenu = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 四柱八字实时卡片
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

    if (showSaveProfileDialog) {
        AlertDialog(
            onDismissRequest = { showSaveProfileDialog = false },
            title = { Text("保存为生辰档案", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = profileLabelInput,
                    onValueChange = { profileLabelInput = it.take(12) },
                    label = { Text("档案标签 (如：本人 / 家人)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSaveProfile(profileLabelInput)
                        showSaveProfileDialog = false
                    }
                ) {
                    Text("保存", color = FortuneGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveProfileDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 向后兼容旧版 LocalDateTime 调用入口。
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
            onDateTimeChanged(BaziEngine.lunarToSolar(newLunar))
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
    val displayElem = element
        .replace("metal", "金")
        .replace("wood", "木")
        .replace("water", "水")
        .replace("fire", "火")
        .replace("earth", "土")

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
