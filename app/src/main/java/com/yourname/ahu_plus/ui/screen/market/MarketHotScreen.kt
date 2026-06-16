package com.yourname.ahu_plus.ui.screen.market

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Whatshot
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourname.ahu_plus.data.model.MarketTopic
import com.yourname.ahu_plus.ui.components.AhuTopAppBar
import com.yourname.ahu_plus.ui.theme.MarketColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MarketHotScreen(
    uiState: MarketUiState,
    listState: LazyListState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenTopic: (MarketTopic) -> Unit
) {
    Scaffold(
        topBar = {
            AhuTopAppBar(
                title = { Text("集市热榜") },
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MarketColors.HotFlameBg
                    ),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Whatshot,
                            contentDescription = null,
                            tint = MarketColors.HotFlame
                        )
                        Column(
                            modifier = Modifier.padding(start = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "十大热帖",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MarketColors.HotFlameText
                            )
                            Text(
                                text = "按热度展示近期讨论最多的帖子",
                                style = MaterialTheme.typography.bodySmall,
                                color = MarketColors.HotFlameSubText
                            )
                        }
                    }
                }
            }

            if (uiState.hotLoading && uiState.hotTopics.isEmpty()) {
                item { LoadingRow("正在加载热榜...") }
            }

            uiState.hotError?.let { error ->
                item {
                    StatusCard(text = error, color = MaterialTheme.colorScheme.error) {
                        TextButton(onClick = onRefresh) { Text("重试") }
                    }
                }
            }

            if (!uiState.hotLoading && uiState.hotError == null && uiState.hotTopics.isEmpty()) {
                item {
                    StatusCard(
                        text = "暂时没有热榜内容",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(uiState.hotTopics, key = { it.id }) { topic ->
                HotTopicCard(
                    topic = topic,
                    rank = uiState.hotTopics.indexOf(topic) + 1,
                    onClick = { onOpenTopic(topic) },
                    school = uiState.topicSchoolMap[topic.id]
                )
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }
}
