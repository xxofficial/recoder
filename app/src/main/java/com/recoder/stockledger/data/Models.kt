package com.recoder.stockledger.data

import java.time.LocalDate

enum class Market(
    val label: String,
    val currencySymbol: String,
    val toCnyRate: Double,
) {
    A_SHARE("A股", "¥", 1.0),
    HK("港股", "HK$", 0.92),
    US("美股", "$", 7.20),
    CASH("现金", "¥", 1.0),
    ;

    companion object {
        fun fromString(name: String): Market? {
            return when (name.uppercase()) {
                "HK", "HONG_KONG" -> HK
                "A_SHARE" -> A_SHARE
                "US" -> US
                "CASH" -> CASH
                else -> entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
            }
        }
    }
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
    TRANSFER_OUT("转出"),
    TRANSFER_IN("转入"),
    INTEREST("利息"),
    SPLIT("拆并股"),
    EXPIRE("期权到期"),
    DIVIDEND("分红"),
    TAX("税费"),
    ;

    val isSecurityTrade: Boolean
        get() = this == BUY || this == SELL || this == SPLIT || this == EXPIRE

    val isCashFlowPositive: Boolean
        get() = this == SELL || this == DEPOSIT || this == TRANSFER_IN || this == DIVIDEND
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
    INTEREST("利息", TradeType.INTEREST),
    TRANSFER_IN("划入/转入", TradeType.TRANSFER_IN),
    TRANSFER_OUT("划出/转出", TradeType.TRANSFER_OUT),
    SPLIT("拆并股", TradeType.SPLIT),
    EXPIRE("期权到期", TradeType.EXPIRE),
    DIVIDEND("分红", TradeType.DIVIDEND),
    TAX("税费", TradeType.TAX),
}

enum class MarketFilter(
    val label: String,
    val market: Market?,
) {
    ALL("全部市场", null),
    A_SHARE("A股", Market.A_SHARE),
    HK("港股", Market.HK),
    US("美股", Market.US),
    CASH("现金", Market.CASH),
}

enum class PdfImportMode(val label: String) {
    REGEX("正则匹配 (本地)"),
    TEXT_MODEL("大模型解析 (文本提取)"),
    ;

    val shortLabel: String
        get() = when (this) {
            REGEX -> "本地解析"
            TEXT_MODEL -> "大模型解析"
        }

    val description: String
        get() = when (this) {
            REGEX -> "正则匹配在本地运行，适合格式稳定且已验证的平台结单。"
            TEXT_MODEL -> "大模型会先提取 PDF 文本再解析，需要联网并消耗 API 额度。"
        }
}

enum class BrokerPlatform(
    val label: String,
    val shortLabel: String,
    val isConfigurable: Boolean = true,
    val supportsPdfImport: Boolean = false,
) {
    UNSPECIFIED("未设置", "未", false),
    ALIPAY("支付宝", "支"),
    EAST_MONEY("东方财富", "东财"),
    LONGBRIDGE("长桥证券", "长桥", supportsPdfImport = true),
    HSBC("汇丰银行", "HS", supportsPdfImport = true),
    USMART("uSMART", "uSMART", supportsPdfImport = true),
    ZHUORUI("卓锐证券", "卓锐", supportsPdfImport = true),
    CHIEF("致富证券", "致富"),
    SCHWAB("嘉信国际", "嘉信"),
    ;

    companion object {
        val configurableEntries: List<BrokerPlatform> = entries.filter { it.isConfigurable }
    }
}

enum class FeeEstimateStatus {
    UNAVAILABLE,
    AUTO_APPLIED,
    MANUAL_OVERRIDE,
}

data class TradeFeePlanOptionUiModel(
    val id: String,
    val label: String,
    val description: String,
    val isSelected: Boolean,
)

data class PlatformFeePlanUiModel(
    val platform: BrokerPlatform,
    val selectedPlanId: String,
    val selectedPlanLabel: String,
    val selectedPlanDescription: String,
    val options: List<TradeFeePlanOptionUiModel>,
)

enum class ImportSourceChannel(val label: String) {
    HSBC_SMS("汇丰短信"),
    HSBC_EMAIL("汇丰邮件"),
    ZHUORUI_EMAIL("卓锐邮件"),
    ZHUORUI_STATEMENT("卓锐结单"),
    PDF_STATEMENT("电子结单"),
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
    val holdingsValue: String,
    val totalFee: String,
    val totalFeeHint: String,
    val tradeCount: String,
    val tradeCountHint: String,
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
    val isOption: Boolean = false,
    val underlyingSymbol: String? = null,
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
    val primaryMeta: String,
    val secondaryMeta: String?,
    val amountLabel: String,
    val timeLabel: String,
    val feeLabel: String,
    val platform: BrokerPlatform,
    val platformLabel: String,
)

data class ManagedPlatformUiModel(
    val platform: BrokerPlatform?,
    val label: String,
    val totalAssetsLabel: String,
    val isSelected: Boolean,
)

data class PlatformVisibilityUiModel(
    val platform: BrokerPlatform,
    val label: String,
    val totalAssetsLabel: String,
    val isEnabled: Boolean,
)

data class TradeFormState(
    val selectedType: TradeType,
    val platform: BrokerPlatform,
    val market: Market,
    val symbolOrName: String,
    val tradeDate: String,
    val tradeTime: String = "",
    val priceLabel: String,
    val quantityLabel: String,
    val commissionLabel: String,
    val taxLabel: String,
    val note: String,
    val feeEstimateStatus: FeeEstimateStatus = FeeEstimateStatus.UNAVAILABLE,
    val feeEstimateSummary: String? = null,
    val feeEstimateDetail: String? = null,
    val canAutoEstimateFees: Boolean = false,
    val investorName: String? = null,
    val assetType: String = "STOCK",
    val optionUnderlyingSymbol: String = "",
    val optionExpiryDate: String = "",
    val optionType: String = "CALL",
    val optionStrikePriceLabel: String = "",
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

data class ZhuoruiPromoConfig(
    val startDate: String = "",
    val durationDays: Int = 100,
) {
    val endDate: LocalDate?
        get() {
            if (startDate.isBlank()) return null
            val parsed = runCatching { LocalDate.parse(startDate) }.getOrNull() ?: return null
            return parsed.plusDays(durationDays.toLong())
        }

    val isActive: Boolean
        get() {
            val end = endDate ?: return false
            return !LocalDate.now().isAfter(end)
        }
}

data class YahooSplitEvent(
    val date: String,
    val ratio: Double,
)
