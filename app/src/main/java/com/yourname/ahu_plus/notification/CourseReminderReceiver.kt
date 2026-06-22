package com.yourname.ahu_plus.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.yourname.ahu_plus.MainActivity
import com.yourname.ahu_plus.data.debug.DebugClock
import com.yourname.ahu_plus.data.local.AppDataStore
import com.yourname.ahu_plus.data.local.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * 课程提醒通知广播接收器。
 *
 * 由 [CourseReminderScheduler] 在每节课前 [REMINDER_LEAD_MINUTES] 分钟触发:
 *  1. 从 DataStore 读取课表缓存
 *  2. 找到匹配"今日 + 当前周 + 节次"的课程
 *  3. 发送系统通知,deep-link 到 MainActivity
 *
 * 失败兜底:
 *  - 课表缓存为空 → 静默退出(不打扰用户)
 *  - 当前时刻已无下一节课 → 静默退出
 */
class CourseReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_COURSE_REMINDER) {
            return
        }
        val lessonKey = intent.getStringExtra(EXTRA_LESSON_KEY)
        Log.i(TAG, "onReceive: 课程提醒 lessonKey=$lessonKey")

        val pendingResult = goAsync()
        GlobalScope.launch(Dispatchers.IO) {
            try {
                handleReminder(context.applicationContext, lessonKey)
            } catch (e: Exception) {
                Log.e(TAG, "处理提醒异常: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleReminder(appContext: Context, lessonKey: String?) {
        ensureChannel(appContext)

        val sessionManager = SessionManager(AppDataStore(appContext))
        sessionManager.init()

        val lesson = CourseReminderScheduler.findLessonByKey(
            sessionManager = sessionManager,
            key = lessonKey ?: return,
        ) ?: run {
            Log.w(TAG, "handleReminder: lessonKey=$lessonKey 未找到匹配课程,跳过")
            return
        }

        // 距离开课 ≤ 0 → 用户可能刚刚打开 App 跳过了提醒,不再打扰
        val now = DebugClock.nowTime()
        if (lesson.startMinutes != null && now.toMinutesOfDay() >= lesson.startMinutes) {
            Log.i(TAG, "handleReminder: 已开课或正在上课,跳过")
            return
        }

        val title = "即将上课:${lesson.courseName}"
        val body = buildString {
            append(lesson.timeText)
            if (lesson.room.isNotBlank()) {
                append(" · ")
                append(lesson.room)
            }
            append(" · $REMINDER_LEAD_MINUTES 分钟后开课")
        }

        val deepLink = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_DEEP_LINK, MainActivity.DEEP_LINK_SCHEDULE)
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            lessonKey.hashCode(),
            deepLink,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // 使用 lessonKey 的 hashCode 作为通知 id,确保同一节课的提醒覆盖更新
        nm.notify(lessonKey.hashCode(), notification)
        Log.i(TAG, "通知已发送: $title")
    }

    /**
     * 确保通知 channel 已创建。Android 8.0+ 必须有 channel 才能发通知。
     */
    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "课程提醒",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "在每节课前提醒上课时间和教室"
                    enableLights(true)
                    enableVibration(true)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun java.time.LocalTime.toMinutesOfDay(): Int = hour * 60 + minute

    companion object {
        private const val TAG = "CourseReminder"

        const val ACTION_COURSE_REMINDER = "com.yourname.ahu_plus.notification.COURSE_REMINDER"
        const val EXTRA_LESSON_KEY = "lesson_key"

        const val CHANNEL_ID = "course_reminder"

        /** 提前多少分钟提醒(可在未来做成可配置项) */
        const val REMINDER_LEAD_MINUTES = 15
    }
}