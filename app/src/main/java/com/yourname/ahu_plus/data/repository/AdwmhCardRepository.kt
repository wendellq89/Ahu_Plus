package com.yourname.ahu_plus.data.repository

import com.google.gson.JsonObject
import com.yourname.ahu_plus.data.GsonProvider
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.network.SecureHttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap

class AdwmhCardRepository(
    private val sessionManager: SessionManager
) {
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

    private val client = SecureHttpClientFactory.create(cookieJar = cookieJar)

    fun getAuthStartUrl(): String = "https://$HOST/index/wxindex?id=2022"

    suspend fun importSessionId(sessionId: String) {
        val normalized = sessionId.trim()
        if (normalized.isBlank()) return
        sessionManager.saveAdwmhSessionId(normalized)
        setSessionCookie(normalized)
    }

    fun clearCookies() {
        cookieStore.clear()
    }

    suspend fun getQrCode(): Result<AdwmhQrCode> = withContext(Dispatchers.IO) {
        runCatching {
            ensureSessionId()
            val json = getJson("/xzxcard/qrcode")
            val payload = json.get("object")?.asString?.takeIf { it.isNotBlank() }
                ?: throw AdwmhAuthException("QR payload is empty")
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

    private fun ensureSessionId(): String {
        val sessionId = sessionManager.getAdwmhSessionId()?.takeIf { it.isNotBlank() }
            ?: throw AdwmhAuthException("Please authorize Smart AHU first")
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
        val requestBuilder = Request.Builder()
            .url("https://$HOST$path")
            .header("User-Agent", WECHAT_UA)
            .header("Accept", "application/json, text/plain, */*")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", "https://$HOST/www/index.html")

        if (method == "POST") {
            requestBuilder
                .header("Origin", "https://$HOST")
                .post(ByteArray(0).toRequestBody(null))
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.code == 401 || response.code == 403 || response.code in 300..399) {
                throw AdwmhAuthException("Smart AHU session expired")
            }
            if (!response.isSuccessful) {
                throw AdwmhAuthException("Smart AHU HTTP ${response.code}")
            }
            if (body.trimStart().startsWith("<")) {
                throw AdwmhAuthException("Smart AHU returned HTML; authorize again")
            }
            val json = gson.fromJson(body, JsonObject::class.java)
                ?: throw AdwmhAuthException("Smart AHU response is empty")
            val code = json.get("code")?.asInt
            if (code != 10000) {
                throw AdwmhAuthException(json.get("msg")?.asString ?: "Smart AHU request failed")
            }
            return json
        }
    }

    private companion object {
        const val HOST = "adwmh.ahu.edu.cn"
        const val SESSION_COOKIE = "JSESSIONID"
        const val WECHAT_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36 " +
                "NetType/WIFI MicroMessenger/7.0.20.1781 WindowsWechat Flue"
    }
}

data class AdwmhQrCode(
    val payload: String,
    val serverTimeText: String,
    val fetchedAt: Long
)

class AdwmhAuthException(message: String) : Exception(message)
