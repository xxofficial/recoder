package com.recoder.stockledger.data.importer

import com.recoder.stockledger.data.Market
import com.recoder.stockledger.data.TradeType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ZhuoruiStatementPdfParserTest {

    // Sample text mimicking actual PDF structure from PyMuPDF extraction
    private val sampleStockSection = """
        成交信息(Transaction Details)
        买卖⽅向
        Trade Direction代码名称
        Stock Code & Name市场/交易所
        Market/Exchange币种
        Currency交易⽇期
        Trade Date交收⽇期
        Sett Date股数
        Quantity均价
        Price清算⾦额
        Clearing Balance
        买⼊
        MUU Direxion每⽇2倍做多MU ETF US/NASDAQ USD 2026-02-02 2026-02-03 1 228.0000 -229.39
        合计Total(USD) 1 228.0000 -229.39
        沽出
        PXLW 美国像素 US/NASDAQ USD 2026-02-02 2026-02-03 36 6.3500 227.19
        合计Total(USD) 36 6.3500 227.19
        沽出
        MU 美光科技 US/NASDAQ USD 2026-02-03 2026-02-04 4 445.5825 1,780.91
        MU 美光科技 US/NASDAQ USD 2026-02-03 2026-02-04 2 408.5000 815.60
        合计Total(USD) 6 433.2217 2,596.51
        买⼊
        GOOG ⾕歌 US/NASDAQ USD 2026-02-10 2026-02-11 1 318.4990 -319.89
        合计Total(USD) 1 318.4990 -319.89
    """.trimIndent()

    private val sampleFundSection = """
        基⾦成交信息(Fund Transaction Details)
        申/赎
        S/R代码名称
        Fund Code & Name市场/交易所
        Market/Exchange币种
        Currency交易⽇期
        Trade Date交收⽇期
        Sett Date股数
        Quantity均价
        Price清算⾦额
        Clearing Balance
        申购
        10008001 ⾼腾微财货币基⾦ HK/Mutual Fund HKD 2026-02-02 2026-02-02 8.6759 11.5261 -100.00
        合计Total(HKD) 8.6759 11.5261 -100.00
        赎回
        10018002 ⾼腾微⾦美元货币基⾦ HK/Mutual Fund USD 2026-02-04 2026-02-04 0.0209 11.7291 0.24
        合计Total(USD) 0.0209 11.7291 0.24
    """.trimIndent()

    @Test
    fun `parseText extracts buy trade from stock section`() {
        val trades = ZhuoruiStatementPdfParser.parseText(sampleStockSection)

        assertEquals(5, trades.size)

        trades[0].let { trade ->
            assertEquals(TradeType.BUY, trade.tradeType)
            assertEquals("MUU", trade.symbol)
            assertEquals("Direxion每⽇2倍做多MU ETF", trade.name)
            assertEquals(Market.US, trade.market)
            assertEquals("USD", trade.currencyCode)
            assertEquals(1, trade.quantity)
            assertEquals(228.0, trade.price, 0.01)
            assertEquals(229.39, trade.amount, 0.01)
            assertEquals(LocalDate.of(2026, 2, 2), trade.tradeDate)
        }
    }

    @Test
    fun `parseText extracts sell trade`() {
        val trades = ZhuoruiStatementPdfParser.parseText(sampleStockSection)

        trades[1].let { trade ->
            assertEquals(TradeType.SELL, trade.tradeType)
            assertEquals("PXLW", trade.symbol)
            assertEquals("美国像素", trade.name)
            assertEquals(36, trade.quantity)
            assertEquals(6.35, trade.price, 0.01)
            assertEquals(227.19, trade.amount, 0.01)
        }
    }

    @Test
    fun `parseText handles multi-line sell trade group`() {
        val trades = ZhuoruiStatementPdfParser.parseText(sampleStockSection)

        // Second sell group (MU) has 2 detail lines, should produce 2 trades
        val muTrades = trades.filter { it.symbol == "MU" }
        assertEquals(2, muTrades.size)
        muTrades[0].let { trade ->
            assertEquals(TradeType.SELL, trade.tradeType)
            assertEquals(4, trade.quantity)
            assertEquals(445.5825, trade.price, 0.01)
        }
        muTrades[1].let { trade ->
            assertEquals(2, trade.quantity)
            assertEquals(408.5, trade.price, 0.01)
        }
    }

    @Test
    fun `parseText extracts CJK stock name`() {
        val trades = ZhuoruiStatementPdfParser.parseText(sampleStockSection)

        val googs = trades.filter { it.symbol == "GOOG" }
        assertEquals(1, googs.size)
        googs[0].let { trade ->
            assertEquals("⾕歌", trade.name)
            assertEquals(Market.US, trade.market)
        }
    }

    @Test
    fun `parseText extracts fund trades`() {
        val trades = ZhuoruiStatementPdfParser.parseText(sampleFundSection)

        assertEquals(2, trades.size)
        trades[0].let { trade ->
            assertEquals(TradeType.BUY, trade.tradeType) // 申购 = BUY
            assertEquals("10008001.HK", trade.symbol)
            assertEquals("⾼腾微财货币基⾦", trade.name)
            assertEquals(Market.HONG_KONG, trade.market)
            assertEquals("HKD", trade.currencyCode)
            assertEquals(LocalDate.of(2026, 2, 2), trade.tradeDate)
        }
        trades[1].let { trade ->
            assertEquals(TradeType.SELL, trade.tradeType) // 赎回 = SELL
            assertEquals("10018002.HK", trade.symbol)
        }
    }

    @Test
    fun `parseText returns empty list when no sections found`() {
        val text = """
            Portfolio Holdings
            Stock Code  Stock Name
            AAPL  APPLE INC
        """.trimIndent()

        val trades = ZhuoruiStatementPdfParser.parseText(text)
        assertEquals(0, trades.size)
    }

    @Test
    fun `parseText handles full statement with both sections`() {
        val fullText = sampleStockSection + "\n\n" + sampleFundSection
        val trades = ZhuoruiStatementPdfParser.parseText(fullText)

        // 5 stock trades + 2 fund trades = 7
        assertEquals(7, trades.size)
    }

    @Test
    fun `parseText handles multi-line trade from PDFBox`() {
        // PDFBox outputs each field on a separate line within a block
        val text = """
            成交信息(Transaction Details)
            买⼊
            MUU Direxion每⽇2倍做多MU ETF
            US/NASDAQ
            USD
            2026-02-02
            2026-02-03
            1
            228.0000
            -229.39
            合计Total(USD) 1 228.0000 -229.39
        """.trimIndent()

        val trades = ZhuoruiStatementPdfParser.parseText(text)
        assertEquals(1, trades.size)
        trades[0].let { trade ->
            assertEquals(1, trade.quantity)
            assertEquals(228.0, trade.price, 0.01)
            assertEquals(229.39, trade.amount, 0.01)
            assertEquals("MUU", trade.symbol)
        }
    }

    @Test
    fun `resolveSymbol normalizes Hong Kong stock codes`() {
        val text = """
            成交信息(Transaction Details)
            买⼊
            09961 TRIP.COM-S HK/SEHK HKD 2026-02-17 2026-02-18 20 537.0000 10,740.00
            合计Total(HKD) 20 537.0000 10,740.00
        """.trimIndent()

        val trades = ZhuoruiStatementPdfParser.parseText(text)
        assertEquals(1, trades.size)
        assertEquals("09961.HK", trades[0].symbol)
        assertEquals(Market.HONG_KONG, trades[0].market)
    }
}
