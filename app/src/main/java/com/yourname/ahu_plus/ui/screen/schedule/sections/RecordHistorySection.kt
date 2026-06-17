package com.yourname.ahu_plus.ui.screen.schedule.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourname.ahu_plus.data.model.course.RecordEntry
import com.yourname.ahu_plus.data.model.course.RecordType
import com.yourname.ahu_plus.ui.screen.schedule.components.CollapsibleSection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 记录一览 section。
 *
 * 显示该门课所有 (点名/签到/作业) 记录,按 createdAt 倒序。
 * 作业可勾选完成;所有记录可删除。
 */
@Composable
fun RecordHistorySection(
    records: List<RecordEntry>,
    onToggleCompleted: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    CollapsibleSection(
        title = "记录一览",
        defaultExpanded = false, // 2026-06-17: 全部默认收起
        badge = if (records.isNotEmpty()) "${records.size}" else null,
        modifier = modifier,
    ) {
        if (records.isEmpty()) {
            Text(
                text = "暂无记录。在『此节课备注』section 里点 作业 / 点名 / 签到 即可添加。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 倒序: 最新的在上面
                records.sortedByDescending { it.createdAt }.forEach { entry ->
                    RecordRow(
                        entry = entry,
                        onToggleCompleted = { onToggleCompleted(entry.id) },
                        onDelete = { onDelete(entry.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordRow(
    entry: RecordEntry,
    onToggleCompleted: () -> Unit,
    onDelete: () -> Unit,
) {
    val (typeLabel, typeColor) = when (entry.type) {
        RecordType.ROLL_CALL -> "点名" to MaterialTheme.colorScheme.error
        RecordType.SIGN_IN -> "签到" to MaterialTheme.colorScheme.tertiary
        RecordType.HOMEWORK -> "作业" to MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Checkbox (仅 HOMEWORK 可勾, 其他类型用 24dp 占位保持对齐)
        if (entry.type == RecordType.HOMEWORK) {
            Checkbox(checked = entry.completed, onCheckedChange = { onToggleCompleted() })
        } else {
            // 占位对齐 (24dp 宽,避免挤压文本)
            androidx.compose.foundation.layout.Spacer(Modifier.size(24.dp))
        }

        Column(modifier = Modifier.weight(1f).padding(start = 2.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = typeColor.copy(alpha = 0.14f),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Icon(
                            imageVector = when (entry.type) {
                                RecordType.ROLL_CALL -> Icons.Filled.CheckCircle
                                RecordType.SIGN_IN -> Icons.Filled.Edit
                                RecordType.HOMEWORK -> Icons.Filled.AssignmentTurnedIn
                            },
                            contentDescription = null,
                            tint = typeColor,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = " $typeLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = typeColor,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                Text(
                    text = " 第 ${entry.week} 周 · 周${cnWeekday(entry.weekday)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val titleText = entry.text.orEmpty().ifBlank { "—" }
            Text(
                text = titleText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textDecoration = if (entry.completed) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = buildString {
                entry.deadline?.let {
                    append("截止: ")
                    append(SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(it)))
                }
                if (entry.createdAt > 0) {
                    if (isNotEmpty()) append(" · ")
                    append(SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                        .format(Date(entry.createdAt)))
                }
            }
            if (sub.isNotBlank()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                )
            }
        }

        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun cnWeekday(d: Int): String = when (d) {
    1 -> "一"; 2 -> "二"; 3 -> "三"; 4 -> "四"; 5 -> "五"; 6 -> "六"; 7 -> "日"
    else -> "?"
}
