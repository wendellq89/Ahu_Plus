package com.yourname.ahu_plus.ui.screen.dashboard

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Room
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourname.ahu_plus.data.model.JwcNotice
import com.yourname.ahu_plus.data.model.jw.CourseDisplayItem
import com.yourname.ahu_plus.data.model.jw.CourseUnit
import com.yourname.ahu_plus.data.model.jw.formatTime
import com.yourname.ahu_plus.data.model.jw.parseTimeMinutes
import com.yourname.ahu_plus.data.repository.CourseRepository
import com.yourname.ahu_plus.ui.components.AhuCard
import com.yourname.ahu_plus.ui.components.AhuIconBox
import com.yourname.ahu_plus.ui.components.AhuSectionTitle
import com.yourname.ahu_plus.ui.components.AhuShapes
import com.yourname.ahu_plus.ui.components.AhuTopAppBar
import com.yourname.ahu_plus.ui.screen.schedule.ScheduleUiState
import com.yourname.ahu_plus.ui.screen.schedule.ScheduleViewModel
import com.yourname.ahu_plus.ui.theme.AhuBlue
import com.yourname.ahu_plus.ui.theme.AhuGreen
import com.yourname.ahu_plus.ui.theme.AhuIndigo
import com.yourname.ahu_plus.ui.theme.AhuOrange
import com.yourname.ahu_plus.ui.theme.AhuRed
import com.yourname.ahu_plus.ui.theme.AhuTeal
import com.yourname.ahu_plus.ui.theme.AhuViolet
import org.json.JSONArray
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: ScheduleViewModel,
    noticeViewModel: JwcNoticeViewModel,
    onOpenSchedule: () -> Unit,
    onOpenCard: () -> Unit,
    onOpenNoticeList: () -> Unit,
    onOpenGrade: () -> Unit,
    onOpenExam: () -> Unit,
    onOpenBathroom: () -> Unit,
    onOpenAc: () -> Unit,
    onOpenLighting: () -> Unit,
    onOpenInternet: () -> Unit,
    onOpenCardAnalytics: () -> Unit,
    onOpenTrainingPlan: () -> Unit = {},
    onOpenEmptyClassroom: () -> Unit = {},
    onAddUserTask: () -> Unit = {},
    onToggleTask: (com.yourname.ahu_plus.data.model.task.RecentTaskItem) -> Unit = {},
    onAddTodayHomework: () -> Unit = {},
    onTodayRollCall: () -> Unit = {},
    onOpenAllTasks: () -> Unit = {},
    onNeedsLogin: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val noticeUiState by noticeViewModel.uiState.collectAsStateWithLifecycle()
    val recentTasks by viewModel.recentTasks.collectAsStateWithLifecycle()
    val todayHasSignIn by viewModel.todayHasSignIn.collectAsStateWithLifecycle()

    // 2026-06-17 Bug4: 弹"添加待办"对话框
    var showAddTaskDialog by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }
    // 2026-06-17 Bug9: 弹"查看全部近期任务"对话框
    var showAllTasksDialog by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }
    // 2026-06-17 Bug4 修复: 首页添加作业对话框
    var showTodayHomeworkDialog by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }

    LaunchedEffect(uiState.needsLogin) {
        if (uiState.needsLogin) onNeedsLogin()
    }

    Scaffold(
        topBar = {
            AhuTopAppBar(
                title = { Text("安大 Plus") },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.onRefresh()
                            noticeViewModel.loadNotices()
                        }
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    com.yourname.ahu_plus.ui.screen.dashboard.TodayCourseCard(
                        uiState = com.yourname.ahu_plus.ui.screen.dashboard.TodayCourseUiState(
                            todayItems = uiState.allActivities.mapNotNull { act ->
                                val start = act.startUnit ?: return@mapNotNull null
                                com.yourname.ahu_plus.data.model.jw.CourseDisplayItem(
                                    lessonId = act.lessonId ?: 0,
                                    courseName = act.courseName ?: "未知课程",
                                    courseCode = act.courseCode,
                                    teacherNames = act.teacherNames?.joinToString("、")
                                        ?: act.teachers?.joinToString("、") ?: "",
                                    room = act.room,
                                    weekday = act.weekday ?: 0,
                                    startUnit = start,
                                    endUnit = act.endUnit ?: start,
                                    weekIndexes = act.weekIndexes ?: emptyList(),
                                    weeksStr = act.weeksStr,
                                    startTime = act.startTime,
                                    endTime = act.endTime,
                                    courseType = act.courseType?.nameZh,
                                    credits = act.credits,
                                    campus = act.campus,
                                    colorIndex = 0,
                                )
                            },
                            unitTimes = uiState.unitTimes,
                        ),
                        onOpenSchedule = onOpenSchedule,
                        onRefresh = viewModel::onRefresh,
                        // 2026-06-17 Bug3/4 修复: 真正接上 ViewModel + 签到状态
                        onRollCall = { viewModel.addQuickSignInForToday() },
                        onAddHomework = { showTodayHomeworkDialog = true },
                        // Bug1 修复: 从 ViewModel 订阅 todayHasSignIn
                        todayHasRollCall = todayHasSignIn,
                    )
                }

                item {
                    com.yourname.ahu_plus.ui.screen.dashboard.UpcomingTasksCard(
                        tasks = recentTasks,
                        // 2026-06-17 Bug4 修复: + 号真正接上对话框
                        onToggleComplete = { viewModel.toggleRecentTask(it) },
                        onAdd = { showAddTaskDialog = true },
                        onViewAll = { showAllTasksDialog = true },
                    )
                }

                item {
                    AppDock(
                        onOpenSchedule = onOpenSchedule,
                        onOpenCard = onOpenCard,
                        onOpenGrade = onOpenGrade,
                        onOpenExam = onOpenExam,
                        onOpenNoticeList = onOpenNoticeList,
                        onOpenBathroom = onOpenBathroom,
                        onOpenAc = onOpenAc,
                        onOpenLighting = onOpenLighting,
                        onOpenInternet = onOpenInternet,
                        onOpenCardAnalytics = onOpenCardAnalytics,
                        onOpenTrainingPlan = onOpenTrainingPlan,
                        onOpenEmptyClassroom = onOpenEmptyClassroom
                    )
                }

                item {
                    JwcNoticeSection(
                        uiState = noticeUiState,
                        onRefresh = noticeViewModel::loadNotices,
                        onToggleNotice = noticeViewModel::toggleNotice,
                        onOpenMore = onOpenNoticeList
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            JwcHtmlLoader(
                url = noticeUiState.noticeFetchUrl,
                reloadKey = noticeUiState.noticeFetchKey,
                onHtml = noticeViewModel::onNoticeHtmlLoaded,
                onError = noticeViewModel::onNoticeHtmlError
            )

            val detailFetchUrl = noticeUiState.detailFetchUrl
            if (detailFetchUrl != null) {
                JwcHtmlLoader(
                    url = detailFetchUrl,
                    reloadKey = detailFetchUrl.hashCode(),
                    onHtml = { _, html -> noticeViewModel.onDetailHtmlLoaded(detailFetchUrl, html) },
                    onError = { _, message -> noticeViewModel.onDetailHtmlError(detailFetchUrl, message) }
                )
            }
        }
    }

    // 2026-06-17 Bug4: 添加待办对话框
    if (showAddTaskDialog) {
        com.yourname.ahu_plus.ui.screen.dashboard.AddUserTaskDialog(
            onDismiss = { showAddTaskDialog = false },
            onConfirm = { title, subtitle, dueAt ->
                viewModel.addUserTask(title, subtitle, dueAt)
                showAddTaskDialog = false
            },
        )
    }

    // 2026-06-17 Bug9: 全部近期任务对话框
    if (showAllTasksDialog) {
        AllTasksDialog(
            tasks = recentTasks,
            onToggle = { viewModel.toggleRecentTask(it) },
            onDeleteUserTask = { item ->
                viewModel.deleteUserTask(item.id.removePrefix("task:"))
            },
            onDeleteHomework = { item ->
                viewModel.deleteRecord(item.id.removePrefix("hw:"))
            },
            onAdd = { showAddTaskDialog = true; showAllTasksDialog = false },
            onDismiss = { showAllTasksDialog = false },
        )
    }

    // 2026-06-17 Bug4 修复: 首页添加作业对话框
    if (showTodayHomeworkDialog) {
        com.yourname.ahu_plus.ui.screen.schedule.components.AddHomeworkDialog(
            courseName = "今日作业",
            onDismiss = { showTodayHomeworkDialog = false },
            onConfirm = { text, deadline ->
                viewModel.addQuickHomeworkForToday(text, deadline)
                showTodayHomeworkDialog = false
            },
        )
    }
}

