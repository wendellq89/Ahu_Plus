package com.yourname.ahu_plus.ui.screen.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel,
    onBack: () -> Unit,
    onNeedsLogin: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.needsLogin) {
        if (uiState.needsLogin) onNeedsLogin()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 缓存极值,避免每帧 4 次 O(n) 遍历
        val minWeek = remember(uiState.weekIndices) { uiState.weekIndices.minOrNull() ?: 1 }
        val maxWeek = remember(uiState.weekIndices) { uiState.weekIndices.maxOrNull() ?: 20 }

        // ── 顶栏：返回 + 周导航 + 刷新（合并为一行）─────
        WeekHeader(
            selectedWeek = uiState.selectedWeek,
            currentWeek = uiState.currentWeek,
            studentName = uiState.studentName,
            minWeek = minWeek,
            maxWeek = maxWeek,
            hasPrevious = uiState.selectedWeek > minWeek,
            hasNext = uiState.selectedWeek < maxWeek,
            onBack = onBack,
            onPrevious = { viewModel.onPreviousWeek() },
            onNext = { viewModel.onNextWeek() },
            onRefresh = { viewModel.onRefresh() }
        )

        // ── 内容区 ────────────────────────────────────
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading && uiState.allActivities.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "加载课表中...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                uiState.error != null && uiState.allActivities.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = uiState.error!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.onRefresh() }) {
                            Text("重试")
                        }
                    }
                }

                uiState.allActivities.isEmpty() && !uiState.isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "暂无课表数据",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.onRefresh() }) {
                            Text("刷新")
                        }
                    }
                }

                else -> {
                    WeekGrid(
                        displayItems = uiState.displayItems,
                        unitTimes = uiState.unitTimes,
                        selectedWeek = uiState.selectedWeek,
                        currentWeek = uiState.currentWeek,
                        onCourseClick = viewModel::onCourseClicked,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    // ── 课程详情 BottomSheet ────────────────────────
    uiState.selectedCourseDetail?.let { detail ->
        CourseDetailSheet(
            detail = detail,
            onNoteChange = viewModel::onNoteDraftChanged,
            onSave = viewModel::onNoteSave,
            onDismiss = viewModel::onDismissSheet,
        )
    }
}

@Composable
private fun WeekHeader(
    selectedWeek: Int,
    currentWeek: Int,
    studentName: String?,
    minWeek: Int,
    maxWeek: Int,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 返回键
        IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // 周切换
        IconButton(
            onClick = onPrevious,
            enabled = hasPrevious,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Filled.ChevronLeft,
                contentDescription = "上一周",
                modifier = Modifier.size(20.dp)
            )
        }

        Text(
            text = "第 $selectedWeek 周",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )

        if (selectedWeek == currentWeek) {
            Text(
                text = "（本周）",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 2.dp)
            )
        }

        IconButton(
            onClick = onNext,
            enabled = hasNext,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "下一周",
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // 刷新
        IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = "刷新",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
