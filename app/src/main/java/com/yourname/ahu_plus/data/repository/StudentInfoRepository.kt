package com.yourname.ahu_plus.data.repository

import android.util.Log
import com.google.gson.JsonObject
import com.yourname.ahu_plus.data.GsonProvider
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.model.StudentInfo
import com.yourname.ahu_plus.data.model.StudentInfoCodeLookup
import com.yourname.ahu_plus.data.model.StudentInfoField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StudentInfoRepository(
    private val sessionManager: SessionManager,
    private val casAuthRepository: CasAuthRepository,
    private val client: StudentTableClient = StudentTableClient(casAuthRepository)
) {
    private val gson = GsonProvider.instance

    suspend fun getStudentInfo(): Result<StudentInfo> {
        return try {
            withContext(Dispatchers.IO) {
                client.activateSession()
                val username = sessionManager.getUsername()
                    ?: return@withContext Result.failure(Exception("未找到当前账号"))
                client.activateStudentRole()
                val userInfo = fetchUserInfo(username)
                val dataItems = client.getAllDataItems()

                // 表单数据优先（服务端正统中文标签），getUserInfo 仅补充独有字段
                // 先对 form 字段做码值解码，使得 label/value 与 getUserInfo 对齐后再去重
                val formBasic = decodeFields(fetchNamedFields(dataItems, "学生基本信息", username))
                val formLabelSet = formBasic.map { it.label }.toSet()
                val extraUserFields = decodeFields(userInfo).filter { it.label !in formLabelSet }
                val basicFields = (formBasic + extraUserFields).distinctBy { it.value }

                // 住宿数据：getList API 返回已解析的中文名（JZWH="榴园" 而非 "56"）
                val housingFields = fetchHousingListFields(dataItems, username)

                val academicWarningFields = fetchNamedFields(dataItems, "学业预警信息", username)

                if (basicFields.isEmpty() && housingFields.isEmpty() && academicWarningFields.isEmpty()) {
                    Result.failure(Exception("未获取到任何学生一张表数据"))
                } else {
                    val info = StudentInfo(
                        basicFields = basicFields,
                        housingFields = housingFields,
                        academicWarningFields = academicWarningFields
                    )
                    persistToCache(info)
                    Result.success(info)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "student info error", e)
            Result.failure(e)
        }
    }

    /** 读取本地缓存,无网络请求 */
    fun readCachedStudentInfo(): StudentInfo? {
        val json = sessionManager.getStudentInfoJson() ?: return null
        return runCatching {
            gson.fromJson(json, StudentInfo::class.java)
        }.getOrNull()
    }

    private suspend fun persistToCache(info: StudentInfo) {
        val json = runCatching { gson.toJson(info) }.getOrNull() ?: return
        sessionManager.saveStudentInfoJson(json)
    }

    // ── fetchUserInfo ──────────────────────────────────────

    /**
     * 取 getUserInfo 返回的非空字段。
     * - 英文 key 映射为中文标签
     * - 跳过 [StudentInfoCodeLookup.skipUserInfoKeys] 中的冗余字段
     * - 码值通过 StudentInfoCodeLookup 解码
     * - BIRTHDAY 是 Unix 时间戳(ms)，转为日期字符串
     */
    private fun fetchUserInfo(username: String): List<StudentInfoField> {
        val json = client.fetchUserInfoRaw(username)
        val out = mutableListOf<StudentInfoField>()
        for ((key, element) in json.entrySet()) {
            if (element == null || element.isJsonNull) continue
            if (key in StudentInfoCodeLookup.skipUserInfoKeys) continue

            val rawValue = runCatching { element.asString }.getOrNull()?.takeIf { it.isNotBlank() }
            // BIRTHDAY 是 Long 型时间戳
            val value: String = rawValue
                ?: runCatching {
                    val ts = element.asLong
                    if (ts > 0) formatBirthday(ts) else null
                }.getOrNull()
                ?: continue

            val label = StudentInfoCodeLookup.resolveUserInfoLabel(key) ?: key
            // 解码码值（性别/民族/政治面貌等）
            val decoded = StudentInfoCodeLookup.decode(label, value)
            if (decoded != null) {
                out.add(StudentInfoField(decoded.first, decoded.second))
            } else {
                out.add(StudentInfoField(label, value))
            }
        }
        return out
    }

    private fun formatBirthday(timestampMs: Long): String {
        return runCatching {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            sdf.format(java.util.Date(timestampMs))
        }.getOrElse { timestampMs.toString() }
    }

    // ── 住宿数据 (getList API) ──────────────────────────────

    /**
     * 住宿数据使用 list 型 API（getListData），因为 form API 返回码值
     * （校区=5、楼栋=56），而 getList 返回已解析的中文名（JZWH="榴园"、
     * XQH="磬苑校区"）。
     */
    private fun fetchHousingListFields(
        dataItems: List<JsonObject>,
        username: String
    ): List<StudentInfoField> {
        val item = dataItems.firstOrNull {
            it.get("NAME")?.asString == "住宿数据" || it.get("DATATYPENAME")?.asString == "住宿数据"
        } ?: return emptyList()

        val path = item.get("CKLZ")?.asString?.takeIf { it.isNotBlank() } ?: return emptyList()
        val pageContext = client.getPageContext(path) ?: return emptyList()

        val resp = runCatching {
            client.getListData(pageContext, pageNum = 1, pageSize = 1)
        }.getOrNull() ?: return emptyList()

        val listArr = resp.getAsJsonArray("list") ?: return emptyList()
        if (listArr.size() == 0) return emptyList()
        val row = listArr.get(0)?.asJsonObject ?: return emptyList()

        val fields = mutableListOf<StudentInfoField>()
        for ((key, element) in row.entrySet()) {
            if (element == null || element.isJsonNull) continue
            if (key in HOUSING_SKIP_KEYS || key.endsWith("_CPCODE")) continue
            val value = runCatching { element.asString }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: continue
            val label = HOUSING_LABEL_MAP[key] ?: key
            val decoded = StudentInfoCodeLookup.decode(label, value)
            if (decoded != null) {
                fields.add(StudentInfoField(decoded.first, decoded.second))
            } else {
                fields.add(StudentInfoField(label, value))
            }
        }
        return fields
    }

    /**
     * 对字段列表应用 [StudentInfoCodeLookup] 解码，隐藏内部字段。
     * 解码在 Repository 层完成，以便后续去重逻辑能识别同一信息的码值/显示值两种形态。
     */
    private fun decodeFields(fields: List<StudentInfoField>): List<StudentInfoField> {
        return fields.mapNotNull { field ->
            if (StudentInfoCodeLookup.isHiddenField(field.label)) null
            else {
                val decoded = StudentInfoCodeLookup.decode(field.label, field.value)
                if (decoded != null) StudentInfoField(decoded.first, decoded.second) else field
            }
        }
    }

    // ── 通用 form 型 API ────────────────────────────────────

    /**
     * form 型 API (getDataItemFields)。
     * parseFormFields 已优先取 _NAME 后缀字段，但 updatedata 未必有 _NAME
     * （如学生基本信息中性别码/民族码只有原始码值），因此 UI 层通过
     * [StudentInfoCodeLookup.decode] 做二次解码。
     */
    private fun fetchNamedFields(
        dataItems: List<JsonObject>,
        itemName: String,
        username: String
    ): List<StudentInfoField> {
        val item = dataItems.firstOrNull {
            it.get("NAME")?.asString == itemName || it.get("DATATYPENAME")?.asString == itemName
        } ?: return emptyList()
        val total = item.get("TOTE")?.asString
        val fields = runCatching { client.getDataItemFields(item, username) }.getOrNull().orEmpty()
        return if (fields.isEmpty() && !total.isNullOrBlank() && total != "0") {
            listOf(StudentInfoField("记录数", total))
        } else {
            fields
        }
    }

    companion object {
        private const val TAG = "StudentInfo"

        /** getList 住宿数据: 字段名 → 中文标签 */
        private val HOUSING_LABEL_MAP: Map<String, String> = mapOf(
            "JZWH" to "楼栋",
            "SSFJH" to "宿舍房间",
            "XQH" to "校区",
            "CWH" to "床位",
            "XYBH_NAME" to "学院",
            "XH" to "学号",
            "USER_NAME" to "姓名",
        )

        /** getList 住宿数据: 不展示的内部字段 */
        private val HOUSING_SKIP_KEYS: Set<String> = setOf(
            "ROWNUM", "RESOURCE_ID", "ROW_ID", "SFDR",
            "GSR_VALUE", "SPJG_VALUE", "SPJG", "SPJG_CPCODE",
            "IS_VALID", "JZGBH", "XYBH", "RKFS", "RKFS_VALUE",
        )
    }
}
