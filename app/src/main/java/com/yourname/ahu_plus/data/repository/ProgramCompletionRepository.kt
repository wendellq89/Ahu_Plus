package com.yourname.ahu_plus.data.repository

import android.util.Log
import com.yourname.ahu_plus.data.GsonProvider
import com.yourname.ahu_plus.data.model.jw.CompletionCourse
import com.yourname.ahu_plus.data.model.jw.CompletionSummary
import com.yourname.ahu_plus.data.network.SecureHttpClientFactory
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 培养方案完成概览仓库。
 *
 * 端点: GET /student/for-std/program-completion-preview → 302 → /info/{studentId}
 * 返回 HTML SSR 页面，内嵌 JS 变量 allCourseList（培养方案内课程+完成状态）
 * 和 outerCourseList（外部/转学分课程）。
 */
class ProgramCompletionRepository(
    private val jwAuthRepository: JwAuthRepository
) {
    private val gson = GsonProvider.instance

    private val client: OkHttpClient = SecureHttpClientFactory.create(
        cookieJar = jwAuthRepository.jwCookieJar,
        followRedirects = false,
        disableGzip = false,
        extraInterceptors = listOf(
            okhttp3.Interceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", UA)
                    .build()
                chain.proceed(req)
            }
        )
    )

    @Volatile private var cachedStudentId: Long? = null

    /**
     * 获取培养方案完成数据（含每门课修读状态 + 学分汇总）。
     */
    suspend fun getCompletionData(): Result<Pair<List<CompletionCourse>, CompletionSummary>> {
        return try {
            val studentId = resolveStudentId().getOrElse { return Result.failure(it) }
            val url = "$JW_BASE/student/for-std/program-completion-preview/info/$studentId"
            Log.i(TAG, "请求培养方案完成数据: studentId=$studentId")

            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "text/html,application/xhtml+xml,*/*")
                .build()
            client.newCall(request).execute().use { response ->
                val html = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    throw Exception("完成概览请求失败: HTTP ${response.code}")
                }
                if (html.isBlank()) {
                    throw Exception("完成概览返回空页")
                }

                val courses = parseAllCourseList(html)
                val summary = computeSummary(courses)
                Log.i(TAG, "完成数据解析: ${courses.size} 门课, passed=${summary.passedCredits}, taking=${summary.takingCredits}")
                Result.success(courses to summary)
            }
        } catch (e: Exception) {
            Log.e(TAG, "完成数据获取失败", e)
            Result.failure(e)
        }
    }

    /** 从程序概览页 302 提取 studentId */
    private suspend fun resolveStudentId(): Result<Long> {
        cachedStudentId?.let { return Result.success(it) }
        return try {
            val url = "$JW_BASE/student/for-std/program-completion-preview"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                val location = response.headers["Location"] ?: ""
                val id = GradeRepository.parseStudentIdFromLocation(location)
                if (id != null) {
                    cachedStudentId = id
                    Log.i(TAG, "解析 studentId=$id")
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

    // ── HTML 解析 ────────────────────────────────────────────────

    /**
     * 从 HTML 内嵌 JS 中提取 **所有** allCourseList 数组并去重合并。
     * 页面中每个模块区域各有一个 allCourseList，需要全部汇总。
     */
    private fun parseAllCourseList(html: String): List<CompletionCourse> {
        val key = "allCourseList':["
        val allCourses = mutableListOf<CompletionCourse>()
        val seenCodes = mutableSetOf<String>()

        var searchFrom = 0
        while (true) {
            val idx = html.indexOf(key, searchFrom)
            if (idx < 0) break

            val start = idx + key.length - 1
            val end = findMatchingBracket(html, start)
            if (end < 0) {
                searchFrom = idx + key.length
                continue
            }

            val raw = html.substring(start, end + 1)
            val json = jsToJson(raw)
            try {
                val type = object : TypeToken<List<CompletionCourse>>() {}.getType()
                val list: List<CompletionCourse> = gson.fromJson(json, type)
                for (c in list) {
                    val code = c.code ?: continue
                    if (seenCodes.add(code)) {
                        allCourses.add(c)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "allCourseList 解析失败: ${e.message}")
            }
            searchFrom = end + 1
        }

        if (allCourses.isEmpty()) {
            Log.w(TAG, "未找到任何 allCourseList")
        }
        return allCourses
    }

    /** 从已解析的 allCourseList 计算学分汇总 */
    private fun computeSummary(courses: List<CompletionCourse>): CompletionSummary {
        var passed = 0.0
        var taking = 0.0
        var failed = 0.0
        var passedCount = 0
        var takingCount = 0
        var unrepairedCount = 0
        var failedCount = 0

        for (c in courses) {
            val credits = c.credits ?: 0.0
            when {
                c.isPassed -> { passed += credits; passedCount++ }
                c.isTaking -> { taking += credits; takingCount++ }
                c.isFailed -> { failed += credits; failedCount++ }
                c.isUnrepaired -> { unrepairedCount++ }
            }
        }
        return CompletionSummary(
            passedCredits = passed,
            takingCredits = taking,
            failedCredits = failed,
            passedCount = passedCount,
            takingCount = takingCount,
            unrepairedCount = unrepairedCount,
            failedCount = failedCount
        )
    }

    // ── 辅助 ────────────────────────────────────────────────────

    /** JS 对象转 JSON：单引号→双引号，null 去引号 */
    private fun jsToJson(js: String): String {
        return js
            .replace("'", "\"")
            .let { Regex("\"null\"").replace(it, "null") }
    }

    private fun findMatchingBracket(s: String, start: Int): Int {
        var depth = 0
        for (i in start until s.length) {
            when (s[i]) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }

    companion object {
        private const val TAG = "ProgramCompRepo"
        private const val JW_BASE = "https://jw.ahu.edu.cn"
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0"
    }
}
