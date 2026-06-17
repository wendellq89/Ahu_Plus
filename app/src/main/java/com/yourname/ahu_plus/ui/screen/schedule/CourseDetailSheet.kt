package com.yourname.ahu_plus.ui.screen.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourname.ahu_plus.data.model.jw.CourseDisplayItem
import com.yourname.ahu_plus.data.model.jw.GetDataLesson
import com.yourname.ahu_plus.data.repository.AssessmentRepository
import com.yourname.ahu_plus.ui.screen.schedule.components.CollapsibleSection
import com.yourname.ahu_plus.ui.screen.schedule.sections.AssessmentSection
import com.yourname.ahu_plus.ui.screen.schedule.sections.CourseNoteSection
import com.yourname.ahu_plus.ui.screen.schedule.sections.RecordHistorySection
import com.yourname.ahu_plus.ui.screen.schedule.sections.SlotNoteSection

/**
 * 课程详情底部弹窗 (2026-06-17 重构)。
 *
 * 包含 5 个可折叠 section:
 *  1. 课程详情 — 教务静态信息
 *  2. 考核方案 — 用户自填文字 + 图片 (Step 4 接入)
 *  3. 课程备注 — 跨节次共享,按 courseCode
 *  4. 此节课备注 — 按 lessonId+week
 *  5. 记录一览 — 该门课所有点名/签到/作业记录
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailSheet(
    viewModel: ScheduleViewModel,
    assessmentRepository: AssessmentRepository,
    onDismiss: () -> Unit,
) {
    val detail = viewModel.uiState.collectAsStateWithLifecycle().value.selectedCourseDetail ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val courseNote by viewModel.courseNote.collectAsStateWithLifecycle()
    val slotNote by viewModel.slotNote.collectAsStateWithLifecycle()
    val records by viewModel.courseRecords.collectAsStateWithLifecycle()
    val assessmentPlan by viewModel.assessmentPlan.collectAsStateWithLifecycle()
    val hasSignedIn by viewModel.hasSignedIn.collectAsStateWithLifecycle()

    val item = detail.item
    val lessonDetail = detail.lessonDetail
    val courseCode = item.courseCode.orEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── 标题 ─────────────────────────────────
            Text(
                text = item.courseName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))

            // ── 1. 课程详情 (默认全部收起) ───────
            CollapsibleSection(title = "课程详情", defaultExpanded = false) {
                DetailRow("课程代码", item.courseCode)
                DetailRow("学分", (lessonDetail?.course?.credits ?: item.credits)?.toString())
                DetailRow("教师", item.teacherNames)
                DetailRow("教室", item.room)
                DetailRow("校区", lessonDetail?.campus?.nameZh ?: item.campus)
                DetailRow("课程类型", lessonDetail?.courseType?.nameZh ?: item.courseType)
                DetailRow("考核方式", lessonDetail?.examMode?.nameZh)
                DetailRow("修读类别", lessonDetail?.compulsorysStr)
                DetailRow("周次", item.weeksStr ?: item.weekIndexes.toString())
                DetailRow("排课周次", lessonDetail?.scheduleWeeksInfo)
                DetailRow(
                    "上课时间",
                    buildString {
                        item.startTime?.let { append(it) }
                        item.endTime?.let {
                            if (isNotEmpty()) append(" - ")
                            append(it)
                        }
                    }.ifBlank { null }
                )
                DetailRow(
                    "排课描述",
                    lessonDetail?.scheduleText?.dateTimePlaceText?.textZh
                        ?: lessonDetail?.scheduleText?.dateTimePlacePersonText?.textZh
                )
                if (lessonDetail?.stdCount != null || lessonDetail?.limitCount != null) {
                    DetailRow(
                        "已选/限选",
                        "${lessonDetail?.stdCount ?: "-"} / ${lessonDetail?.limitCount ?: "-"}"
                    )
                }
            }

            // ── 2. 考核方案 (Step 4 已接入) ──────────
            AssessmentSection(
                plan = assessmentPlan,
                lessonId = item.lessonId.toString(),
                assessmentRepository = assessmentRepository,
                onSave = { text, paths ->
                    viewModel.saveAssessment(
                        com.yourname.ahu_plus.data.model.course.AssessmentPlan(
                            lessonId = item.lessonId.toString(),
                            text = text,
                            imagePaths = paths,
                        )
                    )
                },
            )

            // ── 3. 课程备注 (跨节次共享) ──────────────
            if (courseCode.isNotBlank()) {
                CourseNoteSection(
                    savedNote = courseNote,
                    onSave = { viewModel.saveCourseNote(it) },
                )
            }

            // ── 4. 此节课备注 ─────────────────────────
            SlotNoteSection(
                savedNote = slotNote,
                courseName = item.courseName,
                hasSignedIn = hasSignedIn,
                onSaveNote = { viewModel.saveSlotNote(it) },
                onAddSignIn = { viewModel.addSignIn() },
                onAddHomework = { text, deadline -> viewModel.addHomework(text, deadline) },
            )

            // ── 5. 记录一览 ──────────────────────────
            RecordHistorySection(
                records = records,
                onToggleCompleted = { viewModel.toggleRecordCompleted(it) },
                onDelete = { viewModel.deleteRecord(it) },
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 单行键值对:左边标签灰色,右边取值右对齐;值为空不渲染整行 */
@Composable
private fun DetailRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
        )
    }
}
