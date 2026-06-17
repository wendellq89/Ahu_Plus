package com.yourname.ahu_plus.data.model.course

/**
 * 记录类型:点名/签到/作业。
 */
enum class RecordType {
    ROLL_CALL,  // 点名
    SIGN_IN,    // 签到
    HOMEWORK,   // 作业
}

/**
 * 一条点名/签到/作业记录。
 *
 * 关联到具体的 (lessonId, week) 即"某周次的某一节"。
 * 但也冗余存了 [courseCode]/[courseName] 以便在该门课的所有节次
 * 详情页中"记录一览"section 都能显示出来。
 *
 * @property id 唯一 ID (UUID)
 * @property lessonId 关联节次
 * @property courseCode 课程代码 (跨节次聚合用)
 * @property courseName 课程名称
 * @property week 周次
 * @property weekday 星期 (1..7)
 * @property startUnit 起节
 * @property type 记录类型
 * @property text 描述/详情 (点名/签到可空,作业必填)
 * @property deadline 截止时间 (仅作业有意义,epoch millis)
 * @property completed 是否已完成 (仅作业可勾选)
 * @property createdAt 创建时间 (epoch millis)
 */
data class RecordEntry(
    val id: String,
    val lessonId: String,
    val courseCode: String,
    val courseName: String,
    val week: Int,
    val weekday: Int,
    val startUnit: Int,
    val type: RecordType,
    val text: String? = null,
    val deadline: Long? = null,
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
