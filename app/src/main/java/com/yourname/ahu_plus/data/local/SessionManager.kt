package com.yourname.ahu_plus.data.local

import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
        cachedMarketApiIdentity = prefs[MARKET_API_IDENTITY_KEY]
        cachedThemeMode = AppThemeMode.fromStorageValue(prefs[THEME_MODE_KEY])
        cachedStudentInfoJson = prefs[STUDENT_INFO_KEY]
        cachedStudentInfoUpdatedAt = prefs[STUDENT_INFO_UPDATED_AT_KEY] ?: 0L
        initialized = true
        Log.i(
            TAG, "init done: session=${cachedSessionId?.take(8)}... " +
                "jw=${cachedJwSessionId?.take(8)}... user=$cachedUsername"
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

    // ── 集市 API 身份字段 ───────────────────────────────

    fun getMarketApiIdentity(): String? = cachedMarketApiIdentity

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

    /** 清除所有数据(session + 凭据 + JW session) — 用户主动退出登录时调用 */
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

    private companion object {
        const val TAG = "SessionManager"
    }
}

