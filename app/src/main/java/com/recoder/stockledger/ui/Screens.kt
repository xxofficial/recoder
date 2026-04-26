package com.recoder.stockledger.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recoder.stockledger.data.BrokerPlatform
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
import com.recoder.stockledger.data.TransactionUiModel
import com.recoder.stockledger.ui.theme.BackgroundPrimary
import com.recoder.stockledger.ui.theme.ForegroundMuted
import com.recoder.stockledger.ui.theme.ForegroundPrimary
import com.recoder.stockledger.ui.theme.ForegroundSecondary
import com.recoder.stockledger.ui.theme.MarketDown
import com.recoder.stockledger.ui.theme.MarketUp
import com.recoder.stockledger.ui.theme.SurfaceSecondary
import java.time.LocalDate

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HoldingsRoute(
    summary: PortfolioSummary,
    displayCurrency: DisplayCurrency,
    holdings: List<HoldingUiModel>,
    selectedPlatform: BrokerPlatform?,
    onPlatformClick: () -> Unit,
    onDisplayCurrencySelected: (DisplayCurrency) -> Unit,
    onRefresh: () -> Unit,
    onDestinationSelected: (TopLevelDestination) -> Unit,
) {
    val pullRefreshState = rememberPullRefreshState(
        refreshing = summary.refreshState == RefreshState.REFRESHING,
        onRefresh = onRefresh,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PlatformTopBar(
                selectedPlatform = selectedPlatform,
                onClick = onPlatformClick,
                modifier = Modifier.statusBarsPadding(),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .pullRefresh(pullRefreshState)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InlineCurrencyDropdown(
                        title = "总资产",
                        selected = displayCurrency,
                        onSelected = onDisplayCurrencySelected,
                    )
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

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = SurfaceSecondary,
                            shape = RoundedCornerShape(16.dp),
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
                            label = "持仓浮盈",
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
                            shape = RoundedCornerShape(16.dp),
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (holdings.isEmpty()) {
                        Text("当前范围内还没有持仓。", color = ForegroundMuted, fontSize = 14.sp)
                    } else {
                        holdings.forEach { item ->
                            EnhancedHoldingsCard(item = item)
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
                text = "上次刷新 ${summary.refreshTimeLabel}",
                color = ForegroundSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 96.dp)
                    .background(
                        color = SurfaceSecondary,
                        shape = RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        BottomPillNavigation(
            current = TopLevelDestination.HOLDINGS,
            onDestinationSelected = onDestinationSelected,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
@Composable
fun OperationsRoute(
    selectedPlatform: BrokerPlatform?,
    onPlatformClick: () -> Unit,
    onBuyClick: () -> Unit,
    onSellClick: () -> Unit,
    onDepositClick: () -> Unit,
    onWithdrawClick: () -> Unit,
    onExportBackupClick: () -> Unit,
    onImportBackupClick: () -> Unit,
    backupStatusMessage: String?,
    onDestinationSelected: (TopLevelDestination) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PlatformTopBar(
                selectedPlatform = selectedPlatform,
                onClick = onPlatformClick,
                modifier = Modifier.statusBarsPadding(),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                CurrentPlatformBanner(
                    selectedPlatform = selectedPlatform,
                    title = "默认交易平台",
                    subtitle = "买入、卖出、入金、出金会默认带出当前选中的平台。",
                )

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
                            shape = RoundedCornerShape(16.dp),
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("数据备份", color = ForegroundPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "导出会保存当前交易记录和当前全局平台选择；导入会覆盖本地交易数据并重新刷新行情。",
                        color = ForegroundSecondary,
                        fontSize = 13.sp,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FilledActionButton(
                            text = "导出备份",
                            onClick = onExportBackupClick,
                            modifier = Modifier.weight(1f),
                        )
                        OutlineActionButton(
                            text = "导入备份",
                            onClick = onImportBackupClick,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (!backupStatusMessage.isNullOrBlank()) {
                        Text(
                            text = backupStatusMessage,
                            color = ForegroundMuted,
                            fontSize = 12.sp,
                        )
                    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsRoute(
    sections: List<TransactionSection>,
    selectedPlatform: BrokerPlatform?,
    selectedTradeFilter: TransactionFilter,
    selectedMarketFilter: MarketFilter,
    transactionKeyword: String,
    transactionDateStart: String,
    transactionDateEnd: String,
    onTradeFilterSelected: (TransactionFilter) -> Unit,
    onMarketFilterSelected: (MarketFilter) -> Unit,
    onTransactionKeywordChange: (String) -> Unit,
    onTransactionDateRangeChange: (String?, String?) -> Unit,
    onResetFilters: () -> Unit,
    onEditTradeClick: (Long) -> Unit,
    onPlatformClick: () -> Unit,
    onDestinationSelected: (TopLevelDestination) -> Unit,
) {
    var showFilterSheet by remember { mutableStateOf(false) }
    var draftTradeFilter by remember { mutableStateOf(selectedTradeFilter) }
    var draftMarketFilter by remember { mutableStateOf(selectedMarketFilter) }
    var draftStartDate by remember { mutableStateOf(transactionDateStart) }
    var draftEndDate by remember { mutableStateOf(transactionDateEnd) }

    val hasActiveFilters = selectedTradeFilter != TransactionFilter.ALL ||
        selectedMarketFilter != MarketFilter.ALL ||
        transactionKeyword.isNotBlank() ||
        transactionDateStart.isNotBlank() ||
        transactionDateEnd.isNotBlank()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PlatformTopBar(
                selectedPlatform = selectedPlatform,
                onClick = onPlatformClick,
                modifier = Modifier.statusBarsPadding(),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    InputFieldBlock(
                        label = "搜索证券名称或代码",
                        value = transactionKeyword,
                        modifier = Modifier.weight(1f),
                        onValueChange = onTransactionKeywordChange,
                    )
                    FilterActionButton(
                        active = hasActiveFilters,
                        onClick = {
                            draftTradeFilter = selectedTradeFilter
                            draftMarketFilter = selectedMarketFilter
                            draftStartDate = transactionDateStart
                            draftEndDate = transactionDateEnd
                            showFilterSheet = true
                        },
                    )
                }

                if (hasActiveFilters) {
                    ActiveTransactionFilterSummary(
                        tradeFilter = selectedTradeFilter,
                        marketFilter = selectedMarketFilter,
                        keyword = transactionKeyword,
                        startDate = transactionDateStart,
                        endDate = transactionDateEnd,
                    )
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
                                    shape = RoundedCornerShape(16.dp),
                                )
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            section.items.forEach { item ->
                                TransactionRow(
                                    item = item,
                                    onClick = { onEditTradeClick(item.id) },
                                )
                            }
                        }
                    }
                }
            }
        }

        BottomPillNavigation(
            current = TopLevelDestination.TRANSACTIONS,
            onDestinationSelected = onDestinationSelected,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            containerColor = BackgroundPrimary,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("筛选流水", color = ForegroundPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("交易类型", color = ForegroundSecondary, fontSize = 13.sp)
                    FilterChipWrapRow(
                        options = TransactionFilter.entries,
                        selected = draftTradeFilter,
                        label = { it.label },
                        onSelected = { draftTradeFilter = it },
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("市场", color = ForegroundSecondary, fontSize = 13.sp)
                    FilterChipWrapRow(
                        options = MarketFilter.entries,
                        selected = draftMarketFilter,
                        label = { it.label },
                        onSelected = { draftMarketFilter = it },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilterDateField(
                        label = "开始日期",
                        value = draftStartDate,
                        modifier = Modifier.weight(1f),
                        onValueChange = { draftStartDate = it },
                    )
                    FilterDateField(
                        label = "结束日期",
                        value = draftEndDate,
                        modifier = Modifier.weight(1f),
                        onValueChange = { draftEndDate = it },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlineActionButton(
                        text = "重置条件",
                        onClick = {
                            onResetFilters()
                            showFilterSheet = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                    FilledActionButton(
                        text = "确定",
                        onClick = {
                            val (normalizedStart, normalizedEnd) = normalizeDateRange(draftStartDate, draftEndDate)
                            onTradeFilterSelected(draftTradeFilter)
                            onMarketFilterSelected(draftMarketFilter)
                            onTransactionDateRangeChange(normalizedStart, normalizedEnd)
                            showFilterSheet = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
fun TradeEntryRoute(
    state: TradeFormState,
    isEditing: Boolean,
    displayCurrency: DisplayCurrency,
    sellCandidates: List<SellCandidateUiModel>,
    symbolLookup: SymbolLookupUiModel,
    symbolSuggestions: List<SecuritySuggestionUiModel>,
    canSubmit: Boolean,
    validationMessage: String?,
    onBackClick: () -> Unit,
    onTradeTypeSelected: (TradeType) -> Unit,
    onTradePlatformSelected: (BrokerPlatform) -> Unit,
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
    onDeleteTradeClick: (() -> Unit)? = null,
    onSubmit: () -> Unit,
) {
    val isSecurityTrade = state.selectedType.isSecurityTrade
    val isSellTrade = state.selectedType == TradeType.SELL
    val (tradeTypeBadgeBackground, tradeTypeBadgeForeground) = tradeTypeColors(state.selectedType)
    var showDeleteDialog by remember { mutableStateOf(false) }
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
        ScreenHeader(
            title = if (isEditing) "编辑记录" else "录入交易",
            onBack = onBackClick,
            trailingContent = if (isEditing && onDeleteTradeClick != null) {
                {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "筛选",
                        tint = MarketDown,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable { showDeleteDialog = true },
                    )
                }
            } else {
                null
            },
        )

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
                if (isEditing) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("浜ゆ槗绫诲瀷", color = ForegroundSecondary, fontSize = 14.sp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = SurfaceSecondary,
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            PillLabel(
                                text = state.selectedType.label,
                                background = tradeTypeBadgeBackground,
                                foreground = tradeTypeBadgeForeground,
                            )
                            Text(
                                text = "编辑时不可修改",
                                color = ForegroundMuted,
                                fontSize = 12.sp,
                            )
                        }
                    }
                } else {
                    TradeTypeSelector(
                        selected = state.selectedType,
                        onSelected = onTradeTypeSelected,
                    )
                }

                PlatformDropdownField(
                    selectedPlatform = state.platform,
                    onSelected = onTradePlatformSelected,
                )

                if (isSellTrade) {
                    PreciseSellCandidateSection(
                        candidates = sellCandidates,
                        selectedSymbol = symbolLookup.resolvedSymbol,
                        selectedMarket = symbolLookup.resolvedMarket,
                        onSelected = onSellCandidateSelected,
                    )
                }

                if (isSecurityTrade) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("甯傚満", color = ForegroundSecondary, fontSize = 14.sp)
                        TradeEntryMarketSelector(
                            selected = state.market,
                            onSelected = onMarketSelected,
                        )
                    }
                }

                if (isSecurityTrade) {
                    InputFieldBlock(
                        label = "璇佸埜浠ｇ爜 / 鍚嶇О",
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
                            label = "鎴愪氦浠锋牸",
                            value = state.priceLabel,
                            modifier = Modifier.weight(1f),
                            keyboardType = KeyboardType.Decimal,
                            onValueChange = onPriceChange,
                        )
                        InputFieldBlock(
                            label = "鎴愪氦鏁伴噺",
                            value = state.quantityLabel,
                            modifier = Modifier.weight(1f),
                            keyboardType = KeyboardType.Number,
                            onValueChange = onQuantityChange,
                        )
                    }
                } else {
                    InputFieldBlock(
                        label = if (state.selectedType == TradeType.DEPOSIT) {
                            "入金金额 ($cashCurrencyCode)"
                        } else {
                            "出金金额 ($cashCurrencyCode)"
                        },
                        value = state.priceLabel,
                        supportingText = "当前按${cashCurrencyLabel}录入，保存后会自动折算到资产汇总。",
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
                    text = if (isEditing) {
                        "保存修改"
                    } else {
                        when (state.selectedType) {
                            TradeType.BUY -> "确认买入"
                            TradeType.SELL -> "确认卖出"
                            TradeType.DEPOSIT -> "确认入金"
                            TradeType.WITHDRAW -> "确认出金"
                        }
                    },
                    onClick = onSubmit,
                    enabled = canSubmit,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (showDeleteDialog && onDeleteTradeClick != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("删除记录") },
                text = { Text("确认删除这条${state.selectedType.label}记录吗？删除后无法恢复。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            onDeleteTradeClick()
                        },
                    ) {
                        Text("删除", color = MarketDown)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("取消")
                    }
                },
            )
        }
    }
}

@Composable
fun CurrentPlatformBanner(
    selectedPlatform: BrokerPlatform?,
    title: String = "当前平台",
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = SurfaceSecondary,
                shape = RoundedCornerShape(16.dp),
            )
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectedPlatform != null) {
            PlatformLogoBadge(
                platform = selectedPlatform,
                modifier = Modifier.size(40.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = ForegroundSecondary, fontSize = 13.sp)
            Text(
                text = selectedPlatform?.label ?: "汇总",
                color = ForegroundPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, color = ForegroundMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun FilterActionButton(
    active: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .background(
                color = if (active) ForegroundPrimary else SurfaceSecondary,
                shape = RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.FilterList,
            contentDescription = "筛选",
            tint = if (active) BackgroundPrimary else ForegroundPrimary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = if (active) "已筛选" else "筛选",
            color = if (active) BackgroundPrimary else ForegroundPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ActiveTransactionFilterSummary(
    tradeFilter: TransactionFilter,
    marketFilter: MarketFilter,
    keyword: String,
    startDate: String,
    endDate: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = SurfaceSecondary,
                shape = RoundedCornerShape(16.dp),
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("当前筛选", color = ForegroundSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        val summaryItems = buildList {
            if (tradeFilter != TransactionFilter.ALL) add(tradeFilter.label)
            if (marketFilter != MarketFilter.ALL) add(marketFilter.label)
            if (keyword.isNotBlank()) add("关键词：$keyword")
            if (startDate.isNotBlank() || endDate.isNotBlank()) {
                add("${startDate.ifBlank { "最早" }} - ${endDate.ifBlank { "今天" }}")
            }
        }
        Text(summaryItems.joinToString(" 路 "), color = ForegroundPrimary, fontSize = 13.sp)
    }
}

@Composable
private fun <T> FilterChipWrapRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(3).forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowOptions.forEach { option ->
                    FilterChip(
                        text = label(option),
                        selected = option == selected,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelected(option) },
                    )
                }
                repeat(3 - rowOptions.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FilterDateField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    val context = LocalContext.current
    val selectedDate = remember(value) {
        runCatching { LocalDate.parse(value) }.getOrNull() ?: LocalDate.now()
    }
    InputFieldBlock(
        label = label,
        value = value,
        modifier = modifier,
        trailingIcon = Icons.Filled.DateRange,
        onClick = {
            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    onValueChange(LocalDate.of(year, month + 1, dayOfMonth).toString())
                },
                selectedDate.year,
                selectedDate.monthValue - 1,
                selectedDate.dayOfMonth,
            ).show()
        },
    )
}

@Composable
private fun PlatformDropdownField(
    selectedPlatform: BrokerPlatform,
    onSelected: (BrokerPlatform) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("浜ゆ槗骞冲彴", color = ForegroundSecondary, fontSize = 14.sp)
        Box {
            InputFieldBlock(
                label = "",
                value = selectedPlatform.label,
                trailingIcon = Icons.Filled.ArrowDropDown,
                onClick = { expanded = true },
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(BackgroundPrimary),
            ) {
                BrokerPlatform.configurableEntries.forEach { platform ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PlatformLogoBadge(
                                    platform = platform,
                                    modifier = Modifier.size(28.dp),
                                )
                                Text(platform.label)
                            }
                        },
                        onClick = {
                            onSelected(platform)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

private fun normalizeDateRange(
    startDate: String,
    endDate: String,
): Pair<String?, String?> {
    val normalizedStart = startDate.takeIf { it.isNotBlank() }
    val normalizedEnd = endDate.takeIf { it.isNotBlank() }
    val parsedStart = normalizedStart?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val parsedEnd = normalizedEnd?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    return if (parsedStart != null && parsedEnd != null && parsedStart.isAfter(parsedEnd)) {
        normalizedEnd to normalizedStart
    } else {
        normalizedStart to normalizedEnd
    }
}


