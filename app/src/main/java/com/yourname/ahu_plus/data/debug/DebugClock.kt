package com.yourname.ahu_plus.data.debug

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 全局时间注入工具。
 *
 * 设计动机:Ahu_Plus 大量"时间判断"逻辑(今天有课吗 / 当前节次 / 考试是否已结束 / 课程提醒触发时机)
 * 都直接调用 [LocalTime.now] / [LocalDate.now] / [System.currentTimeMillis],无法在测试和调试
 * 时控制时间。借助此工具替换为单点入口,所有时间判断都可以走"时间穿越"。
 *
 * 用法(生产代码):
 *   val now = DebugClock.now()                                  // 替代 LocalDateTime.now()
 *   val today = DebugClock.todayDate()                          // 替代 LocalDate.now()
 *   val nowMs = DebugClock.nowMillis()                          // 替代 System.currentTimeMillis()
 *
 * 用法(调试 / 单元测试):
 *   DebugClock.advanceTo(LocalDateTime.of(2026, 6, 23, 8, 30))  // 穿越到下节课前
 *   ... 跑你的时间判断逻辑 ...
 *   DebugClock.reset()                                          // 恢复真实时间
 *
 * 设计目标:
 *  - 零依赖(只用 java.time 标准库)
 *  - 线程安全(@Volatile + 单一可变字段)
 *  - 默认行为完全等价于真实时间(生产无感)
 *  - 不引入第三方 mock 框架(运行时小开销)
 */
object DebugClock {
    private const val TAG = "DebugClock"

    /**
     * 用户注入的偏移量(毫秒)。
     *
     * - null → 使用真实时间
     * - 非 null → nowMillis() = System.currentTimeMillis() + offset
     */
    @Volatile
    private var offsetMillis: Long? = null

    // ── 真实时间原语(供"真实"路径使用,以及 reset() 时校验)──────

    fun nowMillis(): Long {
        val offset = offsetMillis
        return if (offset != null) {
            System.currentTimeMillis() + offset
        } else {
            System.currentTimeMillis()
        }
    }

    fun now(): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis()), ZoneId.systemDefault())

    fun todayDate(): LocalDate = now().toLocalDate()

    fun nowTime(): LocalTime = now().toLocalTime()

    /**
     * 当前时间在当天的分钟数(0..1439)。
     *
     * 用于课表节次判断、空教室时间标记线、考试剩余时间等场景。
     */
    fun currentMinutes(): Int {
        val t = nowTime()
        return t.hour * 60 + t.minute
    }

    /**
     * 把分钟数(0..1439)转为格式化时间 "HH:mm",便于 UI 显示。
     */
    fun formatMinutes(minutes: Int): String {
        val safe = ((minutes % 1440) + 1440) % 1440
        return "%02d:%02d".format(safe / 60, safe % 60)
    }

    // ── 调试入口(默认关闭)────────────────────────────

    /**
     * 把当前时间"穿越"到 [target]。
     *
     * 后续所有 [nowMillis] / [now] / [todayDate] / [nowTime] / [currentMinutes]
     * 都返回相对真实时间偏移 [target] 的结果。
     */
    fun advanceTo(target: LocalDateTime) {
        val targetMillis = target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val realMillis = System.currentTimeMillis()
        offsetMillis = targetMillis - realMillis
        android.util.Log.i(TAG, "advanceTo: $target (offset=${offsetMillis}ms)")
    }

    /**
     * 等价于 [advanceTo](now + durationMillis),方便测试快速推进时间。
     */
    fun advance(deltaMillis: Long) {
        offsetMillis = (offsetMillis ?: 0L) + deltaMillis
        android.util.Log.i(TAG, "advance: ${deltaMillis}ms (offset=${offsetMillis}ms)")
    }

    /**
     * 清除注入,恢复到真实时间。
     */
    fun reset() {
        offsetMillis = null
        android.util.Log.i(TAG, "reset: 恢复真实时间")
    }

    /**
     * 当前是否处于"时间穿越"状态。供 UI 显示调试横幅或测试断言。
     */
    fun isFrozen(): Boolean = offsetMillis != null
}