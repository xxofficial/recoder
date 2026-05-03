package com.recoder.stockledger.data.importer.vision

import android.content.Context
import android.util.Log
import com.recoder.stockledger.data.importer.ParsedStatementTrade
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

class VisionPdfImporter(
    private val context: Context,
    private val apiClient: VisionApiClient,
) {
    private companion object {
        const val TAG = "VisionPdfImporter"
    }

    /**
     * Import trades from a PDF statement using vision-based extraction.
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
            Log.d(TAG, "Starting vision-based import, password=${password != null}")

            // 1. Decrypt if needed (PdfRenderer cannot handle encrypted PDFs)
            val decryptedStream = decryptIfNeeded(inputStream, password)

            // 2. Render PDF pages to images
            val images = PdfPageRenderer.renderPages(decryptedStream, password = null)
            Log.d(TAG, "Rendered ${images.size} page images")

            if (images.isEmpty()) {
                Log.w(TAG, "No pages rendered from PDF")
                return@withContext emptyList<ParsedStatementTrade>()
            }

            // 3. Call vision API
            val result = apiClient.extractTrades(images, passwordHint = password)

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

    /**
     * If the PDF is encrypted, decrypt it using PDFBox and return a new InputStream.
     * Otherwise return the original stream.
     */
    private fun decryptIfNeeded(inputStream: InputStream, password: String?): InputStream {
        if (password.isNullOrBlank()) return inputStream

        return try {
            val doc = PDDocument.load(inputStream, password)
            if (doc.isEncrypted) {
                doc.documentCatalog  // force decryption
            }
            val baos = java.io.ByteArrayOutputStream()
            doc.save(baos)
            doc.close()
            Log.d(TAG, "PDF decrypted successfully")
            ByteArrayInputStream(baos.toByteArray())
        } catch (e: Exception) {
            Log.w(TAG, "Decryption failed or PDF not encrypted, using original stream: ${e.message}")
            inputStream
        }
    }

    private fun buildTradeRef(trade: VisionExtractedTrade, index: Int): String {
        val dateStr = trade.tradeDate.toString().replace("-", "")
        return "${dateStr}-${trade.symbol}-${trade.tradeType.name}-${trade.quantity}-$index"
    }
}
