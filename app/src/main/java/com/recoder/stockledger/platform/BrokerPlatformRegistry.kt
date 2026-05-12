package com.recoder.stockledger.platform

import com.recoder.stockledger.data.BrokerPlatform
import com.recoder.stockledger.data.ImportSourceChannel
import com.recoder.stockledger.data.Market
import com.recoder.stockledger.data.TradeFeeEstimator

interface BrokerPlatformAdapter {
    val platform: BrokerPlatform
    val supportedMarkets: Set<Market>
    val supportedImportChannels: Set<ImportSourceChannel>
    val defaultFeePlanId: String
}

class DefaultBrokerPlatformAdapter(
    override val platform: BrokerPlatform,
    override val supportedMarkets: Set<Market>,
    override val supportedImportChannels: Set<ImportSourceChannel>,
) : BrokerPlatformAdapter {
    override val defaultFeePlanId: String = TradeFeeEstimator.defaultPlanId(platform)
}

class BrokerPlatformRegistry(
    adapters: List<BrokerPlatformAdapter> = defaultAdapters(),
) {
    private val adaptersByPlatform = adapters.associateBy { it.platform }

    fun adapterFor(platform: BrokerPlatform): BrokerPlatformAdapter? = adaptersByPlatform[platform]

    fun allAdapters(): List<BrokerPlatformAdapter> = adaptersByPlatform.values.toList()

    companion object {
        private fun defaultAdapters(): List<BrokerPlatformAdapter> = listOf(
            DefaultBrokerPlatformAdapter(
                platform = BrokerPlatform.ALIPAY,
                supportedMarkets = setOf(Market.A_SHARE, Market.HK, Market.US),
                supportedImportChannels = emptySet(),
            ),
            DefaultBrokerPlatformAdapter(
                platform = BrokerPlatform.EAST_MONEY,
                supportedMarkets = setOf(Market.A_SHARE, Market.HK, Market.US),
                supportedImportChannels = emptySet(),
            ),
            DefaultBrokerPlatformAdapter(
                platform = BrokerPlatform.LONGBRIDGE,
                supportedMarkets = setOf(Market.HK, Market.US),
                supportedImportChannels = emptySet(),
            ),
            DefaultBrokerPlatformAdapter(
                platform = BrokerPlatform.HSBC,
                supportedMarkets = setOf(Market.HK, Market.US),
                supportedImportChannels = setOf(ImportSourceChannel.HSBC_SMS, ImportSourceChannel.HSBC_EMAIL),
            ),
            DefaultBrokerPlatformAdapter(
                platform = BrokerPlatform.WEBULL,
                supportedMarkets = setOf(Market.HK, Market.US),
                supportedImportChannels = emptySet(),
            ),
            DefaultBrokerPlatformAdapter(
                platform = BrokerPlatform.ZHUORUI,
                supportedMarkets = setOf(Market.HK, Market.US),
                supportedImportChannels = setOf(
                    ImportSourceChannel.ZHUORUI_EMAIL,
                    ImportSourceChannel.ZHUORUI_STATEMENT,
                    ImportSourceChannel.PDF_STATEMENT,
                ),
            ),
            DefaultBrokerPlatformAdapter(
                platform = BrokerPlatform.CHIEF,
                supportedMarkets = setOf(Market.HK, Market.US),
                supportedImportChannels = setOf(ImportSourceChannel.PDF_STATEMENT),
            ),
            DefaultBrokerPlatformAdapter(
                platform = BrokerPlatform.SCHWAB,
                supportedMarkets = setOf(Market.US),
                supportedImportChannels = setOf(ImportSourceChannel.PDF_STATEMENT),
            ),
        )
    }
}
