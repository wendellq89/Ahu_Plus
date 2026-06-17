package com.yourname.ahu_plus.ui.screen.profile

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourname.ahu_plus.data.local.AppThemeMode
import com.yourname.ahu_plus.data.model.AttendanceRecord
import com.yourname.ahu_plus.data.model.BillRecord
import com.yourname.ahu_plus.data.model.FinanceSummary
import com.yourname.ahu_plus.data.model.StudentInfo
import com.yourname.ahu_plus.data.model.StudentInfoCodeLookup
import com.yourname.ahu_plus.data.model.StudentInfoField
import com.yourname.ahu_plus.data.repository.AdwmhQrCode
import com.yourname.ahu_plus.ui.components.AhuInfoRow
import com.yourname.ahu_plus.ui.components.AhuShapes
import com.yourname.ahu_plus.data.local.ElectricityRoomConfig
import com.yourname.ahu_plus.data.model.ElectricityDailyRecord
import com.yourname.ahu_plus.data.model.ElectricityUiData
import com.yourname.ahu_plus.ui.screen.home.BathroomBalanceCard
import com.yourname.ahu_plus.data.model.InternetBalanceData
import com.yourname.ahu_plus.data.model.InternetBillRecord
import com.yourname.ahu_plus.ui.screen.home.ElectricityBalanceCard
import com.yourname.ahu_plus.ui.screen.home.ElectricityBillRange
import com.yourname.ahu_plus.ui.screen.home.ElectricityState
import com.yourname.ahu_plus.ui.screen.home.HomeViewModel
import com.yourname.ahu_plus.ui.screen.home.InternetBalanceCard
import com.yourname.ahu_plus.ui.screen.market.MarketIdentityEditor
import com.yourname.ahu_plus.ui.screen.market.MarketSettingsScreen
import com.yourname.ahu_plus.ui.screen.market.MarketViewModel
import com.yourname.ahu_plus.ui.screen.schedule.ScheduleUiState
import com.yourname.ahu_plus.ui.theme.AhuGreen
import com.yourname.ahu_plus.util.BrowserOpener
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun ProfileScreen(
    cardViewModel: HomeViewModel,
    marketViewModel: MarketViewModel,
    studentInfoViewModel: StudentInfoViewModel,
    financeViewModel: FinanceViewModel,
    attendanceViewModel: AttendanceViewModel,
    scheduleUiState: ScheduleUiState,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    /** 2026-06-17 Bug2: 近期任务全局设置 */
    showCompletedTasks: Boolean = false,
    showCompletedExams: Boolean = true,
    onShowCompletedTasksChanged: (Boolean) -> Unit = {},
    onShowCompletedExamsChanged: (Boolean) -> Unit = {},
    scrollTarget: String? = null,
    onScrollTargetConsumed: () -> Unit = {},
    profileSubPage: String? = null,
    onProfileSubPageConsumed: () -> Unit = {},
    openCardAnalytics: Boolean = false,
    onCardAnalyticsConsumed: () -> Unit = {},
    onLogout: () -> Unit
) {
    var showBills by rememberSaveable { mutableStateOf(false) }
    var showMarketSettings by rememberSaveable { mutableStateOf(false) }
    var showMyInfoHub by rememberSaveable { mutableStateOf(false) }
    var showStudentBasicInfo by rememberSaveable { mutableStateOf(false) }
    var showHousingInfo by rememberSaveable { mutableStateOf(false) }
    var showAcademicWarning by rememberSaveable { mutableStateOf(false) }
    var showFinance by rememberSaveable { mutableStateOf(false) }
    var showAttendance by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showUtilities by rememberSaveable { mutableStateOf(false) }
    var showCardAnalytics by rememberSaveable { mutableStateOf(false) }
    val cardUiState by cardViewModel.uiState.collectAsStateWithLifecycle()
    val marketUiState by marketViewModel.uiState.collectAsStateWithLifecycle()

    fun openUtility(target: String) {
        showUtilities = target == "bathroom" || target == "ac" || target == "lighting" || target == "internet"
    }

    val studentInfoUiState by studentInfoViewModel.uiState.collectAsStateWithLifecycle()
    val financeUiState by financeViewModel.uiState.collectAsStateWithLifecycle()
    val attendanceUiState by attendanceViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(studentInfoUiState.info) {
        cardViewModel.applyStudentInfoPrefill(studentInfoUiState.info)
    }

    LaunchedEffect(scrollTarget) {
        val target = scrollTarget ?: return@LaunchedEffect
        if (target == "bathroom" || target == "ac" || target == "lighting" || target == "internet") {
            openUtility(target)
            onScrollTargetConsumed()
        }
    }

    LaunchedEffect(profileSubPage) {
        when (profileSubPage) {
            "myInfoHub" -> showMyInfoHub = true
            "finance" -> showFinance = true
            "attendance" -> showAttendance = true
            "settings" -> showSettings = true
            "cardAnalytics" -> showCardAnalytics = true
        }
        if (profileSubPage != null) {
            onProfileSubPageConsumed()
        }
    }

    LaunchedEffect(openCardAnalytics) {
        if (openCardAnalytics) {
            showCardAnalytics = true
            onCardAnalyticsConsumed()
        }
    }

    if (showCardAnalytics) {
        BackHandler(enabled = true) { showCardAnalytics = false }
        CardAnalyticsScreen(
            bills = cardUiState.bills,
            isLoading = cardUiState.billsLoading,
            error = cardUiState.billsError,
            onBack = { showCardAnalytics = false },
            onRefresh = cardViewModel::onRefresh
        )
    } else if (showBills) {
        BackHandler(enabled = true) { showBills = false }
        BillDetailScreen(
            bills = cardUiState.bills,
            isLoading = cardUiState.billsLoading,
            error = cardUiState.billsError,
            onBack = { showBills = false },
            onRefresh = cardViewModel::onRefresh,
            onOpenAnalytics = { showCardAnalytics = true }
        )
    } else if (showUtilities) {
        BackHandler(enabled = true) { showUtilities = false }
        WaterElectricityUtilityDetailScreen(
            bathroomData = cardUiState.bathroomData,
            bathroomLoading = cardUiState.bathroomLoading,
            bathroomError = cardUiState.bathroomError,
            bathroomPhone = cardUiState.bathroomPhone,
            acState = cardUiState.ac,
            acBills = cardUiState.acBills,
            acBillRange = cardUiState.acBillRange,
            acBillsLoading = cardUiState.acBillsLoading,
            acBillsError = cardUiState.acBillsError,
            lightingState = cardUiState.lighting,
            lightingBills = cardUiState.lightingBills,
            lightingBillRange = cardUiState.lightingBillRange,
            lightingBillsLoading = cardUiState.lightingBillsLoading,
            lightingBillsError = cardUiState.lightingBillsError,
            internetData = cardUiState.internetData,
            internetLoading = cardUiState.internetLoading,
            internetError = cardUiState.internetError,
            internetBills = cardUiState.internetBills,
            internetBillsLoading = cardUiState.internetBillsLoading,
            internetBillsError = cardUiState.internetBillsError,
            onBack = { showUtilities = false },
            onSaveBathroomPhone = cardViewModel::saveBathroomPhone,
            onSaveAcConfig = cardViewModel::saveAcConfig,
            onSaveLightingConfig = cardViewModel::saveLightingConfig,
            onRefreshBathroom = cardViewModel::loadBathroomBalance,
            onRefreshAcBalance = cardViewModel::loadAcBalance,
            onRefreshLightingBalance = cardViewModel::loadLightingBalance,
            onRefreshInternetBalance = cardViewModel::loadInternetBalance,
            onRefreshAcBills = { cardViewModel.loadAcBills() },
            onRefreshLightingBills = { cardViewModel.loadLightingBills() },
            onRefreshInternetBills = cardViewModel::loadInternetBills,
            onAcBillRangeSelected = cardViewModel::setAcBillRange,
            onLightingBillRangeSelected = cardViewModel::setLightingBillRange
        )
    } else if (showMarketSettings) {
        BackHandler(enabled = true) { showMarketSettings = false }
        MarketSettingsScreen(
            uiState = marketUiState,
            onBack = { showMarketSettings = false },
            onIdentityChanged = marketViewModel::onIdentityInputChanged,
            onAddIdentity = marketViewModel::saveIdentity,
            onClearIdentities = marketViewModel::clearIdentity,
            onRemoveIdentity = marketViewModel::removeIdentity,
            onToggleIdentitySelection = marketViewModel::toggleIdentitySelection,
            onBlockPinnedChanged = marketViewModel::setBlockPinned,
            onKeywordInputChanged = marketViewModel::onKeywordInputChanged,
            onAddKeyword = marketViewModel::addBlockKeyword,
            onRemoveKeyword = marketViewModel::removeBlockKeyword,
            onToggleFilterNode = marketViewModel::toggleFilterNode
        )
    } else if (showStudentBasicInfo) {
        BackHandler(enabled = true) { showStudentBasicInfo = false; showMyInfoHub = true }
        CategoryDetailScreen(
            title = "学生基本信息",
            fields = studentInfoUiState.info?.basicFields ?: emptyList(),
            isLoading = studentInfoUiState.isLoading,
            error = studentInfoUiState.error,
            lastUpdatedAt = studentInfoUiState.lastUpdatedAt,
            onBack = { showStudentBasicInfo = false; showMyInfoHub = true },
            onRefresh = studentInfoViewModel::refreshStudentInfo
        )
    } else if (showHousingInfo) {
        BackHandler(enabled = true) { showHousingInfo = false; showMyInfoHub = true }
        CategoryDetailScreen(
            title = "住宿数据",
            fields = studentInfoUiState.info?.housingFields ?: emptyList(),
            isLoading = studentInfoUiState.isLoading,
            error = studentInfoUiState.error,
            lastUpdatedAt = studentInfoUiState.lastUpdatedAt,
            onBack = { showHousingInfo = false; showMyInfoHub = true },
            onRefresh = studentInfoViewModel::refreshStudentInfo
        )
    } else if (showAcademicWarning) {
        BackHandler(enabled = true) { showAcademicWarning = false; showMyInfoHub = true }
        CategoryDetailScreen(
            title = "学业预警信息",
            fields = studentInfoUiState.info?.academicWarningFields ?: emptyList(),
            isLoading = studentInfoUiState.isLoading,
            error = studentInfoUiState.error,
            lastUpdatedAt = studentInfoUiState.lastUpdatedAt,
            onBack = { showAcademicWarning = false; showMyInfoHub = true },
            onRefresh = studentInfoViewModel::refreshStudentInfo
        )
    } else if (showFinance) {
        BackHandler(enabled = true) { showFinance = false; showMyInfoHub = true }
        FinanceDetailScreen(
            uiState = financeUiState,
            onBack = { showFinance = false; showMyInfoHub = true },
            onRefresh = financeViewModel::refreshFinance
        )
    } else if (showAttendance) {
        BackHandler(enabled = true) { showAttendance = false; showMyInfoHub = true }
        AttendanceListScreen(
            uiState = attendanceUiState,
            onBack = { showAttendance = false; showMyInfoHub = true },
            onRefresh = attendanceViewModel::refreshAttendance
        )
    } else if (showMyInfoHub) {
        BackHandler(enabled = true) { showMyInfoHub = false }
        MyInfoHubScreen(
            studentInfoUiState = studentInfoUiState,
            financeUiState = financeUiState,
            attendanceUiState = attendanceUiState,
            onBack = { showMyInfoHub = false },
            onRefreshAll = {
                studentInfoViewModel.refreshStudentInfo()
                financeViewModel.refreshFinance()
                attendanceViewModel.refreshAttendance()
            },
            onOpenBasicInfo = { showStudentBasicInfo = true },
            onOpenHousing = { showHousingInfo = true },
            onOpenAcademicWarning = { showAcademicWarning = true },
            onOpenFinance = { showFinance = true },
            onOpenAttendance = { showAttendance = true }
        )
    } else if (showSettings) {
        BackHandler(enabled = true) { showSettings = false }
        AppSettingsScreen(
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            marketEnabled = marketUiState.marketEnabled,
            onMarketEnabledChanged = marketViewModel::setMarketEnabled,
            // 2026-06-17 Bug2: 从 scheduleUiState 获取 (已通过 ViewModel 反应)
            showCompletedTasks = scheduleUiState.showCompletedTasks,
            showCompletedExams = scheduleUiState.showCompletedExams,
            onShowCompletedTasksChanged = onShowCompletedTasksChanged,
            onShowCompletedExamsChanged = onShowCompletedExamsChanged,
            onBack = { showSettings = false }
        )
    } else {
        val studentInfo = studentInfoUiState.info
        ProfileHomeScreen(
            studentName = studentInfo?.displayName() ?: scheduleUiState.studentName,
            department = studentInfo?.department() ?: scheduleUiState.department,
            className = studentInfo?.classOrMajor() ?: scheduleUiState.className,
            hasStudentInfo = studentInfo != null,
            financeItemCount = financeUiState.summary?.let { s ->
                s.scholarship.size + s.grant.size + s.hardshipGrant.size +
                    s.workStudy.size + s.loan.size + s.arrearsStatus.size
            } ?: 0,
            attendanceTotal = attendanceUiState.summary?.total ?: 0,
            balance = cardUiState.balance,
            balanceLoading = cardUiState.isLoading,
            balanceError = cardUiState.error,
            timestamp = cardUiState.timestamp,
            qrCode = cardUiState.qrCode,
            qrBalance = cardUiState.qrBalance,
            qrLoading = cardUiState.qrLoading,
            qrError = cardUiState.qrError,
            qrAuthUrl = cardViewModel.getAdwmhAuthStartUrl(),
            onAuthorizeQr = cardViewModel::importAdwmhSession,
            onRefreshQr = cardViewModel::loadCampusQrCode,
            identityCount = marketUiState.identities.size,
            hasMarketIdentity = marketUiState.hasSavedIdentity,
            marketEnabled = marketUiState.marketEnabled,
            bathroomData = cardUiState.bathroomData,
            bathroomLoading = cardUiState.bathroomLoading,
            bathroomError = cardUiState.bathroomError,
            bathroomPhone = cardUiState.bathroomPhone,
            onSaveBathroomPhone = cardViewModel::saveBathroomPhone,
            onRetryBathroom = cardViewModel::loadBathroomBalance,
            acState = cardUiState.ac,
            lightingState = cardUiState.lighting,
            onSaveAcConfig = cardViewModel::saveAcConfig,
            onSaveLightingConfig = cardViewModel::saveLightingConfig,
            onRetryAcBalance = cardViewModel::loadAcBalance,
            onRetryLightingBalance = cardViewModel::loadLightingBalance,
            internetData = cardUiState.internetData,
            internetLoading = cardUiState.internetLoading,
            internetError = cardUiState.internetError,
            onRetryInternet = cardViewModel::loadInternetBalance,
            onRefresh = { cardViewModel.onRefresh() },
            onOpenBills = { showBills = true },
            onOpenBathroom = { openUtility("bathroom") },
            onOpenAc = { openUtility("ac") },
            onOpenLighting = { openUtility("lighting") },
            onOpenInternet = { openUtility("internet") },
            onOpenMyInfoHub = { showMyInfoHub = true },
            onOpenMarketSettings = { showMarketSettings = true },
            themeMode = themeMode,
            onOpenSettings = { showSettings = true },
            onLogout = onLogout
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileHomeScreen(
    studentName: String?,
    department: String?,
    className: String?,
    hasStudentInfo: Boolean,
    financeItemCount: Int,
    attendanceTotal: Int,
    balance: Double,
    balanceLoading: Boolean,
    balanceError: String?,
    timestamp: Long,
    qrCode: AdwmhQrCode?,
    qrBalance: Double?,
    qrLoading: Boolean,
    qrError: String?,
    qrAuthUrl: String,
    onAuthorizeQr: (String) -> Unit,
    onRefreshQr: () -> Unit,
    identityCount: Int,
    hasMarketIdentity: Boolean,
    marketEnabled: Boolean,
    // 浴室余额
    bathroomData: com.yourname.ahu_plus.data.model.BathroomBalanceData?,
    bathroomLoading: Boolean,
    bathroomError: String?,
    bathroomPhone: String,
    onSaveBathroomPhone: (String) -> Unit,
    onRetryBathroom: () -> Unit,
    // 电费
    acState: ElectricityState,
    lightingState: ElectricityState,
    onSaveAcConfig: (ElectricityRoomConfig) -> Unit,
    onSaveLightingConfig: (ElectricityRoomConfig) -> Unit,
    onRetryAcBalance: () -> Unit,
    onRetryLightingBalance: () -> Unit,
    // 网费
    internetData: InternetBalanceData?,
    internetLoading: Boolean,
    internetError: String?,
    onRetryInternet: () -> Unit,
    onRefresh: () -> Unit,
    onOpenBills: () -> Unit,
    onOpenBathroom: () -> Unit,
    onOpenAc: () -> Unit,
    onOpenLighting: () -> Unit,
    onOpenInternet: () -> Unit,
    onOpenMyInfoHub: () -> Unit,
    onOpenMarketSettings: () -> Unit,
    themeMode: AppThemeMode,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit
) {
    val displayName = studentName?.takeIf { it.isNotBlank() } ?: "未命名同学"
    val subtitle = listOfNotNull(
        department?.takeIf { it.isNotBlank() },
        className?.takeIf { it.isNotBlank() }
    ).joinToString(" · ").ifBlank { "学生信息接口待接入" }
    var showLogoutConfirm by rememberSaveable { mutableStateOf(false) }
    var showDeveloperContact by rememberSaveable { mutableStateOf(false) }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("确认退出登录？") },
            text = { Text("退出后会清除本地凭据与登录会话，需要重新登录后继续使用。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirm = false
                        onLogout()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("确认退出")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showDeveloperContact) {
        DeveloperContactDialog(onDismiss = { showDeveloperContact = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的") },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ProfileHeader(
                    displayName = displayName,
                    subtitle = subtitle
                )
            }

            item {
                BalanceCard(
                    balance = balance,
                    isLoading = balanceLoading,
                    error = balanceError,
                    timestamp = timestamp,
                    qrAuthUrl = qrAuthUrl,
                    onClick = onOpenBills
                )
            }

            item {
                ProfileSection {
                    SettingsRow(
                        title = "水电费查询",
                        description = "浴室、空调、照明、网费余额",
                        iconColor = Color(0xFF2F80ED),
                        icon = { Icon(Icons.Filled.WaterDrop, contentDescription = null) },
                        onClick = onOpenBathroom
                    )
                }
            }

            item {
                ProfileSection {
                    SettingsRow(
                        title = "我的信息",
                        description = buildMyInfoDescription(hasStudentInfo, financeItemCount, attendanceTotal),
                        iconColor = Color(0xFF2F80ED),
                        icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                        onClick = onOpenMyInfoHub
                    )
                    if (marketEnabled) {
                        HorizontalDivider()
                        SettingsRow(
                            title = "集市设置",
                            description = if (hasMarketIdentity) {
                                "已配置 $identityCount 个校区，点击管理"
                            } else {
                                "添加校园集市身份，支持多校区"
                            },
                            iconColor = Color(0xFFB7791F),
                            icon = { Icon(Icons.Filled.Storefront, contentDescription = null) },
                            onClick = onOpenMarketSettings
                        )
                    }
                    HorizontalDivider()
                    SettingsRow(
                        title = "设置",
                        description = "外观：${themeMode.titleText()}",
                        iconColor = Color(0xFF6C63FF),
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        onClick = onOpenSettings
                    )
                    HorizontalDivider()
                    SettingsRow(
                        title = "退出登录",
                        description = "清除本地凭据与登录会话",
                        iconColor = MaterialTheme.colorScheme.error,
                        icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                        onClick = { showLogoutConfirm = true }
                    )
                }
            }

            item {
                Text(
                    text = "联系开发者",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDeveloperContact = true }
                        .padding(vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = Color(0xFF2F80ED)
                )
            }

            item {
                Spacer(modifier = Modifier.height(64.dp))
            }
        }
    }
}

private fun buildMyInfoDescription(
    hasStudentInfo: Boolean,
    financeItemCount: Int,
    attendanceTotal: Int
): String {
    val parts = mutableListOf<String>()
    if (hasStudentInfo) parts.add("基本信息")
    if (financeItemCount > 0) parts.add("财务")
    if (attendanceTotal > 0) parts.add("考勤")
    return if (parts.isEmpty()) "学生基本信息、住宿、财务、考勤等" else parts.joinToString("、") + "等"
}

@Composable
private fun DeveloperContactDialog(onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    fun copy(text: String) {
        clipboard.setText(AnnotatedString(text))
        Toast.makeText(context, "已复制到剪切板", Toast.LENGTH_SHORT).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("联系开发者") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ContactMethodRow(
                    title = "加入 QQ 讨论群",
                    value = "483448621",
                    onCopy = { copy("483448621") }
                )
                HorizontalDivider()
                ContactMethodRow(
                    title = "给开发者发邮件",
                    value = "2867299793@qq.com",
                    onCopy = { copy("2867299793@qq.com") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun ContactMethodRow(
    title: String,
    value: String,
    onCopy: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onCopy) {
            Text("复制")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppSettingsScreen(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    marketEnabled: Boolean,
    onMarketEnabledChanged: (Boolean) -> Unit,
    /** 2026-06-17 Bug2: 近期任务全局设置 */
    showCompletedTasks: Boolean = false,
    showCompletedExams: Boolean = true,
    onShowCompletedTasksChanged: (Boolean) -> Unit = {},
    onShowCompletedExamsChanged: (Boolean) -> Unit = {},
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
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
            item {
                ProfileSection {
                    Column {
                        Text(
                            text = "外观",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        AppThemeMode.entries.forEachIndexed { index, option ->
                            if (index > 0) HorizontalDivider()
                            ThemeModeRow(
                                themeMode = option,
                                selected = themeMode == option,
                                onClick = { onThemeModeChange(option) }
                            )
                        }
                    }
                }
            }
            item {
                ProfileSection {
                    Column {
                        Text(
                            text = "功能",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        SettingsSwitchRow(
                            title = "启用集市功能",
                            description = if (marketEnabled) {
                                "关闭后底部导航将隐藏「集市」Tab"
                            } else {
                                "已关闭，启用后恢复「集市」Tab"
                            },
                            checked = marketEnabled,
                            onCheckedChange = onMarketEnabledChanged
                        )
                        HorizontalDivider()
                        SettingsSwitchRow(
                            title = "显示已完成考试",
                            description = if (showCompletedExams) {
                                "近期任务中展示已结束的考试"
                            } else {
                                "近期任务中隐藏已考完的考试"
                            },
                            checked = showCompletedExams,
                            onCheckedChange = onShowCompletedExamsChanged,
                        )
                        HorizontalDivider()
                        SettingsSwitchRow(
                            title = "显示已完成任务",
                            description = if (showCompletedTasks) {
                                "近期任务中展示已划线的待办与作业"
                            } else {
                                "近期任务中隐藏已完成的待办与作业"
                            },
                            checked = showCompletedTasks,
                            onCheckedChange = onShowCompletedTasksChanged,
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                fontWeight = FontWeight.Medium
            )
        },
        supportingContent = {
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        modifier = Modifier.clickable { onCheckedChange(!checked) }
    )
}

@Composable
private fun ThemeModeRow(
    themeMode: AppThemeMode,
    selected: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = themeMode.titleText(),
                fontWeight = FontWeight.Medium
            )
        },
        supportingContent = {
            Text(text = themeMode.descriptionText())
        },
        leadingContent = {
            RadioButton(
                selected = selected,
                onClick = onClick
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

private fun AppThemeMode.titleText(): String = when (this) {
    AppThemeMode.DAY -> "白天"
    AppThemeMode.DARK -> "深色"
    AppThemeMode.SYSTEM -> "跟随系统"
}

private fun AppThemeMode.descriptionText(): String = when (this) {
    AppThemeMode.DAY -> "始终使用浅色界面"
    AppThemeMode.DARK -> "始终使用深色界面"
    AppThemeMode.SYSTEM -> "根据系统深色模式自动切换"
}

// ─── 我的信息二级入口 (Hub) ──────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyInfoHubScreen(
    studentInfoUiState: StudentInfoUiState,
    financeUiState: FinanceUiState,
    attendanceUiState: AttendanceUiState,
    onBack: () -> Unit,
    onRefreshAll: () -> Unit,
    onOpenBasicInfo: () -> Unit,
    onOpenHousing: () -> Unit,
    onOpenAcademicWarning: () -> Unit,
    onOpenFinance: () -> Unit,
    onOpenAttendance: () -> Unit
) {
    val info = studentInfoUiState.info
    val basicCount = info?.basicFields?.size ?: 0
    val housingCount = info?.housingFields?.size ?: 0
    val warningCount = info?.academicWarningFields?.size ?: 0
    val financeCount = financeUiState.summary?.let { s ->
        s.scholarship.size + s.grant.size + s.hardshipGrant.size +
            s.workStudy.size + s.loan.size + s.arrearsStatus.size
    } ?: 0
    val attendanceTotal = attendanceUiState.summary?.total ?: 0
    val isRefreshing = studentInfoUiState.isLoading || financeUiState.isLoading || attendanceUiState.isLoading

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的信息") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                ProfileSection {
                    Column {
                        MyInfoHubRow(
                            title = "学生基本信息",
                            summary = if (basicCount > 0) "$basicCount 项" else "暂无数据",
                            icon = Icons.Filled.Person,
                            iconColor = Color(0xFF2F80ED),
                            onClick = onOpenBasicInfo
                        )
                        HorizontalDivider()
                        MyInfoHubRow(
                            title = "住宿数据",
                            summary = if (housingCount > 0) "$housingCount 项" else "暂无数据",
                            icon = Icons.Filled.Info,
                            iconColor = Color(0xFF27AE60),
                            onClick = onOpenHousing
                        )
                        HorizontalDivider()
                        MyInfoHubRow(
                            title = "学业预警信息",
                            summary = if (warningCount > 0) "$warningCount 项" else "无记录",
                            icon = Icons.Filled.Info,
                            iconColor = Color(0xFFE67E22),
                            onClick = onOpenAcademicWarning
                        )
                        HorizontalDivider()
                        MyInfoHubRow(
                            title = "我的财务",
                            summary = if (financeCount > 0) "$financeCount 项" else "暂无数据",
                            icon = Icons.Filled.AccountBalanceWallet,
                            iconColor = Color(0xFFB7791F),
                            onClick = onOpenFinance
                        )
                        HorizontalDivider()
                        MyInfoHubRow(
                            title = "考勤记录",
                            summary = if (attendanceTotal > 0) "$attendanceTotal 条记录" else "暂无记录",
                            icon = Icons.Filled.EventBusy,
                            iconColor = Color(0xFFE74C3C),
                            onClick = onOpenAttendance
                        )
                    }
                }
            }

            // 刷新全部按钮
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = onRefreshAll,
                        enabled = !isRefreshing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(if (isRefreshing) "正在更新数据..." else "更新全部数据")
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun MyInfoHubRow(
    title: String,
    summary: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(text = title, fontWeight = FontWeight.Medium)
        },
        supportingContent = {
            Text(text = summary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(AhuShapes.IconBox)
                    .background(iconColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor)
            }
        },
        trailingContent = {
            Icon(Icons.Filled.ChevronRight, contentDescription = null)
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

// ─── 通用分类详情页（学生基本信息 / 住宿数据 / 学业预警信息）───

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(
    title: String,
    fields: List<StudentInfoField>,
    isLoading: Boolean,
    error: String?,
    lastUpdatedAt: Long,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
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
            when {
                isLoading && fields.isEmpty() -> {
                    item { LoadingBlock("正在加载...") }
                }
                error != null && fields.isEmpty() -> {
                    item { ErrorBlock(error = error, onRefresh = onRefresh) }
                }
                fields.isEmpty() -> {
                    item {
                        ProfileSection {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 28.dp, horizontal = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (title == "学业预警信息") "暂无预警信息" else "暂无数据",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                else -> {
                    item { StudentInfoSection(title = title, fields = fields) }
                    item {
                        StudentInfoFooter(
                            lastUpdatedAt = lastUpdatedAt,
                            isLoading = isLoading,
                            error = error,
                            onRefresh = onRefresh
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun StudentInfoFooter(
    lastUpdatedAt: Long,
    isLoading: Boolean,
    error: String?,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (lastUpdatedAt > 0) {
            Text(
                text = studentInfoUpdatedText(lastUpdatedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp)
            )
        }
        if (error != null) {
            Text(
                text = "更新失败:$error",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        Button(
            onClick = onRefresh,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.size(8.dp))
            } else {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
            }
            Text("更新数据")
        }
    }
}

private fun studentInfoUpdatedText(timestamp: Long): String {
    val text = try {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    } catch (_: Exception) {
        ""
    }
    return if (text.isBlank()) "本地缓存" else "最后更新于 $text"
}

@Composable
private fun StudentInfoSection(
    title: String,
    fields: List<StudentInfoField>
) {
    val displayFields = fields.mapNotNull { it.toDisplayField() }
    ProfileSection {
        Column {
            Text(
                text = title,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (displayFields.isEmpty()) {
                Text(
                    text = "暂无数据",
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                displayFields.forEachIndexed { index, field ->
                    if (index > 0) HorizontalDivider()
                    StudentInfoRow(field = field)
                }
            }
        }
    }
}

private fun StudentInfoField.toDisplayField(): StudentInfoField? {
    if (StudentInfoCodeLookup.isHiddenField(label)) return null
    val decoded = StudentInfoCodeLookup.decode(label, value)
    return if (decoded != null) StudentInfoField(decoded.first, decoded.second) else this
}

@Composable
private fun StudentInfoRow(field: StudentInfoField) {
    AhuInfoRow(label = field.label, value = field.value)
}

@Composable
private fun ProfileHeader(
    displayName: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(34.dp)
            )
        }
        Column(
            modifier = Modifier.padding(start = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BalanceCard(
    balance: Double,
    isLoading: Boolean,
    error: String?,
    timestamp: Long,
    qrAuthUrl: String,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val formatter = DecimalFormat("\u00A5#,##0.00")
    var showQrHint by rememberSaveable { mutableStateOf(false) }

    fun shareQrLink() {
        val opened = BrowserOpener.shareTextToWeChat(context, qrAuthUrl)
        if (!opened) {
            Toast.makeText(context, "未能分享到微信，请确认已安装微信", Toast.LENGTH_SHORT).show()
        }
    }

    if (showQrHint) {
        AlertDialog(
            onDismissRequest = { showQrHint = false },
            title = { Text("智慧安大支付码") },
            text = {
                Text("智慧安大支付码暂不支持第三方调用，需要你自己分享网页链接到微信，再点击链接进入。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        context.getSharedPreferences("campus_qr_hint", android.content.Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean("shown", true)
                            .apply()
                        showQrHint = false
                        shareQrLink()
                    }
                ) {
                    Text("去微信分享")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQrHint = false }) {
                    Text("取消")
                }
            }
        )
    }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(AhuShapes.IconBox)
                    .background(AhuGreen.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.AccountBalanceWallet,
                    contentDescription = null,
                    tint = AhuGreen
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "\u6821\u56ed\u5361\u4f59\u989d",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = updatedText(timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            when {
                isLoading && balance == 0.0 -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
                error != null && balance == 0.0 -> {
                    Text(
                        text = "\u83b7\u53d6\u5931\u8d25",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> {
                    Text(
                        text = formatter.format(balance),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            IconButton(
                onClick = {
                    val shown = context.getSharedPreferences(
                        "campus_qr_hint",
                        android.content.Context.MODE_PRIVATE
                    ).getBoolean("shown", false)
                    if (shown) {
                        shareQrLink()
                    } else {
                        showQrHint = true
                    }
                },
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Icon(
                    Icons.Filled.QrCode2,
                    contentDescription = "智慧安大支付码",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun ProfileSection(content: @Composable () -> Unit) {
    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        content()
    }
}

@Composable
private fun SettingsRow(
    title: String,
    description: String,
    iconColor: Color,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                fontWeight = FontWeight.Medium
            )
        },
        supportingContent = {
            Text(
                text = description,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(AhuShapes.IconBox)
                    .background(iconColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides iconColor
                ) {
                    icon()
                }
            }
        },
        trailingContent = {
            Icon(Icons.Filled.ChevronRight, contentDescription = null)
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaterElectricityUtilityDetailScreen(
    bathroomData: com.yourname.ahu_plus.data.model.BathroomBalanceData?,
    bathroomLoading: Boolean,
    bathroomError: String?,
    bathroomPhone: String,
    acState: ElectricityState,
    acBills: List<ElectricityDailyRecord>,
    acBillRange: ElectricityBillRange,
    acBillsLoading: Boolean,
    acBillsError: String?,
    lightingState: ElectricityState,
    lightingBills: List<ElectricityDailyRecord>,
    lightingBillRange: ElectricityBillRange,
    lightingBillsLoading: Boolean,
    lightingBillsError: String?,
    internetData: InternetBalanceData?,
    internetLoading: Boolean,
    internetError: String?,
    internetBills: List<InternetBillRecord>,
    internetBillsLoading: Boolean,
    internetBillsError: String?,
    onBack: () -> Unit,
    onSaveBathroomPhone: (String) -> Unit,
    onSaveAcConfig: (ElectricityRoomConfig) -> Unit,
    onSaveLightingConfig: (ElectricityRoomConfig) -> Unit,
    onRefreshBathroom: () -> Unit,
    onRefreshAcBalance: () -> Unit,
    onRefreshLightingBalance: () -> Unit,
    onRefreshInternetBalance: () -> Unit,
    onRefreshAcBills: () -> Unit,
    onRefreshLightingBills: () -> Unit,
    onRefreshInternetBills: () -> Unit,
    onAcBillRangeSelected: (ElectricityBillRange) -> Unit,
    onLightingBillRangeSelected: (ElectricityBillRange) -> Unit,
    initialUtility: String? = null
) {
    var selectedUtility by rememberSaveable { mutableStateOf(initialUtility) }

    LaunchedEffect(Unit) {
        onRefreshAcBills()
        onRefreshLightingBills()
        onRefreshInternetBills()
    }

    when (selectedUtility) {
        "bathroom" -> {
            BackHandler(enabled = true) { selectedUtility = null }
            BathroomUtilityDetailScreen(
                data = bathroomData,
                isLoading = bathroomLoading,
                error = bathroomError,
                phone = bathroomPhone,
                onBack = { selectedUtility = null },
                onSavePhone = onSaveBathroomPhone,
                onRefresh = onRefreshBathroom
            )
            return
        }
        "ac" -> {
            BackHandler(enabled = true) { selectedUtility = null }
            ElectricityUtilityDetailScreen(
                title = "空调余额",
                state = acState,
                bills = acBills,
                billRange = acBillRange,
                billsLoading = acBillsLoading,
                billsError = acBillsError,
                onBack = { selectedUtility = null },
                onSaveConfig = onSaveAcConfig,
                onRefreshBalance = onRefreshAcBalance,
                onRefreshBills = onRefreshAcBills,
                onBillRangeSelected = onAcBillRangeSelected
            )
            return
        }
        "lighting" -> {
            BackHandler(enabled = true) { selectedUtility = null }
            ElectricityUtilityDetailScreen(
                title = "照明余额",
                state = lightingState,
                bills = lightingBills,
                billRange = lightingBillRange,
                billsLoading = lightingBillsLoading,
                billsError = lightingBillsError,
                onBack = { selectedUtility = null },
                onSaveConfig = onSaveLightingConfig,
                onRefreshBalance = onRefreshLightingBalance,
                onRefreshBills = onRefreshLightingBills,
                onBillRangeSelected = onLightingBillRangeSelected
            )
            return
        }
        "internet" -> {
            BackHandler(enabled = true) { selectedUtility = null }
            InternetUtilityDetailScreen(
                data = internetData,
                isLoading = internetLoading,
                error = internetError,
                bills = internetBills,
                billsLoading = internetBillsLoading,
                billsError = internetBillsError,
                onBack = { selectedUtility = null },
                onRefreshBalance = onRefreshInternetBalance,
                onRefreshBills = onRefreshInternetBills
            )
            return
        }
    }

    UtilityDetailScaffold(
        title = "水电费查询",
        onBack = onBack,
        onRefresh = {
            onRefreshBathroom()
            onRefreshAcBalance()
            onRefreshLightingBalance()
            onRefreshInternetBalance()
            onRefreshAcBills()
            onRefreshLightingBills()
            onRefreshInternetBills()
        }
    ) {
        item {
            ClickableUtilityCard(onClick = { selectedUtility = "bathroom" }) {
                BathroomBalanceCard(
                    data = bathroomData,
                    isLoading = bathroomLoading,
                    error = bathroomError,
                    phone = bathroomPhone,
                    onSavePhone = onSaveBathroomPhone,
                    onRetry = onRefreshBathroom
                )
            }
        }
        item {
            ClickableUtilityCard(onClick = { selectedUtility = "ac" }) {
                ElectricityBalanceCard(
                    label = "空调余额",
                    state = acState,
                    onSaveConfig = onSaveAcConfig,
                    onRetry = onRefreshAcBalance
                )
            }
        }
        item {
            ClickableUtilityCard(onClick = { selectedUtility = "lighting" }) {
                ElectricityBalanceCard(
                    label = "照明余额",
                    state = lightingState,
                    onSaveConfig = onSaveLightingConfig,
                    onRetry = onRefreshLightingBalance
                )
            }
        }
        item {
            ClickableUtilityCard(onClick = { selectedUtility = "internet" }) {
                InternetBalanceCard(
                    data = internetData,
                    isLoading = internetLoading,
                    error = internetError,
                    onRetry = onRefreshInternetBalance
                )
            }
        }
        item {
            ProfileSection {
                Text(
                    text = "提醒：如果想要实现自动获取数据，请先去更新「我的信息」里的学生基本信息和住宿数据。",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ClickableUtilityCard(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.clickable(onClick = onClick)) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BathroomUtilityDetailScreen(
    data: com.yourname.ahu_plus.data.model.BathroomBalanceData?,
    isLoading: Boolean,
    error: String?,
    phone: String,
    onBack: () -> Unit,
    onSavePhone: (String) -> Unit,
    onRefresh: () -> Unit
) {
    UtilityDetailScaffold(
        title = "浴室余额",
        onBack = onBack,
        onRefresh = onRefresh
    ) {
        item {
            BathroomBalanceCard(
                data = data,
                isLoading = isLoading,
                error = error,
                phone = phone,
                onSavePhone = onSavePhone,
                onRetry = onRefresh
            )
        }
        item {
            ProfileSection {
                EmptyBlock("智慧安大里只有每次给浴室充钱的记录，所以不做。")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElectricityUtilityDetailScreen(
    title: String,
    state: ElectricityState,
    bills: List<ElectricityDailyRecord>,
    billRange: ElectricityBillRange,
    billsLoading: Boolean,
    billsError: String?,
    onBack: () -> Unit,
    onSaveConfig: (ElectricityRoomConfig) -> Unit,
    onRefreshBalance: () -> Unit,
    onRefreshBills: () -> Unit,
    onBillRangeSelected: (ElectricityBillRange) -> Unit
) {
    LaunchedEffect(Unit) {
        onRefreshBills()
    }
    UtilityDetailScaffold(
        title = title,
        onBack = onBack,
        onRefresh = {
            onRefreshBalance()
            onRefreshBills()
        }
    ) {
        item {
            ElectricityBalanceCard(
                label = title,
                state = state,
                onSaveConfig = onSaveConfig,
                onRetry = onRefreshBalance
            )
        }
        item {
            ElectricityBillsSection(
                title = "${title}账单查询",
                records = bills,
                range = billRange,
                isLoading = billsLoading,
                error = billsError,
                onRefresh = onRefreshBills,
                onRangeSelected = onBillRangeSelected
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InternetUtilityDetailScreen(
    data: InternetBalanceData?,
    isLoading: Boolean,
    error: String?,
    bills: List<InternetBillRecord>,
    billsLoading: Boolean,
    billsError: String?,
    onBack: () -> Unit,
    onRefreshBalance: () -> Unit,
    onRefreshBills: () -> Unit
) {
    LaunchedEffect(Unit) {
        onRefreshBills()
    }
    UtilityDetailScaffold(
        title = "网费余额",
        onBack = onBack,
        onRefresh = {
            onRefreshBalance()
            onRefreshBills()
        }
    ) {
        item {
            InternetBalanceCard(
                data = data,
                isLoading = isLoading,
                error = error,
                onRetry = onRefreshBalance
            )
        }
        item {
            InternetBillsSection(
                records = bills,
                isLoading = billsLoading,
                error = billsError,
                onRefresh = onRefreshBills
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UtilityDetailScaffold(
    title: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = {
                content()
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        )
    }
}

@Composable
private fun ElectricityBillsSection(
    title: String,
    records: List<ElectricityDailyRecord>,
    range: ElectricityBillRange,
    isLoading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onRangeSelected: (ElectricityBillRange) -> Unit
) {
    val sortedRecords = remember(records) {
        records.sortedByDescending { it.date }
    }
    ProfileSection {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ElectricityBillRange.entries.forEach { option ->
                        FilterChip(
                            selected = range == option,
                            onClick = {
                                if (range != option) {
                                    onRangeSelected(option)
                                }
                            },
                            label = { Text(option.label) }
                        )
                    }
                }
            }
            HorizontalDivider()
            when {
                isLoading && sortedRecords.isEmpty() -> LoadingInline(range.loadingText)
                error != null && sortedRecords.isEmpty() -> ErrorInline(error = error, onRefresh = onRefresh)
                sortedRecords.isEmpty() -> EmptyBlock(range.emptyText)
                else -> {
                    sortedRecords.forEachIndexed { index, record ->
                        ElectricityBillRow(record)
                        if (index != sortedRecords.lastIndex) HorizontalDivider()
                    }
                    if (isLoading) {
                        HorizontalDivider()
                        LoadingInline(range.loadingText)
                    }
                }
            }
        }
    }
}

@Composable
private fun ElectricityBillRow(record: ElectricityDailyRecord) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color(0xFF00A6A6).copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = Color(0xFF00A6A6))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = record.date.ifBlank { "未知日期" },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "日用电明细",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = record.kwh?.let { "${DecimalFormat("#,##0.00").format(it)} 度" }
                ?: record.degreeText.ifBlank { "-" },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun InternetBillsSection(
    records: List<InternetBillRecord>,
    isLoading: Boolean,
    error: String?,
    onRefresh: () -> Unit
) {
    val sortedRecords = remember(records) {
        records.sortedByDescending { it.successDate.ifBlank { it.itemName } }
    }
    when {
        isLoading && sortedRecords.isEmpty() -> LoadingBlock("正在加载网费明细...")
        error != null && sortedRecords.isEmpty() -> ErrorBlock(error = error, onRefresh = onRefresh)
        sortedRecords.isEmpty() -> ProfileSection { EmptyBlock("暂无网费充值明细") }
        else -> ProfileSection {
            Column {
                sortedRecords.forEachIndexed { index, record ->
                    InternetBillRow(record)
                    if (index != sortedRecords.lastIndex) HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun InternetBillRow(record: InternetBillRecord) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color(0xFF2A9D8F).copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.ArrowDownward, contentDescription = null, tint = Color(0xFF2A9D8F))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = record.abstracts.ifBlank { record.typeName.ifBlank { "网费充值" } },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = record.successDate.ifBlank { record.itemName },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = "+${DecimalFormat("¥#,##0").format(record.tranAmt)}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2A9D8F)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillDetailScreen(
    bills: List<BillRecord>,
    isLoading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenAnalytics: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("校园卡账单") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenAnalytics) {
                        Icon(Icons.Filled.Assessment, contentDescription = "分析")
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            when {
                isLoading && bills.isEmpty() -> {
                    item {
                        LoadingBlock("正在加载账单...")
                    }
                }
                error != null && bills.isEmpty() -> {
                    item {
                        ErrorBlock(error = error, onRefresh = onRefresh)
                    }
                }
                bills.isEmpty() -> {
                    item {
                        EmptyBlock("暂无账单记录")
                    }
                }
                else -> {
                    item {
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                bills.forEachIndexed { index, bill ->
                                    BillRow(bill = bill)
                                    if (index != bills.lastIndex) HorizontalDivider()
                                }
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
fun BillRow(bill: BillRecord) {
    val typeText = (bill.turnoverType + " " + bill.consumeTypeName.orEmpty()).trim()
    val isPayment = when {
        bill.tranAmt < 0 -> true
        typeText.contains("充值") -> false
        typeText.contains("退款") || typeText.contains("退费") -> false
        typeText.contains("转入") || typeText.contains("入账") -> false
        typeText.contains("消费") || typeText.contains("支付") || typeText.contains("扣款") -> true
        else -> false
    }
    val amount = abs(bill.tranAmt) / 100.0
    val formatter = DecimalFormat("¥#0.00")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    if (isPayment) MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                    else Color(0xFF2A9D8F).copy(alpha = 0.14f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPayment) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                contentDescription = null,
                tint = if (isPayment) MaterialTheme.colorScheme.error else Color(0xFF2A9D8F)
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = bill.resume.ifBlank { bill.turnoverType.ifBlank { "校园卡交易" } },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = bill.effectDateStr.ifBlank { bill.jndatetimeStr },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = if (isPayment) "-${formatter.format(amount)}" else "+${formatter.format(amount)}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = if (isPayment) MaterialTheme.colorScheme.error else Color(0xFF2A9D8F)
        )
    }
}

@Composable
fun LoadingBlock(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(30.dp))
        Text(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ErrorBlock(error: String, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
        )
        TextButton(onClick = onRefresh) {
            Text("重试")
        }
    }
}

@Composable
fun LoadingInline(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ErrorInline(error: String, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = error,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
        TextButton(onClick = onRefresh) {
            Text("重试")
        }
    }
}

@Composable
fun EmptyBlock(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            Icons.Filled.CreditCard,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun updatedText(timestamp: Long): String {
    if (timestamp <= 0) return "点击查看账单明细"
    val text = try {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    } catch (_: Exception) {
        ""
    }
    return if (text.isBlank()) "点击查看账单明细" else "更新于 $text · 点击查看账单"
}

private fun StudentInfo.displayName(): String? {
    return firstValueOf("姓名", "学生姓名", "本人姓名", "USER_NAME")
}

private fun StudentInfo.department(): String? {
    return firstValueOf("学院", "院系", "院系名称", "所在院系", "培养单位", "UNIT_NAME")
}

private fun StudentInfo.classOrMajor(): String? {
    val major = firstValueOf("专业", "专业名称", "所在专业")
    val className = firstValueOf("班级", "行政班", "自然班")
    return listOfNotNull(major, className)
        .distinct()
        .joinToString(" · ")
        .takeIf { it.isNotBlank() }
}

// ─── 财务汇总 ──────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceDetailScreen(
    uiState: FinanceUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的财务") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
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
            when {
                uiState.isLoading && uiState.summary == null -> {
                    item { LoadingBlock("正在加载财务信息...") }
                }
                uiState.error != null && uiState.summary == null -> {
                    item { ErrorBlock(error = uiState.error, onRefresh = onRefresh) }
                }
                uiState.summary == null -> {
                    item {
                        FinanceEmptyWithUpdate(
                            isLoading = uiState.isLoading,
                            onRefresh = onRefresh
                        )
                    }
                }
                else -> {
                    val s = uiState.summary!!
                    item { StudentInfoSection("奖学金数据", s.scholarship) }
                    item { StudentInfoSection("助学金数据", s.grant) }
                    item { StudentInfoSection("临时困难补助数据", s.hardshipGrant) }
                    item { StudentInfoSection("勤工助学数据", s.workStudy) }
                    item { StudentInfoSection("欠费状态", s.arrearsStatus) }
                    item { StudentInfoSection("贷款数据", s.loan) }
                    item {
                        StudentInfoFooter(
                            lastUpdatedAt = uiState.lastUpdatedAt,
                            isLoading = uiState.isLoading,
                            error = uiState.error,
                            onRefresh = onRefresh
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun FinanceEmptyWithUpdate(
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    ProfileSection {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 36.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                Icons.Filled.AccountBalanceWallet,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "本地暂无财务数据",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "点击下方按钮从学生一张表同步",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onRefresh,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                }
                Text("更新数据")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceListScreen(
    uiState: AttendanceUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("考勤记录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when {
                uiState.isLoading && uiState.summary == null -> {
                    item { LoadingBlock("正在加载考勤记录...") }
                }
                uiState.error != null && uiState.summary == null -> {
                    item { ErrorBlock(error = uiState.error, onRefresh = onRefresh) }
                }
                uiState.summary == null -> {
                    item {
                        ProfileSection {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 36.dp, horizontal = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Icon(
                                    Icons.Filled.EventBusy,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "本地暂无考勤记录",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "点击下方按钮从学生一张表同步",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = onRefresh,
                                    enabled = !uiState.isLoading,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("更新数据")
                                }
                            }
                        }
                    }
                }
                else -> {
                    val records = uiState.summary.records
                    if (records.isEmpty()) {
                        item { EmptyBlock("暂无缺勤记录") }
                    } else {
                        items(
                            items = records,
                            key = { it.resourceId.ifBlank { it.hashCode().toString() } }
                        ) { record ->
                            AttendanceRow(record = record)
                        }
                    }
                    item {
                        StudentInfoFooter(
                            lastUpdatedAt = uiState.lastUpdatedAt,
                            isLoading = uiState.isLoading,
                            error = uiState.error,
                            onRefresh = onRefresh
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun AttendanceRow(record: AttendanceRecord) {
    // 颜色由状态决定：正常=绿色，缺勤/迟到/早退/旷课=红色/黄色
    val isAbsent = record.attendanceType?.let {
        it.contains("旷") || it.contains("迟到") || it.contains("早退") || it.contains("缺")
    } ?: (record.status?.let { s -> s != "正常" } ?: false)
    val typeColor = when {
        isAbsent && (record.attendanceType?.contains("旷") == true) -> MaterialTheme.colorScheme.error
        isAbsent -> Color(0xFFE0A100)
        else -> Color(0xFF27AE60) // 正常出勤=绿色
    }
    val badgeText = when {
        !record.attendanceType.isNullOrBlank() -> record.attendanceType.take(1)
        record.status == "正常" -> "到"
        !record.status.isNullOrBlank() -> record.status.take(1)
        else -> "到"
    }
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(typeColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = typeColor
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = record.courseName?.ifBlank { "未命名课程" } ?: "未命名课程",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // 第一行副标题：日期 · 节次 · 教室
                Text(
                    text = listOfNotNull(
                        record.classDate?.ifBlank { null },
                        record.classPeriod?.ifBlank { null }?.let { "第${it}节" },
                        record.classroom?.ifBlank { null }
                    ).joinToString(" · ").ifBlank { "日期未知" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // 第二行副标题：教师 · 状态
                val detailLine = listOfNotNull(
                    record.teacherName?.ifBlank { null }?.let { "教师:${it}" },
                    record.status?.ifBlank { null },
                    record.source?.ifBlank { null }
                ).joinToString(" · ")
                if (detailLine.isNotBlank()) {
                    Text(
                        text = detailLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            val statusText = record.attendanceType?.takeIf { it.isNotBlank() }
                ?: record.status?.takeIf { it.isNotBlank() && it != "正常" }
            if (!statusText.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = typeColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = typeColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

private fun formatShortTime(timestamp: Long): String {
    if (timestamp <= 0) return "—"
    return try {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    } catch (_: Exception) {
        "—"
    }
}
