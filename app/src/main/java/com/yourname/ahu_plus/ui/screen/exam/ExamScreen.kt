package com.yourname.ahu_plus.ui.screen.exam

import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourname.ahu_plus.data.model.jw.Exam
import com.yourname.ahu_plus.ui.screen.grade.CenteredError
import com.yourname.ahu_plus.ui.screen.grade.CenteredLoader
import com.yourname.ahu_plus.ui.screen.grade.CenteredMessage
import com.yourname.ahu_plus.ui.components.AhuTopAppBar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamScreen(
    viewModel: ExamViewModel,
    onBack: () -> Unit,
    onNeedsLogin: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.needsLogin) {
        if (uiState.needsLogin) onNeedsLogin()
    }

    Scaffold(
        topBar = {
            AhuTopAppBar(
                title = { Text("考试安排") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        when {
            uiState.isLoading && uiState.exams.isEmpty() -> {
                CenteredLoader(modifier = Modifier.padding(innerPadding))
            }
            uiState.error != null && uiState.exams.isEmpty() -> {
                val errMsg = uiState.error
                CenteredError(
                    message = errMsg!!,
                    onRetry = viewModel::onRefresh,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            uiState.exams.isEmpty() -> {
                CenteredMessage(
                    text = "本学期暂无考试安排",
                    modifier = Modifier.padding(innerPadding)
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(uiState.exams, key = { it.id }) { exam ->
                        ExamRow(exam = exam)
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ExamRow(exam: Exam) {
    val context = LocalContext.current
    var showCalendarDialog by remember { mutableStateOf(false) }

    // 确认添加到日历的对话框
    if (showCalendarDialog) {
        AlertDialog(
            onDismissRequest = { showCalendarDialog = false },
            title = { Text("添加到系统日历") },
            text = {
                Text(
                    "是否将「${exam.displayCourse}」的考试安排添加到系统日历？\n\n" +
                        "时间：${exam.displayTime}\n" +
                        "地点：${exam.displayLocation}" +
                        if (!exam.seatNumber.isNullOrBlank()) "\n座位号：${exam.seatNumber}" else ""
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showCalendarDialog = false
                    try {
                        val intent = buildCalendarIntent(exam)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "无法打开日历: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("添加")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCalendarDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // 左侧类型色块
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = exam.displayType,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = examTypeColor(exam.examType)
                )
                if (exam.campus?.isNotBlank() == true) {
                    Text(
                        text = exam.campus,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exam.displayCourse,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                InfoLine(icon = Icons.Filled.AccessTime, text = exam.displayTime)
                Spacer(modifier = Modifier.height(4.dp))
                InfoLine(icon = Icons.Filled.LocationOn, text = exam.displayLocation)
                if (!exam.seatNumber.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    InfoLine(icon = Icons.Filled.EventNote, text = "座位号 ${exam.seatNumber}")
                }
            }

            // 添加到日历按钮
            IconButton(
                onClick = { showCalendarDialog = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Filled.CalendarMonth,
                    contentDescription = "添加到日历",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 解析考试时间字符串，构建系统日历 Intent。
 *
 * 考试时间格式："2026-05-24 14:00~15:40"
 * 若解析失败，回退为全天事件。
 */
private fun buildCalendarIntent(exam: Exam): Intent {
    val timePattern = Regex("""(\d{4}-\d{2}-\d{2})\s+(\d{1,2}:\d{2})~(\d{1,2}:\d{2})""")
    val match = timePattern.find(exam.examTime)

    val beginTime = if (match != null) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        sdf.parse("${match.groupValues[1]} ${match.groupValues[2]}")?.time ?: 0L
    } else {
        0L
    }
    val endTime = if (match != null) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        sdf.parse("${match.groupValues[1]} ${match.groupValues[3]}")?.time ?: (beginTime + 2 * 60 * 60 * 1000)
    } else {
        beginTime + 2 * 60 * 60 * 1000
    }

    val location = exam.displayLocation
    val description = buildString {
        append("课程：${exam.displayCourse}")
        if (!exam.seatNumber.isNullOrBlank()) append("\n座位号：${exam.seatNumber}")
        if (exam.campus?.isNotBlank() == true) append("\n校区：${exam.campus}")
    }

    return Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.Events.TITLE, exam.displayCourse)
        putExtra(CalendarContract.Events.DESCRIPTION, description)
        putExtra(CalendarContract.Events.EVENT_LOCATION, location)
        if (beginTime > 0) {
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginTime)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime)
        } else {
            // 解析失败时设为全天事件（使用当前日期）
            val cal = Calendar.getInstance()
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, cal.timeInMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, cal.timeInMillis + 2 * 60 * 60 * 1000)
        }
    }
}

@Composable
private fun InfoLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun examTypeColor(type: String?): Color = when {
    type == null -> MaterialTheme.colorScheme.onSurfaceVariant
    type.contains("补") -> Color(0xFFE53935)
    type.contains("缓") -> Color(0xFFFB8C00)
    else -> MaterialTheme.colorScheme.primary
}
