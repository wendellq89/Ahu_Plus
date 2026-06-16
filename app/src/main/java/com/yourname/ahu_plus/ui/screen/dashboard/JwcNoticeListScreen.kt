package com.yourname.ahu_plus.ui.screen.dashboard

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourname.ahu_plus.data.model.JwcNotice
import com.yourname.ahu_plus.ui.components.AhuTopAppBar
import com.yourname.ahu_plus.util.BrowserOpener
import org.json.JSONArray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JwcNoticeListScreen(
    viewModel: JwcNoticeListViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            AhuTopAppBar(
                title = { Text("通知公告") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::loadFirstPage) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NoticeListBody(
                uiState = uiState,
                onOpen = { notice -> BrowserOpener.open(context, viewModel.noticeUrl(notice)) },
                onLoadMore = viewModel::loadNextPage,
                onRetry = viewModel::loadFirstPage
            )

            // 离屏 WebView:负责拉取分页 HTML,避开教务处 JS 反爬
            val fetchUrl = uiState.noticeFetchUrl
            if (fetchUrl != null) {
                JwcListHtmlLoader(
                    url = fetchUrl,
                    reloadKey = uiState.noticeFetchKey,
                    onHtml = viewModel::onNoticeHtmlLoaded,
                    onError = viewModel::onNoticeHtmlError
                )
            }
        }
    }
}

@Composable
private fun NoticeListBody(
    uiState: JwcNoticeListUiState,
    onOpen: (JwcNotice) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit
) {
    val listState = rememberLazyListState()

    // 滚到倒数第 3 项以内就触发加载下一页,避免用户看到列表空洞
    val shouldLoadMore by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            total > 0 && lastVisible >= total - 3
        }
    }
    LaunchedEffect(shouldLoadMore, uiState.hasMore, uiState.isLoadingMore, uiState.isLoading) {
        if (shouldLoadMore && uiState.hasMore && !uiState.isLoadingMore && !uiState.isLoading) {
            onLoadMore()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        when {
            uiState.isLoading && uiState.notices.isEmpty() -> {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 80.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text(
                            text = "正在加载通知公告...",
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                }
            }

            uiState.error != null && uiState.notices.isEmpty() -> {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 80.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = uiState.error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        FilledTonalButton(onClick = onRetry) {
                            Text("重新加载")
                        }
                    }
                }
            }

            else -> {
                items(items = uiState.notices, key = { it.url }) { notice ->
                    NoticeRow(notice = notice, onClick = { onOpen(notice) })
                }
                if (uiState.isLoadingMore || (uiState.hasMore && uiState.notices.isNotEmpty() && !uiState.isLoading)) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "加载下一页...",
                                    modifier = Modifier.padding(start = 10.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else if (!uiState.hasMore && uiState.notices.isNotEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "已经到底啦",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeRow(
    notice: JwcNotice,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .padding(end = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Campaign,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notice.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (notice.date.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = notice.date,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 离屏 WebView:只为拉取 HTML 绕过教务处 JS 反爬,正常情况下 UI 看不到。
 */
@Composable
private fun JwcListHtmlLoader(
    url: String,
    reloadKey: Int,
    onHtml: (String, String) -> Unit,
    onError: (String, String) -> Unit
) {
    AndroidView(
        modifier = Modifier
            .size(1.dp)
            .alpha(0f),
        factory = { ctx ->
            WebView(ctx).apply {
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
                            { extractListHtml(view, currentRequestUrl(view), onHtml, onError, pageStartTime) },
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

private fun extractListHtml(
    webView: WebView,
    expectedUrl: String,
    onHtml: (String, String) -> Unit,
    onError: (String, String) -> Unit,
    pageStartTime: Long
) {
    webView.evaluateJavascript(
        """
            (function() {
              var block = document.querySelector('.news_list, #wp_news_w14, .wp_articlecontent, .article');
              return (block ? block.outerHTML : document.documentElement.outerHTML);
            })();
        """.trimIndent()
    ) { encoded ->
        val html = decodeJsString(encoded)
        if (html.isBlank()) {
            onError(expectedUrl, "教务处页面内容为空")
            return@evaluateJavascript
        }
        val isChallenge = html.contains("\$_ss=") && !html.contains("news_list") &&
            !html.contains("wp_news_w14") && !html.contains("wp_articlecontent")
        if (isChallenge && System.currentTimeMillis() - pageStartTime < 12_000L) {
            webView.postDelayed(
                { extractListHtml(webView, expectedUrl, onHtml, onError, pageStartTime) },
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
