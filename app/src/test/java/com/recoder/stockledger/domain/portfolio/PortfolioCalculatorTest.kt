package com.recoder.stockledger.domain.portfolio

import com.recoder.stockledger.data.ExchangeRates
import com.recoder.stockledger.data.Market
import com.recoder.stockledger.data.TradeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PortfolioCalculatorTest {
    private val calculator = PortfolioCalculator()

    @Test
    fun testFormatQuantity() {
        listOf(20.0, 2.0, 20.5, 20.05, 0.0, 200.0, 20.0001).forEach { qty ->
            val formatted = String.format(java.util.Locale.US, "%.2f", qty)
            val res = if (formatted.contains(".")) {
                formatted.dropLastWhile { it == '0' }.removeSuffix(".")
            } else {
                formatted
            }
            println("formatQuantity($qty) = '$res'")
        }
    }

    @Test
    fun testDramCalculation() {
        val trades = listOf(
            trade(type = TradeType.BUY, market = Market.US, symbol = "DRAM", name = "存储ETF", price = 27.17, quantity = 6.0, tradeDate = "2026-04-02", tradeTime = "22:01"),
            trade(type = TradeType.BUY, market = Market.US, symbol = "DRAM", name = "存储ETF", price = 32.31, quantity = 8.0, tradeDate = "2026-04-08", tradeTime = "23:59"),
            trade(type = TradeType.BUY, market = Market.US, symbol = "DRAM", name = "存储ETF", price = 32.3, quantity = 5.0, tradeDate = "2026-04-09", tradeTime = "11:16"),
            trade(type = TradeType.BUY, market = Market.US, symbol = "DRAM", name = "存储ETF", price = 34.0, quantity = 5.0, tradeDate = "2026-04-15", tradeTime = "23:02"),
            trade(type = TradeType.SELL, market = Market.US, symbol = "DRAM", name = "存储ETF", price = 34.97, quantity = 4.0, tradeDate = "2026-04-17", tradeTime = "09:37"),
            trade(type = TradeType.SELL, market = Market.US, symbol = "DRAM", name = "存储ETF", price = 35.72, quantity = 10.0, tradeDate = "2026-04-17", tradeTime = "23:51"),
            trade(type = TradeType.BUY, market = Market.US, symbol = "DRAM", name = "存储ETF", price = 36.37, quantity = 2.0, tradeDate = "2026-04-25", tradeTime = "09:37"),
            trade(type = TradeType.BUY, market = Market.US, symbol = "DRAM", name = "存储ETF", price = 38.9, quantity = 3.0, tradeDate = "2026-04-30", tradeTime = "08:23"),
            trade(type = TradeType.BUY, market = Market.US, symbol = "DRAM", name = "存储ETF", price = 42.15, quantity = 5.0, tradeDate = "2026-05-04", tradeTime = "17:11"),
            trade(type = TradeType.SELL, market = Market.US, symbol = "DRAM", name = "存储ETF", price = 48.01, quantity = 3.0, tradeDate = "2026-05-07", tradeTime = "00:31"),
            trade(type = TradeType.BUY, market = Market.US, symbol = "DRAM", name = "存储ETF", price = 51.03, quantity = 3.0, tradeDate = "2026-05-18", tradeTime = "22:06"),
            // Option trade
            trade(type = TradeType.BUY, market = Market.US, symbol = "DRAM 260618C65", name = "DRAM Jun 2026 65.000 call", price = 3.8, quantity = 1.0, tradeDate = "2026-05-11", tradeTime = "22:06")
        )
        val snapshot = calculator.calculate(
            transactions = trades,
            quotes = listOf(
                PortfolioQuote(symbol = "DRAM", market = Market.US, currentPrice = 65.12, previousClose = 65.12),
                PortfolioQuote(symbol = "DRAM 260618C65", market = Market.US, currentPrice = 3.75, previousClose = 3.75)
            ),
            exchangeRates = ExchangeRates(usdToCny = 7.0, hkdToCny = 1.0)
        )
        println("COMPUTED POSITIONS:")
        snapshot.positions.forEach { (key, pos) ->
            println("$key: qty=${pos.quantity}, avgCost=${pos.averageCost}, remainingCost=${pos.remainingCost}")
        }
    }

    @Test
    fun `calculate handles cash deposit and usd buy with quote`() {
        val snapshot = calculator.calculate(
            transactions = listOf(
                trade(type = TradeType.DEPOSIT, market = Market.CASH, price = 10_000.0, quantity = 1.0),
                trade(type = TradeType.BUY, market = Market.US, symbol = "AAPL", price = 100.0, quantity = 10.0, commission = 1.0),
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
        assertEquals(10.0, snapshot.positions.getValue("US:AAPL").quantity, 0.0001)
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
                trade(type = TradeType.BUY, market = Market.HK, symbol = "0700.HK", price = 300.0, quantity = 10.0),
                trade(type = TradeType.SELL, market = Market.HK, symbol = "0700.HK", price = 330.0, quantity = 4.0, commission = 2.0),
            ),
            quotes = emptyList(),
            exchangeRates = ExchangeRates(usdToCny = 7.0, hkdToCny = 1.0),
        )

        val position = snapshot.positions.getValue("HK:0700.HK")
        assertEquals(6.0, position.quantity, 0.0001)
        assertEquals(300.0, position.averageCost, 0.0001)
        assertEquals(118.0, position.realizedProfit, 0.0001)
        assertEquals(-1_682.0, snapshot.cashBalanceCny, 0.0001)
    }

    @Test
    fun `calculate handles transfer of cash and stocks`() {
        val snapshot = calculator.calculate(
            transactions = listOf(
                trade(type = TradeType.DEPOSIT, market = Market.CASH, price = 10_000.0, quantity = 1.0),
                trade(type = TradeType.BUY, market = Market.US, symbol = "AAPL", price = 100.0, quantity = 10.0),
                trade(type = TradeType.TRANSFER_OUT, market = Market.US, symbol = "AAPL", price = 100.0, quantity = 5.0),
                trade(type = TradeType.TRANSFER_IN, market = Market.US, symbol = "AAPL", price = 100.0, quantity = 5.0),
                trade(type = TradeType.TRANSFER_OUT, market = Market.CASH, symbol = "CASH", price = 1000.0, quantity = 1.0),
                trade(type = TradeType.TRANSFER_IN, market = Market.CASH, symbol = "CASH", price = 1000.0, quantity = 1.0),
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
        assertEquals(10.0, snapshot.positions.getValue("US:AAPL").quantity, 0.0001)
        assertEquals(100.0, snapshot.positions.getValue("US:AAPL").averageCost, 0.0001)
        assertEquals(3_000.0, snapshot.cashBalanceCny, 0.0001)
    }

    @Test
    fun `calculate handles stock split and reverse split`() {
        // AAPL buy 10 shares at $100. Then AAPL splits 2-for-1 (ratio = 2.0).
        // Total cost remains $1000. Qty becomes 20. Average cost becomes $50.
        val snapshot = calculator.calculate(
            transactions = listOf(
                trade(type = TradeType.BUY, market = Market.US, symbol = "AAPL", price = 100.0, quantity = 10.0),
                trade(type = TradeType.SPLIT, market = Market.US, symbol = "AAPL", price = 2.0, quantity = 1.0),
            ),
            quotes = listOf(
                PortfolioQuote(
                    symbol = "AAPL",
                    market = Market.US,
                    currentPrice = 60.0,
                    previousClose = 55.0,
                ),
            ),
            exchangeRates = ExchangeRates(usdToCny = 7.0, hkdToCny = 0.9),
        )

        assertEquals(1, snapshot.positions.size)
        val position = snapshot.positions.getValue("US:AAPL")
        assertEquals(20.0, position.quantity, 0.0001)
        assertEquals(50.0, position.averageCost, 0.0001)
        assertEquals(1000.0, position.remainingCost, 0.0001)
    }

    @Test
    fun `calculate handles US stock split timezone sorting`() {
        // Test that a US split on 2026-03-10 00:00:00 (which is the effective date of the split)
        // is sorted BEFORE a US trade on 2026-03-11 05:44 HKT (which gets shifted to 2026-03-10 effective date)
        // and also BEFORE a US trade on 2026-03-10 22:06 HKT (which is on the same effective date).
        val snapshot = calculator.calculate(
            transactions = listOf(
                trade(type = TradeType.BUY, market = Market.US, symbol = "LITX", price = 10.0, quantity = 7.0, tradeDate = "2026-03-09", tradeTime = "17:14"),
                // Sell execution recorded at 2026-03-10 22:06 HKT (effective date 2026-03-10)
                trade(type = TradeType.SELL, market = Market.US, symbol = "LITX", price = 10.0, quantity = 6.0, tradeDate = "2026-03-10", tradeTime = "22:06"),
                // Split recorded on 2026-03-10 with time 00:00:00 (due to date shifting to match US execution date)
                trade(type = TradeType.SPLIT, market = Market.US, symbol = "LITX", price = 3.0, quantity = 1.0, tradeDate = "2026-03-10", tradeTime = "00:00:00"),
                // Sell execution recorded at 2026-03-11 05:44 HKT (effective date 2026-03-10)
                trade(type = TradeType.SELL, market = Market.US, symbol = "LITX", price = 10.0, quantity = 5.0, tradeDate = "2026-03-11", tradeTime = "05:44"),
            ),
            quotes = emptyList(),
            exchangeRates = ExchangeRates(usdToCny = 7.0, hkdToCny = 1.0),
        )
        
        // 7.0 (buy) * 3 (split) = 21.0
        // 21.0 - 6.0 (sell 1) - 5.0 (sell 2) = 10.0
        val pos = snapshot.positions.getValue("US:LITX")
        assertEquals(10.0, pos.quantity, 0.0001)
    }

    @Test
    fun `calculate handles option expiration`() {
        val usdRate = 7.0
        val optSymbol = "AAPL 260618C00150000"
        
        // 1. Long option expiration (Buy 1 Call, then expire)
        val snapshotLong = calculator.calculate(
            transactions = listOf(
                trade(type = TradeType.DEPOSIT, market = Market.CASH, price = 1000.0, quantity = 1.0),
                trade(type = TradeType.BUY, market = Market.US, symbol = optSymbol, price = 2.0, quantity = 1.0),
                trade(type = TradeType.EXPIRE, market = Market.US, symbol = optSymbol, price = 0.0, quantity = 1.0),
            ),
            quotes = emptyList(),
            exchangeRates = ExchangeRates(usdToCny = usdRate, hkdToCny = 1.0),
        )
        
        val posLong = snapshotLong.positions.getValue("US:$optSymbol")
        assertEquals(0.0, posLong.quantity, 0.0001)
        assertEquals(0.0, posLong.remainingCost, 0.0001)
        assertEquals(0.0, posLong.averageCost, 0.0001)
        assertEquals(-200.0, posLong.realizedProfit, 0.0001)
        // Cash spent is 2.0 * 1 * 100 * 7.0 = 1400.0. Since deposit was 1000.0, balance is -400.0.
        assertEquals(-400.0, snapshotLong.cashBalanceCny, 0.0001)

        // 2. Short option expiration (Sell 1 Call, then expire)
        val snapshotShort = calculator.calculate(
            transactions = listOf(
                trade(type = TradeType.SELL, market = Market.US, symbol = optSymbol, price = 2.0, quantity = 1.0),
                trade(type = TradeType.EXPIRE, market = Market.US, symbol = optSymbol, price = 0.0, quantity = 1.0),
            ),
            quotes = emptyList(),
            exchangeRates = ExchangeRates(usdToCny = usdRate, hkdToCny = 1.0),
        )
        
        val posShort = snapshotShort.positions.getValue("US:$optSymbol")
        assertEquals(0.0, posShort.quantity, 0.0001)
        assertEquals(0.0, posShort.remainingCost, 0.0001)
        assertEquals(0.0, posShort.averageCost, 0.0001)
        assertEquals(200.0, posShort.realizedProfit, 0.0001)
        assertEquals(1400.0, snapshotShort.cashBalanceCny, 0.0001)
    }

    @Test
    fun `calculate handles option expiration sorting with specific trade times`() {
        val usdRate = 7.0
        val optSymbol = "SLV 260413P66"
        
        // Scenario: A BUY transaction at 09:38 on 2026-04-13
        // And an EXPIRE transaction at 23:59:59 on 2026-04-13
        // Verify that the EXPIRE transaction is sorted AFTER the BUY transaction
        val snapshot = calculator.calculate(
            transactions = listOf(
                trade(
                    type = TradeType.BUY,
                    market = Market.US,
                    symbol = optSymbol,
                    price = 1.0,
                    quantity = 1.0,
                    tradeDate = "2026-04-13",
                    tradeTime = "09:38:00"
                ),
                trade(
                    type = TradeType.EXPIRE,
                    market = Market.US,
                    symbol = optSymbol,
                    price = 0.0,
                    quantity = 1.0,
                    tradeDate = "2026-04-13",
                    tradeTime = "23:59:59"
                )
            ),
            quotes = emptyList(),
            exchangeRates = ExchangeRates(usdToCny = usdRate, hkdToCny = 1.0),
        )
        
        val pos = snapshot.positions.getValue("US:$optSymbol")
        assertEquals(0.0, pos.quantity, 0.0001)
        assertEquals(0.0, pos.remainingCost, 0.0001)
        assertEquals(-100.0, pos.realizedProfit, 0.0001)
    }

    @Test
    fun `calculate keeps April SPY options separate from SPY stock and includes fees`() {
        val snapshot = calculator.calculate(
            transactions = listOf(
                trade(
                    type = TradeType.BUY,
                    market = Market.US,
                    symbol = "SPY 260407P645",
                    name = "SPY 2026-04-07 Put @ 645",
                    price = 0.45,
                    quantity = 1.0,
                    commission = 1.79,
                    tax = 0.24,
                    tradeDate = "2026-04-07",
                    tradeTime = "00:41",
                    assetType = "OPTION"
                ),
                trade(
                    type = TradeType.SELL,
                    market = Market.US,
                    symbol = "SPY 260407P645",
                    name = "SPY 2026-04-07 Put @ 645",
                    price = 0.03,
                    quantity = 1.0,
                    commission = 1.29,
                    tax = 0.25,
                    tradeDate = "2026-04-07",
                    tradeTime = "03:02",
                    assetType = "OPTION"
                ),
                trade(
                    type = TradeType.BUY,
                    market = Market.US,
                    symbol = "SPY 260409P666",
                    name = "SPY 2026-04-09 Put @ 666",
                    price = 0.56,
                    quantity = 1.0,
                    commission = 1.79,
                    tax = 0.24,
                    tradeDate = "2026-04-08",
                    tradeTime = "23:43",
                    assetType = "OPTION"
                ),
                trade(
                    type = TradeType.EXPIRE,
                    market = Market.US,
                    symbol = "SPY 260409P666",
                    name = "SPY 2026-04-09 Put @ 666",
                    price = 0.0,
                    quantity = 1.0,
                    tradeDate = "2026-04-09",
                    tradeTime = "23:59:59",
                    assetType = "OPTION"
                )
            ),
            quotes = listOf(
                PortfolioQuote(symbol = "SPY", market = Market.US, currentPrice = 737.76, previousClose = 725.43)
            ),
            exchangeRates = ExchangeRates(usdToCny = 7.0, hkdToCny = 1.0),
        )

        assertFalse(snapshot.positions.containsKey("US:SPY"))
        assertEquals(-45.57, snapshot.positions.getValue("US:SPY 260407P645").realizedProfit, 0.0001)
        assertEquals(-58.03, snapshot.positions.getValue("US:SPY 260409P666").realizedProfit, 0.0001)
        assertEquals(-103.60, snapshot.positions.values.sumOf { it.realizedProfit }, 0.0001)
        assertEquals(0.0, snapshot.holdingsValueCny, 0.0001)
    }

    @Test
    fun `calculate ignores informational currency conversion records`() {
        val baseline = calculator.calculate(
            transactions = listOf(
                trade(type = TradeType.DEPOSIT, market = Market.HK, symbol = "CASH", price = 19_000.0, quantity = 1.0)
            ),
            quotes = emptyList(),
            exchangeRates = ExchangeRates(usdToCny = 7.0, hkdToCny = 0.9),
        )
        val withFx = calculator.calculate(
            transactions = listOf(
                trade(type = TradeType.DEPOSIT, market = Market.HK, symbol = "CASH", price = 19_000.0, quantity = 1.0),
                trade(type = TradeType.FX_CONVERSION, market = Market.HK, symbol = "CASH", price = 5_000.0, quantity = 1.0),
            ),
            quotes = emptyList(),
            exchangeRates = ExchangeRates(usdToCny = 7.0, hkdToCny = 0.9),
        )

        assertEquals(baseline.cashBalanceCny, withFx.cashBalanceCny, 0.0001)
        assertEquals(baseline.netInflowCny, withFx.netInflowCny, 0.0001)
        assertEquals(baseline.totalAssetsCny, withFx.totalAssetsCny, 0.0001)
        assertEquals(baseline.totalDepositCny, withFx.totalDepositCny, 0.0001)
        assertEquals(baseline.totalWithdrawCny, withFx.totalWithdrawCny, 0.0001)
    }

    @Test
    fun testCalculateAllLedger1() {
        val txnsJson = java.io.File("src/test/resources/ledger_1_txns.json").readText()
        val quotesJson = java.io.File("src/test/resources/ledger_1_quotes.json").readText()
        
        val trades = parseTransactionsJson(txnsJson)
        val quotes = parseQuotesJson(quotesJson)
        
        val snapshot = calculator.calculate(
            transactions = trades,
            quotes = quotes,
            exchangeRates = ExchangeRates(usdToCny = 7.0, hkdToCny = 0.9)
        )
        
        println("ALL COMPUTED POSITIONS:")
        snapshot.positions.forEach { (key, pos) ->
            println("$key: name=${pos.name}, qty=${pos.quantity}, avgCost=${pos.averageCost}, remainingCost=${pos.remainingCost}")
        }
    }

    @Test
    fun testCashAndStockDepositWithdrawal() {
        val usdRate = 7.0
        // Scenario:
        // 1. Deposit cash: 10,000 USD (market = Market.CASH, symbol = "CASH")
        // 2. Deposit stock: 100 shares of TSLA at $200 (market = Market.US, symbol = "TSLA")
        // 3. Withdraw stock: 20 shares of TSLA at $200 (market = Market.US, symbol = "TSLA")
        // 4. Withdraw cash: 1,000 USD (market = Market.CASH, symbol = "CASH")
        val snapshot = calculator.calculate(
            transactions = listOf(
                trade(
                    type = TradeType.DEPOSIT,
                    market = Market.CASH,
                    symbol = "CASH",
                    price = 10000.0,
                    quantity = 1.0
                ),
                trade(
                    type = TradeType.DEPOSIT,
                    market = Market.US,
                    symbol = "TSLA",
                    price = 200.0,
                    quantity = 100.0
                ),
                trade(
                    type = TradeType.WITHDRAW,
                    market = Market.US,
                    symbol = "TSLA",
                    price = 200.0,
                    quantity = 20.0
                ),
                trade(
                    type = TradeType.WITHDRAW,
                    market = Market.CASH,
                    symbol = "CASH",
                    price = 1000.0,
                    quantity = 1.0
                )
            ),
            quotes = emptyList(),
            exchangeRates = ExchangeRates(usdToCny = usdRate, hkdToCny = 1.0),
        )

        // Cash balance should reflect only cash transactions: 10,000 - 1,000 = 9,000 USD.
        // In CNY: 9,000 * 1.0 = 9000.0
        assertEquals(9000.0, snapshot.cashBalanceCny, 0.0001)

        // Stock position for TSLA should reflect deposits/withdrawals: 100 - 20 = 80 shares.
        val tslaPos = snapshot.positions.getValue("US:TSLA")
        assertEquals(80.0, tslaPos.quantity, 0.0001)
        // Average cost and remaining cost:
        // Initial deposit remaining cost: 100 * 200 = 20,000 USD.
        // Withdrawal of 20 shares: remaining cost decreases by 20 * 200 = 4,000 USD.
        // Final remaining cost: 16,000 USD. Average cost: 200.0.
        assertEquals(16000.0, tslaPos.remainingCost, 0.0001)
        assertEquals(200.0, tslaPos.averageCost, 0.0001)
    }

    private fun parseTransactionsJson(json: String): List<PortfolioTrade> {
        val list = mutableListOf<PortfolioTrade>()
        val objRegex = Regex("""\{([^}]+)}""", RegexOption.DOT_MATCHES_ALL)
        objRegex.findAll(json).forEach { match ->
            val content = match.groupValues[1]
            val symbol = extractStringField(content, "symbol")
            val name = extractStringField(content, "name")
            val market = extractStringField(content, "market")
            val tradeType = extractStringField(content, "tradeType")
            val price = extractDoubleField(content, "price")
            val quantity = extractDoubleField(content, "quantity")
            val commission = extractDoubleField(content, "commission")
            val tax = extractDoubleField(content, "tax")
            val tradeDate = extractStringField(content, "tradeDate")
            val tradeTime = extractStringField(content, "tradeTime")
            val createdAt = extractLongField(content, "createdAt")
            val assetType = extractStringField(content, "assetType")
            
            list.add(PortfolioTrade(
                tradeType = TradeType.valueOf(tradeType),
                market = Market.valueOf(market),
                symbol = symbol,
                name = name,
                tradeDate = tradeDate,
                tradeTime = tradeTime,
                price = price,
                quantity = quantity,
                commission = commission,
                tax = tax,
                createdAt = createdAt,
                assetType = assetType,
            ))
        }
        return list
    }

    private fun parseQuotesJson(json: String): List<PortfolioQuote> {
        val list = mutableListOf<PortfolioQuote>()
        val objRegex = Regex("""\{([^}]+)}""", RegexOption.DOT_MATCHES_ALL)
        objRegex.findAll(json).forEach { match ->
            val content = match.groupValues[1]
            val symbol = extractStringField(content, "symbol")
            val market = extractStringField(content, "market")
            val currentPrice = extractDoubleFieldOrNull(content, "currentPrice")
            val previousClose = extractDoubleFieldOrNull(content, "previousClose")
            list.add(PortfolioQuote(
                symbol = symbol,
                market = Market.valueOf(market),
                currentPrice = currentPrice,
                previousClose = previousClose
            ))
        }
        return list
    }

    private fun extractStringField(jsonObj: String, fieldName: String): String {
        val regex = Regex("\"$fieldName\"\\s*:\\s*\"([^\"]*)\"")
        return regex.find(jsonObj)?.groupValues?.get(1) ?: ""
    }

    private fun extractDoubleField(jsonObj: String, fieldName: String): Double {
        val regex = Regex("\"$fieldName\"\\s*:\\s*([0-9.-]+)")
        return regex.find(jsonObj)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
    }

    private fun extractDoubleFieldOrNull(jsonObj: String, fieldName: String): Double? {
        val regex = Regex("\"$fieldName\"\\s*:\\s*([0-9.-]+)")
        return regex.find(jsonObj)?.groupValues?.get(1)?.toDoubleOrNull()
    }

    private fun extractLongField(jsonObj: String, fieldName: String): Long {
        val regex = Regex("\"$fieldName\"\\s*:\\s*([0-9]+)")
        return regex.find(jsonObj)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    }

    private fun trade(
        type: TradeType,
        market: Market,
        symbol: String = "",
        name: String = symbol,
        price: Double,
        quantity: Double,
        commission: Double = 0.0,
        tax: Double = 0.0,
        tradeDate: String = "2026-01-02",
        tradeTime: String = "10:00",
        assetType: String = "",
    ): PortfolioTrade = PortfolioTrade(
        tradeType = type,
        market = market,
        symbol = symbol,
        name = name,
        tradeDate = tradeDate,
        tradeTime = tradeTime,
        price = price,
        quantity = quantity,
        commission = commission,
        tax = tax,
        createdAt = 1L,
        assetType = assetType,
    )

    @Test
    fun testUserDatabase() {
        val dbFile = java.io.File("e:\\AndroidWorkSpace\\recoder\\tmp\\stock-ledger-emulator.db")
        if (!dbFile.exists()) {
            println("stock-ledger-emulator.db does not exist!")
            return
        }
        val trades = mutableListOf<PortfolioTrade>()
        val conn = try {
            java.sql.DriverManager.getConnection("jdbc:sqlite:" + dbFile.absolutePath)
        } catch (error: java.sql.SQLException) {
            println("Skipping local database smoke test: ${error.message}")
            return
        }
        val stmt = conn.createStatement()
        val rs = stmt.executeQuery("SELECT * FROM transactions WHERE ledgerId = 1")
        while (rs.next()) {
            trades.add(PortfolioTrade(
                tradeType = TradeType.valueOf(rs.getString("tradeType")),
                market = Market.fromString(rs.getString("market")) ?: Market.CASH,
                symbol = rs.getString("symbol"),
                name = rs.getString("name"),
                tradeDate = rs.getString("tradeDate"),
                tradeTime = rs.getString("tradeTime"),
                price = rs.getDouble("price"),
                quantity = rs.getDouble("quantity"),
                commission = rs.getDouble("commission"),
                tax = rs.getDouble("tax"),
                createdAt = rs.getLong("createdAt"),
                assetType = rs.getString("assetType"),
            ))
        }
        conn.close()
        
        val quotes = mutableListOf<PortfolioQuote>()
        val connQuotes = try {
            java.sql.DriverManager.getConnection("jdbc:sqlite:" + dbFile.absolutePath)
        } catch (error: java.sql.SQLException) {
            println("Skipping local quote load: ${error.message}")
            return
        }
        val stmtQuotes = connQuotes.createStatement()
        val rsQuotes = stmtQuotes.executeQuery("SELECT * FROM quote_snapshots")
        while (rsQuotes.next()) {
            quotes.add(PortfolioQuote(
                symbol = rsQuotes.getString("symbol"),
                market = Market.fromString(rsQuotes.getString("market")) ?: Market.CASH,
                currentPrice = if (rsQuotes.getObject("currentPrice") == null) null else rsQuotes.getDouble("currentPrice"),
                previousClose = if (rsQuotes.getObject("previousClose") == null) null else rsQuotes.getDouble("previousClose"),
            ))
        }
        connQuotes.close()


        
        val exchangeRates = ExchangeRates(usdToCny = 6.7658, hkdToCny = 0.86247)
        println("Parsed trades size: ${trades.size}")
        println("Parsed quotes size: ${quotes.size}")
        if (trades.isNotEmpty()) {
            println("First trade: ${trades.first()}")
        }
        val snapshot = calculator.calculate(trades, quotes, exchangeRates)
        println("--- KOTLIN PORTFOLIO CALCULATOR SNAPSHOT ---")
        println("Total Assets CNY: ${snapshot.totalAssetsCny}")
        println("Holdings Value CNY: ${snapshot.holdingsValueCny}")
        println("Cash Balance CNY: ${snapshot.cashBalanceCny}")
        println("Total Deposit CNY: ${snapshot.totalDepositCny}")
        println("Total Withdraw CNY: ${snapshot.totalWithdrawCny}")
        println("Net Inflow CNY: ${snapshot.netInflowCny}")
        println("Total Profit CNY: ${snapshot.totalAssetsCny - snapshot.netInflowCny}")
        println("unrealizedProfitCny: ${snapshot.unrealizedProfitCny}")
        
        println("--- Computed Positions ---")
        snapshot.positions.forEach { (key, pos) ->
            if (pos.quantity != 0.0 || key.contains("LITX")) {
                println("Key: $key, Qty: ${pos.quantity}, AvgCost: ${pos.averageCost}, RemCost: ${pos.remainingCost}, Realized: ${pos.realizedProfit}")
            }
        }
    }

    @Test
    fun `calculate handles dividend tax and other income`() {
        val usdRate = 7.0
        val snapshot = calculator.calculate(
            transactions = listOf(
                trade(type = TradeType.BUY, market = Market.US, symbol = "AAPL", price = 100.0, quantity = 10.0),
                trade(type = TradeType.DIVIDEND, market = Market.US, symbol = "AAPL", price = 1.5, quantity = 10.0, tax = 1.5),
                trade(type = TradeType.OTHER, market = Market.US, symbol = "CASH", price = 100.0, quantity = 1.0),
            ),
            quotes = emptyList(),
            exchangeRates = ExchangeRates(usdToCny = usdRate, hkdToCny = 1.0),
        )

        // Cash balance should be -1000 + (15 - 1.5) + 100 = -886.5 USD.
        assertEquals(-886.5 * usdRate, snapshot.cashBalanceCny, 0.0001)

        val position = snapshot.positions.getValue("US:AAPL")
        assertEquals(10.0, position.quantity, 0.0001)
        assertEquals(100.0, position.averageCost, 0.0001)
        assertEquals(13.5, position.realizedProfit, 0.0001)
        assertFalse(snapshot.positions.containsKey("US:CASH"))
    }
}

