package com.recoder.stockledger.data.importer

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import com.recoder.stockledger.data.TradeType
import com.recoder.stockledger.data.Market

class HsbcStatementPdfParserTest {

    private val statementsDir = File("e:\\AndroidWorkSpace\\recoder\\Statements\\hsbc")
    private val password = TestConfig.getPassword("hsbc", "581577")

    @Before
    fun setUp() {
        val resourceName = "org/apache/pdfbox/resources/glyphlist/glyphlist.txt"
        val inputStream = this.javaClass.classLoader.getResourceAsStream(resourceName)
        if (inputStream != null) {
            val bytes = inputStream.readBytes()
            val paths = listOf(
                "app/build/resources/test/com/tom_roush/pdfbox/resources/glyphlist/glyphlist.txt",
                "app/build/classes/kotlin/test/com/tom_roush/pdfbox/resources/glyphlist/glyphlist.txt",
                "app/build/classes/java/test/com/tom_roush/pdfbox/resources/glyphlist/glyphlist.txt",
                "app/src/test/resources/com/tom_roush/pdfbox/resources/glyphlist/glyphlist.txt"
            )
            for (path in paths) {
                try {
                    val destFile = File(path)
                    destFile.parentFile.mkdirs()
                    destFile.writeBytes(bytes)
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }


    @Test
    fun testParseCashMovementsFromText() {
        val text = """
            Charges and income summary
            01APR2026 DIVIDEND USD NVDA.US CASH DIVIDEND USD 12.50
            01APR2026 WITHHOLDING TAX USD NVDA.US WITHHOLDING TAX USD -3.75
            02APR2026 CASH REWARD USD PROMOTION REWARD USD 100.00
            03APR2026 DEPOSIT HKD HKD 19,000.00
            04APR2026 WITHDRAWAL HKD HKD -500.00
            08SEP2025 WITHDRAWAL HKD HKD 72864
            09SEP2025 TRADE25 HKD MONTHLY FEE HKD 25.00
            05APR2026 FOREIGN EXCHANGE OUT HKD HKD TO USD @ 0.1272 HKD -5,000.00
            05APR2026 FOREIGN EXCHANGE IN USD HKD TO USD @ 0.1272 USD 636.00
            06APR2026 WITHHOLDING TAX USD MSFT.US WITHHOLDING TAX USD -1.00
        """.trimIndent()

        val parsed = HsbcStatementPdfParser.parseText(text)

        val dividend = parsed.single { it.tradeType == TradeType.DIVIDEND }
        assertEquals("NVDA", dividend.symbol)
        assertEquals(Market.US, dividend.market)
        assertEquals("USD", dividend.currencyCode)
        assertEquals(12.50, dividend.price, 0.0001)
        assertEquals(3.75, dividend.tax ?: 0.0, 0.0001)
        assertTrue(dividend.rawLine.contains("WITHHOLDING TAX"))

        val tax = parsed.single { it.tradeType == TradeType.TAX }
        assertEquals("MSFT", tax.symbol)
        assertEquals(1.0, tax.price, 0.0001)

        val others = parsed.filter { it.tradeType == TradeType.OTHER }
        assertEquals(2, others.size)
        val reward = others.single { it.price > 0.0 }
        assertEquals("CASH", reward.symbol)
        assertEquals(100.0, reward.price, 0.0001)
        val trade25 = others.single { it.price < 0.0 }
        assertEquals("CASH", trade25.symbol)
        assertEquals(Market.HK, trade25.market)
        assertEquals("HKD", trade25.currencyCode)
        assertEquals(-25.0, trade25.price, 0.0001)
        assertEquals(-25.0, trade25.amount, 0.0001)

        val deposit = parsed.single { it.tradeType == TradeType.DEPOSIT }
        assertEquals(Market.HK, deposit.market)
        assertEquals("HKD", deposit.currencyCode)
        assertEquals(19000.0, deposit.price, 0.0001)

        val withdraws = parsed.filter { it.tradeType == TradeType.WITHDRAW }
        assertEquals(2, withdraws.size)
        assertNotNull(withdraws.firstOrNull { kotlin.math.abs(it.price - 500.0) < 0.0001 })
        assertNotNull(withdraws.firstOrNull { kotlin.math.abs(it.price - 728.64) < 0.0001 })

        val fx = parsed.single { it.tradeType == TradeType.FX_CONVERSION }
        assertEquals("HKD", fx.fxFromCurrency)
        assertEquals(5000.0, fx.fxFromAmount ?: 0.0, 0.0001)
        assertEquals("USD", fx.fxToCurrency)
        assertEquals(636.0, fx.fxToAmount ?: 0.0, 0.0001)
        assertEquals(0.1272, fx.fxRate ?: 0.0, 0.0000001)
    }


    @Test
    fun testParseAllHsbcPdfs() {
        assertTrue("Statements directory must exist", statementsDir.exists())
        val files = statementsDir.listFiles { f -> f.extension.equals("pdf", ignoreCase = true) }?.sortedBy { it.name }
        assertNotNull("Should find PDF files", files)
        assertTrue("Should have PDF files in the directory", files!!.isNotEmpty())

        val allParsedTrades = mutableListOf<ParsedStatementTrade>()
        var failedFiles = 0

        for (file in files) {
            println("--- Parsing HSBC ${file.name} ---")
            try {
                var doc: PDDocument? = null
                try {
                    doc = PDDocument.load(file)
                } catch (e: Exception) {
                    try {
                        doc = PDDocument.load(file, password)
                    } catch (e2: Exception) {
                        throw e2
                    }
                }

                val rawText = doc!!.use { document ->
                    val stripper = PDFTextStripper().apply {
                        sortByPosition = true
                    }
                    stripper.getText(document)
                }

                val parsed = HsbcStatementPdfParser.parseText(rawText)
                println("  Extracted ${parsed.size} records from ${file.name}")
                for (t in parsed) {
                    println("    [${t.tradeType}] ${t.symbol} (${t.name}) x${t.quantity} @${t.price} amt=${t.amount} comm=${t.commission} tax=${t.tax} date=${t.tradeDate} ref=${t.tradeRef}")
                    assertNotNull("Symbol should not be null", t.symbol)
                    assertNotNull("Name should not be null", t.name)
                    assertTrue("Commission should not be negative", (t.commission ?: 0.0) >= 0.0)
                    assertTrue("Tax should not be negative", (t.tax ?: 0.0) >= 0.0)
                }
                allParsedTrades.addAll(parsed)
            } catch (e: Exception) {
                println("  FAILED to parse ${file.name}: ${e.message}")
                e.printStackTrace()
                failedFiles++
            }
        }

        val trades = allParsedTrades.filter { it.symbol != "CUSTODY" }
        val custodyFees = allParsedTrades.filter { it.symbol == "CUSTODY" }

        println("\n=== HSBC PARSING SUMMARY ===")
        println("Processed ${files.size} files")
        println("Failed: $failedFiles files")
        println("Total Security Trades: ${trades.size}")
        println("Total Safe Custody Charges: ${custodyFees.size}")
        println("Total Records: ${allParsedTrades.size}")

        assertEquals("Should have 0 failed files", 0, failedFiles)
        assertTrue("Should extract at least some trades", trades.isNotEmpty())
        assertTrue("Should extract at least some custody charges", custodyFees.isNotEmpty())

        // Additional detailed validations
        // Safe custody charges should all be HKD 25.00
        for (c in custodyFees) {
            assertEquals("Custody fee type should be OTHER", TradeType.OTHER, c.tradeType)
            assertEquals("Custody fee amount should be -25.00", -25.00, c.amount, 0.001)
            assertEquals("Custody fee price should be -25.00", -25.00, c.price, 0.001)
            assertEquals("Custody fee currency should be HKD", "HKD", c.currencyCode)
        }
    }

    @Test
    fun testSpecificFileSqqq() {
        val file = File(statementsDir, "eStatementFile_20260526113616.pdf")
        assertTrue(file.exists())
        var doc: PDDocument? = null
        try {
            doc = PDDocument.load(file)
        } catch (e: Exception) {
            doc = PDDocument.load(file, password)
        }
        val rawText = doc.use { document ->
            val stripper = PDFTextStripper().apply {
                sortByPosition = true
            }
            stripper.getText(document)
        }
        val parsed = HsbcStatementPdfParser.parseText(rawText)
        println("--- SQQQ trades parsed from eStatementFile_20260526113616.pdf ---")
        val sqqqTrades = parsed.filter { it.symbol == "SQQQ" }
        sqqqTrades.forEach { t ->
            println("[${t.tradeType}] ${t.symbol} x${t.quantity} @${t.price} ref=${t.tradeRef} date=${t.tradeDate}")
        }
        assertEquals("Should parse all 4 SQQQ trades", 4, sqqqTrades.size)
    }
}

