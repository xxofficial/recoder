package com.recoder.stockledger.ui

import com.recoder.stockledger.data.BrokerPlatform
import com.recoder.stockledger.data.DisplayCurrency
import com.recoder.stockledger.data.FeeEstimateStatus
import com.recoder.stockledger.data.HoldingUiModel
import com.recoder.stockledger.data.ImportSourceChannel
import com.recoder.stockledger.data.Market
import com.recoder.stockledger.data.PlatformFeePlanUiModel
import com.recoder.stockledger.data.PortfolioSummary
import com.recoder.stockledger.data.PriceTrend
import com.recoder.stockledger.data.ProfitAnalysisPointUiModel
import com.recoder.stockledger.data.ProfitAnalysisUiModel
import com.recoder.stockledger.data.RefreshState
import com.recoder.stockledger.data.SampleData
import com.recoder.stockledger.data.SecurityProfitAnalysisUiModel
import com.recoder.stockledger.data.SecurityProfitPointUiModel
import com.recoder.stockledger.data.SecuritySuggestionUiModel
import com.recoder.stockledger.data.SellCandidateUiModel
import com.recoder.stockledger.data.SymbolLookupState
import com.recoder.stockledger.data.SymbolLookupUiModel
import com.recoder.stockledger.data.TradeFeePlanOptionUiModel
import com.recoder.stockledger.data.TradeType
import com.recoder.stockledger.data.TransactionSection
import com.recoder.stockledger.data.TransactionUiModel
import com.recoder.stockledger.data.ZhuoruiPromoConfig
import com.recoder.stockledger.data.local.TransactionEntity
import java.time.LocalDate

internal object PreviewFixtures {
    val portfolioSummary = PortfolioSummary(
        totalAssets = "¥236,540.10",
        totalCost = "¥182,600.00",
        totalCostHint = "累计净入金",
        cashBalance = "¥32,520.00",
        cashBalanceHint = "可用余额",
        totalProfit = "+¥53,940.10",
        totalProfitHint = "累计浮盈",
        dayProfit = "+¥1,286.32",
        holdingsValue = "¥204,020.10",
        commissionTotal = "¥893.20",
        taxTotal = "¥166.70",
        tradeCount = "128",
        refreshState = RefreshState.FRESH,
        refreshMessage = "刷新完成",
        refreshTimeLabel = "刚刚",
        showPullRefreshTime = true,
    )

    val holdings = listOf(
        HoldingUiModel(
            name = "腾讯控股",
            code = "00700",
            market = Market.HONG_KONG,
            quantityLabel = "300股",
            costLabel = "HK$320.00",
            priceLabel = "HK$371.20",
            changeLabel = "+1.83%",
            pnlLabel = "+HK$15,360.00",
            trend = PriceTrend.UP,
            dayProfitLabel = "+HK$620.00",
            dayProfitPercentLabel = "+0.56%",
            totalProfitLabel = "+HK$15,360.00",
            totalProfitPercentLabel = "+16.00%",
            dayTrend = PriceTrend.UP,
            totalTrend = PriceTrend.UP,
        ),
        HoldingUiModel(
            name = "Apple Inc.",
            code = "AAPL",
            market = Market.US,
            quantityLabel = "40股",
            costLabel = "$168.10",
            priceLabel = "$193.25",
            changeLabel = "-0.42%",
            pnlLabel = "+$1,006.00",
            trend = PriceTrend.DOWN,
            dayProfitLabel = "-$32.80",
            dayProfitPercentLabel = "-0.42%",
            totalProfitLabel = "+$1,006.00",
            totalProfitPercentLabel = "+14.96%",
            dayTrend = PriceTrend.DOWN,
            totalTrend = PriceTrend.UP,
        ),
    )

    private val points = listOf(
        ProfitAnalysisPointUiModel(
            date = LocalDate.now().minusDays(2),
            dailyProfitCny = 1200.0,
            cumulativeProfitCny = 36200.0,
            totalAssetsCny = 230000.0,
            netInflowCny = 182600.0,
            dailyReturnPercent = 0.63,
            cumulativeReturnPercent = 19.83,
        ),
        ProfitAnalysisPointUiModel(
            date = LocalDate.now().minusDays(1),
            dailyProfitCny = -380.0,
            cumulativeProfitCny = 35820.0,
            totalAssetsCny = 229620.0,
            netInflowCny = 182600.0,
            dailyReturnPercent = -0.20,
            cumulativeReturnPercent = 19.62,
        ),
        ProfitAnalysisPointUiModel(
            date = LocalDate.now(),
            dailyProfitCny = 1260.0,
            cumulativeProfitCny = 37080.0,
            totalAssetsCny = 230880.0,
            netInflowCny = 182600.0,
            dailyReturnPercent = 0.69,
            cumulativeReturnPercent = 20.31,
        ),
    )

    private val transactions = listOf(
        TransactionEntity(
            id = 1,
            tradeType = TradeType.BUY.name,
            platform = BrokerPlatform.HSBC.name,
            sourceChannel = ImportSourceChannel.HSBC_SMS.name,
            market = Market.US.name,
            symbol = "AAPL",
            name = "Apple Inc.",
            tradeDate = LocalDate.now().minusDays(30).toString(),
            tradeTime = "10:32:00",
            price = 175.20,
            quantity = 20,
            commission = 1.5,
            tax = 0.0,
            note = "",
            createdAt = System.currentTimeMillis() - 2_592_000_000,
        ),
        TransactionEntity(
            id = 2,
            tradeType = TradeType.SELL.name,
            platform = BrokerPlatform.HSBC.name,
            sourceChannel = ImportSourceChannel.HSBC_SMS.name,
            market = Market.US.name,
            symbol = "AAPL",
            name = "Apple Inc.",
            tradeDate = LocalDate.now().minusDays(10).toString(),
            tradeTime = "14:15:00",
            price = 191.80,
            quantity = 10,
            commission = 1.3,
            tax = 0.0,
            note = "止盈",
            createdAt = System.currentTimeMillis() - 864_000_000,
        ),
    )

