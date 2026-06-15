package com.yourname.ahu_plus.data.repository

import android.util.Log
import com.yourname.ahu_plus.data.GsonProvider
import com.yourname.ahu_plus.data.model.BillResponse
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
 * 一卡通账单仓库 (ycard.ahu.edu.cn)。
 *
 * 认证链路:
 *   1. 优先复用 CasAuthRepository 已有的 CASTGC(简易 SSO),大幅缩短登录耗时
 *   2. 若 CASTGC 缺失或已过期,回退到完整 CAS 登录(DES 加密 + 6 步流程)
 *   3. 跟随重定向 → 最终 URL 含 synjones-auth JWT
 *   4. 用 JWT + SESSION cookie 调账单 API
 */
class YcardRepository(
    private val casAuthRepository: CasAuthRepository? = null
) {
    companion object {
        private const val TAG = "YcardRepo"
        private const val YCARD_BASE = "https://ycard.ahu.edu.cn"
        private val YCARD_CAS_ENTRY = "$YCARD_BASE/berserker-auth/cas/login/neusoftCas" +
            "?targetUrl=https%3A%2F%2Fycard.ahu.edu.cn%2Fberserker-base%2Fredirect%3FappId%3D16%26type%3Dapp"
        private const val CAS_BASE = "https://one.ahu.edu.cn/cas"
        private val SERVICE = YCARD_CAS_ENTRY
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36"
        private const val CAS_HOST = "one.ahu.edu.cn"
        private const val YCARD_HOST = "ycard.ahu.edu.cn"
    }

    private val gson = GsonProvider.instance

    // ── Cookie 存储 ──────────────────────────────────
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()
    private val cookieJar = object : CookieJar {
        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            cookieStore[url.host] ?: emptyList()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val hostCookies = cookieStore.getOrPut(url.host) { mutableListOf() }
            for (cookie in cookies) {
                hostCookies.removeAll { it.name == cookie.name }
                hostCookies.add(cookie)
            }
        }
    }

    // ── OkHttp 客户端 ──────────────────────────────────
    private val client: OkHttpClient = SecureHttpClientFactory.create(
        cookieJar = cookieJar,
        followRedirects = false,
        disableGzip = true
    )

    // 第二个客户端用于跟随重定向提取 JWT
    private val redirectClient: OkHttpClient = client.newBuilder()
        .followRedirects(true)
        .build()

    // 缓存的 JWT
    @Volatile
    private var cachedJwt: String? = null

    /** 清除内存 cookie 和 JWT(退出登录时调用) */
    fun clearCookies() {
        cookieStore.clear()
        cachedJwt = null
    }

    // ══════════════════════════════════════════════════════
    // 公开 API
    // ══════════════════════════════════════════════════════

    /**
     * 登录 ycard。
     * 优先复用 CasAuthRepository 的 CASTGC,失败时回退到完整 CAS 登录。
     */
    suspend fun login(username: String, password: String): Result<Unit> {
        return try {
            Log.e(TAG, "开始 ycard 认证...")
            cookieStore.clear()
            cachedJwt = null

            // 策略 1: 复用 CASTGC 走简易 SSO
            val castgc = casAuthRepository?.cookieStore?.get(CAS_HOST)
                ?.find { it.name == "CASTGC" }?.value
            if (castgc != null) {
                try {
                    Log.e(TAG, "尝试复用 CASTGC 走简易 SSO")
                    loginWithCastgc(castgc)
                    Log.e(TAG, "ycard 简易 SSO 成功")
                    return Result.success(Unit)
                } catch (e: Exception) {
                    Log.e(TAG, "简易 SSO 失败，回退到完整登录: ${e.message}")
                    cookieStore.clear()
                    cachedJwt = null
                }
            }

            // 策略 2: 完整 CAS 登录(走 6 步流程)
            performFullLogin(username, password)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "ycard 认证失败", e)
            Result.failure(e)
        }
    }

    /**
     * 查询一卡通账单。
     */
    suspend fun getBills(current: Int = 1, size: Int = 20): Result<BillResponse> {
        return try {
            val jwt = cachedJwt ?: return Result.failure(Exception("未登录 ycard"))

            val url = "$YCARD_BASE/berserker-search/search/personal/turnover" +
                "?size=$size&current=$current&synAccessSource=h5"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("synjones-auth", "bearer $jwt")
                .header("synAccessSource", "h5")
                .header("Accept", "*/*")
                .header("Referer", "$YCARD_BASE/campus-card/billing/list?appId=16&type=app")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                val code = response.code
                Log.e(TAG, "账单 API HTTP $code")

                if (code != 200) {
                    return Result.failure(Exception("账单查询失败 HTTP $code"))
                }

                val billResp = gson.fromJson(body, BillResponse::class.java)
                Result.success(billResp)
            }
        } catch (e: Exception) {
            Log.e(TAG, "账单查询错误", e)
            Result.failure(e)
        }
    }

    // ══════════════════════════════════════════════════════
    // 策略 1: 复用 CASTGC 简易 SSO
    // ══════════════════════════════════════════════════════

    /** 复用 CAS cookie,直接请求 ycard CAS 入口,服务器识别 CASTGC 后会签发 ycard JWT */
    private suspend fun loginWithCastgc(castgc: String) {
        // Step 1: GET ycard CAS 入口(带 CASTGC)
        val r1 = client.newCall(Request.Builder()
            .url(YCARD_CAS_ENTRY)
            .header("User-Agent", UA)
            .header("Cookie", "CASTGC=$castgc")
            .build()).execute().use { response ->
            if (response.code == 200) {
                // 没有重定向,可能 CASTGC 已被消费或服务器特殊处理
                throw Exception("CASTGC 未触发重定向")
            }
            response.header("Location") ?: throw Exception("ycard 未重定向")
        }

        // Step 2: 跟随重定向链(进入 ycard 主页,激活 session)
        val finalUrl = redirectClient.newCall(Request.Builder()
            .url(r1)
            .header("User-Agent", UA)
            .build()).execute().use { response ->
            response.request.url.toString()
        }

        // Step 3: 从 URL 中提取 JWT
        val jwt = Regex("""synjones-auth=([^&"]+)""").find(finalUrl)?.groupValues?.get(1)
            ?: throw Exception("未获取到 synjones-auth JWT (final URL: ${finalUrl.take(200)})")

        cachedJwt = jwt
        Log.e(TAG, "ycard 简易 SSO 成功，JWT=${jwt.take(40)}...")
    }

    // ══════════════════════════════════════════════════════
    // 策略 2: 完整 CAS 登录回退
    // ══════════════════════════════════════════════════════

    private suspend fun performFullLogin(username: String, password: String) {
        // Step 1: GET ycard CAS 入口 → 302 → CAS login URL
        val casUrl = client.newCall(Request.Builder().url(YCARD_CAS_ENTRY)
            .header("User-Agent", UA).build()).execute().use { response ->
            response.header("Location")
                ?: throw Exception("ycard 未重定向到 CAS")
        }
        Log.e(TAG, "CAS URL: ${casUrl.take(100)}")

        // Step 2: GET CAS login page → lt + execution
        val (lt, execution) = client.newCall(Request.Builder().url(casUrl)
            .header("User-Agent", UA).build()).execute().use { response ->
            val html = response.body?.string() ?: ""
            val lt = Regex("""name="lt"\s+value="([^"]+)"""").find(html)?.groupValues?.get(1)
                ?: throw Exception("未找到 lt")
            val execution = Regex("""name="execution"\s+value="([^"]+)"""").find(html)?.groupValues?.get(1)
                ?: throw Exception("未找到 execution")
            Log.e(TAG, "lt=$lt")
            Pair(lt, execution)
        }

        // Step 3: DES 加密
        val encrypted = DES.strEnc(username + password + lt, "1", "2", "3")

        // Step 4: device 预验证
        client.newCall(Request.Builder().url("$CAS_BASE/device")
            .header("User-Agent", UA)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .post(FormBody.Builder()
                .add("ul", username.length.toString())
                .add("pl", password.length.toString())
                .add("rsa", encrypted)
                .add("method", "login").build()).build()
        ).execute().use { response ->
            val deviceBody = response.body?.string() ?: ""
            Log.e(TAG, "device: $deviceBody")
        }

        // Step 5: 提交 CAS 表单(跟随重定向以获取 JWT)
        val finalUrl = redirectClient.newCall(Request.Builder().url(casUrl)
            .header("User-Agent", UA)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .post(FormBody.Builder()
                .add("rsa", encrypted)
                .add("ul", username.length.toString())
                .add("pl", password.length.toString())
                .add("lt", lt)
                .add("execution", execution)
                .add("_eventId", "submit").build()).build()
        ).execute().use { response ->
            response.request.url.toString()
        }
        Log.e(TAG, "最终 URL: ${finalUrl.take(150)}")

        // Step 6: 从 URL 中提取 JWT
        val jwt = Regex("""synjones-auth=([^&"]+)""").find(finalUrl)?.groupValues?.get(1)
            ?: throw Exception("未获取到 synjones-auth JWT")

        cachedJwt = jwt
        Log.e(TAG, "ycard 完整登录成功，JWT=${jwt.take(40)}...")
    }
}