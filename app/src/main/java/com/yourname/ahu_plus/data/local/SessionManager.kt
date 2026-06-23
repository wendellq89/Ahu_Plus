package com.yourname.ahu_plus.data.local











import android.util.Log





import androidx.datastore.preferences.core.edit





import androidx.datastore.preferences.core.longPreferencesKey





import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey





import com.google.gson.reflect.TypeToken





import com.yourname.ahu_plus.data.GsonProvider





import com.yourname.ahu_plus.data.model.MarketIdentity





import com.yourname.ahu_plus.data.model.AiCommentModel





import com.yourname.ahu_plus.data.model.AiCommentStyle





import com.yourname.ahu_plus.data.model.AiCommentPrompts





import com.yourname.ahu_plus.data.model.AiCommentTemplate





import com.yourname.ahu_plus.data.model.defaultAiCommentTemplates





import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first





import kotlinx.coroutines.flow.map











/**





 * \u4F1A\u8BDD\u4E0E\u51ED\u636E\u5B58\u50A8\u3002





 *





 * \u6301\u4E45\u5316\u7B56\u7565(\u5199\u6B7B\u4E00\u70B9,\u4F18\u5148\u7A33\u5B9A\u6027):





 *  - \u6240\u6709\u6570\u636E\u7EDF\u4E00\u5B58\u5230 DataStore Preferences (\u8FDB\u7A0B\u7EA7\u5355\u4F8B [AppDataStore])





 *  - JSESSIONID / JW SESSION \u660E\u6587\u5B58\u50A8





 *





 * \u5907\u6CE8:\u8BFE\u7A0B\u5907\u6CE8\u7531 [AppDataStore] \u72EC\u7ACB\u66B4\u9732\u7684 noteFlow/saveNote \u7B49\u65B9\u6CD5\u7BA1\u7406,





 *       SessionManager \u4E0D\u518D\u627F\u62C5\u3002





 */





class SessionManager(private val appDataStore: AppDataStore) {











    // \u2500\u2500 \u4E91\u7AEF\u5907\u4EFD\uFF08\u5EF6\u8FDF\u6CE8\u5165\uFF0C\u907F\u514D\u5FAA\u73AF\u4F9D\u8D56\uFF09 \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500





    @Volatile private var backupManager: com.yourname.ahu_plus.data.repository.CloudBackupManager? = null
    private val backupScope = CoroutineScope(Dispatchers.IO)



    /** 首次登录初始化是否已完成 */
    @Volatile var firstLoginInitDone: Boolean = false

    /** 后台学习时是否显示悬浮窗 */
    @Volatile var showStudyOverlay: Boolean = true











    fun setBackupManager(bm: com.yourname.ahu_plus.data.repository.CloudBackupManager) {





        backupManager = bm





    }











    /**
     * 登录成功后触发云端备份（受每日一次节流控制）。
     * 由 LoginViewModel / AutoLoginViewModel / ChaoxingViewModel 调用。
     * 全程静默，不通知用户。
     */
    fun notifyBackupOnLogin() {
        backupManager?.let { mgr ->
            backupScope.launch { mgr.backupOnLogin() }
        }
    }
















    @Volatile private var initialized = false





    @Volatile private var cachedSessionId: String? = null





    @Volatile private var cachedJwSessionId: String? = null





    @Volatile private var cachedJwPstSid: String? = null





    @Volatile private var cachedUsername: String? = null





    @Volatile private var cachedPassword: String? = null





    @Volatile private var cachedMarketApiIdentity: String? = null





    @Volatile private var cachedThemeMode: AppThemeMode = AppThemeMode.SYSTEM





    @Volatile private var cachedStudentInfoJson: String? = null





    @Volatile private var cachedStudentInfoUpdatedAt: Long = 0L











    // \u2500\u2500 \u96C6\u5E02\u8BBE\u7F6E \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500





    @Volatile private var cachedMarketIdentities: List<MarketIdentity> = emptyList()





    @Volatile private var cachedSelectedIdentityIds: Set<String> = emptySet()





    @Volatile private var cachedBlockPinned: Boolean = false





    @Volatile private var cachedBlockKeywords: List<String> = emptyList()





    @Volatile private var cachedFilterNodeIds: List<Long> = emptyList()





    // \u96C6\u5E02\u529F\u80FD\u603B\u5F00\u5173 (true = \u542F\u7528\uFF0C\u5E95\u90E8\u5BFC\u822A\u663E\u793A 3 \u9879\uFF1Bfalse = \u7981\u7528\uFF0C\u4EC5 2 \u9879)





    @Volatile private var cachedMarketEnabled: Boolean = false





    // \u96C6\u5E02\u5217\u8868\u5E03\u5C40\u6A21\u5F0F ("list" \u5355\u5217 / "stagger" \u5C0F\u7EA2\u4E66\u53CC\u5217\u7011\u5E03)





    @Volatile private var cachedMarketListLayoutMode: String = "list"





    // \u96C6\u5E02\u5217\u8868"\u56DE\u5230\u9876\u90E8"\u6309\u94AE





    @Volatile private var cachedMarketScrollToTop: Boolean = true





    @Volatile private var cachedAiCommentEnabled: Boolean = false





    @Volatile private var cachedAiCommentModel: AiCommentModel = AiCommentModel.FLASH





    @Volatile private var cachedAiCommentStyle: AiCommentStyle = AiCommentStyle.GENTLE





    @Volatile private var cachedAiOverallPrompt: String = AiCommentPrompts.defaultOverallPrompt





    @Volatile private var cachedAiStylePrompts: Map<String, String> = defaultAiStylePrompts()





    @Volatile private var cachedAiTemplates: List<AiCommentTemplate> = defaultAiCommentTemplates()





    @Volatile private var cachedAiSelectedTemplateId: String = AiCommentStyle.GENTLE.name





    // \u9996\u9875"\u6700\u8FD1\u4F7F\u7528"\u5E94\u7528\u8FFD\u8E2A (\u9017\u53F7\u5206\u9694\u7684 app key \u5217\u8868, \u6700\u591A 5 \u4E2A)





    @Volatile private var cachedRecentApps: String = ""





    @Volatile private var cachedScheduleColWidth: Float = 64f





    @Volatile private var cachedScheduleRowHeight: Float = 56f





    @Volatile private var cachedScheduleFontScale: Float = 1.0f











    // \u8BFE\u8868\u663E\u793A\u8BBE\u7F6E (2026-06-17 \u8BFE\u8868\u91CD\u6784)





    @Volatile private var cachedShowSat: Boolean = true





    @Volatile private var cachedShowSun: Boolean = true





    @Volatile private var cachedPagerEnabled: Boolean = true





    @Volatile private var cachedResetOnEnter: Boolean = true





    @Volatile private var cachedShowCompletedTasks: Boolean = false





    @Volatile private var cachedShowCompletedExams: Boolean = false











    // \u2500\u2500 \u4E1A\u52A1\u6570\u636E\u7F13\u5B58 \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500





    @Volatile private var cachedScheduleJson: String? = null





    @Volatile private var cachedScheduleUpdatedAt: Long = 0L





    @Volatile private var cachedGradesJson: String? = null





    @Volatile private var cachedGpaMetadataJson: String? = null





    @Volatile private var cachedGradesUpdatedAt: Long = 0L





    @Volatile private var cachedExamsJson: String? = null





    @Volatile private var cachedExamsUpdatedAt: Long = 0L





    @Volatile private var cachedFinanceJson: String? = null





    @Volatile private var cachedFinanceUpdatedAt: Long = 0L





    @Volatile private var cachedAttendanceJson: String? = null





    @Volatile private var cachedAttendanceUpdatedAt: Long = 0L





    @Volatile private var cachedKqcardAttendanceJson: String? = null





    @Volatile private var cachedKqcardAttendanceUpdatedAt: Long = 0L





    @Volatile private var cachedUserScheduleJson: String? = null





    @Volatile private var cachedAssessmentJson: String? = null





    @Volatile private var cachedAssessmentUpdatedAt: Long = 0L





    @Volatile private var cachedRecordIndexJson: String? = null





    @Volatile private var cachedRecordIndexUpdatedAt: Long = 0L





    @Volatile private var cachedHomeworkJson: String? = null





    @Volatile private var cachedHomeworkUpdatedAt: Long = 0L





    @Volatile private var cachedUserTasksJson: String? = null





    @Volatile private var cachedUserTasksUpdatedAt: Long = 0L





    @Volatile private var cachedBathroomPhone: String? = null





    @Volatile private var cachedBillsJson: String? = null





    @Volatile private var cachedBillsUpdatedAt: Long = 0L





    @Volatile private var cachedAcConfig: ElectricityRoomConfig = ElectricityRoomConfig()





    @Volatile private var cachedLightingConfig: ElectricityRoomConfig = ElectricityRoomConfig()





    @Volatile private var cachedNewCampusConfig: ElectricityRoomConfig = ElectricityRoomConfig()





    @Volatile private var cachedAdwmhSessionId: String? = null
    /**
     * 排考预测 Gitee JSON 缓存:
     * 由 [com.yourname.ahu_plus.data.repository.ExamDataRepository]
     * 从 Gitee `yao-enqi/ahu-plus-update` 仓库的
     * `exam_predictions/exam_predictions.json` 拉取后写入。
     * 客户端读取时解析 meta + 与本地课表 courseCode 精确匹配。
     */
    @Volatile private var cachedExamPredictionsJson: String? = null





    @Volatile private var cachedQrBrightnessBoost: Boolean = false





    @Volatile private var cachedAdwmhConcurrentRetry: Boolean = true





    @Volatile private var cachedTrainingPlanJson: String? = null





    @Volatile private var cachedTrainingPlanUpdatedAt: Long = 0L





    @Volatile private var cachedTrainingPlanCacheVersion: Long = 0L





    @Volatile private var cachedEmptyClassroomJson: String? = null





    @Volatile private var cachedEmptyClassroomKey: String? = null





    @Volatile private var cachedEmptyClassroomUpdatedAt: Long = 0L











    // \u2500\u2500 \u8D85\u661F\u5B66\u4E60\u901A \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500





    @Volatile private var cachedCxCookies: String? = null

    @Volatile private var cachedCxPhone: String? = null
    @Volatile private var cachedCxPassword: String? = null




    @Volatile private var cachedCxCoursesJson: String? = null


    @Volatile private var cachedCxCoursesProgressJson: String? = null




    @Volatile private var cachedCxHomeworkJson: String? = null




    @Volatile private var cachedCxHomeworkDetailJson: String? = null




    @Volatile private var cachedCxVisitBrushEnabled: String = "false"
    @Volatile private var cachedCxVisitBrushInterval: String = "30"




    @Volatile private var cachedCxDownloadEnabled: String = "false"




    @Volatile private var cachedCxHideEndedCourses: String = "true"
    @Volatile private var cachedCxHiddenCourses: String = ""





