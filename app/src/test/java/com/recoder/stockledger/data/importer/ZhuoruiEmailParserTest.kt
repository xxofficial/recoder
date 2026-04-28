package com.recoder.stockledger.data.importer

import com.recoder.stockledger.data.ImportSourceChannel
import com.recoder.stockledger.data.Market
import com.recoder.stockledger.data.TradeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ZhuoruiEmailParserTest {
    @Test
    fun parsesBuyEmailSharedText() {
        val parsed = ZhuoruiEmailParser.parse(
            """
            [图片]
            尊敬的客户：
            您好！
            您762092870253美股账户于2026-04-24 23:18:37成功买入证券，详情如下：
            股票名称
            股票代码
            币种
            成交价格
            成交数量
            成交金额
            累计成交数量
            累计成交金额
            GraniteShares每日2倍做多MRVL主动型ETF
            MVLL
            USD
            66.910
            15
            1003.65
            15
            1003.65
            *此为系统邮件，请勿答复！
            """.trimIndent(),
        )

        assertNotNull(parsed)
        parsed!!
        assertEquals(ImportSourceChannel.ZHUORUI_EMAIL, parsed.sourceChannel)
        assertEquals(TradeType.BUY, parsed.tradeType)
        assertEquals(Market.US, parsed.market)
        assertEquals("MVLL", parsed.symbol)
        assertEquals("GraniteShares每日2倍做多MRVL主动型ETF", parsed.name)
        assertEquals(66.910, parsed.price, 0.0001)
        assertEquals(15, parsed.quantity)
        assertEquals("ZR-762092870253-20260424231837-BUY-MVLL-15-66_91", parsed.externalReference)
    }

    @Test
    fun parsesSellEmailSharedText() {
        val parsed = ZhuoruiEmailParser.parse(
            """
            [图片]
            尊敬的客户：
            您好！
            您762092870253美股账户于2026-04-24 23:14:00成功卖出证券，详情如下：
            股票名称
            股票代码
            币种
            成交价格
            成交数量
            成交金额
            累计成交数量
            累计成交金额
            Tradr每日2倍做多LITE主动型ETF
            LITX
            USD
            42.940
            25
            1073.50
            25
            1073.50
            卓锐证券官网：
            www.zr.hk
            """.trimIndent(),
        )

        assertNotNull(parsed)
        parsed!!
        assertEquals(TradeType.SELL, parsed.tradeType)
        assertEquals("LITX", parsed.symbol)
        assertEquals(42.940, parsed.price, 0.0001)
        assertEquals(25, parsed.quantity)
    }

    @Test
    fun parsesForwardedQqMailboxPdfText() {
        val parsed = ZhuoruiEmailParser.parse(
            """
            Fwd: 证券买入通知
            Fule Ji<jifule523@gmail.com>
            发件人Fule Ji
            收件人736917696@qq.com<736917696@qq.com>
            ---------- Forwarded message ---------
            发件人： 卓锐证券 <settlement@m.zrsechk.com>
            Date: 2026年4月24日周五 23:18
            Subject: 证券买入通知
            To: <jifule523@gmail.com>
            尊敬的客⼾：
            您好！
            您762092870253美股账⼾于2026-04-24 23:18:37成功买⼊证券，详情如下：
            股票名称 股票代码币种成交价格成交数量成交⾦额累计成交数量累计成交⾦额
            GraniteShares每⽇2倍做多MRVL主动型ETF MVLL U SD 66.910 15 1003.65 15 1003.65
            卓锐证券为⾹港证监会持牌法团(中央编号BRE865)，受⾹港证监会监管。
            """.trimIndent(),
        )

        assertNotNull(parsed)
        parsed!!
        assertEquals(TradeType.BUY, parsed.tradeType)
        assertEquals("MVLL", parsed.symbol)
        assertEquals("USD", parsed.currencyCode)
        assertEquals(66.910, parsed.price, 0.0001)
        assertEquals(15, parsed.quantity)
    }

    @Test
    fun ignoresUnknownText() {
        assertNull(ZhuoruiEmailParser.parse("这不是卓锐成交邮件"))
    }
}
