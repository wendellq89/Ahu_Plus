package com.yourname.ahu_plus.ui.screen.schedule.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import com.yourname.ahu_plus.ui.screen.schedule.components.CollapsibleSection
import kotlinx.coroutines.launch

/** 课程备注最大字符数 */
private const val NOTE_MAX_LEN = 500

/**
 * 课程备注 section (跨节次共享,按 courseCode 聚合)。
 *
 * 内部维护 draft 状态;保存时调 [onSave] 回调。父组件负责把 note 存到
 * CourseNoteRepository 并在 save 完成后回写已保存值 (用于回滚失败时的状态)。
 */
@Composable
fun CourseNoteSection(
    savedNote: String,
    onSave: suspend (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember(savedNote) { mutableStateOf(savedNote) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    CollapsibleSection(
        title = "课程备注",
        defaultExpanded = false, // 2026-06-17: 全部默认收起
        modifier = modifier,
    ) {
        Text(
            text = "这门课的所有周次、所有节次都能看到这条备注。例如:整体复习重点、教师偏好等。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = draft,
            onValueChange = { newText ->
                if (newText.length <= NOTE_MAX_LEN) draft = newText
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp),
            placeholder = { Text("例如:整体复习重点章节 3、5、7;教师偏好用 PPT…") },
            supportingText = {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "${draft.length} / $NOTE_MAX_LEN",
                        modifier = Modifier.align(Alignment.CenterEnd),
                        color = if (draft.length >= NOTE_MAX_LEN)
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            },
            maxLines = 6,
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
                        try { onSave(draft) } finally { saving = false }
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !saving && draft != savedNote,
            ) {
                if (saving) CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                ) else Text("保存备注")
            }
        }
    }
}
