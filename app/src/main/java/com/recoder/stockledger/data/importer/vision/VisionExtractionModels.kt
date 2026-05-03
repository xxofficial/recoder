package com.recoder.stockledger.data.importer.vision

import com.recoder.stockledger.data.ImportSourceChannel
import com.recoder.stockledger.data.Market
import com.recoder.stockledger.data.TradeType
import java.time.LocalDate

data class VisionExtractedTrade(
    val tradeDate: LocalDate,
    val tradeType: TradeType,
    val symbol: String,
    val name: String,
    val market: Market,
    val exchange: String,
    val quantity: Int,
    val price: Double,
    val amount: Double,
    val currencyCode: String,
    val fees: VisionExtractedFees? = null,
    val rawText: String = "",
)

data class VisionExtractedFees(
    val commission: Double = 0.0,
    val platformFee: Double = 0.0,
    val settlementFee: Double = 0.0,
    val secFee: Double = 0.0,
    val transactionFee: Double = 0.0,
    val stampDuty: Double = 0.0,
    val otherFees: Double = 0.0,
)

fun VisionExtractedTrade.toParsedStatementTrade(tradeRef: String): com.recoder.stockledger.data.importer.ParsedStatementTrade {
    return com.recoder.stockledger.data.importer.ParsedStatementTrade(
        sourceChannel = ImportSourceChannel.ZHUORUI_STATEMENT,
        tradeType = tradeType,
        market = market,
        symbol = symbol,
        name = name,
        currencyCode = currencyCode,
        price = price,
        quantity = quantity,
        amount = amount,
        tradeDate = tradeDate,
        tradeRef = tradeRef,
        rawLine = rawText,
    )
}

sealed class VisionExtractionResult {
    data class Success(val trades: List<VisionExtractedTrade>) : VisionExtractionResult()
    data class Error(val message: String) : VisionExtractionResult()
}
