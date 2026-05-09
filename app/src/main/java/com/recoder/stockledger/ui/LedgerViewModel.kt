package com.recoder.stockledger.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.recoder.stockledger.StockLedgerApplication
import com.recoder.stockledger.StockLedgerPreferences
import com.recoder.stockledger.data.BrokerPlatform
import com.recoder.stockledger.data.DisplayCurrency
import com.recoder.stockledger.data.ExchangeRateOrigin
import com.recoder.stockledger.data.ExchangeRates
import com.recoder.stockledger.data.FeeEstimateStatus
import com.recoder.stockledger.data.HoldingUiModel
import com.recoder.stockledger.data.ImportSourceChannel
import com.recoder.stockledger.data.Market
import com.recoder.stockledger.data.MarketFilter
import com.recoder.stockledger.data.ManagedPlatformUiModel
import com.recoder.stockledger.data.PlatformFeePlanUiModel
import com.recoder.stockledger.data.PlatformVisibilityUiModel
import com.recoder.stockledger.data.PortfolioSummary
import com.recoder.stockledger.data.PriceTrend
import com.recoder.stockledger.data.ProfitAnalysisPointUiModel
import com.recoder.stockledger.data.ProfitAnalysisUiModel
import com.recoder.stockledger.data.SecurityProfitAnalysisUiModel
import com.recoder.stockledger.data.SecurityProfitPointUiModel
import com.recoder.stockledger.data.RefreshState
import com.recoder.stockledger.data.SampleData
import com.recoder.stockledger.data.SecuritySuggestionUiModel
import com.recoder.stockledger.data.SellCandidateUiModel
import com.recoder.stockledger.data.SymbolLookupState
import com.recoder.stockledger.data.SymbolLookupUiModel
import com.recoder.stockledger.data.TradeFeeEstimator
import com.recoder.stockledger.data.TradeFeeEstimateContext
import com.recoder.stockledger.data.TradeFormState
import com.recoder.stockledger.data.TradeFeePlanOptionUiModel
import com.recoder.stockledger.data.TradeType
import com.recoder.stockledger.data.TransactionFilter
import com.recoder.stockledger.data.TransactionSection
import com.recoder.stockledger.data.ZhuoruiPromoConfig
import com.recoder.stockledger.data.TransactionUiModel
import com.recoder.stockledger.data.ZhuoruiEmailManualSyncOptions
import com.recoder.stockledger.data.ZhuoruiEmailSyncConfig
import com.recoder.stockledger.data.PdfImportMode
import com.recoder.stockledger.data.rateToCny
import com.recoder.stockledger.data.local.QuoteSnapshotEntity
import com.recoder.stockledger.data.local.TransactionEntity
import com.recoder.stockledger.importer.ZhuoruiEmailSyncWorker
import com.recoder.stockledger.data.repository.DefaultLedgerRepository
import com.recoder.stockledger.data.repository.HistoricalClosePoint
import com.recoder.stockledger.data.repository.ImportedBackup
import com.recoder.stockledger.data.repository.SecurityLookupResult
import com.recoder.stockledger.data.repository.TradeDraftInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.text.DecimalFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue

private const val DEFAULT_REFRESH_MESSAGE = "打开应用会自动刷新一次行情，手动下拉 1 分钟内仅可触发一次"
private const val US_TIMEZONE_CUTOFF = "06:00"

private data class ResolvedSecurity(
    val symbol: String,
    val name: String,
    val market: Market,
)

private data class PositionComputation(
    val symbol: String,
    val name: String,
    val market: Market,
    val quantity: Int,
    val averageCost: Double,
    val remainingCost: Double,
    val realizedProfit: Double,
)

data class LedgerUiState(
    val summary: PortfolioSummary = PortfolioSummary(
        totalAssets = "¥0.00",
        totalCost = "¥0.00",
        totalCostHint = "累计入金 ¥0.00 · 累计出金 ¥0.00",
        cashBalance = "¥0.00",
        cashBalanceHint = "A股 0 只 · 港股 0 只",
        totalProfit = "+¥0.00",
        totalProfitHint = "折算收益率 +0.00%",
        dayProfit = "+¥0.00 (+0.00%)",
        holdingsValue = "¥0.00",
        commissionTotal = "¥0.00",
        taxTotal = "¥0.00",
        tradeCount = "0 笔",
        refreshState = RefreshState.IDLE,
        refreshMessage = DEFAULT_REFRESH_MESSAGE,
        refreshTimeLabel = null,
        showPullRefreshTime = false,
    ),
    val holdings: List<HoldingUiModel> = emptyList(),
    val sellCandidates: List<SellCandidateUiModel> = emptyList(),
    val transactionSections: List<TransactionSection> = emptyList(),
    val profitAnalysis: ProfitAnalysisUiModel = ProfitAnalysisUiModel(),
    val selectedTradeFilter: TransactionFilter = TransactionFilter.ALL,
    val selectedMarketFilter: MarketFilter = MarketFilter.ALL,
    val transactionKeyword: String = "",
    val transactionDateStart: String = "",
    val transactionDateEnd: String = "",
    val managedPlatforms: List<ManagedPlatformUiModel> = emptyList(),
    val platformVisibilityOptions: List<PlatformVisibilityUiModel> = emptyList(),
    val selectedPlatformFeePlan: PlatformFeePlanUiModel? = null,
    val availableTradePlatforms: List<BrokerPlatform> = emptyList(),
    val selectedPlatform: BrokerPlatform? = null,
    val draft: TradeFormState = SampleData.tradeForm(TradeType.BUY),
    val symbolLookup: SymbolLookupUiModel = SymbolLookupUiModel(),
    val symbolSuggestions: List<SecuritySuggestionUiModel> = emptyList(),
    val canSubmitTrade: Boolean = false,
    val tradeValidationMessage: String? = null,
    val editingTransactionId: Long? = null,
    val displayCurrency: DisplayCurrency = DisplayCurrency.CNY,
    val exchangeRates: ExchangeRates = ExchangeRates(),
    val backupStatusMessage: String? = null,
    val hsbcImportDraftText: String = "",
    val hsbcImportStatusMessage: String? = null,
    val zhuoruiEmailSyncConfig: ZhuoruiEmailSyncConfig = ZhuoruiEmailSyncConfig(),
    val zhuoruiEmailManualSyncOptions: ZhuoruiEmailManualSyncOptions = ZhuoruiEmailManualSyncOptions(),
    val zhuoruiEmailAutoImportEnabled: Boolean = false,
    val zhuoruiEmailSyncStatusMessage: String? = null,
    val zhuoruiPromoConfig: ZhuoruiPromoConfig = ZhuoruiPromoConfig(),
    val zhuoruiStatementPdfPassword: String = "",
    val pdfImportStatusMessage: String? = null,
    val pdfImportProgressFraction: Float? = null,
    val hasFailedPdfImports: Boolean = false,
    val alibabaBailianApiKey: String = "",
    val pdfImportMode: PdfImportMode = PdfImportMode.REGEX,
    val textImportModel: String = "",
    val llmApiBaseUrl: String = "",
    val batchSelectionMode: Boolean = false,
    val selectedTransactionIds: Set<Long> = emptySet(),
)

class LedgerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: DefaultLedgerRepository =
        (application as StockLedgerApplication).repository
    private val preferences = application.getSharedPreferences(
        StockLedgerPreferences.PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    private val tradeFilter = MutableStateFlow(TransactionFilter.ALL)
    private val marketFilter = MutableStateFlow(MarketFilter.ALL)
    private val transactionKeyword = MutableStateFlow("")
    private val transactionDateStart = MutableStateFlow<String?>(null)
    private val transactionDateEnd = MutableStateFlow<String?>(null)
    private val selectedPlatform = MutableStateFlow(loadSavedSelectedPlatform())
    private val enabledPlatforms = MutableStateFlow(loadEnabledPlatforms())
    private val platformFeePlanSelections = MutableStateFlow(loadPlatformFeePlanSelections())
    private val zhuoruiPromoConfig = MutableStateFlow(loadZhuoruiPromoConfig())
    private val transactionSnapshot = MutableStateFlow<List<TransactionEntity>>(emptyList())
    private val draft = MutableStateFlow(SampleData.tradeForm(TradeType.BUY))
    private val symbolLookup = MutableStateFlow(SymbolLookupUiModel())
    private val symbolSuggestions = MutableStateFlow<List<SecuritySuggestionUiModel>>(emptyList())
    private val refreshState = MutableStateFlow(RefreshState.IDLE)
    private val refreshNote = MutableStateFlow(DEFAULT_REFRESH_MESSAGE)
    private val lastRefreshTimestamp = MutableStateFlow(0L)
    private val showPullRefreshTime = MutableStateFlow(false)
    private val displayCurrency = MutableStateFlow(loadSavedDisplayCurrency())
    private val editingTrade = MutableStateFlow<EditingTradeSession?>(null)
    private val backupStatusMessage = MutableStateFlow<String?>(null)
    private val hsbcImportDraftText = MutableStateFlow("")
    private val hsbcImportStatusMessage = MutableStateFlow<String?>(null)
    private val zhuoruiEmailSyncConfig = MutableStateFlow(loadZhuoruiEmailSyncConfig())
    private val zhuoruiEmailManualSyncOptions = MutableStateFlow(ZhuoruiEmailManualSyncOptions())
    private val zhuoruiEmailAutoImportEnabled = MutableStateFlow(loadZhuoruiEmailAutoImportEnabled())
    private val zhuoruiEmailSyncStatusMessage = MutableStateFlow(loadZhuoruiEmailSyncStatusMessage())
    private val zhuoruiStatementPdfPassword = MutableStateFlow(loadZhuoruiStatementPdfPassword())
    private val pdfImportStatusMessage = MutableStateFlow<String?>(null)
    private val pdfImportProgressFraction = MutableStateFlow<Float?>(null)
    private val failedPdfUris = MutableStateFlow<List<Uri>>(emptyList())
    private val alibabaBailianApiKey = MutableStateFlow(loadAlibabaBailianApiKey())
    private val pdfImportMode = MutableStateFlow(loadPdfImportMode())
    private val textImportModel = MutableStateFlow(loadTextImportModel())
    private val llmApiBaseUrl = MutableStateFlow(loadLlmApiBaseUrl())
    private val batchSelectionMode = MutableStateFlow(false)
    private val selectedTransactionIds = MutableStateFlow<Set<Long>>(emptySet())

    private var lastManualRefreshTriggeredAt: Long = 0L
    private var symbolLookupJob: Job? = null

    private val portfolio = combine(
        repository.transactions,
        repository.quotes,
        repository.exchangeRates,
        selectedPlatform,
    ) { transactions, quotes, exchangeRates, activePlatform ->
        computePortfolio(
            filterTransactionsByPlatform(transactions, activePlatform),
            quotes,
            exchangeRates,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyPortfolioComputation(),
    )

    private val draftReferencePortfolio = combine(
        repository.transactions,
        repository.quotes,
        repository.exchangeRates,
        editingTrade,
        draft,
    ) { transactions, quotes, exchangeRates, currentEditingTrade, currentDraft ->
        val referenceTransactions = currentEditingTrade?.let { session ->
            transactions.filterNot { it.id == session.transactionId }
        } ?: transactions
        computePortfolio(
            filterTransactionsByPlatform(referenceTransactions, currentDraft.platform),
            quotes,
            exchangeRates,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyPortfolioComputation(),
    )

    private val filters = combine(
        tradeFilter,
        marketFilter,
        transactionKeyword,
        transactionDateStart,
        transactionDateEnd,
    ) { selectedTradeFilter, selectedMarketFilter, keyword, startDate, endDate ->
        TransactionFilters(
            tradeFilter = selectedTradeFilter,
            marketFilter = selectedMarketFilter,
            keyword = keyword,
            startDate = startDate,
            endDate = endDate,
        )
    }

    private val refreshMeta = combine(
        refreshState,
        lastRefreshTimestamp,
        refreshNote,
        showPullRefreshTime,
    ) { refresh, refreshedAt, note, shouldShowPullRefreshTime ->
        RefreshMeta(
            refresh = refresh,
            refreshedAt = refreshedAt,
            note = note,
            showPullRefreshTime = shouldShowPullRefreshTime,
        )
    }

    private val analysisInputs = combine(
        repository.historicalCloses,
        repository.quotes,
    ) { historicalCloses, quotes ->
        historicalCloses to quotes
    }

    private val platformSetup = combine(enabledPlatforms, selectedPlatform) { enabled, active ->
        val resolvedEnabled = BrokerPlatform.configurableEntries.filter { it in enabled }
            .ifEmpty { BrokerPlatform.configurableEntries }
        val resolvedSelection = active?.takeIf { it in resolvedEnabled }
        if (resolvedSelection != active) {
            selectedPlatform.value = resolvedSelection
            saveSelectedPlatform(resolvedSelection)
        }
        PlatformSetup(
            enabled = resolvedEnabled,
            selected = resolvedSelection,
        )
    }

    val uiState: StateFlow<LedgerUiState> = combine(
        portfolio,
        draftReferencePortfolio,
        repository.transactions,
        editingTrade,
        analysisInputs,
    ) { portfolioState, referencePortfolioState, transactions, currentEditingTrade, analysisInput ->
        val (historicalCloses, quotes) = analysisInput
        PortfolioContext(
            portfolio = portfolioState,
            draftReferencePortfolio = referencePortfolioState,
            transactions = transactions,
            editingTrade = currentEditingTrade,
            historicalCloses = historicalCloses,
            quotes = quotes,
        )
    }.combine(filters) { context, selectedFilters ->
        PortfolioTransactionsAndFilters(context = context, filters = selectedFilters)
    }.combine(draft) { upstream, tradeDraft ->
        PortfolioTransactionsFiltersAndDraft(upstream = upstream, draft = tradeDraft)
    }.combine(refreshMeta) { upstream, meta ->
        PortfolioTransactionsFiltersDraftAndRefresh(upstream = upstream, refresh = meta)
    }.combine(platformSetup) { upstream, platforms ->
        upstream to platforms
    }.combine(displayCurrency) { upstream, selectedDisplayCurrency ->
        Triple(upstream.first, upstream.second, selectedDisplayCurrency)
    }.combine(symbolLookup) { upstream, lookup ->
        val (state, platforms, selectedDisplayCurrency) = upstream
        val selectedFilters = state.upstream.upstream.filters
        val context = state.upstream.upstream.context

        // Ensure heavy computations run off the main thread
        withContext(Dispatchers.Default) {
            val platformScopedTransactions = filterTransactionsByPlatform(
                context.transactions,
                platforms.selected,
            )
            val validationMessage = validateTradeDraft(
                draft = state.upstream.draft,
                portfolio = context.draftReferencePortfolio,
                lookup = lookup,
            )
            val allTransactionsPortfolio = computePortfolio(
                transactions = context.transactions,
                quotes = context.quotes,
                exchangeRates = repository.exchangeRates.value,
            )
            val totalAssetsByPlatform = buildPlatformAssetLabels(
                transactions = context.transactions,
                quotes = context.quotes,
                exchangeRates = repository.exchangeRates.value,
                displayCurrency = selectedDisplayCurrency,
                enabledPlatforms = platforms.enabled,
                summaryPortfolio = allTransactionsPortfolio,
            )
            val availableTradePlatforms = buildList {
                addAll(platforms.enabled)
                state.upstream.draft.platform
                    .takeIf { current -> current !in platforms.enabled && current.isConfigurable }
                    ?.let(::add)
            }
            LedgerUiState(
                summary = buildSummary(
                    portfolio = context.portfolio,
                    refresh = state.refresh.refresh,
                    refreshedAt = state.refresh.refreshedAt,
                    refreshNote = state.refresh.note,
                    showPullRefreshTime = state.refresh.showPullRefreshTime,
                    displayCurrency = selectedDisplayCurrency,
                    exchangeRates = repository.exchangeRates.value,
                ),
                holdings = context.portfolio.holdings,
                sellCandidates = buildSellCandidates(context.draftReferencePortfolio.positions),
                transactionSections = buildTransactionSections(
                    transactions = platformScopedTransactions,
                    tradeFilter = selectedFilters.tradeFilter,
                    marketFilter = selectedFilters.marketFilter,
                    keyword = selectedFilters.keyword,
                    startDate = selectedFilters.startDate,
                    endDate = selectedFilters.endDate,
                ),
                profitAnalysis = buildProfitAnalysis(
                    portfolio = context.portfolio,
                    transactions = platformScopedTransactions,
                    exchangeRates = repository.exchangeRates.value,
                    historicalCloses = context.historicalCloses,
                    quotes = context.quotes,
                ),
                selectedTradeFilter = selectedFilters.tradeFilter,
                selectedMarketFilter = selectedFilters.marketFilter,
                transactionKeyword = selectedFilters.keyword,
                transactionDateStart = selectedFilters.startDate.orEmpty(),
                transactionDateEnd = selectedFilters.endDate.orEmpty(),
                managedPlatforms = buildList {
                    add(
                        ManagedPlatformUiModel(
                            platform = null,
                            label = "汇总",
                            totalAssetsLabel = totalAssetsByPlatform[null] ?: formatDisplayAmount(
                                allTransactionsPortfolio.totalAssetsCny,
                                selectedDisplayCurrency,
                                repository.exchangeRates.value,
                            ),
                            isSelected = platforms.selected == null,
                        ),
                    )
                    platforms.enabled.forEach { platform ->
                        add(
                            ManagedPlatformUiModel(
                                platform = platform,
                                label = platform.label,
                                totalAssetsLabel = totalAssetsByPlatform[platform]
                                    ?: formatDisplayAmount(0.0, selectedDisplayCurrency, repository.exchangeRates.value),
                                isSelected = platform == platforms.selected,
                            ),
                        )
                    }
                },
                platformVisibilityOptions = BrokerPlatform.configurableEntries.map { platform ->
                    PlatformVisibilityUiModel(
                        platform = platform,
                        label = platform.label,
                        totalAssetsLabel = totalAssetsByPlatform[platform]
                            ?: formatDisplayAmount(0.0, selectedDisplayCurrency, repository.exchangeRates.value),
                        isEnabled = platform in platforms.enabled,
                    )
                },
                availableTradePlatforms = availableTradePlatforms,
                selectedPlatform = platforms.selected,
                draft = state.upstream.draft,
                symbolLookup = lookup,
                canSubmitTrade = validationMessage == null,
                tradeValidationMessage = validationMessage,
                editingTransactionId = context.editingTrade?.transactionId,
                displayCurrency = selectedDisplayCurrency,
                exchangeRates = repository.exchangeRates.value,
            )
        }
    }.combine(symbolSuggestions) { state, suggestions ->
        state.copy(symbolSuggestions = suggestions)
    }.combine(platformFeePlanSelections) { state, selections ->
        state.copy(
            selectedPlatformFeePlan = buildPlatformFeePlanUiModel(
                selectedPlatform = state.selectedPlatform,
                selections = selections,
            ),
        )
    }.combine(backupStatusMessage) { state, message ->
        state.copy(backupStatusMessage = message)
    }.combine(hsbcImportDraftText) { state, value ->
        state.copy(hsbcImportDraftText = value)
    }.combine(hsbcImportStatusMessage) { state, message ->
        state.copy(hsbcImportStatusMessage = message)
    }.combine(zhuoruiEmailSyncConfig) { state, config ->
        state.copy(zhuoruiEmailSyncConfig = config)
    }.combine(zhuoruiEmailManualSyncOptions) { state, options ->
        state.copy(zhuoruiEmailManualSyncOptions = options)
    }.combine(zhuoruiEmailAutoImportEnabled) { state, enabled ->
        state.copy(zhuoruiEmailAutoImportEnabled = enabled)
    }.combine(zhuoruiEmailSyncStatusMessage) { state, message ->
        state.copy(zhuoruiEmailSyncStatusMessage = message)
    }.combine(zhuoruiPromoConfig) { state, promo ->
        state.copy(zhuoruiPromoConfig = promo)
    }.combine(zhuoruiStatementPdfPassword) { state, password ->
        state.copy(zhuoruiStatementPdfPassword = password)
    }.combine(pdfImportStatusMessage) { state, message ->
        state.copy(pdfImportStatusMessage = message)
    }.combine(pdfImportProgressFraction) { state, fraction ->
        state.copy(pdfImportProgressFraction = fraction)
    }.combine(failedPdfUris) { state, failed ->
        state.copy(hasFailedPdfImports = failed.isNotEmpty())
    }.combine(alibabaBailianApiKey) { state, key ->
        state.copy(alibabaBailianApiKey = key)
    }.combine(pdfImportMode) { state, mode ->
        state.copy(pdfImportMode = mode)
    }.combine(textImportModel) { state, model ->
        state.copy(textImportModel = model)
    }.combine(llmApiBaseUrl) { state, url ->
        state.copy(llmApiBaseUrl = url)
    }.combine(batchSelectionMode) { state, batchMode ->
        state.copy(batchSelectionMode = batchMode)
    }.combine(selectedTransactionIds) { state, selectedIds ->
        state.copy(selectedTransactionIds = selectedIds)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LedgerUiState(),
    )

    init {
        reconcileZhuoruiEmailAutoSync()
        viewModelScope.launch {
            repository.transactions.collect { transactions ->
                transactionSnapshot.value = transactions
            }
        }
        viewModelScope.launch {
            repository.purgeLegacySeedData()
            repository.seedIfEmpty()
            repository.refreshExchangeRates()
            val latestTransactions = repository.transactions.first()
            if (latestTransactions.isEmpty()) {
                refreshState.value = RefreshState.IDLE
                refreshNote.value = "暂无默认数据，录入交易后会开始跟踪行情"
            } else {
                refreshQuotes(trigger = RefreshTrigger.APP_OPEN)
            }
        }
    }

    fun openTradeEntry(type: TradeType) {
        val activePlatform = selectedPlatform.value
            ?.takeIf { it in enabledPlatforms.value }
            ?: draft.value.platform.takeIf { it in enabledPlatforms.value }
            ?: enabledPlatforms.value.firstOrNull()
            ?: SampleData.tradeForm(type).platform
        symbolLookupJob?.cancel()
        editingTrade.value = null
        symbolLookup.value = SymbolLookupUiModel()
        symbolSuggestions.value = emptyList()
        draft.value = applyDraftRules(
            resetDraftForType(
                type = type,
                preferredCashMarket = cashMarketFor(displayCurrency.value),
                preferredPlatform = activePlatform,
            ),
        )
    }

    fun startEditingTrade(transactionId: Long) {
        viewModelScope.launch {
            val transactions = repository.transactions.first()
            val transaction = transactions.firstOrNull { it.id == transactionId } ?: return@launch
            val transactionPlatform = runCatching {
                BrokerPlatform.valueOf(transaction.platform)
            }.getOrDefault(BrokerPlatform.UNSPECIFIED)
            val referencePortfolio = computePortfolio(
                transactions = filterTransactionsByPlatform(
                    transactions.filterNot { it.id == transactionId },
                    transactionPlatform,
                ),
                quotes = repository.quotes.first(),
                exchangeRates = repository.exchangeRates.value,
            )
            val tradeType = TradeType.valueOf(transaction.tradeType)
            val platform = runCatching { BrokerPlatform.valueOf(transaction.platform) }.getOrDefault(BrokerPlatform.UNSPECIFIED)
            val market = Market.fromString(transaction.market) ?: Market.CASH
            val resolvedLookup = if (tradeType.isSecurityTrade) {
                SymbolLookupUiModel(
                    state = SymbolLookupState.RESOLVED,
                    message = "已带出 ${transaction.name} ${transaction.symbol}",
                    resolvedSymbol = transaction.symbol,
                    resolvedName = transaction.name,
                    resolvedMarket = market,
                )
            } else {
                SymbolLookupUiModel()
            }

            symbolLookupJob?.cancel()
            editingTrade.value = EditingTradeSession(
                transactionId = transaction.id,
                tradeTime = transaction.tradeTime,
                createdAt = transaction.createdAt,
                sourceChannel = transaction.sourceChannel,
                externalReference = transaction.externalReference,
            )
            symbolLookup.value = resolvedLookup
            symbolSuggestions.value = emptyList()
            draft.value = applyDraftRules(
                draft = TradeFormState(
                    selectedType = tradeType,
                    platform = platform,
                    market = market,
                    symbolOrName = if (tradeType.isSecurityTrade) {
                        "${transaction.name} ${transaction.symbol}".trim()
                    } else {
                        ""
                    },
                    tradeDate = transaction.tradeDate,
                    priceLabel = formatEditableAmount(transaction.price),
                    quantityLabel = if (tradeType.isSecurityTrade) transaction.quantity.toString() else "1",
                    commissionLabel = formatEditableAmount(transaction.commission),
                    taxLabel = formatEditableAmount(transaction.tax),
                    note = transaction.note,
                    feeEstimateStatus = FeeEstimateStatus.MANUAL_OVERRIDE,
                ),
                lookup = resolvedLookup,
                positions = referencePortfolio.positions,
            )
        }
    }

    fun selectTradeType(type: TradeType) {
        symbolLookupJob?.cancel()
        symbolLookup.value = SymbolLookupUiModel()
        symbolSuggestions.value = emptyList()
        draft.update { current ->
            applyDraftRules(
                resetDraftForType(
                    type = type,
                    current = current,
                    preferredCashMarket = cashMarketFor(displayCurrency.value),
                    preferredPlatform = current.platform,
                ),
            )
        }
        if (type.isSecurityTrade) {
            refreshSymbolLookupForCurrentDraft()
        }
    }

    fun selectSellCandidate(candidate: SellCandidateUiModel) {
        symbolLookupJob?.cancel()
        symbolSuggestions.value = emptyList()
        val resolvedLookup = SymbolLookupUiModel(
            state = SymbolLookupState.RESOLVED,
            message = "已带出 ${candidate.name} ${candidate.symbol}",
            resolvedSymbol = candidate.symbol,
            resolvedName = candidate.name,
            resolvedMarket = candidate.market,
        )
        draft.update { current ->
            applyDraftRules(
                current.copy(
                selectedType = TradeType.SELL,
                market = candidate.market,
                symbolOrName = "${candidate.name} ${candidate.symbol}",
                ),
                lookup = resolvedLookup,
            )
        }
        symbolLookup.value = resolvedLookup
    }

    fun selectTradeFilter(filter: TransactionFilter) {
        tradeFilter.value = filter
    }

    fun selectMarketFilter(filter: MarketFilter) {
        marketFilter.value = filter
    }

    fun updateTransactionDateRange(
        startDate: String?,
        endDate: String?,
    ) {
        transactionDateStart.value = startDate?.takeIf { it.isNotBlank() }
        transactionDateEnd.value = endDate?.takeIf { it.isNotBlank() }
    }

    fun updateTransactionKeyword(keyword: String) {
        transactionKeyword.value = keyword.trimStart()
    }

    fun resetTransactionFilters() {
        tradeFilter.value = TransactionFilter.ALL
        marketFilter.value = MarketFilter.ALL
        transactionKeyword.value = ""
        transactionDateStart.value = null
        transactionDateEnd.value = null
    }

    fun selectGlobalPlatform(platform: BrokerPlatform?) {
        selectedPlatform.value = platform
        saveSelectedPlatform(platform)
        backupStatusMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
    }

    fun setPlatformVisibility(platform: BrokerPlatform, enabled: Boolean) {
        if (!platform.isConfigurable) return
        val current = enabledPlatforms.value.toMutableList()
        if (enabled) {
            if (platform !in current) {
                current += platform
            }
        } else {
            if (platform !in current || current.size <= 1) return
            current -= platform
        }
        val ordered = BrokerPlatform.configurableEntries.filter { it in current }
        enabledPlatforms.value = ordered
        saveEnabledPlatforms(ordered)
        if (selectedPlatform.value !in ordered) {
            selectedPlatform.value = null
            saveSelectedPlatform(null)
        }
        if (editingTrade.value == null && draft.value.platform !in ordered) {
            val fallbackPlatform = ordered.firstOrNull() ?: return
            draft.value = applyDraftRules(draft.value.copy(platform = fallbackPlatform))
        }
    }

    fun selectDisplayCurrency(currency: DisplayCurrency) {
        saveDisplayCurrency(currency)
        displayCurrency.value = currency
        if (!draft.value.selectedType.isSecurityTrade) {
            draft.update { current ->
                applyDraftRules(current.copy(market = cashMarketFor(currency)))
            }
        }
    }

    fun updateHsbcImportDraftText(value: String) {
        hsbcImportDraftText.value = value
    }

    fun importHsbcNotificationText() {
        val rawText = hsbcImportDraftText.value.trim()
        if (rawText.isBlank()) {
            hsbcImportStatusMessage.value = "请先粘贴汇丰短信文本"
            return
        }
        viewModelScope.launch {
            hsbcImportStatusMessage.value = "正在解析短信文本，请稍候..."
            runCatching {
                repository.importHsbcNotificationText(rawText)
            }.onSuccess { result ->
                hsbcImportStatusMessage.value = result.message
                if (result.outcome == com.recoder.stockledger.data.repository.TradeImportOutcome.IMPORTED) {
                    hsbcImportDraftText.value = ""
                    repository.refreshQuotesForPortfolio(repository.transactions.first())
                }
            }.onFailure { error ->
                hsbcImportStatusMessage.value = "短信解析失败：${error.message ?: "请稍后重试"}"
            }
        }
    }

    fun updateZhuoruiEmailSyncConfig(transform: (ZhuoruiEmailSyncConfig) -> ZhuoruiEmailSyncConfig) {
        zhuoruiEmailSyncConfig.update(transform)
    }

    fun saveZhuoruiEmailSyncConfig() {
        val config = zhuoruiEmailSyncConfig.value
        val validationMessage = config.validationMessage()
        preferences.edit()
            .putString(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_IMAP_HOST, config.imapHost)
            .putString(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_IMAP_PORT, config.imapPort)
            .putString(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_ACCOUNT, config.account)
            .putString(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_PASSWORD, config.password)
            .putString(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_FOLDER, config.folder)
            .apply()
        val message = validationMessage?.let { "邮箱配置已保存，但$it" } ?: "邮箱配置已保存"
        zhuoruiEmailSyncStatusMessage.value = message
        preferences.edit()
            .putString(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_LAST_SYNC_MESSAGE, message)
            .apply()
        reconcileZhuoruiEmailAutoSync()
    }

    fun updateZhuoruiEmailManualSyncOptions(transform: (ZhuoruiEmailManualSyncOptions) -> ZhuoruiEmailManualSyncOptions) {
        zhuoruiEmailManualSyncOptions.update(transform)
    }

    fun setZhuoruiEmailAutoImportEnabled(enabled: Boolean) {
        val config = zhuoruiEmailSyncConfig.value
        val validationMessage = config.validationMessage()
        if (enabled && validationMessage != null) {
            val message = "请先完成邮箱配置并保存，再开启自动同步：$validationMessage"
            zhuoruiEmailSyncStatusMessage.value = message
            preferences.edit()
                .putString(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_LAST_SYNC_MESSAGE, message)
                .apply()
            return
        }
        preferences.edit()
            .putBoolean(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_AUTO_IMPORT_ENABLED, enabled)
            .apply()
        zhuoruiEmailAutoImportEnabled.value = enabled
        reconcileZhuoruiEmailAutoSync()
        val message = if (enabled) "已开启邮箱自动同步" else "已关闭邮箱自动同步"
        zhuoruiEmailSyncStatusMessage.value = message
        preferences.edit()
            .putString(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_LAST_SYNC_MESSAGE, message)
            .apply()
    }

    fun syncZhuoruiMailboxNow() {
        val config = zhuoruiEmailSyncConfig.value
        val configValidationMessage = config.validationMessage()
        if (configValidationMessage != null) {
            zhuoruiEmailSyncStatusMessage.value = configValidationMessage
            return
        }
        val manualOptions = zhuoruiEmailManualSyncOptions.value
        val optionsValidationMessage = manualOptions.validationMessage()
        if (optionsValidationMessage != null) {
            zhuoruiEmailSyncStatusMessage.value = optionsValidationMessage
            return
        }
        viewModelScope.launch {
            zhuoruiEmailSyncStatusMessage.value = "正在同步邮箱，请稍候..."
            runCatching {
                repository.syncZhuoruiMailbox(
                    config = config,
                    lastSyncAtMillis = loadZhuoruiEmailLastSyncAt(),
                    fetchCount = manualOptions.resolvedFetchCount(),
                    earliestReceivedAtMillis = manualOptions.resolvedEarliestReceivedAtMillis(),
                )
            }.onSuccess { result ->
                val syncAt = result.latestSeenMessageAt ?: System.currentTimeMillis()
                preferences.edit()
                    .putLong(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_LAST_SYNC_AT, syncAt)
                    .apply()
                val message = when {
                    result.importedCount > 0 ->
                        "同步完成：新增 ${result.importedCount} 条，重复 ${result.duplicateCount} 条"
                    result.duplicateCount > 0 ->
                        "同步完成：没有新增记录，重复 ${result.duplicateCount} 条"
                    else ->
                        "同步完成：未发现可导入的新邮件"
                }
                zhuoruiEmailSyncStatusMessage.value = message
                preferences.edit()
                    .putString(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_LAST_SYNC_MESSAGE, message)
                    .apply()
            }.onFailure { error ->
                val message = "邮箱同步失败：${error.message ?: "请检查 IMAP 配置"}"
                zhuoruiEmailSyncStatusMessage.value = message
                preferences.edit()
                    .putString(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_LAST_SYNC_MESSAGE, message)
                    .apply()
            }
        }
    }

    fun selectTradeMarket(market: Market) {
        draft.update { current ->
            applyDraftRules(current.copy(market = market))
        }
        refreshSymbolLookupForCurrentDraft()
    }

    fun selectCashCurrency(currency: DisplayCurrency) {
        draft.update { current ->
            applyDraftRules(current.copy(market = cashMarketFor(currency)))
        }
    }

    fun selectTradePlatform(platform: BrokerPlatform) {
        if (platform !in enabledPlatforms.value) return
        draft.update { current ->
            applyDraftRules(current.copy(platform = platform))
        }
    }

    fun selectPlatformFeePlan(planId: String) {
        val platform = selectedPlatform.value ?: return
        val resolvedPlanId = TradeFeeEstimator.resolvePlanId(platform, planId)
        if (resolvedPlanId.isBlank()) return
        platformFeePlanSelections.update { current ->
            current.toMutableMap().apply {
                put(platform, resolvedPlanId)
            }
        }
        savePlatformFeePlanSelections(platformFeePlanSelections.value)
        draft.update { current ->
            if (current.platform == platform) {
                applyDraftRules(current)
            } else {
                current
            }
        }
    }

    fun updateZhuoruiPromoConfig(config: ZhuoruiPromoConfig) {
        zhuoruiPromoConfig.value = config
    }

    fun saveZhuoruiPromoConfig() {
        saveZhuoruiPromoConfigToPrefs(zhuoruiPromoConfig.value)
        draft.update { current ->
            if (current.platform == BrokerPlatform.ZHUORUI) {
                applyDraftRules(current)
            } else {
                current
            }
        }
    }

    fun onSymbolInputChanged(value: String) {
        draft.update { current ->
            applyDraftRules(current.copy(symbolOrName = value))
        }
        scheduleSymbolLookup(value = value, market = draft.value.market)
    }

    fun selectSymbolSuggestion(suggestion: SecuritySuggestionUiModel) {
        symbolLookupJob?.cancel()
        val resolvedLookup = SymbolLookupUiModel(
            state = SymbolLookupState.RESOLVED,
            message = "已识别为 ${suggestion.name} ${suggestion.symbol}",
            resolvedSymbol = suggestion.symbol,
            resolvedName = suggestion.name,
            resolvedMarket = suggestion.market,
        )
        draft.update { current ->
            applyDraftRules(
                current.copy(
                market = suggestion.market,
                symbolOrName = suggestion.displayLabel,
                ),
                lookup = resolvedLookup,
            )
        }
        symbolSuggestions.value = emptyList()
        symbolLookup.value = resolvedLookup
    }

    fun updateDraft(transform: (TradeFormState) -> TradeFormState) {
        draft.update { current ->
            applyDraftRules(transform(current))
        }
    }

    fun updateTradeCommission(value: String) {
        draft.update { current ->
            applyDraftRules(
                current.copy(
                    commissionLabel = value,
                    feeEstimateStatus = FeeEstimateStatus.MANUAL_OVERRIDE,
                ),
            )
        }
    }

    fun updateTradeTax(value: String) {
        draft.update { current ->
            applyDraftRules(
                current.copy(
                    taxLabel = value,
                    feeEstimateStatus = FeeEstimateStatus.MANUAL_OVERRIDE,
                ),
            )
        }
    }

    fun recalculateTradeFees() {
        draft.update { current ->
            applyDraftRules(
                current.copy(
                    feeEstimateStatus = FeeEstimateStatus.UNAVAILABLE,
                ),
            )
        }
    }

    fun refreshQuotesByPull() {
        if (refreshState.value == RefreshState.REFRESHING) return

        val now = System.currentTimeMillis()
        val remainingMs = MANUAL_REFRESH_INTERVAL_MS - (now - lastManualRefreshTriggeredAt)
        if (lastManualRefreshTriggeredAt > 0L && remainingMs > 0L) {
            val remainingSeconds = ((remainingMs + 999L) / 1_000L).toInt()
            refreshState.value = RefreshState.IDLE
            refreshNote.value = "距离上次手动刷新不足 1 分钟，请 $remainingSeconds 秒后再试"
            return
        }

        lastManualRefreshTriggeredAt = now
        showPullRefreshTime.value = true
        viewModelScope.launch {
            refreshQuotes(trigger = RefreshTrigger.MANUAL_PULL)
        }
    }

    fun deleteHolding(holding: HoldingUiModel) {
        viewModelScope.launch {
            repository.deleteHolding(
                symbol = holding.code,
                market = holding.market,
            )

            if (repository.transactions.first().isEmpty()) {
                refreshState.value = RefreshState.IDLE
                refreshNote.value = "暂无持仓，录入交易后会开始跟踪行情"
                lastRefreshTimestamp.value = 0L
            }
        }
    }

    fun deleteTrade(transactionId: Long) {
        viewModelScope.launch {
            repository.deleteTrade(transactionId)
            if (editingTrade.value?.transactionId == transactionId) {
                editingTrade.value = null
                symbolLookup.value = SymbolLookupUiModel()
                symbolSuggestions.value = emptyList()
                draft.value = applyDraftRules(
                    resetDraftForType(
                        type = TradeType.BUY,
                        preferredCashMarket = cashMarketFor(displayCurrency.value),
                    ),
                )
            }

            val latestTransactions = repository.transactions.first()
            if (latestTransactions.isEmpty()) {
                refreshState.value = RefreshState.IDLE
                refreshNote.value = "暂无交易记录，录入后会开始跟踪行情"
                lastRefreshTimestamp.value = 0L
            } else {
                refreshQuotes(trigger = RefreshTrigger.TRADE_DELETE)
            }
        }
    }

    fun enterBatchSelectionMode() {
        batchSelectionMode.value = true
        selectedTransactionIds.value = emptySet()
    }

    fun exitBatchSelectionMode() {
        batchSelectionMode.value = false
        selectedTransactionIds.value = emptySet()
    }

    fun toggleTransactionSelection(transactionId: Long) {
        selectedTransactionIds.update { current ->
            if (transactionId in current) current - transactionId else current + transactionId
        }
    }

    fun selectAllTransactions(transactionIds: List<Long>) {
        selectedTransactionIds.value = transactionIds.toSet()
    }

    fun deleteSelectedTransactions() {
        val ids = selectedTransactionIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.deleteTransactionsByIds(ids)
            batchSelectionMode.value = false
            selectedTransactionIds.value = emptySet()
            val latestTransactions = repository.transactions.first()
            if (latestTransactions.isEmpty()) {
                refreshState.value = RefreshState.IDLE
                refreshNote.value = "暂无交易记录，录入后会开始跟踪行情"
                lastRefreshTimestamp.value = 0L
            } else {
                refreshQuotes(trigger = RefreshTrigger.TRADE_DELETE)
            }
        }
    }

    suspend fun submitTrade(): Boolean {
        val currentDraft = draft.value
        val currentEditingTrade = editingTrade.value
        val latestTransactions = repository.transactions.first()
        val portfolioState = computePortfolio(
            transactions = filterTransactionsByPlatform(
                currentEditingTrade?.let { editing ->
                    latestTransactions.filterNot { it.id == editing.transactionId }
                } ?: latestTransactions,
                currentDraft.platform,
            ),
            quotes = repository.quotes.first(),
            exchangeRates = repository.exchangeRates.value,
        )
        val currentLookup = symbolLookup.value
        val validationMessage = validateTradeDraft(currentDraft, portfolioState, currentLookup)
        if (validationMessage != null) {
            return false
        }

        val resolved = if (currentDraft.selectedType.isSecurityTrade) {
            resolveSecurity(currentDraft, portfolioState.positions, currentLookup) ?: return false
        } else {
            ResolvedSecurity(
                symbol = CASH_ACCOUNT_SYMBOL,
                name = CASH_ACCOUNT_NAME,
                market = normalizeCashMarket(currentDraft.market),
            )
        }
        val input = TradeDraftInput(
            tradeType = currentDraft.selectedType,
            platform = currentDraft.platform,
            sourceChannel = currentEditingTrade?.sourceChannel?.let { sourceName ->
                runCatching { ImportSourceChannel.valueOf(sourceName) }.getOrNull()
            },
            externalReference = currentEditingTrade?.externalReference,
            market = resolved.market,
            symbol = resolved.symbol,
            name = resolved.name,
            tradeDate = currentDraft.tradeDate,
            price = parseDecimal(currentDraft.priceLabel),
            quantity = if (currentDraft.selectedType.isSecurityTrade) parseQuantity(currentDraft.quantityLabel) else 1,
            commission = if (currentDraft.selectedType.isSecurityTrade) parseDecimal(currentDraft.commissionLabel) else 0.0,
            tax = if (currentDraft.selectedType.isSecurityTrade) parseDecimal(currentDraft.taxLabel) else 0.0,
            note = currentDraft.note.trim(),
            tradeTime = currentEditingTrade?.tradeTime ?: LocalTime.now().format(timeFormatter),
            createdAt = currentEditingTrade?.createdAt ?: System.currentTimeMillis(),
        )

        if (currentEditingTrade == null) {
            repository.addTrade(input)
        } else {
            repository.updateTrade(currentEditingTrade.transactionId, input)
        }
        tradeFilter.value = TransactionFilter.ALL
        marketFilter.value = MarketFilter.ALL
        backupStatusMessage.value = null
        editingTrade.value = null
        symbolLookup.value = SymbolLookupUiModel()
        symbolSuggestions.value = emptyList()
        draft.value = applyDraftRules(
            resetDraftForType(
                currentDraft.selectedType,
                preferredPlatform = currentDraft.platform,
            ),
        )
        viewModelScope.launch {
            refreshQuotes(
                trigger = if (currentEditingTrade == null) {
                    RefreshTrigger.TRADE_CREATE
                } else {
                    RefreshTrigger.TRADE_UPDATE
                },
            )
        }
        return true
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val recordedPlatforms = repository.transactions.first()
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { outputStream ->
                    repository.exportBackup(
                        outputStream = outputStream,
                        displayCurrencyName = displayCurrency.value.name,
                        enabledPlatforms = enabledPlatforms.value,
                        selectedPlatform = selectedPlatform.value,
                    )
                } ?: error("无法创建备份文件")
            }.onSuccess {
                backupStatusMessage.value = "备份已导出"
            }.onFailure { error ->
                backupStatusMessage.value = "备份失败：${error.message ?: "请稍后重试"}"
            }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val imported = getApplication<Application>().contentResolver.openInputStream(uri)?.use { inputStream ->
                    repository.importBackup(inputStream)
                } ?: error("无法读取备份文件")
                applyImportedBackup(imported)
            }.onSuccess {
                val latestTransactions = repository.transactions.first()
                if (latestTransactions.isEmpty()) {
                    refreshState.value = RefreshState.IDLE
                    refreshNote.value = "备份已导入，但当前没有交易记录"
                    lastRefreshTimestamp.value = 0L
                } else {
                    refreshQuotes(trigger = RefreshTrigger.BACKUP_IMPORT)
                }
            }.onFailure { error ->
                backupStatusMessage.value = "导入失败：${error.message ?: "请检查备份文件"}"
            }
        }
    }

    private fun refreshSymbolLookupForCurrentDraft() {
        val currentDraft = draft.value
        if (!currentDraft.selectedType.isSecurityTrade) {
            symbolLookup.value = SymbolLookupUiModel()
            symbolSuggestions.value = emptyList()
            return
        }
        scheduleSymbolLookup(
            value = currentDraft.symbolOrName,
            market = currentDraft.market,
        )
    }

    private fun scheduleSymbolLookup(
        value: String,
        market: Market,
    ) {
        symbolLookupJob?.cancel()
        if (!draft.value.selectedType.isSecurityTrade || market == Market.CASH) {
            symbolSuggestions.value = emptyList()
            symbolLookup.value = SymbolLookupUiModel()
            return
        }
        val trimmed = value.trim()
        val local = resolveKnownSecurity(trimmed, market)
        if (local != null) {
            symbolSuggestions.value = emptyList()
            val resolvedLookup = SymbolLookupUiModel(
                state = SymbolLookupState.RESOLVED,
                    message = "已识别为 ${local.name} ${local.symbol}",
                resolvedSymbol = local.symbol,
                resolvedName = local.name,
                resolvedMarket = local.market,
            )
            symbolLookup.value = resolvedLookup
            draft.update { current ->
                applyDraftRules(current, lookup = resolvedLookup)
            }
            return
        }

        if (trimmed.isBlank()) {
            symbolSuggestions.value = emptyList()
            symbolLookup.value = SymbolLookupUiModel()
            return
        }

        symbolLookup.value = SymbolLookupUiModel(
            state = if (shouldLookupRemotely(trimmed, market)) SymbolLookupState.LOOKING_UP else SymbolLookupState.IDLE,
            message = lookupHintMessage(market),
        )

        symbolLookupJob = viewModelScope.launch {
            val lookupInput = trimmed
            delay(SYMBOL_LOOKUP_DEBOUNCE_MS)
            val suggestions = if (shouldLookupRemotely(lookupInput, market)) {
                runCatching {
                    repository.searchSecurities(lookupInput, market, limit = 6)
                }.getOrDefault(emptyList())
            } else {
                emptyList()
            }

            val latestDraft = draft.value
            if (latestDraft.market != market || latestDraft.symbolOrName.trim() != lookupInput) {
                return@launch
            }

            val exactSuggestion = suggestions.firstOrNull { suggestion ->
                isExactSuggestionMatch(lookupInput, suggestion)
            }
            val suggestionItems = suggestions.map { suggestion ->
                SecuritySuggestionUiModel(
                    symbol = suggestion.symbol,
                    name = suggestion.name,
                    market = suggestion.market,
                    displayLabel = "${suggestion.name} ${suggestion.symbol}",
                )
            }

            if (exactSuggestion != null) {
                symbolSuggestions.value = suggestionItems
                val resolvedLookup = SymbolLookupUiModel(
                    state = SymbolLookupState.RESOLVED,
                    message = "已识别为 ${exactSuggestion.name} ${exactSuggestion.symbol}，点候选项才会填入",
                    resolvedSymbol = exactSuggestion.symbol,
                    resolvedName = exactSuggestion.name,
                    resolvedMarket = exactSuggestion.market,
                )
                symbolLookup.value = resolvedLookup
                draft.update { current ->
                    applyDraftRules(current, lookup = resolvedLookup)
                }
                return@launch
            }

            symbolSuggestions.value = suggestionItems

            symbolLookup.value = when {
                suggestions.isNotEmpty() -> SymbolLookupUiModel(
                    state = SymbolLookupState.IDLE,
                    message = "找到 ${suggestions.size} 条候选，点一下可自动补全",
                )

                isLookupReady(lookupInput, market) -> SymbolLookupUiModel(
                    state = SymbolLookupState.INVALID,
                    message = "未找到对应股票，请检查代码和市场后重试",
                )

                else -> SymbolLookupUiModel(
                    state = SymbolLookupState.IDLE,
                    message = lookupHintMessage(market),
                )
            }
        }
    }

    private suspend fun refreshQuotes(trigger: RefreshTrigger) {
        if (trigger != RefreshTrigger.MANUAL_PULL) {
            showPullRefreshTime.value = false
        }
        refreshState.value = RefreshState.REFRESHING
        refreshNote.value = when (trigger) {
            RefreshTrigger.APP_OPEN -> "正在刷新启动行情..."
            RefreshTrigger.MANUAL_PULL -> "正在手动刷新行情..."
            RefreshTrigger.TRADE_CREATE -> "正在同步新增交易后的最新行情..."
            RefreshTrigger.TRADE_UPDATE -> "正在同步修改交易后的最新行情..."
            RefreshTrigger.TRADE_DELETE -> "正在同步删除交易后的最新行情..."
            RefreshTrigger.BACKUP_IMPORT -> "正在同步导入备份后的最新行情..."
        }

        runCatching {
            val latestTransactions = repository.transactions.first()
            val exchangeRateResult = repository.refreshExchangeRates()
            lastRefreshTimestamp.value = repository.refreshQuotesForPortfolio(latestTransactions)
            refreshState.value = RefreshState.FRESH
            val rateSuffix = when (exchangeRateResult.origin) {
                ExchangeRateOrigin.NETWORK -> ""
                ExchangeRateOrigin.CACHE -> " · 汇率暂时沿用缓存"
                ExchangeRateOrigin.DEFAULT -> " · 汇率暂时沿用默认值"
            }
            refreshNote.value = when (trigger) {
                RefreshTrigger.APP_OPEN ->
                    "启动已刷新 ${formatRefreshTime(lastRefreshTimestamp.value)} · 手动下拉至少间隔 1 分钟$rateSuffix"
                RefreshTrigger.MANUAL_PULL ->
                    "最近刷新 ${formatRefreshTime(lastRefreshTimestamp.value)} · 手动下拉至少间隔 1 分钟$rateSuffix"
                RefreshTrigger.TRADE_CREATE ->
                    "录入成功，已同步最新行情 · ${formatRefreshTime(lastRefreshTimestamp.value)}$rateSuffix"
                RefreshTrigger.TRADE_UPDATE ->
                    "修改成功，已同步最新行情 · ${formatRefreshTime(lastRefreshTimestamp.value)}$rateSuffix"
                RefreshTrigger.TRADE_DELETE ->
                    "删除成功，已同步最新行情 · ${formatRefreshTime(lastRefreshTimestamp.value)}$rateSuffix"
                RefreshTrigger.BACKUP_IMPORT ->
                    "备份导入成功，已同步最新行情 · ${formatRefreshTime(lastRefreshTimestamp.value)}$rateSuffix"
            }
        }.onFailure { error ->
            refreshState.value = RefreshState.FAILED
            refreshNote.value = when (trigger) {
                RefreshTrigger.APP_OPEN ->
                    "启动刷新失败：${error.message ?: "请稍后手动下拉刷新"}"
                RefreshTrigger.MANUAL_PULL ->
                    "手动刷新失败：${error.message ?: "请检查网络连接后重试"}"
                RefreshTrigger.TRADE_CREATE ->
                    "交易已保存，但行情同步失败：${error.message ?: "请稍后手动下拉刷新"}"
                RefreshTrigger.TRADE_UPDATE ->
                    "修改已保存，但行情同步失败：${error.message ?: "请稍后手动下拉刷新"}"
                RefreshTrigger.TRADE_DELETE ->
                    "删除已完成，但行情同步失败：${error.message ?: "请稍后手动下拉刷新"}"
                RefreshTrigger.BACKUP_IMPORT ->
                    "备份已导入，但行情同步失败：${error.message ?: "请稍后手动下拉刷新"}"
            }
        }
        if (trigger == RefreshTrigger.MANUAL_PULL) {
            showPullRefreshTime.value = false
        }
    }

    private fun validateTradeDraft(
        draft: TradeFormState,
        portfolio: PortfolioComputation,
        lookup: SymbolLookupUiModel,
    ): String? {
        val noInputYet = if (draft.selectedType.isSecurityTrade) {
            draft.symbolOrName.isBlank() &&
                draft.priceLabel.isBlank() &&
                draft.quantityLabel.isBlank()
        } else {
            draft.priceLabel.isBlank()
        }
        if (noInputYet) return null

        if (draft.tradeDate.isBlank()) return "请选择交易日期"
        if (parseTradeDateOrNull(draft.tradeDate) == null) return "请选择有效的交易日期"

        if (!draft.selectedType.isSecurityTrade) {
            val amount = parseDecimal(draft.priceLabel)
            if (amount <= 0.0) return "金额必须大于 0"

            val amountCny = convertToCny(amount, normalizeCashMarket(draft.market), repository.exchangeRates.value)
            if (draft.selectedType == TradeType.WITHDRAW && portfolio.cashBalanceCny + EPSILON < amountCny) {
                return "可用现金不足，无法完成出金"
            }
            return null
        }

        if (draft.symbolOrName.isBlank()) return "请输入股票代码"

        val resolved = resolveSecurity(draft, portfolio.positions, lookup)
        if (resolved == null) {
            return when (lookup.state) {
                SymbolLookupState.LOOKING_UP -> "正在校验股票代码，请稍候"
                SymbolLookupState.INVALID -> lookup.message ?: "未找到对应股票，请检查代码和市场"
                else -> "请先输入有效股票代码并完成校验"
            }
        }

        val price = parseDecimal(draft.priceLabel)
        if (price <= 0.0) return "成交价格必须大于 0"

        val quantity = parseQuantity(draft.quantityLabel)
        if (quantity <= 0) return "成交数量必须大于 0"

        when (draft.selectedType) {
            TradeType.BUY, TradeType.SELL -> Unit
            else -> Unit
        }

        return null
    }

    private fun applyDraftRules(
        draft: TradeFormState,
        lookup: SymbolLookupUiModel = symbolLookup.value,
        positions: Map<String, PositionComputation> = draftReferencePortfolio.value.positions,
    ): TradeFormState {
        val normalizedBase = if (draft.selectedType.isSecurityTrade) {
            draft.copy(
                market = if (draft.market == Market.CASH) Market.A_SHARE else draft.market,
            )
        } else {
            draft.copy(
                market = normalizeCashMarket(draft.market),
                symbolOrName = "",
                quantityLabel = "1",
                commissionLabel = "0.00",
                taxLabel = "0.00",
                feeEstimateStatus = FeeEstimateStatus.UNAVAILABLE,
                feeEstimateSummary = null,
                feeEstimateDetail = null,
                canAutoEstimateFees = false,
            )
        }

        return applyFeeEstimateRules(normalizedBase)
    }

    private fun applyFeeEstimateRules(draft: TradeFormState): TradeFormState {
        if (!draft.selectedType.isSecurityTrade) return draft

        val selectedPlanId = resolveSelectedFeePlanId(draft.platform)
        val profile = TradeFeeEstimator.profile(draft.platform, draft.market, selectedPlanId)
        val price = parseDecimal(draft.priceLabel)
        val quantity = parseQuantity(draft.quantityLabel)
        val manualOverride = draft.feeEstimateStatus == FeeEstimateStatus.MANUAL_OVERRIDE

        if (profile.coverage == com.recoder.stockledger.data.FeeEstimateCoverage.UNSUPPORTED) {
            return if (manualOverride) {
                draft.copy(
                    feeEstimateSummary = "当前费用为手动输入。${profile.note}",
                    feeEstimateDetail = profile.note,
                    canAutoEstimateFees = false,
                )
            } else {
                draft.copy(
                    commissionLabel = "0.00",
                    taxLabel = "0.00",
                    feeEstimateStatus = FeeEstimateStatus.UNAVAILABLE,
                    feeEstimateSummary = profile.note,
                    feeEstimateDetail = null,
                    canAutoEstimateFees = false,
                )
            }
        }

        if (price <= 0.0 || quantity <= 0) {
            val pendingMessage = "补全成交价格和数量后，将按公开费率自动估算。"
            return if (manualOverride) {
                draft.copy(
                    feeEstimateSummary = "当前费用为手动输入。$pendingMessage",
                    feeEstimateDetail = profile.note,
                    canAutoEstimateFees = true,
                )
            } else {
                draft.copy(
                    commissionLabel = "0.00",
                    taxLabel = "0.00",
                    feeEstimateStatus = FeeEstimateStatus.UNAVAILABLE,
                    feeEstimateSummary = pendingMessage,
                    feeEstimateDetail = profile.note,
                    canAutoEstimateFees = true,
                )
            }
        }

        val estimate = TradeFeeEstimator.estimate(
            platform = draft.platform,
            market = draft.market,
            tradeType = draft.selectedType,
            price = price,
            quantity = quantity,
            planId = selectedPlanId,
            context = buildTradeFeeEstimateContext(draft),
        )
        if (!estimate.canAutoApply) {
            return if (manualOverride) {
                draft.copy(
                    feeEstimateSummary = "当前费用为手动输入。${estimate.summary}",
                    feeEstimateDetail = estimate.detail,
                    canAutoEstimateFees = false,
                )
            } else {
                draft.copy(
                    commissionLabel = "0.00",
                    taxLabel = "0.00",
                    feeEstimateStatus = FeeEstimateStatus.UNAVAILABLE,
                    feeEstimateSummary = estimate.summary,
                    feeEstimateDetail = estimate.detail,
                    canAutoEstimateFees = false,
                )
            }
        }

        return if (manualOverride) {
            draft.copy(
                feeEstimateSummary = "当前费用为手动输入。${estimate.summary}",
                feeEstimateDetail = estimate.detail,
                canAutoEstimateFees = true,
            )
        } else {
            draft.copy(
                commissionLabel = formatEditableAmount(estimate.commission),
                taxLabel = formatEditableAmount(estimate.tax),
                feeEstimateStatus = FeeEstimateStatus.AUTO_APPLIED,
                feeEstimateSummary = estimate.summary,
                feeEstimateDetail = estimate.detail,
                canAutoEstimateFees = true,
            )
        }
    }

    private fun resolveSecurity(
        draft: TradeFormState,
        positions: Map<String, PositionComputation>,
        lookup: SymbolLookupUiModel,
    ): ResolvedSecurity? {
        val raw = draft.symbolOrName.trim()
        if (raw.isBlank()) return null

        resolveLookupSelection(rawInput = raw, market = draft.market, lookup = lookup)?.let { resolved ->
            return resolved
        }

        val position = positions.values.firstOrNull { current ->
            current.market == draft.market &&
                matchesSecurityInput(
                    rawInput = raw,
                    market = draft.market,
                    symbol = current.symbol,
                    name = current.name,
                )
        }
        if (position != null) {
            return ResolvedSecurity(
                symbol = position.symbol,
                name = position.name,
                market = position.market,
            )
        }

        return resolveKnownSecurity(raw, draft.market)
    }

    private fun resolveKnownSecurity(
        rawInput: String,
        market: Market,
    ): ResolvedSecurity? {
        val raw = rawInput.trim()
        return knownSecurities.firstOrNull { security ->
            security.market == market &&
                matchesSecurityInput(
                    rawInput = raw,
                    market = market,
                    symbol = security.symbol,
                    name = security.name,
                )
        }
    }

    private fun resolveLookupSelection(
        rawInput: String,
        market: Market,
        lookup: SymbolLookupUiModel,
    ): ResolvedSecurity? {
        val resolvedSymbol = lookup.resolvedSymbol ?: return null
        val resolvedName = lookup.resolvedName ?: return null
        if (lookup.state != SymbolLookupState.RESOLVED || lookup.resolvedMarket != market) {
            return null
        }

        return if (
            matchesSecurityInput(
                rawInput = rawInput,
                market = market,
                symbol = resolvedSymbol,
                name = resolvedName,
            )
        ) {
            ResolvedSecurity(
                symbol = resolvedSymbol,
                name = resolvedName,
                market = market,
            )
        } else {
            null
        }
    }

    private fun matchesSecurityInput(
        rawInput: String,
        market: Market,
        symbol: String,
        name: String,
    ): Boolean {
        val raw = rawInput.trim()
        if (raw.isBlank()) return false

        val normalizedRaw = normalizeComparableSymbol(raw.uppercase().replace(" ", ""), market)
        val normalizedSymbol = normalizeComparableSymbol(symbol.uppercase(), market)
        val trailingSymbol = extractTrailingComparableSymbol(raw, market)

        return symbol.equals(raw, ignoreCase = true) ||
            name.equals(raw, ignoreCase = true) ||
            (normalizedRaw.isNotBlank() && normalizedRaw == normalizedSymbol) ||
            (trailingSymbol != null && trailingSymbol == normalizedSymbol)
    }

    private fun extractTrailingComparableSymbol(rawInput: String, market: Market): String? {
        val trailingToken = rawInput
            .trim()
            .split(Regex("\\s+"))
            .lastOrNull()
            ?.trim(',', '.', '，', '。', '(', ')', '[', ']', '{', '}')
            .orEmpty()
        if (trailingToken.isBlank() || !isLookupReady(trailingToken, market)) {
            return null
        }
        return normalizeComparableSymbol(trailingToken.uppercase(), market).takeIf { it.isNotBlank() }
    }

    private fun shouldLookupRemotely(rawInput: String, market: Market): Boolean {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) return false
        return when (market) {
            Market.A_SHARE -> trimmed.length >= 2
            Market.HK -> trimmed.length >= 2
            Market.US -> trimmed.length >= 1
            Market.CASH -> false
        }
    }

    private fun isLookupReady(rawInput: String, market: Market): Boolean {
        val compact = rawInput.trim().uppercase().replace(" ", "")
        val digits = when (market) {
            Market.A_SHARE -> compact
                .removePrefix("SH")
                .removePrefix("SZ")
                .substringBefore(".")
                .filter(Char::isDigit)

            Market.HK -> compact
                .removePrefix("HK")
                .removeSuffix(".HK")
                .filter(Char::isDigit)

            Market.US -> normalizeUsLookupInput(compact)
                .filter { it.isLetterOrDigit() || it == '.' || it == '-' }

            Market.CASH -> ""
        }

        return when (market) {
            Market.A_SHARE -> digits.length == 6
            Market.HK -> digits.length in 3..5
            Market.US -> digits.matches(Regex("[A-Z][A-Z0-9.-]{0,9}")) && digits.any(Char::isLetter)
            Market.CASH -> false
        }
    }

    private fun isExactSuggestionMatch(
        rawInput: String,
        suggestion: SecurityLookupResult,
    ): Boolean {
        val compactInput = rawInput.trim().uppercase().replace(" ", "")
        val normalizedInput = normalizeComparableSymbol(compactInput, suggestion.market)
        val normalizedSuggestion = normalizeComparableSymbol(suggestion.symbol, suggestion.market)

        return suggestion.name.equals(rawInput.trim(), ignoreCase = true) ||
            suggestion.symbol.equals(rawInput.trim(), ignoreCase = true) ||
            (normalizedInput.isNotBlank() && normalizedInput == normalizedSuggestion)
    }

    private fun lookupHintMessage(market: Market): String = when (market) {
        Market.A_SHARE -> "输入股票代码或名称，支持自动联想和补全"
        Market.HK -> "输入港股代码或名称，支持自动联想和补全"
        Market.US -> "输入美股代码或名称，支持自动联想和补全"
        Market.CASH -> "现金流水无需识别股票"
    }

    private fun buildSummary(
        portfolio: PortfolioComputation,
        refresh: RefreshState,
        refreshedAt: Long,
        refreshNote: String,
        showPullRefreshTime: Boolean,
        displayCurrency: DisplayCurrency,
        exchangeRates: ExchangeRates,
    ): PortfolioSummary {
        val aCount = portfolio.positions.values.count { it.market == Market.A_SHARE && it.quantity != 0 }
        val hkCount = portfolio.positions.values.count { it.market == Market.HK && it.quantity != 0 }
        val usCount = portfolio.positions.values.count { it.market == Market.US && it.quantity != 0 }
        return PortfolioSummary(
            totalAssets = formatDisplayAmount(portfolio.totalAssetsCny, displayCurrency, exchangeRates),
            totalCost = formatDisplayAmount(portfolio.netInflowCny, displayCurrency, exchangeRates),
            totalCostHint = "累计入金 ${formatDisplayAmount(portfolio.totalDepositCny, displayCurrency, exchangeRates)} · 累计出金 ${formatDisplayAmount(portfolio.totalWithdrawCny, displayCurrency, exchangeRates)}",
            cashBalance = formatDisplayAmount(portfolio.cashBalanceCny, displayCurrency, exchangeRates),
            cashBalanceHint = "A股 $aCount 只 · 港股 $hkCount 只 · 美股 $usCount 只",
            totalProfit = formatSignedDisplayAmount(portfolio.unrealizedProfitCny, displayCurrency, exchangeRates),
            totalProfitHint = "按现价估算收益率 ${formatSignedPercent(portfolio.unrealizedProfitPercent)}",
            dayProfit = "${formatSignedDisplayAmount(portfolio.dayProfitCny, displayCurrency, exchangeRates)} (${formatSignedPercent(portfolio.dayProfitPercent)})",
            holdingsValue = formatDisplayAmount(portfolio.holdingsValueCny, displayCurrency, exchangeRates),
            commissionTotal = formatDisplayAmount(portfolio.totalCommissionCny, displayCurrency, exchangeRates),
            taxTotal = formatDisplayAmount(portfolio.totalTaxCny, displayCurrency, exchangeRates),
            tradeCount = "${portfolio.securityTradeCount} 笔",
            refreshState = refresh,
            refreshMessage = when (refresh) {
                RefreshState.IDLE -> refreshNote.ifBlank { DEFAULT_REFRESH_MESSAGE }
                RefreshState.REFRESHING -> refreshNote
                RefreshState.FRESH -> refreshNote.ifBlank {
                    if (refreshedAt > 0L) "最近刷新 ${formatRefreshTime(refreshedAt)}" else DEFAULT_REFRESH_MESSAGE
                }
                RefreshState.FAILED -> refreshNote.ifBlank { "刷新失败，请稍后重试" }
            },
            refreshTimeLabel = refreshedAt.takeIf { it > 0L }?.let(::formatRefreshTime),
            showPullRefreshTime = refresh == RefreshState.REFRESHING && showPullRefreshTime,
        )
    }

    private fun buildPlatformAssetLabels(
        transactions: List<TransactionEntity>,
        quotes: List<QuoteSnapshotEntity>,
        exchangeRates: ExchangeRates,
        displayCurrency: DisplayCurrency,
        enabledPlatforms: List<BrokerPlatform>,
        summaryPortfolio: PortfolioComputation,
    ): Map<BrokerPlatform?, String> {
        val labels = linkedMapOf<BrokerPlatform?, String>()
        labels[null] = "总资产 ${formatDisplayAmount(summaryPortfolio.totalAssetsCny, displayCurrency, exchangeRates)}"
        enabledPlatforms.forEach { platform ->
            val portfolio = computePortfolio(
                transactions = filterTransactionsByPlatform(transactions, platform),
                quotes = quotes,
                exchangeRates = exchangeRates,
            )
            labels[platform] = "总资产 ${formatDisplayAmount(portfolio.totalAssetsCny, displayCurrency, exchangeRates)}"
        }
        return labels
    }

    private fun buildSellCandidates(
        positions: Map<String, PositionComputation>,
    ): List<SellCandidateUiModel> = positions.values
        .filter { it.quantity != 0 }
        .sortedByDescending { it.quantity }
        .map { position ->
            SellCandidateUiModel(
                symbol = position.symbol,
                name = position.name,
                market = position.market,
                quantityLabel = if (position.quantity > 0) {
                    "可卖 ${position.quantity} 股"
                } else {
                    "空仓 ${-position.quantity} 股"
                },
                costLabel = "成本 ${formatMarketAmount(position.averageCost, position.market)}",
            )
        }

    private fun buildTransactionSections(
        transactions: List<TransactionEntity>,
        tradeFilter: TransactionFilter,
        marketFilter: MarketFilter,
        keyword: String,
        startDate: String?,
        endDate: String?,
    ): List<TransactionSection> {
        val parsedStartDate = startDate?.let(::parseTradeDateOrNull)
        val parsedEndDate = endDate?.let(::parseTradeDateOrNull)
        val normalizedKeyword = keyword.trim().lowercase()

        val filtered = transactions.filter { transaction ->
            // 1. Trade Type Filter
            if (tradeFilter.tradeType != null && transaction.tradeType != tradeFilter.tradeType.name) return@filter false

            // 2. Market Filter
            if (marketFilter.market != null && transaction.market != marketFilter.market.name) return@filter false

            // 3. Keyword Filter
            if (normalizedKeyword.isNotBlank()) {
                val match = transaction.symbol.lowercase().contains(normalizedKeyword) ||
                    transaction.name.lowercase().contains(normalizedKeyword)
                if (!match) return@filter false
            }

            // 4. Date Filter
            if (parsedStartDate != null || parsedEndDate != null) {
                val tradeDate = parseTradeDateOrNull(transaction.tradeDate) ?: return@filter false
                if (parsedStartDate != null && tradeDate.isBefore(parsedStartDate)) return@filter false
                if (parsedEndDate != null && tradeDate.isAfter(parsedEndDate)) return@filter false
            }

            true
        }

        return filtered
            .groupBy { it.tradeDate }
            .toList()
            .sortedByDescending { (date, _) -> date } // Assuming ISO format "yyyy-MM-dd" allows string sorting
            .map { (date, entries) ->
                TransactionSection(
                    title = displayDate(date),
                    items = entries.map { transaction ->
                        val market = Market.fromString(transaction.market) ?: Market.CASH
                        val tradeType = TradeType.valueOf(transaction.tradeType)
                        val platform = runCatching { BrokerPlatform.valueOf(transaction.platform) }.getOrDefault(BrokerPlatform.UNSPECIFIED)
                        val cashFlow = transactionCashFlow(transaction, tradeType)
                        TransactionUiModel(
                            id = transaction.id,
                            tradeType = tradeType,
                            stockName = if (tradeType.isSecurityTrade) {
                                "${transaction.symbol} ${transaction.name}"
                            } else {
                                CASH_ACCOUNT_NAME
                            },
                            primaryMeta = if (tradeType.isSecurityTrade) {
                                "${platform.label} · ${market.label}"
                            } else {
                                platform.label
                            },
                            secondaryMeta = if (tradeType.isSecurityTrade) {
                                "成交价 ${formatMarketAmount(transaction.price, market)} · ${transaction.quantity} 股"
                            } else {
                                transaction.note.ifBlank { "现金账户流水" }
                            },
                            amountLabel = formatSignedMarketAmount(cashFlow, market),
                            timeLabel = transaction.tradeTime,
                            feeLabel = if (tradeType.isSecurityTrade) {
                                "费用 ${formatMarketAmount(transaction.commission + transaction.tax, market)}"
                            } else {
                                "净变动 ${formatMarketAmount(transaction.price * transaction.quantity, market)}"
                            },
                            platform = platform,
                            platformLabel = platform.label,
                        )
                    },
                )
            }
    }

    private fun buildProfitAnalysis(
        portfolio: PortfolioComputation,
        transactions: List<TransactionEntity>,
        exchangeRates: ExchangeRates,
        historicalCloses: List<HistoricalClosePoint>,
        quotes: List<QuoteSnapshotEntity>,
    ): ProfitAnalysisUiModel {
        val securityMeta = transactions
            .filter { TradeType.valueOf(it.tradeType).isSecurityTrade && it.symbol.isNotBlank() }
            .distinctBy { positionKey(it.symbol, Market.fromString(it.market) ?: Market.CASH) }
            .associate { transaction ->
                val market = Market.fromString(transaction.market) ?: Market.CASH

                positionKey(transaction.symbol, market) to ResolvedSecurity(
                    symbol = transaction.symbol,
                    name = transaction.name,
                    market = market,
                )
            }

        if (historicalCloses.isEmpty()) {
            return buildProfitAnalysisFromRealizedAndCurrent(
                portfolio = portfolio,
                transactions = transactions,
                exchangeRates = exchangeRates,
                quotes = quotes,
                securityMeta = securityMeta,
            )
        }

        val ordered = transactions.sortedWith(
            compareBy<TransactionEntity>({ it.tradeDate }, { it.tradeTime }, { it.createdAt }),
        )
        val datedTransactions = ordered.mapNotNull { transaction ->
            val market = Market.fromString(transaction.market) ?: Market.CASH
            val date = effectiveTradeDate(transaction.tradeDate, transaction.tradeTime, market)
            date to transaction
        }
        val transactionMap = datedTransactions.groupBy({ it.first }, { it.second })
        val historyByDate = historicalCloses
            .groupBy { it.date }
            .mapValues { (_, values) -> values.sortedBy { it.symbol } }
        val firstDate = datedTransactions.minOfOrNull { it.first }
            ?: historicalCloses.minOfOrNull { it.date }
            ?: LocalDate.now()
        val latestHistoryDate = historicalCloses.maxOfOrNull { it.date }
        val latestDate = maxOf(
            LocalDate.now(),
            datedTransactions.maxOfOrNull { it.first } ?: LocalDate.now(),
            latestHistoryDate ?: LocalDate.now(),
        )

        val positions = linkedMapOf<String, PositionComputation>()
        val latestCloseByPosition = mutableMapOf<String, Double>()
        var cashBalanceCny = 0.0
        var totalDepositCny = 0.0
        var totalWithdrawCny = 0.0
        val dailyPoints = mutableListOf<ProfitAnalysisPointUiModel>()
        val securitySeries = securityMeta.mapValues { mutableListOf<SecurityProfitPointUiModel>() }.toMutableMap()
        val previousSecurityProfit = mutableMapOf<String, Double>()
        var cursor = firstDate
        var previousCumulativeProfit = 0.0
        var previousTotalAssets = 0.0
        var cumulativeNav = 1.0
        var hasPreviousPoint = false
        while (!cursor.isAfter(latestDate)) {
            var dailyNetFlowCny = 0.0
            transactionMap[cursor].orEmpty().forEach { transaction ->
                val market = Market.fromString(transaction.market) ?: Market.CASH
                when (val tradeType = TradeType.valueOf(transaction.tradeType)) {
                    TradeType.DEPOSIT -> {
                        val amountCny = convertToCny(transaction.price * transaction.quantity, market, exchangeRates)
                        cashBalanceCny += amountCny
                        totalDepositCny += amountCny
                        dailyNetFlowCny += amountCny
                    }

                    TradeType.WITHDRAW -> {
                        val amountCny = convertToCny(transaction.price * transaction.quantity, market, exchangeRates)
                        cashBalanceCny -= amountCny
                        totalWithdrawCny += amountCny
                        dailyNetFlowCny -= amountCny
                    }

                    TradeType.BUY, TradeType.SELL -> {
                        val key = positionKey(transaction.symbol, market)
                        val current = positions[key]
                            ?: PositionComputation(
                                symbol = transaction.symbol,
                                name = transaction.name,
                                market = market,
                                quantity = 0,
                                averageCost = 0.0,
                                remainingCost = 0.0,
                                realizedProfit = 0.0,
                            )
                        if (tradeType == TradeType.BUY) {
                            if (current.quantity < 0) {
                                // Covering short position
                                val coverQuantity = minOf(-current.quantity, transaction.quantity)
                                val coverProfit = (current.averageCost - transaction.price) * coverQuantity
                                val coverFees = transaction.commission + transaction.tax
                                val remainingBuyQty = transaction.quantity - coverQuantity
                                val totalCost = transaction.price * transaction.quantity + coverFees
                                cashBalanceCny -= convertToCny(totalCost, market, exchangeRates)
                                if (remainingBuyQty > 0) {
                                    positions[key] = PositionComputation(
                                        symbol = transaction.symbol,
                                        name = transaction.name,
                                        market = market,
                                        quantity = remainingBuyQty,
                                        remainingCost = transaction.price * remainingBuyQty,
                                        averageCost = transaction.price,
                                        realizedProfit = current.realizedProfit + coverProfit - coverFees,
                                    )
                                } else {
                                    val nextQuantity = current.quantity + transaction.quantity
                                    val nextRemaining = if (nextQuantity == 0) 0.0 else current.remainingCost * (nextQuantity.toDouble() / current.quantity.toDouble())
                                    positions[key] = current.copy(
                                        quantity = nextQuantity,
                                        remainingCost = nextRemaining,
                                        averageCost = if (nextQuantity == 0) 0.0 else nextRemaining / nextQuantity,
                                        realizedProfit = current.realizedProfit + coverProfit - coverFees,
                                    )
                                }
                            } else {
                                val buyCost = transaction.price * transaction.quantity + transaction.commission + transaction.tax
                                val nextQuantity = current.quantity + transaction.quantity
                                val nextRemaining = current.remainingCost + buyCost
                                cashBalanceCny -= convertToCny(buyCost, market, exchangeRates)
                                positions[key] = current.copy(
                                    quantity = nextQuantity,
                                    remainingCost = nextRemaining,
                                    averageCost = if (nextQuantity == 0) 0.0 else nextRemaining / nextQuantity,
                                )
                            }
                        } else {
                            // SELL
                            if (current.quantity > 0) {
                                val closeQuantity = minOf(current.quantity, transaction.quantity)
                                val removedCost = current.averageCost * closeQuantity
                                val closeProceeds = transaction.price * closeQuantity
                                val closeProfit = closeProceeds - removedCost
                                val remainingSellQty = transaction.quantity - closeQuantity
                                val totalProceeds = transaction.price * transaction.quantity - transaction.commission - transaction.tax
                                cashBalanceCny += convertToCny(totalProceeds, market, exchangeRates)
                                if (remainingSellQty > 0) {
                                    positions[key] = PositionComputation(
                                        symbol = transaction.symbol,
                                        name = transaction.name,
                                        market = market,
                                        quantity = -remainingSellQty,
                                        remainingCost = -(transaction.price * remainingSellQty),
                                        averageCost = transaction.price,
                                        realizedProfit = current.realizedProfit + closeProfit,
                                    )
                                } else {
                                    val nextQuantity = current.quantity - closeQuantity
                                    val nextRemaining = if (nextQuantity == 0) 0.0 else current.remainingCost - removedCost
                                    positions[key] = current.copy(
                                        quantity = nextQuantity,
                                        remainingCost = nextRemaining,
                                        averageCost = if (nextQuantity == 0) 0.0 else nextRemaining / nextQuantity,
                                        realizedProfit = current.realizedProfit + closeProfit,
                                    )
                                }
                            } else {
                                val totalProceeds = transaction.price * transaction.quantity - transaction.commission - transaction.tax
                                cashBalanceCny += convertToCny(totalProceeds, market, exchangeRates)
                                val nextQuantity = current.quantity - transaction.quantity
                                val nextRemaining = current.remainingCost - (transaction.price * transaction.quantity)
                                positions[key] = current.copy(
                                    quantity = nextQuantity,
                                    remainingCost = nextRemaining,
                                    averageCost = if (nextQuantity == 0) 0.0 else nextRemaining / nextQuantity,
                                )
                            }
                        }
                    }
                }
            }

            historyByDate[cursor].orEmpty().forEach { closePoint ->
                latestCloseByPosition[positionKey(closePoint.symbol, closePoint.market)] = closePoint.closePrice
            }

            val holdingsValueCny = positions.values.sumOf { position ->
                if (position.quantity == 0) return@sumOf 0.0
                val key = positionKey(position.symbol, position.market)
                val closePrice = latestCloseByPosition[key] ?: position.averageCost
                convertToCny(closePrice * position.quantity, position.market, exchangeRates)
            }
            val netInflowCny = totalDepositCny - totalWithdrawCny
            val totalAssetsCny = holdingsValueCny + cashBalanceCny
            val cumulativeProfit = totalAssetsCny - netInflowCny
            val dailyProfit = if (hasPreviousPoint) {
                cumulativeProfit - previousCumulativeProfit
            } else {
                cumulativeProfit
            }
            val dailyReturnPercent = if (hasPreviousPoint && previousTotalAssets > 0.0) {
                ((totalAssetsCny - dailyNetFlowCny - previousTotalAssets) / previousTotalAssets) * 100.0
            } else {
                0.0
            }
            cumulativeNav = if (hasPreviousPoint) {
                cumulativeNav * (1 + dailyReturnPercent / 100.0)
            } else {
                1.0
            }
            dailyPoints += ProfitAnalysisPointUiModel(
                date = cursor,
                dailyProfitCny = dailyProfit,
                cumulativeProfitCny = cumulativeProfit,
                totalAssetsCny = totalAssetsCny,
                netInflowCny = netInflowCny,
                dailyReturnPercent = dailyReturnPercent,
                cumulativeReturnPercent = (cumulativeNav - 1.0) * 100.0,
            )
            securityMeta.forEach { (key, security) ->
                val position = positions[key]
                val realizedCny = position?.let { convertToCny(it.realizedProfit, it.market, exchangeRates) } ?: 0.0
                val unrealizedCny = position?.takeIf { it.quantity != 0 }?.let {
                    val closePrice = latestCloseByPosition[key] ?: it.averageCost
                    convertToCny((closePrice - it.averageCost) * it.quantity, it.market, exchangeRates)
                } ?: 0.0
                val cumulativeSecurityProfit = realizedCny + unrealizedCny
                val previousSecurityCumulative = previousSecurityProfit[key] ?: 0.0
                securitySeries.getValue(key) += SecurityProfitPointUiModel(
                    date = cursor,
                    dailyProfitCny = if (hasPreviousPoint) {
                        cumulativeSecurityProfit - previousSecurityCumulative
                    } else {
                        cumulativeSecurityProfit
                    },
                    cumulativeProfitCny = cumulativeSecurityProfit,
                    closePrice = latestCloseByPosition[key],
                )
                previousSecurityProfit[key] = cumulativeSecurityProfit
            }
            previousCumulativeProfit = cumulativeProfit
            previousTotalAssets = totalAssetsCny
            hasPreviousPoint = true
            cursor = cursor.plusDays(1)
        }

        // Unify today's P&L with Holdings screen (live mark-to-market)
        val today = LocalDate.now()
        if (dailyPoints.isNotEmpty() && dailyPoints.last().date == today) {
            val lastIdx = dailyPoints.lastIndex
            val previousCumulative = if (lastIdx > 0) dailyPoints[lastIdx - 1].cumulativeProfitCny else 0.0
            val liveDailyProfit = portfolio.dayProfitCny
            dailyPoints[lastIdx] = dailyPoints[lastIdx].copy(
                dailyProfitCny = liveDailyProfit,
                cumulativeProfitCny = previousCumulative + liveDailyProfit,
            )
        }

        return ProfitAnalysisUiModel(
            dailyPoints = dailyPoints,
            securityAnalyses = securityMeta.map { (key, security) ->
                SecurityProfitAnalysisUiModel(
                    symbol = security.symbol,
                    name = security.name,
                    market = security.market,
                    dailyPoints = securitySeries[key].orEmpty(),
                )
            },
            netInflowCny = totalDepositCny - totalWithdrawCny,
            latestDate = latestDate,
            totalCommissionCny = portfolio.totalCommissionCny,
            totalTaxCny = portfolio.totalTaxCny,
            securityTradeCount = portfolio.securityTradeCount,
            transactions = transactions,
        )
    }

    private fun buildProfitAnalysisFromRealizedAndCurrent(
        portfolio: PortfolioComputation,
        transactions: List<TransactionEntity>,
        exchangeRates: ExchangeRates,
        quotes: List<QuoteSnapshotEntity>,
        securityMeta: Map<String, ResolvedSecurity>,
    ): ProfitAnalysisUiModel {
        val ordered = transactions.sortedWith(
            compareBy<TransactionEntity>({ it.tradeDate }, { it.tradeTime }, { it.createdAt }),
        )
        val positions = linkedMapOf<String, PositionComputation>()
        val dailyProfitByDate = linkedMapOf<LocalDate, Double>()
        val netFlowByDate = linkedMapOf<LocalDate, Double>()
        val securityProfitByDate = mutableMapOf<String, MutableMap<LocalDate, Double>>()
        val quoteMap = quotes.associateBy { positionKey(it.symbol, Market.fromString(it.market) ?: Market.CASH) }

        ordered.forEach { transaction ->
            val market = Market.fromString(transaction.market) ?: return@forEach
            val date = effectiveTradeDate(transaction.tradeDate, transaction.tradeTime, market)
            when (val tradeType = TradeType.valueOf(transaction.tradeType)) {
                TradeType.BUY -> {
                    val key = positionKey(transaction.symbol, market)
                    val current = positions[key]
                        ?: PositionComputation(
                            symbol = transaction.symbol,
                            name = transaction.name,
                            market = market,
                            quantity = 0,
                            averageCost = 0.0,
                            remainingCost = 0.0,
                            realizedProfit = 0.0,
                        )
                    if (current.quantity < 0) {
                        // Covering short position
                        val coverQuantity = minOf(-current.quantity, transaction.quantity)
                        val coverProfit = (current.averageCost - transaction.price) * coverQuantity
                        val coverFees = transaction.commission + transaction.tax
                        val coverProfitCny = convertToCny(coverProfit - coverFees, market, exchangeRates)
                        dailyProfitByDate[date] = dailyProfitByDate.getOrDefault(date, 0.0) + coverProfitCny
                        val securityDailyProfit = securityProfitByDate.getOrPut(key) { linkedMapOf() }
                        securityDailyProfit[date] = securityDailyProfit.getOrDefault(date, 0.0) + coverProfitCny
                        val remainingBuyQty = transaction.quantity - coverQuantity
                        if (remainingBuyQty > 0) {
                            positions[key] = PositionComputation(
                                symbol = transaction.symbol,
                                name = transaction.name,
                                market = market,
                                quantity = remainingBuyQty,
                                remainingCost = transaction.price * remainingBuyQty,
                                averageCost = transaction.price,
                                realizedProfit = current.realizedProfit + coverProfit - coverFees,
                            )
                        } else {
                            val nextQuantity = current.quantity + transaction.quantity
                            val nextRemaining = if (nextQuantity == 0) 0.0 else current.remainingCost * (nextQuantity.toDouble() / current.quantity.toDouble())
                            positions[key] = current.copy(
                                quantity = nextQuantity,
                                remainingCost = nextRemaining,
                                averageCost = if (nextQuantity == 0) 0.0 else nextRemaining / nextQuantity,
                                realizedProfit = current.realizedProfit + coverProfit - coverFees,
                            )
                        }
                    } else {
                        val buyCost = transaction.price * transaction.quantity + transaction.commission + transaction.tax
                        val nextQuantity = current.quantity + transaction.quantity
                        val nextRemaining = current.remainingCost + buyCost
                        positions[key] = current.copy(
                            quantity = nextQuantity,
                            remainingCost = nextRemaining,
                            averageCost = if (nextQuantity == 0) 0.0 else nextRemaining / nextQuantity,
                        )
                    }
                }

                TradeType.SELL -> {
                    val key = positionKey(transaction.symbol, market)
                    val current = positions[key]
                        ?: PositionComputation(
                            symbol = transaction.symbol,
                            name = transaction.name,
                            market = market,
                            quantity = 0,
                            averageCost = 0.0,
                            remainingCost = 0.0,
                            realizedProfit = 0.0,
                        )
                    if (current.quantity > 0) {
                        // Has long position: sell to close, may open short
                        val closeQuantity = minOf(current.quantity, transaction.quantity)
                        val removedCost = current.averageCost * closeQuantity
                        val closeProceeds = transaction.price * closeQuantity
                        val closeProfit = closeProceeds - removedCost
                        val closeProfitCny = convertToCny(closeProfit, market, exchangeRates)
                        dailyProfitByDate[date] = dailyProfitByDate.getOrDefault(date, 0.0) + closeProfitCny
                        val securityDailyProfit = securityProfitByDate.getOrPut(key) { linkedMapOf() }
                        securityDailyProfit[date] = securityDailyProfit.getOrDefault(date, 0.0) + closeProfitCny
                        val remainingSellQty = transaction.quantity - closeQuantity
                        if (remainingSellQty > 0) {
                            positions[key] = PositionComputation(
                                symbol = transaction.symbol,
                                name = transaction.name,
                                market = market,
                                quantity = -remainingSellQty,
                                remainingCost = -(transaction.price * remainingSellQty),
                                averageCost = transaction.price,
                                realizedProfit = current.realizedProfit + closeProfit,
                            )
                        } else {
                            val nextQuantity = current.quantity - closeQuantity
                            val nextRemaining = if (nextQuantity == 0) 0.0 else current.remainingCost - removedCost
                            positions[key] = current.copy(
                                quantity = nextQuantity,
                                remainingCost = nextRemaining,
                                averageCost = if (nextQuantity == 0) 0.0 else nextRemaining / nextQuantity,
                                realizedProfit = current.realizedProfit + closeProfit,
                            )
                        }
                    } else {
                        // No long position: open/extend short, no realized profit yet
                        val totalProceeds = transaction.price * transaction.quantity - transaction.commission - transaction.tax
                        val nextQuantity = current.quantity - transaction.quantity
                        val nextRemaining = current.remainingCost - (transaction.price * transaction.quantity)
                        positions[key] = current.copy(
                            quantity = nextQuantity,
                            remainingCost = nextRemaining,
                            averageCost = if (nextQuantity == 0) 0.0 else nextRemaining / nextQuantity,
                        )
                    }
                }

                TradeType.DEPOSIT -> {
                    val amountCny = convertToCny(transaction.price * transaction.quantity, market, exchangeRates)
                    netFlowByDate[date] = netFlowByDate.getOrDefault(date, 0.0) + amountCny
                }

                TradeType.WITHDRAW -> {
                    val amountCny = convertToCny(transaction.price * transaction.quantity, market, exchangeRates)
                    netFlowByDate[date] = netFlowByDate.getOrDefault(date, 0.0) - amountCny
                }
            }
        }

        val lastTransactionDate = ordered.mapNotNull { parseTradeDateOrNull(it.tradeDate) }.maxOrNull()
        val latestDate = maxOf(LocalDate.now(), lastTransactionDate ?: LocalDate.now())
        val realizedTotalCny = dailyProfitByDate.values.sum()
        val openProfitAdjustmentCny = portfolio.totalProfitCny - realizedTotalCny
        dailyProfitByDate[latestDate] = dailyProfitByDate.getOrDefault(latestDate, 0.0) + openProfitAdjustmentCny
        positions.values.forEach { position ->
            if (position.quantity == 0) return@forEach
            val key = positionKey(position.symbol, position.market)
            val currentPrice = quoteMap[key]?.currentPrice ?: position.averageCost
            val unrealizedCny = convertToCny(
                (currentPrice - position.averageCost) * position.quantity,
                position.market,
                exchangeRates,
            )
            val seriesForSecurity = securityProfitByDate.getOrPut(key) { linkedMapOf() }
            seriesForSecurity[latestDate] = seriesForSecurity.getOrDefault(latestDate, 0.0) + unrealizedCny
        }

        val firstDate = listOf(
            dailyProfitByDate.keys.minOrNull(),
            netFlowByDate.keys.minOrNull(),
        ).filterNotNull().minOrNull() ?: latestDate
        val dailyPoints = mutableListOf<ProfitAnalysisPointUiModel>()
        val securitySeries = securityMeta.mapValues { mutableListOf<SecurityProfitPointUiModel>() }.toMutableMap()
        var cursor = firstDate
        var cumulativeProfit = 0.0
        var cumulativeNetInflow = 0.0
        var previousTotalAssets = 0.0
        var cumulativeNav = 1.0
        var hasPreviousPoint = false
        while (!cursor.isAfter(latestDate)) {
            val dailyProfit = dailyProfitByDate.getOrDefault(cursor, 0.0)
            val dailyNetFlow = netFlowByDate.getOrDefault(cursor, 0.0)
            cumulativeProfit += dailyProfit
            cumulativeNetInflow += dailyNetFlow
            val totalAssetsCny = cumulativeNetInflow + cumulativeProfit
            val dailyReturnPercent = if (hasPreviousPoint && previousTotalAssets > 0.0) {
                ((totalAssetsCny - dailyNetFlow - previousTotalAssets) / previousTotalAssets) * 100.0
            } else {
                0.0
            }
            cumulativeNav = if (hasPreviousPoint) {
                cumulativeNav * (1 + dailyReturnPercent / 100.0)
            } else {
                1.0
            }
            dailyPoints += ProfitAnalysisPointUiModel(
                date = cursor,
                dailyProfitCny = dailyProfit,
                cumulativeProfitCny = cumulativeProfit,
                totalAssetsCny = totalAssetsCny,
                netInflowCny = cumulativeNetInflow,
                dailyReturnPercent = dailyReturnPercent,
                cumulativeReturnPercent = (cumulativeNav - 1.0) * 100.0,
            )
            securityMeta.forEach { (key, security) ->
                val seriesMap = securityProfitByDate[key].orEmpty()
                val priorCumulative = securitySeries[key]?.lastOrNull()?.cumulativeProfitCny ?: 0.0
                val securityDailyProfit = seriesMap[cursor] ?: 0.0
                securitySeries.getValue(key) += SecurityProfitPointUiModel(
                    date = cursor,
                    dailyProfitCny = securityDailyProfit,
                    cumulativeProfitCny = priorCumulative + securityDailyProfit,
                    closePrice = null,
                )
            }
            previousTotalAssets = totalAssetsCny
            hasPreviousPoint = true
            cursor = cursor.plusDays(1)
        }

        // Unify today's P&L with Holdings screen (live mark-to-market)
        val today = LocalDate.now()
        if (dailyPoints.isNotEmpty() && dailyPoints.last().date == today) {
            val lastIdx = dailyPoints.lastIndex
            val previousCumulative = if (lastIdx > 0) dailyPoints[lastIdx - 1].cumulativeProfitCny else 0.0
            val liveDailyProfit = portfolio.dayProfitCny
            dailyPoints[lastIdx] = dailyPoints[lastIdx].copy(
                dailyProfitCny = liveDailyProfit,
                cumulativeProfitCny = previousCumulative + liveDailyProfit,
            )
        }

        return ProfitAnalysisUiModel(
            dailyPoints = dailyPoints,
            securityAnalyses = securityMeta.map { (key, security) ->
                SecurityProfitAnalysisUiModel(
                    symbol = security.symbol,
                    name = security.name,
                    market = security.market,
                    dailyPoints = securitySeries[key].orEmpty(),
                )
            },
            netInflowCny = portfolio.netInflowCny,
            latestDate = latestDate,
            totalCommissionCny = portfolio.totalCommissionCny,
            totalTaxCny = portfolio.totalTaxCny,
            securityTradeCount = portfolio.securityTradeCount,
            transactions = transactions,
        )
    }

    private fun computePortfolio(
        transactions: List<TransactionEntity>,
        quotes: List<QuoteSnapshotEntity>,
        exchangeRates: ExchangeRates,
    ): PortfolioComputation {
        val positions = linkedMapOf<String, PositionComputation>()
        var cashBalanceCny = 0.0
        var totalDepositCny = 0.0
        var totalWithdrawCny = 0.0
        var totalCommissionCny = 0.0
        var totalTaxCny = 0.0
        var securityTradeCount = 0
        val ordered = transactions.sortedWith(
            compareBy<TransactionEntity>({
                val m = Market.fromString(it.market) ?: Market.CASH
                effectiveTradeDate(it.tradeDate, it.tradeTime, m).toString()
            }, { it.tradeTime }, { it.createdAt }),
        )

        ordered.forEach { transaction ->
            val market = Market.fromString(transaction.market) ?: Market.CASH
            when (val tradeType = TradeType.valueOf(transaction.tradeType)) {
                TradeType.DEPOSIT -> {
                    val amountCny = convertToCny(transaction.price * transaction.quantity, market, exchangeRates)
                    cashBalanceCny += amountCny
                    totalDepositCny += amountCny
                }

                TradeType.WITHDRAW -> {
                    val amountCny = convertToCny(transaction.price * transaction.quantity, market, exchangeRates)
                    cashBalanceCny -= amountCny
                    totalWithdrawCny += amountCny
                }

                TradeType.BUY, TradeType.SELL -> {
                    totalCommissionCny += convertToCny(transaction.commission, market, exchangeRates)
                    totalTaxCny += convertToCny(transaction.tax, market, exchangeRates)
                    securityTradeCount += 1
                    val key = positionKey(transaction.symbol, market)
                    val current = positions[key]
                        ?: PositionComputation(
                            symbol = transaction.symbol,
                            name = transaction.name,
                            market = market,
                            quantity = 0,
                            averageCost = 0.0,
                            remainingCost = 0.0,
                            realizedProfit = 0.0,
                        )

                    positions[key] = if (tradeType == TradeType.BUY) {
                        if (current.quantity < 0) {
                            // Covering short position
                            val coverQuantity = minOf(-current.quantity, transaction.quantity)
                            val coverProfit = (current.averageCost - transaction.price) * coverQuantity
                            val coverFees = transaction.commission + transaction.tax
                            val remainingBuyQty = transaction.quantity - coverQuantity
                            val totalCost = transaction.price * transaction.quantity + coverFees
                            cashBalanceCny -= convertToCny(totalCost, market, exchangeRates)
                            if (remainingBuyQty > 0) {
                                // Covered fully, opened long with remainder
                                PositionComputation(
                                    symbol = transaction.symbol,
                                    name = transaction.name,
                                    market = market,
                                    quantity = remainingBuyQty,
                                    remainingCost = transaction.price * remainingBuyQty,
                                    averageCost = transaction.price,
                                    realizedProfit = current.realizedProfit + coverProfit - coverFees,
                                )
                            } else {
                                // Partial or full cover, no new position
                                val nextQuantity = current.quantity + transaction.quantity
                                val nextRemaining = if (nextQuantity == 0) 0.0 else current.remainingCost * (nextQuantity.toDouble() / current.quantity.toDouble())
                                current.copy(
                                    quantity = nextQuantity,
                                    remainingCost = nextRemaining,
                                    averageCost = if (nextQuantity == 0) 0.0 else nextRemaining / nextQuantity,
                                    realizedProfit = current.realizedProfit + coverProfit - coverFees,
                                )
                            }
                        } else {
                            // Normal buy
                            val buyCost = transaction.price * transaction.quantity + transaction.commission + transaction.tax
                            val nextQuantity = current.quantity + transaction.quantity
                            val nextRemaining = current.remainingCost + buyCost
                            cashBalanceCny -= convertToCny(buyCost, market, exchangeRates)
                            current.copy(
                                quantity = nextQuantity,
                                remainingCost = nextRemaining,
                                averageCost = if (nextQuantity == 0) 0.0 else nextRemaining / nextQuantity,
                            )
                        }
                    } else {
                        // SELL
                        if (current.quantity > 0) {
                            // Has long position: sell to close, may open short if oversold
                            val closeQuantity = minOf(current.quantity, transaction.quantity)
                            val removedCost = current.averageCost * closeQuantity
                            val closeProceeds = transaction.price * closeQuantity
                            val closeProfit = closeProceeds - removedCost
                            val remainingSellQty = transaction.quantity - closeQuantity
                            val totalProceeds = transaction.price * transaction.quantity - transaction.commission - transaction.tax
                            cashBalanceCny += convertToCny(totalProceeds, market, exchangeRates)
                            if (remainingSellQty > 0) {
                                // Closed long, opened short with remainder
                                PositionComputation(
                                    symbol = transaction.symbol,
                                    name = transaction.name,
                                    market = market,
                                    quantity = -remainingSellQty,
                                    remainingCost = -(transaction.price * remainingSellQty),
                                    averageCost = transaction.price,
                                    realizedProfit = current.realizedProfit + closeProfit,
                                )
                            } else {
                                // Partial or full close of long
                                val nextQuantity = current.quantity - closeQuantity
                                val nextRemaining = if (nextQuantity == 0) 0.0 else current.remainingCost - removedCost
                                current.copy(
                                    quantity = nextQuantity,
                                    remainingCost = nextRemaining,
                                    averageCost = if (nextQuantity == 0) 0.0 else nextRemaining / nextQuantity,
                                    realizedProfit = current.realizedProfit + closeProfit,
                                )
                            }
                        } else {
                            // No long position (quantity <= 0): open/extend short
                            val totalProceeds = transaction.price * transaction.quantity - transaction.commission - transaction.tax
                            cashBalanceCny += convertToCny(totalProceeds, market, exchangeRates)
                            val nextQuantity = current.quantity - transaction.quantity
                            val nextRemaining = current.remainingCost - (transaction.price * transaction.quantity)
                            current.copy(
                                quantity = nextQuantity,
                                remainingCost = nextRemaining,
                                averageCost = if (nextQuantity == 0) 0.0 else nextRemaining / nextQuantity,
                            )
                        }
                    }
                }
            }
        }

        val quoteMap = quotes.associateBy { positionKey(it.symbol, Market.fromString(it.market) ?: Market.CASH) }
        val holdings = positions.values
            .filter { it.quantity != 0 }
            .map { position ->
                val quote = quoteMap[positionKey(position.symbol, position.market)]
                val currentPrice = quote?.currentPrice
                val previousClose = quote?.previousClose
                val unrealized = currentPrice?.let { (it - position.averageCost) * position.quantity }
                val dayProfit = if (currentPrice != null && previousClose != null) {
                    (currentPrice - previousClose) * position.quantity
                } else {
                    null
                }
                val dayPercent = if (currentPrice != null && previousClose != null && previousClose > 0.0) {
                    ((currentPrice - previousClose) / previousClose) * 100.0
                } else {
                    null
                }
                val totalProfitPercent = if (unrealized != null && position.remainingCost != 0.0) {
                    (unrealized / position.remainingCost.absoluteValue) * 100.0
                } else {
                    null
                }
                val marketValue = (currentPrice ?: position.averageCost) * position.quantity

                HoldingUiModel(
                    name = position.name,
                    code = position.symbol,
                    market = position.market,
                    quantityLabel = "${position.quantity} 股",
                    costLabel = "成本 ${formatMarketAmount(position.averageCost, position.market)}",
                    priceLabel = currentPrice?.let { formatMarketAmount(it, position.market) } ?: "--",
                    changeLabel = dayPercent?.let(::formatSignedPercent) ?: "价格暂不可用",
                    pnlLabel = unrealized?.let { labelForUnrealized(it, position.market) } ?: "价格暂不可用",
                    trend = when {
                        currentPrice == null || dayPercent == null -> PriceTrend.NEUTRAL
                        dayPercent > 0 -> PriceTrend.UP
                        dayPercent < 0 -> PriceTrend.DOWN
                        else -> PriceTrend.NEUTRAL
                    },
                    dayProfitLabel = dayProfit?.let { formatSignedMarketAmount(it, position.market) } ?: "--",
                    dayProfitPercentLabel = dayPercent?.let(::formatSignedPercent) ?: "--",
                    totalProfitLabel = unrealized?.let { formatSignedMarketAmount(it, position.market) } ?: "--",
                    totalProfitPercentLabel = totalProfitPercent?.let(::formatSignedPercent) ?: "--",
                    dayTrend = when {
                        dayProfit == null -> PriceTrend.NEUTRAL
                        dayProfit > 0 -> PriceTrend.UP
                        dayProfit < 0 -> PriceTrend.DOWN
                        else -> PriceTrend.NEUTRAL
                    },
                    totalTrend = when {
                        unrealized == null -> PriceTrend.NEUTRAL
                        unrealized > 0 -> PriceTrend.UP
                        unrealized < 0 -> PriceTrend.DOWN
                        else -> PriceTrend.NEUTRAL
                    },
                ) to marketValue
            }
            .sortedByDescending { it.second }
            .map { it.first }

        val holdingsValueCny = positions.values.sumOf { position ->
            val quote = quoteMap[positionKey(position.symbol, position.market)]
            convertToCny(position.quantity * (quote?.currentPrice ?: position.averageCost), position.market, exchangeRates)
        }
        val holdingsCostCny = positions.values.sumOf { position ->
            convertToCny(position.remainingCost, position.market, exchangeRates)
        }
        val unrealizedProfitCny = holdingsValueCny - holdingsCostCny
        val dayProfitCny = positions.values.sumOf { position ->
            val quote = quoteMap[positionKey(position.symbol, position.market)] ?: return@sumOf 0.0
            val current = quote.currentPrice ?: return@sumOf 0.0
            val previous = quote.previousClose ?: return@sumOf 0.0
            convertToCny((current - previous) * position.quantity, position.market, exchangeRates)
        }
        val previousHoldingsValueCny = positions.values.sumOf { position ->
            val quote = quoteMap[positionKey(position.symbol, position.market)] ?: return@sumOf 0.0
            val previous = quote.previousClose ?: return@sumOf 0.0
            convertToCny(previous * position.quantity, position.market, exchangeRates)
        }
        val netInflowCny = totalDepositCny - totalWithdrawCny
        val totalAssetsCny = holdingsValueCny + cashBalanceCny
        val previousAssetValueCny = previousHoldingsValueCny + cashBalanceCny

        return PortfolioComputation(
            holdings = holdings,
            positions = positions,
            totalAssetsCny = totalAssetsCny,
            holdingsValueCny = holdingsValueCny,
            cashBalanceCny = cashBalanceCny,
            totalDepositCny = totalDepositCny,
            totalWithdrawCny = totalWithdrawCny,
            netInflowCny = netInflowCny,
            totalProfitCny = totalAssetsCny - netInflowCny,
            unrealizedProfitCny = unrealizedProfitCny,
            unrealizedProfitPercent = if (holdingsCostCny == 0.0) 0.0 else {
                (unrealizedProfitCny / holdingsCostCny) * 100.0
            },
            dayProfitCny = dayProfitCny,
            dayProfitPercent = if (previousAssetValueCny == 0.0) 0.0 else {
                (dayProfitCny / previousAssetValueCny) * 100.0
            },
            totalCommissionCny = totalCommissionCny,
            totalTaxCny = totalTaxCny,
            securityTradeCount = securityTradeCount,
        )
    }

    private fun convertToCny(value: Double, market: Market, exchangeRates: ExchangeRates): Double =
        value * exchangeRates.rateToCny(market)

    private fun convertFromCny(
        value: Double,
        currency: DisplayCurrency,
        exchangeRates: ExchangeRates,
    ): Double = value / exchangeRates.rateToCny(currency)

    private fun formatDisplayAmount(
        valueCny: Double,
        currency: DisplayCurrency,
        exchangeRates: ExchangeRates,
    ): String {
        val amount = convertFromCny(valueCny, currency, exchangeRates)
        return "${currency.symbol}${numberFormatter.format(amount.absoluteValue)}"
    }

    private fun formatSignedDisplayAmount(
        valueCny: Double,
        currency: DisplayCurrency,
        exchangeRates: ExchangeRates,
    ): String {
        val sign = if (valueCny >= 0) "+" else "-"
        return "$sign${formatDisplayAmount(valueCny, currency, exchangeRates)}"
    }

    private fun formatMarketAmount(value: Double, market: Market): String =
        "${market.currencySymbol}${numberFormatter.format(value.absoluteValue)}"

    private fun formatSignedMarketAmount(value: Double, market: Market): String {
        val sign = if (value >= 0) "+" else "-"
        return "$sign${formatMarketAmount(value, market)}"
    }

    private fun formatSignedPercent(value: Double): String {
        val sign = if (value >= 0) "+" else "-"
        return "$sign${percentFormatter.format(value.absoluteValue)}%"
    }

    private fun labelForUnrealized(value: Double, market: Market): String {
        val prefix = if (value >= 0) "浮盈" else "浮亏"
        val sign = if (value >= 0) "+" else "-"
        return "$prefix $sign${formatMarketAmount(value, market)}"
    }

    private fun displayDate(date: String): String {
        val parsed = parseTradeDateOrNull(date) ?: return date
        val today = LocalDate.now()
        val prefix = when (parsed) {
            today -> "今天"
            today.minusDays(1) -> "昨天"
            else -> parsed.format(dateFormatter)
        }
        return "$prefix · ${parsed.format(monthDayFormatter)}"
    }

    private fun parseTradeDateOrNull(value: String): LocalDate? = runCatching {
        LocalDate.parse(value)
    }.getOrNull()

    private fun effectiveTradeDate(tradeDate: String, tradeTime: String, market: Market): LocalDate {
        val date = parseTradeDateOrNull(tradeDate) ?: LocalDate.parse(tradeDate)
        return if (market == Market.US && tradeTime < US_TIMEZONE_CUTOFF) {
            date.minusDays(1)
        } else {
            date
        }
    }

    private fun parseTradeTimeOrNull(value: String): LocalTime? = runCatching {
        LocalTime.parse(value)
    }.getOrNull()

    private fun resolveSelectedFeePlanId(platform: BrokerPlatform): String =
        TradeFeeEstimator.resolvePlanId(platform, platformFeePlanSelections.value[platform])

    private fun buildPlatformFeePlanUiModel(
        selectedPlatform: BrokerPlatform?,
        selections: Map<BrokerPlatform, String>,
    ): PlatformFeePlanUiModel? {
        val platform = selectedPlatform ?: return null
        val options = TradeFeeEstimator.availablePlans(platform)
        if (options.isEmpty()) return null
        val selectedPlanId = TradeFeeEstimator.resolvePlanId(platform, selections[platform])
        val selectedOption = options.firstOrNull { it.id == selectedPlanId } ?: options.first()
        return PlatformFeePlanUiModel(
            platform = platform,
            selectedPlanId = selectedOption.id,
            selectedPlanLabel = selectedOption.label,
            selectedPlanDescription = selectedOption.description,
            options = options.map { option ->
                TradeFeePlanOptionUiModel(
                    id = option.id,
                    label = option.label,
                    description = option.description,
                    isSelected = option.id == selectedOption.id,
                )
            },
        )
    }

    private fun buildTradeFeeEstimateContext(draft: TradeFormState): TradeFeeEstimateContext {
        val promo = zhuoruiPromoConfig.value
        val tradeDate = parseTradeDateOrNull(draft.tradeDate)
        return TradeFeeEstimateContext(
            monthlyTurnoverHkdBeforeTrade = if (draft.platform == BrokerPlatform.HSBC) {
                calculateHsbcMonthlyTurnoverHkdBeforeTrade(draft)
            } else {
                null
            },
            zhuoruiCommissionFreeEndDate = promo.endDate,
            tradeDate = tradeDate,
        )
    }

    private fun calculateHsbcMonthlyTurnoverHkdBeforeTrade(draft: TradeFormState): Double {
        val draftDate = parseTradeDateOrNull(draft.tradeDate) ?: return 0.0
        val editingSession = editingTrade.value
        val draftTime = editingSession?.tradeTime
            ?.let(::parseTradeTimeOrNull)
            ?: if (draftDate == LocalDate.now()) LocalTime.now() else LocalTime.MAX
        return transactionSnapshot.value
            .filterNot { transaction -> transaction.id == editingSession?.transactionId }
            .mapNotNull { transaction ->
                val platform = runCatching { BrokerPlatform.valueOf(transaction.platform) }.getOrDefault(BrokerPlatform.UNSPECIFIED)
                if (platform != BrokerPlatform.HSBC) return@mapNotNull null
                val tradeType = runCatching { TradeType.valueOf(transaction.tradeType) }.getOrNull()
                    ?: return@mapNotNull null
                if (!tradeType.isSecurityTrade) return@mapNotNull null
                val transactionDate = parseTradeDateOrNull(transaction.tradeDate) ?: return@mapNotNull null
                if (transactionDate.year != draftDate.year || transactionDate.month != draftDate.month) {
                    return@mapNotNull null
                }
                val transactionTime = parseTradeTimeOrNull(transaction.tradeTime) ?: LocalTime.MAX
                val isBeforeDraft = transactionDate.isBefore(draftDate) ||
                    (transactionDate == draftDate && transactionTime <= draftTime)
                if (!isBeforeDraft) return@mapNotNull null
                val market = Market.fromString(transaction.market)
                    ?: return@mapNotNull null
                amountToHkdEquivalent(transaction.price * transaction.quantity, market)
            }
            .sum()
    }

    private fun amountToHkdEquivalent(amount: Double, market: Market): Double {
        if (market == Market.HK) return amount
        val exchangeRates = repository.exchangeRates.value
        val amountCny = convertToCny(amount, market, exchangeRates)
        val hkdRateToCny = exchangeRates.rateToCny(Market.HK)
        return if (hkdRateToCny <= EPSILON) {
            amount
        } else {
            amountCny / hkdRateToCny
        }
    }

    private fun formatEditableAmount(value: Double): String =
        BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

    private fun formatRefreshTime(timestamp: Long): String {
        return Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
            .format(timeFormatter)
    }

    private fun parseDecimal(value: String): Double {
        val sanitized = value
            .replace(",", "")
            .replace("¥", "")
            .replace("HK$", "")
            .replace("$", "")
            .trim()
        return sanitized.toDoubleOrNull() ?: 0.0
    }

    private fun normalizeComparableSymbol(rawInput: String, market: Market): String = when (market) {
        Market.A_SHARE -> rawInput
            .removePrefix("SH")
            .removePrefix("SZ")
            .substringBefore(".")
            .filter(Char::isDigit)
            .trimStart('0')

        Market.HK -> rawInput
            .removePrefix("HK")
            .removeSuffix(".HK")
            .filter(Char::isDigit)
            .trimStart('0')

        Market.US -> normalizeUsLookupInput(rawInput)

        Market.CASH -> ""
    }

    private fun normalizeUsLookupInput(rawInput: String): String = rawInput
        .uppercase()
        .removePrefix("US.")
        .removePrefix("US")
        .removePrefix("GB_")
        .removeSuffix(".US")
        .replace("_", ".")

    private fun parseQuantity(value: String): Int = value.filter { it.isDigit() }.toIntOrNull() ?: 0

    private fun positionKey(symbol: String, market: Market): String = "${market.name}:$symbol"

    private fun resetDraftForType(
        type: TradeType,
        current: TradeFormState? = null,
        preferredCashMarket: Market = cashMarketFor(displayCurrency.value),
        preferredPlatform: BrokerPlatform = current?.platform
            ?: selectedPlatform.value
            ?: SampleData.tradeForm(type).platform,
    ): TradeFormState {
        val base = SampleData.tradeForm(type)
        return base.copy(
            platform = preferredPlatform,
            market = if (type.isSecurityTrade) base.market else preferredCashMarket,
            tradeDate = current?.tradeDate ?: base.tradeDate,
            note = current?.note.orEmpty(),
            priceLabel = if (current?.selectedType == type) current.priceLabel else "",
        )
    }

    private fun filterTransactionsByPlatform(
        transactions: List<TransactionEntity>,
        platform: BrokerPlatform?,
    ): List<TransactionEntity> {
        if (platform == null) return transactions
        return transactions.filter { transaction ->
            runCatching { BrokerPlatform.valueOf(transaction.platform) }.getOrDefault(BrokerPlatform.UNSPECIFIED) == platform
        }
    }

    private fun applyImportedBackup(importedBackup: ImportedBackup) {
        importedBackup.displayCurrencyName
            ?.let { savedName -> DisplayCurrency.entries.firstOrNull { it.name == savedName } }
            ?.let { currency ->
                saveDisplayCurrency(currency)
                displayCurrency.value = currency
            }
        val restoredEnabledPlatforms = importedBackup.enabledPlatforms.ifEmpty {
            BrokerPlatform.configurableEntries
        }
        enabledPlatforms.value = restoredEnabledPlatforms
        saveEnabledPlatforms(restoredEnabledPlatforms)
        val restoredSelectedPlatform = importedBackup.selectedPlatform
            ?.takeIf { it in restoredEnabledPlatforms }
        selectedPlatform.value = restoredSelectedPlatform
        saveSelectedPlatform(restoredSelectedPlatform)
        tradeFilter.value = TransactionFilter.ALL
        marketFilter.value = MarketFilter.ALL
        transactionDateStart.value = null
        transactionDateEnd.value = null
        editingTrade.value = null
        symbolLookup.value = SymbolLookupUiModel()
        symbolSuggestions.value = emptyList()
        draft.value = applyDraftRules(
            resetDraftForType(
                type = TradeType.BUY,
                preferredPlatform = restoredSelectedPlatform
                    ?: restoredEnabledPlatforms.firstOrNull()
                    ?: SampleData.tradeForm(TradeType.BUY).platform,
            ),
        )
        backupStatusMessage.value = "已导入 ${importedBackup.transactionCount} 条交易记录"
    }

    private fun cashMarketFor(currency: DisplayCurrency): Market = when (currency) {
        DisplayCurrency.USD -> Market.US
        DisplayCurrency.CNY -> Market.CASH
        DisplayCurrency.HKD -> Market.HK
    }

    private fun normalizeCashMarket(market: Market): Market = when (market) {
        Market.HK, Market.US, Market.CASH -> market
        Market.A_SHARE -> Market.CASH
    }

    private fun transactionCashFlow(transaction: TransactionEntity, tradeType: TradeType): Double = when (tradeType) {
        TradeType.BUY -> -(transaction.price * transaction.quantity + transaction.commission + transaction.tax)
        TradeType.SELL -> transaction.price * transaction.quantity - transaction.commission - transaction.tax
        TradeType.DEPOSIT -> transaction.price * transaction.quantity
        TradeType.WITHDRAW -> -(transaction.price * transaction.quantity)
    }

    private enum class RefreshTrigger {
        APP_OPEN,
        MANUAL_PULL,
        TRADE_CREATE,
        TRADE_UPDATE,
        TRADE_DELETE,
        BACKUP_IMPORT,
    }

    private data class EditingTradeSession(
        val transactionId: Long,
        val tradeTime: String,
        val createdAt: Long,
        val sourceChannel: String?,
        val externalReference: String?,
    )



    private data class PortfolioComputation(
        val holdings: List<HoldingUiModel>,
        val positions: Map<String, PositionComputation>,
        val totalAssetsCny: Double,
        val holdingsValueCny: Double,
        val cashBalanceCny: Double,
        val totalDepositCny: Double,
        val totalWithdrawCny: Double,
        val netInflowCny: Double,
        val totalProfitCny: Double,
        val unrealizedProfitCny: Double,
        val unrealizedProfitPercent: Double,
        val dayProfitCny: Double,
        val dayProfitPercent: Double,
        val totalCommissionCny: Double,
        val totalTaxCny: Double,
        val securityTradeCount: Int,
    )

    private data class PortfolioContext(
        val portfolio: PortfolioComputation,
        val draftReferencePortfolio: PortfolioComputation,
        val transactions: List<TransactionEntity>,
        val editingTrade: EditingTradeSession?,
        val historicalCloses: List<HistoricalClosePoint>,
        val quotes: List<QuoteSnapshotEntity>,
    )

    private data class PortfolioTransactionsAndFilters(
        val context: PortfolioContext,
        val filters: TransactionFilters,
    )

    private data class PortfolioTransactionsFiltersAndDraft(
        val upstream: PortfolioTransactionsAndFilters,
        val draft: TradeFormState,
    )

    private data class PortfolioTransactionsFiltersDraftAndRefresh(
        val upstream: PortfolioTransactionsFiltersAndDraft,
        val refresh: RefreshMeta,
    )

    private data class TransactionFilters(
        val tradeFilter: TransactionFilter,
        val marketFilter: MarketFilter,
        val keyword: String,
        val startDate: String?,
        val endDate: String?,
    )

    private data class PlatformSetup(
        val enabled: List<BrokerPlatform>,
        val selected: BrokerPlatform?,
    )

