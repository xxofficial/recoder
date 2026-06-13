package com.recoder.stockledger.ui

import android.app.DatePickerDialog
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recoder.stockledger.data.DisplayCurrency
import com.recoder.stockledger.data.Market
import com.recoder.stockledger.data.ProfitAnalysisUiModel
import com.recoder.stockledger.data.SecurityProfitPointUiModel
import com.recoder.stockledger.data.local.TransactionEntity
import com.recoder.stockledger.data.local.QuoteSnapshotEntity
import com.recoder.stockledger.data.TradeType
import com.recoder.stockledger.ui.theme.BackgroundPrimary
import com.recoder.stockledger.ui.theme.ForegroundMuted
import com.recoder.stockledger.ui.theme.ForegroundPrimary
import com.recoder.stockledger.ui.theme.ForegroundSecondary
import com.recoder.stockledger.ui.theme.MarketDown
import com.recoder.stockledger.ui.theme.MarketUp
import com.recoder.stockledger.ui.theme.StockLedgerTheme
import com.recoder.stockledger.ui.theme.SurfaceSecondary
import java.text.DecimalFormat
import java.time.LocalDate
import kotlin.math.absoluteValue

private enum class DetailRange(val label: String) {
    ALL("全部"),
    THIS_MONTH("本月"),
    ONE_MONTH("近1月"),
    SIX_MONTHS("近6月"),
    THIS_YEAR("本年"),
    CUSTOM("自定义"),
}

private enum class DetailTab(val label: String) {
    STOCK("正股"),
    OPTION("衍生品")
}

private val detailNumberFormatter = DecimalFormat("#,##0.00")

