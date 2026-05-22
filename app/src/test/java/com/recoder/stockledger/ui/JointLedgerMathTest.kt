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

    private fun computePartnerContributions(
        ledger: MockLedger,
        transactions: List<MockTransaction>,
        portfolio: MockPortfolio,
        exchangeRates: MockExchangeRates
    ): List<PartnerContribution> {
        val partnerNames = ledger.partners.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (partnerNames.isEmpty()) return emptyList()
        val contributions = partnerNames.associateWith { 0.0 }.toMutableMap()
        transactions.forEach { tx ->
            if (tx.tradeType == "DEPOSIT" || tx.tradeType == "WITHDRAW") {
                val amountCny = tx.price * tx.quantity * exchangeRates.rateToCny(tx.market)
                val investor = tx.investorName?.trim()?.takeIf { it in contributions } ?: partnerNames.first()
                val current = contributions[investor] ?: 0.0
                contributions[investor] = if (tx.tradeType == "DEPOSIT") current + amountCny else current - amountCny
            }
        }
        val totalNet = contributions.values.sum()
        val totalAssets = portfolio.totalAssetsCny
        val totalProfit = portfolio.totalProfitCny
        return partnerNames.map { name ->
            val net = contributions[name] ?: 0.0
            val ratio = if (totalNet > 0.0) net / totalNet else 1.0 / partnerNames.size
            PartnerContribution(
                name = name,
                netContributionCny = net,
                ratio = ratio,
                assetsShareCny = totalAssets * ratio,
                pnlShareCny = totalProfit * ratio
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
        assertEquals(500.0, results[0].pnlShareCny, 0.001)
        // Alice
        assertEquals("Alice", results[1].name)
        assertEquals(0.0, results[1].netContributionCny, 0.001)
        assertEquals(0.5, results[1].ratio, 0.001)
        assertEquals(2500.0, results[1].assetsShareCny, 0.001)
        assertEquals(500.0, results[1].pnlShareCny, 0.001)
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
}
