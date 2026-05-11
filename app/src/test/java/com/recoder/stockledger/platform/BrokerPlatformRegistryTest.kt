package com.recoder.stockledger.platform

import com.recoder.stockledger.data.BrokerPlatform
import com.recoder.stockledger.data.ImportSourceChannel
import com.recoder.stockledger.data.Market
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
}
