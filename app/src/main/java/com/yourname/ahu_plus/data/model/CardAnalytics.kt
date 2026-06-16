package com.yourname.ahu_plus.data.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private const val DAY_BOUNDARY_HOUR = 4

private val DATE_TIME_FORMATS = listOf(
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()),
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
)
private val DATE_ONLY_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
private val MONTH_ID_FORMAT = SimpleDateFormat("yyyy-MM", Locale.getDefault())
private val MONTH_LABEL_FORMAT = SimpleDateFormat("yyyy年M月", Locale.getDefault())

private val CANTEEN_MAPPING = mapOf(
    "北一区" to "桔园",
    "北二区" to "榴园",
    "北三区" to "蕙园",
    "南一区" to "梅园",
    "南二区" to "桂园",
    "南三区" to "梧桐园"
)

enum class AnalyticsPeriodKind(val label: String) {
    MONTH("按月"),
    SEMESTER("按学期")
}

data class AnalyticsPeriod(
    val id: String,
    val kind: AnalyticsPeriodKind,
    val label: String,
    val startDay: String,
    val endDay: String,
    val isCurrent: Boolean = false
)

data class CardAnalyticsReport(
    val monthPeriods: List<AnalyticsPeriod> = emptyList(),
    val semesterPeriods: List<AnalyticsPeriod> = emptyList(),
    val currentMonthId: String? = null,
    val currentSemesterId: String? = null,
    val summaries: Map<String, CardAnalyticsSummary> = emptyMap()
) {
    val currentMonth: CardAnalyticsSummary?
        get() = currentMonthId?.let { summaries[it] } ?: monthPeriods.firstOrNull()?.let { summaries[it.id] }

    val currentSemester: CardAnalyticsSummary?
        get() = currentSemesterId?.let { summaries[it] } ?: semesterPeriods.firstOrNull()?.let { summaries[it.id] }

    fun summary(periodId: String?): CardAnalyticsSummary? = periodId?.let { summaries[it] }
}

data class CardAnalyticsSummary(
    val period: AnalyticsPeriod,
    val totalExpense: Double = 0.0,
    val expenseCount: Int = 0,
    val activeDays: Int = 0,
    val dailyAvg: Double = 0.0,
    val canteenShare: Double = 0.0,
    val dailyTrend: List<DailyPoint> = emptyList(),
    val splitRows: List<FoodSplitStat> = emptyList(),
    val canteenStats: List<CanteenStat> = emptyList(),
    val mealStats: List<CategoryStat> = emptyList(),
    val nonCanteenCategories: List<CategoryStat> = emptyList(),
    val nonCanteenMerchants: List<MerchantStat> = emptyList(),
    val highDays: List<DailyPoint> = emptyList(),
    val transactionsByDay: Map<String, List<TransactionItem>> = emptyMap()
)

data class DailyPoint(
    val date: String,
    val totalExpense: Double,
    val txnCount: Int
)

data class FoodSplitStat(
    val name: String,
    val totalAmount: Double,
    val count: Int,
    val share: Double
)

data class CanteenStat(
    val name: String,
    val totalAmount: Double,
    val count: Int,
    val share: Double,
    val avgAmount: Double
)

data class CategoryStat(
    val name: String,
    val totalAmount: Double,
    val count: Int,
    val share: Double,
    val avgAmount: Double
)

data class MerchantStat(
    val name: String,
    val totalAmount: Double,
    val count: Int,
    val avgAmount: Double
)

data class TransactionItem(
    val day: String,
    val time: String,
    val amount: Double,
    val merchant: String,
    val group: String,
    val category: String
)

private data class ParsedBill(
    val record: BillRecord,
    val time: Date,
    val logicalDay: String,
    val amount: Double,
    val merchant: String,
    val canteenName: String?,
    val mealName: String?,
    val nonCanteenCategory: String?
)

