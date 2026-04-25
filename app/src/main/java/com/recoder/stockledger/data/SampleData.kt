package com.recoder.stockledger.data

import java.time.LocalDate

object SampleData {
    fun tradeForm(defaultType: TradeType): TradeFormState = TradeFormState(
        selectedType = defaultType,
        market = Market.A_SHARE,
        symbolOrName = "",
        tradeDate = LocalDate.now().toString(),
        priceLabel = "",
        quantityLabel = "",
        commissionLabel = "0.00",
        taxLabel = "0.00",
        note = "",
    )
}
