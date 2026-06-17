package com.yourname.ahu_plus.data.local

import kotlinx.coroutines.flow.Flow

/**
 * 课程备注仓库。
 *
 * 提供两类备注的观察/保存/清除能力:
 *  - [observeCourseNote] / [saveCourseNote] / [clearCourseNote]
 *    跨节次共享,按 courseCode 聚合 (例如周一第1节英语课的备注,所有周次都能看到)
 *  - [observeSlotNote] / [saveSlotNote] / [clearSlotNote]
 *    按 (lessonId, week) 唯一 (某一节课、某一具体周次的备注)
 *
 * 旧 API (observeNote/saveNote/clearNote 走 note_<lessonId>) 已彻底废弃,代码不再读也不再写。
 * 数据若需清理,等待 [SessionManager.clearAll] 触发。
 */
class CourseNoteRepository(private val appDataStore: AppDataStore) {

    // ── 课程备注 (跨节次共享) ──────────────────────────

    /** 观察指定课程的备注 (空字符串表示无备注) */
    fun observeCourseNote(courseCode: String): Flow<String> =
        appDataStore.courseNoteFlow(courseCode)

    /** 保存课程备注。自动 trim 前后空白。 */
    suspend fun saveCourseNote(courseCode: String, text: String) {
        appDataStore.saveCourseNote(courseCode, text.trim())
    }

    /** 清除指定课程的备注 */
    suspend fun clearCourseNote(courseCode: String) {
        appDataStore.clearCourseNote(courseCode)
    }

    // ── 此节课备注 (按 lessonId + week) ─────────────────

    /** 观察某节课(具体周次)的备注 (空字符串表示无备注) */
    fun observeSlotNote(lessonId: String, week: Int): Flow<String> =
        appDataStore.slotNoteFlow(lessonId, week)

    /** 保存某节课的备注。自动 trim 前后空白。 */
    suspend fun saveSlotNote(lessonId: String, week: Int, text: String) {
        appDataStore.saveSlotNote(lessonId, week, text.trim())
    }

    /** 清除某节课的备注 */
    suspend fun clearSlotNote(lessonId: String, week: Int) {
        appDataStore.clearSlotNote(lessonId, week)
    }
}