fun List<BillRecord>.toAnalyticsReport(today: Date = Date()): CardAnalyticsReport {
    val expenses = mapNotNull { record ->
        val time = parseDateTime(record.effectDateStr) ?: parseDateTime(record.jndatetimeStr)
        if (time == null || !record.isExpense()) return@mapNotNull null
        val merchant = record.merchantText()
        val canteen = extractCanteenName(merchant)
        ParsedBill(
            record = record,
            time = time,
            logicalDay = logicalDayKey(time),
            amount = record.amountYuan(),
            merchant = merchant,
            canteenName = canteen,
            mealName = if (canteen != null) mealName(time) else null,
            nonCanteenCategory = if (canteen == null) classifyNonCanteen(merchant) else null
        )
    }.sortedBy { it.time }

    if (expenses.isEmpty()) return CardAnalyticsReport()

    val monthPeriods = buildMonthPeriods(expenses, today)
    val semesterPeriods = buildSemesterPeriods(expenses, today)
    val summaries = (monthPeriods + semesterPeriods).associate { period ->
        period.id to buildSummary(period, expenses)
    }

    return CardAnalyticsReport(
        monthPeriods = monthPeriods,
        semesterPeriods = semesterPeriods,
        currentMonthId = monthPeriods.firstOrNull { it.isCurrent }?.id ?: monthPeriods.firstOrNull()?.id,
        currentSemesterId = semesterPeriods.firstOrNull { it.isCurrent }?.id ?: semesterPeriods.firstOrNull()?.id,
        summaries = summaries
    )
}

private fun buildSummary(period: AnalyticsPeriod, allExpenses: List<ParsedBill>): CardAnalyticsSummary {
    val expenses = allExpenses.filter { it.logicalDay >= period.startDay && it.logicalDay <= period.endDay }
    val total = expenses.sumOf { it.amount }
    val activeDays = expenses.map { it.logicalDay }.distinct().size
    val dailyAvg = if (activeDays > 0) total / activeDays else 0.0
    val transactionItems = expenses
        .sortedByDescending { it.time }
        .map { it.toTransactionItem() }

    val dailyTrend = buildDailyTrend(period, expenses)
    val foodTotal = expenses.filter { it.canteenName != null }.sumOf { it.amount }
    val canteenShare = if (total > 0) foodTotal / total else 0.0

    val splitRows = listOf(
        FoodSplitStat(
            name = "食堂",
            totalAmount = foodTotal,
            count = expenses.count { it.canteenName != null },
            share = canteenShare
        ),
        FoodSplitStat(
            name = "非食堂",
            totalAmount = total - foodTotal,
            count = expenses.count { it.canteenName == null },
            share = if (total > 0) (total - foodTotal) / total else 0.0
        )
    )

    val canteenStats = expenses.filter { it.canteenName != null }
        .groupBy { it.canteenName.orEmpty() }
        .map { (name, rows) ->
            val amount = rows.sumOf { it.amount }
            CanteenStat(
                name = name,
                totalAmount = amount,
                count = rows.size,
                share = if (foodTotal > 0) amount / foodTotal else 0.0,
                avgAmount = amount / rows.size
            )
        }
        .sortedByDescending { it.totalAmount }

    val mealStats = expenses.filter { it.mealName != null }
        .groupBy { it.mealName.orEmpty() }
        .map { (name, rows) ->
            val amount = rows.sumOf { it.amount }
            CategoryStat(
                name = name,
                totalAmount = amount,
                count = rows.size,
                share = if (foodTotal > 0) amount / foodTotal else 0.0,
                avgAmount = amount / rows.size
            )
        }
        .sortedWith(compareBy<CategoryStat> { mealSortIndex(it.name) }.thenByDescending { it.totalAmount })

    val nonCanteen = expenses.filter { it.canteenName == null }
    val nonCanteenTotal = nonCanteen.sumOf { it.amount }
    val nonCanteenCategories = nonCanteen
        .groupBy { it.nonCanteenCategory.orEmpty() }
        .map { (name, rows) ->
            val amount = rows.sumOf { it.amount }
            CategoryStat(
                name = name,
                totalAmount = amount,
                count = rows.size,
                share = if (nonCanteenTotal > 0) amount / nonCanteenTotal else 0.0,
                avgAmount = amount / rows.size
            )
        }
        .sortedByDescending { it.totalAmount }

    val nonCanteenMerchants = nonCanteen
        .groupBy { normalizeMerchantName(it.merchant) }
        .map { (name, rows) ->
            val amount = rows.sumOf { it.amount }
            MerchantStat(
                name = name,
                totalAmount = amount,
                count = rows.size,
                avgAmount = amount / rows.size
            )
        }
        .sortedByDescending { it.totalAmount }
        .take(8)

    return CardAnalyticsSummary(
        period = period,
        totalExpense = total,
        expenseCount = expenses.size,
        activeDays = activeDays,
        dailyAvg = dailyAvg,
        canteenShare = canteenShare,
        dailyTrend = dailyTrend,
        splitRows = splitRows,
        canteenStats = canteenStats,
        mealStats = mealStats,
        nonCanteenCategories = nonCanteenCategories,
        nonCanteenMerchants = nonCanteenMerchants,
        highDays = dailyTrend.sortedByDescending { it.totalExpense }.take(5),
        transactionsByDay = transactionItems.groupBy { it.day }
    )
}

