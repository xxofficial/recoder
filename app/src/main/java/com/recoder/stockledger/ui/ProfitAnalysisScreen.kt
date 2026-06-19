package com.recoder.stockledger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.recoder.stockledger.data.DisplayCurrency
import com.recoder.stockledger.data.ExchangeRates
import com.recoder.stockledger.data.ProfitAnalysisPointUiModel
import com.recoder.stockledger.data.ProfitAnalysisUiModel
import com.recoder.stockledger.data.SecurityProfitAnalysisUiModel
import com.recoder.stockledger.data.rateToCny
import com.recoder.stockledger.ui.theme.BackgroundPrimary
import com.recoder.stockledger.ui.theme.BorderSubtle
import com.recoder.stockledger.ui.theme.ForegroundMuted
import com.recoder.stockledger.ui.theme.ForegroundPrimary
import com.recoder.stockledger.ui.theme.ForegroundSecondary
import com.recoder.stockledger.ui.theme.MarketDown
import com.recoder.stockledger.ui.theme.MarketDownSoft
import com.recoder.stockledger.ui.theme.MarketUp
import com.recoder.stockledger.ui.theme.MarketUpSoft
import com.recoder.stockledger.ui.theme.StockLedgerTheme
import com.recoder.stockledger.ui.theme.SurfaceInverse
import com.recoder.stockledger.ui.theme.SurfaceSecondary
import java.text.DecimalFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val AnalysisLineColor = Color(0xFF3B82F6)
private val AnalysisFillTopColor = Color(0x443B82F6)
private val AnalysisFillBottomColor = Color(0x003B82F6)
internal val SegmentBackground = Color(0xFFF3F4F6)
private val numberFormatter = DecimalFormat("#,##0.00")
private val wholeNumberFormatter = DecimalFormat("0")
private val chartDateFormatter = DateTimeFormatter.ofPattern("yyyy/M/d")
private val monthTitleFormatter = DateTimeFormatter.ofPattern("yyyy年 M月")
private val yearFormatter = DateTimeFormatter.ofPattern("yyyy年")
private val weekFields: WeekFields = WeekFields.of(DayOfWeek.SUNDAY, 1)

private enum class ProfitRange(val label: String) {
    ALL("全部"),
    THIS_MONTH("本月"),
    ONE_MONTH("近1月"),
    SIX_MONTHS("近6月"),
    THIS_YEAR("本年"),
}

private enum class CalendarMode(val label: String) {
    DAY("日"),
    WEEK("周"),
    MONTH("月"),
    YEAR("年"),
}

private enum class ValueUnit(val label: String) {
    AMOUNT("¥"),
    PERCENT("%"),
}

private data class RangeStats(
    val totalProfitCny: Double,
    val returnPercent: Double,
    val averageDailyProfitCny: Double,
    val winRate: Double,
    val bestDayProfitCny: Double,
    val maxDrawdownPercent: Double,
    val rangeStart: LocalDate,
    val rangeEnd: LocalDate,
)

private data class CalendarBucket(
    val title: String,
    val valueAmountCny: Double,
    val valuePercent: Double,
)

