package com.recoder.stockledger.data.importer

import android.util.Log
import com.recoder.stockledger.data.ImportSourceChannel
import com.recoder.stockledger.data.Market
import com.recoder.stockledger.data.TradeType
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream
import java.time.LocalDate
import java.time.ZoneId

object HsbcStatementPdfParser {
    private const val TAG = "HsbcStmtPdfParser"

    private val monthMap = mapOf(
        "JAN" to 1, "FEB" to 2, "MAR" to 3, "APR" to 4,
        "MAY" to 5, "JUN" to 6, "JUL" to 7, "AUG" to 8,
        "SEP" to 9, "OCT" to 10, "NOV" to 11, "DEC" to 12
    )

    fun parseHsbcDate(str: String): LocalDate? {
        val regex = Regex("""^(\d{1,2})([A-Z]{3})(\d{4})$""")
        val match = regex.find(str.uppercase()) ?: return null
        val day = match.groupValues[1].toInt()
        val monthStr = match.groupValues[2]
        val year = match.groupValues[3].toInt()
        val month = monthMap[monthStr] ?: return null
        return LocalDate.of(year, month, day)
    }

    fun parse(inputStream: InputStream, password: String? = null): List<ParsedStatementTrade> {
        val rawText = extractText(inputStream, password)
        return parseText(rawText)
    }

    fun parseText(rawText: String): List<ParsedStatementTrade> {
        val lines = rawText.lines().map { it.trim() }
        val refFeeMap = mutableMapOf<String, Double>()

        // 1. Scan for charges and map reference to fee
        for (idx in 0 until lines.size - 1) {
            val line = lines[idx]
            if (line.contains("OUR REFERENCE:")) {
                val refMatch = Regex("""OUR REFERENCE:\s*(\S+)""").find(line)
                if (refMatch != null) {
                    val ref = refMatch.groupValues[1].trim()
                    val nextLine = lines[idx + 1]
                    val feeMatch = Regex("""XACT CHARGE\s+[A-Z]{3}\s+([\d,.]+)""", RegexOption.IGNORE_CASE).find(nextLine)
                    if (feeMatch != null) {
                        val fee = feeMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: 0.0
                        refFeeMap[ref] = fee
                    }
                }
            }
        }

        // 2. Scan for transactions and custody fees
        val trades = mutableListOf<ParsedStatementTrade>()
        var currentMarket = Market.HK
        var currentStockTicker = ""
        var currentStockName = ""
        var inTransactionSummary = false

        // Regex definitions
        // Stock Header: Ticker and description ending with (SHS) or (WTS) or similar, e.g. "07709 XL2CSOPHYNIX  (SHS)" or "NVDA NVIDIA CORP  (SHS)"
        val stockHeaderPattern = Regex("""^([A-Z0-9.\-]+)\s+(.*?)\s*\((SHS|WTS)\)\s*$""", RegexOption.IGNORE_CASE)

        // Trade line pattern, e.g. "16DEC2025 18DEC2025 HKD 11.2100 100 HKD 1,121.09" or "06MAR2026 TBC USD 345.00000 1- USD 345.00"
        val tradeLinePattern = Regex("""^(\d{1,2}[A-Z]{3}\d{4})\s+(\S+)\s+([A-Z]{3})\s+(\d[\d,.]*)\s+([\d,]+-?)\s+([A-Z]{3})\s+([\d,.]+)$""", RegexOption.IGNORE_CASE)

        // Reference pattern, e.g. "Reference: PURMGK726806001 Type: PUR"
        val refPattern = Regex("""^Reference:\s*(\S+)\s+Type:\s*(\S+)""", RegexOption.IGNORE_CASE)

        var pendingTradeLine: MatchResult? = null
        var pendingRawLines = mutableListOf<String>()

        for (idx in lines.indices) {
            val line = lines[idx]
            if (line.isEmpty()) continue

            // Detect section headers
            if (line.contains("Transaction summary", ignoreCase = true)) {
                inTransactionSummary = true
                continue
            }
            if (line.contains("Charges and income summary", ignoreCase = true) || line.contains("Portfolio details", ignoreCase = true)) {
                inTransactionSummary = false
            }

            // Parse SAFE CUSTODY CHARGE if we see it (independent of transaction summary section check, as it resides in charges section)
            val custodyMatch = Regex("""^(\d{1,2}[A-Z]{3}\d{4})\s+SAFE CUSTODY CHARGE""", RegexOption.IGNORE_CASE).find(line)
            if (custodyMatch != null && idx + 2 < lines.size) {
                val dateStr = custodyMatch.groupValues[1]
                val refLine = lines[idx + 1]
                val refMatch = Regex("""^OUR REFERENCE:\s*(\S+)""", RegexOption.IGNORE_CASE).find(refLine)
                if (refMatch != null) {
                    val ref = refMatch.groupValues[1].trim()
                    val amtLine = lines[idx + 2]
                    val amtMatch = Regex("""^SAFE CUSTODY\s+([A-Z]{3})\s+([\d,.]+)""", RegexOption.IGNORE_CASE).find(amtLine)
                    if (amtMatch != null) {
                        val curr = amtMatch.groupValues[1]
                        val amount = amtMatch.groupValues[2].replace(",", "").toDoubleOrNull() ?: 0.0
                        val date = parseHsbcDate(dateStr)
                        if (date != null) {
                            val rawText = "$line | $refLine | $amtLine"
                            trades.add(
                                ParsedStatementTrade(
                                    sourceChannel = ImportSourceChannel.PDF_STATEMENT,
                                    tradeType = TradeType.WITHDRAW,
                                    market = Market.HK,
                                    symbol = "CUSTODY",
                                    name = "代收保管费",
                                    currencyCode = curr,
                                    price = amount,
                                    quantity = 1.0,
                                    amount = amount,
                                    tradeDate = date,
                                    tradeTime = getTradeTimeForMarket(Market.HK),
                                    commission = 0.0,
                                    platformFee = 0.0,
                                    tax = 0.0,
                                    tradeRef = ref,
                                    rawLine = rawText,
                                    createdAt = date.atTime(9, 35).atZone(ZoneId.of("UTC+8")).toInstant().toEpochMilli()
                                )
                            )
                        }
                    }
                }
            }

            if (!inTransactionSummary) continue

            // Detect market change
            if (line.contains("LOCAL SHARES", ignoreCase = true)) {
                currentMarket = Market.HK
                continue
            }
            if (line.contains("FOREIGN SHARES", ignoreCase = true)) {
                currentMarket = Market.US
                continue
            }

            // Check if page noise line, skip it
            if (line.contains("Page no :", ignoreCase = true) || line.contains("A/C no :", ignoreCase = true) || line.contains("Period :", ignoreCase = true)) {
                continue
            }
            if (line.contains("Securities Securities description", ignoreCase = true) || line.contains("/Settlement date", ignoreCase = true)) {
                continue
            }
            if (line.contains("Thank you for choosing HSBC", ignoreCase = true) || line.contains("Date :", ignoreCase = true)) {
                continue
            }

            // Match stock header
            val stockMatch = stockHeaderPattern.matchEntire(line)
            if (stockMatch != null) {
                currentStockTicker = stockMatch.groupValues[1].trim()
                currentStockName = stockMatch.groupValues[2].trim()
                pendingTradeLine = null
                pendingRawLines.clear()
                continue
            }

            // Match trade line
            val tradeMatch = tradeLinePattern.matchEntire(line)
            if (tradeMatch != null) {
                pendingTradeLine = tradeMatch
                pendingRawLines.clear()
                pendingRawLines.add(line)
                continue
            }

            // Match reference line
            val refMatch = refPattern.find(line)
            if (refMatch != null && pendingTradeLine != null) {
                pendingRawLines.add(line)
                val tradeResult = pendingTradeLine!!
                val tradeDateStr = tradeResult.groupValues[1]
                val currency = tradeResult.groupValues[3]
                val priceVal = tradeResult.groupValues[4].replace(",", "").toDoubleOrNull() ?: 0.0
                val qtyStr = tradeResult.groupValues[5]
                val amountVal = tradeResult.groupValues[7].replace(",", "").toDoubleOrNull() ?: 0.0

                val ref = refMatch.groupValues[1].trim()
                val typeStr = refMatch.groupValues[2].trim().uppercase()

                val tradeType = if (typeStr == "SAL" || qtyStr.endsWith("-")) TradeType.SELL else TradeType.BUY
                val quantity = qtyStr.removeSuffix("-").replace(",", "").toDoubleOrNull() ?: 0.0
                val tradeDate = parseHsbcDate(tradeDateStr)

                if (tradeDate != null && currentStockTicker.isNotEmpty()) {
                    val resolvedSymbol = resolveSymbol(currentStockTicker, currentMarket)
                    val fee = refFeeMap[ref] ?: 0.0
                    val hour = if (currentMarket == Market.US) 21 else 9

                    trades.add(
                        ParsedStatementTrade(
                            sourceChannel = ImportSourceChannel.PDF_STATEMENT,
                            tradeType = tradeType,
                            market = currentMarket,
                            symbol = resolvedSymbol,
                            name = currentStockName,
                            currencyCode = currency,
                            price = priceVal,
                            quantity = quantity,
                            amount = amountVal,
                            tradeDate = tradeDate,
                            tradeTime = getTradeTimeForMarket(currentMarket),
                            commission = fee,
                            platformFee = 0.0,
                            tax = 0.0,
                            tradeRef = ref,
                            rawLine = pendingRawLines.joinToString(" | "),
                            createdAt = tradeDate.atTime(hour, 35).atZone(ZoneId.of("UTC+8")).toInstant().toEpochMilli()
                        )
                    )
                }

                pendingTradeLine = null
                pendingRawLines.clear()
            }
        }

        return trades
    }

