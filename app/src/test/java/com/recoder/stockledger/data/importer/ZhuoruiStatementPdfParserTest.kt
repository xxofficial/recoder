package com.recoder.stockledger.data.importer

import com.recoder.stockledger.data.Market
import com.recoder.stockledger.data.TradeType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ZhuoruiStatementPdfParserTest {

    // ===== 格式A（新版日结单）测试数据 =====

    private val sampleFormatA = """
        成交信息(Transaction Details)
        买卖⽅向
        Trade Direction
        代码名称
        Stock Code & Name
        市场/交易所
        Market/Exchange
        币种
        Currency
        交易时间
        Trade Time
        交收⽇期
        Sett Date
        股数
        Quantity
        均价
        Price
        清算⾦额
        Clearing Balance
        买⼊
        MU 美光科技 US/NASDAQ USD 2026-02-09 19:04:52 2026-02-10 1 380.0000 -381.39
        合计Total(USD) 1 380.0000 -381.39
        经纪佣⾦ 0.00， 交收费 0.40， 证监会费 0.00， 
        交易活动费 0.00， Finra交易活动费 0.00， 平台使⽤费 0.99， 综合会计追踪资⾦费 0.00， 应计利息 0.00
        ⼩计 1.39
        沽出
        IAU iShares⻩⾦信托ETF US/NYSE USD 2026-02-09 18:48:25 2026-02-10 4 94.0200 374.68
        合计Total(USD) 4 94.0200 374.68
    """.trimIndent()

    private val sampleFormatAMultiLine = """
        成交信息(Transaction Details)
        买⼊
        MU 美光科技
        US/NASDAQ
        USD
        2026-02-09 19:04:52
        2026-02-10
        1
        380.0000
        -381.39
        合计Total(USD)
        1
        380.0000
        -381.39
    """.trimIndent()

    private val sampleFormatAMultipleSameDirection = """
        成交信息(Transaction Details)
        买⼊
        RDW Redwire Corp US/NYSE USD 2026-02-06 22:38:44 2026-02-09 3 89.0250 -268.46
        RDW Redwire Corp US/NYSE USD 2026-02-07 03:55:26 2026-02-09 27 10.0000 -271.39
        合计Total(USD) 55 9.4196 -520.86
        经纪佣⾦ 0.00， 交收费 0.40， 证监会费 0.00， 
        交易活动费 0.00， Finra交易活动费 0.00， 平台使⽤费 0.99， 综合会计追踪资⾦费 0.00， 应计利息 0.00
        ⼩计 2.78
    """.trimIndent()

    // ===== 格式B（旧版日结单）测试数据 =====

    private val sampleFormatB = """
        成交信息(Transaction Details)
        交易⽇
        Trade Date
        交收⽇
        Sett Date
        编号
        Ref.
        买/沽
        B/S
        内容
        Description
        股数
        Quantity
        成交单价
        Price
        清算⾦额
        Clear Balance
        US-NASDAQ-USD
        2026-01-16 2026-01-20 42548480 买⼊ FUTU 富途控股 1 176.0000 -177.39
        经纪佣⾦ Commission 0.00
        交收费 Settlement Fee 0.40
    """.trimIndent()

    private val sampleFormatBMultiLine = """
        成交信息(Transaction Details)
        US-NASDAQ-USD
        2026-01-16
        2026-01-20
        42548480
        买⼊
        FUTU 富途控股
        1
        176.0000
        -177.39
        经纪佣⾦ Commission
        0.00
    """.trimIndent()

    // ===== 测试用例 =====

    @Test
    fun `parseText extracts stock trades from format A`() {
        val trades = ZhuoruiStatementPdfParser.parseText(sampleFormatA)

        assertEquals(2, trades.size)

        trades[0].let { trade ->
            assertEquals(TradeType.BUY, trade.tradeType)
            assertEquals("MU", trade.symbol)
            assertEquals("美光科技", trade.name)
            assertEquals(Market.US, trade.market)
            assertEquals("USD", trade.currencyCode)
            assertEquals(1.0, trade.quantity, 0.0001)
            assertEquals(380.0, trade.price, 0.01)
            assertEquals(381.39, trade.amount, 0.01)
            assertEquals(LocalDate.of(2026, 2, 9), trade.tradeDate)
            assertEquals("20260209-MU-BUY-1-380.0000", trade.tradeRef)
        }

        trades[1].let { trade ->
            assertEquals(TradeType.SELL, trade.tradeType)
            assertEquals("IAU", trade.symbol)
            assertEquals("iShares黄金信托ETF", trade.name)
            assertEquals(Market.US, trade.market)
            assertEquals("USD", trade.currencyCode)
            assertEquals(4.0, trade.quantity, 0.0001)
            assertEquals(94.02, trade.price, 0.01)
            assertEquals(374.68, trade.amount, 0.01)
            assertEquals(LocalDate.of(2026, 2, 9), trade.tradeDate)
            assertEquals("20260209-IAU-SELL-4-94.0200", trade.tradeRef)
        }
    }

    @Test
    fun `parseText extracts stock trades from format B`() {
        val trades = ZhuoruiStatementPdfParser.parseText(sampleFormatB)

        assertEquals(1, trades.size)

        trades[0].let { trade ->
            assertEquals(TradeType.BUY, trade.tradeType)
            assertEquals("FUTU", trade.symbol)
            assertEquals("富途控股", trade.name)
            assertEquals(Market.US, trade.market)
            assertEquals("USD", trade.currencyCode)
            assertEquals(1.0, trade.quantity, 0.0001)
            assertEquals(176.0, trade.price, 0.01)
            assertEquals(177.39, trade.amount, 0.01)
            assertEquals(LocalDate.of(2026, 1, 16), trade.tradeDate)
            assertEquals("20260116-FUTU-BUY-1-176.0000-42548480", trade.tradeRef)
        }
    }

    @Test
    fun `parseText skips fee detail lines in format A`() {
        val trades = ZhuoruiStatementPdfParser.parseText(sampleFormatA)

        // 费用明细行不应产生额外的交易记录
        assertEquals(2, trades.size)
    }

    @Test
    fun `parseText handles multi-line PDFBox output for format A`() {
        val trades = ZhuoruiStatementPdfParser.parseText(sampleFormatAMultiLine)

        assertEquals(1, trades.size)
        trades[0].let { trade ->
            assertEquals(TradeType.BUY, trade.tradeType)
            assertEquals("MU", trade.symbol)
            assertEquals(1.0, trade.quantity, 0.0001)
            assertEquals(380.0, trade.price, 0.01)
            assertEquals(381.39, trade.amount, 0.01)
        }
    }

    @Test
    fun `parseText handles multi-line PDFBox output for format B`() {
        val trades = ZhuoruiStatementPdfParser.parseText(sampleFormatBMultiLine)

        assertEquals(1, trades.size)
        trades[0].let { trade ->
            assertEquals(TradeType.BUY, trade.tradeType)
            assertEquals("FUTU", trade.symbol)
            assertEquals(1.0, trade.quantity, 0.0001)
            assertEquals(176.0, trade.price, 0.01)
            assertEquals(177.39, trade.amount, 0.01)
        }
    }

    @Test
    fun `parseText handles multiple same-direction trades in format A`() {
        val trades = ZhuoruiStatementPdfParser.parseText(sampleFormatAMultipleSameDirection)

        assertEquals(2, trades.size)

        val trade3 = trades.find { it.quantity == 3.0 }
        assertEquals(TradeType.BUY, trade3?.tradeType)
        assertEquals("RDW", trade3?.symbol)
        assertEquals(89.025, trade3?.price ?: 0.0, 0.001)

        val trade27 = trades.find { it.quantity == 27.0 }
        assertEquals(TradeType.BUY, trade27?.tradeType)
        assertEquals("RDW", trade27?.symbol)
        assertEquals(10.0, trade27?.price ?: 0.0, 0.01)

        // 验证 tradeRef 因数量不同而区分
        assertEquals("20260206-RDW-BUY-3-89.0250", trade3?.tradeRef)
        assertEquals("20260207-RDW-BUY-27-10.0000", trade27?.tradeRef)
    }

    @Test
    fun `parseText returns empty list when no transaction sections`() {
        val text = """
            Portfolio Holdings
            Stock Code  Stock Name
            AAPL  APPLE INC
        """.trimIndent()

        val trades = ZhuoruiStatementPdfParser.parseText(text)
        assertEquals(0, trades.size)
    }

    @Test
    fun `parseText handles full daily statement with fund and unsettled sections`() {
        val fullText = """
            ⽇结单(Daily Statement)
            账⼾信息(Account Information)
            季福乐
            762092870766
            
            成交信息(Transaction Details)
            买卖⽅向
            Trade Direction
            代码名称
            Stock Code & Name
            市场/交易所
            Market/Exchange
            币种
            Currency
            交易时间
            Trade Time
            交收⽇期
            Sett Date
            股数
            Quantity
            均价
            Price
            清算⾦额
            Clearing Balance
            买⼊
            GOOG ⾕歌 US/NASDAQ USD 2026-02-10 23:45:47 2026-02-11 1 318.4990 -319.89
            合计Total(USD) 1 318.4990 -319.89
            经纪佣⾦ 0.00， 交收费 0.40， 证监会费 0.00， 
            交易活动费 0.00， Finra交易活动费 0.00， 平台使⽤费 0.99， 综合会计追踪资⾦费 0.00， 应计利息 0.00
            ⼩计 1.39
            
            基⾦成交信息(Fund Transaction Details)
            交易⽇
            Trade Date
            交收⽇
            Sett Date
            编号
            Ref.
            申/赎
            S/R
            内容
            Description
            股数
            Quantity
            委托单价
            Price
            发⽣⾦额
            Business Balance
            HK-Mutual Fund-USD
            2026-02-10 2026-02-10 61517824 申购 10018002 ⾼腾微⾦美元货币基⾦ 18.3068 11.7355 -214.84
            佣⾦ Commission 0.00
            
            未交收证券交易(Unsettled Securities Transaction)
            交易⽇
            Trade Date
            交收⽇
            Sett Date
            编号
            Ref.
            买/沽
            B/S
            内容
            Description
            股数
            Quantity
            成交单价
            Price
            清算⾦额
            Clearing Balance
            US-NASDAQ-USD
            2026-02-10 2026-02-11 66237952 买⼊ GOOG ⾕歌 Alphabet Inc. Class C 1 318.4990 -319.89
            
            重要提⽰（Important Notes）
            (1)...
        """.trimIndent()

        val trades = ZhuoruiStatementPdfParser.parseText(fullText)

        // 只应解析股票成交信息中的交易，跳过基金和未交收证券交易区域
        assertEquals(1, trades.size)
        trades[0].let { trade ->
            assertEquals(TradeType.BUY, trade.tradeType)
            assertEquals("GOOG", trade.symbol)
            assertEquals("谷歌", trade.name)
            assertEquals(1.0, trade.quantity, 0.0001)
            assertEquals(318.499, trade.price, 0.001)
            assertEquals(319.89, trade.amount, 0.01)
        }
    }
}
