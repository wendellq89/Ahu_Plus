package com.yourname.ahu_plus.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 课表课程调色板 (10 色)。
 *
 * 用于 [com.yourname.ahu_plus.ui.screen.schedule.WeekGrid] 中按 [colorIndex] 选色。
 * 选用品牌色 + 2 个补充色,保证可读性 (浅底卡片上文字白/深色均可辨认)。
 */
val CourseColors: List<Color> = listOf(
    AhuBlue,           // 0  深蓝
    AhuTeal,           // 1  青绿
    AhuIndigo,         // 2  靛紫
    AhuGreen,          // 3  翠绿
    AhuOrange,         // 4  橙黄
    AhuRed,            // 5  朱红
    AhuViolet,         // 6  紫罗兰
    AhuBlueLight,      // 7  浅蓝
    Color(0xFFE76F51), // 8  珊瑚
    Color(0xFF1F7A8C), // 9  深青
)
