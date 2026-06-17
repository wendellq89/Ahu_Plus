package com.yourname.ahu_plus.ui.screen.market

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.yourname.ahu_plus.data.model.MarketComment
import com.yourname.ahu_plus.data.model.MarketTopic
import com.yourname.ahu_plus.data.model.MarketUser
import com.yourname.ahu_plus.data.network.SecureHttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

internal object MarketExportUtils {
    private const val ALBUM = "AhuPlus"
    private val client = SecureHttpClientFactory.create(readTimeoutSec = 30)

    private data class CommentLayout(
        val comment: MarketComment,
        val authorLine: Float,    // 作者头像 + 名字 + meta 一行高度
        val bodyLayout: StaticLayout,
        val imageHeight: Int,     // 评论图片总高度（含间距）
        val replies: List<ReplyLayout>
    )

    private data class ReplyLayout(
        val reply: MarketComment,
        val authorLine: Float,
        val bodyLayout: StaticLayout
    )

    suspend fun saveRemoteImage(context: Context, imageUrl: String): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val data = downloadImage(imageUrl)
            val mimeType = data.mimeType ?: "image/jpeg"
            val extension = extensionFor(mimeType, imageUrl)
            saveBytesToGallery(
                context = context,
                bytes = data.bytes,
                displayName = "ahu_market_${System.currentTimeMillis()}.$extension",
                mimeType = mimeType
            )
        }
    }

    /**
     * 导出完整帖子详情：包含帖子正文、全部评论及楼中楼回复。
     *
     * @param comments 已经调用 [com.yourname.ahu_plus.data.repository.MarketRepository.loadAllCommentsWithReplies]
     *                  拉到的全量评论列表；导出期间不会重复分页。
     */
    suspend fun exportTopicDetail(
        context: Context,
        topic: MarketTopic,
        comments: List<MarketComment>,
        school: String?
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = renderTopicDetailBitmap(context, topic, comments, school)
            saveBitmapToGallery(
                context = context,
                bitmap = bitmap,
                displayName = "ahu_market_topic_${topic.id}_${System.currentTimeMillis()}.png"
            )
        }
    }

    private fun downloadImage(url: String): DownloadedImage {
        val request = Request.Builder()
            .url(normalizeImageUrl(url))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("图片下载失败 HTTP ${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("图片响应为空")
            return DownloadedImage(
                bytes = body.bytes(),
                mimeType = body.contentType()?.toString()?.substringBefore(";")
            )
        }
    }

    private fun saveBitmapToGallery(context: Context, bitmap: Bitmap, displayName: String): Uri {
        val bytes = java.io.ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        return saveBytesToGallery(context, bytes, displayName, "image/png")
    }

    private fun saveBytesToGallery(
        context: Context,
        bytes: ByteArray,
        displayName: String,
        mimeType: String
    ): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("无法创建相册文件")
            try {
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: throw IllegalStateException("无法写入相册文件")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                ALBUM
            )
            if (!dir.exists() && !dir.mkdirs()) {
                throw IllegalStateException("无法创建相册目录")
            }
            val file = File(dir, displayName)
            FileOutputStream(file).use { it.write(bytes) }
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mimeType), null)
            Uri.fromFile(file)
        }
    }

    // ══════════════════════════════════════════════════════
    //  渲染：帖子正文 + 评论 + 楼中楼
    // ══════════════════════════════════════════════════════

    private fun renderTopicDetailBitmap(
        context: Context,
        topic: MarketTopic,
        comments: List<MarketComment>,
        school: String?
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        fun dp(value: Float): Float = value * density

        // 导出宽度按手机实际像素的 1.5 倍动态计算,保证内容区接近浏览时的视觉宽度。
        // 3x density 设备 (1080px 屏) → 1620px,留出 outerPadding/cardPadding 后约 1500px 内容。
        val width = max(1080, (context.resources.displayMetrics.widthPixels * 1.5f).roundToInt())
        val outerPadding = dp(28f)
        val cardPadding = dp(32f)
        val contentWidth = (width - outerPadding * 2 - cardPadding * 2).roundToInt()

        // 预下载帖子图片与所有评论 / 回复图片
        val topicImages = topic.imgs.mapNotNull { url ->
            runCatching { decodeBitmapFromUrl(url) }.getOrNull()
        }
        val commentImages: Map<String, Bitmap> = comments.flatMap { c ->
            (listOf(c) + c.visibleReplies).flatMap { mr -> mr.imgs }
        }.distinct().associateWith { url ->
            runCatching { decodeBitmapFromUrl(url) }.getOrNull()
        }.filterValues { it != null }.mapValues { it.value!! }
        val avatarBitmaps: Map<String, Bitmap> = collectAvatarUrls(topic, comments)
            .associateWith { url -> runCatching { decodeBitmapFromUrl(url) }.getOrNull() }
            .filterValues { it != null }
            .mapValues { it.value!! }

        // 画笔
        val titlePaint = textPaint(color = Color.rgb(24, 24, 27), textSize = dp(24f), fakeBold = true)
        val bodyPaint = textPaint(color = Color.rgb(39, 39, 42), textSize = dp(18f))
        val metaPaint = textPaint(color = Color.rgb(82, 82, 91), textSize = dp(14f))
        val footerPaint = textPaint(color = Color.rgb(113, 113, 122), textSize = dp(14f))
        val authorPaint = textPaint(color = Color.rgb(24, 24, 27), textSize = dp(17f), fakeBold = true)
        val commentAuthorPaint = textPaint(color = Color.rgb(24, 24, 27), textSize = dp(16f), fakeBold = true)
        val replyAuthorPaint = textPaint(color = Color.rgb(63, 63, 70), textSize = dp(14f), fakeBold = true)
        val commentMetaPaint = textPaint(color = Color.rgb(113, 113, 122), textSize = dp(12f))
        val commentBodyPaint = textPaint(color = Color.rgb(39, 39, 42), textSize = dp(15f))
        val replyBodyPaint = textPaint(color = Color.rgb(63, 63, 70), textSize = dp(13f))
        val replyPickPaint = textPaint(color = Color.rgb(113, 113, 122), textSize = dp(12f))
        val sectionTitlePaint = textPaint(color = Color.rgb(24, 24, 27), textSize = dp(20f), fakeBold = true)

        // ── 帖子正文部分 ──
        val title = topic.title.takeIf { it.isNotBlank() && it != "none" }.orEmpty()
        val content = topic.content.ifBlank { "无正文" }
        val titleLayout = title.takeIf { it.isNotBlank() }?.let { staticLayout(it, titlePaint, contentWidth) }
        val contentLayout = staticLayout(content, bodyPaint, contentWidth)
        // 帖子图片高度（含 12dp 间隔），与绘制循环保持一致
        var topicImagesTotalHeight = 0
        topicImages.forEachIndexed { index, bitmap ->
            val aspectHeight = contentWidth * bitmap.height / bitmap.width.coerceAtLeast(1).toFloat()
            val target = aspectHeight.coerceIn(dp(180f), dp(520f)).roundToInt()
            topicImagesTotalHeight += target + dp(12f).roundToInt()
        }

        var cardHeight = cardPadding * 2 + dp(52f) + dp(18f)
        if (titleLayout != null) cardHeight += titleLayout.height + dp(12f)
        cardHeight += contentLayout.height + dp(16f)
        cardHeight += topicImagesTotalHeight
        cardHeight += dp(32f)

        // ── 评论部分 ──
        val commentLayouts = comments.map { comment ->
            val body = comment.content.ifBlank { "无内容" }
            val bodyLayout = staticLayout(body, commentBodyPaint, contentWidth)
            val imageH = comment.imgs.mapIndexed { idx, url ->
                val bitmap = commentImages[url]
                if (bitmap == null) 0
                else {
                    val aspectHeight = contentWidth * bitmap.height / bitmap.width.coerceAtLeast(1).toFloat()
                    val target = aspectHeight.coerceIn(dp(120f), dp(420f)).roundToInt()
                    target + if (idx != comment.imgs.lastIndex) dp(8f).roundToInt() else 0
                }
            }.sum()
            val replyLayouts = comment.visibleReplies.map { reply ->
                val rBody = reply.content.ifBlank { "无内容" }
                val rBodyLayout = staticLayout(rBody, replyBodyPaint, contentWidth)
                ReplyLayout(reply, dp(22f), rBodyLayout)
            }
            CommentLayout(comment, dp(36f), bodyLayout, imageH, replyLayouts)
        }

        var commentsHeight = 0f
        if (commentLayouts.isNotEmpty()) {
            commentsHeight += dp(46f) // "共 N 条评论" 标题
            commentLayouts.forEachIndexed { index, cl ->
                // 评论之间的间距
                if (index != 0) commentsHeight += dp(20f)
                // 评论头 + body + 图片
                commentsHeight += cl.authorLine + dp(8f) + cl.bodyLayout.height
                if (cl.imageHeight > 0) commentsHeight += cl.imageHeight + dp(10f)
                // 回复容器
                if (cl.replies.isNotEmpty()) {
                    commentsHeight += dp(14f) // 顶部 padding
                    cl.replies.forEachIndexed { rIdx, rl ->
                        if (rIdx != 0) commentsHeight += dp(10f)
                        commentsHeight += rl.authorLine + dp(4f) + rl.bodyLayout.height
                    }
                    commentsHeight += dp(14f) // 底部 padding
                }
            }
            commentsHeight += dp(8f)
        }

        cardHeight += commentsHeight.roundToInt()

        val height = ceil(cardHeight + outerPadding * 2).roundToInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(245, 247, 250))

        val cardRect = RectF(outerPadding, outerPadding, width - outerPadding, height - outerPadding)
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawRoundRect(cardRect, dp(18f), dp(18f), cardPaint)

        var y = outerPadding + cardPadding
        val x = outerPadding + cardPadding
        val maxAuthorWidth = contentWidth - dp(52f) - dp(110f) // 留出头像 + 时间戳的空间
        // ── 帖子头：头像 + 名字 + meta ──
        val avatarSize = dp(40f)
        drawAvatar(
            canvas,
            cx = x + avatarSize / 2f,
            cy = y + avatarSize / 2f,
            radius = avatarSize / 2f,
            user = topic.userInfo,
            avatarBitmap = topic.userInfo?.avatar?.let { avatarBitmaps[it] }
        )
        val author = topic.userInfo?.nickname?.takeIf { it.isNotBlank() } ?: "匿名同学"
        drawClippedText(
            canvas = canvas,
            text = author,
            paint = authorPaint,
            x = x + avatarSize + dp(12f),
            y = y + dp(17f),
            maxWidth = maxAuthorWidth
        )
        val meta = listOfNotNull(
            topic.node.ifBlank { "集市" },
            school,
            topic.createTime.takeIf { it.isNotBlank() }
        ).joinToString(" · ")
        drawClippedText(
            canvas = canvas,
            text = meta,
            paint = metaPaint,
            x = x + avatarSize + dp(12f),
            y = y + dp(40f),
            maxWidth = maxAuthorWidth
        )
        y += avatarSize + dp(18f)

        // ── 帖子标题 + 正文 ──
        titleLayout?.let {
            canvas.save()
            canvas.translate(x, y)
            it.draw(canvas)
            canvas.restore()
            y += it.height + dp(12f)
        }

        canvas.save()
        canvas.translate(x, y)
        contentLayout.draw(canvas)
        canvas.restore()
        y += contentLayout.height + dp(16f)

        // ── 帖子图片 ──
        topicImages.forEachIndexed { index, source ->
            val aspectHeight = contentWidth * source.height / source.width.coerceAtLeast(1).toFloat()
            val imageHeight = aspectHeight.coerceIn(dp(180f), dp(520f))
            val dest = RectF(x, y, x + contentWidth, y + imageHeight)
            drawImageCover(canvas, source, dest, dp(12f))
            y += imageHeight + dp(12f)
        }

        val footer = "赞 ${topic.likeCount}   评论 ${topic.commentCount}   图片 ${topic.imgs.size}"
        canvas.drawText(footer, x, y + dp(18f), footerPaint)
        y += dp(40f)

        // ── 评论区 ──
        if (comments.isNotEmpty()) {
            // 分隔线
            val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(228, 228, 231)
                strokeWidth = dp(1f)
            }
            canvas.drawLine(x, y, x + contentWidth, y, dividerPaint)
            y += dp(20f)
            // 标题
            canvas.drawText(
                "评论 (${comments.size})",
                x,
                y + dp(18f),
                sectionTitlePaint
            )
            y += dp(46f)

            commentLayouts.forEachIndexed { index, cl ->
                if (index != 0) y += dp(20f)
                // 评论头
                val cAvatarSize = dp(32f)
                drawAvatar(
                    canvas,
                    cx = x + cAvatarSize / 2f,
                    cy = y + cAvatarSize / 2f,
                    radius = cAvatarSize / 2f,
                    user = cl.comment.userInfo,
                    avatarBitmap = cl.comment.userInfo?.avatar?.let { avatarBitmaps[it] }
                )
                val cAuthor = cl.comment.userInfo?.nickname?.takeIf { it.isNotBlank() } ?: "匿名同学"
                drawClippedText(
                    canvas = canvas,
                    text = cAuthor,
                    paint = commentAuthorPaint,
                    x = x + cAvatarSize + dp(10f),
                    y = y + dp(15f),
                    maxWidth = contentWidth - cAvatarSize - dp(10f) - dp(120f)
                )
                drawClippedText(
                    canvas = canvas,
                    text = cl.comment.createTime,
                    paint = commentMetaPaint,
                    x = x + cAvatarSize + dp(10f),
                    y = y + dp(33f),
                    maxWidth = contentWidth - cAvatarSize - dp(10f)
                )
                y += cl.authorLine

                // 正文
                canvas.save()
                canvas.translate(x, y)
                cl.bodyLayout.draw(canvas)
                canvas.restore()
                y += cl.bodyLayout.height + dp(8f)

                // 评论图片
                cl.comment.imgs.forEachIndexed { iIdx, url ->
                    val imgBitmap = commentImages[url] ?: return@forEachIndexed
                    val aspectHeight = contentWidth * imgBitmap.height / imgBitmap.width.coerceAtLeast(1).toFloat()
                    val imgHeight = aspectHeight.coerceIn(dp(120f), dp(420f))
                    val dest = RectF(x, y, x + contentWidth, y + imgHeight)
                    drawImageCover(canvas, imgBitmap, dest, dp(10f))
                    y += imgHeight + dp(8f)
                }
                if (cl.imageHeight > 0) y += dp(2f)

                // 回复
                if (cl.replies.isNotEmpty()) {
                    val replyContainerLeft = x + dp(36f)
                    val replyContainerRight = x + contentWidth
                    val replyContainerWidth = contentWidth - dp(36f)
                    // 先量回复容器高度
                    var innerHeight = dp(14f)
                    cl.replies.forEachIndexed { rIdx, rl ->
                        if (rIdx != 0) innerHeight += dp(10f)
                        innerHeight += rl.authorLine + dp(4f) + rl.bodyLayout.height
                    }
                    innerHeight += dp(14f)
                    val replyBg = RectF(
                        replyContainerLeft,
                        y,
                        replyContainerRight,
                        y + innerHeight
                    )
                    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(244, 244, 245)
                    }
                    canvas.drawRoundRect(replyBg, dp(10f), dp(10f), bgPaint)
                    var replyY = y + dp(14f)
                    cl.replies.forEachIndexed { rIdx, rl ->
                        if (rIdx != 0) replyY += dp(10f)
                        val rAvatarSize = dp(22f)
                        drawAvatar(
                            canvas,
                            cx = replyContainerLeft + dp(14f) + rAvatarSize / 2f,
                            cy = replyY + rAvatarSize / 2f,
                            radius = rAvatarSize / 2f,
                            user = rl.reply.userInfo,
                            avatarBitmap = rl.reply.userInfo?.avatar?.let { avatarBitmaps[it] }
                        )
                        val rAuthor = rl.reply.userInfo?.nickname?.takeIf { it.isNotBlank() } ?: "同学"
                        val authorX = replyContainerLeft + dp(14f) + rAvatarSize + dp(8f)
                        val authorWidthLimit = replyContainerWidth - dp(14f) - rAvatarSize - dp(8f) - dp(8f)
                        drawClippedText(
                            canvas = canvas,
                            text = rAuthor,
                            paint = replyAuthorPaint,
                            x = authorX,
                            y = replyY + dp(13f),
                            maxWidth = authorWidthLimit
                        )
                        val pickName = rl.reply.pickUserInfo?.nickname?.takeIf { it.isNotBlank() }
                        if (pickName != null) {
                            // 按真实文本宽度接 "@被回复人"，避免中文宽度估算偏差
                            val pickText = " 回复 $pickName"
                            val authorTextWidth = replyAuthorPaint.measureText(rAuthor)
                            drawClippedText(
                                canvas = canvas,
                                text = pickText,
                                paint = replyPickPaint,
                                x = authorX + authorTextWidth,
                                y = replyY + dp(13f),
                                maxWidth = (authorWidthLimit - authorTextWidth).coerceAtLeast(0f)
                            )
                        }
                        // 回复正文
                        canvas.save()
                        canvas.translate(
                            replyContainerLeft + dp(14f),
                            replyY + rl.authorLine + dp(4f)
                        )
                        rl.bodyLayout.draw(canvas)
                        canvas.restore()
                        replyY += rl.authorLine + dp(4f) + rl.bodyLayout.height
                    }
                    y += innerHeight
                }
            }
        }

        return bitmap
    }

    // ══════════════════════════════════════════════════════
    //  头像 / 图片 / 文本绘制
    // ══════════════════════════════════════════════════════

    private fun drawAvatar(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        user: MarketUser?,
        avatarBitmap: Bitmap?
    ) {
        // 背景圆 + 描边
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(230, 240, 255) }
        canvas.drawCircle(cx, cy, radius, bgPaint)
        if (avatarBitmap != null) {
            // 用圆形 BitmapShader 裁剪贴图
            val shader = BitmapShader(avatarBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val matrix = Matrix()
            val dstRectF = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
            val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                this.shader = shader
            }
            // 居中裁剪（centerCrop 行为）
            val srcRatio = avatarBitmap.width / avatarBitmap.height.toFloat()
            val dstRatio = 1f
            if (srcRatio > dstRatio) {
                val newWidth = (avatarBitmap.height * dstRatio).roundToInt()
                val left = (avatarBitmap.width - newWidth) / 2
                matrix.postTranslate(-left.toFloat(), 0f)
            } else {
                val newHeight = (avatarBitmap.width / dstRatio).roundToInt()
                val top = (avatarBitmap.height - newHeight) / 2
                matrix.postTranslate(0f, -top.toFloat())
            }
            shader.setLocalMatrix(matrix)
            canvas.save()
            val path = Path().apply { addCircle(cx, cy, radius, Path.Direction.CW) }
            canvas.clipPath(path)
            canvas.drawRect(dstRectF, shaderPaint)
            canvas.restore()
        } else {
            // 回退首字母
            val label = user?.nickname?.firstOrNull()?.toString() ?: "匿"
            val textPaint = textPaint(Color.rgb(47, 128, 237), radius * 0.95f, true).apply {
                textAlign = Paint.Align.CENTER
            }
            val baseline = cy - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(label, cx, baseline, textPaint)
        }
        // 描边
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.rgb(228, 228, 231)
            strokeWidth = max(1f, radius * 0.06f)
        }
        canvas.drawCircle(cx, cy, radius, strokePaint)
    }

    private fun drawImageCover(canvas: Canvas, bitmap: Bitmap, dest: RectF, radius: Float) {
        val srcRatio = bitmap.width / bitmap.height.toFloat()
        val destRatio = dest.width() / dest.height()
        val src = if (srcRatio > destRatio) {
            val srcWidth = (bitmap.height * destRatio).roundToInt()
            val left = (bitmap.width - srcWidth) / 2
            android.graphics.Rect(left, 0, left + srcWidth, bitmap.height)
        } else {
            val srcHeight = (bitmap.width / destRatio).roundToInt()
            val top = (bitmap.height - srcHeight) / 2
            android.graphics.Rect(0, top, bitmap.width, top + srcHeight)
        }
        val path = android.graphics.Path().apply {
            addRoundRect(dest, radius, radius, android.graphics.Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(path)
        canvas.drawBitmap(bitmap, src, dest, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
    }

    /**
     * 绘制单行文本，按 [maxWidth] 截断，超出部分追加 "…"。避免 drawText 不带宽度限制时溢出卡片。
     */
    private fun drawClippedText(
        canvas: Canvas,
        text: String,
        paint: TextPaint,
        x: Float,
        y: Float,
        maxWidth: Float
    ) {
        if (maxWidth <= 0f || text.isEmpty()) {
            canvas.drawText(text, x, y, paint)
            return
        }
        val measured = paint.measureText(text)
        if (measured <= maxWidth) {
            canvas.drawText(text, x, y, paint)
            return
        }
        val ellipsis = "…"
        val ellipsisWidth = paint.measureText(ellipsis)
        val buffer = text.toCharArray()
        var endIndex = paint.breakText(buffer, 0, buffer.size, maxWidth - ellipsisWidth, null)
        if (endIndex <= 0) endIndex = 1
        canvas.drawText(String(buffer, 0, endIndex) + ellipsis, x, y, paint)
    }

    private fun staticLayout(text: String, paint: TextPaint, width: Int): StaticLayout {
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.08f)
            .setIncludePad(false)
            .build()
    }

    private fun textPaint(color: Int, textSize: Float, fakeBold: Boolean = false): TextPaint {
        return TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.textSize = textSize
            this.isFakeBoldText = fakeBold
        }
    }

    private fun decodeBitmapFromUrl(url: String): Bitmap? {
        val bytes = downloadImage(url).bytes
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun collectAvatarUrls(topic: MarketTopic, comments: List<MarketComment>): List<String> {
        val urls = mutableListOf<String>()
        topic.userInfo?.avatar?.takeIf { it.isNotBlank() }?.let { urls += it }
        comments.forEach { c ->
            c.userInfo?.avatar?.takeIf { it.isNotBlank() }?.let { urls += it }
            c.pickUserInfo?.avatar?.takeIf { it.isNotBlank() }?.let { urls += it }
            c.visibleReplies.forEach { r ->
                r.userInfo?.avatar?.takeIf { it.isNotBlank() }?.let { urls += it }
                r.pickUserInfo?.avatar?.takeIf { it.isNotBlank() }?.let { urls += it }
            }
        }
        return urls.distinct()
    }

    private fun normalizeImageUrl(url: String): String {
        val trimmed = url.trim()
        return when {
            trimmed.startsWith("//") -> "https:$trimmed"
            else -> trimmed
        }
    }

    private fun extensionFor(mimeType: String, url: String): String {
        val fromMime = when (mimeType.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> null
        }
        if (fromMime != null) return fromMime
        val path = runCatching { URLDecoder.decode(url.substringBefore("?"), "UTF-8") }.getOrDefault(url)
        return path.substringAfterLast('.', "jpg").takeIf { it.length in 2..5 } ?: "jpg"
    }

    private data class DownloadedImage(
        val bytes: ByteArray,
        val mimeType: String?
    )
}
