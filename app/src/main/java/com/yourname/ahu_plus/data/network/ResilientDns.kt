package com.yourname.ahu_plus.data.network

import android.util.Log
import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * 抗 DNS 污染的自定义 OkHttp DNS 解析器 (2026-06-23)
 *
 * 背景:
 *   部分运营商/校园网把 gitee.com 解析到 198.18.0.0/15 段
 *   (IANA benchmark 保留地址) 或 0.0.0.0 等伪 IP,导致 HTTPS 连接失败。
 *   而真实 Gitee 的 IP (180.76.x.x 系列, Baidu CDN 出口做 302 跳转) 是
 *   可达的。
 *
 * 关键陷阱 (2026-06-23 v2 修复):
 *   Gitee raw URL `https://gitee.com/.../xxx.json` 实际是 302 跳转到
 *   `https://raw.giteeusercontent.com/...` (带签名参数)。OkHttp 跟随 302
 *   后会用本 ResilientDns 再次解析新域名。如果只给 gitee.com 配置回退 IP,
 *   重定向目标 raw.giteeusercontent.com 依然会卡在系统 DNS 污染上 → 拉取失败。
 *
 * 策略:
 *   1. 优先用系统 DNS 解析,过滤掉明显的污染 IP:
 *      - 198.18.0.0/15 (IANA benchmark)
 *      - 0.0.0.0
 *      - 127.0.0.0/8 (loopback)
 *      - 169.254.0.0/16 (link-local)
 *      - 224.0.0.0/4 (multicast)
 *   2. 若过滤后没有可用 IP,回退到硬编码的 Gitee CDN IP 列表 (Baidu 出口,
 *      会做 302 跳到真实 Gitee,实测可用)。
 *   3. 必须同时覆盖 gitee.com (入口) 和 raw.giteeusercontent.com (重定向目标),
 *      否则 302 后会再次失败。
 *
 * 注意:
 *   - OkHttp 仍会按 SNI 校验 TLS 证书 (CN=gitee.com),所以用 IP 直连是安全的。
 *   - 302 跳转后实际是真实 Gitee,流量也会走 HTTPS。
 *   - redirect 后的 raw.giteeusercontent.com 也使用 *.giteeusercontent.com 通配
 *     证书 (CN=giteeusercontent.com),IP 直连同样可用。
 */
object ResilientDns : Dns {

    private const val TAG = "ResilientDns"

    /**
     * 已知可达的 Gitee 系 CDN 入口 IP (2026-06-23 多源验证)。
     * 必须同时登记主域名和重定向后的 raw 域名,否则 302 后会再次失败。
     *
     * 多 IP 的原因: 用户网络可能屏蔽其中某些 IP(如校园网封锁 180.76.x.x),
     * OkHttp 会按列表顺序尝试,首个失败的会自动跳下一个。
     */
    private val FALLBACK_IPS: Map<String, List<String>> = mapOf(
        // 主入口: gitee.com 的 302 服务 IP (Baidu Yunjiasu CDN)
        "gitee.com" to listOf(
            "180.76.199.13",
            "180.76.198.77",
            "180.76.198.225",
        ),
        // 302 重定向目标: raw.giteeusercontent.com 实际服务 IP
        // 注: 公共 DNS (8.8.8.8/1.1.1.1/223.5.5.5/114.114.114.114) 都查不到这个域名的 A 记录
        // (Gitee 私有域名), 实际 IP 只能从运营商 DNS 或 curl 抓 handshake 得知
        "raw.giteeusercontent.com" to listOf(
            "180.76.199.86",  // 2026-06-23 curl 跟 redirect 实测握手 IP
            "180.76.198.225", // gitee.com 备用, 同机房
        ),
        // 通配兜底: giteeusercontent.com 主域
        "giteeusercontent.com" to listOf(
            "180.76.199.86",
        ),
        // 公共 DNS: 万一 180.76.x.x 全部被屏蔽, 可以尝试用 Google DNS IP 直连 DoH
        "dns.google" to listOf(
            "8.8.8.8",
            "8.8.4.4",
        ),
        "cloudflare-dns.com" to listOf(
            "1.1.1.1",
            "1.0.0.1",
        ),
    )

    /**
     * 子域兜底: 如果查 raw.xxx.giteeusercontent.com 没命中,
     * 退化到查 xxx.giteeusercontent.com,再到 giteeusercontent.com。
     */
    private fun lookupFallbackChain(hostname: String): List<InetAddress> {
        // 1) 精确命中
        FALLBACK_IPS[hostname]?.let { return resolve(it) }
        // 2) 去掉最左侧子域,逐级向上查找 (例如 a.b.giteeusercontent.com → b.giteeusercontent.com)
        var current = hostname
        while (true) {
            val dot = current.indexOf('.')
            if (dot < 0) break
            current = current.substring(dot + 1)
            if (current.isEmpty()) break
            FALLBACK_IPS[current]?.let { return resolve(it) }
        }
        Log.w(TAG, "$hostname 无任何回退 IP 配置")
        return emptyList()
    }

    private fun resolve(ips: List<String>): List<InetAddress> =
        ips.mapNotNull { addr ->
            runCatching { InetAddress.getByName(addr) }.getOrNull()
        }

    override fun lookup(hostname: String): List<InetAddress> {
        // 1) 优先系统 DNS
        val systemAddrs = try {
            InetAddress.getAllByName(hostname).toList()
        } catch (e: UnknownHostException) {
            Log.w(TAG, "system DNS lookup failed for $hostname: ${e.message}")
            emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "system DNS lookup error for $hostname: ${e.message}")
            emptyList()
        }

        val clean = systemAddrs.filter { !isPoisoned(it) }
        if (clean.isNotEmpty()) {
            Log.d(TAG, "$hostname → ${clean.size} clean IPs from system: ${clean.joinToString { it.hostAddress ?: "?" }}")
            return clean
        }

        // 2) 系统 DNS 全部被污染 / 失败 → 回退硬编码
        if (systemAddrs.isNotEmpty()) {
            Log.w(
                TAG, "$hostname system DNS returned only poisoned IPs: " +
                    systemAddrs.joinToString { it.hostAddress ?: "?" } + ", using fallback"
            )
        } else {
            Log.w(TAG, "$hostname system DNS returned empty, using fallback")
        }
        return lookupFallbackChain(hostname)
    }

    /**
     * 判断是否为 DNS 污染地址。常见模式:
     *   - 198.18.0.0/15  IANA benchmark (RFC 2544 / 6890)
     *   - 0.0.0.0        通配
     *   - 127.0.0.0/8    loopback
     *   - 169.254.0.0/16 link-local
     *   - 224.0.0.0/4    multicast
     */
    private fun isPoisoned(addr: InetAddress): Boolean {
        val raw = addr.hostAddress ?: return true
        // IPv4 only — IPv6 污染很少见,放行
        if (raw.contains(':')) return false
        val parts = raw.split('.')
        if (parts.size != 4) return true
        val o1 = parts[0].toIntOrNull() ?: return true
        val o2 = parts[1].toIntOrNull() ?: return true
        return when {
            o1 == 198 && o2 in 18..19 -> true         // 198.18.0.0/15
            o1 == 0 -> true                            // 0.0.0.0
            o1 == 127 -> true                          // loopback
            o1 == 169 && o2 == 254 -> true             // link-local
            o1 in 224..239 -> true                     // multicast
            else -> false
        }
    }
}