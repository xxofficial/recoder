package com.recoder.stockledger.data.importer.llm

import com.recoder.stockledger.data.ImportSourceChannel
import com.recoder.stockledger.data.Market
import com.recoder.stockledger.data.TradeType
import java.time.LocalDate

data class LlmExtractedTrade(
    val tradeType: TradeType,
    val market: Market,
    val symbol: String,
    val name: String,
    val tradeDate: LocalDate,
    val tradeTime: String,
    val price: Double,
    val quantity: Int,
    val commission: Double,
    val tax: Double,
    val note: String,
    val createdAt: Long,
)

fun LlmExtractedTrade.toParsedStatementTrade(tradeRef: String): com.recoder.stockledger.data.importer.ParsedStatementTrade {
    return com.recoder.stockledger.data.importer.ParsedStatementTrade(
        sourceChannel = ImportSourceChannel.PDF_STATEMENT,
        tradeType = tradeType,
        market = market,
        symbol = symbol,
        name = name,
        currencyCode = if (market == Market.US) "USD" else if (market == Market.HK) "HKD" else "CNY",
        price = price,
        quantity = quantity,
        amount = price * quantity,
        tradeDate = tradeDate,
        tradeTime = tradeTime,
        commission = commission.takeIf { it > 0 },
        platformFee = null,
        tax = tax.takeIf { it > 0 },
        tradeRef = tradeRef,
        rawLine = note,
        createdAt = createdAt,
    )
}

sealed class LlmExtractionResult {
    data class Success(val trades: List<LlmExtractedTrade>) : LlmExtractionResult()
    data class Error(val message: String) : LlmExtractionResult()
}
