package com.yourname.ahu_plus.ui.screen.trainingplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.ahu_plus.data.GsonProvider
import com.yourname.ahu_plus.data.model.jw.CompletionCourse
import com.yourname.ahu_plus.data.model.jw.CompletionSummary
import com.yourname.ahu_plus.data.model.jw.GradeResponse
import com.yourname.ahu_plus.data.model.jw.PlanModuleNode
import com.yourname.ahu_plus.data.model.jw.TrainingPlanResponse
import com.yourname.ahu_plus.data.repository.JwAuthRepository
import com.yourname.ahu_plus.data.repository.ProgramCompletionRepository
import com.yourname.ahu_plus.data.repository.SessionExpiredException
import com.yourname.ahu_plus.data.repository.TrainingPlanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TrainingPlanViewModel(
    private val jwAuthRepository: JwAuthRepository,
    private val trainingPlanRepository: TrainingPlanRepository,
    private val completionRepository: ProgramCompletionRepository? = null,
    private val sessionManager: com.yourname.ahu_plus.data.local.SessionManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrainingPlanUiState())
    val uiState: StateFlow<TrainingPlanUiState> = _uiState.asStateFlow()

    private val gson = GsonProvider.instance
    private val expandedIds = mutableSetOf<Int>()

    init {
        viewModelScope.launch {
            val cached = loadFromCache()
            if (cached) {
                launch { loadAll(isRefresh = true) }
            } else {
                loadAll(isRefresh = false)
            }
        }
    }

    fun onRefresh() {
        viewModelScope.launch { loadAll(isRefresh = false) }
    }

    fun toggleExpand(moduleId: Int) {
        if (moduleId in expandedIds) expandedIds.remove(moduleId)
        else expandedIds.add(moduleId)
        _uiState.update { it.copy(expandedIds = expandedIds.toSet()) }
    }

    private suspend fun loadFromCache(): Boolean {
        val sm = sessionManager ?: return false
        val json = sm.getTrainingPlanJson() ?: return false
        return try {
            withContext(Dispatchers.IO) {
                val resp = gson.fromJson(json, TrainingPlanResponse::class.java)
                applyTrainingPlan(resp)

                // 从缓存加载成绩（用于粗略完成状态兜底）
                val gradesJson = sm.getGradesJson()
                if (gradesJson != null) {
                    runCatching { gson.fromJson(gradesJson, GradeResponse::class.java) }
                        .getOrNull()?.let { applyGrades(it) }
                }
                // 从缓存加载课表（用于在读判断兜底）
                val scheduleJson = sm.getScheduleJson()
                if (scheduleJson != null) {
                    applyScheduleCache(scheduleJson)
                }
            }
            true
        } catch (_: Exception) { false }
    }

    private suspend fun loadAll(isRefresh: Boolean) {
        if (!isRefresh) {
            _uiState.update { it.copy(isLoading = true, error = null) }
        }
        val wasLoaded = _uiState.value.topModules.isNotEmpty()
        try {
            withContext(Dispatchers.IO) {
                val authResult = jwAuthRepository.authenticate()
                if (authResult.isFailure) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            needsLogin = !wasLoaded,
                            error = if (!wasLoaded) "教务处认证失败" else null
                        )
                    }
                    return@withContext
                }

                // 并行加载培养方案 + 完成数据
                val planResult = trainingPlanRepository.getTrainingPlan()
                val compResult = completionRepository?.getCompletionData()

                planResult.fold(
                    onSuccess = { resp ->
                        sessionManager?.let { sm ->
                            try { sm.saveTrainingPlanJson(gson.toJson(resp)) }
                            catch (_: Exception) {}
                        }
                        applyTrainingPlan(resp)
                    },
                    onFailure = { e ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = if (!wasLoaded) (e.message ?: "培养方案加载失败") else it.error,
                                needsLogin = !wasLoaded && e is SessionExpiredException
                            )
                        }
                    }
                )

                compResult?.fold(
                    onSuccess = { (courses, summary) ->
                        applyCompletionData(courses, summary)
                    },
                    onFailure = { /* 完成数据加载失败使用兜底方案 */ }
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = if (!wasLoaded) "未知错误: ${e.message}" else it.error,
                    needsLogin = !wasLoaded && e is SessionExpiredException
                )
            }
        }
    }

    private fun applyTrainingPlan(resp: TrainingPlanResponse) {
        _uiState.update {
            it.copy(
                isLoading = false,
                planId = resp.id,
                topModules = resp.children.orEmpty(),
                totalRequiredCredits = resp.sumChildrenRequiredCreditsOrZero?.toDoubleOrNull()
                    ?: resp.requireInfo?.requiredCredits,
                creditBySubModule = resp.creditBySubModule.orEmpty(),
                error = null,
                needsLogin = false
            )
        }
    }

    /** 官方 program-completion-preview 数据 */
    private fun applyCompletionData(courses: List<CompletionCourse>, summary: CompletionSummary) {
        val passedCodes = courses.filter { it.isPassed }.mapNotNull { it.code }.toSet()
        val takingCodes = courses.filter { it.isTaking }.mapNotNull { it.code }.toSet()
        val failedCodes = courses.filter { it.isFailed }.mapNotNull { it.code }.toSet()

        // code → credits 映射
        val codeCredits = courses.filter { it.code != null }.associate { it.code!! to (it.credits ?: 0.0) }

        _uiState.update { state ->
            // 收集培养方案中所有课程代码
            val allPlanCodes = mutableSetOf<String>()
            fun collectCodes(node: PlanModuleNode) {
                node.planCourses?.forEach { c -> c.course?.code?.let { allPlanCodes.add(it) } }
                node.children?.forEach { collectCodes(it) }
            }
            state.topModules.forEach { collectCodes(it) }

            // 已通过但不在培养方案中的课程代码（如通识选修的 TX 课程）
            val unmatchedPassed = passedCodes - allPlanCodes
            var unmatchedPassedCredits = 0.0
            unmatchedPassed.forEach { code -> unmatchedPassedCredits += codeCredits[code] ?: 0.0 }
            // 同样收集未匹配的修读中/挂科/未修课程
            val allUnmatchedCodes = (passedCodes + takingCodes + failedCodes) - allPlanCodes
            val unmatchedCourses = courses.filter { it.code in allUnmatchedCodes }

            // 计算每模块完成学分，未匹配的归入「通识选修」
            val moduleCompletion = computeModuleCompletion(
                state.topModules, passedCodes, takingCodes, failedCodes, codeCredits
            )
            // 把未匹配学分加到通识选修
            val adjustedCompletion = moduleCompletion.toMutableMap()
            val flexModule = "通识选修"
            if (unmatchedPassedCredits > 0 && flexModule in adjustedCompletion) {
                adjustedCompletion[flexModule] = (adjustedCompletion[flexModule] ?: 0.0) + unmatchedPassedCredits
            }

            state.copy(
                officialSummary = summary.copy(
                    requiredCredits = state.totalRequiredCredits,
                    passedCount = passedCodes.size,
                    takingCount = takingCodes.size,
                    unrepairedCount = courses.count { c -> c.isUnrepaired },
                    failedCount = failedCodes.size
                ),
                passedCourseCodes = passedCodes,
                inProgressCourseCodes = takingCodes,
                failedCourseCodes = failedCodes,
                moduleCompletion = adjustedCompletion,
                unmatchedCompletionCourses = unmatchedCourses,
                hasOfficialCompletion = true,
                isLoading = false
            )
        }
    }

    /** 递归计算每个模块的已通过学分（深度遍历子树） */
    private fun computeModuleCompletion(
        modules: List<PlanModuleNode>,
        passedCodes: Set<String>,
        takingCodes: Set<String>,
        failedCodes: Set<String>,
        codeCredits: Map<String, Double>
    ): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        for (m in modules) {
            result[m.type?.nameZh ?: continue] = sumPassedRecursive(m, passedCodes, codeCredits)
        }
        return result
    }

    private fun sumPassedRecursive(
        node: PlanModuleNode,
        passedCodes: Set<String>,
        codeCredits: Map<String, Double>
    ): Double {
        var sum = 0.0
        node.planCourses?.forEach { c ->
            val code = c.course?.code ?: return@forEach
            if (code in passedCodes) sum += codeCredits[code] ?: c.displayCredits
        }
        node.children?.forEach { child ->
            sum += sumPassedRecursive(child, passedCodes, codeCredits)
        }
        return sum
    }

    /** 兜底：从成绩数据粗略判断已修 */
    private fun applyGrades(resp: GradeResponse) {
        val allGrades = resp.allGrades()
        val gradeByCode = allGrades
            .filter { it.courseCode != null }
            .groupBy { it.courseCode!! }
            .mapValues { (_, grades) ->
                grades.firstOrNull { it.gaGrade != null && it.gaGrade != "--" && it.published == true }
                    ?: grades.firstOrNull { it.published == true }
                    ?: grades.first()
            }

        val passedCodes = gradeByCode
            .filter { (_, g) -> g.gaGrade != null && g.gaGrade != "--" && g.published == true }
            .keys

        if (!_uiState.value.hasOfficialCompletion) {
            _uiState.update { it.copy(passedCourseCodes = it.passedCourseCodes + passedCodes) }
        }
    }

    /** 兜底：从缓存的课表 JSON 提取 courseCode */
    private fun applyScheduleCache(json: String) {
        runCatching {
            val data = gson.fromJson(json, com.yourname.ahu_plus.data.model.jw.ScheduleData::class.java)
            val codes = data.activities.mapNotNull { it.courseCode }.toSet()
            if (!_uiState.value.hasOfficialCompletion) {
                _uiState.update { it.copy(inProgressCourseCodes = it.inProgressCourseCodes + codes) }
            }
        }
    }

    fun getCourseStatus(courseCode: String): CourseCompletion = when {
        courseCode in _uiState.value.passedCourseCodes -> CourseCompletion.PASSED
        courseCode in _uiState.value.failedCourseCodes -> CourseCompletion.FAILED
        courseCode in _uiState.value.inProgressCourseCodes -> CourseCompletion.IN_PROGRESS
        else -> CourseCompletion.NOT_TAKEN
    }
}

