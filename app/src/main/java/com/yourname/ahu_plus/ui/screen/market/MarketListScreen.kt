package com.yourname.ahu_plus.ui.screen.market

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings

import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourname.ahu_plus.data.model.MarketIdentity
import com.yourname.ahu_plus.data.model.MarketTopic
import com.yourname.ahu_plus.ui.components.AhuTopAppBar
import com.yourname.ahu_plus.ui.theme.MarketColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MarketListScreen(
    uiState: MarketUiState,
    listState: LazyListState,
    onIdentityChanged: (String) -> Unit,
    onSaveIdentity: () -> Unit,
    onClearIdentity: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenHot: () -> Unit,
    onOpenTopic: (MarketTopic) -> Unit,
    onOpenCompose: () -> Unit,
    onOpenNotices: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onSearchClose: () -> Unit,
    onToggleSchool: (String, Boolean) -> Unit = { _, _ -> },
    onSelectAllSchools: () -> Unit = {}
) {
    val shouldLoadMore by remember(uiState.topics.size, uiState.hasMoreTopics) {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 5
        }
    }

    LaunchedEffect(shouldLoadMore, uiState.topics.size, uiState.hasMoreTopics) {
        if (shouldLoadMore && !uiState.isSearching) onLoadMore()
    }

    Scaffold(
        topBar = {
            if (uiState.isSearching) {
                AhuTopAppBar(
                    title = {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = onSearchQueryChanged,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp),
                            placeholder = { Text("搜索帖子内容") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onSearchClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        if (uiState.searchQuery.isNotBlank()) {
                            IconButton(onClick = { onSearchQueryChanged("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "清除")
                            }
                        }
                        TextButton(
                            onClick = onSearchSubmit,
                            enabled = uiState.searchQuery.isNotBlank() && !uiState.searchLoading
                        ) {
                            Text(if (uiState.searchLoading) "搜索中" else "搜索")
                        }
                    }
                )
            } else {
                AhuTopAppBar(
                    title = { Text("校园集市") },
                    actions = {
                        if (uiState.hasSavedIdentity) {
                            IconButton(onClick = onOpenSettings) {
                                Icon(Icons.Filled.Settings, contentDescription = "设置")
                            }
                            IconButton(onClick = onOpenNotices) {
                                Icon(Icons.Filled.Notifications, contentDescription = "消息")
                            }
                            IconButton(onClick = onOpenCompose) {
                                Icon(Icons.Filled.Add, contentDescription = "发帖")
                            }
                        }
                    }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (uiState.isSearching) {
            SearchResultList(
                uiState = uiState,
                onOpenTopic = onOpenTopic,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            PullToRefreshBox(
                isRefreshing = uiState.isLoading && uiState.topics.isEmpty(),
                onRefresh = onRefresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (!uiState.hasSavedIdentity) {
                        item {
                            IdentityCard(
                                uiState = uiState,
                                onIdentityChanged = onIdentityChanged,
                                onSave = onSaveIdentity,
                                onClear = onClearIdentity
                            )
                        }
                    } else {
                        if (uiState.identities.size > 1) {
                            item {
                                SchoolSwitcherRow(
                                    identities = uiState.identities,
                                    selectedIds = uiState.selectedIdentityIds,
                                    onToggle = onToggleSchool,
                                    onSelectAll = onSelectAllSchools
                                )
                            }
                        }
                        item { HotEntryCard(onClick = onOpenHot) }
                        item { SearchEntryCard(onClick = onOpenSearch) }
                    }

                    if (uiState.isLoading) {
                        item { LoadingRow("正在加载集市...") }
                    }

                    uiState.error?.let { error ->
                        item {
                            StatusCard(text = error, color = MaterialTheme.colorScheme.error) {
                                TextButton(onClick = onRefresh) { Text("重试") }
                            }
                        }
                    }

                    uiState.saveMessage?.let { message ->
                        item { StatusCard(text = message, color = MarketColors.Success) }
                    }

                    if (uiState.hasSavedIdentity && !uiState.isLoading && uiState.error == null &&
                        uiState.topics.isEmpty()
                    ) {
                        item {
                            StatusCard(
                                text = "暂时没有加载到集市内容",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    items(uiState.topics, key = { it.id }) { topic ->
                        MarketTopicCard(
                            topic = topic,
                            onClick = { onOpenTopic(topic) },
                            school = uiState.topicSchoolMap[topic.id]
                        )
                    }

                    if (uiState.topics.isNotEmpty()) {
                        item {
                            AutoLoadFooter(
                                isLoading = uiState.isLoadingMore,
                                hasMore = uiState.hasMoreTopics,
                                loadingText = "正在加载更多...",
                                emptyText = "没有更多帖子了"
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SearchEntryCard(onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2F80ED).copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = Color(0xFF2F80ED)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "搜索帖子",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "按正文关键词搜索集市内容",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SearchResultList(
    uiState: MarketUiState,
    onOpenTopic: (MarketTopic) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (uiState.searchLoading && uiState.searchResults.isEmpty()) {
            item { LoadingRow("正在搜索 \"${uiState.searchQuery}\" ...") }
        }

        uiState.searchError?.let { error ->
            item { StatusCard(text = error, color = MaterialTheme.colorScheme.error) }
        }

        if (!uiState.searchLoading && uiState.searchError == null &&
            uiState.searchResults.isEmpty() && uiState.searchQuery.isNotBlank()
        ) {
            item {
                StatusCard(
                    text = "没有找到包含 \"${uiState.searchQuery}\" 的帖子",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(uiState.searchResults, key = { it.id }) { topic ->
            MarketTopicCard(
                topic = topic,
                onClick = { onOpenTopic(topic) },
                school = uiState.topicSchoolMap[topic.id]
            )
        }

        if (uiState.searchResults.isNotEmpty()) {
            item {
                StatusCard(
                    text = "共 ${uiState.searchResults.size} 条结果",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item { Spacer(modifier = Modifier.height(72.dp)) }
    }
}

@Composable
private fun SchoolSwitcherRow(
    identities: List<MarketIdentity>,
    selectedIds: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onSelectAll: () -> Unit = {}
) {
    val allSelected = identities.all { it.id in selectedIds }
    val scrollState = rememberScrollState()

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "校区切换",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // "全部" chip
                FilterChip(
                    selected = allSelected,
                    onClick = { if (!allSelected) onSelectAll() },
                    label = { Text("全部校区") },
                    leadingIcon = {
                        if (allSelected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    }
                )
                // Individual school chips
                identities.forEach { identity ->
                    val isSelected = identity.id in selectedIds
                    FilterChip(
                        selected = isSelected,
                        onClick = { onToggle(identity.id, !isSelected) },
                        label = {
                            Text(
                                text = identity.school ?: "未识别校区",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            if (isSelected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun HotEntryCard(onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MarketColors.HotEntryIconBg.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Whatshot,
                    contentDescription = null,
                    tint = MarketColors.HotFlame
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "集市热榜",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "查看近期讨论最热的帖子",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "进入",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
