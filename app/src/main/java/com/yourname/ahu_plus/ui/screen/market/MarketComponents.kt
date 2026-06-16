package com.yourname.ahu_plus.ui.screen.market

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yourname.ahu_plus.data.model.MarketTopic
import com.yourname.ahu_plus.data.model.MarketUser
import com.yourname.ahu_plus.ui.components.AhuShapes
import com.yourname.ahu_plus.ui.theme.MarketColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// ═══════════════════════════════════════════════════════════
//  头像 / Loading / Status / Footer
// ═══════════════════════════════════════════════════════════

@Composable
internal fun UserAvatar(user: MarketUser?, size: Dp) {
    val avatar = user?.avatar?.takeIf { it.isNotBlank() }
    val fallback = user?.nickname?.takeIf { it.isNotBlank() }?.firstOrNull()?.toString() ?: "匿"

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (avatar != null) {
            AsyncImage(
                model = avatar,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = fallback,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
internal fun AutoLoadFooter(
    isLoading: Boolean,
    hasMore: Boolean,
    loadingText: String,
    emptyText: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> LoadingRow(loadingText)
            hasMore -> Text(
                text = "继续下滑加载更多",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> Text(
                text = emptyText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun LoadingRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Text(text)
    }
}

@Composable
internal fun StatusCard(
    text: String,
    color: Color,
    action: (@Composable () -> Unit)? = null
) {
    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                color = color,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            action?.invoke()
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  帖子主题渲染：标题 / 头 / 首图 / 底部
// ═══════════════════════════════════════════════════════════

@Composable
internal fun TopicMetaHeader(topic: MarketTopic, school: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        UserAvatar(user = topic.userInfo, size = 36.dp)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = topic.userInfo?.nickname?.takeIf { it.isNotBlank() } ?: "匿名同学",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = topic.node.ifBlank { "集市" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                if (school != null) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = school,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        Text(
            text = relativeMarketTime(topic.createTime),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
internal fun TopicTitle(topic: MarketTopic) {
    val title = topic.title.takeIf { it.isNotBlank() && it != "none" }
    if (title != null) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun TopicFirstImage(topic: MarketTopic) {
    TopicImageGrid(imgs = topic.imgs, detail = false)
}

@Composable
internal fun TopicFooter(topic: MarketTopic) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "赞 ${topic.likeCount}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(10.dp))
        Icon(
            Icons.Filled.ChatBubbleOutline,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = topic.commentCount.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (topic.imgs.isNotEmpty()) {
            Spacer(modifier = Modifier.width(10.dp))
            Icon(
                Icons.Filled.Image,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = topic.imgs.size.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  帖子卡片（列表 / 热榜 / 详情 通用）
// ═══════════════════════════════════════════════════════════

@Composable
internal fun MarketTopicCard(topic: MarketTopic, onClick: () -> Unit, school: String? = null) {
    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TopicMetaHeader(topic = topic, school = school)
            TopicTitle(topic = topic)
            Text(
                text = topic.content.ifBlank { "无正文" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )
            TopicFirstImage(topic = topic)
            TopicFooter(topic = topic)
        }
    }
}

@Composable
internal fun HotTopicCard(
    topic: MarketTopic,
    rank: Int,
    onClick: () -> Unit,
    school: String? = null
) {
    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        when (rank) {
                            1 -> MarketColors.HotFlame
                            2 -> MarketColors.HotBadge
                            3 -> MarketColors.HotBadgeGold
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = rank.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (rank <= 3) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TopicMetaHeader(topic = topic, school = school)
                TopicTitle(topic = topic)
                Text(
                    text = topic.content.ifBlank { "无正文" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                TopicFirstImage(topic = topic)
                TopicFooter(topic = topic)
            }
        }
    }
}

@Composable
internal fun MarketTopicDetailCard(
    topic: MarketTopic,
    school: String? = null,
    onImageClick: (String) -> Unit = {}
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TopicMetaHeader(topic = topic, school = school)
            TopicTitle(topic = topic)
            Text(
                text = topic.content.ifBlank { "无正文" },
                style = MaterialTheme.typography.bodyLarge
            )
            TopicImageGrid(imgs = topic.imgs, detail = true, onImageClick = onImageClick)
            TopicFooter(topic = topic)
        }
    }
}

@Composable
private fun TopicImageGrid(
    imgs: List<String>,
    detail: Boolean,
    onImageClick: (String) -> Unit = {}
) {
    val visible = imgs.filter { it.isNotBlank() }
    if (visible.isEmpty()) return

    val corner = RoundedCornerShape(10.dp)
    val singleHeight = if (detail) 260.dp else 190.dp
    when (visible.size) {
        1 -> TopicImage(
            url = visible.first(),
            modifier = Modifier
                .fillMaxWidth()
                .height(singleHeight)
                .clip(corner)
                .clickable { onImageClick(visible.first()) }
        )
        2 -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            visible.take(2).forEach { url ->
                TopicImage(
                    url = url,
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(corner)
                        .clickable { onImageClick(url) }
                )
            }
        }
        else -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            visible.take(3).forEachIndexed { index, url ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(corner)
                        .clickable { onImageClick(url) }
                ) {
                    TopicImage(
                        url = url,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (index == 2 && visible.size > 3) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.38f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+${visible.size - 3}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicImage(url: String, modifier: Modifier) {
    AsyncImage(
        model = url,
        contentDescription = null,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentScale = ContentScale.Crop
    )
}

// ═══════════════════════════════════════════════════════════
//  集市身份字段输入卡（列表页 + 设置页 + 我的页 共享）
// ═══════════════════════════════════════════════════════════

@Composable
internal fun IdentityCard(
    uiState: MarketUiState,
    onIdentityChanged: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showIdentity by rememberSaveable { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MarketColors.IdentityAccentBg.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Storefront,
                        contentDescription = null,
                        tint = MarketColors.IdentityAccent
                    )
                }
                Column(
                    modifier = Modifier.padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "集市身份字段",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = uiState.school?.let { "当前学校：$it" } ?: "粘贴 Bearer 字段后加载内容",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = uiState.identityInput,
                onValueChange = onIdentityChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API 身份字段") },
                placeholder = { Text("Bearer eyJ...") },
                minLines = 2,
                maxLines = 4,
                leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { showIdentity = !showIdentity }) {
                        Icon(
                            imageVector = if (showIdentity) Icons.Filled.VisibilityOff
                            else Icons.Filled.Visibility,
                            contentDescription = if (showIdentity) "隐藏" else "显示"
                        )
                    }
                },
                visualTransformation = if (showIdentity) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )

            uiState.identityError?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            uiState.saveMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MarketColors.Success
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("保存")
                }
                OutlinedButton(
                    onClick = onClear,
                    enabled = uiState.hasSavedIdentity
                ) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("清除")
                }
            }
        }
    }
}

/**
 * 公开 API —— `ProfileScreen` 通过它嵌入"集市身份字段"卡到我的页。
 * 实际渲染委托给共享的 [IdentityCard]。
 */
@Composable
fun MarketIdentityEditor(
    uiState: MarketUiState,
    onIdentityChanged: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    IdentityCard(
        uiState = uiState,
        onIdentityChanged = onIdentityChanged,
        onSave = onSave,
        onClear = onClear,
        modifier = modifier
    )
}

/** 把 Token 字符串截断到合适长度用于 UI 展示。 */
internal fun maskToken(token: String): String {
    val t = if (token.startsWith("Bearer ", ignoreCase = true)) token.drop(7) else token
    return if (t.length > 24) "${t.take(12)}…${t.takeLast(8)}" else t
}

private fun relativeMarketTime(raw: String): String {
    if (raw.isBlank()) return ""
    val parsed = runCatching {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(raw)
    }.getOrNull() ?: return raw
    val diff = Date().time - parsed.time
    if (diff < 0) return raw
    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "刚刚"
        diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)} 分钟前"
        diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)} 小时前"
        diff < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(diff)} 天前"
        else -> SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(parsed)
    }
}
