package com.yourname.ahu_plus.data.model.exam

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

/**
 * 排考预测 Gitee JSON Schema (顶层 DTO)。
 *
 * 数据源: https://gitee.com/yao-enqi/ahu-plus-update/raw/master/exam_predictions/exam_predictions.json
 * 由 [tools/exam_prediction/scan_exams.py] 生成,客户端零登录拉取后与课表匹配。
 *
 * 反混淆策略 (2026-06-23 重构):
 *   1. 文件放在 [com.yourname.ahu_plus.data.model.exam] 包下,
 *      ProGuard 规则 `-keep class com.yourname.ahu_plus.data.model.** { *; }` 自动覆盖
 *      (保留类名 + 字段名,Gson 反射能正常工作)
 *   2. 所有字段都加 [@SerializedName] —— 即使 R8 混淆了字段名,Gson 仍能通过注解
 *      找到对应的 JSON key (双保险)
 *   3. 类加 [@Keep] —— 标记 R8 必须保留 (第三层保险,IDE Lint 也会提示误删警告)
 *   4. R8 激进混淆 (-repackageclasses + -allowaccessmodification) 继续对其他业务代码生效
 */
@Keep
data class ExamPredictionsDto(
    @SerializedName("version") val version: Int = 1,
    @SerializedName("generated_at") val generatedAt: String? = null,
    @SerializedName("semester") val semester: String? = null,
    @SerializedName("date_range") val dateRange: List<String>? = null,
    @SerializedName("campuses") val campuses: List<String>? = null,
    @SerializedName("source") val source: String? = null,
    @SerializedName("count") val count: Int = 0,
    @SerializedName("summary_by_date") val summaryByDate: List<DaySummary>? = null,
    @SerializedName("exams") val exams: List<ExamRawRecord>? = null,
)

/**
 * 单条 Exam 原始记录 (Gitee JSON → Android 内部模型)。
 * 后续由 [ExamDataRepository.matchPredictions] 与用户课表做精确匹配。
 */
@Keep
data class ExamRawRecord(
    @SerializedName("date") val date: String? = null,
    @SerializedName("start") val start: String? = null,
    @SerializedName("end") val end: String? = null,
    @SerializedName("course_name") val courseName: String? = null,
    @SerializedName("course_code") val courseCode: String? = null,
    @SerializedName("section") val section: String? = null,
    @SerializedName("full_code") val fullCode: String? = null,
    @SerializedName("semester") val semester: String? = null,
    @SerializedName("college") val college: String? = null,
    @SerializedName("room_name") val roomName: String? = null,
    @SerializedName("room_code") val roomCode: String? = null,
    @SerializedName("campus") val campus: String? = null,
    @SerializedName("building_id") val buildingId: Long? = null,
    @SerializedName("teacher") val teacher: String? = null,
    @SerializedName("activity_id") val activityId: Long? = null,
)

/** 单日摘要 (用于统计页)。 */
@Keep
data class DaySummary(
    @SerializedName("date") val date: String? = null,
    @SerializedName("count") val count: Int = 0,
    @SerializedName("campuses") val campuses: List<String>? = null,
    @SerializedName("elapsed_sec") val elapsedSec: Double = 0.0,
)

/**
 * 给 UI 用的轻量 meta 视图 (避免每次都解析 exams 数组)。
 *
 * 这个类**不是** Gson 反序列化产物 (由 [ExamDataRepository.getCachedMeta] 构造),
 * 所以不需要 [@SerializedName] / [@Keep]。保留在这里方便统一管理 DTO 相关类型。
 */
data class ExamPredictionsMeta(
    val generatedAt: String?,
    val semester: String?,
    val dateRange: List<String>,
    val totalCount: Int,
    val source: String?,
)
