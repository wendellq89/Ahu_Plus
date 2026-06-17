package com.yourname.ahu_plus.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.yourname.ahu_plus.data.GsonProvider
import com.yourname.ahu_plus.data.model.course.AssessmentPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 全局共享的 DataStore 委托。
 *
 * 从 SessionManager 抽离而来——原本 SessionManager 顶层的 `Context.dataStore`
 * 是 private 的,只能它自己用。现在提升到 internal,允许同包下的其他类
 * (例如 CourseNoteRepository) 复用同一个 Preferences 文件。
 *
 * 注意:这是进程级单例,同一进程对一个 name 只能有一个委托实例。
 */
internal val Context.ahuPlusDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "ahu_plus_prefs")

/**
 * 全局共享的 DataStore 访问层。
 *
 * 职责:
 *  - 暴露原始 DataStore 给 SessionManager 沿用旧 key (JSESSIONID / JW SESSION / 凭据 / 主题 等)
 *  - 提供课程备注存取 (key 形如 course_note_<code> / slot_note_<lessonId>_<week>)
 *  - 提供考核方案存取 (key 形如 assessment_<lessonId>, 存 JSON)
 */
class AppDataStore(context: Context) {

    private val ds: DataStore<Preferences> = context.applicationContext.ahuPlusDataStore
    private val gson: Gson = GsonProvider.instance

    /** 暴露原始 DataStore,供 SessionManager 沿用其原有 key 体系 */
    val dataStore: DataStore<Preferences> get() = ds

    // ── 课程备注 (跨节次共享,按 courseCode 聚合) ─────────

    /** 观察某门课的备注 (默认空字符串) */
    fun courseNoteFlow(courseCode: String): Flow<String> = ds.data.map { prefs ->
        prefs[courseNoteKey(courseCode)] ?: ""
    }

    /** 保存某门课的备注 */
    suspend fun saveCourseNote(courseCode: String, text: String) {
        ds.edit { it[courseNoteKey(courseCode)] = text }
    }

    /** 清除某门课的备注 */
    suspend fun clearCourseNote(courseCode: String) {
        ds.edit { it.remove(courseNoteKey(courseCode)) }
    }

    private fun courseNoteKey(courseCode: String): Preferences.Key<String> =
        stringPreferencesKey("course_note_${courseCode}")

    // ── 此节课备注 (按 lessonId+week 唯一) ─────────────────

    /** 观察某节课(具体周次)的备注 */
    fun slotNoteFlow(lessonId: String, week: Int): Flow<String> = ds.data.map { prefs ->
        prefs[slotNoteKey(lessonId, week)] ?: ""
    }

    /** 保存某节课的备注 */
    suspend fun saveSlotNote(lessonId: String, week: Int, text: String) {
        ds.edit { it[slotNoteKey(lessonId, week)] = text }
    }

    /** 清除某节课的备注 */
    suspend fun clearSlotNote(lessonId: String, week: Int) {
        ds.edit { it.remove(slotNoteKey(lessonId, week)) }
    }

    private fun slotNoteKey(lessonId: String, week: Int): Preferences.Key<String> =
        stringPreferencesKey("slot_note_${lessonId}_${week}")

    // ── 考核方案 (按 lessonId,存 JSON) ────────────────────

    /** 观察某节课的考核方案 (可能为 null) */
    fun assessmentFlow(lessonId: String): Flow<AssessmentPlan?> = ds.data.map { prefs ->
        val json = prefs[assessmentKey(lessonId)] ?: return@map null
        runCatching { gson.fromJson(json, AssessmentPlan::class.java) }.getOrNull()
    }

    /** 保存考核方案 */
    suspend fun saveAssessment(plan: AssessmentPlan) {
        ds.edit { it[assessmentKey(plan.lessonId)] = gson.toJson(plan) }
    }

    /** 清除某节课的考核方案 */
    suspend fun clearAssessment(lessonId: String) {
        ds.edit { it.remove(assessmentKey(lessonId)) }
    }

    private fun assessmentKey(lessonId: String): Preferences.Key<String> =
        stringPreferencesKey("assessment_${lessonId}")
}
