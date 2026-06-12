package com.recoder.stockledger.domain.portfolio

import com.recoder.stockledger.data.ExchangeRates
import com.recoder.stockledger.data.Market
import com.recoder.stockledger.data.TradeType
import org.junit.Assert.assertEquals
import org.junit.Test

class PortfolioCalculatorTest {
    private val calculator = PortfolioCalculator()

    @Test
    fun `calculate handles cash deposit and usd buy with quote`() {
        val snapshot = calculator.calculate(
            transactions = listOf(
                trade(type = TradeType.DEPOSIT, market = Market.CASH, price = 10_000.0, quantity = 1),
                trade(type = TradeType.BUY, market = Market.US, symbol = "AAPL", price = 100.0, quantity = 10, commission = 1.0),
            ),
            quotes = listOf(
                PortfolioQuote(
                    symbol = "AAPL",
                    market = Market.US,
                    currentPrice = 110.0,
                    previousClose = 105.0,
                ),
            ),
            exchangeRates = ExchangeRates(usdToCny = 7.0, hkdToCny = 0.9),
        )

        assertEquals(1, snapshot.positions.size)
        assertEquals(10, snapshot.positions.getValue("US:AAPL").quantity)
        assertEquals(100.1, snapshot.positions.getValue("US:AAPL").averageCost, 0.0001)
        assertEquals(2_993.0, snapshot.cashBalanceCny, 0.0001)
        assertEquals(7_700.0, snapshot.holdingsValueCny, 0.0001)
        assertEquals(10_693.0, snapshot.totalAssetsCny, 0.0001)
        assertEquals(350.0, snapshot.dayProfitCny, 0.0001)
    }

    @Test
    fun `calculate handles partial sell and realized position state`() {
        val snapshot = calculator.calculate(
            transactions = listOf(
                trade(type = TradeType.BUY, market = Market.HK, symbol = "0700.HK", price = 300.0, quantity = 10),
                trade(type = TradeType.SELL, market = Market.HK, symbol = "0700.HK", price = 330.0, quantity = 4, commission = 2.0),
            ),
            quotes = emptyList(),
            exchangeRates = ExchangeRates(usdToCny = 7.0, hkdToCny = 1.0),
        )

        val position = snapshot.positions.getValue("HK:0700.HK")
        assertEquals(6, position.quantity)
        assertEquals(300.0, position.averageCost, 0.0001)
        assertEquals(120.0, position.realizedProfit, 0.0001)
        assertEquals(-1_682.0, snapshot.cashBalanceCny, 0.0001)
    }

    @Test
    fun `calculate handles transfer of cash and stocks`() {
        val snapshot = calculator.calculate(
            transactions = listOf(
                trade(type = TradeType.DEPOSIT, market = Market.CASH, price = 10_000.0, quantity = 1),
                trade(type = TradeType.BUY, market = Market.US, symbol = "AAPL", price = 100.0, quantity = 10),
                trade(type = TradeType.TRANSFER_OUT, market = Market.US, symbol = "AAPL", price = 100.0, quantity = 5),
                trade(type = TradeType.TRANSFER_IN, market = Market.US, symbol = "AAPL", price = 100.0, quantity = 5),
                trade(type = TradeType.TRANSFER_OUT, market = Market.CASH, symbol = "CASH", price = 1000.0, quantity = 1),
                trade(type = TradeType.TRANSFER_IN, market = Market.CASH, symbol = "CASH", price = 1000.0, quantity = 1),
            ),
            quotes = listOf(
                PortfolioQuote(
                    symbol = "AAPL",
                    market = Market.US,
                    currentPrice = 110.0,
                    previousClose = 105.0,
                ),
            ),
            exchangeRates = ExchangeRates(usdToCny = 7.0, hkdToCny = 0.9),
        )

        assertEquals(1, snapshot.positions.size)
        assertEquals(10, snapshot.positions.getValue("US:AAPL").quantity)
        assertEquals(100.0, snapshot.positions.getValue("US:AAPL").averageCost, 0.0001)
        assertEquals(3_000.0, snapshot.cashBalanceCny, 0.0001)
    }

    @Test
    fun `calculate handles option expiration`() {
        val usdRate = 7.0
        val optSymbol = "AAPL 260618C00150000"
        
        // 1. Long option expiration (Buy 1 Call, then expire)
        val snapshotLong = calculator.calculate(
            transactions = listOf(
                trade(type = TradeType.DEPOSIT, market = Market.CASH, price = 1000.0, quantity = 1),
                trade(type = TradeType.BUY, market = Market.US, symbol = optSymbol, price = 2.0, quantity = 1),
                trade(type = TradeType.EXPIRE, market = Market.US, symbol = optSymbol, price = 0.0, quantity = 1),
            ),
            quotes = emptyList(),
            exchangeRates = ExchangeRates(usdToCny = usdRate, hkdToCny = 1.0),
        )
        
        val posLong = snapshotLong.positions.getValue("US:$optSymbol")
        assertEquals(0, posLong.quantity)
        assertEquals(0.0, posLong.remainingCost, 0.0001)
        assertEquals(0.0, posLong.averageCost, 0.0001)
        assertEquals(-200.0, posLong.realizedProfit, 0.0001)
        // Cash spent is 2.0 * 1 * 100 * 7.0 = 1400.0. Since deposit was 1000.0, balance is -400.0.
        assertEquals(-400.0, snapshotLong.cashBalanceCny, 0.0001)

        // 2. Short option expiration (Sell 1 Call, then expire)
        val snapshotShort = calculator.calculate(
            transactions = listOf(
                trade(type = TradeType.SELL, market = Market.US, symbol = optSymbol, price = 2.0, quantity = 1),
                trade(type = TradeType.EXPIRE, market = Market.US, symbol = optSymbol, price = 0.0, quantity = 1),
            ),
            quotes = emptyList(),
            exchangeRates = ExchangeRates(usdToCny = usdRate, hkdToCny = 1.0),
        )
        
        val posShort = snapshotShort.positions.getValue("US:$optSymbol")
        assertEquals(0, posShort.quantity)
        assertEquals(0.0, posShort.remainingCost, 0.0001)
        assertEquals(0.0, posShort.averageCost, 0.0001)
        assertEquals(200.0, posShort.realizedProfit, 0.0001)
        assertEquals(1400.0, snapshotShort.cashBalanceCny, 0.0001)
    }

    private fun trade(
        type: TradeType,
        market: Market,
        symbol: String = "",
        name: String = symbol,
        price: Double,
        quantity: Int,
        commission: Double = 0.0,
        tax: Double = 0.0,
    ): PortfolioTrade = PortfolioTrade(
        tradeType = type,
        market = market,
        symbol = symbol,
        name = name,
        tradeDate = "2026-01-02",
        tradeTime = "10:00",
        price = price,
        quantity = quantity,
        commission = commission,
        tax = tax,
        createdAt = 1L,
    )
}
