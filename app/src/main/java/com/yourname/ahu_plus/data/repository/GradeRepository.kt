package com.yourname.ahu_plus.data.repository

import android.util.Log
import com.yourname.ahu_plus.data.GsonProvider
import com.yourname.ahu_plus.data.model.jw.GpaMetadata
import com.yourname.ahu_plus.data.model.jw.GradeResponse
import com.yourname.ahu_plus.data.model.jw.SemesterGpaEntry
import com.yourname.ahu_plus.data.network.SecureHttpClientFactory
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 教务处成绩查询仓库。
 *
 * 真实端点（基于 JwSystem 9 实测）：
 *   GET /student/for-std/grade/sheet?semesterId=...   → 302 → Location 含 studentId
 *   GET /student/for-std/grade/sheet/info/{studentId}?semester={semesterId}  → JSON
 *
 * studentId 是 per-user JW 内部 id，不是学号。第一次访问时通过 302 Location 抽取并缓存。
 */
class GradeRepository(
    private val jwAuthRepository: JwAuthRepository
) {
    private val gson = GsonProvider.instance

    @Volatile private var cachedStudentId: Long? = null

    private val client: OkHttpClient = SecureHttpClientFactory.create(
        cookieJar = jwAuthRepository.jwCookieJar,
        followRedirects = false,
        disableGzip = false,
        extraInterceptors = listOf(
            okhttp3.Interceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", UA)
                    .header("x-requested-with", "XMLHttpRequest")
                    .build()
                chain.proceed(req)
            }
        )
    )

    /**
     * 拉取所有学期的成绩（一次性返回）。studentId 首次访问时自动解析。
     *
     * 注：实测 `info/{studentId}` 不带 `semester` 参数会返回该生全部学期成绩（约 25KB JSON）。
     *     带 `semester` 参数只返回指定学期。
     */
    suspend fun getGrades(): Result<GradeResponse> {
        return try {
            val studentId = resolveStudentId().getOrElse {
                return Result.failure(it)
            }
            val url = "$JW_BASE/student/for-std/grade/sheet/info/$studentId"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.code == 302) {
                    throw SessionExpiredException()
                }
                if (!response.isSuccessful) {
                    throw Exception("成绩查询失败: HTTP ${response.code}")
                }
                if (body.isBlank() || body[0] != '{') {
                    throw Exception("成绩接口返回非 JSON: ${body.take(200)}")
                }
                val parsed = gson.fromJson(body, GradeResponse::class.java)
                val count = parsed.semesterId2studentGrades?.values
                    ?.sumOf { it.size } ?: 0
                Log.i(TAG, "成绩加载完成: $count 条 across ${parsed.semesterId2studentGrades?.size ?: 0} 学期")
                Result.success(parsed)
            }
        } catch (e: Exception) {
            Log.e(TAG, "成绩查询失败", e)
            Result.failure(e)
        }
    }

    /**
     * 第一次访问时拉 `/student/for-std/grade/sheet`，从 302 Location 抽取 studentId。
     * 解析后缓存到 [cachedStudentId]，后续不再请求。
     */
    private suspend fun resolveStudentId(): Result<Long> {
        cachedStudentId?.let { return Result.success(it) }
        return try {
            val url = "$JW_BASE/student/for-std/grade/sheet" +
                "?semesterId=${CourseRepository.DEFAULT_SEMESTER_ID}"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val location = response.headers["Location"] ?: ""
                Log.i(TAG, "grade/sheet HTTP ${response.code}, Location=$location")
                // Location 形如: /student/for-std/grade/sheet/semester-index/99166
                val id = parseStudentIdFromLocation(location)
                if (id != null) {
                    cachedStudentId = id
                    Result.success(id)
                } else {
                    Result.failure(Exception("无法从 Location 解析 studentId: $location"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "resolveStudentId 失败", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "GradeRepo"
        private const val JW_BASE = "https://jw.ahu.edu.cn"
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0"

        // Regex: match top-level 'key':value in the gpaSemesterModel JS object
        // We search in the prefix before 'gpaSemesterSubs' to avoid matching sub-entries
        private val GPA_MODEL_REGEX = Regex(
            """var gpaSemesterModel = (\{.+?\});""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
        private val GPA_SUBS_REGEX = Regex(
            """'gpaSemesterSubs':(\[.+?\])\s*[,}]""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )

        /** Extract a single-quoted number value from JS object text. */
        private fun extractJsNum(text: String, key: String): String? {
            val m = Regex("'$key':\\s*([\\d.]+)").find(text) ?: return null
            return m.groupValues[1]
        }

        /** Extract a single-quoted int value from JS object text. */
        private fun extractJsInt(text: String, key: String): Int? {
            val m = Regex("'$key':\\s*(\\d+)").find(text) ?: return null
            return m.groupValues[1].toIntOrNull()
        }

        /**
         * 返回 gpaSemesterModel JS 对象中 `gpaSemesterSubs` 数组之后的部分。
         * 所有顶层聚合值(totalCredits/inPlanCredits/majorRank 等)都在 subs 数组之后。
         */
        private fun findAfterSubsArray(jsText: String): String {
            val subsKey = "'gpaSemesterSubs':"
            val start = jsText.indexOf(subsKey)
            if (start < 0) return jsText // fallback
            var pos = start + subsKey.length
            // skip whitespace
            while (pos < jsText.length && jsText[pos] in " \t") pos++
            if (pos >= jsText.length || jsText[pos] != '[') return jsText
            // bracket matching to find end of array
            var depth = 0
            for (i in pos until jsText.length) {
                when (jsText[i]) {
                    '[' -> depth++
                    ']' -> {
                        depth--
                        if (depth == 0) return jsText.substring(i + 1)
                    }
                }
            }
            return jsText // fallback
        }

        /**
         * 从 semester-index HTML 解析 GPA 元数据。
         *
         * 输入是 grade/sheet/semester-index/{studentId} 页面的 HTML。
         * 该页面 inline script 里有 `var gpaSemesterModel = {...}`。
         *
         * 返回 null 表示解析失败（非当前页面、或无 GPA 数据）。
         */
        fun parseGpaFromHtml(html: String): GpaMetadata? {
            val modelMatch = GPA_MODEL_REGEX.find(html) ?: return null
            val jsText = modelMatch.groupValues[1]
            // 'gpa' is before gpaSemesterSubs, all other top-level values are AFTER the subs array
            val prefix = jsText.substringBefore("'gpaSemesterSubs'")
            val gpa = extractJsNum(prefix, "gpa")?.toDoubleOrNull()

            // Extract top-level values from AFTER the subs array (skip sub-entry values)
            val afterSubs = findAfterSubsArray(jsText)
            val totalCredits = extractJsNum(afterSubs, "totalCredits")?.toDoubleOrNull()
            val inPlanCredits = extractJsNum(afterSubs, "inPlanCredits")?.toDoubleOrNull()
            val outPlanCredits = extractJsNum(afterSubs, "outPlanCredits")?.toDoubleOrNull()
            val majorRank = extractJsInt(afterSubs, "majorRank")
            val majorHeadCount = extractJsInt(afterSubs, "majorHeadCount")

            // Parse semester subs array
            val subs = mutableListOf<SemesterGpaEntry>()
            val subsMatch = GPA_SUBS_REGEX.find(jsText)
            if (subsMatch != null) {
                val subsStr = subsMatch.groupValues[1]
                    .replace("None", "null")
                    // Convert single-quoted JS keys to double-quoted JSON keys
                    .let { s -> Regex("'(\\w+)':").replace(s) { "\"${it.groupValues[1]}\":" } }
                try {
                    val array = com.google.gson.Gson().fromJson(subsStr, Array<SubEntry>::class.java)
                    for (entry in array) {
                        subs.add(SemesterGpaEntry(
                            semesterId = entry.semesterId ?: 0,
                            gpa = entry.gpa,
                            totalCredits = entry.totalCredits ?: 0.0,
                            inPlanCredits = entry.inPlanCredits ?: 0.0,
                            outPlanCredits = entry.outPlanCredits ?: 0.0,
                            majorRank = entry.majorRank,
                        ))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "gpaSemesterSubs parse failed", e)
                }
            }

            return GpaMetadata(
                gpa = gpa,
                totalCredits = totalCredits,
                inPlanCredits = inPlanCredits,
                outPlanCredits = outPlanCredits,
                majorRank = majorRank,
                majorHeadCount = majorHeadCount,
                perSemesterGpa = subs,
            )
        }

        /** 临时内部类用于 Gson 反序列化 subs 数组。 */
        private data class SubEntry(
            val semesterId: Int?,
            val gpa: Double?,
            val totalCredits: Double?,
            val inPlanCredits: Double?,
            val outPlanCredits: Double?,
            val majorRank: Int?
        )

        /**
         * 从 Location URL 末尾抽取纯数字 id。
         * 例: `/student/for-std/grade/sheet/semester-index/99166` → 99166
         */
        internal fun parseStudentIdFromLocation(location: String): Long? {
            if (location.isBlank()) return null
            val tail = location.trim().trimEnd('/').substringAfterLast('/')
            return tail.toLongOrNull()
        }
    }

    /**
     * 获取 GPA 元数据（总均绩点 / 学分 / 排名 / 每学期 GPA）。
     *
     * 数据来源：成绩 semester-index HTML 页面的 inline JS `var gpaSemesterModel`。
     * 与 [getGrades] 独立——GPA 模型在一个单独的 HTML 页里。
     */
    suspend fun getGpaMetadata(): Result<GpaMetadata?> {
        return try {
            val studentId = resolveStudentId().getOrElse { return Result.failure(it) }
            val url = "$JW_BASE/student/for-std/grade/sheet/semester-index/$studentId"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "text/html,application/xhtml+xml,*/*")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    throw Exception("GPA 页面请求失败: HTTP ${response.code}")
                }
                val gpa = parseGpaFromHtml(body)
                Log.i(TAG, "GPA metadata parsed: ${gpa?.gpa ?: "null"}")
                Result.success(gpa)
            }
        } catch (e: Exception) {
            Log.e(TAG, "GPA metadata 加载失败", e)
            Result.failure(e)
        }
    }
}
