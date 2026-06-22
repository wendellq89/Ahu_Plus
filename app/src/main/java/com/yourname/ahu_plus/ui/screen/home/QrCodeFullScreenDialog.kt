package com.yourname.ahu_plus.ui.screen.home

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yourname.ahu_plus.data.repository.AdwmhQrCode
import com.yourname.ahu_plus.ui.components.CountdownArc
import com.yourname.ahu_plus.ui.theme.AhuGradient
import com.yourname.ahu_plus.ui.theme.AhuShapes
import com.yourname.ahu_plus.util.QrCodeBitmap
import java.text.DecimalFormat

/**
 * 全屏沉浸式校园支付码弹窗。
 *
 * - 暗色 scrim 背景
 * - 大尺寸 QR 码（300dp）+ 脉冲呼吸动画
 * - 自动提升屏幕亮度（退出恢复）
 * - 倒计时环（72dp）+ 余额 + 刷新时间
 * - 分享到微信 / 手动刷新 / 关闭
 */
@Composable
fun QrCodeFullScreenDialog(
    qrCode: AdwmhQrCode?,
    balance: Double?,
    isLoading: Boolean,
    countdownSeconds: Int,
    totalCountdownSeconds: Int,
    qrError: String?,
    brightnessBoost: Boolean = true,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val window = remember { context.findActivity()?.window }

    // 仅当设置开启时才调高屏幕亮度；关闭 → 恢复
    DisposableEffect(brightnessBoost) {
        if (brightnessBoost) {
            val original = window?.attributes?.screenBrightness
            window?.attributes = window?.attributes?.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            }
            onDispose {
                window?.attributes = window?.attributes?.apply {
                    screenBrightness = original ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        } else {
            onDispose { }
        }
    }

    // 脉冲动画
    val pulseTransition = rememberInfiniteTransition(label = "qrFullPulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
        ) {
            // 关闭按钮 — 右上角
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f)),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = Color.White
                )
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "关闭",
                    modifier = Modifier.size(22.dp)
                )
            }

            // 主内容 — 居中
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when {
                    qrCode != null -> {
                        // 已有 QR 码 → 显示大码
                        val image = remember(qrCode.payload) {
                            QrCodeBitmap.create(qrCode.payload, 720)
                        }
                        Image(
                            bitmap = image,
                            contentDescription = "校园支付码",
                            modifier = Modifier
                                .size(300.dp)
                                .graphicsLayer(
                                    scaleX = pulseScale,
                                    scaleY = pulseScale
                                )
                        )

                        // 倒计时环 + 余额行
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            CountdownArc(
                                secondsRemaining = countdownSeconds,
                                totalSeconds = totalCountdownSeconds,
                                size = 56.dp,
                                strokeWidth = 3.dp,
                                trackColor = Color.White.copy(alpha = 0.2f),
                                progressColor = Color.White,
                                textColor = Color.White
                            )

                            if (balance != null) {
                                Text(
                                    text = DecimalFormat("¥#,##0.00").format(balance),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        // 服务器时间
                        Text(
                            text = qrCode.serverTimeText.ifBlank { "已刷新" },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )

                        // 刷新中指示
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    else -> {
                        // 还未生成 QR 码 → 显示当前阶段进度
                        ProgressView(
                            qrError = qrError,
                            isLoading = isLoading,
                            onRetry = onRefresh
                        )
                    }
                }
            }

            // 底部操作栏 — 有 QR 时显示刷新按钮，否则显示刷新+关闭
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                TextButton(onClick = onRefresh) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("刷新", color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
    }
}

/** 从 Context 中提取 Activity（递归解包 ContextWrapper）。 */
internal fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * QR 码生成进度视图。
 *
 * 显示当前处于哪一步：
 * 1. 连接智慧安大服务器
 * 2. 获取验证码图片
 * 3. OCR 识别验证码 / 登录智慧安大
 * 4. 获取支付码
 *
 * 当 [qrError] 不为空时切换到错误视图，显示具体卡住的原因。
 */
@Composable
private fun ProgressView(
    qrError: String?,
    isLoading: Boolean,
    onRetry: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.padding(horizontal = 32.dp)
    ) {
        // 错误优先
        if (qrError != null) {
            Text(
                text = "⚠ 加载失败",
                color = Color(0xFFEF5350),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = qrError,
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                onClick = onRetry,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = Color.White
                )
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("重试", color = Color.White)
            }
            return@Column
        }

        // 加载中 → 显示步骤
        CircularProgressIndicator(
            color = Color.White,
            modifier = Modifier.size(56.dp),
            strokeWidth = 3.dp
        )
        Text(
            text = "正在生成支付码",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (isLoading) "请稍候..." else "点击刷新按钮开始",
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 步骤指示
        ProgressStep(index = 1, label = "连接智慧安大服务器")
        ProgressStep(index = 2, label = "验证登录状态")
        ProgressStep(index = 3, label = "获取支付码数据")
    }
}

@Composable
private fun ProgressStep(index: Int, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$index",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall
            )
        }
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
