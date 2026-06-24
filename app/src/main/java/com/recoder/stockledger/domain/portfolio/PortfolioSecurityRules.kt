package com.recoder.stockledger.domain.portfolio

import com.recoder.stockledger.data.Market
import com.recoder.stockledger.data.TradeType
import java.time.LocalDate
import kotlin.math.round

object PortfolioSecurityRules {
    private const val US_TIMEZONE_CUTOFF = "06:00"

    fun isOptionAsset(assetType: String?, symbol: String): Boolean =
        assetType?.uppercase(java.util.Locale.US) == "OPTION" || isOptionSymbol(symbol)

    fun optionMultiplier(assetType: String?, symbol: String): Double =
        if (isOptionAsset(assetType, symbol)) 100.0 else 1.0

    fun positionKey(symbol: String, market: Market): String = "${market.name}:$symbol"

    fun attributionSymbol(symbol: String, assetType: String?, underlyingSymbol: String?): String =
        if (isOptionAsset(assetType, symbol)) {
            underlyingSymbol?.takeIf { it.isNotBlank() } ?: symbol.substringBefore(" ").ifBlank { symbol }
        } else {
            symbol
        }

    fun attributionKey(symbol: String, market: Market, assetType: String?, underlyingSymbol: String?): String =
        positionKey(attributionSymbol(symbol, assetType, underlyingSymbol), market)

    fun splitEventKey(market: Market, symbol: String, tradeDate: String, ratio: Double): String {
        val normalizedSymbol = symbol.trim().uppercase(java.util.Locale.US)
        val normalizedRatio = round(ratio * 1_000_000_000.0) / 1_000_000_000.0
        return "${market.name}:$normalizedSymbol:$tradeDate:$normalizedRatio"
    }

    fun splitEventKey(transaction: PortfolioTrade): String =
        splitEventKey(transaction.market, transaction.symbol, transaction.tradeDate, transaction.price)

    fun effectiveTradeDate(tradeDate: String, tradeTime: String, market: Market, tradeType: TradeType): LocalDate {
        val date = LocalDate.parse(tradeDate)
        if (tradeType == TradeType.SPLIT) return date
        return if (market == Market.US && tradeTime < US_TIMEZONE_CUTOFF) {
            date.minusDays(1)
        } else {
            date
        }
    }

    private fun isOptionSymbol(symbol: String): Boolean {
        val parts = symbol.trim().split(" ")
        if (parts.size != 2) return false
        val optPart = parts[1]
        if (optPart.length < 8) return false
        val datePart = optPart.substring(0, 6)
        if (!datePart.all { it.isDigit() }) return false
        val typeChar = optPart[6]
        return typeChar == 'C' || typeChar == 'P'
    }
}
