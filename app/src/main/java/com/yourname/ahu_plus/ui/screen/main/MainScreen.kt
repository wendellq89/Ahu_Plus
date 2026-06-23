package com.yourname.ahu_plus.ui.screen.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.yourname.ahu_plus.AhuPlusApplication
import com.yourname.ahu_plus.data.local.AppThemeMode
import com.yourname.ahu_plus.data.local.CourseNoteRepository
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.repository.AdwmhCardRepository
import com.yourname.ahu_plus.data.repository.KqAttendanceRepository
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
import com.yourname.ahu_plus.ui.screen.chaoxing.ChaoxingTabScreen
import com.yourname.ahu_plus.ui.screen.chaoxing.ChaoxingViewModel
import com.yourname.ahu_plus.ui.screen.dashboard.DashboardScreen
import com.yourname.ahu_plus.ui.screen.dashboard.JwcNoticeListScreen
import com.yourname.ahu_plus.ui.screen.dashboard.JwcNoticeListViewModel
import com.yourname.ahu_plus.ui.screen.dashboard.JwcNoticeViewModel
import com.yourname.ahu_plus.ui.screen.exam.ExamScreen
import com.yourname.ahu_plus.ui.screen.exam.ExamViewModel
import com.yourname.ahu_plus.ui.screen.exam.ExamPredictionScreen
import com.yourname.ahu_plus.ui.screen.exam.ExamPredictionViewModel
import com.yourname.ahu_plus.ui.screen.grade.GradeScreen
import com.yourname.ahu_plus.ui.screen.grade.GradeViewModel
import com.yourname.ahu_plus.ui.screen.home.HomeViewModel
import com.yourname.ahu_plus.ui.screen.market.MarketScreen
import com.yourname.ahu_plus.ui.screen.market.MarketViewModel
import com.yourname.ahu_plus.ui.screen.profile.AttendanceViewModel
import com.yourname.ahu_plus.ui.screen.profile.FinanceViewModel
import com.yourname.ahu_plus.ui.screen.profile.ProfileScreen
import com.yourname.ahu_plus.ui.screen.profile.StudentInfoViewModel
import com.yourname.ahu_plus.ui.screen.emptyclassroom.EmptyClassroomScreen
import com.yourname.ahu_plus.ui.screen.emptyclassroom.EmptyClassroomViewModel
import com.yourname.ahu_plus.ui.screen.schedule.ScheduleScreen
import com.yourname.ahu_plus.ui.screen.schedule.ScheduleViewModel
import com.yourname.ahu_plus.ui.screen.trainingplan.TrainingPlanScreen
import com.yourname.ahu_plus.ui.screen.trainingplan.TrainingPlanViewModel

private const val TAB_HOME = 0
private const val TAB_MARKET = 1
private const val TAB_CHAOXING = 2
private const val TAB_APPS = 3
private const val TAB_PROFILE = 4

