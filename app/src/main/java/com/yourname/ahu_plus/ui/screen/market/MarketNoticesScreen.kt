package com.yourname.ahu_plus.ui.screen.market

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourname.ahu_plus.data.model.MarketNotice
import com.yourname.ahu_plus.data.model.MarketNoticeTopic
import com.yourname.ahu_plus.data.model.MarketTopic
import com.yourname.ahu_plus.ui.components.AhuTopAppBar
import com.yourname.ahu_plus.ui.theme.MarketColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MarketNoticesScreen(
    uiState: MarketUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenTopic: (MarketTopic) -> Unit
) {
    val listState = rememberLazyListState()
    val shouldLoadMore by remember(uiState.notices.size, uiState.hasMoreNotices) {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 5
        }
    }

    LaunchedEffect(shouldLoadMore, uiState.notices.size, uiState.hasMoreNotices) {
        if (shouldLoadMore) onLoadMore()
    }

    Scaffold(
        topBar = {
            AhuTopAppBar(
                title = {
                    val countSuffix = if (uiState.noticesCount > 0) "（${uiState.noticesCount}）" else ""
                    Text("消息$countSuffix")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (uiState.noticesLoading && uiState.notices.isEmpty()) {
                item { LoadingRow("正在加载消息...") }
            }

            uiState.noticesError?.let { error ->
                item {
                    StatusCard(text = error, color = MaterialTheme.colorScheme.error) {
                        TextButton(onClick = onRefresh) { Text("重试") }
                    }
                }
            }

            if (!uiState.noticesLoading && uiState.noticesError == null && uiState.notices.isEmpty()) {
                item {
                    StatusCard(
                        text = "暂时没有消息",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(uiState.notices, key = { it.id }) { notice ->
                MarketNoticeCard(
                    notice = notice,
                    onClick = {
                        notice.topic?.let { topic ->
                            onOpenTopic(topic.toMarketTopic())
                        }
                    }
                )
            }

            if (uiState.notices.isNotEmpty()) {
                item {
                    AutoLoadFooter(
                        isLoading = uiState.noticesLoadingMore,
                        hasMore = uiState.hasMoreNotices,
                        loadingText = "正在加载更多...",
                        emptyText = "没有更多消息了"
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun MarketNoticeCard(
    notice: MarketNotice,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(user = notice.senderUserInfo, size = 36.dp)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = notice.senderUserInfo?.nickname?.takeIf { it.isNotBlank() }
                            ?: "匿名同学",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = notice.actionTypeText.ifBlank { defaultActionText(notice) },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                NoticeActionIcon(notice = notice)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = notice.createTime,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            val body = notice.bodyText
            if (body.isNotBlank()) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            notice.topic?.let { topic ->
                val topicTitle = topic.title.takeIf { it.isNotBlank() && it != "none" }
                    ?: topic.content.lineSequence().firstOrNull().orEmpty()
                if (topicTitle.isNotBlank()) {
                    Text(
                        text = "原帖：$topicTitle",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun NoticeActionIcon(notice: MarketNotice) {
    val tint = if (notice.isLike) MarketColors.LikeRed else MaterialTheme.colorScheme.primary
    if (notice.isLike) {
        Icon(
            Icons.Filled.Favorite,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    } else {
        Icon(
            Icons.AutoMirrored.Filled.Reply,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

private fun defaultActionText(notice: MarketNotice): String = when (notice.actionType) {
    2 -> "评论了你的帖子"
    3 -> "回复了你"
    4 -> "点赞了你的主题"
    6 -> "点赞了你的回复"
    else -> if (notice.isLike) "赞了你" else "提到了你"
}

/**
 * 把通知里嵌入的精简主题展开成 `MarketTopic`，交给 ViewModel 进入详情页。
 * 缺省字段（node / isAnon / likeCount 等）由 `loadTopicDetail` 异步补全。
 */
private fun MarketNoticeTopic.toMarketTopic(): MarketTopic =
    MarketTopic(
        id = id,
        title = title,
        content = content,
        imgs = imgs
    )
