package com.yourname.ahu_plus.data.repository

import android.util.Log
import com.google.gson.JsonParser
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.network.SecureHttpClientFactory
import com.yourname.ahu_plus.util.DES
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/**
 * CAS 自动登录仓库。
 *
 * 处理安徽大学 CAS (one.ahu.edu.cn) 的完整 5 步认证流程:
 *   1. GET  login page → 提取 lt + execution
 *   2. POST /cas/device → 预验证加密凭据
 *   3. POST /cas/login → 提交表单 → CASTGC cookie
 *   4. GET  /cas/login (CASTGC) → ST ticket
 *   5. GET  /tp_up/view (ST) → JSESSIONID
 */
class CasAuthRepository(
    private val sessionManager: SessionManager,
    /** 内部可注入的 CAS base URL,测试时可指向 MockWebServer */
    casBaseUrl: String = "https://one.ahu.edu.cn/cas",
    tpUpBaseUrl: String = "https://one.ahu.edu.cn/tp_up"
) {
    private val casBaseUrl = if (casBaseUrl.endsWith("/")) casBaseUrl.dropLast(1) else casBaseUrl
    private val tpUpBaseUrl = if (tpUpBaseUrl.endsWith("/")) tpUpBaseUrl.dropLast(1) else tpUpBaseUrl
    private val service = "$tpUpBaseUrl/view?m=up"
    private val casHost = "one.ahu.edu.cn"

    companion object {
        private const val TAG = "CasAuth"
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36"
    }

    // ── Cookie 存储 ──────────────────────────────────
    val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    /**
     * JSESSIONID 缓存:把查询从 O(n) 降到 O(1)。
     * 缓存值失效时回退到 cookieStore 重新扫描,确保正确性。
     */
    @Volatile
    private var cachedJsessionId: String? = null

    /** 获取当前有效的 JSESSIONID(供 CardRepository 直接使用) */
    fun getJsessionid(): String? {
        cachedJsessionId?.let { return it }
        val id = cookieStore[casHost]
            ?.find { it.name == "JSESSIONID" }
            ?.value
        cachedJsessionId = id
        return id
    }

    private val cookieJar = object : CookieJar {
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore[url.host] ?: emptyList()
        }

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val hostCookies = cookieStore.getOrPut(url.host) { mutableListOf() }
            for (cookie in cookies) {
                // 移除同名旧 cookie
                hostCookies.removeAll { it.name == cookie.name }
                hostCookies.add(cookie)
            }
            // 任意 cookie 变化都使 JSESSIONID 缓存失效
            if (cookies.any { it.name == "JSESSIONID" || it.name == "CASTGC" }) {
                cachedJsessionId = null
            }
        }
    }

    // ── OkHttp 客户端(手动跟随重定向以捕获 Set-Cookie)───────
    private val client: OkHttpClient = SecureHttpClientFactory.create(
        cookieJar = cookieJar,
        followRedirects = false,
        disableGzip = true  // CAS 流程关闭 gzip
    )

    /** 公开给 YcardRepository 等复用 CAS cookie 的系统 */
    fun getCookieJar(): CookieJar = cookieJar

    /** 清除所有内存 Cookie(退出登录时调用) */
    fun clearCookies() {
        cookieStore.clear()
        cachedJsessionId = null
    }

    // ══════════════════════════════════════════════════════
    // 公开 API
    // ══════════════════════════════════════════════════════

    /**
     * 使用用户名和密码登录 CAS,成功后将 JSESSIONID 和凭据保存到 SessionManager。
     */
    suspend fun login(username: String, password: String): Result<Unit> {
        return try {
            Log.e(TAG, "开始 CAS 登录: user=$username")
            clearCookies()

            // Step 1: 获取 lt 和 execution
            val (lt, execution) = fetchLoginPage()

            // Step 2: DES 加密
            val encrypted = DES.strEnc(username + password + lt, "1", "2", "3")
            val ul = username.length
            val pl = password.length
            Log.e(TAG, "加密结果: ${encrypted.take(40)}...")

            // Step 3: device 预验证
            performDeviceAuth(encrypted, ul, pl)

            // Step 4: 提交登录表单 → CASTGC
            val castgc = submitLoginForm(encrypted, ul, pl, lt, execution)
            Log.e(TAG, "获取到 CASTGC: ${castgc.take(20)}...")

            // Step 5: CASTGC → ST ticket
            val ticket = exchangeForServiceTicket()
            Log.e(TAG, "获取到 ST: $ticket")

            // Step 6: ST → JSESSIONID
            val jsessionid = exchangeForJsessionid(ticket)
            Log.e(TAG, "获取到 JSESSIONID: ${jsessionid.take(16)}...")

            // 保存
            sessionManager.saveSessionId(jsessionid)
            sessionManager.saveCredentials(username, password)
            Log.e(TAG, "登录成功，JSESSIONID=$jsessionid")
            Log.e(TAG, "cookieStore keys: ${cookieStore.keys}")
            cookieStore[casHost]?.forEach { c ->
                Log.e(TAG, "  cookie: ${c.name}=${c.value} path=${c.path}")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "登录失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 使用已保存的凭据自动登录。
     */
    suspend fun autoLogin(): Result<Unit> {
        val username = sessionManager.getUsername()
        val password = sessionManager.getPassword()
        return if (username != null && password != null) {
            login(username, password)
        } else {
            Result.failure(Exception("未保存凭据"))
        }
    }

    /**
     * 验证当前 JSESSIONID 是否有效:若失效则尝试重新登录。
     *
     * 实现策略:直接使用 GET 一个轻量接口来探测。
     * 若返回 200 且 body 是 JSON → session 有效。
     * 若返回 302 到 CAS 或 body 含登录页标记 → session 失效。
     */
    suspend fun ensureValidSession(): Result<Unit> {
        val sessionId = sessionManager.getSessionId()
        if (sessionId.isNullOrBlank()) {
            return autoLogin()
        }
        // 用现有 JSESSIONID 探测
        return probeSession(sessionId).recoverCatching {
            Log.e(TAG, "JSESSIONID 已失效，尝试重新登录")
            clearCookies()
            sessionManager.clearSession()
            autoLogin().getOrThrow()
        }
    }

    /**
     * 使用现有 CASTGC 为任意 one.ahu.edu.cn 业务系统换取 ST ticket,并访问目标 service
     * 以激活该业务系统自己的 JSESSIONID。
     */
    suspend fun authenticateService(serviceUrl: String): Result<Unit> {
        return try {
            val castgc = cookieStore[casHost]?.find { it.name == "CASTGC" }?.value
            if (castgc.isNullOrBlank()) {
                autoLogin().getOrThrow()
            }

            val ticket = exchangeForServiceTicket(serviceUrl)
            val serviceWithTicket = serviceUrl.toHttpUrl()
                .newBuilder()
                .addQueryParameter("ticket", ticket)
                .build()

            val redirectClient = client.newBuilder()
                .followRedirects(true)
                .build()
            redirectClient.newCall(
                Request.Builder()
                    .url(serviceWithTicket)
                    .header("User-Agent", UA)
                    .build()
            ).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (body.contains("cas/login") || body.contains("name=\"lt\"")) {
                    throw CasAuthException("业务系统会话激活失败")
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "业务系统认证失败: service=$serviceUrl, ${e.message}", e)
            Result.failure(e)
        }
    }

    /** 探测 JSESSIONID 是否有效(用 /tp_up/view 一个轻量 GET) */
    private suspend fun probeSession(sessionId: String): Result<Unit> {
        return try {
            val request = Request.Builder()
                .url("$tpUpBaseUrl/view?m=up")
                .header("Cookie", "JSESSIONID=$sessionId")
                .header("User-Agent", UA)
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful && body.contains("\"code\"")) {
                    Result.success(Unit)
                } else if (response.code == 302 || body.contains("name=\"lt\"")) {
                    // 仅 CAS 登录表单(含 name="lt")才是真过期；HTML 中引用 "cas/login" 链接不算
                    Result.failure(Exception("JSESSIONID 失效"))
                } else {
                    // 其他情况(VPN拦截页/门户首页/网络错误):乐观认为有效,让真正的 API 调用再判断
                    Result.success(Unit)
                }
            }
        } catch (e: Exception) {
            // 网络错误时不要清 session,留给业务 API 决定
            Result.success(Unit)
        }
    }

    // ══════════════════════════════════════════════════════
    // 内部 CAS 流程
    // ══════════════════════════════════════════════════════

    /** Step 1: GET CAS 登录页面,提取 lt 和 execution */
    private suspend fun fetchLoginPage(): Pair<String, String> {
        val url = "$casBaseUrl/login?service=$service"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .build()

        client.newCall(request).execute().use { response ->
            val html = response.body?.string() ?: ""
            if (response.code in 300..399) {
                throw CasAuthException("已有有效 CASTGC(重定向),请先清除 Cookie")
            }
            val lt = Regex("""name="lt"\s+value="([^"]+)"""").find(html)?.groupValues?.get(1)
                ?: throw CasAuthException("未找到 lt 字段")
            val execution = Regex("""name="execution"\s+value="([^"]+)"""").find(html)?.groupValues?.get(1)
                ?: throw CasAuthException("未找到 execution 字段")
            Log.e(TAG, "lt=$lt, execution=$execution")
            return Pair(lt, execution)
        }
    }

    /** Step 2-3: POST /cas/device 预验证 */
    private suspend fun performDeviceAuth(encrypted: String, ul: Int, pl: Int) {
        val formBody = FormBody.Builder()
            .add("ul", ul.toString())
            .add("pl", pl.toString())
            .add("rsa", encrypted)
            .add("method", "login")
            .build()

        val request = Request.Builder()
            .url("$casBaseUrl/device")
            .header("User-Agent", UA)
            .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("Referer", "$casBaseUrl/login?service=$service")
            .post(formBody)
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            Log.e(TAG, "device 响应: $body")
            val json = JsonParser.parseString(body).asJsonObject
            val info = json.get("info")?.asString ?: ""
            when (info) {
                "ok" -> { /* 验证通过 */ }
                "nf" -> throw CasAuthException("学号或密码错误")
                "err" -> throw CasAuthException("登录验证失败，请稍后重试")
                else -> throw CasAuthException("设备验证失败: $info")
            }
        }
    }

    /** Step 4: POST /cas/login 提交表单 → CASTGC */
    private suspend fun submitLoginForm(
        encrypted: String, ul: Int, pl: Int, lt: String, execution: String
    ): String {
        val formBody = FormBody.Builder()
            .add("rsa", encrypted)
            .add("ul", ul.toString())
            .add("pl", pl.toString())
            .add("lt", lt)
            .add("execution", execution)
            .add("_eventId", "submit")
            .build()

        val request = Request.Builder()
            .url("$casBaseUrl/login?service=$service")
            .header("User-Agent", UA)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Referer", "$casBaseUrl/login?service=$service")
            .post(formBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code !in 300..399) {
                throw CasAuthException("CAS 登录表单提交失败: HTTP ${response.code}")
            }
            // 从 cookie 存储中提取 CASTGC
            val castgc = cookieStore[casHost]
                ?.find { it.name == "CASTGC" }
                ?.value
                ?: throw CasAuthException("未获取到 CASTGC cookie")
            return castgc
        }
    }

    /** Step 5: GET /cas/login (带 CASTGC) → ST ticket */
    private suspend fun exchangeForServiceTicket(): String {
        val request = Request.Builder()
            .url("$casBaseUrl/login?service=$service")
            .header("User-Agent", UA)
            .build()

        client.newCall(request).execute().use { response ->
            val location = response.header("Location")
                ?: throw CasAuthException("未收到 CAS 重定向 (Location)")
            return Regex("""ticket=([^&]+)""").find(location)?.groupValues?.get(1)
                ?: throw CasAuthException("未在 Location 中找到 ticket: $location")
        }
    }

    /** GET /cas/login (带 CASTGC) → 指定 service 的 ST ticket */
    private suspend fun exchangeForServiceTicket(serviceUrl: String): String {
        val casLoginUrl = "$casBaseUrl/login".toHttpUrl()
            .newBuilder()
            .addQueryParameter("service", serviceUrl)
            .build()
        val request = Request.Builder()
            .url(casLoginUrl)
            .header("User-Agent", UA)
            .build()

        client.newCall(request).execute().use { response ->
            val location = response.header("Location")
                ?: throw CasAuthException("未收到 CAS 重定向 (Location)")
            return Regex("""ticket=([^&]+)""").find(location)?.groupValues?.get(1)
                ?: throw CasAuthException("未在 Location 中找到 ticket: $location")
        }
    }

    /** Step 6: GET /tp_up/view (带 ST) → JSESSIONID */
    private suspend fun exchangeForJsessionid(ticket: String): String {
        val request = Request.Builder()
            .url("$tpUpBaseUrl/view?m=up&ticket=$ticket")
            .header("User-Agent", UA)
            .build()

        client.newCall(request).execute().use { /* response body discarded */ }
        val jsessionid = cookieStore[casHost]
            ?.find { it.name == "JSESSIONID" }
            ?.value
            ?: throw CasAuthException("未获取到 JSESSIONID cookie")
        return jsessionid
    }
}

class CasAuthException(message: String) : Exception(message)
