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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/**
 * 教务处 (jw.ahu.edu.cn) CAS SSO 认证仓库。
 *
 * 两种认证策略:
 *  1. **简易 SSO**(首选):复用 CasAuthRepository 已有的 CASTGC cookie,
 *     直接向 CAS 请求 jw 服务的 service ticket。
 *  2. **完整 CAS 登录**(回退):如果 CASTGC 缺失或已过期,
 *     执行完整的 6 步 CAS 登录流程,service URL 指向教务处 SSO。
 *
 * 成功后自动保存 SESSION + __pstsid__ 到 SessionManager。
 */
class JwAuthRepository(
    private val sessionManager: SessionManager,
    private val casAuthRepository: CasAuthRepository
) {
    companion object {
        private const val TAG = "JwAuth"
        private const val JW_BASE = "https://jw.ahu.edu.cn"
        private const val JW_SSO = "$JW_BASE/student/sso/login"
        private const val CAS_BASE = "https://one.ahu.edu.cn/cas"
        private val SERVICE = JW_SSO
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0"
        private const val JW_HOST = "jw.ahu.edu.cn"
    }

    // ── Cookie 存储(共享给 CourseRepository 使用)────────
    val jwCookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    val jwCookieJar = object : CookieJar {
        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            jwCookieStore[url.host] ?: emptyList()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val hostCookies = jwCookieStore.getOrPut(url.host) { mutableListOf() }
            for (cookie in cookies) {
                hostCookies.removeAll { it.name == cookie.name }
                hostCookies.add(cookie)
            }
        }
    }

    // ── OkHttp 客户端(手动跟随重定向)─────────────────
    private val client: OkHttpClient = SecureHttpClientFactory.create(
        cookieJar = jwCookieJar,
        followRedirects = false,
        disableGzip = true
    )

    // ── CAS 回退客户端(独立 cookie store,不污染主 store)──
    private val casClient: OkHttpClient = SecureHttpClientFactory.create(
        cookieJar = createIsolatedCasJar(),
        followRedirects = false,
        disableGzip = true
    )

    /** 清除所有内存 Cookie(退出登录时调用) */
    fun clearCookies() {
        jwCookieStore.clear()
    }

    // ══════════════════════════════════════════════════════

    /** 获取当前 JW SESSION cookie 值 */
    fun getJwSessionId(): String? {
        return sessionManager.getJwSessionId()
            ?: jwCookieStore[JW_HOST]?.find { it.name == "SESSION" }?.value
    }

    /** 获取当前 JW __pstsid__ cookie 值 */
    fun getJwPstSid(): String? {
        return sessionManager.getJwPstSid()
            ?: jwCookieStore[JW_HOST]?.find { it.name == "__pstsid__" }?.value
    }

    /** 获取完整的 JW Cookie 头(直接从 CookieStore 读,不经过 SessionManager) */
    fun getJwCookieHeader(): String {
        val cookies = jwCookieStore[JW_HOST] ?: emptyList()
        return cookies.joinToString("; ") { "${it.name}=${it.value}" }
    }

    /**
     * 尝试认证教务处。
     * 优先尝试简易 SSO(复用 CASTGC),失败则回退到完整 CAS 登录。
     */
    suspend fun authenticate(): Result<Unit> {
        return try {
            // 先尝试从持久化存储恢复
            val savedSession = sessionManager.getJwSessionId()
            val savedPstSid = sessionManager.getJwPstSid()
            if (!savedSession.isNullOrBlank() && !savedPstSid.isNullOrBlank()) {
                Log.e(TAG, "使用已保存的 JW session: ${savedSession.take(16)}...")
                saveToCookieStore(JW_HOST, "SESSION", savedSession)
                saveToCookieStore(JW_HOST, "__pstsid__", savedPstSid)
                return Result.success(Unit)
            }

            Log.e(TAG, "开始 JW SSO 认证...")
            // 策略 1: 简易 SSO(复用 CASTGC)
            try {
                trySimplifiedSso()
                Log.e(TAG, "简易 SSO 成功")
            } catch (e: Exception) {
                Log.e(TAG, "简易 SSO 失败: ${e.message}，回退到完整 CAS 登录")
                // 策略 2: 完整 CAS 回退
                performFullLogin()
                Log.e(TAG, "完整 CAS 登录成功")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "JW 认证失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ══════════════════════════════════════════════════════
    // 策略 1: 简易 SSO(复用 CASTGC)
    // ══════════════════════════════════════════════════════

    private suspend fun trySimplifiedSso() {
        // Step A: 访问 JW SSO → 期望 302 到 CAS
        val jwSsoResponse = client.newCall(Request.Builder()
            .url(JW_SSO)
            .header("User-Agent", UA)
            .header("Referer", "https://one.ahu.edu.cn/")
            .build()).execute()

        val location1 = jwSsoResponse.header("Location") ?: ""
        jwSsoResponse.use { /* auto close */ }
        Log.e(TAG, "JW SSO → 302 Location: ${location1.take(80)}...")

        if (!location1.contains("cas/login")) {
            // 可能已经有有效 session：302 到 JW 首页，或 200 返回页面
            if (jwSsoResponse.code == 200 || jwSsoResponse.code in 300..399) {
                val session = jwCookieStore[JW_HOST]?.find { it.name == "SESSION" }?.value
                if (session != null) {
                    Log.e(TAG, "JW 已有有效 session (HTTP ${jwSsoResponse.code} → ${location1.take(60)})")
                    persistSession(session)
                    sessionManager.saveJwSession(session, "")
                    return
                }
                // 302 但没有 SESSION cookie —— 跟随重定向获取
                if (jwSsoResponse.code in 300..399) {
                    val redirectClient = client.newBuilder().followRedirects(true).build()
                    redirectClient.newCall(Request.Builder()
                        .url(JW_SSO)
                        .header("User-Agent", UA)
                        .build()).execute().use {
                            val session2 = jwCookieStore[JW_HOST]?.find { it.name == "SESSION" }?.value
                            if (session2 != null) {
                                Log.e(TAG, "跟随 JW 重定向后获取到 session: ${session2.take(16)}...")
                                persistSession(session2)
                                sessionManager.saveJwSession(session2, "")
                                return
                            }
                        }
                }
            }
            throw JwAuthException("JW SSO 未返回 CAS 重定向且无 session: HTTP ${jwSsoResponse.code}")
        }

        // Step B: 读取 CASTGC,带到 CAS 请求
        val castgc = getCastgcFromExisting()
            ?: throw JwAuthException("没有有效的 CASTGC,需要完整登录")

        val casUrl = if (location1.startsWith("http")) location1
            else "https://one.ahu.edu.cn$location1"

        client.newCall(Request.Builder()
            .url(casUrl)
            .header("User-Agent", UA)
            .header("Cookie", "CASTGC=$castgc")
            .build()).execute().use {
            val location2 = it.header("Location") ?: ""
            Log.e(TAG, "CAS (with CASTGC) → 302 Location: ${location2.take(80)}...")

            // Step C: 跟随 ticket 重定向到 JW(必须带 CASTGC 验证)
            if (!location2.contains("ticket=")) {
                throw JwAuthException("CAS 未返回 ST ticket: $location2")
            }
            val redirectClient2 = client.newBuilder().followRedirects(true).build()
            redirectClient2.newCall(Request.Builder()
                .url(location2)
                .header("User-Agent", UA)
                .build()).execute().use { /* 让 JW 内部跳转激活 session */ }
        }

        // Step D: 提取 SESSION
        val session = jwCookieStore[JW_HOST]?.find { it.name == "SESSION" }?.value
            ?: throw JwAuthException("未获取到 JW SESSION cookie")

        Log.e(TAG, "获取到 SESSION: ${session.take(16)}...")
        sessionManager.saveJwSession(session, "")
    }

    /** 从 CasAuthRepository 的 cookieStore 中读取 CASTGC */
    private fun getCastgcFromExisting(): String? {
        return casAuthRepository.cookieStore["one.ahu.edu.cn"]
            ?.find { it.name == "CASTGC" }?.value
    }

    private fun persistSession(session: String) {
        saveToCookieStore(JW_HOST, "SESSION", session)
    }

    private fun saveToCookieStore(host: String, name: String, value: String) {
        val list = jwCookieStore.getOrPut(host) { mutableListOf() }
        list.removeAll { it.name == name }
        list.add(Cookie.Builder()
            .name(name)
            .value(value)
            .domain(host)
            .path("/")
            .build())
    }

    // ══════════════════════════════════════════════════════
    // 策略 2: 完整 CAS 登录回退
    // ══════════════════════════════════════════════════════

    private suspend fun performFullLogin() {
        val username = sessionManager.getUsername()
            ?: throw JwAuthException("没有保存的凭据，无法自动登录教务处")
        val password = sessionManager.getPassword()
            ?: throw JwAuthException("没有保存的凭据，无法自动登录教务处")

        Log.e(TAG, "执行完整 CAS 登录: user=$username")

        // Step 1: 获取 lt + execution
        val (lt, execution) = fetchLoginPageFb()

        // Step 2: DES 加密
        val encrypted = DES.strEnc(username + password + lt, "1", "2", "3")
        val ul = username.length
        val pl = password.length

        // Step 3: device 预验证
        performDeviceAuthFb(encrypted, ul, pl)

        // Step 4: 提交登录表单 → CASTGC
        val castgc = submitLoginFormFb(encrypted, ul, pl, lt, execution)
        Log.e(TAG, "回退: 获取到 CASTGC: ${castgc.take(20)}...")

        // 把 CASTGC 同步到 CasAuthRepository 的 cookieStore(共享)
        val oneHostCookies = casAuthRepository.cookieStore
            .getOrPut("one.ahu.edu.cn") { mutableListOf() }
        oneHostCookies.removeAll { it.name == "CASTGC" }
        oneHostCookies.add(Cookie.Builder()
            .name("CASTGC").value(castgc).domain("one.ahu.edu.cn").path("/").build())

        // Step 5: CASTGC → ST ticket (jw service)
        val ticket = exchangeForTicketFb(castgc)
        Log.e(TAG, "回退: 获取到 ST: $ticket")

        // Step 6: ST → JW SESSION
        val session = exchangeTicketForSessionFb(ticket)

        Log.e(TAG, "回退: 获取到 SESSION: ${session.take(16)}...")
        sessionManager.saveJwSession(session, "")
    }

    /** 回退 Step 1: GET CAS 登录页面 */
    private suspend fun fetchLoginPageFb(): Pair<String, String> {
        casClient.newCall(Request.Builder()
            .url("$CAS_BASE/login?service=$SERVICE")
            .header("User-Agent", UA).build()).execute().use { response ->
            val html = response.body?.string() ?: ""
            if (response.code in 300..399) {
                throw JwAuthException("已有有效 CASTGC(重定向),不应执行完整登录")
            }
            val lt = Regex("""name="lt"\s+value="([^"]+)"""").find(html)?.groupValues?.get(1)
                ?: throw JwAuthException("未找到 lt 字段")
            val execution = Regex("""name="execution"\s+value="([^"]+)"""").find(html)?.groupValues?.get(1)
                ?: throw JwAuthException("未找到 execution 字段")
            return Pair(lt, execution)
        }
    }

    /** 回退 Step 2-3: device 预验证 */
    private suspend fun performDeviceAuthFb(encrypted: String, ul: Int, pl: Int) {
        val formBody = FormBody.Builder()
            .add("ul", ul.toString())
            .add("pl", pl.toString())
            .add("rsa", encrypted)
            .add("method", "login")
            .build()
        casClient.newCall(Request.Builder()
            .url("$CAS_BASE/device")
            .header("User-Agent", UA)
            .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("Referer", "$CAS_BASE/login?service=$SERVICE")
            .post(formBody).build()).execute().use { response ->
            val body = response.body?.string() ?: ""
            val json = JsonParser.parseString(body).asJsonObject
            when (json.get("info")?.asString) {
                "ok" -> {}
                "nf" -> throw JwAuthException("学号或密码错误")
                "err" -> throw JwAuthException("登录验证失败，请稍后重试")
                else -> throw JwAuthException("设备验证失败")
            }
        }
    }

    /** 回退 Step 4: POST /cas/login → CASTGC */
    private suspend fun submitLoginFormFb(
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
        casClient.newCall(Request.Builder()
            .url("$CAS_BASE/login?service=$SERVICE")
            .header("User-Agent", UA)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Referer", "$CAS_BASE/login?service=$SERVICE")
            .post(formBody).build()).execute().use { response ->
            if (response.code !in 300..399) {
                throw JwAuthException("CAS 表单提交失败: HTTP ${response.code}")
            }
            // 从 response headers 直接提取 CASTGC(casClient 的 store 是隔离的,无法访问)
            return extractCastgcFromResponse(response)
        }
    }

    private fun extractCastgcFromResponse(response: okhttp3.Response): String {
        val setCookieHeaders = response.headers("Set-Cookie")
        for (header in setCookieHeaders) {
            val match = Regex("CASTGC=([^;]+)").find(header)
            if (match != null) return match.groupValues[1]
        }
        throw JwAuthException("未从响应中提取到 CASTGC")
    }

    /** 回退 Step 5: CASTGC → ST ticket */
    private suspend fun exchangeForTicketFb(castgc: String): String {
        casClient.newCall(Request.Builder()
            .url("$CAS_BASE/login?service=$SERVICE")
            .header("User-Agent", UA)
            .header("Cookie", "CASTGC=$castgc")
            .build()).execute().use { response ->
            val location = response.header("Location")
                ?: throw JwAuthException("CAS 未返回 Location")
            return Regex("""ticket=([^&]+)""").find(location)?.groupValues?.get(1)
                ?: throw JwAuthException("未找到 ticket: $location")
        }
    }

    /** 回退 Step 6: ST → JW SESSION,并跟随重定向激活 session */
    private suspend fun exchangeTicketForSessionFb(ticket: String): String {
        val url = "$JW_SSO?ticket=$ticket"
        // 用跟随重定向的客户端,让 session 被 JW 首页"激活"
        val redirectClient = client.newBuilder().followRedirects(true).build()
        redirectClient.newCall(Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .build()).execute().use { response ->
            val finalUrl = response.request.url.toString()
            Log.e(TAG, "ST → JW final URL: ${finalUrl.take(100)}")
            val session = jwCookieStore[JW_HOST]?.find { it.name == "SESSION" }?.value
                ?: throw JwAuthException("未获取到 JW SESSION")
            Log.e(TAG, "获取到 SESSION: ${session.take(16)}... (final: ${finalUrl.take(60)})")
            return session
        }
    }

    /** 独立的 CookieJar,完整 CAS 回退时用,不污染主 store */
    private fun createIsolatedCasJar(): CookieJar {
        val store = ConcurrentHashMap<String, MutableList<Cookie>>()
        return object : CookieJar {
            override fun loadForRequest(url: HttpUrl) = store[url.host] ?: emptyList()
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val hostCookies = store.getOrPut(url.host) { mutableListOf() }
                for (c in cookies) {
                    hostCookies.removeAll { it.name == c.name }
                    hostCookies.add(c)
                }
            }
        }
    }
}

class JwAuthException(message: String) : Exception(message)