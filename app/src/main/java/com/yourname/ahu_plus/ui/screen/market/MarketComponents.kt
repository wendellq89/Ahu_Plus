package com.yourname.ahu_plus.ui.screen.market

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.yourname.ahu_plus.data.model.MarketComment
import com.yourname.ahu_plus.data.model.MarketIdentity
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

/**
 * 多校模式下的「选学校发布」行 —— 横向 [FilterChip] 列表,让用户明确选择把新帖发到哪所学校。
 * 单校模式不调用此组件(由调用方根据 [com.yourname.ahu_plus.data.model.MarketIdentity] 数量判断)。
 */
@Composable
internal fun SchoolPickerRow(
    identities: List<MarketIdentity>,
    selectedId: String?,
    onSelect: (String) -> Unit
) {
    val scrollState = androidx.compose.foundation.rememberScrollState()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "选择发布到哪个学校",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            identities.forEach { identity ->
                FilterChip(
                    selected = identity.id == selectedId,
                    onClick = { onSelect(identity.id) },
                    label = { Text(identity.school ?: "未识别校区") }
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  帖子卡片（列表 / 热榜 / 详情 通用）
// ═══════════════════════════════════════════════════════════

@Composable
internal fun MarketTopicCard(
    topic: MarketTopic,
    onClick: () -> Unit,
    school: String? = null,
    showTopComments: Boolean = true
) {
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
            // 列表接口可能返回前两条热门评论(若接口字段未提供则空列表,不显示)
            if (showTopComments && topic.topComments.isNotEmpty()) {
                TopCommentsPreview(
                    comments = topic.topComments.take(2),
                    totalCount = topic.commentCount
                )
            }
            TopicFooter(topic = topic)
        }
    }
}

/**
 * 列表卡片底部的「前两条评论」预览(无头像,只显示用户名 + 内容)。
 * 设计:半透明背景 + 左侧色条,跟帖子主区域视觉分层。
 */
@Composable
private fun TopCommentsPreview(
    comments: List<MarketComment>,
    totalCount: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        comments.forEach { comment ->
            val nickname = comment.userInfo?.nickname?.takeIf { it.isNotBlank() } ?: "匿名"
            Text(
                text = "${nickname}: ${comment.content}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (totalCount > comments.size) {
            Text(
                text = "查看全部 $totalCount 条评论",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  小红书双列瀑布流卡片
// ═══════════════════════════════════════════════════════════

/**
 * 小红书风格双列卡片:
 * - 有图:大图(3:4 portrait aspectRatio)+ 标题 2 行 + 用户 + 学校 + 评论数
 * - 纯文字:大字体居中显示部分内容,占据图位
 *
 * 注意:瀑布流模式按用户要求不显示前两条评论。
 */
@Composable
internal fun StaggerMarketTopicCard(
    topic: MarketTopic,
    onClick: () -> Unit,
    school: String? = null
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column {
            // 图片/文字区(占图位)
            if (topic.imgs.isNotEmpty()) {
                AsyncImage(
                    model = topic.imgs.first(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.75f)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
            } else {
                // 纯文字:把字调大,占图位
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.75f)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = topic.content.take(60).ifBlank { topic.title },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            // 标题 + meta
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = topic.title.takeIf { it.isNotBlank() && it != "none" }
                        ?: topic.content.take(20),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = topic.userInfo?.nickname?.takeIf { it.isNotBlank() } ?: "匿名",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (school != null) {
                        Text(
                            text = "· $school",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = "💬 ${topic.commentCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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
    onImageClick: (String, Int) -> Unit = { _, _ -> }
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
    onImageClick: (String, Int) -> Unit = { _, _ -> }
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
                .clickable { onImageClick(visible.first(), 0) }
        )
        2 -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            visible.take(2).forEachIndexed { index, url ->
                TopicImage(
                    url = url,
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(corner)
                        .clickable { onImageClick(url, index) }
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
                        .clickable { onImageClick(url, index) }
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

/**
 * 紧凑模式身份卡 —— 适配「设置页」:不再暴露丑陋的大块 OutlinedTextField,
 * 改为可点击的简洁 Card(单行),点击后弹出 [IdentityInputDialog] 输入真实 token。
 *
 * @param onAddIdentity 点 "保存" 后的回调 —— 沿用现有 [MarketViewModel.saveIdentity] 逻辑
 * @param onRemoveIdentity 移除已保存身份
 */
@Composable
fun CompactIdentityCard(
    uiState: MarketUiState,
    onIdentityChanged: (String) -> Unit,
    onAddIdentity: () -> Unit,
    onRemoveIdentity: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val firstIdentity = uiState.identities.firstOrNull()

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDialog = true }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MarketColors.IdentityAccentBg.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Key,
                    contentDescription = null,
                    tint = MarketColors.IdentityAccent
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "API 身份字段",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = firstIdentity?.let {
                        "${it.school ?: "未识别校区"} · ${maskToken(it.token)}"
                    } ?: "点击添加 Bearer JWT 身份字段",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (firstIdentity != null) {
                IconButton(onClick = onRemoveIdentity) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "添加",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        uiState.identityError?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
            )
        }
        uiState.saveMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MarketColors.Success,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
            )
        }
    }

    if (showDialog) {
        IdentityInputDialog(
            initialValue = uiState.identityInput,
            onValueChange = onIdentityChanged,
            onDismiss = { showDialog = false },
            onSave = {
                onAddIdentity()
                showDialog = false
            }
        )
    }
}

@Composable
private fun IdentityInputDialog(
    initialValue: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var showToken by rememberSaveable { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties()
    ) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "添加 API 身份字段",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "从微信小程序抓包获取 Bearer JWT 后粘贴到下方",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = initialValue,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Bearer eyJ...") },
                    minLines = 2,
                    maxLines = 4,
                    leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(
                                imageVector = if (showToken) Icons.Filled.VisibilityOff
                                else Icons.Filled.Visibility,
                                contentDescription = if (showToken) "隐藏" else "显示"
                            )
                        }
                    },
                    visualTransformation = if (showToken) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(
                        onClick = onSave,
                        enabled = initialValue.isNotBlank()
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
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

// ═══════════════════════════════════════════════════════════
//  可拖动悬浮按钮(DraggableFab)
// ═══════════════════════════════════════════════════════════

/**
 * 可拖动悬浮按钮(FAB)。
 * - 默认位置:右下角(bottom 16dp,end 16dp)
 * - 拖动:用 [pointerInput] 累积 [IntOffset]
 * - 位置:用 [rememberSaveable] 持久化(横竖屏切换后保留)
 * - 边界:约束在父容器内(0..maxX, 0..maxY)
 * - 可见性:由 `visible` 控制;false 时不渲染
 */
@Composable
fun DraggableFab(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    val density = LocalDensity.current
    val fabSize = 56.dp
    val initialMargin = 16.dp

    // 用 rememberSaveable 持久化位置(x, y 单位 px,相对屏幕右下角偏移)
    var offsetX by rememberSaveable { mutableIntStateOf(0) }
    var offsetY by rememberSaveable { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxSize()
            // 整体定位在右下角,然后用 offset 调整用户拖动
            .padding(end = initialMargin, bottom = initialMargin),
        contentAlignment = Alignment.BottomEnd
    ) {
        Box(
            modifier = Modifier
                .offset {
                    // 负值表示「往左上方向移动」,所以保存正值表示「往右下方向」
                    IntOffset(
                        x = -offsetX,
                        y = -offsetY
                    )
                }
                .size(fabSize)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        // 累积拖动量;限制在容器内
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
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Icon(
                imageVector = androidx.compose.material.icons.Icons.Filled.Add,
                contentDescription = "发帖",
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  多图浏览:HorizontalPager + 点击黑边关闭
// ═══════════════════════════════════════════════════════════

/** 多图预览的状态。`urls` 为当前帖子所有图片,`initialIndex` 为点击时的索引。 */
data class ImagePreviewState(val urls: List<String>, val initialIndex: Int = 0) {
    val isEmpty: Boolean get() = urls.isEmpty()
    val size: Int get() = urls.size
}

@Composable
fun MarketImagePreviewPager(
    state: ImagePreviewState,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    if (state.isEmpty) return
    val pagerState = rememberPagerState(initialPage = state.initialIndex) { state.size }
    val currentUrl = state.urls.getOrNull(pagerState.currentPage) ?: state.urls.first()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                // 点击黑色边缘关闭(图片本身 clickable=false 阻止冒泡)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                key(state.urls[page]) {
                    PagerImageItem(url = state.urls[page])
                }
            }

            // 顶部栏:页码 + 保存 + 关闭
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${state.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { onSave(pagerState.currentPage) }
                ) {
                    Icon(
                        Icons.Filled.Save,
                        contentDescription = "保存图片",
                        tint = Color.White
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun PagerImageItem(url: String) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset = if (scale <= 1.01f) Offset.Zero else offset + panChange
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                // 图片本身不消费点击,让外层 Box 的"点黑边关闭"生效
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .transformable(transformState)
        )
    }
}
