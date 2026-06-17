package com.yourname.ahu_plus.ui.screen.emptyclassroom

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourname.ahu_plus.data.model.AhuUnitTimes
import com.yourname.ahu_plus.data.repository.FreeRoomResult
import com.yourname.ahu_plus.ui.components.AhuTopAppBar
import com.yourname.ahu_plus.ui.theme.AhuGreen
import com.yourname.ahu_plus.ui.theme.AhuOrange
import com.yourname.ahu_plus.ui.theme.AhuRed
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val FreeGreen = AhuGreen
private val FreeOrange = AhuOrange
private val FreeRed = AhuRed
private val BarBackground = Color(0xFFE0E0E0)
private val PastUnitGray = Color(0xFFBDBDBD)
private val BusyUnitGray = Color(0xFFEEEEEE)

private val dateFormatter = DateTimeFormatter.ofPattern("M月d日")

@Composable
fun EmptyClassroomScreen(
    viewModel: EmptyClassroomViewModel,
    onBack: () -> Unit,
    onNeedsLogin: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.needsLogin) {
        if (uiState.needsLogin) onNeedsLogin()
    }

    Scaffold(
        topBar = {
            AhuTopAppBar(
                title = { Text("空教室查询") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (uiState.hasBuildingSelected) {
                        IconButton(onClick = viewModel::onRefresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                        }
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── 选择区域（始终可见）──────────────────────
            item { SelectorArea(uiState = uiState, viewModel = viewModel) }

            // ── 结果区域 ─────────────────────────────────
            when {
                uiState.isLoading && uiState.rooms.isEmpty() -> {
                    item { CenteredLoader(modifier = Modifier.fillMaxWidth().height(200.dp)) }
                }

                uiState.error != null && uiState.rooms.isEmpty() -> {
                    item {
                        CenteredError(
                            message = uiState.error ?: "加载失败",
                            onRetry = viewModel::onRefresh,
                            modifier = Modifier.fillMaxWidth().height(200.dp)
                        )
                    }
                }

                !uiState.hasBuildingSelected -> {
                    item {
                        CenteredMessage(
                            text = "请选择校区和教学楼",
                            modifier = Modifier.fillMaxWidth().height(200.dp)
                        )
                    }
                }

                uiState.rooms.isEmpty() && !uiState.isLoading -> {
                    item {
                        CenteredMessage(
                            text = if (uiState.currentUnit == null) "今天所有课程已结束"
                            else "当前教学楼暂无空闲教室",
                            modifier = Modifier.fillMaxWidth().height(200.dp)
                        )
                    }
                }

                else -> {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "共 ${uiState.filteredRooms.size} 间空闲教室",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(
                        items = uiState.filteredRooms,
                        key = { it.room.id }
                    ) { result ->
                        RoomResultCard(result = result, currentUnit = uiState.currentUnit)
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

// ── 选择区域 ─────────────────────────────────────────────────

@Composable
private fun SelectorArea(
    uiState: EmptyClassroomUiState,
    viewModel: EmptyClassroomViewModel
) {
    val today = LocalDate.now().format(dateFormatter)
    val unitInfo = if (uiState.currentUnit != null) {
        val timeRange = AhuUnitTimes.formatUnitTime(uiState.currentUnit)
        "当前第 ${uiState.currentUnit} 节 ($timeRange)"
    } else {
        "课程已结束"
    }
    Text(
        text = "$today · $unitInfo",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp)
    )

    // 校区选择
    Text(
        text = "校区",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp)
    )
    Spacer(modifier = Modifier.height(4.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        uiState.availableCampuses.forEach { campus ->
            FilterChip(
                selected = campus.id == uiState.selectedCampusId,
                onClick = { viewModel.selectCampus(campus.id) },
                label = { Text(campus.nameZh) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }

    // 教学楼选择
    if (uiState.selectedCampusId != null && uiState.buildings.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "教学楼",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            uiState.buildings.forEach { building ->
                FilterChip(
                    selected = building.id == uiState.selectedBuildingId,
                    onClick = { viewModel.selectBuilding(building.id) },
                    label = { Text(building.nameZh) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
    }

    // 楼层过滤
    if (uiState.availableFloors.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "楼层",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = uiState.selectedFloor == null,
                onClick = { viewModel.selectFloor(null) },
                label = { Text("全部") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
            uiState.availableFloors.forEach { floor ->
                FilterChip(
                    selected = floor == uiState.selectedFloor,
                    onClick = { viewModel.selectFloor(floor) },
                    label = { Text("${floor}F") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
    }
}

// ── 单间教室卡片 ─────────────────────────────────────────────

@Composable
private fun RoomResultCard(result: FreeRoomResult, currentUnit: Int?) {
    val freeColor = when {
        result.freeUnitsCount >= 4 -> FreeGreen
        result.freeUnitsCount >= 2 -> FreeOrange
        else -> FreeRed
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 第一行：教室名 + 空闲时长标签
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = result.room.nameZh,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                FreeDurationChip(unitCount = result.freeUnitsCount, color = freeColor)
            }

            // 第二行：类型 + 楼层 + 座位
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val roomType = result.room.roomType?.nameZh
                if (!roomType.isNullOrBlank()) {
                    Text(
                        text = roomType,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                result.room.floor?.let { floor ->
                    Text(
                        text = "${floor}F",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                result.room.seats?.let { seats ->
                    if (seats > 0) {
                        Text(
                            text = "${seats}座",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!result.room.remark.isNullOrBlank()) {
                    Text(
                        text = result.room.remark,
                        style = MaterialTheme.typography.bodySmall,
                        color = FreeGreen,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 第三行：空闲时间段文字
            if (result.freeTimeRange.isNotEmpty()) {
                Text(
                    text = result.freeTimeRange,
                    style = MaterialTheme.typography.bodySmall,
                    color = freeColor,
                    fontWeight = FontWeight.Medium
                )
            }

            // 第四行：可视化空闲条
            FreeTimeBar(
                freeUnitNumbers = result.freeUnitNumbers,
                currentUnit = currentUnit,
                freeColor = freeColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            )
        }
    }
}

// ── 空闲时长标签 ─────────────────────────────────────────────

@Composable
private fun FreeDurationChip(unitCount: Int, color: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f))
    ) {
        Text(
            text = "空闲 $unitCount 节",
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// ── 可视化空闲时间条 ─────────────────────────────────────────

@Composable
private fun FreeTimeBar(
    freeUnitNumbers: List<Int>,
    currentUnit: Int?,
    freeColor: Color,
    modifier: Modifier = Modifier
) {
    val totalUnits = AhuUnitTimes.totalUnits()

    Canvas(modifier = modifier) {
        val barWidth = size.width
        val barHeight = size.height
        val unitWidth = barWidth / totalUnits
        val cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())

        for (unit in 1..totalUnits) {
            val x = (unit - 1) * unitWidth
            val isFree = unit in freeUnitNumbers
            val isPast = currentUnit != null && unit < (currentUnit)

            val color = when {
                isPast && !isFree -> PastUnitGray
                isPast && isFree -> freeColor.copy(alpha = 0.3f)
                isFree -> freeColor
                else -> BusyUnitGray
            }

            drawRoundRect(
                color = color,
                topLeft = Offset(x + 1.dp.toPx(), 0f),
                size = Size(unitWidth - 2.dp.toPx(), barHeight),
                cornerRadius = cornerRadius
            )
        }
    }
}

// ── 通用状态组件 ─────────────────────────────────────────────

@Composable
private fun CenteredLoader(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun CenteredError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(text = message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            FilledTonalButton(onClick = onRetry) { Text("重新加载") }
        }
    }
}

@Composable
private fun CenteredMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
