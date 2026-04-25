package com.recoder.stockledger.data

enum class Market(
    val label: String,
    val currencySymbol: String,
    val toCnyRate: Double,
) {
    A_SHARE("A股", "¥", 1.0),
    HONG_KONG("港股", "HK$", 0.92),
}

enum class TradeType(val label: String) {
    BUY("买入"),
    SELL("卖出"),
}

enum class RefreshState(val title: String) {
    IDLE("等待手动刷新"),
    REFRESHING("刷新中"),
    FRESH("刚更新"),
    FAILED("刷新失败"),
}

enum class SymbolLookupState {
    IDLE,
    LOOKING_UP,
    RESOLVED,
    INVALID,
}

enum class PriceTrend {
    UP,
    DOWN,
    NEUTRAL,
}

enum class TransactionFilter(
    val label: String,
    val tradeType: TradeType?,
) {
    ALL("全部", null),
    BUY("买入", TradeType.BUY),
    SELL("卖出", TradeType.SELL),
}

enum class MarketFilter(
    val label: String,
    val market: Market?,
) {
    ALL("全部市场", null),
    A_SHARE("A股", Market.A_SHARE),
    HONG_KONG("港股", Market.HONG_KONG),
}

data class PortfolioSummary(
    val totalAssets: String,
    val totalCost: String,
    val totalCostHint: String,
    val totalProfit: String,
    val totalProfitHint: String,
    val dayProfit: String,
    val refreshState: RefreshState,
    val refreshMessage: String,
)

data class HoldingUiModel(
    val name: String,
    val code: String,
    val market: Market,
    val quantityLabel: String,
    val costLabel: String,
    val priceLabel: String,
    val changeLabel: String,
    val pnlLabel: String,
    val trend: PriceTrend,
)

data class SellCandidateUiModel(
    val symbol: String,
    val name: String,
    val market: Market,
    val quantityLabel: String,
    val costLabel: String,
)

data class TransactionSection(
    val title: String,
    val items: List<TransactionUiModel>,
)

data class TransactionUiModel(
    val tradeType: TradeType,
    val stockName: String,
    val stockMeta: String,
    val amountLabel: String,
    val timeLabel: String,
    val feeLabel: String,
)

data class TradeFormState(
    val selectedType: TradeType,
    val market: Market,
    val symbolOrName: String,
    val tradeDate: String,
    val priceLabel: String,
    val quantityLabel: String,
    val commissionLabel: String,
    val taxLabel: String,
    val note: String,
)

data class SymbolLookupUiModel(
    val state: SymbolLookupState = SymbolLookupState.IDLE,
    val message: String? = null,
    val resolvedSymbol: String? = null,
    val resolvedName: String? = null,
    val resolvedMarket: Market? = null,
)

data class SecuritySuggestionUiModel(
    val symbol: String,
    val name: String,
    val market: Market,
    val displayLabel: String,
)
