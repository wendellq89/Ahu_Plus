package com.yourname.ahu_plus.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
import com.yourname.ahu_plus.ui.screen.autologin.AutoLoginScreen
import com.yourname.ahu_plus.ui.screen.autologin.AutoLoginViewModel
import com.yourname.ahu_plus.ui.screen.login.LoginScreen
import com.yourname.ahu_plus.ui.screen.login.LoginViewModel
import com.yourname.ahu_plus.ui.screen.main.MainScreen
import kotlinx.coroutines.launch

@Composable
fun AppNavigation(
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
    onThemeModeChange: (AppThemeMode) -> Unit
) {
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()
    var startRoute by remember { mutableStateOf<String?>(null) }

    // 启动决策:
    //  - 正在初始化 → 短暂显示 loading
    //  - 有凭据 → 进入 autologin(自动登录 + 失败时显示校徽重试页)
    //  - 无凭据 → 进入 login
    LaunchedEffect(Unit) {
        sessionManager.init()
        startRoute = if (sessionManager.hasCredentials()) "autologin" else "login"
    }

    if (startRoute == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "加载中...",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        return
    }

    NavHost(
        navController = navController,
        startDestination = startRoute!!
    ) {
        // ── 自动登录 + 重试页 ──────────────────────────
        composable("autologin") {
            val viewModel = remember {
                AutoLoginViewModel(sessionManager, casAuthRepository, ycardRepository)
            }
            AutoLoginScreen(
                viewModel = viewModel,
                onSuccess = {
                    navController.navigate("main") {
                        popUpTo("autologin") { inclusive = true }
                    }
                },
                onNoCredentials = {
                    navController.navigate("login") {
                        popUpTo("autologin") { inclusive = true }
                    }
                }
            )
        }

        // ── 手动登录页 ─────────────────────────────────
        composable("login") {
            val viewModel = remember { LoginViewModel(casAuthRepository, ycardRepository) }
            LoginScreen(
                viewModel = viewModel,
                savedUsername = sessionManager.getUsername(),
                savedPassword = sessionManager.getPassword(),
                onNavigateToHome = {
                    navController.navigate("main") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        // ── 主页(一卡通 + 课表)────────────────────────
        composable("main") {
            val navigateToLogin: () -> Unit = {
                navController.navigate("login") {
                    popUpTo("main") { inclusive = true }
                }
            }
            MainScreen(
                sessionManager = sessionManager,
                cardRepository = cardRepository,
                casAuthRepository = casAuthRepository,
                jwAuthRepository = jwAuthRepository,
                courseRepository = courseRepository,
                ycardRepository = ycardRepository,
                marketRepository = marketRepository,
                jwcNoticeRepository = jwcNoticeRepository,
                studentInfoRepository = studentInfoRepository,
                courseNoteRepository = courseNoteRepository,
                gradeRepository = gradeRepository,
                examRepository = examRepository,
                financeRepository = financeRepository,
                attendanceRepository = attendanceRepository,
                adwmhCardRepository = adwmhCardRepository,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                onReauth = {
                    coroutineScope.launch {
                        // 仅清除会话 Cookie,保留凭据 / 集市 token / 学生信息等本地数据
                        casAuthRepository.clearCookies()
                        jwAuthRepository.clearCookies()
                        ycardRepository.clearCookies()
                        adwmhCardRepository.clearCookies()
                        sessionManager.clearSession()
                        sessionManager.clearJwSession()
                        navigateToLogin()
                    }
                },
                onLogout = {
                    coroutineScope.launch {
                        // 完全退出:清除所有本地数据
                        casAuthRepository.clearCookies()
                        jwAuthRepository.clearCookies()
                        ycardRepository.clearCookies()
                        adwmhCardRepository.clearCookies()
                        sessionManager.clearAuthData()
                        navigateToLogin()
                    }
                }
            )
        }
    }
}
