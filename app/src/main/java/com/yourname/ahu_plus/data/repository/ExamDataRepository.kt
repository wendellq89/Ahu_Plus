package com.yourname.ahu_plus.data.repository

import android.util.Log
import com.yourname.ahu_plus.BuildConfig
import com.yourname.ahu_plus.data.GsonProvider
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.model.exam.ExamPrediction
import com.yourname.ahu_plus.data.model.exam.ExamPredictionsDto
import com.yourname.ahu_plus.data.model.exam.ExamPredictionsMeta
import com.yourname.ahu_plus.data.model.exam.ExamRawRecord
import com.yourname.ahu_plus.data.network.ResilientDns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.ConnectionSpec
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 排考预测数据源 — 从 Gitee 仓库拉取已标准化的 JSON,
 * 缓存到 SessionManager,并与用户课表做精确匹配。
 *
 * 数据流:
 *   yao-enqi/ahu-plus-update (Gitee)
 *     └─ exam_predictions/exam_predictions.json
 *        → ExamDataRepository.fetchRemote()
 *        → SessionManager.saveExamPredictionsJson()
 *        → 与 SessionManager.getScheduleJson() 里的 courseCode 匹配
 *        → 输出 ExamPrediction 列表给 ViewModel
 *
 * 数据更新由维护者在本地跑 [tools/exam_prediction/scan_exams.py] 后 push 到 Gitee,
 * 客户端无需任何登录态或 Cookie。
 *
 * v3 (2026-06-23 二次修复): vivo 客户端实测 OkHttp 默认配置仍失败。
 *   主要改动:
 *     1. 强制 HTTP/1.1 (避免 HTTP/2 + 某些 CDN 边缘节点协商失败)
 *     2. 强制 COMPATIBLE_TLS 连接规格 (放宽 cipher suite,绕开 BoringSSL 的严苛策略)
 *     3. ResilientDns 增加更多 IP 候选 (AliYun / 备用 Baidu)
 *     4. 多策略 fallback:手动 302 → 自动 302 → 直连 raw
 *     5. 所有步骤 Log.w (R8 release 不剥),便于 adb logcat 排查
 */