    val profitAnalysis = ProfitAnalysisUiModel(
        dailyPoints = points,
        securityAnalyses = listOf(
            SecurityProfitAnalysisUiModel(
                symbol = "AAPL",
                name = "Apple Inc.",
                market = Market.US,
                dailyPoints = listOf(
                    SecurityProfitPointUiModel(LocalDate.now().minusDays(2), 620.0, 8920.0, 188.2),
                    SecurityProfitPointUiModel(LocalDate.now().minusDays(1), -120.0, 8800.0, 187.4),
                    SecurityProfitPointUiModel(LocalDate.now(), 380.0, 9180.0, 193.2),
                ),
            ),
            SecurityProfitAnalysisUiModel(
                symbol = "00700",
                name = "腾讯控股",
                market = Market.HONG_KONG,
                dailyPoints = listOf(
                    SecurityProfitPointUiModel(LocalDate.now().minusDays(2), 530.0, 11500.0, 368.0),
                    SecurityProfitPointUiModel(LocalDate.now().minusDays(1), 140.0, 11640.0, 369.2),
                    SecurityProfitPointUiModel(LocalDate.now(), 310.0, 11950.0, 371.2),
                ),
            ),
        ),
        netInflowCny = 182600.0,
        latestDate = LocalDate.now(),
        totalCommissionCny = 893.2,
        totalTaxCny = 166.7,
        securityTradeCount = 128,
        transactions = transactions,
    )

    val transactionSections = listOf(
        TransactionSection(
            title = "今天",
            items = listOf(
                TransactionUiModel(
                    id = 101,
                    tradeType = TradeType.BUY,
                    stockName = "Apple Inc.",
                    primaryMeta = "AAPL · 美股",
                    secondaryMeta = "10股 x $193.25",
                    amountLabel = "-$1,932.50",
                    timeLabel = "09:31",
                    feeLabel = "费 $1.70",
                    platform = BrokerPlatform.HSBC,
                    platformLabel = BrokerPlatform.HSBC.label,
                ),
            ),
        ),
        TransactionSection(
            title = "昨天",
            items = listOf(
                TransactionUiModel(
                    id = 102,
                    tradeType = TradeType.SELL,
                    stockName = "腾讯控股",
                    primaryMeta = "00700 · 港股",
                    secondaryMeta = "100股 x HK$370.60",
                    amountLabel = "+HK$37,060.00",
                    timeLabel = "15:42",
                    feeLabel = "费 HK$42.00",
                    platform = BrokerPlatform.HSBC,
                    platformLabel = BrokerPlatform.HSBC.label,
                ),
            ),
        ),
    )

    val symbolSuggestions = listOf(
        SecuritySuggestionUiModel(
            symbol = "AAPL",
            name = "Apple Inc.",
            market = Market.US,
            displayLabel = "AAPL · Apple Inc. · 美股",
        ),
    )

    val sellCandidates = listOf(
        SellCandidateUiModel(
            symbol = "AAPL",
            name = "Apple Inc.",
            market = Market.US,
            quantityLabel = "40股",
            costLabel = "$168.10",
        ),
    )

    val tradeEntryState = SampleData.tradeForm(TradeType.BUY).copy(
        symbolOrName = "AAPL",
        priceLabel = "193.25",
        quantityLabel = "10",
        commissionLabel = "1.20",
        taxLabel = "0.50",
        note = "分批建仓",
        feeEstimateStatus = FeeEstimateStatus.AUTO_APPLIED,
        feeEstimateSummary = "预计费用 ¥12.30",
        feeEstimateDetail = "包含佣金与税费",
    )

    val symbolLookup = SymbolLookupUiModel(
        state = SymbolLookupState.RESOLVED,
        message = "已匹配到证券",
        resolvedSymbol = "AAPL",
        resolvedName = "Apple Inc.",
        resolvedMarket = Market.US,
    )

    val feePlan = PlatformFeePlanUiModel(
        platform = BrokerPlatform.ZHUORUI,
        selectedPlanId = "standard",
        selectedPlanLabel = "标准费率",
        selectedPlanDescription = "佣金按成交额比例收取，平台费按每笔固定值计算。",
        options = listOf(
            TradeFeePlanOptionUiModel(
                id = "standard",
                label = "标准费率",
                description = "常规客户费率",
                isSelected = true,
            ),
            TradeFeePlanOptionUiModel(
                id = "promo",
                label = "新客免佣",
                description = "仅收平台费",
                isSelected = false,
            ),
        ),
    )

    val zhuoruiPromoConfig = ZhuoruiPromoConfig(
        startDate = LocalDate.now().minusDays(15).toString(),
        durationDays = 100,
    )

    val exchangeRates = com.recoder.stockledger.data.ExchangeRates(
        usdToCny = 7.2,
        hkdToCny = 0.92,
    )

    val hsbcImportDraftText = "您于 05/03 09:30 买入 腾讯控股 100 股，成交价 360.00 HKD"
}
