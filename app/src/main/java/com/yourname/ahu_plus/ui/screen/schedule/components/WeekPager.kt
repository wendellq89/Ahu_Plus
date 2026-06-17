package com.yourname.ahu_plus.ui.screen.schedule.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 周滑动 Pager (修复版 — 每页独立内容)。
 *
 * 使用 HorizontalPager 包裹，[content] 接收 page (0-based)，由外部为每页构建
 * 对应周的 WeekGrid。超出可视范围的页面会被 Compose 回收。
 *
 * @param maxPage     最大页码 (maxWeek - 1)
 * @param currentPage 当前选中页 (selectedWeek - 1)
 * @param enabled     是否启用手势
 * @param onPageChanged 滑动到新页回调
 * @param content     页面内容 (page: 0..maxPage-1)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WeekPager(
    maxPage: Int,
    currentPage: Int,
    enabled: Boolean,
    onPageChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (page: Int) -> Unit,
) {
    if (maxPage <= 0) {
        content(currentPage)
        return
    }

    if (!enabled) {
        content(currentPage)
        return
    }

    val pagerState = rememberPagerState(
        initialPage = currentPage.coerceIn(0, maxPage - 1),
    ) { maxPage }

    // 用户滑动 → 通知外部
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page -> onPageChanged(page) }
    }

    // 外部 setSelectedWeek → 同步 Pager
    LaunchedEffect(currentPage) {
        if (pagerState.currentPage != currentPage && currentPage in 0 until maxPage) {
            pagerState.scrollToPage(currentPage)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        userScrollEnabled = enabled,
    ) { page ->
        content(page)
    }
}