private data class RefreshMeta(
        val refresh: RefreshState,
        val refreshedAt: Long,
        val note: String,
        val showPullRefreshTime: Boolean,
    )

    private fun emptyPortfolioComputation(): PortfolioComputation = PortfolioComputation(
        holdings = emptyList(),
        positions = emptyMap(),
        totalAssetsCny = 0.0,
        holdingsValueCny = 0.0,
        cashBalanceCny = 0.0,
        totalDepositCny = 0.0,
        totalWithdrawCny = 0.0,
        netInflowCny = 0.0,
        totalProfitCny = 0.0,
        unrealizedProfitCny = 0.0,
        unrealizedProfitPercent = 0.0,
        dayProfitCny = 0.0,
        dayProfitPercent = 0.0,
        totalCommissionCny = 0.0,
        totalTaxCny = 0.0,
        securityTradeCount = 0,
    )

    private fun loadSavedDisplayCurrency(): DisplayCurrency {
        val savedName = preferences.getString(
            StockLedgerPreferences.KEY_DISPLAY_CURRENCY,
            DisplayCurrency.CNY.name,
        )
        return DisplayCurrency.entries.firstOrNull { it.name == savedName } ?: DisplayCurrency.CNY
    }

    private fun saveDisplayCurrency(currency: DisplayCurrency) {
        preferences.edit()
            .putString(StockLedgerPreferences.KEY_DISPLAY_CURRENCY, currency.name)
            .apply()
    }

    private fun loadSavedSelectedPlatform(): BrokerPlatform? {
        val savedName = preferences.getString(StockLedgerPreferences.KEY_SELECTED_PLATFORM, null).orEmpty()
        return BrokerPlatform.entries.firstOrNull { it.name == savedName && it.isConfigurable }
    }

    private fun loadEnabledPlatforms(): List<BrokerPlatform> {
        val saved = preferences.getStringSet(StockLedgerPreferences.KEY_ENABLED_PLATFORMS, null)
            ?.mapNotNull { name -> BrokerPlatform.entries.firstOrNull { it.name == name && it.isConfigurable } }
            .orEmpty()
        return if (saved.isEmpty()) {
            BrokerPlatform.configurableEntries
        } else {
            BrokerPlatform.configurableEntries.filter { it in saved }
        }
    }

    private fun loadPlatformFeePlanSelections(): Map<BrokerPlatform, String> {
        val serialized = preferences.getString(
            StockLedgerPreferences.KEY_PLATFORM_FEE_PLAN_SELECTIONS,
            null,
        ).orEmpty()
        if (serialized.isBlank()) return emptyMap()
        return serialized.split("|")
            .mapNotNull { entry ->
                val separatorIndex = entry.indexOf('=')
                if (separatorIndex <= 0 || separatorIndex >= entry.lastIndex) {
                    return@mapNotNull null
                }
                val platform = BrokerPlatform.entries.firstOrNull { it.name == entry.substring(0, separatorIndex) }
                    ?: return@mapNotNull null
                val planId = entry.substring(separatorIndex + 1)
                val resolvedPlanId = TradeFeeEstimator.resolvePlanId(platform, planId)
                if (resolvedPlanId.isBlank()) {
                    null
                } else {
                    platform to resolvedPlanId
                }
            }
            .toMap()
    }

    private fun loadZhuoruiPromoConfig(): ZhuoruiPromoConfig {
        val startDate = preferences.getString(StockLedgerPreferences.KEY_ZHUORUI_PROMO_START_DATE, null).orEmpty()
        val durationDays = preferences.getInt(StockLedgerPreferences.KEY_ZHUORUI_PROMO_DURATION_DAYS, 100)
        return ZhuoruiPromoConfig(startDate = startDate, durationDays = durationDays)
    }

    private fun saveZhuoruiPromoConfigToPrefs(config: ZhuoruiPromoConfig) {
        preferences.edit()
            .putString(StockLedgerPreferences.KEY_ZHUORUI_PROMO_START_DATE, config.startDate)
            .putInt(StockLedgerPreferences.KEY_ZHUORUI_PROMO_DURATION_DAYS, config.durationDays)
            .apply()
    }

    private fun saveEnabledPlatforms(platforms: List<BrokerPlatform>) {
        preferences.edit()
            .putStringSet(
                StockLedgerPreferences.KEY_ENABLED_PLATFORMS,
                platforms.map { it.name }.toSet(),
            )
            .apply()
    }

    private fun savePlatformFeePlanSelections(selections: Map<BrokerPlatform, String>) {
        val serialized = selections.entries
            .sortedBy { it.key.name }
            .joinToString("|") { (platform, planId) -> "${platform.name}=$planId" }
        preferences.edit()
            .putString(StockLedgerPreferences.KEY_PLATFORM_FEE_PLAN_SELECTIONS, serialized.ifBlank { null })
            .apply()
    }

    private fun saveSelectedPlatform(platform: BrokerPlatform?) {
        preferences.edit()
            .putString(StockLedgerPreferences.KEY_SELECTED_PLATFORM, platform?.name)
            .apply()
    }

    private fun loadZhuoruiEmailSyncConfig(): ZhuoruiEmailSyncConfig = ZhuoruiEmailSyncConfig(
        imapHost = preferences.getString(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_IMAP_HOST, "").orEmpty(),
        imapPort = preferences.getString(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_IMAP_PORT, "993").orEmpty(),
        account = preferences.getString(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_ACCOUNT, "").orEmpty(),
        password = preferences.getString(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_PASSWORD, "").orEmpty(),
        folder = preferences.getString(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_FOLDER, "INBOX").orEmpty().ifBlank { "INBOX" },
    )

    private fun loadZhuoruiEmailAutoImportEnabled(): Boolean {
        return preferences.getBoolean(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_AUTO_IMPORT_ENABLED, false)
    }

    private fun loadZhuoruiEmailSyncStatusMessage(): String? {
        return preferences.getString(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_LAST_SYNC_MESSAGE, null)
    }

    private fun loadZhuoruiEmailLastSyncAt(): Long {
        return preferences.getLong(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_LAST_SYNC_AT, 0L)
    }

    private fun loadZhuoruiStatementPdfPassword(): String {
        return preferences.getString(StockLedgerPreferences.KEY_ZHUORUI_STATEMENT_PDF_PASSWORD, "").orEmpty()
    }

    fun updateZhuoruiStatementPdfPassword(password: String) {
        zhuoruiStatementPdfPassword.value = password
        preferences.edit()
            .putString(StockLedgerPreferences.KEY_ZHUORUI_STATEMENT_PDF_PASSWORD, password)
            .apply()
    }

    fun importStatementPdfs(uris: List<Uri>, platform: BrokerPlatform) {
        failedPdfUris.value = emptyList()
        pdfImportProgressFraction.value = null
        val password = zhuoruiStatementPdfPassword.value
        if (password.isBlank()) {
            pdfImportStatusMessage.value = "请先输入PDF结单密码"
            return
        }

        when (pdfImportMode.value) {
            PdfImportMode.REGEX -> importStatementPdfsViaRegex(uris, platform, password)
            PdfImportMode.TEXT_MODEL -> importStatementPdfsViaTextModel(uris, platform, password)
        }
    }

    fun retryFailedPdfImport(platform: BrokerPlatform) {
        val uris = failedPdfUris.value
        if (uris.isEmpty()) return
        failedPdfUris.value = emptyList()
        pdfImportProgressFraction.value = null
        val password = zhuoruiStatementPdfPassword.value
        if (password.isBlank()) {
            pdfImportStatusMessage.value = "请先输入PDF结单密码"
            return
        }

        when (pdfImportMode.value) {
            PdfImportMode.REGEX -> importStatementPdfsViaRegex(uris, platform, password)
            PdfImportMode.TEXT_MODEL -> importStatementPdfsViaTextModel(uris, platform, password)
        }
    }

    private fun importStatementPdfsViaRegex(uris: List<Uri>, platform: BrokerPlatform, password: String) {
        if (uris.isEmpty()) {
            pdfImportStatusMessage.value = "请选择要导入的PDF文件"
            return
        }

        viewModelScope.launch {
            pdfImportStatusMessage.value = "正在导入${uris.size}个PDF文件..."
            pdfImportProgressFraction.value = 0f
            var totalImported = 0
            var totalDuplicate = 0
            var totalFailed = 0
            var totalSkipped = 0

            uris.forEachIndexed { index, uri ->
                runCatching {
                    val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
                    inputStream?.use { stream ->
                        // NOTE: ZhuoruiStatementPdfParser might currently only parse Zhuorui format.
                        // We will let it try anyway.
                        val results = repository.importZhuoruiStatementPdf(stream, password)
                        if (results.isEmpty()) {
                            totalSkipped++
                        } else {
                            for (result in results) {
                                when (result.outcome) {
                                    com.recoder.stockledger.data.repository.TradeImportOutcome.IMPORTED -> totalImported++
                                    com.recoder.stockledger.data.repository.TradeImportOutcome.DUPLICATE -> totalDuplicate++
                                    else -> totalFailed++
                                }
                            }
                        }
                    }
                }.onFailure { error ->
                    totalFailed++
                    val fileName = getFileName(uri)
                    android.util.Log.e("LedgerViewModel", "PDF导入失败 [$fileName]: ${error.message}", error)
                }
                pdfImportProgressFraction.value = (index + 1) / uris.size.toFloat()
            }

            val message = buildString {
                append("导入完成：")
                if (totalImported > 0) append("新增 $totalImported 条 ")
                if (totalDuplicate > 0) append("重复 $totalDuplicate 条 ")
                if (totalSkipped > 0) append("$totalSkipped 个文件无记录 ")
                if (totalFailed > 0) append("失败 $totalFailed 个文件")
                if (totalImported == 0 && totalDuplicate == 0 && totalFailed == 0 && totalSkipped == 0) {
                    append("未找到可导入的交易记录")
                }
            }
            pdfImportStatusMessage.value = message
            pdfImportProgressFraction.value = null
        }
    }

    private fun importStatementPdfsViaTextModel(
        uris: List<Uri>,
        platform: com.recoder.stockledger.data.BrokerPlatform,
        password: String
    ) {
        viewModelScope.launch {
            val apiKey = alibabaBailianApiKey.value
            if (apiKey.isBlank()) {
                pdfImportStatusMessage.value = "请先在设置中配置API Key"
                return@launch
            }

            pdfImportStatusMessage.value = "正在解析${uris.size}个PDF文件..."
            pdfImportProgressFraction.value = 0f
            var totalImported = 0
            var totalDuplicate = 0
            var totalFailed = 0
            var totalSkipped = 0
            var lastErrorMessage: String? = null
            val failedUris = mutableListOf<Uri>()

            val model = textImportModel.value.takeIf { it.isNotBlank() } ?: "deepseek-chat"
            val baseUrl = llmApiBaseUrl.value.takeIf { it.isNotBlank() }
                ?: "https://api.deepseek.com/v1"

            val client = com.recoder.stockledger.data.importer.llm.OpenAiTradeExtractionClient(
                apiKey = apiKey,
                model = model,
                baseUrl = baseUrl,
            )
            val importer = com.recoder.stockledger.data.importer.llm.TextPdfImporter(
                apiClient = client,
            )

            uris.forEachIndexed { index, uri ->
                val fileName = getFileName(uri)
                runCatching {
                    val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
                    inputStream?.use { stream ->
                        // 重新打开流以进行实际导入（或者直接传 text 给 importer，但目前 importer 是内部处理流的）
                        // 为了简单，我们这里再次调用原逻辑，或者您可以直接看上面的日志
                        val trades = importer.importStatement(getApplication<Application>().contentResolver.openInputStream(uri)!!, password)
                        if (trades.isEmpty()) {
                            totalSkipped++
                        } else {
                            val results = repository.importParsedTrades(trades, platform)
                            for (result in results) {
                                when (result.outcome) {
                                    com.recoder.stockledger.data.repository.TradeImportOutcome.IMPORTED -> totalImported++
                                    com.recoder.stockledger.data.repository.TradeImportOutcome.DUPLICATE -> totalDuplicate++
                                    else -> totalFailed++
                                }
                            }
                        }
                    }
                }.onFailure { error ->
                    totalFailed++
                    failedUris.add(uri)
                    val fileName = getFileName(uri)
                    lastErrorMessage = "文件 $fileName: ${error.message}"
                    android.util.Log.e("LedgerViewModel", "文本导入失败 [$fileName]: ${error.message}", error)
                }
                pdfImportProgressFraction.value = (index + 1) / uris.size.toFloat()
            }

            failedPdfUris.value = failedUris

            val message = buildString {
                append("文本导入完成：")
                if (totalImported > 0) append("新增 $totalImported 条 ")
                if (totalDuplicate > 0) append("重复 $totalDuplicate 条 ")
                if (totalSkipped > 0) append("$totalSkipped 个文件无记录 ")
                if (totalFailed > 0) {
                    append("失败 $totalFailed 个文件")
                    if (lastErrorMessage != null) {
                        append(" (原因: $lastErrorMessage)")
                    }
                }
                if (totalImported == 0 && totalDuplicate == 0 && totalFailed == 0 && totalSkipped == 0) {
                    append("未找到可导入的交易记录")
                }
            }
            pdfImportStatusMessage.value = message
            pdfImportProgressFraction.value = null
        }
    }

    fun updateAlibabaBailianApiKey(key: String) {
        alibabaBailianApiKey.value = key
        preferences.edit()
            .putString(StockLedgerPreferences.KEY_ALIBABA_BAILIAN_API_KEY, key)
            .apply()
    }

    fun updatePdfImportMode(mode: PdfImportMode) {
        pdfImportMode.value = mode
        preferences.edit()
            .putString(StockLedgerPreferences.KEY_ZHUORUI_PDF_IMPORT_MODE, mode.name)
            .apply()
    }

    fun updateTextImportModel(model: String) {
        textImportModel.value = model
        preferences.edit()
            .putString(StockLedgerPreferences.KEY_TEXT_IMPORT_MODEL, model)
            .apply()
    }

    fun updateLlmApiBaseUrl(url: String) {
        llmApiBaseUrl.value = url
        preferences.edit()
            .putString(StockLedgerPreferences.KEY_VISION_API_BASE_URL, url)
            .apply()
    }

    fun clearPdfImportStatus() {
        pdfImportStatusMessage.value = null
        pdfImportProgressFraction.value = null
        failedPdfUris.value = emptyList()
    }

    private fun loadAlibabaBailianApiKey(): String {
        return preferences.getString(StockLedgerPreferences.KEY_ALIBABA_BAILIAN_API_KEY, "").orEmpty()
    }

    private fun loadPdfImportMode(): PdfImportMode {
        val name = preferences.getString(StockLedgerPreferences.KEY_ZHUORUI_PDF_IMPORT_MODE, PdfImportMode.REGEX.name)
        return try {
            PdfImportMode.valueOf(name ?: PdfImportMode.REGEX.name)
        } catch (_: Exception) {
            PdfImportMode.REGEX
        }
    }

    private fun loadTextImportModel(): String {
        return preferences.getString(StockLedgerPreferences.KEY_TEXT_IMPORT_MODEL, "").orEmpty()
    }

    private fun loadLlmApiBaseUrl(): String {
        return preferences.getString(StockLedgerPreferences.KEY_VISION_API_BASE_URL, "").orEmpty()
    }

    private fun reconcileZhuoruiEmailAutoSync() {
        val enabled = zhuoruiEmailAutoImportEnabled.value
        val config = zhuoruiEmailSyncConfig.value
        if (enabled && config.isComplete()) {
            ZhuoruiEmailSyncWorker.schedule(getApplication(), config)
        } else {
            ZhuoruiEmailSyncWorker.cancel(getApplication())
        }
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = getApplication<Application>().contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = cursor.getString(index)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "unknown"
    }

    private companion object {
        const val MANUAL_REFRESH_INTERVAL_MS = 60_000L
        const val SYMBOL_LOOKUP_DEBOUNCE_MS = 350L
        const val CASH_ACCOUNT_SYMBOL = "CASH"
        const val CASH_ACCOUNT_NAME = "资金账户"
        const val EPSILON = 1e-6

        val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val monthDayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd")
        val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")
        val numberFormatter = DecimalFormat("#,##0.00")
        val percentFormatter = DecimalFormat("0.00")
        val knownSecurities = emptyList<ResolvedSecurity>()
    }
}
