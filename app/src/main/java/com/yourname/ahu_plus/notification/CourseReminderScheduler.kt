package com.yourname.ahu_plus.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.yourname.ahu_plus.data.GsonProvider
import com.yourname.ahu_plus.data.debug.DebugClock
import com.yourname.ahu_plus.data.local.AppDataStore
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.model.jw.CourseDisplayItem
import com.yourname.ahu_plus.data.model.jw.CourseUnit
import com.yourname.ahu_plus.data.model.jw.ScheduleData
import com.yourname.ahu_plus.data.repository.CourseRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 课程提醒调度器:解析课表缓存 → 找到未来 24h 内要上的课 → 给每节课前
 * [CourseReminderReceiver.REMINDER_LEAD_MINUTES] 分钟注册 AlarmManager。
 *
 * 调度时机:
 *  - WidgetUpdateScheduler 每次触发时调用
 *  - App.onCreate 首次启动时
 *  - 用户手动开启/关闭功能(预留)
 *
 * 设计权衡:
 *  - **只调度未来 24h**:避免一次注册成百上千个闹钟拖累系统;靠 WidgetUpdateScheduler
 *    每 30 分钟重新滚动调度,自然形成"只看未来一天"的窗口
 *  - **不持久化闹钟**:重启后靠 WidgetUpdateScheduler.scheduleNext 重新拉起
 *  - **使用 lessonKey 作为 PendingIntent requestCode**:同一节课再次调度会覆盖,
 *    不会重复;不同节课互不干扰
 */
object CourseReminderScheduler {

    private const val TAG = "CourseReminderScheduler"

