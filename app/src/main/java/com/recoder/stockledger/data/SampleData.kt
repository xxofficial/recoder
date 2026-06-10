package com.recoder.stockledger.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object SampleData {
    private val tradeTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun tradeForm(defaultType: TradeType): TradeFormState = TradeFormState(
        selectedType = defaultType,
        platform = BrokerPlatform.configurableEntries.first(),
        market = if (defaultType.isSecurityTrade) Market.A_SHARE else Market.CASH,
        symbolOrName = "",
        tradeDate = LocalDate.now().toString(),
        tradeTime = LocalTime.now().format(tradeTimeFormatter),
        priceLabel = "",
        quantityLabel = if (defaultType == TradeType.SPLIT) "1" else if (defaultType.isSecurityTrade) "" else "1",
        commissionLabel = "0.00",
        taxLabel = "0.00",
        note = "",
        feeEstimateStatus = FeeEstimateStatus.UNAVAILABLE,
        feeEstimateSummary = null,
        feeEstimateDetail = null,
        canAutoEstimateFees = false,
    )
}
