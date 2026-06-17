package com.yourname.ahu_plus.data.model.task

/**
 * 用户自定义待办 (与课程/作业无关的纯个人待办)。
 *
 * @property id 唯一 ID (UUID)
 * @property title 标题
 * @property subtitle 副标题/说明
 * @property dueAt 截止时间 (epoch millis,可空)
 * @property completed 是否已完成
 * @property completedAt 完成时间
 * @property createdAt 创建时间
 */
data class UserTask(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val dueAt: Long? = null,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
