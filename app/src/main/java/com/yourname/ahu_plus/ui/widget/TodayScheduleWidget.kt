package com.yourname.ahu_plus.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.yourname.ahu_plus.MainActivity

class TodayScheduleWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = TodayScheduleWidgetData.load(context)
        provideContent {
            TodayScheduleWidgetContent(state)
        }
    }
}

class TodayScheduleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayScheduleWidget()
}

object TodayScheduleWidgetUpdater {
    suspend fun updateAll(context: Context) {
        TodayScheduleWidget().updateAll(context.applicationContext)
    }
}

/**
 * Widget 内嵌"刷新"按钮触发的 ActionCallback。
 *
 * 借鉴 AHUTong RefreshAction:点击后调用 [TodayScheduleWidget.update] 重新拉取
 * provideGlance → 刷新显示。注意这里只是触发 widget 重渲染,不会重新拉取网络
 * (网络数据由 WidgetUpdateScheduler 每 30 分钟拉一次)。
 */
class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        TodayScheduleWidget().update(context, glanceId)
    }
}

private val WidgetBackground = Color(0xFF172033)
private val WidgetPanel = Color(0xFFF7FAFF)
private val WidgetSoftPanel = Color(0xFFEAF2FF)
private val WidgetPrimary = Color(0xFF2F6FC7)
private val WidgetText = Color(0xFF111827)
private val WidgetMuted = Color(0xFF607084)
private val WidgetLine = Color(0xFFD6E2F4)
private val WidgetSuccess = Color(0xFF16865B)
private val WidgetWarning = Color(0xFFE28B20)
private val WidgetOnDark = Color(0xFFFFFFFF)

@androidx.glance.GlanceComposable
@Composable
private fun TodayScheduleWidgetContent(state: TodayScheduleWidgetState) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(WidgetBackground))
            .clickable(actionStartActivity<MainActivity>())
            .padding(12.dp),
    ) {
        HeaderRow(state)
        Spacer(GlanceModifier.height(10.dp))
        FocusPanel(state)
        if (state.upcoming.isNotEmpty()) {
            Spacer(GlanceModifier.height(8.dp))
            TimelineHeader(state)
            state.upcoming.take(2).forEach { course ->
                CourseLine(course)
            }
        }
        Spacer(GlanceModifier.defaultWeight())
        Footer(state)
    }
}

@androidx.glance.GlanceComposable
@Composable
private fun HeaderRow(state: TodayScheduleWidgetState) {
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        Column {
            Text(
                text = "AHU+",
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(WidgetOnDark),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                ),
            )
            Text(
                text = if (state.todayCount > 0) "今日 ${state.todayCount} 门课" else "今日课表",
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(Color(0xFFB9C7D8)),
                    fontSize = 11.sp,
                ),
            )
        }
        Spacer(GlanceModifier.defaultWeight())
        StatusChip(state.status)
        Spacer(GlanceModifier.width(6.dp))
        // 2026-06-22 新增:借鉴 AHUTong 内嵌刷新按钮
        Text(
            text = "刷新",
            maxLines = 1,
            modifier = GlanceModifier
                .background(ColorProvider(Color(0xFF2A394E)))
                .cornerRadius(8.dp)
                .clickable(actionRunCallback<RefreshWidgetAction>())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            style = TextStyle(
                color = ColorProvider(WidgetOnDark),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@androidx.glance.GlanceComposable
@Composable
private fun StatusChip(status: TodayScheduleStatus) {
    Box(
        modifier = GlanceModifier
            .background(ColorProvider(statusColor(status)))
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(
            text = statusLabel(status),
            maxLines = 1,
            style = TextStyle(
                color = ColorProvider(WidgetOnDark),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            ),
        )
    }
}

@androidx.glance.GlanceComposable
@Composable
private fun FocusPanel(state: TodayScheduleWidgetState) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(ColorProvider(WidgetPanel))
            .padding(12.dp),
    ) {
        Text(
            text = state.subtitle.ifBlank { "今日课表" },
            maxLines = 1,
            style = TextStyle(
                color = ColorProvider(statusColor(state.status)),
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
            ),
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = state.title,
            maxLines = 1,
            style = TextStyle(
                color = ColorProvider(WidgetText),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            ),
        )
        if (state.detail.isNotBlank()) {
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = state.detail,
                maxLines = 1,
                style = TextStyle(color = ColorProvider(WidgetMuted), fontSize = 12.sp),
            )
        }
    }
}

@androidx.glance.GlanceComposable
@Composable
private fun TimelineHeader(state: TodayScheduleWidgetState) {
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        Text(
            text = when (state.status) {
                TodayScheduleStatus.Finished -> "今天上过"
                else -> "接下来"
            },
            maxLines = 1,
            style = TextStyle(
                color = ColorProvider(Color(0xFFB9C7D8)),
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
            ),
        )
        Spacer(GlanceModifier.defaultWeight())
        Text(
            text = "点按查看完整课表",
            maxLines = 1,
            style = TextStyle(color = ColorProvider(Color(0xFF8FA3BA)), fontSize = 10.sp),
        )
    }
    Spacer(GlanceModifier.height(4.dp))
}

@androidx.glance.GlanceComposable
@Composable
private fun CourseLine(course: TodayScheduleWidgetCourse) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(ColorProvider(WidgetSoftPanel))
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Text(
            text = course.time,
            maxLines = 1,
            style = TextStyle(
                color = ColorProvider(WidgetPrimary),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            ),
            modifier = GlanceModifier.width(74.dp),
        )
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = course.name,
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(WidgetText),
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                ),
            )
            if (course.room.isNotBlank()) {
                Text(
                    text = course.room,
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(WidgetMuted), fontSize = 10.sp),
                )
            }
        }
    }
    Spacer(GlanceModifier.height(4.dp))
}

@androidx.glance.GlanceComposable
@Composable
private fun Footer(state: TodayScheduleWidgetState) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Spacer(GlanceModifier.height(1.dp).fillMaxWidth().background(ColorProvider(Color(0xFF2A394E))))
        Spacer(GlanceModifier.height(5.dp))
        Text(
            text = state.updatedText.ifBlank { "点按打开完整课表" },
            maxLines = 1,
            style = TextStyle(color = ColorProvider(Color(0xFFB9C7D8)), fontSize = 10.sp),
        )
    }
}

private fun statusLabel(status: TodayScheduleStatus): String = when (status) {
    TodayScheduleStatus.InClass -> "上课中"
    TodayScheduleStatus.Next -> "下节课"
    TodayScheduleStatus.Finished -> "已结束"
    TodayScheduleStatus.Empty -> "空课表"
    TodayScheduleStatus.NoCache -> "待同步"
    TodayScheduleStatus.Error -> "需刷新"
}

private fun statusColor(status: TodayScheduleStatus): Color = when (status) {
    TodayScheduleStatus.InClass -> WidgetSuccess
    TodayScheduleStatus.Next -> WidgetPrimary
    TodayScheduleStatus.Finished -> WidgetMuted
    TodayScheduleStatus.Empty -> WidgetPrimary
    TodayScheduleStatus.NoCache -> WidgetWarning
    TodayScheduleStatus.Error -> Color(0xFFB3261E)
}
