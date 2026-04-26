package com.recoder.stockledger.data

enum class Market(
    val label: String,
    val currencySymbol: String,
    val toCnyRate: Double,
) {
    A_SHARE("A股", "¥", 1.0),
    HONG_KONG("港股", "HK$", 0.92),
    US("美股", "$", 7.20),
    CASH("现金", "¥", 1.0),
}

enum class DisplayCurrency(
    val label: String,
    val code: String,
    val symbol: String,
    val cnyRate: Double,
) {
    USD("美元", "USD", "$", 7.20),
    CNY("人民币", "CNY", "¥", 1.0),
    HKD("港币", "HKD", "HK$", 0.92),
}

enum class TradeType(val label: String) {
    BUY("买入"),
    SELL("卖出"),
    DEPOSIT("入金"),
    WITHDRAW("出金"),
    ;

    val isSecurityTrade: Boolean
        get() = this == BUY || this == SELL

    val isCashFlowPositive: Boolean
        get() = this == SELL || this == DEPOSIT
}

enum class RefreshState(val title: String) {
    IDLE("等待手动刷新"),
    REFRESHING("刷新中"),
    FRESH("刚刚刷新"),
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
    DEPOSIT("入金", TradeType.DEPOSIT),
    WITHDRAW("出金", TradeType.WITHDRAW),
}

enum class MarketFilter(
    val label: String,
    val market: Market?,
) {
    ALL("全部市场", null),
    A_SHARE("A股", Market.A_SHARE),
    HONG_KONG("港股", Market.HONG_KONG),
    US("美股", Market.US),
    CASH("现金", Market.CASH),
}

enum class BrokerPlatform(
    val label: String,
    val shortLabel: String,
    val isConfigurable: Boolean = true,
) {
    UNSPECIFIED("未设置", "未", false),
    ALIPAY("支付宝", "支"),
    EAST_MONEY("东方财富", "东财"),
    LONGBRIDGE("长桥证券", "长桥"),
    HSBC("汇丰银行", "HS"),
    WEBULL("盈立证券", "盈立"),
    ZHUORUI("卓锐证券", "卓锐"),
    ;

    companion object {
        val configurableEntries: List<BrokerPlatform> = entries.filter { it.isConfigurable }
    }
}

data class PortfolioSummary(
    val totalAssets: String,
    val totalCost: String,
    val totalCostHint: String,
    val cashBalance: String,
    val cashBalanceHint: String,
    val totalProfit: String,
    val totalProfitHint: String,
    val dayProfit: String,
    val refreshState: RefreshState,
    val refreshMessage: String,
    val refreshTimeLabel: String?,
    val showPullRefreshTime: Boolean,
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
    val dayProfitLabel: String,
    val dayProfitPercentLabel: String,
    val totalProfitLabel: String,
    val totalProfitPercentLabel: String,
    val dayTrend: PriceTrend,
    val totalTrend: PriceTrend,
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
    val id: Long,
    val tradeType: TradeType,
    val stockName: String,
    val stockMeta: String,
    val amountLabel: String,
    val timeLabel: String,
    val feeLabel: String,
    val platform: BrokerPlatform,
    val platformLabel: String,
)

data class ManagedPlatformUiModel(
    val platform: BrokerPlatform?,
    val label: String,
    val isSelected: Boolean,
)

data class TradeFormState(
    val selectedType: TradeType,
    val platform: BrokerPlatform,
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
