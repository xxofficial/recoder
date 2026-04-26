package com.recoder.stockledger.data

import java.time.LocalDate

object SampleData {
    fun tradeForm(defaultType: TradeType): TradeFormState = TradeFormState(
        selectedType = defaultType,
        platform = BrokerPlatform.configurableEntries.first(),
        market = if (defaultType.isSecurityTrade) Market.A_SHARE else Market.CASH,
        symbolOrName = "",
        tradeDate = LocalDate.now().toString(),
        priceLabel = "",
        quantityLabel = if (defaultType.isSecurityTrade) "" else "1",
        commissionLabel = "0.00",
        taxLabel = "0.00",
        note = "",
    )
}