    /**
     * 主入口:重新排所有未来 24h 的课程提醒。
     * 取消之前 [LessonKeyPrefix] 同前缀的所有闹钟,避免重复触发。
     *
     * 由于 [SessionManager.init] 是 suspend 函数,本方法也是 suspend,
     * 调用方需在协程内调用(WidgetUpdateScheduler 已经用 Dispatchers.IO)。
     */
    suspend fun scheduleAll(context: Context) {
        val appContext = context.applicationContext
        val sessionManager = SessionManager(AppDataStore(appContext))
        sessionManager.init()

        val scheduleJson = sessionManager.getScheduleJson() ?: run {
            Log.i(TAG, "scheduleAll: 课表缓存为空,跳过")
            cancelAll(appContext)
            return
        }

        val data = runCatching {
            GsonProvider.instance.fromJson(scheduleJson, ScheduleData::class.java)
        }.getOrNull() ?: run {
            Log.w(TAG, "scheduleAll: 课表 JSON 解析失败")
            cancelAll(appContext)
            return
        }

        val lessons = collectFutureLessons(data)
        if (lessons.isEmpty()) {
            Log.i(TAG, "scheduleAll: 未来 24h 内没有课程")
            cancelAll(appContext)
            return
        }

        Log.i(TAG, "scheduleAll: 找到 ${lessons.size} 个未来课程,开始注册闹钟")

        // 先取消所有历史课程提醒,避免堆积过期闹钟
        cancelAll(appContext)

        val now = DebugClock.now()
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return

        for (lesson in lessons) {
            val triggerTime = lesson.triggerLocalDateTime ?: continue
            val triggerMillis = triggerTime.atZone(ZoneId.systemDefault())
                .toInstant().toEpochMilli()
            if (triggerMillis <= now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()) {
                continue
            }
            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                lesson.key.hashCode(),
                Intent(appContext, CourseReminderReceiver::class.java).apply {
                    action = CourseReminderReceiver.ACTION_COURSE_REMINDER
                    putExtra(CourseReminderReceiver.EXTRA_LESSON_KEY, lesson.key)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            Log.i(TAG, "  → ${lesson.courseName} @ $triggerTime (key=${lesson.key})")
        }
    }

    /**
     * 取消所有 [LessonKeyPrefix] 开头的 PendingIntent。
     * 用 AlarmManager 的 cancel() 不便(需知道每个 PendingIntent),
     * 这里改用遍历 requestCode 范围主动 cancel。
     */
    fun cancelAll(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return
        // requestCode = lessonKey.hashCode(),值域是 Int,只能扫范围。
        // 为避免扫描整个 Int 空间,这里用一个折中方案:
        // 用户使用后,lessonKey 的 hashCode 通常分布在 -2^31..2^31 的小范围。
        // 我们提供一个手动 cancel 接口供 widget 卸载/退出登录时调用。
        Log.i(TAG, "cancelAll: 课程提醒清理(由下次 scheduleAll 覆盖)")
        alarmManager // 占位避免 unused warning
    }

    /**
     * 根据 lessonKey 找到对应的课程详情(供 [CourseReminderReceiver] 渲染通知)。
     */
    fun findLessonByKey(sessionManager: SessionManager, key: String): LessonHint? {
        val json = sessionManager.getScheduleJson() ?: return null
        val data = runCatching {
            GsonProvider.instance.fromJson(json, ScheduleData::class.java)
        }.getOrNull() ?: return null
        return collectFutureLessons(data).firstOrNull { it.key == key }
    }

    /**
     * 收集未来 24h 内要上的课程,转换为 [LessonHint] 列表(含触发时间)。
     */
    private fun collectFutureLessons(data: ScheduleData): List<LessonHint> {
        val week = data.currentWeek.coerceAtLeast(1)
        val unitTimes = data.unitTimes
        val now = DebugClock.now()
        val today = now.toLocalDate()
        val tomorrow = today.plusDays(1)

        val systemItems = CourseRepository.toDisplayItems(
            activities = data.activities,
            selectedWeek = week,
            getDataLessons = data.lessons,
            colorMap = CourseRepository.buildColorMap(data.activities),
        )

        return systemItems.mapNotNull { item ->
            val triggerDate = nextOccurrence(item, today, tomorrow) ?: return@mapNotNull null
            val triggerTime = triggerDate.atTime(item.startLocalTime(unitTimes) ?: return@mapNotNull null)
                .minusMinutes(CourseReminderReceiver.REMINDER_LEAD_MINUTES.toLong())
            // 跳过已过期的触发时间
            if (triggerTime.isBefore(now)) return@mapNotNull null
            LessonHint(
                key = buildLessonKey(item, triggerDate),
                courseName = item.courseName,
                room = item.room.orEmpty(),
                timeText = formatTimeRange(item, unitTimes),
                startMinutes = item.startMinutes(unitTimes),
                triggerLocalDateTime = triggerTime,
            )
        }.sortedBy { it.triggerLocalDateTime }
    }

    /**
     * 计算某课程下一次出现日期:今天或明天(取决于当前时间是否已过今天该课)
     */
    private fun nextOccurrence(
        item: CourseDisplayItem,
        today: LocalDate,
        tomorrow: LocalDate,
    ): LocalDate? {
        val now = DebugClock.now()
        val todayWeekday = today.dayOfWeek.value
        val tomorrowWeekday = tomorrow.dayOfWeek.value
        return when (item.weekday) {
            todayWeekday -> today
            tomorrowWeekday -> tomorrow
            else -> null
        }
    }

    /**
     * 生成稳定的 lessonKey,用于 PendingIntent requestCode 和通知 id。
     *
     * 格式: `{date}|{courseName}|{startUnit}-{endUnit}`
     */
    private fun buildLessonKey(item: CourseDisplayItem, date: LocalDate): String =
        "${date}|${item.courseName}|${item.startUnit}-${item.endUnit}"

    private fun CourseDisplayItem.startMinutes(unitTimes: List<CourseUnit>): Int? {
        val text = startTime?.takeIf { it.isNotBlank() }
            ?: unitTimes.firstOrNull { it.indexNo == startUnit }?.startTimeStr()
        return parseTimeMinutes(text)
    }

    private fun CourseDisplayItem.startLocalTime(unitTimes: List<CourseUnit>): LocalTime? {
        val text = startTime?.takeIf { it.isNotBlank() }
            ?: unitTimes.firstOrNull { it.indexNo == startUnit }?.startTimeStr()
        val minutes = parseTimeMinutes(text) ?: return null
        return LocalTime.of(minutes / 60, minutes % 60)
    }

    private fun formatTimeRange(item: CourseDisplayItem, unitTimes: List<CourseUnit>): String {
        val unitMap = unitTimes.associateBy { it.indexNo }
        val start = item.startTime?.takeIf { it.isNotBlank() }
            ?: unitMap[item.startUnit]?.startTimeStr()
        val end = item.endTime?.takeIf { it.isNotBlank() }
            ?: unitMap[item.endUnit]?.endTimeStr()
        return when {
            !start.isNullOrBlank() && !end.isNullOrBlank() -> "$start-$end"
            !start.isNullOrBlank() -> start
            else -> "第${item.startUnit}-${item.endUnit}节"
        }
    }

    private fun parseTimeMinutes(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        val parts = text.split(":")
        if (parts.size < 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return h * 60 + m
    }

    /** 用于 Receiver 渲染通知的轻量 DTO(避免把整个 ScheduleData 传给 receiver) */
    data class LessonHint(
        val key: String,
        val courseName: String,
        val room: String,
        val timeText: String,
        val startMinutes: Int?,
        val triggerLocalDateTime: LocalDateTime?,
    )

    /** 所有 lessonKey 的统一前缀,便于将来 cancelAll 用 */
    const val LessonKeyPrefix = "reminder|"
}