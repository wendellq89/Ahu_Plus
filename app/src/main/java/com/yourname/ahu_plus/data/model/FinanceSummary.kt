package com.yourname.ahu_plus.data.model

/**
 * 学生一张表「我的财务」聚合。
 *
 * 6 类数据项 (奖学金/助学金/临时困难补助/勤工助学/欠费状态/贷款)
 * 各自从 [StudentTableClient] 拉取后聚合,前端用 6 个 section 渲染。
 *
 * 缓存:整段序列化为 JSON 存到 SessionManager.student_info_json 的同 key
 * (因为 [StudentInfo] model 已含 academicWarningFields 字段,可平滑扩展 —
 *  但因为字段类型不同,本类用独立 cache key 放在 SessionManager.attendance_json 同源位置)。
 */
data class FinanceSummary(
    val scholarship: List<StudentInfoField> = emptyList(),
    val grant: List<StudentInfoField> = emptyList(),
    val hardshipGrant: List<StudentInfoField> = emptyList(),
    val workStudy: List<StudentInfoField> = emptyList(),
    val arrearsStatus: List<StudentInfoField> = emptyList(),
    val loan: List<StudentInfoField> = emptyList(),
    val lastUpdatedAt: Long = 0L
) {
    fun isEmpty(): Boolean = scholarship.isEmpty() && grant.isEmpty() &&
        hardshipGrant.isEmpty() && workStudy.isEmpty() &&
        arrearsStatus.isEmpty() && loan.isEmpty()
}
