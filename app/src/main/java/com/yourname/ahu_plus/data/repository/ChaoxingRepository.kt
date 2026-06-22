package com.yourname.ahu_plus.data.repository

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.model.CxActivity
import com.yourname.ahu_plus.data.model.CxAttachment
import com.yourname.ahu_plus.data.model.CxChapter
import com.yourname.ahu_plus.data.model.CxCourse
import com.yourname.ahu_plus.data.model.CxCoursePoints
import com.yourname.ahu_plus.data.model.CxCourseProgress
import com.yourname.ahu_plus.data.model.CxJob
import com.yourname.ahu_plus.data.model.CxJobInfo
import com.yourname.ahu_plus.data.model.CxMessage
import com.yourname.ahu_plus.data.model.CxMessageSource
import com.yourname.ahu_plus.data.model.CxVideoInfo
import com.yourname.ahu_plus.data.model.CxWorkData
import com.yourname.ahu_plus.data.model.CxQuestion
import com.yourname.ahu_plus.data.network.SecureHttpClientFactory
import com.yourname.ahu_plus.util.AESCipher
import com.yourname.ahu_plus.util.CxFontDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * 超星学习通 Repository。
 *
 * 负责登录、课程列表、章节列表、任务点卡片、视频信息、签到活动等 API。
 * 认证方式：AES 加密手机号+密码 → Cookie（`_uid`/`uf`/`vc3`/`_d`）。
 */
