package com.recoder.stockledger.data.importer

import android.util.Log
import com.recoder.stockledger.data.ImportSourceChannel
import com.recoder.stockledger.data.Market
import com.recoder.stockledger.data.TradeType
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.InputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object LongBridgeStatementPdfParser {
    private const val TAG = "LongBridgePdfParser"

    fun parsePdf(inputStream: InputStream, password: String, cacheDir: File? = null): List<ParsedStatementTrade> {
        Log.d(TAG, "开始解析PDF文件, password长度=${password.length}")
        return try {
            val document = PDDocument.load(inputStream, password)
            Log.d(TAG, "PDF文档加载成功, 页数=${document.numberOfPages}")
            document.use { doc ->
                val stripper = PDFTextStripper().apply {
                    sortByPosition = true
                }
                val rawText = stripper.getText(doc)
                Log.d(TAG, "PDF文本提取成功, 文本长度=${rawText.length}")
                
                // Keep the first 30 lines for diagnostic preview
                val previewLines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }.take(30)
                Log.d(TAG, "PDF文本前30行预览:\n${previewLines.joinToString("\n")}")
                
                if (cacheDir != null) {
                    try {
                        val ts = System.currentTimeMillis()
                        val debugFile = File(cacheDir, "longbridge_pdf_extract_$ts.txt")
                        debugFile.writeText(rawText, Charsets.UTF_8)
                        Log.d(TAG, "PDF提取文本已保存到: ${debugFile.absolutePath}")
                    } catch (e: Exception) {
                        Log.w(TAG, "保存诊断文件失败: ${e.message}")
                    }
                }
                
                val trades = parseText(rawText)
                Log.d(TAG, "解析完成, 找到${trades.size}条交易记录")
                trades
            }
        } catch (e: Exception) {
            Log.e(TAG, "PDF解析失败: ${e.message}", e)
            emptyList()
        }
    }

    fun parseText(text: String): List<ParsedStatementTrade> {
        val normalized = normalizeCjkCompatChars(text)
        val lines = normalized.lines().map { it.trim() }.filter { it.isNotBlank() }
        val trades = mutableListOf<ParsedStatementTrade>()

        var currentMarket = Market.US
        var currentCurrency = "USD"

        val orderIdRegex = Regex("""^OS\d+$""")
        val numericRegex = Regex("""^-?[\d,]+\.?\d*$""")
        val optionRegex = Regex("""([A-Za-z]+)(\d{6})([CP])(\d+).*""")

        var i = 0
        while (i < lines.size) {
            val line = lines[i]

            // 1. Detect market/currency section headers
            if (line.contains("市场: 港市场") || line.contains("市场: ⾹港市场") || line.contains("市场: 香港市场")) {
                currentMarket = Market.HK
                currentCurrency = "HKD"
                i++
                continue
            }
            if (line.contains("市场: 美国市场")) {
                currentMarket = Market.US
                currentCurrency = "USD"
                i++
                continue
            }

            // 2. Try parsing single-line trade record first
            val parts = line.split("\\s+".toRegex())
            val osIdx = parts.indexOfFirst { it.matches(Regex("""^OS\d+$""")) }
            if (osIdx >= 2 && parts.size >= osIdx + 6) {
                val tradeDateStr = parts[osIdx - 2]
                val settDateStr = parts[osIdx - 1]
                val datePattern = Regex("""^\d{4}\.\d{2}\.\d{2}$""")
                if (tradeDateStr.matches(datePattern) && settDateStr.matches(datePattern)) {
                    val n = parts.size
                    val qty = parts[n - 4].replace(",", "").toDoubleOrNull()
                    val price = parts[n - 3].replace(",", "").toDoubleOrNull()
                    val amount = parts[n - 2].replace(",", "").toDoubleOrNull()
                    val netChange = parts[n - 1].replace(",", "").toDoubleOrNull()
                    
                    if (qty != null && price != null && amount != null && netChange != null) {
                        val orderId = parts[osIdx]
                        val sideStr = parts[osIdx + 1]
                        val tradeType = if (sideStr.contains("买") || sideStr.contains("买入") || sideStr.contains("买⼊")) {
                            TradeType.BUY
                        } else {
                            TradeType.SELL
                        }
                        
                        val tradeDate = LocalDate.parse(tradeDateStr, DateTimeFormatter.ofPattern("yyyy.MM.dd"))
                        val joinedName = parts.subList(osIdx + 2, n - 4).joinToString(" ").trim()
                        val optionMatch = optionRegex.matchEntire(joinedName.replace(" ", ""))
                        
                        val (tradeMarket, tradeCurrency, parsedTradeTime) = scanTimeAndTz(
                            lines, i + 1, currentMarket, currentCurrency
                        )

                        val symbol: String
                        val name: String
                        val assetType: String
                        val underlyingSymbol: String?
                        val expiryDate: String?
                        val strikePrice: Double?
                        val optionType: String?

                        if (optionMatch != null) {
                            val underlying = optionMatch.groupValues[1].uppercase()
                            val expiryCompact = optionMatch.groupValues[2]
                            val typeChar = optionMatch.groupValues[3]
                            val strikeRaw = optionMatch.groupValues[4].toDouble()
                            val strike = strikeRaw / 1000.0
                            val optType = if (typeChar == "C") "CALL" else "PUT"
                            
                            val year = 2000 + expiryCompact.substring(0, 2).toInt()
                            val month = expiryCompact.substring(2, 4).toInt()
                            val day = expiryCompact.substring(4, 6).toInt()
                            val expiryStr = String.format("%04d-%02d-%02d", year, month, day)

                            val strikeLabel = if (strike == strike.toLong().toDouble()) strike.toLong().toString() else strike.toString()
                            symbol = "$underlying $expiryCompact$typeChar$strikeLabel"
                            name = "$underlying $expiryStr ${if (optType == "CALL") "Call" else "Put"} @ $strikeLabel"
                            assetType = "OPTION"
                            underlyingSymbol = underlying
                            expiryDate = expiryStr
                            strikePrice = strike
                            optionType = optType
                        } else {
                            val cleanedJoinedName = joinedName.replace("\"", "").trim()
                            val nameParts = cleanedJoinedName.split("\\s+".toRegex(), limit = 2)
                            val firstPart = nameParts.getOrNull(0) ?: ""
                            
                            val stockCode: String
                            val stockName: String
                            if (firstPart.all { it.isDigit() }) {
                                stockCode = firstPart
                                stockName = nameParts.getOrNull(1) ?: firstPart
                            } else {
                                stockCode = ""
                                stockName = cleanedJoinedName
                            }
                            
                            symbol = if (tradeMarket == Market.HK) {
                                val digits = stockCode.filter { it.isDigit() }
                                when {
                                    digits.length == 5 -> "$digits.HK"
                                    digits.length in 1..4 -> digits.padStart(4, '0') + ".HK"
                                    digits.length > 5 -> "$digits.HK"
                                    else -> stockCode
                                }
                            } else {
                                stockCode.uppercase()
                            }
                            name = stockName
                            assetType = "STOCK"
                            underlyingSymbol = null
                            expiryDate = null
                            strikePrice = null
                            optionType = null
                        }

                        val trade = ParsedStatementTrade(
                            sourceChannel = ImportSourceChannel.PDF_STATEMENT,
                            tradeType = tradeType,
                            market = tradeMarket,
                            symbol = symbol,
                            name = name,
                            currencyCode = tradeCurrency,
                            price = price,
                            quantity = qty,
                            amount = amount,
                            tradeDate = tradeDate,
                            tradeTime = parsedTradeTime,
                            tradeRef = orderId,
                            rawLine = line,
                            commission = 0.0,
                            tax = 0.0,
                            platformFee = 0.0,
                            assetType = assetType,
                            underlyingSymbol = underlyingSymbol,
                            expiryDate = expiryDate,
                            strikePrice = strikePrice,
                            optionType = optionType
                        )
                        trades.add(trade)
                        i++
                        continue
                    }
                }
            }

            // 3. Fallback: Detect multi-line order transaction block starting with Order ID
            if (line.matches(orderIdRegex)) {
                val orderId = line
                if (i >= 2) {
                    val tradeDateStr = lines[i - 2]
                    val settDateStr = lines[i - 1]
                    val datePattern = Regex("""^\d{4}\.\d{2}\.\d{2}$""")
                    if (tradeDateStr.matches(datePattern) && settDateStr.matches(datePattern)) {
                        val tradeDate = LocalDate.parse(tradeDateStr, DateTimeFormatter.ofPattern("yyyy.MM.dd"))
                        
                        val sideStr = lines.getOrNull(i + 1) ?: ""
                        val tradeType = if (sideStr.contains("买") || sideStr.contains("买入") || sideStr.contains("买阻") || sideStr.contains("买⼊")) {
                            TradeType.BUY
                        } else {
                            TradeType.SELL
                        }

                        // Collect name lines and subsequent numbers
                        val nameLines = mutableListOf<String>()
                        var k = i + 2
                        var qty = 0.0
                        var price = 0.0
                        var amount = 0.0
                        var netChange = 0.0
                        var foundNumbers = false

                        while (k < lines.size) {
                            val nextLine = lines[k]
                            // Skip any header/footer pages info if it gets mixed in
                            if (nextLine.contains("季福乐") || nextLine.contains("Page ") || nextLine.contains("综合账") || nextLine.contains("PAGE") || nextLine.contains("交易日期") || nextLine.contains("变动金额")) {
                                k++
                                continue
                            }

                            if (nextLine.matches(numericRegex)) {
                                val q = nextLine.replace(",", "").toDoubleOrNull()
                                val p = lines.getOrNull(k + 1)?.replace(",", "")?.toDoubleOrNull()
                                val a = lines.getOrNull(k + 2)?.replace(",", "")?.toDoubleOrNull()
                                val n = lines.getOrNull(k + 3)?.replace(",", "")?.toDoubleOrNull()
                                if (q != null && p != null && a != null && n != null) {
                                    qty = q
                                    price = p
                                    amount = a
                                    netChange = n
                                    foundNumbers = true
                                    k += 4
                                }
                                break
                            } else {
                                nameLines.add(nextLine)
                                k++
                            }
                        }

                        val (tradeMarket, tradeCurrency, parsedTradeTime) = scanTimeAndTz(
                            lines, k, currentMarket, currentCurrency
                        )

                        if (foundNumbers && nameLines.isNotEmpty()) {
                            val joinedName = nameLines.joinToString(" ").trim()
                            val optionMatch = optionRegex.matchEntire(joinedName.replace(" ", ""))
                            
                            val symbol: String
                            val name: String
                            val assetType: String
                            val underlyingSymbol: String?
                            val expiryDate: String?
                            val strikePrice: Double?
                            val optionType: String?

                            if (optionMatch != null) {
                                val underlying = optionMatch.groupValues[1].uppercase()
                                val expiryCompact = optionMatch.groupValues[2]
                                val typeChar = optionMatch.groupValues[3]
                                val strikeRaw = optionMatch.groupValues[4].toDouble()
                                val strike = strikeRaw / 1000.0
                                val optType = if (typeChar == "C") "CALL" else "PUT"
                                
                                val year = 2000 + expiryCompact.substring(0, 2).toInt()
                                val month = expiryCompact.substring(2, 4).toInt()
                                val day = expiryCompact.substring(4, 6).toInt()
                                val expiryStr = String.format("%04d-%02d-%02d", year, month, day)

                                val strikeLabel = if (strike == strike.toLong().toDouble()) strike.toLong().toString() else strike.toString()
                                symbol = "$underlying $expiryCompact$typeChar$strikeLabel"
                                name = "$underlying $expiryStr ${if (optType == "CALL") "Call" else "Put"} @ $strikeLabel"
                                assetType = "OPTION"
                                underlyingSymbol = underlying
                                expiryDate = expiryStr
                                strikePrice = strike
                                optionType = optType
                            } else {
                                val cleanedJoinedName = joinedName.replace("\"", "").trim()
                                val partsName = cleanedJoinedName.split("\\s+".toRegex(), limit = 2)
                                val firstPart = partsName.getOrNull(0) ?: ""
                                
                                val stockCode: String
                                val stockName: String
                                if (firstPart.all { it.isDigit() }) {
                                    stockCode = firstPart
                                    stockName = partsName.getOrNull(1) ?: firstPart
                                } else {
                                    stockCode = ""
                                    stockName = cleanedJoinedName
                                }
                                
                                symbol = if (tradeMarket == Market.HK) {
                                    val digits = stockCode.filter { it.isDigit() }
                                    when {
                                        digits.length == 5 -> "$digits.HK"
                                        digits.length in 1..4 -> digits.padStart(4, '0') + ".HK"
                                        digits.length > 5 -> "$digits.HK"
                                        else -> stockCode
                                    }
                                } else {
                                    stockCode.uppercase()
                                }
                                name = stockName
                                assetType = "STOCK"
                                underlyingSymbol = null
                                expiryDate = null
                                strikePrice = null
                                optionType = null
                            }

                            val trade = ParsedStatementTrade(
                                sourceChannel = ImportSourceChannel.PDF_STATEMENT,
                                tradeType = tradeType,
                                market = tradeMarket,
                                symbol = symbol,
                                name = name,
                                currencyCode = tradeCurrency,
                                price = price,
                                quantity = qty,
                                amount = amount,
                                tradeDate = tradeDate,
                                tradeTime = parsedTradeTime,
                                tradeRef = orderId,
                                rawLine = lines.subList(maxOf(0, i - 2), minOf(lines.size, k)).joinToString(" | "),
                                commission = 0.0,
                                tax = 0.0,
                                platformFee = 0.0,
                                assetType = assetType,
                                underlyingSymbol = underlyingSymbol,
                                expiryDate = expiryDate,
                                strikePrice = strikePrice,
                                optionType = optionType
                            )
                            trades.add(trade)
                            i = k - 1
                        }
                    }
                }
            }

            // 4. Process fee keywords and assign them to the last parsed trade
            val feeParts = line.split("\\s+".toRegex())
            if (feeParts.isNotEmpty() && trades.isNotEmpty()) {
                val keyword = feeParts[0]
                var feeVal: Double? = null
                var linesConsumed = 1

                if (feeParts.size >= 2) {
                    feeVal = feeParts.last().replace(",", "").toDoubleOrNull()
                } else if (i + 1 < lines.size) {
                    val nextLine = lines[i + 1]
                    val nextLineParts = nextLine.split("\\s+".toRegex())
                    if (nextLineParts.isNotEmpty()) {
                        feeVal = nextLineParts.last().replace(",", "").toDoubleOrNull()
                        if (feeVal != null) {
                            linesConsumed = 2
                        }
                    }
                }

                if (feeVal != null) {
                    val lastTrade = trades.last()
                    val isUpdated = when (keyword) {
                        "佣⾦", "佣金" -> {
                            trades[trades.size - 1] = lastTrade.copy(commission = feeVal)
                            true
                        }
                        "平台费" -> {
                            trades[trades.size - 1] = lastTrade.copy(commission = (lastTrade.commission ?: 0.0) + feeVal)
                            true
                        }
                        "印花税", "证券交易委员会费" -> {
                            trades[trades.size - 1] = lastTrade.copy(tax = (lastTrade.tax ?: 0.0) + feeVal)
                            true
                        }
                        "交收费", "交易征费", "交易费", "会财局交易征费",
                        "综合审计跟踪费⽤", "综合审计跟踪费用", "期权清算费",
                        "交易活动收费", "期权监管费", "期权交收费",
                        "其他交易费⽤", "其他交易费用" -> {
                            trades[trades.size - 1] = lastTrade.copy(tax = (lastTrade.tax ?: 0.0) + feeVal)
                            true
                        }
                        else -> false
                    }
                    if (isUpdated) {
                        i += linesConsumed
                        continue
                    }
                }
            }

            i++
        }

        return trades
    }

    private fun scanTimeAndTz(
        lines: List<String>,
        startIndex: Int,
        currentMarket: Market,
        currentCurrency: String
    ): Triple<Market, String, String?> {
        val timeRegex = Regex("""(\d{2}:\d{2}:\d{2})\s*"?\s*(HKT|EST|EDT)""")
        for (idx in startIndex until minOf(lines.size, startIndex + 15)) {
            val scanLine = lines[idx]
            if (scanLine.startsWith("OS") && idx > startIndex) break
            val match = timeRegex.find(scanLine)
            if (match != null) {
                val tz = match.groupValues[2]
                val market = if (tz == "HKT") Market.HK else Market.US
                val currency = if (tz == "HKT") "HKD" else "USD"
                val tradeTime = match.groupValues[1].substring(0, 5)
                return Triple(market, currency, tradeTime)
            }
        }
        return Triple(currentMarket, currentCurrency, null)
    }

    private fun normalizeCjkCompatChars(text: String): String {
        var result = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC)
        result = result
            .replace('\u2EA0', '\u6C11') // ⺠ -> 民
            .replace('\u2EC5', '\u89C1') // ⻅ -> 见
            .replace('\u2EE9', '\u9EC4') // ⻩ -> 黄
            .replace('\u2EF0', '\u9F99') // ⻰ -> 龙
            .replace('\u6236', '\u6237') // 戶 -> 户
        return result
    }
}
