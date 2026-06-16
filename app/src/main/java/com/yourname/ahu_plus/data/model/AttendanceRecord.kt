package com.yourname.ahu_plus.data.model

import com.google.gson.JsonObject

/**
 * 考勤缺勤单条记录 (来自学生一张表 /cp/templateList/getList)。
 *
 * 服务端返回的字段名（基于 [viewlist_labels] 解析顺序）是中文
 * 「课次/学生姓名/课程名称/教师/上课时间/节次/缺勤类别」,本类按业务语义重命名。
 * 兼容字段缺失:任意字段都可为 null,UI 自行兜底。
 */
data class AttendanceRecord(
    val resourceId: String,
    val courseName: String?,
    val teacherName: String?,
    val classroom: String?,        // 教室/上课地点 (JS)
    val classDate: String?,        // 上课日期 (SKSJ, yyyy-MM-dd)
    val classPeriod: String?,      // 上课节次 (JC, e.g. "1-2")
    val attendanceType: String?,   // 缺勤类别 (e.g. "迟到"/"早退"/"旷课"/"请假")
    val status: String?,           // 状态 (ZT, e.g. "正常")
    val source: String?,           // 数据来源 (RKFS, e.g. "电子课表")
    val remark: String? = null,
) {
    companion object {
        /**
         * 从 [JsonObject] 解析为 [AttendanceRecord]。
         * 字段名兼容多种可能 (服务端的 viewlist 标签可能在不同学期/批次有差异)。
         */
        fun fromJson(obj: JsonObject): AttendanceRecord {
            return AttendanceRecord(
                resourceId = obj.get("RESOURCE_ID")?.asString.orEmpty(),
                courseName = obj.firstString("KCMC", "课程名称", "课程名", "COURSE_NAME"),
                teacherName = obj.firstString("JSXM", "JSXM_NAME", "教师", "教师姓名", "TEACHER_NAME"),
                classroom = obj.firstString("JS", "JS_NAME", "上课地点", "教室", "CLASSROOM"),
                classDate = obj.firstString("SKSJ", "SKRQ", "上课日期", "日期", "SKRQ_NAME", "CLASS_DATE"),
                classPeriod = obj.firstString("JC", "SKJC", "节次", "上课节次"),
                attendanceType = obj.firstString("KQLB", "KQLB_NAME", "缺勤类别", "缺勤类型", "QQLB"),
                status = obj.firstString("ZT", "状态", "STATUS"),
                source = obj.firstString("RKFS", "RKFS_NAME", "数据来源", "录入方式", "SOURCE"),
                remark = obj.firstString("BZ", "备注", "REMARK"),
            )
        }

        private fun JsonObject.firstString(vararg keys: String): String? {
            for (k in keys) {
                val v = get(k) ?: continue
                if (v.isJsonNull) continue
                val s = runCatching { v.asString }.getOrNull()?.takeIf { it.isNotBlank() }
                if (s != null) return s
            }
            return null
        }
    }
}

data class AttendanceSummary(
    val records: List<AttendanceRecord> = emptyList(),
    val total: Int = 0,
    val lastUpdatedAt: Long = 0L
)
