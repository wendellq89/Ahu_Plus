package com.yourname.ahu_plus.data.repository

import android.util.Base64
import android.util.Log
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.yourname.ahu_plus.data.GsonProvider
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.model.MarketComment
import com.yourname.ahu_plus.data.model.MarketCommentReplies
import com.yourname.ahu_plus.data.model.MarketNotice
import com.yourname.ahu_plus.data.model.MarketNoticeContent
import com.yourname.ahu_plus.data.model.MarketNoticePage
import com.yourname.ahu_plus.data.model.MarketNoticeTopic
import com.yourname.ahu_plus.data.model.MarketTopic
import com.yourname.ahu_plus.data.model.MarketUser
import com.yourname.ahu_plus.data.network.SecureHttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class MarketRepository(
    private val sessionManager: SessionManager
) {
    private val gson = GsonProvider.instance
    private val client = SecureHttpClientFactory.create()

    suspend fun getTopics(page: Int = 1): Result<List<MarketTopic>> = withContext(Dispatchers.IO) {
        requestMarketJson("$TOPICS_URL?page=$page").mapCatching(::parseTopics)
    }

    suspend fun getTopTopics(): Result<List<MarketTopic>> = withContext(Dispatchers.IO) {
        requestMarketJson(TOPICS_TOP_URL).mapCatching(::parseTopics)
    }

    suspend fun getTopic(topicId: Long): Result<MarketTopic> = withContext(Dispatchers.IO) {
        requestMarketJson("$TOPICS_URL/$topicId").mapCatching { body ->
            parseTopic(body) ?: throw Exception("帖子详情解析失败")
        }
    }

    suspend fun getComments(
        topicId: Long,
        page: Int = 1,
        sort: String = "hot"
    ): Result<List<MarketComment>> = withContext(Dispatchers.IO) {
        requestMarketJson("$COMMENTS_URL?topic_id=$topicId&sort=$sort&page=$page")
            .mapCatching(::parseComments)
    }

    suspend fun getCommentReplies(
        topicId: Long,
        commentId: Long,
        page: Int = 1,
        pageSize: Int = 6
    ): Result<MarketCommentReplies> = withContext(Dispatchers.IO) {
        requestMarketJson(
            "$COMMENTS_URL?topic_id=$topicId&comment_id=$commentId&sort=time&page=$page&page_size=$pageSize"
        ).mapCatching { body ->
            parseCommentReplies(body, page, pageSize)
        }
    }

    /**
     * 拉取用户通知（评论/回复/点赞）列表。
     *
     * 接口：GET https://api.zxs-bbs.cn/api/client/user_notices?page=N
     * Resp: {status, msg, data:{count, page, rows:[...]}}
     */
    suspend fun getNotices(page: Int = 1): Result<MarketNoticePage> = withContext(Dispatchers.IO) {
        requestMarketJson("$USER_NOTICES_URL?page=$page").mapCatching { body ->
            parseNotices(body, page)
        }
    }

    /**
     * 发布新帖。V1 仅支持纯文本，固定不发图片与 @ 提及。
     *
     * 接口参考：
     *  POST https://api.zxs-bbs.cn/api/client/topics
     *  Body: {"title","content","is_anon","link_type","link_people","link_info",
     *         "node_id","imgs","source","school_sub_address_id","is_ad_fake"}
     *  Resp: {"status":"success","code":200,"msg":"发布成功","data":{"id":...}}
     *
     * @return Result<Long> 成功时为新帖 topic id
     */
    suspend fun createTopic(
        title: String,
        content: String,
        nodeId: Long,
        isAnon: Boolean
    ): Result<Long> = withContext(Dispatchers.IO) {
        val payload = gson.toJson(JsonObject().apply {
            addProperty("title", title.ifBlank { "none" })
            addProperty("content", content)
            addProperty("is_anon", if (isAnon) 1 else 0)
            addProperty("link_type", 0)
            addProperty("link_people", "")
            addProperty("link_info", "")
            addProperty("node_id", nodeId)
            add("imgs", com.google.gson.JsonArray())
            addProperty("source", "xcx")
            addProperty("school_sub_address_id", "")
            addProperty("is_ad_fake", 0)
        })

        postMarketJson(TOPICS_URL, payload).mapCatching { body -> parseCreatedTopicId(body) }
    }

    /**
     * 发布评论或回复。
     *
     * 接口参考：
     *  POST https://api.zxs-bbs.cn/api/client/comments
     *  Body: {"is_fake","target_user_id","topic_id","comment_id","reply_id",
     *         "content","imgs","source","is_ad_fake"}
     *  Resp: {"status":"success","code":200,"msg":"发表成功","data":{...,"comment":{...}}}
     *
     * @param topicId   被评论的帖子 id
     * @param content   评论内容（不含空白校验）
     * @param commentId 0=评论帖子；>0=回复某条评论
     * @param replyId   commentId>0 时可指定回复到具体某条楼中楼
     * @param targetUserId 被回复人的 uuid（仅在回复场景下使用）
     * @return Result<MarketComment> 后端返回的完整评论对象
     */
    suspend fun createComment(
        topicId: Long,
        content: String,
        commentId: Long = 0L,
        replyId: Long = 0L,
        targetUserId: Long = 0L
    ): Result<MarketComment> = withContext(Dispatchers.IO) {
        val payload = gson.toJson(JsonObject().apply {
            addProperty("is_fake", 0)
            addProperty("target_user_id", targetUserId)
            addProperty("topic_id", topicId)
            addProperty("comment_id", commentId)
            addProperty("reply_id", replyId)
            addProperty("content", content)
            add("imgs", com.google.gson.JsonArray())
            addProperty("source", "xcx")
            addProperty("is_ad_fake", 0)
        })

        postMarketJson(COMMENTS_URL, payload).mapCatching { body -> parseCreatedComment(body, topicId) }
    }

    private fun requestMarketJson(url: String): Result<String> {
        val identity = sessionManager.getMarketApiIdentity()
            ?.takeIf { it.isNotBlank() } ?: return Result.failure(Exception("请先填写集市 API 身份字段"))

        return try {
            val request = Request.Builder().url(url).applyMarketHeaders(identity).build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                Log.i(TAG, "market ${response.request.url.encodedPath} HTTP ${response.code}")

                if (response.code == 401 || response.code == 403) {
                    return Result.failure(Exception("集市身份字段已失效，请重新获取"))
                }
                if (!response.isSuccessful) {
                    return Result.failure(Exception("集市加载失败 HTTP ${response.code}"))
                }

                Result.success(body)
            }
        } catch (e: Exception) {
            Log.e(TAG, "集市加载失败", e)
            Result.failure(e)
        }
    }

    private fun postMarketJson(url: String, jsonBody: String): Result<String> {
        val identity = sessionManager.getMarketApiIdentity()
            ?.takeIf { it.isNotBlank() } ?: return Result.failure(Exception("请先填写集市 API 身份字段"))

        return try {
            val body = jsonBody.toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(url)
                .post(body)
                .applyMarketHeaders(identity)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                Log.i(
                    TAG,
                    "market POST ${response.request.url.encodedPath} HTTP ${response.code}"
                )

                if (response.code == 401 || response.code == 403) {
                    return Result.failure(Exception("集市身份字段已失效，请重新获取"))
                }
                if (!response.isSuccessful) {
                    return Result.failure(
                        Exception("集市发布失败 HTTP ${response.code}：${responseBody.take(160)}")
                    )
                }

                Result.success(responseBody)
            }
        } catch (e: Exception) {
            Log.e(TAG, "集市发布失败", e)
            Result.failure(e)
        }
    }

    fun getSavedIdentity(): String? = sessionManager.getMarketApiIdentity()

    suspend fun saveIdentity(identity: String) {
        sessionManager.saveMarketApiIdentity(normalizeIdentity(identity))
    }

    suspend fun clearIdentity() {
        sessionManager.clearMarketApiIdentity()
    }

    private fun parseTopics(body: String): List<MarketTopic> {
        return parseRows(body).mapNotNull { element ->
            runCatching { gson.fromJson(element, MarketTopic::class.java) }.getOrNull()
        }.filter { it.id != 0L }
    }

    private fun parseTopic(body: String): MarketTopic? {
        val root = JsonParser.parseString(body)
        val data = parseData(root)
        return if (data.isJsonObject) {
            runCatching { gson.fromJson(data, MarketTopic::class.java) }.getOrNull()
        } else {
            parseTopics(body).firstOrNull()
        }
    }

    private fun parseComments(body: String): List<MarketComment> {
        return parseRows(body).mapNotNull { element ->
            runCatching { gson.fromJson(element, MarketComment::class.java) }.getOrNull()
        }.filter { it.id != 0L }
    }

    private fun parseNotices(body: String, fallbackPage: Int): MarketNoticePage {
        val root = JsonParser.parseString(body)
        if (!root.isJsonObject) throw Exception("通知列表返回格式异常")
        val obj = root.asJsonObject
        val status = obj.get("status")?.asString
        if (status != null && status != "success") {
            val msg = obj.get("msg")?.asString ?: "通知列表加载失败"
            throw Exception("通知列表加载失败：$msg")
        }
        val data = obj.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw Exception("通知列表响应缺少 data 字段")
        val count = data.get("count")?.asInt ?: 0
        val page = data.get("page")?.asInt ?: fallbackPage
        val rowsArray = data.getAsJsonArray("rows")
        val rows = rowsArray?.mapNotNull { element ->
            runCatching { parseNoticeElement(element) }.getOrNull()
        }.orEmpty()
        return MarketNoticePage(count = count, page = page, rows = rows)
    }

    private fun parseNoticeElement(element: JsonElement): MarketNotice? {
        if (!element.isJsonObject) return null
        val obj = element.asJsonObject
        return MarketNotice(
            id = obj.get("id")?.asLong ?: 0L,
            actionType = obj.get("actionType")?.asInt ?: 0,
            actionTypeText = obj.get("actionTypeText")?.asString.orEmpty(),
            type = obj.get("type")?.asString.orEmpty(),
            createTime = obj.get("createTime")?.asString.orEmpty(),
            topic = parseNoticeTopic(obj.get("topic")),
            senderUserInfo = parseUserInfo(obj.getAsJsonObject("senderUserInfo")),
            sendContent = parseNoticeContent(obj.get("sendContent")),
            targetContent = parseNoticeContent(obj.get("targetContent")),
            commentContent = parseNoticeContent(obj.get("commentContent")),
            replyContent = parseNoticeContent(obj.get("replyContent"))
        )
    }

    private fun parseNoticeTopic(element: JsonElement?): MarketNoticeTopic? {
        if (element == null || !element.isJsonObject) return null
        val obj = element.asJsonObject
        // topic.data.imgs → 展平为 List<String>
        val imgs = obj.getAsJsonObject("data")
            ?.getAsJsonArray("imgs")
            ?.mapNotNull { it.takeIf(JsonElement::isJsonPrimitive)?.asString }
            .orEmpty()
        return MarketNoticeTopic(
            id = obj.get("id")?.asLong ?: 0L,
            title = obj.get("title")?.asString.orEmpty(),
            content = obj.get("content")?.asString.orEmpty(),
            imgs = imgs
        )
    }

    private fun parseNoticeContent(element: JsonElement?): MarketNoticeContent? {
        if (element == null || !element.isJsonObject) return null
        val obj = element.asJsonObject
        return MarketNoticeContent(
            id = obj.get("id")?.asLong ?: 0L,
            content = obj.get("content")?.asString.orEmpty(),
            topicId = obj.get("topicId")?.asLong ?: 0L,
            commentId = obj.get("commentId")?.asLong ?: 0L,
            replyId = obj.get("replyId")?.asLong ?: 0L
        )
    }

    private fun parseUserInfo(element: JsonObject?): MarketUser? {
        if (element == null) return null
        return MarketUser(
            uuid = element.get("uuid")?.asLong ?: 0L,
            nickname = element.get("nickname")?.asString.orEmpty(),
            avatar = element.get("avatar")?.asString.orEmpty()
        )
    }

    private fun parseCommentReplies(
        body: String,
        fallbackPage: Int,
        fallbackPageSize: Int
    ): MarketCommentReplies {
        val root = JsonParser.parseString(body)
        val data = parseData(root)
        val rows = parseRows(body).mapNotNull { element ->
            runCatching { gson.fromJson(element, MarketComment::class.java) }.getOrNull()
        }.filter { it.id != 0L }

        return if (data.isJsonObject) {
            val obj = data.asJsonObject
            MarketCommentReplies(
                count = obj.get("count")?.asInt ?: rows.size,
                page = obj.get("page")?.asInt ?: fallbackPage,
                rows = rows,
                loading = false,
                pageSize = obj.get("pageSize")?.asInt ?: fallbackPageSize
            )
        } else {
            MarketCommentReplies(
                count = rows.size,
                page = fallbackPage,
                rows = rows,
                loading = false,
                pageSize = fallbackPageSize
            )
        }
    }

    private fun parseRows(body: String): List<JsonElement> {
        val root = JsonParser.parseString(body)
        val data = parseData(root)

        return when {
            data.isJsonArray -> data.asJsonArray.toList()
            data.isJsonObject && data.asJsonObject.has("rows") ->
                data.asJsonObject.getAsJsonArray("rows").toList()
            data.isJsonObject -> listOf(data)
            else -> emptyList()
        }
    }

    private fun parseData(root: JsonElement): JsonElement =
        if (root.isJsonObject && root.asJsonObject.has("data")) {
            root.asJsonObject.get("data")
        } else {
            root
        }

    private fun parseCreatedTopicId(body: String): Long {
        val root = JsonParser.parseString(body)
        if (!root.isJsonObject) {
            throw Exception("集市发布返回格式异常")
        }
        val obj = root.asJsonObject
        val status = obj.get("status")?.asString
        if (status != null && status != "success") {
            val msg = obj.get("msg")?.asString ?: "发布失败"
            throw Exception("集市发布失败：$msg")
        }
        val data = obj.get("data")
            ?: throw Exception("集市发布响应缺少 data 字段")
        val id = when {
            data.isJsonObject && data.asJsonObject.has("id") ->
                data.asJsonObject.get("id").asLong
            data.isJsonPrimitive && data.asJsonPrimitive.isNumber ->
                data.asLong
            else -> throw Exception("集市发布响应缺少 topic id")
        }
        if (id <= 0L) throw Exception("集市发布返回了无效的 topic id")
        return id
    }

    private fun parseCreatedComment(body: String, fallbackTopicId: Long): MarketComment {
        val root = JsonParser.parseString(body)
        if (!root.isJsonObject) {
            throw Exception("评论发布返回格式异常")
        }
        val obj = root.asJsonObject
        val status = obj.get("status")?.asString
        if (status != null && status != "success") {
            val msg = obj.get("msg")?.asString ?: "发表失败"
            throw Exception("评论发表失败：$msg")
        }
        val data = obj.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw Exception("评论发表响应缺少 data 字段")
        val commentElement = data.get("comment")?.takeIf { it.isJsonObject }
            ?: throw Exception("评论发表响应缺少 data.comment 字段")

        val parsed = runCatching {
            gson.fromJson(commentElement, MarketComment::class.java)
        }.getOrNull() ?: throw Exception("评论对象解析失败")

        // 响应里 topic 是嵌套对象，我们用本地 topicId 平铺回模型
        return parsed.copy(topicId = fallbackTopicId)
    }


    companion object {
        private const val TAG = "MarketRepo"
        private const val TOPICS_URL = "https://api.zxs-bbs.cn/api/client/topics"
        private const val TOPICS_TOP_URL = "https://api.zxs-bbs.cn/api/client/topics/top"
        private const val COMMENTS_URL = "https://api.zxs-bbs.cn/api/client/comments"
        private const val USER_NOTICES_URL = "https://api.zxs-bbs.cn/api/client/user_notices"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /**
         * 默认板块列表。V1 采用硬编码，待官方提供节点列表接口（推测
         * GET /api/client/nodes）后再切换为动态拉取。
         *
         * id 必须与后端 node_id 一一对应；name 仅用于 UI 展示。
         */
        val DEFAULT_NODES: List<com.yourname.ahu_plus.data.model.MarketNode> = listOf(
            com.yourname.ahu_plus.data.model.MarketNode(3727, "新鲜事"),
            com.yourname.ahu_plus.data.model.MarketNode(3728, "日常投稿"),
            com.yourname.ahu_plus.data.model.MarketNode(3729, "二手闲置"),
            com.yourname.ahu_plus.data.model.MarketNode(3730, "树洞"),
            com.yourname.ahu_plus.data.model.MarketNode(3731, "表白墙")
        )

        val DEFAULT_NODE_ID: Long = 3727L

        fun normalizeIdentity(identity: String): String {
            val trimmed = identity.trim()
            return if (trimmed.startsWith("Bearer ", ignoreCase = true)) trimmed else "Bearer $trimmed"
        }

        fun schoolFromIdentity(identity: String?): String? {
            val raw = identity?.trim()?.takeIf { it.isNotBlank() } ?: return null
            val token = if (raw.startsWith("Bearer ", ignoreCase = true)) raw.drop(7).trim() else raw
            val parts = token.split(".")
            if (parts.size < 2) return null
            return runCatching {
                val payload = parts[1] + "=".repeat((4 - parts[1].length % 4) % 4)
                val json = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
                JsonParser.parseString(json).asJsonObject.get("school")?.asString
            }.getOrNull()
        }
    }
}

private fun Request.Builder.applyMarketHeaders(identity: String): Request.Builder =
    header("Host", "api.zxs-bbs.cn")
        .header("Connection", "keep-alive")
        .header("xweb_xhr", "1")
        .header("Content-Type", "application/json")
        .header("Tenant", "7")
        .header("Accept", "*/*")
        .header("Sec-Fetch-Site", "cross-site")
        .header("Sec-Fetch-Mode", "cors")
        .header("Sec-Fetch-Dest", "empty")
        .header("Referer", "https://servicewechat.com/wxc56be16e96fc1df1/66/page-frame.html")
        .header("Accept-Language", "zh-CN,zh;q=0.9")
        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        .header("Authorization", MarketRepository.normalizeIdentity(identity))
