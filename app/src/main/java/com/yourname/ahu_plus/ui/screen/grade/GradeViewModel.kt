package com.yourname.ahu_plus.ui.screen.grade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.ahu_plus.data.model.jw.GpaMetadata
import com.yourname.ahu_plus.data.model.jw.Grade
import com.yourname.ahu_plus.data.repository.GradeRepository
import com.yourname.ahu_plus.data.repository.JwAuthRepository
import com.yourname.ahu_plus.data.repository.SessionExpiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GradeViewModel(
    private val jwAuthRepository: JwAuthRepository,
    private val gradeRepository: GradeRepository,
    private val sessionManager: com.yourname.ahu_plus.data.local.SessionManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(GradeUiState())
    val uiState: StateFlow<GradeUiState> = _uiState.asStateFlow()

    private val gson = com.yourname.ahu_plus.data.GsonProvider.instance

    init {
        // 优先加载本地缓存
        viewModelScope.launch {
            val cached = loadFromCache()
            if (cached) {
                launch { loadGrades(isRefresh = true) }
            } else {
                loadGrades(isRefresh = false)
            }
        }
    }

    fun onRefresh() {
        viewModelScope.launch { loadGrades(isRefresh = false) }
    }

    fun selectSemester(semesterId: Int) {
        _uiState.update { it.copy(selectedSemesterId = semesterId) }
    }

    fun onGradeClicked(grade: Grade) {
        _uiState.update { it.copy(selectedGrade = grade) }
    }

    fun onDismissGradeDetail() {
        _uiState.update { it.copy(selectedGrade = null) }
    }

    /** 从 SessionManager 恢复缓存的成绩数据 */
    private suspend fun loadFromCache(): Boolean {
        val sm = sessionManager ?: return false
        val gradesJson = sm.getGradesJson() ?: return false
        return try {
            withContext(Dispatchers.IO) {
                val resp = gson.fromJson(gradesJson, com.yourname.ahu_plus.data.model.jw.GradeResponse::class.java)
                val gpaJson = sm.getGpaMetadataJson()
                val gpa = if (gpaJson != null) {
                    runCatching { gson.fromJson(gpaJson, GpaMetadata::class.java) }.getOrNull()
                } else null

                val semesterIds = resp.semesterId2studentGrades?.keys
                    ?.mapNotNull { it.toIntOrNull() }
                    ?.sortedDescending()
                    ?: emptyList()
                val gradesBySem = resp.semesterId2studentGrades.orEmpty()
                val defaultSem = semesterIds.firstOrNull()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        gradesBySemester = gradesBySem,
                        availableSemesterIds = semesterIds,
                        selectedSemesterId = defaultSem,
                        semesterName = defaultSem?.let { id ->
                            gradesBySem[id.toString()]?.firstOrNull()?.semesterName
                                ?: resp.semesters?.firstOrNull { s -> s.id == id }?.nameZh
                        },
                        gpaMetadata = gpa,
                        error = null,
                        needsLogin = false
                    )
                }
            }
            true
        } catch (_: Exception) { false }
    }

    private suspend fun loadGrades(isRefresh: Boolean) {
        if (!isRefresh) {
            _uiState.update { it.copy(isLoading = true, error = null) }
        }
        val wasLoaded = _uiState.value.gradesBySemester.isNotEmpty()
        try {
            withContext(Dispatchers.IO) {
                val authResult = jwAuthRepository.authenticate()
                if (authResult.isFailure) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            needsLogin = !wasLoaded,
                            error = if (!wasLoaded) "教务处认证失败: ${authResult.exceptionOrNull()?.message}" else null
                        )
                    }
                    return@withContext
                }

                // 并发请求：grades JSON + GPA HTML
                val gradesResult = gradeRepository.getGrades()
                val gpaResult = gradeRepository.getGpaMetadata()

                gradesResult.fold(
                    onSuccess = { resp ->
                        // 缓存到本地
                        val sm = sessionManager
                        if (sm != null) {
                            try {
                                sm.saveGradesJson(
                                    gson.toJson(resp),
                                    gpaResult.getOrNull()?.let { gson.toJson(it) }
                                )
                            } catch (_: Exception) { /* 忽略缓存失败 */ }
                        }

                        val semesterIds = resp.semesterId2studentGrades?.keys
                            ?.mapNotNull { it.toIntOrNull() }
                            ?.sortedDescending()
                            ?: emptyList()
                        val gradesBySem = resp.semesterId2studentGrades.orEmpty()
                        val defaultSem = _uiState.value.selectedSemesterId
                            ?.takeIf { it in semesterIds }
                            ?: semesterIds.firstOrNull()
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                gradesBySemester = gradesBySem,
                                availableSemesterIds = semesterIds,
                                selectedSemesterId = defaultSem,
                                semesterName = defaultSem?.let { id ->
                                    gradesBySem[id.toString()]?.firstOrNull()?.semesterName
                                        ?: resp.semesters?.firstOrNull { s -> s.id == id }?.nameZh
                                },
                                gpaMetadata = gpaResult.getOrNull(),
                                error = null,
                                needsLogin = false
                            )
                        }
                    },
                    onFailure = { e ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = if (!wasLoaded) (e.message ?: "成绩加载失败") else it.error,
                                needsLogin = !wasLoaded && e is SessionExpiredException,
                                gpaMetadata = gpaResult.getOrNull(),
                            )
                        }
                    }
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
}

data class GradeUiState(
    val isLoading: Boolean = true,
    val gradesBySemester: Map<String, List<Grade>> = emptyMap(),
    val availableSemesterIds: List<Int> = emptyList(),
    val selectedSemesterId: Int? = null,
    val semesterName: String? = null,
    val gpaMetadata: GpaMetadata? = null,
    val selectedGrade: Grade? = null,
    val error: String? = null,
    val needsLogin: Boolean = false
) {
    val currentGrades: List<Grade>
        get() = selectedSemesterId?.let { gradesBySemester[it.toString()].orEmpty() }
            .orEmpty()
            .sortedBy { it.courseName.orEmpty() }
}
