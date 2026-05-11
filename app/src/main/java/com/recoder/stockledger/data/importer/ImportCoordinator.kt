package com.recoder.stockledger.data.importer

import com.recoder.stockledger.data.repository.ImportRepository
import com.recoder.stockledger.data.repository.LedgerRepository
import com.recoder.stockledger.data.repository.MarketDataRepository
import com.recoder.stockledger.data.repository.TradeImportOutcome
import com.recoder.stockledger.data.repository.TradeImportResult
import kotlinx.coroutines.flow.first

class ImportCoordinator(
    private val ledgerRepository: LedgerRepository,
    private val marketDataRepository: MarketDataRepository,
    private val importRepository: ImportRepository,
) {
    suspend fun importSharedTradeText(
        rawText: String,
        receivedAtMillis: Long = System.currentTimeMillis(),
    ): TradeImportResult {
        val result = importRepository.importSharedTradeText(rawText, receivedAtMillis)
        refreshQuotesAfterImport(result)
        return result
    }

    suspend fun importHsbcNotificationText(
        rawText: String,
        receivedAtMillis: Long = System.currentTimeMillis(),
    ): TradeImportResult {
        val result = importRepository.importHsbcNotificationText(rawText, receivedAtMillis)
        refreshQuotesAfterImport(result)
        return result
    }

    private suspend fun refreshQuotesAfterImport(result: TradeImportResult) {
        if (result.outcome == TradeImportOutcome.IMPORTED) {
            runCatching {
                marketDataRepository.refreshQuotesForPortfolio(ledgerRepository.transactions.first())
            }
        }
    }
}
