package com.yourname.ahu_plus.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 倒计时圆弧组件 — 画布圆环 + 居中秒数文本。
 *
 * 用于校园支付码等需要可视化倒计时的场景。
 *
 * @param secondsRemaining 剩余秒数
 * @param totalSeconds     总倒计时秒数
 * @param modifier         修饰符
 * @param strokeWidth      弧线粗细
 * @param trackColor       底轨颜色
 * @param progressColor    进度弧颜色
 * @param textColor        秒数文字颜色
 */
@Composable
fun CountdownArc(
    secondsRemaining: Int,
    totalSeconds: Int,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 3.dp,
    trackColor: Color = Color.White.copy(alpha = 0.25f),
    progressColor: Color = Color.White,
    textColor: Color = Color.White
) {
    val fraction = (secondsRemaining.coerceIn(0, totalSeconds)).toFloat() / totalSeconds

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val strokePx = strokeWidth.toPx()
            val halfStroke = strokePx / 2f
            val arcSize = Size(
                width = size.width - strokePx,
                height = size.height - strokePx
            )
            val topLeft = Offset(halfStroke, halfStroke)

            // 底轨（全圆）
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // 进度弧（从顶部顺时针，随剩余时间减少而缩短）
            val sweepAngle = 360f * fraction
            if (sweepAngle > 0f) {
                drawArc(
                    color = progressColor,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }

        Text(
            text = "${secondsRemaining}s",
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
    }
}

/** 以 dp 尺寸创建指定大小的 CountdownArc。 */
@Composable
fun CountdownArc(
    secondsRemaining: Int,
    totalSeconds: Int,
    size: Dp,
    strokeWidth: Dp = 3.dp,
    trackColor: Color = Color.White.copy(alpha = 0.25f),
    progressColor: Color = Color.White,
    textColor: Color = Color.White
) {
    CountdownArc(
        secondsRemaining = secondsRemaining,
        totalSeconds = totalSeconds,
        modifier = Modifier.size(size),
        strokeWidth = strokeWidth,
        trackColor = trackColor,
        progressColor = progressColor,
        textColor = textColor
    )
}
