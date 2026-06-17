package com.yourname.ahu_plus.ui.screen.schedule.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 作业添加对话框。
 *
 * 字段:
 *  - 标题 (必填)
 *  - 描述 (可空)
 *  - 截止时间 (可空,选填后弹 DatePicker 再选 TimePicker)
 *
 * @param courseName 显示在标题上方的"X 课程 · 布置作业"提示
 * @param onDismiss 取消/关闭
 * @param onConfirm 确认: (text, deadline: Long?) → ViewModel 添加 HomeworkRecord + RecordEntry
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddHomeworkDialog(
    courseName: String,
    onDismiss: () -> Unit,
    onConfirm: (text: String, deadline: Long?) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var hasDeadline by remember { mutableStateOf(false) }
    var pickedDateMillis by remember { mutableStateOf<Long?>(null) }
    var pickedHour by remember { mutableStateOf(23) }
    var pickedMinute by remember { mutableStateOf(59) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val deadlineMillis: Long? = remember(pickedDateMillis, pickedHour, pickedMinute) {
        if (!hasDeadline || pickedDateMillis == null) null
        else {
            Calendar.getInstance(TimeZone.getDefault()).apply {
                timeInMillis = pickedDateMillis!!
                set(Calendar.HOUR_OF_DAY, pickedHour)
                set(Calendar.MINUTE, pickedMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = pickedDateMillis ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickedDateMillis = state.selectedDateMillis
                    showDatePicker = false
                    showTimePicker = true
                }) { Text("下一步") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(
            initialHour = pickedHour,
            initialMinute = pickedMinute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("选择截止时刻") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()) {
                    TimePicker(state = state)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pickedHour = state.hour
                    pickedMinute = state.minute
                    showTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("取消") }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("布置作业") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = courseName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(80) },
                    label = { Text("标题") },
                    placeholder = { Text("例如:第 3 章课后习题 1-5") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(500) },
                    label = { Text("说明 (可选)") },
                    placeholder = { Text("要求、提交方式等") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("设置截止时间", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = hasDeadline,
                        onCheckedChange = {
                            hasDeadline = it
                            if (it && pickedDateMillis == null) {
                                showDatePicker = true
                            }
                        },
                    )
                }

                if (hasDeadline) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            val d = pickedDateMillis?.let {
                                SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(it))
                            } ?: "选择日期"
                            Text(d)
                        }
                        OutlinedButton(
                            onClick = {
                                if (pickedDateMillis != null) showTimePicker = true
                                else showDatePicker = true
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(String.format(Locale.getDefault(), "%02d:%02d", pickedHour, pickedMinute))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title.trim(), deadlineMillis) },
                enabled = title.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
