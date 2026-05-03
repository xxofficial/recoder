package com.recoder.stockledger.data.importer

import org.junit.Test
import java.io.File

class ZhuoruiStatementPdfParserDiagnosticTest {

    @Test
    fun `diagnose parseText with real PDF extracted text format A`() {
        parseFile("daily_statement_text.txt", "Format A")
    }

    @Test
    fun `diagnose parseText with real PDF extracted text format B`() {
        parseFile("daily_statement_text_formatb.txt", "Format B")
    }

    private fun parseFile(fileName: String, label: String) {
        val textFile = File("e:\\AndroidWorkSpace\\recoder\\app\\src\\test\\resources\\$fileName")
        if (!textFile.exists()) {
            println("$label text file not found, skipping")
            return
        }

        val text = textFile.readText(Charsets.UTF_8)
        println("\n========== $label ==========")
        println("Text length: ${text.length}")
        println("First 500 chars:\n${text.take(500)}")
        println("\n--- Parsing ---")

        val trades = ZhuoruiStatementPdfParser.parseText(text)
        println("Parsed trades: ${trades.size}")
        for (trade in trades) {
            println("  ${trade.tradeType} ${trade.symbol} x${trade.quantity} @${trade.price} on ${trade.tradeDate}")
        }
    }
}
