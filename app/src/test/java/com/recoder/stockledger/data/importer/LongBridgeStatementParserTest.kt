package com.recoder.stockledger.data.importer

import com.recoder.stockledger.data.Market
import com.recoder.stockledger.data.TradeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
}
