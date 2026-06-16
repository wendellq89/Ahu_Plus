package com.yourname.ahu_plus.ui.screen.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.ahu_plus.data.local.CourseNoteRepository
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.model.jw.CourseActivity
import com.yourname.ahu_plus.data.model.jw.CourseDisplayItem
import com.yourname.ahu_plus.data.model.jw.CourseUnit
import com.yourname.ahu_plus.data.model.jw.GetDataLesson
import com.yourname.ahu_plus.data.model.jw.SemesterInfo
import com.yourname.ahu_plus.data.model.jw.UserScheduleItem
import com.yourname.ahu_plus.data.repository.CourseRepository
import com.yourname.ahu_plus.data.repository.JwAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class ScheduleViewModel(
    private val jwAuthRepository: JwAuthRepository,
    private val courseRepository: CourseRepository,
    private val noteRepository: CourseNoteRepository,
    private val sessionManager: SessionManager? = null,
) : ViewModel() {

    private val gson = com.yourname.ahu_plus.data.GsonProvider.instance
    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    init {
        // 从持久化恢复课表布局偏好
        val sm = sessionManager
        if (sm != null) {
            _uiState.update {
                it.copy(
                    colWidthDp = sm.getScheduleColWidth(),
                    rowHeightDp = sm.getScheduleRowHeight(),
                    fontScale = sm.getScheduleFontScale(),
                )
            }
        }
        // 优先加载本地缓存，再尝试静默网络刷新
        viewModelScope.launch {
            // 先加载用户自定义课表
            loadUserItems()
            val cached = loadFromCache()
            if (cached) {
                // 有缓存数据 → 后台静默刷新
                launch { loadScheduleData(isRefresh = true) }
            } else {
                // 无缓存 → 主动加载
                loadScheduleData(isRefresh = false)
            }
        }
    }

    /** 加载用户自定义课表条目 */
    private fun loadUserItems() {
        val sm = sessionManager ?: return
        val json = sm.getUserScheduleJson() ?: return
        try {
            val items = gson.fromJson(json, Array<UserScheduleItem>::class.java).toList()
            _uiState.update { it.copy(userScheduleItems = items) }
        } catch (_: Exception) {}
    }

    /** 持久化用户自定义课表条目 */
    private fun saveUserItems() {
        val sm = sessionManager ?: return
        viewModelScope.launch {
            try {
                val json = gson.toJson(_uiState.value.userScheduleItems)
                sm.saveUserScheduleJson(json)
            } catch (_: Exception) {}
        }
    }

    /** 添加用户自定义课表条目 */
    fun addUserScheduleItem(item: UserScheduleItem) {
        _uiState.update {
            it.copy(userScheduleItems = it.userScheduleItems + item)
        }
        saveUserItems()
        rebuildDisplayItems()
    }

    /** 删除用户自定义课表条目 */
    fun removeUserScheduleItem(id: String) {
        _uiState.update {
            it.copy(userScheduleItems = it.userScheduleItems.filter { i -> i.id != id })
        }
        saveUserItems()
        rebuildDisplayItems()
    }

    fun onToggleAddCourse() {
        _uiState.update { it.copy(showAddCourse = !it.showAddCourse) }
    }

    /** 从 SessionManager 恢复已缓存的课表 JSON */
    private suspend fun loadFromCache(): Boolean {
        val sm = sessionManager ?: return false
        val json = sm.getScheduleJson() ?: return false
        return try {
            withContext(Dispatchers.IO) {
                val data = gson
                    .fromJson(json, com.yourname.ahu_plus.data.model.jw.ScheduleData::class.java)
                val displayItems = buildDisplayItems(
                    activities = data.activities,
                    selectedWeek = data.currentWeek,
                    lessons = data.lessons
                )
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = null,
                        studentName = data.studentName,
                        className = data.className,
                        department = data.department,
                        credits = data.credits,
                        allActivities = data.activities,
                        displayItems = displayItems,
                        unitTimes = data.unitTimes,
                        semester = data.semester,
                        currentWeek = data.currentWeek,
                        selectedWeek = data.currentWeek,
                        weekIndices = data.weekIndices,
                        lessons = data.lessons,
                    )
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 合并教务课程与用户自定义条目的 displayItems */
    private fun buildDisplayItems(
        activities: List<CourseActivity>,
        selectedWeek: Int,
        lessons: List<GetDataLesson>?
    ): List<CourseDisplayItem> {
        val systemItems = CourseRepository.toDisplayItems(activities, selectedWeek, lessons)
        val userItems = _uiState.value.userScheduleItems
            .filter { it.weeks.contains(selectedWeek) }
            .map { it.toDisplayItem() }
        return (systemItems + userItems).sortedWith(compareBy({ it.weekday }, { it.startUnit }))
    }

    private fun rebuildDisplayItems() {
        val data = _uiState.value
        val items = buildDisplayItems(
            activities = data.allActivities,
            selectedWeek = data.selectedWeek,
            lessons = data.lessons
        )
        _uiState.update { it.copy(displayItems = items) }
    }

    /**
     * 加载本学期课表。
     * @param isRefresh true=静默刷新(不显示 loading)，false=主动加载
     */
    private suspend fun loadScheduleData(isRefresh: Boolean = false) {
        if (!isRefresh) {
            _uiState.update { it.copy(isLoading = true, error = null) }
        }
        val wasLoaded = _uiState.value.allActivities.isNotEmpty()
        try {
            withContext(Dispatchers.IO) {
                // Step 1: 认证
                val authResult = jwAuthRepository.authenticate()
                if (authResult.isFailure) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            // 仅当本地无数据时才触发 needsLogin
                            needsLogin = !wasLoaded,
                            error = if (!wasLoaded) "教务处认证失败: ${authResult.exceptionOrNull()?.message}" else null
                        )
                    }
                    return@withContext
                }

                // Step 2: 获取课表
                val result = courseRepository.getSchedule()
                result.fold(
                    onSuccess = { data ->
                        // 序列化并缓存到本地
                        val sm = sessionManager
                        if (sm != null) {
                            try {
                                val json = com.yourname.ahu_plus.data.GsonProvider.instance.toJson(data)
                                sm.saveScheduleJson(json)
                            } catch (_: Exception) { /* 缓存失败不影响 UI */ }
                        }

                        val displayItems = buildDisplayItems(
                            activities = data.activities,
                            selectedWeek = data.currentWeek,
                            lessons = data.lessons
                        )
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = null,
                                needsLogin = false,
                                studentName = data.studentName,
                                className = data.className,
                                department = data.department,
                                credits = data.credits,
                                allActivities = data.activities,
                                displayItems = displayItems,
                                unitTimes = data.unitTimes,
                                semester = data.semester,
                                currentWeek = data.currentWeek,
                                selectedWeek = data.currentWeek,
                                weekIndices = data.weekIndices,
                                lessons = data.lessons,
                            )
                        }
                    },
                    onFailure = { e ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = if (!wasLoaded) "课表加载失败: ${e.message}" else it.error
                            )
                        }
                    }
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = if (!wasLoaded) "未知错误: ${e.message}" else it.error
                )
            }
        }
    }

    // ── 周切换 ─────────────────────────────────────

    fun onPreviousWeek() {
        val current = _uiState.value.selectedWeek
        val minWeek = _uiState.value.weekIndices.minOrNull() ?: 1
        if (current > minWeek) setSelectedWeek(current - 1)
    }

    fun onNextWeek() {
        val current = _uiState.value.selectedWeek
        val maxWeek = _uiState.value.weekIndices.maxOrNull() ?: 20
        if (current < maxWeek) setSelectedWeek(current + 1)
    }

    fun onWeekSelected(week: Int) {
        setSelectedWeek(week)
    }

    fun onRefresh() {
        viewModelScope.launch {
            loadScheduleData()
        }
    }

    // ── 课表显示设置 ─────────────────────────────────

    fun onToggleSettings() {
        _uiState.update { it.copy(showSettings = !it.showSettings) }
    }

    fun onColWidthChanged(value: Float) {
        _uiState.update { it.copy(colWidthDp = value) }
        viewModelScope.launch { sessionManager?.saveScheduleColWidth(value) }
    }

    fun onRowHeightChanged(value: Float) {
        _uiState.update { it.copy(rowHeightDp = value) }
        viewModelScope.launch { sessionManager?.saveScheduleRowHeight(value) }
    }

    fun onFontScaleChanged(value: Float) {
        _uiState.update { it.copy(fontScale = value) }
        viewModelScope.launch { sessionManager?.saveScheduleFontScale(value) }
    }

    fun onResetSettings() {
        _uiState.update { it.copy(colWidthDp = 64f, rowHeightDp = 56f, fontScale = 1.0f) }
        viewModelScope.launch {
            sessionManager?.saveScheduleColWidth(64f)
            sessionManager?.saveScheduleRowHeight(56f)
            sessionManager?.saveScheduleFontScale(1.0f)
        }
    }

    private fun setSelectedWeek(week: Int) {
        val data = _uiState.value
        val items = buildDisplayItems(
            activities = data.allActivities,
            selectedWeek = week,
            lessons = data.lessons
        )
        _uiState.update { it.copy(selectedWeek = week, displayItems = items) }
    }

    // ── 课程详情 + 备注 ─────────────────────────────

    fun onCourseClicked(item: CourseDisplayItem) {
        viewModelScope.launch {
            val note = noteRepository.observeNote(item.lessonId).first()
            _uiState.update {
                it.copy(
                    selectedCourseDetail = CourseDetailUiModel(
                        item = item,
                        lessonDetail = item.lessonDetail,
                        noteDraft = note,
                    )
                )
            }
        }
    }

    fun onNoteDraftChanged(text: String) {
        _uiState.update {
            it.copy(
                selectedCourseDetail = it.selectedCourseDetail?.copy(noteDraft = text)
            )
        }
    }

    fun onNoteSave() {
        val detail = _uiState.value.selectedCourseDetail ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(selectedCourseDetail = detail.copy(isSaving = true))
            }
            noteRepository.saveNote(detail.item.lessonId, detail.noteDraft)
            _uiState.update { it.copy(selectedCourseDetail = null) }
        }
    }

    fun onDismissSheet() {
        _uiState.update { it.copy(selectedCourseDetail = null) }
    }
}

