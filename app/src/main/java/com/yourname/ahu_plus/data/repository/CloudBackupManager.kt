package com.yourname.ahu_plus.data.repository

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.yourname.ahu_plus.data.GsonProvider
import com.yourname.ahu_plus.data.local.AppDataStore
import com.yourname.ahu_plus.data.local.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 云端备份管理器。
 *
 * ## 备份 JSON schema (v2, 2026-06-21)
 *
 * 顶层结构: meta / settings / credentials / data / user_data / manifest
 * 层级清晰,所有时间戳 ISO 8601 + epoch ms 双写,凭据 base64 编码。
 *
 * ```json
 * {
 *   "meta":          { "schema_version": 2, "exported_at": "...", "username": "...", ... },
 *   "settings":      { "appearance": {...}, "schedule": {...}, "market": {...}, ... },
 *   "credentials":   { "cas": {...}, "chaoxing": {...}, "market": {...} },
 *   "data":          { "schedule": {"json":"...","updated_at":...}, "grades": {...}, ... },
 *   "user_data":     { "course_notes": {...}, "slot_notes": {...}, "assessments": {...}, ... },
 *   "manifest":      { "data_keys": [...], "settings_keys": [...], ... }
 * }
 * ```
 *
 * ## 触发点
 *
 * 由 [SessionManager] 中 16 个业务 `save*Json` 各自的"首次"判定触发,
 * 详见 `notifyBackupIfFirst()`。
 */
