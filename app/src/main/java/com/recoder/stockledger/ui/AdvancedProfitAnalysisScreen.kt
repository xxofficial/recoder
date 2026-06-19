package com.recoder.stockledger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.recoder.stockledger.data.BrokerPlatform
import com.recoder.stockledger.data.ExchangeRates
import com.recoder.stockledger.data.Market
import com.recoder.stockledger.data.TradeType
import com.recoder.stockledger.data.local.TransactionEntity
import com.recoder.stockledger.data.ProfitAnalysisPointUiModel
import com.recoder.stockledger.data.ProfitAnalysisUiModel
import com.recoder.stockledger.data.SecurityProfitAnalysisUiModel
import com.recoder.stockledger.data.SecurityProfitPointUiModel
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
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val AdvancedAnalysisLineColor = Color(0xFF2563EB)
private val AdvancedAnalysisFillTopColor = Color(0x332563EB)
private val AdvancedAnalysisFillBottomColor = Color(0x002563EB)
private val AdvancedSegmentBackground = Color(0xFFF3F4F6)
private val advancedNumberFormatter = DecimalFormat("#,##0.00")
private val advancedWholeNumberFormatter = DecimalFormat("0")
private val advancedChartDateFormatter = DateTimeFormatter.ofPattern("yyyy/M/d")
private val advancedMonthTitleFormatter = DateTimeFormatter.ofPattern("yyyy年M月")
private val advancedYearFormatter = DateTimeFormatter.ofPattern("yyyy年")
private val advancedWeekFields: WeekFields = WeekFields.of(DayOfWeek.SUNDAY, 1)

enum class AdvancedProfitRange(val label: String) {
    ALL("全部"),
    THIS_MONTH("本月"),
    ONE_MONTH("近1月"),
    SIX_MONTHS("近6月"),
    THIS_YEAR("今年"),
    CUSTOM("自定义"),
}

private enum class AdvancedChartMetric(val label: String) {
    RETURN("收益率走势"),
    ASSET("总资产趋势"),
    TRADE_COUNT("交易次数"),
}

private enum class AdvancedCalendarMode(val label: String) {
    DAY("日"),
    WEEK("周"),
    MONTH("月"),
    YEAR("年"),
}

private enum class AdvancedValueUnit(val label: String) {
    AMOUNT("￥"),
    PERCENT("%"),
}

private data class AdvancedRangeStats(
    val totalProfitCny: Double,
    val returnPercent: Double,
    val averageDailyProfitCny: Double,
    val winRate: Double,
    val bestDayProfitCny: Double,
    val maxDrawdownPercent: Double,
    val rangeStart: LocalDate,
    val rangeEnd: LocalDate,
)

private data class AdvancedSecurityRangeStats(
    val key: String,
    val symbol: String,
    val name: String,
    val marketLabel: String,
    val totalProfitCny: Double,
    val stockProfitCny: Double,
    val derivativeProfitCny: Double,
    val averageDailyProfitCny: Double,
    val bestDayProfitCny: Double,
    val winRate: Double,
)

private data class AdvancedCalendarBucket(
    val title: String,
    val anchorDate: LocalDate,
    val valueAmountCny: Double,
    val valuePercent: Double,
)

internal enum class ProfitCalendarDetailMode(val routeValue: String, val label: String) {
    DAY("day", "日"),
    WEEK("week", "周"),
    MONTH("month", "月"),
    YEAR("year", "年"),
}

internal enum class ProfitCalendarDetailScope {
    DAY,
    WEEK,
    MONTH,
    YEAR,
}

internal data class ProfitCalendarDetailWindow(
    val start: LocalDate,
    val end: LocalDate,
    val scope: ProfitCalendarDetailScope,
)

internal data class ProfitCalendarDetailSecurityRow(
    val key: String,
    val symbol: String,
    val name: String,
    val marketLabel: String,
    val amountCny: Double,
    val stockProfitCny: Double,
    val derivativeProfitCny: Double,
    val returnPercent: Double,
)