@Composable
private fun JwcNoticeSection(
    uiState: JwcNoticeUiState,
    onRefresh: () -> Unit,
    onToggleNotice: (JwcNotice) -> Unit,
    onOpenMore: () -> Unit
) {
    AhuCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AhuIconBox(
                    imageVector = Icons.Filled.Campaign,
                    tint = AhuOrange
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(
                        text = "教务通告",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "来自教务处通知公告",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onOpenMore) {
                    Text("更多")
                }
            }

            when {
                uiState.isLoading && uiState.notices.isEmpty() -> {
                    Row(
                        modifier = Modifier.padding(top = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "正在加载通告...",
                            modifier = Modifier.padding(start = 10.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                uiState.error != null && uiState.notices.isEmpty() -> {
                    Column(
                        modifier = Modifier.padding(top = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = uiState.error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        FilledTonalButton(onClick = onRefresh) {
                            Text("重新加载")
                        }
                    }
                }

                uiState.notices.isEmpty() -> {
                    Text(
                        text = "暂无通告",
                        modifier = Modifier.padding(top = 14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        uiState.notices.forEachIndexed { index, notice ->
                            if (index > 0) HorizontalDivider()
                            JwcNoticeItem(
                                notice = notice,
                                isExpanded = uiState.expandedUrl == notice.url,
                                detailState = uiState.details[notice.url],
                                onClick = { onToggleNotice(notice) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JwcNoticeItem(
    notice: JwcNotice,
    isExpanded: Boolean,
    detailState: NoticeDetailState?,
    onClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notice.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = if (isExpanded) 3 else 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (notice.date.isNotBlank()) {
                    Text(
                        text = notice.date,
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 2.dp, end = 2.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (detailState) {
                    NoticeDetailState.Loading, null -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "正在展开详情...",
                                modifier = Modifier.padding(start = 10.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    is NoticeDetailState.Success -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = detailState.detail.content.ifBlank { "详情页暂无正文内容" },
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    is NoticeDetailState.Error -> {
                        Text(
                            text = detailState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JwcHtmlLoader(
    url: String,
    reloadKey: Int,
    onHtml: (String, String) -> Unit,
    onError: (String, String) -> Unit
) {
    AndroidView(
        modifier = Modifier
            .size(1.dp)
            .alpha(0f),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.loadsImagesAutomatically = false
                settings.blockNetworkImage = true
                settings.userAgentString =
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
                webViewClient = object : WebViewClient() {
                    private var pageStartTime = 0L

                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                        pageStartTime = System.currentTimeMillis()
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        view.postDelayed(
                            { extractHtml(view, currentRequestUrl(view), onHtml, onError, pageStartTime) },
                            350
                        )
                    }

                    override fun onReceivedHttpError(
                        view: WebView,
                        request: WebResourceRequest,
                        errorResponse: WebResourceResponse
                    ) {
                        if (request.isForMainFrame && errorResponse.statusCode != 412) {
                            onError(
                                currentRequestUrl(view),
                                "教务处网站返回 HTTP ${errorResponse.statusCode}"
                            )
                        }
                    }
                }
            }
        },
        update = { webView ->
            val requestKey = "$url#$reloadKey"
            if (webView.tag != requestKey) {
                webView.tag = requestKey
                webView.loadUrl(url)
            }
        }
    )
}

private fun currentRequestUrl(webView: WebView): String {
    return (webView.tag as? String)?.substringBeforeLast("#") ?: webView.url.orEmpty()
}

private fun extractHtml(
    webView: WebView,
    expectedUrl: String,
    onHtml: (String, String) -> Unit,
    onError: (String, String) -> Unit,
    pageStartTime: Long
) {
    webView.evaluateJavascript(
        """
            (function() {
              var block = document.querySelector('#wp_news_w14, .news_list, .wp_articlecontent, .article, .wp_entry');
              return (block ? block.outerHTML : document.documentElement.outerHTML);
            })();
        """.trimIndent()
    ) { encoded ->
        val html = decodeJsString(encoded)
        if (html.isBlank()) {
            onError(expectedUrl, "教务处页面内容为空")
            return@evaluateJavascript
        }
        val isChallenge = html.contains("\$_ss=") && !html.contains("wp_news_w14") &&
            !html.contains("news_list") && !html.contains("wp_articlecontent")
        if (isChallenge && System.currentTimeMillis() - pageStartTime < 12_000L) {
            webView.postDelayed(
                { extractHtml(webView, expectedUrl, onHtml, onError, pageStartTime) },
                600
            )
            return@evaluateJavascript
        }
        if (isChallenge) {
            onError(expectedUrl, "教务处网站校验未通过，请稍后重试")
        } else {
            onHtml(expectedUrl, html)
        }
    }
}

private fun decodeJsString(value: String?): String {
    if (value.isNullOrBlank() || value == "null") return ""
    return runCatching { JSONArray("[$value]").getString(0) }.getOrDefault(value)
}

@Composable
private fun TodayCourseCard(
    uiState: ScheduleUiState,
    onOpenSchedule: () -> Unit,
    onRefresh: () -> Unit
) {
    val todayItems = remember(
        uiState.allActivities,
        uiState.currentWeek,
        uiState.lessons
    ) {
        CourseRepository.toDisplayItems(
            activities = uiState.allActivities,
            selectedWeek = uiState.currentWeek,
            getDataLessons = uiState.lessons
        ).filter { it.weekday == LocalDate.now().dayOfWeek.value }
            .sortedBy { it.startUnit }
    }
    val nextCourse = remember(todayItems, uiState.unitTimes) {
        todayItems.firstOrNull { isNotStarted(it, uiState.unitTimes) }
    }

    Card(
        shape = AhuShapes.LargeCard,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // 顶部彩色条 + 标签 —— 与下方正文区视觉分割
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (nextCourse != null) "下节课" else "今天的安排",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
                if (nextCourse != null) {
                    Spacer(modifier = Modifier.weight(1f))
                    CountdownChip(course = nextCourse, unitTimes = uiState.unitTimes)
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "第 ${uiState.currentWeek} 周",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when {
                    uiState.isLoading && uiState.allActivities.isEmpty() -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "正在同步课表...",
                                modifier = Modifier.padding(start = 10.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    uiState.error != null && uiState.allActivities.isEmpty() -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = uiState.error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            FilledTonalButton(onClick = onRefresh) {
                                Text("重新加载课表")
                            }
                        }
                    }

                    nextCourse != null -> {
                        CourseSummary(
                            course = nextCourse,
                            unitTimes = uiState.unitTimes
                        )
                    }

                    else -> {
                        Text(
                            text = if (todayItems.isEmpty()) "今天没课，可以慢慢安排。"
                            else "今天后面没课，剩下的时间归你。",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenSchedule)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "查看完整课表",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Filled.ChevronRight, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun CountdownChip(
    course: CourseDisplayItem,
    unitTimes: List<CourseUnit>
) {
    val minutes = courseStartMinutes(course, unitTimes) ?: return

    // 每 30 秒 tick 一次，驱动倒计时动态刷新
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            tick++
        }
    }
    @Suppress("UNUSED_EXPRESSION") tick // 读取 tick 确保 LaunchedEffect 触发重组

    val now = LocalTime.now()
    val diff = minutes - (now.hour * 60 + now.minute)
    val (label, color) = when {
        diff < 0 -> "已开始" to MaterialTheme.colorScheme.error
        diff < 60 -> "${diff} 分钟后" to MaterialTheme.colorScheme.error
        diff < 180 -> "${diff / 60} 小时后" to MaterialTheme.colorScheme.tertiary
        else -> "${diff / 60}h${diff % 60}m" to MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.20f)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CourseSummary(
    course: CourseDisplayItem,
    unitTimes: List<CourseUnit>
) {
    val timeText = courseTimeText(course, unitTimes)
    val roomText = course.room?.takeIf { it.isNotBlank() } ?: "教室待定"
    val teacherText = course.teacherNames.takeIf { it.isNotBlank() } ?: "教师待定"

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 课程名 —— 整张卡的视觉焦点
        Text(
            text = course.courseName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        // 时间 / 地点 —— 同一行带图标
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InfoIconText(
                icon = Icons.Filled.AccessTime,
                text = timeText,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            InfoIconText(
                icon = Icons.Filled.LocationOn,
                text = roomText,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        // 教师 —— 单独一行，弱化
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = teacherText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun InfoIconText(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = tint.copy(alpha = 0.75f)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = tint,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun AppDock(
    onOpenSchedule: () -> Unit,
    onOpenCard: () -> Unit,
    onOpenGrade: () -> Unit,
    onOpenExam: () -> Unit,
    onOpenNoticeList: () -> Unit,
    onOpenBathroom: () -> Unit,
    onOpenAc: () -> Unit,
    onOpenLighting: () -> Unit,
    onOpenInternet: () -> Unit,
    onOpenCardAnalytics: () -> Unit,
    onOpenTrainingPlan: () -> Unit = {},
    onOpenEmptyClassroom: () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AhuSectionTitle(text = "常用应用")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AppDockItem(
                title = "课表",
                iconColor = AhuBlue,
                onClick = onOpenSchedule,
                modifier = Modifier.width(78.dp)
            ) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null)
            }
            AppDockItem(
                title = "成绩",
                iconColor = AhuRed,
                onClick = onOpenGrade,
                modifier = Modifier.width(78.dp)
            ) {
                Icon(Icons.Filled.Grade, contentDescription = null)
            }
            AppDockItem(
                title = "考试",
                iconColor = AhuOrange,
                onClick = onOpenExam,
                modifier = Modifier.width(78.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.EventNote, contentDescription = null)
            }
            AppDockItem(
                title = "消费分析",
                iconColor = AhuViolet,
                onClick = onOpenCardAnalytics,
                modifier = Modifier.width(78.dp)
            ) {
                Icon(Icons.Filled.Assessment, contentDescription = null)
            }
            AppDockItem(
                title = "浴室",
                iconColor = AhuTeal,
                onClick = onOpenBathroom,
                modifier = Modifier.width(78.dp)
            ) {
                Icon(Icons.Filled.WaterDrop, contentDescription = null)
            }
            AppDockItem(
                title = "空调",
                iconColor = AhuBlue,
                onClick = onOpenAc,
                modifier = Modifier.width(78.dp)
            ) {
                Icon(Icons.Filled.AcUnit, contentDescription = null)
            }
            AppDockItem(
                title = "照明",
                iconColor = AhuOrange,
                onClick = onOpenLighting,
                modifier = Modifier.width(78.dp)
            ) {
                Icon(Icons.Filled.Lightbulb, contentDescription = null)
            }
            AppDockItem(
                title = "网费",
                iconColor = AhuIndigo,
                onClick = onOpenInternet,
                modifier = Modifier.width(78.dp)
            ) {
                Icon(Icons.Filled.Wifi, contentDescription = null)
            }
            AppDockItem(
                title = "培养方案",
                iconColor = Color(0xFF6C63FF),
                onClick = onOpenTrainingPlan,
                modifier = Modifier.width(78.dp)
            ) {
                Icon(Icons.Filled.School, contentDescription = null)
            }
            AppDockItem(
                title = "空教室",
                iconColor = AhuGreen,
                onClick = onOpenEmptyClassroom,
                modifier = Modifier.width(78.dp)
            ) {
                Icon(Icons.Filled.Room, contentDescription = null)
            }
            AppDockItem(
                title = "通告",
                iconColor = AhuViolet,
                onClick = onOpenNoticeList,
                modifier = Modifier.width(78.dp)
            ) {
                Icon(Icons.Filled.Campaign, contentDescription = null)
            }
        }
    }
}

@Composable
private fun AppDockItem(
    title: String,
    iconColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = AhuShapes.Card,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
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
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

private fun isNotStarted(
    course: CourseDisplayItem,
    unitTimes: List<CourseUnit>
): Boolean {
    val minutes = courseStartMinutes(course, unitTimes) ?: return true
    val now = LocalTime.now()
    return minutes >= now.hour * 60 + now.minute
}

private fun courseTimeText(
    course: CourseDisplayItem,
    unitTimes: List<CourseUnit>
): String {
    val unitMap = unitTimes.associateBy { it.indexNo }
    val start = course.startTime?.takeIf { it.isNotBlank() }
        ?: unitMap[course.startUnit]?.startTimeStr()
    val end = course.endTime?.takeIf { it.isNotBlank() }
        ?: unitMap[course.endUnit]?.endTimeStr()

    return when {
        !start.isNullOrBlank() && !end.isNullOrBlank() -> "${formatTime(start)}-${formatTime(end)}"
        !start.isNullOrBlank() -> formatTime(start)
        else -> "第 ${course.startUnit}-${course.endUnit} 节"
    }
}

private fun courseStartMinutes(
    course: CourseDisplayItem,
    unitTimes: List<CourseUnit>
): Int? {
    val text = course.startTime?.takeIf { it.isNotBlank() }
        ?: unitTimes.firstOrNull { it.indexNo == course.startUnit }?.startTimeStr()
    return parseTimeMinutes(text)
}