    private fun getTradeTimeForMarket(market: Market): String = when (market) {
        Market.US -> "21:35"
        else -> "09:35"
    }

    private fun resolveSymbol(raw: String, market: Market): String = when (market) {
        Market.HK -> {
            val digits = raw.filter(Char::isDigit)
            when {
                digits.length == 5 -> "$digits.HK"
                digits.length in 1..4 -> "${digits.padStart(4, '0')}.HK"
                digits.length > 5 -> "$digits.HK"
                else -> raw
            }
        }
        else -> raw.uppercase()
    }

    private fun extractText(inputStream: InputStream, password: String?): String {
        return try {
            val bytes = inputStream.readBytes()
            val doc = try {
                PDDocument.load(java.io.ByteArrayInputStream(bytes))
            } catch (e: Exception) {
                if (!password.isNullOrBlank()) {
                    PDDocument.load(java.io.ByteArrayInputStream(bytes), password).apply {
                        if (isEncrypted) setAllSecurityToBeRemoved(true)
                    }
                } else {
                    throw e
                }
            }
            doc.use { document ->
                val stripper = PDFTextStripper().apply {
                    sortByPosition = true
                }
                stripper.getText(document)
            }
        } catch (e: Exception) {
            Log.e(TAG, "HSBC PDF extraction failed", e)
            throw e
        }
    }
}
