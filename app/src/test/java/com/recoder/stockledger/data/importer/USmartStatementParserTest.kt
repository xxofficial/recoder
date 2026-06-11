package com.recoder.stockledger.data.importer

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import com.recoder.stockledger.data.TradeType

import org.json.JSONObject
import org.json.JSONArray

class USmartStatementParserTest {

    private val statementsDir = File("../Statements/uSMART")
    private val password = TestConfig.getPassword("usmart", "581577")
    private val goldenFile = File(statementsDir, "parsed_results.json")

    /**
     * ONE-TIME GENERATOR: Run this test once to create the golden standard JSON file.
     * After generating and verifying the file, this test should be disabled
     * by setting GENERATE_MODE = false.
     *
     * The generated file can also be used as a backup import into the app.
     */
    private val GENERATE_MODE = false

    @Test
    fun testParseAllUSmartPdfs() {
        org.junit.Assume.assumeTrue("Statements directory must exist", statementsDir.exists())
        val files = statementsDir.listFiles { f -> f.extension == "pdf" }?.sortedBy { it.name }
        assertNotNull("Should find PDF files", files)
        assertTrue("Should have PDF files in the directory", files!!.isNotEmpty())

        // Register mock OCR engine for tests running on host JVM
        USmartStatementPdfParser.ocrEngine = object : OcrEngine {
            override fun recognizeText(inputStream: java.io.InputStream): String? {
                val ocrFile = File(statementsDir, "2025-9_ocr.txt")
                return if (ocrFile.exists()) ocrFile.readText(Charsets.UTF_8) else null
            }
        }

        // Register JVM PDF text extractor
        USmartStatementPdfParser.pdfTextExtractor = object : PdfTextExtractor {
            override fun extractText(inputStream: java.io.InputStream, password: String?): String {
                val doc = if (!password.isNullOrBlank()) {
                    org.apache.pdfbox.pdmodel.PDDocument.load(inputStream, password)
                } else {
                    org.apache.pdfbox.pdmodel.PDDocument.load(inputStream)
                }
                return doc.use { document ->
                    val stripper = org.apache.pdfbox.text.PDFTextStripper().apply {
                        sortByPosition = true
                    }
                    stripper.getText(document)
                }
            }
        }

        // Parse all PDFs
        val allParsedTrades = mutableListOf<ParsedStatementTrade>()
        var failedFiles = 0

        for (file in files) {
            println("--- Parsing ${file.name} ---")
            try {
                val doc = PDDocument.load(file, password)
                val rawText = doc.use { document ->
                    val stripper = PDFTextStripper().apply {
                        sortByPosition = true
                    }
                    stripper.getText(document)
                }
                // Save extracted text for diagnosis
                val outDir = File("../Statements/uSMART/extracted")
                outDir.mkdirs()
                File(outDir, file.name.replace(".pdf", ".txt")).writeText(rawText)

                val parsed = file.inputStream().use { stream ->
                    USmartStatementPdfParser.parse(stream, password)
                }
                println("  Extracted ${parsed.size} records from ${file.name}")
                for (t in parsed) {
                    println("    [${t.tradeType}] ${t.symbol} (${t.name}) x${t.quantity} @${t.price} amt=${t.amount} comm=${t.commission} tax=${t.tax} date=${t.tradeDate}")
                    // Basic validation
                    assertNotNull("Symbol should not be null", t.symbol)
                    assertNotNull("Name should not be null", t.name)
                    assertTrue("Security name should be captured fully: ${t.name}", t.name.length >= 2)
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

        val trades = allParsedTrades.filter { it.tradeType != TradeType.INTEREST }
        val interests = allParsedTrades.filter { it.tradeType == TradeType.INTEREST }

        println("\n=== SUMMARY ===")
        println("Processed ${files.size} files")
        println("Failed: $failedFiles files")
        println("Total Trades: ${trades.size}")
        println("Total Interests: ${interests.size}")
        println("Total Records: ${allParsedTrades.size}")

        assertEquals("Should have 0 failed files", 0, failedFiles)
        assertTrue("Should extract at least some trades", trades.isNotEmpty())
        assertTrue("Should extract at least some interest records", interests.isNotEmpty())

        if (GENERATE_MODE) {
            // Generate the golden standard JSON file
            val json = toBackupJson(allParsedTrades)
            goldenFile.writeText(json, Charsets.UTF_8)
            println("\nGolden standard file written to: ${goldenFile.absolutePath}")
            println("Total records in JSON: ${allParsedTrades.size}")
            println("\n*** IMPORTANT: Set GENERATE_MODE = false after verifying the file! ***")
        } else {
            // Assertion mode: compare against the golden standard
            assertTrue("Golden standard file must exist: ${goldenFile.absolutePath}", goldenFile.exists())
            val expectedJson = goldenFile.readText(Charsets.UTF_8)
            
            // Lightweight custom regex parsing to avoid Android SDK org.json dependencies in unit tests
            val txBlockPattern = Regex("""\{\s*"tradeType"\s*:[^}]*\}""")
            val expectedBlocks = txBlockPattern.findAll(expectedJson).map { it.value }.toList()

            assertEquals(
                "Parsed trade count should match golden standard",
                expectedBlocks.size,
                allParsedTrades.size
            )

            // Helper function to extract keys from json block
            fun extractValue(jsonBlock: String, key: String): String {
                val regex = Regex(""""$key"\s*:\s*(?:"([^"]*)"|([^,\n}]*))""")
                val match = regex.find(jsonBlock) ?: return ""
                return (match.groupValues[1].takeIf { it.isNotEmpty() } ?: match.groupValues[2]).trim()
            }

            // Compare each trade
            for (idx in 0 until expectedBlocks.size) {
                val expectedBlock = expectedBlocks[idx]
                val actual = allParsedTrades[idx]

                assertEquals("Trade $idx symbol", extractValue(expectedBlock, "symbol"), actual.symbol)
                assertEquals("Trade $idx name", extractValue(expectedBlock, "name"), actual.name)
                assertEquals("Trade $idx tradeType", extractValue(expectedBlock, "tradeType"), actual.tradeType.name)
                assertEquals("Trade $idx market", extractValue(expectedBlock, "market"), actual.market.name)
                assertEquals("Trade $idx price", extractValue(expectedBlock, "price").toDouble(), actual.price, 0.0001)
                assertEquals("Trade $idx quantity", extractValue(expectedBlock, "quantity").toDouble(), actual.quantity, 0.0001)
                assertEquals("Trade $idx amount", extractValue(expectedBlock, "amount").toDouble(), actual.amount, 0.01)
                assertEquals("Trade $idx tradeDate", extractValue(expectedBlock, "tradeDate"), actual.tradeDate.toString())
                assertEquals("Trade $idx commission", extractValue(expectedBlock, "commission").toDouble(), actual.commission ?: 0.0, 0.01)
                assertEquals("Trade $idx tax", extractValue(expectedBlock, "tax").toDouble(), actual.tax ?: 0.0, 0.01)
            }
            println("\n*** All ${allParsedTrades.size} records match the golden standard! ***")
        }
    }

    /**
     * Convert parsed trades to the app's standard backup JSON format.
     * This format is compatible with the app's import/restore function.
     * Constructed manually to avoid Android SDK org.json dependencies in unit tests.
     */
    private fun toBackupJson(trades: List<ParsedStatementTrade>): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"version\": 3,\n")
        sb.append("  \"exportedAt\": ${System.currentTimeMillis()},\n")
        sb.append("  \"displayCurrency\": \"CNY\",\n")
        sb.append("  \"enabledPlatforms\": [\n")
        sb.append("    \"USMART\"\n")
        sb.append("  ],\n")
        sb.append("  \"selectedPlatform\": \"USMART\",\n")
        sb.append("  \"transactions\": [\n")
        
        trades.forEachIndexed { idx, t ->
            sb.append("    {\n")
            sb.append("      \"tradeType\": \"${t.tradeType.name}\",\n")
            sb.append("      \"platform\": \"USMART\",\n")
            sb.append("      \"sourceChannel\": \"${t.sourceChannel.name}\",\n")
            sb.append("      \"externalReference\": \"${t.tradeRef}\",\n")
            sb.append("      \"market\": \"${t.market.name}\",\n")
            sb.append("      \"symbol\": \"${t.symbol}\",\n")
            sb.append("      \"name\": \"${t.name.replace("\"", "\\\"")}\",\n")
            sb.append("      \"tradeDate\": \"${t.tradeDate}\",\n")
            sb.append("      \"tradeTime\": \"${t.tradeTime ?: ""}\",\n")
            sb.append("      \"price\": ${t.price},\n")
            sb.append("      \"quantity\": ${t.quantity},\n")
            sb.append("      \"amount\": ${t.amount},\n")
            sb.append("      \"commission\": ${t.commission ?: 0.0},\n")
            sb.append("      \"tax\": ${t.tax ?: 0.0},\n")
            sb.append("      \"note\": \"\",\n")
            sb.append("      \"createdAt\": ${t.createdAt ?: 0L}\n")
            sb.append("    }")
            if (idx < trades.size - 1) {
                sb.append(",")
            }
            sb.append("\n")
        }
        
        sb.append("  ]\n")
        sb.append("}")
        return sb.toString()
    }
}
