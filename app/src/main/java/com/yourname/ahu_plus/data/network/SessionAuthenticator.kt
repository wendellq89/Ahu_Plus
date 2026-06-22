package com.yourname.ahu_plus.data.network

import android.util.Log
import com.yourname.ahu_plus.data.repository.CasAuthRepository
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * JSESSIONID 自动续期拦截器 + OkHttp Authenticator。
 *
 * 目标:Ahu_Plus 用户每次启动会话有效期约 1 周,失效后必须手动重新登录。
 * 本组件让所有 *.ahu.edu.cn / ycard / jw 业务请求在以下情况自动恢复会话:
 *
 *  1. **响应体嗅探**(Interceptor 层):即使服务器返回 HTTP 200 但 body 是 CAS 登录表单
 *     (`name="lt"`),说明 JSESSIONID 已过期被强制跳回登录页 —— 触发 [refreshSession]
 *  2. **HTTP 401/403**(Authenticator 层):OkHttp 在认证头缺失/失效时回调
 *
 * 重试控制:
 *  - 单次请求最多触发 [MAX_REFRESH_PER_REQUEST] 次刷新,避免凭据错误导致无限循环
 *  - 用 [InFlightRefresh] 互斥锁,防止 N 个并发请求同时触发刷新(只刷一次)
 *  - 重试通过 [Response.priorResponse] 自动堆叠,OkHttp 检测到新凭据后会重发原请求
 *
 * 用法:
 *   val authenticator = SessionAuthenticator(casAuthRepository)
 *   val client = SecureHttpClientFactory.create(
 *       cookieJar = cookieJar,
 *       authenticator = authenticator,
 *       sessionExpiredInterceptor = authenticator.asInterceptor(),
 *   )
 */
class SessionAuthenticator(
    private val casAuthRepository: CasAuthRepository,
    private val maxRefreshPerRequest: Int = MAX_REFRESH_PER_REQUEST,
) : Authenticator {

    companion object {
        private const val TAG = "SessionAuth"

        /** 单请求最大重试次数(防止凭据错误导致无限循环) */
        const val MAX_REFRESH_PER_REQUEST = 2

        /** CAS 登录表单特征:HTML 含 name="lt" 隐藏字段 */
        private val CAS_LOGIN_MARKER = Regex("""name="lt"\s+value="[^"]+"""")

        /** 业务系统常见 session 过期指示 */
        private val SESSION_EXPIRED_INDICATORS = listOf(
            "session timeout",
            "会话已过期",
            "请重新登录",
        )

        /** 嗅探 body 前 4KB 足以识别 CAS 表单 */
        private const val PEEK_BYTES = 4L * 1024
    }

    /**
     * 标记当前请求已触发过刷新的次数,避免重试链失控。
     * 用 [okhttp3.Response.priorResponse] 深度计算。
     */
    private fun retryCount(response: Response?): Int {
        var count = 0
        var cur = response
        while (cur != null) {
            count++
            cur = cur.priorResponse
        }
        return count
    }

    /**
     * Authenticator 入口:OkHttp 在收到 401/403 时调用。
     *
     * 注意:OkHttp 只对带 Authorization 头(本项目无)或 401/407 响应调用此方法。
     * 对于 HTML 表单型会话过期(无 Auth 头),需要靠 [asInterceptor] 嗅探。
     */
    override fun authenticate(route: Route?, response: Response): Request? {
        if (retryCount(response) > maxRefreshPerRequest) {
            Log.w(TAG, "authenticate: 已达最大重试次数 ${maxRefreshPerRequest}，放弃")
            return null
        }

        Log.i(TAG, "authenticate: 收到 ${response.code}，尝试刷新 JSESSIONID")
        val refreshed = refreshSession()
        if (!refreshed) {
            Log.w(TAG, "authenticate: 刷新失败，不再重试")
            return null
        }

        // 返回重发请求(OkHttp 自动沿用 cookieJar 最新 Cookie)
        return response.request.newBuilder().build()
    }

    /**
     * 包装为 Interceptor:在响应后嗅探 HTML body,识别"静默过期"。
     *
     * 这是 Ahu_Plus 真实场景下的主要触发点 —— 安大门户会返回 HTTP 200 + 登录表单 HTML,
     * 业务 Repository 拿到这个 body 后解析失败,但 OkHttp 不会主动重试。
     */
    fun asInterceptor(): Interceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)

        // 仅对 ahud 域名嗅探,避免误伤其他域名
        val host = request.url.host
        if (!host.endsWith(".ahu.edu.cn") && host != "ycard.ahu.edu.cn") {
            return@Interceptor response
        }

        // 已经是 401/403 让 Authenticator 处理
        if (response.code == 401 || response.code == 403) {
            return@Interceptor response
        }

        // 浅读 body 前 4KB 用于嗅探(完整 body 后续仍可读)
        val peekSource = response.peekBody(PEEK_BYTES).string()
        val isExpired = CAS_LOGIN_MARKER.containsMatchIn(peekSource) ||
            SESSION_EXPIRED_INDICATORS.any { it in peekSource }

        if (!isExpired) {
            return@Interceptor response
        }

        // 计算累计重试次数(包括 OkHttp Authenticator 已触发的)
        if (retryCount(response) >= maxRefreshPerRequest) {
            Log.w(TAG, "asInterceptor: 已达最大重试次数 ${maxRefreshPerRequest}，不再刷新")
            return@Interceptor response
        }

        Log.w(TAG, "asInterceptor: 响应疑似 session 过期 (host=$host, code=${response.code})，刷新并重发")
        val refreshed = refreshSession()
        if (!refreshed) {
            Log.w(TAG, "asInterceptor: 刷新失败，返回原始响应")
            return@Interceptor response
        }

        response.close()
        chain.proceed(request.newBuilder().build())
    }

    /**
     * 真正触发会话刷新的入口。
     * 用 [InFlightRefresh] 互斥锁,保证并发安全。
     */
    private val inFlight = InFlightRefresh()

    private fun refreshSession(): Boolean {
        if (!inFlight.tryAcquire()) {
            // 已有其他线程在刷新,等待其完成
            return inFlight.awaitResult()
        }
        try {
            val result = runBlocking { casAuthRepository.ensureValidSession() }
            return result.isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "refreshSession 异常: ${e.message}", e)
            return false
        } finally {
            inFlight.release(result = true)
        }
    }

    /** 简单互斥锁 + 结果传递,避免 N 个并发请求触发 N 次登录 */
    private class InFlightRefresh {
        private val lock = Object()
        private var completed = false
        @Volatile private var lastResult: Boolean = false

        fun tryAcquire(): Boolean = synchronized(lock) {
            if (completed) {
                // 已有结果,直接复用
                false
            } else {
                true
            }
        }

        fun awaitResult(): Boolean = synchronized(lock) {
            while (!completed) {
                (lock as Object).wait()
            }
            lastResult
        }

        fun release(result: Boolean) {
            synchronized(lock) {
                completed = true
                lastResult = result
                (lock as Object).notifyAll()
            }
        }
    }
}

/** 异常标识:OkHttp Interceptor 内嗅探到 CAS 登录表单 */
class SessionExpiredException(message: String) : Exception(message)