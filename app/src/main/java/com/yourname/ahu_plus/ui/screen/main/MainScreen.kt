package com.yourname.ahu_plus.ui.screen.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourname.ahu_plus.AhuPlusApplication
import com.yourname.ahu_plus.data.local.AppThemeMode
import com.yourname.ahu_plus.data.local.CourseNoteRepository
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.repository.AdwmhCardRepository
import com.yourname.ahu_plus.data.repository.AttendanceRepository
import com.yourname.ahu_plus.data.repository.CardRepository
import com.yourname.ahu_plus.data.repository.CasAuthRepository
import com.yourname.ahu_plus.data.repository.CourseRepository
import com.yourname.ahu_plus.data.repository.ExamRepository
import com.yourname.ahu_plus.data.repository.FinanceRepository
import com.yourname.ahu_plus.data.repository.GradeRepository
import com.yourname.ahu_plus.data.repository.JwcNoticeRepository
import com.yourname.ahu_plus.data.repository.JwAuthRepository
import com.yourname.ahu_plus.data.repository.MarketRepository
import com.yourname.ahu_plus.data.repository.StudentInfoRepository
import com.yourname.ahu_plus.data.repository.YcardRepository
import com.yourname.ahu_plus.ui.screen.apps.AppHubScreen
import com.yourname.ahu_plus.ui.screen.dashboard.DashboardScreen
import com.yourname.ahu_plus.ui.screen.dashboard.JwcNoticeListScreen
import com.yourname.ahu_plus.ui.screen.dashboard.JwcNoticeListViewModel
import com.yourname.ahu_plus.ui.screen.dashboard.JwcNoticeViewModel
import com.yourname.ahu_plus.ui.screen.exam.ExamScreen
import com.yourname.ahu_plus.ui.screen.exam.ExamViewModel
import com.yourname.ahu_plus.ui.screen.grade.GradeScreen
import com.yourname.ahu_plus.ui.screen.grade.GradeViewModel
import com.yourname.ahu_plus.ui.screen.home.HomeViewModel
import com.yourname.ahu_plus.ui.screen.market.MarketScreen
import com.yourname.ahu_plus.ui.screen.market.MarketViewModel
import com.yourname.ahu_plus.ui.screen.profile.AttendanceViewModel
import com.yourname.ahu_plus.ui.screen.profile.FinanceViewModel
import com.yourname.ahu_plus.ui.screen.profile.ProfileScreen
import com.yourname.ahu_plus.ui.screen.profile.StudentInfoViewModel
import com.yourname.ahu_plus.ui.screen.schedule.ScheduleScreen
import com.yourname.ahu_plus.ui.screen.schedule.ScheduleViewModel

private const val TAB_HOME = 0
private const val TAB_MARKET = 1
private const val TAB_APPS = 2
private const val TAB_PROFILE = 3

private const val HOME_DASHBOARD = 0
private const val HOME_SCHEDULE = 1
private const val HOME_NOTICE_LIST = 2
private const val HOME_GRADE = 3
private const val HOME_EXAM = 4
private const val HOME_BILLS = 5

