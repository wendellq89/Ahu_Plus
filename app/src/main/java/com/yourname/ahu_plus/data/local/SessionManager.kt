package com.yourname.ahu_plus.data.local

import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.reflect.TypeToken
import com.yourname.ahu_plus.data.GsonProvider
import com.yourname.ahu_plus.data.model.MarketIdentity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 会话与凭据存储。
 *
 * 持久化策略(写死一点,优先稳定性):
 *  - 所有数据统一存到 DataStore Preferences (进程级单例 [AppDataStore])
 *  - 用户名 / 密码 明文存储(用户已在本地授权过的设备上使用)
 *  - JSESSIONID / JW SESSION 也明文存储
 *
 * 这样:
 *  - 不依赖 EncryptedSharedPreferences / Keystore,避免设备兼容性问题
 *  - 杀后台后启动能 100% 恢复登录态
 *  - 用户主动退出登录前,凭据不会被清
 *
 * 备注:课程备注由 [AppDataStore] 独立暴露的 noteFlow/saveNote 等方法管理,
 *       SessionManager 不再承担。
 */
class SessionManager(private val appDataStore: AppDataStore) {

    private var initialized = false
    private var cachedSessionId: String? = null
    private var cachedJwSessionId: String? = null
    private var cachedJwPstSid: String? = null
    private var cachedUsername: String? = null
    private var cachedPassword: String? = null
    private var cachedMarketApiIdentity: String? = null
    private var cachedThemeMode: AppThemeMode = AppThemeMode.SYSTEM
    private var cachedStudentInfoJson: String? = null
    private var cachedStudentInfoUpdatedAt: Long = 0L

    // ── 集市设置 ─────────────────────────────────────
    private var cachedMarketIdentities: List<MarketIdentity> = emptyList()
    private var cachedSelectedIdentityIds: Set<String> = emptySet()
    private var cachedBlockPinned: Boolean = false
    private var cachedBlockKeywords: List<String> = emptyList()
    private var cachedFilterNodeIds: List<Long> = emptyList()
    // 集市功能总开关 (true = 启用，底部导航显示 3 项；false = 禁用，仅 2 项)
    private var cachedMarketEnabled: Boolean = true
    // 集市列表布局模式 ("list" 单列 / "stagger" 小红书双列瀑布)
    private var cachedMarketListLayoutMode: String = "list"
    private var cachedScheduleColWidth: Float = 64f
    private var cachedScheduleRowHeight: Float = 56f
    private var cachedScheduleFontScale: Float = 1.0f

    // 课表显示设置 (2026-06-17 课表重构)
    private var cachedShowSat: Boolean = true
    private var cachedShowSun: Boolean = true
    private var cachedPagerEnabled: Boolean = true
    private var cachedResetOnEnter: Boolean = true
    private var cachedShowCompletedTasks: Boolean = false
    private var cachedShowCompletedExams: Boolean = true

    // ── 业务数据缓存 ─────────────────────────────────
    private var cachedScheduleJson: String? = null
    private var cachedScheduleUpdatedAt: Long = 0L
    private var cachedGradesJson: String? = null
    private var cachedGpaMetadataJson: String? = null
    private var cachedGradesUpdatedAt: Long = 0L
    private var cachedExamsJson: String? = null
    private var cachedExamsUpdatedAt: Long = 0L
    private var cachedFinanceJson: String? = null
    private var cachedFinanceUpdatedAt: Long = 0L
    private var cachedAttendanceJson: String? = null
    private var cachedAttendanceUpdatedAt: Long = 0L
    private var cachedKqcardAttendanceJson: String? = null
    private var cachedKqcardAttendanceUpdatedAt: Long = 0L
    private var cachedUserScheduleJson: String? = null
    private var cachedAssessmentJson: String? = null
    private var cachedAssessmentUpdatedAt: Long = 0L
    private var cachedRecordIndexJson: String? = null
    private var cachedRecordIndexUpdatedAt: Long = 0L
    private var cachedHomeworkJson: String? = null
    private var cachedHomeworkUpdatedAt: Long = 0L
    private var cachedUserTasksJson: String? = null
    private var cachedUserTasksUpdatedAt: Long = 0L
    private var cachedBathroomPhone: String? = null
    private var cachedAcConfig: ElectricityRoomConfig = ElectricityRoomConfig()
    private var cachedLightingConfig: ElectricityRoomConfig = ElectricityRoomConfig()
    private var cachedAdwmhSessionId: String? = null
    private var cachedTrainingPlanJson: String? = null
    private var cachedTrainingPlanUpdatedAt: Long = 0L
    private var cachedEmptyClassroomJson: String? = null
    private var cachedEmptyClassroomKey: String? = null
    private var cachedEmptyClassroomUpdatedAt: Long = 0L

    val themeModeFlow = appDataStore.dataStore.data.map { preferences ->
        AppThemeMode.fromStorageValue(preferences[THEME_MODE_KEY])
    }

