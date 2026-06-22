# ============================================================
# Ahu_Plus ProGuard Rules
# ============================================================

# ---------- Gson ----------
# Keep all model classes with @SerializedName (used by Gson reflection)
-keepattributes Signature
-keepattributes *Annotation*

# Keep all data model classes used for Gson serialization/deserialization
-keep class com.yourname.ahu_plus.data.model.** { *; }
-keep class com.yourname.ahu_plus.data.model.jw.** { *; }
-keep class com.yourname.ahu_plus.data.model.course.** { *; }
-keep class com.yourname.ahu_plus.data.model.task.** { *; }

# Gson specific
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class com.google.gson.stream.** { *; }

# ---------- OkHttp ----------
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ---------- Jsoup ----------
-keep class org.jsoup.** { *; }

# ---------- Coil SVG ----------
-keep class coil.decode.** { *; }

# ---------- ZXing ----------
-keep class com.google.zxing.** { *; }

# ---------- AndroidX Glance ----------
-keep class androidx.glance.** { *; }

# ---------- AndroidX Work (WorkManager, used by Glance) ----------
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class androidx.work.impl.** { *; }

# ---------- AndroidX Room (used by WorkManager) ----------
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.Database
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-dontwarn androidx.room.paging.**

# ---------- AndroidX Startup ----------
-keep class androidx.startup.** { *; }
-keep class * implements androidx.startup.Initializer

# ---------- Security Crypto ----------
-keep class androidx.security.crypto.** { *; }

# ---------- Kotlin Coroutines ----------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ---------- Kotlin Serialization (if ever added) ----------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# ============================================================
# 2026-06-22: 防逆向加固（内测阶段，隐藏测试功能）
# ============================================================

# 激进混淆：合并包路径，所有未 keep 的类归入单一 internal 包
-repackageclasses 'com.yourname.ahu_plus.internal'

# 允许 R8 修改访问修饰符（public → private），利于内联/优化
-allowaccessmodification

# 方法名复用：不同签名的方法可使用相同混淆名
-overloadaggressively

# 合并所有可能的接口（减少类数量）
-mergeinterfacesaggressively

# ---------- 移除日志（release 不输出 debug 日志） ----------
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
}

# ---------- 不保留局部变量名（减少调试信息泄露） ----------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------- 云端备份：不额外 keep，让其自然混淆 ----------
# CloudBackupManager / CloudStorageRepository 的类名和方法名
# 会被 R8 重命名为 a.b.c / a() 等，无需显式规则。
# 仅保留 COS 签名相关的 OkHttp 框架类（已在上面 OkHttp 段处理）。
