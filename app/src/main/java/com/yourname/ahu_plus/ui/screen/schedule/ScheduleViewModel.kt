package com.yourname.ahu_plus.ui.screen.schedule

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.ahu_plus.data.local.CourseNoteRepository
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.model.course.AssessmentPlan
import com.yourname.ahu_plus.data.model.course.RecordEntry
import com.yourname.ahu_plus.data.model.course.RecordType
import com.yourname.ahu_plus.data.model.jw.CourseActivity
import com.yourname.ahu_plus.data.model.jw.CourseDisplayItem
import com.yourname.ahu_plus.data.model.jw.CourseUnit
import com.yourname.ahu_plus.data.model.jw.GetDataLesson
import com.yourname.ahu_plus.data.model.jw.SemesterInfo
import com.yourname.ahu_plus.data.model.jw.UserScheduleItem
import com.yourname.ahu_plus.data.model.task.HomeworkRecord
import com.yourname.ahu_plus.data.model.task.RecentTaskItem
import com.yourname.ahu_plus.data.model.task.RecentTaskSource
import com.yourname.ahu_plus.data.model.task.UserTask
import com.yourname.ahu_plus.data.repository.AssessmentRepository
import com.yourname.ahu_plus.data.repository.CourseRepository
import com.yourname.ahu_plus.data.repository.ExamRepository
import com.yourname.ahu_plus.data.repository.HomeworkRepository
import com.yourname.ahu_plus.data.repository.JwAuthRepository
import com.yourname.ahu_plus.data.repository.RecordRepository
import com.yourname.ahu_plus.data.repository.UserTaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import java.util.UUID

