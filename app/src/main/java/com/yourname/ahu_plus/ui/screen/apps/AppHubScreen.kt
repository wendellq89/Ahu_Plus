package com.yourname.ahu_plus.ui.screen.apps

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourname.ahu_plus.AhuPlusApplication
import com.yourname.ahu_plus.ui.components.AhuIconBox
import com.yourname.ahu_plus.ui.components.AhuSectionTitle
import com.yourname.ahu_plus.ui.components.AhuTopAppBar
import com.yourname.ahu_plus.ui.screen.dashboard.JwcNoticeListScreen
import com.yourname.ahu_plus.ui.screen.dashboard.JwcNoticeListViewModel
import com.yourname.ahu_plus.ui.screen.exam.ExamScreen
import com.yourname.ahu_plus.ui.screen.exam.ExamViewModel
import com.yourname.ahu_plus.ui.screen.grade.GradeScreen
import com.yourname.ahu_plus.ui.screen.grade.GradeViewModel
import com.yourname.ahu_plus.ui.screen.home.HomeViewModel
import com.yourname.ahu_plus.ui.screen.profile.AttendanceListScreen
import com.yourname.ahu_plus.ui.screen.profile.AttendanceViewModel
import com.yourname.ahu_plus.ui.screen.profile.BathroomUtilityDetailScreen
import com.yourname.ahu_plus.ui.screen.profile.BillDetailScreen
import com.yourname.ahu_plus.ui.screen.profile.CardAnalyticsScreen
import com.yourname.ahu_plus.ui.screen.profile.CategoryDetailScreen
import com.yourname.ahu_plus.ui.screen.profile.ElectricityUtilityDetailScreen
import com.yourname.ahu_plus.ui.screen.profile.FinanceDetailScreen
import com.yourname.ahu_plus.ui.screen.profile.FinanceViewModel
import com.yourname.ahu_plus.ui.screen.profile.InternetUtilityDetailScreen
import com.yourname.ahu_plus.ui.screen.profile.MyInfoHubScreen
import com.yourname.ahu_plus.ui.screen.profile.StudentInfoViewModel
import com.yourname.ahu_plus.ui.screen.schedule.ScheduleScreen
import com.yourname.ahu_plus.ui.screen.schedule.ScheduleViewModel
import com.yourname.ahu_plus.ui.theme.AhuBlue
import com.yourname.ahu_plus.ui.theme.AhuGreen
import com.yourname.ahu_plus.ui.theme.AhuIndigo
import com.yourname.ahu_plus.ui.theme.AhuOrange
import com.yourname.ahu_plus.ui.theme.AhuRed
import com.yourname.ahu_plus.ui.theme.AhuTeal
import com.yourname.ahu_plus.ui.theme.AhuViolet

// ── internal page keys ─────────────────────────────────────────────
private const val PAGE_SCHEDULE = "schedule"
private const val PAGE_GRADE = "grade"
private const val PAGE_EXAM = "exam"
private const val PAGE_NOTICES = "notices"
private const val PAGE_BILLS = "bills"
private const val PAGE_ANALYTICS = "analytics"
private const val PAGE_BATHROOM = "bathroom"
private const val PAGE_AC = "ac"
private const val PAGE_LIGHTING = "lighting"
private const val PAGE_INTERNET = "internet"
private const val PAGE_MY_INFO_HUB = "myInfoHub"
private const val PAGE_STUDENT_BASIC_INFO = "studentBasicInfo"
private const val PAGE_HOUSING_INFO = "housingInfo"
private const val PAGE_ACADEMIC_WARNING = "academicWarning"
private const val PAGE_FINANCE = "finance"
private const val PAGE_ATTENDANCE = "attendance"

