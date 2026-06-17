package com.yourname.ahu_plus.ui.screen.home

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourname.ahu_plus.data.local.ElectricityRoomConfig
import com.yourname.ahu_plus.data.repository.AdwmhQrCode
import com.yourname.ahu_plus.data.model.InternetBalanceData
import com.yourname.ahu_plus.ui.components.AhuTopAppBar
import com.yourname.ahu_plus.util.BrowserOpener
import com.yourname.ahu_plus.util.QrCodeBitmap
import java.text.DecimalFormat

/**
 * 兼容旧入口的校园卡页面。
 *
 * 新版主流程已迁移到「我的」页,这里保留为独立页面以便后续复用或调试。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onLogout: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AhuTopAppBar(
                title = { Text("校园卡") },
                actions = {
                    IconButton(onClick = viewModel::onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                BalanceSummaryCard(
                    balance = uiState.balance,
                    isLoading = uiState.isLoading,
                    error = uiState.error,
                    onRetry = viewModel::loadBalance
                )
            }
            item {
                CampusQrCodeCard(
                    qrCode = uiState.qrCode,
                    balance = uiState.qrBalance,
                    isLoading = uiState.qrLoading,
                    error = uiState.qrError,
                    authUrl = viewModel.getAdwmhAuthStartUrl(),
                    onAuthorize = viewModel::importAdwmhSession,
                    onRefresh = viewModel::loadCampusQrCode
                )
            }
            item {
                BathroomBalanceCard(
                    data = uiState.bathroomData,
                    isLoading = uiState.bathroomLoading,
                    error = uiState.bathroomError,
                    phone = uiState.bathroomPhone,
                    onSavePhone = viewModel::saveBathroomPhone,
                    onRetry = viewModel::loadBathroomBalance
                )
            }
            item {
                ElectricityBalanceCard(
                    label = "空调余额",
                    state = uiState.ac,
                    onSaveConfig = viewModel::saveAcConfig,
                    onRetry = viewModel::loadAcBalance
                )
            }
            item {
                ElectricityBalanceCard(
                    label = "照明余额",
                    state = uiState.lighting,
                    onSaveConfig = viewModel::saveLightingConfig,
                    onRetry = viewModel::loadLightingBalance
                )
            }
            item {
                InternetBalanceCard(
                    data = uiState.internetData,
                    isLoading = uiState.internetLoading,
                    error = uiState.internetError,
                    onRetry = viewModel::loadInternetBalance
                )
            }
            item {
                Text(
                    text = "最近账单：${uiState.bills.size} 条",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                val billsError = uiState.billsError
                if (billsError != null) {
                    Text(
                        text = billsError,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    TextButton(onClick = viewModel::loadBills) {
                        Text("重试")
                    }
                }
            }
        }
    }
}

@Composable
fun CampusQrCodeCard(
    qrCode: AdwmhQrCode?,
    balance: Double?,
    isLoading: Boolean,
    error: String?,
    authUrl: String,
    onAuthorize: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    var openError by remember { mutableStateOf<String?>(null) }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "校园码",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            when {
                isLoading && qrCode == null -> CircularProgressIndicator()
                qrCode != null -> {
                    val image = remember(qrCode.payload) {
                        QrCodeBitmap.create(qrCode.payload, 720)
                    }
                    Image(
                        bitmap = image,
                        contentDescription = "校园码",
                        modifier = Modifier.size(220.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (balance != null) {
                        Text(
                            text = "余额 ${DecimalFormat("¥#,##0.00").format(balance)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = qrCode.serverTimeText.ifBlank { "已刷新" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    if (isLoading) {
                        Spacer(modifier = Modifier.height(6.dp))
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                }
                else -> {
                    Text(
                        text = openError ?: error ?: "请在微信内打开智慧安大校园码",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.Center) {
                TextButton(
                    onClick = {
                        openError = null
                        val opened = BrowserOpener.shareTextToWeChat(context, authUrl)
                        if (!opened) {
                            openError = "未能分享到微信，请确认已安装微信并允许分享。"
                        }
                    }
                ) {
                    Text("分享到微信")
                }
                TextButton(onClick = onRefresh) {
                    Text("刷新")
                }
            }
        }
    }
}

@Composable
private fun BalanceSummaryCard(
    balance: Double,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading && balance == 0.0 -> CircularProgressIndicator()
                error != null && balance == 0.0 -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRetry) {
                            Text("重新查询")
                        }
                    }
                }
                else -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "校园卡余额",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = DecimalFormat("¥#,##0.00").format(balance),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BathroomBalanceCard(
    data: com.yourname.ahu_plus.data.model.BathroomBalanceData?,
    isLoading: Boolean,
    error: String?,
    phone: String,
    onSavePhone: (String) -> Unit,
    onRetry: () -> Unit
) {
    var showPhoneDialog by remember { mutableStateOf(false) }

    if (showPhoneDialog) {
        PhoneInputDialog(
            currentPhone = phone,
            onDismiss = { showPhoneDialog = false },
            onConfirm = { newPhone ->
                onSavePhone(newPhone)
                showPhoneDialog = false
            }
        )
    }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                phone.isBlank() -> {
                    // 未设置手机号
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "浴室余额",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "点击设置手机号以查询",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { showPhoneDialog = true }) {
                            Text("设置手机号")
                        }
                    }
                }
                isLoading && data == null -> {
                    CircularProgressIndicator()
                }
                error != null && data == null -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        Row {
                            TextButton(onClick = { showPhoneDialog = true }) {
                                Text("修改手机号")
                            }
                            TextButton(onClick = onRetry) {
                                Text("重试")
                            }
                        }
                    }
                }
                data != null -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = data.projectName.ifBlank { "浴室余额" },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = DecimalFormat("¥#,##0.00").format(data.cashYuan),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        if (data.bonusYuan > 0) {
                            Text(
                                text = "赠送 ${DecimalFormat("¥#,##0.00").format(data.bonusYuan)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row {
                            Text(
                                text = "手机 ${data.telPhone}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = { showPhoneDialog = true },
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("修改", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (isLoading) {
                            Spacer(modifier = Modifier.height(4.dp))
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneInputDialog(
    currentPhone: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var input by remember { mutableStateOf(currentPhone) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置浴室手机号") },
        text = {
            Column {
                Text(
                    text = "请输入在浴室水控系统中绑定的手机号（11位）",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { if (it.length <= 11) input = it },
                    label = { Text("手机号") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(input.trim()) },
                enabled = input.trim().length == 11
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

// ─── 电费卡片 (空调/照明通用) ──────────────────────────────

@Composable
fun ElectricityBalanceCard(
    label: String,
    state: ElectricityState,
    onSaveConfig: (ElectricityRoomConfig) -> Unit,
    onRetry: () -> Unit
) {
    var showConfigDialog by remember { mutableStateOf(false) }

    if (showConfigDialog) {
        RoomConfigDialog(
            title = "设置${label}房间",
            currentConfig = state.config,
            onDismiss = { showConfigDialog = false },
            onConfirm = { config ->
                onSaveConfig(config)
                showConfigDialog = false
            }
        )
    }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                !state.config.isComplete -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "点击设置房间信息以查询",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { showConfigDialog = true }) {
                            Text("设置房间")
                        }
                    }
                }
                state.loading && state.data == null -> {
                    CircularProgressIndicator()
                }
                state.error != null && state.data == null -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error, color = MaterialTheme.colorScheme.error)
                        Row {
                            TextButton(onClick = { showConfigDialog = true }) {
                                Text("修改房间")
                            }
                            TextButton(onClick = onRetry) {
                                Text("重试")
                            }
                        }
                    }
                }
                state.data != null -> {
                    val kwh = state.data.remainingKwh
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.data.buildingName.ifBlank { label },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (kwh != null) {
                            Text(
                                text = "${DecimalFormat("#,##0.00").format(kwh)} 度",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        } else {
                            Text(
                                text = state.data.infoText.ifBlank { "未知" },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row {
                            Text(
                                text = "${state.data.roomName} · ${state.data.floorName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = { showConfigDialog = true },
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("修改", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (state.loading) {
                            Spacer(modifier = Modifier.height(4.dp))
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomConfigDialog(
    title: String,
    currentConfig: ElectricityRoomConfig,
    onDismiss: () -> Unit,
    onConfirm: (ElectricityRoomConfig) -> Unit
) {
    var building by remember { mutableStateOf(currentConfig.building) }
    var floor by remember { mutableStateOf(currentConfig.floor) }
    var room by remember { mutableStateOf(currentConfig.room) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    "楼栋/楼层: 需从抓包获取完整名（如\"57&榴园二号楼空调\"）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = building,
                    onValueChange = { building = it },
                    label = { Text("楼栋 (code&name)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = floor,
                    onValueChange = { floor = it },
                    label = { Text("楼层 (code&name)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("房间 (如 2514&2514)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // 自动从学生表预填 room 提示
                Text(
                    "提示: 房间号可从「我的信息」→「住宿数据」中查",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(ElectricityRoomConfig(building.trim(), floor.trim(), room.trim()))
                },
                enabled = ElectricityRoomConfig(
                    building = building.trim(),
                    floor = floor.trim(),
                    room = room.trim()
                ).isComplete
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

// ─── 网费卡片 ──────────────────────────────────────────────

@Composable
fun InternetBalanceCard(
    data: InternetBalanceData?,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading && data == null -> {
                    CircularProgressIndicator()
                }
                error != null && data == null -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRetry) { Text("重试") }
                    }
                }
                data != null -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "网费余额",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${DecimalFormat("#,##0.00").format(data.balanceYuan)} 元",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val details = buildString {
                            if (data.usedFlowMb > 0) append("流量 ${formatFlow(data.usedFlowMb)}")
                            if (data.usedTimeMinutes > 0L) {
                                if (isNotEmpty()) append(" · ")
                                append("时长 ${formatMinutes(data.usedTimeMinutes)}")
                            }
                        }
                        if (details.isNotEmpty()) {
                            Text(
                                text = "本期: $details",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        Text(
                            text = "账号 ${data.account}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                        )
                        if (isLoading) {
                            Spacer(modifier = Modifier.height(4.dp))
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}

private fun formatFlow(mb: Double): String {
    return if (mb >= 1024) "${DecimalFormat("#,##0.0").format(mb / 1024)} GB" else "${DecimalFormat("#,##0").format(mb)} MB"
}

private fun formatMinutes(minutes: Long): String {
    return if (minutes >= 60) "${minutes / 60}h${minutes % 60}m" else "${minutes}m"
}
