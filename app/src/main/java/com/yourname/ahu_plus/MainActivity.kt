package com.yourname.ahu_plus

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourname.ahu_plus.data.model.CheckResult
import com.yourname.ahu_plus.data.model.UpdateInfo
import com.yourname.ahu_plus.ui.components.UpdateDialog
import com.yourname.ahu_plus.ui.navigation.AppNavigation
import com.yourname.ahu_plus.ui.theme.AhuPlusTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        /**
         * deep-link extra key:由通知 / widget 等入口传入,
         * AppNavigation 据此决定初始跳转。
         */
        const val EXTRA_DEEP_LINK = "deep_link"

        /** 深链到课表页(课程提醒通知点击时使用) */
        const val DEEP_LINK_SCHEDULE = "schedule"

        /** 深链到成绩页 */
        const val DEEP_LINK_GRADE = "grade"
    }

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

            // ── 启动自动更新检查 ────────────────────────────
            var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
            val checkedAtStartup = remember { mutableStateOf(false) }
            if (!checkedAtStartup.value) {
                coroutineScope.launch {
                    checkedAtStartup.value = true
                    val result = app.updateManager.checkForUpdateWithIgnore()
                    if (result == CheckResult.UPDATE_AVAILABLE) {
                        updateInfo = app.updateManager.lastFetchedUpdateInfo
                    }
                }
            }

            // ── 下载进度定时刷新（每 500ms 读一次 Volatile 变量） ──
            var isDownloading by remember { mutableStateOf(false) }
            var downloadProgress by remember { mutableIntStateOf(0) }
            var showDownloadDialog by remember { mutableStateOf(false) }
            var updatingInfo by remember { mutableStateOf<UpdateInfo?>(null) }
            LaunchedEffect(Unit) {
                while (true) {
                    isDownloading = app.updateManager.isDownloading
                    downloadProgress = app.updateManager.downloadProgress
                    kotlinx.coroutines.delay(500)
                }
            }

            AhuPlusTheme(
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

                // ── 更新 Dialog ──────────────────────────────
                val dialogInfo = updatingInfo ?: updateInfo

                if (dialogInfo != null) {
                    val dl = isDownloading && showDownloadDialog
                    UpdateDialog(
                        info = dialogInfo,
                        downloading = dl,
                        downloadProgress = downloadProgress,
                        onUpdate = {
                            Log.d("MainActivity", "立即更新: starting download")
                            showDownloadDialog = true
                            updatingInfo = dialogInfo
                            app.updateManager.downloadApk(dialogInfo)
                        },
                        onLater = {
                            Log.d("MainActivity", "以后再说")
                            updateInfo = null
                            updatingInfo = null
                        },
                        onIgnore = {
                            updateInfo = null
                            updatingInfo = null
                            coroutineScope.launch {
                                app.sessionManager.saveIgnoredVersionCode(
                                    dialogInfo.latestVersionCode
                                )
                            }
                        },
                        onDismiss = {
                            Log.d("MainActivity", "dismiss")
                            updateInfo = null
                            updatingInfo = null
                        }
                    )
                }
            }
        }
    }
}
