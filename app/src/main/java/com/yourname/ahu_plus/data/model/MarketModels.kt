package com.yourname.ahu_plus.data.model

import com.google.gson.annotations.SerializedName

data class MarketTopic(
    val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val status: Int = 0,
    val imgs: List<String> = emptyList(),
    val node: String = "",
    val isAnon: Int = 0,
    val viewCount: Int = 0,
    @SerializedName("is_top")
    val isTop: Int = 0,
    val createTime: String = "",
    val userInfo: MarketUser? = null,
    val schoolSubAddress: String? = null,
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    /**
     * 列表接口返回的「前两条热门评论」(如果后端有给)。
     * - 默认 emptyList() 兜底,Gson 宽容解析接口不返回也不会报错
     * - 多个 SerializedName 兜底不同字段名(top_comments / preview_comments / hot_comments)
     * - 嵌套 List<MarketComment> 解析会被 `JsonUtils.parseRowsSafe` 的
     *   `isFilteredZeroId` 过滤掉 id=0 的脏数据,符合「接口字段不一定返回」的现状
     */
    @SerializedName(value = "top_comments", alternate = ["preview_comments", "hot_comments"])
    val topComments: List<MarketComment> = emptyList()
)

/**
 * 校园身份标识，对应一个 Bearer JWT token。
 * 每个身份代表一个校区（校园），从 JWT payload 的 `school` 字段自动识别学校名称。
 */
data class MarketIdentity(
    val id: String,
    val token: String,
    val school: String? = null
)

data class MarketUser(
    val uuid: Long = 0,
    val nickname: String = "",
    val avatar: String = ""
)

data class MarketComment(
    val id: Long = 0,
    val topicId: Long = 0,
    val content: String = "",
    val createTime: String = "",
    val imgs: List<String> = emptyList(),
    val userInfo: MarketUser? = null,
    val pickUserInfo: MarketUser? = null,
    val likeCount: Int = 0,
    val replyCount: Int = 0,
    val replies: List<MarketComment> = emptyList(),
    @SerializedName("replys")
    val replys: MarketCommentReplies? = null
) {
    val visibleReplies: List<MarketComment>
        get() = replies.ifEmpty { replys?.rows.orEmpty() }

    val visibleReplyCount: Int
        get() = maxOf(replyCount, replys?.count ?: 0, visibleReplies.size)
}

data class MarketCommentReplies(
    val count: Int = 0,
    val page: Int = 0,
    val rows: List<MarketComment> = emptyList(),
    val loading: Boolean = false,
    val pageSize: Int = 0
)

/**
 * 集市板块（node）。当前版本采用硬编码方式（待官方提供节点列表接口后再切换）。
 *
 * @param id   板块的 node_id，发帖时直接透传给后端
 * @param name 板块显示名
 */
data class MarketNode(
    val id: Long,
    val name: String
)

/**
 * 单条用户通知（消息）。来自 `GET /api/client/user_notices`。
 *
 * 字段命名贴近后端原始 JSON 字段（snake_case → camelCase 后保留原语义）：
 *  - actionType  2=评论主题 3=回复评论 4=点赞主题 6=点赞回复（推断）
 *  - type        "comment" / "like"，UI 用来选择图标
 *  - actionTypeText 后端给的中文动作文案（"回复了你" 等）
 *  - topic       关联主题的精简信息
 *  - sendContent / targetContent / commentContent / replyContent
 *                后端四选一填充，分别对应"发出的内容"和"被作用的原内容"
 */
data class MarketNotice(
    val id: Long = 0,
    val actionType: Int = 0,
    val actionTypeText: String = "",
    val type: String = "",
    val createTime: String = "",
    val topic: MarketNoticeTopic? = null,
    val senderUserInfo: MarketUser? = null,
    val sendContent: MarketNoticeContent? = null,
    val targetContent: MarketNoticeContent? = null,
    val commentContent: MarketNoticeContent? = null,
    val replyContent: MarketNoticeContent? = null
) {
    /** 用于列表里展示的"动作对象"摘要：发出方/被作用方的纯文本。 */
    val bodyText: String
        get() = sendContent?.content
            ?: targetContent?.content
            ?: commentContent?.content
            ?: replyContent?.content
            ?: ""

    /** 是否为点赞类通知。 */
    val isLike: Boolean
        get() = type == "like" || actionType == 4 || actionType == 6
}

/** 通知里嵌入的精简主题。`data.imgs` 已展平成 List<String>。 */
data class MarketNoticeTopic(
    val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val imgs: List<String> = emptyList()
)

/** 通知里嵌入的内容片段（评论/回复/帖子）。只保留 UI 关心的字段。 */
data class MarketNoticeContent(
    val id: Long = 0,
    val content: String = "",
    val topicId: Long = 0,
    val commentId: Long = 0,
    val replyId: Long = 0
)

/** 通知列表分页响应。 */
data class MarketNoticePage(
    val count: Int = 0,
    val page: Int = 0,
    val rows: List<MarketNotice> = emptyList()
)
