package com.yourname.ahu_plus.data.model.jw

import com.google.gson.annotations.SerializedName

/**
 * print-data API 响应体。
 * 对应 POST/GET https://jw.ahu.edu.cn/student/for-std/course-table/semester/{id}/print-data
 */
data class PrintDataResponse(
    @SerializedName("studentTableVms") val studentTableVms: List<StudentTable>?,
    @SerializedName("timeTableLayout") val timeTableLayout: TimeTableLayout?,
    @SerializedName("arrangedLessonSearchVms") val arrangedLessonSearchVms: List<ArrangedLesson>?,
    @SerializedName("lessonSearchVms") val lessonSearchVms: List<Any>?
)

data class StudentTable(
    val id: Int?,
    val name: String?,
    val code: String?,
    val grade: String?,
    val department: String?,
    val major: String?,
    @SerializedName("adminclass") val adminclass: String?,
    val credits: Double?,
    val activities: List<CourseActivity>?
)

data class CourseActivity(
    @SerializedName("lessonId") val lessonId: Long?,
    @SerializedName("lessonCode") val lessonCode: String?,
    @SerializedName("lessonName") val lessonName: String?,
    @SerializedName("courseCode") val courseCode: String?,
    @SerializedName("courseName") val courseName: String?,
    @SerializedName("weeksStr") val weeksStr: String?,
    @SerializedName("weekIndexes") val weekIndexes: List<Int>?,
    val room: String?,
    val building: String?,
    val campus: String?,
    val weekday: Int?,
    @SerializedName("startUnit") val startUnit: Int?,
    @SerializedName("endUnit") val endUnit: Int?,
    val teachers: List<String>?,
    @SerializedName("teacherNames") val teacherNames: List<String>?,
    @SerializedName("courseType") val courseType: CourseTypeInfo?,
    val credits: Double?,
    @SerializedName("startTime") val startTime: String?,
    @SerializedName("endTime") val endTime: String?,
    @SerializedName("periodInfo") val periodInfo: PeriodInfo?,
    @SerializedName("stdCount") val stdCount: Int?,
    @SerializedName("limitCount") val limitCount: Int?
)

data class CourseTypeInfo(
    @SerializedName("nameZh") val nameZh: String?,
    @SerializedName("nameEn") val nameEn: String?,
    val id: Int?,
    val code: String?,
    val name: String?
)

data class PeriodInfo(
    val total: Int?,
    val weeks: Double?,
    val theory: Int?,
    @SerializedName("theoryUnit") val theoryUnit: String?,
    val practice: Int?,
    val experiment: Int?,
    @SerializedName("periodsPerWeek") val periodsPerWeek: Int?
)

/**
 * 时间布局 —— 节次定义（第1节 08:00-08:45 等）
 */
data class TimeTableLayout(
    val id: Int?,
    @SerializedName("nameZh") val nameZh: String?,
    @SerializedName("courseUnitList") val courseUnitList: List<CourseUnit>?,
    @SerializedName("minIndexNo") val minIndexNo: Int?,
    @SerializedName("maxIndexNo") val maxIndexNo: Int?
)

data class CourseUnit(
    @SerializedName("nameZh") val nameZh: String?,
    val indexNo: Int?,
    @SerializedName("startTime") val startTime: Int?,
    @SerializedName("endTime") val endTime: Int?,
    @SerializedName("dayPart") val dayPart: String?,
    val name: String?
) {
    /** 格式化时间，例如 "08:00" */
    fun startTimeStr(): String {
        val t = startTime ?: return ""
        val h = t / 100
        val m = t % 100
        return "%02d:%02d".format(h, m)
    }

    fun endTimeStr(): String {
        val t = endTime ?: return ""
        val h = t / 100
        val m = t % 100
        return "%02d:%02d".format(h, m)
    }
}

/**
 * 统一展示模型 —— 供 WeekGrid UI 使用
 */
/**
 * 用户自行添加的课表条目（加课 / 加安排两种模式）。
 *
 * [type] "course" = 加课（如自习课），"arrangement" = 加安排（如临时班会）
 * [id] 为 UUID 字符串以区分于教务系统的 Long 型 lessonId
 */
data class UserScheduleItem(
    val id: String,
    val name: String,
    val type: String,
    val weekday: Int,
    val startUnit: Int,
    val endUnit: Int,
    val weeks: List<Int>,
    val room: String,
    val teacher: String,
    val note: String,
    val createdAt: Long = System.currentTimeMillis(),
) {
    /** 转换为 [CourseDisplayItem] 以便在 WeekGrid 中统一渲染。 */
    fun toDisplayItem(): CourseDisplayItem = CourseDisplayItem(
        lessonId = -(id.hashCode().toLong()), // 负数区分自定义条目
        courseName = name,
        courseCode = null,
        teacherNames = teacher,
        room = room.ifBlank { note.ifBlank { when (type) { "arrangement" -> "安排" else -> "自定义" } } },
        weekday = weekday,
        startUnit = startUnit,
        endUnit = endUnit,
        weekIndexes = weeks,
        weeksStr = weeks.sorted().joinToString(", "),
        startTime = null,
        endTime = null,
        courseType = when (type) { "course" -> "自定义课程" else -> "临时安排" },
        credits = null,
        campus = null,
        colorIndex = Math.abs(name.hashCode()) % 10,
        lessonDetail = null,
    )

    companion object {
        val TYPE_COURSE = "course"
        val TYPE_ARRANGEMENT = "arrangement"
    }
}

