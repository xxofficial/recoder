package com.recoder.stockledger.data.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZhuoruiPhoneExtractTest {

    private fun loadResourceText(name: String): String {
        return javaClass.classLoader.getResourceAsStream(name)!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    @Test
    fun parsePhoneExtractedText_formatA_20260422() {
        val text = loadResourceText("phone_extract_20260422.txt")
        val trades = ZhuoruiStatementPdfParser.parseText(text)
        println("Parsed ${trades.size} trades from phone-extracted 2026-04-22 text")
        trades.forEach { println("  ${it.tradeType} ${it.symbol} x${it.quantity} @${it.price} ${it.currencyCode}") }
        assertTrue("Should parse at least 5 trades from 2026-04-22 file", trades.size >= 5)
    }

    @Test
    fun parsePhoneExtractedText_formatB_20251212() {
        val text = loadResourceText("phone_extract_20251212.txt")
        val trades = ZhuoruiStatementPdfParser.parseText(text)
        println("Parsed ${trades.size} trades from phone-extracted 2025-12-12 text")
        assertEquals(0, trades.size)
    }

    @Test
    fun parsePhoneExtractedText_noTrades_20260105() {
        val text = loadResourceText("phone_extract_20260105.txt")
        val trades = ZhuoruiStatementPdfParser.parseText(text)
        println("Parsed ${trades.size} trades from phone-extracted 2026-01-05 text")
        assertEquals(0, trades.size)
    }
}
