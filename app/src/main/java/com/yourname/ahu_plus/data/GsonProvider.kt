package com.yourname.ahu_plus.data

import com.google.gson.GsonBuilder
import com.google.gson.Strictness

/**
 * 全局共享的 Lenient Gson 实例。
 *
 * 所有需要 Gson 反序列化的 Repository 统一引用此单例，
 * 避免各文件各自 `GsonBuilder().setStrictness(LENIENT).create()`。
 */
object GsonProvider {
    val instance by lazy {
        GsonBuilder()
            .setStrictness(Strictness.LENIENT)
            .create()
    }
}
