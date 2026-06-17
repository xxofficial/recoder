package com.recoder.stockledger.data.importer

import com.recoder.stockledger.data.Market
import com.recoder.stockledger.data.TradeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LongBridgeStatementParserTest {

    @Test
    fun testParseLongBridgeStockAndOption() {
        val sampleText = """
            市场: ⾹港市场; 币种: 港元
            2026.04.01
            2026.04.08
            OS20260401252868
            买⼊
            7515  南⽅两倍看空⽇经
            20.00
            24.00
            480.00
            -495.07
            佣⾦
            0.99 0.00
            综合审计跟踪费⽤
            0.00
            平台费
            1.00
            印花税
            2.00
            交收费
            0.50

            市场: 美国市场; 币种: 美元
            2026.04.01
            2026.04.02
            OS20260402132175
            买⼊
            MU260402P340000  MU
            260402 340 Put
            1.00
            0.34
            34.00
            -36.03
            佣⾦
            1.49
            平台费
            0.50
        """.trimIndent()

        val trades = LongBridgeStatementPdfParser.parseText(sampleText)
        assertEquals(2, trades.size)

        // 1. Verify HK Stock Trade
        val hkTrade = trades[0]
        assertEquals("OS20260401252868", hkTrade.tradeRef)
        assertEquals(TradeType.BUY, hkTrade.tradeType)
        assertEquals(Market.HK, hkTrade.market)
        assertEquals("7515.HK", hkTrade.symbol)
        assertEquals("南方两倍看空日经", hkTrade.name)
        assertEquals(20.0, hkTrade.quantity, 0.001)
        assertEquals(24.0, hkTrade.price, 0.001)
        assertEquals(480.0, hkTrade.amount, 0.001)
        assertEquals("HKD", hkTrade.currencyCode)
        assertEquals(LocalDate.of(2026, 4, 1), hkTrade.tradeDate)
        assertEquals("STOCK", hkTrade.assetType)
        assertNull(hkTrade.underlyingSymbol)
        assertNull(hkTrade.expiryDate)
        assertNull(hkTrade.strikePrice)
        assertNull(hkTrade.optionType)

        // HK Fees:
        // commission = 0.00 (from 0.99 0.00) + 1.00 (platformFee) = 1.00
        // tax = 2.00 (stamp duty) + 0.50 (settlement fee) = 2.50
        assertEquals(1.0, hkTrade.commission ?: 0.0, 0.001)
        assertEquals(2.5, hkTrade.tax ?: 0.0, 0.001)

        // 2. Verify US Option Trade
        val usTrade = trades[1]
        assertEquals("OS20260402132175", usTrade.tradeRef)
        assertEquals(TradeType.BUY, usTrade.tradeType)
        assertEquals(Market.US, usTrade.market)
        assertEquals("MU 260402P340", usTrade.symbol)
        assertEquals("MU 2026-04-02 Put @ 340", usTrade.name)
        assertEquals(1.0, usTrade.quantity, 0.001)
        assertEquals(0.34, usTrade.price, 0.001)
        assertEquals(34.0, usTrade.amount, 0.001)
        assertEquals("USD", usTrade.currencyCode)
        assertEquals(LocalDate.of(2026, 4, 1), usTrade.tradeDate)
        assertEquals("OPTION", usTrade.assetType)
        assertEquals("MU", usTrade.underlyingSymbol)
        assertEquals("2026-04-02", usTrade.expiryDate)
        assertEquals(340.0, usTrade.strikePrice ?: 0.0, 0.001)
        assertEquals("PUT", usTrade.optionType)

        // US Fees:
        // commission = 1.49 (commission) + 0.50 (platformFee) = 1.99
        // tax = 0.0
        assertEquals(1.99, usTrade.commission ?: 0.0, 0.001)
        assertEquals(0.0, usTrade.tax ?: 0.0, 0.001)
    }

    @Test
    fun testParseTimeAndTimezoneAndCleanName() {
        val sampleText = """
            2026.04.01
            2026.04.08
            OS20260401252868
            买⼊
            " "蜜雪集团
            20.00
            24.00
            480.00
            -495.07
            下单时间 / 成交时间
            2026.04.01 09:30:15 HKT
        """.trimIndent()

        val trades = LongBridgeStatementPdfParser.parseText(sampleText)
        assertEquals(1, trades.size)
        val trade = trades[0]
        assertEquals("OS20260401252868", trade.tradeRef)
        assertEquals(Market.HK, trade.market)
        assertEquals("蜜雪集团", trade.name)
        assertEquals("", trade.symbol)
        assertEquals("09:30", trade.tradeTime)
    }

    @Test
    fun testParseUsOptionTimezoneConversion() {
        val sampleText = """
            2026.04.13
            2026.04.14
            OS20260413123456
            买⼊
            SLV260413P66000  SLV
            260413 66 Put
            1.00
            0.50
            50.00
            -51.50
            下单时间 / 成交时间
            2026.04.13 09:38:00 EDT
        """.trimIndent()

        val trades = LongBridgeStatementPdfParser.parseText(sampleText)
        assertEquals(1, trades.size)
        val trade = trades[0]
        assertEquals(Market.US, trade.market)
        assertEquals("SLV 260413P66", trade.symbol)
        assertEquals(LocalDate.of(2026, 4, 13), trade.tradeDate)
        assertEquals("21:38", trade.tradeTime)
    }

    @Test
    fun testParseUsOptionTimezoneConversionRollover() {
        val sampleText = """
            2026.04.13
            2026.04.14
            OS20260413654321
            买⼊
            SLV260413P66000  SLV
            260413 66 Put
            1.00
            0.50
            50.00
            -51.50
            下单时间 / 成交时间
            2026.04.13 16:30:00 EDT
        """.trimIndent()

        val trades = LongBridgeStatementPdfParser.parseText(sampleText)
        assertEquals(1, trades.size)
        val trade = trades[0]
        assertEquals(Market.US, trade.market)
        assertEquals("SLV 260413P66", trade.symbol)
        assertEquals(LocalDate.of(2026, 4, 14), trade.tradeDate)
        assertEquals("04:30", trade.tradeTime)
    }

    @Test
    fun testParseUsStockSymbolAndName() {
        val sampleText = """
            市场: 美国市场; 币种: 美元
            2026.02.03
            2026.02.04
            OS20260203112233
            买⼊
            NVDA 英伟达
            1.00
            186.92
            186.92
            -187.92
            下单时间 / 成交时间
            2026.02.03 09:30:00 EST
        """.trimIndent()

        val trades = LongBridgeStatementPdfParser.parseText(sampleText)
        assertEquals(1, trades.size)
        val trade = trades[0]
        assertEquals(Market.US, trade.market)
        assertEquals("NVDA", trade.symbol)
        assertEquals("英伟达", trade.name)
    }

    @Test
    fun testParseIpoAllotment() {
        val sampleText = """
            其他资⾦出⼊明细
            发⽣⽇期 业务类型 项⽬ ⾦额 结余 备注
            2025.09.15 新股中签款扣除 IPO 2525.HK 中签⾦额 (20 股 @HKD 4,256.00) -4,298.92 10,000.00
            
            其他持仓出入明细
            发生日期 类型 项目 备注 数量
            市场: 香港市场
            2025.09.15 中签新股入账 2525  禾赛-W IPO 2525.HK 中簽(20 股) 20.00
            责任说明
        """.trimIndent()

        val trades = LongBridgeStatementPdfParser.parseText(sampleText)
        assertEquals(1, trades.size)
        val trade = trades[0]
        assertEquals(TradeType.BUY, trade.tradeType)
        assertEquals(Market.HK, trade.market)
        assertEquals("2525.HK", trade.symbol)
        assertEquals("禾赛-W", trade.name)
        assertEquals(20.0, trade.quantity, 0.001)
        assertEquals(212.8, trade.price, 0.001)
        assertEquals(4256.0, trade.amount, 0.001)
        assertEquals("HKD", trade.currencyCode)
        assertEquals(LocalDate.of(2025, 9, 15), trade.tradeDate)
        assertEquals("STOCK", trade.assetType)
        assertEquals("IPO-2525.HK-20250915", trade.tradeRef)
    }

    @Test
    fun testParseSingleLineCashMovementsWithoutSectionHeader() {
        val sampleText = """
            市场: 美国市场; 币种: 美元
            2025.06.05 活动礼包 现金奖励 100.00
            2025.06.06 货币兑换出账 HKD 换汇至 USD @ 0.1272 -5,000.00
            H11275047 Page 3 of 4
            2025.06
            综合账户月结单
            2025.06.06 货币兑换入账 HKD 换汇至 USD @ 0.1272 636.00
        """.trimIndent()

        val trades = LongBridgeStatementPdfParser.parseText(sampleText)
        assertEquals(3, trades.size)

        val gift = trades[0]
        assertEquals(TradeType.DIVIDEND, gift.tradeType)
        assertEquals(Market.US, gift.market)
        assertEquals("USD", gift.currencyCode)
        assertEquals("CASH", gift.symbol)
        assertEquals(100.0, gift.price, 0.001)
        assertEquals(1.0, gift.quantity, 0.001)
        assertTrue(gift.tradeRef.startsWith("GIFT-20250605-活动礼包-100.0"))
        assertTrue(gift.rawLine.contains("现金奖励"))

        val withdraw = trades[1]
        assertEquals(TradeType.WITHDRAW, withdraw.tradeType)
        assertEquals(Market.HK, withdraw.market)
        assertEquals("HKD", withdraw.currencyCode)
        assertEquals("CASH", withdraw.symbol)
        assertEquals(5000.0, withdraw.price, 0.001)
        assertTrue(withdraw.tradeRef.startsWith("WTH-20250606-货币兑换出账-5000.0"))

        val deposit = trades[2]
        assertEquals(TradeType.DEPOSIT, deposit.tradeType)
        assertEquals(Market.US, deposit.market)
        assertEquals("USD", deposit.currencyCode)
        assertEquals("CASH", deposit.symbol)
        assertEquals(636.0, deposit.price, 0.001)
        assertTrue(deposit.tradeRef.startsWith("DEP-20250606-货币兑换入账-636.0"))
    }

    @Test
    fun testDoesNotParseBareFundingLinesWithoutCashSectionHeader() {
        val sampleText = """
            --- PAGE 1 ---
            20,000.00
            港元
            2025.05.22
            存入资金
            1,000.00
            2025.05.28
            存入资金
            19,000.00
            2025.05.28 存入资金 19,000.00
            责任说明
        """.trimIndent()

        val trades = LongBridgeStatementPdfParser.parseText(sampleText)
        assertEquals(0, trades.size)
    }

    @Test
    fun testParseColumnCashDividendAndTax() {
        val sampleText = """
            其他资金出入明细
            发生日期
            类型
            备注
            金额
            币种: 美元
            2025.07.04
            现金分红
            NVDA.US Cash Dividend: 0.01 USD per Share ,
            Held:0.7
            0.01
            2025.07.14
            公司行动其他费用
            TSM(US8740391003) Payment in Lieu of Dividend
            (Ordinary Div - NRA Withholding Exempt)
            Withholding Tax/Dividend Fee
            -0.07
            汇总 (美元)
            0.29
            责任说明
        """.trimIndent()

        val trades = LongBridgeStatementPdfParser.parseText(sampleText)
        assertEquals(2, trades.size)

        val dividend = trades[0]
        assertEquals(TradeType.DIVIDEND, dividend.tradeType)
        assertEquals(Market.US, dividend.market)
        assertEquals("USD", dividend.currencyCode)
        assertEquals("NVDA", dividend.symbol)
        assertEquals("NVDA", dividend.name)
        assertEquals(0.01, dividend.price, 0.001)
        assertEquals(1.0, dividend.quantity, 0.001)
        assertTrue(dividend.tradeRef.startsWith("DIV-20250704-现金分红-0.01"))
        assertTrue(dividend.rawLine.contains("NVDA.US"))

        val tax = trades[1]
        assertEquals(TradeType.TAX, tax.tradeType)
        assertEquals(Market.US, tax.market)
        assertEquals("USD", tax.currencyCode)
        assertEquals("TSM", tax.symbol)
        assertEquals("TSM", tax.name)
        assertEquals(0.07, tax.price, 0.001)
        assertEquals(1.0, tax.quantity, 0.001)
        assertTrue(tax.tradeRef.startsWith("TAX-20250714-公司行动其他费用-0.07"))
        assertTrue(tax.rawLine.contains("Withholding Tax/Dividend Fee"))
    }

    @Test
    fun testParseMixedCurrencyColumnCashMovements() {
        val sampleText = """
            其他资金出入明细
            发生日期
            类型
            备注
            金额
            币种: 港元
            2026.04.01
            融资利息
            融资利息
            -27.06
            2026.04.20
            存入资金
            18,000.00
            汇总 (港元)
            17,972.94
            币种: 美元
            2026.04.01
            现金分红
            SPXS.US Cash Dividend: 0.38267 USD per Share ,
            Held:20
            7.65
            2026.04.01
            公司行动其他费用
            SPXS.US Cash Dividend: 0.38267 USD per Share
            Withholding Tax/Dividend Fee
            -0.77
            H11275047 Page 41 of 43
            2026.04
            综合账户月结单
            2026.04.01
            融券利息
            融券利息
            -0.21
            责任说明
        """.trimIndent()

        val trades = LongBridgeStatementPdfParser.parseText(sampleText)
        assertEquals(5, trades.size)

        val hkdInterest = trades[0]
        assertEquals(TradeType.INTEREST, hkdInterest.tradeType)
        assertEquals(Market.HK, hkdInterest.market)
        assertEquals("HKD", hkdInterest.currencyCode)
        assertEquals("INTEREST", hkdInterest.symbol)
        assertEquals(27.06, hkdInterest.price, 0.001)
        assertTrue(hkdInterest.tradeRef.startsWith("INT-20260401-融资利息-27.06"))

        val hkdDeposit = trades[1]
        assertEquals(TradeType.DEPOSIT, hkdDeposit.tradeType)
        assertEquals(Market.HK, hkdDeposit.market)
        assertEquals("HKD", hkdDeposit.currencyCode)
        assertEquals("CASH", hkdDeposit.symbol)
        assertEquals(18000.0, hkdDeposit.price, 0.001)

        val usdDividend = trades[2]
        assertEquals(TradeType.DIVIDEND, usdDividend.tradeType)
        assertEquals(Market.US, usdDividend.market)
        assertEquals("USD", usdDividend.currencyCode)
        assertEquals("SPXS", usdDividend.symbol)
        assertEquals(7.65, usdDividend.price, 0.001)

        val usdTax = trades[3]
        assertEquals(TradeType.TAX, usdTax.tradeType)
        assertEquals(Market.US, usdTax.market)
        assertEquals("USD", usdTax.currencyCode)
        assertEquals("SPXS", usdTax.symbol)
        assertEquals(0.77, usdTax.price, 0.001)
        assertTrue(usdTax.rawLine.contains("Withholding Tax/Dividend Fee"))

        val usdShortInterest = trades[4]
        assertEquals(TradeType.INTEREST, usdShortInterest.tradeType)
        assertEquals(Market.US, usdShortInterest.market)
        assertEquals("USD", usdShortInterest.currencyCode)
        assertEquals("INTEREST", usdShortInterest.symbol)
        assertEquals(0.21, usdShortInterest.price, 0.001)
    }
}