class ExamDataRepository(
    private val sessionManager: SessionManager
) {
    private val gson = GsonProvider.instance

    /**
     * 策略 A: 手动跟踪 302,带详细日志。
     * - HTTP/1.1 (避免 HTTP/2 协商问题)
     * - COMPATIBLE_TLS (支持 TLS 1.0/1.1/1.2,兼容老 CDN)
     * - 30s 连接超时,90s 读取超时 (Gitee 1.2MB JSON 在弱网下需更久)
     */
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .protocols(listOf(Protocol.HTTP_1_1))  // 关键: 部分 vivo ROM 上 HTTP/2 握手挂死
        .connectionSpecs(listOf(ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT))
        .dns(ResilientDns)
        .build()

    /**
     * 策略 B: OkHttp 默认自动跟 302 + HTTP/2 + MODERN_TLS。
     * 作为手动策略的兜底,排除「我们手动实现有 bug」的可能。
     */
    private val fallbackClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .dns(ResilientDns)
        .build()

    private val userAgent = "Ahu_Plus/${BuildConfig.VERSION_NAME} (Android)"

    companion object {
        private const val TAG = "ExamDataRepo"

        /** Gitee 公开仓库 raw URL。更新扫描脚本后会覆盖同一文件。 */
        const val REMOTE_URL =
            "https://gitee.com/yao-enqi/ahu-plus-update/raw/master/exam_predictions/exam_predictions.json"

        /** JSON schema 版本号,供客户端校验。 */
        const val SCHEMA_VERSION = 1
    }

    // ─────────────────────────────────────────────────────────
    // 拉取与缓存
    // ─────────────────────────────────────────────────────────

    /**
     * 从 Gitee 拉取最新 JSON,写入缓存。
     * 失败时返回 Result.failure,不修改已有缓存。
     */
    suspend fun fetchRemote(): Result<Unit> = withContext(Dispatchers.IO) {
        // 策略 A: HTTP/1.1 + COMPATIBLE_TLS + 手动 302
        val strategyA = runCatching { fetchWithManualRedirect(client) }
        if (strategyA.isSuccess) return@withContext Result.success(Unit)
        Log.w(TAG, "策略 A (HTTP/1.1+COMPATIBLE_TLS+手动302) 失败: ${strategyA.exceptionOrNull()?.message}")

        // 策略 B: OkHttp 默认自动跟 302 (排除我们手动实现的 bug)
        val strategyB = runCatching { fetchWithAutoRedirect(fallbackClient) }
        if (strategyB.isSuccess) return@withContext Result.success(Unit)
        Log.w(TAG, "策略 B (默认HTTP/2+自动302) 失败: ${strategyB.exceptionOrNull()?.message}")

        // 策略 C: 重试一次 (DNS 缓存可能更新)
        delay(2000)
        val strategyC = runCatching { fetchWithManualRedirect(client) }
        if (strategyC.isSuccess) return@withContext Result.success(Unit)
        Log.w(TAG, "策略 C (重试策略A) 失败: ${strategyC.exceptionOrNull()?.message}")

        // 全部失败: 返回 A 的错误 (信息最详细)
        val err = strategyA.exceptionOrNull() ?: strategyB.exceptionOrNull() ?: strategyC.exceptionOrNull()
        Log.e(TAG, "fetchRemote 三策略全部失败: ${err?.message}", err)
        Result.failure(err ?: Exception("未知错误"))
    }

    /**
     * 策略 A:手动跟踪 302,每跳都打印 URL + 状态码,失败时能定位是哪一跳挂的。
     */
    private suspend fun fetchWithManualRedirect(client: OkHttpClient) {
        var currentUrl = REMOTE_URL.toHttpUrl()
        var redirectCount = 0
        val maxRedirects = 5

        while (true) {
            val request = Request.Builder()
                .url(currentUrl)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json, */*;q=0.8")
                .get()
                .build()

            Log.w(TAG, "[策略A] GET ${currentUrl} (redirect=$redirectCount)")
            val response = client.newCall(request).execute()

            // 打印服务器返回的实际 IP (只能从 response 推断)
            Log.w(TAG, "[策略A] ← HTTP ${response.code}, handshake=${response.handshake?.peerPrincipal}")

            if (response.isRedirect) {
                val location = response.header("Location")
                    ?: error("HTTP ${response.code} 但无 Location 头")
                response.close()
                val nextUrl = currentUrl.resolve(location)
                    ?: error("无法解析 Location: $location")
                Log.w(TAG, "[策略A] redirect → $nextUrl")
                currentUrl = nextUrl
                redirectCount++
                check(redirectCount <= maxRedirects) {
                    "重定向超过 $maxRedirects 次"
                }
                continue
            }

            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                error("HTTP $code (final=${currentUrl})")
            }

            val body = response.body?.string()
                ?: error("empty body")

            // 写入缓存前先做最小 schema 校验,避免下游解析崩溃
            val parsed = gson.fromJson(body, ExamPredictionsDto::class.java)
                ?: error("invalid JSON")

            if (parsed.version != SCHEMA_VERSION) {
                Log.w(TAG, "schema version mismatch: ${parsed.version} (expected $SCHEMA_VERSION), still try to use")
            }

            sessionManager.saveExamPredictionsJson(body)
            Log.w(TAG, "✓ 成功: ${parsed.exams?.size ?: 0} exams, generated_at=${parsed.generatedAt}, " +
                "via $redirectCount redirects, ${body.length} bytes")
            return
        }
    }

    /**
     * 策略 B:让 OkHttp 自动跟 302 (用 default client,HTTP/2 + MODERN_TLS)。
     * 用于排除「我们手动实现有 bug」的可能。
     */
    private suspend fun fetchWithAutoRedirect(client: OkHttpClient) {
        Log.w(TAG, "[策略B] GET $REMOTE_URL (auto-redirect)")
        val request = Request.Builder()
            .url(REMOTE_URL)
            .header("User-Agent", userAgent)
            .header("Accept", "application/json, */*;q=0.8")
            .get()
            .build()
        val response = client.newCall(request).execute()

        Log.w(TAG, "[策略B] ← HTTP ${response.code}, final=${response.request.url}")

        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            error("HTTP $code (final=${response.request.url})")
        }

        val body = response.body?.string()
            ?: error("empty body")

        val parsed = gson.fromJson(body, ExamPredictionsDto::class.java)
            ?: error("invalid JSON")

        if (parsed.version != SCHEMA_VERSION) {
            Log.w(TAG, "schema version mismatch: ${parsed.version} (expected $SCHEMA_VERSION), still try to use")
        }

        sessionManager.saveExamPredictionsJson(body)
        Log.w(TAG, "✓ 策略B 成功: ${parsed.exams?.size ?: 0} exams, ${body.length} bytes")
    }

    /**
     * 从缓存中读取 meta 信息(最后更新时间、考试数量等)。
     * 缓存为空 / JSON 无法解析 / 缺 exams 字段(旧版数据) 返回 null。
     */
    fun getCachedMeta(): ExamPredictionsMeta? {
        val json = sessionManager.getExamPredictionsJson() ?: return null
        return runCatching {
            gson.fromJson(json, ExamPredictionsDto::class.java)
        }.getOrNull()?.takeIf { it.exams != null }?.let {
            ExamPredictionsMeta(
                generatedAt = it.generatedAt,
                semester = it.semester,
                dateRange = it.dateRange ?: emptyList(),
                totalCount = it.count,
                source = it.source
            )
        }
    }

    /** 获取本地缓存的所有 Exam 原始记录(未匹配)。 */
    fun getCachedExams(): List<ExamRawRecord>? {
        val json = sessionManager.getExamPredictionsJson() ?: return null
        return runCatching {
            gson.fromJson(json, ExamPredictionsDto::class.java)
        }.getOrNull()?.exams
    }

    // ─────────────────────────────────────────────────────────
    // 匹配
    // ─────────────────────────────────────────────────────────

    /**
     * 与用户课表课程代码做精确匹配,返回预测结果列表。
     *
     * @param userCourseCodes 从 SessionManager.getScheduleJson() 解析出的
     *        courseCode → courseName 映射
     */
    fun matchPredictions(userCourseCodes: Map<String, String>): List<ExamPrediction> {
        val records = getCachedExams() ?: return emptyList()
        val matched = mutableListOf<ExamPrediction>()
        val seen = mutableSetOf<String>()

        for (r in records) {
            val code = r.courseCode ?: continue
            val key = "${r.fullCode ?: "$code.${r.section}"}|${r.date}|${r.start}"
            if (code in userCourseCodes && key !in seen) {
                seen.add(key)
                matched.add(
                    ExamPrediction(
                        courseName = r.courseName.orEmpty(),
                        courseCode = code,
                        section = r.section.orEmpty(),
                        fullCode = r.fullCode ?: "$code.${r.section}",
                        college = r.college.orEmpty(),
                        date = r.date.orEmpty(),
                        startTime = r.start.orEmpty(),
                        endTime = r.end.orEmpty(),
                        roomName = r.roomName.orEmpty(),
                        roomCode = r.roomCode,
                        campus = r.campus,
                        teacherName = r.teacher.orEmpty(),
                        activityId = r.activityId,
                        matchedCourseName = userCourseCodes[code] ?: "",
                        matchType = "code"
                    )
                )
            }
        }

        return matched.sortedWith(
            compareBy({ it.date }, { it.startTime }, { it.roomName })
        )
    }
}