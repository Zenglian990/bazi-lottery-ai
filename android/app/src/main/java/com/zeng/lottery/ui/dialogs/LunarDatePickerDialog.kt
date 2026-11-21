package com.zeng.lottery.ui.dialogs

import androidx.compose.runtime.Composable
import com.zeng.lottery.data.model.LunarDate
import com.zeng.lottery.ui.components.LunarDatePickerDialog as ComponentLunarDatePickerDialog

/**
 * LunarDatePickerDialog in dialogs package delegating to component implementation.
 */
@Composable
fun LunarDatePickerDialog(
    currentDate: LunarDate,
    onDismissRequest: () -> Unit,
    onDateSelected: (LunarDate) -> Unit
) {
    ComponentLunarDatePickerDialog(
        currentDate = currentDate,
        onDismissRequest = onDismissRequest,
        onDateSelected = onDateSelected
    )
}
