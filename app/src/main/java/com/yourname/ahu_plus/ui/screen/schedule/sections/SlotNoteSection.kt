package com.yourname.ahu_plus.ui.screen.schedule.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yourname.ahu_plus.ui.screen.schedule.components.AddHomeworkDialog
import com.yourname.ahu_plus.ui.screen.schedule.components.CollapsibleSection
import kotlinx.coroutines.launch

/** 此节课备注最大字符数 */
private const val NOTE_MAX_LEN = 500

/**
 * 此节课备注 section (按 lessonId+week 唯一)。
 *
 * 包含:
 *  - 多行备注编辑 (保存/还原)
 *  - 两个 quick action: 签到 / 作业 (2026-06-17: 点名与签到合并为"签到"一个按钮)
 *
 * @param savedNote 已保存的备注
 * @param courseName 课程名 (用于作业对话框标题)
 * @param hasSignedIn 当前节次是否已签到 (UI 差异化显示)
 * @param onSaveNote 保存此节课备注
 * @param onAddSignIn 添加签到记录 (无描述)
 * @param onAddHomework 添加作业 (弹 AddHomeworkDialog)
 */
@Composable
fun SlotNoteSection(
    savedNote: String,
    courseName: String,
    hasSignedIn: Boolean,
    onSaveNote: suspend (String) -> Unit,
    onAddSignIn: () -> Unit,
    onAddHomework: (text: String, deadline: Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember(savedNote) { mutableStateOf(savedNote) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var showHomeworkDialog by remember { mutableStateOf(false) }

    CollapsibleSection(
        title = "此节课备注",
        defaultExpanded = false,
        modifier = modifier,
    ) {
        Text(
            text = "记录本次课的具体事项,例如:今天讲到第几章、临时安排等。仅本节可见。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Quick action 行 (点名 + 签到 已合并为"签到"一个按钮)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(
                onClick = onAddSignIn,
                label = { Text("签到") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                        tint = if (hasSignedIn) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            AssistChip(
                onClick = { showHomeworkDialog = true },
                label = { Text("作业") },
                leadingIcon = {
                    Icon(Icons.Filled.AssignmentTurnedIn, contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize))
                },
            )
        }

        OutlinedTextField(
            value = draft,
            onValueChange = { newText -> if (newText.length <= NOTE_MAX_LEN) draft = newText },
            modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
            placeholder = { Text("本节课特别事项、点名情况、复习要点等…") },
            supportingText = {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "${draft.length} / $NOTE_MAX_LEN",
                        modifier = Modifier.align(Alignment.CenterEnd),
                        color = if (draft.length >= NOTE_MAX_LEN) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            },
            maxLines = 5,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { draft = savedNote },
                modifier = Modifier.weight(1f),
                enabled = draft != savedNote && !saving,
            ) { Text("还原") }
            Button(
                onClick = {
                    scope.launch {
                        saving = true
                        try { onSaveNote(draft) } finally { saving = false }
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !saving && draft != savedNote,
            ) {
                if (saving) CircularProgressIndicator(
                    modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                ) else Text("保存备注")
            }
        }
    }

    if (showHomeworkDialog) {
        AddHomeworkDialog(
            courseName = courseName,
            onDismiss = { showHomeworkDialog = false },
            onConfirm = { text, deadline ->
                onAddHomework(text, deadline)
                showHomeworkDialog = false
            },
        )
    }
}
