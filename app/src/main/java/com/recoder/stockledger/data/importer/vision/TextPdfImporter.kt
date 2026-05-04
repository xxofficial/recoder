package com.recoder.stockledger.data.importer.vision

import android.util.Log
import com.recoder.stockledger.data.importer.ParsedStatementTrade
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.InputStream

class TextPdfImporter(
    private val apiClient: VisionApiClient,
) {
    private companion object {
        const val TAG = "TextPdfImporter"
    }

    /**
     * Import trades from a PDF statement using text-based LLM extraction.
     *
     * @param inputStream PDF input stream.
     * @param password PDF password if encrypted.
     * @return List of parsed trades, or empty list if extraction fails.
     */
    suspend fun importStatement(
        inputStream: InputStream,
        password: String? = null,
    ): List<ParsedStatementTrade> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting text-based import, password=${password != null}")

            // 1. Decrypt and extract text
            val text = extractText(inputStream, password)
            Log.d(TAG, "Extracted text length: ${text.length}")

            if (text.isBlank()) {
                Log.w(TAG, "No text extracted from PDF")
                return@withContext emptyList<ParsedStatementTrade>()
            }

            // 2. Call text-based API
            val result = apiClient.extractTradesFromText(text, passwordHint = password)

            when (result) {
                is VisionExtractionResult.Success -> {
                    Log.d(TAG, "API returned ${result.trades.size} trades")
                    result.trades.mapIndexed { index, trade ->
                        val tradeRef = buildTradeRef(trade, index)
                        trade.toParsedStatementTrade(tradeRef)
                    }
                }
                is VisionExtractionResult.Error -> {
                    Log.e(TAG, "Extraction failed: ${result.message}")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            emptyList()
        }
    }

    private fun extractText(inputStream: InputStream, password: String?): String {
        return try {
            val doc = if (!password.isNullOrBlank()) {
                PDDocument.load(inputStream, password).apply {
                    if (isEncrypted) {
                        setAllSecurityToBeRemoved(true)
                    }
                }
            } else {
                PDDocument.load(inputStream)
            }
            doc.use { document ->
                val stripper = PDFTextStripper().apply {
                    sortByPosition = true
                }
                val text = stripper.getText(document)
                Log.d(TAG, "PDF text extraction successful, pages=${document.numberOfPages}")
                text
            }
        } catch (e: Exception) {
            Log.e(TAG, "Text extraction failed", e)
            ""
        }
    }

    private fun buildTradeRef(trade: VisionExtractedTrade, index: Int): String {
        val dateStr = trade.tradeDate.toString().replace("-", "")
        return "${dateStr}-${trade.symbol}-${trade.tradeType.name}-${trade.quantity}-$index"
    }
}
