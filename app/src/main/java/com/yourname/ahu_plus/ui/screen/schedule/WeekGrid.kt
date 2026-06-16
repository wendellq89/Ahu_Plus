package com.yourname.ahu_plus.ui.screen.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourname.ahu_plus.data.model.jw.CourseDisplayItem
import com.yourname.ahu_plus.data.model.jw.CourseUnit
import com.yourname.ahu_plus.data.model.jw.parseTimeMinutes
import com.yourname.ahu_plus.ui.theme.AhuBlue
import com.yourname.ahu_plus.ui.theme.AhuGreen
import com.yourname.ahu_plus.ui.theme.AhuOrange
import com.yourname.ahu_plus.ui.theme.AhuRed
import com.yourname.ahu_plus.ui.theme.AhuTeal
import com.yourname.ahu_plus.ui.theme.AhuViolet
import java.time.LocalDate
import java.time.LocalTime

val CourseColors = listOf(
    AhuBlue, Color(0xFFE76F51), AhuGreen,
    AhuViolet, AhuOrange, AhuTeal,
    AhuRed, Color(0xFF27AE60), Color(0xFF8B5CF6),
    Color(0xFF1F7A8C)
)

// ── 布局常量 (可经设置面板调整) ──────────────────────────
private val TIME_COL_WIDTH = 40.dp
private val HEADER_HEIGHT = 40.dp

private val DAY_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

