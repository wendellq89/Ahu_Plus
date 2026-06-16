package com.yourname.ahu_plus.ui.screen.grade

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourname.ahu_plus.data.model.jw.GpaMetadata
import com.yourname.ahu_plus.data.model.jw.Grade
import com.yourname.ahu_plus.data.model.jw.SemesterGpaEntry
import com.yourname.ahu_plus.ui.components.AhuTopAppBar

// ── 语义色 ──
private val Score90 = Color(0xFFE53935)
private val Score80 = Color(0xFFFB8C00)
private val Score70 = Color(0xFF43A047)
private val Score60 = Color(0xFF1E88E5)
private val ScoreNa = Color(0xFF8A8A8A)
private val BarColor = Color(0xFF2F80ED)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradeScreen(
    viewModel: GradeViewModel,
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
                title = {
                    Column {
                        Text("成绩查询")
                        uiState.semesterName?.takeIf { it.isNotBlank() }?.let { sem ->
                            Text(
                                text = sem,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
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
        // ── 成绩明细 BottomSheet ──
        uiState.selectedGrade?.let { grade ->
            GradeDetailSheet(
                grade = grade,
                onDismiss = viewModel::onDismissGradeDetail
            )
        }

        val allGradesEmpty = uiState.gradesBySemester.isEmpty()
        when {
            uiState.isLoading && allGradesEmpty -> {
                CenteredLoader(modifier = Modifier.padding(innerPadding))
            }
            uiState.error != null && allGradesEmpty -> {
                val errMsg = uiState.error
                CenteredError(
                    message = errMsg!!,
                    onRetry = viewModel::onRefresh,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            allGradesEmpty -> {
                CenteredMessage(text = "暂无成绩记录", modifier = Modifier.padding(innerPadding))
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    // ── GPA 汇总卡 ──
                    uiState.gpaMetadata?.let { gpa ->
                        item {
                            GpaSummaryCard(
                                gpa = gpa,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        // 学期 GPA 柱状图
                        if (gpa.perSemesterGpa.isNotEmpty()) {
                            item {
                                GpaBarChart(
                                    entries = gpa.perSemesterGpa,
                                    selectedSemesterId = uiState.selectedSemesterId,
                                    onSelect = viewModel::selectSemester,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }

                    // ── 学期选择器 ──
                    item {
                        SemesterChips(
                            availableIds = uiState.availableSemesterIds,
                            selectedId = uiState.selectedSemesterId,
                            semesterName = { id ->
                                uiState.gradesBySemester[id.toString()]
                                    ?.firstOrNull()?.semesterName ?: "学期 $id"
                            },
                            onSelect = viewModel::selectSemester
                        )
                    }

                    // ── 本学期汇总 ──
                    item {
                        GradeSummaryCard(
                            grades = uiState.currentGrades,
                            semesterName = uiState.semesterName
                        )
                    }

                    // ── 成绩列表 ──
                    val current = uiState.currentGrades
                    if (current.isEmpty()) {
                        item {
                            CenteredMessage(text = "本学期暂无成绩")
                        }
                    } else {
                        items(current, key = { it.id ?: it.courseCode.hashCode().toLong() }) { g ->
                            GradeRow(
                                grade = g,
                                onClick = { viewModel.onGradeClicked(g) }
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }
}


// ═══════════════════════ GPA Summary Card ═══════════════════════

@Composable
private fun GpaSummaryCard(gpa: GpaMetadata, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Grade,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "全部学期汇总",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                GpaStat(label = "总均绩点", value = gpa.gpa?.let { "%.2f".format(it) } ?: "—")
                GpaStat(label = "总学分", value = gpa.totalCredits?.let { "%.0f".format(it) } ?: "—")
                if (gpa.majorRank != null && gpa.majorHeadCount != null) {
                    GpaStat(
                        label = "专业排名",
                        value = "${gpa.majorRank}/${gpa.majorHeadCount}"
                    )
                }
                GpaStat(
                    label = "计划内",
                    value = gpa.inPlanCredits?.let { "%.0f".format(it) } ?: "—"
                )
            }
        }
    }
}

@Composable
private fun GpaStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
    }
}


// ═══════════════════════ GPA Bar Chart ═══════════════════════

@Composable
private fun GpaBarChart(
    entries: List<SemesterGpaEntry>,
    selectedSemesterId: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val gpaValues = entries.mapNotNull { it.gpa }
    if (gpaValues.isEmpty()) return
    val maxGpa = (gpaValues.maxOrNull() ?: 4.0).coerceAtLeast(4.0)
    val barHeight = 80.dp

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "学期绩点趋势",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                entries.forEach { entry ->
                    val selected = entry.semesterId == selectedSemesterId
                    SemesterBar(
                        entry = entry,
                        maxGpa = maxGpa,
                        barHeight = barHeight,
                        selected = selected,
                        onClick = { onSelect(entry.semesterId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SemesterBar(
    entry: SemesterGpaEntry,
    maxGpa: Double,
    barHeight: androidx.compose.ui.unit.Dp,
    selected: Boolean,
    onClick: () -> Unit
) {
    val gpa = entry.gpa
    val fraction = ((gpa ?: 0.0) / maxGpa).toFloat().coerceIn(0f, 1f)
    val barColor = if (selected) MaterialTheme.colorScheme.primary else BarColor.copy(alpha = 0.55f)
    val bgColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = gpa?.let { "%.2f".format(it) } ?: "—",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = barColor
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(barHeight),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight * fraction)
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .background(barColor)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        // Semester short name
        Text(
            text = semesterShortName(entry.semesterId),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

private fun semesterShortName(id: Int): String = when (id) {
    112 -> "25-26春"
    92 -> "25-26秋"
    72 -> "24-25春"
    52 -> "24-25秋"
    50 -> "23-24春"
    49 -> "23-24秋"
    else -> id.toString()
}


// ═══════════════════════ Grade Detail Sheet ═══════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GradeDetailSheet(grade: Grade, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // 课程名 + 分数
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = grade.displayCourse,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!grade.courseNameEn.isNullOrBlank() && grade.courseNameEn != grade.courseName) {
                        Text(
                            text = grade.courseNameEn,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = grade.displayScore,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = scoreColor(grade.scoreAsDouble())
                    )
                    if (grade.gp != null) {
                        Text(
                            text = "绩点 %.2f".format(grade.gp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(14.dp))

            // 基本信息
            DetailLine("课程代码", grade.courseCode)
            DetailLine("学分", grade.displayCredits)
            DetailLine("课程类型", grade.courseType)
            DetailLine("课程性质", grade.courseProperty)
            DetailLine("课程类别", grade.courseTaxon)
            DetailLine("学期", grade.semesterName)
            if (grade.published != null) {
                DetailLine(
                    "成绩状态",
                    if (grade.published) "已发布" else "未发布（暂存）"
                )
            }

            // 成绩明细
            val detailText = grade.detailPlainText()
            if (!detailText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "成绩明细",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = detailText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 成绩原始 HTML（未发布状态也显示）
            val rawDetail = grade.gradeDetail?.takeIf { it.isNotBlank() }
            if (!rawDetail.isNullOrBlank() && rawDetail != detailText) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "原始明细",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = rawDetail.replace(Regex("<[^>]+>"), "").trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}


// ═══════════════════════ Semester Chips ═══════════════════════

@Composable
private fun SemesterChips(
    availableIds: List<Int>,
    selectedId: Int?,
    semesterName: (Int) -> String,
    onSelect: (Int) -> Unit
) {
    if (availableIds.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        availableIds.forEach { id ->
            val selected = id == selectedId
            FilterChip(
                selected = selected,
                onClick = { onSelect(id) },
                label = { Text(semesterName(id)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}


// ═══════════════════════ Grade Summary Card ═══════════════════════

@Composable
private fun GradeSummaryCard(grades: List<Grade>, semesterName: String?) {
    val validScores = grades.mapNotNull { it.scoreAsDouble() }
    val validGpa = grades.mapNotNull { it.gp }
    val avgScore = if (validScores.isEmpty()) null else validScores.average()
    val totalCredits = grades.mapNotNull { it.credits }.sum()
    val weightedGpa = grades
        .mapNotNull { g -> g.gp?.let { it to (g.credits ?: 0.0) } }
        .takeIf { it.isNotEmpty() }
        ?.let { pairs ->
            val sumProduct = pairs.sumOf { it.first * it.second }
            val sumCredits = pairs.sumOf { it.second }
            if (sumCredits > 0) sumProduct / sumCredits else null
        }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Grade,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = semesterName?.let { "$it 汇总" } ?: "本学期汇总",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Stat(label = "课程数", value = "${grades.size}")
                Stat(label = "总学分", value = "%.1f".format(totalCredits))
                Stat(label = "均分", value = avgScore?.let { "%.1f".format(it) } ?: "—")
                Stat(label = "加权均绩", value = weightedGpa?.let { "%.2f".format(it) } ?: "—")
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
    }
}


// ═══════════════════════ Grade Row ═══════════════════════

@Composable
private fun GradeRow(grade: Grade, onClick: () -> Unit) {
    val scoreColor = scoreColor(grade.scoreAsDouble())
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = grade.displayCourse,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetaPill(text = "${grade.displayCredits} 学分")
                    if (!grade.courseType.isNullOrBlank()) {
                        MetaPill(text = grade.courseType)
                    }
                    if (!grade.courseProperty.isNullOrBlank() && grade.courseProperty != "必修") {
                        MetaPill(text = grade.courseProperty)
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = grade.displayScore,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = scoreColor
                )
                Text(
                    text = "绩点 ${grade.displayGradePoint}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MetaPill(text: String) {
    Box(modifier = Modifier.padding(end = 4.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


// ═══════════════════════ Utilities ═══════════════════════

private fun scoreColor(score: Double?): Color = when {
    score == null -> ScoreNa
    score >= 90 -> Score90
    score >= 80 -> Score80
    score >= 70 -> Score70
    score >= 60 -> Score60
    else -> ScoreNa
}

@Composable
internal fun CenteredLoader(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
internal fun CenteredError(
    message: String, onRetry: () -> Unit, modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(text = message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            FilledTonalButton(onClick = onRetry) { Text("重新加载") }
        }
    }
}

@Composable
internal fun CenteredMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
