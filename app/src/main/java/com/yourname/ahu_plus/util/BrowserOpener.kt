package com.yourname.ahu_plus.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

/**
 * 用系统浏览器/已注册的应用打开 URL。
 *
 * 找不到可处理的 Activity 时返回 false,由调用方决定是否提示用户。
 */
object BrowserOpener {
    fun open(context: Context, url: String): Boolean {
        if (url.isBlank()) return false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    fun openInWeChat(context: Context, url: String): Boolean {
        if (url.isBlank()) return false
        val encodedUrl = URLEncoder.encode(url, "UTF-8")
        val weChatWebViewUrl = "weixin://dl/businessWebview/link/?appid=wxb0cc6c5fccbaf1d0&url=$encodedUrl"
        val weChatSchemeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(weChatWebViewUrl)).apply {
            setPackage("com.tencent.mm")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (runCatching { context.startActivity(weChatSchemeIntent) }.isSuccess) return true

        val publicSchemeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(weChatWebViewUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (runCatching { context.startActivity(publicSchemeIntent) }.isSuccess) return true

        return open(context, url)
    }

    fun shareTextToWeChat(context: Context, text: String): Boolean {
        if (text.isBlank()) return false
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage("com.tencent.mm")
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
