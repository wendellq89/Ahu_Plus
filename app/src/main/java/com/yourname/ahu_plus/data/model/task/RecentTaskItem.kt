package com.yourname.ahu_plus.data.model.task

/**
 * 近期任务项的来源类型。
 */
enum class RecentTaskSource {
    HOMEWORK,   // 作业 (来自 HomeworkRepository)
    EXAM,       // 考试 (来自 Exam cache,解析后的 ExamItem)
    USER_TASK,  // 自定义待办 (来自 UserTaskRepository)
}

/**
 * 首页"近期任务"卡片使用的统一数据模型。
 *
 * 聚合 Homework + Exam + UserTask 三个来源,按 [dueAt] 升序排序
 * (null 排后),UI 层用此统一类型渲染。
 *
 * @property id 全局唯一 ID,格式 "hw:<uuid>" / "exam:<id>" / "task:<uuid>"
 * @property source 来源
 * @property title 主标题
 * @property subtitle 副标题 (考试为时间地点,作业为描述)
 * @property dueAt 截止/发生时间 (epoch millis)
 * @property isCompleted 是否已完成 (考试始终为 false)
 * @property payload 点击跳转所需的载荷 (如 examId, navigation key)
 */
data class RecentTaskItem(
    val id: String,
    val source: RecentTaskSource,
    val title: String,
    val subtitle: String? = null,
    val dueAt: Long? = null,
    val isCompleted: Boolean = false,
    val payload: String? = null,
)