class CloudBackupManager(
    private val context: Context,
    private val sessionManager: SessionManager,
    private val appDataStore: AppDataStore,
    private val cloudStorage: CloudStorageRepository
) {

    private val gson: Gson = GsonProvider.instance
    private val backupScope = CoroutineScope(Dispatchers.IO)

    // ── 防抖备份 ──────────────────────────────────────────
    @Volatile private var pendingBackupJob: Job? = null

    /**
     * 数据变更通知。调用后防抖 5 秒自动上传。
     * 连续多次调用只触发一次上传。
     */
    fun notifyDataChanged() {
        pendingBackupJob?.cancel()
        pendingBackupJob = backupScope.launch {
            delay(DEBOUNCE_MS)
            silentBackup()
        }
    }

    // ── 用户隔离路径 ──────────────────────────────────────

    private fun resolveBackupKey(): String {
        val username = sessionManager.getUsername() ?: "anonymous"
        return "backup/$username/full_backup.json"
    }

    private fun resolveStudentField(vararg labels: String): String? {
        val json = sessionManager.getStudentInfoJson() ?: return null
        return runCatching {
            val obj = gson.fromJson(json, JsonObject::class.java)
            val basicFields = obj.getAsJsonArray("basicFields") ?: return null
            for (field in basicFields) {
                val label = field.asJsonObject.get("label")?.asString ?: continue
                if (label in labels) {
                    return@runCatching field.asJsonObject.get("value")?.asString?.takeIf { it.isNotBlank() }
                }
            }
            null
        }.getOrNull()
    }

    // ═══════════════════════════════════════════════════════
    //  静默备份
    // ═══════════════════════════════════════════════════════

    suspend fun silentBackup() {
        val username = sessionManager.getUsername()
        if (username.isNullOrBlank()) {
            Log.d(TAG, "静默备份跳过：未登录")
            return
        }
        try {
            val json = exportToJson()
            val key = resolveBackupKey()
            cloudStorage.uploadString(key, json, "application/json")
            Log.i(TAG, "静默备份成功: key=$key, ${json.length} bytes")
        } catch (e: Exception) {
            Log.w(TAG, "静默备份失败: ${e.message}")
        }
    }

    /**
     * 登录触发的备份（受每日一次节流控制）。
     * 在 CAS 自动登录 / 手动登录 / 超星登录成功后调用。
     * 全程静默，不通知用户。
     */
    suspend fun backupOnLogin() {
        val username = sessionManager.getUsername()
        if (username.isNullOrBlank()) return

        val prefs = appDataStore.dataStore.data.first()
        val lastBackup = prefs[LAST_BACKUP_TIMESTAMP_KEY] ?: 0L
        val now = System.currentTimeMillis()
        if (now - lastBackup < ONE_DAY_MS) {
            Log.d(TAG, "登录备份跳过：距上次不足24h (${(now - lastBackup) / 3600_000}h)")
            return
        }

        Log.i(TAG, "登录触发备份")
        silentBackup()
        appDataStore.dataStore.edit { it[LAST_BACKUP_TIMESTAMP_KEY] = now }
    }

    // ═══════════════════════════════════════════════════════
    //  导出：本地 → JSON (schema v2)
    // ═══════════════════════════════════════════════════════

    suspend fun exportToJson(): String = withContext(Dispatchers.IO) {
        val prefs = appDataStore.dataStore.data.first()
        val nowMs = System.currentTimeMillis()
        val nowIso = formatIso8601(nowMs)

        val meta = JsonObject().apply {
            addProperty("schema_version", SCHEMA_VERSION)
            addProperty("app", "ahu_plus")
            addProperty("exported_at", nowIso)
            addProperty("exported_at_ms", nowMs)
            addProperty("username", sessionManager.getUsername() ?: "")
            addProperty("student_id", resolveStudentField("学号", "学工号") ?: "")
            addProperty("student_name", resolveStudentField("姓名", "学生姓名") ?: "")
        }

        val settings = buildSettings(prefs)
        val credentials = buildCredentials(prefs)
        val data = buildData(prefs)
        val userData = buildUserData(prefs)
        val manifest = buildManifest(settings, credentials, data, userData)

        val root = JsonObject().apply {
            add("meta", meta)
            add("settings", settings)
            add("credentials", credentials)
            add("data", data)
            add("user_data", userData)
            add("manifest", manifest)
        }

        gson.toJson(root)
    }

    /** 构造 settings 段:按类别分组(appearance/schedule/market/ai_comment/chaoxing/electricity/navigation) */
    private fun buildSettings(prefs: Preferences): JsonObject = JsonObject().apply {
        add("appearance", JsonObject().apply {
            putStr(prefs, stringPreferencesKey("theme_mode"))
        })
        add("schedule", JsonObject().apply {
            putNumOrStr(prefs, stringPreferencesKey("schedule_col_width"))
            putNumOrStr(prefs, stringPreferencesKey("schedule_row_height"))
            putNumOrStr(prefs, stringPreferencesKey("schedule_font_scale"))
            putBoolStr(prefs, stringPreferencesKey("schedule_show_sat"))
            putBoolStr(prefs, stringPreferencesKey("schedule_show_sun"))
            putBoolStr(prefs, stringPreferencesKey("schedule_pager_enabled"))
            putBoolStr(prefs, stringPreferencesKey("schedule_reset_on_enter"))
            putBoolStr(prefs, stringPreferencesKey("schedule_show_completed_tasks"))
            putBoolStr(prefs, stringPreferencesKey("schedule_show_completed_exams"))
        })
        add("market", JsonObject().apply {
            putStr(prefs, stringPreferencesKey("market_identities"))
            putStr(prefs, stringPreferencesKey("market_selected_ids"))
            putBoolStr(prefs, stringPreferencesKey("market_block_pinned"))
            putStr(prefs, stringPreferencesKey("market_block_keywords"))
            putStr(prefs, stringPreferencesKey("market_filter_nodes"))
            putBoolStr(prefs, stringPreferencesKey("market_enabled"))
            putStr(prefs, stringPreferencesKey("market_list_layout_mode"))
            putBoolStr(prefs, stringPreferencesKey("market_scroll_to_top"))
        })
        add("ai_comment", JsonObject().apply {
            putBoolStr(prefs, stringPreferencesKey("ai_comment_enabled"))
            putStr(prefs, stringPreferencesKey("ai_comment_model"))
            putStr(prefs, stringPreferencesKey("ai_comment_style"))
            putStr(prefs, stringPreferencesKey("ai_comment_overall_prompt"))
            putStr(prefs, stringPreferencesKey("ai_comment_style_prompts"))
            putStr(prefs, stringPreferencesKey("ai_comment_templates_json"))
            putStr(prefs, stringPreferencesKey("ai_comment_selected_template"))
        })
        add("chaoxing", buildChaoxingSettings(prefs))
        add("electricity", JsonObject().apply {
            putStr(prefs, stringPreferencesKey("bathroom_phone"))
            putStr(prefs, stringPreferencesKey("ac_config"))
            putStr(prefs, stringPreferencesKey("lighting_config"))
            putStr(prefs, stringPreferencesKey("new_campus_config"))
        })
        add("navigation", JsonObject().apply {
            putStr(prefs, stringPreferencesKey("recent_apps"))
        })
    }

    /** 超星学习通设置(36 个 key 全覆盖) */
    private fun buildChaoxingSettings(prefs: Preferences): JsonObject = JsonObject().apply {
        putNumOrStr(prefs, stringPreferencesKey("cx_speed"))
        putNumOrStr(prefs, stringPreferencesKey("cx_concurrency"))
        putStr(prefs, stringPreferencesKey("cx_notopen_action"))
        putBoolStr(prefs, stringPreferencesKey("cx_auto_sign"))
        putStr(prefs, stringPreferencesKey("cx_submit_mode"))
        putStr(prefs, stringPreferencesKey("cx_tiku_type"))
        putStr(prefs, stringPreferencesKey("cx_task_types"))
        putStr(prefs, stringPreferencesKey("cx_ai_base_url"))
        putStr(prefs, stringPreferencesKey("cx_ai_model"))
        add("sign", JsonObject().apply {
            putNumOrStr(prefs, stringPreferencesKey("cx_sign_lat"))
            putNumOrStr(prefs, stringPreferencesKey("cx_sign_lon"))
            putStr(prefs, stringPreferencesKey("cx_sign_address"))
            putStr(prefs, stringPreferencesKey("cx_sign_gesture"))
        })
        add("tiku", JsonObject().apply {
            putStr(prefs, stringPreferencesKey("cx_provider_chain"))
            putStr(prefs, stringPreferencesKey("cx_tokens_yanxi"))
            putNumOrStr(prefs, stringPreferencesKey("cx_cover_rate"))
            putNumOrStr(prefs, stringPreferencesKey("cx_tiku_delay"))
            putNumOrStr(prefs, stringPreferencesKey("cx_ai_min_interval"))
            putStr(prefs, stringPreferencesKey("cx_ai_http_proxy"))
            putStr(prefs, stringPreferencesKey("cx_tiku_adapter_url"))
        })
        add("siliconflow", JsonObject().apply {
            putStr(prefs, stringPreferencesKey("cx_siliconflow_key"))
            putStr(prefs, stringPreferencesKey("cx_siliconflow_model"))
            putStr(prefs, stringPreferencesKey("cx_siliconflow_endpoint"))
        })
        add("likeapi", JsonObject().apply {
            putBoolStr(prefs, stringPreferencesKey("cx_likeapi_search"))
            putBoolStr(prefs, stringPreferencesKey("cx_likeapi_vision"))
            putStr(prefs, stringPreferencesKey("cx_likeapi_model"))
        })
        add("go", JsonObject().apply {
            putStr(prefs, stringPreferencesKey("cx_go_authorization"))
            putNumOrStr(prefs, stringPreferencesKey("cx_go_min_interval"))
        })
        add("notify", JsonObject().apply {
            putStr(prefs, stringPreferencesKey("cx_notify_provider"))
            putStr(prefs, stringPreferencesKey("cx_notify_url"))
            putStr(prefs, stringPreferencesKey("cx_notify_tg_chat_id"))
        })
    }

    /** 构造 credentials 段(全部 base64 编码或保持明文 session) */
    private fun buildCredentials(prefs: Preferences): JsonObject = JsonObject().apply {
        add("cas", JsonObject().apply {
            putStr(prefs, stringPreferencesKey("username"))
            putB64Raw(prefs, stringPreferencesKey("password"))
            putStr(prefs, stringPreferencesKey("jsessionid"))
            putStr(prefs, stringPreferencesKey("jw_session_id"))
            putStr(prefs, stringPreferencesKey("jw_pst_sid"))
            putStr(prefs, stringPreferencesKey("adwmh_jsessionid"))
        })
        add("chaoxing", JsonObject().apply {
            putB64Raw(prefs, stringPreferencesKey("cx_cookies"))
            putB64Raw(prefs, stringPreferencesKey("cx_tiku_token"))
            putB64Raw(prefs, stringPreferencesKey("cx_ai_key"))
            putStr(prefs, stringPreferencesKey("cx_phone"))
            putB64Raw(prefs, stringPreferencesKey("cx_password"))
        })
        add("market", JsonObject().apply {
            putStr(prefs, stringPreferencesKey("market_api_identity"))
        })
    }

    /** 构造 data 段(业务缓存,每类带 json + updated_at) */
    private fun buildData(prefs: Preferences): JsonObject = JsonObject().apply {
        // 学习通
        add("chaoxing_courses", JsonObject().apply {
            putStr(prefs, stringPreferencesKey("cx_courses_json"))
        })
        add("chaoxing_messages", JsonObject().apply {
            putStr(prefs, stringPreferencesKey("cx_messages_json"))
            putStr(prefs, stringPreferencesKey("cx_messages_cursor"))
        })
        putStr(prefs, stringPreferencesKey("cx_tiku_config"))
        add("chaoxing_tiku_cache", JsonObject().apply {
            // 题库缓存:question title → answer 的 Map (JSON 字符串)
            val raw = prefs[stringPreferencesKey("cx_tiku_cache")]
            if (!raw.isNullOrBlank()) {
                addProperty("raw", raw)
            }
        })
        add("chaoxing_settings_meta", JsonObject().apply {
            putStr(prefs, stringPreferencesKey("cx_task_log"))
        })
        add("chaoxing_messages_meta", JsonObject().apply {
            putBoolStr(prefs, stringPreferencesKey("cx_messages_merge"))
        })

        // 学生一张表
        add("student_info", cacheEntry(prefs, "student_info_json", "student_info_updated_at"))
    }

    /** 标准缓存条目(json + updated_at) */
    private fun cacheEntry(prefs: Preferences, jsonKey: String, updatedAtKey: String): JsonObject =
        JsonObject().apply {
            putStr(prefs, stringPreferencesKey(jsonKey))
            putLong(prefs, longPreferencesKey(updatedAtKey))
        }

    /** 构造 user_data 段(笔记 + 作业 + 待办 + 用户课表) */
    private fun buildUserData(prefs: Preferences): JsonObject = JsonObject().apply {
        // 课程备注(按 courseCode) - 前缀 course_note_
        add("course_notes", collectPrefixed(prefs, "course_note_", exclude = emptySet()))
        // 此节课备注(按 lessonId_week) - 前缀 slot_note_
        add("slot_notes", collectPrefixed(prefs, "slot_note_", exclude = emptySet()))
        // 考核方案(按 lessonId,存 JSON) - 前缀 assessment_,但要排除 cache 段的 assessment_json / assessment_updated_at
        add("assessments", collectPrefixed(
            prefs,
            "assessment_",
            exclude = setOf("assessment_json", "assessment_updated_at")
        ))
        // 用户自定义课表条目
        putStr(prefs, stringPreferencesKey("user_schedule_json"))
        // 作业
        add("homework", cacheEntry(prefs, "homework_json", "homework_updated_at"))
        // 用户自定义待办
        add("user_tasks", cacheEntry(prefs, "user_tasks_json", "user_tasks_updated_at"))
        // 记录索引
        add("record_index", cacheEntry(prefs, "record_index_json", "record_index_updated_at"))
    }

    /**
     * 收集所有以 [prefix] 开头的 DataStore key,写入 JsonObject。
     * [exclude] 中列出的完整 key 名会被跳过(用于排除 cache 段与 per-xxx 数据同前缀的冲突)。
     */
    private fun collectPrefixed(
        prefs: Preferences,
        prefix: String,
        exclude: Set<String>
    ): JsonObject = JsonObject().apply {
        for (key in prefs.asMap().keys) {
            val name = key.name
            if (!name.startsWith(prefix)) continue
            if (name in exclude) continue
            prefs[key]?.let { addProperty(name, it.toString()) }
        }
    }

    /** 构造 manifest 段(列出本 backup 包含的所有 key,便于审计完整性) */
    private fun buildManifest(
        settings: JsonObject,
        credentials: JsonObject,
        data: JsonObject,
        userData: JsonObject
    ): JsonObject = JsonObject().apply {
        add("settings_keys", settings.keySet().toJsonArray())
        add("credentials_keys", credentials.keySet().toJsonArray())
        add("data_keys", data.keySet().toJsonArray())
        add("user_data_keys", userData.keySet().toJsonArray())
    }

    private fun Set<String>.toJsonArray(): JsonArray {
        val arr = JsonArray()
        sorted().forEach { arr.add(it) }
        return arr
    }

    // ═══════════════════════════════════════════════════════
    //  恢复：JSON → 本地 (支持 schema v1 + v2 兼容)
    // ═══════════════════════════════════════════════════════

    suspend fun restoreFromJson(json: String) = withContext(Dispatchers.IO) {
        val root = gson.fromJson(json, JsonObject::class.java)
        val version = root.getAsJsonObject("meta")?.get("schema_version")?.asInt
            ?: root.get("version")?.asInt
            ?: 0
        if (version < 1) throw IllegalArgumentException("不支持的备份版本: $version")

        appDataStore.dataStore.edit { edit ->
            // v2 新结构(settings / credentials / data / user_data 分层)
            if (version >= 2) {
                root.getAsJsonObject("settings")?.let { restoreSettings(edit, it) }
                root.getAsJsonObject("credentials")?.let { restoreCredentials(edit, it) }
                root.getAsJsonObject("data")?.let { restoreData(edit, it) }
                root.getAsJsonObject("user_data")?.let { restoreUserData(edit, it) }
            } else {
                // v1 旧结构兼容:settings/cache/user_data/credentials 平铺
                restoreV1(edit, root)
            }
        }

        sessionManager.init()
        Log.i(TAG, "恢复完成 (schema v$version)")
    }

    /** 恢复 settings 段(按子类别扁平写回 DataStore) */
    private fun restoreSettings(edit: androidx.datastore.preferences.core.MutablePreferences, src: JsonObject) {
        src.entrySet().forEach { (categoryName, categoryValue) ->
            if (!categoryValue.isJsonObject) return@forEach
            val cat = categoryValue.asJsonObject
            // 特殊处理:chaoxing 是嵌套的,需要展开
            if (categoryName == "chaoxing") {
                restoreChaoxingSettings(edit, cat)
                return@forEach
            }
            // 其余分类:扁平键值对直接写回
            cat.entrySet().forEach { (k, v) ->
                if (v.isJsonPrimitive) {
                    edit[stringPreferencesKey(k)] = v.asString
                }
            }
        }
    }

    /** 恢复超星设置(嵌套结构展开为扁平 DataStore key) */
    private fun restoreChaoxingSettings(
        edit: androidx.datastore.preferences.core.MutablePreferences,
        src: JsonObject
    ) {
        // 顶层扁平字段
        val flatKeys = listOf(
            "cx_speed", "cx_concurrency", "cx_notopen_action", "cx_auto_sign",
            "cx_submit_mode", "cx_tiku_type", "cx_task_types",
            "cx_ai_base_url", "cx_ai_model"
        )
        flatKeys.forEach { k ->
            if (src.has(k) && src.get(k).isJsonPrimitive) {
                edit[stringPreferencesKey(k)] = src.get(k).asString
            }
        }
        // 嵌套子对象
        listOf("sign", "tiku", "siliconflow", "likeapi", "go", "notify").forEach { sub ->
            src.getAsJsonObject(sub)?.let { subObj ->
                subObj.entrySet().forEach { (k, v) ->
                    if (v.isJsonPrimitive) edit[stringPreferencesKey(k)] = v.asString
                }
            }
        }
    }

    /** 恢复 credentials 段 */
    private fun restoreCredentials(
        edit: androidx.datastore.preferences.core.MutablePreferences,
        src: JsonObject
    ) {
        // CAS 凭据(username 明文 / password base64 / 其他 session 明文)
        src.getAsJsonObject("cas")?.let { cas ->
            cas.get("username")?.takeIf { it.isJsonPrimitive }?.let {
                edit[stringPreferencesKey("username")] = it.asString
            }
            cas.get("password")?.takeIf { it.isJsonPrimitive }?.let {
                edit[stringPreferencesKey("password")] = decodeB64(it.asString)
            }
            listOf("jsessionid", "jw_session_id", "jw_pst_sid", "adwmh_jsessionid").forEach { k ->
                cas.get(k)?.takeIf { it.isJsonPrimitive }?.let {
                    edit[stringPreferencesKey(k)] = it.asString
                }
            }
        }
        // 超星凭据(cookies/token/key base64; phone 明文; password base64)
        src.getAsJsonObject("chaoxing")?.let { cx ->
            listOf("cookies", "tiku_token", "ai_key").forEach { k ->
                cx.get(k)?.takeIf { it.isJsonPrimitive }?.let {
                    edit[stringPreferencesKey("cx_$k")] = decodeB64(it.asString)
                }
            }
            cx.get("phone")?.takeIf { it.isJsonPrimitive }?.let {
                edit[stringPreferencesKey("cx_phone")] = it.asString
            }
            cx.get("password")?.takeIf { it.isJsonPrimitive }?.let {
                edit[stringPreferencesKey("cx_password")] = decodeB64(it.asString)
            }
        }
        // 集市 API 身份字段
        src.getAsJsonObject("market")?.get("api_identity")?.takeIf { it.isJsonPrimitive }?.let {
            edit[stringPreferencesKey("market_api_identity")] = it.asString
        }
    }

    /** 恢复 data 段(每类带 json + updated_at) */
    private fun restoreData(
        edit: androidx.datastore.preferences.core.MutablePreferences,
        src: JsonObject
    ) {
        // 学习通课程
        src.getAsJsonObject("chaoxing_courses")?.let {
            it.get("json")?.takeIf { v -> v.isJsonPrimitive }?.let { v ->
                edit[stringPreferencesKey("cx_courses_json")] = v.asString
            }
        }
        // 学习通消息
        src.getAsJsonObject("chaoxing_messages")?.let { msg ->
            msg.get("json")?.takeIf { v -> v.isJsonPrimitive }?.let { v ->
                edit[stringPreferencesKey("cx_messages_json")] = v.asString
            }
            msg.get("cursor")?.takeIf { v -> v.isJsonPrimitive }?.let { v ->
                edit[stringPreferencesKey("cx_messages_cursor")] = v.asString
            }
        }
        // cx_tiku_config 是顶层 string
        src.get("cx_tiku_config")?.takeIf { it.isJsonPrimitive }?.let {
            edit[stringPreferencesKey("cx_tiku_config")] = it.asString
        }
        // 题库缓存(raw 字段是 JSON 字符串)
        src.getAsJsonObject("chaoxing_tiku_cache")?.get("raw")?.takeIf { it.isJsonPrimitive }?.let {
            edit[stringPreferencesKey("cx_tiku_cache")] = it.asString
        }
        // 任务日志 / 消息合并
        src.getAsJsonObject("chaoxing_settings_meta")?.get("cx_task_log")?.takeIf { it.isJsonPrimitive }?.let {
            edit[stringPreferencesKey("cx_task_log")] = it.asString
        }
        src.getAsJsonObject("chaoxing_messages_meta")?.get("cx_messages_merge")?.takeIf { it.isJsonPrimitive }?.let {
            edit[stringPreferencesKey("cx_messages_merge")] = it.asString
        }

        // 学生一张表
        restoreCacheEntry(edit, src, "student_info", "student_info_json", "student_info_updated_at")
    }

    /** 恢复标准 cache 条目({json, updated_at} → 2 个 DataStore key) */
    private fun restoreCacheEntry(
        edit: androidx.datastore.preferences.core.MutablePreferences,
        src: JsonObject,
        nodeName: String,
        jsonKey: String,
        updatedAtKey: String
    ) {
        val node = src.getAsJsonObject(nodeName) ?: return
        node.get("json")?.takeIf { it.isJsonPrimitive }?.let {
            edit[stringPreferencesKey(jsonKey)] = it.asString
        }
        node.get("updated_at")?.takeIf { it.isJsonPrimitive }?.let {
            edit[longPreferencesKey(updatedAtKey)] = it.asString.toLongOrNull() ?: 0L
        }
    }

    /** 恢复 user_data 段 */
    private fun restoreUserData(
        edit: androidx.datastore.preferences.core.MutablePreferences,
        src: JsonObject
    ) {
        // 课程笔记(直接是 course_note_<code> 形式的 key)
        restorePrefixedMap(edit, src.getAsJsonObject("course_notes"))
        // 此节课笔记
        restorePrefixedMap(edit, src.getAsJsonObject("slot_notes"))
        // 考核方案(assessment_<lessonId>)
        restorePrefixedMap(edit, src.getAsJsonObject("assessments"))

        // 用户自定义课表条目
        src.get("user_schedule_json")?.takeIf { it.isJsonPrimitive }?.let {
            edit[stringPreferencesKey("user_schedule_json")] = it.asString
        }
        // 作业
        restoreCacheEntry(edit, src, "homework", "homework_json", "homework_updated_at")
        // 用户待办
        restoreCacheEntry(edit, src, "user_tasks", "user_tasks_json", "user_tasks_updated_at")
        // 记录索引
        restoreCacheEntry(edit, src, "record_index", "record_index_json", "record_index_updated_at")
    }

    /** 把 JsonObject 的所有 key-value 直接写回 DataStore(用于 per-key 的笔记) */
    private fun restorePrefixedMap(
        edit: androidx.datastore.preferences.core.MutablePreferences,
        src: JsonObject?
    ) {
        if (src == null) return
        src.entrySet().forEach { (k, v) ->
            if (v.isJsonPrimitive) edit[stringPreferencesKey(k)] = v.asString
        }
    }

    /** v1 schema 兼容(扁平结构) */
    private fun restoreV1(
        edit: androidx.datastore.preferences.core.MutablePreferences,
        root: JsonObject
    ) {
        root.getAsJsonObject("settings")?.let { settings ->
            for ((key, value) in settings.entrySet()) {
                if (value.isJsonPrimitive) edit[stringPreferencesKey(key)] = value.asString
            }
        }
        root.getAsJsonObject("cache")?.let { cache ->
            for ((key, value) in cache.entrySet()) {
                if (!value.isJsonPrimitive) continue
                val v = value.asString
                if (key.endsWith("_updated_at")) {
                    edit[longPreferencesKey(key)] = v.toLongOrNull() ?: 0L
                } else {
                    edit[stringPreferencesKey(key)] = v
                }
            }
        }
        root.getAsJsonObject("user_data")?.let { userData ->
            userData.getAsJsonObject("notes")?.let { notes ->
                for ((key, value) in notes.entrySet()) {
                    if (value.isJsonPrimitive) edit[stringPreferencesKey(key)] = value.asString
                }
            }
            for (key in listOf("homework_json", "user_tasks_json", "record_index_json", "user_schedule_json")) {
                userData.get(key)?.takeIf { it.isJsonPrimitive }?.let {
                    edit[stringPreferencesKey(key)] = it.asString
                }
            }
            for (key in listOf("homework_updated_at", "user_tasks_updated_at", "record_index_updated_at")) {
                userData.get(key)?.takeIf { it.isJsonPrimitive }?.let {
                    edit[longPreferencesKey(key)] = it.asString.toLongOrNull() ?: 0L
                }
            }
        }
        root.getAsJsonObject("credentials")?.let { creds ->
            creds.get("username")?.takeIf { it.isJsonPrimitive }?.let {
                edit[stringPreferencesKey("username")] = it.asString
            }
            for (key in listOf("password", "cx_cookies", "cx_tiku_token", "cx_ai_key")) {
                creds.get("${key}_b64")?.takeIf { it.isJsonPrimitive }?.let {
                    edit[stringPreferencesKey(key)] = decodeB64(it.asString)
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    //  云端操作（带 Result）
    // ═══════════════════════════════════════════════════════

    suspend fun backupToCloud(): Result<String> = runCatching {
        val json = exportToJson()
        val key = resolveBackupKey()
        cloudStorage.uploadString(key, json, "application/json")
        Log.i(TAG, "备份上传成功: key=$key, ${json.length} bytes")
        "备份成功（${json.length / 1024}KB）"
    }

    suspend fun restoreFromCloud(): Result<String> = runCatching {
        val key = resolveBackupKey()
        val json = cloudStorage.downloadAsString(key)
        restoreFromJson(json)
        Log.i(TAG, "云端恢复完成: key=$key")
        "恢复成功"
    }

    // ═══════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════

    private fun JsonObject.putStr(prefs: Preferences, key: Preferences.Key<String>) {
        prefs[key]?.let { addProperty(key.name, it) }
    }

    private fun JsonObject.putLong(prefs: Preferences, key: Preferences.Key<Long>) {
        prefs[key]?.let { addProperty(key.name, it) }
    }

    /** 写入 base64 编码字段(后缀 _b64,供人工审计/grep 标识) */
    private fun JsonObject.putB64Raw(prefs: Preferences, key: Preferences.Key<String>) {
        prefs[key]?.let {
            addProperty(key.name, Base64.encodeToString(it.toByteArray(), Base64.NO_WRAP))
        }
    }

    /** 数字或字符串:DataStore 内部存 String,但用户期望导出时保持原始数字形态(便于审计) */
    private fun JsonObject.putNumOrStr(prefs: Preferences, key: Preferences.Key<String>) {
        prefs[key]?.let { addProperty(key.name, it) }
    }

    /** 布尔:DataStore 内部存 "true"/"false" 字符串 */
    private fun JsonObject.putBoolStr(prefs: Preferences, key: Preferences.Key<String>) {
        prefs[key]?.let { addProperty(key.name, it) }
    }

    private fun decodeB64(b64: String): String = runCatching {
        String(Base64.decode(b64, Base64.DEFAULT))
    }.getOrDefault("")

    private fun formatIso8601(epochMs: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(epochMs))
    }

    companion object {
        private const val TAG = "CloudBackupManager"
        private const val DEBOUNCE_MS = 5000L
        private const val ONE_DAY_MS = 24L * 60 * 60 * 1000
        /** 当前 schema 版本。v2: 2026-06-21 分层结构; v3: 2026-06-22 精简数据+超星凭据 */
        const val SCHEMA_VERSION = 3
        private val LAST_BACKUP_TIMESTAMP_KEY = longPreferencesKey("last_backup_timestamp")
    }
}
