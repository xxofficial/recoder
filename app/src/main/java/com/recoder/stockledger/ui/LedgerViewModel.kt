package com.recoder.stockledger.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.recoder.stockledger.data.DisplayCurrency
import com.recoder.stockledger.data.ExchangeRateOrigin
import com.recoder.stockledger.data.ExchangeRates
import com.recoder.stockledger.StockLedgerApplication
import com.recoder.stockledger.data.HoldingUiModel
import com.recoder.stockledger.data.Market
import com.recoder.stockledger.data.MarketFilter
import com.recoder.stockledger.data.PortfolioSummary
import com.recoder.stockledger.data.PriceTrend
import com.recoder.stockledger.data.ProfitAnalysisPointUiModel
import com.recoder.stockledger.data.ProfitAnalysisUiModel
import com.recoder.stockledger.data.RefreshState
import com.recoder.stockledger.data.SampleData
import com.recoder.stockledger.data.SecuritySuggestionUiModel
import com.recoder.stockledger.data.SellCandidateUiModel
import com.recoder.stockledger.data.SymbolLookupState
import com.recoder.stockledger.data.SymbolLookupUiModel
import com.recoder.stockledger.data.TradeFormState
import com.recoder.stockledger.data.TradeType
import com.recoder.stockledger.data.TransactionFilter
import com.recoder.stockledger.data.TransactionSection
import com.recoder.stockledger.data.TransactionUiModel
import com.recoder.stockledger.data.rateToCny
import com.recoder.stockledger.data.local.QuoteSnapshotEntity
import com.recoder.stockledger.data.local.TransactionEntity
import com.recoder.stockledger.data.repository.DefaultLedgerRepository
import com.recoder.stockledger.data.repository.SecurityLookupResult
import com.recoder.stockledger.data.repository.TradeDraftInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue

private const val DEFAULT_REFRESH_MESSAGE = "打开应用会自动刷新一次行情，手动下拉 1 分钟内仅可触发一次"

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
    val draft: TradeFormState = SampleData.tradeForm(TradeType.BUY),
    val symbolLookup: SymbolLookupUiModel = SymbolLookupUiModel(),
    val symbolSuggestions: List<SecuritySuggestionUiModel> = emptyList(),
    val canSubmitTrade: Boolean = false,
    val tradeValidationMessage: String? = null,
    val displayCurrency: DisplayCurrency = DisplayCurrency.CNY,
    val exchangeRates: ExchangeRates = ExchangeRates(),
)

class LedgerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: DefaultLedgerRepository =
        (application as StockLedgerApplication).repository
    private val preferences = application.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val tradeFilter = MutableStateFlow(TransactionFilter.ALL)
    private val marketFilter = MutableStateFlow(MarketFilter.ALL)
    private val draft = MutableStateFlow(SampleData.tradeForm(TradeType.BUY))
    private val symbolLookup = MutableStateFlow(SymbolLookupUiModel())
    private val symbolSuggestions = MutableStateFlow<List<SecuritySuggestionUiModel>>(emptyList())
    private val refreshState = MutableStateFlow(RefreshState.IDLE)
    private val refreshNote = MutableStateFlow(DEFAULT_REFRESH_MESSAGE)
    private val lastRefreshTimestamp = MutableStateFlow(0L)
    private val showPullRefreshTime = MutableStateFlow(false)
    private val displayCurrency = MutableStateFlow(loadSavedDisplayCurrency())

    private var lastManualRefreshTriggeredAt: Long = 0L
    private var symbolLookupJob: Job? = null

    private val portfolio = combine(repository.transactions, repository.quotes, repository.exchangeRates) { transactions, quotes, exchangeRates ->
        computePortfolio(transactions, quotes, exchangeRates)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyPortfolioComputation(),
    )

    private val filters = combine(tradeFilter, marketFilter) { selectedTradeFilter, selectedMarketFilter ->
        selectedTradeFilter to selectedMarketFilter
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

    val uiState: StateFlow<LedgerUiState> = combine(portfolio, repository.transactions) { portfolioState, transactions ->
        PortfolioAndTransactions(portfolioState, transactions)
    }.combine(filters) { upstream, selectedFilters ->
        PortfolioTransactionsAndFilters(upstream.portfolio, upstream.transactions, selectedFilters)
    }.combine(draft) { upstream, tradeDraft ->
        PortfolioTransactionsFiltersAndDraft(
            portfolio = upstream.portfolio,
            transactions = upstream.transactions,
            filters = upstream.filters,
            draft = tradeDraft,
        )
    }.combine(refreshMeta) { upstream, meta ->
        PortfolioTransactionsFiltersDraftAndRefresh(
            portfolio = upstream.portfolio,
            transactions = upstream.transactions,
            filters = upstream.filters,
            draft = upstream.draft,
            refresh = meta,
        )
    }.combine(displayCurrency) { upstream, selectedDisplayCurrency ->
        upstream to selectedDisplayCurrency
    }.combine(symbolLookup) { upstream, lookup ->
        val (state, selectedDisplayCurrency) = upstream
        val (selectedTradeFilter, selectedMarketFilter) = state.filters
        val validationMessage = validateTradeDraft(state.draft, state.portfolio, lookup)
        LedgerUiState(
            summary = buildSummary(
                portfolio = state.portfolio,
                refresh = state.refresh.refresh,
                refreshedAt = state.refresh.refreshedAt,
                refreshNote = state.refresh.note,
                showPullRefreshTime = state.refresh.showPullRefreshTime,
                displayCurrency = selectedDisplayCurrency,
                exchangeRates = repository.exchangeRates.value,
            ),
            holdings = state.portfolio.holdings,
            sellCandidates = buildSellCandidates(state.portfolio.positions),
            transactionSections = buildTransactionSections(
                transactions = state.transactions,
                tradeFilter = selectedTradeFilter,
                marketFilter = selectedMarketFilter,
            ),
            profitAnalysis = buildProfitAnalysis(
                portfolio = state.portfolio,
                transactions = state.transactions,
                exchangeRates = repository.exchangeRates.value,
            ),
            selectedTradeFilter = selectedTradeFilter,
            selectedMarketFilter = selectedMarketFilter,
            draft = state.draft,
            symbolLookup = lookup,
            canSubmitTrade = validationMessage == null,
            tradeValidationMessage = validationMessage,
            displayCurrency = selectedDisplayCurrency,
            exchangeRates = repository.exchangeRates.value,
        )
    }.combine(symbolSuggestions) { state, suggestions ->
        state.copy(symbolSuggestions = suggestions)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LedgerUiState(),
    )

    init {
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
        symbolLookupJob?.cancel()
        symbolLookup.value = SymbolLookupUiModel()
        symbolSuggestions.value = emptyList()
        draft.value = applyDraftRules(resetDraftForType(type, preferredCashMarket = cashMarketFor(displayCurrency.value)))
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

    fun selectDisplayCurrency(currency: DisplayCurrency) {
        saveDisplayCurrency(currency)
        displayCurrency.value = currency
        if (!draft.value.selectedType.isSecurityTrade) {
            draft.update { current ->
                applyDraftRules(current.copy(market = cashMarketFor(currency)))
            }
        }
    }

    fun selectTradeMarket(market: Market) {
        draft.update { current ->
            applyDraftRules(current.copy(market = market))
        }
        refreshSymbolLookupForCurrentDraft()
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

    suspend fun submitTrade(): Boolean {
        val currentDraft = draft.value
        val portfolioState = computePortfolio(
            transactions = repository.transactions.first(),
            quotes = repository.quotes.first(),
            exchangeRates = repository.exchangeRates.value,
        )
        val currentLookup = symbolLookup.value
        val validationMessage = validateTradeDraft(currentDraft, portfolioState, currentLookup)
        if (validationMessage != null) return false

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
            market = resolved.market,
            symbol = resolved.symbol,
            name = resolved.name,
            tradeDate = currentDraft.tradeDate,
            tradeTime = LocalTime.now().format(timeFormatter),
            price = parseDecimal(currentDraft.priceLabel),
            quantity = if (currentDraft.selectedType.isSecurityTrade) parseQuantity(currentDraft.quantityLabel) else 1,
            commission = if (currentDraft.selectedType.isSecurityTrade) parseDecimal(currentDraft.commissionLabel) else 0.0,
            tax = if (currentDraft.selectedType.isSecurityTrade) parseDecimal(currentDraft.taxLabel) else 0.0,
            note = currentDraft.note.trim(),
        )

        repository.addTrade(input)
        tradeFilter.value = TransactionFilter.ALL
        marketFilter.value = MarketFilter.ALL
        symbolLookup.value = SymbolLookupUiModel()
        symbolSuggestions.value = emptyList()
        draft.value = applyDraftRules(resetDraftForType(currentDraft.selectedType))
        refreshQuotes(trigger = RefreshTrigger.TRADE_ENTRY)
        return true
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

            if (exactSuggestion != null) {
                draft.update { current ->
                    if (current.market == market && current.symbolOrName.trim() == lookupInput) {
                        current.copy(symbolOrName = "${exactSuggestion.name} ${exactSuggestion.symbol}")
                    } else {
                        current
                    }
                }
                symbolSuggestions.value = emptyList()
                val resolvedLookup = SymbolLookupUiModel(
                    state = SymbolLookupState.RESOLVED,
                    message = "已识别为 ${exactSuggestion.name} ${exactSuggestion.symbol}",
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

            symbolSuggestions.value = suggestions.map { suggestion ->
                SecuritySuggestionUiModel(
                    symbol = suggestion.symbol,
                    name = suggestion.name,
                    market = suggestion.market,
                    displayLabel = "${suggestion.name} ${suggestion.symbol}",
                )
            }

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
            RefreshTrigger.TRADE_ENTRY -> "正在同步录入后的最新行情..."
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
                RefreshTrigger.TRADE_ENTRY ->
                    "录入成功，已同步最新行情 · ${formatRefreshTime(lastRefreshTimestamp.value)}$rateSuffix"
            }
        }.onFailure { error ->
            refreshState.value = RefreshState.FAILED
            refreshNote.value = when (trigger) {
                RefreshTrigger.APP_OPEN ->
                    "启动刷新失败：${error.message ?: "请稍后手动下拉刷新"}"
                RefreshTrigger.MANUAL_PULL ->
                    "手动刷新失败：${error.message ?: "请检查网络连接后重试"}"
                RefreshTrigger.TRADE_ENTRY ->
                    "交易已保存，但行情同步失败：${error.message ?: "请稍后手动下拉刷新"}"
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

        if (draft.tradeDate.isBlank()) return "请输入交易日期"

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
            TradeType.BUY -> {
                val requiredCash = convertToCny(
                    price * quantity + parseDecimal(draft.commissionLabel) + parseDecimal(draft.taxLabel),
                    resolved.market,
                    repository.exchangeRates.value,
                )
                if (portfolio.cashBalanceCny + EPSILON < requiredCash) {
                    return "可用现金不足，请先入金"
                }
            }

            TradeType.SELL -> {
                val position = portfolio.positions[positionKey(resolved.symbol, resolved.market)]
                if (position == null || position.quantity < quantity) {
                    return "当前持仓不足，无法完成卖出"
                }
            }

            else -> Unit
        }

        return null
    }

    private fun applyDraftRules(
        draft: TradeFormState,
        lookup: SymbolLookupUiModel = symbolLookup.value,
        positions: Map<String, PositionComputation> = portfolio.value.positions,
    ): TradeFormState {
        val normalized = if (draft.selectedType.isSecurityTrade) {
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
            )
        }

        if (normalized.selectedType != TradeType.SELL) return normalized

        val requestedQuantity = parseQuantity(normalized.quantityLabel)
        if (requestedQuantity <= 0) return normalized

        val resolved = resolveSecurity(normalized, positions, lookup) ?: return normalized
        val availableQuantity = positions[positionKey(resolved.symbol, resolved.market)]?.quantity ?: return normalized
        if (requestedQuantity <= availableQuantity) return normalized

        return normalized.copy(quantityLabel = availableQuantity.toString())
    }

    private fun resolveSecurity(
        draft: TradeFormState,
        positions: Map<String, PositionComputation>,
        lookup: SymbolLookupUiModel,
    ): ResolvedSecurity? {
        val raw = draft.symbolOrName.trim()
        val position = positions.values.firstOrNull { current ->
            current.market == draft.market && (
                current.symbol.equals(raw, ignoreCase = true) ||
                    current.name.equals(raw, ignoreCase = true) ||
                    raw.contains(current.symbol, ignoreCase = true) ||
                    raw.contains(current.name, ignoreCase = true)
                )
        }
        if (position != null) {
            return ResolvedSecurity(
                symbol = position.symbol,
                name = position.name,
                market = position.market,
            )
        }

        if (lookup.state == SymbolLookupState.RESOLVED &&
            lookup.resolvedSymbol != null &&
            lookup.resolvedName != null &&
            lookup.resolvedMarket == draft.market
        ) {
            return ResolvedSecurity(
                symbol = lookup.resolvedSymbol,
                name = lookup.resolvedName,
                market = draft.market,
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
            security.market == market && (
                security.symbol.equals(raw, ignoreCase = true) ||
                    security.name.equals(raw, ignoreCase = true) ||
                    raw.contains(security.symbol, ignoreCase = true) ||
                    raw.contains(security.name, ignoreCase = true)
                )
        }
    }

    private fun shouldLookupRemotely(rawInput: String, market: Market): Boolean {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) return false
        return when (market) {
            Market.A_SHARE -> trimmed.length >= 2
            Market.HONG_KONG -> trimmed.length >= 2
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

            Market.HONG_KONG -> compact
                .removePrefix("HK")
                .removeSuffix(".HK")
                .filter(Char::isDigit)

            Market.US -> normalizeUsLookupInput(compact)
                .filter { it.isLetterOrDigit() || it == '.' || it == '-' }

            Market.CASH -> ""
        }

        return when (market) {
            Market.A_SHARE -> digits.length == 6
            Market.HONG_KONG -> digits.length in 3..5
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
        Market.HONG_KONG -> "输入港股代码或名称，支持自动联想和补全"
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
        val aCount = portfolio.positions.values.count { it.market == Market.A_SHARE && it.quantity > 0 }
        val hkCount = portfolio.positions.values.count { it.market == Market.HONG_KONG && it.quantity > 0 }
        val usCount = portfolio.positions.values.count { it.market == Market.US && it.quantity > 0 }
        val totalRate = if (portfolio.netInflowCny <= 0.0) {
            0.0
        } else {
            (portfolio.totalProfitCny / portfolio.netInflowCny) * 100.0
        }

        return PortfolioSummary(
            totalAssets = formatDisplayAmount(portfolio.totalAssetsCny, displayCurrency, exchangeRates),
            totalCost = formatDisplayAmount(portfolio.netInflowCny, displayCurrency, exchangeRates),
            totalCostHint = "累计入金 ${formatDisplayAmount(portfolio.totalDepositCny, displayCurrency, exchangeRates)} · 累计出金 ${formatDisplayAmount(portfolio.totalWithdrawCny, displayCurrency, exchangeRates)}",
            cashBalance = formatDisplayAmount(portfolio.cashBalanceCny, displayCurrency, exchangeRates),
            cashBalanceHint = "A股 $aCount 只 · 港股 $hkCount 只 · 美股 $usCount 只",
            totalProfit = formatSignedDisplayAmount(portfolio.totalProfitCny, displayCurrency, exchangeRates),
            totalProfitHint = "折算收益率 ${formatSignedPercent(totalRate)}",
            dayProfit = "${formatSignedDisplayAmount(portfolio.dayProfitCny, displayCurrency, exchangeRates)} (${formatSignedPercent(portfolio.dayProfitPercent)})",
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

    private fun buildSellCandidates(
        positions: Map<String, PositionComputation>,
    ): List<SellCandidateUiModel> = positions.values
        .filter { it.quantity > 0 }
        .sortedByDescending { it.quantity }
        .map { position ->
            SellCandidateUiModel(
                symbol = position.symbol,
                name = position.name,
                market = position.market,
                quantityLabel = "可卖 ${position.quantity} 股",
                costLabel = "成本 ${formatMarketAmount(position.averageCost, position.market)}",
            )
        }

    private fun buildTransactionSections(
        transactions: List<TransactionEntity>,
        tradeFilter: TransactionFilter,
        marketFilter: MarketFilter,
    ): List<TransactionSection> {
        val filtered = transactions
            .filter { tradeFilter.tradeType == null || it.tradeType == tradeFilter.tradeType.name }
            .filter { marketFilter.market == null || it.market == marketFilter.market.name }

        return filtered
            .groupBy { it.tradeDate }
            .toSortedMap(compareByDescending { it })
            .map { (date, entries) ->
                TransactionSection(
                    title = displayDate(date),
                    items = entries.map { transaction ->
                        val market = Market.valueOf(transaction.market)
                        val tradeType = TradeType.valueOf(transaction.tradeType)
                        val cashFlow = transactionCashFlow(transaction, tradeType)
                        TransactionUiModel(
                            tradeType = tradeType,
                            stockName = if (tradeType.isSecurityTrade) {
                                "${transaction.name} ${transaction.symbol}"
                            } else {
                                CASH_ACCOUNT_NAME
                            },
                            stockMeta = if (tradeType.isSecurityTrade) {
                                "${market.label} · 成交价 ${formatMarketAmount(transaction.price, market)} · ${transaction.quantity} 股"
                            } else {
                                transaction.note.ifBlank { "现金账户流水" }
                            },
                            amountLabel = formatSignedMarketAmount(cashFlow, market),
                            timeLabel = "${tradeType.label} ${transaction.tradeTime}",
                            feeLabel = if (tradeType.isSecurityTrade) {
                                "费用 ${formatMarketAmount(transaction.commission + transaction.tax, market)}"
                            } else {
                                "净变动 ${formatMarketAmount(transaction.price * transaction.quantity, market)}"
                            },
                        )
                    },
                )
            }
    }

    private fun buildProfitAnalysis(
        portfolio: PortfolioComputation,
        transactions: List<TransactionEntity>,
        exchangeRates: ExchangeRates,
    ): ProfitAnalysisUiModel {
        val ordered = transactions.sortedWith(
            compareBy<TransactionEntity>({ it.tradeDate }, { it.tradeTime }, { it.createdAt }),
        )
        val positions = linkedMapOf<String, PositionComputation>()
        val realizedByDate = linkedMapOf<LocalDate, Double>()

        ordered.forEach { transaction ->
            val market = Market.valueOf(transaction.market)
            val date = LocalDate.parse(transaction.tradeDate)
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
                    val buyCost = transaction.price * transaction.quantity + transaction.commission + transaction.tax
                    val nextQuantity = current.quantity + transaction.quantity
                    val nextRemaining = current.remainingCost + buyCost
                    positions[key] = current.copy(
                        quantity = nextQuantity,
                        remainingCost = nextRemaining,
                        averageCost = if (nextQuantity == 0) 0.0 else nextRemaining / nextQuantity,
                    )
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
                    val sellQuantity = minOf(current.quantity, transaction.quantity)
                    val removedCost = current.averageCost * sellQuantity
                    val proceeds = transaction.price * sellQuantity - transaction.commission - transaction.tax
                    val realizedProfitCny = convertToCny(proceeds - removedCost, market, exchangeRates)
                    realizedByDate[date] = realizedByDate.getOrDefault(date, 0.0) + realizedProfitCny

                    val nextQuantity = (current.quantity - sellQuantity).coerceAtLeast(0)
                    val nextRemaining = (current.remainingCost - removedCost).coerceAtLeast(0.0)
                    positions[key] = current.copy(
                        quantity = nextQuantity,
                        remainingCost = nextRemaining,
                        averageCost = if (nextQuantity == 0) 0.0 else nextRemaining / nextQuantity,
                        realizedProfit = current.realizedProfit + (proceeds - removedCost),
                    )
                }

                TradeType.DEPOSIT, TradeType.WITHDRAW -> Unit
            }
        }

        val lastTransactionDate = ordered.lastOrNull()?.tradeDate?.let(LocalDate::parse)
        val latestDate = maxOf(LocalDate.now(), lastTransactionDate ?: LocalDate.now())
        val realizedTotalCny = realizedByDate.values.sum()
        val openProfitAdjustmentCny = portfolio.totalProfitCny - realizedTotalCny
        realizedByDate[latestDate] = realizedByDate.getOrDefault(latestDate, 0.0) + openProfitAdjustmentCny

        val firstDate = realizedByDate.keys.minOrNull() ?: latestDate
        val dailyPoints = mutableListOf<ProfitAnalysisPointUiModel>()
        var cursor = firstDate
        var cumulativeProfit = 0.0
        while (!cursor.isAfter(latestDate)) {
            val dailyProfit = realizedByDate.getOrDefault(cursor, 0.0)
            cumulativeProfit += dailyProfit
            dailyPoints += ProfitAnalysisPointUiModel(
                date = cursor,
                dailyProfitCny = dailyProfit,
                cumulativeProfitCny = cumulativeProfit,
            )
            cursor = cursor.plusDays(1)
        }

        return ProfitAnalysisUiModel(
            dailyPoints = dailyPoints,
            netInflowCny = portfolio.netInflowCny,
            latestDate = latestDate,
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
        val ordered = transactions.sortedWith(
            compareBy<TransactionEntity>({ it.tradeDate }, { it.tradeTime }, { it.createdAt }),
        )

        ordered.forEach { transaction ->
            val market = Market.valueOf(transaction.market)
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
                        val buyCost = transaction.price * transaction.quantity + transaction.commission + transaction.tax
                        val nextQuantity = current.quantity + transaction.quantity
                        val nextRemaining = current.remainingCost + buyCost
                        cashBalanceCny -= convertToCny(buyCost, market, exchangeRates)
                        current.copy(
                            quantity = nextQuantity,
                            remainingCost = nextRemaining,
                            averageCost = if (nextQuantity == 0) 0.0 else nextRemaining / nextQuantity,
                        )
                    } else {
                        val sellQuantity = minOf(current.quantity, transaction.quantity)
                        val removedCost = current.averageCost * sellQuantity
                        val proceeds = transaction.price * sellQuantity - transaction.commission - transaction.tax
                        val nextQuantity = (current.quantity - sellQuantity).coerceAtLeast(0)
                        val nextRemaining = (current.remainingCost - removedCost).coerceAtLeast(0.0)
                        cashBalanceCny += convertToCny(proceeds, market, exchangeRates)
                        current.copy(
                            quantity = nextQuantity,
                            remainingCost = nextRemaining,
                            averageCost = if (nextQuantity == 0) 0.0 else nextRemaining / nextQuantity,
                            realizedProfit = current.realizedProfit + (proceeds - removedCost),
                        )
                    }
                }
            }
        }

        val quoteMap = quotes.associateBy { positionKey(it.symbol, Market.valueOf(it.market)) }
        val holdings = positions.values
            .filter { it.quantity > 0 }
            .map { position ->
                val quote = quoteMap[positionKey(position.symbol, position.market)]
                val currentPrice = quote?.currentPrice
                val previousClose = quote?.previousClose
                val unrealized = currentPrice?.let { (it - position.averageCost) * position.quantity }
                val dayPercent = if (currentPrice != null && previousClose != null && previousClose > 0.0) {
                    ((currentPrice - previousClose) / previousClose) * 100.0
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
                ) to marketValue
            }
            .sortedByDescending { it.second }
            .map { it.first }

        val holdingsValueCny = positions.values.sumOf { position ->
            val quote = quoteMap[positionKey(position.symbol, position.market)]
            convertToCny(position.quantity * (quote?.currentPrice ?: position.averageCost), position.market, exchangeRates)
        }
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
            cashBalanceCny = cashBalanceCny,
            totalDepositCny = totalDepositCny,
            totalWithdrawCny = totalWithdrawCny,
            netInflowCny = netInflowCny,
            totalProfitCny = totalAssetsCny - netInflowCny,
            dayProfitCny = dayProfitCny,
            dayProfitPercent = if (previousAssetValueCny == 0.0) 0.0 else {
                (dayProfitCny / previousAssetValueCny) * 100.0
            },
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
        val parsed = LocalDate.parse(date)
        val today = LocalDate.now()
        val prefix = when (parsed) {
            today -> "今天"
            today.minusDays(1) -> "昨天"
            else -> parsed.format(dateFormatter)
        }
        return "$prefix · ${parsed.format(monthDayFormatter)}"
    }

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

        Market.HONG_KONG -> rawInput
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
    ): TradeFormState {
        val base = SampleData.tradeForm(type)
        return base.copy(
            market = if (type.isSecurityTrade) base.market else preferredCashMarket,
            tradeDate = current?.tradeDate ?: base.tradeDate,
            note = current?.note.orEmpty(),
            priceLabel = if (current?.selectedType == type) current.priceLabel else "",
        )
    }

    private fun cashMarketFor(currency: DisplayCurrency): Market = when (currency) {
        DisplayCurrency.USD -> Market.US
        DisplayCurrency.CNY -> Market.CASH
        DisplayCurrency.HKD -> Market.HONG_KONG
    }

    private fun normalizeCashMarket(market: Market): Market = when (market) {
        Market.HONG_KONG, Market.US, Market.CASH -> market
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
        TRADE_ENTRY,
    }

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

    private data class PortfolioComputation(
        val holdings: List<HoldingUiModel>,
        val positions: Map<String, PositionComputation>,
        val totalAssetsCny: Double,
        val cashBalanceCny: Double,
        val totalDepositCny: Double,
        val totalWithdrawCny: Double,
        val netInflowCny: Double,
        val totalProfitCny: Double,
        val dayProfitCny: Double,
        val dayProfitPercent: Double,
    )

    private data class PortfolioAndTransactions(
        val portfolio: PortfolioComputation,
        val transactions: List<TransactionEntity>,
    )

    private data class PortfolioTransactionsAndFilters(
        val portfolio: PortfolioComputation,
        val transactions: List<TransactionEntity>,
        val filters: Pair<TransactionFilter, MarketFilter>,
    )

    private data class PortfolioTransactionsFiltersAndDraft(
        val portfolio: PortfolioComputation,
        val transactions: List<TransactionEntity>,
        val filters: Pair<TransactionFilter, MarketFilter>,
        val draft: TradeFormState,
    )

    private data class PortfolioTransactionsFiltersDraftAndRefresh(
        val portfolio: PortfolioComputation,
        val transactions: List<TransactionEntity>,
        val filters: Pair<TransactionFilter, MarketFilter>,
        val draft: TradeFormState,
        val refresh: RefreshMeta,
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
        cashBalanceCny = 0.0,
        totalDepositCny = 0.0,
        totalWithdrawCny = 0.0,
        netInflowCny = 0.0,
        totalProfitCny = 0.0,
        dayProfitCny = 0.0,
        dayProfitPercent = 0.0,
    )

    private fun loadSavedDisplayCurrency(): DisplayCurrency {
        val savedName = preferences.getString(KEY_DISPLAY_CURRENCY, DisplayCurrency.CNY.name)
        return DisplayCurrency.entries.firstOrNull { it.name == savedName } ?: DisplayCurrency.CNY
    }

    private fun saveDisplayCurrency(currency: DisplayCurrency) {
        preferences.edit()
            .putString(KEY_DISPLAY_CURRENCY, currency.name)
            .apply()
    }

    private companion object {
        const val MANUAL_REFRESH_INTERVAL_MS = 60_000L
        const val SYMBOL_LOOKUP_DEBOUNCE_MS = 350L
        const val CASH_ACCOUNT_SYMBOL = "CASH"
        const val CASH_ACCOUNT_NAME = "资金账户"
        const val EPSILON = 1e-6
        const val PREFERENCES_NAME = "stock_ledger_preferences"
        const val KEY_DISPLAY_CURRENCY = "display_currency"

        val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val monthDayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd")
        val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")
        val numberFormatter = DecimalFormat("#,##0.00")
        val percentFormatter = DecimalFormat("0.00")
        val knownSecurities = emptyList<ResolvedSecurity>()
    }
}
