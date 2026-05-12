package com.recoder.stockledger.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TradeFeeEstimatorTest {
    @Test
    fun `hsbc hk buy uses minimum brokerage and hk levies`() {
        val estimate = TradeFeeEstimator.estimate(
            platform = BrokerPlatform.HSBC,
            market = Market.HK,
            tradeType = TradeType.BUY,
            price = 10.0,
            quantity = 100,
        )

        assertEquals(100.00, estimate.commission, 0.0001)
        assertEquals(1.09, estimate.tax, 0.0001)
        assertEquals(FeeEstimateCoverage.PARTIAL, estimate.coverage)
    }

    @Test
    fun `zhuorui us sell includes commission platform and sell side charges`() {
        val estimate = TradeFeeEstimator.estimate(
            platform = BrokerPlatform.ZHUORUI,
            market = Market.US,
            tradeType = TradeType.SELL,
            price = 42.94,
            quantity = 25,
        )

        assertEquals(1.98, estimate.commission, 0.0001)
        assertEquals(0.43, estimate.tax, 0.0001)
        assertTrue(estimate.summary.contains("手续费"))
    }

    @Test
    fun `hsbc us sell does not include sec fee after may 14 2025`() {
        val estimate = TradeFeeEstimator.estimate(
            platform = BrokerPlatform.HSBC,
            market = Market.US,
            tradeType = TradeType.SELL,
            price = 42.94,
            quantity = 25,
        )

        assertEquals(18.00, estimate.commission, 0.0001)
        assertEquals(0.01, estimate.tax, 0.0001)
        assertTrue(estimate.detail.contains("2025-05-14"))
    }

    @Test
    fun `east money us penny stock broker fees capped at one percent of turnover`() {
        val estimate = TradeFeeEstimator.estimate(
            platform = BrokerPlatform.EAST_MONEY,
            market = Market.US,
            tradeType = TradeType.BUY,
            price = 0.35,
            quantity = 1000,
        )

        assertEquals(3.50, estimate.commission, 0.0001)
        assertEquals(3.00, estimate.tax, 0.0001)
        assertEquals(FeeEstimateCoverage.FULL, estimate.coverage)
    }

    @Test
    fun `hsbc trade25 keeps zero commission while monthly turnover is within limit`() {
        val trade25PlanId = TradeFeeEstimator.availablePlans(BrokerPlatform.HSBC)
            .first { it.label == "Trade25" }
            .id
        val estimate = TradeFeeEstimator.estimate(
            platform = BrokerPlatform.HSBC,
            market = Market.US,
            tradeType = TradeType.BUY,
            price = 50.0,
            quantity = 100,
            planId = trade25PlanId,
            context = TradeFeeEstimateContext(monthlyTurnoverHkdBeforeTrade = 120_000.0),
        )

        assertEquals(0.00, estimate.commission, 0.0001)
        assertTrue(estimate.summary.contains("Trade25"))
    }

    @Test
    fun `hsbc trade25 falls back to standard commission after monthly turnover limit`() {
        val trade25PlanId = TradeFeeEstimator.availablePlans(BrokerPlatform.HSBC)
            .first { it.label == "Trade25" }
            .id
        val estimate = TradeFeeEstimator.estimate(
            platform = BrokerPlatform.HSBC,
            market = Market.US,
            tradeType = TradeType.BUY,
            price = 50.0,
            quantity = 100,
            planId = trade25PlanId,
            context = TradeFeeEstimateContext(monthlyTurnoverHkdBeforeTrade = 260_000.0),
        )

        assertEquals(18.00, estimate.commission, 0.0001)
    }

    @Test
    fun `zhuorui legacy plan omits platform fee`() {
        val legacyPlanId = TradeFeeEstimator.availablePlans(BrokerPlatform.ZHUORUI)
            .first { it.label == "老客费率待核" }
            .id
        val estimate = TradeFeeEstimator.estimate(
            platform = BrokerPlatform.ZHUORUI,
            market = Market.HK,
            tradeType = TradeType.BUY,
            price = 20.0,
            quantity = 100,
            planId = legacyPlanId,
        )

        assertEquals(3.00, estimate.commission, 0.0001)
        assertTrue(estimate.detail.contains("未自动加入老客户差异化平台费"))
    }

    @Test
    fun `chief hk buy uses online commission minimum and no platform fee`() {
        val estimate = TradeFeeEstimator.estimate(
            platform = BrokerPlatform.CHIEF,
            market = Market.HK,
            tradeType = TradeType.BUY,
            price = 20.0,
            quantity = 1000,
        )

        assertEquals(20.00, estimate.commission, 0.0001)
        assertEquals(23.70, estimate.tax, 0.0001)
        assertTrue(estimate.detail.contains("平台费 HK$0.00"))
    }

    @Test
    fun `chief us buy applies platform and clearing fees`() {
        val estimate = TradeFeeEstimator.estimate(
            platform = BrokerPlatform.CHIEF,
            market = Market.US,
            tradeType = TradeType.BUY,
            price = 10.0,
            quantity = 100,
        )

        assertEquals(0.99, estimate.commission, 0.0001)
        assertEquals(0.30, estimate.tax, 0.0001)
    }

    @Test
    fun `schwab us sell keeps zero commission and includes industry fees`() {
        val estimate = TradeFeeEstimator.estimate(
            platform = BrokerPlatform.SCHWAB,
            market = Market.US,
            tradeType = TradeType.SELL,
            price = 50.0,
            quantity = 10,
        )

        assertEquals(0.00, estimate.commission, 0.0001)
        assertEquals(0.02, estimate.tax, 0.0001)
        assertTrue(estimate.detail.contains("行业规费"))
    }
}
