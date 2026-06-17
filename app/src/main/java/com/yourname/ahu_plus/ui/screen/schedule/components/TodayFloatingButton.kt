package com.yourname.ahu_plus.ui.screen.schedule.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourname.ahu_plus.ui.theme.AhuBlue
import kotlinx.coroutines.delay

/**
 * "今" 浮动按钮 (2026-06-17): 课表内非当前周时显示,点击回到本周。
 *
 * 点击动画 (2026-06-17 Bug10): 按下时缩小到 0.85, 松开时弹回 1.0。
 * 让用户明确感知到操作被触发, 动画 200ms 完成。
 */
@Composable
fun TodayFloatingButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    val density = LocalDensity.current
    val fabSize = 40.dp
    val initialMargin = 16.dp

    var offsetX by rememberSaveable { mutableIntStateOf(0) }
    var offsetY by rememberSaveable { mutableIntStateOf(0) }

    // 按下缩放动画
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.85f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "fab-scale",
    )

    // 防御: 若 onClick 后状态没回归, 200ms 后强制 reset
    LaunchedEffect(pressed) {
        if (pressed) {
            delay(220)
            pressed = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(end = initialMargin, bottom = initialMargin),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(x = -offsetX, y = -offsetY) }
                .size(fabSize)
                .scale(scale)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(AhuBlue)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { pressed = true },
                        onDragEnd = { pressed = false },
                        onDragCancel = { pressed = false },
                    ) { change, drag ->
                        change.consume()
                        val maxX = (size.width - with(density) { fabSize.toPx() }).toInt()
                            .coerceAtLeast(0)
                        val maxY = (size.height - with(density) { fabSize.toPx() }).toInt()
                            .coerceAtLeast(0)
                        offsetX = (offsetX - drag.x.toInt()).coerceIn(-maxX, maxX)
                        offsetY = (offsetY - drag.y.toInt()).coerceIn(-maxX, maxY)
                    }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    pressed = true
                    onClick()
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "今",
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
