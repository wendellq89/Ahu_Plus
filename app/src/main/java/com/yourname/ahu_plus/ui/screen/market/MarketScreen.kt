package com.yourname.ahu_plus.ui.screen.market

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 集市模块顶层 Router —— 仅做"按 uiState 切换子页面 + 注入 BackHandler",
 * 真正的渲染分布在同包下的：
 *  - [MarketListScreen]   列表 / 搜索
 *  - [MarketDetailScreen] 帖子详情 + 评论 + 输入栏
 *  - [MarketComposeScreen] 发帖
 *  - [MarketSettingsScreen] 身份 / 屏蔽词 / 板块
 *  - [MarketHotScreen]    热榜
 *  - [MarketNoticesScreen] 消息通知
 *  - [MarketComponents]   共享的 Card / Avatar / Status 等
 */
@Composable
fun MarketScreen(viewModel: MarketViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val hotListState = rememberLazyListState()

    when {
        uiState.showSettings -> {
            BackHandler(onBack = viewModel::closeSettings)
            MarketSettingsScreen(
                uiState = uiState,
                onBack = viewModel::closeSettings,
                onIdentityChanged = viewModel::onIdentityInputChanged,
                onAddIdentity = viewModel::saveIdentity,
                onClearIdentities = viewModel::clearIdentity,
                onRemoveIdentity = viewModel::removeIdentity,
                onToggleIdentitySelection = viewModel::toggleIdentitySelection,
                onBlockPinnedChanged = viewModel::setBlockPinned,
                onKeywordInputChanged = viewModel::onKeywordInputChanged,
                onAddKeyword = viewModel::addBlockKeyword,
                onRemoveKeyword = viewModel::removeBlockKeyword,
                onToggleFilterNode = viewModel::toggleFilterNode,
                onListLayoutModeChange = viewModel::setListLayoutMode
            )
        }

        uiState.showCompose -> {
            BackHandler(onBack = viewModel::closeCompose)
            MarketComposeScreen(
                uiState = uiState,
                onBack = viewModel::closeCompose,
                onNodeMenuToggle = viewModel::onComposeNodeMenuToggle,
                onNodeSelected = viewModel::onComposeNodeSelected,
                onTitleChanged = viewModel::onComposeTitleChanged,
                onContentChanged = viewModel::onComposeContentChanged,
                onAnonChanged = viewModel::onComposeAnonChanged,
                onSubmit = viewModel::submitPost,
                onComposeSchoolSelected = viewModel::setComposeSchoolId
            )
        }

        uiState.selectedTopic != null -> {
            BackHandler(onBack = viewModel::closeTopic)
            MarketDetailScreen(
                uiState = uiState,
                onBack = viewModel::closeTopic,
                onRefresh = viewModel::retryDetail,
                onLoadMoreComments = viewModel::loadMoreComments,
                onLoadMoreReplies = viewModel::loadMoreReplies,
                onLoadFullCommentsForExport = viewModel::loadFullCommentsForExport,
                onCommentDraftChanged = viewModel::onCommentDraftChanged,
                onCommentSubmit = viewModel::submitComment,
                onCancelReply = viewModel::cancelReply,
                onStartReplyingToComment = viewModel::startReplyingToComment,
                onStartReplyingToReply = viewModel::startReplyingToReply,
                onCommentSuccessShown = viewModel::dismissPostCommentSuccessMessage
            )
        }

        uiState.showHotTopics -> {
            BackHandler(onBack = viewModel::closeHotTopics)
            MarketHotScreen(
                uiState = uiState,
                listState = hotListState,
                onBack = viewModel::closeHotTopics,
                onRefresh = viewModel::refreshHotTopics,
                onOpenTopic = viewModel::openTopic
            )
        }

        uiState.showNotices -> {
            BackHandler(onBack = viewModel::closeNotices)
            MarketNoticesScreen(
                uiState = uiState,
                onBack = viewModel::closeNotices,
                onRefresh = viewModel::refreshNotices,
                onLoadMore = viewModel::loadMoreNotices,
                onOpenTopic = viewModel::openTopic
            )
        }

        else -> {
            MarketListScreen(
                uiState = uiState,
                listState = listState,
                onIdentityChanged = viewModel::onIdentityInputChanged,
                onSaveIdentity = viewModel::saveIdentity,
                onClearIdentity = viewModel::clearIdentity,
                onRefresh = viewModel::refreshTopics,
                onLoadMore = viewModel::loadNextPage,
                onOpenHot = viewModel::openHotTopics,
                onOpenTopic = viewModel::openTopic,
                onOpenCompose = viewModel::openCompose,
                onOpenNotices = viewModel::openNotices,
                onOpenSettings = viewModel::openSettings,
                onOpenSearch = viewModel::openSearch,
                onSearchQueryChanged = viewModel::onSearchQueryChanged,
                onSearchSubmit = viewModel::submitSearch,
                onSearchClose = viewModel::closeSearch,
                onToggleSchool = viewModel::toggleIdentitySelection,
                onSelectAllSchools = viewModel::selectAllIdentities
            )
        }
    }
}