@Composable
fun MainScreen(
    sessionManager: SessionManager,
    cardRepository: CardRepository,
    casAuthRepository: CasAuthRepository,
    jwAuthRepository: JwAuthRepository,
    courseRepository: CourseRepository,
    ycardRepository: YcardRepository,
    marketRepository: MarketRepository,
    jwcNoticeRepository: JwcNoticeRepository,
    studentInfoRepository: StudentInfoRepository,
    courseNoteRepository: CourseNoteRepository,
    gradeRepository: GradeRepository,
    examRepository: ExamRepository,
    financeRepository: FinanceRepository,
    attendanceRepository: AttendanceRepository,
    adwmhCardRepository: AdwmhCardRepository,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    /** 仅清除会话并跳转登录(保留凭据/集市token等本地数据) */
    onReauth: () -> Unit,
    /** 完全退出登录(清除所有本地数据) */
    onLogout: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(TAB_HOME) }
    var homePage by rememberSaveable { mutableIntStateOf(HOME_DASHBOARD) }

    // 跨 Tab 跳转目标:Dashboard 常用应用点击「浴室/空调/照明/网费」时使用
    // 切到「我的」Tab 并把 scrollTarget 透传给 ProfileScreen,滚动到对应卡片后清空
    var profileScrollTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var profileSubPage by rememberSaveable { mutableStateOf<String?>(null) }
    var openCardAnalytics by rememberSaveable { mutableStateOf(false) }

    // 系统返回键:仅处理首页的子页面回退 (集市/我的子页面由各自 BackHandler 处理)
    BackHandler(enabled = homePage != HOME_DASHBOARD) {
        homePage = HOME_DASHBOARD
    }

    val cardViewModel = remember {
        HomeViewModel(
            cardRepository,
            casAuthRepository,
            ycardRepository,
            sessionManager,
            studentInfoRepository,
            adwmhCardRepository
        )
    }
    val app = LocalContext.current.applicationContext as AhuPlusApplication
    val scheduleViewModel = remember {
        ScheduleViewModel(
            application = app,
            jwAuthRepository = jwAuthRepository,
            courseRepository = courseRepository,
            noteRepository = courseNoteRepository,
            sessionManager = sessionManager,
            assessmentRepository = app.assessmentRepository,
            recordRepository = app.recordRepository,
            homeworkRepository = app.homeworkRepository,
            userTaskRepository = app.userTaskRepository,
            examRepository = examRepository,
        )
    }
    val marketViewModel = remember {
        MarketViewModel(marketRepository)
    }
    val marketUiState by marketViewModel.uiState.collectAsStateWithLifecycle()
    // 集市功能总开关:true=底部 3 Tab(首页/集市/我的),false=仅 2 Tab(首页/我的)
    // 关闭后即使 selectedTab 指向 TAB_MARKET 也降级到 TAB_HOME
    val marketEnabled = marketUiState.marketEnabled
    val jwcNoticeViewModel = remember {
        JwcNoticeViewModel(jwcNoticeRepository)
    }
    val jwcNoticeListViewModel = remember {
        JwcNoticeListViewModel(jwcNoticeRepository)
    }
    val studentInfoViewModel = remember {
        StudentInfoViewModel(studentInfoRepository, sessionManager)
    }
    val gradeViewModel = remember {
        GradeViewModel(jwAuthRepository, gradeRepository, sessionManager)
    }
    val examViewModel = remember {
        ExamViewModel(jwAuthRepository, examRepository, sessionManager)
    }
    val financeViewModel = remember {
        FinanceViewModel(financeRepository, sessionManager)
    }
    val attendanceViewModel = remember {
        AttendanceViewModel(attendanceRepository, sessionManager)
    }
    val scheduleUiState by scheduleViewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == TAB_HOME,
                    onClick = {
                        selectedTab = TAB_HOME
                        homePage = HOME_DASHBOARD
                    },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == TAB_HOME) Icons.Filled.Home
                            else Icons.Outlined.Home,
                            contentDescription = "首页"
                        )
                    },
                    label = { Text("首页") }
                )
                if (marketEnabled) {
                    NavigationBarItem(
                        selected = selectedTab == TAB_MARKET,
                        onClick = { selectedTab = TAB_MARKET },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == TAB_MARKET) Icons.Filled.Storefront
                                else Icons.Outlined.Storefront,
                                contentDescription = "集市"
                            )
                        },
                        label = { Text("集市") }
                    )
                }
                NavigationBarItem(
                    selected = selectedTab == TAB_APPS,
                    onClick = { selectedTab = TAB_APPS },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == TAB_APPS) Icons.Filled.Apps
                            else Icons.Outlined.Apps,
                            contentDescription = "应用"
                        )
                    },
                    label = { Text("应用") }
                )
                NavigationBarItem(
                    selected = selectedTab == TAB_PROFILE,
                    onClick = { selectedTab = TAB_PROFILE },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == TAB_PROFILE) Icons.Filled.Person
                            else Icons.Outlined.Person,
                            contentDescription = "我的"
                        )
                    },
                    label = { Text("我的") }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when {
                !marketEnabled && selectedTab == TAB_MARKET -> {
                    // 集市被禁用,但 selectedTab 仍指向 TAB_MARKET (旧状态) — 降级到首页
                    DashboardScreen(
                        viewModel = scheduleViewModel,
                        noticeViewModel = jwcNoticeViewModel,
                        onOpenSchedule = { homePage = HOME_SCHEDULE },
                        onOpenCard = { homePage = HOME_BILLS },
                        onOpenNoticeList = { homePage = HOME_NOTICE_LIST },
                        onOpenGrade = { homePage = HOME_GRADE },
                        onOpenExam = { homePage = HOME_EXAM },
                        onOpenBathroom = {
                            selectedTab = TAB_PROFILE
                            profileScrollTarget = "bathroom"
                        },
                        onOpenAc = {
                            selectedTab = TAB_PROFILE
                            profileScrollTarget = "ac"
                        },
                        onOpenLighting = {
                            selectedTab = TAB_PROFILE
                            profileScrollTarget = "lighting"
                        },
                        onOpenInternet = {
                            selectedTab = TAB_PROFILE
                            profileScrollTarget = "internet"
                        },
                        onOpenCardAnalytics = {
                            selectedTab = TAB_PROFILE
                            openCardAnalytics = true
                        },
                        onNeedsLogin = onReauth
                    )
                }
                selectedTab == TAB_HOME -> {
                    when (homePage) {
                        HOME_SCHEDULE -> ScheduleScreen(
                            viewModel = scheduleViewModel,
                            assessmentRepository = app.assessmentRepository,
                            onBack = { homePage = HOME_DASHBOARD },
                            onNeedsLogin = onReauth
                        )
                        HOME_NOTICE_LIST -> JwcNoticeListScreen(
                            viewModel = jwcNoticeListViewModel,
                            onBack = { homePage = HOME_DASHBOARD }
                        )
                        HOME_GRADE -> GradeScreen(
                            viewModel = gradeViewModel,
                            onBack = { homePage = HOME_DASHBOARD },
                            onNeedsLogin = onReauth
                        )
                        HOME_EXAM -> ExamScreen(
                            viewModel = examViewModel,
                            onBack = { homePage = HOME_DASHBOARD },
                            onNeedsLogin = onReauth
                        )
                        HOME_BILLS -> {
                            val cardState by cardViewModel.uiState.collectAsStateWithLifecycle()
                            com.yourname.ahu_plus.ui.screen.profile.BillDetailScreen(
                                bills = cardState.bills,
                                isLoading = cardState.billsLoading,
                                error = cardState.billsError,
                                onBack = { homePage = HOME_DASHBOARD },
                                onRefresh = cardViewModel::onRefresh,
                                onOpenAnalytics = {
                                    selectedTab = TAB_PROFILE
                                    openCardAnalytics = true
                                }
                            )
                        }
                        else -> DashboardScreen(
                            viewModel = scheduleViewModel,
                            noticeViewModel = jwcNoticeViewModel,
                            onOpenSchedule = { homePage = HOME_SCHEDULE },
                            onOpenCard = { homePage = HOME_BILLS },
                            onOpenNoticeList = { homePage = HOME_NOTICE_LIST },
                            onOpenGrade = { homePage = HOME_GRADE },
                            onOpenExam = { homePage = HOME_EXAM },
                            onOpenBathroom = {
                                selectedTab = TAB_PROFILE
                                profileScrollTarget = "bathroom"
                            },
                            onOpenAc = {
                                selectedTab = TAB_PROFILE
                                profileScrollTarget = "ac"
                            },
                            onOpenLighting = {
                                selectedTab = TAB_PROFILE
                                profileScrollTarget = "lighting"
                            },
                            onOpenInternet = {
                                selectedTab = TAB_PROFILE
                                profileScrollTarget = "internet"
                            },
                            onOpenCardAnalytics = {
                                selectedTab = TAB_PROFILE
                                openCardAnalytics = true
                            },
                            onNeedsLogin = onReauth
                        )
                    }
                }
                selectedTab == TAB_MARKET -> MarketScreen(viewModel = marketViewModel)
                selectedTab == TAB_APPS -> AppHubScreen(
                    scheduleViewModel = scheduleViewModel,
                    gradeViewModel = gradeViewModel,
                    examViewModel = examViewModel,
                    cardViewModel = cardViewModel,
                    jwcNoticeListViewModel = jwcNoticeListViewModel,
                    studentInfoViewModel = studentInfoViewModel,
                    financeViewModel = financeViewModel,
                    attendanceViewModel = attendanceViewModel,
                    onNeedsLogin = onReauth
                )
                selectedTab == TAB_PROFILE -> ProfileScreen(
                    cardViewModel = cardViewModel,
                    marketViewModel = marketViewModel,
                    studentInfoViewModel = studentInfoViewModel,
                    financeViewModel = financeViewModel,
                    attendanceViewModel = attendanceViewModel,
                    scheduleUiState = scheduleUiState,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    // 2026-06-17 Bug2: 近期任务全局设置
                    showCompletedTasks = scheduleUiState.showCompletedTasks,
                    showCompletedExams = scheduleUiState.showCompletedExams,
                    onShowCompletedTasksChanged = { scheduleViewModel.setShowCompletedTasks(it) },
                    onShowCompletedExamsChanged = { scheduleViewModel.setShowCompletedExams(it) },
                    scrollTarget = profileScrollTarget,
                    onScrollTargetConsumed = { profileScrollTarget = null },
                    profileSubPage = profileSubPage,
                    onProfileSubPageConsumed = { profileSubPage = null },
                    openCardAnalytics = openCardAnalytics,
                    onCardAnalyticsConsumed = { openCardAnalytics = false },
                    onLogout = onLogout
                )
                else -> DashboardScreen(
                    viewModel = scheduleViewModel,
                    noticeViewModel = jwcNoticeViewModel,
                    onOpenSchedule = { homePage = HOME_SCHEDULE },
                    onOpenCard = { homePage = HOME_BILLS },
                    onOpenNoticeList = { homePage = HOME_NOTICE_LIST },
                    onOpenGrade = { homePage = HOME_GRADE },
                    onOpenExam = { homePage = HOME_EXAM },
                    onOpenBathroom = {
                        selectedTab = TAB_PROFILE
                        profileScrollTarget = "bathroom"
                    },
                    onOpenAc = {
                        selectedTab = TAB_PROFILE
                        profileScrollTarget = "ac"
                    },
                    onOpenLighting = {
                        selectedTab = TAB_PROFILE
                        profileScrollTarget = "lighting"
                    },
                    onOpenInternet = {
                        selectedTab = TAB_PROFILE
                        profileScrollTarget = "internet"
                    },
                    onOpenCardAnalytics = {
                        selectedTab = TAB_PROFILE
                        openCardAnalytics = true
                    },
                    onNeedsLogin = onReauth
                )
            }
        }
    }
}