enum class CourseCompletion { NOT_TAKEN, IN_PROGRESS, PASSED, FAILED }

data class TrainingPlanUiState(
    val isLoading: Boolean = true,
    val planId: Int? = null,
    val topModules: List<PlanModuleNode> = emptyList(),
    val totalRequiredCredits: Double? = null,
    val creditBySubModule: Map<String, Double> = emptyMap(),
    val expandedIds: Set<Int> = emptySet(),
    val error: String? = null,
    val needsLogin: Boolean = false,
    // ── 完成状态 ──
    val hasOfficialCompletion: Boolean = false,
    val officialSummary: CompletionSummary = CompletionSummary(),
    val passedCourseCodes: Set<String> = emptySet(),
    val inProgressCourseCodes: Set<String> = emptySet(),
    val failedCourseCodes: Set<String> = emptySet(),
    /** 模块名 → 已通过学分 */
    val moduleCompletion: Map<String, Double> = emptyMap(),
    /** 未匹配到培养方案课程的完成数据（如通识选修的 TX 课程） */
    val unmatchedCompletionCourses: List<CompletionCourse> = emptyList()
) {
    val totalModuleCount: Int get() = topModules.size
    val completionProgress: Float
        get() = if (hasOfficialCompletion) officialSummary.completionProgress
        else if (totalRequiredCredits != null && totalRequiredCredits > 0) {
            (totalEarnedCredits / totalRequiredCredits).toFloat().coerceIn(0f, 1f)
        } else 0f

    /** 兜底：从成绩计算已获学分 */
    private val totalEarnedCredits: Double
        get() = officialSummary.passedCredits
}
