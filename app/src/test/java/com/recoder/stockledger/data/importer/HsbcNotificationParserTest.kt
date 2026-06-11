package com.recoder.stockledger.data.importer

import com.recoder.stockledger.data.ImportSourceChannel
import com.recoder.stockledger.data.Market
import com.recoder.stockledger.data.TradeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HsbcNotificationParserTest {
    @Test
    fun parsesExecutedSms() {
        val parsed = HsbcNotificationParser.parse(
            "【滙豐】滙豐： 買入2股AMKR， 成交價USD77.50，共買入2股，未買入0股。P899228",
        )

        assertNotNull(parsed)
        parsed!!
        assertEquals(ImportSourceChannel.HSBC_SMS, parsed.sourceChannel)
        assertEquals(HsbcNotificationStatus.EXECUTED, parsed.status)
        assertEquals(TradeType.BUY, parsed.tradeType)
        assertEquals(Market.US, parsed.market)
        assertEquals("AMKR", parsed.symbol)
        assertEquals(77.50, parsed.price ?: 0.0, 0.0001)
        assertEquals(2.0, parsed.quantity, 0.0001)
        assertEquals("P899228", parsed.externalReference)
    }

    @Test
    fun parsesCancelledSms() {
        val parsed = HsbcNotificationParser.parse(
            "【滙豐】滙豐：未能買入3股AAPU， 指示取消。P120056",
        )

        assertNotNull(parsed)
        parsed!!
        assertEquals(HsbcNotificationStatus.CANCELLED, parsed.status)
        assertEquals(TradeType.BUY, parsed.tradeType)
        assertEquals("AAPU", parsed.symbol)
        assertEquals("P120056", parsed.externalReference)
    }

    @Test
    fun parsesSimplifiedExecutedSms() {
        val parsed = HsbcNotificationParser.parse(
            "【汇丰】汇丰： 买入2股AMKR， 成交价USD77.50，共买入2股，未买入0股。P899228",
        )

        assertNotNull(parsed)
        parsed!!
        assertEquals(ImportSourceChannel.HSBC_SMS, parsed.sourceChannel)
        assertEquals(HsbcNotificationStatus.EXECUTED, parsed.status)
        assertEquals(TradeType.BUY, parsed.tradeType)
        assertEquals(Market.US, parsed.market)
        assertEquals("AMKR", parsed.symbol)
        assertEquals(77.50, parsed.price ?: 0.0, 0.0001)
        assertEquals(2.0, parsed.quantity, 0.0001)
        assertEquals("P899228", parsed.externalReference)
    }

    @Test
    fun parsesSimplifiedCancelledSms() {
        val parsed = HsbcNotificationParser.parse(
            "【汇丰】汇丰：未能卖出3股AAPU， 指示取消。P120056",
        )

        assertNotNull(parsed)
        parsed!!
        assertEquals(HsbcNotificationStatus.CANCELLED, parsed.status)
        assertEquals(TradeType.SELL, parsed.tradeType)
        assertEquals("AAPU", parsed.symbol)
        assertEquals("P120056", parsed.externalReference)
    }

    @Test
    fun parsesExecutedEmailBody() {
        val parsed = HsbcNotificationParser.parse(
            """
            親愛的客戶

            • 交易編號: P899228
            • 交易狀況: 全部執行
            • 指示類別: 買入
            • 股票名稱/ 股票編號: AMKOR TECHNOLOGY INC (AMKR)
            • 已成交數量(股/單位): 2
            • 成交價: USD77.50
            • 共成交數量(股/單位): 2
            • 餘下數量(股/單位): 0
            """.trimIndent(),
        )

        assertNotNull(parsed)
        parsed!!
        assertEquals(ImportSourceChannel.HSBC_EMAIL, parsed.sourceChannel)
        assertEquals(HsbcNotificationStatus.EXECUTED, parsed.status)
        assertEquals("AMKOR TECHNOLOGY INC", parsed.name)
        assertEquals("AMKR", parsed.symbol)
        assertEquals(2.0, parsed.quantity, 0.0001)
    }

    @Test
    fun ignoresUnknownText() {
        assertNull(HsbcNotificationParser.parse("这不是汇丰交易通知"))
    }
}
