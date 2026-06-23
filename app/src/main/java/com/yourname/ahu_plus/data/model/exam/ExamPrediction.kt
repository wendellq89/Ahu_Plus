package com.yourname.ahu_plus.data.model.exam

/**
 * 排考预测结果 —— 与用户课表匹配后的考试条目。
 *
 * 数据流:
 *   Gitee exam_predictions.json (meta + exams)
 *     → ExamDataRepository.fetchRemote() 解析 exams
 *     → matchPredictions() 用 course_code 与用户课表精确匹配
 *     → ExamPrediction 列表 (本类型) → UI 展示
 */
data class ExamPrediction(
    // 课程信息
    val courseName: String,
    val courseCode: String,
    val section: String,
    val fullCode: String,
    val college: String,
    // 时间地点
    val date: String,              // "2026-07-10"
    val startTime: String,         // "08:00"
    val endTime: String,           // "10:00"
    val roomName: String,
    val roomCode: String?,
    val campus: String?,
    // 监考
    val teacherName: String,
    val activityId: Long?,
    // 匹配元数据
    val matchedCourseName: String,  // 用户课表中的课程名
    val matchType: String           // "code" 精确 / "base" 基础 / "substring" 子串
)