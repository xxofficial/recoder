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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.recoder.stockledger.StockLedgerApplication
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
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
import com.recoder.stockledger.data.local.LedgerEntity
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
    ledgerViewModel: LedgerViewModel = viewModel(
        factory = (LocalContext.current.applicationContext as StockLedgerApplication)
            .container
            .ledgerViewModelFactory,
    ),
) {
    val navController = rememberNavController()
    val uiState by ledgerViewModel.uiState.collectAsStateWithLifecycle()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerGesturesEnabled = currentRoute != Routes.FullRanking &&
        currentRoute?.startsWith("${Routes.StockDetail}/") != true &&
        drawerState.isOpen
    val coroutineScope = rememberCoroutineScope()
    val activeLedger = uiState.ledgers.firstOrNull { it.id == uiState.selectedLedgerId }
    val activeLedgerType = activeLedger?.type.orEmpty()
    val activeLedgerPartners = activeLedger?.partners.orEmpty()
    var showExportDialog by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }
    var selectedLedgerIdsForExport by remember { mutableStateOf(emptySet<Long>()) }
    var selectedPlatformsForExport by remember { mutableStateOf(emptySet<String>()) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            ledgerViewModel.exportBackup(
                uri = uri,
                selectedLedgerIds = selectedLedgerIdsForExport.toList(),
                selectedPlatforms = selectedPlatformsForExport.toList()
            )
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            ledgerViewModel.parseBackupFile(uri)
        }
    }
    var pendingPdfImportPlatform by remember { mutableStateOf<BrokerPlatform?>(null) }
    var analysisRange by rememberSaveable { mutableStateOf(AdvancedProfitRange.THIS_MONTH) }
    var analysisCustomStart by rememberSaveable { mutableStateOf("") }
    var analysisCustomEnd by rememberSaveable { mutableStateOf("") }
    val pdfImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            pendingPdfImportPlatform?.let { platform ->
                ledgerViewModel.importStatementPdfs(uris, platform)
            }
        }
    }
    LaunchedEffect(drawerGesturesEnabled) {
        if (!drawerGesturesEnabled && drawerState.isOpen) {
            drawerState.close()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerGesturesEnabled,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.fillMaxWidth(0.78f),
            ) {
                PlatformDrawerContent(
                    ledgers = uiState.ledgers,
                    selectedLedgerId = uiState.selectedLedgerId,
                    options = uiState.managedPlatforms,
                    visibilityOptions = uiState.platformVisibilityOptions,
                    onSelectLedger = ledgerViewModel::switchLedger,
                    onCreateLedger = ledgerViewModel::createLedger,
                    onDeleteLedger = ledgerViewModel::deleteLedger,
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
                        activeLedgerType = activeLedgerType,
                        partnerContributions = uiState.partnerContributions,
                        selectedPartner = uiState.selectedPartnerPerspective,
                        onPartnerClick = ledgerViewModel::selectPartnerPerspective,
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
                        selectedRange = analysisRange,
                        customStart = analysisCustomStart,
                        customEnd = analysisCustomEnd,
                        onSelectedRangeChange = { analysisRange = it },
                        onCustomStartChange = { analysisCustomStart = it },
                        onCustomEndChange = { analysisCustomEnd = it },
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
                        onTransferClick = {
                            showTransferDialog = true
                        },
                        onInterestClick = {
                            ledgerViewModel.openTradeEntry(TradeType.INTEREST)
                            navController.navigate(Routes.tradeEntry(TradeType.INTEREST))
                        },
                        onExportBackupClick = {
                            selectedLedgerIdsForExport = uiState.ledgers.map { it.id }.toSet()
                            val platformsWithTx = ledgerViewModel.transactionSnapshot.value.map { it.platform }.filter { it.isNotBlank() }.toSet()
                            val currentPlatform = uiState.selectedPlatform?.name
                            selectedPlatformsForExport = if (currentPlatform != null) {
                                setOf(currentPlatform)
                            } else {
                                platformsWithTx
                            }
                            showExportDialog = true
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
                        pdfImportStatusMessage = uiState.pdfImportStatusMessage,
                        pdfImportProgressFraction = uiState.pdfImportProgressFraction,
                        hasFailedPdfImports = uiState.hasFailedPdfImports,
                        onImportPdfStatements = { platform ->
                            // Clear status before launching picker
                            ledgerViewModel.clearPdfImportStatus()
                            pendingPdfImportPlatform = platform
                            pdfImportLauncher.launch(arrayOf("application/pdf"))
                        },
                        onRetryFailedPdfImport = { platform ->
                            ledgerViewModel.retryFailedPdfImport(platform)
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
                        ledgers = uiState.ledgers,
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
                        ledgers = uiState.ledgers,
                        activeLedgerId = uiState.selectedLedgerId,
                        onMoveTransactionsToLedger = ledgerViewModel::moveSelectedTransactionsToLedger,
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
                        onTimeChange = { value -> ledgerViewModel.updateDraft { draft -> draft.copy(tradeTime = value) } },
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
                        activeLedgerType = activeLedgerType,
                        activeLedgerPartners = activeLedgerPartners,
                        onInvestorSelected = ledgerViewModel::updateTradeInvestorName,
                        onAssetTypeSelected = { value -> ledgerViewModel.updateDraft { draft -> draft.copy(assetType = value) } },
                        onOptionUnderlyingSymbolChanged = ledgerViewModel::onOptionUnderlyingSymbolChanged,
                        onOptionExpiryDateChanged = { value -> ledgerViewModel.updateDraft { draft -> draft.copy(optionExpiryDate = value) } },
                        onOptionTypeSelected = { value -> ledgerViewModel.updateDraft { draft -> draft.copy(optionType = value) } },
                        onOptionStrikePriceChanged = { value -> ledgerViewModel.updateDraft { draft -> draft.copy(optionStrikePriceLabel = value) } },
                        onOptionUnderlyingSuggestionSelected = ledgerViewModel::selectOptionUnderlyingSuggestion,
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
                        statementPdfPassword = uiState.statementPdfPassword,
                        pdfImportMode = uiState.pdfImportMode,
                        textImportModel = uiState.textImportModel,
                        llmApiBaseUrl = uiState.llmApiBaseUrl,
                        onAlibabaBailianApiKeyChange = ledgerViewModel::updateAlibabaBailianApiKey,
                        onStatementPdfPasswordChange = ledgerViewModel::updateStatementPdfPassword,
                        onPdfImportModeChange = ledgerViewModel::updatePdfImportMode,
                        onTextImportModelChange = ledgerViewModel::updateTextImportModel,
                        onLlmApiBaseUrlChange = ledgerViewModel::updateLlmApiBaseUrl,
                        onPlatformClick = { coroutineScope.launch { drawerState.open() } },
                        onBackClick = { navController.popBackStack() },
                    )
                }

                composable(Routes.FullRanking) {
                    FullRankingRoute(
                        analysis = uiState.profitAnalysis,
                        displayCurrency = uiState.displayCurrency,
                        exchangeRates = uiState.exchangeRates,
                        selectedRange = analysisRange,
                        customStart = analysisCustomStart,
                        customEnd = analysisCustomEnd,
                        onSelectedRangeChange = { analysisRange = it },
                        onCustomStartChange = { analysisCustomStart = it },
                        onCustomEndChange = { analysisCustomEnd = it },
                        onBack = { navController.popBackStack() },
                        onSecurityClick = { symbol, market ->
                            navController.navigate(Routes.stockDetail(symbol, market))
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
                    )
                }
            }

            if (showTransferDialog) {
                PlatformTransferDialog(
                    enabledPlatforms = BrokerPlatform.entries.filter { it.isConfigurable },
                    getCashBalance = ledgerViewModel::getCashBalance,
                    onDismiss = { showTransferDialog = false },
                    onConfirm = { isStock, symbol, name, market, quantity, amount, currency, sourcePlatform, targetPlatform, date, time ->
                        ledgerViewModel.performPlatformTransfer(
                            isStock = isStock,
                            symbol = symbol,
                            name = name,
                            market = market,
                            quantity = quantity,
                            amount = amount,
                            currency = currency,
                            sourcePlatform = sourcePlatform,
                            targetPlatform = targetPlatform,
                            tradeDate = date,
                            tradeTime = time,
                        )
                    }
                )
            }

            if (showExportDialog) {
                AlertDialog(
                    onDismissRequest = { showExportDialog = false },
                    title = { Text("导出备份设置", color = ForegroundPrimary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        ) {
                            Text("选择要导出的账本：", color = ForegroundSecondary, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                            uiState.ledgers.forEach { ledger ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedLedgerIdsForExport = if (selectedLedgerIdsForExport.contains(ledger.id)) {
                                                selectedLedgerIdsForExport - ledger.id
                                            } else {
                                                selectedLedgerIdsForExport + ledger.id
                                            }
                                        }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Checkbox(
                                        checked = selectedLedgerIdsForExport.contains(ledger.id),
                                        onCheckedChange = null,
                                        colors = CheckboxDefaults.colors(checkedColor = ForegroundPrimary, uncheckedColor = ForegroundMuted)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(ledger.name, color = ForegroundPrimary, fontSize = 14.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text("选择要导出的交易平台：", color = ForegroundSecondary, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                            val allPlatforms = remember {
                                BrokerPlatform.entries.map { it.name }
                            }
                            allPlatforms.forEach { platformName ->
                                val platform = BrokerPlatform.entries.firstOrNull { it.name == platformName } ?: return@forEach
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedPlatformsForExport = if (selectedPlatformsForExport.contains(platformName)) {
                                                selectedPlatformsForExport - platformName
                                            } else {
                                                selectedPlatformsForExport + platformName
                                            }
                                        }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Checkbox(
                                        checked = selectedPlatformsForExport.contains(platformName),
                                        onCheckedChange = null,
                                        colors = CheckboxDefaults.colors(checkedColor = ForegroundPrimary, uncheckedColor = ForegroundMuted)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(platform.label, color = ForegroundPrimary, fontSize = 14.sp)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                if (selectedLedgerIdsForExport.isNotEmpty() && selectedPlatformsForExport.isNotEmpty()) {
                                    showExportDialog = false
                                    val filename = "stock-ledger-backup-${
                                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                                    }.json"
                                    exportLauncher.launch(filename)
                                }
                            },
                            enabled = selectedLedgerIdsForExport.isNotEmpty() && selectedPlatformsForExport.isNotEmpty()
                        ) {
                            Text("确认导出", color = ForegroundPrimary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { showExportDialog = false }) {
                            Text("取消", color = ForegroundSecondary)
                        }
                    }
                )
            }

            if (uiState.showImportDialog && uiState.parsedBackupData != null) {
                val backup = uiState.parsedBackupData!!
                val importedPlatforms = remember(backup) {
                    backup.transactions.map { it.platform }.distinct().sorted()
                }
                var selectedLedgerIdsForImport by remember(backup) {
                    mutableStateOf(backup.ledgers.map { it.id }.toSet())
                }
                var selectedPlatformsForImport by remember(backup) {
                    mutableStateOf(importedPlatforms.toSet())
                }

                AlertDialog(
                    onDismissRequest = {
                        ledgerViewModel.showImportDialog.value = false
                        ledgerViewModel.parsedBackupData.value = null
                    },
                    title = { Text("确认导入备份", color = ForegroundPrimary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        ) {
                            Text("选择要导入的账本：", color = ForegroundSecondary, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                            backup.ledgers.forEach { ledger ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedLedgerIdsForImport = if (selectedLedgerIdsForImport.contains(ledger.id)) {
                                                selectedLedgerIdsForImport - ledger.id
                                            } else {
                                                selectedLedgerIdsForImport + ledger.id
                                            }
                                        }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Checkbox(
                                        checked = selectedLedgerIdsForImport.contains(ledger.id),
                                        onCheckedChange = null,
                                        colors = CheckboxDefaults.colors(checkedColor = ForegroundPrimary, uncheckedColor = ForegroundMuted)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(ledger.name, color = ForegroundPrimary, fontSize = 14.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text("选择要导入的交易平台：", color = ForegroundSecondary, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                            if (importedPlatforms.isEmpty()) {
                                Text("无交易平台记录", color = ForegroundMuted, fontSize = 13.sp)
                            } else {
                                importedPlatforms.forEach { platformName ->
                                    val platform = BrokerPlatform.entries.firstOrNull { it.name == platformName }
                                    val label = platform?.label ?: platformName
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedPlatformsForImport = if (selectedPlatformsForImport.contains(platformName)) {
                                                    selectedPlatformsForImport - platformName
                                                } else {
                                                    selectedPlatformsForImport + platformName
                                                }
                                            }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Checkbox(
                                            checked = selectedPlatformsForImport.contains(platformName),
                                            onCheckedChange = null,
                                            colors = CheckboxDefaults.colors(checkedColor = ForegroundPrimary, uncheckedColor = ForegroundMuted)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(label, color = ForegroundPrimary, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                ledgerViewModel.confirmImport(
                                    selectedLedgerIds = selectedLedgerIdsForImport.toList(),
                                    selectedPlatforms = selectedPlatformsForImport.toList()
                                )
                            },
                            enabled = selectedLedgerIdsForImport.isNotEmpty() && (importedPlatforms.isEmpty() || selectedPlatformsForImport.isNotEmpty())
                        ) {
                            Text("确认导入", color = ForegroundPrimary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                ledgerViewModel.showImportDialog.value = false
                                ledgerViewModel.parsedBackupData.value = null
                            }
                        ) {
                            Text("取消", color = ForegroundSecondary)
                        }
                    }
                )
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
    ledgers: List<LedgerEntity>,
    selectedLedgerId: Long,
    options: List<ManagedPlatformUiModel>,
    visibilityOptions: List<PlatformVisibilityUiModel>,
    onSelectLedger: (Long) -> Unit,
    onCreateLedger: (String, String, String, String) -> Unit,
    onDeleteLedger: (Long) -> Unit,
    onSelect: (BrokerPlatform?) -> Unit,
    onPlatformVisibilityChange: (BrokerPlatform, Boolean) -> Unit,
    onClose: () -> Unit = {},
) {
    var showVisibilitySettings by remember { mutableStateOf(false) }
    var showCreateLedgerDialog by remember { mutableStateOf(false) }
    var ledgerToDelete by remember { mutableStateOf<LedgerEntity?>(null) }

    if (showCreateLedgerDialog) {
        var ledgerName by remember { mutableStateOf("") }
        var ledgerType by remember { mutableStateOf("PERSONAL") }
        var ledgerDesc by remember { mutableStateOf("") }
        var ledgerPartners by remember { mutableStateOf("") }
        var showError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCreateLedgerDialog = false },
            title = { Text("新建账本", color = ForegroundPrimary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    androidx.compose.material3.OutlinedTextField(
                        value = ledgerName,
                        onValueChange = { ledgerName = it; showError = false },
                        label = { Text("账本名称") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text("账本类型", color = ForegroundSecondary, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("PERSONAL" to "个人", "JOINT" to "合资").forEach { (typeVal, typeLbl) ->
                            val selected = ledgerType == typeVal
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        color = if (selected) BackgroundPrimary else SurfaceSecondary,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { ledgerType = typeVal }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = typeLbl,
                                    color = if (selected) ForegroundPrimary else ForegroundSecondary,
                                    fontSize = 14.sp,
                                    fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                                )
                            }
                        }
                    }
                    if (ledgerType == "JOINT") {
                        androidx.compose.material3.OutlinedTextField(
                            value = ledgerPartners,
                            onValueChange = { ledgerPartners = it },
                            label = { Text("合伙人/出资人姓名 (英文或中文逗号分隔)") },
                            placeholder = { Text("如: 张三, 李四") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    androidx.compose.material3.OutlinedTextField(
                        value = ledgerDesc,
                        onValueChange = { ledgerDesc = it },
                        label = { Text("账本备注 (选填)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (showError) {
                        Text("账本名称及合伙人姓名不能为空", color = androidx.compose.ui.graphics.Color(0xFFE53935), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        if (ledgerName.isBlank() || (ledgerType == "JOINT" && ledgerPartners.isBlank())) {
                            showError = true
                        } else {
                            onCreateLedger(ledgerName.trim(), ledgerType, ledgerDesc.trim(), ledgerPartners.trim())
                            showCreateLedgerDialog = false
                        }
                    }
                ) {
                    Text("创建", color = ForegroundPrimary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showCreateLedgerDialog = false }) {
                    Text("取消", color = ForegroundSecondary)
                }
            }
        )
    }

    ledgerToDelete?.let { ledger: LedgerEntity ->
        AlertDialog(
            onDismissRequest = { ledgerToDelete = null },
            title = { Text("确认删除账本", color = ForegroundPrimary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
            text = { Text("此操作将永久删除账本 [${ledger.name}] 及其下所有的交易记录，且无法恢复！确认删除吗？", color = ForegroundSecondary) },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        onDeleteLedger(ledger.id)
                        ledgerToDelete = null
                    }
                ) {
                    Text("确认删除", color = androidx.compose.ui.graphics.Color(0xFFE53935), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { ledgerToDelete = null }) {
                    Text("取消", color = ForegroundSecondary)
                }
            }
        )
    }

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
            Text("我的账本", color = ForegroundPrimary, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Box(
                modifier = Modifier
                    .background(color = SurfaceSecondary, shape = RoundedCornerShape(12.dp))
                    .clickable { showCreateLedgerDialog = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("+ 新建账本", color = ForegroundPrimary, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            }
        }

        Text("在不同账本间切换以进行资产隔离。合资账本支持资金比例和收益分摊计算。", color = ForegroundSecondary, fontSize = 13.sp)

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
            ledgers.forEach { ledger ->
                val isSelected = ledger.id == selectedLedgerId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (isSelected) BackgroundPrimary else SurfaceSecondary,
                            shape = RoundedCornerShape(18.dp),
                        )
                        .clickable { 
                            onSelectLedger(ledger.id)
                            onClose() 
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = ledger.name,
                                color = ForegroundPrimary,
                                fontSize = 15.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                            )
                            val typeLabel = when (ledger.type) {
                                "PERSONAL" -> "个人"
                                "JOINT" -> "合资"
                                "MANAGED" -> "代操"
                                else -> ledger.type
                            }
                            val typeBgColor = when (ledger.type) {
                                "PERSONAL" -> androidx.compose.ui.graphics.Color(0xFF2A82E4).copy(alpha = 0.15f)
                                "JOINT" -> androidx.compose.ui.graphics.Color(0xFFE5A93B).copy(alpha = 0.15f)
                                "MANAGED" -> androidx.compose.ui.graphics.Color(0xFF9C27B0).copy(alpha = 0.15f)
                                else -> androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.15f)
                            }
                            val typeTextColor = when (ledger.type) {
                                "PERSONAL" -> androidx.compose.ui.graphics.Color(0xFF2A82E4)
                                "JOINT" -> androidx.compose.ui.graphics.Color(0xFFE5A93B)
                                "MANAGED" -> androidx.compose.ui.graphics.Color(0xFF9C27B0)
                                else -> androidx.compose.ui.graphics.Color.Gray
                            }
                            Box(
                                modifier = Modifier
                                    .background(color = typeBgColor, shape = RoundedCornerShape(8.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = typeLabel,
                                    color = typeTextColor,
                                    fontSize = 10.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                            }
                        }
                        if (ledger.description.isNotBlank()) {
                            Text(
                                text = ledger.description,
                                color = ForegroundMuted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isSelected) {
                            Text("当前", color = ForegroundPrimary, fontSize = 12.sp)
                        }
                        if (ledger.id != 1L) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "删除账本",
                                tint = androidx.compose.ui.graphics.Color(0xFFE53935),
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { ledgerToDelete = ledger }
                            )
                        }
                    }
                }
            }
        }

        // 分割线
        androidx.compose.material3.HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = SurfaceSecondary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("交易平台", color = ForegroundPrimary, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
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
                            .clickable { 
                                onSelect(option.platform) 
                                onClose()
                            }
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
