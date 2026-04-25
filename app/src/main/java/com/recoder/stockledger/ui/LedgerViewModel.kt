package com.recoder.stockledger.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.recoder.stockledger.StockLedgerApplication
import com.recoder.stockledger.data.HoldingUiModel
import com.recoder.stockledger.data.Market
import com.recoder.stockledger.data.MarketFilter
import com.recoder.stockledger.data.PortfolioSummary
import com.recoder.stockledger.data.PriceTrend
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
        totalCostHint = "A股 0 只 · 港股 0 只",
        totalProfit = "+¥0.00",
        totalProfitHint = "折算收益率 +0.00%",
        dayProfit = "+¥0.00 (+0.00%)",
        refreshState = RefreshState.IDLE,
        refreshMessage = DEFAULT_REFRESH_MESSAGE,
    ),
    val holdings: List<HoldingUiModel> = emptyList(),
    val sellCandidates: List<SellCandidateUiModel> = emptyList(),
    val transactionSections: List<TransactionSection> = emptyList(),
    val selectedTradeFilter: TransactionFilter = TransactionFilter.ALL,
    val selectedMarketFilter: MarketFilter = MarketFilter.ALL,
    val draft: TradeFormState = SampleData.tradeForm(TradeType.BUY),
    val symbolLookup: SymbolLookupUiModel = SymbolLookupUiModel(),
    val symbolSuggestions: List<SecuritySuggestionUiModel> = emptyList(),
    val canSubmitTrade: Boolean = false,
    val tradeValidationMessage: String? = null,
)

class LedgerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: DefaultLedgerRepository =
        (application as StockLedgerApplication).repository

    private val tradeFilter = MutableStateFlow(TransactionFilter.ALL)
    private val marketFilter = MutableStateFlow(MarketFilter.ALL)
    private val draft = MutableStateFlow(SampleData.tradeForm(TradeType.BUY))
    private val symbolLookup = MutableStateFlow(SymbolLookupUiModel())
    private val symbolSuggestions = MutableStateFlow<List<SecuritySuggestionUiModel>>(emptyList())
    private val refreshState = MutableStateFlow(RefreshState.IDLE)
    private val refreshNote = MutableStateFlow(DEFAULT_REFRESH_MESSAGE)
    private val lastRefreshTimestamp = MutableStateFlow(0L)

    private var lastManualRefreshTriggeredAt: Long = 0L
    private var symbolLookupJob: Job? = null

    private val portfolio = combine(repository.transactions, repository.quotes) { transactions, quotes ->
        computePortfolio(transactions, quotes)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyPortfolioComputation(),
    )

    private val filters = combine(tradeFilter, marketFilter) { selectedTradeFilter, selectedMarketFilter ->
        selectedTradeFilter to selectedMarketFilter
    }

    private val refreshMeta = combine(refreshState, lastRefreshTimestamp, refreshNote) { refresh, refreshedAt, note ->
        RefreshMeta(refresh = refresh, refreshedAt = refreshedAt, note = note)
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
    }.combine(symbolLookup) { upstream, lookup ->
        val (selectedTradeFilter, selectedMarketFilter) = upstream.filters
        val validationMessage = validateTradeDraft(upstream.draft, upstream.portfolio.positions, lookup)
        LedgerUiState(
            summary = buildSummary(
                portfolio = upstream.portfolio,
                refresh = upstream.refresh.refresh,
                refreshedAt = upstream.refresh.refreshedAt,
                refreshNote = upstream.refresh.note,
            ),
            holdings = upstream.portfolio.holdings,
            sellCandidates = buildSellCandidates(upstream.portfolio.positions),
            transactionSections = buildTransactionSections(
                transactions = upstream.transactions,
                tradeFilter = selectedTradeFilter,
                marketFilter = selectedMarketFilter,
            ),
            selectedTradeFilter = selectedTradeFilter,
            selectedMarketFilter = selectedMarketFilter,
            draft = upstream.draft,
            symbolLookup = lookup,
            canSubmitTrade = validationMessage == null,
            tradeValidationMessage = validationMessage,
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
        draft.value = applySellQuantityLimit(
            SampleData.tradeForm(type).copy(selectedType = type),
        )
    }

    fun selectTradeType(type: TradeType) {
        draft.update { current ->
            applySellQuantityLimit(current.copy(selectedType = type))
        }
        refreshSymbolLookupForCurrentDraft()
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
            applySellQuantityLimit(
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

    fun selectTradeMarket(market: Market) {
        draft.update { current ->
            applySellQuantityLimit(current.copy(market = market))
        }
        refreshSymbolLookupForCurrentDraft()
    }

    fun onSymbolInputChanged(value: String) {
        draft.update { current ->
            applySellQuantityLimit(current.copy(symbolOrName = value))
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
            applySellQuantityLimit(
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
            applySellQuantityLimit(transform(current))
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
        )
        val currentLookup = symbolLookup.value
        val validationMessage = validateTradeDraft(currentDraft, portfolioState.positions, currentLookup)
        if (validationMessage != null) return false

        val resolved = resolveSecurity(currentDraft, portfolioState.positions, currentLookup) ?: return false
        val input = TradeDraftInput(
            tradeType = currentDraft.selectedType,
            market = currentDraft.market,
            symbol = resolved.symbol,
            name = resolved.name,
            tradeDate = currentDraft.tradeDate,
            tradeTime = LocalTime.now().format(timeFormatter),
            price = parseDecimal(currentDraft.priceLabel),
            quantity = parseQuantity(currentDraft.quantityLabel),
            commission = parseDecimal(currentDraft.commissionLabel),
            tax = parseDecimal(currentDraft.taxLabel),
            note = currentDraft.note.trim(),
        )

        repository.addTrade(input)
        tradeFilter.value = TransactionFilter.ALL
        marketFilter.value = MarketFilter.ALL
        symbolLookup.value = SymbolLookupUiModel()
        symbolSuggestions.value = emptyList()
        draft.value = SampleData.tradeForm(currentDraft.selectedType)
        refreshQuotes(trigger = RefreshTrigger.TRADE_ENTRY)
        return true
    }

    private fun refreshSymbolLookupForCurrentDraft() {
        val currentDraft = draft.value
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
                applySellQuantityLimit(current, lookup = resolvedLookup)
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
                    applySellQuantityLimit(current, lookup = resolvedLookup)
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
        refreshState.value = RefreshState.REFRESHING
        refreshNote.value = when (trigger) {
            RefreshTrigger.APP_OPEN -> "正在刷新启动行情..."
            RefreshTrigger.MANUAL_PULL -> "正在手动刷新行情..."
            RefreshTrigger.TRADE_ENTRY -> "正在同步录入后的最新行情..."
        }

        runCatching {
            val latestTransactions = repository.transactions.first()
            lastRefreshTimestamp.value = repository.refreshQuotesForPortfolio(latestTransactions)
            refreshState.value = RefreshState.FRESH
            refreshNote.value = when (trigger) {
                RefreshTrigger.APP_OPEN ->
                    "启动已刷新 ${formatRefreshTime(lastRefreshTimestamp.value)} · 手动下拉至少间隔 1 分钟"
                RefreshTrigger.MANUAL_PULL ->
                    "最近刷新 ${formatRefreshTime(lastRefreshTimestamp.value)} · 手动下拉至少间隔 1 分钟"
                RefreshTrigger.TRADE_ENTRY ->
                    "录入成功，已同步最新行情 · ${formatRefreshTime(lastRefreshTimestamp.value)}"
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
    }

    private fun validateTradeDraft(
        draft: TradeFormState,
        positions: Map<String, PositionComputation>,
        lookup: SymbolLookupUiModel,
    ): String? {
        val noInputYet = draft.symbolOrName.isBlank() &&
            draft.priceLabel.isBlank() &&
            draft.quantityLabel.isBlank()
        if (noInputYet) return null

        if (draft.symbolOrName.isBlank()) return "请输入股票代码"

        val resolved = resolveSecurity(draft, positions, lookup)
        if (resolved == null) {
            return when (lookup.state) {
                SymbolLookupState.LOOKING_UP -> "正在校验股票代码，请稍候"
                SymbolLookupState.INVALID -> lookup.message ?: "未找到对应股票，请检查代码和市场"
                else -> "请先输入有效股票代码并完成校验"
            }
        }

        if (draft.tradeDate.isBlank()) return "请输入交易日期"

        val price = parseDecimal(draft.priceLabel)
        if (price <= 0.0) return "成交价格必须大于 0"

        val quantity = parseQuantity(draft.quantityLabel)
        if (quantity <= 0) return "成交数量必须大于 0"

        if (draft.selectedType == TradeType.SELL) {
            val position = positions[positionKey(resolved.symbol, resolved.market)]
            if (position == null || position.quantity < quantity) {
                return "当前持仓不足，无法完成卖出"
            }
        }

        return null
    }

    private fun applySellQuantityLimit(
        draft: TradeFormState,
        lookup: SymbolLookupUiModel = symbolLookup.value,
        positions: Map<String, PositionComputation> = portfolio.value.positions,
    ): TradeFormState {
        if (draft.selectedType != TradeType.SELL) return draft

        val requestedQuantity = parseQuantity(draft.quantityLabel)
        if (requestedQuantity <= 0) return draft

        val resolved = resolveSecurity(draft, positions, lookup) ?: return draft
        val availableQuantity = positions[positionKey(resolved.symbol, resolved.market)]?.quantity ?: return draft
        if (requestedQuantity <= availableQuantity) return draft

        return draft.copy(quantityLabel = availableQuantity.toString())
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
        }

        return when (market) {
            Market.A_SHARE -> digits.length == 6
            Market.HONG_KONG -> digits.length in 3..5
        }
    }

    private fun isExactSuggestionMatch(
        rawInput: String,
        suggestion: SecurityLookupResult,
    ): Boolean {
        val compactInput = rawInput.trim().uppercase().replace(" ", "")
        val normalizedInputDigits = compactInput
            .removePrefix("SH")
            .removePrefix("SZ")
            .removePrefix("HK")
            .removeSuffix(".HK")
            .filter(Char::isDigit)
            .trimStart('0')

        val suggestionDigits = suggestion.symbol
            .uppercase()
            .removeSuffix(".HK")
            .filter(Char::isDigit)
            .trimStart('0')

        return suggestion.name.equals(rawInput.trim(), ignoreCase = true) ||
            suggestion.symbol.equals(rawInput.trim(), ignoreCase = true) ||
            (normalizedInputDigits.isNotBlank() && normalizedInputDigits == suggestionDigits)
    }

    private fun lookupHintMessage(market: Market): String = when (market) {
        Market.A_SHARE -> "输入股票代码或名称，支持自动联想和补全"
        Market.HONG_KONG -> "输入港股代码或名称，支持自动联想和补全"
    }

    private fun buildSummary(
        portfolio: PortfolioComputation,
        refresh: RefreshState,
        refreshedAt: Long,
        refreshNote: String,
    ): PortfolioSummary {
        val aCount = portfolio.positions.values.count { it.market == Market.A_SHARE && it.quantity > 0 }
        val hkCount = portfolio.positions.values.count { it.market == Market.HONG_KONG && it.quantity > 0 }
        val totalRate = if (portfolio.totalCostCny == 0.0) {
            0.0
        } else {
            (portfolio.totalProfitCny / portfolio.totalCostCny) * 100.0
        }

        return PortfolioSummary(
            totalAssets = formatBaseAmount(portfolio.totalAssetsCny),
            totalCost = formatBaseAmount(portfolio.totalCostCny),
            totalCostHint = "A股 $aCount 只 · 港股 $hkCount 只",
            totalProfit = formatSignedBaseAmount(portfolio.totalProfitCny),
            totalProfitHint = "折算收益率 ${formatSignedPercent(totalRate)}",
            dayProfit = "${formatSignedBaseAmount(portfolio.dayProfitCny)} (${formatSignedPercent(portfolio.dayProfitPercent)})",
            refreshState = refresh,
            refreshMessage = when (refresh) {
                RefreshState.IDLE -> refreshNote.ifBlank { DEFAULT_REFRESH_MESSAGE }
                RefreshState.REFRESHING -> refreshNote
                RefreshState.FRESH -> refreshNote.ifBlank {
                    if (refreshedAt > 0L) "最近刷新 ${formatRefreshTime(refreshedAt)}" else DEFAULT_REFRESH_MESSAGE
                }
                RefreshState.FAILED -> refreshNote.ifBlank { "刷新失败，请稍后重试" }
            },
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
                        TransactionUiModel(
                            tradeType = tradeType,
                            stockName = "${transaction.name} ${transaction.symbol}",
                            stockMeta = "${market.label} · 成交价 ${formatMarketAmount(transaction.price, market)} · ${transaction.quantity} 股",
                            amountLabel = formatMarketAmount(transaction.price * transaction.quantity, market),
                            timeLabel = "${tradeType.label} ${transaction.tradeTime}",
                            feeLabel = "费用 ${formatMarketAmount(transaction.commission + transaction.tax, market)}",
                        )
                    },
                )
            }
    }

    private fun computePortfolio(
        transactions: List<TransactionEntity>,
        quotes: List<QuoteSnapshotEntity>,
    ): PortfolioComputation {
        val positions = linkedMapOf<String, PositionComputation>()
        val ordered = transactions.sortedWith(
            compareBy<TransactionEntity>({ it.tradeDate }, { it.tradeTime }, { it.createdAt }),
        )

        ordered.forEach { transaction ->
            val market = Market.valueOf(transaction.market)
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

            positions[key] = if (transaction.tradeType == TradeType.BUY.name) {
                val buyCost = transaction.price * transaction.quantity + transaction.commission + transaction.tax
                val nextQuantity = current.quantity + transaction.quantity
                val nextRemaining = current.remainingCost + buyCost
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
                current.copy(
                    quantity = nextQuantity,
                    remainingCost = nextRemaining,
                    averageCost = if (nextQuantity == 0) 0.0 else nextRemaining / nextQuantity,
                    realizedProfit = current.realizedProfit + (proceeds - removedCost),
                )
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

        val totalAssetsCny = positions.values.sumOf { position ->
            val quote = quoteMap[positionKey(position.symbol, position.market)]
            convertToCny(position.quantity * (quote?.currentPrice ?: position.averageCost), position.market)
        }
        val totalCostCny = positions.values.sumOf { position ->
            convertToCny(position.remainingCost, position.market)
        }
        val realizedProfitCny = positions.values.sumOf { position ->
            convertToCny(position.realizedProfit, position.market)
        }
        val unrealizedProfitCny = positions.values.sumOf { position ->
            val quote = quoteMap[positionKey(position.symbol, position.market)]
            val current = quote?.currentPrice ?: return@sumOf 0.0
            convertToCny((current - position.averageCost) * position.quantity, position.market)
        }
        val dayProfitCny = positions.values.sumOf { position ->
            val quote = quoteMap[positionKey(position.symbol, position.market)] ?: return@sumOf 0.0
            val current = quote.currentPrice ?: return@sumOf 0.0
            val previous = quote.previousClose ?: return@sumOf 0.0
            convertToCny((current - previous) * position.quantity, position.market)
        }
        val previousAssetValueCny = positions.values.sumOf { position ->
            val quote = quoteMap[positionKey(position.symbol, position.market)] ?: return@sumOf 0.0
            val previous = quote.previousClose ?: return@sumOf 0.0
            convertToCny(previous * position.quantity, position.market)
        }

        return PortfolioComputation(
            holdings = holdings,
            positions = positions,
            totalAssetsCny = totalAssetsCny,
            totalCostCny = totalCostCny,
            totalProfitCny = realizedProfitCny + unrealizedProfitCny,
            dayProfitCny = dayProfitCny,
            dayProfitPercent = if (previousAssetValueCny == 0.0) 0.0 else {
                (dayProfitCny / previousAssetValueCny) * 100.0
            },
        )
    }

    private fun convertToCny(value: Double, market: Market): Double = value * market.toCnyRate

    private fun formatBaseAmount(value: Double): String = "¥${numberFormatter.format(value.absoluteValue)}"

    private fun formatSignedBaseAmount(value: Double): String {
        val sign = if (value >= 0) "+" else "-"
        return "$sign${formatBaseAmount(value)}"
    }

    private fun formatMarketAmount(value: Double, market: Market): String =
        "${market.currencySymbol}${numberFormatter.format(value.absoluteValue)}"

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
            .trim()
        return sanitized.toDoubleOrNull() ?: 0.0
    }

    private fun parseQuantity(value: String): Int = value.filter { it.isDigit() }.toIntOrNull() ?: 0

    private fun positionKey(symbol: String, market: Market): String = "${market.name}:$symbol"

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
        val totalCostCny: Double,
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
    )

    private fun emptyPortfolioComputation(): PortfolioComputation = PortfolioComputation(
        holdings = emptyList(),
        positions = emptyMap(),
        totalAssetsCny = 0.0,
        totalCostCny = 0.0,
        totalProfitCny = 0.0,
        dayProfitCny = 0.0,
        dayProfitPercent = 0.0,
    )

    private companion object {
        const val MANUAL_REFRESH_INTERVAL_MS = 60_000L
        const val SYMBOL_LOOKUP_DEBOUNCE_MS = 350L

        val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val monthDayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd")
        val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")
        val numberFormatter = DecimalFormat("#,##0.00")
        val percentFormatter = DecimalFormat("0.00")
        val knownSecurities = emptyList<ResolvedSecurity>()
    }
}
