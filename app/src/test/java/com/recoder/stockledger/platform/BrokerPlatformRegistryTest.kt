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

    @Test
    fun `registry exposes longbridge platform capabilities`() {
        val adapter = BrokerPlatformRegistry().adapterFor(BrokerPlatform.LONGBRIDGE)
        requireNotNull(adapter)
        assertTrue(Market.HK in adapter.supportedMarkets)
        assertTrue(Market.US in adapter.supportedMarkets)
        assertTrue(ImportSourceChannel.PDF_STATEMENT in adapter.supportedImportChannels)
    }

    @Test
    fun `all platforms that implement PDF statement parsing must be registered in the registry with PDF_STATEMENT channel`() {
        val platformsWithPdfParser = setOf(
            BrokerPlatform.ZHUORUI,
            BrokerPlatform.USMART,
            BrokerPlatform.HSBC,
            BrokerPlatform.LONGBRIDGE
        )
        val registry = BrokerPlatformRegistry()
        for (platform in platformsWithPdfParser) {
            val adapter = registry.adapterFor(platform)
            requireNotNull(adapter) { "Platform $platform is not registered in BrokerPlatformRegistry" }
            assertTrue(
                "Platform $platform has a PDF statement parser implemented but is not registered with ImportSourceChannel.PDF_STATEMENT in the registry",
                ImportSourceChannel.PDF_STATEMENT in adapter.supportedImportChannels
            )
        }
    }
}
