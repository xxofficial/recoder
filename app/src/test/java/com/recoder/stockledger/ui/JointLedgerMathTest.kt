package com.recoder.stockledger.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class JointLedgerMathTest {

    data class MockLedger(
        val partners: String
    )

    data class MockTransaction(
        val tradeType: String,
        val price: Double,
        val quantity: Double,
        val market: String,
        val investorName: String?
    )

    data class MockPortfolio(
        val totalAssetsCny: Double,
        val totalProfitCny: Double
    )

    data class MockExchangeRates(
        val usdToCny: Double = 7.0,
        val hkdToCny: Double = 1.0,
        val cnyToCny: Double = 1.0
    ) {
        fun rateToCny(market: String): Double {
            return when (market) {
                "US" -> usdToCny
                "HK" -> hkdToCny
                else -> cnyToCny
            }
        }
    }

    data class MockQuote(
        val symbol: String,
        val market: String,
        val currentPrice: Double
    )

    private fun computePartnerContributions(
        ledger: MockLedger,
        transactions: List<MockTransaction>,
        portfolio: MockPortfolio,
        exchangeRates: MockExchangeRates,
        quotes: List<MockQuote> = emptyList()
    ): List<PartnerContribution> {
        val partnerNames = ledger.partners.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (partnerNames.isEmpty()) return emptyList()

        val partnerUnits = partnerNames.associateWith { 0.0 }.toMutableMap()
        val partnerNetContributions = partnerNames.associateWith { 0.0 }.toMutableMap()
        var totalUnits = 0.0

        var cashBalanceCny = 0.0
        val stockQuantities = mutableMapOf<String, Double>()
        val stockPrices = mutableMapOf<String, Double>()

        quotes.forEach { quote ->
            val key = "${quote.market}:${quote.symbol}"
            stockPrices[key] = quote.currentPrice * exchangeRates.rateToCny(quote.market)
        }

        transactions.forEach { tx ->
            val market = tx.market

            when (tx.tradeType) {
                "DEPOSIT" -> {
                    val amountCny = tx.price * tx.quantity * exchangeRates.rateToCny(market)
                    val investor = tx.investorName?.trim()?.takeIf { it in partnerUnits } ?: partnerNames.first()

                    val holdingsValueCny = stockQuantities.entries.sumOf { (k, qty) ->
                        val price = stockPrices[k] ?: 0.0
                        qty * price
                    }
                    val totalAssetValueBefore = cashBalanceCny + holdingsValueCny

                    val unitPrice = if (totalUnits > 0.0 && totalAssetValueBefore > 0.0) {
                        totalAssetValueBefore / totalUnits
                    } else {
                        1.0
                    }

                    val unitsIssued = amountCny / unitPrice
                    partnerUnits[investor] = (partnerUnits[investor] ?: 0.0) + unitsIssued
                    totalUnits += unitsIssued
                    cashBalanceCny += amountCny
                    partnerNetContributions[investor] = (partnerNetContributions[investor] ?: 0.0) + amountCny
                }
                "WITHDRAW" -> {
                    val amountCny = tx.price * tx.quantity * exchangeRates.rateToCny(market)
                    val investor = tx.investorName?.trim()?.takeIf { it in partnerUnits } ?: partnerNames.first()

                    val holdingsValueCny = stockQuantities.entries.sumOf { (k, qty) ->
                        val price = stockPrices[k] ?: 0.0
                        qty * price
                    }
                    val totalAssetValueBefore = cashBalanceCny + holdingsValueCny

                    val unitPrice = if (totalUnits > 0.0 && totalAssetValueBefore > 0.0) {
                        totalAssetValueBefore / totalUnits
                    } else {
                        1.0
                    }

                    val unitsRedeemed = amountCny / unitPrice
                    partnerUnits[investor] = (partnerUnits[investor] ?: 0.0) - unitsRedeemed
                    totalUnits -= unitsRedeemed
                    cashBalanceCny -= amountCny
                    partnerNetContributions[investor] = (partnerNetContributions[investor] ?: 0.0) - amountCny
                }
                "BUY" -> {
                    val symbol = tx.investorName.orEmpty()
                    val key = "${market}:${symbol}"
                    val txPriceCny = tx.price * exchangeRates.rateToCny(market)
                    if (stockPrices[key] == null || stockPrices[key] == 0.0) {
                        stockPrices[key] = txPriceCny
                    }
                    stockQuantities[key] = (stockQuantities[key] ?: 0.0) + tx.quantity
                    cashBalanceCny -= tx.price * tx.quantity * exchangeRates.rateToCny(market)
                }
                "SELL" -> {
                    val symbol = tx.investorName.orEmpty()
                    val key = "${market}:${symbol}"
                    val txPriceCny = tx.price * exchangeRates.rateToCny(market)
                    if (stockPrices[key] == null || stockPrices[key] == 0.0) {
                        stockPrices[key] = txPriceCny
                    }
                    stockQuantities[key] = (stockQuantities[key] ?: 0.0) - tx.quantity
                    cashBalanceCny += tx.price * tx.quantity * exchangeRates.rateToCny(market)
                }
            }
        }

        val totalAssets = portfolio.totalAssetsCny
        return partnerNames.map { name ->
            val net = partnerNetContributions[name] ?: 0.0
            val ratio = if (totalUnits > 0.0) {
                val u = partnerUnits[name] ?: 0.0
                maxOf(0.0, u / totalUnits)
            } else {
                1.0 / partnerNames.size
            }
            val assetsShare = totalAssets * ratio
            PartnerContribution(
                name = name,
                netContributionCny = net,
                ratio = ratio,
                assetsShareCny = assetsShare,
                pnlShareCny = assetsShare - net
            )
        }
    }


    @Test
    fun testEvenSplitOnZeroNetContribution() {
        val ledger = MockLedger("我,Alice")
        val transactions = emptyList<MockTransaction>()
        val portfolio = MockPortfolio(totalAssetsCny = 5000.0, totalProfitCny = 1000.0)
        val exchangeRates = MockExchangeRates()

        val results = computePartnerContributions(ledger, transactions, portfolio, exchangeRates)

        assertEquals(2, results.size)
        // 我
        assertEquals("我", results[0].name)
        assertEquals(0.0, results[0].netContributionCny, 0.001)
        assertEquals(0.5, results[0].ratio, 0.001)
        assertEquals(2500.0, results[0].assetsShareCny, 0.001)
        assertEquals(2500.0, results[0].pnlShareCny, 0.001)
        // Alice
        assertEquals("Alice", results[1].name)
        assertEquals(0.0, results[1].netContributionCny, 0.001)
        assertEquals(0.5, results[1].ratio, 0.001)
        assertEquals(2500.0, results[1].assetsShareCny, 0.001)
        assertEquals(2500.0, results[1].pnlShareCny, 0.001)
    }

    @Test
    fun testDynamicContributionProportions() {
        val ledger = MockLedger("我,Alice,Bob")
        val transactions = listOf(
            MockTransaction("DEPOSIT", 10000.0, 1.0, "CNY", "我"),
            MockTransaction("DEPOSIT", 10000.0, 1.0, "CNY", "Alice"),
            //Bob hasn't deposited yet
        )
        val portfolio = MockPortfolio(totalAssetsCny = 30000.0, totalProfitCny = 10000.0)
        val exchangeRates = MockExchangeRates()

        val results = computePartnerContributions(ledger, transactions, portfolio, exchangeRates)

        assertEquals(3, results.size)
        // 我: 10k net contribution, ratio 50%
        assertEquals("我", results[0].name)
        assertEquals(10000.0, results[0].netContributionCny, 0.001)
        assertEquals(0.5, results[0].ratio, 0.001)
        assertEquals(15000.0, results[0].assetsShareCny, 0.001)
        assertEquals(5000.0, results[0].pnlShareCny, 0.001)

        // Alice: 10k net contribution, ratio 50%
        assertEquals("Alice", results[1].name)
        assertEquals(10000.0, results[1].netContributionCny, 0.001)
        assertEquals(0.5, results[1].ratio, 0.001)
        assertEquals(15000.0, results[1].assetsShareCny, 0.001)
        assertEquals(5000.0, results[1].pnlShareCny, 0.001)

        // Bob: 0 net contribution, ratio 0%
        assertEquals("Bob", results[2].name)
        assertEquals(0.0, results[2].netContributionCny, 0.001)
        assertEquals(0.0, results[2].ratio, 0.001)
        assertEquals(0.0, results[2].assetsShareCny, 0.001)
        assertEquals(0.0, results[2].pnlShareCny, 0.001)
    }

    @Test
    fun testCurrencyConversionAndDynamicWithdrawal() {
        val ledger = MockLedger("我,Alice")
        val transactions = listOf(
            MockTransaction("DEPOSIT", 3000.0, 1.0, "US", "我"), // 3000 USD * 7.0 = 21000 CNY
            MockTransaction("DEPOSIT", 10000.0, 1.0, "CNY", "Alice"), // 10000 CNY
            MockTransaction("WITHDRAW", 1000.0, 1.0, "US", "我"), // Withdraw 1000 USD * 7.0 = 7000 CNY -> Net 14000 CNY
        )
        // Total net = 14000 (我) + 10000 (Alice) = 24000 CNY
        // 我 ratio = 14000 / 24000 = 58.333%
        // Alice ratio = 10000 / 24000 = 41.667%
        val portfolio = MockPortfolio(totalAssetsCny = 36000.0, totalProfitCny = 12000.0)
        val exchangeRates = MockExchangeRates()

        val results = computePartnerContributions(ledger, transactions, portfolio, exchangeRates)

        assertEquals(2, results.size)
        // 我
        assertEquals("我", results[0].name)
        assertEquals(14000.0, results[0].netContributionCny, 0.001)
        assertEquals(14000.0 / 24000.0, results[0].ratio, 0.001)
        assertEquals(36000.0 * (14000.0 / 24000.0), results[0].assetsShareCny, 0.001)
        assertEquals(12000.0 * (14000.0 / 24000.0), results[0].pnlShareCny, 0.001)
        // Alice
        assertEquals("Alice", results[1].name)
        assertEquals(10000.0, results[1].netContributionCny, 0.001)
        assertEquals(10000.0 / 24000.0, results[1].ratio, 0.001)
        assertEquals(36000.0 * (10000.0 / 24000.0), results[1].assetsShareCny, 0.001)
        assertEquals(12000.0 * (10000.0 / 24000.0), results[1].pnlShareCny, 0.001)
    }

    @Test
    fun testJointLedgerCalculationWithLossAndSubsequentDeposit() {
        val ledger = MockLedger("A,B")
        val transactions = listOf(
            MockTransaction("DEPOSIT", 1000.0, 1.0, "CNY", "A"),
            MockTransaction("DEPOSIT", 2000.0, 1.0, "CNY", "B"),
            // Then they lost 50% on their stock X, total assets before A's deposit = 1500 (A: 500, B: 1000).
            MockTransaction("BUY", 1.0, 3000.0, "CNY", "X"), // Buy X for 3000 (all their money)
            // A deposits 500
            MockTransaction("DEPOSIT", 500.0, 1.0, "CNY", "A")
        )
        // Quote for X shows it dropped to 0.5 (representing 50% loss)
        val quotes = listOf(
            MockQuote("X", "CNY", 0.5)
        )
        // Total assets after A's second deposit is 2000 (1500 + 500).
        val portfolio = MockPortfolio(totalAssetsCny = 2000.0, totalProfitCny = -1500.0)
        val exchangeRates = MockExchangeRates()

        val results = computePartnerContributions(ledger, transactions, portfolio, exchangeRates, quotes)

        assertEquals(2, results.size)
        // A
        assertEquals("A", results[0].name)
        assertEquals(1500.0, results[0].netContributionCny, 0.001)
        assertEquals(0.5, results[0].ratio, 0.001)
        assertEquals(1000.0, results[0].assetsShareCny, 0.001)
        assertEquals(-500.0, results[0].pnlShareCny, 0.001)
        // B
        assertEquals("B", results[1].name)
        assertEquals(2000.0, results[1].netContributionCny, 0.001)
        assertEquals(0.5, results[1].ratio, 0.001)
        assertEquals(1000.0, results[1].assetsShareCny, 0.001)
        assertEquals(-1000.0, results[1].pnlShareCny, 0.001)
    }
}