class ChaoxingRepository(
    private val sessionManager: SessionManager,
) {
    companion object {
        private const val TAG = "Chaoxing"
        private const val BASE_PASSPORT = "https://passport2.chaoxing.com"
        private const val BASE_MOOC2 = "https://mooc2-ans.chaoxing.com"
        private const val BASE_MOOC1 = "https://mooc1.chaoxing.com"
        private const val BASE_MOBILE = "https://mobilelearn.chaoxing.com"
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    // ── Cookie 存储 ──────────────────────────────────────────────
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()
    private val cxHost = "chaoxing.com"

    val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            // 超星所有子域名共享 Cookie（passport2 / mooc1 / mooc2 / mobilelearn 等）
            val domain = normalizeDomain(url.host)
            val list = cookieStore.getOrPut(domain) { mutableListOf() }
            for (c in cookies) {
                list.removeAll { it.name == c.name }
                list.add(c)
            }
            persistCookies()
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val domain = normalizeDomain(url.host)
            return cookieStore[domain]?.toList() ?: emptyList()
        }

        /**
         * 将 *.chaoxing.com 子域名统一归到 "chaoxing.com"，
         * 保证登录 Cookie 跨子域共享。
         */
        private fun normalizeDomain(host: String): String {
            return if (host.endsWith(".chaoxing.com") || host == "chaoxing.com") {
                "chaoxing.com"
            } else {
                host
            }
        }
    }

    private val client: OkHttpClient = SecureHttpClientFactory.create(
        cookieJar = cookieJar,
        followRedirects = true,
        trustAll = false,  // 超星用标准 HTTPS 证书
        connectTimeoutSec = 20,
        readTimeoutSec = 30,
    )

    private val gson = Gson()

    // ── Cookie 持久化 ────────────────────────────────────────────

    @Volatile
    private var cookiesDirty = false

    private fun persistCookies() {
        cookiesDirty = true
    }

    /** 将内存中的 Cookie 持久化到 DataStore（需在协程中调用） */
    suspend fun flushCookies() {
        if (!cookiesDirty) return
        cookiesDirty = false
        val all = cookieStore.values.flatten()
        val str = all.joinToString(";") { "${it.name}=${it.value}" }
        sessionManager.saveCxCookies(str)
    }

    fun loadPersistedCookies() {
        val raw = sessionManager.getCxCookies() ?: return
        if (raw.isBlank()) return
        val cookies = raw.split(";").mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) return@mapNotNull null
            Cookie.Builder()
                .domain(cxHost)
                .path("/")
                .name(part.substring(0, eq).trim())
                .value(part.substring(eq + 1).trim())
                .build()
        }
        cookieStore[cxHost] = cookies.toMutableList()
    }

    suspend fun clearCookies() {
        cookieStore.clear()
        sessionManager.saveCxCookies("")
    }

    // ── 获取当前用户 ID ─────────────────────────────────────────

    fun getUid(): String {
        return cookieStore[cxHost]?.firstOrNull { it.name == "_uid" }?.value
            ?: throw IllegalStateException("未登录超星，无法获取 uid")
    }

    fun getFid(): String {
        return cookieStore[cxHost]?.firstOrNull { it.name == "fid" }?.value ?: "1024"
    }

    fun isLoggedIn(): Boolean {
        return cookieStore[cxHost]?.any { it.name == "_uid" } == true
    }

    /**
     * 自动登录：使用 SessionManager 中已保存的凭据重新登录，
     * 避免用户手动输入。返回 true 表示登录成功。
     */
    suspend fun autoLogin(): Boolean {
        val phone = sessionManager.getCxPhone() ?: return false
        val password = sessionManager.getCxPassword() ?: return false
        return login(phone, password).isSuccess
    }

    /** 返回当前所有 Cookie 的 "name=value;name=value" 字符串，供外部 HTTP 客户端使用。 */
    fun getCookieString(): String {
        return cookieStore[cxHost]?.joinToString("; ") { "${it.name}=${it.value}" } ?: ""
    }

    // ══════════════════════════════════════════════════════════════
    //  登录
    // ══════════════════════════════════════════════════════════════

    /**
     * 超星登录。
     *
     * POST https://passport2.chaoxing.com/fanyalogin
     * 参数: fid=-1, uname=AES(手机号), password=AES(密码), refer=..., t=true
     */
    suspend fun login(username: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val encUser = AESCipher.encrypt(username)
            val encPass = AESCipher.encrypt(password)

            val body = FormBody.Builder()
                .add("fid", "-1")
                .add("uname", encUser)
                .add("password", encPass)
                .add("refer", "https%3A%2F%2Fi.chaoxing.com")
                .add("t", "true")
                .add("forbidotherlogin", "0")
                .add("validate", "")
                .add("doubleFactorLogin", "0")
                .add("independentId", "0")
                .build()

            val request = Request.Builder()
                .url("$BASE_PASSPORT/fanyalogin")
                .post(body)
                .header("User-Agent", UA)
                .header("X-Requested-With", "XMLHttpRequest")
                .build()

            val resp = client.newCall(request).execute()
            val json = JsonParser.parseString(resp.body?.string() ?: "{}").asJsonObject

            if (json.has("status") && json.get("status").asBoolean) {
                Log.i(TAG, "登录成功")
                flushCookies()
                Result.success("登录成功")
            } else {
                val msg = json.str("msg2").ifBlank { json.str("msg") }.ifBlank { "未知错误" }
                Log.w(TAG, "登录失败: $msg")
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "登录异常", e)
            Result.failure(e)
        }
    }

    /**
     * 校验当前 Cookie 是否仍然有效。
     */
    suspend fun validateSession(): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = FormBody.Builder()
                .add("courseType", "1")
                .add("courseFolderId", "0")
                .add("query", "")
                .add("superstarClass", "0")
                .build()

            val request = Request.Builder()
                .url("$BASE_MOOC2/mooc2-ans/visit/courselistdata")
                .post(body)
                .header("User-Agent", UA)
                .build()

            val resp = client.newCall(request).execute()
            val text = resp.body?.string() ?: ""
            resp.close()

            // 如果被重定向到登录页或返回包含 login 字样，说明 Cookie 失效
            !text.contains("passport2.chaoxing.com") && !text.lowercase().contains("login")
        } catch (e: Exception) {
            Log.e(TAG, "校验会话异常", e)
            false
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  课程列表
    // ══════════════════════════════════════════════════════════════

    /**
     * 获取所有课程列表。
     *
     * POST https://mooc2-ans.chaoxing.com/mooc2-ans/visit/courselistdata
     * 响应: HTML → Jsoup 解析 div.course
     */
    suspend fun getCourseList(): Result<List<CxCourse>> = withContext(Dispatchers.IO) {
        try {
            val body = FormBody.Builder()
                .add("courseType", "1")
                .add("courseFolderId", "0")
                .add("query", "")
                .add("superstarClass", "0")
                .build()

            val request = Request.Builder()
                .url("$BASE_MOOC2/mooc2-ans/visit/courselistdata")
                .post(body)
                .header("User-Agent", UA)
                .header("Referer", "$BASE_MOOC2/mooc2-ans/visit/interaction?moocDomain=https://mooc1-1.chaoxing.com/mooc-ans")
                .build()

            val resp = client.newCall(request).execute()
            val html = resp.body?.string() ?: ""
            resp.close()

            val courses = decodeCourseList(html)
            Log.i(TAG, "获取课程列表成功: ${courses.size} 门")
            Result.success(courses)
        } catch (e: Exception) {
            Log.e(TAG, "获取课程列表异常", e)
            Result.failure(e)
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  章节列表
    // ══════════════════════════════════════════════════════════════

    /**
     * 批量获取所有课程的任务点进度（后台并行）。
     *
     * 遍历每门课 -> 拉取章节列表 -> 汇总 jobCount / hasFinished。
     *
     * @return Map<courseKey, CxCourseProgress>  其中 key 为 "${courseId}_${clazzId}"
     */
    suspend fun getAllCoursesProgress(courses: List<CxCourse>): Map<String, CxCourseProgress> =
        withContext(Dispatchers.IO) {
            courses.mapNotNull { course ->
                try {
                    val key = course.courseId + "_" + course.clazzId
                    val points = getCoursePoints(course).getOrNull()?.points ?: return@mapNotNull null
                    if (points.isEmpty()) return@mapNotNull key to CxCourseProgress()

                    val totalJobs = points.sumOf { it.jobCount }
                    val completedJobs = points.count { it.hasFinished }
                    key to CxCourseProgress(
                        totalJobs = totalJobs,
                        completedJobs = minOf(completedJobs, totalJobs),
                    )
                } catch (_: Exception) { null }
            }.toMap()
        }

    /**
     * 获取课程的所有章节。
     *
     * GET https://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/studentcourse?courseid=X&clazzid=Y&cpi=Z&ut=s
     */
    suspend fun getCoursePoints(course: CxCourse): Result<CxCoursePoints> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_MOOC2/mooc2-ans/mycourse/studentcourse" +
                "?courseid=${course.courseId}&clazzid=${course.clazzId}&cpi=${course.cpi}&ut=s"

            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", UA)
                .build()

            val resp = client.newCall(request).execute()
            val html = resp.body?.string() ?: ""
            resp.close()

            val points = decodeCoursePoints(html)
            Log.i(TAG, "获取章节成功: ${points.points.size} 个")
            Result.success(points)
        } catch (e: Exception) {
            Log.e(TAG, "获取章节异常", e)
            Result.failure(e)
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  任务点卡片
    // ══════════════════════════════════════════════════════════════

    /**
     * 获取章节的所有任务点。
     *
     * GET https://mooc1.chaoxing.com/mooc-ans/knowledge/cards
     * 响应: JS+HTML → 正则提取 mArg JSON → 解析 attachments
     */
    suspend fun getJobList(course: CxCourse, chapter: CxChapter): Result<Pair<List<CxJob>, CxJobInfo>> =
        withContext(Dispatchers.IO) {
            try {
                val allJobs = mutableListOf<CxJob>()
                var jobInfo = CxJobInfo()

                // 遍历可能的 num 值 (0~6)
                for (num in "0123456") {
                    val url = "$BASE_MOOC1/mooc-ans/knowledge/cards" +
                        "?clazzid=${course.clazzId}&courseid=${course.courseId}" +
                        "&knowledgeid=${chapter.id}&ut=s&cpi=${course.cpi}" +
                        "&v=2025-0424-1038-3&mooc2=1&num=$num"

                    val request = Request.Builder()
                        .url(url)
                        .get()
                        .header("User-Agent", UA)
                        .header("Referer", "https://mooc2-ans.chaoxing.com/mycourse/studentcourse")
                        .build()

                    val resp = client.newCall(request).execute()
                    val html = resp.body?.string() ?: ""
                    Log.d(TAG, "getJobList num=$num code=${resp.code} finalUrl=${resp.request.url} htmlLen=${html.length}")
                    if (num == '0') {
                        // 搜索 mArg 关键字
                        val mIdx = html.indexOf("mArg")
                        Log.d(TAG, "mArg 位置: $mIdx, html 总长: ${html.length}")
                        if (mIdx >= 0) {
                            Log.d(TAG, "mArg 上下文: ${html.substring(maxOf(0, mIdx - 20), minOf(html.length, mIdx + 200))}")
                        } else {
                            Log.d(TAG, "html 内容 (前 3000 字): ${html.take(3000)}")
                        }
                    }
                    resp.close()

                    // 检查章节未开放
                    if (html.contains("章节未开放")) {
                        return@withContext Result.success(Pair(emptyList(), CxJobInfo()))
                    }

                    val (jobs, info) = decodeCourseCard(html)
                    if (info.knowledgeid.isNotEmpty()) {
                        jobInfo = info
                    }
                    allJobs.addAll(jobs)
                }

                Log.i(TAG, "获取任务点成功: ${allJobs.size} 个")
                Result.success(Pair(allJobs, jobInfo))
            } catch (e: Exception) {
                Log.e(TAG, "获取任务点异常", e)
                Result.failure(e)
            }
        }

    // ══════════════════════════════════════════════════════════════
    //  视频信息
    // ══════════════════════════════════════════════════════════════

    /**
     * 获取视频元信息。
     *
     * GET https://mooc1.chaoxing.com/ananas/status/{objectid}?k={fid}&flag=normal
     */
    suspend fun getVideoInfo(objectId: String): Result<CxVideoInfo> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_MOOC1/ananas/status/$objectId?k=${getFid()}&flag=normal"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", UA)
                .header("Referer", "https://mooc1.chaoxing.com/ananas/modules/video/index.html")
                .build()

            val resp = client.newCall(request).execute()
            val json = resp.body?.string() ?: "{}"
            resp.close()

            val obj = JsonParser.parseString(json).asJsonObject
            val info = CxVideoInfo(
                dtoken = obj.str("dtoken"),
                duration = obj.get("duration")?.asInt ?: 0,
                crc = obj.str("crc"),
                key = obj.str("key"),
                status = obj.str("status"),
                filename = obj.str("filename"),
            )

            if (info.status == "success") {
                Result.success(info)
            } else {
                Result.failure(Exception("视频状态异常: ${info.status}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取视频信息异常", e)
            Result.failure(e)
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  视频进度上报
    // ══════════════════════════════════════════════════════════════

    /**
     * 上报视频播放进度。
     *
     * GET https://mooc1.chaoxing.com/mooc-ans/multimedia/log/a/{cpi}/{dtoken}
     * 返回: {"isPassed": true/false}
     */
    suspend fun reportVideoProgress(
        course: CxCourse,
        job: CxJob,
        jobInfo: CxJobInfo,
        dtoken: String,
        duration: Int,
        playingTime: Int,
        isdrag: Int = 3,
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val enc = getEnc(course.clazzId, job.jobid, job.objectid, playingTime, duration, getUid())

            // rt 参数处理（对齐原仓库 base.py video_progress_log）
            // 优先用 job.rt，为空则从 otherInfo 中解析 -rt_1 / -rt_d
            var rt = job.rt
            if (rt.isBlank()) {
                val rtMatch = Regex("""-rt_([1d])""").find(job.otherinfo)
                if (rtMatch != null) {
                    rt = if (rtMatch.groupValues[1] == "d") "0.9" else "1"
                }
            }
            val rtValues = if (rt.isNotBlank()) listOf(rt) else listOf("0.9", "1")

            // 原仓库：rt 为空时依次尝试 0.9 和 1
            var lastError: Exception? = null
            for (tryRt in rtValues) {
                val url = "$BASE_MOOC1/mooc-ans/multimedia/log/a/${course.cpi}/$dtoken" +
                    "?clazzId=${course.clazzId}&playingTime=$playingTime&duration=$duration" +
                    "&clipTime=0_$duration&objectId=${job.objectid}" +
                    "&otherInfo=${job.otherinfo}&courseId=${course.courseId}" +
                    "&jobid=${job.jobid}&userid=${getUid()}" +
                    "&isdrag=$isdrag&view=pc&enc=$enc&dtype=Video" +
                    "&rt=$tryRt&_t=${System.currentTimeMillis()}"

                val extraParams = buildString {
                    if (job.videoFaceCaptureEnc.isNotEmpty()) append("&videoFaceCaptureEnc=${job.videoFaceCaptureEnc}")
                    if (job.attDuration.isNotEmpty()) append("&attDuration=${job.attDuration}")
                    if (job.attDurationEnc.isNotEmpty()) append("&attDurationEnc=${job.attDurationEnc}")
                }

                val request = Request.Builder()
                    .url(url + extraParams)
                    .get()
                    .header("User-Agent", UA)
                    .header("Referer", "https://mooc1.chaoxing.com/ananas/modules/video/index.html")
                    .build()

                val resp = client.newCall(request).execute()
                val code = resp.code
                val text = resp.body?.string() ?: ""
                resp.close()

                when (code) {
                    200 -> {
                        val json = JsonParser.parseString(text).asJsonObject
                        val isPassed = json.get("isPassed")?.asBoolean ?: false
                        return@withContext Result.success(isPassed)
                    }
                    403 -> {
                        lastError = Exception("403 Forbidden")
                        // 403 尝试下一个 rt
                        continue
                    }
                    else -> {
                        lastError = Exception("HTTP $code")
                        continue
                    }
                }
            }
            return@withContext Result.failure(lastError ?: Exception("上报失败"))
        } catch (e: Exception) {
            Log.e(TAG, "视频进度上报异常", e)
            Result.failure(e)
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  章节检测（答题）
    // ══════════════════════════════════════════════════════════════

    /**
     * 获取章节检测题目。
     *
     * GET https://mooc1.chaoxing.com/mooc-ans/api/work
     */
    suspend fun getWorkQuestions(
        course: CxCourse,
        job: CxJob,
        jobInfo: CxJobInfo,
    ): Result<CxWorkData> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_MOOC1/mooc-ans/api/work" +
                "?api=1&workId=${job.jobid.removePrefix("work-")}" +
                "&jobid=${job.jobid}&originJobId=${job.jobid}" +
                "&needRedirect=true&skipHeader=true" +
                "&knowledgeid=${jobInfo.knowledgeid}&ktoken=${jobInfo.ktoken}" +
                "&cpi=${jobInfo.cpi}&ut=s&clazzId=${course.clazzId}" +
                "&type=&enc=${job.enc}&mooc2=1&courseid=${course.courseId}"

            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", UA)
                .build()

            val resp = client.newCall(request).execute()
            val html = resp.body?.string() ?: ""
            resp.close()

            val workData = decodeQuestions(html)
            Log.i(TAG, "获取题目成功: ${workData.questions.size} 道")
            Result.success(workData)
        } catch (e: Exception) {
            Log.e(TAG, "获取题目异常", e)
            Result.failure(e)
        }
    }

    /**
     * 提交/保存章节检测答案。
     *
     * POST https://mooc1.chaoxing.com/mooc-ans/work/addStudentWorkNew
     */
    /**
     * 提交/保存章节检测答案。
     *
     * POST https://mooc1.chaoxing.com/mooc-ans/work/addStudentWorkNew
     *
     * 与 Python study_work 提交逻辑完全对齐:
     *   1. 先加 answerwqbid + pyFlag
     *   2. 再加所有表单隐藏字段 (formFields)
     *   3. 最后逐题加 answer{id} + answertype{id}
     */
    suspend fun submitWork(workData: CxWorkData): Result<String> = withContext(Dispatchers.IO) {
        try {
            val formBuilder = FormBody.Builder()

            // 与 Python 一致: 先加 answerwqbid 和 pyFlag
            formBuilder.add("answerwqbid", workData.answerwqbid)
            formBuilder.add("pyFlag", workData.pyFlag)

            // 再加所有表单隐藏字段 (跳过 pyFlag 避免重复)
            for ((k, v) in workData.formFields) {
                if (k == "pyFlag") continue
                formBuilder.add(k, v)
            }

            // 诊断日志 — 完整请求体
            val body = formBuilder.build()
            val bodySize = body.size
            val bodyStr = (0 until bodySize).joinToString("&") { i ->
                "${body.encodedName(i)}=${body.encodedValue(i)}"
            }
            Log.i(TAG, "[submit] pyFlag='${workData.pyFlag}', body=${bodyStr.take(1000)}")

            val request = Request.Builder()
                .url("$BASE_MOOC1/mooc-ans/work/addStudentWorkNew")
                .post(body)
                .header("User-Agent", UA)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "*/*")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Origin", BASE_MOOC1)
                .header("Referer", "$BASE_MOOC1/mooc-ans/work/addStudentWorkNew")
                .build()

            val resp = client.newCall(request).execute()
            val code = resp.code
            val json = resp.body?.string() ?: "{}"
            resp.close()

            Log.i(TAG, "[submit] HTTP $code, pyFlag='${workData.pyFlag}', 响应: ${json.take(500)}")

            val obj = JsonParser.parseString(json).asJsonObject
            if (obj.get("status")?.asBoolean == true) {
                Result.success(obj.str("msg").ifBlank { "成功" })
            } else {
                val errMsg = obj.str("msg").ifBlank { "提交失败, 服务端返回: ${json.take(200)}" }
                Log.e(TAG, "[submit] 失败: $errMsg")
                Result.failure(Exception(errMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "提交答案异常", e)
            Result.failure(e)
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  文档 / 阅读 / 空页面任务
    // ══════════════════════════════════════════════════════════════

    /** 文档任务 */
    suspend fun studyDocument(course: CxCourse, job: CxJob, jobInfo: CxJobInfo): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val nodeId = Regex("""nodeId_(\d+)-""").find(job.otherinfo)?.groupValues?.get(1) ?: ""
                val url = "$BASE_MOOC1/ananas/job/document" +
                    "?jobid=${job.jobid}&knowledgeid=$nodeId" +
                    "&courseid=${course.courseId}&clazzid=${course.clazzId}" +
                    "&jtoken=${job.jtoken}&_dc=${System.currentTimeMillis()}"

                val request = Request.Builder().url(url).get().header("User-Agent", UA).build()
                val resp = client.newCall(request).execute()
                resp.close()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** 阅读任务 */
    suspend fun studyRead(course: CxCourse, job: CxJob, jobInfo: CxJobInfo): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_MOOC1/ananas/job/readv2" +
                    "?jobid=${job.jobid}&knowledgeid=${jobInfo.knowledgeid}" +
                    "&jtoken=${job.jtoken}&courseid=${course.courseId}" +
                    "&clazzid=${course.clazzId}"

                val request = Request.Builder().url(url).get().header("User-Agent", UA).build()
                val resp = client.newCall(request).execute()
                resp.close()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** 空页面任务 */
    suspend fun studyEmptyPage(course: CxCourse, chapter: CxChapter): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_MOOC1/mooc-ans/mycourse/studentstudyAjax" +
                    "?courseId=${course.courseId}&clazzid=${course.clazzId}" +
                    "&chapterId=${chapter.id}&cpi=${course.cpi}" +
                    "&verificationcode=&mooc2=1&microTopicId=0&editorPreview=0"

                val request = Request.Builder().url(url).get().header("User-Agent", UA).build()
                val resp = client.newCall(request).execute()
                resp.close()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ══════════════════════════════════════════════════════════════
    //  签到
    // ══════════════════════════════════════════════════════════════

    /** 获取签到活动列表 */
    suspend fun getActivityList(course: CxCourse): Result<List<CxActivity>> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_MOBILE/v2/apis/active/student/activelist" +
                "?fid=${getFid()}&courseId=${course.courseId}&classId=${course.clazzId}" +
                "&showNotStartedActive=0&_=${System.currentTimeMillis()}"

            val request = Request.Builder().url(url).get().header("User-Agent", UA).build()
            val resp = client.newCall(request).execute()
            val json = resp.body?.string() ?: "{}"
            resp.close()

            val obj = JsonParser.parseString(json).asJsonObject
            if (obj.get("result")?.asInt != 1) {
                return@withContext Result.success(emptyList())
            }

            val list = obj.getAsJsonObject("data").getAsJsonArray("activeList")
            val activities = list.map { el ->
                val o = el.asJsonObject
                val otherInfo = o.str("otherInfo")
                // 解析签到类型:otherInfo 里类似 "type=4&..." 或顶级 type 字段
                val typeCode = Regex("""type=(\d+)""").find(otherInfo)?.groupValues?.get(1)?.toIntOrNull()
                    ?: o.get("type")?.asInt
                    ?: 0
                CxActivity(
                    id = o.get("id")?.asLong ?: 0,
                    name = o.str("nameOne").ifBlank { o.str("name") },
                    type = o.get("type")?.asInt ?: 0,
                    status = o.get("status")?.asInt ?: 0,
                    courseId = course.courseId,
                    classId = course.clazzId,
                    startTime = o.get("startTime")?.asLong ?: 0,
                    endTime = o.get("endTime")?.asLong ?: 0,
                    signType = com.yourname.ahu_plus.data.model.CxSignType.fromCode(typeCode),
                )
            }
            Result.success(activities)
        } catch (e: Exception) {
            Log.e(TAG, "获取签到活动异常", e)
            Result.failure(e)
        }
    }

    /**
     * preSign 前置签名(2026-06-20 集成 Phase 3,移植自 base.py:pre_sign)。
     *
     * 某些签到(尤其位置签到)需要先调 preSign 拿 token,再调 stuSignajax。
     */
    suspend fun preSign(course: CxCourse, activityId: Long): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_MOBILE/newsign/preSign" +
                "?general=1&sys=1&ls=1&appType=15&tid=&ut=s" +
                "&uid=${getUid()}&activePrimaryId=$activityId" +
                "&courseId=${course.courseId}&classId=${course.clazzId}"

            val request = Request.Builder().url(url).get().header("User-Agent", UA).build()
            val resp = client.newCall(request).execute()
            val text = resp.body?.string() ?: ""
            resp.close()
            Result.success(text)
        } catch (e: Exception) {
            Log.e(TAG, "preSign 异常", e)
            Result.failure(e)
        }
    }

    /**
     * 位置签到(2026-06-20 集成 Phase 3,移植自 base.py:sign_in_normal + type_=LOCATION)。
     *
     * 携带真实经纬度 + 地址名称 + objectId。
     */
    suspend fun signInLocation(
        course: CxCourse,
        activityId: Long,
        latitude: Double,
        longitude: Double,
        address: String = "",
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_MOBILE/pptSign/stuSignajax" +
                "?activeId=$activityId&uid=${getUid()}&fid=${getFid()}" +
                "&courseId=${course.courseId}&classId=${course.clazzId}" +
                "&clientip=&objectId=aaa" +
                "&name=${java.net.URLEncoder.encode(address, "UTF-8")}" +
                "&useragent=&latitude=$latitude&longitude=$longitude&appType=15"

            val request = Request.Builder().url(url).get().header("User-Agent", UA).build()
            val resp = client.newCall(request).execute()
            val text = resp.body?.string() ?: ""
            resp.close()
            Result.success(text)
        } catch (e: Exception) {
            Log.e(TAG, "位置签到异常", e)
            Result.failure(e)
        }
    }

    /**
     * 手势签到(2026-06-20 集成 Phase 3,移植自 base.py:SignType.GESTURE)。
     *
     * 超星手势签到通常需要提交一个手势编码字符串(学号 / 姓名首字母等),
     * 编码由用户在设置中自行配置。
     */
    suspend fun signInGesture(
        course: CxCourse,
        activityId: Long,
        gestureCode: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_MOBILE/pptSign/stuSignajax" +
                "?activeId=$activityId&uid=${getUid()}&fid=${getFid()}" +
                "&courseId=${course.courseId}&classId=${course.clazzId}" +
                "&clientip=&objectId=aaa" +
                "&name=${java.net.URLEncoder.encode(gestureCode, "UTF-8")}" +
                "&useragent=&latitude=-1&longitude=-1&appType=15&signCode=$gestureCode"

            val request = Request.Builder().url(url).get().header("User-Agent", UA).build()
            val resp = client.newCall(request).execute()
            val text = resp.body?.string() ?: ""
            resp.close()
            Result.success(text)
        } catch (e: Exception) {
            Log.e(TAG, "手势签到异常", e)
            Result.failure(e)
        }
    }

    /** 执行普通签到 */
    suspend fun signNormal(course: CxCourse, activityId: Long): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_MOBILE/pptSign/stuSignajax" +
                "?activeId=$activityId&uid=${getUid()}&fid=${getFid()}" +
                "&courseId=${course.courseId}&classId=${course.clazzId}" +
                "&clientip=&objectId=aaa&name=&useragent=&latitude=-1&longitude=-1&appType=15"

            val request = Request.Builder().url(url).get().header("User-Agent", UA).build()
            val resp = client.newCall(request).execute()
            val text = resp.body?.string() ?: ""
            resp.close()
            Result.success(text)
        } catch (e: Exception) {
            Log.e(TAG, "签到异常", e)
            Result.failure(e)
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  工具方法
    // ══════════════════════════════════════════════════════════════

    /** 视频进度 enc 签名 */
    private fun getEnc(clazzId: String, jobid: String, objectId: String, playingTime: Int, duration: Int, userid: String): String {
        val raw = "[$clazzId][$userid][$jobid][$objectId][${playingTime * 1000}][d_yHJ!\$pdA~5][${duration * 1000}][0_$duration]"
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    // ══════════════════════════════════════════════════════════════
    //  HTML 解析 (移植 decode.py)
    // ══════════════════════════════════════════════════════════════

    /** 解析课程列表 HTML */
    private fun decodeCourseList(html: String): List<CxCourse> {
        val doc = Jsoup.parse(html)
        val courses = mutableListOf<CxCourse>()

        for (el in doc.select("div.course")) {
            // 跳过未开放
            if (el.select("a.not-open-tip").isNotEmpty() || el.select("div.not-open-tip").isNotEmpty()) continue

            val id = el.attr("id")
            if (id.isBlank()) continue

            val clazzId = el.selectFirst("input.clazzId")?.attr("value") ?: continue
            val courseId = el.selectFirst("input.courseId")?.attr("value") ?: continue
            val cpi = Regex("""cpi=(\d+)&""").find(el.selectFirst("a")?.attr("href") ?: "")?.groupValues?.get(1) ?: ""
            val title = CxFontDecoder.decode(html, el.selectFirst("span.course-name")?.attr("title") ?: "")
            val teacher = CxFontDecoder.decode(html, el.selectFirst("p.color3")?.attr("title") ?: "")

            courses.add(CxCourse(courseId = courseId, clazzId = clazzId, cpi = cpi, title = title, teacher = teacher))
        }
        return courses
    }

    /** 解析章节列表 HTML */
    private fun decodeCoursePoints(html: String): CxCoursePoints {
        val doc = Jsoup.parse(html)
        var hasLocked = false
        val points = mutableListOf<CxChapter>()

        for (unit in doc.select("div.chapter_unit")) {
            for (li in unit.select("li")) {
                val div = li.selectFirst("div") ?: continue
                val rawId = div.attr("id")
                if (rawId.isBlank()) continue

                val id = Regex("""cur(\d+)""").find(rawId)?.groupValues?.get(1) ?: continue
                val titleRaw = div.selectFirst("a.clicktitle")?.text()?.replace("\n", "")?.trim() ?: ""
                val title = CxFontDecoder.decode(html, titleRaw)

                val jobCountEl = div.selectFirst("input.knowledgeJobCount")
                val jobCount = jobCountEl?.attr("value")?.toIntOrNull() ?: 1

                val tipsEl = div.selectFirst("span.bntHoverTips")
                val tipsText = tipsEl?.text() ?: ""
                val needUnlock = tipsText.contains("解锁")
                val hasFinished = tipsText.contains("已完成")

                if (needUnlock) hasLocked = true

                points.add(CxChapter(id = id, title = title, jobCount = jobCount, hasFinished = hasFinished, needUnlock = needUnlock))
            }
        }
        return CxCoursePoints(hasLocked = hasLocked, points = points)
    }

    /** 从 HTML 中用括号计数法提取 mArg={...} JSON 字符串 */
    private fun extractMArgJson(html: String): String? {
        // 找到 "mArg=" 或 "mArg =" 的位置
        val startMarker = Regex("""mArg\s*=\s*\{""").find(html) ?: return null
        val jsonStart = startMarker.range.last  // 指向 '{'
        // 从 jsonStart 开始括号计数，找到匹配的 '}'
        var depth = 0
        for (i in jsonStart until html.length) {
            when (html[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return html.substring(jsonStart, i + 1)
                }
            }
        }
        return null
    }

    /** 解析任务点卡片 JS → (jobs, jobInfo) */
    private fun decodeCourseCard(html: String): Pair<List<CxJob>, CxJobInfo> {
        if (html.contains("章节未开放")) {
            Log.d(TAG, "章节未开放")
            return Pair(emptyList(), CxJobInfo())
        }

        val mArgStr = extractMArgJson(html)
        if (mArgStr == null) {
            // 有些章节页面确实没有 mArg（纯文本/阅读类章节）
            val hasContent = html.length > 500
            Log.d(TAG, "mArg 未匹配, html 长度=${html.length}, 有内容=$hasContent")
            return Pair(emptyList(), CxJobInfo())
        }
        Log.d(TAG, "mArg 匹配成功, 长度: ${mArgStr.length}")

        val json = try {
            JsonParser.parseString(mArgStr).asJsonObject
        } catch (e: Exception) {
            Log.e(TAG, "mArg JSON 解析失败: ${e.message}", e)
            return Pair(emptyList(), CxJobInfo())
        }

        // 提取 jobInfo
        val defaults = json.getAsJsonObject("defaults")
        val jobInfo = CxJobInfo(
            ktoken = defaults?.str("ktoken") ?: "",
            defenc = defaults?.str("defenc") ?: "",
            cardid = defaults?.str("cardid") ?: "",
            cpi = defaults?.str("cpi") ?: "",
            knowledgeid = defaults?.str("knowledgeid") ?: "",
        )

        // 解析 attachments
        val attachments = json.getAsJsonArray("attachments")
        if (attachments == null) {
            Log.w(TAG, "attachments 为 null, json keys: ${json.keySet()}")
            return Pair(emptyList(), jobInfo)
        }
        Log.d(TAG, "attachments 数量: ${attachments.size()}")
        val jobs = mutableListOf<CxJob>()
        var allPassedCount = 0  // count of already-passed tasks

        for ((idx, el) in attachments.withIndex()) {
            val card = el.asJsonObject
            val rawType = card.str("type")
            val isPassed = card.get("isPassed")?.asBoolean == true
            Log.d(TAG, "  attachment[$idx] type=$rawType isPassed=$isPassed")

            // skip already passed (but count them)
            if (isPassed) {
                allPassedCount++
                continue
            }

            // job 字段可能是 JsonObject / JsonPrimitive(true) / null
            // null → 可能是阅读任务；其他情况按 type 分类处理
            val jobEl = card.get("job")
            if (jobEl == null || jobEl.isJsonNull) {
                val readJob = parseReadJob(card)
                if (readJob != null) {
                    Log.d(TAG, "    → 阅读任务: ${readJob.name}")
                    jobs.add(readJob)
                } else {
                    Log.d(TAG, "    → job 为 null 且非阅读任务, 跳过")
                }
                continue
            }

            // 清理 otherInfo
            var otherInfo = card.str("otherInfo")
            if (otherInfo.contains("courseId")) {
                otherInfo = otherInfo.split("&")[0]
            }

            val type = rawType.lowercase()
            val prop = card.getAsJsonObject("property") ?: JsonObject()

            val parsedJob = when {
                type.contains("live") || prop.str("liveId").isNotBlank() -> parseLiveJob(card, prop, otherInfo)
                type == "video" -> parseVideoJob(card, prop, otherInfo)
                type == "document" -> parseDocumentJob(card, prop, otherInfo)
                type.contains("work") -> parseWorkJob(card, otherInfo)
                else -> null
            }
            if (parsedJob != null) {
                Log.d(TAG, "    → ${parsedJob.type}: ${parsedJob.name}")
                jobs.add(parsedJob)
            } else {
                Log.d(TAG, "    → 未知类型: $type, 跳过")
            }
        }
        // Log when all tasks are already completed
        if (allPassedCount > 0 && jobs.isEmpty()) {
            Log.i(TAG, "所有 $allPassedCount 个任务点已完成，无需学习")
        }
        return Pair(jobs, jobInfo)
    }

    private fun parseVideoJob(card: JsonObject, prop: JsonObject, otherInfo: String): CxJob? {
        val mid = card.str("mid")
        if (mid.isBlank()) return null
        return CxJob(
            type = "video",
            jobid = card.str("jobid"),
            name = prop.str("name").ifBlank { card.str("name") },
            objectid = card.str("objectId"),
            otherinfo = otherInfo,
            mid = mid,
            // playTime 来自超星 mArg attachments[].playTime，单位是秒
            playTime = card.get("playTime")?.asInt ?: 0,
            rt = prop.str("rt"),
            attDuration = card.str("attDuration"),
            attDurationEnc = card.str("attDurationEnc"),
            videoFaceCaptureEnc = card.str("videoFaceCaptureEnc"),
        )
    }

    private fun parseDocumentJob(card: JsonObject, prop: JsonObject, otherInfo: String): CxJob {
        return CxJob(
            type = "document",
            jobid = card.str("jobid"),
            objectid = prop.str("objectid"),
            otherinfo = otherInfo,
            jtoken = card.str("jtoken"),
            mid = card.str("mid"),
            enc = card.str("enc"),
            aid = card.str("aid"),
        )
    }

    private fun parseWorkJob(card: JsonObject, otherInfo: String): CxJob {
        return CxJob(
            type = "workid",
            jobid = card.str("jobid"),
            otherinfo = otherInfo,
            mid = card.str("mid"),
            enc = card.str("enc"),
            aid = card.str("aid"),
        )
    }

    /** 从 JsonObject 安全获取字符串值 */
    private fun JsonObject.str(key: String): String {
        val el = get(key) ?: return ""
        return if (el.isJsonPrimitive) el.asString else el.toString()
    }

    private fun parseReadJob(card: JsonObject): CxJob? {
        if (card.str("type") != "read") return null
        return CxJob(
            type = "read",
            jobid = card.str("jobid"),
            name = card.getAsJsonObject("property")?.str("title") ?: "",
            jtoken = card.str("jtoken"),
            mid = card.str("mid"),
            otherinfo = card.str("otherInfo"),
            enc = card.str("enc"),
            aid = card.str("aid"),
        )
    }

    private fun parseLiveJob(card: JsonObject, prop: JsonObject, otherInfo: String): CxJob {
        return CxJob(
            type = "live",
            jobid = card.str("jobid").ifBlank { card.str("id") },
            name = prop.str("title").ifBlank { prop.str("name") }.ifBlank { "直播" },
            objectid = card.str("objectId"),
            otherinfo = otherInfo,
            mid = card.str("mid"),
            aid = card.str("aid"),
        )
    }

    /**
     * 解析章节检测题目 HTML。
     *
     * 与 Python decode_questions_info 对齐:
     *   1. _extract_form_data: 提取所有非 answer* 的 input 字段
     *   2. answerwqbid 由题目 ID 列表生成
     *   3. 每道题的 answerField 包含 answer{id} 和 answertype{id}
     */
    private fun decodeQuestions(html: String): CxWorkData {
        val doc = Jsoup.parse(html)
        val form = doc.selectFirst("form") ?: return CxWorkData()

        // 提取表单隐藏字段 (与 Python _extract_form_data 一致: 跳过所有含 "answer" 的 name)
        val formFields = mutableMapOf<String, String>()
        for (input in form.select("input")) {
            val name = input.attr("name")
            if (name.isBlank() || name.contains("answer")) continue
            formFields[name] = input.attr("value")
        }

        // 提取题目
        val questions = mutableListOf<CxQuestion>()
        for (div in form.select("div.singleQuesId")) {
            val qId = div.attr("data")
            if (qId.isBlank()) continue

            val tiMu = div.selectFirst("div.TiMu")
            val typeCode = tiMu?.attr("data") ?: ""
            val type = when (typeCode) {
                "0" -> "single"
                "1" -> "multiple"
                "2" -> "completion"
                "3" -> "judgement"
                "4" -> "shortanswer"
                else -> "unknown"
            }

            val titleDiv = div.selectFirst("div.Zy_TItle")
            val titleRaw = titleDiv?.text()?.replace("\r", "")?.replace("\t", "")?.replace("\n", "") ?: ""
            val title = CxFontDecoder.decode(html, titleRaw)

            val optionsList = div.select("ul li").map { li ->
                val raw = (li.attr("aria-label").ifBlank { li.text() }).trim()
                var decoded = CxFontDecoder.decode(html, raw).trim()
                // 去掉末尾 "选择" (与 Python _extract_choices 一致)
                if (decoded.endsWith("选择")) decoded = decoded.dropLast(2).trimEnd()
                decoded
            }.sorted()

            val options = optionsList.joinToString("\n")

            val answerField = mapOf(
                "answer$qId" to "",
                "answertype$qId" to typeCode,
            )

            questions.add(CxQuestion(id = qId, title = title, options = options, type = type, answerField = answerField))
        }

        // answerwqbid 由所有题目 ID 拼接 (与 Python 一致)
        val answerwqbid = questions.joinToString(",") { it.id } + ","

        return CxWorkData(
            questions = questions,
            answerwqbid = answerwqbid,
            formFields = formFields,
        )
    }

    // ══════════════════════════════════════════════════════════════
    //  消息中心 (2026-06-21)
    // ══════════════════════════════════════════════════════════════

    /**
     * 获取收件箱通知列表（notice.chaoxing.com）。
     *
     * @param lastValue 游标，首次为空，后续用上一页 lastGetId
     * @return Pair(消息列表, 下一页游标)
     */
    suspend fun getNoticeList(lastValue: String = ""): Result<Pair<List<CxMessage>, String>> =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://notice.chaoxing.com/pc/notice/getNoticeList"
                val body = FormBody.Builder()
                    .add("type", "")
                    .add("notice_type", "")
                    .add("lastValue", lastValue)
                    .add("sort", "")
                    .add("folderUUID", "")
                    .add("kw", "")
                    .add("startTime", "")
                    .add("endTime", "")
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .header("User-Agent", UA)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .build()

                val resp = client.newCall(request).execute()
                val json = resp.body?.string() ?: "{}"
                resp.close()

                val obj = JsonParser.parseString(json).asJsonObject
                val notices = obj.getAsJsonObject("notices") ?: run {
                    Log.w(TAG, "getNoticeList: notices 为 null")
                    return@withContext Result.success(Pair(emptyList(), ""))
                }
                val nextCursor = notices.str("lastGetId")
                val list = notices.getAsJsonArray("list") ?: return@withContext Result.success(Pair(emptyList(), nextCursor))

                val messages = list.mapNotNull { el ->
                    try {
                        val o = el.asJsonObject
                        val sendTimeStr = o.str("sendTime")
                        val sendTimeMs = parseTimeToMillis(sendTimeStr)
                        CxMessage(
                            id = o.str("idCode"),
                            source = CxMessageSource.NOTICE,
                            title = o.str("title"),
                            content = o.str("content"),
                            senderName = o.str("createrName"),
                            senderId = o.get("createrId")?.asLong ?: 0,
                            time = sendTimeMs,
                            isRead = o.get("grayReadTag")?.asBoolean == true || o.get("isread")?.asInt == 1,
                            logo = o.str("logo"),
                            attachment = o.str("attachment"),
                            rtfContent = o.str("rtf_content"),
                            attachments = parseAttachments(o.str("attachment")),
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "解析通知失败: ${e.message}")
                        null
                    }
                }
                Log.i(TAG, "getNoticeList: ${messages.size} 条, nextCursor=${nextCursor.take(16)}")
                Result.success(Pair(messages, nextCursor))
            } catch (e: Exception) {
                Log.e(TAG, "getNoticeList 异常", e)
                Result.failure(e)
            }
        }

    /**
     * 获取课程活动通知并转为 CxMessage 列表。
     *
     * 复用 [getActivityList] 的 API，但解析更多字段。
     */
    suspend fun getActivityMessages(courses: List<CxCourse>): Result<List<CxMessage>> =
        withContext(Dispatchers.IO) {
            try {
                val allMessages = mutableListOf<CxMessage>()
                for (course in courses) {
                    val url = "$BASE_MOBILE/v2/apis/active/student/activelist" +
                        "?fid=${getFid()}&courseId=${course.courseId}&classId=${course.clazzId}" +
                        "&showNotStartedActive=0&_=${System.currentTimeMillis()}"

                    val request = Request.Builder().url(url).get().header("User-Agent", UA).build()
                    val resp = client.newCall(request).execute()
                    val json = resp.body?.string() ?: "{}"
                    resp.close()

                    val obj = JsonParser.parseString(json).asJsonObject
                    if (obj.get("result")?.asInt != 1) continue

                    val data = obj.getAsJsonObject("data") ?: continue
                    val list = data.getAsJsonArray("activeList") ?: continue

                    for (el in list) {
                        try {
                            val o = el.asJsonObject
                            val actType = o.get("type")?.asInt ?: 0
                            val typeName = when (actType) {
                                2 -> "签到"
                                11 -> "选人"
                                42 -> "随堂练习"
                                45 -> "通知"
                                62 -> "分组任务"
                                else -> "活动"
                            }
                            allMessages.add(
                                CxMessage(
                                    id = "act_${o.get("id")?.asLong ?: 0}",
                                    source = CxMessageSource.ACTIVITY,
                                    title = o.str("nameOne").ifBlank { o.str("name") },
                                    content = "",
                                    senderName = course.title,
                                    time = o.get("startTime")?.asLong ?: 0,
                                    isRead = o.get("userStatus")?.asInt == 1,
                                    type = actType,
                                    typeName = typeName,
                                    courseId = course.courseId,
                                    courseName = course.title,
                                    logo = o.str("logo"),
                                    activityStatus = o.get("status")?.asInt ?: 0,
                                    userStatus = o.get("userStatus")?.asInt ?: 0,
                                    attendNum = o.get("attendNum")?.asInt ?: 0,
                                    releaseNum = o.get("releaseNum")?.asInt ?: 0,
                                )
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "解析活动失败: ${e.message}")
                        }
                    }
                }
                Log.i(TAG, "getActivityMessages: ${allMessages.size} 条")
                Result.success(allMessages)
            } catch (e: Exception) {
                Log.e(TAG, "getActivityMessages 异常", e)
                Result.failure(e)
            }
        }

    /**
     * 获取附件的真实下载 URL。
     *
     * 超星云盘下载需要签名，直接拼 URL 会 404。
     * 实际流程：preview 页面（已签名）→ 页面内嵌 download URL（d0.cldisk.com，带 at_/ak_/ad_ 参数）。
     */
    suspend fun getAttachmentDownloadUrl(att: CxAttachment): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            val previewUrl = att.preview
            if (previewUrl.isBlank()) return@withContext Result.failure(Exception("无预览地址"))

            val request = Request.Builder().url(previewUrl).get().header("User-Agent", UA).build()
            val resp = client.newCall(request).execute()
            val html = resp.body?.string() ?: ""
            resp.close()

            // 从 HTML 中提取 d0.cldisk.com/download/... 签名 URL
            val match = Regex("""https?://d\d+\.cldisk\.com/download/[^"'\s]+""").find(html)
            if (match != null) {
                // 下载需要 Referer 指向 preview 页面
                Result.success(Pair(match.value, previewUrl))
            } else {
                Result.failure(Exception("未找到下载链接"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取下载链接异常", e)
            Result.failure(e)
        }
    }

    /**
     * 解析通知的 attachment JSON 字符串为 [CxAttachment] 列表。
     */
    private fun parseAttachments(raw: String): List<CxAttachment> {
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JsonParser.parseString(raw).asJsonArray
            arr.mapNotNull { el ->
                try {
                    val obj = el.asJsonObject
                    val cloud = obj.getAsJsonObject("att_clouddisk") ?: return@mapNotNull null
                    CxAttachment(
                        name = cloud.str("name"),
                        fileSize = cloud.str("fileSize"),
                        suffix = cloud.str("suffix"),
                        objectId = cloud.str("objectId"),
                        preview = cloud.str("preview"),
                        puid = cloud.str("puid"),
                        forbidDownload = cloud.get("forbidDownload")?.asInt ?: 0,
                    )
                } catch (_: Exception) { null }
            }
        } catch (_: Exception) { emptyList() }
    }

    /**
     * 解析 "2026-06-15 16:31:48" 格式时间为毫秒时间戳。
     */
    private fun parseTimeToMillis(timeStr: String): Long {
        if (timeStr.isBlank()) return 0
        return try {
            val parts = timeStr.split(" ", "-", ":")
            if (parts.size >= 6) {
                val cal = java.util.Calendar.getInstance()
                cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(),
                    parts[3].toInt(), parts[4].toInt(), parts[5].toInt())
                cal.set(java.util.Calendar.MILLISECOND, 0)
                cal.timeInMillis
            } else 0
        } catch (e: Exception) { 0 }
    }
}
