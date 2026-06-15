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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.yourname.ahu_plus.ui.screen.schedule.ScheduleUiState
import com.yourname.ahu_plus.ui.screen.schedule.ScheduleViewModel
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
    onNeedsLogin: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val noticeUiState by noticeViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.needsLogin) {
        if (uiState.needsLogin) onNeedsLogin()
    }

    Scaffold(
        topBar = {
            TopAppBar(
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
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
                    TodayCourseCard(
                        uiState = uiState,
                        onOpenSchedule = onOpenSchedule,
                        onRefresh = viewModel::onRefresh
                    )
                }

                item {
                    AppDock(
                        onOpenSchedule = onOpenSchedule,
                        onOpenCard = onOpenCard
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
}

@Composable
private fun JwcNoticeSection(
    uiState: JwcNoticeUiState,
    onRefresh: () -> Unit,
    onToggleNotice: (JwcNotice) -> Unit,
    onOpenMore: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE07A5F).copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.material3.LocalContentColor provides Color(0xFFE07A5F)
                    ) {
                        Icon(Icons.Filled.Campaign, contentDescription = null)
                    }
                }
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
    val studentName = uiState.studentName?.takeIf { it.isNotBlank() } ?: "同学"
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
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(
                        text = "今天的课",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                    )
                    Text(
                        text = "${studentName}同学",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

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
                        text = if (todayItems.isEmpty()) "今天没课啦，可以慢慢安排自己的时间。"
                        else "今天后面没课啦，剩下的时间归你。",
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

@Composable
private fun CourseSummary(
    course: CourseDisplayItem,
    unitTimes: List<CourseUnit>
) {
    val timeText = courseTimeText(course, unitTimes)
    val roomText = course.room?.takeIf { it.isNotBlank() } ?: "教室待定"
    val teacherText = course.teacherNames.takeIf { it.isNotBlank() } ?: "教师待定"

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "接下来是 ${course.courseName}",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = "$timeText · $roomText",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = teacherText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.68f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AppDock(
    onOpenSchedule: () -> Unit,
    onOpenCard: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "常用应用",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AppDockItem(
                title = "课表",
                iconColor = Color(0xFF2F80ED),
                onClick = onOpenSchedule,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null)
            }
            AppDockItem(
                title = "校园卡",
                iconColor = Color(0xFF2A9D8F),
                onClick = onOpenCard,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null)
            }
            AppDockItem(
                title = "更多",
                iconColor = Color(0xFF6C63FF),
                onClick = {},
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.GridView, contentDescription = null)
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
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
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
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
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


