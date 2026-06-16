package com.yourname.ahu_plus.data.repository

import android.util.Log
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.yourname.ahu_plus.data.GsonProvider
import com.yourname.ahu_plus.data.model.StudentInfoField
import com.yourname.ahu_plus.data.network.SecureHttpClientFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 「学生一张表」公共客户端。
 *
 * 把 [StudentInfoRepository] 内部原本散落的辅助方法
 * (激活会话/拉数据项清单/拉单个数据项字段/解析 viewlist/解析 form 等) 抽到本类，
 * 供 [StudentInfoRepository] / [FinanceRepository] / [AttendanceRepository] 复用。
 *
 * 通信链路:
 *   1. 复用 [CasAuthRepository] 的 CookieJar + session
 *   2. CAS → ST ticket → tp_ep_stu session
 *   3. POST /ep/data/database/getAllDataItem 拿 33 项数据项清单
 *   4. 对每个数据项: GET /cp/templateList/p/{menuId} 拿 page_id 等
 *      - 表单型: POST /cp/templateButtonForm/getFormHtml 拿字段
 *      - 列表型: POST /cp/templateList/getList 拿 rows
 */
class StudentTableClient(
    private val casAuthRepository: CasAuthRepository,
    private val studentTableUrl: String = STUDENT_TABLE_URL,
    private val roleId: String = STUDENT_ROLE_ID
) {
    private val gson = GsonProvider.instance

    private val client: OkHttpClient = SecureHttpClientFactory.create(
        cookieJar = casAuthRepository.getCookieJar(),
        followRedirects = true,
        disableGzip = false
    )

    /**
     * 确保 (a) CAS session 有效 (b) tp_ep_stu service ticket 已换
     * 调用方必须在网络请求前先调本方法。
     */
    suspend fun activateSession() {
        casAuthRepository.ensureValidSession().getOrThrow()
        casAuthRepository.authenticateService(studentTableUrl).getOrThrow()
    }

    /**
     * GET 一次 `view?m=ep&role=...&type=1` 记录当前角色为学生。
     * 服务端依赖此 referer 来过滤数据项权限。
     */
    fun activateStudentRole() {
        client.newCall(
            Request.Builder()
                .url("$TP_EP_BASE/view?m=ep&role=$roleId&type=1")
                .header("User-Agent", UA)
                .build()
        ).execute().use { /* 触发服务端记录当前角色 */ }
    }

    /**
     * 拉取当前角色可见的全部 33 个数据项清单。
     * 调用方按 `NAME` / `DATATYPENAME` 字段匹配。
     */
    fun getAllDataItems(): List<JsonObject> {
        val json = postJson(
            path = "/ep/data/database/getAllDataItem",
            body = mapOf(
                "type" to "wdsj",
                "role" to roleId,
                "mydata_is_hide_nodatasdiv" to "1"
            ),
            referer = "$TP_EP_BASE/view?m=ep&role=$roleId&type=1#act=ep/data/database"
        ).asJsonObject

        return json.getAsJsonArray("SJXLIST")
            ?.mapNotNull { it.takeIf { item -> item.isJsonObject }?.asJsonObject }
            ?: emptyList()
    }

    /**
     * 调 /ep/userHome/getUserInfo 取「我的摘要」JSON (~19 字段)。
     * 字段名直接当展示 label,数值直接当 value。
     */
    fun fetchUserInfoRaw(username: String): JsonObject {
        val referer = "$TP_EP_BASE/view?m=ep&role=$roleId&type=1"
        return postJson(
            path = "/ep/userHome/getUserInfo",
            body = mapOf("ID_NUMBER" to username),
            referer = referer
        ).asJsonObject
    }

    /**
     * 拉取单个数据项的字段（key-value 形态）。
     *
     * - 字段来源: /cp/templateButtonForm/getFormHtml 返回的 updatedata
     * - 字段标签: /cp/templateButtonForm/getFormHtml 返回的 viewlist 解析
     * - 数据项无内容（updatedata 空）时返回空列表
     */
    fun getDataItemFields(item: JsonObject, username: String): List<StudentInfoField> {
        val path = item.get("CKLZ")?.asString?.takeIf { it.isNotBlank() } ?: return emptyList()
        val detail = fetchDetailPage(path)
        val formId = fetchFormId(detail) ?: return emptyList()
        val formJson = fetchFormHtml(detail, formId, username)
        return parseFormFields(formJson)
    }

    /**
     * 进入详情页 HTML，提取 cp.templatelist 暴露的运行时上下文。
     * 列表型数据项调用 [getListData] 时需要这些字段。
     */
    fun getPageContext(menuPath: String): PageContext? {
        val detail = fetchDetailPage(menuPath)
        val pageId = extractJsValue(detail.html, "page_id") ?: return null
        return PageContext(
            pageId = pageId,
            menuId = extractJsValue(detail.html, "menu_id") ?: detail.menuId,
            systemType = extractJsValue(detail.html, "system_type") ?: "ep",
            templateId = extractJsValue(detail.html, "template_id").orEmpty(),
            workflowId = extractJsValue(detail.html, "workflow_id").orEmpty(),
            workflowNodeId = extractJsValue(detail.html, "workflow_node_id").orEmpty(),
        )
    }

    /**
     * 调 /cp/templateList/getList 拉列表数据。
     *
     * 实测需要至少这些字段（缺一个 total 就返回 0）：
     *  - draw (递增) / bSort / fixedColumns
     *  - INSTANCE_STATE / CURRENT_NODE / CREATE_TIME_BEGIN/_END / LAST_RESULT (cp_vant_query_hidden 等价字段)
     *  - is_i18n = "false"  (空串会失败)
     *  - referer hash 必须是 #act=ep/data/database (不是 #act=cp/templateList/p/...)
     *
     * @return 服务端完整响应 JSON（pageNum/total/list 等），由调用方解析
     */
    fun getListData(
        pageContext: PageContext,
        pageNum: Int = 1,
        pageSize: Int = 50,
        condition: String = ""
    ): JsonObject {
        // referer hash 用 ep/data/database —— 实测前端 SPA 在数据列表页发起 list 请求
        val referer = "$TP_EP_BASE/view?m=ep&role=$roleId&type=1#act=ep/data/database"
        val body = mapOf(
            "pageid" to pageContext.pageId,
            "menuid" to pageContext.menuId,
            "systemType" to pageContext.systemType,
            "tab_id" to "",
            "workflow_id" to pageContext.workflowId,
            "workflow_node_id" to pageContext.workflowNodeId,
            "workflow_type" to "",
            "cp_language" to "",
            "is_i18n" to "false",
            "order" to emptyList<Int>(),
            "pageNum" to pageNum,
            "pageSize" to pageSize,
            "start" to (pageNum - 1) * pageSize,
            "length" to pageSize,
            "draw" to 1,
            "bSort" to true,
            "fixedColumns" to "",
            "INSTANCE_STATE" to "",
            "CURRENT_NODE" to "",
            "CREATE_TIME_BEGIN" to "",
            "CREATE_TIME_END" to "",
            "LAST_RESULT" to "",
        ) + if (condition.isNotBlank()) mapOf("condition" to condition) else emptyMap()
        val resp = postJson(path = "/cp/templateList/getList", body = body, referer = referer)
        return resp.asJsonObject
    }

    // ─── 内部辅助 ──────────────────────────────────────

    private data class DetailPage(
        val html: String,
        val menuId: String,
        val pageId: String?,
        val templateId: String?
    )

    private fun fetchDetailPage(path: String): DetailPage {
        val normalizedPath = path.trimStart('/')
        val request = Request.Builder()
            .url("$TP_EP_BASE/$normalizedPath")
            .header("User-Agent", UA)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", "$TP_EP_BASE/view?m=ep&role=$roleId&type=1#act=ep/data/database")
            .build()

        client.newCall(request).execute().use { response ->
            val html = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw Exception("学生一张表明细页错误 HTTP ${response.code}: $path")
            }
            val menuId = normalizedPath.substringAfterLast('/')
            return DetailPage(
                html = html,
                menuId = menuId,
                pageId = extractJsValue(html, "page_id"),
                templateId = extractJsValue(html, "template_id"),
            )
        }
    }

    private fun fetchFormId(detail: DetailPage): String? {
        val pageId = detail.pageId ?: return null
        val templateId = detail.templateId ?: return null
        val json = postJson(
            path = "/cp/templateButtonForm/getFormId",
            body = mapOf(
                "page_id" to pageId,
                "template_id" to templateId,
                "tab_id" to "",
                "system_type" to "ep"
            ),
            referer = "$TP_EP_BASE/view?m=ep&role=$roleId&type=1#act=cp/templateList/p/${detail.menuId}"
        ).asJsonObject
        return json.get("RESOURCE_ID")?.asString
    }

    private fun fetchFormHtml(detail: DetailPage, formId: String, username: String): JsonObject {
        val pageId = detail.pageId ?: throw Exception("缺 page_id")
        val templateId = detail.templateId ?: throw Exception("缺 template_id")
        return postJson(
            path = "/cp/templateButtonForm/getFormHtml",
            body = mapOf(
                "form_id" to formId,
                "id" to username,
                "template_id" to templateId,
                "page_id" to pageId,
                "systemType" to "ep",
                "tabid" to "",
                "form_type" to 3,
                "menuId" to detail.menuId,
                "editDetail" to 1,
                "cp_language" to "",
                "is_i18n" to "",
                "is_mobile" to false,
                "is_update_preview" to ""
            ),
            referer = "$TP_EP_BASE/view?m=ep&role=$roleId&type=1#act=cp/templateList/p/${detail.menuId}"
        ).asJsonObject
    }

    private fun parseFormFields(formJson: JsonObject): List<StudentInfoField> {
        val updatedData = formJson.getAsJsonObject("updatedata") ?: return emptyList()
        val labels = parseViewListLabels(formJson.get("viewlist")?.asString.orEmpty())
        return labels.mapNotNull { (key, label) ->
            // 优先取 _NAME（已解析的码值显示名），其次取原始值，最后取 _VALUE
            val value = updatedData.stringValue("${key}_NAME")
                ?: updatedData.stringValue(key)
                ?: updatedData.stringValue("${key}_VALUE")
            value?.takeIf { it.isNotBlank() }?.let { StudentInfoField(label, it) }
        }.distinctBy { it.label }
    }

    private fun parseViewListLabels(viewList: String): List<Pair<String, String>> {
        return viewListBlockRegex.findAll(viewList)
            .mapNotNull { match ->
                val block = match.groupValues[1]
                val label = blockLabelRegex.find(block)?.groupValues?.get(1)?.trim()
                val column = blockColumnRegex.find(block)?.groupValues?.get(1)?.trim()
                val key = column?.substringAfterLast(".")
                if (label.isNullOrBlank() || key.isNullOrBlank()) null else key to label
            }
            .toList()
    }

    private fun postJson(
        path: String,
        body: Map<String, Any>,
        referer: String
    ): JsonElement {
        val requestBody = gson.toJson(body)
            .toRequestBody("application/json; charset=UTF-8".toMediaType())
        val request = Request.Builder()
            .url("$TP_EP_BASE$path")
            .header("User-Agent", UA)
            .header("Content-Type", "application/json; charset=UTF-8")
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", referer)
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            Log.d(TAG, "POST $path HTTP ${response.code}, body[:160]=${text.take(160)}")
            if (!response.isSuccessful) {
                throw Exception("学生一张表接口错误 HTTP ${response.code}: $path")
            }
            if (text.contains("cas/login") || text.contains("name=\"lt\"")) {
                throw SessionExpiredException()
            }
            return gson.fromJson(text, JsonElement::class.java)
        }
    }

    private fun JsonObject.stringValue(key: String): String? {
        val value = get(key) ?: return null
        if (value.isJsonNull) return null
        return runCatching { value.asString }.getOrNull()
    }

    private fun extractJsValue(html: String, key: String): String? {
        return jsValueRegex(key).find(html)
            ?.groupValues?.get(1)
    }

    private fun jsValueRegex(key: String) =
        Regex("""cp\.templatelist\.$key\s*=\s*'([^']*)'""")

    /**
     * 调 /cp/templateList/getPageGrid 拉取页面列定义元数据。
     *
     * 返回的 [PageGridMeta] 包含：
     * - columnLabels: COLUMN_NAME → TITLE_NAME 中文标签映射
     * - simpleCodeMap: 可从 UNION ALL SQL 直接解析的 COLUMN_NAME → (code→name) 映射
     */
    fun getPageGrid(pageContext: PageContext): PageGridMeta {
        val referer = "$TP_EP_BASE/view?m=ep&role=$roleId&type=1#act=ep/data/database"
        val body = mapOf(
            "pageid" to pageContext.pageId,
            "menuid" to pageContext.menuId,
            "systemType" to pageContext.systemType,
            "tab_id" to "",
            "workflow_id" to pageContext.workflowId,
            "workflow_node_id" to pageContext.workflowNodeId,
            "cp_language" to "",
            "is_i18n" to "false",
        )
        val resp = postJson(path = "/cp/templateList/getPageGrid", body = body, referer = referer)
            .asJsonObject
        val columns = resp.getAsJsonArray("columnlist")
            ?.mapNotNull { it.takeIf { el -> el.isJsonObject }?.asJsonObject }
            ?: emptyList()

        val columnLabels = mutableMapOf<String, String>()
        val simpleCodeMap = mutableMapOf<String, Map<String, String>>()

        for (col in columns) {
            val columnName = col.get("COLUMN_NAME")?.asString ?: continue
            val titleName = col.get("TITLE_NAME")?.asString
            if (!titleName.isNullOrBlank()) {
                columnLabels[columnName] = titleName
            }
            val codeSql = col.get("CODE_SQL")?.asString
            if (!codeSql.isNullOrBlank()) {
                val parsed = parseSimpleUnionAll(codeSql)
                if (parsed.isNotEmpty()) {
                    simpleCodeMap[columnName] = parsed
                }
            }
        }
        return PageGridMeta(columnLabels = columnLabels, simpleCodeMap = simpleCodeMap)
    }

    data class PageContext(
        val pageId: String,
        val menuId: String,
        val systemType: String,
        val templateId: String,
        val workflowId: String,
        val workflowNodeId: String,
    )

    data class PageGridMeta(
        val columnLabels: Map<String, String>,
        val simpleCodeMap: Map<String, Map<String, String>>
    )

    companion object {
        private const val TAG = "StudentTable"
        const val TP_EP_BASE = "https://one.ahu.edu.cn/tp_ep_stu"
        const val STUDENT_ROLE_ID = "62002108194816"
        const val STUDENT_TABLE_URL = "$TP_EP_BASE/view?m=ep"
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36"

        // 暴露给同包 Repository 复用的 viewlist 解析正则
        internal val viewListBlockRegex = Regex("""\{([^{}]+)\}""")
        internal val blockLabelRegex = Regex("""ELEMENT_NAME=([^,}]+)""")
        internal val blockColumnRegex = Regex("""COLUMN_NAME=([^,}]+)""")

        /**
         * 解析仅含 DUAL UNION ALL 的简单 SQL 码表。
         * 示例: SELECT '1' AS CODEVALUE,'在校' AS CODENAME FROM DUAL
         *        UNION ALL SELECT '0' AS CODEVALUE,'毕业' AS CODENAME FROM DUAL
         *
         * 复杂 SQL（引用数据库表）返回空 map —— 这些映射由服务端在 list API 中完成。
         */
        fun parseSimpleUnionAll(sql: String): Map<String, String> {
            val trimmed = sql.trim()
            // 仅处理纯 DUAL UNION ALL 的 SQL
            if (!trimmed.contains("FROM DUAL", ignoreCase = true)) return emptyMap()
            val parts = trimmed.split(Regex("UNION\\s+ALL", RegexOption.IGNORE_CASE))
            val result = mutableMapOf<String, String>()
            for (part in parts) {
                val codeMatch = Regex("""'([^']*)'\s*AS\s+CODEVALUE""", RegexOption.IGNORE_CASE)
                    .find(part)?.groupValues?.get(1)
                val nameMatch = Regex("""'([^']*)'\s*AS\s+CODENAME""", RegexOption.IGNORE_CASE)
                    .find(part)?.groupValues?.get(1)
                if (codeMatch != null && nameMatch != null) {
                    result[codeMatch] = nameMatch
                }
            }
            return result
        }
    }
}
