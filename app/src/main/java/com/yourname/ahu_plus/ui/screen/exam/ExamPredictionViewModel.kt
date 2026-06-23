package com.yourname.ahu_plus.ui.screen.exam

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.ahu_plus.data.GsonProvider
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.model.exam.AggregatedCourse
import com.yourname.ahu_plus.data.model.exam.ExamPrediction
import com.yourname.ahu_plus.data.model.exam.MATCH_TYPE_TEACHER
import com.yourname.ahu_plus.data.repository.ExamDataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 排考预测 ViewModel — Gitee 数据源模式 (2026-06-23 重构)
 *
 * 数据流:
 *   1. init: 从本地缓存读取 → 立即展示 (避免空白页)
 *   2. 后台异步从 Gitee 拉取最新 JSON → 覆盖缓存 → 重新聚合
 *   3. 用户手动下拉刷新: 重新拉取 + 重新匹配
 *
 * 懒加载: VM 仅在 ExamPredictionScreen 进入组合 (Composable 进入 composition) 时
 * 创建,所以 `init` 也只在用户真正进入页面时执行。配合 MainScreen /
 * AppHubScreen 中 `remember { ... }` 放在 `HOME_EXAM_PREDICTION` 分支内部,
 * 确保不会在 App 启动时拉取。
 */
