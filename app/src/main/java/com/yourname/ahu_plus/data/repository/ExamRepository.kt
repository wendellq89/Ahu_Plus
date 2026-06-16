package com.yourname.ahu_plus.data.repository

import android.util.Log
import com.yourname.ahu_plus.data.model.jw.Exam
import com.yourname.ahu_plus.data.network.SecureHttpClientFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * 教务处考试安排仓库。
 *
 * 真实端点（基于 JwSystem 9 实测）：
 *   GET /student/for-std/exam-arrange?semesterId=...   → 302 → Location 含 studentId
 *   GET /student/for-std/exam-arrange/info/{studentId}  → HTML（Thymeleaf SSR）
 *
 * **响应体是 HTML 而非 JSON**，需要用 Jsoup 解析：
 *   `<table id="exams">` 内每个 `<tr class="finished hide">` 或 `<tr class="unfinished hide">` 是
 *   一场考试。结构：
 *   ```
 *   <tr data-finished="true" class="finished hide">
 *     <td>
 *       <div class="time">2026-05-24 14:00~15:40</div>
 *       <div>
 *         <span>磬苑校区</span>
 *         <span>博学楼</span>
 *         <span>博学楼B101</span>
 *         <span id="seat-123"></span>
 *       </div>
 *     </td>
 *     <td>
 *       <div><span>程序设计基础</span></div>
 *       <div><span class="tag-span type1">考试</span></div>
 *     </td>
 *     <td>已完成</td>
 *   </tr>
 *   ```
 */
class ExamRepository(
    private val jwAuthRepository: JwAuthRepository
) {
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
     * 拉取考试安排（HTML 解析）。
     *
     * 注：API 不接受 semesterId 参数（302 redirect 不变），返回的是所有学期（过往 + 未来）的考试。
     * UI 端可按时间过滤。
     */
    suspend fun getExams(
        @Suppress("UNUSED_PARAMETER") semesterId: Int = CourseRepository.DEFAULT_SEMESTER_ID
    ): Result<List<Exam>> {
        return try {
            val studentId = resolveStudentId().getOrElse {
                return Result.failure(it)
            }
            val url = "$JW_BASE/student/for-std/exam-arrange/info/$studentId"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.code == 302) {
                    throw SessionExpiredException()
                }
                if (!response.isSuccessful) {
                    throw Exception("考试安排查询失败: HTTP ${response.code}")
                }
                if (body.isBlank()) {
                    throw Exception("考试安排返回空响应")
                }
                val exams = parseExamHtml(body)
                Log.i(TAG, "考试安排解析完成: ${exams.size} 条 (studentId=$studentId)")
                Result.success(exams)
            }
        } catch (e: Exception) {
            Log.e(TAG, "考试安排查询失败", e)
            Result.failure(e)
        }
    }

    /**
     * 解析考试安排 HTML —— 提取 `<table id="exams">` 内的所有 `<tr>` 行。
     */
    internal fun parseExamHtml(html: String): List<Exam> {
        val doc = Jsoup.parse(html)
        val table = doc.getElementById("exams") ?: return emptyList()
        val rows = table.select("tbody > tr")
        return rows.mapNotNull { parseRow(it) }
    }

    private fun parseRow(row: Element): Exam? {
        val rowClass = row.className()
        if ("finished" !in rowClass && "unfinished" !in rowClass) {
            // 跳过占位行（tr-empty 等）
            return null
        }
        val tds = row.select("> td")
        if (tds.size < 2) return null

        // —— 时间 + 地点 + 座位号 ——
        val firstTd = tds[0]
        val timeText = firstTd.selectFirst(".time")?.text()?.trim().orEmpty()
        val locationSpans = firstTd.select("> div > span")
            .map { it.text().trim() }
            .filter { it.isNotEmpty() && !it.startsWith("座位") }
        val campus = locationSpans.getOrNull(0).orEmpty()
        val building = locationSpans.getOrNull(1).orEmpty()
        val room = locationSpans.getOrNull(2).orEmpty()
        // 座位号：HTML 中 span id="seat-XXXXXXX" 为数据库记录 ID(7位数)，
        // 无真实文本内容。仅当数值 ≤999 时才可能是真实座位号。
        val seatElement = firstTd.selectFirst("[id^=seat-]")
        val seatId = seatElement?.id()?.removePrefix("seat-")?.toIntOrNull()
        val seatNumber = seatId?.takeIf { it in 1..999 }?.toString()

        // —— 课程名 + 考试类型 ——
        val secondTd = tds[1]
        // 课程名：第二个 td 第一个 div 里的 span
        val courseName = secondTd.selectFirst("div > span")?.text()?.trim().orEmpty()
        // 考试类型：tag-span 类的 span
        val examType = secondTd.selectFirst(".tag-span")?.text()?.trim().orEmpty()

        // —— 状态：第三个 td ——
        val status = tds.getOrNull(2)?.text()?.trim().orEmpty()
        val isFinished = "finished" in rowClass ||
            status.contains("完成") || status.contains("已考") || status.contains("已结束")

        if (courseName.isBlank() && timeText.isBlank()) return null

        val id = "$courseName|$timeText|$room".hashCode().toLong()
        return Exam(
            id = if (id < 0) "e${-id}" else "e$id",
            courseName = courseName,
            examType = examType,
            examTime = timeText,
            campus = campus,
            building = building,
            room = room,
            seatNumber = seatNumber,
            isFinished = isFinished
        )
    }

    private suspend fun resolveStudentId(): Result<Long> {
        cachedStudentId?.let { return Result.success(it) }
        return try {
            val url = "$JW_BASE/student/for-std/exam-arrange" +
                "?semesterId=${CourseRepository.DEFAULT_SEMESTER_ID}"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                val location = response.headers["Location"] ?: ""
                Log.i(TAG, "exam-arrange HTTP ${response.code}, Location=$location")
                val id = GradeRepository.parseStudentIdFromLocation(location)
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
        private const val TAG = "ExamRepo"
        private const val JW_BASE = "https://jw.ahu.edu.cn"
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0"
    }
}
