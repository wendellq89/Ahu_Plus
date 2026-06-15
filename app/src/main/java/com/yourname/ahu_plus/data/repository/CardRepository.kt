package com.yourname.ahu_plus.data.repository

import android.util.Log
import com.google.gson.JsonParser
import com.yourname.ahu_plus.data.GsonProvider
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.network.SecureHttpClientFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class CardRepository(
    private val sessionManager: SessionManager,
    private val baseUrl: String = "https://adwmh.ahu.edu.cn",
    private val portalJsessionIdProvider: () -> String? = { null }
) {
    private val gson = GsonProvider.instance

    // adwmh 客户端(明文 HTTP,不需 trust-all)
    private val client: OkHttpClient = SecureHttpClientFactory.create(
        followRedirects = true,
        disableGzip = false  // 启用 gzip
    )

    // one.ahu.edu.cn 客户端(自签名证书)
    private val portalClient: OkHttpClient = SecureHttpClientFactory.create(
        followRedirects = true,
        disableGzip = false,
        extraInterceptors = listOf(
            okhttp3.Interceptor { chain ->
                val req = chain.request()
                Log.e("CARD_API", ">>> ${req.method} ${req.url}  Cookie=${req.header("Cookie")}")
                chain.proceed(req)
            }
        )
    )

    // ── 通用请求构建(adwmh)──────────────────────────
    private fun buildRequest(path: String): Request {
        val sessionId = sessionManager.getSessionId()
        return Request.Builder()
            .url("$baseUrl$path")
            .header("Cookie", "JSESSIONID=$sessionId")
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/132.0.0.0 Safari/537.36 NetType/WIFI MicroMessenger/7.0.20.1781(0x6700143B) " +
                    "WindowsWechat(0x63090a13) UnifiedPCWindowsWechat(0xf2541a35) XWEB/19977 Flue"
            )
            .header("Referer", "https://adwmh.ahu.edu.cn/www/index.html")
            .header("x-requested-with", "XMLHttpRequest")
            .build()
    }

    // ── adwmh API ──────────────────────────────────────

    suspend fun getBalance(): Result<Double> {
        return try {
            client.newCall(buildRequest("/xzxcard/yue")).execute().use { response ->
                val body = response.body?.string() ?: ""
                Log.d("CARD_API", "Balance raw body: $body")
                val json = JsonParser.parseString(body).asJsonObject
                val code = json.get("code").asInt
                if (code == 10000) {
                    Result.success(json.get("object").asDouble)
                } else {
                    val msg = json.get("msg")?.asString ?: "未知错误"
                    Result.failure(Exception(msg))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getQrCode(): Result<String> {
        return try {
            client.newCall(buildRequest("/xzxcard/qrcode")).execute().use { response ->
                val body = response.body?.string() ?: ""
                Log.d("CARD_API", "QR raw body: $body")
                val json = JsonParser.parseString(body).asJsonObject
                val code = json.get("code").asInt
                if (code == 10000) {
                    Result.success(json.get("object").asString)
                } else {
                    val msg = json.get("msg")?.asString ?: "未知错误"
                    Result.failure(Exception(msg))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── 门户余额 API (one.ahu.edu.cn) ──────────────────

    data class PortalBalance(
        val balance: Double,
        val timestamp: Long
    )

    /**
     * 获取门户一卡通余额。
     *
     * 可能抛出的异常:
     * - [PortalHtmlResponseException] 接口返回了门户 HTML 页面,而不是余额 JSON
     * - [SessionExpiredException] JSESSIONID 已失效,需要重新登录
     * - 其他 Exception 网络错误 / JSON 解析错误
     */
    suspend fun getPortalBalance(): Result<PortalBalance> {
        return try {
            val jsessionid = portalJsessionIdProvider()
                ?: return Result.failure(Exception("未登录"))

            val jsonBody = "{}".toRequestBody("application/json; charset=UTF-8".toMediaType())
            val request = Request.Builder()
                .url("https://one.ahu.edu.cn/tp_up/up/subgroup/getCardMoney")
                .header("Cookie", "JSESSIONID=$jsessionid")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36" +
                        " (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36"
                )
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("Referer", "https://one.ahu.edu.cn/tp_up/view?m=up")
                .header("Origin", "https://one.ahu.edu.cn")
                .post(jsonBody)
                .build()

            portalClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                val code = response.code
                Log.e("CARD_API", "HTTP $code, body[:300]=${body.take(300)}")

                if (code != 200) {
                    return Result.failure(Exception("服务器错误 HTTP $code"))
                }

                // 检测 session 失效:被踢回登录页
                if (body.contains("cas/login") || body.contains("name=\"lt\"")) {
                    return Result.failure(SessionExpiredException())
                }

                // 余额接口实测非校园网也可访问;若返回 HTML,更可能是门户页/拦截页而非余额 JSON。
                if (body.contains("__vpn_") || body.trimStart().startsWith("<html")) {
                    return Result.failure(PortalHtmlResponseException())
                }

                // 使用 lenient 模式解析
                val json = try {
                    gson.fromJson(body, com.google.gson.JsonObject::class.java)
                } catch (parseErr: Exception) {
                    return Result.failure(Exception("会话已过期，请重新登录"))
                }

                val balance = json.get("KHYE")?.asDouble
                    ?: return Result.failure(Exception("缺少KHYE字段: ${body.take(200)}"))
                val timestamp = json.get("SJTBSJ")?.asLong ?: 0L
                Result.success(PortalBalance(balance, timestamp))
            }
        } catch (e: Exception) {
            Log.e("CARD_API", "Portal balance error", e)
            Result.failure(e)
        }
    }
}

/** 余额接口返回门户 HTML 页面,而不是预期的 JSON。 */
class PortalHtmlResponseException : Exception("余额接口返回了门户页面，请重新登录后再试")

/** JSESSIONID 已失效,需要重新登录 */
class SessionExpiredException : Exception("会话已过期")
