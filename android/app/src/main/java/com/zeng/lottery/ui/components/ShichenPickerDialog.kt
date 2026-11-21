package com.zeng.lottery.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeng.lottery.ui.theme.FortuneGold
import com.zeng.lottery.ui.theme.ImperialRed

data class ShichenItem(
    val name: String,
    val timeRange: String,
    val hour: Int,
    val minute: Int,
    val element: String,
    val animal: String
)

val SHICHEN_LIST = listOf(
    ShichenItem("早子时", "00:00 - 01:00", 0, 30, "水", "鼠"),
    ShichenItem("丑时", "01:00 - 03:00", 2, 0, "土", "牛"),
    ShichenItem("寅时", "03:00 - 05:00", 4, 0, "木", "虎"),
    ShichenItem("卯时", "05:00 - 07:00", 6, 0, "木", "兔"),
    ShichenItem("辰时", "07:00 - 09:00", 8, 0, "土", "龙"),
    ShichenItem("巳时", "09:00 - 11:00", 10, 0, "火", "蛇"),
    ShichenItem("午时", "11:00 - 13:00", 12, 0, "火", "马"),
    ShichenItem("未时", "13:00 - 15:00", 14, 0, "土", "羊"),
    ShichenItem("申时", "15:00 - 17:00", 16, 0, "金", "猴"),
    ShichenItem("酉时", "17:00 - 19:00", 18, 0, "金", "鸡"),
    ShichenItem("戌时", "19:00 - 21:00", 20, 0, "土", "狗"),
    ShichenItem("亥时", "21:00 - 23:00", 22, 0, "水", "猪"),
    ShichenItem("晚子时", "23:00 - 24:00", 23, 30, "水", "鼠")
)

@Composable
fun ShichenPickerDialog(
    currentHour: Int,
    onDismissRequest: () -> Unit,
    onShichenSelected: (hour: Int, minute: Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "选择传统十二时辰",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = FortuneGold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
            ) {
                Text(
                    text = "古法十二时辰划分，含早晚子时精准区分：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(SHICHEN_LIST) { item ->
                        val isSelected = when (item.name) {
                            "早子时" -> currentHour == 0
                            "晚子时" -> currentHour == 23
                            "丑时" -> currentHour in 1..2
                            "寅时" -> currentHour in 3..4
                            "卯时" -> currentHour in 5..6
                            "辰时" -> currentHour in 7..8
                            "巳时" -> currentHour in 9..10
                            "午时" -> currentHour in 11..12
                            "未时" -> currentHour in 13..14
                            "申时" -> currentHour in 15..16
                            "酉时" -> currentHour in 17..18
                            "戌时" -> currentHour in 19..20
                            "亥时" -> currentHour in 21..22
                            else -> false
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) FortuneGold.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                .clickable {
                                    onShichenSelected(item.hour, item.minute)
                                    onDismissRequest()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) FortuneGold else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "(${item.animal} · ${item.element})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    color = if (isSelected) ImperialRed else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = item.timeRange,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("关闭", color = FortuneGold)
            }
        }
    )
}
