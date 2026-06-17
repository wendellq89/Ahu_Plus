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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourname.ahu_plus.data.model.MarketIdentity
import com.yourname.ahu_plus.data.remote.market.MarketApi
import com.yourname.ahu_plus.ui.components.AhuTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketSettingsScreen(
    uiState: MarketUiState,
    onBack: () -> Unit,
    onIdentityChanged: (String) -> Unit,
    onAddIdentity: () -> Unit,
    onClearIdentities: () -> Unit,
    onRemoveIdentity: (String) -> Unit,
    onToggleIdentitySelection: (String, Boolean) -> Unit,
    onBlockPinnedChanged: (Boolean) -> Unit,
    onKeywordInputChanged: (String) -> Unit,
    onAddKeyword: () -> Unit,
    onRemoveKeyword: (String) -> Unit,
    onToggleFilterNode: (Long) -> Unit,
    onListLayoutModeChange: (String) -> Unit = {}
) {
    Scaffold(
        topBar = {
            AhuTopAppBar(
                title = { Text("集市设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── 1. 身份管理 ──────────────────────────
            item { SettingsSectionTitle("身份管理") }
            item {
                CompactIdentityCard(
                    uiState = uiState,
                    onIdentityChanged = onIdentityChanged,
                    onAddIdentity = onAddIdentity,
                    onRemoveIdentity = {
                        // 移除第一个身份(紧凑卡只显示一个)
                        uiState.identities.firstOrNull()?.let { onRemoveIdentity(it.id) }
                    }
                )
            }

            if (uiState.identities.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "已保存的校区身份（${uiState.identities.size} 个）",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            uiState.identities.forEachIndexed { index, identity ->
                                if (index > 0) HorizontalDivider()
                                SavedIdentityRow(
                                    identity = identity,
                                    isSelected = identity.id in uiState.selectedIdentityIds,
                                    onToggle = { checked ->
                                        onToggleIdentitySelection(identity.id, checked)
                                    },
                                    onRemove = { onRemoveIdentity(identity.id) }
                                )
                            }
                        }
                    }
                }
            }

            // ── 2. 屏蔽置顶 ──────────────────────────
            item { SettingsSectionTitle("屏蔽置顶") }
            item {
                SettingsToggleCard(
                    title = "屏蔽置顶帖",
                    description = "开启后隐藏置顶/广告帖子",
                    checked = uiState.blockPinned,
                    onCheckedChange = onBlockPinnedChanged
                )
            }

            // ── 2.5 列表显示模式 ────────────────────────────
            item { SettingsSectionTitle("列表显示模式") }
            item {
                ListLayoutModeCard(
                    currentMode = uiState.listLayoutMode,
                    onSelect = onListLayoutModeChange
                )
            }

            // ── 3. 屏蔽词 ────────────────────────────
            item { SettingsSectionTitle("屏蔽词") }
            item {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "帖子标题或正文包含以下关键词时将被隐藏",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = uiState.keywordInput,
                                onValueChange = onKeywordInputChanged,
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("输入屏蔽词") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = onAddKeyword) {
                                Text("添加")
                            }
                        }
                        if (uiState.blockKeywords.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                uiState.blockKeywords.forEach { kw ->
                                    KeywordChip(
                                        keyword = kw,
                                        onRemove = { onRemoveKeyword(kw) }
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "暂无屏蔽词",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ── 4. 板块筛选 ──────────────────────────
            item { SettingsSectionTitle("板块筛选") }
            item {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "仅显示所选板块的帖子（未选中则显示全部）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        MarketApi.DEFAULT_NODES.forEach { node ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleFilterNode(node.id) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = node.id in uiState.filterNodeIds,
                                    onCheckedChange = { onToggleFilterNode(node.id) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = node.name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun SettingsToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SavedIdentityRow(
    identity: MarketIdentity,
    isSelected: Boolean,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = isSelected, onCheckedChange = onToggle)
        Spacer(modifier = Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = identity.school ?: "未识别校区",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = maskToken(identity.token),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Filled.DeleteOutline,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun KeywordChip(keyword: String, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = keyword,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = "删除",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun ListLayoutModeCard(
    currentMode: String,
    onSelect: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "列表显示模式",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "单列文字优先 / 小红书双列瀑布流(大图+标题)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = currentMode == "list",
                    onClick = { onSelect("list") },
                    label = { Text("单列列表") },
                    leadingIcon = {
                        if (currentMode == "list") {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    }
                )
                FilterChip(
                    selected = currentMode == "stagger",
                    onClick = { onSelect("stagger") },
                    label = { Text("双列瀑布") },
                    leadingIcon = {
                        if (currentMode == "stagger") {
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
