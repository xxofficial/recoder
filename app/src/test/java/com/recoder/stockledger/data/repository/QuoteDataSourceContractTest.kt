package com.recoder.stockledger.data.repository

import com.recoder.stockledger.data.Market
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuoteDataSourceContractTest {
    @Test
    fun `fake quote source satisfies quote data source contract`() = runBlocking {
        val source: QuoteDataSource = FakeQuoteDataSource()

        val quotes = source.refreshQuotes(
            listOf(
                QuoteRequest(symbol = "AAPL", name = "Apple", market = Market.US),
                QuoteRequest(symbol = "0700.HK", name = "Tencent", market = Market.HK),
            ),
        )
        val suggestions = source.searchSecurities("AAPL", Market.US)

        assertEquals(2, quotes.size)
        assertTrue(quotes.all { it.currentPrice != null })
        assertTrue(suggestions.any { it.symbol == "AAPL" })
    }
}
