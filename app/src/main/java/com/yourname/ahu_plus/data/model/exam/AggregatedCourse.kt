package com.yourname.ahu_plus.data.model.exam

/**
 * 排考预测 — 按用户课程聚合后的展示单元。
 *
 * 一门用户课 (courseCode) 可能对应多场考试 (如 复变函数 同时有 5 个时段/考场),
 * 这里把它们聚合成一张卡,点击展开后看到所有具体场次。
 */
data class AggregatedCourse(
    /** 用户课表里的课程名 (来自 scheduleData.activities.courseName) */
    val courseName: String,
    /** 课程代码 (如 ZJ32090) — 聚合主键 */
    val courseCode: String,
    /** 该课程用户的任课老师列表 (可能为空) */
    val teacherNames: List<String>,
    /** 该课程匹配到的所有具体考试场次 — 已按"老师匹配优先 + 日期时间"排序 */
    val sessions: List<ExamPrediction>
) {
    val sessionCount: Int get() = sessions.size

    val earliestDate: String? get() = sessions.minOfOrNull { it.date }

    /** 是否存在任一场次的考官与用户任课老师匹配 */
    val hasTeacherMatch: Boolean
        get() = sessions.any { it.matchType == MATCH_TYPE_TEACHER }
}

/**
 * Session 的 matchType 取值:
 *  - "code"    课程代码精确匹配 (基础)
 *  - "teacher" 课程代码 + 任课老师 = 考官 (优先置顶)
 */
const val MATCH_TYPE_TEACHER = "teacher"
const val MATCH_TYPE_CODE = "code"