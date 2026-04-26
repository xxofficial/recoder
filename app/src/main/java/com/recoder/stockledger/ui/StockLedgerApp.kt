package com.recoder.stockledger.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.recoder.stockledger.data.BrokerPlatform
import com.recoder.stockledger.data.ManagedPlatformUiModel
import com.recoder.stockledger.data.TradeType
import com.recoder.stockledger.ui.theme.BackgroundPrimary
import com.recoder.stockledger.ui.theme.BorderSubtle
import com.recoder.stockledger.ui.theme.ForegroundMuted
import com.recoder.stockledger.ui.theme.ForegroundPrimary
import com.recoder.stockledger.ui.theme.ForegroundSecondary
import com.recoder.stockledger.ui.theme.SurfaceSecondary
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private object Routes {
    const val Holdings = "holdings"
    const val Analysis = "analysis"
    const val Operations = "operations"
    const val Transactions = "transactions"
    const val TradeEntry = "trade-entry"
    const val TradeTypeArg = "tradeType"

    fun tradeEntry(type: TradeType): String = "$TradeEntry/${type.name}"
}

@Composable
fun StockLedgerApp(
    modifier: Modifier = Modifier,
    ledgerViewModel: LedgerViewModel = viewModel(),
) {
    val navController = rememberNavController()
    val uiState by ledgerViewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            ledgerViewModel.exportBackup(uri)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            ledgerViewModel.importBackup(uri)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.fillMaxWidth(0.78f),
            ) {
                PlatformDrawerContent(
                    options = uiState.managedPlatforms,
                    onSelect = { platform ->
                        ledgerViewModel.selectGlobalPlatform(platform)
                        coroutineScope.launch { drawerState.close() }
                    },
                )
            }
        },
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = Routes.Holdings,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(Routes.Holdings) {
                    HoldingsRoute(
                        summary = uiState.summary,
                        displayCurrency = uiState.displayCurrency,
                        holdings = uiState.holdings,
                        selectedPlatform = uiState.selectedPlatform,
                        onPlatformClick = { coroutineScope.launch { drawerState.open() } },
                        onDisplayCurrencySelected = ledgerViewModel::selectDisplayCurrency,
                        onRefresh = ledgerViewModel::refreshQuotesByPull,
                        onDestinationSelected = { destination ->
                            when (destination) {
                                TopLevelDestination.HOLDINGS -> Unit
                                TopLevelDestination.ANALYSIS -> navController.navigate(Routes.Analysis) { launchSingleTop = true }
                                TopLevelDestination.OPERATIONS -> navController.navigate(Routes.Operations) { launchSingleTop = true }
                                TopLevelDestination.TRANSACTIONS -> navController.navigate(Routes.Transactions) { launchSingleTop = true }
                            }
                        },
                    )
                }

                composable(Routes.Analysis) {
                    AdvancedProfitAnalysisRoute(
                        analysis = uiState.profitAnalysis,
                        displayCurrency = uiState.displayCurrency,
                        exchangeRates = uiState.exchangeRates,
                        selectedPlatform = uiState.selectedPlatform,
                        onPlatformClick = { coroutineScope.launch { drawerState.open() } },
                        onDisplayCurrencySelected = ledgerViewModel::selectDisplayCurrency,
                        onDestinationSelected = { destination ->
                            when (destination) {
                                TopLevelDestination.HOLDINGS -> navController.navigate(Routes.Holdings) { launchSingleTop = true }
                                TopLevelDestination.ANALYSIS -> Unit
                                TopLevelDestination.OPERATIONS -> navController.navigate(Routes.Operations) { launchSingleTop = true }
                                TopLevelDestination.TRANSACTIONS -> navController.navigate(Routes.Transactions) { launchSingleTop = true }
                            }
                        },
                    )
                }

                composable(Routes.Operations) {
                    OperationsRoute(
                        selectedPlatform = uiState.selectedPlatform,
                        onPlatformClick = { coroutineScope.launch { drawerState.open() } },
                        onBuyClick = {
                            ledgerViewModel.openTradeEntry(TradeType.BUY)
                            navController.navigate(Routes.tradeEntry(TradeType.BUY))
                        },
                        onSellClick = {
                            ledgerViewModel.openTradeEntry(TradeType.SELL)
                            navController.navigate(Routes.tradeEntry(TradeType.SELL))
                        },
                        onDepositClick = {
                            ledgerViewModel.openTradeEntry(TradeType.DEPOSIT)
                            navController.navigate(Routes.tradeEntry(TradeType.DEPOSIT))
                        },
                        onWithdrawClick = {
                            ledgerViewModel.openTradeEntry(TradeType.WITHDRAW)
                            navController.navigate(Routes.tradeEntry(TradeType.WITHDRAW))
                        },
                        onExportBackupClick = {
                            val filename = "stock-ledger-backup-${
                                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                            }.json"
                            exportLauncher.launch(filename)
                        },
                        onImportBackupClick = {
                            importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        },
                        backupStatusMessage = uiState.backupStatusMessage,
                        onDestinationSelected = { destination ->
                            when (destination) {
                                TopLevelDestination.HOLDINGS -> navController.navigate(Routes.Holdings) { launchSingleTop = true }
                                TopLevelDestination.ANALYSIS -> navController.navigate(Routes.Analysis) { launchSingleTop = true }
                                TopLevelDestination.OPERATIONS -> Unit
                                TopLevelDestination.TRANSACTIONS -> navController.navigate(Routes.Transactions) { launchSingleTop = true }
                            }
                        },
                    )
                }

                composable(Routes.Transactions) {
                    TransactionsRoute(
                        sections = uiState.transactionSections,
                        selectedPlatform = uiState.selectedPlatform,
                        selectedTradeFilter = uiState.selectedTradeFilter,
                        selectedMarketFilter = uiState.selectedMarketFilter,
                        transactionKeyword = uiState.transactionKeyword,
                        transactionDateStart = uiState.transactionDateStart,
                        transactionDateEnd = uiState.transactionDateEnd,
                        onTradeFilterSelected = ledgerViewModel::selectTradeFilter,
                        onMarketFilterSelected = ledgerViewModel::selectMarketFilter,
                        onTransactionKeywordChange = ledgerViewModel::updateTransactionKeyword,
                        onTransactionDateRangeChange = ledgerViewModel::updateTransactionDateRange,
                        onResetFilters = ledgerViewModel::resetTransactionFilters,
                        onEditTradeClick = { transactionId ->
                            val target = uiState.transactionSections
                                .flatMap { it.items }
                                .firstOrNull { it.id == transactionId }
                            if (target != null) {
                                ledgerViewModel.startEditingTrade(transactionId)
                                navController.navigate(Routes.tradeEntry(target.tradeType))
                            }
                        },
                        onPlatformClick = { coroutineScope.launch { drawerState.open() } },
                        onDestinationSelected = { destination ->
                            when (destination) {
                                TopLevelDestination.HOLDINGS -> navController.navigate(Routes.Holdings) { launchSingleTop = true }
                                TopLevelDestination.ANALYSIS -> navController.navigate(Routes.Analysis) { launchSingleTop = true }
                                TopLevelDestination.OPERATIONS -> navController.navigate(Routes.Operations) { launchSingleTop = true }
                                TopLevelDestination.TRANSACTIONS -> Unit
                            }
                        },
                    )
                }

                composable(
                    route = "${Routes.TradeEntry}/{${Routes.TradeTypeArg}}",
                    arguments = listOf(navArgument(Routes.TradeTypeArg) { type = NavType.StringType }),
                ) {
                    TradeEntryRoute(
                        state = uiState.draft,
                        isEditing = uiState.editingTransactionId != null,
                        displayCurrency = uiState.displayCurrency,
                        sellCandidates = uiState.sellCandidates,
                        symbolLookup = uiState.symbolLookup,
                        symbolSuggestions = uiState.symbolSuggestions,
                        canSubmit = uiState.canSubmitTrade,
                        validationMessage = uiState.tradeValidationMessage,
                        onBackClick = { navController.popBackStack() },
                        onTradeTypeSelected = ledgerViewModel::selectTradeType,
                        onTradePlatformSelected = ledgerViewModel::selectTradePlatform,
                        onSellCandidateSelected = ledgerViewModel::selectSellCandidate,
                        onSymbolSuggestionSelected = ledgerViewModel::selectSymbolSuggestion,
                        onMarketSelected = ledgerViewModel::selectTradeMarket,
                        onSymbolChange = ledgerViewModel::onSymbolInputChanged,
                        onDateChange = { value -> ledgerViewModel.updateDraft { draft -> draft.copy(tradeDate = value) } },
                        onPriceChange = { value -> ledgerViewModel.updateDraft { draft -> draft.copy(priceLabel = value) } },
                        onQuantityChange = { value -> ledgerViewModel.updateDraft { draft -> draft.copy(quantityLabel = value) } },
                        onCommissionChange = { value -> ledgerViewModel.updateDraft { draft -> draft.copy(commissionLabel = value) } },
                        onTaxChange = { value -> ledgerViewModel.updateDraft { draft -> draft.copy(taxLabel = value) } },
                        onNoteChange = { value -> ledgerViewModel.updateDraft { draft -> draft.copy(note = value) } },
                        onDeleteTradeClick = uiState.editingTransactionId?.let { transactionId ->
                            {
                                ledgerViewModel.deleteTrade(transactionId)
                                navController.popBackStack()
                            }
                        },
                        onSubmit = {
                            coroutineScope.launch {
                                if (ledgerViewModel.submitTrade()) {
                                    navController.navigate(Routes.Transactions) {
                                        popUpTo(Routes.Holdings)
                                        launchSingleTop = true
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerToggleButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .statusBarsPadding()
            .padding(end = 16.dp, top = 12.dp)
            .background(
                color = BackgroundPrimary,
                shape = RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Menu,
            contentDescription = "平台侧边栏",
            tint = ForegroundPrimary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun PlatformDrawerContent(
    options: List<ManagedPlatformUiModel>,
    onSelect: (BrokerPlatform?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary)
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("交易平台", color = ForegroundPrimary, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text("侧边栏切换后，持仓、盈亏和流水会同步显示对应平台的数据。", color = ForegroundSecondary, fontSize = 13.sp)

        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (option.isSelected) SurfaceSecondary else BackgroundPrimary,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .clickable { onSelect(option.platform) }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (option.platform != null) {
                    PlatformLogoBadge(
                        platform = option.platform,
                        modifier = Modifier.size(42.dp),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(SurfaceSecondary, RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("汇", color = ForegroundPrimary, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(option.label, color = ForegroundPrimary, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Text(
                        text = if (option.platform == null) "查看全部平台汇总数据" else "只看 ${option.label} 的数据",
                        color = ForegroundMuted,
                        fontSize = 12.sp,
                    )
                }
                if (option.isSelected) {
                    Text("当前", color = ForegroundPrimary, fontSize = 12.sp)
                }
            }
        }
    }
}
