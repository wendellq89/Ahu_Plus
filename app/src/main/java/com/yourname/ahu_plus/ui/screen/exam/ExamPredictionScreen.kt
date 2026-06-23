package com.yourname.ahu_plus.ui.screen.exam

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourname.ahu_plus.data.model.exam.AggregatedCourse
import com.yourname.ahu_plus.data.model.exam.MATCH_TYPE_TEACHER
import com.yourname.ahu_plus.data.model.exam.ExamPrediction
import com.yourname.ahu_plus.ui.components.AhuShapes
import com.yourname.ahu_plus.ui.components.AhuTopAppBar
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * 排考预测页 — Gitee 数据源 + 聚合卡片 (2026-06-23 重构)
 *
 * 顶部展示「数据更新于 X 分钟前」+ 手动刷新按钮;
 * 主体按用户课程聚合,每张卡显示该课的所有考试场次,点击展开。
 * 场次按"老师匹配优先"排序,匹配上的显示"你的老师"徽章并置顶。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamPredictionScreen(
    viewModel: ExamPredictionViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 聚合卡的展开状态: courseCode → expanded
    val expandedMap = remember { mutableStateMapOf<String, Boolean>() }

    Scaffold(
        topBar = {
            AhuTopAppBar(
                title = { Text("排考预测") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            DataSourceCard(
                generatedAt = uiState.generatedAt,
                lastFetchedAt = uiState.lastFetchedAt,
                isLoading = uiState.isLoading,
                isFromCache = uiState.isFromCache,
                aggregatedCount = uiState.aggregated.size,
                sessionCount = uiState.aggregated.sumOf { it.sessionCount }
            )
            Spacer(modifier = Modifier.height(12.dp))

            when {
                uiState.isLoading && uiState.aggregated.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "正在从 Gitee 拉取最新考试数据…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                uiState.error != null && uiState.aggregated.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                Icons.Filled.CloudOff, null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(40.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "暂无考试预测数据",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                uiState.error ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = viewModel::onRefresh,
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Icon(Icons.Filled.Refresh, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("重新拉取")
                            }
                        }
                    }
                }

                uiState.aggregated.isEmpty() && uiState.generatedAt != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                Icons.Filled.School, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "暂未匹配到你的考试",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "数据中暂无与你课表课程代码一致的考试。\n" +
                                    "如有疑问,请确认课表已刷新。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                else -> {
                    AggregatedList(
                        items = uiState.aggregated,
                        expandedMap = expandedMap
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 数据源信息卡
// ═══════════════════════════════════════════════════════════

@Composable
private fun DataSourceCard(
    generatedAt: String?,
    lastFetchedAt: Long?,
    isLoading: Boolean,
    isFromCache: Boolean,
    aggregatedCount: Int,
    sessionCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isLoading) Icons.Filled.Update
                    else if (isFromCache) Icons.Filled.CloudOff
                    else Icons.Filled.CloudDone,
                    null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = when {
                        isLoading -> "正在拉取最新数据…"
                        isFromCache -> "展示本地缓存(网络拉取失败)"
                        else -> "数据为最新"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(4.dp))

            if (generatedAt != null) {
                Text(
                    text = "数据生成时间: ${formatGeneratedAt(generatedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "尚未获取到考试预测数据",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (aggregatedCount > 0) {
                Text(
                    text = "匹配到 $aggregatedCount 门课程,共 $sessionCount 场考试",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val lastFetchedLabel = lastFetchedAt?.let { formatRelativeTime(it) }
            if (lastFetchedLabel != null) {
                Text(
                    text = "本机更新: $lastFetchedLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 聚合列表
// ═══════════════════════════════════════════════════════════

@Composable
private fun AggregatedList(
    items: List<AggregatedCourse>,
    expandedMap: MutableMap<String, Boolean>
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.courseCode }) { agg ->
            val isExpanded = expandedMap[agg.courseCode] == true
            AggregatedCard(
                aggregated = agg,
                isExpanded = isExpanded,
                onToggle = { expandedMap[agg.courseCode] = !isExpanded }
            )
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

// ═══════════════════════════════════════════════════════════
// 聚合卡 (可展开)
// ═══════════════════════════════════════════════════════════

@Composable
private fun AggregatedCard(
    aggregated: AggregatedCourse,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // ── 头部: 课程名 + 场次数 + 展开图标 ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = aggregated.courseName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (aggregated.hasTeacherMatch) {
                            Spacer(Modifier.width(6.dp))
                            TeacherMatchBadge()
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${aggregated.courseCode} · ${aggregated.sessionCount} 场考试" +
                            (aggregated.earliestDate?.let { " · 最早 $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (isExpanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── 展开后的场次列表 ──
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(8.dp))
                    if (aggregated.teacherNames.isNotEmpty()) {
                        Text(
                            text = "任课老师: ${aggregated.teacherNames.joinToString("、")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    aggregated.sessions.forEach { session ->
                        SessionCard(session)
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 单场次卡 (展开后显示)
// ═══════════════════════════════════════════════════════════

@Composable
private fun SessionCard(session: ExamPrediction) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (session.matchType == MATCH_TYPE_TEACHER)
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${session.courseCode}.${session.section}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (session.matchType == MATCH_TYPE_TEACHER) {
                    TeacherMatchBadge()
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.AccessTime, null, Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "${session.date} ${session.startTime}~${session.endTime}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.LocationOn, null, Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    buildString {
                        append(session.roomName)
                        if (!session.campus.isNullOrBlank()) {
                            append(" · ")
                            append(session.campus)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (session.teacherName.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.School, null, Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        ExamPredictionViewModel.parseProctorNames(session.teacherName)
                            .joinToString("、")
                            .ifBlank { session.teacherName },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// "你的老师" 徽章
// ═══════════════════════════════════════════════════════════

@Composable
private fun TeacherMatchBadge() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Icon(
                Icons.Filled.Star, null,
                modifier = Modifier.size(11.dp),
            )
            Spacer(Modifier.width(2.dp))
            Text(
                "你的老师",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 时间格式工具
// ═══════════════════════════════════════════════════════════

private val GENERATED_AT_FORMATTERS = listOf(
    DateTimeFormatter.ISO_OFFSET_DATE_TIME,
    DateTimeFormatter.ISO_DATE_TIME,
)

private fun formatGeneratedAt(raw: String): String {
    for (formatter in GENERATED_AT_FORMATTERS) {
        try {
            val dt = OffsetDateTime.parse(raw, formatter)
            return dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        } catch (_: DateTimeParseException) {
            // try next formatter
        }
    }
    return raw
}

private fun formatRelativeTime(epochMs: Long): String {
    val diffMs = System.currentTimeMillis() - epochMs
    val minutes = diffMs / 60_000
    return when {
        diffMs < 0 -> "刚刚"
        minutes < 1 -> "刚刚"
        minutes < 60 -> "${minutes} 分钟前"
        minutes < 60 * 24 -> "${minutes / 60} 小时前"
        else -> "${minutes / (60 * 24)} 天前"
    }
}