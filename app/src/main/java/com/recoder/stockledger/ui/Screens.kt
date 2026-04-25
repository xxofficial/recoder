package com.recoder.stockledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recoder.stockledger.data.DisplayCurrency
import com.recoder.stockledger.data.HoldingUiModel
import com.recoder.stockledger.data.Market
import com.recoder.stockledger.data.MarketFilter
import com.recoder.stockledger.data.PortfolioSummary
import com.recoder.stockledger.data.RefreshState
import com.recoder.stockledger.data.SecuritySuggestionUiModel
import com.recoder.stockledger.data.SellCandidateUiModel
import com.recoder.stockledger.data.SymbolLookupState
import com.recoder.stockledger.data.SymbolLookupUiModel
import com.recoder.stockledger.data.TradeFormState
import com.recoder.stockledger.data.TradeType
import com.recoder.stockledger.data.TransactionFilter
import com.recoder.stockledger.data.TransactionSection
import com.recoder.stockledger.ui.theme.BackgroundPrimary
import com.recoder.stockledger.ui.theme.ForegroundMuted
import com.recoder.stockledger.ui.theme.ForegroundPrimary
import com.recoder.stockledger.ui.theme.ForegroundSecondary
import com.recoder.stockledger.ui.theme.MarketDown
import com.recoder.stockledger.ui.theme.MarketUp
import com.recoder.stockledger.ui.theme.SurfaceSecondary

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HoldingsRoute(
    summary: PortfolioSummary,
    displayCurrency: DisplayCurrency,
    holdings: List<HoldingUiModel>,
    onDeleteHolding: (HoldingUiModel) -> Unit,
    onDisplayCurrencySelected: (DisplayCurrency) -> Unit,
    onRefresh: () -> Unit,
    onDestinationSelected: (TopLevelDestination) -> Unit,
) {
    var pendingDeleteHolding by remember { mutableStateOf<HoldingUiModel?>(null) }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = summary.refreshState == RefreshState.REFRESHING,
        onRefresh = onRefresh,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary)
            .pullRefresh(pullRefreshState),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("总资产", color = ForegroundSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(summary.totalAssets, color = ForegroundPrimary, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    Row {
                        Text("今日盈亏 ", color = ForegroundMuted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            summary.dayProfit,
                            color = if (summary.dayProfit.startsWith("-")) MarketDown else MarketUp,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                CurrencySelector(
                    selected = displayCurrency,
                    onSelected = onDisplayCurrencySelected,
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = SurfaceSecondary,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        SummaryMetric(
                            label = "净入金",
                            value = summary.totalCost,
                            hint = summary.totalCostHint,
                            modifier = Modifier.weight(1f),
                        )
                        SummaryMetric(
                            label = "可用现金",
                            value = summary.cashBalance,
                            hint = summary.cashBalanceHint,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        SummaryMetric(
                            label = "累计盈亏",
                            value = summary.totalProfit,
                            hint = summary.totalProfitHint,
                            valueColor = if (summary.totalProfit.startsWith("-")) MarketDown else MarketUp,
                            modifier = Modifier.weight(1f),
                        )
                        SummaryMetric(
                            label = "当日盈亏",
                            value = summary.dayProfit,
                            hint = "按昨收估算",
                            valueColor = if (summary.dayProfit.startsWith("-")) MarketDown else MarketUp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Text("持仓列表", color = ForegroundPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = SurfaceSecondary,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (holdings.isEmpty()) {
                        Text("还没有持仓，先录入一笔买入交易。", color = ForegroundMuted, fontSize = 14.sp)
                    } else {
                        holdings.forEach { item ->
                            HoldingsCard(
                                item = item,
                                onDeleteClick = { pendingDeleteHolding = item },
                            )
                        }
                    }
                }
            }
        }

        PullRefreshIndicator(
            refreshing = summary.refreshState == RefreshState.REFRESHING,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = SurfaceSecondary,
            contentColor = ForegroundPrimary,
        )

        if (summary.showPullRefreshTime && !summary.refreshTimeLabel.isNullOrBlank()) {
            Text(
                text = "上次更新 ${summary.refreshTimeLabel}",
                color = ForegroundSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp)
                    .background(
                        color = SurfaceSecondary,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        BottomPillNavigation(
            current = TopLevelDestination.HOLDINGS,
            onDestinationSelected = onDestinationSelected,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        pendingDeleteHolding?.let { holding ->
            AlertDialog(
                onDismissRequest = { pendingDeleteHolding = null },
                title = { Text("删除持仓") },
                text = { Text("确认删除 ${holding.name} ${holding.code} 的全部本地交易记录吗？删除后无法恢复。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteHolding(holding)
                            pendingDeleteHolding = null
                        },
                    ) {
                        Text("删除", color = MarketDown)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteHolding = null }) {
                        Text("取消")
                    }
                },
            )
        }
    }
}

@Composable
fun OperationsRoute(
    onBuyClick: () -> Unit,
    onSellClick: () -> Unit,
    onDepositClick: () -> Unit,
    onWithdrawClick: () -> Unit,
    onDestinationSelected: (TopLevelDestination) -> Unit,
) {
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
                Text("\u5feb\u6377\u64cd\u4f5c", color = ForegroundPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

                TradeActionButtons(
                    onBuyClick = onBuyClick,
                    onSellClick = onSellClick,
                    onDepositClick = onDepositClick,
                    onWithdrawClick = onWithdrawClick,
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = SurfaceSecondary,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("\u5f55\u5165\u6307\u5f15", color = ForegroundPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "\u4e70\u5165\u3001\u5356\u51fa\u4f1a\u8fdb\u5165\u4ea4\u6613\u8868\u5355\uff0c\u652f\u6301\u4ee3\u7801\u8054\u60f3\u3001\u8d39\u7528\u5f55\u5165\u548c\u5907\u6ce8\u3002",
                        color = ForegroundSecondary,
                        fontSize = 13.sp,
                    )
                    Text(
                        "\u5165\u91d1\u3001\u51fa\u91d1\u7528\u4e8e\u8bb0\u5f55\u8d44\u91d1\u53d8\u52a8\uff0c\u5e76\u540c\u6b65\u5f71\u54cd\u6301\u4ed3\u603b\u89c8\u91cc\u7684\u51c0\u5165\u91d1\u4e0e\u53ef\u7528\u73b0\u91d1\u3002",
                        color = ForegroundSecondary,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        BottomPillNavigation(
            current = TopLevelDestination.OPERATIONS,
            onDestinationSelected = onDestinationSelected,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
fun TransactionsRoute(
    sections: List<TransactionSection>,
    selectedTradeFilter: TransactionFilter,
    selectedMarketFilter: MarketFilter,
    onTradeFilterSelected: (TransactionFilter) -> Unit,
    onMarketFilterSelected: (MarketFilter) -> Unit,
    onAddTradeClick: () -> Unit,
    onDestinationSelected: (TopLevelDestination) -> Unit,
) {
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
                Text("交易流水", color = ForegroundPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = SurfaceSecondary,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TransactionFilter.entries.forEach { filter ->
                            FilterChip(
                                text = filter.label,
                                selected = filter == selectedTradeFilter,
                                modifier = Modifier.weight(1f),
                                onClick = { onTradeFilterSelected(filter) },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MarketFilter.entries.forEach { filter ->
                            FilterChip(
                                text = filter.label,
                                selected = filter == selectedMarketFilter,
                                modifier = Modifier.weight(1f),
                                onClick = { onMarketFilterSelected(filter) },
                            )
                        }
                    }
                }

                if (sections.isEmpty()) {
                    Text("当前筛选条件下没有交易记录。", color = ForegroundMuted, fontSize = 14.sp)
                } else {
                    sections.forEach { section ->
                        Text(section.title, color = ForegroundSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = SurfaceSecondary,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                                )
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            section.items.forEach { item ->
                                TransactionRow(item)
                            }
                        }
                    }
                }

                FilledActionButton(
                    text = "录入交易",
                    onClick = onAddTradeClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        BottomPillNavigation(
            current = TopLevelDestination.TRANSACTIONS,
            onDestinationSelected = onDestinationSelected,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
fun TradeEntryRoute(
    state: TradeFormState,
    displayCurrency: DisplayCurrency,
    sellCandidates: List<SellCandidateUiModel>,
    symbolLookup: SymbolLookupUiModel,
    symbolSuggestions: List<SecuritySuggestionUiModel>,
    canSubmit: Boolean,
    validationMessage: String?,
    onBackClick: () -> Unit,
    onTradeTypeSelected: (TradeType) -> Unit,
    onSellCandidateSelected: (SellCandidateUiModel) -> Unit,
    onSymbolSuggestionSelected: (SecuritySuggestionUiModel) -> Unit,
    onMarketSelected: (Market) -> Unit,
    onSymbolChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onQuantityChange: (String) -> Unit,
    onCommissionChange: (String) -> Unit,
    onTaxChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val isSecurityTrade = state.selectedType.isSecurityTrade
    val isSellTrade = state.selectedType == TradeType.SELL
    val cashCurrencyCode = when (displayCurrency) {
        DisplayCurrency.USD -> "USD"
        DisplayCurrency.CNY -> "CNY"
        DisplayCurrency.HKD -> "HKD"
    }
    val cashCurrencyLabel = when (displayCurrency) {
        DisplayCurrency.USD -> "美元"
        DisplayCurrency.CNY -> "人民币"
        DisplayCurrency.HKD -> "港币"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(BackgroundPrimary),
    ) {
        ScreenHeader(title = "录入交易", onBack = onBackClick)

        Box(
            modifier = Modifier.weight(1f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 132.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                TradeTypeSelector(
                    selected = state.selectedType,
                    onSelected = onTradeTypeSelected,
                )

                if (isSellTrade) {
                    SellCandidateSection(
                        candidates = sellCandidates,
                        selectedValue = state.symbolOrName,
                        onSelected = onSellCandidateSelected,
                    )
                }

                if (isSecurityTrade) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("市场", color = ForegroundSecondary, fontSize = 14.sp)
                        TradeEntryMarketSelector(
                            selected = state.market,
                            onSelected = onMarketSelected,
                        )
                    }
                }

                if (isSecurityTrade) {
                    InputFieldBlock(
                        label = "证券代码 / 名称",
                        value = state.symbolOrName,
                        supportingText = symbolLookup.message,
                        supportingColor = when (symbolLookup.state) {
                            SymbolLookupState.INVALID -> MarketDown
                            SymbolLookupState.RESOLVED -> MarketUp
                            else -> ForegroundMuted
                        },
                        onValueChange = onSymbolChange,
                    )
                    if (symbolSuggestions.isNotEmpty()) {
                        SymbolSuggestionSection(
                            suggestions = symbolSuggestions,
                            onSelected = onSymbolSuggestionSelected,
                        )
                    }
                }

                TradeEntryDateField(
                    value = state.tradeDate,
                    onValueChange = onDateChange,
                )

                if (isSecurityTrade) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        InputFieldBlock(
                            label = "成交价格",
                            value = state.priceLabel,
                            modifier = Modifier.weight(1f),
                            keyboardType = KeyboardType.Decimal,
                            onValueChange = onPriceChange,
                        )
                        InputFieldBlock(
                            label = "成交数量",
                            value = state.quantityLabel,
                            modifier = Modifier.weight(1f),
                            keyboardType = KeyboardType.Number,
                            onValueChange = onQuantityChange,
                        )
                    }
                } else {
                    InputFieldBlock(
                        label = if (state.selectedType == TradeType.DEPOSIT) "入金金额 ($cashCurrencyCode)" else "出金金额 ($cashCurrencyCode)",
                        value = state.priceLabel,
                        supportingText = "当前按${cashCurrencyLabel}录入，保存后会自动折算到资产汇总",
                        keyboardType = KeyboardType.Decimal,
                        onValueChange = onPriceChange,
                    )
                }

                if (isSecurityTrade) {
                    TradeEntryFeeCard(
                        commission = state.commissionLabel,
                        tax = state.taxLabel,
                        onCommissionChange = onCommissionChange,
                        onTaxChange = onTaxChange,
                    )
                }

                TradeEntryNoteField(
                    note = state.note,
                    onValueChange = onNoteChange,
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (validationMessage != null && validationMessage != symbolLookup.message) {
                    Text(
                        text = validationMessage,
                        color = MarketDown,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                FilledActionButton(
                    text = when (state.selectedType) {
                        TradeType.BUY -> "确认买入"
                        TradeType.SELL -> "确认卖出"
                        TradeType.DEPOSIT -> "确认入金"
                        TradeType.WITHDRAW -> "确认出金"
                    },
                    onClick = onSubmit,
                    enabled = canSubmit,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
