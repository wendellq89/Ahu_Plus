package com.yourname.ahu_plus.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.yourname.ahu_plus.ui.widget.TodayScheduleWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Widget / 课程提醒统一调度器。
 *
 * 借鉴 AHUTong-master 的 WidgetUpdateScheduler.scheduleNext() 自递归模式:
 *  - 每天 01:00 (数据校准)
 *  - 07:30 ~ 22:00 每 30 分钟 (课表时段,保证 widget 时效性)
 *  - 失败 / 取消 / 开机后由 BootReceiver 重新拉起
 *
 * 触发时:
 *  1. 更新桌面小部件 [TodayScheduleWidget]
 *  2. 调用 [CourseReminderScheduler.scheduleAll] 重排未来 24h 的课程提醒
 *
 * 关键决策:
 *  - **API 31+ AlarmManager 精确闹钟权限**:`canScheduleExactAlarms()` 返回 false 时
 *    自动降级 `setAndAllowWhileIdle`,不闪退(部分国产 ROM 该权限默认拒绝)
 *  - **PendingIntent.FLAG_IMMUTABLE**:Android 12+ 强制要求,否则启动期崩溃
 *  - **自递归**:`scheduleNext(context)` 在 onReceive 末尾再次调度,无需外部 Service
 */
class WidgetUpdateScheduler : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_UPDATE_WIDGETS) {
            return
        }
        Log.i(TAG, "onReceive: ACTION_UPDATE_WIDGETS，触发刷新")

        // 1. 更新桌面小部件(协程内异步执行,Glance.updateAll 是 suspend)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                TodayScheduleWidgetUpdater.updateAll(context)
            } catch (e: Exception) {
                Log.e(TAG, "Widget 更新失败: ${e.message}", e)
            } finally {
                try {
                    // 2. 重排未来 24h 课程提醒(可能在 widget 更新后才看到最新缓存)
                    CourseReminderScheduler.scheduleAll(context.applicationContext)
                } catch (e: Exception) {
                    Log.e(TAG, "课程提醒重排失败: ${e.message}", e)
                }
                pendingResult.finish()
            }
        }

        // 3. 自递归排下一个触发
        scheduleNext(context)
    }

    companion object {
        private const val TAG = "WidgetUpdateScheduler"

        const val ACTION_UPDATE_WIDGETS = "com.yourname.ahu_plus.widget.ACTION_UPDATE_WIDGETS"

        /** AlarmManager.PendingIntent requestCode(用于覆盖更新) */
        private const val REQUEST_CODE = 3001

        /**
         * 计算下一次触发时间,采用 AHUTong-master 验证的策略:
         *  - 01:00 (数据校准)
         *  - 07:30 ~ 22:00 每 30 分钟 (课表时段)
         *  - 找不到今日候选 → 明天的 01:00
         */
        fun calculateNextTrigger(now: LocalDateTime): LocalDateTime {
            val today = now.toLocalDate()
            val tomorrow = today.plusDays(1)
            val candidates = mutableListOf<LocalDateTime>()

            // 今日 01:00
            candidates.add(today.atTime(1, 0))

            // 今日 07:30~22:00 每 30 分钟
            var t = today.atTime(7, 30)
            val endTime = today.atTime(22, 0)
            while (!t.isAfter(endTime)) {
                candidates.add(t)
                t = t.plusMinutes(30)
            }

            // 兜底:明日 01:00 (用于 now 是 23:xx 的情况)
            candidates.add(tomorrow.atTime(1, 0))

            // 备用:明日 07:30~22:00 (now 是 22:00~23:59 的情况)
            var tNext = tomorrow.atTime(7, 30)
            val endTimeNext = tomorrow.atTime(22, 0)
            while (!tNext.isAfter(endTimeNext)) {
                candidates.add(tNext)
                tNext = tNext.plusMinutes(30)
            }

            return candidates.firstOrNull { it.isAfter(now) }
                ?: tomorrow.atTime(1, 0)
        }

        /**
         * 调度下一次触发。
         *
         * 调用时机:
         *  - App.onCreate 末尾(启动期首次排程)
         *  - onReceive 末尾(自递归)
         *  - BootReceiver.onReceive(开机后重排)
         */
        fun scheduleNext(context: Context) {
            val now = LocalDateTime.now()
            val nextTrigger = calculateNextTrigger(now)
            val triggerMillis = nextTrigger.atZone(ZoneId.systemDefault())
                .toInstant().toEpochMilli()
            val delayMillis = (triggerMillis - System.currentTimeMillis()).coerceAtLeast(0L)

            Log.i(TAG, "scheduleNext: 下一触发 = $nextTrigger (delay = ${delayMillis / 1000}s)")

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                Intent(context, WidgetUpdateScheduler::class.java).apply {
                    action = ACTION_UPDATE_WIDGETS
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: return

            // API 31+ 默认拒绝 SCHEDULE_EXACT_ALARMS,降级到 inexact API
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !alarmManager.canScheduleExactAlarms()
            ) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent,
                )
                Log.w(TAG, "scheduleNext: 无精确闹钟权限,使用 setAndAllowWhileIdle")
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent,
                )
            }
        }

        /** 取消所有调度(widget 卸载或 App 退出时调用) */
        fun cancel(context: Context) {
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                Intent(context, WidgetUpdateScheduler::class.java).apply {
                    action = ACTION_UPDATE_WIDGETS
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.cancel(pendingIntent)
            Log.i(TAG, "cancel: 已取消所有调度")
        }
    }
}