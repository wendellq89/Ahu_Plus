package com.yourname.ahu_plus.data.model.task

/**
 * 作业记录 (扁平存储,用于首页"近期任务"卡片)。
 *
 * 与 [com.yourname.ahu_plus.data.model.course.RecordEntry] (type=HOMEWORK) 字段高度重合。
 * 设计两份是为了:
 *  1. 首页一次 collectAsStateWithLifecycle 拿到所有作业 (包括其他节次、其他课程)
 *  2. 与 RecordEntry 同步更新 (在 ViewModel 层组合)
 *
 * @property id 与对应 RecordEntry.id 共享,便于关联
 * @property lessonId 关联节次 (可空,如"添加作业待办"场景未关联课程)
 * @property courseCode 课程代码
 * @property courseName 课程名称
 * @property title 作业标题
 * @property notes 作业描述
 * @property deadline 截止时间 (epoch millis,可空)
 * @property completed 是否已完成
 * @property completedAt 完成时间
 * @property createdAt 创建时间
 */
data class HomeworkRecord(
    val id: String,
    val lessonId: String? = null,
    val courseCode: String = "",
    val courseName: String = "",
    val title: String,
    val notes: String? = null,
    val deadline: Long? = null,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