@Composable
fun AppHubScreen(
    scheduleViewModel: ScheduleViewModel,
    gradeViewModel: GradeViewModel,
    examViewModel: ExamViewModel,
    cardViewModel: HomeViewModel,
    jwcNoticeListViewModel: JwcNoticeListViewModel,
    studentInfoViewModel: StudentInfoViewModel,
    financeViewModel: FinanceViewModel,
    attendanceViewModel: AttendanceViewModel,
    onNeedsLogin: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as AhuPlusApplication

    val cardUiState by cardViewModel.uiState.collectAsStateWithLifecycle()
    val studentInfoUiState by studentInfoViewModel.uiState.collectAsStateWithLifecycle()
    val financeUiState by financeViewModel.uiState.collectAsStateWithLifecycle()
    val attendanceUiState by attendanceViewModel.uiState.collectAsStateWithLifecycle()

    var currentPage by rememberSaveable { mutableStateOf<String?>(null) }
    val hubListState = rememberLazyListState()

    // 系统返回键：子页面 → hub（MyInfoHub / 水电费子页面先回到上级）
    BackHandler(enabled = currentPage != null) {
        currentPage = when (currentPage) {
            PAGE_STUDENT_BASIC_INFO, PAGE_HOUSING_INFO, PAGE_ACADEMIC_WARNING,
            PAGE_FINANCE, PAGE_ATTENDANCE -> PAGE_MY_INFO_HUB
            else -> null
        }
    }

    when (currentPage) {
        PAGE_SCHEDULE -> ScheduleScreen(
            viewModel = scheduleViewModel,
            assessmentRepository = app.assessmentRepository,
            onBack = { currentPage = null },
            onNeedsLogin = onNeedsLogin
        )
        PAGE_GRADE -> GradeScreen(
            viewModel = gradeViewModel,
            onBack = { currentPage = null },
            onNeedsLogin = onNeedsLogin
        )
        PAGE_EXAM -> ExamScreen(
            viewModel = examViewModel,
            onBack = { currentPage = null },
            onNeedsLogin = onNeedsLogin
        )
        PAGE_NOTICES -> JwcNoticeListScreen(
            viewModel = jwcNoticeListViewModel,
            onBack = { currentPage = null }
        )
        PAGE_BILLS -> BillDetailScreen(
            bills = cardUiState.bills,
            isLoading = cardUiState.billsLoading,
            error = cardUiState.billsError,
            onBack = { currentPage = null },
            onRefresh = cardViewModel::onRefresh,
            onOpenAnalytics = { currentPage = PAGE_ANALYTICS }
        )
        PAGE_ANALYTICS -> CardAnalyticsScreen(
            bills = cardUiState.bills,
            isLoading = cardUiState.billsLoading,
            error = cardUiState.billsError,
            onBack = { currentPage = null },
            onRefresh = cardViewModel::onRefresh
        )
        PAGE_BATHROOM -> BathroomUtilityDetailScreen(
            data = cardUiState.bathroomData,
            isLoading = cardUiState.bathroomLoading,
            error = cardUiState.bathroomError,
            phone = cardUiState.bathroomPhone,
            onBack = { currentPage = null },
            onSavePhone = cardViewModel::saveBathroomPhone,
            onRefresh = cardViewModel::loadBathroomBalance
        )
        PAGE_AC -> ElectricityUtilityDetailScreen(
            title = "空调余额",
            state = cardUiState.ac,
            bills = cardUiState.acBills,
            billRange = cardUiState.acBillRange,
            billsLoading = cardUiState.acBillsLoading,
            billsError = cardUiState.acBillsError,
            onBack = { currentPage = null },
            onSaveConfig = cardViewModel::saveAcConfig,
            onRefreshBalance = cardViewModel::loadAcBalance,
            onRefreshBills = { cardViewModel.loadAcBills() },
            onBillRangeSelected = cardViewModel::setAcBillRange
        )
        PAGE_LIGHTING -> ElectricityUtilityDetailScreen(
            title = "照明余额",
            state = cardUiState.lighting,
            bills = cardUiState.lightingBills,
            billRange = cardUiState.lightingBillRange,
            billsLoading = cardUiState.lightingBillsLoading,
            billsError = cardUiState.lightingBillsError,
            onBack = { currentPage = null },
            onSaveConfig = cardViewModel::saveLightingConfig,
            onRefreshBalance = cardViewModel::loadLightingBalance,
            onRefreshBills = { cardViewModel.loadLightingBills() },
            onBillRangeSelected = cardViewModel::setLightingBillRange
        )
        PAGE_INTERNET -> InternetUtilityDetailScreen(
            data = cardUiState.internetData,
            isLoading = cardUiState.internetLoading,
            error = cardUiState.internetError,
            bills = cardUiState.internetBills,
            billsLoading = cardUiState.internetBillsLoading,
            billsError = cardUiState.internetBillsError,
            onBack = { currentPage = null },
            onRefreshBalance = cardViewModel::loadInternetBalance,
            onRefreshBills = cardViewModel::loadInternetBills
        )
        PAGE_MY_INFO_HUB -> MyInfoHubScreen(
            studentInfoUiState = studentInfoUiState,
            financeUiState = financeUiState,
            attendanceUiState = attendanceUiState,
            onBack = { currentPage = null },
            onRefreshAll = {
                studentInfoViewModel.refreshStudentInfo()
                financeViewModel.refreshFinance()
                attendanceViewModel.refreshAttendance()
            },
            onOpenBasicInfo = { currentPage = PAGE_STUDENT_BASIC_INFO },
            onOpenHousing = { currentPage = PAGE_HOUSING_INFO },
            onOpenAcademicWarning = { currentPage = PAGE_ACADEMIC_WARNING },
            onOpenFinance = { currentPage = PAGE_FINANCE },
            onOpenAttendance = { currentPage = PAGE_ATTENDANCE }
        )
        PAGE_STUDENT_BASIC_INFO -> CategoryDetailScreen(
            title = "学生基本信息",
            fields = studentInfoUiState.info?.basicFields ?: emptyList(),
            isLoading = studentInfoUiState.isLoading,
            error = studentInfoUiState.error,
            lastUpdatedAt = studentInfoUiState.lastUpdatedAt,
            onBack = { currentPage = PAGE_MY_INFO_HUB },
            onRefresh = studentInfoViewModel::refreshStudentInfo
        )
        PAGE_HOUSING_INFO -> CategoryDetailScreen(
            title = "住宿数据",
            fields = studentInfoUiState.info?.housingFields ?: emptyList(),
            isLoading = studentInfoUiState.isLoading,
            error = studentInfoUiState.error,
            lastUpdatedAt = studentInfoUiState.lastUpdatedAt,
            onBack = { currentPage = PAGE_MY_INFO_HUB },
            onRefresh = studentInfoViewModel::refreshStudentInfo
        )
        PAGE_ACADEMIC_WARNING -> CategoryDetailScreen(
            title = "学业预警信息",
            fields = studentInfoUiState.info?.academicWarningFields ?: emptyList(),
            isLoading = studentInfoUiState.isLoading,
            error = studentInfoUiState.error,
            lastUpdatedAt = studentInfoUiState.lastUpdatedAt,
            onBack = { currentPage = PAGE_MY_INFO_HUB },
            onRefresh = studentInfoViewModel::refreshStudentInfo
        )
        PAGE_FINANCE -> FinanceDetailScreen(
            uiState = financeUiState,
            onBack = { currentPage = PAGE_MY_INFO_HUB },
            onRefresh = financeViewModel::refreshFinance
        )
        PAGE_ATTENDANCE -> AttendanceListScreen(
            uiState = attendanceUiState,
            onBack = { currentPage = PAGE_MY_INFO_HUB },
            onRefresh = attendanceViewModel::refreshAttendance
        )
        else -> AppHubPage(
            listState = hubListState,
            onNavigate = { currentPage = it }
        )
    }
}