data class ScheduleUiState(
    val needsLogin: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val studentName: String? = null,
    val className: String? = null,
    val department: String? = null,
    val credits: Double? = null,
    val allActivities: List<CourseActivity> = emptyList(),
    val displayItems: List<CourseDisplayItem> = emptyList(),
    val unitTimes: List<CourseUnit> = emptyList(),
    val semester: SemesterInfo? = null,
    val currentWeek: Int = 1,
    val selectedWeek: Int = 1,
    val weekIndices: List<Int> = emptyList(),
    val lessons: List<GetDataLesson>? = null,

    /** 当前展示的课程详情 (null 表示 BottomSheet 不显示) */
    val selectedCourseDetail: CourseDetailUiModel? = null,

    // ── 用户自定义课表 ──────────────────────────────
    val userScheduleItems: List<UserScheduleItem> = emptyList(),
    val showAddCourse: Boolean = false,

    // ── 课表显示设置 ─────────────────────────────────
    val colWidthDp: Float = 64f,
    val rowHeightDp: Float = 56f,
    val fontScale: Float = 1.0f,
    val showSettings: Boolean = false,
)

/**
 * 课程详情 UI 模型。
 *
 * 把 [CourseDisplayItem] + [GetDataLesson] 增强数据 + 备注草稿 合并为一个不可变快照,
 * 供 [CourseDetailSheet] 渲染。
 */
data class CourseDetailUiModel(
    val item: CourseDisplayItem,
    val lessonDetail: GetDataLesson?,
    val noteDraft: String = "",
    val isSaving: Boolean = false,
)