@Composable
fun WeekGrid(
    displayItems: List<CourseDisplayItem>,
    unitTimes: List<CourseUnit>,
    selectedWeek: Int,
    currentWeek: Int,
    onCourseClick: (CourseDisplayItem) -> Unit = {},
    modifier: Modifier = Modifier,
    colWidth: Dp = 64.dp,
    rowHeight: Dp = 56.dp,
    fontScale: Float = 1.0f,
) {
    val sortedUnits = remember(unitTimes) {
        unitTimes.filter { it.indexNo != null }.sortedBy { it.indexNo }
    }
    if (sortedUnits.isEmpty()) return

    val todayDayOfWeek = LocalDate.now().dayOfWeek.value
    val isCurrentWeek = selectedWeek == currentWeek

    val minUnit = sortedUnits.minOf { it.indexNo ?: 1 }
    val maxUnit = sortedUnits.maxOf { it.indexNo ?: 13 }
    val totalRows = maxUnit - minUnit + 1
    val unitMap = sortedUnits.associateBy { it.indexNo }

    val horScroll = rememberScrollState()
    val verScroll = rememberScrollState()

    val gridWidth = colWidth * 7
    val bodyHeight = rowHeight * totalRows
    val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)
    val currentTimeLineY = remember(sortedUnits, isCurrentWeek, rowHeight) {
        if (!isCurrentWeek) {
            null
        } else {
            val now = LocalTime.now()
            val nowMinutes = now.hour * 60 + now.minute
            sortedUnits.firstNotNullOfOrNull { unit ->
                val unitNo = unit.indexNo ?: return@firstNotNullOfOrNull null
                val start = parseTimeMinutes(unit.startTimeStr()) ?: return@firstNotNullOfOrNull null
                val end = parseTimeMinutes(unit.endTimeStr()) ?: return@firstNotNullOfOrNull null
                if (nowMinutes !in start..end || end <= start) return@firstNotNullOfOrNull null
                val row = unitNo - minUnit
                val fraction = (nowMinutes - start).toFloat() / (end - start)
                rowHeight * row + rowHeight * fraction
            }
        }
    }

    val groupedItems = remember(displayItems) {
        val groups = mutableMapOf<String, MutableList<CourseDisplayItem>>()
        for (item in displayItems) {
            val key = "d${item.weekday}_u${item.startUnit}"
            groups.getOrPut(key) { mutableListOf() }.add(item)
        }
        groups
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(HEADER_HEIGHT)
        ) {
            Box(
                modifier = Modifier
                    .width(TIME_COL_WIDTH)
                    .height(HEADER_HEIGHT)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(0.5.dp, lineColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "时间",
                    fontSize = (12 * fontScale).sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(horScroll)
            ) {
                Row(modifier = Modifier.width(gridWidth)) {
                    for (d in 1..7) {
                        DayHeaderCell(
                            label = DAY_LABELS[d - 1],
                            isToday = isCurrentWeek && d == todayDayOfWeek,
                            lineColor = lineColor,
                            colWidth = colWidth,
                            fontScale = fontScale,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(verScroll)
        ) {
            Column(
                modifier = Modifier
                    .width(TIME_COL_WIDTH)
                    .height(bodyHeight)
            ) {
                for (u in minUnit..maxUnit) {
                    TimeCell(
                        unit = unitMap[u],
                        fallbackUnit = u,
                        lineColor = lineColor,
                        rowHeight = rowHeight,
                        fontScale = fontScale,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(horScroll)
            ) {
                Box(modifier = Modifier.size(gridWidth, bodyHeight)) {
                    for (u in minUnit..maxUnit) {
                        val row = u - minUnit
                        for (d in 1..7) {
                            val isToday = isCurrentWeek && d == todayDayOfWeek
                            Box(
                                modifier = Modifier
                                    .offset(colWidth * (d - 1), rowHeight * row)
                                    .size(colWidth, rowHeight)
                                    .background(
                                        when {
                                            isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
                                            row % 2 == 0 -> MaterialTheme.colorScheme.surface
                                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
                                        }
                                    )
                                    .border(0.5.dp, lineColor)
                            )
                        }
                    }

                    if (currentTimeLineY != null) {
                        val todayCol = todayDayOfWeek - 1
                        if (todayCol in 0..6) {
                            Box(
                                modifier = Modifier
                                    .offset(colWidth * todayCol + 5.dp, currentTimeLineY)
                                    .size(8.dp, 8.dp)
                                    .background(MaterialTheme.colorScheme.error, RoundedCornerShape(999.dp))
                            )
                            Box(
                                modifier = Modifier
                                    .offset(colWidth * todayCol + 10.dp, currentTimeLineY + 3.dp)
                                    .size(colWidth - 14.dp, 2.dp)
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.78f))
                            )
                        }
                    }

                    for (item in displayItems) {
                        val col = item.weekday - 1
                        if (col !in 0..6) continue
                        val rowStart = item.startUnit - minUnit
                        val rowSpan = item.endUnit - item.startUnit + 1
                        if (rowStart < 0 || rowSpan <= 0) continue

                        val groupKey = "d${item.weekday}_u${item.startUnit}"
                        val overlapCount = groupedItems[groupKey]?.size ?: 1
                        val overlapIdx = if (overlapCount > 1) {
                            groupedItems[groupKey]!!.indexOf(item).coerceAtLeast(0)
                        } else {
                            0
                        }
                        val overlapW = colWidth / overlapCount

                        val x = colWidth * col + overlapW * overlapIdx + 3.dp
                        val y = rowHeight * rowStart + 3.dp
                        val w = overlapW - 6.dp
                        val h = rowHeight * rowSpan - 6.dp

                        CourseCard(
                            item = item,
                            compact = rowSpan <= 1,
                            onClick = { onCourseClick(item) },
                            modifier = Modifier
                                .offset(x, y)
                                .size(w.coerceAtLeast(0.dp), h.coerceAtLeast(0.dp)),
                            fontScale = fontScale,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeaderCell(
    label: String,
    isToday: Boolean,
    lineColor: Color,
    colWidth: Dp,
    fontScale: Float,
) {
    Box(
        modifier = Modifier
            .width(colWidth)
            .height(HEADER_HEIGHT)
            .background(
                if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(0.5.dp, lineColor),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                fontSize = (13 * fontScale).sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isToday) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )
            if (isToday) {
                Text(
                    text = "今天",
                    fontSize = (9 * fontScale).sp,
                    color = MaterialTheme.colorScheme.primary,
                    lineHeight = (10 * fontScale).sp
                )
            }
        }
    }
}

@Composable
private fun TimeCell(
    unit: CourseUnit?,
    fallbackUnit: Int,
    lineColor: Color,
    rowHeight: Dp,
    fontScale: Float,
) {
    Box(
        modifier = Modifier
            .width(TIME_COL_WIDTH)
            .height(rowHeight)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f))
            .border(0.5.dp, lineColor)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${unit?.indexNo ?: fallbackUnit}",
                fontSize = (13 * fontScale).sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val start = unit?.startTimeStr().orEmpty()
            val end = unit?.endTimeStr().orEmpty()
            if (start.isNotBlank()) {
                Text(
                    text = start,
                    fontSize = (9 * fontScale).sp,
                    lineHeight = (11 * fontScale).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                )
            }
            if (end.isNotBlank()) {
                Text(
                    text = end,
                    fontSize = (9 * fontScale).sp,
                    lineHeight = (11 * fontScale).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                )
            }
        }
    }
}

@Composable
private fun CourseCard(
    item: CourseDisplayItem,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    fontScale: Float = 1.0f,
) {
    val bg = CourseColors[item.colorIndex % CourseColors.size]

    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = bg.copy(alpha = 0.94f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = item.courseName,
                fontSize = (10 * fontScale).sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = (12 * fontScale).sp
            )

            if (!item.room.isNullOrBlank()) {
                Text(
                    text = item.room,
                    fontSize = (9 * fontScale).sp,
                    color = Color.White.copy(alpha = 0.9f),
                    softWrap = true,
                    lineHeight = (10 * fontScale).sp
                )
            }

            if (!compact && item.teacherNames.isNotBlank()) {
                Text(
                    text = item.teacherNames,
                    fontSize = (9 * fontScale).sp,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = (10 * fontScale).sp
                )
            }
        }
    }
}