@Composable
fun AdvancedProfitAnalysisRoute(
    analysis: ProfitAnalysisUiModel,
    displayCurrency: DisplayCurrency,
    exchangeRates: ExchangeRates,
    selectedPlatform: BrokerPlatform?,
    selectedRange: AdvancedProfitRange = AdvancedProfitRange.THIS_MONTH,
    customStart: String = "",
    customEnd: String = "",
    onSelectedRangeChange: (AdvancedProfitRange) -> Unit = {},
    onCustomStartChange: (String) -> Unit = {},
    onCustomEndChange: (String) -> Unit = {},
    onPlatformClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDisplayCurrencySelected: (DisplayCurrency) -> Unit,
    onDestinationSelected: (TopLevelDestination) -> Unit,
    onSecurityClick: (String, String) -> Unit = { _, _ -> },
    onViewFullRanking: () -> Unit = {},
    onCalendarDetailClick: (String, LocalDate) -> Unit = { _, _ -> },
) {
    var chartMetric by rememberSaveable { mutableStateOf(AdvancedChartMetric.RETURN) }
    var calendarMode by rememberSaveable { mutableStateOf(AdvancedCalendarMode.DAY) }
    var valueUnit by rememberSaveable { mutableStateOf(AdvancedValueUnit.AMOUNT) }
    var pageOffset by rememberSaveable { mutableIntStateOf(0) }
    var customRangeEditorVisible by rememberSaveable { mutableStateOf(false) }

    val allPoints = remember(analysis) {
        analysis.dailyPoints
            .sortedBy { it.date }
            .ifEmpty {
                listOf(
                    ProfitAnalysisPointUiModel(
                        date = analysis.latestDate,
                        dailyProfitCny = 0.0,
                        cumulativeProfitCny = 0.0,
                    ),
                )
            }
    }
    val firstDate = allPoints.firstOrNull()?.date ?: analysis.latestDate
    var selectedSecurityKey by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(calendarMode) {
        pageOffset = 0
    }
    LaunchedEffect(firstDate, analysis.latestDate) {
        val currentStart = runCatching { LocalDate.parse(customStart) }.getOrNull()
        val currentEnd = runCatching { LocalDate.parse(customEnd) }.getOrNull()
        if (currentStart == null || currentStart.isBefore(firstDate) || currentStart.isAfter(analysis.latestDate)) {
            onCustomStartChange(firstDate.toString())
        }
        if (currentEnd == null || currentEnd.isBefore(firstDate) || currentEnd.isAfter(analysis.latestDate)) {
            onCustomEndChange(analysis.latestDate.toString())
        }
    }

    val (rangeStart, rangeEnd) = remember(
        selectedRange,
        customStart,
        customEnd,
        firstDate,
        analysis.latestDate,
    ) {
        resolveAdvancedRangeWindow(
            latestDate = analysis.latestDate,
            firstDate = firstDate,
            range = selectedRange,
            customStart = customStart,
            customEnd = customEnd,
        )
    }
    val rangePoints = remember(allPoints, rangeStart, rangeEnd) {
        filterAdvancedPointsForRange(
            points = allPoints,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
        )
    }
    val rangeStats = remember(rangePoints, rangeStart, rangeEnd) {
        buildAdvancedRangeStats(
            points = rangePoints,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
        )
    }
    val rangeFeeStats = remember(analysis.transactions, rangeStart, rangeEnd, exchangeRates) {
        val rangeTxns = analysis.transactions.filter { txn ->
            val tradeType = runCatching { TradeType.valueOf(txn.tradeType) }.getOrNull()
            tradeType?.isSecurityTrade == true &&
                txn.tradeDate >= rangeStart.toString() &&
                txn.tradeDate <= rangeEnd.toString()
        }
        Triple(
            rangeTxns.sumOf { txn ->
                val market = Market.fromString(txn.market) ?: Market.US
                txn.commission * exchangeRates.rateToCny(market)
            },
            rangeTxns.sumOf { txn ->
                val market = Market.fromString(txn.market) ?: Market.US
                txn.tax * exchangeRates.rateToCny(market)
            },
            rangeTxns.size,
        )
    }
    val securityStats = remember(analysis.securityAnalyses, rangeStart, rangeEnd) {
        analysis.securityAnalyses
            .mapNotNull { buildAdvancedSecurityRangeStats(it, rangeStart, rangeEnd) }
            .sortedByDescending { it.totalProfitCny.absoluteValue }
    }
    LaunchedEffect(securityStats) {
        if (securityStats.none { it.key == selectedSecurityKey }) {
            selectedSecurityKey = securityStats.firstOrNull()?.key.orEmpty()
        }
    }
    val selectedSecurityStats = securityStats.firstOrNull { it.key == selectedSecurityKey }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PlatformTopBar(
                selectedPlatform = selectedPlatform,
                onClick = onPlatformClick,
                onSettingsClick = onSettingsClick,
                modifier = Modifier.statusBarsPadding(),
            )

            // Fixed time range selector (stays at top when scrolling)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SegmentRow(
                    options = AdvancedProfitRange.entries,
                    selected = if (selectedRange == AdvancedProfitRange.CUSTOM && !customRangeEditorVisible) {
                        null
                    } else {
                        selectedRange
                    },
                    label = { it.label },
                    onSelected = { range ->
                        if (range == AdvancedProfitRange.CUSTOM) {
                            customRangeEditorVisible = true
                        } else {
                            customRangeEditorVisible = false
                        }
                        onSelectedRangeChange(range)
                    },
                )

                if (selectedRange == AdvancedProfitRange.CUSTOM && customRangeEditorVisible) {
                    AdvancedCustomDateRangeEditor(
                        customStart = customStart,
                        customEnd = customEnd,
                        minDate = firstDate,
                        maxDate = analysis.latestDate,
                        onApply = { start, end ->
                            onCustomStartChange(start)
                            onCustomEndChange(end)
                            customRangeEditorVisible = false
                        },
                        fallbackDate = analysis.latestDate,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Scrollable content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
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

                AdvancedSummaryBlock(
                    stats = rangeStats,
                    displayCurrency = displayCurrency,
                    exchangeRates = exchangeRates,
                    onDisplayCurrencySelected = onDisplayCurrencySelected,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AnalysisStatCard(
                        title = "日均盈利",
                        value = advancedFormatSignedAmount(rangeStats.averageDailyProfitCny, displayCurrency, exchangeRates),
                        valueColor = advancedTrendColor(rangeStats.averageDailyProfitCny),
                        modifier = Modifier.weight(1f),
                    )
                    AnalysisStatCard(
                        title = "胜率",
                        value = advancedFormatWinRate(rangeStats.winRate),
                        valueColor = ForegroundPrimary,
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AnalysisStatCard(
                        title = "佣金/平台费",
                        value = advancedFormatUnsignedAmount(rangeFeeStats.first, displayCurrency, exchangeRates),
                        valueColor = ForegroundPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    AnalysisStatCard(
                        title = "税费",
                        value = advancedFormatUnsignedAmount(rangeFeeStats.second, displayCurrency, exchangeRates),
                        valueColor = ForegroundPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    AnalysisStatCard(
                        title = "交易次数",
                        value = "${rangeFeeStats.third} 笔",
                        valueColor = ForegroundPrimary,
                        modifier = Modifier.weight(1f),
                    )
                }

                AdvancedChartSection(
                    points = rangePoints,
                    stats = rangeStats,
                    metric = chartMetric,
                    displayCurrency = displayCurrency,
                    exchangeRates = exchangeRates,
                    onMetricSelected = { chartMetric = it },
                )

                // Calendar before leaderboard
                AdvancedCalendarSection(
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
                    onPreviousPage = { pageOffset -= 1 },
                    onNextPage = { pageOffset += 1 },
                    onBucketClick = onCalendarDetailClick,
                )

                if (securityStats.isNotEmpty()) {
                    AdvancedSecuritySection(
                        stats = securityStats,
                        displayCurrency = displayCurrency,
                        exchangeRates = exchangeRates,
                        onSecurityClick = onSecurityClick,
                        onViewFullRanking = onViewFullRanking,
                    )
                }
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
private fun AdvancedSummaryBlock(
    stats: AdvancedRangeStats,
    displayCurrency: DisplayCurrency,
    exchangeRates: ExchangeRates,
    onDisplayCurrencySelected: (DisplayCurrency) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        InlineCurrencyDropdown(
            title = "区间盈亏",
            selected = displayCurrency,
            onSelected = onDisplayCurrencySelected,
        )
        Text(
            text = advancedFormatSignedPlainNumber(stats.totalProfitCny, displayCurrency, exchangeRates),
            color = advancedTrendColor(stats.totalProfitCny),
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = advancedFormatSignedPercent(stats.returnPercent),
            color = advancedTrendColor(stats.totalProfitCny),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "${stats.rangeStart.format(advancedChartDateFormatter)} - ${stats.rangeEnd.format(advancedChartDateFormatter)}",
            color = ForegroundMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun AdvancedChartSection(
    points: List<ProfitAnalysisPointUiModel>,
    stats: AdvancedRangeStats,
    metric: AdvancedChartMetric,
    displayCurrency: DisplayCurrency,
    exchangeRates: ExchangeRates,
    onMetricSelected: (AdvancedChartMetric) -> Unit,
) {
    var selectedDate by rememberSaveable { mutableStateOf(points.lastOrNull()?.date?.toString().orEmpty()) }
    LaunchedEffect(points) {
        if (points.none { it.date.toString() == selectedDate }) {
            selectedDate = points.lastOrNull()?.date?.toString().orEmpty()
        }
    }
    val selectedPoint = points.firstOrNull { it.date.toString() == selectedDate } ?: points.lastOrNull()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceSecondary)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "收益趋势",
            color = ForegroundPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )

        SegmentRow(
            options = AdvancedChartMetric.entries,
            selected = metric,
            label = { it.label },
            onSelected = onMetricSelected,
        )

        AdvancedTrendChart(
            points = points,
            rangeStart = stats.rangeStart,
            rangeEnd = stats.rangeEnd,
            metric = metric,
            displayCurrency = displayCurrency,
            exchangeRates = exchangeRates,
            selectedDate = selectedDate,
            onSelectedDate = { selectedDate = it },
        )

        selectedPoint?.let { point ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = point.date.format(advancedChartDateFormatter),
                    color = ForegroundPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = when (metric) {
                        AdvancedChartMetric.RETURN -> "收益率 ${advancedFormatSignedPercent(point.cumulativeReturnPercent)}"
                        AdvancedChartMetric.ASSET -> "总资产 ${advancedFormatUnsignedAmount(point.totalAssetsCny, displayCurrency, exchangeRates)}"
                        AdvancedChartMetric.TRADE_COUNT -> "成交 ${point.dailySecurityTradeCount} 笔"
                    },
                    color = ForegroundSecondary,
                    fontSize = 13.sp,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AnalysisStatCard(
                        title = when (metric) {
                            AdvancedChartMetric.RETURN -> "当天收益率"
                            AdvancedChartMetric.ASSET -> "当天收益"
                            AdvancedChartMetric.TRADE_COUNT -> "买入/卖出"
                        },
                        value = when (metric) {
                            AdvancedChartMetric.RETURN -> advancedFormatSignedPercent(point.dailyReturnPercent)
                            AdvancedChartMetric.ASSET -> advancedFormatSignedAmount(point.dailyProfitCny, displayCurrency, exchangeRates)
                            AdvancedChartMetric.TRADE_COUNT -> "${point.dailyBuyCount}/${point.dailySellCount} 笔"
                        },
                        valueColor = advancedTrendColor(
                            when (metric) {
                                AdvancedChartMetric.RETURN -> point.dailyReturnPercent
                                AdvancedChartMetric.ASSET -> point.dailyProfitCny
                                AdvancedChartMetric.TRADE_COUNT -> 0.0
                            },
                        ),
                        background = BackgroundPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    AnalysisStatCard(
                        title = when (metric) {
                            AdvancedChartMetric.RETURN -> "累计收益率"
                            AdvancedChartMetric.ASSET -> "累计收益"
                            AdvancedChartMetric.TRADE_COUNT -> "费用合计"
                        },
                        value = when (metric) {
                            AdvancedChartMetric.RETURN -> advancedFormatSignedPercent(point.cumulativeReturnPercent)
                            AdvancedChartMetric.ASSET -> advancedFormatSignedAmount(point.cumulativeProfitCny, displayCurrency, exchangeRates)
                            AdvancedChartMetric.TRADE_COUNT -> advancedFormatUnsignedAmount(
                                point.dailyCommissionCny + point.dailyTaxCny,
                                displayCurrency,
                                exchangeRates,
                            )
                        },
                        valueColor = advancedTrendColor(
                            when (metric) {
                                AdvancedChartMetric.RETURN -> point.cumulativeReturnPercent
                                AdvancedChartMetric.ASSET -> point.cumulativeProfitCny
                                AdvancedChartMetric.TRADE_COUNT -> 0.0
                            },
                        ),
                        background = BackgroundPrimary,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (metric == AdvancedChartMetric.TRADE_COUNT) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AnalysisStatCard(
                            title = "佣金/平台费",
                            value = advancedFormatUnsignedAmount(point.dailyCommissionCny, displayCurrency, exchangeRates),
                            valueColor = ForegroundPrimary,
                            background = BackgroundPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        AnalysisStatCard(
                            title = "税费",
                            value = advancedFormatUnsignedAmount(point.dailyTaxCny, displayCurrency, exchangeRates),
                            valueColor = ForegroundPrimary,
                            background = BackgroundPrimary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        if (metric != AdvancedChartMetric.TRADE_COUNT) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AnalysisStatCard(
                    title = "最佳单日",
                    value = advancedFormatSignedAmount(stats.bestDayProfitCny, displayCurrency, exchangeRates),
                    valueColor = advancedTrendColor(stats.bestDayProfitCny),
                    background = BackgroundPrimary,
                    modifier = Modifier.weight(1f),
                )
                AnalysisStatCard(
                    title = "最大回撤",
                    value = advancedFormatDrawdown(stats.maxDrawdownPercent),
                    valueColor = MarketUp,
                    background = BackgroundPrimary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AdvancedTrendChart(
    points: List<ProfitAnalysisPointUiModel>,
    rangeStart: LocalDate,
    rangeEnd: LocalDate,
    metric: AdvancedChartMetric,
    displayCurrency: DisplayCurrency,
    exchangeRates: ExchangeRates,
    selectedDate: String,
    onSelectedDate: (String) -> Unit,
) {
    val chartPoints = points.sortedBy { it.date }.ifEmpty {
        listOf(
            ProfitAnalysisPointUiModel(
                date = rangeEnd,
                dailyProfitCny = 0.0,
                cumulativeProfitCny = 0.0,
            ),
        )
    }
    val totalDays = remember(rangeStart, rangeEnd) {
        ChronoUnit.DAYS.between(rangeStart, rangeEnd).coerceAtLeast(1)
    }
    val values = remember(chartPoints, metric, displayCurrency, exchangeRates) {
        chartPoints.map { point ->
            when (metric) {
                AdvancedChartMetric.RETURN -> point.cumulativeReturnPercent
                AdvancedChartMetric.ASSET -> advancedConvertFromCny(point.totalAssetsCny, displayCurrency, exchangeRates)
                AdvancedChartMetric.TRADE_COUNT -> point.dailySecurityTradeCount.toDouble()
            }
        }
    }
    val axisValues = remember(values, metric) { buildAdvancedAxisValues(values, metric) }
    val topAxisValue = axisValues.first()
    val bottomAxisValue = axisValues.last()
    val axisRange = (topAxisValue - bottomAxisValue).takeIf { it > 0.0 } ?: 1.0
    val labels = remember(rangeStart, rangeEnd, totalDays) {
        if (!rangeStart.isBefore(rangeEnd)) {
            listOf(null, rangeStart.format(advancedChartDateFormatter), null)
        } else {
            listOf(
                rangeStart.format(advancedChartDateFormatter),
                rangeStart.plusDays(totalDays / 2).format(advancedChartDateFormatter),
                rangeEnd.format(advancedChartDateFormatter),
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.height(180.dp).width(52.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                axisValues.forEach { value ->
                    Text(
                        text = formatAdvancedAxisValue(value, metric, displayCurrency),
                        color = ForegroundMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                    )
                }
            }

            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(180.dp)
                    .pointerInput(chartPoints, metric, rangeStart, totalDays) {
                        fun selectNearest(offset: Offset) {
                            val horizontalPadding = 8.dp.toPx()
                            val contentStart = horizontalPadding
                            val contentEnd = size.width - horizontalPadding
                            val contentWidth = (contentEnd - contentStart).coerceAtLeast(1f)
                            val selectedPoint = chartPoints.minByOrNull { point ->
                                val dayOffset = ChronoUnit.DAYS.between(rangeStart, point.date)
                                    .coerceIn(0, totalDays)
                                    .toFloat()
                                val pointX = contentStart + dayOffset / totalDays.toFloat() * contentWidth
                                (pointX - offset.x).absoluteValue
                            }
                            selectedPoint?.let { onSelectedDate(it.date.toString()) }
                        }
                        detectTapGestures { offset ->
                            selectNearest(offset)
                        }
                    }
                    .pointerInput(chartPoints, metric, rangeStart, totalDays) {
                        fun selectNearest(offset: Offset) {
                            val horizontalPadding = 8.dp.toPx()
                            val contentStart = horizontalPadding
                            val contentEnd = size.width - horizontalPadding
                            val contentWidth = (contentEnd - contentStart).coerceAtLeast(1f)
                            val selectedPoint = chartPoints.minByOrNull { point ->
                                val dayOffset = ChronoUnit.DAYS.between(rangeStart, point.date)
                                    .coerceIn(0, totalDays)
                                    .toFloat()
                                val pointX = contentStart + dayOffset / totalDays.toFloat() * contentWidth
                                (pointX - offset.x).absoluteValue
                            }
                            selectedPoint?.let { onSelectedDate(it.date.toString()) }
                        }
                        detectDragGestures(
                            onDragStart = { offset -> selectNearest(offset) },
                            onDrag = { change, _ -> selectNearest(change.position) },
                        )
                    },
            ) {
                val horizontalPadding = 8.dp.toPx()
                val contentTop = 8.dp.toPx()
                val contentBottom = size.height - 8.dp.toPx()
                val contentStart = horizontalPadding
                val contentEnd = size.width - horizontalPadding
                val contentWidth = (contentEnd - contentStart).coerceAtLeast(1f)
                val contentHeight = (contentBottom - contentTop).coerceAtLeast(1f)

                axisValues.forEachIndexed { index, _ ->
                    val ratio = if (axisValues.size == 1) 0f else index / (axisValues.lastIndex).toFloat()
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
                val selectedPoint = chartPoints.firstOrNull { it.date.toString() == selectedDate }
                var selectedOffset: Offset? = null

                chartPoints.forEachIndexed { index, point ->
                    val value = when (metric) {
                        AdvancedChartMetric.RETURN -> point.cumulativeReturnPercent
                        AdvancedChartMetric.ASSET -> advancedConvertFromCny(point.totalAssetsCny, displayCurrency, exchangeRates)
                        AdvancedChartMetric.TRADE_COUNT -> point.dailySecurityTradeCount.toDouble()
                    }
                    val dayOffset = ChronoUnit.DAYS.between(rangeStart, point.date)
                        .coerceIn(0, totalDays)
                        .toFloat()
                    val x = contentStart + dayOffset / totalDays.toFloat() * contentWidth
                    val y = (contentTop + ((topAxisValue - value) / axisRange * contentHeight)).toFloat()
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
                    if (selectedPoint?.date == point.date) {
                        selectedOffset = Offset(x, y)
                    }
                }

                drawPath(
                    path = areaPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(AdvancedAnalysisFillTopColor, AdvancedAnalysisFillBottomColor),
                        startY = 0f,
                        endY = contentBottom,
                    ),
                )
                drawPath(
                    path = linePath,
                    color = AdvancedAnalysisLineColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                )

                selectedOffset?.let { offset ->
                    drawLine(
                        color = AdvancedAnalysisLineColor.copy(alpha = 0.25f),
                        start = Offset(offset.x, contentTop),
                        end = Offset(offset.x, contentBottom),
                        strokeWidth = 1.dp.toPx(),
                    )
                    drawCircle(
                        color = BackgroundPrimary,
                        radius = 5.dp.toPx(),
                        center = offset,
                    )
                    drawCircle(
                        color = AdvancedAnalysisLineColor,
                        radius = 3.dp.toPx(),
                        center = offset,
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 60.dp),
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
private fun AdvancedSecuritySection(
    stats: List<AdvancedSecurityRangeStats>,
    displayCurrency: DisplayCurrency,
    exchangeRates: ExchangeRates,
    onSecurityClick: (String, String) -> Unit,
    onViewFullRanking: () -> Unit,
) {
    var showProfit by rememberSaveable { mutableStateOf(true) }
    val ranked = if (showProfit) {
        stats.filter { it.totalProfitCny > 0 }.sortedByDescending { it.totalProfitCny }
    } else {
        stats.filter { it.totalProfitCny < 0 }.sortedBy { it.totalProfitCny }
    }
    val top5 = ranked.take(5)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceSecondary)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "区间盈亏排行",
            color = ForegroundPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )

        // Tab chips
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(true to "盈利Top5", false to "亏损Top5").forEach { (isProfit, label) ->
                val selected = showProfit == isProfit
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected) SurfaceInverse else BackgroundPrimary)
                        .border(
                            width = if (selected) 0.dp else 1.dp,
                            color = BorderSubtle,
                            shape = RoundedCornerShape(999.dp),
                        )
                        .clickable { showProfit = isProfit }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = label,
                        color = if (selected) BackgroundPrimary else ForegroundPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        // Leaderboard list
        if (top5.isEmpty()) {
            Text(
                text = if (showProfit) "当前区间内没有盈利的股票" else "当前区间内没有亏损的股票",
                color = ForegroundMuted,
                fontSize = 13.sp,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                top5.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSecurityClick(item.symbol, item.marketLabel) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${index + 1}",
                            color = ForegroundMuted,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(24.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                color = ForegroundPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = "${item.symbol} · ${item.marketLabel}",
                                color = ForegroundMuted,
                                fontSize = 11.sp,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = advancedFormatSignedAmount(item.totalProfitCny, displayCurrency, exchangeRates),
                                color = advancedTrendColor(item.totalProfitCny),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = ForegroundMuted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        // View full ranking link
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onViewFullRanking() }
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "查看完整排行 >",
                color = AdvancedAnalysisLineColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun AdvancedCalendarSection(
    points: List<ProfitAnalysisPointUiModel>,
    latestDate: LocalDate,
    netInflowCny: Double,
    mode: AdvancedCalendarMode,
    unit: AdvancedValueUnit,
    displayCurrency: DisplayCurrency,
    exchangeRates: ExchangeRates,
    pageOffset: Int,
    onModeSelected: (AdvancedCalendarMode) -> Unit,
    onUnitSelected: (AdvancedValueUnit) -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onBucketClick: (String, LocalDate) -> Unit = { _, _ -> },
) {
    val visibleMonth = remember(latestDate, pageOffset) {
        YearMonth.from(latestDate).plusMonths(pageOffset.toLong())
    }
    val visibleYear = latestDate.year + pageOffset
    val yearBuckets = remember(points, latestDate, pageOffset, netInflowCny) {
        buildAdvancedYearBuckets(points, latestDate.year + pageOffset * 6, netInflowCny)
    }
    val navLabel = remember(mode, visibleMonth, visibleYear, yearBuckets) {
        when (mode) {
            AdvancedCalendarMode.DAY, AdvancedCalendarMode.WEEK -> visibleMonth.atDay(1).format(advancedMonthTitleFormatter)
            AdvancedCalendarMode.MONTH -> LocalDate.of(visibleYear, 1, 1).format(advancedYearFormatter)
            AdvancedCalendarMode.YEAR -> {
                val first = yearBuckets.firstOrNull()?.title.orEmpty()
                val last = yearBuckets.lastOrNull()?.title.orEmpty()
                if (first.isBlank() || last.isBlank()) latestDate.year.toString() else "$first - $last"
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
            text = if (unit == AdvancedValueUnit.AMOUNT) "收益日历 (${displayCurrency.code})" else "收益日历 (%)",
            color = ForegroundPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .background(AdvancedSegmentBackground)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SegmentStrip(
                options = AdvancedCalendarMode.entries,
                selected = mode,
                label = { it.label },
                onSelected = onModeSelected,
                modifier = Modifier.weight(1f),
            )
            SegmentStrip(
                options = AdvancedValueUnit.entries,
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
                contentDescription = "上一页",
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
                contentDescription = "下一页",
                tint = ForegroundMuted,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = onNextPage),
            )
        }

        when (mode) {
            AdvancedCalendarMode.DAY -> AdvancedDailyCalendarGrid(
                points = points,
                visibleMonth = visibleMonth,
                latestDate = latestDate,
                unit = unit,
                displayCurrency = displayCurrency,
                exchangeRates = exchangeRates,
                netInflowCny = netInflowCny,
                onDateClick = { date -> onBucketClick(ProfitCalendarDetailMode.DAY.routeValue, date) },
            )

            AdvancedCalendarMode.WEEK -> AdvancedBucketGrid(
                buckets = buildAdvancedWeekBuckets(points, visibleMonth, netInflowCny),
                unit = unit,
                displayCurrency = displayCurrency,
                exchangeRates = exchangeRates,
                columns = 2,
                onBucketClick = { bucket -> onBucketClick(ProfitCalendarDetailMode.WEEK.routeValue, bucket.anchorDate) },
            )

            AdvancedCalendarMode.MONTH -> AdvancedBucketGrid(
                buckets = buildAdvancedMonthBuckets(points, visibleYear, netInflowCny),
                unit = unit,
                displayCurrency = displayCurrency,
                exchangeRates = exchangeRates,
                columns = 3,
                onBucketClick = { bucket -> onBucketClick(ProfitCalendarDetailMode.MONTH.routeValue, bucket.anchorDate) },
            )

            AdvancedCalendarMode.YEAR -> AdvancedBucketGrid(
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
private fun AdvancedDailyCalendarGrid(
    points: List<ProfitAnalysisPointUiModel>,
    visibleMonth: YearMonth,
    latestDate: LocalDate,
    unit: AdvancedValueUnit,
    displayCurrency: DisplayCurrency,
    exchangeRates: ExchangeRates,
    netInflowCny: Double,
    onDateClick: (LocalDate) -> Unit = {},
) {
    val pointMap = remember(points) { points.associateBy { it.date } }
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
                    AdvancedCalendarDayCell(
                        date = currentDate,
                        point = point,
                        isCurrentMonth = isCurrentMonth,
                        latestDate = latestDate,
                        unit = unit,
                        displayCurrency = displayCurrency,
                        exchangeRates = exchangeRates,
                        netInflowCny = netInflowCny,
                        onClick = if (isCurrentMonth) {
                            { onDateClick(currentDate) }
                        } else {
                            null
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun AdvancedCalendarDayCell(
    date: LocalDate,
    point: ProfitAnalysisPointUiModel?,
    isCurrentMonth: Boolean,
    latestDate: LocalDate,
    unit: AdvancedValueUnit,
    displayCurrency: DisplayCurrency,
    exchangeRates: ExchangeRates,
    netInflowCny: Double,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val amount = point?.dailyProfitCny ?: 0.0
    val percent = advancedAmountToPercent(amount, netInflowCny)
    val hasPoint = point != null
    val isFutureDate = isCurrentMonth && date.isAfter(latestDate)
    val isWeekendClosedDay = isCurrentMonth &&
        !isFutureDate &&
        !hasPoint &&
        date.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    val valueText = when {
        !isCurrentMonth || isFutureDate -> ""
        isWeekendClosedDay -> "休市"
        !hasPoint -> ""
        else -> advancedFormatCompactValue(
            value = if (unit == AdvancedValueUnit.AMOUNT) amount else percent,
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
        hasPoint && amount > 0 -> MarketUpSoft
        hasPoint && amount < 0 -> MarketDownSoft
        else -> BackgroundPrimary
    }
    val valueColor = when {
        !isCurrentMonth || isFutureDate -> Color.Transparent
        isWeekendClosedDay -> ForegroundMuted
        !hasPoint -> Color.Transparent
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
                width = if (isCurrentMonth && (!hasPoint || amount == 0.0)) 1.dp else 0.dp,
                color = BorderSubtle,
                shape = RoundedCornerShape(8.dp),
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (isCurrentMonth) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "%02d".format(date.dayOfMonth),
                    color = if (!hasPoint || amount == 0.0) ForegroundPrimary else valueColor,
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
private fun AdvancedBucketGrid(
    buckets: List<AdvancedCalendarBucket>,
    unit: AdvancedValueUnit,
    displayCurrency: DisplayCurrency,
    exchangeRates: ExchangeRates,
    columns: Int,
    onBucketClick: (AdvancedCalendarBucket) -> Unit = {},
    isBucketSelected: (AdvancedCalendarBucket) -> Boolean = { false },
) {
    val rows = remember(buckets, columns) { buckets.chunked(columns) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { rowBuckets ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowBuckets.forEach { bucket ->
                    AdvancedBucketCard(
                        bucket = bucket,
                        unit = unit,
                        displayCurrency = displayCurrency,
                        exchangeRates = exchangeRates,
                        selected = isBucketSelected(bucket),
                        onClick = { onBucketClick(bucket) },
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
private fun AdvancedBucketCard(
    bucket: AdvancedCalendarBucket,
    unit: AdvancedValueUnit,
    displayCurrency: DisplayCurrency,
    exchangeRates: ExchangeRates,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val value = if (unit == AdvancedValueUnit.AMOUNT) bucket.valueAmountCny else bucket.valuePercent
    val background = when {
        selected -> MarketUp
        value > 0 -> MarketUpSoft
        value < 0 -> MarketDownSoft
        else -> BackgroundPrimary
    }
    val foreground = when {
        selected -> BackgroundPrimary
        value > 0 -> MarketUp
        value < 0 -> MarketDown
        else -> ForegroundMuted
    }
    val titleColor = if (selected) BackgroundPrimary else ForegroundSecondary

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(
                width = if (value == 0.0) 1.dp else 0.dp,
                color = BorderSubtle,
                shape = RoundedCornerShape(12.dp),
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = bucket.title,
            color = titleColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = advancedFormatCompactValue(value, unit, displayCurrency, exchangeRates),
            color = foreground,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun ProfitCalendarDetailRoute(
    analysis: ProfitAnalysisUiModel,
    displayCurrency: DisplayCurrency,
    exchangeRates: ExchangeRates,
    initialMode: String,
    initialDate: LocalDate,
    onDisplayCurrencySelected: (DisplayCurrency) -> Unit,
    onBack: () -> Unit,
) {
    val allPoints = remember(analysis) {
        analysis.dailyPoints.sortedBy { it.date }.ifEmpty {
            listOf(ProfitAnalysisPointUiModel(date = analysis.latestDate, dailyProfitCny = 0.0, cumulativeProfitCny = 0.0))
        }
    }
    var mode by rememberSaveable(initialMode) {
        mutableStateOf(parseProfitCalendarDetailMode(initialMode))
    }
    var anchorDateText by rememberSaveable(initialDate) {
        mutableStateOf(initialDate.toString())
    }
    var scope by rememberSaveable(initialMode) {
        mutableStateOf(mode.defaultScope())
    }
    var calendarUnit by rememberSaveable { mutableStateOf(AdvancedValueUnit.AMOUNT) }
    var yearPageEnd by rememberSaveable(initialDate) { mutableIntStateOf(initialDate.year) }
    var sortAscending by rememberSaveable { mutableStateOf(false) }

    val anchorDate = remember(anchorDateText) {
        runCatching { LocalDate.parse(anchorDateText) }.getOrNull() ?: analysis.latestDate
    }
    val visibleMonth = remember(mode, anchorDate) {
        when (mode) {
            ProfitCalendarDetailMode.DAY, ProfitCalendarDetailMode.WEEK -> YearMonth.from(anchorDate)
            ProfitCalendarDetailMode.MONTH, ProfitCalendarDetailMode.YEAR -> YearMonth.of(anchorDate.year, 1)
        }
    }
    val detailWindow = remember(mode, scope, anchorDate) {
        resolveProfitCalendarDetailWindow(mode, scope, anchorDate)
    }
    val rangePoints = remember(allPoints, detailWindow) {
        filterAdvancedPointsForRange(allPoints, detailWindow.start, detailWindow.end)
    }
    val rangeStats = remember(rangePoints, detailWindow) {
        buildAdvancedRangeStats(rangePoints, detailWindow.start, detailWindow.end)
    }
    val securityRows = remember(analysis.securityAnalyses, detailWindow, analysis.netInflowCny) {
        buildProfitCalendarDetailSecurityRows(
            securityAnalyses = analysis.securityAnalyses,
            rangeStart = detailWindow.start,
            rangeEnd = detailWindow.end,
            netInflowCny = analysis.netInflowCny,
        )
    }
    val sortedRows = remember(securityRows, sortAscending) {
        if (sortAscending) {
            securityRows.sortedBy { it.amountCny }
        } else {
            securityRows.sortedByDescending { it.amountCny }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            ProfitCalendarDetailHeader(
                displayCurrency = displayCurrency,
                onDisplayCurrencySelected = onDisplayCurrencySelected,
                onBack = onBack,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ProfitCalendarDetailCalendar(
                    points = allPoints,
                    mode = mode,
                    unit = calendarUnit,
                    anchorDate = anchorDate,
                    selectedWindow = detailWindow,
                    visibleMonth = visibleMonth,
                    yearPageEnd = yearPageEnd,
                    displayCurrency = displayCurrency,
                    exchangeRates = exchangeRates,
                    netInflowCny = analysis.netInflowCny,
                    onModeSelected = { nextMode ->
                        mode = nextMode
                        scope = nextMode.defaultScope()
                        if (nextMode == ProfitCalendarDetailMode.YEAR && anchorDate.year !in (yearPageEnd - 5)..yearPageEnd) {
                            yearPageEnd = anchorDate.year
                        }
                    },
                    onUnitSelected = { calendarUnit = it },
                    onPreviousPage = {
                        when (mode) {
                            ProfitCalendarDetailMode.DAY, ProfitCalendarDetailMode.WEEK -> {
                                anchorDateText = anchorDate.minusMonths(1).toString()
                            }
                            ProfitCalendarDetailMode.MONTH -> {
                                anchorDateText = anchorDate.minusYears(1).toString()
                            }
                            ProfitCalendarDetailMode.YEAR -> {
                                yearPageEnd -= 6
                                anchorDateText = LocalDate.of(yearPageEnd, 1, 1).toString()
                                scope = ProfitCalendarDetailScope.YEAR
                            }
                        }
                    },
                    onNextPage = {
                        when (mode) {
                            ProfitCalendarDetailMode.DAY, ProfitCalendarDetailMode.WEEK -> {
                                anchorDateText = anchorDate.plusMonths(1).toString()
                            }
                            ProfitCalendarDetailMode.MONTH -> {
                                anchorDateText = anchorDate.plusYears(1).toString()
                            }
                            ProfitCalendarDetailMode.YEAR -> {
                                yearPageEnd += 6
                                anchorDateText = LocalDate.of(yearPageEnd, 1, 1).toString()
                                scope = ProfitCalendarDetailScope.YEAR
                            }
                        }
                    },
                    onBlankClick = {
                        scope = when (mode) {
                            ProfitCalendarDetailMode.DAY, ProfitCalendarDetailMode.WEEK -> ProfitCalendarDetailScope.MONTH
                            ProfitCalendarDetailMode.MONTH -> ProfitCalendarDetailScope.YEAR
                            ProfitCalendarDetailMode.YEAR -> ProfitCalendarDetailScope.YEAR
                        }
                    },
                    onDateSelected = { date ->
                        anchorDateText = date.toString()
                        scope = ProfitCalendarDetailScope.DAY
                    },
                    onWeekSelected = { date ->
                        anchorDateText = date.toString()
                        scope = ProfitCalendarDetailScope.WEEK
                    },
                    onMonthSelected = { date ->
                        anchorDateText = date.toString()
                        scope = ProfitCalendarDetailScope.MONTH
                    },
                    onYearSelected = { date ->
                        anchorDateText = date.toString()
                        scope = ProfitCalendarDetailScope.YEAR
                    },
                )

                ProfitCalendarDetailSummaryCard(
                    stats = rangeStats,
                    displayCurrency = displayCurrency,
                    exchangeRates = exchangeRates,
                )

                ProfitCalendarDetailSecurityTable(
                    title = buildProfitCalendarDetailTitle(detailWindow, anchorDate),
                    rows = sortedRows,
                    displayCurrency = displayCurrency,
                    exchangeRates = exchangeRates,
                    sortAscending = sortAscending,
                    onSortClick = { sortAscending = !sortAscending },
                )
            }
        }
    }
}

@Composable
private fun ProfitCalendarDetailHeader(
    displayCurrency: DisplayCurrency,
    onDisplayCurrencySelected: (DisplayCurrency) -> Unit,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = "返回",
            tint = ForegroundPrimary,
            modifier = Modifier
                .size(24.dp)
                .clickable(onClick = onBack),
        )
        InlineCurrencyDropdown(
            title = "收益日历",
            selected = displayCurrency,
            onSelected = onDisplayCurrencySelected,
        )
        Spacer(modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun ProfitCalendarDetailCalendar(
    points: List<ProfitAnalysisPointUiModel>,
    mode: ProfitCalendarDetailMode,
    unit: AdvancedValueUnit,
    anchorDate: LocalDate,
    selectedWindow: ProfitCalendarDetailWindow,
    visibleMonth: YearMonth,
    yearPageEnd: Int,
    displayCurrency: DisplayCurrency,
    exchangeRates: ExchangeRates,
    netInflowCny: Double,
    onModeSelected: (ProfitCalendarDetailMode) -> Unit,
    onUnitSelected: (AdvancedValueUnit) -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onBlankClick: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onWeekSelected: (LocalDate) -> Unit,
    onMonthSelected: (LocalDate) -> Unit,
    onYearSelected: (LocalDate) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val yearBuckets = remember(points, yearPageEnd, netInflowCny) {
        buildAdvancedYearBuckets(points, yearPageEnd, netInflowCny)
    }
    val navLabel = when (mode) {
        ProfitCalendarDetailMode.DAY, ProfitCalendarDetailMode.WEEK -> visibleMonth.atDay(1).format(advancedMonthTitleFormatter)
        ProfitCalendarDetailMode.MONTH -> LocalDate.of(anchorDate.year, 1, 1).format(advancedYearFormatter)
        ProfitCalendarDetailMode.YEAR -> {
            val first = yearBuckets.firstOrNull()?.title.orEmpty()
            val last = yearBuckets.lastOrNull()?.title.orEmpty()
            if (first.isBlank() || last.isBlank()) anchorDate.year.toString() else "$first - $last"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceSecondary)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onBlankClick,
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .background(AdvancedSegmentBackground)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SegmentStrip(
                options = ProfitCalendarDetailMode.entries,
                selected = mode,
                label = { it.label },
                onSelected = onModeSelected,
                modifier = Modifier.weight(1f),
            )
            SegmentStrip(
                options = AdvancedValueUnit.entries,
                selected = unit,
                label = { it.label },
                onSelected = onUnitSelected,
                modifier = Modifier.weight(0.7f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "上一页",
                tint = ForegroundMuted,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = onPreviousPage),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = navLabel,
                color = ForegroundPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "下一页",
                tint = ForegroundMuted,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = onNextPage),
            )
        }

        when (mode) {
            ProfitCalendarDetailMode.DAY -> ProfitCalendarDetailMonthDayGrid(
                points = points,
                unit = unit,
                visibleMonth = visibleMonth,
                selectedWindow = selectedWindow,
                displayCurrency = displayCurrency,
                exchangeRates = exchangeRates,
                netInflowCny = netInflowCny,
                onDateSelected = onDateSelected,
            )

            ProfitCalendarDetailMode.WEEK -> ProfitCalendarDetailWeekGrid(
                points = points,
                visibleMonth = visibleMonth,
                selectedWindow = selectedWindow,
                unit = unit,
                displayCurrency = displayCurrency,
                exchangeRates = exchangeRates,
                netInflowCny = netInflowCny,
                onWeekSelected = onWeekSelected,
            )

            ProfitCalendarDetailMode.MONTH -> ProfitCalendarDetailMonthGrid(
                points = points,
                year = anchorDate.year,
                selectedWindow = selectedWindow,
                unit = unit,
                displayCurrency = displayCurrency,
                exchangeRates = exchangeRates,
                netInflowCny = netInflowCny,
                onMonthSelected = onMonthSelected,
            )

            ProfitCalendarDetailMode.YEAR -> ProfitCalendarDetailYearGrid(
                yearBuckets = yearBuckets,
                selectedWindow = selectedWindow,
                unit = unit,
                displayCurrency = displayCurrency,
                exchangeRates = exchangeRates,
                onYearSelected = onYearSelected,
            )
        }
    }
}

@Composable
private fun ProfitCalendarDetailMonthDayGrid(
    points: List<ProfitAnalysisPointUiModel>,
    unit: AdvancedValueUnit,
    visibleMonth: YearMonth,
    selectedWindow: ProfitCalendarDetailWindow,
    displayCurrency: DisplayCurrency,
    exchangeRates: ExchangeRates,
    netInflowCny: Double,
    onDateSelected: (LocalDate) -> Unit,
) {
    val pointMap = remember(points) { points.associateBy { it.date } }
    val visibleDates = remember(visibleMonth) {
        buildProfitCalendarDetailMonthDates(visibleMonth)
    }
    val weekdayLabels = listOf("日", "一", "二", "三", "四", "五", "六")

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
        visibleDates.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { date ->
                    val isCurrentMonth = YearMonth.from(date) == visibleMonth
                    val point = pointMap[date]
                    ProfitCalendarDetailDayCell(
                        date = date,
                        point = point,
                        selected = date == selectedWindow.start && selectedWindow.scope == ProfitCalendarDetailScope.DAY,
                        isCurrentMonth = isCurrentMonth,
                        unit = unit,
                        displayCurrency = displayCurrency,
                        exchangeRates = exchangeRates,
                        netInflowCny = netInflowCny,
                        onClick = { onDateSelected(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfitCalendarDetailDayCell(
    date: LocalDate,
    point: ProfitAnalysisPointUiModel?,
    selected: Boolean,
    isCurrentMonth: Boolean,
    unit: AdvancedValueUnit,
    displayCurrency: DisplayCurrency,
    exchangeRates: ExchangeRates,
    netInflowCny: Double,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val amount = point?.dailyProfitCny ?: 0.0
    val percent = advancedAmountToPercent(amount, netInflowCny)
    val hasPoint = point != null
    val valueText = if (isCurrentMonth) {
        profitCalendarDetailFormatDayValue(
            value = if (unit == AdvancedValueUnit.AMOUNT) amount else percent,
            unit = unit,
            displayCurrency = displayCurrency,
            exchangeRates = exchangeRates,
        )
    } else {
        ""
    }
    val background = when {
        !isCurrentMonth -> Color.Transparent
        selected -> MarketUp
        hasPoint && amount > 0 -> MarketUpSoft
        hasPoint && amount < 0 -> MarketDownSoft
        else -> BackgroundPrimary
    }
    val foreground = when {
        !isCurrentMonth -> ForegroundMuted
        selected -> BackgroundPrimary
        amount > 0 -> MarketUp
        amount < 0 -> MarketDown
        else -> ForegroundMuted
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .border(
                width = if (isCurrentMonth && !selected && !hasPoint) 1.dp else 0.dp,
                color = BorderSubtle,
                shape = RoundedCornerShape(4.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 1.dp),
        ) {
            Text(
                text = "%02d".format(date.dayOfMonth),
                color = when {
                    selected -> BackgroundPrimary
                    isCurrentMonth -> ForegroundPrimary
                    else -> ForegroundMuted
                },
                fontSize = 12.sp,
                lineHeight = 1.em,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = valueText,
                color = foreground,
                fontSize = when {
                    valueText.length >= 9 -> 7.sp
                    valueText.length >= 8 -> 8.sp
                    else -> 9.sp
                },
                lineHeight = 1.em,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ProfitCalendarDetailWeekGrid(
    points: List<ProfitAnalysisPointUiModel>,
    visibleMonth: YearMonth,
    selectedWindow: ProfitCalendarDetailWindow,
    unit: AdvancedValueUnit,
    displayCurrency: DisplayCurrency,
    exchangeRates: ExchangeRates,
    netInflowCny: Double,
    onWeekSelected: (LocalDate) -> Unit,
) {
    val buckets = remember(points, visibleMonth, netInflowCny) {
        buildAdvancedWeekBuckets(points, visibleMonth, netInflowCny)
    }
    AdvancedBucketGrid(
        buckets = buckets,
        unit = unit,
        displayCurrency = displayCurrency,
        exchangeRates = exchangeRates,
        columns = 2,
        onBucketClick = { onWeekSelected(it.anchorDate) },
        isBucketSelected = { bucket ->
            selectedWindow.scope == ProfitCalendarDetailScope.WEEK && bucket.anchorDate == selectedWindow.start
        },
    )
}

@Composable
private fun ProfitCalendarDetailMonthGrid(
    points: List<ProfitAnalysisPointUiModel>,
    year: Int,
    selectedWindow: ProfitCalendarDetailWindow,
    unit: AdvancedValueUnit,
    displayCurrency: DisplayCurrency,
    exchangeRates: ExchangeRates,
    netInflowCny: Double,
    onMonthSelected: (LocalDate) -> Unit,
) {
    val buckets = remember(points, year, netInflowCny) {
        buildAdvancedMonthBuckets(points, year, netInflowCny)
    }
    AdvancedBucketGrid(
        buckets = buckets,
        unit = unit,
        displayCurrency = displayCurrency,
        exchangeRates = exchangeRates,
        columns = 3,
        onBucketClick = { onMonthSelected(it.anchorDate) },
        isBucketSelected = { bucket ->
            selectedWindow.scope == ProfitCalendarDetailScope.MONTH && YearMonth.from(bucket.anchorDate) == YearMonth.from(selectedWindow.start)
        },
    )
}

@Composable
private fun ProfitCalendarDetailYearGrid(
    yearBuckets: List<AdvancedCalendarBucket>,
    selectedWindow: ProfitCalendarDetailWindow,
    unit: AdvancedValueUnit,
    displayCurrency: DisplayCurrency,
    exchangeRates: ExchangeRates,
    onYearSelected: (LocalDate) -> Unit,
) {
    AdvancedBucketGrid(
        buckets = yearBuckets,
        unit = unit,
        displayCurrency = displayCurrency,
        exchangeRates = exchangeRates,
        columns = 3,
        onBucketClick = { onYearSelected(it.anchorDate) },
        isBucketSelected = { bucket ->
            selectedWindow.scope == ProfitCalendarDetailScope.YEAR && bucket.anchorDate.year == selectedWindow.start.year
        },
    )
}

@Composable
private fun ProfitCalendarDetailSummaryCard(
    stats: AdvancedRangeStats,
    displayCurrency: DisplayCurrency,
    exchangeRates: ExchangeRates,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceSecondary)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("累计收益", color = ForegroundSecondary, fontSize = 13.sp)
            Text(
                text = advancedFormatSignedPlainNumber(stats.totalProfitCny, displayCurrency, exchangeRates),
                color = advancedTrendColor(stats.totalProfitCny),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("收益率", color = ForegroundSecondary, fontSize = 13.sp)
            Text(
                text = advancedFormatSignedPercent(stats.returnPercent),
                color = advancedTrendColor(stats.returnPercent),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ProfitCalendarDetailSecurityTable(
    title: String,
    rows: List<ProfitCalendarDetailSecurityRow>,
    displayCurrency: DisplayCurrency,
    exchangeRates: ExchangeRates,
    sortAscending: Boolean,
    onSortClick: () -> Unit,
) {
    val amountScrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceSecondary)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "$title 盈亏明细",
            color = ForegroundPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("名称 / 代码", color = ForegroundMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .weight(1.65f)
                    .horizontalScroll(amountScrollState),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "总盈亏 ${if (sortAscending) "↑" else "↓"}",
                    color = ForegroundMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .width(88.dp)
                        .clickable(onClick = onSortClick),
                )
                Text("正股盈亏", color = ForegroundMuted, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.width(88.dp))
                Text("衍生物盈亏", color = ForegroundMuted, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.width(96.dp))
                Text("收益率", color = ForegroundMuted, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.width(72.dp))
            }
        }

        if (rows.isEmpty()) {
            Text(
                text = "当前范围内暂无标的盈亏明细",
                color = ForegroundMuted,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        } else {
            rows.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = row.name.ifBlank { row.symbol },
                            color = ForegroundPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${row.marketLabel} ${row.symbol}",
                            color = ForegroundMuted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .weight(1.65f)
                            .horizontalScroll(amountScrollState),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProfitCalendarDetailAmountText(row.amountCny, displayCurrency, exchangeRates, bold = true, modifier = Modifier.width(88.dp))
                        ProfitCalendarDetailAmountText(row.stockProfitCny, displayCurrency, exchangeRates, modifier = Modifier.width(88.dp))
                        ProfitCalendarDetailAmountText(row.derivativeProfitCny, displayCurrency, exchangeRates, modifier = Modifier.width(96.dp))
                        Text(
                            text = advancedFormatSignedPercent(row.returnPercent),
                            color = advancedTrendColor(row.returnPercent),
                            fontSize = 14.sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(72.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfitCalendarDetailAmountText(
    valueCny: Double,
    displayCurrency: DisplayCurrency,
    exchangeRates: ExchangeRates,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
) {
    Text(
        text = advancedFormatSignedAmount(valueCny, displayCurrency, exchangeRates),
        color = advancedTrendColor(valueCny),
        fontSize = 14.sp,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        textAlign = TextAlign.End,
        maxLines = 1,
        softWrap = false,
        modifier = modifier,
    )
}

internal fun parseProfitCalendarDetailMode(value: String): ProfitCalendarDetailMode {
    return ProfitCalendarDetailMode.entries.firstOrNull { it.routeValue == value } ?: ProfitCalendarDetailMode.DAY
}

private fun ProfitCalendarDetailMode.defaultScope(): ProfitCalendarDetailScope {
    return when (this) {
        ProfitCalendarDetailMode.DAY -> ProfitCalendarDetailScope.DAY
        ProfitCalendarDetailMode.WEEK -> ProfitCalendarDetailScope.WEEK
        ProfitCalendarDetailMode.MONTH -> ProfitCalendarDetailScope.MONTH
        ProfitCalendarDetailMode.YEAR -> ProfitCalendarDetailScope.YEAR
    }
}

internal fun buildProfitCalendarDetailMonthDates(visibleMonth: YearMonth): List<LocalDate> {
    val firstVisibleDate = visibleMonth.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    return (0 until 42).map { offset -> firstVisibleDate.plusDays(offset.toLong()) }
}

private fun profitCalendarDetailFormatDayValue(
    value: Double,
    unit: AdvancedValueUnit,
    displayCurrency: DisplayCurrency,
    exchangeRates: ExchangeRates,
): String {
    if (value == 0.0) {
        return if (unit == AdvancedValueUnit.AMOUNT) {
            advancedNumberFormatter.format(0.0)
        } else {
            "0%"
        }
    }
    val sign = if (value >= 0) "+" else "-"
    return when (unit) {
        AdvancedValueUnit.AMOUNT -> "$sign${advancedNumberFormatter.format(advancedConvertFromCny(value.absoluteValue, displayCurrency, exchangeRates))}"
        AdvancedValueUnit.PERCENT -> "$sign${advancedNumberFormatter.format(value.absoluteValue)}%"
    }
}

internal fun resolveProfitCalendarDetailWindow(
    mode: ProfitCalendarDetailMode,
    scope: ProfitCalendarDetailScope,
    anchorDate: LocalDate,
): ProfitCalendarDetailWindow {
    return when (scope) {
        ProfitCalendarDetailScope.DAY -> ProfitCalendarDetailWindow(
            start = anchorDate,
            end = anchorDate,
            scope = ProfitCalendarDetailScope.DAY,
        )

        ProfitCalendarDetailScope.WEEK -> {
            val start = anchorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
            ProfitCalendarDetailWindow(
                start = start,
                end = start.plusDays(6),
                scope = ProfitCalendarDetailScope.WEEK,
            )
        }

        ProfitCalendarDetailScope.MONTH -> {
            val yearMonth = YearMonth.from(anchorDate)
            ProfitCalendarDetailWindow(
                start = yearMonth.atDay(1),
                end = yearMonth.atEndOfMonth(),
                scope = ProfitCalendarDetailScope.MONTH,
            )
        }

        ProfitCalendarDetailScope.YEAR -> ProfitCalendarDetailWindow(
            start = LocalDate.of(anchorDate.year, 1, 1),
            end = LocalDate.of(anchorDate.year, 12, 31),
            scope = ProfitCalendarDetailScope.YEAR,
        )
    }
}

internal fun buildProfitCalendarDetailSecurityRows(
    securityAnalyses: List<SecurityProfitAnalysisUiModel>,
    rangeStart: LocalDate,
    rangeEnd: LocalDate,
    netInflowCny: Double,
): List<ProfitCalendarDetailSecurityRow> {
    return securityAnalyses.mapNotNull { analysis ->
        buildAdvancedSecurityRangeStats(analysis, rangeStart, rangeEnd)?.let { stats ->
            ProfitCalendarDetailSecurityRow(
                key = stats.key,
                symbol = stats.symbol,
                name = stats.name,
                marketLabel = stats.marketLabel,
                amountCny = stats.totalProfitCny,
                stockProfitCny = stats.stockProfitCny,
                derivativeProfitCny = stats.derivativeProfitCny,
                returnPercent = advancedAmountToPercent(stats.totalProfitCny, netInflowCny),
            )
        }
    }
}

private fun buildProfitCalendarDetailTitle(
    window: ProfitCalendarDetailWindow,
    anchorDate: LocalDate,
): String {
    return when (window.scope) {
        ProfitCalendarDetailScope.DAY -> "${anchorDate.year} 年 %02d 月 %02d 日".format(anchorDate.monthValue, anchorDate.dayOfMonth)
        ProfitCalendarDetailScope.WEEK -> {
            val end = window.end
            "${window.start.monthValue}/${window.start.dayOfMonth} - ${end.monthValue}/${end.dayOfMonth}"
        }
        ProfitCalendarDetailScope.MONTH -> "${window.start.year} 年 %02d 月".format(window.start.monthValue)
        ProfitCalendarDetailScope.YEAR -> "${window.start.year} 年"
    }
}

private fun filterAdvancedPointsForRange(
    points: List<ProfitAnalysisPointUiModel>,
    rangeStart: LocalDate,
    rangeEnd: LocalDate,
): List<ProfitAnalysisPointUiModel> {
    val priorPoint = points.lastOrNull { it.date.isBefore(rangeStart) }
    val priorCumulative = priorPoint?.cumulativeProfitCny ?: 0.0
    var cumulativeReturn = 0.0
    return points
        .filter { !it.date.isBefore(rangeStart) && !it.date.isAfter(rangeEnd) }
        .map { point ->
            cumulativeReturn = ((1 + cumulativeReturn / 100.0) * (1 + point.dailyReturnPercent / 100.0) - 1) * 100.0
            point.copy(
                cumulativeProfitCny = point.cumulativeProfitCny - priorCumulative,
                cumulativeReturnPercent = cumulativeReturn,
            )
        }
        .ifEmpty {
            listOf(
                ProfitAnalysisPointUiModel(
                    date = rangeEnd,
                    dailyProfitCny = 0.0,
                    cumulativeProfitCny = 0.0,
                    totalAssetsCny = priorPoint?.totalAssetsCny ?: 0.0,
                    netInflowCny = priorPoint?.netInflowCny ?: 0.0,
                ),
            )
        }
}

private fun buildAdvancedRangeStats(
    points: List<ProfitAnalysisPointUiModel>,
    rangeStart: LocalDate,
    rangeEnd: LocalDate,
): AdvancedRangeStats {
    val safePoints = points.ifEmpty {
        listOf(
            ProfitAnalysisPointUiModel(
                date = rangeEnd,
                dailyProfitCny = 0.0,
                cumulativeProfitCny = 0.0,
            ),
        )
    }
    val totalProfitCny = safePoints.last().cumulativeProfitCny
    val returnPercent = safePoints.last().cumulativeReturnPercent
    val positiveDays = safePoints.count { it.dailyProfitCny > 0 }
    val activeDays = safePoints.count { it.dailyProfitCny != 0.0 }
    val denominator = max(activeDays, 1)
    val bestDayProfit = safePoints.maxOfOrNull { it.dailyProfitCny } ?: 0.0
    var peakNav = 1.0
    var maxDrawdown = 0.0
    safePoints.forEach { point ->
        val nav = 1 + point.cumulativeReturnPercent / 100.0
        peakNav = max(peakNav, nav)
        if (peakNav > 0.0) {
            maxDrawdown = max(maxDrawdown, ((peakNav - nav) / peakNav) * 100.0)
        }
    }

    return AdvancedRangeStats(
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

private fun buildAdvancedSecurityRangeStats(
    analysis: SecurityProfitAnalysisUiModel,
    rangeStart: LocalDate,
    rangeEnd: LocalDate,
): AdvancedSecurityRangeStats? {
    val points = analysis.dailyPoints
        .sortedBy { it.date }
        .filter { !it.date.isBefore(rangeStart) && !it.date.isAfter(rangeEnd) }
    if (points.isEmpty()) return null
    val priorCumulative = analysis.dailyPoints
        .lastOrNull { it.date.isBefore(rangeStart) }
        ?.cumulativeProfitCny ?: 0.0
    val priorStockCumulative = analysis.dailyPoints
        .lastOrNull { it.date.isBefore(rangeStart) }
        ?.cumulativeStockProfitCny ?: 0.0
    val priorDerivativeCumulative = analysis.dailyPoints
        .lastOrNull { it.date.isBefore(rangeStart) }
        ?.cumulativeDerivativeProfitCny ?: 0.0
    val rebased = points.map { point ->
        SecurityProfitPointUiModel(
            date = point.date,
            dailyProfitCny = point.dailyProfitCny,
            cumulativeProfitCny = point.cumulativeProfitCny - priorCumulative,
            dailyStockProfitCny = point.dailyStockProfitCny,
            dailyDerivativeProfitCny = point.dailyDerivativeProfitCny,
            cumulativeStockProfitCny = point.cumulativeStockProfitCny - priorStockCumulative,
            cumulativeDerivativeProfitCny = point.cumulativeDerivativeProfitCny - priorDerivativeCumulative,
        )
    }
    val totalProfitCny = rebased.last().cumulativeProfitCny
    val stockProfitCny = rebased.last().cumulativeStockProfitCny
    val derivativeProfitCny = rebased.last().cumulativeDerivativeProfitCny
    val positiveDays = rebased.count { it.dailyProfitCny > 0 }
    val activeDays = rebased.count { it.dailyProfitCny != 0.0 }
    return AdvancedSecurityRangeStats(
        key = "${analysis.market.name}:${analysis.symbol}",
        symbol = analysis.symbol,
        name = analysis.name,
        marketLabel = analysis.market.label,
        totalProfitCny = totalProfitCny,
        stockProfitCny = stockProfitCny,
        derivativeProfitCny = derivativeProfitCny,
        averageDailyProfitCny = totalProfitCny / rebased.size.coerceAtLeast(1),
        bestDayProfitCny = rebased.maxOfOrNull { it.dailyProfitCny } ?: 0.0,
        winRate = positiveDays.toDouble() / max(activeDays, 1).toDouble(),
    )
}

private fun resolveAdvancedRangeWindow(
    latestDate: LocalDate,
    firstDate: LocalDate,
    range: AdvancedProfitRange,
    customStart: String,
    customEnd: String,
): Pair<LocalDate, LocalDate> {
    val rawStart = runCatching { LocalDate.parse(customStart) }.getOrNull() ?: firstDate
    val rawEnd = runCatching { LocalDate.parse(customEnd) }.getOrNull() ?: latestDate
    return when (range) {
        AdvancedProfitRange.ALL -> firstDate to latestDate
        AdvancedProfitRange.THIS_MONTH -> latestDate.withDayOfMonth(1) to latestDate
        AdvancedProfitRange.ONE_MONTH -> latestDate.minusMonths(1).plusDays(1) to latestDate
        AdvancedProfitRange.SIX_MONTHS -> latestDate.minusMonths(6).plusDays(1) to latestDate
        AdvancedProfitRange.THIS_YEAR -> latestDate.withDayOfYear(1) to latestDate
        AdvancedProfitRange.CUSTOM -> {
            val start = maxOf(firstDate, minOf(rawStart, rawEnd))
            val end = minOf(latestDate, maxOf(rawStart, rawEnd))
            start to end
        }
    }
}

private fun buildAdvancedAxisValues(
    values: List<Double>,
    metric: AdvancedChartMetric,
): List<Double> {
    if (metric == AdvancedChartMetric.TRADE_COUNT) {
        val top = max(3.0, values.maxOrNull()?.coerceAtLeast(0.0) ?: 0.0)
        return listOf(top, top * 2.0 / 3.0, top / 3.0, 0.0)
    }
    val minValue = values.minOrNull() ?: 0.0
    val maxValue = values.maxOrNull() ?: 0.0
    if ((maxValue - minValue).absoluteValue < 1e-6) {
        val padding = max(1.0, maxValue.absoluteValue * 0.1)
        return listOf(
            maxValue + padding,
            maxValue + padding / 3,
            maxValue - padding / 3,
            maxValue - padding,
        )
    }
    val padding = (maxValue - minValue) * 0.1
    val top = maxValue + padding
    val bottom = minValue - padding
    return (0..3).map { index ->
        top - ((top - bottom) / 3.0) * index
    }
}

private fun buildAdvancedWeekBuckets(
    points: List<ProfitAnalysisPointUiModel>,
    visibleMonth: YearMonth,
    netInflowCny: Double,
): List<AdvancedCalendarBucket> {
    val start = visibleMonth.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    val end = visibleMonth.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
    val pointMap = points.associateBy { it.date }
    val buckets = mutableListOf<AdvancedCalendarBucket>()
    var cursor = start
    while (!cursor.isAfter(end)) {
        val weekEnd = cursor.plusDays(6)
        val amount = generateSequence(cursor) { current ->
            current.plusDays(1).takeIf { !it.isAfter(weekEnd) }
        }.sumOf { date ->
            pointMap[date]?.dailyProfitCny ?: 0.0
        }
        buckets += AdvancedCalendarBucket(
            title = "${cursor.monthValue}/${cursor.dayOfMonth} - ${weekEnd.monthValue}/${weekEnd.dayOfMonth}",
            anchorDate = cursor,
            valueAmountCny = amount,
            valuePercent = advancedAmountToPercent(amount, netInflowCny),
        )
        cursor = cursor.plusWeeks(1)
    }
    return buckets
}

private fun buildAdvancedMonthBuckets(
    points: List<ProfitAnalysisPointUiModel>,
    year: Int,
    netInflowCny: Double,
): List<AdvancedCalendarBucket> {
    val pointMap = points.groupBy { YearMonth.from(it.date) }
    return (1..12).map { month ->
        val yearMonth = YearMonth.of(year, month)
        val amount = pointMap[yearMonth].orEmpty().sumOf { it.dailyProfitCny }
        AdvancedCalendarBucket(
            title = "${month}月",
            anchorDate = yearMonth.atDay(1),
            valueAmountCny = amount,
            valuePercent = advancedAmountToPercent(amount, netInflowCny),
        )
    }
}

private fun buildAdvancedYearBuckets(
    points: List<ProfitAnalysisPointUiModel>,
    endYear: Int,
    netInflowCny: Double,
): List<AdvancedCalendarBucket> {
    val pointMap = points.groupBy { it.date.year }
    val startYear = endYear - 5
    return (startYear..endYear).map { year ->
        val amount = pointMap[year].orEmpty().sumOf { it.dailyProfitCny }
        AdvancedCalendarBucket(
            title = year.toString(),
            anchorDate = LocalDate.of(year, 1, 1),
            valueAmountCny = amount,
            valuePercent = advancedAmountToPercent(amount, netInflowCny),
        )
    }
}

private fun advancedTrendColor(value: Double): Color = when {
    value > 0 -> MarketUp
    value < 0 -> MarketDown
    else -> ForegroundPrimary
}

private fun advancedAmountToPercent(valueCny: Double, netInflowCny: Double): Double {
    if (netInflowCny <= 0.0) return 0.0
    return (valueCny / netInflowCny) * 100.0
}

private fun advancedConvertFromCny(
    value: Double,
    currency: DisplayCurrency,
    exchangeRates: ExchangeRates,
): Double = value / exchangeRates.rateToCny(currency)

private fun advancedFormatSignedAmount(
    value: Double,
    displayCurrency: DisplayCurrency,
    exchangeRates: ExchangeRates,
): String {
    val sign = if (value >= 0) "+" else "-"
    return "$sign${displayCurrency.symbol}${advancedNumberFormatter.format(advancedConvertFromCny(value.absoluteValue, displayCurrency, exchangeRates))}"
}

private fun advancedFormatUnsignedAmount(
    value: Double,
    displayCurrency: DisplayCurrency,
    exchangeRates: ExchangeRates,
): String {
    return "${displayCurrency.symbol}${advancedNumberFormatter.format(advancedConvertFromCny(value, displayCurrency, exchangeRates))}"
}

private fun advancedFormatSignedPlainNumber(
    value: Double,
    displayCurrency: DisplayCurrency,
    exchangeRates: ExchangeRates,
): String {
    val sign = if (value >= 0) "+" else "-"
    return "$sign${advancedNumberFormatter.format(advancedConvertFromCny(value.absoluteValue, displayCurrency, exchangeRates))}"
}

private fun advancedFormatSignedPercent(value: Double): String {
    val sign = if (value >= 0) "+" else "-"
    return "$sign${advancedNumberFormatter.format(value.absoluteValue)}%"
}

private fun advancedFormatWinRate(value: Double): String {
    return "${advancedWholeNumberFormatter.format(value * 100)}%"
}

private fun advancedFormatDrawdown(value: Double): String {
    return "-${advancedNumberFormatter.format(value.absoluteValue)}%"
}

private fun formatAdvancedAxisValue(
    value: Double,
    metric: AdvancedChartMetric,
    currency: DisplayCurrency,
): String = when (metric) {
    AdvancedChartMetric.RETURN -> "${advancedNumberFormatter.format(value)}%"
    AdvancedChartMetric.ASSET -> {
        val rounded = value.roundToInt()
        "${currency.symbol}${rounded}"
    }
    AdvancedChartMetric.TRADE_COUNT -> "${value.roundToInt()}笔"
}

private fun advancedFormatCompactValue(
    value: Double,
    unit: AdvancedValueUnit,
    displayCurrency: DisplayCurrency,
    exchangeRates: ExchangeRates,
): String {
    if (value == 0.0) {
        return if (unit == AdvancedValueUnit.AMOUNT) "--" else "0%"
    }
    val sign = if (value >= 0) "+" else "-"
    return when (unit) {
        AdvancedValueUnit.AMOUNT -> "$sign${advancedNumberFormatter.format(advancedConvertFromCny(value.absoluteValue, displayCurrency, exchangeRates))}"
        AdvancedValueUnit.PERCENT -> "$sign${advancedNumberFormatter.format(value.absoluteValue)}%"
    }
}

@Composable
private fun AdvancedCustomDateRangeEditor(
    customStart: String,
    customEnd: String,
    minDate: LocalDate,
    maxDate: LocalDate,
    onApply: (String, String) -> Unit,
    fallbackDate: LocalDate,
    modifier: Modifier = Modifier,
) {
    var draftStart by rememberSaveable { mutableStateOf(customStart) }
    var draftEnd by rememberSaveable { mutableStateOf(customEnd) }

    LaunchedEffect(customStart) {
        draftStart = customStart
    }
    LaunchedEffect(customEnd) {
        draftEnd = customEnd
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WheelDateRangePicker(
            startDate = draftStart,
            endDate = draftEnd,
            onStartDateChange = { draftStart = it },
            onEndDateChange = { draftEnd = it },
            fallbackDate = fallbackDate,
            minDate = minDate,
            maxDate = maxDate,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(SurfaceInverse)
                    .clickable {
                        val (start, end) = normalizeAdvancedCustomDateRange(
                            startDate = draftStart,
                            endDate = draftEnd,
                            minDate = minDate,
                            maxDate = maxDate,
                        )
                        draftStart = start
                        draftEnd = end
                        onApply(start, end)
                    }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "确定",
                    color = BackgroundPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

internal fun normalizeAdvancedCustomDateRange(
    startDate: String,
    endDate: String,
    minDate: LocalDate,
    maxDate: LocalDate,
): Pair<String, String> {
    val startBound = if (minDate <= maxDate) minDate else maxDate
    val endBound = if (minDate <= maxDate) maxDate else minDate
    val start = (parseWheelDateOrNull(startDate) ?: startBound).coerceIn(startBound, endBound)
    val end = (parseWheelDateOrNull(endDate) ?: endBound).coerceIn(startBound, endBound)
    val orderedStart = if (start <= end) start else end
    val orderedEnd = if (start <= end) end else start
    return orderedStart.toString() to orderedEnd.toString()
}

// ── Full Ranking Page ──────────────────────────────────────────────

@Composable
fun FullRankingRoute(
    analysis: ProfitAnalysisUiModel,
    displayCurrency: DisplayCurrency,
    exchangeRates: ExchangeRates,
    selectedRange: AdvancedProfitRange = AdvancedProfitRange.THIS_MONTH,
    customStart: String = "",
    customEnd: String = "",
    onSelectedRangeChange: (AdvancedProfitRange) -> Unit = {},
    onCustomStartChange: (String) -> Unit = {},
    onCustomEndChange: (String) -> Unit = {},
    onBack: () -> Unit,
    onSecurityClick: (String, String) -> Unit,
) {
    var showProfit by rememberSaveable { mutableStateOf(true) }
    var sortAscending by rememberSaveable { mutableStateOf(false) }
    var customRangeEditorVisible by rememberSaveable { mutableStateOf(false) }

    val allPoints = remember(analysis) {
        analysis.dailyPoints.sortedBy { it.date }.ifEmpty {
            listOf(ProfitAnalysisPointUiModel(date = analysis.latestDate, dailyProfitCny = 0.0, cumulativeProfitCny = 0.0))
        }
    }
    val firstDate = allPoints.firstOrNull()?.date ?: analysis.latestDate
    LaunchedEffect(firstDate, analysis.latestDate) {
        val currentStart = runCatching { LocalDate.parse(customStart) }.getOrNull()
        val currentEnd = runCatching { LocalDate.parse(customEnd) }.getOrNull()
        if (currentStart == null || currentStart.isBefore(firstDate) || currentStart.isAfter(analysis.latestDate)) {
            onCustomStartChange(firstDate.toString())
        }
        if (currentEnd == null || currentEnd.isBefore(firstDate) || currentEnd.isAfter(analysis.latestDate)) {
            onCustomEndChange(analysis.latestDate.toString())
        }
    }

    val (rangeStart, rangeEnd) = remember(selectedRange, customStart, customEnd, firstDate, analysis.latestDate) {
        resolveAdvancedRangeWindow(
            latestDate = analysis.latestDate,
            firstDate = firstDate,
            range = selectedRange,
            customStart = customStart,
            customEnd = customEnd,
        )
    }
    val securityStats = remember(analysis.securityAnalyses, rangeStart, rangeEnd) {
        analysis.securityAnalyses
            .mapNotNull { buildAdvancedSecurityRangeStats(it, rangeStart, rangeEnd) }
    }
    val filtered = remember(securityStats, showProfit) {
        if (showProfit) securityStats.filter { it.totalProfitCny > 0 }
        else securityStats.filter { it.totalProfitCny < 0 }
    }
    val sorted = remember(filtered, sortAscending) {
        if (sortAscending) filtered.sortedBy { it.totalProfitCny }
        else filtered.sortedByDescending { it.totalProfitCny }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary),
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenHeader(title = "盈亏排行", onBack = onBack)

            // Fixed: time range selector + tabs
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SegmentRow(
                    options = AdvancedProfitRange.entries,
                    selected = if (selectedRange == AdvancedProfitRange.CUSTOM && !customRangeEditorVisible) {
                        null
                    } else {
                        selectedRange
                    },
                    label = { it.label },
                    onSelected = { range ->
                        if (range == AdvancedProfitRange.CUSTOM) {
                            customRangeEditorVisible = true
                        } else {
                            customRangeEditorVisible = false
                        }
                        onSelectedRangeChange(range)
                    },
                )
                if (selectedRange == AdvancedProfitRange.CUSTOM && customRangeEditorVisible) {
                    AdvancedCustomDateRangeEditor(
                        customStart = customStart,
                        customEnd = customEnd,
                        minDate = firstDate,
                        maxDate = analysis.latestDate,
                        onApply = { start, end ->
                            onCustomStartChange(start)
                            onCustomEndChange(end)
                            customRangeEditorVisible = false
                        },
                        fallbackDate = analysis.latestDate,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Tab chips + sort toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(true to "盈利排行", false to "亏损排行").forEach { (isProfit, label) ->
                            val selected = showProfit == isProfit
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (selected) SurfaceInverse else BackgroundPrimary)
                                    .border(
                                        width = if (selected) 0.dp else 1.dp,
                                        color = BorderSubtle,
                                        shape = RoundedCornerShape(999.dp),
                                    )
                                    .clickable {
                                        showProfit = isProfit
                                        sortAscending = !isProfit
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = label,
                                    color = if (selected) BackgroundPrimary else ForegroundPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(BackgroundPrimary)
                            .border(width = 1.dp, color = BorderSubtle, shape = RoundedCornerShape(999.dp))
                            .clickable { sortAscending = !sortAscending }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = if (sortAscending) "升序" else "降序",
                            color = ForegroundMuted,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            // Scrollable ranking list
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (sorted.isEmpty()) {
                    Text(
                        text = if (showProfit) "当前区间内没有盈利的股票" else "当前区间内没有亏损的股票",
                        color = ForegroundMuted,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 32.dp),
                    )
                } else {
                    sorted.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSecurityClick(item.symbol, item.marketLabel) }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${index + 1}",
                                color = ForegroundMuted,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(28.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    color = ForegroundPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = "${item.symbol} · ${item.marketLabel}",
                                    color = ForegroundMuted,
                                    fontSize = 12.sp,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = advancedFormatSignedAmount(item.totalProfitCny, displayCurrency, exchangeRates),
                                    color = advancedTrendColor(item.totalProfitCny),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = ForegroundMuted,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }

    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun AdvancedProfitAnalysisRoutePreview() {
    StockLedgerTheme {
        AdvancedProfitAnalysisRoute(
            analysis = PreviewFixtures.profitAnalysis,
            displayCurrency = DisplayCurrency.CNY,
            exchangeRates = PreviewFixtures.exchangeRates,
            selectedPlatform = BrokerPlatform.HSBC,
            onPlatformClick = {},
            onSettingsClick = {},
            onDisplayCurrencySelected = {},
            onDestinationSelected = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun FullRankingRoutePreview() {
    StockLedgerTheme {
        FullRankingRoute(
            analysis = PreviewFixtures.profitAnalysis,
            displayCurrency = DisplayCurrency.CNY,
            exchangeRates = PreviewFixtures.exchangeRates,
            onBack = {},
            onSecurityClick = { _, _ -> },
        )
    }
}