class ScheduleViewModel(
    application: Application,
    private val jwAuthRepository: JwAuthRepository,
    private val courseRepository: CourseRepository,
    private val noteRepository: CourseNoteRepository,
    private val sessionManager: SessionManager? = null,
    private val assessmentRepository: AssessmentRepository,
    private val recordRepository: RecordRepository,
    private val homeworkRepository: HomeworkRepository,
    private val userTaskRepository: UserTaskRepository,
    private val examRepository: ExamRepository,
) : AndroidViewModel(application) {

    private val gson = com.yourname.ahu_plus.data.GsonProvider.instance
    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    // ── 课程详情 sheet 内联状态 (按 lessonId+week 跟踪) ─────
    private val _courseNote = MutableStateFlow("")
    private val _slotNote = MutableStateFlow("")
    private val _assessmentPlan = MutableStateFlow<AssessmentPlan?>(null)
    private val _courseRecords = MutableStateFlow<List<RecordEntry>>(emptyList())
    private val _hasSignedIn = MutableStateFlow(false)
    var courseNote: StateFlow<String> = _courseNote.asStateFlow()
        private set
    var slotNote: StateFlow<String> = _slotNote.asStateFlow()
        private set
    var assessmentPlan: StateFlow<AssessmentPlan?> = _assessmentPlan.asStateFlow()
        private set
    var courseRecords: StateFlow<List<RecordEntry>> = _courseRecords.asStateFlow()
        private set
    /** 当前打开的节次是否已签到 (SIGN_IN 或 ROLL_CALL 任一) */
    var hasSignedIn: StateFlow<Boolean> = _hasSignedIn.asStateFlow()
        private set

    // ── 设置变更触发器 (Profile 页改开关时 bump) ───────
    private val _settingsTicker = MutableStateFlow(0)

    // ── 首页近期任务 (跨源合并) ───────────────────────
    val recentTasks: StateFlow<List<RecentTaskItem>> = combine(
        homeworkRepository.homework,
        userTaskRepository.tasks,
        _settingsTicker,
    ) { hw, tasks, _ -> hw to tasks }
        .combine(MutableStateFlow(loadExamsSnapshot())) { (hw, tasks), exams ->
            val sm = sessionManager
            val showCompleted = sm?.getShowCompletedTasks() ?: false
            val showCompletedExams = sm?.getShowCompletedExams() ?: true
            buildRecentTasks(hw, tasks, exams, showCompleted, showCompletedExams)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ── 首页签到状态 (Bug1: 今日是否已签到) ────────────
    val todayHasSignIn: StateFlow<Boolean> = combine(
        recordRepository.records,
        _settingsTicker,
    ) { records, _ ->
        val today = java.time.LocalDate.now()
        val todayWd = today.dayOfWeek.value
        val week = _uiState.value.currentWeek
        records.values.flatten().any { r ->
            (r.type == RecordType.SIGN_IN || r.type == RecordType.ROLL_CALL)
                && r.weekday == todayWd && r.week == week
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        // 从持久化恢复课表布局偏好
        val sm = sessionManager
        if (sm != null) {
            _uiState.update {
                it.copy(
                    colWidthDp = sm.getScheduleColWidth(),
                    rowHeightDp = sm.getScheduleRowHeight(),
                    fontScale = sm.getScheduleFontScale(),
                    showSat = sm.getShowSat(),
                    showSun = sm.getShowSun(),
                    pagerEnabled = sm.getPagerEnabled(),
                    resetOnEnter = sm.getResetOnEnter(),
                    showCompletedTasks = sm.getShowCompletedTasks(),
                    showCompletedExams = sm.getShowCompletedExams(),
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

    /** courseCode → colorIndex 映射 (从 allActivities 构建一次,跨周稳定) */
    private var colorMap: Map<String, Int> = emptyMap()

    /** 为任意周计算 displayItems (供 WeekPager 各页使用) */
    fun buildDisplayItemsForWeek(week: Int): List<CourseDisplayItem> {
        val data = _uiState.value
        if (colorMap.isEmpty() && data.allActivities.isNotEmpty()) {
            colorMap = CourseRepository.buildColorMap(data.allActivities)
        }
        return buildDisplayItems(data.allActivities, week, data.lessons)
    }

    /** 合并教务课程与用户自定义条目的 displayItems,应用 showSat/showSun 过滤 */
    private fun buildDisplayItems(
        activities: List<CourseActivity>,
        selectedWeek: Int,
        lessons: List<GetDataLesson>?,
        showSat: Boolean = _uiState.value.showSat,
        showSun: Boolean = _uiState.value.showSun,
    ): List<CourseDisplayItem> {
        if (colorMap.isEmpty() && activities.isNotEmpty()) {
            colorMap = CourseRepository.buildColorMap(activities)
        }
        val systemItems = CourseRepository.toDisplayItems(activities, selectedWeek, lessons, colorMap)
        val userItems = _uiState.value.userScheduleItems
            .filter { it.weeks.contains(selectedWeek) }
            .map { it.toDisplayItem() }
        val all = (systemItems + userItems).sortedWith(compareBy({ it.weekday }, { it.startUnit }))
        return all.filter { item ->
            when (item.weekday) {
                6 -> showSat
                7 -> showSun
                else -> true
            }
        }
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
        _uiState.update {
            it.copy(
                colWidthDp = 64f, rowHeightDp = 56f, fontScale = 1.0f,
                showSat = true, showSun = true,
                pagerEnabled = true, resetOnEnter = true,
                showCompletedTasks = false,
                showCompletedExams = true,
            )
        }
        viewModelScope.launch {
            sessionManager?.saveScheduleColWidth(64f)
            sessionManager?.saveScheduleRowHeight(56f)
            sessionManager?.saveScheduleFontScale(1.0f)
            sessionManager?.setShowSat(true)
            sessionManager?.setShowSun(true)
            sessionManager?.setPagerEnabled(true)
            sessionManager?.setResetOnEnter(true)
            sessionManager?.setShowCompletedTasks(false)
        }
        rebuildDisplayItems()
    }

    // ── 课表显示设置 (5 个 Boolean) ──────────────────────

    fun setShowSat(value: Boolean) {
        _uiState.update { it.copy(showSat = value) }
        viewModelScope.launch { sessionManager?.setShowSat(value) }
        rebuildDisplayItems()
    }

    fun setShowSun(value: Boolean) {
        _uiState.update { it.copy(showSun = value) }
        viewModelScope.launch { sessionManager?.setShowSun(value) }
        rebuildDisplayItems()
    }

    fun setPagerEnabled(value: Boolean) {
        _uiState.update { it.copy(pagerEnabled = value) }
        viewModelScope.launch { sessionManager?.setPagerEnabled(value) }
    }

    fun setResetOnEnter(value: Boolean) {
        _uiState.update { it.copy(resetOnEnter = value) }
        viewModelScope.launch { sessionManager?.setResetOnEnter(value) }
    }

    fun setShowCompletedTasks(value: Boolean) {
        _uiState.update { it.copy(showCompletedTasks = value) }
        viewModelScope.launch {
            sessionManager?.setShowCompletedTasks(value)
            _settingsTicker.value++
        }
    }

    fun setShowCompletedExams(value: Boolean) {
        _uiState.update { it.copy(showCompletedExams = value) }
        viewModelScope.launch {
            sessionManager?.setShowCompletedExams(value)
            _settingsTicker.value++
        }
    }

    /** 计算当前课表状态在指定 weekday (6=周六, 7=周日) 上有课的所有节次 */
    fun affectedWeekendClasses(weekday: Int): List<AffectedClass> {
        val data = _uiState.value
        return data.allActivities
            .filter { it.weekday == weekday }
            .mapNotNull { act ->
                val firstWeek = act.weekIndexes?.minOrNull() ?: return@mapNotNull null
                val lastWeek = act.weekIndexes?.maxOrNull() ?: return@mapNotNull null
                if (firstWeek == lastWeek) "第 $firstWeek 周"
                else "第 $firstWeek-$lastWeek 周"
                act.courseName?.let { name ->
                    AffectedClass(
                        label = "$name (${act.weeksStr ?: "${firstWeek}-${lastWeek}"})",
                        weekday = weekday,
                    )
                }
            }
            .distinct()
    }

    fun setSelectedWeek(week: Int) {
        val data = _uiState.value
        val items = buildDisplayItems(
            activities = data.allActivities,
            selectedWeek = week,
            lessons = data.lessons
        )
        _uiState.update { it.copy(selectedWeek = week, displayItems = items) }
    }

    /** 进入 ScheduleScreen 时调用: 若开关开启且不在当前周,跳到当前周 */
    fun applyEnterReset() {
        if (!_uiState.value.resetOnEnter) return
        val data = _uiState.value
        if (data.selectedWeek != data.currentWeek && data.currentWeek >= 1) {
            setSelectedWeek(data.currentWeek)
        }
    }

    // ── 课程详情 + 5 折叠 section 数据订阅 ───────────────

    fun onCourseClicked(item: CourseDisplayItem) {
        val courseCode = item.courseCode.orEmpty()
        val lessonId = item.lessonId.toString()
        val week = _uiState.value.selectedWeek
        _uiState.update {
            it.copy(
                selectedCourseDetail = CourseDetailUiModel(
                    item = item,
                    lessonDetail = item.lessonDetail,
                    currentWeek = week,
                )
            )
        }
        // 启动 4 个 Flow 订阅 (collect 在 viewModelScope,直到 sheet 关闭)
        courseNoteJob?.cancel()
        courseNoteJob = viewModelScope.launch {
            // 课程备注
            launch {
                noteRepository.observeCourseNote(courseCode).collect { _courseNote.value = it }
            }
            // 此节课备注
            launch {
                noteRepository.observeSlotNote(lessonId, week).collect { _slotNote.value = it }
            }
            // 考核方案
            launch {
                assessmentRepository.observe(lessonId).collect { _assessmentPlan.value = it }
            }
            // 该课程代码下的所有记录
            launch {
                recordRepository.recordsForCourse(courseCode).collect { records ->
                    _courseRecords.value = records
                    // 派生: 当前 lessonId+week 是否有 SIGN_IN 或 ROLL_CALL 记录
                    _hasSignedIn.value = records.any { r ->
                        r.lessonId == lessonId &&
                        r.week == week &&
                        (r.type == RecordType.SIGN_IN || r.type == RecordType.ROLL_CALL)
                    }
                }
            }
        }
    }

    fun onDismissSheet() {
        courseNoteJob?.cancel()
        courseNoteJob = null
        _courseNote.value = ""
        _slotNote.value = ""
        _assessmentPlan.value = null
        _courseRecords.value = emptyList()
        _hasSignedIn.value = false
        _uiState.update { it.copy(selectedCourseDetail = null) }
    }

    // 保存课程备注
    fun saveCourseNote(text: String) {
        val cc = _uiState.value.selectedCourseDetail?.item?.courseCode.orEmpty()
        if (cc.isBlank()) return
        viewModelScope.launch { noteRepository.saveCourseNote(cc, text) }
    }

    // 保存此节课备注
    fun saveSlotNote(text: String) {
        val detail = _uiState.value.selectedCourseDetail ?: return
        val lessonId = detail.item.lessonId.toString()
        viewModelScope.launch { noteRepository.saveSlotNote(lessonId, detail.currentWeek, text) }
    }

    // ── 考核方案 (Step 4 完整版;此版本留空接口) ─────────

    fun saveAssessment(plan: AssessmentPlan) {
        viewModelScope.launch { assessmentRepository.save(plan) }
    }

    fun addAssessmentImage(uri: android.net.Uri): Unit {} // Step 4 接入
    fun removeAssessmentImage(path: String): Unit {} // Step 4 接入

    // ── 记录 (点名/签到/作业) ─────────────────────────

    fun addRollCall() {
        addQuickRecord(RecordType.ROLL_CALL, note = null)
    }
    fun addSignIn() {
        addQuickRecord(RecordType.SIGN_IN, note = null)
    }

    /**
     * 不依赖 selectedCourseDetail 的快速签到 (2026-06-17 Bug3 修复)。
     * 用于首页 TodayCourseCard 顶部的"签到"按钮。
     * 优先选当前正在上的课, 否则选今天下一节。
     */
    fun addQuickSignInForToday() {
        val today = java.time.LocalDate.now()
        val todayWd = today.dayOfWeek.value
        val now = java.time.LocalTime.now()
        val currentWeek = _uiState.value.currentWeek
        val items = _uiState.value.allActivities
            .filter { it.weekday == todayWd && (it.weekIndexes ?: emptyList()).contains(currentWeek) }
            .mapNotNull { act ->
                val start = act.startUnit ?: return@mapNotNull null
                val end = act.endUnit ?: start
                val startStr = act.startTime?.takeIf { it.isNotBlank() }
                    ?: _uiState.value.unitTimes.firstOrNull { it.indexNo == start }?.startTimeStr()
                val endStr = act.endTime?.takeIf { it.isNotBlank() }
                    ?: _uiState.value.unitTimes.firstOrNull { it.indexNo == end }?.endTimeStr()
                if (startStr == null || endStr == null) return@mapNotNull null
                val startMin = com.yourname.ahu_plus.data.model.jw.parseTimeMinutes(startStr) ?: return@mapNotNull null
                val endMin = com.yourname.ahu_plus.data.model.jw.parseTimeMinutes(endStr) ?: return@mapNotNull null
                val nowMin = now.hour * 60 + now.minute
                Quad(act, startMin, endMin, nowMin - startMin)
            }
        // 优先正在上, 否则今天还没开始但有课
        val target = items.firstOrNull { (_, s, e, elapsed) -> elapsed in 0..(e - s) }
            ?: items.firstOrNull { it.elapsed < 0 }
            ?: return
        val (act, _, _, _) = target
        viewModelScope.launch {
            recordRepository.addQuickRecord(
                lessonId = act.lessonId?.toString().orEmpty(),
                courseCode = act.courseCode.orEmpty(),
                courseName = act.courseName.orEmpty(),
                week = currentWeek,
                weekday = todayWd,
                startUnit = act.startUnit ?: 0,
                type = RecordType.SIGN_IN,
                note = null,
            )
        }
    }

    /** 不依赖 selectedCourseDetail 的添加作业 (2026-06-17 Bug4 修复)。 */
    fun addQuickHomeworkForToday(text: String, deadline: Long?) {
        val today = java.time.LocalDate.now()
        val todayWd = today.dayOfWeek.value
        val now = java.time.LocalTime.now()
        val currentWeek = _uiState.value.currentWeek
        val items = _uiState.value.allActivities
            .filter { it.weekday == todayWd && (it.weekIndexes ?: emptyList()).contains(currentWeek) }
            .mapNotNull { act ->
                val start = act.startUnit ?: return@mapNotNull null
                val end = act.endUnit ?: start
                val startStr = act.startTime?.takeIf { it.isNotBlank() }
                    ?: _uiState.value.unitTimes.firstOrNull { it.indexNo == start }?.startTimeStr()
                val endStr = act.endTime?.takeIf { it.isNotBlank() }
                    ?: _uiState.value.unitTimes.firstOrNull { it.indexNo == end }?.endTimeStr()
                if (startStr == null || endStr == null) return@mapNotNull null
                val startMin = com.yourname.ahu_plus.data.model.jw.parseTimeMinutes(startStr) ?: return@mapNotNull null
                val endMin = com.yourname.ahu_plus.data.model.jw.parseTimeMinutes(endStr) ?: return@mapNotNull null
                val nowMin = now.hour * 60 + now.minute
                Quad(act, startMin, endMin, nowMin - startMin)
            }
        val target = items.firstOrNull { (_, s, e, elapsed) -> elapsed in 0..(e - s) }
            ?: items.firstOrNull { it.elapsed < 0 }
            ?: return
        val (act, _, _, _) = target
        val id = java.util.UUID.randomUUID().toString()
        val entry = com.yourname.ahu_plus.data.model.course.RecordEntry(
            id = id,
            lessonId = act.lessonId?.toString().orEmpty(),
            courseCode = act.courseCode.orEmpty(),
            courseName = act.courseName.orEmpty(),
            week = currentWeek,
            weekday = todayWd,
            startUnit = act.startUnit ?: 0,
            type = com.yourname.ahu_plus.data.model.course.RecordType.HOMEWORK,
            text = text,
            deadline = deadline,
        )
        val hw = com.yourname.ahu_plus.data.model.task.HomeworkRecord(
            id = id,
            lessonId = act.lessonId?.toString().orEmpty(),
            courseCode = act.courseCode.orEmpty(),
            courseName = act.courseName.orEmpty(),
            title = text,
            notes = null,
            deadline = deadline,
        )
        viewModelScope.launch {
            recordRepository.upsert(entry)
            homeworkRepository.upsert(hw)
        }
    }

    fun addHomework(text: String, deadline: Long?) {
        val detail = _uiState.value.selectedCourseDetail ?: return
        val id = UUID.randomUUID().toString()
        val item = detail.item
        val entry = RecordEntry(
            id = id,
            lessonId = item.lessonId.toString(),
            courseCode = item.courseCode.orEmpty(),
            courseName = item.courseName,
            week = detail.currentWeek,
            weekday = item.weekday,
            startUnit = item.startUnit,
            type = RecordType.HOMEWORK,
            text = text,
            deadline = deadline,
        )
        val hw = HomeworkRecord(
            id = id,
            lessonId = item.lessonId.toString(),
            courseCode = item.courseCode.orEmpty(),
            courseName = item.courseName,
            title = text,
            notes = null,
            deadline = deadline,
        )
        viewModelScope.launch {
            recordRepository.upsert(entry)
            homeworkRepository.upsert(hw)
        }
    }

    private var courseNoteJob: kotlinx.coroutines.Job? = null

    private fun addQuickRecord(type: RecordType, note: String?) {
        val detail = _uiState.value.selectedCourseDetail ?: return
        val item = detail.item
        viewModelScope.launch {
            recordRepository.addQuickRecord(
                lessonId = item.lessonId.toString(),
                courseCode = item.courseCode.orEmpty(),
                courseName = item.courseName,
                week = detail.currentWeek,
                weekday = item.weekday,
                startUnit = item.startUnit,
                type = type,
                note = note,
            )
        }
    }

    fun toggleRecordCompleted(recordId: String) {
        viewModelScope.launch {
            val snapshot = recordRepository.records.first()
            val current = snapshot.values.flatten().firstOrNull { it.id == recordId } ?: return@launch
            val newCompleted = !current.completed
            recordRepository.setCompleted(recordId, newCompleted)
            // 同步 HomeworkRecord 状态
            homeworkRepository.setCompleted(recordId, newCompleted)
        }
    }

    fun deleteRecord(recordId: String) {
        viewModelScope.launch {
            recordRepository.delete(recordId)
            homeworkRepository.delete(recordId)
        }
    }

    // ── 首页近期任务 (用户自定义待办) ─────────────────

    fun addUserTask(title: String, subtitle: String?, dueAt: Long?) {
        viewModelScope.launch {
            userTaskRepository.upsert(
                UserTask(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    subtitle = subtitle,
                    dueAt = dueAt,
                )
            )
        }
    }

    fun toggleUserTask(taskId: String) {
        viewModelScope.launch { userTaskRepository.setCompleted(taskId, !isTaskCompleted(taskId)) }
    }

    private fun isTaskCompleted(taskId: String): Boolean {
        // 通过当前 recentTasks 派生 (UserTask 段)
        return recentTasks.value.firstOrNull { it.id == "task:$taskId" }?.isCompleted ?: false
    }

    fun deleteUserTask(taskId: String) {
        viewModelScope.launch { userTaskRepository.delete(taskId) }
    }

    /**
     * 统一处理"近期任务"勾选 (Bug3 修复)。
     * 根据 item.source 路由到对应 Repository,并同步 RecordEntry。
     */
    fun toggleRecentTask(item: com.yourname.ahu_plus.data.model.task.RecentTaskItem) {
        when (item.source) {
            com.yourname.ahu_plus.data.model.task.RecentTaskSource.USER_TASK -> {
                val id = item.id.removePrefix("task:")
                viewModelScope.launch { userTaskRepository.setCompleted(id, !item.isCompleted) }
            }
            com.yourname.ahu_plus.data.model.task.RecentTaskSource.HOMEWORK -> {
                val id = item.id.removePrefix("hw:")
                viewModelScope.launch {
                    homeworkRepository.setCompleted(id, !item.isCompleted)
                    recordRepository.setCompleted(id, !item.isCompleted)
                }
            }
            com.yourname.ahu_plus.data.model.task.RecentTaskSource.EXAM -> {
                // 考试不可勾选,忽略
            }
        }
    }

    // ── 工具方法 ─────────────────────────────────────

    /** 加载考试缓存快照 (一次性,首页近期任务用) */
    private fun loadExamsSnapshot(): List<com.yourname.ahu_plus.data.model.jw.Exam> {
        val sm = sessionManager ?: return emptyList()
        val json = sm.getExamsJson() ?: return emptyList()
        return runCatching {
            gson.fromJson(json, Array<com.yourname.ahu_plus.data.model.jw.Exam>::class.java).toList()
        }.getOrDefault(emptyList())
    }

    /** 合并作业 + 考试 + 用户任务 → 近期任务列表 */
    private fun buildRecentTasks(
        homeworks: List<HomeworkRecord>,
        tasks: List<UserTask>,
        exams: List<com.yourname.ahu_plus.data.model.jw.Exam>,
        showCompleted: Boolean,
        showCompletedExams: Boolean,
    ): List<RecentTaskItem> {
        val hwItems = homeworks.map { hw ->
            RecentTaskItem(
                id = "hw:${hw.id}",
                source = RecentTaskSource.HOMEWORK,
                title = "${hw.courseName.ifBlank { "作业" }} · ${hw.title}",
                subtitle = hw.notes,
                dueAt = hw.deadline,
                isCompleted = hw.completed,
            )
        }
        val taskItems = tasks.map { t ->
            RecentTaskItem(
                id = "task:${t.id}",
                source = RecentTaskSource.USER_TASK,
                title = t.title,
                subtitle = t.subtitle,
                dueAt = t.dueAt,
                isCompleted = t.completed,
            )
        }
        val examItems = exams.mapNotNull { e ->
            val epoch = parseExamStartMillis(e.examTime) ?: return@mapNotNull null
            RecentTaskItem(
                id = "exam:${e.id}",
                source = RecentTaskSource.EXAM,
                title = e.displayCourse,
                subtitle = e.displayLocation + " · " + e.displayTime,
                dueAt = epoch,
                isCompleted = e.isFinished,
                payload = e.id,
            )
        }
        val merged = (hwItems + taskItems + examItems)
            .let { items ->
                val filtered = if (showCompleted) items
                else items.filter { !it.isCompleted && (it.source != RecentTaskSource.EXAM || !it.isCompleted) }
                // 2026-06-17 Bug2: 过滤已考完考试 (isFinished == true)
                if (showCompletedExams) filtered
                else filtered.filter { it.source != RecentTaskSource.EXAM || !it.isCompleted }
            }
        // 2026-06-17 Bug3: 已完成沉底,未完成按 dueAt 升序
        return merged.sortedWith(
            compareBy<RecentTaskItem> { it.isCompleted }         // 未完成在前
                .thenBy { it.dueAt == null }                     // 有截止在前
                .thenBy { it.dueAt ?: Long.MAX_VALUE }           // 截止早在前
        )
    }

    /**
     * 解析 Exam.examTime 字符串为 epoch millis。
     * 格式如 "2026-05-24 14:00~15:40" — 只取第一段。
     */
    private fun parseExamStartMillis(examTime: String): Long? {
        val regex = Regex("""(\d{4}-\d{2}-\d{2})\s+(\d{1,2}:\d{2})""")
        val match = regex.find(examTime) ?: return null
        val (date, time) = match.destructured
        return runCatching {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            sdf.parse("$date $time")?.time
        }.getOrNull()
    }
}

/** 受影响的周末课程 (供 ConfirmHideWeekendDialog 展示) */
data class AffectedClass(
    val label: String,
    val weekday: Int,
)

/** 四元组 (addQuickSignInForToday / addQuickHomeworkForToday 内部用) */
private data class Quad(
    val act: CourseActivity,
    val start: Int,
    val end: Int,
    val elapsed: Int,
)

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

    // ── 课表显示设置 2.0 (2026-06-17) ─────────────────
    val showSat: Boolean = true,
    val showSun: Boolean = true,
    val pagerEnabled: Boolean = true,
    val resetOnEnter: Boolean = true,
    val showCompletedTasks: Boolean = false,
    val showCompletedExams: Boolean = true,
)

/**
 * 课程详情 UI 模型。
 *
 * 把 [CourseDisplayItem] + [GetDataLesson] 增强数据 + 当前周次 合并为一个不可变快照,
 * 供 [CourseDetailSheet] 渲染。
 */
data class CourseDetailUiModel(
    val item: CourseDisplayItem,
    val lessonDetail: GetDataLesson?,
    val currentWeek: Int = 1,
)