    /**
     * 从 DataStore 恢复所有数据到内存缓存。
     * 只在 App 启动时调用一次。
     */
    suspend fun init(): String? {
        val prefs = appDataStore.dataStore.data.first()
        cachedSessionId = prefs[SESSION_KEY]
        cachedJwSessionId = prefs[JW_SESSION_KEY]
        cachedJwPstSid = prefs[JW_PST_SID_KEY]
        cachedUsername = prefs[USERNAME_KEY]
        cachedPassword = prefs[PASSWORD_KEY]
        cachedThemeMode = AppThemeMode.fromStorageValue(prefs[THEME_MODE_KEY])
        cachedStudentInfoJson = prefs[STUDENT_INFO_KEY]
        cachedStudentInfoUpdatedAt = prefs[STUDENT_INFO_UPDATED_AT_KEY] ?: 0L

        // ── 集市设置：迁移旧单身份 → 新多身份格式 ──────────
        val oldIdentity = prefs[MARKET_API_IDENTITY_KEY]
        val newIdentitiesJson = prefs[MARKET_IDENTITIES_KEY]
        if (!oldIdentity.isNullOrBlank() && newIdentitiesJson.isNullOrBlank()) {
            // 旧格式 → 新格式 一次性迁移
            val school = runCatching {
                com.yourname.ahu_plus.data.remote.market.MarketApi.schoolFromIdentity(oldIdentity)
            }.getOrNull()
            val identity = MarketIdentity(
                id = java.util.UUID.randomUUID().toString(),
                token = oldIdentity,
                school = school
            )
            cachedMarketIdentities = listOf(identity)
            cachedSelectedIdentityIds = setOf(identity.id)
            cachedMarketApiIdentity = oldIdentity
            appDataStore.dataStore.edit { edit ->
                edit[MARKET_IDENTITIES_KEY] = gson.toJson(cachedMarketIdentities)
                edit[MARKET_SELECTED_IDS_KEY] = gson.toJson(cachedSelectedIdentityIds.toList())
                edit.remove(MARKET_API_IDENTITY_KEY)
            }
        } else {
            cachedMarketApiIdentity = oldIdentity
            cachedMarketIdentities = parseIdentityList(newIdentitiesJson)
            val selectedIdsJson = prefs[MARKET_SELECTED_IDS_KEY]
            cachedSelectedIdentityIds = parseStringList(selectedIdsJson).toSet()
            if (cachedSelectedIdentityIds.isEmpty() && cachedMarketIdentities.isNotEmpty()) {
                cachedSelectedIdentityIds = cachedMarketIdentities.map { it.id }.toSet()
            }
        }

        cachedBlockPinned = prefs[MARKET_BLOCK_PINNED_KEY] == "true"
        cachedBlockKeywords = parseStringList(prefs[MARKET_BLOCK_KEYWORDS_KEY])
        cachedFilterNodeIds = parseLongList(prefs[MARKET_FILTER_NODES_KEY])
        // 集市功能总开关: 缺省 true,保持向后兼容 (老用户默认启用)
        cachedMarketEnabled = (prefs[MARKET_ENABLED_KEY] ?: "true") == "true"
        // 列表布局模式: 缺省 "list" (单列)
        cachedMarketListLayoutMode = prefs[MARKET_LIST_LAYOUT_KEY] ?: "list"

        // 课表布局偏好（带默认值容错）
        cachedScheduleColWidth = prefs[SCHEDULE_COL_WIDTH_KEY]?.toFloatOrNull() ?: 64f
        cachedScheduleRowHeight = prefs[SCHEDULE_ROW_HEIGHT_KEY]?.toFloatOrNull() ?: 56f
        cachedScheduleFontScale = prefs[SCHEDULE_FONT_SCALE_KEY]?.toFloatOrNull() ?: 1.0f
        // 课表显示设置 (2026-06-17)
        cachedShowSat = (prefs[KEY_SHOW_SAT] ?: "true") == "true"
        cachedShowSun = (prefs[KEY_SHOW_SUN] ?: "true") == "true"
        cachedPagerEnabled = (prefs[KEY_PAGER_ENABLED] ?: "true") == "true"
        cachedResetOnEnter = (prefs[KEY_RESET_ON_ENTER] ?: "true") == "true"
        cachedShowCompletedTasks = (prefs[KEY_SHOW_COMPLETED_TASKS] ?: "false") == "true"
        cachedShowCompletedExams = (prefs[KEY_SHOW_COMPLETED_EXAMS] ?: "true") == "true"

        // ── 业务数据缓存恢复 ──────────────────────────
        cachedScheduleJson = prefs[SCHEDULE_JSON_KEY]
        cachedScheduleUpdatedAt = prefs[SCHEDULE_UPDATED_AT_KEY] ?: 0L
        cachedGradesJson = prefs[GRADES_JSON_KEY]
        cachedGpaMetadataJson = prefs[GPA_METADATA_JSON_KEY]
        cachedGradesUpdatedAt = prefs[GRADES_UPDATED_AT_KEY] ?: 0L
        cachedExamsJson = prefs[EXAMS_JSON_KEY]
        cachedExamsUpdatedAt = prefs[EXAMS_UPDATED_AT_KEY] ?: 0L
        cachedFinanceJson = prefs[FINANCE_JSON_KEY]
        cachedFinanceUpdatedAt = prefs[FINANCE_UPDATED_AT_KEY] ?: 0L
        cachedAttendanceJson = prefs[ATTENDANCE_JSON_KEY]
        cachedAttendanceUpdatedAt = prefs[ATTENDANCE_UPDATED_AT_KEY] ?: 0L
        cachedKqcardAttendanceJson = prefs[KQCARD_ATTENDANCE_JSON_KEY]
        cachedKqcardAttendanceUpdatedAt = prefs[KQCARD_ATTENDANCE_UPDATED_AT_KEY] ?: 0L
        cachedUserScheduleJson = prefs[USER_SCHEDULE_JSON_KEY]
        cachedAssessmentJson = prefs[ASSESSMENT_JSON_KEY]
        cachedAssessmentUpdatedAt = prefs[ASSESSMENT_UPDATED_AT_KEY] ?: 0L
        cachedRecordIndexJson = prefs[RECORD_INDEX_JSON_KEY]
        cachedRecordIndexUpdatedAt = prefs[RECORD_INDEX_UPDATED_AT_KEY] ?: 0L
        cachedHomeworkJson = prefs[HOMEWORK_JSON_KEY]
        cachedHomeworkUpdatedAt = prefs[HOMEWORK_UPDATED_AT_KEY] ?: 0L
        cachedUserTasksJson = prefs[USER_TASKS_JSON_KEY]
        cachedUserTasksUpdatedAt = prefs[USER_TASKS_UPDATED_AT_KEY] ?: 0L
        cachedBathroomPhone = prefs[BATHROOM_PHONE_KEY]
        cachedAcConfig = parseElectricityConfig(prefs[AC_CONFIG_KEY])
        cachedLightingConfig = parseElectricityConfig(prefs[LIGHTING_CONFIG_KEY])
        cachedAdwmhSessionId = prefs[ADWMH_SESSION_KEY]
        cachedTrainingPlanJson = prefs[TRAINING_PLAN_JSON_KEY]
        cachedTrainingPlanUpdatedAt = prefs[TRAINING_PLAN_UPDATED_AT_KEY] ?: 0L
        cachedEmptyClassroomJson = prefs[EMPTY_CLASSROOM_JSON_KEY]
        cachedEmptyClassroomKey = prefs[EMPTY_CLASSROOM_KEY_KEY]
        cachedEmptyClassroomUpdatedAt = prefs[EMPTY_CLASSROOM_UPDATED_AT_KEY] ?: 0L

        initialized = true
        Log.i(
            TAG, "init done: session=${cachedSessionId?.take(8)}... " +
                "jw=${cachedJwSessionId?.take(8)}... user=$cachedUsername " +
                "identities=${cachedMarketIdentities.size}"
        )
        return cachedSessionId
    }

