package com.recoder.stockledger.data

data class ExchangeRates(
    val usdToCny: Double = DisplayCurrency.USD.cnyRate,
    val hkdToCny: Double = DisplayCurrency.HKD.cnyRate,
    val updatedAtMillis: Long? = null,
)

enum class ExchangeRateOrigin {
    NETWORK,
    CACHE,
    DEFAULT,
}

data class ExchangeRateRefreshResult(
    val rates: ExchangeRates,
    val origin: ExchangeRateOrigin,
)

fun ExchangeRates.rateToCny(currency: DisplayCurrency): Double = when (currency) {
    DisplayCurrency.USD -> usdToCny
    DisplayCurrency.CNY -> 1.0
    DisplayCurrency.HKD -> hkdToCny
}

fun ExchangeRates.rateToCny(market: Market): Double = when (market) {
    Market.A_SHARE, Market.CASH -> 1.0
    Market.HONG_KONG -> hkdToCny
    Market.US -> usdToCny
}
