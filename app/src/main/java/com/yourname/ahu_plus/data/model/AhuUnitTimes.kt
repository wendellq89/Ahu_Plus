package com.yourname.ahu_plus.data.model

import java.time.LocalTime

/**
 * 安大标准节次时间表 + 工具方法。
 *
 * 用于空教室查询中：确定当前节次 → 计算剩余节次 → 格式化时间范围。
 */
object AhuUnitTimes {
    /** 节次编号 → (开始时间, 结束时间)，格式 "HH:mm"。 */
    val UNIT_TO_TIME: Map<Int, Pair<String, String>> = mapOf(
        1 to ("08:20" to "09:05"),
        2 to ("09:15" to "10:00"),
        3 to ("10:20" to "11:05"),
        4 to ("11:15" to "12:00"),
        5 to ("14:00" to "14:45"),
        6 to ("14:55" to "15:40"),
        7 to ("15:50" to "16:35"),
        8 to ("16:45" to "17:30"),
        9 to ("19:00" to "19:45"),
        10 to ("19:55" to "20:40"),
        11 to ("20:50" to "21:35"),
        12 to ("21:45" to "22:30"),
        13 to ("22:40" to "23:25")
    )

    private val MAX_UNIT: Int = UNIT_TO_TIME.keys.maxOrNull() ?: 13

    /**
     * 返回当前时刻所在的节次编号，或即将到来的下一节。
     * 若在当日所有节次之后，返回 null。
     */
    fun getCurrentUnit(now: LocalTime = LocalTime.now()): Int? {
        val nowMinutes = now.hour * 60 + now.minute
        var nextUpcoming: Int? = null
        for ((unit, time) in UNIT_TO_TIME) {
            val startMinutes = parseMinutes(time.first)
            val endMinutes = parseMinutes(time.second)
            if (nowMinutes in startMinutes..endMinutes) return unit
            if (nowMinutes < startMinutes && nextUpcoming == null) nextUpcoming = unit
        }
        return nextUpcoming
    }

    /** 返回从 [fromUnit] 开始到当日最后一节的节次编号列表。 */
    fun getRemainingUnits(fromUnit: Int): List<Int> =
        UNIT_TO_TIME.keys.filter { it >= fromUnit }.sorted()

    /**
     * 格式化节次范围，如 "第 5-8 节 (14:00-17:30)"。
     */
    fun formatUnitRange(units: List<Int>): String {
        if (units.isEmpty()) return ""
        val first = UNIT_TO_TIME[units.first()] ?: return ""
        val last = UNIT_TO_TIME[units.last()] ?: return ""
        return "第 ${units.first()}-${units.last()} 节 (${first.first}-${last.second})"
    }

    /** 格式化单节时间，如 "14:00-14:45"。 */
    fun formatUnitTime(unit: Int): String {
        val (start, end) = UNIT_TO_TIME[unit] ?: return ""
        return "$start-$end"
    }

    /** 当日总节次（用于可视化全宽）。 */
    fun totalUnits(): Int = MAX_UNIT

    private fun parseMinutes(s: String): Int {
        val parts = s.split(":")
        return parts[0].toInt() * 60 + parts[1].toInt()
    }
}
