package com.recoder.stockledger.domain.portfolio

import com.recoder.stockledger.data.Market
import com.recoder.stockledger.data.TradeType
import com.recoder.stockledger.data.local.QuoteSnapshotEntity
import com.recoder.stockledger.data.local.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class PortfolioMappersTest {
    @Test
    fun `transaction mapper keeps option underlying symbol`() {
        val entity = TransactionEntity(
            tradeType = TradeType.BUY.name,
            platform = "LONG_BRIDGE",
            market = Market.US.name,
            symbol = "AAPL 260501C295",
            name = "AAPL May 2026 295 Call",
            tradeDate = "2026-04-21",
            tradeTime = "22:30",
            price = 1.23,
            quantity = 1.0,
            commission = 0.65,
            tax = 0.01,
            note = "",
            createdAt = 1L,
            assetType = "OPTION",
            underlyingSymbol = "AAPL",
        )

        val trade = entity.toPortfolioTrade()

        assertEquals(TradeType.BUY, trade.tradeType)
        assertEquals(Market.US, trade.market)
        assertEquals("AAPL 260501C295", trade.symbol)
        assertEquals("OPTION", trade.assetType)
        assertEquals("AAPL", trade.underlyingSymbol)
    }

    @Test
    fun `quote mapper resolves market and prices`() {
        val quote = QuoteSnapshotEntity(
            symbol = "AAPL",
            market = Market.US.name,
            name = "Apple Inc.",
            currentPrice = 210.0,
            previousClose = 205.0,
            lastUpdatedAt = 1L,
        ).toPortfolioQuote()

        assertEquals("AAPL", quote.symbol)
        assertEquals(Market.US, quote.market)
        assertEquals(210.0, quote.currentPrice!!, 0.0001)
        assertEquals(205.0, quote.previousClose!!, 0.0001)
    }
}