data class CourseDisplayItem(
    val lessonId: Long,
    val courseName: String,
    val courseCode: String?,
    val teacherNames: String,
    val room: String?,
    val weekday: Int,
    val startUnit: Int,
    val endUnit: Int,
    val weekIndexes: List<Int>,
    val weeksStr: String?,
    val startTime: String?,
    val endTime: String?,
    val courseType: String?,
    val credits: Double?,
    val campus: String?,
    /** 用于着色 —— 由 courseName.hashCode() 映射到颜色调色板索引 */
    val colorIndex: Int,
    /**
     * 教务处增强数据 (考核方式 / 排课文本 / 修读类别 等)。
     * 仅在课程详情弹窗使用,不参与网格渲染。
     * 默认 null 以兼容旧调用方。
     */
    val lessonDetail: GetDataLesson? = null,
)

// ── get-data 端点（arrangedLessonSearchVms）的轻量模型 ──────

data class ArrangedLesson(
    val id: Long?,
    @SerializedName("course") val course: ArrangedCourse?,
    @SerializedName("teacherAssignmentList") val teacherAssignmentList: List<TeacherAssignment>?,
    @SerializedName("scheduleText") val scheduleText: ScheduleText?,
    @SerializedName("suggestScheduleWeeks") val suggestScheduleWeeks: List<Int>?,
    @SerializedName("stdCount") val stdCount: Int?,
    @SerializedName("limitCount") val limitCount: Int?
)

data class ArrangedCourse(
    @SerializedName("nameZh") val nameZh: String?,
    val code: String?,
    val credits: Double?
)

data class TeacherAssignment(
    @SerializedName("teacher") val teacher: TeacherInfo?,
    @SerializedName("person") val person: PersonInfo?
) {
    fun teacherName(): String = person?.nameZh ?: teacher?.person?.nameZh ?: ""
}

data class TeacherInfo(
    val person: PersonInfo?
)

data class PersonInfo(
    @SerializedName("nameZh") val nameZh: String?
)

data class ScheduleText(
    @SerializedName("dateTimePlacePersonText") val dateTimePlacePersonText: StringText?,
    @SerializedName("dateTimePlaceText") val dateTimePlaceText: StringText?,
    @SerializedName("dateTimeText") val dateTimeText: StringText?
)

data class StringText(
    @SerializedName("textZh") val textZh: String?,
    val text: String?
)

// ── get-data 端点完整响应 ────────────────────────────────

data class GetDataResponse(
    val lessons: List<GetDataLesson>?,
    val lessonIds: List<Long>?,
    @SerializedName("semester") val semester: SemesterInfo?,
    @SerializedName("currentWeek") val currentWeek: Int?,
    @SerializedName("weekIndices") val weekIndices: List<Int>?,
    @SerializedName("timeTableLayoutId") val timeTableLayoutId: Int?
)

data class GetDataLesson(
    val id: Long?,
    @SerializedName("nameZh") val nameZh: String?,
    val code: String?,
    @SerializedName("course") val course: ArrangedCourse?,
    @SerializedName("scheduleText") val scheduleText: ScheduleText?,
    @SerializedName("teacherAssignmentList") val teacherAssignmentList: List<TeacherAssignment>?,
    @SerializedName("suggestScheduleWeeks") val suggestScheduleWeeks: List<Int>?,
    @SerializedName("courseType") val courseType: CourseTypeInfo?,
    val credits: Double?,
    @SerializedName("campus") val campus: CampusInfo?,
    @SerializedName("scheduleWeeksInfo") val scheduleWeeksInfo: String?,
    @SerializedName("compulsorysStr") val compulsorysStr: String?,
    @SerializedName("examMode") val examMode: ExamModeInfo?,
    @SerializedName("stdCount") val stdCount: Int?,
    @SerializedName("limitCount") val limitCount: Int?
)

data class SemesterInfo(
    val id: Int?,
    @SerializedName("nameZh") val nameZh: String?,
    val code: String?,
    @SerializedName("schoolYear") val schoolYear: String?,
    @SerializedName("startDate") val startDate: String?,
    @SerializedName("endDate") val endDate: String?,
    val season: String?
)

data class CampusInfo(
    val id: Int?,
    @SerializedName("nameZh") val nameZh: String?
)

data class ExamModeInfo(
    val id: Int?,
    @SerializedName("nameZh") val nameZh: String?,
    val code: String?,
    val name: String?
)

/**
 * 课程表统一数据，由 CourseRepository 合并返回
 */
data class ScheduleData(
    val studentName: String?,
    val className: String?,
    val department: String?,
    val credits: Double?,
    val activities: List<CourseActivity>,
    val unitTimes: List<CourseUnit>,
    val semester: SemesterInfo?,
    val currentWeek: Int,
    val weekIndices: List<Int>,
    val lessons: List<GetDataLesson>?
)

/**
 * 将时间字符串（如 "0800", "14:30", "8:00"）解析为从零点开始的分钟数。
 * 返回 null 表示无法解析。
 */
fun parseTimeMinutes(value: String?): Int? {
    if (value.isNullOrBlank()) return null
    val digits = value.filter { it.isDigit() }
    if (digits.length < 3) return null
    val padded = digits.padStart(4, '0')
    val hour = padded.take(2).toIntOrNull() ?: return null
    val minute = padded.drop(2).take(2).toIntOrNull() ?: return null
    return hour * 60 + minute
}

/**
 * 将时间字符串格式化为 "HH:MM" 显示格式。
 */
fun formatTime(value: String): String {
    val digits = value.filter { it.isDigit() }
    if (digits.length < 3) return value
    val padded = digits.padStart(4, '0')
    return "${padded.take(2)}:${padded.drop(2).take(2)}"
}
