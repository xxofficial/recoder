package com.recoder.stockledger.data.importer

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import com.recoder.stockledger.data.Market
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
    fun testParseCashMovementsFromText() {
        val text = """
            结单日期：2026-04
            资金明细
            USD
            2026-04-10 现金分红 USD NVDA.US Cash Dividend 12.50
            2026-04-10 Withholding Tax USD NVDA.US Withholding Tax -3.75
            2026-04-11 活动礼包 USD 现金奖励 100.00
            HKD
            2026-05-22 存入资金 1,000.00
            2026-05-28 存入资金 HKD 19,000.00
            2026-05-29 提取资金 HKD -500.00
            2026-04-12 货币兑换出账 HKD HKD 换汇至 USD @ 0.1272 -5,000.00
            2026-04-12 货币兑换入账 USD HKD 换汇至 USD @ 0.1272 636.00
            融资利息 USD -2.65 2026-04-30
        """.trimIndent()

        val parsed = USmartStatementPdfParser.parseText(text)

        val dividend = parsed.single { it.tradeType == TradeType.DIVIDEND }
        assertEquals("NVDA", dividend.symbol)
        assertEquals(Market.US, dividend.market)
        assertEquals("USD", dividend.currencyCode)
        assertEquals(12.50, dividend.price, 0.0001)
        assertEquals(1.0, dividend.quantity, 0.0001)
        assertEquals(3.75, dividend.tax ?: 0.0, 0.0001)
        assertTrue(dividend.rawLine.contains("Withholding Tax"))

        val other = parsed.single { it.tradeType == TradeType.OTHER }
        assertEquals("CASH", other.symbol)
        assertEquals(100.00, other.price, 0.0001)

        val deposits = parsed.filter { it.tradeType == TradeType.DEPOSIT }.sortedBy { it.price }
        assertEquals(2, deposits.size)
        assertEquals(listOf(1000.0, 19000.0), deposits.map { it.price })
        deposits.forEach {
            assertEquals(Market.HK, it.market)
            assertEquals("HKD", it.currencyCode)
        }

        val withdraw = parsed.single { it.tradeType == TradeType.WITHDRAW }
        assertEquals(500.0, withdraw.price, 0.0001)
        assertEquals("HKD", withdraw.currencyCode)

        val fx = parsed.single { it.tradeType == TradeType.FX_CONVERSION }
        assertEquals("HKD", fx.fxFromCurrency)
        assertEquals(5000.0, fx.fxFromAmount ?: 0.0, 0.0001)
        assertEquals("USD", fx.fxToCurrency)
        assertEquals(636.0, fx.fxToAmount ?: 0.0, 0.0001)
        assertEquals(0.1272, fx.fxRate ?: 0.0, 0.0000001)

        val interest = parsed.single { it.tradeType == TradeType.INTEREST }
        assertEquals(2.65, interest.price, 0.0001)
    }

    @Test
    fun testParseOptionTradesFromCashMovements() {
        val text = """
            结单日期：2026-04
            资金明细
            资金自动划出—买入期
            USD -40.00 2026-03-02 LITE260227P600000
            权
            资金自动划出—买入期
            USD -0.98 2026-03-02 LITE260227P600000
            权手续费
            资金自动转入—期权账
            USD 10.00 2026-03-04 QQQ260303C606000
            户卖出期权
            资金自动划出—卖出期
            USD -0.54 2026-03-04 QQQ260303C606000
            权手续费
        """.trimIndent()

        val parsed = USmartStatementPdfParser.parseText(text)
        val buy = parsed.single { it.tradeType == TradeType.BUY }
        assertEquals("LITE 260227P600", buy.symbol)
        assertEquals("LITE", buy.underlyingSymbol)
        assertEquals("2026-02-27", buy.expiryDate)
        assertEquals(600.0, buy.strikePrice ?: 0.0, 0.0001)
        assertEquals("PUT", buy.optionType)
        assertEquals("OPTION", buy.assetType)
        assertEquals(Market.US, buy.market)
        assertEquals("USD", buy.currencyCode)
        assertEquals(0.40, buy.price, 0.0001)
        assertEquals(1.0, buy.quantity, 0.0001)
        assertEquals(40.0, buy.amount, 0.0001)
        assertEquals(0.98, buy.commission ?: 0.0, 0.0001)
        assertTrue(buy.rawLine.contains("手续费"))

        val sell = parsed.single { it.tradeType == TradeType.SELL }
        assertEquals("QQQ 260303C606", sell.symbol)
        assertEquals("QQQ", sell.underlyingSymbol)
        assertEquals("2026-03-03", sell.expiryDate)
        assertEquals(606.0, sell.strikePrice ?: 0.0, 0.0001)
        assertEquals("CALL", sell.optionType)
        assertEquals("OPTION", sell.assetType)
        assertEquals(0.10, sell.price, 0.0001)
        assertEquals(10.0, sell.amount, 0.0001)
        assertEquals(0.54, sell.commission ?: 0.0, 0.0001)
        assertTrue(sell.tradeRef.contains("YL-OPT"))
    }

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
            val allExpectedBlocks = txBlockPattern.findAll(expectedJson).map { it.value }.toList()

            fun extractValue(jsonBlock: String, key: String): String {
                val regex = Regex(""""$key"\s*:\s*(?:"([^"]*)"|([^,\n}]*))""")
                val match = regex.find(jsonBlock) ?: return ""
                return (match.groupValues[1].takeIf { it.isNotEmpty() } ?: match.groupValues[2]).trim()
            }

            val legacyTypes = setOf(TradeType.BUY.name, TradeType.SELL.name, TradeType.INTEREST.name)
            val expectedBlocks = allExpectedBlocks.filter { extractValue(it, "tradeType") in legacyTypes }
            val actualLegacyTrades = allParsedTrades.filter {
                it.tradeType.name in legacyTypes && it.assetType != "OPTION"
            }

            assertEquals(
                "Parsed legacy trade count should match golden standard",
                expectedBlocks.size,
                actualLegacyTrades.size
            )

            // Compare each trade
            for (idx in 0 until expectedBlocks.size) {
                val expectedBlock = expectedBlocks[idx]
                val actual = actualLegacyTrades[idx]

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
            println("\n*** All ${actualLegacyTrades.size} legacy records match the golden standard! ***")
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
