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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.recoder.stockledger.data.PlatformVisibilityUiModel
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
    const val Settings = "settings"
    const val FullRanking = "full-ranking"
    const val StockDetail = "stock-detail"
    const val TradeTypeArg = "tradeType"
    const val SymbolArg = "symbol"
    const val MarketArg = "market"

    fun tradeEntry(type: TradeType): String = "$TradeEntry/${type.name}"
    fun stockDetail(symbol: String, market: String): String = "$StockDetail/$symbol/$market"
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
    val pdfImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            ledgerViewModel.importZhuoruiStatementPdfs(uris)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.fillMaxWidth(0.78f),
            ) {
                PlatformDrawerContent(
                    options = uiState.managedPlatforms,
                    visibilityOptions = uiState.platformVisibilityOptions,
                    onSelect = { platform ->
                        ledgerViewModel.selectGlobalPlatform(platform)
                        coroutineScope.launch { drawerState.close() }
                    },
                    onPlatformVisibilityChange = ledgerViewModel::setPlatformVisibility,
                    onClose = { coroutineScope.launch { drawerState.close() } },
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
                        onSettingsClick = { navController.navigate(Routes.Settings) },
                        onDisplayCurrencySelected = ledgerViewModel::selectDisplayCurrency,
                        onRefresh = ledgerViewModel::refreshQuotesByPull,
                        onDestinationSelected = { destination ->
                            when (destination) {
                                TopLevelDestination.HOLDINGS -> Unit
                                TopLevelDestination.ANALYSIS -> navController.navigate(Routes.Analysis) {
                                    launchSingleTop = true
                                    popUpTo(Routes.Holdings) { saveState = true }
                                }
                                TopLevelDestination.OPERATIONS -> navController.navigate(Routes.Operations) {
                                    launchSingleTop = true
                                    popUpTo(Routes.Holdings) { saveState = true }
                                }
                                TopLevelDestination.TRANSACTIONS -> navController.navigate(Routes.Transactions) {
                                    launchSingleTop = true
                                    popUpTo(Routes.Holdings) { saveState = true }
                                }
                            }
                        },
                        onHoldingClick = { holding ->
                            navController.navigate(Routes.stockDetail(holding.code, holding.market.label))
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
                        onSettingsClick = { navController.navigate(Routes.Settings) },
                        onDisplayCurrencySelected = ledgerViewModel::selectDisplayCurrency,
                        onDestinationSelected = { destination ->
                            when (destination) {
                                TopLevelDestination.HOLDINGS -> navController.navigate(Routes.Holdings) {
                                    launchSingleTop = true
                                    popUpTo(Routes.Holdings) { saveState = true }
                                }
                                TopLevelDestination.ANALYSIS -> Unit
                                TopLevelDestination.OPERATIONS -> navController.navigate(Routes.Operations) {
                                    launchSingleTop = true
                                    popUpTo(Routes.Holdings) { saveState = true }
                                }
                                TopLevelDestination.TRANSACTIONS -> navController.navigate(Routes.Transactions) {
                                    launchSingleTop = true
                                    popUpTo(Routes.Holdings) { saveState = true }
                                }
                            }
                        },
                        onSecurityClick = { symbol, market ->
                            navController.navigate(Routes.stockDetail(symbol, market))
                        },
                        onViewFullRanking = {
                            navController.navigate(Routes.FullRanking)
                        },
                    )
                }

                composable(Routes.Operations) {
                    OperationsRoute(
                        selectedPlatform = uiState.selectedPlatform,
                        onPlatformClick = { coroutineScope.launch { drawerState.open() } },
                        onSettingsClick = { navController.navigate(Routes.Settings) },
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
                        hsbcImportDraftText = uiState.hsbcImportDraftText,
                        hsbcImportStatusMessage = uiState.hsbcImportStatusMessage,
                        onHsbcImportDraftTextChange = ledgerViewModel::updateHsbcImportDraftText,
                        onImportHsbcNotificationText = ledgerViewModel::importHsbcNotificationText,
                        zhuoruiEmailSyncConfig = uiState.zhuoruiEmailSyncConfig,
                        zhuoruiEmailManualSyncOptions = uiState.zhuoruiEmailManualSyncOptions,
                        zhuoruiEmailAutoImportEnabled = uiState.zhuoruiEmailAutoImportEnabled,
                        zhuoruiEmailSyncStatusMessage = uiState.zhuoruiEmailSyncStatusMessage,
                        onZhuoruiEmailSyncConfigChange = { config ->
                            ledgerViewModel.updateZhuoruiEmailSyncConfig { config }
                        },
                        onZhuoruiEmailManualSyncOptionsChange = { options ->
                            ledgerViewModel.updateZhuoruiEmailManualSyncOptions { options }
                        },
                        onSaveZhuoruiEmailSyncConfig = ledgerViewModel::saveZhuoruiEmailSyncConfig,
                        onSyncZhuoruiMailboxNow = ledgerViewModel::syncZhuoruiMailboxNow,
                        onEnableZhuoruiEmailAutoImport = { ledgerViewModel.setZhuoruiEmailAutoImportEnabled(true) },
                        onDisableZhuoruiEmailAutoImport = { ledgerViewModel.setZhuoruiEmailAutoImportEnabled(false) },
                        zhuoruiStatementPdfPassword = uiState.zhuoruiStatementPdfPassword,
                        zhuoruiStatementPdfImportStatusMessage = uiState.zhuoruiStatementPdfImportStatusMessage,
                        onZhuoruiStatementPdfPasswordChange = ledgerViewModel::updateZhuoruiStatementPdfPassword,
                        onImportZhuoruiStatementPdfs = {
                            pdfImportLauncher.launch(arrayOf("application/pdf"))
                        },
                        onDestinationSelected = { destination ->
                            when (destination) {
                                TopLevelDestination.HOLDINGS -> navController.navigate(Routes.Holdings) {
                                    launchSingleTop = true
                                    popUpTo(Routes.Holdings) { saveState = true }
                                }
                                TopLevelDestination.ANALYSIS -> navController.navigate(Routes.Analysis) {
                                    launchSingleTop = true
                                    popUpTo(Routes.Holdings) { saveState = true }
                                }
                                TopLevelDestination.OPERATIONS -> Unit
                                TopLevelDestination.TRANSACTIONS -> navController.navigate(Routes.Transactions) {
                                    launchSingleTop = true
                                    popUpTo(Routes.Holdings) { saveState = true }
                                }
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
                        onSettingsClick = { navController.navigate(Routes.Settings) },
                        onDestinationSelected = { destination ->
                            when (destination) {
                                TopLevelDestination.HOLDINGS -> navController.navigate(Routes.Holdings) {
                                    launchSingleTop = true
                                    popUpTo(Routes.Holdings) { saveState = true }
                                }
                                TopLevelDestination.ANALYSIS -> navController.navigate(Routes.Analysis) {
                                    launchSingleTop = true
                                    popUpTo(Routes.Holdings) { saveState = true }
                                }
                                TopLevelDestination.OPERATIONS -> navController.navigate(Routes.Operations) {
                                    launchSingleTop = true
                                    popUpTo(Routes.Holdings) { saveState = true }
                                }
                                TopLevelDestination.TRANSACTIONS -> Unit
                            }
                        },
                        batchSelectionMode = uiState.batchSelectionMode,
                        selectedTransactionIds = uiState.selectedTransactionIds,
                        onEnterBatchMode = ledgerViewModel::enterBatchSelectionMode,
                        onExitBatchMode = ledgerViewModel::exitBatchSelectionMode,
                        onToggleSelection = ledgerViewModel::toggleTransactionSelection,
                        onSelectAll = ledgerViewModel::selectAllTransactions,
                        onDeleteSelected = ledgerViewModel::deleteSelectedTransactions,
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
                        availablePlatforms = uiState.availableTradePlatforms,
                        sellCandidates = uiState.sellCandidates,
                        symbolLookup = uiState.symbolLookup,
                        symbolSuggestions = uiState.symbolSuggestions,
                        canSubmit = uiState.canSubmitTrade,
                        validationMessage = uiState.tradeValidationMessage,
                        onBackClick = { navController.popBackStack() },
                        onTradePlatformSelected = ledgerViewModel::selectTradePlatform,
                        onSellCandidateSelected = ledgerViewModel::selectSellCandidate,
                        onSymbolSuggestionSelected = ledgerViewModel::selectSymbolSuggestion,
                        onMarketSelected = ledgerViewModel::selectTradeMarket,
                        onCashCurrencySelected = ledgerViewModel::selectCashCurrency,
                        onSymbolChange = ledgerViewModel::onSymbolInputChanged,
                        onDateChange = { value -> ledgerViewModel.updateDraft { draft -> draft.copy(tradeDate = value) } },
                        onPriceChange = { value -> ledgerViewModel.updateDraft { draft -> draft.copy(priceLabel = value) } },
                        onQuantityChange = { value -> ledgerViewModel.updateDraft { draft -> draft.copy(quantityLabel = value) } },
                        onCommissionChange = ledgerViewModel::updateTradeCommission,
                        onTaxChange = ledgerViewModel::updateTradeTax,
                        onRecalculateFees = ledgerViewModel::recalculateTradeFees,
                        onNoteChange = { value -> ledgerViewModel.updateDraft { draft -> draft.copy(note = value) } },
                        onDeleteTradeClick = uiState.editingTransactionId?.let { transactionId ->
                            {
                                ledgerViewModel.deleteTrade(transactionId)
                                navController.popBackStack()
                            }
                        },
                        onSubmit = {
                            val isEditingSession = uiState.editingTransactionId != null
                            coroutineScope.launch {
                                if (ledgerViewModel.submitTrade()) {
                                    if (isEditingSession) {
                                        navController.navigate(Routes.Transactions) {
                                            popUpTo(Routes.Transactions) {
                                                inclusive = false
                                            }
                                            launchSingleTop = true
                                        }
                                    } else {
                                        navController.navigate(Routes.Transactions) {
                                            popUpTo(Routes.Operations) {
                                                inclusive = false
                                            }
                                            launchSingleTop = true
                                        }
                                    }
                                }
                            }
                        },
                    )
                }

                composable(Routes.Settings) {
                    SettingsRoute(
                        selectedPlatform = uiState.selectedPlatform,
                        selectedPlatformFeePlan = uiState.selectedPlatformFeePlan,
                        onPlatformFeePlanSelected = ledgerViewModel::selectPlatformFeePlan,
                        zhuoruiPromoConfig = uiState.zhuoruiPromoConfig,
                        onZhuoruiPromoChange = ledgerViewModel::updateZhuoruiPromoConfig,
                        onSaveZhuoruiPromo = ledgerViewModel::saveZhuoruiPromoConfig,
                        alibabaBailianApiKey = uiState.alibabaBailianApiKey,
                        zhuoruiPdfImportMode = uiState.zhuoruiPdfImportMode,
                        visionImportModel = uiState.visionImportModel,
                        textImportModel = uiState.textImportModel,
                        visionApiBaseUrl = uiState.visionApiBaseUrl,
                        onAlibabaBailianApiKeyChange = ledgerViewModel::updateAlibabaBailianApiKey,
                        onZhuoruiPdfImportModeChange = ledgerViewModel::updateZhuoruiPdfImportMode,
                        onVisionImportModelChange = ledgerViewModel::updateVisionImportModel,
                        onTextImportModelChange = ledgerViewModel::updateTextImportModel,
                        onVisionApiBaseUrlChange = ledgerViewModel::updateVisionApiBaseUrl,
                        onPlatformClick = { coroutineScope.launch { drawerState.open() } },
                        onBackClick = { navController.popBackStack() },
                    )
                }

                composable(Routes.FullRanking) {
                    FullRankingRoute(
                        analysis = uiState.profitAnalysis,
                        displayCurrency = uiState.displayCurrency,
                        exchangeRates = uiState.exchangeRates,
                        onBack = { navController.popBackStack() },
                        onSecurityClick = { symbol, market ->
                            navController.navigate(Routes.stockDetail(symbol, market))
                        },
                        onDestinationSelected = { destination ->
                            when (destination) {
                                TopLevelDestination.HOLDINGS -> navController.navigate(Routes.Holdings) {
                                    launchSingleTop = true
                                    popUpTo(Routes.Holdings) { saveState = true }
                                }
                                TopLevelDestination.ANALYSIS -> navController.navigate(Routes.Analysis) {
                                    launchSingleTop = true
                                    popUpTo(Routes.Holdings) { saveState = true }
                                }
                                TopLevelDestination.OPERATIONS -> navController.navigate(Routes.Operations) {
                                    launchSingleTop = true
                                    popUpTo(Routes.Holdings) { saveState = true }
                                }
                                TopLevelDestination.TRANSACTIONS -> navController.navigate(Routes.Transactions) {
                                    launchSingleTop = true
                                    popUpTo(Routes.Holdings) { saveState = true }
                                }
                            }
                        },
                    )
                }

                composable(
                    route = "${Routes.StockDetail}/{${Routes.SymbolArg}}/{${Routes.MarketArg}}",
                    arguments = listOf(
                        navArgument(Routes.SymbolArg) { type = NavType.StringType },
                        navArgument(Routes.MarketArg) { type = NavType.StringType },
                    ),
                ) { backStackEntry ->
                    val symbol = backStackEntry.arguments?.getString(Routes.SymbolArg).orEmpty()
                    val market = backStackEntry.arguments?.getString(Routes.MarketArg).orEmpty()
                    StockDetailRoute(
                        symbol = symbol,
                        marketLabel = market,
                        analysis = uiState.profitAnalysis,
                        displayCurrency = uiState.displayCurrency,
                        onBack = { navController.popBackStack() },
                        onDestinationSelected = { destination ->
                            when (destination) {
                                TopLevelDestination.HOLDINGS -> navController.navigate(Routes.Holdings) {
                                    launchSingleTop = true
                                    popUpTo(Routes.Holdings) { saveState = true }
                                }
                                TopLevelDestination.ANALYSIS -> navController.navigate(Routes.Analysis) {
                                    launchSingleTop = true
                                    popUpTo(Routes.Holdings) { saveState = true }
                                }
                                TopLevelDestination.OPERATIONS -> navController.navigate(Routes.Operations) {
                                    launchSingleTop = true
                                    popUpTo(Routes.Holdings) { saveState = true }
                                }
                                TopLevelDestination.TRANSACTIONS -> navController.navigate(Routes.Transactions) {
                                    launchSingleTop = true
                                    popUpTo(Routes.Holdings) { saveState = true }
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
    visibilityOptions: List<PlatformVisibilityUiModel>,
    onSelect: (BrokerPlatform?) -> Unit,
    onPlatformVisibilityChange: (BrokerPlatform, Boolean) -> Unit,
    onClose: () -> Unit = {},
) {
    var showVisibilitySettings by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("交易平台", color = ForegroundPrimary, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text(
                text = "关闭",
                color = ForegroundMuted,
                fontSize = 14.sp,
                modifier = Modifier
                    .clickable { onClose() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Text("侧边栏切换后，持仓、盈亏和流水会同步显示对应平台的数据。", color = ForegroundSecondary, fontSize = 13.sp)
        if (!showVisibilitySettings) {
            Text("当前显示", color = ForegroundMuted, fontSize = 12.sp)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = SurfaceSecondary,
                        shape = RoundedCornerShape(22.dp),
                    )
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (option.isSelected) BackgroundPrimary else SurfaceSecondary,
                                shape = RoundedCornerShape(18.dp),
                            )
                            .clickable { onSelect(option.platform) }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (option.platform != null) {
                            PlatformLogoBadge(
                                platform = option.platform,
                                modifier = Modifier.size(40.dp),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(BackgroundPrimary, RoundedCornerShape(12.dp))
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("汇", color = ForegroundPrimary, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(option.label, color = ForegroundPrimary, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                            Text(option.totalAssetsLabel, color = ForegroundMuted, fontSize = 12.sp)
                        }
                        if (option.isSelected) {
                            Text("当前", color = ForegroundPrimary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = SurfaceSecondary,
                    shape = RoundedCornerShape(22.dp),
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showVisibilitySettings = !showVisibilitySettings }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("显示设置", color = ForegroundPrimary, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Text("管理侧边栏展示的平台", color = ForegroundSecondary, fontSize = 12.sp)
                }
                Icon(
                    imageVector = if (showVisibilitySettings) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (showVisibilitySettings) "收起显示设置" else "展开显示设置",
                    tint = ForegroundMuted,
                    modifier = Modifier.size(20.dp),
                )
            }

            if (showVisibilitySettings) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("这里只影响侧边栏展示和录入页的平台选项，不会删除任何交易数据。至少保留一个平台。", color = ForegroundSecondary, fontSize = 12.sp)

                    visibilityOptions.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = BackgroundPrimary,
                                    shape = RoundedCornerShape(16.dp),
                                )
                                .clickable {
                                    onPlatformVisibilityChange(option.platform, !option.isEnabled)
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PlatformLogoBadge(
                                platform = option.platform,
                                modifier = Modifier.size(34.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(option.label, color = ForegroundPrimary, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                Text(option.totalAssetsLabel, color = ForegroundMuted, fontSize = 12.sp)
                            }
                            Switch(
                                checked = option.isEnabled,
                                onCheckedChange = { enabled ->
                                    onPlatformVisibilityChange(option.platform, enabled)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
