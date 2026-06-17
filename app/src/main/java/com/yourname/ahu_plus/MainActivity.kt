package com.yourname.ahu_plus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourname.ahu_plus.ui.navigation.AppNavigation
import com.yourname.ahu_plus.ui.theme.Ahu_PlusTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as AhuPlusApplication
        setContent {
            val themeMode by app.sessionManager.themeModeFlow.collectAsStateWithLifecycle(
                initialValue = app.sessionManager.getThemeMode()
            )
            val systemDarkTheme = isSystemInDarkTheme()
            val coroutineScope = rememberCoroutineScope()

            Ahu_PlusTheme(
                darkTheme = themeMode.shouldUseDarkTheme(systemDarkTheme)
            ) {
                AppNavigation(
                    sessionManager = app.sessionManager,
                    cardRepository = app.cardRepository,
                    casAuthRepository = app.casAuthRepository,
                    jwAuthRepository = app.jwAuthRepository,
                    courseRepository = app.courseRepository,
                    ycardRepository = app.ycardRepository,
                    marketRepository = app.marketRepository,
                    jwcNoticeRepository = app.jwcNoticeRepository,
                    studentInfoRepository = app.studentInfoRepository,
                    courseNoteRepository = app.courseNoteRepository,
                    gradeRepository = app.gradeRepository,
                    examRepository = app.examRepository,
                    financeRepository = app.financeRepository,
                    attendanceRepository = app.attendanceRepository,
                    adwmhCardRepository = app.adwmhCardRepository,
                    themeMode = themeMode,
                    onThemeModeChange = { newThemeMode ->
                        coroutineScope.launch {
                            app.sessionManager.saveThemeMode(newThemeMode)
                        }
                    }
                )
            }
        }
    }
}