    /** 同步从当前缓存读取(供 UI 在 init 完成前/中安全访问) */
    fun isInitialized(): Boolean = initialized

    // ── 外观设置 ─────────────────────────────────────

    fun getThemeMode(): AppThemeMode = cachedThemeMode

    suspend fun saveThemeMode(themeMode: AppThemeMode) {
        cachedThemeMode = themeMode
        appDataStore.dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = themeMode.storageValue
        }
    }

    // ── 一卡通 session (JSESSIONID) ────────────────────

    fun getSessionId(): String? = cachedSessionId

    suspend fun saveSessionId(id: String) {
        cachedSessionId = id
        appDataStore.dataStore.edit { preferences ->
            preferences[SESSION_KEY] = id
        }
    }

    suspend fun clearSession() {
        cachedSessionId = null
        appDataStore.dataStore.edit { preferences ->
            preferences.remove(SESSION_KEY)
        }
    }

    // ── 教务处 session ───────────────────────────────

    fun getJwSessionId(): String? = cachedJwSessionId

    fun getJwPstSid(): String? = cachedJwPstSid

    suspend fun saveJwSession(sessionId: String, pstSid: String) {
        cachedJwSessionId = sessionId
        cachedJwPstSid = pstSid
        appDataStore.dataStore.edit { preferences ->
            preferences[JW_SESSION_KEY] = sessionId
            preferences[JW_PST_SID_KEY] = pstSid
        }
    }

    suspend fun clearJwSession() {
        cachedJwSessionId = null
        cachedJwPstSid = null
        appDataStore.dataStore.edit { preferences ->
            preferences.remove(JW_SESSION_KEY)
            preferences.remove(JW_PST_SID_KEY)
        }
    }

    // ── 凭据 ─────────────────────────────────────────

    suspend fun saveCredentials(username: String, password: String) {
        cachedUsername = username
        cachedPassword = password
        appDataStore.dataStore.edit { preferences ->
            preferences[USERNAME_KEY] = username
            preferences[PASSWORD_KEY] = password
        }
        Log.i(TAG, "凭据已保存: user=$username")
    }

    fun getUsername(): String? = cachedUsername

    fun getPassword(): String? = cachedPassword

    fun hasCredentials(): Boolean = !cachedUsername.isNullOrBlank() && !cachedPassword.isNullOrBlank()

    suspend fun clearCredentials() {
        cachedUsername = null
        cachedPassword = null
        appDataStore.dataStore.edit { preferences ->
            preferences.remove(USERNAME_KEY)
            preferences.remove(PASSWORD_KEY)
        }
    }

    // ── 集市 API 身份字段（兼容旧接口） ────────────────────

    fun getMarketApiIdentity(): String? {
        // 优先从多身份列表中返回第一个选中的
        val selected = cachedMarketIdentities.filter { it.id in cachedSelectedIdentityIds }
        return selected.firstOrNull()?.token
            ?: cachedMarketApiIdentity?.takeIf { it.isNotBlank() }
    }

    suspend fun saveMarketApiIdentity(identity: String) {
        cachedMarketApiIdentity = identity
        appDataStore.dataStore.edit { preferences ->
            preferences[MARKET_API_IDENTITY_KEY] = identity
        }
    }

    suspend fun clearMarketApiIdentity() {
        cachedMarketApiIdentity = null
        appDataStore.dataStore.edit { preferences ->
            preferences.remove(MARKET_API_IDENTITY_KEY)
        }
    }

    // ── 集市多身份管理 ─────────────────────────────────

    fun getMarketIdentities(): List<MarketIdentity> = cachedMarketIdentities

    fun getSelectedIdentityIds(): Set<String> = cachedSelectedIdentityIds

    suspend fun saveMarketIdentities(identities: List<MarketIdentity>) {
        cachedMarketIdentities = identities
        appDataStore.dataStore.edit { it[MARKET_IDENTITIES_KEY] = gson.toJson(identities) }
    }

    suspend fun saveSelectedIdentityIds(ids: Set<String>) {
        cachedSelectedIdentityIds = ids
        appDataStore.dataStore.edit { it[MARKET_SELECTED_IDS_KEY] = gson.toJson(ids.toList()) }
    }

    // ── 集市屏蔽置顶 ──────────────────────────────────

    fun getBlockPinned(): Boolean = cachedBlockPinned

    suspend fun setBlockPinned(enabled: Boolean) {
        cachedBlockPinned = enabled
        appDataStore.dataStore.edit {
            it[MARKET_BLOCK_PINNED_KEY] = if (enabled) "true" else "false"
        }
    }

    // ── 集市屏蔽词 ────────────────────────────────────

    fun getBlockKeywords(): List<String> = cachedBlockKeywords

    suspend fun saveBlockKeywords(keywords: List<String>) {
        cachedBlockKeywords = keywords
        appDataStore.dataStore.edit { it[MARKET_BLOCK_KEYWORDS_KEY] = gson.toJson(keywords) }
    }

    // ── 集市板块筛选 ──────────────────────────────────

    fun getFilterNodeIds(): List<Long> = cachedFilterNodeIds

    suspend fun saveFilterNodeIds(nodeIds: List<Long>) {
        cachedFilterNodeIds = nodeIds
        appDataStore.dataStore.edit { it[MARKET_FILTER_NODES_KEY] = gson.toJson(nodeIds) }
    }

    // ── 集市功能总开关 ──────────────────────────────────

    fun getMarketEnabled(): Boolean = cachedMarketEnabled

    suspend fun setMarketEnabled(enabled: Boolean) {
        cachedMarketEnabled = enabled
        appDataStore.dataStore.edit {
            it[MARKET_ENABLED_KEY] = if (enabled) "true" else "false"
        }
    }

    // ── 集市列表布局模式 ────────────────────────────────
    // "list" 单列 / "stagger" 小红书双列瀑布

    fun getListLayoutMode(): String = cachedMarketListLayoutMode

    suspend fun setListLayoutMode(mode: String) {
        val normalized = if (mode == "stagger") "stagger" else "list"
        cachedMarketListLayoutMode = normalized
        appDataStore.dataStore.edit { it[MARKET_LIST_LAYOUT_KEY] = normalized }
    }

    // ── 我的信息缓存 ─────────────────────────────────

    fun getStudentInfoJson(): String? = cachedStudentInfoJson

    fun getStudentInfoUpdatedAt(): Long = cachedStudentInfoUpdatedAt

    suspend fun saveStudentInfoJson(json: String, updatedAt: Long = System.currentTimeMillis()) {
        cachedStudentInfoJson = json
        cachedStudentInfoUpdatedAt = updatedAt
        appDataStore.dataStore.edit { preferences ->
            preferences[STUDENT_INFO_KEY] = json
            preferences[STUDENT_INFO_UPDATED_AT_KEY] = updatedAt
        }
    }

    suspend fun clearStudentInfoJson() {
        cachedStudentInfoJson = null
        cachedStudentInfoUpdatedAt = 0L
        appDataStore.dataStore.edit { preferences ->
            preferences.remove(STUDENT_INFO_KEY)
            preferences.remove(STUDENT_INFO_UPDATED_AT_KEY)
        }
    }

    // ── 课表缓存 ─────────────────────────────────────

    fun getScheduleJson(): String? = cachedScheduleJson

    fun getScheduleUpdatedAt(): Long = cachedScheduleUpdatedAt

    suspend fun saveScheduleJson(json: String) {
        cachedScheduleJson = json
        cachedScheduleUpdatedAt = System.currentTimeMillis()
        appDataStore.dataStore.edit { preferences ->
            preferences[SCHEDULE_JSON_KEY] = json
            preferences[SCHEDULE_UPDATED_AT_KEY] = cachedScheduleUpdatedAt
        }
    }

    suspend fun clearScheduleJson() {
        cachedScheduleJson = null
        cachedScheduleUpdatedAt = 0L
        appDataStore.dataStore.edit { preferences ->
            preferences.remove(SCHEDULE_JSON_KEY)
            preferences.remove(SCHEDULE_UPDATED_AT_KEY)
        }
    }

    // ── 成绩缓存 ─────────────────────────────────────

    fun getGradesJson(): String? = cachedGradesJson

    fun getGpaMetadataJson(): String? = cachedGpaMetadataJson

    fun getGradesUpdatedAt(): Long = cachedGradesUpdatedAt

    suspend fun saveGradesJson(json: String, gpaMetadataJson: String?) {
        cachedGradesJson = json
        cachedGpaMetadataJson = gpaMetadataJson
        cachedGradesUpdatedAt = System.currentTimeMillis()
        appDataStore.dataStore.edit { preferences ->
            preferences[GRADES_JSON_KEY] = json
            if (gpaMetadataJson != null) {
                preferences[GPA_METADATA_JSON_KEY] = gpaMetadataJson
            }
            preferences[GRADES_UPDATED_AT_KEY] = cachedGradesUpdatedAt
        }
    }

    suspend fun clearGradesJson() {
        cachedGradesJson = null
        cachedGpaMetadataJson = null
        cachedGradesUpdatedAt = 0L
        appDataStore.dataStore.edit { preferences ->
            preferences.remove(GRADES_JSON_KEY)
            preferences.remove(GPA_METADATA_JSON_KEY)
            preferences.remove(GRADES_UPDATED_AT_KEY)
        }
    }

    // ── 考试安排缓存 ─────────────────────────────────

    fun getExamsJson(): String? = cachedExamsJson

    fun getExamsUpdatedAt(): Long = cachedExamsUpdatedAt

    suspend fun saveExamsJson(json: String) {
        cachedExamsJson = json
        cachedExamsUpdatedAt = System.currentTimeMillis()
        appDataStore.dataStore.edit { preferences ->
            preferences[EXAMS_JSON_KEY] = json
            preferences[EXAMS_UPDATED_AT_KEY] = cachedExamsUpdatedAt
        }
    }

    suspend fun clearExamsJson() {
        cachedExamsJson = null
        cachedExamsUpdatedAt = 0L
        appDataStore.dataStore.edit { preferences ->
            preferences.remove(EXAMS_JSON_KEY)
            preferences.remove(EXAMS_UPDATED_AT_KEY)
        }
    }

    // ── 财务汇总缓存 ─────────────────────────────────

    fun getFinanceJson(): String? = cachedFinanceJson

    fun getFinanceUpdatedAt(): Long = cachedFinanceUpdatedAt

    suspend fun saveFinanceJson(json: String) {
        cachedFinanceJson = json
        cachedFinanceUpdatedAt = System.currentTimeMillis()
        appDataStore.dataStore.edit { preferences ->
            preferences[FINANCE_JSON_KEY] = json
            preferences[FINANCE_UPDATED_AT_KEY] = cachedFinanceUpdatedAt
        }
    }

    suspend fun clearFinanceJson() {
        cachedFinanceJson = null
        cachedFinanceUpdatedAt = 0L
        appDataStore.dataStore.edit { preferences ->
            preferences.remove(FINANCE_JSON_KEY)
            preferences.remove(FINANCE_UPDATED_AT_KEY)
        }
    }

    // ── 考勤缺勤缓存 ─────────────────────────────────

    fun getAttendanceJson(): String? = cachedAttendanceJson

    fun getAttendanceUpdatedAt(): Long = cachedAttendanceUpdatedAt

    suspend fun saveAttendanceJson(json: String) {
        cachedAttendanceJson = json
        cachedAttendanceUpdatedAt = System.currentTimeMillis()
        appDataStore.dataStore.edit { preferences ->
            preferences[ATTENDANCE_JSON_KEY] = json
            preferences[ATTENDANCE_UPDATED_AT_KEY] = cachedAttendanceUpdatedAt
        }
    }

    suspend fun clearAttendanceJson() {
        cachedAttendanceJson = null
        cachedAttendanceUpdatedAt = 0L
        appDataStore.dataStore.edit { preferences ->
            preferences.remove(ATTENDANCE_JSON_KEY)
            preferences.remove(ATTENDANCE_UPDATED_AT_KEY)
        }
    }

    // ── kqcard 考勤缓存 ──────────────────────────────

    fun getKqcardAttendanceJson(): String? = cachedKqcardAttendanceJson

    fun getKqcardAttendanceUpdatedAt(): Long = cachedKqcardAttendanceUpdatedAt

    suspend fun saveKqcardAttendanceJson(json: String) {
        cachedKqcardAttendanceJson = json
        cachedKqcardAttendanceUpdatedAt = System.currentTimeMillis()
        appDataStore.dataStore.edit { preferences ->
            preferences[KQCARD_ATTENDANCE_JSON_KEY] = json
            preferences[KQCARD_ATTENDANCE_UPDATED_AT_KEY] = cachedKqcardAttendanceUpdatedAt
        }
    }

    suspend fun clearKqcardAttendanceJson() {
        cachedKqcardAttendanceJson = null
        cachedKqcardAttendanceUpdatedAt = 0L
        appDataStore.dataStore.edit { preferences ->
            preferences.remove(KQCARD_ATTENDANCE_JSON_KEY)
            preferences.remove(KQCARD_ATTENDANCE_UPDATED_AT_KEY)
        }
    }

    // ── 用户自定义课表条目 ──────────────────────────────

    fun getUserScheduleJson(): String? = cachedUserScheduleJson

    suspend fun saveUserScheduleJson(json: String) {
        cachedUserScheduleJson = json
        appDataStore.dataStore.edit { it[USER_SCHEDULE_JSON_KEY] = json }
    }

    suspend fun clearUserScheduleJson() {
        cachedUserScheduleJson = null
        appDataStore.dataStore.edit { it.remove(USER_SCHEDULE_JSON_KEY) }
    }

    // ── 课表布局偏好 ─────────────────────────────────

    fun getScheduleColWidth(): Float = cachedScheduleColWidth

    fun getScheduleRowHeight(): Float = cachedScheduleRowHeight

    fun getScheduleFontScale(): Float = cachedScheduleFontScale

    suspend fun saveScheduleColWidth(value: Float) {
        cachedScheduleColWidth = value
        appDataStore.dataStore.edit { it[SCHEDULE_COL_WIDTH_KEY] = value.toString() }
    }

    suspend fun saveScheduleRowHeight(value: Float) {
        cachedScheduleRowHeight = value
        appDataStore.dataStore.edit { it[SCHEDULE_ROW_HEIGHT_KEY] = value.toString() }
    }

    suspend fun saveScheduleFontScale(value: Float) {
        cachedScheduleFontScale = value
        appDataStore.dataStore.edit { it[SCHEDULE_FONT_SCALE_KEY] = value.toString() }
    }

    // ── 课表显示设置 (2026-06-17) ─────────────────────

    fun getShowSat(): Boolean = cachedShowSat
    suspend fun setShowSat(value: Boolean) {
        cachedShowSat = value
        appDataStore.dataStore.edit { it[KEY_SHOW_SAT] = if (value) "true" else "false" }
    }

    fun getShowSun(): Boolean = cachedShowSun
    suspend fun setShowSun(value: Boolean) {
        cachedShowSun = value
        appDataStore.dataStore.edit { it[KEY_SHOW_SUN] = if (value) "true" else "false" }
    }

    fun getPagerEnabled(): Boolean = cachedPagerEnabled
    suspend fun setPagerEnabled(value: Boolean) {
        cachedPagerEnabled = value
        appDataStore.dataStore.edit { it[KEY_PAGER_ENABLED] = if (value) "true" else "false" }
    }

    fun getResetOnEnter(): Boolean = cachedResetOnEnter
    suspend fun setResetOnEnter(value: Boolean) {
        cachedResetOnEnter = value
        appDataStore.dataStore.edit { it[KEY_RESET_ON_ENTER] = if (value) "true" else "false" }
    }

    fun getShowCompletedTasks(): Boolean = cachedShowCompletedTasks
    suspend fun setShowCompletedTasks(value: Boolean) {
        cachedShowCompletedTasks = value
        appDataStore.dataStore.edit { it[KEY_SHOW_COMPLETED_TASKS] = if (value) "true" else "false" }
    }

    fun getShowCompletedExams(): Boolean = cachedShowCompletedExams
    suspend fun setShowCompletedExams(value: Boolean) {
        cachedShowCompletedExams = value
        appDataStore.dataStore.edit { it[KEY_SHOW_COMPLETED_EXAMS] = if (value) "true" else "false" }
    }

    // ── 考核方案缓存 ─────────────────────────────────

    fun getAssessmentJson(): String? = cachedAssessmentJson
    fun getAssessmentUpdatedAt(): Long = cachedAssessmentUpdatedAt
    suspend fun saveAssessmentJson(json: String) {
        cachedAssessmentJson = json
        cachedAssessmentUpdatedAt = System.currentTimeMillis()
        appDataStore.dataStore.edit {
            it[ASSESSMENT_JSON_KEY] = json
            it[ASSESSMENT_UPDATED_AT_KEY] = cachedAssessmentUpdatedAt
        }
    }
    suspend fun clearAssessmentJson() {
        cachedAssessmentJson = null
        cachedAssessmentUpdatedAt = 0L
        appDataStore.dataStore.edit {
            it.remove(ASSESSMENT_JSON_KEY)
            it.remove(ASSESSMENT_UPDATED_AT_KEY)
        }
    }

    // ── 记录索引缓存 (点名/签到/作业) ─────────────────

    fun getRecordIndexJson(): String? = cachedRecordIndexJson
    fun getRecordIndexUpdatedAt(): Long = cachedRecordIndexUpdatedAt
    suspend fun saveRecordIndexJson(json: String) {
        cachedRecordIndexJson = json
        cachedRecordIndexUpdatedAt = System.currentTimeMillis()
        appDataStore.dataStore.edit {
            it[RECORD_INDEX_JSON_KEY] = json
            it[RECORD_INDEX_UPDATED_AT_KEY] = cachedRecordIndexUpdatedAt
        }
    }
    suspend fun clearRecordIndexJson() {
        cachedRecordIndexJson = null
        cachedRecordIndexUpdatedAt = 0L
        appDataStore.dataStore.edit {
            it.remove(RECORD_INDEX_JSON_KEY)
            it.remove(RECORD_INDEX_UPDATED_AT_KEY)
        }
    }

    // ── 作业列表缓存 (首页近期任务) ───────────────────

    fun getHomeworkJson(): String? = cachedHomeworkJson
    fun getHomeworkUpdatedAt(): Long = cachedHomeworkUpdatedAt
    suspend fun saveHomeworkJson(json: String) {
        cachedHomeworkJson = json
        cachedHomeworkUpdatedAt = System.currentTimeMillis()
        appDataStore.dataStore.edit {
            it[HOMEWORK_JSON_KEY] = json
            it[HOMEWORK_UPDATED_AT_KEY] = cachedHomeworkUpdatedAt
        }
    }
    suspend fun clearHomeworkJson() {
        cachedHomeworkJson = null
        cachedHomeworkUpdatedAt = 0L
        appDataStore.dataStore.edit {
            it.remove(HOMEWORK_JSON_KEY)
            it.remove(HOMEWORK_UPDATED_AT_KEY)
        }
    }

    // ── 用户自定义待办缓存 (首页近期任务) ──────────────

    fun getUserTasksJson(): String? = cachedUserTasksJson
    fun getUserTasksUpdatedAt(): Long = cachedUserTasksUpdatedAt
    suspend fun saveUserTasksJson(json: String) {
        cachedUserTasksJson = json
        cachedUserTasksUpdatedAt = System.currentTimeMillis()
        appDataStore.dataStore.edit {
            it[USER_TASKS_JSON_KEY] = json
            it[USER_TASKS_UPDATED_AT_KEY] = cachedUserTasksUpdatedAt
        }
    }
    suspend fun clearUserTasksJson() {
        cachedUserTasksJson = null
        cachedUserTasksUpdatedAt = 0L
        appDataStore.dataStore.edit {
            it.remove(USER_TASKS_JSON_KEY)
            it.remove(USER_TASKS_UPDATED_AT_KEY)
        }
    }

    // ── 浴室余额手机号 ──────────────────────────────

    fun getBathroomPhone(): String? = cachedBathroomPhone

    suspend fun saveBathroomPhone(phone: String) {
        cachedBathroomPhone = phone
        appDataStore.dataStore.edit { it[BATHROOM_PHONE_KEY] = phone }
    }

    suspend fun clearBathroomPhone() {
        cachedBathroomPhone = null
        appDataStore.dataStore.edit { it.remove(BATHROOM_PHONE_KEY) }
    }

    // ── 电费房间配置 ────────────────────────────────

    fun getAcConfig(): ElectricityRoomConfig = cachedAcConfig

    fun getLightingConfig(): ElectricityRoomConfig = cachedLightingConfig

    suspend fun saveAcConfig(config: ElectricityRoomConfig) {
        cachedAcConfig = config
        appDataStore.dataStore.edit { it[AC_CONFIG_KEY] = gson.toJson(config) }
    }

    suspend fun saveLightingConfig(config: ElectricityRoomConfig) {
        cachedLightingConfig = config
        appDataStore.dataStore.edit { it[LIGHTING_CONFIG_KEY] = gson.toJson(config) }
    }

    fun getAdwmhSessionId(): String? = cachedAdwmhSessionId

    suspend fun saveAdwmhSessionId(sessionId: String) {
        cachedAdwmhSessionId = sessionId
        appDataStore.dataStore.edit { it[ADWMH_SESSION_KEY] = sessionId }
    }

    suspend fun clearAdwmhSessionId() {
        cachedAdwmhSessionId = null
        appDataStore.dataStore.edit { it.remove(ADWMH_SESSION_KEY) }
    }

    // ── 培养方案完成进度缓存 ──────────────────────────

    fun getTrainingPlanJson(): String? = cachedTrainingPlanJson

    fun getTrainingPlanUpdatedAt(): Long = cachedTrainingPlanUpdatedAt

    suspend fun saveTrainingPlanJson(json: String) {
        cachedTrainingPlanJson = json
        cachedTrainingPlanUpdatedAt = System.currentTimeMillis()
        appDataStore.dataStore.edit { preferences ->
            preferences[TRAINING_PLAN_JSON_KEY] = json
            preferences[TRAINING_PLAN_UPDATED_AT_KEY] = cachedTrainingPlanUpdatedAt
        }
    }

    fun getEmptyClassroomJson(): String? = cachedEmptyClassroomJson

    fun getEmptyClassroomKey(): String? = cachedEmptyClassroomKey

    fun getEmptyClassroomUpdatedAt(): Long = cachedEmptyClassroomUpdatedAt

    suspend fun saveEmptyClassroomJson(json: String, cacheKey: String) {
        cachedEmptyClassroomJson = json
        cachedEmptyClassroomKey = cacheKey
        cachedEmptyClassroomUpdatedAt = System.currentTimeMillis()
        appDataStore.dataStore.edit { preferences ->
            preferences[EMPTY_CLASSROOM_JSON_KEY] = json
            preferences[EMPTY_CLASSROOM_KEY_KEY] = cacheKey
            preferences[EMPTY_CLASSROOM_UPDATED_AT_KEY] = cachedEmptyClassroomUpdatedAt
        }
    }

    suspend fun clearEmptyClassroomJson() {
        cachedEmptyClassroomJson = null
        cachedEmptyClassroomKey = null
        cachedEmptyClassroomUpdatedAt = 0L
        appDataStore.dataStore.edit { preferences ->
            preferences.remove(EMPTY_CLASSROOM_JSON_KEY)
            preferences.remove(EMPTY_CLASSROOM_KEY_KEY)
            preferences.remove(EMPTY_CLASSROOM_UPDATED_AT_KEY)
        }
    }

    suspend fun clearTrainingPlanJson() {
        cachedTrainingPlanJson = null
        cachedTrainingPlanUpdatedAt = 0L
        appDataStore.dataStore.edit { preferences ->
            preferences.remove(TRAINING_PLAN_JSON_KEY)
            preferences.remove(TRAINING_PLAN_UPDATED_AT_KEY)
        }
    }

    /** 清除所有数据(session + 凭据 + JW session + 集市设置) — 用户主动退出登录时调用 */
    suspend fun clearAuthData() {
        cachedSessionId = null
        cachedJwSessionId = null
        cachedJwPstSid = null
        cachedUsername = null
        cachedPassword = null
        cachedStudentInfoJson = null
        cachedStudentInfoUpdatedAt = 0L
        cachedAdwmhSessionId = null
        appDataStore.dataStore.edit { preferences ->
            preferences.remove(SESSION_KEY)
            preferences.remove(JW_SESSION_KEY)
            preferences.remove(JW_PST_SID_KEY)
            preferences.remove(USERNAME_KEY)
            preferences.remove(PASSWORD_KEY)
            preferences.remove(STUDENT_INFO_KEY)
            preferences.remove(STUDENT_INFO_UPDATED_AT_KEY)
            preferences.remove(ADWMH_SESSION_KEY)
        }
    }

    suspend fun clearAll() {
        // 先清除内存缓存
        cachedSessionId = null
        cachedJwSessionId = null
        cachedJwPstSid = null
        cachedUsername = null
        cachedPassword = null
        cachedMarketApiIdentity = null
        cachedStudentInfoJson = null
        cachedStudentInfoUpdatedAt = 0L
        cachedMarketIdentities = emptyList()
        cachedSelectedIdentityIds = emptySet()
        cachedBlockPinned = false
        cachedBlockKeywords = emptyList()
        cachedFilterNodeIds = emptyList()
        cachedMarketEnabled = true
        cachedMarketListLayoutMode = "list"
        cachedScheduleColWidth = 64f
        cachedScheduleRowHeight = 56f
        cachedScheduleFontScale = 1.0f
        // 课表显示设置 (2026-06-17) 恢复默认值
        cachedShowSat = true
        cachedShowSun = true
        cachedPagerEnabled = true
        cachedResetOnEnter = true
        cachedShowCompletedTasks = false
        cachedShowCompletedExams = true
        // 业务数据缓存
        cachedScheduleJson = null
        cachedScheduleUpdatedAt = 0L
        cachedGradesJson = null
        cachedGpaMetadataJson = null
        cachedGradesUpdatedAt = 0L
        cachedExamsJson = null
        cachedExamsUpdatedAt = 0L
        cachedFinanceJson = null
        cachedFinanceUpdatedAt = 0L
        cachedAttendanceJson = null
        cachedAttendanceUpdatedAt = 0L
        cachedKqcardAttendanceJson = null
        cachedKqcardAttendanceUpdatedAt = 0L
        cachedUserScheduleJson = null
        cachedAssessmentJson = null
        cachedAssessmentUpdatedAt = 0L
        cachedRecordIndexJson = null
        cachedRecordIndexUpdatedAt = 0L
        cachedHomeworkJson = null
        cachedHomeworkUpdatedAt = 0L
        cachedUserTasksJson = null
        cachedUserTasksUpdatedAt = 0L
        cachedBathroomPhone = null
        cachedAcConfig = ElectricityRoomConfig()
        cachedLightingConfig = ElectricityRoomConfig()
        cachedAdwmhSessionId = null
        // 一次 edit 完成所有删除，避免多次 DataStore 序列化/写入
        appDataStore.dataStore.edit { preferences ->
            preferences.remove(SESSION_KEY)
            preferences.remove(JW_SESSION_KEY)
            preferences.remove(JW_PST_SID_KEY)
            preferences.remove(USERNAME_KEY)
            preferences.remove(PASSWORD_KEY)
            preferences.remove(MARKET_API_IDENTITY_KEY)
            preferences.remove(STUDENT_INFO_KEY)
            preferences.remove(STUDENT_INFO_UPDATED_AT_KEY)
            preferences.remove(MARKET_IDENTITIES_KEY)
            preferences.remove(MARKET_SELECTED_IDS_KEY)
            preferences.remove(MARKET_BLOCK_PINNED_KEY)
            preferences.remove(MARKET_BLOCK_KEYWORDS_KEY)
            preferences.remove(MARKET_FILTER_NODES_KEY)
            preferences.remove(MARKET_ENABLED_KEY)
            preferences.remove(MARKET_LIST_LAYOUT_KEY)
            preferences.remove(SCHEDULE_COL_WIDTH_KEY)
            preferences.remove(SCHEDULE_ROW_HEIGHT_KEY)
            preferences.remove(SCHEDULE_FONT_SCALE_KEY)
            preferences.remove(KEY_SHOW_SAT)
            preferences.remove(KEY_SHOW_SUN)
            preferences.remove(KEY_PAGER_ENABLED)
            preferences.remove(KEY_RESET_ON_ENTER)
            preferences.remove(KEY_SHOW_COMPLETED_TASKS)
            preferences.remove(KEY_SHOW_COMPLETED_EXAMS)
            preferences.remove(SCHEDULE_JSON_KEY)
            preferences.remove(SCHEDULE_UPDATED_AT_KEY)
            preferences.remove(GRADES_JSON_KEY)
            preferences.remove(GPA_METADATA_JSON_KEY)
            preferences.remove(GRADES_UPDATED_AT_KEY)
            preferences.remove(EXAMS_JSON_KEY)
            preferences.remove(EXAMS_UPDATED_AT_KEY)
            preferences.remove(FINANCE_JSON_KEY)
            preferences.remove(FINANCE_UPDATED_AT_KEY)
            preferences.remove(ATTENDANCE_JSON_KEY)
            preferences.remove(ATTENDANCE_UPDATED_AT_KEY)
            preferences.remove(KQCARD_ATTENDANCE_JSON_KEY)
            preferences.remove(KQCARD_ATTENDANCE_UPDATED_AT_KEY)
            preferences.remove(USER_SCHEDULE_JSON_KEY)
            preferences.remove(ASSESSMENT_JSON_KEY)
            preferences.remove(ASSESSMENT_UPDATED_AT_KEY)
            preferences.remove(RECORD_INDEX_JSON_KEY)
            preferences.remove(RECORD_INDEX_UPDATED_AT_KEY)
            preferences.remove(HOMEWORK_JSON_KEY)
            preferences.remove(HOMEWORK_UPDATED_AT_KEY)
            preferences.remove(USER_TASKS_JSON_KEY)
            preferences.remove(USER_TASKS_UPDATED_AT_KEY)
            preferences.remove(BATHROOM_PHONE_KEY)
            preferences.remove(AC_CONFIG_KEY)
            preferences.remove(LIGHTING_CONFIG_KEY)
            preferences.remove(ADWMH_SESSION_KEY)
        }
        Log.i(TAG, "所有会话和凭据已清除")
    }

    private val SESSION_KEY = stringPreferencesKey("jsessionid")
    private val JW_SESSION_KEY = stringPreferencesKey("jw_session_id")
    private val JW_PST_SID_KEY = stringPreferencesKey("jw_pst_sid")
    private val USERNAME_KEY = stringPreferencesKey("username")
    private val PASSWORD_KEY = stringPreferencesKey("password")
    private val MARKET_API_IDENTITY_KEY = stringPreferencesKey("market_api_identity")
    private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
    private val STUDENT_INFO_KEY = stringPreferencesKey("student_info_json")
    private val STUDENT_INFO_UPDATED_AT_KEY = longPreferencesKey("student_info_updated_at")

    // 集市设置新 key
    private val MARKET_IDENTITIES_KEY = stringPreferencesKey("market_identities")
    private val MARKET_SELECTED_IDS_KEY = stringPreferencesKey("market_selected_ids")
    private val MARKET_BLOCK_PINNED_KEY = stringPreferencesKey("market_block_pinned")
    private val MARKET_BLOCK_KEYWORDS_KEY = stringPreferencesKey("market_block_keywords")
    private val MARKET_FILTER_NODES_KEY = stringPreferencesKey("market_filter_nodes")
    private val MARKET_ENABLED_KEY = stringPreferencesKey("market_enabled")
    private val MARKET_LIST_LAYOUT_KEY = stringPreferencesKey("market_list_layout_mode")
    private val SCHEDULE_COL_WIDTH_KEY = stringPreferencesKey("schedule_col_width")
    private val SCHEDULE_ROW_HEIGHT_KEY = stringPreferencesKey("schedule_row_height")
    private val SCHEDULE_FONT_SCALE_KEY = stringPreferencesKey("schedule_font_scale")
    // 课表显示设置 (2026-06-17)
    private val KEY_SHOW_SAT = stringPreferencesKey("schedule_show_sat")
    private val KEY_SHOW_SUN = stringPreferencesKey("schedule_show_sun")
    private val KEY_PAGER_ENABLED = stringPreferencesKey("schedule_pager_enabled")
    private val KEY_RESET_ON_ENTER = stringPreferencesKey("schedule_reset_on_enter")
    private val KEY_SHOW_COMPLETED_TASKS = stringPreferencesKey("schedule_show_completed_tasks")
    private val KEY_SHOW_COMPLETED_EXAMS = stringPreferencesKey("schedule_show_completed_exams")
    // 业务数据缓存 key
    private val SCHEDULE_JSON_KEY = stringPreferencesKey("schedule_json")
    private val SCHEDULE_UPDATED_AT_KEY = longPreferencesKey("schedule_updated_at")
    private val GRADES_JSON_KEY = stringPreferencesKey("grades_json")
    private val GPA_METADATA_JSON_KEY = stringPreferencesKey("gpa_metadata_json")
    private val GRADES_UPDATED_AT_KEY = longPreferencesKey("grades_updated_at")
    private val EXAMS_JSON_KEY = stringPreferencesKey("exams_json")
    private val EXAMS_UPDATED_AT_KEY = longPreferencesKey("exams_updated_at")
    private val FINANCE_JSON_KEY = stringPreferencesKey("finance_json")
    private val FINANCE_UPDATED_AT_KEY = longPreferencesKey("finance_updated_at")
    private val ATTENDANCE_JSON_KEY = stringPreferencesKey("attendance_json")
    private val ATTENDANCE_UPDATED_AT_KEY = longPreferencesKey("attendance_updated_at")
    private val KQCARD_ATTENDANCE_JSON_KEY = stringPreferencesKey("kqcard_attendance_json")
    private val KQCARD_ATTENDANCE_UPDATED_AT_KEY = longPreferencesKey("kqcard_attendance_updated_at")
    private val USER_SCHEDULE_JSON_KEY = stringPreferencesKey("user_schedule_json")
    private val ASSESSMENT_JSON_KEY = stringPreferencesKey("assessment_json")
    private val ASSESSMENT_UPDATED_AT_KEY = longPreferencesKey("assessment_updated_at")
    private val RECORD_INDEX_JSON_KEY = stringPreferencesKey("record_index_json")
    private val RECORD_INDEX_UPDATED_AT_KEY = longPreferencesKey("record_index_updated_at")
    private val HOMEWORK_JSON_KEY = stringPreferencesKey("homework_json")
    private val HOMEWORK_UPDATED_AT_KEY = longPreferencesKey("homework_updated_at")
    private val USER_TASKS_JSON_KEY = stringPreferencesKey("user_tasks_json")
    private val USER_TASKS_UPDATED_AT_KEY = longPreferencesKey("user_tasks_updated_at")
    private val BATHROOM_PHONE_KEY = stringPreferencesKey("bathroom_phone")
    private val AC_CONFIG_KEY = stringPreferencesKey("ac_config")
    private val LIGHTING_CONFIG_KEY = stringPreferencesKey("lighting_config")
    private val ADWMH_SESSION_KEY = stringPreferencesKey("adwmh_jsessionid")
    private val TRAINING_PLAN_JSON_KEY = stringPreferencesKey("training_plan_json")
    private val TRAINING_PLAN_UPDATED_AT_KEY = longPreferencesKey("training_plan_updated_at")
    private val EMPTY_CLASSROOM_JSON_KEY = stringPreferencesKey("empty_classroom_json")
    private val EMPTY_CLASSROOM_KEY_KEY = stringPreferencesKey("empty_classroom_key")
    private val EMPTY_CLASSROOM_UPDATED_AT_KEY = longPreferencesKey("empty_classroom_updated_at")

    // ── JSON 解析辅助 ──────────────────────────────────

    private val gson = GsonProvider.instance

    private fun parseIdentityList(json: String?): List<MarketIdentity> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            gson.fromJson(json, Array<MarketIdentity>::class.java).toList()
        }.getOrDefault(emptyList())
    }

    private fun parseStringList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            gson.fromJson(json, Array<String>::class.java).toList()
        }.getOrDefault(emptyList())
    }

    private fun parseLongList(json: String?): List<Long> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            gson.fromJson(json, Array<Long>::class.java).toList()
        }.getOrDefault(emptyList())
    }

    private fun parseElectricityConfig(json: String?): ElectricityRoomConfig {
        if (json.isNullOrBlank()) return ElectricityRoomConfig()
        return runCatching {
            gson.fromJson(json, ElectricityRoomConfig::class.java)
        }.getOrDefault(ElectricityRoomConfig())
    }

    private companion object {
        const val TAG = "SessionManager"
    }
}

/**
 * 电费房间配置 (空调/照明)。
 * building/floor/room 均为 "code&name" 格式,如 "57&榴园二号楼空调"。
 */
data class ElectricityRoomConfig(
    val building: String = "",
    val floor: String = "",
    val room: String = ""
) {
    val isComplete: Boolean
        get() = listOf(building, floor, room).all { value ->
            val parts = value.split("&", limit = 2)
            parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()
        }
}
