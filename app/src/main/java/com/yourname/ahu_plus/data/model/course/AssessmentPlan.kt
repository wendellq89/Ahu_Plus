package com.yourname.ahu_plus.data.model.course

/**
 * 课程的考核方案 (用户自填:文字 + 图片)。
 *
 * 关联到具体节次 lessonId (与教务处的排课 ID 对齐)。
 * 图片路径列表存储的是相对路径 (相对 filesDir/course_assets/),
 * 完整文件路径在显示时由 [resolveImagePath] 等工具拼接。
 *
 * @property lessonId 关联的节次 ID
 * @property text 考核方案文字内容
 * @property imagePaths 图片相对路径列表
 */
data class AssessmentPlan(
    val lessonId: String,
    val text: String = "",
    val imagePaths: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = text.isBlank() && imagePaths.isEmpty()
}