@Composable
fun ProfitAnalysisRoute(
    analysis: ProfitAnalysisUiModel,
    displayCurrency: DisplayCurrency,
    exchangeRates: ExchangeRates,
    onDisplayCurrencySelected: (DisplayCurrency) -> Unit,
    onDestinationSelected: (TopLevelDestination) -> Unit,
) {
    var selectedRange by rememberSaveable { mutableStateOf(ProfitRange.THIS_MONTH) }
    var calendarMode by rememberSaveable { mutableStateOf(CalendarMode.DAY) }
    var valueUnit by rememberSaveable { mutableStateOf(ValueUnit.AMOUNT) }
    var pageOffset by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(calendarMode) {
        pageOffset = 0
    }

    val allPoints = remember(analysis) {
        if (analysis.dailyPoints.isEmpty()) {
            listOf(
                ProfitAnalysisPointUiModel(
                    date = analysis.latestDate,
                    dailyProfitCny = 0.0,
                    cumulativeProfitCny = 0.0,
                ),
            )
        } else {
            analysis.dailyPoints
        }
    }
    val rangePoints = remember(allPoints, analysis.latestDate, selectedRange) {
        filterPointsForRange(
            points = allPoints,
            latestDate = analysis.latestDate,
            range = selectedRange,
        )
    }
    val firstDate = remember(allPoints) { allPoints.firstOrNull()?.date ?: analysis.latestDate }
    val (rangeStart, rangeEnd) = remember(analysis.latestDate, selectedRange, firstDate) {
        resolveRangeWindow(
            latestDate = analysis.latestDate,
            firstDate = firstDate,
            range = selectedRange,
        )
    }
    val rangeStats = remember(rangePoints, analysis.netInflowCny, rangeStart, rangeEnd) {
        buildRangeStats(
            points = rangePoints,
            netInflowCny = analysis.netInflowCny,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                ScreenHeader(title = "盈亏分析")

                SegmentRow(
                    options = ProfitRange.entries,
                    selected = selectedRange,
                    label = { it.label },
                    onSelected = { selectedRange = it },
                )

                CurrencySelector(
                    selected = displayCurrency,
                    onSelected = onDisplayCurrencySelected,
                )

                if (analysis.isHistoricalDataFallback) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFFFBEB))
                            .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Warning",
                            tint = Color(0xFFB45309),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "未获取到历史收盘价数据，部分历史区间的盈亏统计可能存在偏差（今日持仓总盈亏已自动对齐持仓界面）。请检查网络或稍后重试。",
                            color = Color(0xFFB45309),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                SummaryBlock(
                    stats = rangeStats,
                    displayCurrency = displayCurrency,
                    exchangeRates = exchangeRates,
                    selectedRange = selectedRange,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AnalysisStatCard(
                        title = "日均盈利",
                        value = formatSignedAmount(rangeStats.averageDailyProfitCny, displayCurrency, exchangeRates),
                        valueColor = trendColor(rangeStats.averageDailyProfitCny),
                        modifier = Modifier.weight(1f),
                    )
                    AnalysisStatCard(
                        title = "胜率",
                        value = formatWinRate(rangeStats.winRate),
                        valueColor = ForegroundPrimary,
                        modifier = Modifier.weight(1f),
                    )
                }

                ChartSection(
                    points = rangePoints,
                    stats = rangeStats,
                    displayCurrency = displayCurrency,
                    exchangeRates = exchangeRates,
                )

                CalendarSection(
                    points = allPoints,
                    latestDate = analysis.latestDate,
                    netInflowCny = analysis.netInflowCny,
                    mode = calendarMode,
                    unit = valueUnit,
                    displayCurrency = displayCurrency,
                    exchangeRates = exchangeRates,
                    pageOffset = pageOffset,
                    onModeSelected = { calendarMode = it },
                    onUnitSelected = { valueUnit = it },
                    onPreviousPage = { pageOffset = pageOffset - 1 },
                    onNextPage = { pageOffset = pageOffset + 1 },
                )
            }
        }

        BottomPillNavigation(
            current = TopLevelDestination.ANALYSIS,
            onDestinationSelected = onDestinationSelected,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun SummaryBlock(
    stats: RangeStats,
    displayCurrency: DisplayCurrency,
    exchangeRates: ExchangeRates,
    selectedRange: ProfitRange,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val titleText = if (selectedRange == ProfitRange.ALL) {
            "累计总盈亏 (${displayCurrency.code})"
        } else {
            "区间盈亏 (${displayCurrency.code})"
        }
        Text(
            text = titleText,
            color = ForegroundSecondary,
            fontSize = 14.sp,
        )
        Text(
            text = formatSignedPlainNumber(stats.totalProfitCny, displayCurrency, exchangeRates),
            color = trendColor(stats.totalProfitCny),
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = formatSignedPercent(stats.returnPercent),
            color = trendColor(stats.totalProfitCny),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "${stats.rangeStart.format(chartDateFormatter)} - ${stats.rangeEnd.format(chartDateFormatter)}",
            color = ForegroundMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun ChartSection(
    points: List<ProfitAnalysisPointUiModel>,
    stats: RangeStats,
    displayCurrency: DisplayCurrency,
    exchangeRates: ExchangeRates,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceSecondary)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "盈亏按日期统计",
            color = ForegroundPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )

        ProfitTrendChart(
            points = points,
            rangeStart = stats.rangeStart,
            rangeEnd = stats.rangeEnd,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnalysisStatCard(
                title = "最佳单日",
                value = formatSignedAmount(stats.bestDayProfitCny, displayCurrency, exchangeRates),
                valueColor = trendColor(stats.bestDayProfitCny),
                background = BackgroundPrimary,
                modifier = Modifier.weight(1f),
            )
            AnalysisStatCard(
                title = "最大回撤",
                value = formatDrawdown(stats.maxDrawdownPercent),
                valueColor = MarketUp,
                background = BackgroundPrimary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ProfitTrendChart(
    points: List<ProfitAnalysisPointUiModel>,
    rangeStart: LocalDate,
    rangeEnd: LocalDate,
) {
    val chartPoints = if (points.isEmpty()) {
        listOf(
            ProfitAnalysisPointUiModel(
                date = rangeEnd,
                dailyProfitCny = 0.0,
                cumulativeProfitCny = 0.0,
            ),
        )
    } else {
        points.sortedBy { it.date }
    }
    val totalDays = remember(rangeStart, rangeEnd) {
        ChronoUnit.DAYS.between(rangeStart, rangeEnd).coerceAtLeast(1)
    }
    val labels = remember(rangeStart, rangeEnd) {
        if (!rangeStart.isBefore(rangeEnd)) {
            listOf(null, rangeStart.format(chartDateFormatter), null)
        } else {
            val middleDate = rangeStart.plusDays(totalDays / 2)
            listOf(
                rangeStart.format(chartDateFormatter),
                middleDate.format(chartDateFormatter),
                rangeEnd.format(chartDateFormatter),
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
        ) {
            val horizontalPadding = 8.dp.toPx()
            val contentTop = 8.dp.toPx()
            val contentBottom = size.height - 28.dp.toPx()
            val contentStart = horizontalPadding
            val contentEnd = size.width - horizontalPadding
            val contentWidth = (contentEnd - contentStart).coerceAtLeast(0f)
            val contentHeight = (contentBottom - contentTop).coerceAtLeast(0f)
            val values = chartPoints.map { it.cumulativeProfitCny }
            val minValue = min(values.minOrNull() ?: 0.0, 0.0)
            val maxValue = max(values.maxOrNull() ?: 0.0, 0.0)
            val range = (maxValue - minValue).takeIf { it > 0.0 } ?: 1.0

            repeat(4) { index ->
                val ratio = index / 3f
                val y = contentTop + ratio * contentHeight
                drawLine(
                    color = BorderSubtle,
                    start = Offset(contentStart, y),
                    end = Offset(contentEnd, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            val linePath = Path()
            val areaPath = Path()
            chartPoints.forEachIndexed { index, point ->
                val dayOffset = ChronoUnit.DAYS.between(rangeStart, point.date)
                    .coerceIn(0, totalDays)
                    .toFloat()
                val x = contentStart + dayOffset / totalDays.toFloat() * contentWidth
                val y = (
                    contentTop +
                        ((maxValue - point.cumulativeProfitCny) / range * contentHeight)
                    ).toFloat()
                if (index == 0) {
                    linePath.moveTo(x, y)
                    areaPath.moveTo(x, contentBottom)
                    areaPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    areaPath.lineTo(x, y)
                }
                if (index == chartPoints.lastIndex) {
                    areaPath.lineTo(x, contentBottom)
                    areaPath.close()
                }
            }

            drawPath(
                path = areaPath,
                brush = Brush.verticalGradient(
                    colors = listOf(AnalysisFillTopColor, AnalysisFillBottomColor),
                    startY = 0f,
                    endY = contentBottom,
                ),
            )
            drawPath(
                path = linePath,
                color = AnalysisLineColor,
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                ),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            labels.forEachIndexed { index, label ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = when (index) {
                        0 -> Alignment.CenterStart
                        1 -> Alignment.Center
                        else -> Alignment.CenterEnd
                    },
                ) {
                    if (label != null) {
                        Text(
                            text = label,
                            color = ForegroundMuted,
                            fontSize = 10.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarSection(
    points: List<ProfitAnalysisPointUiModel>,
    latestDate: LocalDate,
    netInflowCny: Double,
    mode: CalendarMode,
    unit: ValueUnit,
    displayCurrency: DisplayCurrency,
    exchangeRates: ExchangeRates,
    pageOffset: Int,
    onModeSelected: (CalendarMode) -> Unit,
    onUnitSelected: (ValueUnit) -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
) {
    val visibleMonth = remember(latestDate, pageOffset) {
        YearMonth.from(latestDate).plusMonths(pageOffset.toLong())
    }
    val visibleYear = latestDate.year + pageOffset
    val yearBuckets = remember(points, latestDate, pageOffset, netInflowCny) {
        buildYearBuckets(
            points = points,
            endYear = latestDate.year + pageOffset * 6,
            netInflowCny = netInflowCny,
        )
    }
    val navLabel = remember(mode, visibleMonth, visibleYear, yearBuckets) {
        when (mode) {
            CalendarMode.DAY, CalendarMode.WEEK -> visibleMonth.atDay(1).format(monthTitleFormatter)
            CalendarMode.MONTH -> LocalDate.of(visibleYear, 1, 1).format(yearFormatter)
            CalendarMode.YEAR -> {
                val first = yearBuckets.firstOrNull()?.title.orEmpty()
                val last = yearBuckets.lastOrNull()?.title.orEmpty()
                if (first.isBlank() || last.isBlank()) {
                    latestDate.year.toString()
                } else {
                    "$first - $last"
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BackgroundPrimary)
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = if (unit == ValueUnit.AMOUNT) "收益日历 (${displayCurrency.code})" else "收益日历 (%)",
            color = ForegroundPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .background(SegmentBackground)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SegmentStrip(
                options = CalendarMode.entries,
                selected = mode,
                label = { it.label },
                onSelected = onModeSelected,
                modifier = Modifier.weight(1f),
            )
            SegmentStrip(
                options = ValueUnit.entries,
                selected = unit,
                label = { it.label },
                onSelected = onUnitSelected,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "上一个周期",
                tint = ForegroundMuted,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = onPreviousPage),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = navLabel,
                color = ForegroundPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "下一个周期",
                tint = ForegroundMuted,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = onNextPage),
            )
        }

        when (mode) {
            CalendarMode.DAY -> DailyCalendarGrid(
                points = points,
                visibleMonth = visibleMonth,
                latestDate = latestDate,
                unit = unit,
                displayCurrency = displayCurrency,
                exchangeRates = exchangeRates,
                netInflowCny = netInflowCny,
            )

            CalendarMode.WEEK -> BucketGrid(
                buckets = buildWeekBuckets(
                    points = points,
                    visibleMonth = visibleMonth,
                    netInflowCny = netInflowCny,
                ),
                unit = unit,
                displayCurrency = displayCurrency,
                exchangeRates = exchangeRates,
                columns = 2,
            )

            CalendarMode.MONTH -> BucketGrid(
                buckets = buildMonthBuckets(
                    points = points,
                    year = visibleYear,
                    netInflowCny = netInflowCny,
                ),
                unit = unit,
                displayCurrency = displayCurrency,
                exchangeRates = exchangeRates,
                columns = 3,
            )

            CalendarMode.YEAR -> BucketGrid(
                buckets = yearBuckets,
                unit = unit,
                displayCurrency = displayCurrency,
                exchangeRates = exchangeRates,
                columns = 3,
            )
        }
    }
}

@Composable
private fun DailyCalendarGrid(
    points: List<ProfitAnalysisPointUiModel>,
    visibleMonth: YearMonth,
    latestDate: LocalDate,
    unit: ValueUnit,
    displayCurrency: DisplayCurrency,
    exchangeRates: ExchangeRates,
    netInflowCny: Double,
) {
    val pointMap = remember(points) {
        points.associateBy { it.date }
    }
    val firstVisibleDate = remember(visibleMonth) {
        visibleMonth.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    }
    val weekdayLabels = listOf("日", "一", "二", "三", "四", "五", "六")

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            weekdayLabels.forEach { label ->
                Text(
                    text = label,
                    color = ForegroundPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        repeat(6) { rowIndex ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(7) { columnIndex ->
                    val currentDate = firstVisibleDate.plusDays((rowIndex * 7 + columnIndex).toLong())
                    val point = pointMap[currentDate]
                    val isCurrentMonth = YearMonth.from(currentDate) == visibleMonth
                    CalendarDayCell(
                        date = currentDate,
                        point = point,
                        isCurrentMonth = isCurrentMonth,
                        latestDate = latestDate,
                        unit = unit,
                        displayCurrency = displayCurrency,
                        exchangeRates = exchangeRates,
                        netInflowCny = netInflowCny,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    date: LocalDate,
    point: ProfitAnalysisPointUiModel?,
    isCurrentMonth: Boolean,
    latestDate: LocalDate,
    unit: ValueUnit,
    displayCurrency: DisplayCurrency,
    exchangeRates: ExchangeRates,
    netInflowCny: Double,
    modifier: Modifier = Modifier,
) {
    val amount = point?.dailyProfitCny ?: 0.0
    val percent = amountToPercent(amount, netInflowCny)
    val isFutureDate = isCurrentMonth && date.isAfter(latestDate)
    val isWeekendClosedDay = isCurrentMonth &&
        !isFutureDate &&
        amount == 0.0 &&
        date.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    val valueText = when {
        !isCurrentMonth || isFutureDate -> ""
        isWeekendClosedDay -> "休市"
        else -> formatCompactValue(
            value = if (unit == ValueUnit.AMOUNT) amount else percent,
            unit = unit,
            displayCurrency = displayCurrency,
            exchangeRates = exchangeRates,
        )
    }
    val valueFontSize = when {
        valueText.length >= 9 -> 7.sp
        valueText.length >= 8 -> 8.sp
        valueText.length >= 7 -> 9.sp
        else -> 10.sp
    }
    val background = when {
        !isCurrentMonth -> Color.Transparent
        amount > 0 -> MarketUpSoft
        amount < 0 -> MarketDownSoft
        else -> BackgroundPrimary
    }
    val valueColor = when {
        !isCurrentMonth -> Color.Transparent
        isFutureDate -> Color.Transparent
        isWeekendClosedDay -> ForegroundMuted
        amount > 0 -> MarketUp
        amount < 0 -> MarketDown
        else -> ForegroundMuted
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(
                width = if (isCurrentMonth && amount == 0.0) 1.dp else 0.dp,
                color = BorderSubtle,
                shape = RoundedCornerShape(8.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isCurrentMonth) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "%02d".format(date.dayOfMonth),
                    color = if (amount == 0.0) ForegroundPrimary else valueColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = valueText,
                    color = valueColor,
                    fontSize = valueFontSize,
                    lineHeight = 1.em,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun BucketGrid(
    buckets: List<CalendarBucket>,
    unit: ValueUnit,
    displayCurrency: DisplayCurrency,
    exchangeRates: ExchangeRates,
    columns: Int,
) {
    val rows = remember(buckets, columns) {
        buckets.chunked(columns)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { rowBuckets ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowBuckets.forEach { bucket ->
                    BucketCard(
                        bucket = bucket,
                        unit = unit,
                        displayCurrency = displayCurrency,
                        exchangeRates = exchangeRates,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - rowBuckets.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun BucketCard(
    bucket: CalendarBucket,
    unit: ValueUnit,
    displayCurrency: DisplayCurrency,
    exchangeRates: ExchangeRates,
    modifier: Modifier = Modifier,
) {
    val value = if (unit == ValueUnit.AMOUNT) bucket.valueAmountCny else bucket.valuePercent
    val background = when {
        value > 0 -> MarketUpSoft
        value < 0 -> MarketDownSoft
        else -> BackgroundPrimary
    }
    val foreground = when {
        value > 0 -> MarketUp
        value < 0 -> MarketDown
        else -> ForegroundMuted
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(
                width = if (value == 0.0) 1.dp else 0.dp,
                color = BorderSubtle,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = bucket.title,
            color = ForegroundSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = formatCompactValue(value, unit, displayCurrency, exchangeRates),
            color = foreground,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
internal fun AnalysisStatCard(
    title: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
    background: Color = SurfaceSecondary,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            color = ForegroundSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
internal fun <T> SegmentRow(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(SegmentBackground)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isSelected) BackgroundPrimary else Color.Transparent)
                    .clickable { onSelected(option) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    color = if (isSelected) ForegroundPrimary else ForegroundSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
internal fun <T> SegmentStrip(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isSelected) BackgroundPrimary else Color.Transparent)
                    .clickable { onSelected(option) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    color = if (isSelected) ForegroundPrimary else ForegroundMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

private fun filterPointsForRange(
    points: List<ProfitAnalysisPointUiModel>,
    latestDate: LocalDate,
    range: ProfitRange,
): List<ProfitAnalysisPointUiModel> {
    val startDate = when (range) {
        ProfitRange.ALL -> points.firstOrNull()?.date ?: latestDate
        ProfitRange.THIS_MONTH -> latestDate.withDayOfMonth(1)
        ProfitRange.ONE_MONTH -> latestDate.minusMonths(1).plusDays(1)
        ProfitRange.SIX_MONTHS -> latestDate.minusMonths(6).plusDays(1)
        ProfitRange.THIS_YEAR -> latestDate.withDayOfYear(1)
    }
    val priorCumulative = if (range == ProfitRange.ALL) 0.0 else points.lastOrNull { it.date.isBefore(startDate) }?.cumulativeProfitCny ?: 0.0
    val filtered = points.filter { !it.date.isBefore(startDate) && !it.date.isAfter(latestDate) }

    return if (filtered.isEmpty()) {
        listOf(
            ProfitAnalysisPointUiModel(
                date = latestDate,
                dailyProfitCny = 0.0,
                cumulativeProfitCny = 0.0,
            ),
        )
    } else {
        filtered.map { point ->
            point.copy(cumulativeProfitCny = point.cumulativeProfitCny - priorCumulative)
        }
    }
}

private fun buildRangeStats(
    points: List<ProfitAnalysisPointUiModel>,
    netInflowCny: Double,
    rangeStart: LocalDate,
    rangeEnd: LocalDate,
): RangeStats {
    val safePoints = if (points.isEmpty()) {
        listOf(
            ProfitAnalysisPointUiModel(
                date = LocalDate.now(),
                dailyProfitCny = 0.0,
                cumulativeProfitCny = 0.0,
            ),
        )
    } else {
        points
    }
    val totalProfitCny = safePoints.last().cumulativeProfitCny
    val returnPercent = amountToPercent(totalProfitCny, netInflowCny)
    val positiveDays = safePoints.count { it.dailyProfitCny > 0 }
    val activeDays = safePoints.count { it.dailyProfitCny != 0.0 }
    val denominator = max(activeDays, 1)
    val bestDayProfit = safePoints.maxOfOrNull { it.dailyProfitCny } ?: 0.0

    var peak = safePoints.first().cumulativeProfitCny
    var maxDrawdown = 0.0
    safePoints.forEach { point ->
        peak = max(peak, point.cumulativeProfitCny)
        if (peak > 0.0) {
            val drawdown = ((peak - point.cumulativeProfitCny) / peak) * 100.0
            maxDrawdown = max(maxDrawdown, drawdown)
        }
    }

    return RangeStats(
        totalProfitCny = totalProfitCny,
        returnPercent = returnPercent,
        averageDailyProfitCny = totalProfitCny / safePoints.size.coerceAtLeast(1),
        winRate = positiveDays.toDouble() / denominator.toDouble(),
        bestDayProfitCny = bestDayProfit,
        maxDrawdownPercent = maxDrawdown,
        rangeStart = rangeStart,
        rangeEnd = rangeEnd,
    )
}

private fun resolveRangeWindow(
    latestDate: LocalDate,
    firstDate: LocalDate,
    range: ProfitRange,
): Pair<LocalDate, LocalDate> {
    val startDate = when (range) {
        ProfitRange.ALL -> firstDate
        ProfitRange.THIS_MONTH -> latestDate.withDayOfMonth(1)
        ProfitRange.ONE_MONTH -> latestDate.minusMonths(1).plusDays(1)
        ProfitRange.SIX_MONTHS -> latestDate.minusMonths(6).plusDays(1)
        ProfitRange.THIS_YEAR -> latestDate.withDayOfYear(1)
    }
    return startDate to latestDate
}

private fun buildWeekBuckets(
    points: List<ProfitAnalysisPointUiModel>,
    visibleMonth: YearMonth,
    netInflowCny: Double,
): List<CalendarBucket> {
    val start = visibleMonth.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    val end = visibleMonth.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
    val pointMap = points.associateBy { it.date }
    val buckets = mutableListOf<CalendarBucket>()
    var cursor = start
    while (!cursor.isAfter(end)) {
        val weekEnd = cursor.plusDays(6)
        val amount = generateSequence(cursor) { current ->
            current.plusDays(1).takeIf { !it.isAfter(weekEnd) }
        }.sumOf { date ->
            pointMap[date]?.dailyProfitCny ?: 0.0
        }
        buckets += CalendarBucket(
            title = "${cursor.monthValue}/${cursor.dayOfMonth} - ${weekEnd.monthValue}/${weekEnd.dayOfMonth}",
            valueAmountCny = amount,
            valuePercent = amountToPercent(amount, netInflowCny),
        )
        cursor = cursor.plusWeeks(1)
    }
    return buckets
}

private fun buildMonthBuckets(
    points: List<ProfitAnalysisPointUiModel>,
    year: Int,
    netInflowCny: Double,
): List<CalendarBucket> {
    val pointMap = points.groupBy { YearMonth.from(it.date) }
    return (1..12).map { month ->
        val yearMonth = YearMonth.of(year, month)
        val amount = pointMap[yearMonth].orEmpty().sumOf { it.dailyProfitCny }
        CalendarBucket(
            title = "${month}月",
            valueAmountCny = amount,
            valuePercent = amountToPercent(amount, netInflowCny),
        )
    }
}

private fun buildYearBuckets(
    points: List<ProfitAnalysisPointUiModel>,
    endYear: Int,
    netInflowCny: Double,
): List<CalendarBucket> {
    val pointMap = points.groupBy { it.date.year }
    val startYear = endYear - 5
    return (startYear..endYear).map { year ->
        val amount = pointMap[year].orEmpty().sumOf { it.dailyProfitCny }
        CalendarBucket(
            title = year.toString(),
            valueAmountCny = amount,
            valuePercent = amountToPercent(amount, netInflowCny),
        )
    }
}

private fun trendColor(value: Double): Color = when {
    value > 0 -> MarketUp
    value < 0 -> MarketDown
    else -> ForegroundPrimary
}

private fun amountToPercent(valueCny: Double, netInflowCny: Double): Double {
    if (netInflowCny <= 0.0) return 0.0
    return (valueCny / netInflowCny) * 100.0
}

private fun convertFromCny(
    value: Double,
    currency: DisplayCurrency,
    exchangeRates: ExchangeRates,
): Double = value / exchangeRates.rateToCny(currency)

private fun formatSignedAmount(
    value: Double,
    displayCurrency: DisplayCurrency,
    exchangeRates: ExchangeRates,
): String {
    val sign = if (value >= 0) "+" else "-"
    return "$sign${displayCurrency.symbol}${numberFormatter.format(convertFromCny(value.absoluteValue, displayCurrency, exchangeRates))}"
}

private fun formatSignedPlainNumber(
    value: Double,
    displayCurrency: DisplayCurrency,
    exchangeRates: ExchangeRates,
): String {
    val sign = if (value >= 0) "+" else "-"
    return "$sign${numberFormatter.format(convertFromCny(value.absoluteValue, displayCurrency, exchangeRates))}"
}

private fun formatSignedPercent(value: Double): String {
    val sign = if (value >= 0) "+" else "-"
    return "$sign${numberFormatter.format(value.absoluteValue)}%"
}

private fun formatWinRate(value: Double): String {
    return "${wholeNumberFormatter.format(value * 100)}%"
}

private fun formatDrawdown(value: Double): String {
    return "-${numberFormatter.format(value.absoluteValue)}%"
}

private fun formatCompactValue(
    value: Double,
    unit: ValueUnit,
    displayCurrency: DisplayCurrency,
    exchangeRates: ExchangeRates,
): String {
    if (value == 0.0) {
        return if (unit == ValueUnit.AMOUNT) "--" else "0%"
    }
    val sign = if (value >= 0) "+" else "-"
    return when (unit) {
        ValueUnit.AMOUNT -> "$sign${numberFormatter.format(convertFromCny(value.absoluteValue, displayCurrency, exchangeRates))}"
        ValueUnit.PERCENT -> "$sign${numberFormatter.format(value.absoluteValue)}%"
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun ProfitAnalysisRoutePreview() {
    StockLedgerTheme {
        ProfitAnalysisRoute(
            analysis = PreviewFixtures.profitAnalysis,
            displayCurrency = DisplayCurrency.CNY,
            exchangeRates = PreviewFixtures.exchangeRates,
            onDisplayCurrencySelected = {},
            onDestinationSelected = {},
        )
    }
}
