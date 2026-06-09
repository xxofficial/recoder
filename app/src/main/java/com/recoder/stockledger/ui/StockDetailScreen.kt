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

private val detailNumberFormatter = DecimalFormat("#,##0.00")

@Composable
fun StockDetailRoute(
    symbol: String,
    marketLabel: String,
    analysis: ProfitAnalysisUiModel,
    displayCurrency: DisplayCurrency,
    onBack: () -> Unit,
) {
    var selectedRange by rememberSaveable { mutableStateOf(DetailRange.ALL) }
    var customStartDate by rememberSaveable { mutableStateOf("") }
    var customEndDate by rememberSaveable { mutableStateOf("") }

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

        val closingQty = stockTransactions.filter { it.tradeDate <= rangeEnd.toString() }.fold(0) { qty, txn ->
            val tradeType = runCatching { TradeType.valueOf(txn.tradeType) }.getOrNull()
            when (tradeType) {
                TradeType.BUY -> qty + txn.quantity
                TradeType.SELL -> qty - txn.quantity
                else -> qty
            }
        }
        val openingQty = stockTransactions.filter { it.tradeDate < rangeStart.toString() }.fold(0) { qty, txn ->
            val tradeType = runCatching { TradeType.valueOf(txn.tradeType) }.getOrNull()
            when (tradeType) {
                TradeType.BUY -> qty + txn.quantity
                TradeType.SELL -> qty - txn.quantity
                else -> qty
            }
        }
        val closingPrice = allSecurityPoints.lastOrNull { !it.date.isAfter(rangeEnd) }?.closePrice
            ?: rangeTxns.lastOrNull { runCatching { TradeType.valueOf(it.tradeType) }.getOrNull()?.isSecurityTrade == true }?.price
            ?: 0.0
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
            }.sumOf { it.price * it.quantity }
            val optBuyCost = optRangeTxns.filter {
                runCatching { TradeType.valueOf(it.tradeType) }.getOrNull() == TradeType.BUY
            }.sumOf { it.price * it.quantity }

            val firstTxn = optTxns.firstOrNull()
            val expiryStr = firstTxn?.expiryDate?.takeIf { it.isNotBlank() } ?: ""
            val expiryDate = runCatching { LocalDate.parse(expiryStr) }.getOrNull()

            val optClosingQty = optTxns.filter { it.tradeDate <= rangeEnd.toString() }.fold(0) { qty, txn ->
                val tradeType = runCatching { TradeType.valueOf(txn.tradeType) }.getOrNull()
                when (tradeType) {
                    TradeType.BUY -> qty + txn.quantity
                    TradeType.SELL -> qty - txn.quantity
                    else -> qty
                }
            }
            val optOpeningQty = optTxns.filter { it.tradeDate < rangeStart.toString() }.fold(0) { qty, txn ->
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
                optTxns.lastOrNull { it.tradeDate <= rangeEnd.toString() && runCatching { TradeType.valueOf(it.tradeType) }.getOrNull()?.isSecurityTrade == true }?.price ?: 0.0
            }
            val optOpeningPrice = if (isExpiredAtStart) 0.0 else {
                optTxns.lastOrNull { it.tradeDate < rangeStart.toString() && runCatching { TradeType.valueOf(it.tradeType) }.getOrNull()?.isSecurityTrade == true }?.price ?: optClosingPrice
            }

            val optClosingValue = optClosingPrice * optClosingQty
            val optOpeningValue = optOpeningPrice * optOpeningQty

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
    }.sumOf { it.price * it.quantity }
    val totalBuyCost = rangeTransactions.filter {
        runCatching { TradeType.valueOf(it.tradeType) }.getOrNull() == TradeType.BUY
    }.sumOf { it.price * it.quantity }

    val closingStockQty = stockTransactions.filter { it.tradeDate <= rangeEnd.toString() }.fold(0) { qty, txn ->
        val tradeType = runCatching { TradeType.valueOf(txn.tradeType) }.getOrNull()
        when (tradeType) {
            TradeType.BUY -> qty + txn.quantity
            TradeType.SELL -> qty - txn.quantity
            else -> qty
        }
    }
    val openingStockQty = stockTransactions.filter { it.tradeDate < rangeStart.toString() }.fold(0) { qty, txn ->
        val tradeType = runCatching { TradeType.valueOf(txn.tradeType) }.getOrNull()
        when (tradeType) {
            TradeType.BUY -> qty + txn.quantity
            TradeType.SELL -> qty - txn.quantity
            else -> qty
        }
    }
    val closingStockPrice = allSecurityPoints.lastOrNull { !it.date.isAfter(rangeEnd) }?.closePrice
        ?: stockTransactions.lastOrNull { it.tradeDate <= rangeEnd.toString() && runCatching { TradeType.valueOf(it.tradeType) }.getOrNull()?.isSecurityTrade == true }?.price
        ?: 0.0
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

        val optClosingQty = optTxns.filter { it.tradeDate <= rangeEnd.toString() }.fold(0) { qty, txn ->
            val tradeType = runCatching { TradeType.valueOf(txn.tradeType) }.getOrNull()
            when (tradeType) {
                TradeType.BUY -> qty + txn.quantity
                TradeType.SELL -> qty - txn.quantity
                else -> qty
            }
        }
        val optOpeningQty = optTxns.filter { it.tradeDate < rangeStart.toString() }.fold(0) { qty, txn ->
            val tradeType = runCatching { TradeType.valueOf(txn.tradeType) }.getOrNull()
            when (tradeType) {
                TradeType.BUY -> qty + txn.quantity
                TradeType.SELL -> qty - txn.quantity
                else -> qty
            }
        }
        val optClosingPrice = if (isExpiredAtEnd) 0.0 else {
            optTxns.lastOrNull { it.tradeDate <= rangeEnd.toString() && runCatching { TradeType.valueOf(it.tradeType) }.getOrNull()?.isSecurityTrade == true }?.price ?: 0.0
        }
        val optOpeningPrice = if (isExpiredAtStart) 0.0 else {
            optTxns.lastOrNull { it.tradeDate < rangeStart.toString() && runCatching { TradeType.valueOf(it.tradeType) }.getOrNull()?.isSecurityTrade == true }?.price ?: optClosingPrice
        }

        closingOptionMarketValue += optClosingPrice * optClosingQty
        openingOptionMarketValue += optOpeningPrice * optOpeningQty
    }

    val closingMarketValue = closingStockMarketValue + closingOptionMarketValue
    val openingMarketValue = openingStockMarketValue + openingOptionMarketValue
    val rangePnl = closingMarketValue - openingMarketValue + totalSellProceeds - totalBuyCost - rangeCommission - rangeTax

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
                    Text(
                        text = "$securityName $symbol.$marketLabel",
                        color = ForegroundPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
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
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("累计盈亏", color = ForegroundPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("区间盈亏", color = ForegroundSecondary, fontSize = 14.sp)
                        val sign = if (rangePnl >= 0) "+" else ""
                        Text(
                            text = "$sign${displayCurrency.symbol}${detailNumberFormatter.format(rangePnl.absoluteValue)}",
                            color = if (rangePnl >= 0) MarketUp else MarketDown,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    if (optionTransactions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("├─ 正股 ($symbol)", color = ForegroundSecondary, fontSize = 13.sp)
                            val sign = if (stockPnl >= 0) "+" else ""
                            Text(
                                text = "$sign${displayCurrency.symbol}${detailNumberFormatter.format(stockPnl.absoluteValue)}",
                                color = if (stockPnl >= 0) MarketUp else MarketDown,
                                fontSize = 13.sp,
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("└─ 衍生品期权", color = ForegroundSecondary, fontSize = 13.sp)
                            val sign = if (optionPnl >= 0) "+" else ""
                            Text(
                                text = "$sign${displayCurrency.symbol}${detailNumberFormatter.format(optionPnl.absoluteValue)}",
                                color = if (optionPnl >= 0) MarketUp else MarketDown,
                                fontSize = 13.sp,
                            )
                        }
                    }
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
                    DetailRow("期初持仓市值", openingMarketValue, displayCurrency, forceUnsigned = true)
                    DetailRow("期末持仓市值", closingMarketValue, displayCurrency, forceUnsigned = true)
                    DetailRow("累计入账金额", totalSellProceeds, displayCurrency, forcePositive = true)
                    DetailRow("累计出账金额", totalBuyCost, displayCurrency, forceNegative = true)
                    DetailRow("佣金/平台费", rangeCommission, displayCurrency, forceNegative = true)
                    DetailRow("税费", rangeTax, displayCurrency, forceNegative = true)
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
                        Text("共 ${rangeTransactions.size} 笔", color = ForegroundMuted, fontSize = 13.sp)
                    }

                    if (rangeTransactions.isEmpty()) {
                        Text("当前区间内没有交易记录", color = ForegroundMuted, fontSize = 13.sp)
                    } else {
                        rangeTransactions.forEach { txn ->
                            val tradeType = runCatching { TradeType.valueOf(txn.tradeType) }.getOrNull()
                            val isBuy = tradeType == TradeType.BUY
                            val amount = txn.price * txn.quantity
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
                                    Text(
                                        text = "${txn.tradeDate} ${txn.tradeTime}",
                                        color = ForegroundMuted,
                                        fontSize = 11.sp,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    if (tradeType?.isSecurityTrade == true) {
                                        val formulaText = buildString {
                                            append(if (isBuy) "+" else "-")
                                            append(txn.quantity)
                                            val unit = if (txn.assetType == "OPTION") "张" else "股"
                                            append(unit)
                                            append(" × ")
                                            append(displayCurrency.symbol)
                                            append(detailNumberFormatter.format(txn.price))
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
                                    Text(
                                        text = "${if (isBuy) "-" else "+"}${displayCurrency.symbol}${detailNumberFormatter.format(amountWithFee.absoluteValue)}",
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
            onBack = {},
        )
    }
}
