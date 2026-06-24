package com.recoder.stockledger.domain.portfolio

import com.recoder.stockledger.data.Market
import com.recoder.stockledger.data.TradeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PortfolioSecurityRulesTest {
    @Test
    fun `option rules identify multiplier and underlying attribution`() {
        assertTrue(PortfolioSecurityRules.isOptionAsset("OPTION", "AAPL 260501C295"))
        assertTrue(PortfolioSecurityRules.isOptionAsset(null, "AAPL 260501C295"))
        assertFalse(PortfolioSecurityRules.isOptionAsset("STOCK", "AAPL"))
        assertEquals(100.0, PortfolioSecurityRules.optionMultiplier("OPTION", "AAPL 260501C295"), 0.0001)
        assertEquals(1.0, PortfolioSecurityRules.optionMultiplier("STOCK", "AAPL"), 0.0001)
        assertEquals("AAPL", PortfolioSecurityRules.attributionSymbol("AAPL 260501C295", "OPTION", "AAPL"))
        assertEquals("AAPL", PortfolioSecurityRules.attributionSymbol("AAPL 260501C295", "OPTION", null))
        assertEquals("US:AAPL", PortfolioSecurityRules.attributionKey("AAPL 260501C295", Market.US, "OPTION", "AAPL"))
    }

    @Test
    fun `split event key normalizes duplicate cross platform events`() {
        val first = PortfolioSecurityRules.splitEventKey(Market.US, "snxx", "2026-06-03", 8.0)
        val second = PortfolioSecurityRules.splitEventKey(Market.US, "SNXX", "2026-06-03", 8.0000000001)

        assertEquals(first, second)
    }

    @Test
    fun `effective trade date shifts early US trades except split`() {
        assertEquals(
            LocalDate.of(2026, 4, 20),
            PortfolioSecurityRules.effectiveTradeDate("2026-04-21", "05:59", Market.US, TradeType.BUY),
        )
        assertEquals(
            LocalDate.of(2026, 4, 21),
            PortfolioSecurityRules.effectiveTradeDate("2026-04-21", "00:00", Market.US, TradeType.SPLIT),
        )
    }
}