@Composable
fun StockDetailRoute(
    symbol: String,
    marketLabel: String,
    analysis: ProfitAnalysisUiModel,
    displayCurrency: DisplayCurrency,
    quotes: List<QuoteSnapshotEntity>,
    onBack: () -> Unit,
) {
    var selectedRange by rememberSaveable { mutableStateOf(DetailRange.ALL) }
    var customStartDate by rememberSaveable { mutableStateOf("") }
    var customEndDate by rememberSaveable { mutableStateOf("") }
    var activeTab by rememberSaveable { mutableStateOf(DetailTab.STOCK) }

    val security = remember(analysis.securityAnalyses, symbol, marketLabel) {
        analysis.securityAnalyses.firstOrNull {
            it.symbol == symbol && it.market.label == marketLabel
        }
    }
    val securityName = security?.name ?: symbol
    val allSecurityPoints = remember(security) {
        security?.dailyPoints?.sortedBy { it.date }.orEmpty()
    }
    val latestDate = analysis.latestDate
    val context = LocalContext.current
    val quoteMap = remember(quotes) { quotes.associateBy { it.symbol } }

    val rangePair: Pair<LocalDate, LocalDate> = remember(selectedRange, latestDate, customStartDate, customEndDate) {
        when (selectedRange) {
            DetailRange.ALL -> Pair(allSecurityPoints.firstOrNull()?.date ?: latestDate, latestDate)
            DetailRange.THIS_MONTH -> Pair(latestDate.withDayOfMonth(1), latestDate)
            DetailRange.ONE_MONTH -> Pair(latestDate.minusMonths(1).plusDays(1), latestDate)
            DetailRange.SIX_MONTHS -> Pair(latestDate.minusMonths(6).plusDays(1), latestDate)
            DetailRange.THIS_YEAR -> Pair(latestDate.withDayOfYear(1), latestDate)
            DetailRange.CUSTOM -> {
                val start = runCatching { LocalDate.parse(customStartDate) }.getOrNull() ?: latestDate.minusMonths(1)
                val end = runCatching { LocalDate.parse(customEndDate) }.getOrNull() ?: latestDate
                Pair(start, end)
            }
        }
    }
    val rangeStart = rangePair.first
    val rangeEnd = rangePair.second

    // Filter all transactions for this security (including options)
    val allSecurityTransactions = remember(analysis.transactions, symbol, marketLabel) {
        analysis.transactions.filter { txn ->
            (txn.symbol == symbol || (txn.assetType == "OPTION" && txn.underlyingSymbol == symbol)) &&
                Market.fromString(txn.market)?.label == marketLabel
        }.sortedWith(compareBy({ it.tradeDate }, { it.tradeTime }, { it.createdAt }))
    }

    // Filter transactions in range
    val rangeTransactions = remember(allSecurityTransactions, rangeStart, rangeEnd) {
        allSecurityTransactions.filter { txn ->
            txn.tradeDate >= rangeStart.toString() && txn.tradeDate <= rangeEnd.toString()
        }
    }

    val stockTransactions = remember(allSecurityTransactions) {
        allSecurityTransactions.filter { it.assetType != "OPTION" }
    }
    val optionTransactions = remember(allSecurityTransactions) {
        allSecurityTransactions.filter { it.assetType == "OPTION" }
    }

    // Compute stock PnL
    val stockPnl = remember(stockTransactions, rangeStart, rangeEnd, allSecurityPoints) {
        val rangeTxns = stockTransactions.filter { it.tradeDate >= rangeStart.toString() && it.tradeDate <= rangeEnd.toString() }
        val commission = rangeTxns.filter {
            runCatching { TradeType.valueOf(it.tradeType) }.getOrNull()?.isSecurityTrade == true
        }.sumOf { it.commission }
        val tax = rangeTxns.filter {
            runCatching { TradeType.valueOf(it.tradeType) }.getOrNull()?.isSecurityTrade == true
        }.sumOf { it.tax }
        val sellProceeds = rangeTxns.filter {
            runCatching { TradeType.valueOf(it.tradeType) }.getOrNull() == TradeType.SELL
        }.sumOf { it.price * it.quantity }
        val buyCost = rangeTxns.filter {
            runCatching { TradeType.valueOf(it.tradeType) }.getOrNull() == TradeType.BUY
        }.sumOf { it.price * it.quantity }

        val closingQty = stockTransactions.filter { it.tradeDate <= rangeEnd.toString() }.fold(0.0) { qty, txn ->
            val tradeType = runCatching { TradeType.valueOf(txn.tradeType) }.getOrNull()
            when (tradeType) {
                TradeType.BUY -> qty + txn.quantity
                TradeType.SELL -> qty - txn.quantity
                TradeType.SPLIT -> qty * txn.price
                else -> qty
            }
        }
        val openingQty = stockTransactions.filter { it.tradeDate < rangeStart.toString() }.fold(0.0) { qty, txn ->
            val tradeType = runCatching { TradeType.valueOf(txn.tradeType) }.getOrNull()
            when (tradeType) {
                TradeType.BUY -> qty + txn.quantity
                TradeType.SELL -> qty - txn.quantity
                TradeType.SPLIT -> qty * txn.price
                else -> qty
            }
        }
        val closingPrice = if (rangeEnd == latestDate && quoteMap[symbol]?.currentPrice != null) {
            quoteMap[symbol]!!.currentPrice!!
        } else {
            allSecurityPoints.lastOrNull { !it.date.isAfter(rangeEnd) }?.closePrice
                ?: rangeTxns.lastOrNull { runCatching { TradeType.valueOf(it.tradeType) }.getOrNull()?.isSecurityTrade == true }?.price
                ?: 0.0
        }
        val openingPrice = allSecurityPoints.lastOrNull { it.date.isBefore(rangeStart) }?.closePrice
            ?: stockTransactions.lastOrNull { it.tradeDate < rangeStart.toString() && runCatching { TradeType.valueOf(it.tradeType) }.getOrNull()?.isSecurityTrade == true }?.price
            ?: closingPrice

        val closingMarketValue = closingPrice * closingQty
        val openingMarketValue = openingPrice * openingQty
        closingMarketValue - openingMarketValue + sellProceeds - buyCost - commission - tax
    }

    // Compute options PnL
    val optionPnl = remember(optionTransactions, rangeStart, rangeEnd) {
        var pnl = 0.0
        val grouped = optionTransactions.groupBy { it.symbol }
        for ((optSymbol, optTxns) in grouped) {
            val optRangeTxns = optTxns.filter { it.tradeDate >= rangeStart.toString() && it.tradeDate <= rangeEnd.toString() }
            val optCommission = optRangeTxns.filter {
                runCatching { TradeType.valueOf(it.tradeType) }.getOrNull()?.isSecurityTrade == true
            }.sumOf { it.commission }
            val optTax = optRangeTxns.filter {
                runCatching { TradeType.valueOf(it.tradeType) }.getOrNull()?.isSecurityTrade == true
            }.sumOf { it.tax }
            val optSellProceeds = optRangeTxns.filter {
                runCatching { TradeType.valueOf(it.tradeType) }.getOrNull() == TradeType.SELL
            }.sumOf { it.price * it.quantity * 100.0 }
            val optBuyCost = optRangeTxns.filter {
                runCatching { TradeType.valueOf(it.tradeType) }.getOrNull() == TradeType.BUY
            }.sumOf { it.price * it.quantity * 100.0 }

            val firstTxn = optTxns.firstOrNull()
            val expiryStr = firstTxn?.expiryDate?.takeIf { it.isNotBlank() } ?: ""
            val expiryDate = runCatching { LocalDate.parse(expiryStr) }.getOrNull()

            val optClosingQty = optTxns.filter { it.tradeDate <= rangeEnd.toString() }.fold(0.0) { qty, txn ->
                val tradeType = runCatching { TradeType.valueOf(txn.tradeType) }.getOrNull()
                when (tradeType) {
                    TradeType.BUY -> qty + txn.quantity
                    TradeType.SELL -> qty - txn.quantity
                    else -> qty
                }
            }
            val optOpeningQty = optTxns.filter { it.tradeDate < rangeStart.toString() }.fold(0.0) { qty, txn ->
                val tradeType = runCatching { TradeType.valueOf(txn.tradeType) }.getOrNull()
                when (tradeType) {
                    TradeType.BUY -> qty + txn.quantity
                    TradeType.SELL -> qty - txn.quantity
                    else -> qty
                }
            }

            val isExpiredAtEnd = expiryDate != null && rangeEnd.isAfter(expiryDate)
            val isExpiredAtStart = expiryDate != null && rangeStart.minusDays(1).isAfter(expiryDate)

            val optClosingPrice = if (isExpiredAtEnd) 0.0 else {
                if (rangeEnd == latestDate && quoteMap[optSymbol]?.currentPrice != null) {
                    quoteMap[optSymbol]!!.currentPrice!!
                } else {
                    optTxns.lastOrNull { it.tradeDate <= rangeEnd.toString() && runCatching { TradeType.valueOf(it.tradeType) }.getOrNull()?.isSecurityTrade == true }?.price ?: 0.0
                }
            }
            val optOpeningPrice = if (isExpiredAtStart) 0.0 else {
                optTxns.lastOrNull { it.tradeDate < rangeStart.toString() && runCatching { TradeType.valueOf(it.tradeType) }.getOrNull()?.isSecurityTrade == true }?.price ?: optClosingPrice
            }

            val optClosingValue = optClosingPrice * optClosingQty * 100.0
            val optOpeningValue = optOpeningPrice * optOpeningQty * 100.0

            pnl += (optClosingValue - optOpeningValue + optSellProceeds - optBuyCost - optCommission - optTax)
        }
        pnl
    }

    val rangeCommission = rangeTransactions.filter {
        runCatching { TradeType.valueOf(it.tradeType) }.getOrNull()?.isSecurityTrade == true
    }.sumOf { it.commission }
    val rangeTax = rangeTransactions.filter {
        runCatching { TradeType.valueOf(it.tradeType) }.getOrNull()?.isSecurityTrade == true
    }.sumOf { it.tax }
    val totalSellProceeds = rangeTransactions.filter {
        runCatching { TradeType.valueOf(it.tradeType) }.getOrNull() == TradeType.SELL
    }.sumOf { it.price * it.quantity * (if (it.assetType == "OPTION") 100.0 else 1.0) }
    val totalBuyCost = rangeTransactions.filter {
        runCatching { TradeType.valueOf(it.tradeType) }.getOrNull() == TradeType.BUY
    }.sumOf { it.price * it.quantity * (if (it.assetType == "OPTION") 100.0 else 1.0) }

    val closingStockQty = stockTransactions.filter { it.tradeDate <= rangeEnd.toString() }.fold(0.0) { qty, txn ->
        val tradeType = runCatching { TradeType.valueOf(txn.tradeType) }.getOrNull()
        when (tradeType) {
            TradeType.BUY -> qty + txn.quantity
            TradeType.SELL -> qty - txn.quantity
            TradeType.SPLIT -> qty * txn.price
            else -> qty
        }
    }
    val openingStockQty = stockTransactions.filter { it.tradeDate < rangeStart.toString() }.fold(0.0) { qty, txn ->
        val tradeType = runCatching { TradeType.valueOf(txn.tradeType) }.getOrNull()
        when (tradeType) {
            TradeType.BUY -> qty + txn.quantity
            TradeType.SELL -> qty - txn.quantity
            TradeType.SPLIT -> qty * txn.price
            else -> qty
        }
    }
    val closingStockPrice = if (rangeEnd == latestDate && quoteMap[symbol]?.currentPrice != null) {
        quoteMap[symbol]!!.currentPrice!!
    } else {
        allSecurityPoints.lastOrNull { !it.date.isAfter(rangeEnd) }?.closePrice
            ?: stockTransactions.lastOrNull { it.tradeDate <= rangeEnd.toString() && runCatching { TradeType.valueOf(it.tradeType) }.getOrNull()?.isSecurityTrade == true }?.price
            ?: 0.0
    }
    val openingStockPrice = allSecurityPoints.lastOrNull { it.date.isBefore(rangeStart) }?.closePrice
        ?: stockTransactions.lastOrNull { it.tradeDate < rangeStart.toString() && runCatching { TradeType.valueOf(it.tradeType) }.getOrNull()?.isSecurityTrade == true }?.price
        ?: closingStockPrice
    val closingStockMarketValue = closingStockPrice * closingStockQty
    val openingStockMarketValue = openingStockPrice * openingStockQty

    var closingOptionMarketValue = 0.0
    var openingOptionMarketValue = 0.0
    optionTransactions.groupBy { it.symbol }.forEach { (optSymbol, optTxns) ->
        val firstTxn = optTxns.firstOrNull()
        val expiryStr = firstTxn?.expiryDate?.takeIf { it.isNotBlank() } ?: ""
        val expiryDate = runCatching { LocalDate.parse(expiryStr) }.getOrNull()
        val isExpiredAtEnd = expiryDate != null && rangeEnd.isAfter(expiryDate)
        val isExpiredAtStart = expiryDate != null && rangeStart.minusDays(1).isAfter(expiryDate)

        val optClosingQty = optTxns.filter { it.tradeDate <= rangeEnd.toString() }.fold(0.0) { qty, txn ->
            val tradeType = runCatching { TradeType.valueOf(txn.tradeType) }.getOrNull()
            when (tradeType) {
                TradeType.BUY -> qty + txn.quantity
                TradeType.SELL -> qty - txn.quantity
                else -> qty
            }
        }
        val optOpeningQty = optTxns.filter { it.tradeDate < rangeStart.toString() }.fold(0.0) { qty, txn ->
            val tradeType = runCatching { TradeType.valueOf(txn.tradeType) }.getOrNull()
            when (tradeType) {
                TradeType.BUY -> qty + txn.quantity
                TradeType.SELL -> qty - txn.quantity
                else -> qty
            }
        }
        val optClosingPrice = if (isExpiredAtEnd) 0.0 else {
            if (rangeEnd == latestDate && quoteMap[optSymbol]?.currentPrice != null) {
                quoteMap[optSymbol]!!.currentPrice!!
            } else {
                optTxns.lastOrNull { it.tradeDate <= rangeEnd.toString() && runCatching { TradeType.valueOf(it.tradeType) }.getOrNull()?.isSecurityTrade == true }?.price ?: 0.0
            }
        }
        val optOpeningPrice = if (isExpiredAtStart) 0.0 else {
            optTxns.lastOrNull { it.tradeDate < rangeStart.toString() && runCatching { TradeType.valueOf(it.tradeType) }.getOrNull()?.isSecurityTrade == true }?.price ?: optClosingPrice
        }

        closingOptionMarketValue += optClosingPrice * optClosingQty * 100.0
        openingOptionMarketValue += optOpeningPrice * optOpeningQty * 100.0
    }

    val closingMarketValue = closingStockMarketValue + closingOptionMarketValue
    val openingMarketValue = openingStockMarketValue + openingOptionMarketValue
    val rangePnl = closingMarketValue - openingMarketValue + totalSellProceeds - totalBuyCost - rangeCommission - rangeTax

    // Active Tab computed values
    val activeTransactions = remember(rangeTransactions, activeTab) {
        rangeTransactions.filter { txn ->
            if (activeTab == DetailTab.STOCK) txn.assetType != "OPTION" else txn.assetType == "OPTION"
        }
    }
    val activeClosingMarketValue = remember(activeTab, closingStockMarketValue, closingOptionMarketValue) {
        if (activeTab == DetailTab.STOCK) closingStockMarketValue else closingOptionMarketValue
    }
    val activePnl = remember(activeTab, stockPnl, optionPnl) {
        if (activeTab == DetailTab.STOCK) stockPnl else optionPnl
    }
    val activeCommission = remember(activeTransactions) {
        activeTransactions.filter {
            runCatching { TradeType.valueOf(it.tradeType) }.getOrNull()?.isSecurityTrade == true
        }.sumOf { it.commission }
    }
    val activeTax = remember(activeTransactions) {
        activeTransactions.filter {
            runCatching { TradeType.valueOf(it.tradeType) }.getOrNull()?.isSecurityTrade == true
        }.sumOf { it.tax }
    }
    val activeSellProceeds = remember(activeTransactions) {
        activeTransactions.filter {
            runCatching { TradeType.valueOf(it.tradeType) }.getOrNull() == TradeType.SELL
        }.sumOf { it.price * it.quantity * (if (it.assetType == "OPTION") 100.0 else 1.0) }
    }
    val activeBuyCost = remember(activeTransactions) {
        activeTransactions.filter {
            runCatching { TradeType.valueOf(it.tradeType) }.getOrNull() == TradeType.BUY
        }.sumOf { it.price * it.quantity * (if (it.assetType == "OPTION") 100.0 else 1.0) }
    }
    val activeSellFee = remember(activeTransactions) {
        activeTransactions.filter {
            runCatching { TradeType.valueOf(it.tradeType) }.getOrNull() == TradeType.SELL
        }.sumOf { it.commission + it.tax }
    }
    val activeBuyFee = remember(activeTransactions) {
        activeTransactions.filter {
            runCatching { TradeType.valueOf(it.tradeType) }.getOrNull() == TradeType.BUY
        }.sumOf { it.commission + it.tax }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onBack() }
                            .padding(8.dp),
                    ) {
                        Text("←", fontSize = 20.sp, color = ForegroundPrimary)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = "$securityName $symbol.$marketLabel",
                            color = ForegroundPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "更新至: ${latestDate.toString().replace("-", ".")}",
                            color = ForegroundMuted,
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            // Fixed range selector - SegmentRow style matching profit analysis
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 8.dp),
            ) {
                SegmentRow(
                    options = DetailRange.entries,
                    selected = selectedRange,
                    label = { it.label },
                    onSelected = { selectedRange = it },
                )
                if (selectedRange == DetailRange.CUSTOM) {
                    Spacer(modifier = Modifier.padding(top = 8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val startLabel = customStartDate.ifBlank { "起始日期" }
                        val endLabel = customEndDate.ifBlank { "结束日期" }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceSecondary)
                                .clickable {
                                    val init = runCatching { LocalDate.parse(customStartDate) }.getOrNull() ?: LocalDate.now()
                                    DatePickerDialog(context, { _, y, m, d ->
                                        customStartDate = LocalDate.of(y, m + 1, d).toString()
                                    }, init.year, init.monthValue - 1, init.dayOfMonth).show()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text(startLabel, color = if (customStartDate.isBlank()) ForegroundMuted else ForegroundPrimary, fontSize = 13.sp)
                        }
                        Text("-", color = ForegroundMuted, fontSize = 13.sp)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceSecondary)
                                .clickable {
                                    val init = runCatching { LocalDate.parse(customEndDate) }.getOrNull() ?: LocalDate.now()
                                    DatePickerDialog(context, { _, y, m, d ->
                                        customEndDate = LocalDate.of(y, m + 1, d).toString()
                                    }, init.year, init.monthValue - 1, init.dayOfMonth).show()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text(endLabel, color = if (customEndDate.isBlank()) ForegroundMuted else ForegroundPrimary, fontSize = 13.sp)
                        }
                    }
                }
            }

            // Scrollable content - 3 major sections
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // Section 1: 累计盈亏
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceSecondary)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val rangeStartFormatted = rangeStart.toString().replace("-", ".")
                    val rangeEndFormatted = rangeEnd.toString().replace("-", ".")
                    Text(
                        text = "$rangeStartFormatted - $rangeEndFormatted",
                        color = ForegroundMuted,
                        fontSize = 12.sp,
                    )
                    Text(
                        text = "累计盈亏 (${displayCurrency.name})",
                        color = ForegroundPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    
                    val totalSign = if (rangePnl >= 0) "+" else ""
                    Text(
                        text = "$totalSign${detailNumberFormatter.format(rangePnl)}",
                        color = if (rangePnl >= 0) MarketUp else MarketDown,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    
                    if (optionTransactions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("正股盈亏", color = ForegroundMuted, fontSize = 12.sp)
                                val stockSign = if (stockPnl >= 0) "+" else ""
                                Text(
                                    text = "$stockSign${detailNumberFormatter.format(stockPnl)}",
                                    color = if (stockPnl >= 0) MarketUp else MarketDown,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("衍生品盈亏", color = ForegroundMuted, fontSize = 12.sp)
                                val optionSign = if (optionPnl >= 0) "+" else ""
                                Text(
                                    text = "$optionSign${detailNumberFormatter.format(optionPnl)}",
                                    color = if (optionPnl >= 0) MarketUp else MarketDown,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Tab Switcher between Section 1 and Section 2
                if (optionTransactions.isNotEmpty()) {
                    SegmentRow(
                        options = DetailTab.entries.toList(),
                        selected = activeTab,
                        label = { it.label },
                        onSelected = { activeTab = it },
                    )
                }

                // Section 2: 盈亏构成
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceSecondary)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("盈亏构成", color = ForegroundPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    
                    val activeOpeningMarketValue = if (activeTab == DetailTab.STOCK) openingStockMarketValue else openingOptionMarketValue
                    if (activeOpeningMarketValue > 0.0) {
                        DetailRow("期初持仓市值", activeOpeningMarketValue, displayCurrency, forceUnsigned = true)
                    }
                    DetailRow("持仓市值", activeClosingMarketValue, displayCurrency, forceUnsigned = true)
                    
                    DetailRow("累计入账金额", activeSellProceeds, displayCurrency, forcePositive = true)
                    val sellLabel = if (activeTab == DetailTab.STOCK) "股票卖出" else "期权卖出"
                    DetailSubRow(sellLabel, activeSellProceeds, displayCurrency)
                    
                    DetailRow("累计出账金额", activeBuyCost, displayCurrency, forceNegative = true)
                    val buyLabel = if (activeTab == DetailTab.STOCK) "股票买入" else "期权买入"
                    DetailSubRow(buyLabel, activeBuyCost, displayCurrency)
                    
                    val activeFeeTotal = activeCommission + activeTax
                    DetailRow("费用合计", activeFeeTotal, displayCurrency, forceNegative = true)
                    val sellFeeLabel = if (activeTab == DetailTab.STOCK) "股票卖出费用" else "期权卖出费用"
                    DetailSubRow(sellFeeLabel, activeSellFee, displayCurrency)
                    val buyFeeLabel = if (activeTab == DetailTab.STOCK) "股票买入费用" else "期权买入费用"
                    DetailSubRow(buyFeeLabel, activeBuyFee, displayCurrency)
                    
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ForegroundMuted.copy(alpha = 0.2f)))
                    
                    DetailRow("盈亏合计", activePnl, displayCurrency)
                    
                    val formulaDesc = if (activeOpeningMarketValue > 0.0) {
                        "盈亏合计 = 持仓市值 - 期初持仓市值 + 累计入账金额 - 累计出账金额 - 费用合计"
                    } else {
                        "盈亏合计 = 持仓市值 + 累计入账金额 - 累计出账金额 - 费用合计"
                    }
                    Text(
                        text = formulaDesc,
                        color = ForegroundMuted,
                        fontSize = 11.sp,
                    )
                }

                // Section 3: 流水明细
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceSecondary)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("流水明细", color = ForegroundPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("共 ${activeTransactions.size} 笔", color = ForegroundMuted, fontSize = 13.sp)
                    }

                    if (activeTransactions.isEmpty()) {
                        Text("当前区间内没有交易记录", color = ForegroundMuted, fontSize = 13.sp)
                    } else {
                        activeTransactions.forEach { txn ->
                            val tradeType = runCatching { TradeType.valueOf(txn.tradeType) }.getOrNull()
                            val isBuy = tradeType == TradeType.BUY
                            val mult = if (txn.assetType == "OPTION") 100.0 else 1.0
                            val amount = txn.price * txn.quantity * mult
                            val feeTotal = txn.commission + txn.tax
                            val amountWithFee = if (tradeType?.isSecurityTrade == true) {
                                if (isBuy) amount + feeTotal else amount - feeTotal
                            } else {
                                amount
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = tradeType?.label ?: txn.tradeType,
                                            color = ForegroundPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        if (txn.assetType == "OPTION") {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = txn.symbol,
                                                color = ForegroundSecondary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Normal,
                                            )
                                        }
                                    }
                                    val timeText = if (tradeType == TradeType.SPLIT) {
                                        txn.tradeDate
                                    } else {
                                        "${txn.tradeDate} ${txn.tradeTime}"
                                    }
                                    Text(
                                        text = timeText,
                                        color = ForegroundMuted,
                                        fontSize = 11.sp,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    if (tradeType == TradeType.SPLIT) {
                                        Text(
                                            text = "折算比例 ${txn.price}",
                                            color = ForegroundSecondary,
                                            fontSize = 12.sp,
                                        )
                                    } else if (tradeType?.isSecurityTrade == true) {
                                        val formulaText = buildString {
                                            append(if (isBuy) "+" else "-")
                                            append(txn.quantity)
                                            val unit = if (txn.assetType == "OPTION") "张" else "股"
                                            append(unit)
                                            append(" × ")
                                            append(displayCurrency.symbol)
                                            append(detailNumberFormatter.format(txn.price))
                                            if (txn.assetType == "OPTION") {
                                                append(" × 100")
                                            }
                                            if (feeTotal > 0.0) {
                                                append(" + 费用")
                                                append(displayCurrency.symbol)
                                                append(detailNumberFormatter.format(feeTotal))
                                            }
                                        }
                                        Text(
                                            text = formulaText,
                                            color = ForegroundSecondary,
                                            fontSize = 12.sp,
                                        )
                                    }
                                    val amountText = if (tradeType == TradeType.SPLIT) {
                                        "--"
                                    } else {
                                        "${if (isBuy) "-" else "+"}${displayCurrency.symbol}${detailNumberFormatter.format(amountWithFee.absoluteValue)}"
                                    }
                                    Text(
                                        text = amountText,
                                        color = ForegroundPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

    }
}

@Composable
private fun DetailSubRow(
    label: String,
    value: Double,
    displayCurrency: DisplayCurrency,
) {
    if (value > 0.0) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = ForegroundMuted, fontSize = 12.sp)
            Text(
                text = "${displayCurrency.symbol}${detailNumberFormatter.format(value)}",
                color = ForegroundSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: Double,
    displayCurrency: DisplayCurrency,
    forceUnsigned: Boolean = false,
    forcePositive: Boolean = false,
    forceNegative: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = ForegroundSecondary, fontSize = 14.sp)
        val formatted = when {
            forceUnsigned -> "${displayCurrency.symbol}${detailNumberFormatter.format(value.absoluteValue)}"
            forcePositive -> "+${displayCurrency.symbol}${detailNumberFormatter.format(value.absoluteValue)}"
            forceNegative -> "-${displayCurrency.symbol}${detailNumberFormatter.format(value.absoluteValue)}"
            else -> {
                val sign = if (value >= 0) "+" else ""
                "$sign${displayCurrency.symbol}${detailNumberFormatter.format(value.absoluteValue)}"
            }
        }
        val color = when {
            forceUnsigned -> ForegroundPrimary
            forcePositive -> MarketUp
            forceNegative -> MarketDown
            else -> if (value >= 0) MarketUp else MarketDown
        }
        Text(
            text = formatted,
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun StockDetailRoutePreview() {
    StockLedgerTheme {
        StockDetailRoute(
            symbol = "AAPL",
            marketLabel = Market.US.label,
            analysis = PreviewFixtures.profitAnalysis,
            displayCurrency = DisplayCurrency.USD,
            quotes = emptyList(),
            onBack = {},
        )
    }
}