// ── Hub page ──────────────────────────────────────────────────────

@Composable
private fun AppHubPage(
    listState: androidx.compose.foundation.lazy.LazyListState,
    onNavigate: (String) -> Unit
) {
    Scaffold(
        topBar = {
            AhuTopAppBar(title = { Text("应用") })
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // ── 学习 ──────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(8.dp))
                AhuSectionTitle(text = "学习")
                Spacer(modifier = Modifier.height(4.dp))
            }
            item {
                AppHubItem("课表", Icons.Filled.CalendarMonth, AhuBlue) {
                    onNavigate(PAGE_SCHEDULE)
                }
            }
            item {
                AppHubItem("成绩", Icons.Filled.Grade, AhuRed) {
                    onNavigate(PAGE_GRADE)
                }
            }
            item {
                AppHubItem("考试", Icons.AutoMirrored.Filled.EventNote, AhuOrange) {
                    onNavigate(PAGE_EXAM)
                }
            }

            // ── 通知 ──────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(8.dp))
                AhuSectionTitle(text = "通知")
                Spacer(modifier = Modifier.height(4.dp))
            }
            item {
                AppHubItem("教务通知", Icons.Filled.Campaign, AhuViolet) {
                    onNavigate(PAGE_NOTICES)
                }
            }

            // ── 校园卡 ────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(8.dp))
                AhuSectionTitle(text = "校园卡")
                Spacer(modifier = Modifier.height(4.dp))
            }
            item {
                AppHubItem("消费账单", Icons.Filled.AccountBalanceWallet, AhuGreen) {
                    onNavigate(PAGE_BILLS)
                }
            }
            item {
                AppHubItem("消费分析", Icons.Filled.Assessment, AhuViolet) {
                    onNavigate(PAGE_ANALYTICS)
                }
            }

            // ── 生活 ──────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(8.dp))
                AhuSectionTitle(text = "生活")
                Spacer(modifier = Modifier.height(4.dp))
            }
            item {
                AppHubItem("浴室", Icons.Filled.WaterDrop, AhuTeal) {
                    onNavigate(PAGE_BATHROOM)
                }
            }
            item {
                AppHubItem("空调", Icons.Filled.AcUnit, AhuBlue) {
                    onNavigate(PAGE_AC)
                }
            }
            item {
                AppHubItem("照明", Icons.Filled.Lightbulb, AhuOrange) {
                    onNavigate(PAGE_LIGHTING)
                }
            }
            item {
                AppHubItem("网费", Icons.Filled.Wifi, AhuIndigo) {
                    onNavigate(PAGE_INTERNET)
                }
            }

            // ── 个人信息 ──────────────────────────────
            item {
                Spacer(modifier = Modifier.height(8.dp))
                AhuSectionTitle(text = "个人信息")
                Spacer(modifier = Modifier.height(4.dp))
            }
            item {
                AppHubItem("学生信息", Icons.Filled.Info, AhuBlue) {
                    onNavigate(PAGE_MY_INFO_HUB)
                }
            }
            item {
                AppHubItem("财务汇总", Icons.Filled.AccountBalanceWallet, AhuGreen) {
                    onNavigate(PAGE_FINANCE)
                }
            }
            item {
                AppHubItem("考勤记录", Icons.Filled.EventBusy, AhuRed) {
                    onNavigate(PAGE_ATTENDANCE)
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

// ── Shared item component ─────────────────────────────────────────

@Composable
private fun AppHubItem(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            leadingContent = {
                AhuIconBox(
                    imageVector = icon,
                    tint = iconColor,
                    size = 38.dp
                )
            },
            trailingContent = {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        )
        HorizontalDivider(
            modifier = Modifier.padding(start = 62.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    }
}