private const val HOME_DASHBOARD = 0
private const val HOME_SCHEDULE = 1
private const val HOME_NOTICE_LIST = 2
private const val HOME_GRADE = 3
private const val HOME_EXAM = 4
private const val HOME_BILLS = 5
private const val HOME_TRAINING_PLAN = 6
private const val HOME_EMPTY_CLASSROOM = 7
private const val HOME_EXAM_PREDICTION = 8

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
    attendanceRepository: KqAttendanceRepository,
    adwmhCardRepository: AdwmhCardRepository,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    /** 仅清除会话并跳转登录(保留凭据/集市token等本地数据) */
    onReauth: () -> Unit,
    /** 完全退出登录(清除所有本地数据) */
    onLogout: () -> Unit,
    /** 首次登录初始化消息流 (LoginViewModel emit → MainScreen 订阅 → 底部 Snackbar 1 秒) */
    initMessageFlow: kotlinx.coroutines.flow.MutableSharedFlow<String>? = null,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(TAB_HOME) }
    var homePage by rememberSaveable { mutableIntStateOf(HOME_DASHBOARD) }

    // 首次登录初始化冒泡 — SnackbarHost
    val initSnackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(initMessageFlow) {
        if (initMessageFlow != null) {
            initMessageFlow.collect { msg ->
                initSnackbarHostState.showSnackbar(
                    message = msg,
                    duration = androidx.compose.material3.SnackbarDuration.Short,
                    withDismissAction = false,
                )
            }
        }
    }

    // 跨 Tab 跳转目标:Dashboard 常用应用点击「浴室/空调/照明/网费」时使用
    // 切到「我的」Tab 并把 scrollTarget 透传给 ProfileScreen,滚动到对应卡片后清空
    var profileScrollTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var profileSubPage by rememberSaveable { mutableStateOf<String?>(null) }
    var openCardAnalytics by rememberSaveable { mutableStateOf(false) }
    // B-001: 跨 Tab 跳转时记录"上一页 Tab"，按返回键可回到原 Tab
    var previousTab by rememberSaveable { mutableStateOf<Int?>(null) }
    // B-002: 双击返回键退出
    var backPressedTime by remember { mutableLongStateOf(0L) }

    // 首页"最近使用"追踪 (mutableStateOf 保证 recordRecentApp 后 UI 立即刷新)
    var recentApps by remember { mutableStateOf(sessionManager.getRecentApps()) }
    val scope = rememberCoroutineScope()
    val recordApp: (String) -> Unit = remember {
        { appKey: String -> scope.launch { sessionManager.recordRecentApp(appKey); recentApps = sessionManager.getRecentApps() } }
    }

    // 2026-06-22: 通知点击 deep-link → 跳到学习通 Tab
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        (context as? Activity)?.intent?.getStringExtra("open_tab")?.let { tab ->
            when (tab) { "chaoxing" -> selectedTab = TAB_CHAOXING }
            (context as? Activity)?.intent?.removeExtra("open_tab")
        }
    }

    // 系统返回键: 子页面回退 → 跨 Tab 回退 → 双击退出
    BackHandler {
        when {
            // 1. 我的 Tab 子页面 → 我的主页 (ProfileScreen 内部 BackHandler 先拦截,这里兜底)
            profileSubPage != null -> profileSubPage = null
            // 2. 预测页 → 考试页（精确回退，不回 Dashboard）
            homePage == HOME_EXAM_PREDICTION -> homePage = HOME_EXAM
            // 3. 首页其他子页面 → Dashboard
            homePage != HOME_DASHBOARD -> homePage = HOME_DASHBOARD
            // 3. 跨 Tab 跳转过来的 → 回到上一页 Tab
            previousTab != null -> {
                selectedTab = previousTab!!
                previousTab = null
                profileScrollTarget = null
                openCardAnalytics = false
            }
            // 4. 顶层页: 双击返回键退出
            else -> {
                val now = System.currentTimeMillis()
                if (now - backPressedTime < 1500) {
                    (context as? Activity)?.finish()
                } else {
                    backPressedTime = now
                    Toast.makeText(context, "再按一次退出", Toast.LENGTH_SHORT).show()
                }
            }
        }
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
            kqAttendanceRepository = app.attendanceRepository,
        )
    }
    val marketViewModel = remember {
        MarketViewModel(marketRepository, app.aiCommentRepository)
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
    val trainingPlanViewModel = remember {
        TrainingPlanViewModel(jwAuthRepository, app.trainingPlanRepository, app.programCompletionRepository, sessionManager)
    }
    val emptyClassroomViewModel = remember {
        EmptyClassroomViewModel(jwAuthRepository, app.emptyClassroomRepository, sessionManager)
    }
    val chaoxingViewModel = remember {
        ChaoxingViewModel(app.chaoxingRepository, app.chaoxingStudyRepository, app.chaoxingTikuRepository, sessionManager)
    }
    val scheduleUiState by scheduleViewModel.uiState.collectAsStateWithLifecycle()
    val showBottomNavigation = selectedTab != TAB_MARKET || (
        marketUiState.selectedTopic == null &&
            !marketUiState.showCompose &&
            !marketUiState.showSettings &&
            !marketUiState.showHotTopics &&
            !marketUiState.showNotices
        )

    Scaffold(
        // 顶层 Scaffold 不消耗系统栏 inset:顶部由各内层屏 (DashboardScreen/ScheduleScreen/
        // GradeScreen/AppHubScreen/ChaoxingTabScreen/...) 自己的 Scaffold + AhuTopAppBar
        // 自适应处理,底部由 NavigationBar 自带 navigationBarsPadding 自动处理。
        // 这样不会出现"双重状态栏空白"。
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { androidx.compose.material3.SnackbarHost(initSnackbarHostState) },
        bottomBar = {
            if (showBottomNavigation) NavigationBar(
                tonalElevation = 0.dp,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
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
                    label = {
                        Text(
                            "首页",
                            fontWeight = if (selectedTab == TAB_HOME) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                        label = {
                            Text(
                                "集市",
                                fontWeight = if (selectedTab == TAB_MARKET) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    )
                }
                NavigationBarItem(
                    selected = selectedTab == TAB_CHAOXING,
                    onClick = { selectedTab = TAB_CHAOXING },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == TAB_CHAOXING) Icons.Filled.School
                            else Icons.Outlined.School,
                            contentDescription = "学习通"
                        )
                    },
                    label = {
                        Text(
                            "学习通",
                            fontWeight = if (selectedTab == TAB_CHAOXING) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                )
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
                    label = {
                        Text(
                            "应用",
                            fontWeight = if (selectedTab == TAB_APPS) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                    label = {
                        Text(
                            "我的",
                            fontWeight = if (selectedTab == TAB_PROFILE) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                            previousTab = selectedTab
                            selectedTab = TAB_PROFILE
                            profileScrollTarget = "bathroom"
                        },
                        onOpenAc = {
                            previousTab = selectedTab
                            selectedTab = TAB_PROFILE
                            profileScrollTarget = "ac"
                        },
                        onOpenLighting = {
                            previousTab = selectedTab
                            selectedTab = TAB_PROFILE
                            profileScrollTarget = "lighting"
                        },
                        onOpenInternet = {
                            previousTab = selectedTab
                            selectedTab = TAB_PROFILE
                            profileScrollTarget = "internet"
                        },
                        onOpenCardAnalytics = {
                            previousTab = selectedTab
                            selectedTab = TAB_PROFILE
                            openCardAnalytics = true
                        },
                        onOpenAppHub = {
                            previousTab = selectedTab
                            selectedTab = TAB_APPS
                        },
                        recentApps = recentApps,
                        onRecordApp = recordApp,
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
                            onNeedsLogin = onReauth,
                            onOpenPrediction = { homePage = HOME_EXAM_PREDICTION }
                        )
                        HOME_EXAM_PREDICTION -> {
                            // 进入排考预测页时才创建 VM,触发首次拉取
                            val examPredictionViewModel = remember {
                                ExamPredictionViewModel(app.examDataRepository, sessionManager)
                            }
                            ExamPredictionScreen(
                                viewModel = examPredictionViewModel,
                                onBack = { homePage = HOME_EXAM }
                            )
                        }
                        HOME_BILLS -> {
                            val cardState by cardViewModel.uiState.collectAsStateWithLifecycle()
                            com.yourname.ahu_plus.ui.screen.profile.BillDetailScreen(
                                bills = cardState.bills,
                                isLoading = cardState.billsLoading,
                                error = cardState.billsError,
                                onBack = { homePage = HOME_DASHBOARD },
                                onRefresh = cardViewModel::onRefresh,
                                onOpenAnalytics = {
                                    previousTab = selectedTab
                                    selectedTab = TAB_PROFILE
                                    openCardAnalytics = true
                                }
                            )
                        }
                        HOME_TRAINING_PLAN -> TrainingPlanScreen(
                            viewModel = trainingPlanViewModel,
                            onBack = { homePage = HOME_DASHBOARD },
                            onNeedsLogin = onReauth
                        )
                        HOME_EMPTY_CLASSROOM -> EmptyClassroomScreen(
                            viewModel = emptyClassroomViewModel,
                            onBack = { homePage = HOME_DASHBOARD },
                            onNeedsLogin = onReauth
                        )
                        else -> DashboardScreen(
                            viewModel = scheduleViewModel,
                            noticeViewModel = jwcNoticeViewModel,
                            onOpenSchedule = { homePage = HOME_SCHEDULE },
                            onOpenCard = { homePage = HOME_BILLS },
                            onOpenNoticeList = { homePage = HOME_NOTICE_LIST },
                            onOpenGrade = { homePage = HOME_GRADE },
                            onOpenExam = { homePage = HOME_EXAM },
                            onOpenTrainingPlan = { homePage = HOME_TRAINING_PLAN },
                            onOpenEmptyClassroom = { homePage = HOME_EMPTY_CLASSROOM },
                            onOpenBathroom = {
                                previousTab = selectedTab
                                selectedTab = TAB_PROFILE
                                profileScrollTarget = "bathroom"
                            },
                            onOpenAc = {
                                previousTab = selectedTab
                                selectedTab = TAB_PROFILE
                                profileScrollTarget = "ac"
                            },
                            onOpenLighting = {
                                previousTab = selectedTab
                                selectedTab = TAB_PROFILE
                                profileScrollTarget = "lighting"
                            },
                            onOpenInternet = {
                                previousTab = selectedTab
                                selectedTab = TAB_PROFILE
                                profileScrollTarget = "internet"
                            },
                            onOpenCardAnalytics = {
                                previousTab = selectedTab
                                selectedTab = TAB_PROFILE
                                openCardAnalytics = true
                            },
                            onOpenAppHub = {
                                previousTab = selectedTab
                                selectedTab = TAB_APPS
                            },
                            recentApps = recentApps,
                            onRecordApp = recordApp,
                            onNeedsLogin = onReauth
                        )
                    }
                }
                selectedTab == TAB_MARKET -> MarketScreen(viewModel = marketViewModel)
                selectedTab == TAB_CHAOXING -> ChaoxingTabScreen(
                    viewModel = chaoxingViewModel,
                    onSwitchToAppsTab = { selectedTab = TAB_APPS },
                )
                selectedTab == TAB_APPS -> AppHubScreen(
                    scheduleViewModel = scheduleViewModel,
                    gradeViewModel = gradeViewModel,
                    examViewModel = examViewModel,
                    trainingPlanViewModel = trainingPlanViewModel,
                    emptyClassroomViewModel = emptyClassroomViewModel,
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
                        previousTab = selectedTab
                        selectedTab = TAB_PROFILE
                        profileScrollTarget = "bathroom"
                    },
                    onOpenAc = {
                        previousTab = selectedTab
                        selectedTab = TAB_PROFILE
                        profileScrollTarget = "ac"
                    },
                    onOpenLighting = {
                        previousTab = selectedTab
                        selectedTab = TAB_PROFILE
                        profileScrollTarget = "lighting"
                    },
                    onOpenInternet = {
                        previousTab = selectedTab
                        selectedTab = TAB_PROFILE
                        profileScrollTarget = "internet"
                    },
                    onOpenCardAnalytics = {
                        previousTab = selectedTab
                        selectedTab = TAB_PROFILE
                        openCardAnalytics = true
                    },
                    onOpenAppHub = {
                        previousTab = selectedTab
                        selectedTab = TAB_APPS
                    },
                    recentApps = recentApps,
                    onRecordApp = recordApp,
                    onNeedsLogin = onReauth
                )
            }
        }
    }
}
