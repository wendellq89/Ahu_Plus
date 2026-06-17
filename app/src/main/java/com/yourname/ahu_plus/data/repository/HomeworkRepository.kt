package com.yourname.ahu_plus.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yourname.ahu_plus.data.GsonProvider
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.model.task.HomeworkRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * 作业仓库 (扁平列表,用于首页"近期任务"卡片)。
 *
 * 与 [RecordRepository] 中 type=HOMEWORK 的记录同步:
 * - 添加时需要同时调用 [upsert] (本类) 与 [RecordRepository.upsert] (另一类)
 * - 切换完成状态时同时更新两处 (在 ViewModel 层组合)
 *
 * 持久化在 SessionManager 的 `homework_json` 中。
 */
class HomeworkRepository(private val sessionManager: SessionManager) {

    private val gson: Gson = GsonProvider.instance
    private val type = object : TypeToken<List<HomeworkRecord>>() {}.type

    private val flow = MutableStateFlow<List<HomeworkRecord>>(emptyList())
    val homework: Flow<List<HomeworkRecord>> = flow.asStateFlow()

    init {
        runCatching {
            val json = sessionManager.getHomeworkJson() ?: return@runCatching
            val list: List<HomeworkRecord> = gson.fromJson(json, type) ?: emptyList()
            flow.value = list
        }
    }

    suspend fun upsert(record: HomeworkRecord) = withContext(Dispatchers.IO) {
        val current = flow.value.toMutableList()
        val idx = current.indexOfFirst { it.id == record.id }
        if (idx >= 0) {
            current[idx] = record
        } else {
            current.add(record)
        }
        flow.value = current
        persist(current)
    }

    suspend fun setCompleted(id: String, completed: Boolean) = withContext(Dispatchers.IO) {
        val current = flow.value.toMutableList()
        val idx = current.indexOfFirst { it.id == id }
        if (idx >= 0) {
            current[idx] = current[idx].copy(
                completed = completed,
                completedAt = if (completed) System.currentTimeMillis() else null,
            )
            flow.value = current
            persist(current)
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val current = flow.value.filter { it.id != id }
        if (current.size != flow.value.size) {
            flow.value = current
            persist(current)
        }
    }

    /** 按 ID 查找单条 (一次性) */
    fun getById(id: String): HomeworkRecord? = flow.value.firstOrNull { it.id == id }

    private suspend fun persist(list: List<HomeworkRecord>) {
        sessionManager.saveHomeworkJson(gson.toJson(list))
    }
}
