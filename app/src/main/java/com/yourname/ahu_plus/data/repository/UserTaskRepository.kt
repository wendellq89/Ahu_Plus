package com.yourname.ahu_plus.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yourname.ahu_plus.data.GsonProvider
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.model.task.UserTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * 用户自定义待办仓库。
 *
 * 持久化在 SessionManager 的 `user_tasks_json` 中。
 */
class UserTaskRepository(private val sessionManager: SessionManager) {

    private val gson: Gson = GsonProvider.instance
    private val type = object : TypeToken<List<UserTask>>() {}.type

    private val flow = MutableStateFlow<List<UserTask>>(emptyList())
    val tasks: Flow<List<UserTask>> = flow.asStateFlow()

    init {
        runCatching {
            val json = sessionManager.getUserTasksJson() ?: return@runCatching
            val list: List<UserTask> = gson.fromJson(json, type) ?: emptyList()
            flow.value = list
        }
    }

    suspend fun upsert(task: UserTask) = withContext(Dispatchers.IO) {
        val current = flow.value.toMutableList()
        val idx = current.indexOfFirst { it.id == task.id }
        if (idx >= 0) {
            current[idx] = task
        } else {
            current.add(task)
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

    private suspend fun persist(list: List<UserTask>) {
        sessionManager.saveUserTasksJson(gson.toJson(list))
    }
}
