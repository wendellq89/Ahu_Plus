package com.yourname.ahu_plus.data.model.jw

/**
 * 考试安排条目 —— 服务端没有 JSON API，**从 SSR HTML 解析**。
 *
 * 接口：`GET /student/for-std/exam-arrange/info/{studentId}`
 * 响应：Thymeleaf 渲染的 HTML，`<table id="exams">` 里的 `<tr class="finished hide">`
 *       和 `<tr class="unfinished hide">` 行包含全部数据。
 *
 * 解析逻辑见 [com.yourname.ahu_plus.data.repository.ExamRepository.parseExamHtml]。
 */
data class Exam(
    val id: String,
    val courseName: String,
    val examType: String,
    val examTime: String,
    val campus: String,
    val building: String,
    val room: String,
    val seatNumber: String?,
    val isFinished: Boolean
) {
    val displayCourse: String get() = courseName
    val displayTime: String get() = examTime
    val displayLocation: String
        get() = listOfNotNull(
                campus?.replace("校区", "")?.takeIf { it.isNotBlank() },
                room?.takeIf { it.isNotBlank() } ?: building
            )
            .joinToString(" · ")
            .ifBlank { "地点待定" }
    val displayType: String get() = examType.ifBlank { "考试" }
}