class ExamPredictionViewModel(
    private val examDataRepository: ExamDataRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExamPredictionUiState())
    val uiState: StateFlow<ExamPredictionUiState> = _uiState.asStateFlow()

    private val gson = GsonProvider.instance

    init {
        viewModelScope.launch {
            // 1) 先用本地缓存秒显,避免空白页
            loadFromCache()
            // 2) 后台拉远端,失败不影响缓存展示
            refresh()
        }
    }

    /** 手动下拉刷新 — 重新拉远端 + 重新聚合。 */
    fun onRefresh() {
        viewModelScope.launch { refresh() }
    }

    private suspend fun loadFromCache() {
        val meta = examDataRepository.getCachedMeta()
        if (meta == null) {
            _uiState.update {
                it.copy(
                    isFromCache = false,
                    generatedAt = null,
                    lastFetchedAt = null,
                )
            }
            return
        }
        val userCodes = getUserCourseCodes()
        val userTeachers = getUserCourseTeachers()
        if (userCodes.isEmpty()) {
            _uiState.update {
                it.copy(
                    aggregated = emptyList(),
                    isFromCache = true,
                    generatedAt = meta.generatedAt,
                    error = "未找到课表数据,请先在「课程表」中刷新",
                )
            }
            return
        }
        val matched = examDataRepository.matchPredictions(userCodes)
        val aggregated = aggregate(matched, userTeachers)
        _uiState.update {
            it.copy(
                aggregated = aggregated,
                isFromCache = true,
                generatedAt = meta.generatedAt,
                error = null,
            )
        }
        Log.i(TAG, "loaded from cache: ${matched.size} matched, ${aggregated.size} aggregated")
    }

    private suspend fun refresh() {
        _uiState.update { it.copy(isLoading = true, error = null) }

        val fetchResult = examDataRepository.fetchRemote()
        fetchResult.onFailure { e ->
            Log.w(TAG, "fetchRemote failed: ${e.message}")
            val current = _uiState.value
            if (current.aggregated.isEmpty() && current.generatedAt == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "拉取失败: ${e.message ?: "网络异常"}",
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "刷新失败,展示的是本地缓存: ${e.message ?: "网络异常"}",
                    )
                }
            }
            return
        }

        val userCodes = getUserCourseCodes()
        val userTeachers = getUserCourseTeachers()
        if (userCodes.isEmpty()) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    aggregated = emptyList(),
                    generatedAt = examDataRepository.getCachedMeta()?.generatedAt,
                    error = "未找到课表数据,请先在「课程表」中刷新",
                )
            }
            return
        }
        val matched = examDataRepository.matchPredictions(userCodes)
        val aggregated = aggregate(matched, userTeachers)
        _uiState.update {
            it.copy(
                isLoading = false,
                aggregated = aggregated,
                isFromCache = false,
                generatedAt = examDataRepository.getCachedMeta()?.generatedAt,
                lastFetchedAt = System.currentTimeMillis(),
                error = null,
            )
        }
        Log.i(TAG, "refreshed: ${matched.size} matched, ${aggregated.size} aggregated")
    }

    // ─────────────────────────────────────────────────────────
    // 聚合 + 老师匹配
    // ─────────────────────────────────────────────────────────

    /**
     * 把同一门课的多个考试场次聚合成一张卡,内部按"老师匹配优先 + 时间"排序。
     *
     * @param predictions 课程代码精确匹配后的所有考试场次
     * @param userTeachers 用户的任课老师: courseCode → [teacher names]
     */
    private fun aggregate(
        predictions: List<ExamPrediction>,
        userTeachers: Map<String, List<String>>
    ): List<AggregatedCourse> {
        if (predictions.isEmpty()) return emptyList()

        // 按 courseCode 聚合
        val byCode = predictions.groupBy { it.courseCode }
        val result = byCode.map { (code, sessions) ->
            val teachers = userTeachers[code].orEmpty()
            val sorted = sortSessionsByTeacherAndTime(sessions, teachers)
            AggregatedCourse(
                courseName = sessions.first().matchedCourseName.ifBlank { code },
                courseCode = code,
                teacherNames = teachers,
                sessions = sorted,
            )
        }

        // 整体按最早考试日期升序,这样用户先看到最近要考的
        return result.sortedBy { it.sessions.minOf { s -> s.date } }
    }

    /**
     * 对同一门课的多场次排序:老师匹配的在最前,其余按 (date, startTime) 升序。
     * 同时给匹配场次的 matchType 标 "teacher",UI 据此显示徽章。
     */
    private fun sortSessionsByTeacherAndTime(
        sessions: List<ExamPrediction>,
        userTeachers: List<String>
    ): List<ExamPrediction> {
        if (sessions.isEmpty()) return sessions
        val (matched, others) = sessions.partition { s ->
            isTeacherMatch(s, userTeachers)
        }
        val matchedReordered = matched.sortedWith(
            compareBy({ it.date }, { it.startTime }, { it.roomName })
        ).map { it.copy(matchType = MATCH_TYPE_TEACHER) }
        val othersReordered = others.sortedWith(
            compareBy({ it.date }, { it.startTime }, { it.roomName })
        )
        return matchedReordered + othersReordered
    }

    /**
     * 判断考试的考官与用户的任课老师是否匹配。
     * 考官字符串 (主监考：xxx；副监考：yyy) 用多种分隔符拆分,任一与用户任一老师名
     * 互为子串且双方都非空,就视为匹配。
     */
    private fun isTeacherMatch(session: ExamPrediction, userTeachers: List<String>): Boolean {
        if (userTeachers.isEmpty()) return false
        val proctors = parseProctorNames(session.teacherName)
        if (proctors.isEmpty()) return false
        return proctors.any { p ->
            userTeachers.any { t ->
                t.isNotBlank() && p.contains(t) || t.contains(p)
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // 课表解析
    // ─────────────────────────────────────────────────────────

    private fun getUserCourseCodes(): Map<String, String> {
        val json = sessionManager.getScheduleJson() ?: return emptyMap()
        return try {
            val scheduleData = gson.fromJson(
                json,
                com.yourname.ahu_plus.data.model.jw.ScheduleData::class.java
            )
            val result = mutableMapOf<String, String>()
            for (activity in scheduleData.activities) {
                val code = activity.courseCode
                val name = activity.courseName
                if (!code.isNullOrBlank() && !name.isNullOrBlank()) {
                    result[code] = name
                }
            }
            result
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse schedule: ${e.message}")
            emptyMap()
        }
    }

    /**
     * 从课表里提取每个 courseCode 对应的任课老师列表。
     * 一门课可能由多位老师合上 (如实验课),所以返回 List。
     */
    private fun getUserCourseTeachers(): Map<String, List<String>> {
        val json = sessionManager.getScheduleJson() ?: return emptyMap()
        return try {
            val scheduleData = gson.fromJson(
                json,
                com.yourname.ahu_plus.data.model.jw.ScheduleData::class.java
            )
            val result = mutableMapOf<String, MutableSet<String>>()
            for (activity in scheduleData.activities) {
                val code = activity.courseCode ?: continue
                // teachers / teacherNames 字段,任一非空就用
                val names = (activity.teachers.orEmpty() + activity.teacherNames.orEmpty())
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                if (names.isNotEmpty()) {
                    result.getOrPut(code) { mutableSetOf() }.addAll(names)
                }
            }
            result.mapValues { it.value.toList() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse schedule teachers: ${e.message}")
            emptyMap()
        }
    }

    companion object {
        private const val TAG = "ExamPredictionVM"

        /**
         * 解析主/副监考字符串为姓名列表。
         * 格式示例:
         *   "主监考：张三；副监考：李四"  → ["张三", "李四"]
         *   "主监考: 王五;副监考:赵六"   → ["王五", "赵六"]
         *   "" 或 null                  → []
         */
        fun parseProctorNames(raw: String?): List<String> {
            if (raw.isNullOrBlank()) return emptyList()
            val tokens = raw.split(Regex("[;;\\s]+|(?:主|副)监考[：:]"))
            return tokens
                .map { it.trim() }
                .filter { it.isNotBlank() }
        }
    }
}

data class ExamPredictionUiState(
    val isLoading: Boolean = false,
    val aggregated: List<AggregatedCourse> = emptyList(),
    val error: String? = null,
    val isFromCache: Boolean = false,
    /** 远端 JSON 的 generated_at 字段 (ISO 字符串,如 "2026-06-23T18:00:00+08:00") */
    val generatedAt: String? = null,
    /** 本机最后一次成功拉取的时间戳 (epoch millis),用于"刚刚/几分钟前"提示 */
    val lastFetchedAt: Long? = null,
)