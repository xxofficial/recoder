package com.recoder.stockledger.data.importer.llm

import android.util.Log
import com.recoder.stockledger.data.importer.ParsedStatementTrade
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.StringWriter
import kotlin.math.abs

class TextPdfImporter(
    private val apiClient: TradeExtractionApiClient,
) {
    private companion object {
        const val TAG = "TextPdfImporter"

        // English-only keywords for matching — avoids traditional/simplified/CJK variant issues
        const val TRANSACTION_DETAILS_KEY = "Transaction Details"

        // All known section header keywords used to split the text into segments.
        // "Fund Transaction Details" must appear before "Transaction Details" in this list
        // so that it is matched first (since TD is a substring of FTD).
        val SECTION_KEYS = listOf(
            "Fund Transaction Details",
            "Unsettled Securities Transaction",
            "Interest Summary",
            "Important Notes",
            "Transaction Details",
        )

        val PAGE_MARKER_REGEX = Regex("""--- Page \d+/\d+ ---""")
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

            // 1. Decrypt and extract text with page markers
            val rawText = extractTextWithPageMarkers(inputStream, password)
            Log.d(TAG, "Raw text length: ${rawText.length}")

            if (rawText.isBlank()) {
                Log.w(TAG, "No text extracted from PDF")
                return@withContext emptyList<ParsedStatementTrade>()
            }

            // 2. Pre-filter to reduce token usage
            val filteredText = prefilterText(rawText)
            Log.d(TAG, "Filtered text length: ${filteredText.length} (saved ${rawText.length - filteredText.length} chars)")

            if (filteredText.isBlank()) {
                Log.w(TAG, "No relevant content after filtering")
                return@withContext emptyList<ParsedStatementTrade>()
            }

            // 3. Call text-based API
            val result = apiClient.extractTradesFromText(filteredText, passwordHint = password)

            when (result) {
                is LlmExtractionResult.Success -> {
                    Log.d(TAG, "API returned ${result.trades.size} trades")
                    result.trades.mapIndexed { index, trade ->
                        val tradeRef = buildTradeRef(trade, index)
                        trade.toParsedStatementTrade(tradeRef)
                    }
                }
                is LlmExtractionResult.Error -> {
                    Log.e(TAG, "Extraction failed: ${result.message}")
                    error(result.message)
                }
            }
        } catch (e: IllegalStateException) {
            // Re-throw API extraction errors so they propagate to the caller
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            throw e
        }
    }

    /**
     * Extract text from PDF page-by-page using coordinate-based parsing.
     * Collects individual glyph positions and reconstructs rows by Y coordinate,
     * which avoids the ordering and duplication artifacts of PDFTextStripper.getText().
     * Inserts page markers like `--- Page 1/4 ---` between pages.
     */
    internal fun extractTextWithPageMarkers(inputStream: InputStream, password: String?): String {
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
                val totalPages = document.numberOfPages
                Log.d(TAG, "PDF text extraction successful, pages=$totalPages")

                val sb = StringBuilder()
                val extractor = CoordinateTextExtractor()

                for (page in 1..totalPages) {
                    val pageText = extractor.extractPage(document, page)
                    sb.appendLine("--- Page $page/$totalPages ---")
                    sb.append(pageText)
                }
                sb.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Text extraction failed", e)
            throw e
        }
    }

    /**
     * Pre-filter the extracted PDF text to keep only trade-relevant sections,
     * reducing token usage before sending to the LLM.
     *
     * Approach:
     * 1. Find all known section headers and record the start-of-line position for each.
     * 2. Sort by position to divide the text into segments.
     * 3. Only keep segments whose header is "Transaction Details" (not "Fund Transaction Details").
     *
     * Page markers ("--- Page N/M ---") are already embedded in the text from
     * extractTextWithPageMarkers, so they are naturally included in the segments.
     */
    internal fun prefilterText(text: String): String {
        // 1. Find all section header positions, expanded to the start of their line
        data class SectionMark(val lineStart: Int, val key: String)

        val marks = mutableListOf<SectionMark>()
        for (key in SECTION_KEYS) {
            var searchFrom = 0
            while (true) {
                val idx = text.indexOf(key, searchFrom, ignoreCase = true)
                if (idx < 0) break

                // "Transaction Details" is a substring of "Fund Transaction Details".
                // If this match is actually part of a longer FTD, skip it.
                if (key == TRANSACTION_DETAILS_KEY) {
                    val lookBackStart = maxOf(0, idx - 30)
                    val lookBack = text.substring(lookBackStart, idx)
                    if (lookBack.trimEnd().endsWith("Fund", ignoreCase = true)) {
                        searchFrom = idx + key.length
                        continue
                    }
                }

                // Expand to the beginning of the line containing this keyword
                val lineStart = text.lastIndexOf('\n', idx - 1).let { if (it < 0) 0 else it + 1 }

                marks.add(SectionMark(lineStart, key))
                searchFrom = idx + key.length
            }
        }

        if (marks.isEmpty()) {
            Log.d(TAG, "No recognized section headers found in text")
            return ""
        }

        // Sort by position in the text
        marks.sortBy { it.lineStart }

        // 2. Extract only "Transaction Details" segments
        val result = StringBuilder()
        for (i in marks.indices) {
            val mark = marks[i]
            if (mark.key != TRANSACTION_DETAILS_KEY) continue

            val segStart = mark.lineStart
            val segEnd = if (i + 1 < marks.size) marks[i + 1].lineStart else text.length
            val segment = text.substring(segStart, segEnd).trimEnd()

            if (result.isNotEmpty()) result.appendLine() // separator between multiple TD segments
            result.append(segment)
        }

        return result.toString().trim()
    }

    private fun buildTradeRef(trade: LlmExtractedTrade, index: Int): String {
        val dateStr = trade.tradeDate.toString().replace("-", "")
        val priceStr = String.format("%.4f", trade.price)
        return "${dateStr}-${trade.symbol}-${trade.tradeType.name}-${trade.quantity}-${priceStr}-$index"
    }
}

