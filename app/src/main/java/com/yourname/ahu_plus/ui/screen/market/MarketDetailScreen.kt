package com.yourname.ahu_plus.ui.screen.market

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator as M3CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.yourname.ahu_plus.data.model.MarketComment
import com.yourname.ahu_plus.ui.components.AhuTopAppBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MarketDetailScreen(
    uiState: MarketUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMoreComments: () -> Unit,
    onLoadMoreReplies: (MarketComment) -> Unit,
    onLoadFullCommentsForExport: suspend (Long, String?) -> Result<List<MarketComment>>,
    onCommentDraftChanged: (String) -> Unit,
    onCommentSubmit: () -> Unit,
    onCancelReply: () -> Unit,
    onStartReplyingToComment: (MarketComment) -> Unit,
    onStartReplyingToReply: (MarketComment, MarketComment) -> Unit,
    onCommentSuccessShown: () -> Unit
) {
    val topic = uiState.topicDetail ?: uiState.selectedTopic
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val commentsListState = rememberLazyListState()
    val shouldLoadMoreComments by remember(uiState.comments.size, uiState.hasMoreComments) {
        derivedStateOf {
            val total = commentsListState.layoutInfo.totalItemsCount
            val lastVisible = commentsListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 4
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    var isExporting by remember { mutableStateOf(false) }
    var pendingGalleryAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val galleryPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingGalleryAction?.invoke()
        } else {
            scope.launch { snackbarHostState.showSnackbar("没有存储权限，无法保存到相册") }
        }
        pendingGalleryAction = null
    }

    fun runWithGalleryPermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            action()
        } else {
            pendingGalleryAction = action
            galleryPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    fun saveImage(imageUrl: String) {
        runWithGalleryPermission {
            scope.launch {
                val message = runCatching {
                    MarketExportUtils.saveRemoteImage(context, imageUrl).getOrThrow()
                    "图片已保存到相册"
                }.getOrElse { error ->
                    error.message?.takeIf { it.isNotBlank() } ?: "保存失败"
                }
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    fun exportTopicImage(topicToExport: com.yourname.ahu_plus.data.model.MarketTopic) {
        runWithGalleryPermission {
            scope.launch {
                isExporting = true
                val message = runCatching {
                    val identity = uiState.selectedTopicIdentity
                        ?: uiState.topicIdentityMap[topicToExport.id]
                    val commentsResult = onLoadFullCommentsForExport(topicToExport.id, identity)
                    val comments = commentsResult.getOrThrow()
                    MarketExportUtils.exportTopicDetail(
                        context,
                        topicToExport,
                        comments,
                        uiState.topicSchoolMap[topicToExport.id]
                    ).getOrThrow()
                    "帖子详情已导出为图片（含 ${comments.size} 条评论）"
                }.getOrElse { error ->
                    error.message?.takeIf { it.isNotBlank() } ?: "导出失败"
                }
                isExporting = false
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    LaunchedEffect(shouldLoadMoreComments, uiState.comments.size, uiState.hasMoreComments) {
        if (shouldLoadMoreComments) onLoadMoreComments()
    }

    LaunchedEffect(uiState.postCommentSuccessMessage) {
        val msg = uiState.postCommentSuccessMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            onCommentSuccessShown()
        }
    }

    Scaffold(
        topBar = {
            AhuTopAppBar(
                title = { Text("帖子详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { topic?.let { exportTopicImage(it) } },
                        enabled = topic != null && !isExporting
                    ) {
                        if (isExporting) {
                            M3CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.Image, contentDescription = "导出图片")
                        }
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // 把 IME 接入 Scaffold 的 windowInsets，这样 bottomBar 会自动被输入法顶起，
        // 不会和 IME 之间留出空白条；同时仍保留 systemBars 让导航栏 padding 生效。
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.ime),
        bottomBar = {
            CommentComposerBar(
                draft = uiState.commentDraft,
                replyingTo = uiState.replyingTo,
                isPosting = uiState.isPostingComment,
                error = uiState.postCommentError,
                onDraftChanged = onCommentDraftChanged,
                onSubmit = onCommentSubmit,
                onCancelReply = onCancelReply
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            state = commentsListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (topic != null) {
                item {
                    MarketTopicDetailCard(
                        topic = topic,
                        school = uiState.topicSchoolMap[topic.id],
                        onImageClick = { previewImageUrl = it }
                    )
                }
            }

            if (uiState.detailLoading) {
                item { LoadingRow("正在同步详情...") }
            }

            uiState.detailError?.let { error ->
                item {
                    StatusCard(text = error, color = MaterialTheme.colorScheme.error) {
                        TextButton(onClick = onRefresh) { Text("重试") }
                    }
                }
            }

            item {
                Text(
                    text = "评论",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (uiState.commentsLoading && uiState.comments.isEmpty()) {
                item { LoadingRow("正在加载评论...") }
            }

            uiState.commentsError?.let { error ->
                item {
                    StatusCard(text = error, color = MaterialTheme.colorScheme.error) {
                        TextButton(onClick = onRefresh) { Text("重试") }
                    }
                }
            }

            if (!uiState.commentsLoading && uiState.commentsError == null && uiState.comments.isEmpty()) {
                item {
                    StatusCard(
                        text = "暂无评论",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(uiState.comments, key = { it.id }) { comment ->
                CommentCard(
                    comment = comment,
                    isLoadingReplies = uiState.replyLoadingCommentIds.contains(comment.id),
                    replyError = uiState.replyErrors[comment.id],
                    onLoadMoreReplies = { onLoadMoreReplies(comment) },
                    onReplyClick = { onStartReplyingToComment(comment) },
                    onReplyReplyClick = { reply -> onStartReplyingToReply(comment, reply) }
                )
            }

            if (uiState.comments.isNotEmpty()) {
                item {
                    AutoLoadFooter(
                        isLoading = uiState.commentsLoadingMore,
                        hasMore = uiState.hasMoreComments,
                        loadingText = "正在加载更多评论...",
                        emptyText = "没有更多评论了"
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    previewImageUrl?.let { imageUrl ->
        MarketImagePreviewDialog(
            imageUrl = imageUrl,
            onDismiss = { previewImageUrl = null },
            onSave = { saveImage(imageUrl) }
        )
    }
}

@Composable
private fun MarketImagePreviewDialog(
    imageUrl: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset = if (scale <= 1.01f) Offset.Zero else offset + panChange
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .transformable(transformState)
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onSave) {
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
private fun CommentCard(
    comment: MarketComment,
    isLoadingReplies: Boolean,
    replyError: String?,
    onLoadMoreReplies: () -> Unit,
    onReplyClick: () -> Unit,
    onReplyReplyClick: (MarketComment) -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(user = comment.userInfo, size = 32.dp)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp)
                ) {
                    Text(
                        text = comment.userInfo?.nickname?.takeIf { it.isNotBlank() } ?: "匿名同学",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = comment.createTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (comment.likeCount > 0) {
                    Text(
                        text = "赞 ${comment.likeCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = onReplyClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Text("回复", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                text = comment.content.ifBlank { "无内容" },
                style = MaterialTheme.typography.bodyMedium
            )
            val replies = comment.visibleReplies
            if (replies.isNotEmpty() || comment.visibleReplyCount > 0) {
                HorizontalDivider()
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(10.dp)
                ) {
                    replies.forEach { reply ->
                        ReplyRow(
                            reply = reply,
                            onReplyClick = { onReplyReplyClick(reply) }
                        )
                    }
                    val hiddenCount = comment.visibleReplyCount - replies.size
                    if (hiddenCount > 0) {
                        if (isLoadingReplies) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                M3CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "正在加载回复...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            TextButton(
                                onClick = onLoadMoreReplies,
                                modifier = Modifier.padding(horizontal = 0.dp)
                            ) {
                                Text("还有 $hiddenCount 条回复，点击加载")
                            }
                        }
                    }
                    replyError?.let { error ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onLoadMoreReplies) {
                                Text("重试")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplyRow(
    reply: MarketComment,
    onReplyClick: () -> Unit
) {
    Row(verticalAlignment = Alignment.Top) {
        UserAvatar(user = reply.userInfo, size = 24.dp)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = reply.userInfo?.nickname?.takeIf { it.isNotBlank() } ?: "同学",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                reply.pickUserInfo?.nickname?.takeIf { it.isNotBlank() }?.let { pickedName ->
                    Text(
                        text = " 回复 $pickedName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = reply.content.ifBlank { "无内容" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(
            onClick = onReplyClick,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
            Text("回复", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CommentComposerBar(
    draft: String,
    replyingTo: ReplyTarget?,
    isPosting: Boolean,
    error: String?,
    onDraftChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancelReply: () -> Unit
) {
    val nickname = replyingTo?.displayName.orEmpty()
    // Scaffold 的 contentWindowInsets 已经包含 IME，bottomBar 会被自动顶到 IME 之上，
    // 这里不能再叠 imePadding()，否则会和 IME 之间多出一段空白条。
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 2.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (replyingTo != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
                        .padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "回复 @$nickname",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = onCancelReply,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("取消", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = onDraftChanged,
                    maxLines = 3,
                    singleLine = false,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Send,
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp, max = 92.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    decorationBox = { innerTextField ->
                        Box {
                            if (draft.isBlank()) {
                                Text(
                                    text = if (replyingTo != null) "回复 @$nickname" else "说点什么",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilledIconButton(
                    onClick = onSubmit,
                    enabled = !isPosting && draft.trim().isNotEmpty(),
                    modifier = Modifier.size(42.dp)
                ) {
                    if (isPosting) {
                        M3CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送"
                        )
                    }
                }
            }
            error?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}
