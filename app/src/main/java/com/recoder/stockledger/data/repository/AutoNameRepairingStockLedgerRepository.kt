package com.recoder.stockledger.data.repository

import android.util.Log
import com.recoder.stockledger.data.BrokerPlatform
import com.recoder.stockledger.data.Market
import com.recoder.stockledger.data.ZhuoruiEmailSyncConfig
import com.recoder.stockledger.data.importer.ParsedStatementTrade
import java.io.InputStream

class AutoNameRepairingStockLedgerRepository(
    private val delegate: DefaultLedgerRepository,
) : StockLedgerRepository by delegate {

    private val TAG = "AutoNameRepairingRepo"

    override suspend fun importSharedTradeText(
        rawText: String,
        receivedAtMillis: Long,
    ): TradeImportResult {
        val result = delegate.importSharedTradeText(rawText, receivedAtMillis)
        if (result.outcome == TradeImportOutcome.IMPORTED && result.externalReference != null) {
            repairNamesForExternalReferences(listOf(result.externalReference))
        }
        return result
    }

    override suspend fun importHsbcNotificationText(
        rawText: String,
        receivedAtMillis: Long,
    ): TradeImportResult {
        val result = delegate.importHsbcNotificationText(rawText, receivedAtMillis)
        if (result.outcome == TradeImportOutcome.IMPORTED && result.externalReference != null) {
            repairNamesForExternalReferences(listOf(result.externalReference))
        }
        return result
    }

    override suspend fun importStatementPdf(
        inputStream: InputStream,
        password: String,
        platform: BrokerPlatform,
        ledgerId: Long,
    ): List<TradeImportResult> {
        val results = delegate.importStatementPdf(inputStream, password, platform, ledgerId)
        val importedRefs = results.filter { it.outcome == TradeImportOutcome.IMPORTED }
            .mapNotNull { it.externalReference }
        if (importedRefs.isNotEmpty()) {
            repairNamesForExternalReferences(importedRefs)
        }
        return results
    }

    override suspend fun importParsedTrades(
        parsedTrades: List<ParsedStatementTrade>,
        platform: BrokerPlatform,
        ledgerId: Long,
    ): List<TradeImportResult> {
        val results = delegate.importParsedTrades(parsedTrades, platform, ledgerId)
        val importedRefs = results.filter { it.outcome == TradeImportOutcome.IMPORTED }
            .mapNotNull { it.externalReference }
        if (importedRefs.isNotEmpty()) {
            repairNamesForExternalReferences(importedRefs)
        }
        return results
    }

    override suspend fun syncZhuoruiMailbox(
        config: ZhuoruiEmailSyncConfig,
        lastSyncAtMillis: Long,
        fetchCount: Int,
        earliestReceivedAtMillis: Long?,
    ): ZhuoruiMailboxSyncResult {
        val result = delegate.syncZhuoruiMailbox(config, lastSyncAtMillis, fetchCount, earliestReceivedAtMillis)
        if (result.importedExternalReferences.isNotEmpty()) {
            repairNamesForExternalReferences(result.importedExternalReferences)
        }
        return result
    }

    private suspend fun repairNamesForExternalReferences(externalReferences: List<String>) {
        try {
            delegate.repairNamesByExternalReferences(externalReferences)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to automatically repair names after import", e)
        }
    }
}