private fun ParsedBill.toTransactionItem(): TransactionItem {
    val group = if (canteenName != null) "食堂" else "非食堂"
    val category = mealName ?: nonCanteenCategory ?: "其他消费"
    return TransactionItem(
        day = logicalDay,
        time = DATE_TIME_FORMATS.first().format(time),
        amount = amount,
        merchant = canteenName ?: normalizeMerchantName(merchant),
        group = group,
        category = category
    )
}

private fun buildDailyTrend(period: AnalyticsPeriod, expenses: List<ParsedBill>): List<DailyPoint> {
    val perDay = expenses.groupBy { it.logicalDay }
    val points = mutableListOf<DailyPoint>()
    val cal = dayCalendar(period.startDay) ?: return emptyList()
    val end = dayCalendar(period.endDay) ?: return emptyList()
    while (!cal.after(end)) {
        val key = DATE_ONLY_FORMAT.format(cal.time)
        val rows = perDay[key].orEmpty()
        points += DailyPoint(
            date = key,
            totalExpense = rows.sumOf { it.amount },
            txnCount = rows.size
        )
        cal.add(Calendar.DAY_OF_MONTH, 1)
    }
    return points
}

private fun buildMonthPeriods(expenses: List<ParsedBill>, today: Date): List<AnalyticsPeriod> {
    val currentMonthId = MONTH_ID_FORMAT.format(today)
    return expenses
        .groupBy { it.logicalDay.substring(0, 7) }
        .keys
        .sortedDescending()
        .map { monthId ->
            val cal = Calendar.getInstance().apply {
                time = SimpleDateFormat("yyyy-MM", Locale.getDefault()).parse(monthId) ?: today
                set(Calendar.DAY_OF_MONTH, 1)
            }
            val start = DATE_ONLY_FORMAT.format(cal.time)
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            val end = DATE_ONLY_FORMAT.format(cal.time)
            AnalyticsPeriod(
                id = "month-$monthId",
                kind = AnalyticsPeriodKind.MONTH,
                label = MONTH_LABEL_FORMAT.format(cal.time),
                startDay = start,
                endDay = end,
                isCurrent = monthId == currentMonthId
            )
        }
}

private fun buildSemesterPeriods(expenses: List<ParsedBill>, today: Date): List<AnalyticsPeriod> {
    val currentSemesterId = semesterPeriod(today).id
    return expenses
        .map { dayCalendar(it.logicalDay)?.time ?: it.time }
        .map { semesterPeriod(it) }
        .distinctBy { it.id }
        .sortedByDescending { it.startDay }
        .map { it.copy(isCurrent = it.id == currentSemesterId) }
}