    @Volatile private var cachedCxTikuConfig: String? = null





    @Volatile private var cachedCxSpeed: String = "1.0"





    @Volatile private var cachedCxConcurrency: String = "4"





    @Volatile private var cachedCxNotopenAction: String = "retry"





    @Volatile private var cachedCxAutoSign: String = "false"





    @Volatile private var cachedCxSignLat: String = "-1.0"





    @Volatile private var cachedCxSignLon: String = "-1.0"





    @Volatile private var cachedCxSignAddress: String = ""





    @Volatile private var cachedCxSignGesture: String = ""





    @Volatile private var cachedCxProviderChain: String = "CACHE"





    @Volatile private var cachedCxTokensYanxi: String = ""





    @Volatile private var cachedCxCoverRate: String = "0.8"





    @Volatile private var cachedCxTikuDelay: String = "1.0"





    @Volatile private var cachedCxAiMinInterval: String = "3"





    @Volatile private var cachedCxAiHttpProxy: String = ""





    @Volatile private var cachedCxSiliconflowKey: String = ""





    @Volatile private var cachedCxSiliconflowModel: String = "deepseek-ai/DeepSeek-R1"





    @Volatile private var cachedCxSiliconflowEndpoint: String = "https://api.siliconflow.cn/v1/chat/completions"





    @Volatile private var cachedCxLikeapiSearch: String = "false"





    @Volatile private var cachedCxLikeapiVision: String = "true"





    @Volatile private var cachedCxLikeapiModel: String = "glm-4.5-air"





    @Volatile private var cachedCxGoAuthorization: String = ""





    @Volatile private var cachedCxGoMinInterval: String = "1.0"





    @Volatile private var cachedCxTikuAdapterUrl: String = ""





    @Volatile private var cachedCxNotifyProvider: String = ""





    @Volatile private var cachedCxNotifyUrl: String = ""





    @Volatile private var cachedCxNotifyTgChatId: String = ""





    @Volatile private var cachedCxSubmitMode: String = "auto"





    @Volatile private var cachedCxTikuType: String = "CACHE"





    @Volatile private var cachedCxTikuToken: String = ""





    @Volatile private var cachedCxAiKey: String = ""





    @Volatile private var cachedCxAiBaseUrl: String = ""





    @Volatile private var cachedCxAiModel: String = ""





    @Volatile private var cachedCxTaskTypes: String = "video,document,read,workid"





    @Volatile private var cachedCxMessagesJson: String? = null





    @Volatile private var cachedCxMessagesCursor: String = ""





    @Volatile private var cachedCxMessagesMerge: String = "false"











    // \u2500\u2500 \u81EA\u52A8\u66F4\u65B0\u76F8\u5173 \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500





    @Volatile private var cachedIgnoredVersionCode: Int = 0











    val themeModeFlow = appDataStore.dataStore.data.map { preferences ->





        AppThemeMode.fromStorageValue(preferences[THEME_MODE_KEY])





    }











    /**





     * \u4ECE DataStore \u6062\u590D\u6240\u6709\u6570\u636E\u5230\u5185\u5B58\u7F13\u5B58\u3002





     * \u53EA\u5728 App \u542F\u52A8\u65F6\u8C03\u7528\u4E00\u6B21\u3002





     */





