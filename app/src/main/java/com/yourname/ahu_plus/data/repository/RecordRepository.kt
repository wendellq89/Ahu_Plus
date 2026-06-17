package com.yourname.ahu_plus.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yourname.ahu_plus.data.GsonProvider
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.model.course.RecordEntry
import com.yourname.ahu_plus.data.model.course.RecordType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * 记录仓库 (点名/签到/作业)。
 *
 * 持久化在 SessionManager 的 `record_index_json` (类型 `Map<String, List<RecordEntry>>`),
 * key 是 lessonId,value 是该节次下所有记录。
 *
 * 内部维护一个内存 StateFlow 用于响应式订阅;upsert/setCompleted/delete
 * 都会立即更新内存并异步写回 SessionManager。
 */
class RecordRepository(private val sessionManager: SessionManager) {

    private val gson: Gson = GsonProvider.instance
    private val type = object : TypeToken<Map<String, List<RecordEntry>>>() {}.type

    /** 内存索引: lessonId -> 记录列表。StateFlow 驱动 UI。 */
    private val indexFlow = MutableStateFlow<Map<String, List<RecordEntry>>>(emptyMap())
    val records: Flow<Map<String, List<RecordEntry>>> = indexFlow.asStateFlow()

    init {
        // 首次构造时从 SessionManager 还原
        runCatching {
            val json = sessionManager.getRecordIndexJson() ?: return@runCatching
            val map: Map<String, List<RecordEntry>> = gson.fromJson(json, type) ?: emptyMap()
            indexFlow.value = map
        }
    }

    /** 观察某门课 (按 courseCode) 下的所有记录 */
    fun recordsForCourse(courseCode: String): Flow<List<RecordEntry>> =
        indexFlow.map { map ->
            map.values.flatten().filter { it.courseCode == courseCode }
        }

    /** 观察某节课 (按 lessonId) 下的所有记录 */
    fun recordsForLesson(lessonId: String): Flow<List<RecordEntry>> =
        indexFlow.map { map -> map[lessonId].orEmpty() }

    /** 观察今日 (按 weekday + week) 的所有记录 */
    fun recordsForToday(weekday: Int, week: Int): Flow<List<RecordEntry>> =
        indexFlow.map { map ->
            map.values.flatten().filter { it.weekday == weekday && it.week == week }
        }

    /** 添加或更新一条记录 (按 id) */
    suspend fun upsert(entry: RecordEntry) = withContext(Dispatchers.IO) {
        val current = indexFlow.value.toMutableMap()
        val list = current[entry.lessonId].orEmpty().toMutableList()
        val idx = list.indexOfFirst { it.id == entry.id }
        if (idx >= 0) {
            list[idx] = entry
        } else {
            list.add(entry)
        }
        current[entry.lessonId] = list
        indexFlow.value = current
        persist(current)
    }

    /** 切换记录的 completed 状态 (仅对 HOMEWORK 有意义) */
    suspend fun setCompleted(id: String, completed: Boolean) = withContext(Dispatchers.IO) {
        val current = indexFlow.value.toMutableMap()
        var changed = false
        for ((lessonId, list) in current) {
            val idx = list.indexOfFirst { it.id == id }
            if (idx >= 0) {
                val mutable = list.toMutableList()
                mutable[idx] = list[idx].copy(completed = completed)
                current[lessonId] = mutable
                changed = true
                break
            }
        }
        if (changed) {
            indexFlow.value = current
            persist(current)
        }
    }

    /** 删除一条记录 */
    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val current = indexFlow.value.toMutableMap()
        var changed = false
        for ((lessonId, list) in current) {
            val filtered = list.filter { it.id != id }
            if (filtered.size != list.size) {
                current[lessonId] = filtered
                changed = true
                break
            }
        }
        if (changed) {
            indexFlow.value = current
            persist(current)
        }
    }

    private suspend fun persist(map: Map<String, List<RecordEntry>>) {
        sessionManager.saveRecordIndexJson(gson.toJson(map))
    }

    /** 便捷方法: 添加一条点名/签到记录 (无 deadline) */
    suspend fun addQuickRecord(
        lessonId: String,
        courseCode: String,
        courseName: String,
        week: Int,
        weekday: Int,
        startUnit: Int,
        type: RecordType,
        note: String? = null,
    ) {
        val entry = RecordEntry(
            id = java.util.UUID.randomUUID().toString(),
            lessonId = lessonId,
            courseCode = courseCode,
            courseName = courseName,
            week = week,
            weekday = weekday,
            startUnit = startUnit,
            type = type,
            text = note,
        )
        upsert(entry)
    }
}
