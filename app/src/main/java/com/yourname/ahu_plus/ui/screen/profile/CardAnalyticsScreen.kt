package com.yourname.ahu_plus.ui.screen.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourname.ahu_plus.data.model.AnalyticsPeriod
import com.yourname.ahu_plus.data.model.AnalyticsPeriodKind
import com.yourname.ahu_plus.data.model.BillRecord
import com.yourname.ahu_plus.data.model.CardAnalyticsSummary
import com.yourname.ahu_plus.data.model.CategoryStat
import com.yourname.ahu_plus.data.model.DailyPoint
import com.yourname.ahu_plus.data.model.FoodSplitStat
import com.yourname.ahu_plus.data.model.MerchantStat
import com.yourname.ahu_plus.data.model.TransactionItem
import com.yourname.ahu_plus.data.model.toAnalyticsReport
import com.yourname.ahu_plus.ui.components.AhuTopAppBar
import java.text.DecimalFormat

private val MoneyFormat = DecimalFormat("¥#,##0.00")
private val PercentFormat = DecimalFormat("0.0%")

@Composable
fun CardAnalyticsScreen(
    bills: List<BillRecord>,
    isLoading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    val report = remember(bills) { bills.toAnalyticsReport() }
    var selectedKind by rememberSaveable { mutableStateOf(AnalyticsPeriodKind.MONTH.name) }
    var selectedMonthId by rememberSaveable(report.currentMonthId) {
        mutableStateOf(report.currentMonthId)
    }
    var selectedSemesterId by rememberSaveable(report.currentSemesterId) {
        mutableStateOf(report.currentSemesterId)
    }
    var selectedHighDay by rememberSaveable { mutableStateOf<String?>(null) }

    val kind = runCatching { AnalyticsPeriodKind.valueOf(selectedKind) }.getOrDefault(AnalyticsPeriodKind.MONTH)
    val periods = if (kind == AnalyticsPeriodKind.MONTH) report.monthPeriods else report.semesterPeriods
    val selectedId = if (kind == AnalyticsPeriodKind.MONTH) selectedMonthId else selectedSemesterId
    val selectedSummary = report.summary(selectedId) ?: when (kind) {
        AnalyticsPeriodKind.MONTH -> report.currentMonth
        AnalyticsPeriodKind.SEMESTER -> report.currentSemester
    }

    BackHandler(enabled = true) { onBack() }

    Scaffold(
        topBar = {
            AhuTopAppBar(
                title = { Text("一卡通分析", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when {
                isLoading && selectedSummary == null -> item { LoadingBlock("正在加载一卡通账单...") }
                error != null && selectedSummary == null -> item { ErrorBlock(error = error, onRefresh = onRefresh) }
                selectedSummary == null -> item { EmptyBlock("暂无可分析的消费记录") }
                else -> {
                    item {
                        RangeControls(
                            selectedKind = kind,
                            periods = periods,
                            selectedPeriod = selectedSummary.period,
                            onKindChange = { newKind ->
                                selectedKind = newKind.name
                            },
                            onPeriodChange = { period ->
                                if (kind == AnalyticsPeriodKind.MONTH) {
                                    selectedMonthId = period.id
                                } else {
                                    selectedSemesterId = period.id
                                }
                            }
                        )
                    }
                    item { SummaryCards(selectedSummary) }
                    item { SmoothTrendCard(selectedSummary) }
                    item { FoodSplitSection(selectedSummary.splitRows) }
                    item { CanteenAnalysisSection(selectedSummary) }
                    item { NonCanteenAnalysisSection(selectedSummary) }
                    item {
                        HighDaysSection(
                            summary = selectedSummary,
                            onOpenDay = { selectedHighDay = it }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            }
        }
    }

    val transactions = selectedSummary?.transactionsByDay?.get(selectedHighDay).orEmpty()
    if (selectedHighDay != null) {
        TransactionListDialog(
            title = "$selectedHighDay 消费明细",
            transactions = transactions,
            onDismiss = { selectedHighDay = null }
        )
    }
}

@Composable
private fun RangeControls(
    selectedKind: AnalyticsPeriodKind,
    periods: List<AnalyticsPeriod>,
    selectedPeriod: AnalyticsPeriod,
    onKindChange: (AnalyticsPeriodKind) -> Unit,
    onPeriodChange: (AnalyticsPeriod) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnalyticsPeriodKind.entries.forEach { kind ->
                    FilterChip(
                        selected = selectedKind == kind,
                        onClick = { onKindChange(kind) },
                        label = { Text(kind.label) }
                    )
                }
            }
            if (selectedKind == AnalyticsPeriodKind.MONTH) {
                YearMonthDropdowns(
                    periods = periods,
                    selectedPeriod = selectedPeriod,
                    onPeriodChange = onPeriodChange
                )
            } else {
                PeriodDropdown(
                    label = "学期",
                    periods = periods,
                    selectedPeriod = selectedPeriod,
                    onPeriodChange = onPeriodChange
                )
            }
        }
    }
}

@Composable
private fun YearMonthDropdowns(
    periods: List<AnalyticsPeriod>,
    selectedPeriod: AnalyticsPeriod,
    onPeriodChange: (AnalyticsPeriod) -> Unit
) {
    val selectedYear = selectedPeriod.startDay.take(4)
    val yearPeriods = periods.filter { it.startDay.take(4) == selectedYear }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        YearDropdown(
            periods = periods,
            selectedYear = selectedYear,
            onYearChange = { year ->
                periods.firstOrNull { it.startDay.take(4) == year }?.let(onPeriodChange)
            },
            modifier = Modifier.weight(1f)
        )
        PeriodDropdown(
            label = "月份",
            periods = yearPeriods,
            selectedPeriod = selectedPeriod,
            onPeriodChange = onPeriodChange,
            valueText = { "${it.startDay.substring(5, 7).toInt()}月" },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun YearDropdown(
    periods: List<AnalyticsPeriod>,
    selectedYear: String,
    onYearChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val years = periods.map { it.startDay.take(4) }.distinct().sortedDescending()
    Box(modifier = modifier) {
        DropdownBox(
            label = "年份",
            value = "${selectedYear}年",
            onClick = { expanded = true }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            years.forEach { year ->
                DropdownMenuItem(
                    text = { Text("${year}年") },
                    onClick = {
                        expanded = false
                        onYearChange(year)
                    }
                )
            }
        }
    }
}

@Composable
private fun PeriodDropdown(
    label: String,
    periods: List<AnalyticsPeriod>,
    selectedPeriod: AnalyticsPeriod,
    onPeriodChange: (AnalyticsPeriod) -> Unit,
    valueText: (AnalyticsPeriod) -> String = { it.displayLabel() },
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        DropdownBox(
            label = label,
            value = valueText(selectedPeriod),
            onClick = { expanded = true }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            periods.forEach { period ->
                DropdownMenuItem(
                    text = { Text(valueText(period)) },
                    onClick = {
                        expanded = false
                        onPeriodChange(period)
                    }
                )
            }
        }
    }
}

@Composable
private fun DropdownBox(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SummaryCards(summary: CardAnalyticsSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard(
                label = "总支出",
                value = MoneyFormat.format(summary.totalExpense),
                sub = "${summary.expenseCount} 笔",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "日均支出",
                value = MoneyFormat.format(summary.dailyAvg),
                sub = "${summary.activeDays} 天消费",
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard(
                label = "消费天数",
                value = "${summary.activeDays} 天",
                sub = "${summary.period.startDay} 至 ${summary.period.endDay}",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "食堂占比",
                value = PercentFormat.format(summary.canteenShare),
                sub = "食堂 / 非食堂",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun SmoothTrendCard(summary: CardAnalyticsSummary) {
    CardSection(title = "${summary.period.label}每日支出") {
        Text(
            text = "${summary.period.startDay} 至 ${summary.period.endDay}，按 04:00 作为逻辑日分界",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))
        SmoothTrendChart(points = summary.dailyTrend)
    }
}

@Composable
private fun SmoothTrendChart(points: List<DailyPoint>) {
    if (points.size < 2) {
        EmptyBlock("该范围内暂无趋势数据")
        return
    }
    val primary = Color(0xFF4F73C8)
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .padding(top = 8.dp, bottom = 8.dp)
    ) {
        val top = 12f
        val bottom = size.height - 24f
        val left = 8f
        val right = size.width - 8f
        val chartHeight = bottom - top
        val chartWidth = right - left
        val maxValue = points.maxOf { it.totalExpense }.coerceAtLeast(1.0).toFloat()

        repeat(5) { index ->
            val y = top + chartHeight * index / 4f
            drawLine(
                color = grid.copy(alpha = 0.55f),
                start = Offset(left, y),
                end = Offset(right, y),
                strokeWidth = 1f
            )
        }

        val offsets = points.mapIndexed { index, point ->
            val x = left + chartWidth * index / (points.lastIndex.coerceAtLeast(1)).toFloat()
            val y = bottom - (point.totalExpense.toFloat() / maxValue) * chartHeight
            Offset(x, y.coerceIn(top, bottom))
        }
        val curve = smoothPath(offsets)
        val area = Path().apply {
            addPath(curve)
            lineTo(offsets.last().x, bottom)
            lineTo(offsets.first().x, bottom)
            close()
        }

        drawPath(
            path = area,
            brush = Brush.verticalGradient(
                colors = listOf(
                    primary.copy(alpha = 0.17f),
                    primary.copy(alpha = 0.06f),
                    primary.copy(alpha = 0.015f)
                ),
                startY = top,
                endY = bottom
            )
        )
        drawPath(
            path = curve,
            color = primary,
            style = Stroke(width = 4.2f, cap = StrokeCap.Round)
        )
        offsets.forEachIndexed { index, offset ->
            if (points[index].totalExpense > 0.0) {
                drawCircle(
                    color = primary.copy(alpha = 0.28f),
                    radius = 3.4f,
                    center = offset
                )
            }
        }
    }
}

private fun smoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().x, points.first().y)
    if (points.size == 2) {
        path.lineTo(points.last().x, points.last().y)
        return path
    }
    for (index in 1 until points.size) {
        val previous = points[index - 1]
        val current = points[index]
        val mid = Offset((previous.x + current.x) / 2f, (previous.y + current.y) / 2f)
        path.quadraticTo(previous.x, previous.y, mid.x, mid.y)
    }
    val beforeLast = points[points.lastIndex - 1]
    val last = points.last()
    path.quadraticTo(beforeLast.x, beforeLast.y, last.x, last.y)
    return path
}

@Composable
private fun FoodSplitSection(rows: List<FoodSplitStat>) {
    CardSection(title = "食堂 / 非食堂") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEach { row ->
                ProgressRow(
                    name = row.name,
                    amount = row.totalAmount,
                    count = row.count,
                    share = row.share
                )
            }
        }
    }
}

@Composable
private fun CanteenAnalysisSection(summary: CardAnalyticsSummary) {
    CardSection(title = "食堂分析") {
        if (summary.canteenStats.isEmpty()) {
            EmptyBlock("该范围内暂无食堂消费")
            return@CardSection
        }
        summary.canteenStats.forEachIndexed { index, stat ->
            ProgressRow(
                name = stat.name,
                amount = stat.totalAmount,
                count = stat.count,
                share = stat.share,
                avg = stat.avgAmount
            )
            if (index != summary.canteenStats.lastIndex) {
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
        if (summary.mealStats.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))
            Text("餐点分布", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                text = "早餐 06:00-09:59，午餐 10:00-14:59，晚餐 16:00-21:59，其余归入夜宵/其他。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            summary.mealStats.forEach { meal ->
                CompactStatRow(meal.name, MoneyFormat.format(meal.totalAmount), "${meal.count} 笔 · ${PercentFormat.format(meal.share)}")
            }
        }
    }
}

@Composable
private fun NonCanteenAnalysisSection(summary: CardAnalyticsSummary) {
    CardSection(title = "非食堂分析") {
        if (summary.nonCanteenCategories.isEmpty() && summary.nonCanteenMerchants.isEmpty()) {
            EmptyBlock("该范围内暂无非食堂消费")
            return@CardSection
        }
        summary.nonCanteenCategories.take(6).forEach { category ->
            ProgressRow(
                name = category.name,
                amount = category.totalAmount,
                count = category.count,
                share = category.share,
                avg = category.avgAmount
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (summary.nonCanteenMerchants.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Text("商户", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            summary.nonCanteenMerchants.forEach { merchant ->
                CompactStatRow(
                    title = merchant.name,
                    value = MoneyFormat.format(merchant.totalAmount),
                    sub = "${merchant.count} 笔 · 均 ${MoneyFormat.format(merchant.avgAmount)}"
                )
            }
        }
    }
}

@Composable
private fun HighDaysSection(
    summary: CardAnalyticsSummary,
    onOpenDay: (String) -> Unit
) {
    CardSection(title = "高支出日") {
        val days = summary.highDays
        if (days.isEmpty()) {
            EmptyBlock("暂无高支出日")
            return@CardSection
        }
        days.forEach { day ->
            CompactStatRow(
                title = day.date,
                value = MoneyFormat.format(day.totalExpense),
                sub = "${day.txnCount} 笔 · 点击查看明细",
                onClick = { onOpenDay(day.date) }
            )
        }
    }
}

@Composable
private fun TransactionListDialog(
    title: String,
    transactions: List<TransactionItem>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            if (transactions.isEmpty()) {
                Text("暂无明细")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(transactions.size) { index ->
                        val item = transactions[index]
                        TransactionRow(item)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun TransactionRow(item: TransactionItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = item.merchant,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${item.time.substring(11, 16)} · ${item.group} · ${item.category}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = MoneyFormat.format(item.amount),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ProgressRow(
    name: String,
    amount: Double,
    count: Int,
    share: Double,
    avg: Double? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = MoneyFormat.format(amount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
        LinearProgressIndicator(
            progress = { share.toFloat().coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = Color(0xFF4F73C8),
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Text(
            text = buildString {
                append(PercentFormat.format(share))
                append(" · ")
                append(count)
                append(" 笔")
                if (avg != null) append(" · 均 ${MoneyFormat.format(avg)}")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CompactStatRow(
    title: String,
    value: String,
    sub: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CardSection(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

private fun AnalyticsPeriod.displayLabel(): String {
    val prefix = when {
        isCurrent && kind == AnalyticsPeriodKind.MONTH -> "本月 · "
        isCurrent && kind == AnalyticsPeriodKind.SEMESTER -> "本学期 · "
        else -> ""
    }
    return "$prefix$label"
}
