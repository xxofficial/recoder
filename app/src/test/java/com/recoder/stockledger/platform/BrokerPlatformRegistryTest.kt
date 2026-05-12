package com.recoder.stockledger.platform

import com.recoder.stockledger.data.BrokerPlatform
import com.recoder.stockledger.data.ImportSourceChannel
import com.recoder.stockledger.data.Market
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrokerPlatformRegistryTest {
    @Test
    fun `registry exposes zhuorui extension capabilities`() {
        val adapter = BrokerPlatformRegistry().adapterFor(BrokerPlatform.ZHUORUI)

        requireNotNull(adapter)
        assertTrue(Market.US in adapter.supportedMarkets)
        assertTrue(ImportSourceChannel.ZHUORUI_EMAIL in adapter.supportedImportChannels)
        assertTrue(ImportSourceChannel.PDF_STATEMENT in adapter.supportedImportChannels)
    }

    @Test
    fun `registry exposes chief and schwab platform capabilities`() {
        val chief = BrokerPlatformRegistry().adapterFor(BrokerPlatform.CHIEF)
        val schwab = BrokerPlatformRegistry().adapterFor(BrokerPlatform.SCHWAB)

        requireNotNull(chief)
        assertTrue(Market.HK in chief.supportedMarkets)
        assertTrue(Market.US in chief.supportedMarkets)
        assertFalse(ImportSourceChannel.PDF_STATEMENT in chief.supportedImportChannels)

        requireNotNull(schwab)
        assertTrue(Market.US in schwab.supportedMarkets)
        assertFalse(ImportSourceChannel.PDF_STATEMENT in schwab.supportedImportChannels)
    }
}
