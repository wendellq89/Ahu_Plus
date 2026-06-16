package com.yourname.ahu_plus.data.repository

import android.util.Log
import com.google.gson.JsonObject
import com.yourname.ahu_plus.data.GsonProvider
import com.yourname.ahu_plus.data.network.SecureHttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class CardRepository(
    private val portalJsessionIdProvider: () -> String? = { null }
) {
    private val gson = GsonProvider.instance

    private val portalClient: OkHttpClient = SecureHttpClientFactory.create(
        followRedirects = true,
        disableGzip = false,
        extraInterceptors = listOf(
            okhttp3.Interceptor { chain ->
                val req = chain.request()
                Log.e(TAG, ">>> ${req.method} ${req.url} Cookie=${req.header("Cookie")}")
                chain.proceed(req)
            }
        )
    )

    data class PortalBalance(
        val balance: Double,
        val timestamp: Long
    )

    suspend fun getPortalBalance(): Result<PortalBalance> = withContext(Dispatchers.IO) {
        try {
            val jsessionid = portalJsessionIdProvider()
                ?: return@withContext Result.failure(Exception("not logged in"))

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
                val body = response.body?.string().orEmpty()
                val code = response.code
                Log.e(TAG, "HTTP $code, body[:300]=${body.take(300)}")

                if (code != 200) {
                    return@withContext Result.failure(Exception("server error HTTP $code"))
                }

                val isHtml = body.trimStart().startsWith("<html", ignoreCase = true) ||
                    body.trimStart().startsWith("<!DOCTYPE", ignoreCase = true) ||
                    body.contains("__vpn_")

                if (isHtml) {
                    if (body.contains("name=\"lt\"")) {
                        return@withContext Result.failure(SessionExpiredException())
                    }
                    return@withContext Result.failure(PortalHtmlResponseException())
                }

                val json = try {
                    gson.fromJson(body, JsonObject::class.java)
                } catch (_: Exception) {
                    return@withContext Result.failure(Exception("failed to parse balance data"))
                }

                val balance = json.get("KHYE")?.asDouble
                    ?: return@withContext Result.failure(Exception("missing KHYE field: ${body.take(200)}"))
                val timestamp = json.get("SJTBSJ")?.asLong ?: 0L
                Result.success(PortalBalance(balance, timestamp))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Portal balance error", e)
            Result.failure(e)
        }
    }

    private companion object {
        const val TAG = "CARD_API"
    }
}

class PortalHtmlResponseException : Exception("balance API returned an HTML page; please log in again")

class SessionExpiredException : Exception("session expired")
