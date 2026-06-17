package com.yourname.ahu_plus.ui.screen.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourname.ahu_plus.data.model.jw.CourseDisplayItem
import com.yourname.ahu_plus.data.model.jw.CourseUnit
import com.yourname.ahu_plus.data.model.jw.parseTimeMinutes
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

/**
 * 首页"今日课程"卡片 (2026-06-17 增强版)。
 *
 * 三种状态:
 *  - 上课中: 显示进度条 + "已上 X 分钟 / 还剩 Y 分钟"
 *  - 课前: 倒计时 "下节课还有 X 分钟" / "X 小时 Y 分钟后"
 *  - 课间 / 下课: "下课啦" 或 "下一节还有 X 分钟"
 *  - 今天没课: "今天没课,好好休息"
 *
 * 卡片底部两个 AssistChip:
 *  - 点名 (hasRollCall 高亮)
 *  - 作业 (hasHomework 高亮)
 */
@Composable
fun TodayCourseCard(
    uiState: TodayCourseUiState,
    onOpenSchedule: () -> Unit,
    onRefresh: () -> Unit,
    onRollCall: () -> Unit,
    onAddHomework: () -> Unit,
    todayHasRollCall: Boolean = false,
    todayHasHomework: Boolean = false,
) {
    // 每 30s tick 一次,驱动倒计时/进度条重算
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { delay(30_000); tick++ }
    }
    @Suppress("UNUSED_EXPRESSION") tick

    val now = LocalTime.now()
    val today = LocalDate.now()
    val todayItems = remember(uiState.todayItems, today) {
        uiState.todayItems.filter { it.weekday == today.dayOfWeek.value }
            .sortedBy { it.startUnit }
    }

    // 找当前正在上的课
    val currentCourse = todayItems.firstOrNull { isInClass(it, now, uiState.unitTimes) }
    val nextCourse = todayItems.firstOrNull {
        !isInClass(it, now, uiState.unitTimes) && isFuture(it, now, uiState.unitTimes)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 顶部状态行
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        text = when {
                            currentCourse != null -> "上课中"
                            nextCourse != null -> "下节课"
                            todayItems.isNotEmpty() -> "今日已结束"
                            else -> "今日课程"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            when {
                currentCourse != null -> {
                    val total = courseTotalMinutes(currentCourse, uiState.unitTimes)
                    val start = courseStartMinutes(currentCourse, uiState.unitTimes) ?: 0
                    val end = start + total
                    val nowMin = now.hour * 60 + now.minute
                    val elapsed = (nowMin - start).coerceAtLeast(0)
                    val remaining = (end - nowMin).coerceAtLeast(0)
                    val progress = if (total > 0) (elapsed.toFloat() / total).coerceIn(0f, 1f) else 0f

                    CourseSummary(currentCourse, uiState.unitTimes)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                    )
                    Text(
                        text = "已上 ${elapsed} 分钟 · 还剩 ${remaining} 分钟",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.86f),
                    )
                }
                nextCourse != null -> {
                    val startMin = courseStartMinutes(nextCourse, uiState.unitTimes) ?: 0
                    val nowMin = now.hour * 60 + now.minute
                    val diff = (startMin - nowMin).coerceAtLeast(0)
                    val (label, color) = when {
                        diff <= 0 -> "马上开始" to MaterialTheme.colorScheme.error
                        diff < 60 -> "${diff} 分钟后开始" to MaterialTheme.colorScheme.error
                        diff < 180 -> "${diff / 60} 小时 ${diff % 60} 分后" to MaterialTheme.colorScheme.tertiary
                        else -> "${diff / 60} 小时后" to MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                    }
                    Text(label, style = MaterialTheme.typography.bodySmall, color = color)
                    CourseSummary(nextCourse, uiState.unitTimes)
                }
                todayItems.isNotEmpty() -> {
                    Text(
                        text = "今天课程已结束,明天继续加油 💪",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                    )
                }
                else -> {
                    Text(
                        text = "今天没课,好好休息 ✨",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                }
            }

            // 底部 chip 行 (仅上课中显示,对应正在上的这节课)
            if (currentCourse != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssistChip(
                        onClick = onRollCall,
                        label = { Text("签到") },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.CheckCircle, contentDescription = null,
                                modifier = Modifier.size(AssistChipDefaults.IconSize),
                                tint = if (todayHasRollCall) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                    AssistChip(
                        onClick = onAddHomework,
                    label = { Text("作业") },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.AssignmentTurnedIn, contentDescription = null,
                            modifier = Modifier.size(AssistChipDefaults.IconSize),
                            tint = if (todayHasHomework) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
            } // end if (currentCourse != null)

            // 底部链接
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onOpenSchedule() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "查看完整课表",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun CourseSummary(course: CourseDisplayItem, unitTimes: List<CourseUnit>) {
    val unitMap = unitTimes.associateBy { it.indexNo }
    val start = course.startTime?.takeIf { it.isNotBlank() } ?: unitMap[course.startUnit]?.startTimeStr()
    val end = course.endTime?.takeIf { it.isNotBlank() } ?: unitMap[course.endUnit]?.endTimeStr()
    val timeText = if (!start.isNullOrBlank() && !end.isNullOrBlank()) "$start - $end"
    else "第 ${course.startUnit}-${course.endUnit} 节"

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = course.courseName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.SemiBold,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.AccessTime, contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = " $timeText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
            )
        }
        if (!course.room.isNullOrBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.LocationOn, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = " ${course.room}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                )
            }
        }
        if (course.teacherNames.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Person, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = " ${course.teacherNames}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                )
            }
        }
    }
}

/** 今日课程 UI 状态 (从 ScheduleUiState 派生) */
data class TodayCourseUiState(
    val todayItems: List<CourseDisplayItem>,
    val unitTimes: List<CourseUnit>,
)

private fun isInClass(course: CourseDisplayItem, now: LocalTime, unitTimes: List<CourseUnit>): Boolean {
    val start = courseStartMinutes(course, unitTimes) ?: return false
    val total = courseTotalMinutes(course, unitTimes)
    val nowMin = now.hour * 60 + now.minute
    return nowMin in start..(start + total)
}

private fun isFuture(course: CourseDisplayItem, now: LocalTime, unitTimes: List<CourseUnit>): Boolean {
    val start = courseStartMinutes(course, unitTimes) ?: return false
    val nowMin = now.hour * 60 + now.minute
    return start > nowMin
}

private fun courseStartMinutes(course: CourseDisplayItem, unitTimes: List<CourseUnit>): Int? {
    val text = course.startTime?.takeIf { it.isNotBlank() }
        ?: unitTimes.firstOrNull { it.indexNo == course.startUnit }?.startTimeStr()
    return parseTimeMinutes(text)
}

private fun courseTotalMinutes(course: CourseDisplayItem, unitTimes: List<CourseUnit>): Int {
    val unitMap = unitTimes.associateBy { it.indexNo }
    val start = courseStartMinutes(course, unitTimes) ?: return 0
    val end = course.endTime?.takeIf { it.isNotBlank() } ?: unitMap[course.endUnit]?.endTimeStr()
    val endMin = parseTimeMinutes(end) ?: return 0
    return (endMin - start).coerceAtLeast(0)
}
