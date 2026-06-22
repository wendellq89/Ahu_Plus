package com.yourname.ahu_plus.data.repository

import android.util.Log
import com.google.gson.JsonObject
import com.yourname.ahu_plus.data.GsonProvider
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.network.SecureHttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

class AdwmhCardRepository(
    private val sessionManager: SessionManager
) {
    companion object {
        private const val TAG = "AdwmhCardRepo"
        const val HOST = "adwmh.ahu.edu.cn"
        const val SESSION_COOKIE = "JSESSIONID"
        const val WECHAT_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36 " +
                "NetType/WIFI MicroMessenger/7.0.20.1781 WindowsWechat Flue"
    }

    private val gson = GsonProvider.instance
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    private val cookieJar = object : CookieJar {
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            seedSessionCookie()
            return cookieStore[url.host] ?: emptyList()
        }

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val hostCookies = cookieStore.getOrPut(url.host) { mutableListOf() }
            for (cookie in cookies) {
                hostCookies.removeAll { it.name == cookie.name }
                hostCookies.add(cookie)
            }
        }
    }

    /**
     * 专用于 adwmh.ahu.edu.cn 的 OkHttp 客户端：
     * - 强制 TLS 1.2（服务器 TLS 1.3 握手后不返回 HTTP 响应）
     * - 短超时（8s 连接 / 12s 读取），避免长时间卡住
     */
    private val client = SecureHttpClientFactory.create(
        cookieJar = cookieJar,
        tls12Only = true,
        connectTimeoutSec = 8,
        readTimeoutSec = 12
    )

    /** 两次请求之间的最小间隔（ms），adwmh 服务器有严格速率限制 */
    private var lastRequestTimeMs = 0L

    /** 确保两次请求之间有足够间隔，避免触发服务器速率限制 */
    private fun cooldown() {
        val elapsed = System.currentTimeMillis() - lastRequestTimeMs
        val minGap = 1500L  // 1.5 秒最小间隔（太大会拖慢整个流程）
        if (elapsed < minGap && lastRequestTimeMs > 0) {
            Thread.sleep(minGap - elapsed)
        }
        lastRequestTimeMs = System.currentTimeMillis()
    }

    // ── 自动登录（参考 AHUTong 项目）─────────────────────

    /**
     * 自动登录智慧安大。
     * 先用空验证码尝试，失败则获取验证码 → OCR → 重试，最多 5 次。
     */
    suspend fun autoLogin(
        username: String,
        password: String,
        concurrentRetry: Boolean = false
    ): Result<AdwmhLoginInfo> = withContext(Dispatchers.IO) {
        Log.d(TAG, "autoLogin start (concurrent=$concurrentRetry)")
        // 先尝试无验证码登录
        val firstTry = doLogin(username, password, "")
        Log.d(TAG, "autoLogin firstTry: ${if (firstTry.isSuccess) "OK" else firstTry.exceptionOrNull()?.message}")
        if (firstTry.isSuccess) return@withContext firstTry

        if (!concurrentRetry) {
            // 常规模式：单线程顺序 OCR 重试（限制 2 次，减少速率限制风险）
            val maxRetries = 2
            repeat(maxRetries) { attempt ->
                val captchaBytes = runCatching {
                    getCaptchaRaw()
                }.getOrElse { return@repeat }

                val captcha = runCatching {
                    ocrCaptcha(captchaBytes)
                }.getOrElse { return@repeat }

                val result = doLogin(username, password, captcha)
                if (result.isSuccess) return@withContext result
            }
            return@withContext Result.failure(AdwmhAuthException("智慧安大登录失败，请稍后重试"))
        }

        // 并发模式已废弃：adwmh 服务器有严格速率限制，并发请求会立即触发限流
        // 改为单线程重试 2 次（与上面逻辑相同）
        Log.w(TAG, "concurrentRetry ignored (disabled due to server rate limiting)")
        val maxRetries = 2
        repeat(maxRetries) { attempt ->
            Log.d(TAG, "autoLogin sequential retry ${attempt + 1}/$maxRetries")
            val captchaBytes = runCatching {
                getCaptchaRaw()
            }.getOrElse { return@repeat }

            val captcha = runCatching {
                ocrCaptcha(captchaBytes)
            }.getOrElse { return@repeat }

            val result = doLogin(username, password, captcha)
            if (result.isSuccess) return@withContext result
        }
        Result.failure(AdwmhAuthException("智慧安大登录失败，请稍后重试"))
    }

    /** 获取验证码图片字节。 */
    private fun getCaptchaRaw(): ByteArray {
        cooldown()
        val request = Request.Builder()
            .url("https://$HOST/remind/authcode")
            .header("User-Agent", WECHAT_UA)
            .header("Accept", "image/webp,image/*,*/*")
            .header("X-Requested-With", "XMLHttpRequest")
            .get()
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw AdwmhAuthException("验证码获取失败")
            response.body?.bytes() ?: throw AdwmhAuthException("验证码为空")
        }
    }

    /** OCR 识别验证码。POST https://openahu.org/ocr/captcha */
    private fun ocrCaptcha(imageBytes: ByteArray): String {
        val mediaType = "image/jpg".toMediaType()
        val part = MultipartBody.Part.createFormData(
            "captcha", "img.jpg",
            imageBytes.toRequestBody(mediaType)
        )
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addPart(part)
            .build()
        val request = Request.Builder()
            .url("https://openahu.org/ocr/captcha")
            .post(body)
            .build()
        // 使用独立 client（不带 cookie）
        val ocrClient = SecureHttpClientFactory.create()
        return ocrClient.newCall(request).execute().use { response ->
            val json = gson.fromJson(
                response.body?.string().orEmpty(),
                JsonObject::class.java
            )
            val result = json?.get("result")?.asString?.trim()
                ?: throw AdwmhAuthException("OCR 识别失败")
            if (result.length !in 3..6) throw AdwmhAuthException("OCR 结果异常: $result")
            result
        }
    }

    /** 执行登录 POST /user/login */
    private suspend fun doLogin(
        username: String,
        password: String,
        captcha: String
    ): Result<AdwmhLoginInfo> = withContext(Dispatchers.IO) {
        cooldown()
        runCatching {
            val formBody = FormBody.Builder()
                .add("username", username)
                .add("pwd", password)
                .add("flag", "0")
                .add("imgcode", captcha)
                .build()
            val request = Request.Builder()
                .url("https://$HOST/user/login")
                .header("User-Agent", WECHAT_UA)
                .header("Accept", "application/json, text/plain, */*")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "https://$HOST/www/index.html")
                .post(formBody)
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw AdwmhAuthException("登录失败(${response.code})")
                val json = gson.fromJson(body, JsonObject::class.java)
                    ?: throw AdwmhAuthException("登录响应为空")
                val code = json.get("code")?.asInt ?: -1
                if (code != 10000) {
                    val msg = json.get("msg")?.asString ?: "登录失败"
                    throw AdwmhAuthException(msg)
                }
                val jsessionid = cookieStore[HOST]?.firstOrNull { it.name == SESSION_COOKIE }?.value
                if (!jsessionid.isNullOrBlank()) sessionManager.saveAdwmhSessionId(jsessionid)
                val obj = json.getAsJsonObject("object")
                val user = obj?.getAsJsonObject("user")
                AdwmhLoginInfo(
                    userName = user?.get("userName")?.asString ?: username,
                    cardId = user?.get("cardId")?.asString ?: "",
                    unitName = user?.get("unitName")?.asString ?: ""
                )
            }
        }
    }

    // ── 兼容旧版：手动导入 session ─────────────────────────

    /** @deprecated 推荐使用 [login] 直接登录。 */
    suspend fun importSessionId(sessionId: String) {
        val normalized = sessionId.trim()
        if (normalized.isBlank()) return
        sessionManager.saveAdwmhSessionId(normalized)
        setSessionCookie(normalized)
    }

    fun clearCookies() {
        cookieStore.clear()
    }

    // ── 业务 API ─────────────────────────────────────────

    suspend fun getQrCode(): Result<AdwmhQrCode> = withContext(Dispatchers.IO) {
        runCatching {
            ensureSessionId()
            Log.d(TAG, "getQrCode: fetching...")
            val json = getJson("/xzxcard/qrcode")
            val payload = json.get("object")?.asString?.takeIf { it.isNotBlank() }
                ?: throw AdwmhAuthException("QR payload is empty")
            Log.d(TAG, "getQrCode: OK (payload len=${payload.length})")
            AdwmhQrCode(
                payload = payload,
                serverTimeText = json.get("msg")?.asString.orEmpty(),
                fetchedAt = System.currentTimeMillis()
            )
        }
    }

    suspend fun getBalance(): Result<Double> = withContext(Dispatchers.IO) {
        runCatching {
            ensureSessionId()
            val json = getJson("/xzxcard/yue")
            json.get("object")?.asDouble ?: throw AdwmhAuthException("balance is empty")
        }
    }

    suspend fun validateSession(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            ensureSessionId()
            getJson("/user/session", method = "POST")
            Unit
        }
    }

    /** 是否已配置 session（含持久化的旧 session）。 */
    fun hasSession(): Boolean {
        return !sessionManager.getAdwmhSessionId().isNullOrBlank()
    }

    // ── 内部方法 ──────────────────────────────────────────

    private fun ensureSessionId(): String {
        val sessionId = sessionManager.getAdwmhSessionId()?.takeIf { it.isNotBlank() }
            ?: throw AdwmhAuthException("请先登录智慧安大")
        setSessionCookie(sessionId)
        return sessionId
    }

    private fun seedSessionCookie() {
        val sessionId = sessionManager.getAdwmhSessionId()?.takeIf { it.isNotBlank() } ?: return
        val hasCookie = cookieStore[HOST]?.any { it.name == SESSION_COOKIE } == true
        if (!hasCookie) setSessionCookie(sessionId)
    }

    private fun setSessionCookie(sessionId: String) {
        val hostCookies = cookieStore.getOrPut(HOST) { mutableListOf() }
        hostCookies.removeAll { it.name == SESSION_COOKIE }
        hostCookies.add(
            Cookie.Builder()
                .domain(HOST)
                .path("/")
                .name(SESSION_COOKIE)
                .value(sessionId)
                .httpOnly()
                .build()
        )
    }

    private fun getJson(path: String, method: String = "GET"): JsonObject {
        cooldown()
        val url = "https://$HOST$path"
        Log.d(TAG, "$method $url")
        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", WECHAT_UA)
            .header("Accept", "application/json, text/plain, */*")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", "https://$HOST/www/index.html")

        if (method == "POST") {
            requestBuilder
                .header("Origin", "https://$HOST")
                .post(ByteArray(0).toRequestBody(null))
        }

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                Log.d(TAG, "$method $path → ${response.code} (body len=${body.length})")
                if (response.code == 401 || response.code == 403 || response.code in 300..399) {
                    throw AdwmhAuthException("智慧安大会话已过期，请重新登录")
                }
                if (!response.isSuccessful) {
                    throw AdwmhAuthException("智慧安大 HTTP ${response.code}")
                }
                if (body.trimStart().startsWith("<")) {
                    throw AdwmhAuthException("智慧安大返回 HTML，请重新登录")
                }
                val json = gson.fromJson(body, JsonObject::class.java)
                    ?: throw AdwmhAuthException("智慧安大响应为空")
                val code = json.get("code")?.asInt
                if (code != 10000) {
                    throw AdwmhAuthException(json.get("msg")?.asString ?: "智慧安大请求失败")
                }
                return json
            }
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "$method $path → TIMEOUT: ${e.message}")
            throw AdwmhAuthException("智慧安大连接超时")
        } catch (e: Exception) {
            if (e is AdwmhAuthException) throw e
            Log.e(TAG, "$method $path → ${e.javaClass.simpleName}: ${e.message}")
            throw AdwmhAuthException("智慧安大请求失败: ${e.message}")
        }
    }

}

// ── 数据模型 ─────────────────────────────────────────────

data class AdwmhQrCode(
    val payload: String,
    val serverTimeText: String,
    val fetchedAt: Long
)

/** 登录成功后返回的用户信息。 */
data class AdwmhLoginInfo(
    val userName: String,
    val cardId: String,
    val unitName: String
)

class AdwmhAuthException(message: String) : Exception(message)
