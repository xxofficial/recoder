package com.recoder.stockledger.data.importer

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.Test
import java.io.File

class LongBridgeStatementPdfDiagnosticTest {

    @Test
    fun diagnoseAllLongBridgePdfs() {
        val dir = File("e:\\AndroidWorkSpace\\recoder\\Statements\\longBridge")
        if (!dir.exists()) {
            println("LongBridge directory not found")
            return
        }

        val pdfFiles = dir.listFiles { f -> f.extension.equals("pdf", ignoreCase = true) }
        if (pdfFiles == null || pdfFiles.isEmpty()) {
            println("No PDF files found in Statements/longBridge")
            return
        }

        val password = "15772779"
        println("Found ${pdfFiles.size} LongBridge PDF files:")
        for (file in pdfFiles) {
            println("\n========================================")
            println("Processing ${file.name}...")
            try {
                val doc = PDDocument.load(file, password)
                doc.use { document ->
                    val stripper = PDFTextStripper().apply {
                        sortByPosition = true
                    }
                    val rawText = stripper.getText(document)
                    println("  Extracted ${rawText.length} characters.")
                    
                    if (file.name.contains("202604")) {
                        val outFile = File("e:\\AndroidWorkSpace\\recoder\\Statements\\longBridge\\extracted_pdfbox_202604.txt")
                        outFile.writeText(rawText, Charsets.UTF_8)
                        println("  Wrote PDFBox extracted text to: ${outFile.absolutePath}")
                    }

                    val normalized = java.text.Normalizer.normalize(rawText, java.text.Normalizer.Form.NFKC)
                    val lines = normalized.lines().map { it.trim() }.filter { it.isNotBlank() }
                    println("  Total non-blank lines: ${lines.size}")
                    var osCount = 0
                    for (idx in lines.indices) {
                        if (lines[idx].contains("OS")) {
                            osCount++
                            if (osCount <= 5) {
                                println("    --- OS Match #$osCount at index $idx ---")
                                val start = maxOf(0, idx - 5)
                                val end = minOf(lines.size - 1, idx + 5)
                                for (k in start..end) {
                                    val prefix = if (k == idx) ">>> " else "    "
                                    println("$prefix[$k] ${lines[k]}")
                                }
                            }
                        }
                    }
                    println("  Total OS occurrences: $osCount")

                    val trades = LongBridgeStatementPdfParser.parseText(rawText)
                    for ((idx, trade) in trades.withIndex()) {
                        println("    Trade #${idx + 1}: ${trade.tradeDate} | ${trade.tradeType} | ${trade.symbol} | ${trade.name} | qty=${trade.quantity} | price=${trade.price} | amt=${trade.amount} | comm=${trade.commission} | tax=${trade.tax} | assetType=${trade.assetType} | under=${trade.underlyingSymbol} | exp=${trade.expiryDate} | strike=${trade.strikePrice}")
                    }
                }
            } catch (e: Exception) {
                println("  Failed to process ${file.name}: ${e.message}")
            }
        }
    }
}