private fun semesterPeriod(date: Date): AnalyticsPeriod {
    val cal = Calendar.getInstance().apply { time = date }
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH) + 1
    val (label, startYear, startMonth, endYear, endMonth) = when (month) {
        in 3..7 -> arrayOf("${year - 1}-${year}-2", "${year}", "3", "${year}", "7")
        8 -> arrayOf("${year - 1}-${year}-暑假", "${year}", "8", "${year}", "8")
        in 9..12 -> arrayOf("${year}-${year + 1}-1", "${year}", "9", "${year + 1}", "1")
        1 -> arrayOf("${year - 1}-${year}-1", "${year - 1}", "9", "${year}", "1")
        else -> arrayOf("${year - 1}-${year}-寒假", "${year}", "2", "${year}", "2")
    }
    val start = Calendar.getInstance().apply {
        set(startYear.toInt(), startMonth.toInt() - 1, 1, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val end = Calendar.getInstance().apply {
        set(endYear.toInt(), endMonth.toInt() - 1, 1, 0, 0, 0)
        set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }
    return AnalyticsPeriod(
        id = "semester-$label",
        kind = AnalyticsPeriodKind.SEMESTER,
        label = label,
        startDay = DATE_ONLY_FORMAT.format(start.time),
        endDay = DATE_ONLY_FORMAT.format(end.time)
    )
}

private fun parseDateTime(value: String?): Date? {
    if (value.isNullOrBlank()) return null
    return DATE_TIME_FORMATS.firstNotNullOfOrNull { format ->
        runCatching { format.parse(value) }.getOrNull()
    }
}

private fun dayCalendar(day: String): Calendar? {
    val date = runCatching { DATE_ONLY_FORMAT.parse(day) }.getOrNull() ?: return null
    return Calendar.getInstance().apply { time = date }
}

private fun logicalDayKey(date: Date): String {
    val cal = Calendar.getInstance().apply { time = date }
    if (cal.get(Calendar.HOUR_OF_DAY) < DAY_BOUNDARY_HOUR) {
        cal.add(Calendar.DAY_OF_MONTH, -1)
    }
    return DATE_ONLY_FORMAT.format(cal.time)
}

private fun BillRecord.amountYuan(): Double = abs(tranAmt) / 100.0

private fun BillRecord.searchText(): String {
    return listOf(resume, turnoverType, consumeTypeName.orEmpty(), locationName, toMerchant, payName)
        .joinToString(" ")
}

private fun BillRecord.merchantText(): String {
    return listOf(resume, toMerchant, locationName).firstOrNull { it.isNotBlank() }.orEmpty()
        .ifBlank { searchText() }
}

private fun BillRecord.isExpense(): Boolean {
    val text = searchText()
    if (listOf("充值", "退款", "退费", "转入", "入账").any { text.contains(it) }) return false
    if (listOf("消费", "支付", "扣款", "二维码", "付款").any { text.contains(it) }) return true
    return tranAmt < 0
}

private fun extractCanteenName(text: String): String? {
    if (text.isBlank()) return null
    val isFoodPlace = text.contains("食堂") || text.contains("餐厅")
    val zone = Regex("(北[一二三四五六七八九十]+区|南[一二三四五六七八九十]+区)").find(text)?.value
    if (zone != null && isFoodPlace) {
        return CANTEEN_MAPPING[zone] ?: zone
    }
    return Regex("([\\u4e00-\\u9fa5]{1,10}(?:食堂|餐厅))").find(text)?.groupValues?.getOrNull(1)
}

private fun normalizeMerchantName(raw: String): String {
    val canteen = extractCanteenName(raw)
    if (canteen != null) return canteen
    return raw
        .replace(Regex("(扫码支付|刷卡|消费|扣款|支付|二维码|一楼|二楼|三楼|四楼|五楼)"), "")
        .replace(Regex("[\\-—_/【】（）()\\s]+"), "")
        .trim()
        .ifBlank { "其他" }
}

private fun mealName(date: Date): String {
    val hour = Calendar.getInstance().apply { time = date }.get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 6..9 -> "早餐"
        in 10..14 -> "午餐"
        in 16..21 -> "晚餐"
        else -> "夜宵/其他"
    }
}

private fun mealSortIndex(name: String): Int {
    return when (name) {
        "早餐" -> 0
        "午餐" -> 1
        "晚餐" -> 2
        else -> 3
    }
}

private fun classifyNonCanteen(text: String): String {
    return when {
        text.contains("浴室") || text.contains("水控") || text.contains("洗浴") -> "浴室水控"
        text.contains("开水") || text.contains("热水") -> "开水热水"
        text.contains("网费") || text.contains("网络") -> "网费"
        text.contains("电费") || text.contains("空调") || text.contains("照明") -> "水电缴费"
        text.contains("超市") || text.contains("商店") || text.contains("便利") || text.contains("水果") -> "校园零售"
        text.contains("转账") || text.contains("电子账户") -> "电子账户"
        else -> "其他消费"
    }
}