    suspend fun init(): String? {





        if (initialized) return cachedSessionId





        val prefs = appDataStore.dataStore.data.first()





        cachedSessionId = prefs[SESSION_KEY]





        cachedJwSessionId = prefs[JW_SESSION_KEY]





        cachedJwPstSid = prefs[JW_PST_SID_KEY]





        cachedUsername = prefs[USERNAME_KEY]





        cachedPassword = prefs[PASSWORD_KEY]





        cachedThemeMode = AppThemeMode.fromStorageValue(prefs[THEME_MODE_KEY])





        cachedStudentInfoJson = prefs[STUDENT_INFO_KEY]





        cachedStudentInfoUpdatedAt = prefs[STUDENT_INFO_UPDATED_AT_KEY] ?: 0L











        // \u2500\u2500 \u96C6\u5E02\u8BBE\u7F6E\uFF1A\u8FC1\u79FB\u65E7\u5355\u8EAB\u4EFD \u2192 \u65B0\u591A\u8EAB\u4EFD\u683C\u5F0F \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500





        val oldIdentity = prefs[MARKET_API_IDENTITY_KEY]





        val newIdentitiesJson = prefs[MARKET_IDENTITIES_KEY]





        if (!oldIdentity.isNullOrBlank() && newIdentitiesJson.isNullOrBlank()) {





            // \u65E7\u683C\u5F0F \u2192 \u65B0\u683C\u5F0F \u4E00\u6B21\u6027\u8FC1\u79FB





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





        // \u96C6\u5E02\u529F\u80FD\u603B\u5F00\u5173: \u7F3A\u7701 true,\u4FDD\u6301\u5411\u540E\u517C\u5BB9 (\u8001\u7528\u6237\u9ED8\u8BA4\u542F\u7528)





        cachedMarketEnabled = (prefs[MARKET_ENABLED_KEY] ?: "false") == "true"





        // \u5217\u8868\u5E03\u5C40\u6A21\u5F0F: \u7F3A\u7701 "list" (\u5355\u5217)





        cachedMarketListLayoutMode = prefs[MARKET_LIST_LAYOUT_KEY] ?: "list"





        cachedMarketScrollToTop = (prefs[MARKET_SCROLL_TO_TOP_KEY] ?: "true") == "true"





        cachedAiCommentEnabled = prefs[AI_COMMENT_ENABLED_KEY] == "true"





        cachedAiCommentModel = AiCommentModel.fromStorage(prefs[AI_COMMENT_MODEL_KEY])





        cachedAiCommentStyle = AiCommentStyle.fromStorage(prefs[AI_COMMENT_STYLE_KEY])





        cachedAiOverallPrompt = prefs[AI_COMMENT_OVERALL_PROMPT_KEY]





            ?.takeIf { it.isNotBlank() }





            ?: AiCommentPrompts.defaultOverallPrompt





        cachedAiStylePrompts = parseAiStylePrompts(prefs[AI_COMMENT_STYLE_PROMPTS_KEY])





        cachedAiTemplates = parseAiTemplates(prefs[AI_COMMENT_TEMPLATES_KEY])





        cachedAiSelectedTemplateId = (prefs[AI_COMMENT_SELECTED_TEMPLATE_KEY]





            ?: cachedAiCommentStyle.name).takeIf { id -> cachedAiTemplates.any { it.id == id } }





            ?: cachedAiTemplates.first().id





        cachedRecentApps = prefs[RECENT_APPS_KEY] ?: ""











        // \u8BFE\u8868\u5E03\u5C40\u504F\u597D\uFF08\u5E26\u9ED8\u8BA4\u503C\u5BB9\u9519\uFF09





        cachedScheduleColWidth = prefs[SCHEDULE_COL_WIDTH_KEY]?.toFloatOrNull() ?: 64f





        cachedScheduleRowHeight = prefs[SCHEDULE_ROW_HEIGHT_KEY]?.toFloatOrNull() ?: 56f





        cachedScheduleFontScale = prefs[SCHEDULE_FONT_SCALE_KEY]?.toFloatOrNull() ?: 1.0f





        // \u8BFE\u8868\u663E\u793A\u8BBE\u7F6E (2026-06-17)





        cachedShowSat = (prefs[KEY_SHOW_SAT] ?: "true") == "true"





        cachedShowSun = (prefs[KEY_SHOW_SUN] ?: "true") == "true"





        cachedPagerEnabled = (prefs[KEY_PAGER_ENABLED] ?: "true") == "true"





        cachedResetOnEnter = (prefs[KEY_RESET_ON_ENTER] ?: "true") == "true"





        cachedShowCompletedTasks = (prefs[KEY_SHOW_COMPLETED_TASKS] ?: "false") == "true"





        cachedShowCompletedExams = (prefs[KEY_SHOW_COMPLETED_EXAMS] ?: "false") == "true"











        // \u2500\u2500 \u4E1A\u52A1\u6570\u636E\u7F13\u5B58\u6062\u590D \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500





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





        cachedBillsJson = prefs[BILLS_JSON_KEY]





        cachedBillsUpdatedAt = prefs[BILLS_UPDATED_AT_KEY] ?: 0L





        cachedAcConfig = parseElectricityConfig(prefs[AC_CONFIG_KEY])





        cachedLightingConfig = parseElectricityConfig(prefs[LIGHTING_CONFIG_KEY])





        cachedNewCampusConfig = parseElectricityConfig(prefs[NEW_CAMPUS_CONFIG_KEY])





        cachedAdwmhSessionId = prefs[ADWMH_SESSION_KEY]

        cachedExamPredictionsJson = prefs[EXAM_PREDICTIONS_JSON_KEY]





        cachedQrBrightnessBoost = prefs[QR_BRIGHTNESS_BOOST_KEY] ?: false





        cachedAdwmhConcurrentRetry = prefs[ADWMH_CONCURRENT_RETRY_KEY] ?: true





        cachedTrainingPlanJson = prefs[TRAINING_PLAN_JSON_KEY]





        cachedTrainingPlanUpdatedAt = prefs[TRAINING_PLAN_UPDATED_AT_KEY] ?: 0L





        cachedTrainingPlanCacheVersion = prefs[TRAINING_PLAN_CACHE_VERSION_KEY] ?: 0L





        cachedEmptyClassroomJson = prefs[EMPTY_CLASSROOM_JSON_KEY]





        cachedEmptyClassroomKey = prefs[EMPTY_CLASSROOM_KEY_KEY]





        cachedEmptyClassroomUpdatedAt = prefs[EMPTY_CLASSROOM_UPDATED_AT_KEY] ?: 0L











        // \u8D85\u661F\u5B66\u4E60\u901A





        cachedCxCookies = prefs[CX_COOKIES_KEY]

        cachedCxPhone = prefs[CX_PHONE_KEY]
        cachedCxPassword = prefs[CX_PASSWORD_KEY]

        cachedCxCoursesJson = prefs[CX_COURSES_JSON_KEY]
        cachedCxCoursesProgressJson = prefs[CX_COURSES_PROGRESS_JSON_KEY]
        cachedCxHomeworkJson = prefs[CX_HOMEWORK_JSON_KEY]
        cachedCxHomeworkDetailJson = prefs[CX_HOMEWORK_DETAIL_JSON_KEY]
        cachedCxVisitBrushEnabled = prefs[CX_VISIT_BRUSH_ENABLED_KEY] ?: "false"
        cachedCxVisitBrushInterval = prefs[CX_VISIT_BRUSH_INTERVAL_KEY] ?: "30"
        cachedCxDownloadEnabled = prefs[CX_DOWNLOAD_ENABLED_KEY] ?: "false"
        cachedCxHideEndedCourses = prefs[CX_HIDE_ENDED_COURSES_KEY] ?: "true"
        cachedCxHiddenCourses = prefs[CX_HIDDEN_COURSES_KEY] ?: ""





        cachedCxTikuConfig = prefs[CX_TIKU_CONFIG_KEY]





        cachedCxSpeed = prefs[CX_SPEED_KEY] ?: "1.0"





        cachedCxConcurrency = prefs[CX_CONCURRENCY_KEY] ?: "4"





        cachedCxNotopenAction = prefs[CX_NOTOPEN_ACTION_KEY] ?: "retry"





        cachedCxAutoSign = prefs[CX_AUTO_SIGN_KEY] ?: "false"





        cachedCxSignLat = prefs[CX_SIGN_LAT_KEY] ?: "-1.0"





        cachedCxSignLon = prefs[CX_SIGN_LON_KEY] ?: "-1.0"





        cachedCxSignAddress = prefs[CX_SIGN_ADDRESS_KEY] ?: ""





        cachedCxSignGesture = prefs[CX_SIGN_GESTURE_KEY] ?: ""





        cachedCxProviderChain = prefs[CX_PROVIDER_CHAIN_KEY] ?: "CACHE"





        cachedCxTokensYanxi = prefs[CX_TOKENS_YANXI_KEY] ?: ""





        cachedCxCoverRate = prefs[CX_COVER_RATE_KEY] ?: "0.8"





        cachedCxTikuDelay = prefs[CX_TIKU_DELAY_KEY] ?: "1.0"





        cachedCxAiMinInterval = prefs[CX_AI_MIN_INTERVAL_KEY] ?: "3"





        cachedCxAiHttpProxy = prefs[CX_AI_HTTP_PROXY_KEY] ?: ""





        cachedCxSiliconflowKey = prefs[CX_SILICONFLOW_KEY_KEY] ?: ""





        cachedCxSiliconflowModel = prefs[CX_SILICONFLOW_MODEL_KEY] ?: "deepseek-ai/DeepSeek-R1"





        cachedCxSiliconflowEndpoint = prefs[CX_SILICONFLOW_ENDPOINT_KEY] ?: "https://api.siliconflow.cn/v1/chat/completions"





        cachedCxLikeapiSearch = prefs[CX_LIKEAPI_SEARCH_KEY] ?: "false"





        cachedCxLikeapiVision = prefs[CX_LIKEAPI_VISION_KEY] ?: "true"





        cachedCxLikeapiModel = prefs[CX_LIKEAPI_MODEL_KEY] ?: "glm-4.5-air"





        cachedCxGoAuthorization = prefs[CX_GO_AUTHORIZATION_KEY] ?: ""





        cachedCxGoMinInterval = prefs[CX_GO_MIN_INTERVAL_KEY] ?: "1.0"





        cachedCxTikuAdapterUrl = prefs[CX_TIKU_ADAPTER_URL_KEY] ?: ""





        cachedCxNotifyProvider = prefs[CX_NOTIFY_PROVIDER_KEY] ?: ""





        cachedCxNotifyUrl = prefs[CX_NOTIFY_URL_KEY] ?: ""





        cachedCxNotifyTgChatId = prefs[CX_NOTIFY_TG_CHAT_ID_KEY] ?: ""





        cachedCxSubmitMode = prefs[CX_SUBMIT_MODE_KEY] ?: "auto"





        cachedCxTikuType = prefs[CX_TIKU_TYPE_KEY] ?: "CACHE"





        cachedCxTikuToken = prefs[CX_TIKU_TOKEN_KEY] ?: ""





        cachedCxAiKey = prefs[CX_AI_KEY_KEY] ?: ""





        cachedCxAiBaseUrl = prefs[CX_AI_BASE_URL_KEY] ?: ""





        cachedCxAiModel = prefs[CX_AI_MODEL_KEY] ?: ""





        cachedCxTaskTypes = prefs[CX_TASK_TYPES_KEY] ?: "video,document,read,workid"





        cachedCxMessagesJson = prefs[CX_MESSAGES_JSON_KEY]





        cachedCxMessagesCursor = prefs[CX_MESSAGES_CURSOR_KEY] ?: ""





        cachedCxMessagesMerge = prefs[CX_MESSAGES_MERGE_KEY] ?: "false"











        // \u81EA\u52A8\u66F4\u65B0





        cachedIgnoredVersionCode = prefs[IGNORED_VERSION_CODE_KEY]?.toIntOrNull() ?: 0











        initialized = true





        Log.i(





            TAG, "init done: session=${cachedSessionId != null} " +





                "jw=${cachedJwSessionId != null} user=${cachedUsername != null} " +





                "identities=${cachedMarketIdentities.size}"





        )





        return cachedSessionId





    }











    /** \u540C\u6B65\u4ECE\u5F53\u524D\u7F13\u5B58\u8BFB\u53D6(\u4F9B UI \u5728 init \u5B8C\u6210\u524D/\u4E2D\u5B89\u5168\u8BBF\u95EE) */





    fun isInitialized(): Boolean = initialized











    // \u2500\u2500 \u5916\u89C2\u8BBE\u7F6E \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500











    fun getThemeMode(): AppThemeMode = cachedThemeMode











    suspend fun saveThemeMode(themeMode: AppThemeMode) {





        cachedThemeMode = themeMode





        appDataStore.dataStore.edit { preferences ->





            preferences[THEME_MODE_KEY] = themeMode.storageValue





        }





    }











    // \u2500\u2500 \u4E00\u5361\u901A session (JSESSIONID) \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500











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











    // \u2500\u2500 \u6559\u52A1\u5904 session \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500











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











    // \u2500\u2500 \u51ED\u636E \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500











    suspend fun saveCredentials(username: String, password: String) {





        cachedUsername = username





        cachedPassword = password





        appDataStore.dataStore.edit { preferences ->





            preferences[USERNAME_KEY] = username





            preferences[PASSWORD_KEY] = password





        }





        Log.i(TAG, "\u51ED\u636E\u5DF2\u4FDD\u5B58")





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











    // \u2500\u2500 \u96C6\u5E02 API \u8EAB\u4EFD\u5B57\u6BB5\uFF08\u517C\u5BB9\u65E7\u63A5\u53E3\uFF09 \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500











    fun getMarketApiIdentity(): String? {





        // \u4F18\u5148\u4ECE\u591A\u8EAB\u4EFD\u5217\u8868\u4E2D\u8FD4\u56DE\u7B2C\u4E00\u4E2A\u9009\u4E2D\u7684





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











    // \u2500\u2500 \u96C6\u5E02\u591A\u8EAB\u4EFD\u7BA1\u7406 \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500











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











    // \u2500\u2500 \u96C6\u5E02\u5C4F\u853D\u7F6E\u9876 \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500











    fun getBlockPinned(): Boolean = cachedBlockPinned











    suspend fun setBlockPinned(enabled: Boolean) {





        cachedBlockPinned = enabled





        appDataStore.dataStore.edit {





            it[MARKET_BLOCK_PINNED_KEY] = if (enabled) "true" else "false"





        }





    }











    // \u2500\u2500 \u96C6\u5E02\u5C4F\u853D\u8BCD \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500











    fun getBlockKeywords(): List<String> = cachedBlockKeywords











    suspend fun saveBlockKeywords(keywords: List<String>) {





        cachedBlockKeywords = keywords





        appDataStore.dataStore.edit { it[MARKET_BLOCK_KEYWORDS_KEY] = gson.toJson(keywords) }





    }











    // \u2500\u2500 \u96C6\u5E02\u677F\u5757\u7B5B\u9009 \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500











    fun getFilterNodeIds(): List<Long> = cachedFilterNodeIds











    suspend fun saveFilterNodeIds(nodeIds: List<Long>) {





        cachedFilterNodeIds = nodeIds





        appDataStore.dataStore.edit { it[MARKET_FILTER_NODES_KEY] = gson.toJson(nodeIds) }





    }











    // \u2500\u2500 \u96C6\u5E02\u529F\u80FD\u603B\u5F00\u5173 \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500











    fun getMarketEnabled(): Boolean = cachedMarketEnabled











    suspend fun setMarketEnabled(enabled: Boolean) {





        cachedMarketEnabled = enabled





        appDataStore.dataStore.edit {





            it[MARKET_ENABLED_KEY] = if (enabled) "true" else "false"





        }





    }











    // \u2500\u2500 \u96C6\u5E02\u5217\u8868\u5E03\u5C40\u6A21\u5F0F \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500





    // "list" \u5355\u5217 / "stagger" \u5C0F\u7EA2\u4E66\u53CC\u5217\u7011\u5E03











    fun getListLayoutMode(): String = cachedMarketListLayoutMode











    suspend fun setListLayoutMode(mode: String) {





        val normalized = if (mode == "stagger") "stagger" else "list"





        cachedMarketListLayoutMode = normalized





        appDataStore.dataStore.edit { it[MARKET_LIST_LAYOUT_KEY] = normalized }





    }











    // \u2500\u2500 \u96C6\u5E02"\u56DE\u5230\u9876\u90E8"\u6309\u94AE \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500











    fun getScrollToTop(): Boolean = cachedMarketScrollToTop











    suspend fun setScrollToTop(enabled: Boolean) {





        cachedMarketScrollToTop = enabled





        appDataStore.dataStore.edit { it[MARKET_SCROLL_TO_TOP_KEY] = if (enabled) "true" else "false" }





    }











    fun getAiCommentEnabled(): Boolean = cachedAiCommentEnabled











    suspend fun setAiCommentEnabled(enabled: Boolean) {





        cachedAiCommentEnabled = enabled





        appDataStore.dataStore.edit { it[AI_COMMENT_ENABLED_KEY] = enabled.toString() }





    }











    fun getAiCommentModel(): AiCommentModel = cachedAiCommentModel











    suspend fun setAiCommentModel(model: AiCommentModel) {





        cachedAiCommentModel = model





        appDataStore.dataStore.edit { it[AI_COMMENT_MODEL_KEY] = model.name }





    }











    fun getAiCommentStyle(): AiCommentStyle = cachedAiCommentStyle











    suspend fun setAiCommentStyle(style: AiCommentStyle) {





        cachedAiCommentStyle = style





        appDataStore.dataStore.edit { it[AI_COMMENT_STYLE_KEY] = style.name }





    }











    fun getAiTemplates(): List<AiCommentTemplate> = cachedAiTemplates











    fun getAiSelectedTemplateId(): String = cachedAiSelectedTemplateId











    suspend fun setAiSelectedTemplateId(id: String) {





        if (cachedAiTemplates.none { it.id == id }) return





        cachedAiSelectedTemplateId = id





        appDataStore.dataStore.edit { it[AI_COMMENT_SELECTED_TEMPLATE_KEY] = id }





    }











    suspend fun saveAiTemplate(template: AiCommentTemplate) {





        cachedAiTemplates = if (cachedAiTemplates.any { it.id == template.id }) {





            cachedAiTemplates.map { if (it.id == template.id) template else it }





        } else {





            cachedAiTemplates + template





        }





        appDataStore.dataStore.edit { it[AI_COMMENT_TEMPLATES_KEY] = gson.toJson(cachedAiTemplates) }





    }











    suspend fun deleteAiTemplate(id: String) {





        val target = cachedAiTemplates.firstOrNull { it.id == id } ?: return





        if (target.builtIn) return





        cachedAiTemplates = cachedAiTemplates.filterNot { it.id == id }





        if (cachedAiSelectedTemplateId == id) cachedAiSelectedTemplateId = cachedAiTemplates.first().id





        appDataStore.dataStore.edit {





            it[AI_COMMENT_TEMPLATES_KEY] = gson.toJson(cachedAiTemplates)





            it[AI_COMMENT_SELECTED_TEMPLATE_KEY] = cachedAiSelectedTemplateId





        }





    }











    fun getAiOverallPrompt(): String = cachedAiOverallPrompt











    suspend fun setAiOverallPrompt(prompt: String) {





        cachedAiOverallPrompt = prompt.trim().ifBlank { AiCommentPrompts.defaultOverallPrompt }





        appDataStore.dataStore.edit { it[AI_COMMENT_OVERALL_PROMPT_KEY] = cachedAiOverallPrompt }





    }











    fun getAiStylePrompt(style: AiCommentStyle): String =





        cachedAiStylePrompts[style.name]?.takeIf { it.isNotBlank() } ?: style.prompt











    fun getAiStylePrompts(): Map<AiCommentStyle, String> =





        AiCommentStyle.entries.associateWith(::getAiStylePrompt)











    suspend fun setAiStylePrompt(style: AiCommentStyle, prompt: String) {





        cachedAiStylePrompts = cachedAiStylePrompts +





            (style.name to prompt.trim().ifBlank { style.prompt })





        appDataStore.dataStore.edit { it[AI_COMMENT_STYLE_PROMPTS_KEY] = gson.toJson(cachedAiStylePrompts) }





    }











    suspend fun resetAiPrompts() {





        cachedAiOverallPrompt = AiCommentPrompts.defaultOverallPrompt





        cachedAiStylePrompts = defaultAiStylePrompts()





        cachedAiTemplates = defaultAiCommentTemplates()





        cachedAiSelectedTemplateId = AiCommentStyle.GENTLE.name





        appDataStore.dataStore.edit {





            it.remove(AI_COMMENT_OVERALL_PROMPT_KEY)





            it.remove(AI_COMMENT_STYLE_PROMPTS_KEY)





            it.remove(AI_COMMENT_TEMPLATES_KEY)





            it.remove(AI_COMMENT_SELECTED_TEMPLATE_KEY)





        }





    }











    // \u2500\u2500 \u9996\u9875"\u6700\u8FD1\u4F7F\u7528" \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500











    /** \u6700\u8FD1\u4F7F\u7528\u7684\u5E94\u7528 key \u5217\u8868(\u6309\u65F6\u95F4\u5012\u5E8F,\u6700\u591A 5 \u4E2A) */





    fun getRecentApps(): List<String> =





        cachedRecentApps.split(",").filter { it.isNotBlank() }











    /** \u8BB0\u5F55\u4E00\u6B21\u5E94\u7528\u4F7F\u7528:\u63A8\u5230\u6700\u524D\u9762,\u53BB\u91CD,\u622A\u65AD\u5230 5 \u4E2A */





    suspend fun recordRecentApp(appKey: String) {





        val list = getRecentApps().toMutableList()





        list.remove(appKey) // \u53BB\u91CD





        list.add(0, appKey) // \u63A8\u5230\u6700\u524D





        val value = list.take(5).joinToString(",")





        cachedRecentApps = value





        appDataStore.dataStore.edit { it[RECENT_APPS_KEY] = value }





    }











    // \u2500\u2500 \u6211\u7684\u4FE1\u606F\u7F13\u5B58 \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500











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











    // \u2500\u2500 \u8BFE\u8868\u7F13\u5B58 \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500











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











    // \u2500\u2500 \u6210\u7EE9\u7F13\u5B58 \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500











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











    // \u2500\u2500 \u8003\u8BD5\u5B89\u6392\u7F13\u5B58 \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500











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











    // \u2500\u2500 \u8D22\u52A1\u6C47\u603B\u7F13\u5B58 \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500











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











    // \u2500\u2500 \u8003\u52E4\u7F3A\u52E4\u7F13\u5B58 \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500











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











    // \u2500\u2500 kqcard \u8003\u52E4\u7F13\u5B58 \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500











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











    // \u2500\u2500 \u7528\u6237\u81EA\u5B9A\u4E49\u8BFE\u8868\u6761\u76EE \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500











    fun getUserScheduleJson(): String? = cachedUserScheduleJson











    suspend fun saveUserScheduleJson(json: String) {





        cachedUserScheduleJson = json





        appDataStore.dataStore.edit { it[USER_SCHEDULE_JSON_KEY] = json }





    }











    suspend fun clearUserScheduleJson() {





        cachedUserScheduleJson = null





        appDataStore.dataStore.edit { it.remove(USER_SCHEDULE_JSON_KEY) }





    }











    // \u2500\u2500 \u8BFE\u8868\u5E03\u5C40\u504F\u597D \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500











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











    // \u2500\u2500 \u8BFE\u8868\u663E\u793A\u8BBE\u7F6E (2026-06-17) \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500











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











    // \u2500\u2500 \u8003\u6838\u65B9\u6848\u7F13\u5B58 \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500











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











    // \u2500\u2500 \u8BB0\u5F55\u7D22\u5F15\u7F13\u5B58 (\u70B9\u540D/\u7B7E\u5230/\u4F5C\u4E1A) \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500











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











    // \u2500\u2500 \u4F5C\u4E1A\u5217\u8868\u7F13\u5B58 (\u9996\u9875\u8FD1\u671F\u4EFB\u52A1) \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500











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











    // \u2500\u2500 \u7528\u6237\u81EA\u5B9A\u4E49\u5F85\u529E\u7F13\u5B58 (\u9996\u9875\u8FD1\u671F\u4EFB\u52A1) \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500











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











    // \u2500\u2500 \u6D74\u5BA4\u4F59\u989D\u624B\u673A\u53F7 \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500











    fun getBathroomPhone(): String? = cachedBathroomPhone











    suspend fun saveBathroomPhone(phone: String) {





        cachedBathroomPhone = phone





        appDataStore.dataStore.edit { it[BATHROOM_PHONE_KEY] = phone }





    }











    suspend fun clearBathroomPhone() {





        cachedBathroomPhone = null





        appDataStore.dataStore.edit { it.remove(BATHROOM_PHONE_KEY) }





    }











    // \u2500\u2500 \u4E00\u5361\u901A\u8D26\u5355\u7F13\u5B58 \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500











    fun getBillsJson(): String? = cachedBillsJson











    fun getBillsUpdatedAt(): Long = cachedBillsUpdatedAt











    suspend fun saveBillsJson(json: String) {










        cachedBillsJson = json





        cachedBillsUpdatedAt = System.currentTimeMillis()





        appDataStore.dataStore.edit { preferences ->





            preferences[BILLS_JSON_KEY] = json





            preferences[BILLS_UPDATED_AT_KEY] = cachedBillsUpdatedAt





        }










    }











    suspend fun clearBillsJson() {





        cachedBillsJson = null





        cachedBillsUpdatedAt = 0L





        appDataStore.dataStore.edit { preferences ->





            preferences.remove(BILLS_JSON_KEY)





            preferences.remove(BILLS_UPDATED_AT_KEY)





        }





    }











    // \u2500\u2500 \u7535\u8D39\u623F\u95F4\u914D\u7F6E \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500











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











    fun getNewCampusConfig(): ElectricityRoomConfig = cachedNewCampusConfig











    suspend fun saveNewCampusConfig(config: ElectricityRoomConfig) {





        cachedNewCampusConfig = config





        appDataStore.dataStore.edit { it[NEW_CAMPUS_CONFIG_KEY] = gson.toJson(config) }





    }











    fun getAdwmhSessionId(): String? = cachedAdwmhSessionId











    suspend fun saveAdwmhSessionId(sessionId: String) {





        cachedAdwmhSessionId = sessionId





        appDataStore.dataStore.edit { it[ADWMH_SESSION_KEY] = sessionId }





    }











    suspend fun clearAdwmhSessionId() {





        cachedAdwmhSessionId = null
        cachedExamPredictionsJson = null





        appDataStore.dataStore.edit { it.remove(ADWMH_SESSION_KEY) }





    }











    // \u2500\u2500 \u652F\u4ED8\u7801\u662F\u5426\u81EA\u52A8\u8C03\u9AD8\u5C4F\u5E55\u4EAE\u5EA6\uFF08\u9ED8\u8BA4\u5F00\u542F\uFF09 \u2500\u2500





    fun getQrBrightnessBoost(): Boolean = cachedQrBrightnessBoost





    suspend fun setQrBrightnessBoost(enabled: Boolean) {





        cachedQrBrightnessBoost = enabled





        appDataStore.dataStore.edit { it[QR_BRIGHTNESS_BOOST_KEY] = enabled }





    }











    // \u2500\u2500 \u667A\u6167\u5B89\u5927\u767B\u5F55\u662F\u5426\u542F\u7528\u5E76\u53D1\u91CD\u8BD5\uFF08\u9ED8\u8BA4\u5173\u95ED\uFF09 \u2500\u2500





    fun getAdwmhConcurrentRetry(): Boolean = cachedAdwmhConcurrentRetry





    suspend fun setAdwmhConcurrentRetry(enabled: Boolean) {





        cachedAdwmhConcurrentRetry = enabled





        appDataStore.dataStore.edit { it[ADWMH_CONCURRENT_RETRY_KEY] = enabled }





    }











    // \u2500\u2500 \u57F9\u517B\u65B9\u6848\u5B8C\u6210\u8FDB\u5EA6\u7F13\u5B58 \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500











    // ── 排考预测 Gitee JSON 缓存 ─────────────────────────
    // 由 ExamDataRepository 从 Gitee yao-enqi/ahu-plus-update 仓库的
    // exam_predictions/exam_predictions.json 拉取后写入。
    // (2026-06-23 改为 Gitee 共享,原先的 jwapp JWT 登录流程已废弃。)

    fun getExamPredictionsJson(): String? = cachedExamPredictionsJson

    suspend fun saveExamPredictionsJson(json: String) {
        cachedExamPredictionsJson = json
        appDataStore.dataStore.edit { it[EXAM_PREDICTIONS_JSON_KEY] = json }
    }

    suspend fun clearExamPredictionsJson() {
        cachedExamPredictionsJson = null
        appDataStore.dataStore.edit { it.remove(EXAM_PREDICTIONS_JSON_KEY) }
    }

    fun getTrainingPlanJson(): String? =





        cachedTrainingPlanJson.takeIf { cachedTrainingPlanCacheVersion >= TRAINING_PLAN_CACHE_VERSION }











    fun getTrainingPlanUpdatedAt(): Long = cachedTrainingPlanUpdatedAt











    suspend fun saveTrainingPlanJson(json: String) {










        cachedTrainingPlanJson = json





        cachedTrainingPlanUpdatedAt = System.currentTimeMillis()





        cachedTrainingPlanCacheVersion = TRAINING_PLAN_CACHE_VERSION





        appDataStore.dataStore.edit { preferences ->





            preferences[TRAINING_PLAN_JSON_KEY] = json





            preferences[TRAINING_PLAN_UPDATED_AT_KEY] = cachedTrainingPlanUpdatedAt





            preferences[TRAINING_PLAN_CACHE_VERSION_KEY] = TRAINING_PLAN_CACHE_VERSION





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





        cachedTrainingPlanCacheVersion = 0L





        appDataStore.dataStore.edit { preferences ->





            preferences.remove(TRAINING_PLAN_JSON_KEY)





            preferences.remove(TRAINING_PLAN_UPDATED_AT_KEY)





            preferences.remove(TRAINING_PLAN_CACHE_VERSION_KEY)





        }





    }











    /** Clear the signed-in account and account-scoped caches, while preserving app settings and market identities. */





    suspend fun clearAuthData() {





        clearCachedAuthData()





        appDataStore.dataStore.edit { preferences ->





            AUTH_DATA_KEYS.forEach { preferences.remove(it) }





        }





    }











    suspend fun clearAll() {





        // \u5148\u6E05\u9664\u5185\u5B58\u7F13\u5B58





        clearCachedAuthData()





        cachedMarketApiIdentity = null





        cachedMarketIdentities = emptyList()





        cachedSelectedIdentityIds = emptySet()





        cachedBlockPinned = false





        cachedBlockKeywords = emptyList()





        cachedFilterNodeIds = emptyList()





        cachedMarketEnabled = false





        cachedMarketListLayoutMode = "list"





        cachedMarketScrollToTop = true





        cachedCxCookies = null
        cachedCxCoursesProgressJson = null
        cachedCxHomeworkJson = null
        cachedCxHomeworkDetailJson = null
        cachedCxVisitBrushEnabled = "false"
        cachedCxVisitBrushInterval = "30"
        cachedCxDownloadEnabled = "false"
        cachedCxHideEndedCourses = "true"
        cachedCxHiddenCourses = ""
        cachedCxPhone = null
        cachedCxPassword = null





        cachedCxCoursesJson = null





        cachedCxTikuConfig = null





        cachedCxMessagesJson = null





        cachedCxMessagesCursor = ""





        cachedCxMessagesMerge = "false"





        cachedAiCommentEnabled = false





        cachedAiCommentModel = AiCommentModel.FLASH





        cachedAiCommentStyle = AiCommentStyle.GENTLE





        cachedAiOverallPrompt = AiCommentPrompts.defaultOverallPrompt





        cachedAiStylePrompts = defaultAiStylePrompts()





        cachedAiTemplates = defaultAiCommentTemplates()





        cachedAiSelectedTemplateId = AiCommentStyle.GENTLE.name





        cachedRecentApps = ""





        cachedScheduleColWidth = 64f





        cachedScheduleRowHeight = 56f





        cachedScheduleFontScale = 1.0f





        cachedShowSat = true





        cachedShowSun = true





        cachedPagerEnabled = true





        cachedResetOnEnter = true





        cachedShowCompletedTasks = false





        cachedShowCompletedExams = false





        // \u4E00\u6B21 edit \u5B8C\u6210\u6240\u6709\u5220\u9664\uFF0C\u907F\u514D\u591A\u6B21 DataStore \u5E8F\u5217\u5316/\u5199\u5165





        appDataStore.dataStore.edit { preferences ->





            ALL_CLEARABLE_KEYS.forEach { preferences.remove(it) }





        }





        Log.i(TAG, "\u6240\u6709\u4F1A\u8BDD\u548C\u51ED\u636E\u5DF2\u6E05\u9664")





    }











    /** \u6E05\u9664\u5185\u5B58\u4E2D\u6240\u6709\u8D26\u6237\u76F8\u5173\u7F13\u5B58 (\u5171\u4EAB\u903B\u8F91) */





    private fun clearCachedAuthData() {





        cachedSessionId = null





        cachedJwSessionId = null





        cachedJwPstSid = null





        cachedUsername = null





        cachedPassword = null





        cachedStudentInfoJson = null





        cachedStudentInfoUpdatedAt = 0L





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





        cachedNewCampusConfig = ElectricityRoomConfig()





        cachedAdwmhSessionId = null
        cachedExamPredictionsJson = null





        cachedTrainingPlanJson = null





        cachedTrainingPlanUpdatedAt = 0L





        cachedTrainingPlanCacheVersion = 0L





        cachedEmptyClassroomJson = null





        cachedEmptyClassroomKey = null





        cachedEmptyClassroomUpdatedAt = 0L





    }











    // \u2500\u2500 JSON \u89E3\u6790\u8F85\u52A9 \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500











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











    private fun parseAiStylePrompts(json: String?): Map<String, String> {





        if (json.isNullOrBlank()) return defaultAiStylePrompts()





        val type = object : TypeToken<Map<String, String>>() {}.type





        val parsed = runCatching { gson.fromJson<Map<String, String>>(json, type) }.getOrNull().orEmpty()





        return defaultAiStylePrompts() + parsed.filterValues { it.isNotBlank() }





    }











    private fun defaultAiStylePrompts(): Map<String, String> =





        AiCommentStyle.entries.associate { it.name to it.prompt }











    private fun parseAiTemplates(json: String?): List<AiCommentTemplate> {





        if (json.isNullOrBlank()) {





            return defaultAiCommentTemplates().map { template ->





                template.copy(prompt = cachedAiStylePrompts[template.id] ?: template.prompt)





            }





        }





        return runCatching {





            gson.fromJson(json, Array<AiCommentTemplate>::class.java)





                .filter { it.id.isNotBlank() && it.name.isNotBlank() && it.prompt.isNotBlank() }





        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: defaultAiCommentTemplates()





    }











    private fun parseElectricityConfig(json: String?): ElectricityRoomConfig {





        if (json.isNullOrBlank()) return ElectricityRoomConfig()





        return runCatching {





            gson.fromJson(json, ElectricityRoomConfig::class.java)





        }.getOrDefault(ElectricityRoomConfig())





    }











    // \u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550





    //  \u8D85\u661F\u5B66\u4E60\u901A





    // \u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550











    fun getCxCookies(): String? = cachedCxCookies











    suspend fun saveCxCookies(value: String) {





        cachedCxCookies = value





        appDataStore.dataStore.edit { it[CX_COOKIES_KEY] = value }





    }

    // ── 超星凭据 ──────────────────────────────────
    fun getCxPhone(): String? = cachedCxPhone
    fun getCxPassword(): String? = cachedCxPassword

    fun hasCxCredentials(): Boolean = !cachedCxPhone.isNullOrBlank() && !cachedCxPassword.isNullOrBlank()

    suspend fun saveCxCredentials(phone: String, password: String) {
        cachedCxPhone = phone
        cachedCxPassword = password
        appDataStore.dataStore.edit { prefs ->
            prefs[CX_PHONE_KEY] = phone
            prefs[CX_PASSWORD_KEY] = password
        }
    }

    suspend fun clearCxCredentials() {
        cachedCxPhone = null
        cachedCxPassword = null
        appDataStore.dataStore.edit { prefs ->
            prefs.remove(CX_PHONE_KEY)
            prefs.remove(CX_PASSWORD_KEY)
        }
    }










    fun getCxCoursesJson(): String? = cachedCxCoursesJson











    suspend fun saveCxCoursesJson(value: String) {










        cachedCxCoursesJson = value





        appDataStore.dataStore.edit { it[CX_COURSES_JSON_KEY] = value }










    }



    fun getCxCoursesProgressJson(): String? = cachedCxCoursesProgressJson




    suspend fun saveCxCoursesProgressJson(value: String) {
        cachedCxCoursesProgressJson = value
        appDataStore.dataStore.edit { it[CX_COURSES_PROGRESS_JSON_KEY] = value }
    }




    fun getCxHomeworkJson(): String? = cachedCxHomeworkJson




    suspend fun saveCxHomeworkJson(value: String) {
        cachedCxHomeworkJson = value
        appDataStore.dataStore.edit { it[CX_HOMEWORK_JSON_KEY] = value }
    }




    fun getCxHomeworkDetailJson(): String? = cachedCxHomeworkDetailJson




    suspend fun saveCxHomeworkDetailJson(value: String) {
        cachedCxHomeworkDetailJson = value
        appDataStore.dataStore.edit { it[CX_HOMEWORK_DETAIL_JSON_KEY] = value }
    }




    fun getCxVisitBrushEnabled(): Boolean = cachedCxVisitBrushEnabled == "true"




    suspend fun saveCxVisitBrushEnabled(v: Boolean) {
        cachedCxVisitBrushEnabled = v.toString()
        appDataStore.dataStore.edit { it[CX_VISIT_BRUSH_ENABLED_KEY] = v.toString() }
    }




    fun getCxVisitBrushInterval(): Int = cachedCxVisitBrushInterval.toIntOrNull() ?: 30




    suspend fun saveCxVisitBrushInterval(v: Int) {
        cachedCxVisitBrushInterval = v.toString()
        appDataStore.dataStore.edit { it[CX_VISIT_BRUSH_INTERVAL_KEY] = v.toString() }
    }




    fun getCxDownloadEnabled(): Boolean = cachedCxDownloadEnabled == "true"




    suspend fun saveCxDownloadEnabled(v: Boolean) {
        cachedCxDownloadEnabled = v.toString()
        appDataStore.dataStore.edit { it[CX_DOWNLOAD_ENABLED_KEY] = v.toString() }
    }




    fun getCxHideEndedCourses(): Boolean = cachedCxHideEndedCourses == "true"




    suspend fun saveCxHideEndedCourses(v: Boolean) {
        cachedCxHideEndedCourses = v.toString()
        appDataStore.dataStore.edit { it[CX_HIDE_ENDED_COURSES_KEY] = v.toString() }
    }




    fun getCxHiddenCourses(): Set<String> = cachedCxHiddenCourses.split(",").filter { it.isNotBlank() }.toSet()




    suspend fun saveCxHiddenCourses(value: Set<String>) {
        val str = value.joinToString(",")
        cachedCxHiddenCourses = str
        appDataStore.dataStore.edit { it[CX_HIDDEN_COURSES_KEY] = str }
    }











    fun getCxTikuConfig(): String? = cachedCxTikuConfig











    suspend fun saveCxTikuConfig(value: String) {





        cachedCxTikuConfig = value





        appDataStore.dataStore.edit { it[CX_TIKU_CONFIG_KEY] = value }





    }











    /** \u83B7\u53D6\u9898\u5E93\u7F13\u5B58\uFF08question title \u2192 answer\uFF09 */





    suspend fun getCxTikuCache(question: String): String? {





        val json = appDataStore.dataStore.data.first()[CX_TIKU_CACHE_KEY] ?: return null





        return runCatching {





            val map = gson.fromJson<Map<String, String>>(json, object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type)





            map[question]





        }.getOrNull()





    }











    /** \u4FDD\u5B58\u9898\u5E93\u7F13\u5B58 */





    suspend fun saveCxTikuCache(question: String, answer: String) {





        val json = appDataStore.dataStore.data.first()[CX_TIKU_CACHE_KEY]





        val map: MutableMap<String, String> = if (json != null) {





            runCatching {





                gson.fromJson<Map<String, String>>(json, object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type)





            }.getOrNull()?.toMutableMap() ?: mutableMapOf()





        } else {





            mutableMapOf()





        }





        map[question] = answer





        appDataStore.dataStore.edit { it[CX_TIKU_CACHE_KEY] = gson.toJson(map) }





    }











    // \u2500\u2500 \u8D85\u661F\u8BBE\u7F6E getter/setter \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500











    fun getCxSpeed(): Float = cachedCxSpeed.toFloatOrNull() ?: 1.0f





    suspend fun saveCxSpeed(v: Float) { cachedCxSpeed = v.toString(); appDataStore.dataStore.edit { it[CX_SPEED_KEY] = v.toString() } }











    fun getCxConcurrency(): Int = cachedCxConcurrency.toIntOrNull() ?: 4





    suspend fun saveCxConcurrency(v: Int) { cachedCxConcurrency = v.toString(); appDataStore.dataStore.edit { it[CX_CONCURRENCY_KEY] = v.toString() } }











    fun getCxNotopenAction(): String = cachedCxNotopenAction





    suspend fun saveCxNotopenAction(v: String) { cachedCxNotopenAction = v; appDataStore.dataStore.edit { it[CX_NOTOPEN_ACTION_KEY] = v } }











    fun getCxAutoSign(): Boolean = cachedCxAutoSign == "true"





    suspend fun saveCxAutoSign(v: Boolean) { cachedCxAutoSign = v.toString(); appDataStore.dataStore.edit { it[CX_AUTO_SIGN_KEY] = v.toString() } }











    // Phase 3 - \u7B7E\u5230\u914D\u7F6E





    fun getCxSignLat(): Double = cachedCxSignLat.toDoubleOrNull() ?: -1.0





    suspend fun saveCxSignLat(v: Double) { cachedCxSignLat = v.toString(); appDataStore.dataStore.edit { it[CX_SIGN_LAT_KEY] = v.toString() } }





    fun getCxSignLon(): Double = cachedCxSignLon.toDoubleOrNull() ?: -1.0





    suspend fun saveCxSignLon(v: Double) { cachedCxSignLon = v.toString(); appDataStore.dataStore.edit { it[CX_SIGN_LON_KEY] = v.toString() } }





    fun getCxSignAddress(): String = cachedCxSignAddress





    suspend fun saveCxSignAddress(v: String) { cachedCxSignAddress = v; appDataStore.dataStore.edit { it[CX_SIGN_ADDRESS_KEY] = v } }





    fun getCxSignGesture(): String = cachedCxSignGesture





    suspend fun saveCxSignGesture(v: String) { cachedCxSignGesture = v; appDataStore.dataStore.edit { it[CX_SIGN_GESTURE_KEY] = v } }











    // Phase 4 - \u9898\u5E93\u6269\u5C55





    fun getCxProviderChain(): String = cachedCxProviderChain





    suspend fun saveCxProviderChain(v: String) { cachedCxProviderChain = v; appDataStore.dataStore.edit { it[CX_PROVIDER_CHAIN_KEY] = v } }





    fun getCxTokensYanxi(): String = cachedCxTokensYanxi





    suspend fun saveCxTokensYanxi(v: String) { cachedCxTokensYanxi = v; appDataStore.dataStore.edit { it[CX_TOKENS_YANXI_KEY] = v } }





    fun getCxCoverRate(): Double = cachedCxCoverRate.toDoubleOrNull() ?: 0.8





    suspend fun saveCxCoverRate(v: Double) { cachedCxCoverRate = v.toString(); appDataStore.dataStore.edit { it[CX_COVER_RATE_KEY] = v.toString() } }





    fun getCxTikuDelay(): Double = cachedCxTikuDelay.toDoubleOrNull() ?: 1.0





    suspend fun saveCxTikuDelay(v: Double) { cachedCxTikuDelay = v.toString(); appDataStore.dataStore.edit { it[CX_TIKU_DELAY_KEY] = v.toString() } }





    fun getCxAiMinInterval(): Int = cachedCxAiMinInterval.toIntOrNull() ?: 3





    suspend fun saveCxAiMinInterval(v: Int) { cachedCxAiMinInterval = v.toString(); appDataStore.dataStore.edit { it[CX_AI_MIN_INTERVAL_KEY] = v.toString() } }





    fun getCxAiHttpProxy(): String = cachedCxAiHttpProxy





    suspend fun saveCxAiHttpProxy(v: String) { cachedCxAiHttpProxy = v; appDataStore.dataStore.edit { it[CX_AI_HTTP_PROXY_KEY] = v } }





    fun getCxSiliconflowKey(): String = cachedCxSiliconflowKey





    suspend fun saveCxSiliconflowKey(v: String) { cachedCxSiliconflowKey = v; appDataStore.dataStore.edit { it[CX_SILICONFLOW_KEY_KEY] = v } }





    fun getCxSiliconflowModel(): String = cachedCxSiliconflowModel





    suspend fun saveCxSiliconflowModel(v: String) { cachedCxSiliconflowModel = v; appDataStore.dataStore.edit { it[CX_SILICONFLOW_MODEL_KEY] = v } }





    fun getCxSiliconflowEndpoint(): String = cachedCxSiliconflowEndpoint





    suspend fun saveCxSiliconflowEndpoint(v: String) { cachedCxSiliconflowEndpoint = v; appDataStore.dataStore.edit { it[CX_SILICONFLOW_ENDPOINT_KEY] = v } }





    fun getCxLikeapiSearch(): Boolean = cachedCxLikeapiSearch == "true"





    suspend fun saveCxLikeapiSearch(v: Boolean) { cachedCxLikeapiSearch = v.toString(); appDataStore.dataStore.edit { it[CX_LIKEAPI_SEARCH_KEY] = v.toString() } }





    fun getCxLikeapiVision(): Boolean = cachedCxLikeapiVision == "true"





    suspend fun saveCxLikeapiVision(v: Boolean) { cachedCxLikeapiVision = v.toString(); appDataStore.dataStore.edit { it[CX_LIKEAPI_VISION_KEY] = v.toString() } }





    fun getCxLikeapiModel(): String = cachedCxLikeapiModel





    suspend fun saveCxLikeapiModel(v: String) { cachedCxLikeapiModel = v; appDataStore.dataStore.edit { it[CX_LIKEAPI_MODEL_KEY] = v } }





    fun getCxGoAuthorization(): String = cachedCxGoAuthorization





    suspend fun saveCxGoAuthorization(v: String) { cachedCxGoAuthorization = v; appDataStore.dataStore.edit { it[CX_GO_AUTHORIZATION_KEY] = v } }





    fun getCxGoMinInterval(): Double = cachedCxGoMinInterval.toDoubleOrNull() ?: 1.0





    suspend fun saveCxGoMinInterval(v: Double) { cachedCxGoMinInterval = v.toString(); appDataStore.dataStore.edit { it[CX_GO_MIN_INTERVAL_KEY] = v.toString() } }





    fun getCxTikuAdapterUrl(): String = cachedCxTikuAdapterUrl





    suspend fun saveCxTikuAdapterUrl(v: String) { cachedCxTikuAdapterUrl = v; appDataStore.dataStore.edit { it[CX_TIKU_ADAPTER_URL_KEY] = v } }











    // Phase 5 - \u5916\u90E8\u901A\u77E5





    fun getCxNotifyProvider(): String = cachedCxNotifyProvider





    suspend fun saveCxNotifyProvider(v: String) { cachedCxNotifyProvider = v; appDataStore.dataStore.edit { it[CX_NOTIFY_PROVIDER_KEY] = v } }





    fun getCxNotifyUrl(): String = cachedCxNotifyUrl





    suspend fun saveCxNotifyUrl(v: String) { cachedCxNotifyUrl = v; appDataStore.dataStore.edit { it[CX_NOTIFY_URL_KEY] = v } }





    fun getCxNotifyTgChatId(): String = cachedCxNotifyTgChatId





    suspend fun saveCxNotifyTgChatId(v: String) { cachedCxNotifyTgChatId = v; appDataStore.dataStore.edit { it[CX_NOTIFY_TG_CHAT_ID_KEY] = v } }











    fun getCxSubmitMode(): String = cachedCxSubmitMode





    suspend fun saveCxSubmitMode(v: String) { cachedCxSubmitMode = v; appDataStore.dataStore.edit { it[CX_SUBMIT_MODE_KEY] = v } }











    fun getCxTikuType(): String = cachedCxTikuType





    suspend fun saveCxTikuType(v: String) { cachedCxTikuType = v; appDataStore.dataStore.edit { it[CX_TIKU_TYPE_KEY] = v } }











    fun getCxTikuToken(): String = cachedCxTikuToken





    suspend fun saveCxTikuToken(v: String) { cachedCxTikuToken = v; appDataStore.dataStore.edit { it[CX_TIKU_TOKEN_KEY] = v } }











    fun getCxAiKey(): String = cachedCxAiKey





    suspend fun saveCxAiKey(v: String) { cachedCxAiKey = v; appDataStore.dataStore.edit { it[CX_AI_KEY_KEY] = v } }











    fun getCxAiBaseUrl(): String = cachedCxAiBaseUrl.ifBlank { "https://api.deepseek.com" }





    suspend fun saveCxAiBaseUrl(v: String) { cachedCxAiBaseUrl = v; appDataStore.dataStore.edit { it[CX_AI_BASE_URL_KEY] = v } }











    fun getCxAiModel(): String = cachedCxAiModel.ifBlank { "deepseek-v4-flash" }





    suspend fun saveCxAiModel(v: String) { cachedCxAiModel = v; appDataStore.dataStore.edit { it[CX_AI_MODEL_KEY] = v } }











    fun getCxTaskTypes(): Set<String> = cachedCxTaskTypes.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()





    suspend fun saveCxTaskTypes(v: Set<String>) { cachedCxTaskTypes = v.joinToString(","); appDataStore.dataStore.edit { it[CX_TASK_TYPES_KEY] = v.joinToString(",") } }











    // \u2500\u2500 \u6D88\u606F\u4E2D\u5FC3\u7F13\u5B58 \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500











    fun getCxMessagesJson(): String? = cachedCxMessagesJson











    fun getCxMessagesCursor(): String = cachedCxMessagesCursor











    suspend fun saveCxMessagesJson(json: String, cursor: String) {










        cachedCxMessagesJson = json





        cachedCxMessagesCursor = cursor





        appDataStore.dataStore.edit { preferences ->





            preferences[CX_MESSAGES_JSON_KEY] = json





            preferences[CX_MESSAGES_CURSOR_KEY] = cursor





        }










    }











    fun getCxMessagesMerge(): Boolean = cachedCxMessagesMerge == "true"





    suspend fun saveCxMessagesMerge(v: Boolean) {





        cachedCxMessagesMerge = v.toString()





        appDataStore.dataStore.edit { it[CX_MESSAGES_MERGE_KEY] = v.toString() }





    }











    // \u2500\u2500 \u81EA\u52A8\u66F4\u65B0\u76F8\u5173 \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500











    fun getIgnoredVersionCode(): Int = cachedIgnoredVersionCode











    suspend fun saveIgnoredVersionCode(code: Int) {





        cachedIgnoredVersionCode = code





        appDataStore.dataStore.edit { it[IGNORED_VERSION_CODE_KEY] = code.toString() }





    }











    private companion object {





        const val TAG = "SessionManager"





        const val TRAINING_PLAN_CACHE_VERSION = 2L











        // \u2500\u2500 DataStore keys \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500





        val SESSION_KEY = stringPreferencesKey("jsessionid")





        val JW_SESSION_KEY = stringPreferencesKey("jw_session_id")





        val JW_PST_SID_KEY = stringPreferencesKey("jw_pst_sid")





        val USERNAME_KEY = stringPreferencesKey("username")





        val PASSWORD_KEY = stringPreferencesKey("password")





        val MARKET_API_IDENTITY_KEY = stringPreferencesKey("market_api_identity")





        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")





        val STUDENT_INFO_KEY = stringPreferencesKey("student_info_json")





        val STUDENT_INFO_UPDATED_AT_KEY = longPreferencesKey("student_info_updated_at")





        val MARKET_IDENTITIES_KEY = stringPreferencesKey("market_identities")





        val MARKET_SELECTED_IDS_KEY = stringPreferencesKey("market_selected_ids")





        val MARKET_BLOCK_PINNED_KEY = stringPreferencesKey("market_block_pinned")





        val MARKET_BLOCK_KEYWORDS_KEY = stringPreferencesKey("market_block_keywords")





        val MARKET_FILTER_NODES_KEY = stringPreferencesKey("market_filter_nodes")





        val MARKET_ENABLED_KEY = stringPreferencesKey("market_enabled")





        val MARKET_LIST_LAYOUT_KEY = stringPreferencesKey("market_list_layout_mode")





        val MARKET_SCROLL_TO_TOP_KEY = stringPreferencesKey("market_scroll_to_top")





        val AI_COMMENT_ENABLED_KEY = stringPreferencesKey("ai_comment_enabled")





        val AI_COMMENT_MODEL_KEY = stringPreferencesKey("ai_comment_model")





        val AI_COMMENT_STYLE_KEY = stringPreferencesKey("ai_comment_style")





        val AI_COMMENT_OVERALL_PROMPT_KEY = stringPreferencesKey("ai_comment_overall_prompt")





        val AI_COMMENT_STYLE_PROMPTS_KEY = stringPreferencesKey("ai_comment_style_prompts")





        val AI_COMMENT_TEMPLATES_KEY = stringPreferencesKey("ai_comment_templates_json")





        val AI_COMMENT_SELECTED_TEMPLATE_KEY = stringPreferencesKey("ai_comment_selected_template")





        val RECENT_APPS_KEY = stringPreferencesKey("recent_apps")





        val SCHEDULE_COL_WIDTH_KEY = stringPreferencesKey("schedule_col_width")





        val SCHEDULE_ROW_HEIGHT_KEY = stringPreferencesKey("schedule_row_height")





        val SCHEDULE_FONT_SCALE_KEY = stringPreferencesKey("schedule_font_scale")





        val KEY_SHOW_SAT = stringPreferencesKey("schedule_show_sat")





        val KEY_SHOW_SUN = stringPreferencesKey("schedule_show_sun")





        val KEY_PAGER_ENABLED = stringPreferencesKey("schedule_pager_enabled")





        val KEY_RESET_ON_ENTER = stringPreferencesKey("schedule_reset_on_enter")





        val KEY_SHOW_COMPLETED_TASKS = stringPreferencesKey("schedule_show_completed_tasks")





        val KEY_SHOW_COMPLETED_EXAMS = stringPreferencesKey("schedule_show_completed_exams")





        val SCHEDULE_JSON_KEY = stringPreferencesKey("schedule_json")





        val SCHEDULE_UPDATED_AT_KEY = longPreferencesKey("schedule_updated_at")





        val GRADES_JSON_KEY = stringPreferencesKey("grades_json")





        val GPA_METADATA_JSON_KEY = stringPreferencesKey("gpa_metadata_json")





        val GRADES_UPDATED_AT_KEY = longPreferencesKey("grades_updated_at")





        val EXAMS_JSON_KEY = stringPreferencesKey("exams_json")





        val EXAMS_UPDATED_AT_KEY = longPreferencesKey("exams_updated_at")





        val FINANCE_JSON_KEY = stringPreferencesKey("finance_json")





        val FINANCE_UPDATED_AT_KEY = longPreferencesKey("finance_updated_at")





        val ATTENDANCE_JSON_KEY = stringPreferencesKey("attendance_json")





        val ATTENDANCE_UPDATED_AT_KEY = longPreferencesKey("attendance_updated_at")





        val KQCARD_ATTENDANCE_JSON_KEY = stringPreferencesKey("kqcard_attendance_json")





        val KQCARD_ATTENDANCE_UPDATED_AT_KEY = longPreferencesKey("kqcard_attendance_updated_at")





        val USER_SCHEDULE_JSON_KEY = stringPreferencesKey("user_schedule_json")





        val ASSESSMENT_JSON_KEY = stringPreferencesKey("assessment_json")





        val ASSESSMENT_UPDATED_AT_KEY = longPreferencesKey("assessment_updated_at")





        val RECORD_INDEX_JSON_KEY = stringPreferencesKey("record_index_json")





        val RECORD_INDEX_UPDATED_AT_KEY = longPreferencesKey("record_index_updated_at")





        val HOMEWORK_JSON_KEY = stringPreferencesKey("homework_json")





        val HOMEWORK_UPDATED_AT_KEY = longPreferencesKey("homework_updated_at")





        val USER_TASKS_JSON_KEY = stringPreferencesKey("user_tasks_json")





        val USER_TASKS_UPDATED_AT_KEY = longPreferencesKey("user_tasks_updated_at")





        val BATHROOM_PHONE_KEY = stringPreferencesKey("bathroom_phone")





        val BILLS_JSON_KEY = stringPreferencesKey("bills_json")





        val BILLS_UPDATED_AT_KEY = longPreferencesKey("bills_updated_at")





        val AC_CONFIG_KEY = stringPreferencesKey("ac_config")





        val LIGHTING_CONFIG_KEY = stringPreferencesKey("lighting_config")





        val NEW_CAMPUS_CONFIG_KEY = stringPreferencesKey("new_campus_config")





        val ADWMH_SESSION_KEY = stringPreferencesKey("adwmh_jsessionid")
        val EXAM_PREDICTIONS_JSON_KEY = stringPreferencesKey("exam_predictions_json")





    private val QR_BRIGHTNESS_BOOST_KEY = booleanPreferencesKey("qr_brightness_boost")





    private val ADWMH_CONCURRENT_RETRY_KEY = booleanPreferencesKey("adwmh_concurrent_retry")





        val TRAINING_PLAN_JSON_KEY = stringPreferencesKey("training_plan_json")





        val TRAINING_PLAN_UPDATED_AT_KEY = longPreferencesKey("training_plan_updated_at")





        val TRAINING_PLAN_CACHE_VERSION_KEY = longPreferencesKey("training_plan_cache_version")





        val EMPTY_CLASSROOM_JSON_KEY = stringPreferencesKey("empty_classroom_json")





        val EMPTY_CLASSROOM_KEY_KEY = stringPreferencesKey("empty_classroom_key")





        val EMPTY_CLASSROOM_UPDATED_AT_KEY = longPreferencesKey("empty_classroom_updated_at")








        val CX_COURSES_PROGRESS_JSON_KEY = stringPreferencesKey("cx_courses_progress_json")
        val CX_HOMEWORK_JSON_KEY = stringPreferencesKey("cx_homework_json")
        val CX_HOMEWORK_DETAIL_JSON_KEY = stringPreferencesKey("cx_homework_detail_json")
        val CX_VISIT_BRUSH_ENABLED_KEY = stringPreferencesKey("cx_visit_brush_enabled")
        val CX_VISIT_BRUSH_INTERVAL_KEY = stringPreferencesKey("cx_visit_brush_interval")
        val CX_DOWNLOAD_ENABLED_KEY = stringPreferencesKey("cx_download_enabled")
        val CX_HIDE_ENDED_COURSES_KEY = stringPreferencesKey("cx_hide_ended_courses")
        val CX_HIDDEN_COURSES_KEY = stringPreferencesKey("cx_hidden_courses")



        // \u8D85\u661F\u5B66\u4E60\u901A





        val CX_COOKIES_KEY = stringPreferencesKey("cx_cookies")





        val CX_COURSES_JSON_KEY = stringPreferencesKey("cx_courses_json")

        val CX_PHONE_KEY = stringPreferencesKey("cx_phone")
        val CX_PASSWORD_KEY = stringPreferencesKey("cx_password")

        val CX_TIKU_CONFIG_KEY = stringPreferencesKey("cx_tiku_config")





        val CX_TIKU_CACHE_KEY = stringPreferencesKey("cx_tiku_cache")





        val CX_SPEED_KEY = stringPreferencesKey("cx_speed")





        val CX_CONCURRENCY_KEY = stringPreferencesKey("cx_concurrency")





        val CX_NOTOPEN_ACTION_KEY = stringPreferencesKey("cx_notopen_action")





        val CX_AUTO_SIGN_KEY = stringPreferencesKey("cx_auto_sign")





        val CX_SUBMIT_MODE_KEY = stringPreferencesKey("cx_submit_mode")





        val CX_TIKU_TYPE_KEY = stringPreferencesKey("cx_tiku_type")





        val CX_TIKU_TOKEN_KEY = stringPreferencesKey("cx_tiku_token")





        val CX_AI_KEY_KEY = stringPreferencesKey("cx_ai_key")





        val CX_AI_BASE_URL_KEY = stringPreferencesKey("cx_ai_base_url")





        val CX_AI_MODEL_KEY = stringPreferencesKey("cx_ai_model")





        val CX_TASK_TYPES_KEY = stringPreferencesKey("cx_task_types")





        // Phase 3 (2026-06-20) - \u7B7E\u5230\u914D\u7F6E





        val CX_SIGN_LAT_KEY = stringPreferencesKey("cx_sign_lat")





        val CX_SIGN_LON_KEY = stringPreferencesKey("cx_sign_lon")





        val CX_SIGN_ADDRESS_KEY = stringPreferencesKey("cx_sign_address")





        val CX_SIGN_GESTURE_KEY = stringPreferencesKey("cx_sign_gesture")





        // Phase 4 (2026-06-20) - \u9898\u5E93\u6269\u5C55





        val CX_PROVIDER_CHAIN_KEY = stringPreferencesKey("cx_provider_chain")





        val CX_TOKENS_YANXI_KEY = stringPreferencesKey("cx_tokens_yanxi")





        val CX_COVER_RATE_KEY = stringPreferencesKey("cx_cover_rate")





        val CX_TIKU_DELAY_KEY = stringPreferencesKey("cx_tiku_delay")





        val CX_AI_MIN_INTERVAL_KEY = stringPreferencesKey("cx_ai_min_interval")





        val CX_AI_HTTP_PROXY_KEY = stringPreferencesKey("cx_ai_http_proxy")





        val CX_SILICONFLOW_KEY_KEY = stringPreferencesKey("cx_siliconflow_key")





        val CX_SILICONFLOW_MODEL_KEY = stringPreferencesKey("cx_siliconflow_model")





        val CX_SILICONFLOW_ENDPOINT_KEY = stringPreferencesKey("cx_siliconflow_endpoint")





        val CX_LIKEAPI_SEARCH_KEY = stringPreferencesKey("cx_likeapi_search")





        val CX_LIKEAPI_VISION_KEY = stringPreferencesKey("cx_likeapi_vision")





        val CX_LIKEAPI_MODEL_KEY = stringPreferencesKey("cx_likeapi_model")





        val CX_GO_AUTHORIZATION_KEY = stringPreferencesKey("cx_go_authorization")





        val CX_GO_MIN_INTERVAL_KEY = stringPreferencesKey("cx_go_min_interval")





        val CX_TIKU_ADAPTER_URL_KEY = stringPreferencesKey("cx_tiku_adapter_url")





        // Phase 5 (2026-06-20) - \u901A\u77E5 + \u4EFB\u52A1\u5386\u53F2





        val CX_NOTIFY_PROVIDER_KEY = stringPreferencesKey("cx_notify_provider")





        val CX_NOTIFY_URL_KEY = stringPreferencesKey("cx_notify_url")





        val CX_NOTIFY_TG_CHAT_ID_KEY = stringPreferencesKey("cx_notify_tg_chat_id")





        val CX_TASK_LOG_KEY = stringPreferencesKey("cx_task_log")





        val CX_MESSAGES_JSON_KEY = stringPreferencesKey("cx_messages_json")





        val CX_MESSAGES_CURSOR_KEY = stringPreferencesKey("cx_messages_cursor")





        val CX_MESSAGES_MERGE_KEY = stringPreferencesKey("cx_messages_merge")











        // \u81EA\u52A8\u66F4\u65B0





        val IGNORED_VERSION_CODE_KEY = stringPreferencesKey("ignored_version_code")











        /** clearAuthData \u9700\u8981\u79FB\u9664\u7684 DataStore keys */





        val AUTH_DATA_KEYS = listOf(





            SESSION_KEY, JW_SESSION_KEY, JW_PST_SID_KEY,





            USERNAME_KEY, PASSWORD_KEY,





            STUDENT_INFO_KEY, STUDENT_INFO_UPDATED_AT_KEY,





            SCHEDULE_JSON_KEY, SCHEDULE_UPDATED_AT_KEY,





            GRADES_JSON_KEY, GPA_METADATA_JSON_KEY, GRADES_UPDATED_AT_KEY,





            EXAMS_JSON_KEY, EXAMS_UPDATED_AT_KEY,





            FINANCE_JSON_KEY, FINANCE_UPDATED_AT_KEY,





            ATTENDANCE_JSON_KEY, ATTENDANCE_UPDATED_AT_KEY,





            KQCARD_ATTENDANCE_JSON_KEY, KQCARD_ATTENDANCE_UPDATED_AT_KEY,





            USER_SCHEDULE_JSON_KEY,





            ASSESSMENT_JSON_KEY, ASSESSMENT_UPDATED_AT_KEY,





            RECORD_INDEX_JSON_KEY, RECORD_INDEX_UPDATED_AT_KEY,





            HOMEWORK_JSON_KEY, HOMEWORK_UPDATED_AT_KEY,





            USER_TASKS_JSON_KEY, USER_TASKS_UPDATED_AT_KEY,





            BATHROOM_PHONE_KEY,





            BILLS_JSON_KEY, BILLS_UPDATED_AT_KEY,





            AC_CONFIG_KEY, LIGHTING_CONFIG_KEY, NEW_CAMPUS_CONFIG_KEY,





            ADWMH_SESSION_KEY, EXAM_PREDICTIONS_JSON_KEY,





            TRAINING_PLAN_JSON_KEY, TRAINING_PLAN_UPDATED_AT_KEY, TRAINING_PLAN_CACHE_VERSION_KEY,





            EMPTY_CLASSROOM_JSON_KEY, EMPTY_CLASSROOM_KEY_KEY, EMPTY_CLASSROOM_UPDATED_AT_KEY,

            CX_PHONE_KEY, CX_PASSWORD_KEY,




        )











        /** clearAll \u9700\u8981\u79FB\u9664\u7684 DataStore keys (AUTH_DATA + \u96C6\u5E02\u8BBE\u7F6E + UI \u504F\u597D + \u8D85\u661F) */





        val ALL_CLEARABLE_KEYS = AUTH_DATA_KEYS + listOf(





            MARKET_API_IDENTITY_KEY,





            MARKET_IDENTITIES_KEY, MARKET_SELECTED_IDS_KEY,





            MARKET_BLOCK_PINNED_KEY, MARKET_BLOCK_KEYWORDS_KEY, MARKET_FILTER_NODES_KEY,





            MARKET_ENABLED_KEY, MARKET_LIST_LAYOUT_KEY, MARKET_SCROLL_TO_TOP_KEY,





            AI_COMMENT_ENABLED_KEY, AI_COMMENT_MODEL_KEY, AI_COMMENT_STYLE_KEY,





            AI_COMMENT_OVERALL_PROMPT_KEY, AI_COMMENT_STYLE_PROMPTS_KEY,





            AI_COMMENT_TEMPLATES_KEY, AI_COMMENT_SELECTED_TEMPLATE_KEY,





            RECENT_APPS_KEY,





            SCHEDULE_COL_WIDTH_KEY, SCHEDULE_ROW_HEIGHT_KEY, SCHEDULE_FONT_SCALE_KEY,





            KEY_SHOW_SAT, KEY_SHOW_SUN, KEY_PAGER_ENABLED,





            KEY_RESET_ON_ENTER, KEY_SHOW_COMPLETED_TASKS, KEY_SHOW_COMPLETED_EXAMS,





            CX_COOKIES_KEY, CX_COURSES_JSON_KEY, CX_COURSES_PROGRESS_JSON_KEY, CX_HOMEWORK_JSON_KEY, CX_HOMEWORK_DETAIL_JSON_KEY,
            CX_VISIT_BRUSH_ENABLED_KEY, CX_VISIT_BRUSH_INTERVAL_KEY, CX_DOWNLOAD_ENABLED_KEY,
            CX_HIDE_ENDED_COURSES_KEY, CX_HIDDEN_COURSES_KEY, CX_TIKU_CONFIG_KEY, CX_TIKU_CACHE_KEY,





            CX_SPEED_KEY, CX_CONCURRENCY_KEY, CX_NOTOPEN_ACTION_KEY, CX_AUTO_SIGN_KEY,





            CX_SUBMIT_MODE_KEY, CX_TIKU_TYPE_KEY, CX_TIKU_TOKEN_KEY,





            CX_AI_KEY_KEY, CX_AI_BASE_URL_KEY, CX_AI_MODEL_KEY, CX_TASK_TYPES_KEY,





            CX_SIGN_LAT_KEY, CX_SIGN_LON_KEY, CX_SIGN_ADDRESS_KEY, CX_SIGN_GESTURE_KEY,





            CX_PROVIDER_CHAIN_KEY, CX_TOKENS_YANXI_KEY, CX_COVER_RATE_KEY,





            CX_TIKU_DELAY_KEY, CX_AI_MIN_INTERVAL_KEY, CX_AI_HTTP_PROXY_KEY,





            CX_SILICONFLOW_KEY_KEY, CX_SILICONFLOW_MODEL_KEY, CX_SILICONFLOW_ENDPOINT_KEY,





            CX_LIKEAPI_SEARCH_KEY, CX_LIKEAPI_VISION_KEY, CX_LIKEAPI_MODEL_KEY,





            CX_GO_AUTHORIZATION_KEY, CX_GO_MIN_INTERVAL_KEY, CX_TIKU_ADAPTER_URL_KEY,





            CX_NOTIFY_PROVIDER_KEY, CX_NOTIFY_URL_KEY, CX_NOTIFY_TG_CHAT_ID_KEY,





            CX_TASK_LOG_KEY, CX_MESSAGES_JSON_KEY, CX_MESSAGES_CURSOR_KEY, CX_MESSAGES_MERGE_KEY,





        )





    }





}











/**





 * \u7535\u8D39\u623F\u95F4\u914D\u7F6E (\u7A7A\u8C03/\u7167\u660E)\u3002





 * - \u8001\u533A (feeitemid 408/428): building/floor/room \u5747\u4E3A "code&name",campus \u4E3A\u7A7A\u3002





 * - \u65B0\u533A (feeitemid 488): campus/building/floor/room \u56DB\u7EA7\u5747\u4E3A "code&name"\u3002





 */





data class ElectricityRoomConfig(





    val building: String = "",





    val floor: String = "",





    val room: String = "",





    /** \u6821\u533A (\u4EC5 feeitemid=488 \u65B0\u533A\u4F7F\u7528,\u8001\u533A\u4E3A\u7A7A)\u3002\u683C\u5F0F "code&name",\u5982 "ul001002002&\u78EC\u82D1\u6821\u533A"\u3002 */





    val campus: String = ""





) {





    val isComplete: Boolean





        get() = building.isValidCodeName() &&





            floor.isValidCodeName() &&





            room.isValidCodeName() &&





            (campus.isBlank() || campus.isValidCodeName())











    private fun String.isValidCodeName(): Boolean {





        if (isBlank()) return false





        val parts = split("&", limit = 2)





        return parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()





    }





}





