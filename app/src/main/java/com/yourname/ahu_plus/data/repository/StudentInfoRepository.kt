package com.yourname.ahu_plus.data.repository

import android.util.Log
import com.google.gson.JsonObject
import com.yourname.ahu_plus.data.GsonProvider
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.model.StudentInfo
import com.yourname.ahu_plus.data.model.StudentInfoField
import com.yourname.ahu_plus.data.network.SecureHttpClientFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class StudentInfoRepository(
    private val sessionManager: SessionManager,
    private val casAuthRepository: CasAuthRepository,
    private val studentTableUrl: String = STUDENT_TABLE_URL
) {
    private val gson = GsonProvider.instance

    private val client: OkHttpClient = SecureHttpClientFactory.create(
        cookieJar = casAuthRepository.getCookieJar(),
        followRedirects = true,
        disableGzip = false
    )

    suspend fun getStudentInfo(): Result<StudentInfo> {
        return try {
            casAuthRepository.ensureValidSession().getOrThrow()
            casAuthRepository.authenticateService(studentTableUrl).getOrThrow()
            val username = sessionManager.getUsername()
                ?: return Result.failure(Exception("未找到当前账号"))

            activateStudentRole()
            val userInfo = fetchUserInfo(username)
            val dataItems = fetchDataItems()

            val basicFields = buildList {
                addAll(userInfo)
                addAll(fetchNamedDataItemFields(dataItems, "学生基本信息", username))
            }.distinctBy { it.label }

            val housingFields = fetchNamedDataItemFields(dataItems, "住宿数据", username)

            if (basicFields.isEmpty() && housingFields.isEmpty()) {
                Result.failure(Exception("未获取到学生基本信息或住宿数据"))
            } else {
                val info = StudentInfo(
                    basicFields = basicFields,
                    housingFields = housingFields
                )
                persistToCache(info)
                Result.success(info)
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

    private fun activateStudentRole() {
        client.newCall(
            Request.Builder()
                .url("$TP_EP_BASE/view?m=ep&role=$STUDENT_ROLE_ID&type=1")
                .header("User-Agent", UA)
                .build()
        ).execute().use { /* 让服务端记录当前角色 */ }
    }

    private fun fetchUserInfo(username: String): List<StudentInfoField> {
        val json = postJson(
            path = "/ep/userHome/getUserInfo",
            body = mapOf("ID_NUMBER" to username),
            referer = "$TP_EP_BASE/view?m=ep&role=$STUDENT_ROLE_ID&type=1"
        ).asJsonObject

        return listOfNotNull(
            json.field("USER_NAME", "姓名"),
            json.field("ID_NUMBER", "学号"),
            json.field("CODENAME", "性别"),
            json.field("UNIT_NAME", "学院"),
            json.field("NATIVE_PLACE", "籍贯"),
            json.field("MOBILE", "手机号")
        )
    }

    private fun fetchDataItems(): List<JsonObject> {
        val json = postJson(
            path = "/ep/data/database/getAllDataItem",
            body = mapOf(
                "type" to "wdsj",
                "role" to STUDENT_ROLE_ID,
                "mydata_is_hide_nodatasdiv" to "1"
            ),
            referer = "$TP_EP_BASE/view?m=ep&role=$STUDENT_ROLE_ID&type=1#act=ep/data/database"
        ).asJsonObject

        return json.getAsJsonArray("SJXLIST")
            ?.mapNotNull { it.takeIf { item -> item.isJsonObject }?.asJsonObject }
            ?: emptyList()
    }

    private fun fetchNamedDataItemFields(
        dataItems: List<JsonObject>,
        itemName: String,
        username: String
    ): List<StudentInfoField> {
        val item = dataItems.firstOrNull {
            it.get("NAME")?.asString == itemName || it.get("DATATYPENAME")?.asString == itemName
        } ?: return emptyList()

        val path = item.get("CKLZ")?.asString?.takeIf { it.isNotBlank() } ?: return emptyList()
        val detail = fetchDetailPage(path)
        val formId = fetchFormId(detail) ?: return emptyList()
        val formJson = fetchFormHtml(detail, formId, username)
        val fields = parseFormFields(formJson)
        val total = item.get("TOTE")?.asString

        return if (fields.isEmpty() && !total.isNullOrBlank()) {
            listOf(StudentInfoField("记录数", total))
        } else {
            fields
        }
    }

    private fun fetchDetailPage(path: String): DetailPage {
        val normalizedPath = path.trimStart('/')
        val request = Request.Builder()
            .url("$TP_EP_BASE/$normalizedPath")
            .header("User-Agent", UA)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", "$TP_EP_BASE/view?m=ep&role=$STUDENT_ROLE_ID&type=1#act=ep/data/database")
            .build()

        client.newCall(request).execute().use { response ->
            val html = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw Exception("个人信息明细页错误 HTTP ${response.code}")
            }
            return DetailPage(
                pageId = extractJsValue(html, "page_id")
                    ?: throw Exception("未找到 $path 的 page_id"),
                menuId = extractJsValue(html, "menu_id")
                    ?: normalizedPath.substringAfterLast('/'),
                templateId = extractJsValue(html, "template_id")
                    ?: throw Exception("未找到 $path 的 template_id")
            )
        }
    }

    private fun fetchFormId(detail: DetailPage): String? {
        val json = postJson(
            path = "/cp/templateButtonForm/getFormId",
            body = mapOf(
                "page_id" to detail.pageId,
                "template_id" to detail.templateId,
                "tab_id" to "",
                "system_type" to "ep"
            ),
            referer = "$TP_EP_BASE/view?m=ep&role=$STUDENT_ROLE_ID&type=1#act=cp/templateList/p/${detail.menuId}"
        ).asJsonObject
        return json.get("RESOURCE_ID")?.asString
    }

    private fun fetchFormHtml(detail: DetailPage, formId: String, username: String): JsonObject {
        return postJson(
            path = "/cp/templateButtonForm/getFormHtml",
            body = mapOf(
                "form_id" to formId,
                "id" to username,
                "template_id" to detail.templateId,
                "page_id" to detail.pageId,
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
            referer = "$TP_EP_BASE/view?m=ep&role=$STUDENT_ROLE_ID&type=1#act=cp/templateList/p/${detail.menuId}"
        ).asJsonObject
    }

    private fun parseFormFields(formJson: JsonObject): List<StudentInfoField> {
        val updatedData = formJson.getAsJsonObject("updatedata") ?: return emptyList()
        val labels = parseViewListLabels(formJson.get("viewlist")?.asString.orEmpty())
        return labels.mapNotNull { (key, label) ->
            val value = updatedData.stringValue(key)
                ?: updatedData.stringValue("${key}_NAME")
                ?: updatedData.stringValue("${key}_VALUE")
            value?.takeIf { it.isNotBlank() }?.let { StudentInfoField(label, it) }
        }.distinctBy { it.label }
    }

    private fun parseViewListLabels(viewList: String): List<Pair<String, String>> {
        return blockRegex.findAll(viewList)
            .mapNotNull { match ->
                val block = match.groupValues[1]
                val label = Regex("""ELEMENT_NAME=([^,}]+)""").find(block)
                    ?.groupValues?.get(1)?.trim()
                val column = Regex("""COLUMN_NAME=([^,}]+)""").find(block)
                    ?.groupValues?.get(1)?.trim()
                val key = column?.substringAfterLast(".")
                if (label.isNullOrBlank() || key.isNullOrBlank()) null else key to label
            }
            .toList()
    }

    private fun postJson(
        path: String,
        body: Map<String, Any>,
        referer: String
    ): com.google.gson.JsonElement {
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
            Log.e(TAG, "POST $path HTTP ${response.code}, body[:160]=${text.take(160)}")
            if (!response.isSuccessful) {
                throw Exception("个人信息接口错误 HTTP ${response.code}: $path")
            }
            if (text.contains("cas/login") || text.contains("name=\"lt\"")) {
                throw SessionExpiredException()
            }
            return gson.fromJson(text, com.google.gson.JsonElement::class.java)
        }
    }

    private fun JsonObject.field(key: String, label: String): StudentInfoField? {
        return stringValue(key)?.takeIf { it.isNotBlank() }?.let { StudentInfoField(label, it) }
    }

    private fun JsonObject.stringValue(key: String): String? {
        val value = get(key) ?: return null
        if (value.isJsonNull) return null
        return runCatching { value.asString }.getOrNull()
    }

    private data class DetailPage(
        val pageId: String,
        val menuId: String,
        val templateId: String
    )

    companion object {
        private const val TAG = "StudentInfo"
        private const val TP_EP_BASE = "https://one.ahu.edu.cn/tp_ep_stu"
        private const val STUDENT_ROLE_ID = "62002108194816"
        private const val STUDENT_TABLE_URL = "https://one.ahu.edu.cn/tp_ep_stu/view?m=ep"
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36"

        fun parseStudentInfo(html: String): StudentInfo {
            return StudentInfo(
                basicFields = extractSectionFields(html, "学生基本信息"),
                housingFields = extractSectionFields(html, "住宿数据")
            )
        }

        private fun extractSectionFields(html: String, title: String): List<StudentInfoField> {
            val titles = sectionTitleRegex.findAll(html).toList()
            val titleMatch = titles.firstOrNull { cleanText(it.groupValues[1]) == title }
                ?: return emptyList()
            val nextTitle = titles.firstOrNull { it.range.first > titleMatch.range.first }
            val sectionStart = titleMatch.range.last + 1
            val sectionEnd = nextTitle?.range?.first ?: html.length
            val sectionHtml = html.substring(sectionStart, sectionEnd)
            return parsePairs(sectionHtml)
        }

        private fun parsePairs(sectionHtml: String): List<StudentInfoField> {
            val fields = linkedMapOf<String, String>()

            rowRegex.findAll(sectionHtml).forEach { row ->
                val cells = cellRegex.findAll(row.groupValues[1])
                    .map { cleanText(it.groupValues[1]) }
                    .filter { it.isNotBlank() }
                    .toList()
                addCellPairs(cells, fields)
            }

            if (fields.isEmpty()) {
                val tokens = cleanForFallback(sectionHtml)
                    .lines()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                addCellPairs(tokens, fields)
            }

            return fields.map { StudentInfoField(it.key, it.value) }
        }

        private fun addCellPairs(cells: List<String>, fields: MutableMap<String, String>) {
            var index = 0
            while (index + 1 < cells.size) {
                val label = normalizeLabel(cells[index])
                val value = cells[index + 1].trim()
                if (label.isNotBlank() && value.isNotBlank() && looksLikeLabel(label)) {
                    fields.putIfAbsent(label, value)
                    index += 2
                } else {
                    index++
                }
            }
        }

        private fun cleanForFallback(html: String): String {
            return cleanText(
                html
                    .replace(scriptRegex, "")
                    .replace(styleRegex, "")
                    .replace(blockBreakRegex, "\n")
            )
        }

        private fun cleanText(html: String): String {
            return decodeHtmlEntities(
                html
                    .replace(tagRegex, "")
                    .replace(Regex("""\s+"""), " ")
                    .trim()
            )
        }

        private fun normalizeLabel(value: String): String {
            return value.trim()
                .removeSuffix(":")
                .removeSuffix("：")
                .trim()
        }

        private fun looksLikeLabel(value: String): Boolean {
            if (value.length > 24) return false
            return value.any { it.isLetterOrDigit() || it in '\u4e00'..'\u9fff' }
        }

        private fun decodeHtmlEntities(value: String): String {
            return value
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace(numericEntityRegex) { match ->
                    match.groupValues[1].toIntOrNull()?.toChar()?.toString().orEmpty()
                }
        }

        private val sectionTitleRegex = Regex(
            """<div[^>]*class=["'][^"']*\bmydate-title\b[^"']*["'][\s\S]*?<span[^>]*class=["'][^"']*\btext\b[^"']*["'][^>]*>([\s\S]*?)</span>""",
            setOf(RegexOption.IGNORE_CASE)
        )
        private val rowRegex = Regex("""<tr[^>]*>([\s\S]*?)</tr>""", RegexOption.IGNORE_CASE)
        private val cellRegex = Regex("""<t[dh][^>]*>([\s\S]*?)</t[dh]>""", RegexOption.IGNORE_CASE)
        private val scriptRegex = Regex("""<script[^>]*>[\s\S]*?</script>""", RegexOption.IGNORE_CASE)
        private val styleRegex = Regex("""<style[^>]*>[\s\S]*?</style>""", RegexOption.IGNORE_CASE)
        private val blockBreakRegex = Regex("""</?(?:div|p|li|tr|td|th|span|label)[^>]*>""", RegexOption.IGNORE_CASE)
        private val tagRegex = Regex("""<[^>]+>""")
        private val numericEntityRegex = Regex("""&#(\d+);""")
        private val blockRegex = Regex("""\{([^{}]+)\}""")

        private fun extractJsValue(html: String, key: String): String? {
            return Regex("""cp\.templatelist\.$key\s*=\s*'([^']*)'""")
                .find(html)
                ?.groupValues
                ?.get(1)
        }
    }
}
