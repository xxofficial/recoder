package com.recoder.stockledger.data.importer

import com.recoder.stockledger.data.BrokerPlatform
import com.recoder.stockledger.data.ImportSourceChannel
import com.recoder.stockledger.data.repository.TradeImportResult
import java.io.InputStream

interface TradeImporter<Input> {
    val sourceChannel: ImportSourceChannel

    suspend fun import(input: Input): List<TradeImportResult>
}

data class TextTradeImportInput(
    val rawText: String,
    val receivedAtMillis: Long = System.currentTimeMillis(),
)

data class PdfTradeImportInput(
    val inputStream: InputStream,
    val password: String,
    val platform: BrokerPlatform,
)