/**
 * Coordinate-based PDF text extractor.
 *
 * Instead of relying on PDFTextStripper's built-in text flow algorithm (which can
 * reorder table headers and duplicate overlapping text elements), this class:
 * 1. Collects raw glyph positions via [processTextPosition].
 * 2. Groups glyphs into rows by Y-coordinate proximity.
 * 3. Sorts each row left-to-right by X-coordinate.
 * 4. Inserts spaces or tabs based on inter-glyph gaps to preserve column structure.
 */
class CoordinateTextExtractor : PDFTextStripper() {

    private data class TextGlyph(
        val text: String,
        val x: Float,
        val y: Float,
        val endX: Float,
        val spaceWidth: Float,
    )

    private val glyphs = mutableListOf<TextGlyph>()

    init {
        sortByPosition = false // We do our own coordinate-based sorting
    }

    /**
     * Intercept each glyph's position. We intentionally do NOT call super
     * so the default pipeline produces no output — we build text ourselves.
     */
    override fun processTextPosition(text: TextPosition) {
        val str = text.unicode
        if (!str.isNullOrEmpty()) {
            glyphs.add(
                TextGlyph(
                    text = str,
                    x = text.xDirAdj,
                    y = text.yDirAdj,
                    endX = text.xDirAdj + text.widthDirAdj,
                    spaceWidth = text.widthOfSpace,
                )
            )
        }
    }

    /**
     * Extract text from a single page using coordinate-based reconstruction.
     */
    fun extractPage(document: PDDocument, pageNum: Int): String {
        glyphs.clear()
        startPage = pageNum
        endPage = pageNum
        // Trigger processing; output goes to a dummy writer since we build our own text
        writeText(document, StringWriter())
        return buildText()
    }

    private fun buildText(): String {
        if (glyphs.isEmpty()) return ""

        // Sort all glyphs: top-to-bottom (Y), then left-to-right (X)
        val sorted = glyphs.sortedWith(compareBy({ it.y }, { it.x }))

        // Group into rows: glyphs within ROW_TOLERANCE of each other's Y are on the same line
        val ROW_TOLERANCE = 2.0f
        val rows = mutableListOf<MutableList<TextGlyph>>()
        var currentRow = mutableListOf(sorted[0])
        var rowY = sorted[0].y

        for (i in 1 until sorted.size) {
            val g = sorted[i]
            if (abs(g.y - rowY) > ROW_TOLERANCE) {
                rows.add(currentRow)
                currentRow = mutableListOf()
                rowY = g.y
            }
            currentRow.add(g)
        }
        if (currentRow.isNotEmpty()) rows.add(currentRow)

        // Build text from rows
        val sb = StringBuilder()
        for (row in rows) {
            row.sortBy { it.x }

            var lastEndX = -1f
            val avgSpace = row
                .map { it.spaceWidth }
                .filter { it > 0f }
                .let { if (it.isEmpty()) 4f else it.average().toFloat() }

            for (glyph in row) {
                if (lastEndX >= 0f) {
                    val gap = glyph.x - lastEndX
                    when {
                        gap > avgSpace * 2.5f -> sb.append('\t') // Column gap
                        gap > avgSpace * 0.3f -> sb.append(' ')  // Word gap
                        // else: adjacent glyphs, no separator
                    }
                }
                sb.append(glyph.text)
                lastEndX = glyph.endX
            }
            sb.appendLine()
        }

        return sb.toString()
    }
}
