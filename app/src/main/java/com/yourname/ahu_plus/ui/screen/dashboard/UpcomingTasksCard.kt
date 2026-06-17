package com.yourname.ahu_plus.ui.screen.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourname.ahu_plus.data.model.task.RecentTaskItem
import com.yourname.ahu_plus.data.model.task.RecentTaskSource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 首页"近期任务"卡片 (2026-06-17 新增)。
 *
 * 显示作业 + 考试 + 用户自定义待办,按截止时间升序。
 * 已完成项可勾选 (划线);考试项不可勾选。
 */
@Composable
fun UpcomingTasksCard(
    tasks: List<RecentTaskItem>,
    onToggleComplete: (RecentTaskItem) -> Unit,
    onAdd: () -> Unit,
    onClickExam: (RecentTaskItem) -> Unit = {},
    onViewAll: () -> Unit = {},
    maxItems: Int = 5,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 头部
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = " 近期任务",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (tasks.isNotEmpty()) {
                    Text(
                        text = "${tasks.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onAdd) {
                    Icon(Icons.Filled.Add, contentDescription = "添加待办",
                        tint = MaterialTheme.colorScheme.primary)
                }
            }

            if (tasks.isEmpty()) {
                Text(
                    text = "暂无任务。点右上 + 添加自定义待办,或在课程详情里布置作业。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    tasks.take(maxItems).forEach { item ->
                        TaskRow(
                            item = item,
                            onToggle = { onToggleComplete(item) },
                            onClickExam = { onClickExam(item) },
                        )
                    }
                    if (tasks.size > maxItems) {
                        // 2026-06-17: "查看全部" 链接 (Bug9)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onViewAll() }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "还有 ${tasks.size - maxItems} 项未显示 · 查看全部",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(
    item: RecentTaskItem,
    onToggle: () -> Unit,
    onClickExam: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = item.source == RecentTaskSource.EXAM, onClick = onClickExam)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 勾选框 (仅 HOMEWORK / USER_TASK 可勾)
        if (item.source == RecentTaskSource.EXAM) {
            Icon(
                imageVector = Icons.Filled.EventNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Checkbox(
                checked = item.isCompleted,
                onCheckedChange = { onToggle() },
            )
        }

        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            if (!item.subtitle.isNullOrBlank()) {
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // 截止时间 chip
        item.dueAt?.let { epoch ->
            DueChip(epoch = epoch, isOverdue = !item.isCompleted && epoch < System.currentTimeMillis())
        }
    }
}

@Composable
private fun DueChip(epoch: Long, isOverdue: Boolean) {
    val now = System.currentTimeMillis()
    val diffMs = epoch - now
    val isPast = diffMs < 0
    val color = when {
        isOverdue -> MaterialTheme.colorScheme.error
        isPast -> MaterialTheme.colorScheme.onSurfaceVariant
        diffMs < 24L * 3600_000L -> MaterialTheme.colorScheme.error
        diffMs < 7L * 24 * 3600_000L -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val label = when {
        isOverdue -> "已截止"
        isPast -> "已结束"
        diffMs < 60_000L -> "现在"
        diffMs < 3600_000L -> "${diffMs / 60_000L} 分钟后"
        diffMs < 24L * 3600_000L -> "${diffMs / 3600_000L} 小时内"
        else -> SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(epoch))
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.14f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
