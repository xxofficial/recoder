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
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class LongBridgeScanResult(
    val market: Market,
    val currency: String,
    val adjustedDate: LocalDate,
    val adjustedTime: String?
)

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
                        
                        val scanResult = scanTimeAndTz(
                            lines, i + 1, currentMarket, currentCurrency, tradeDate
                        )
                        val tradeMarket = scanResult.market
                        val tradeCurrency = scanResult.currency
                        val adjustedTradeDate = scanResult.adjustedDate
                        val parsedTradeTime = scanResult.adjustedTime

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
                            val isUsTicker = tradeMarket == Market.US && firstPart.matches(Regex("""^[A-Za-z0-9\.\-]+$""")) && firstPart.isNotEmpty()
                            val isHkTicker = tradeMarket == Market.HK && firstPart.all { it.isDigit() } && firstPart.isNotEmpty()
                            
                            if (isUsTicker || isHkTicker) {
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
                            tradeDate = adjustedTradeDate,
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

                        val scanResult = scanTimeAndTz(
                            lines, k, currentMarket, currentCurrency, tradeDate
                        )
                        val tradeMarket = scanResult.market
                        val tradeCurrency = scanResult.currency
                        val adjustedTradeDate = scanResult.adjustedDate
                        val parsedTradeTime = scanResult.adjustedTime

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
                                val isUsTicker = tradeMarket == Market.US && firstPart.matches(Regex("""^[A-Za-z0-9\.\-]+$""")) && firstPart.isNotEmpty()
                                val isHkTicker = tradeMarket == Market.HK && firstPart.all { it.isDigit() } && firstPart.isNotEmpty()
                                
                                if (isUsTicker || isHkTicker) {
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
                                tradeDate = adjustedTradeDate,
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

        // 4.5 Parse Other Cash Movements (其他资金出入明细)
        val otherCashHeaderIdx = lines.indexOfFirst {
            it.contains("其他资金出") || it.contains("其他资金出入") || it.contains("其他资金出⼊") ||
            it.contains("其他資金出") || it.contains("其他資金出入") || it.contains("其他資金出⼊")
        }
        val otherPositionsHeaderIdx = lines.indexOfFirst {
            it.contains("其他持仓出") || it.contains("其他持倉出")
        }

        if (otherCashHeaderIdx != -1) {
            var idx = otherCashHeaderIdx + 1
            val limit = if (otherPositionsHeaderIdx != -1) otherPositionsHeaderIdx else lines.size
            var currentOtherMarket = Market.US
            var currentOtherCurrency = "USD"
            
            while (idx < limit) {
                val line = lines[idx]
                if (line.contains("责任说明") || line.contains("说明") || line.contains("Page") || line.contains("综合账")) {
                    break
                }
                
                if (line.contains("市场: 港市场") || line.contains("市场: ⾹港市场") || line.contains("市场: 香港市场")) {
                    currentOtherMarket = Market.HK
                    currentOtherCurrency = "HKD"
                    idx++
                    continue
                }
                if (line.contains("市场: 美国市场")) {
                    currentOtherMarket = Market.US
                    currentOtherCurrency = "USD"
                    idx++
                    continue
                }
                
                val dateMatch = Regex("""^(\d{4})\.(\d{2})\.(\d{2})$""").matchEntire(line)
                if (dateMatch != null && idx + 1 < limit) {
                    val dateStr = line
                    val tradeDate = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy.MM.dd"))
                    val typeStr = lines[idx + 1]
                    
                    var scan = idx + 2
                    val remarks = mutableListOf<String>()
                    var amount: Double? = null
                    
                    while (scan < limit) {
                        val sLine = lines[scan]
                        if (Regex("""^\d{4}\.\d{2}\.\d{2}$""").matches(sLine) || sLine.contains("币种") || sLine.contains("汇总") || sLine.contains("市场:")) {
                            break
                        }
                        val cleaned = sLine.replace(",", "")
                        val numericMatch = Regex("""^-?\d+\.\d+$""").matchEntire(cleaned)
                        if (numericMatch != null) {
                            amount = cleaned.toDoubleOrNull()
                            scan++
                            break
                        } else {
                            remarks.add(sLine)
                            scan++
                        }
                    }
                    
                    if (amount != null) {
                        val remarkText = remarks.joinToString(" ")
                        val tradeRef = "${dateStr.replace(".", "")}-${typeStr.replace(" ", "")}-${kotlin.math.abs(amount)}"
                        
                        when {
                            typeStr.contains("现金分红") || typeStr.contains("代收股息") -> {
                                var symbol = "CASH"
                                val usTickerMatch = Regex("""\b([A-Z]+)\.US\b""").find(remarkText)
                                val usParenMatch = Regex("""\b([A-Z]+)\(US[A-Z0-9]+\)""").find(remarkText)
                                if (usTickerMatch != null) {
                                    symbol = usTickerMatch.groupValues[1]
                                } else if (usParenMatch != null) {
                                    symbol = usParenMatch.groupValues[1]
                                } else {
                                    val firstWordMatch = Regex("""^[A-Z0-9]+""").find(remarkText)
                                    if (firstWordMatch != null) {
                                        symbol = firstWordMatch.value
                                    }
                                }
                                
                                trades.add(ParsedStatementTrade(
                                    sourceChannel = ImportSourceChannel.PDF_STATEMENT,
                                    tradeType = TradeType.DIVIDEND,
                                    market = currentOtherMarket,
                                    symbol = symbol,
                                    name = if (symbol != "CASH") symbol else "现金分红",
                                    currencyCode = currentOtherCurrency,
                                    price = amount,
                                    quantity = 1.0,
                                    amount = amount,
                                    tradeDate = tradeDate,
                                    tradeTime = "09:00",
                                    tradeRef = "DIV-$tradeRef",
                                    rawLine = "$line | $typeStr | $remarkText",
                                    commission = 0.0,
                                    tax = 0.0,
                                    platformFee = 0.0,
                                    assetType = "STOCK"
                                ))
                            }
                            
                            typeStr.contains("融资利息") || typeStr.contains("融券利息") -> {
                                trades.add(ParsedStatementTrade(
                                    sourceChannel = ImportSourceChannel.PDF_STATEMENT,
                                    tradeType = TradeType.INTEREST,
                                    market = Market.CASH,
                                    symbol = "INTEREST",
                                    name = typeStr,
                                    currencyCode = currentOtherCurrency,
                                    price = kotlin.math.abs(amount),
                                    quantity = 1.0,
                                    amount = kotlin.math.abs(amount),
                                    tradeDate = tradeDate,
                                    tradeTime = "23:59",
                                    tradeRef = "INT-$tradeRef",
                                    rawLine = "$line | $typeStr | $remarkText",
                                    commission = 0.0,
                                    tax = 0.0,
                                    platformFee = 0.0,
                                    assetType = "STOCK"
                                ))
                            }
                            
                            typeStr.contains("活动礼包") || typeStr.contains("现金奖励") -> {
                                trades.add(ParsedStatementTrade(
                                    sourceChannel = ImportSourceChannel.PDF_STATEMENT,
                                    tradeType = TradeType.DIVIDEND,
                                    market = Market.CASH,
                                    symbol = "CASH",
                                    name = "活动礼包",
                                    currencyCode = currentOtherCurrency,
                                    price = amount,
                                    quantity = 1.0,
                                    amount = amount,
                                    tradeDate = tradeDate,
                                    tradeTime = "09:00",
                                    tradeRef = "GIFT-$tradeRef",
                                    rawLine = "$line | $typeStr | $remarkText",
                                    commission = 0.0,
                                    tax = 0.0,
                                    platformFee = 0.0,
                                    assetType = "STOCK"
                                ))
                            }
                            
                            typeStr.contains("公司行动其他费用") || typeStr.contains("扣收") || typeStr.contains("税") || typeStr.contains("Withholding Tax") || typeStr.contains("Dividend Fee") -> {
                                trades.add(ParsedStatementTrade(
                                    sourceChannel = ImportSourceChannel.PDF_STATEMENT,
                                    tradeType = TradeType.TAX,
                                    market = Market.CASH,
                                    symbol = "CASH",
                                    name = if (remarkText.isNotBlank()) remarkText else typeStr,
                                    currencyCode = currentOtherCurrency,
                                    price = kotlin.math.abs(amount),
                                    quantity = 1.0,
                                    amount = kotlin.math.abs(amount),
                                    tradeDate = tradeDate,
                                    tradeTime = "09:00",
                                    tradeRef = "TAX-$tradeRef",
                                    rawLine = "$line | $typeStr | $remarkText",
                                    commission = 0.0,
                                    tax = 0.0,
                                    platformFee = 0.0,
                                    assetType = "STOCK"
                                ))
                            }
                            
                            typeStr.contains("存入资金") || typeStr.contains("入金") || typeStr.contains("货币兑换入账") -> {
                                trades.add(ParsedStatementTrade(
                                    sourceChannel = ImportSourceChannel.PDF_STATEMENT,
                                    tradeType = TradeType.DEPOSIT,
                                    market = Market.CASH,
                                    symbol = "CASH",
                                    name = typeStr,
                                    currencyCode = currentOtherCurrency,
                                    price = kotlin.math.abs(amount),
                                    quantity = 1.0,
                                    amount = kotlin.math.abs(amount),
                                    tradeDate = tradeDate,
                                    tradeTime = "09:00",
                                    tradeRef = "DEP-$tradeRef",
                                    rawLine = "$line | $typeStr | $remarkText",
                                    commission = 0.0,
                                    tax = 0.0,
                                    platformFee = 0.0,
                                    assetType = "STOCK"
                                ))
                            }
                            
                            typeStr.contains("提取资金") || typeStr.contains("出金") || typeStr.contains("货币兑换出账") -> {
                                trades.add(ParsedStatementTrade(
                                    sourceChannel = ImportSourceChannel.PDF_STATEMENT,
                                    tradeType = TradeType.WITHDRAW,
                                    market = Market.CASH,
                                    symbol = "CASH",
                                    name = typeStr,
                                    currencyCode = currentOtherCurrency,
                                    price = kotlin.math.abs(amount),
                                    quantity = 1.0,
                                    amount = kotlin.math.abs(amount),
                                    tradeDate = tradeDate,
                                    tradeTime = "09:00",
                                    tradeRef = "WTH-$tradeRef",
                                    rawLine = "$line | $typeStr | $remarkText",
                                    commission = 0.0,
                                    tax = 0.0,
                                    platformFee = 0.0,
                                    assetType = "STOCK"
                                ))
                            }
                        }
                        
                        idx = scan - 1
                    }
                }
                idx++
            }
        }

        // 5. Parse Other Position In/Out Details (其他持仓出⼊明细) for IPO Allotment, Option Expiration, and Split
        if (otherPositionsHeaderIdx != -1) {
            var idx = otherPositionsHeaderIdx + 1
            var currentOtherMarket = Market.HK
            while (idx < lines.size) {
                val line = lines[idx]
                if (line.contains("责任说明") || line.contains("说明") || line.contains("Page") || line.contains("综合账")) {
                    break
                }
                
                if (line.contains("市场: 港市场") || line.contains("市场: ⾹港市场") || line.contains("市场: 香港市场")) {
                    currentOtherMarket = Market.HK
                    idx++
                    continue
                }
                if (line.contains("市场: 美国市场")) {
                    currentOtherMarket = Market.US
                    idx++
                    continue
                }
                
                val match = Regex("""^(\d{4}\.\d{2}\.\d{2})\s+(\S+)\s+(.*?)\s+(-?[\d,]+\.\d{2})$""").matchEntire(line)
                if (match != null) {
                    val dateStr = match.groupValues[1]
                    val typeStr = match.groupValues[2]
                    val contentStr = match.groupValues[3]
                    val qtyStr = match.groupValues[4]
                    
                    val qty = qtyStr.replace(",", "").toDoubleOrNull()
                    val tradeDate = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy.MM.dd"))
                    
                    if (qty != null) {
                        when {
                            typeStr.contains("中签") || typeStr.contains("中簽") || typeStr.contains("新股") -> {
                                var symbol = ""
                                val remarkSymbolMatch = Regex("""\b(\d+)\.HK\b""").find(contentStr)
                                if (remarkSymbolMatch != null) {
                                    symbol = remarkSymbolMatch.groupValues[1].padStart(4, '0') + ".HK"
                                } else {
                                    val itemSymbolMatch = Regex("""^(\d+)\s+""").find(contentStr)
                                    if (itemSymbolMatch != null) {
                                        val digits = itemSymbolMatch.groupValues[1]
                                        symbol = digits.padStart(4, '0') + ".HK"
                                    }
                                }
                                
                                var name = contentStr
                                if (symbol.isNotEmpty()) {
                                    name = name.replace(Regex("""^\d+\s+"""), "")
                                    val ipoIndex = name.indexOf("IPO ")
                                    if (ipoIndex != -1) {
                                        name = name.substring(0, ipoIndex).trim()
                                    }
                                }
                                name = name.replace("\"", "").trim()
                                
                                var price = 0.0
                                for (cashLine in lines) {
                                    if ((cashLine.contains("中签") || cashLine.contains("中簽")) && cashLine.contains(symbol.replace(".HK", ""))) {
                                        val amtMatch = Regex("""@(?:HKD|USD)?\s*([\d,]+\.?\d*)""").find(cashLine)
                                        if (amtMatch != null) {
                                            val totalAmt = amtMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: 0.0
                                            if (totalAmt > 0 && qty > 0) {
                                                price = totalAmt / qty
                                            }
                                        }
                                    }
                                }
                                
                                trades.add(ParsedStatementTrade(
                                    sourceChannel = ImportSourceChannel.PDF_STATEMENT,
                                    tradeType = TradeType.BUY,
                                    market = currentOtherMarket,
                                    symbol = symbol,
                                    name = name,
                                    currencyCode = if (currentOtherMarket == Market.HK) "HKD" else "USD",
                                    price = price,
                                    quantity = qty,
                                    amount = price * qty,
                                    tradeDate = tradeDate,
                                    tradeTime = "09:00",
                                    tradeRef = "IPO-${symbol}-${dateStr.replace(".", "")}",
                                    rawLine = line,
                                    commission = 0.0,
                                    tax = 0.0,
                                    platformFee = 0.0,
                                    assetType = "STOCK"
                                ))
                            }
                            
                            typeStr.contains("期权到期") || typeStr.contains("到期") -> {
                                val optionRegex = Regex("""([A-Za-z]+)(\d{6})([CP])(\d+).*""")
                                val optionMatch = optionRegex.find(contentStr.replace(" ", ""))
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
                                    val symbol = "$underlying $expiryCompact$typeChar$strikeLabel"
                                    val name = "$underlying $expiryStr ${if (optType == "CALL") "Call" else "Put"} @ $strikeLabel"
                                    
                                    trades.add(ParsedStatementTrade(
                                        sourceChannel = ImportSourceChannel.PDF_STATEMENT,
                                        tradeType = TradeType.EXPIRE,
                                        market = currentOtherMarket,
                                        symbol = symbol,
                                        name = name,
                                        currencyCode = if (currentOtherMarket == Market.HK) "HKD" else "USD",
                                        price = 0.0,
                                        quantity = kotlin.math.abs(qty),
                                        amount = 0.0,
                                        tradeDate = tradeDate,
                                        tradeTime = "23:59",
                                        tradeRef = "EXP-$symbol-${dateStr.replace(".", "")}",
                                        rawLine = line,
                                        commission = 0.0,
                                        tax = 0.0,
                                        platformFee = 0.0,
                                        assetType = "OPTION",
                                        underlyingSymbol = underlying,
                                        expiryDate = expiryStr,
                                        strikePrice = strike,
                                        optionType = optType
                                    ))
                                }
                            }
                            
                            typeStr.contains("公司行动股票进账") || typeStr.contains("股票拆分") || typeStr.contains("Split") -> {
                                val splitRegex = Regex("""Stock Split Amount:\s*(\d+)\s+for\s+(\d+)""")
                                val splitMatch = splitRegex.find(contentStr)
                                if (splitMatch != null) {
                                    val ratio = splitMatch.groupValues[1].toDouble() / splitMatch.groupValues[2].toDouble()
                                    
                                    val parts = contentStr.trim().split("\\s+".toRegex())
                                    val stockCode = parts.firstOrNull()?.uppercase() ?: ""
                                    val symbol = if (currentOtherMarket == Market.HK) {
                                        val digits = stockCode.filter { it.isDigit() }
                                        when {
                                            digits.length == 5 -> "$digits.HK"
                                            digits.length in 1..4 -> digits.padStart(4, '0') + ".HK"
                                            else -> stockCode
                                        }
                                    } else {
                                        stockCode
                                    }
                                    
                                    trades.add(ParsedStatementTrade(
                                        sourceChannel = ImportSourceChannel.PDF_STATEMENT,
                                        tradeType = TradeType.SPLIT,
                                        market = currentOtherMarket,
                                        symbol = symbol,
                                        name = parts.drop(1).joinToString(" ").substringBefore("Stock Split").trim(),
                                        currencyCode = if (currentOtherMarket == Market.HK) "HKD" else "USD",
                                        price = ratio,
                                        quantity = 1.0,
                                        amount = 0.0,
                                        tradeDate = if (currentOtherMarket == Market.US) tradeDate.minusDays(1) else tradeDate,
                                        tradeTime = "00:00",
                                        tradeRef = "SPLIT-$symbol-${dateStr.replace(".", "")}",
                                        rawLine = line,
                                        commission = 0.0,
                                        tax = 0.0,
                                        platformFee = 0.0,
                                        assetType = "STOCK"
                                    ))
                                }
                            }
                        }
                    }
                }
                idx++
            }
        }

        return trades
    }

    private fun convertTimezone(date: LocalDate, timeStr: String, tz: String): Pair<LocalDate, String> {
        return try {
            val zoneId = when (tz) {
                "EST" -> ZoneId.of("America/New_York")
                "EDT" -> ZoneId.of("America/New_York")
                "HKT" -> ZoneId.of("Asia/Hong_Kong")
                else -> ZoneId.of("Asia/Hong_Kong")
            }
            val timeParts = timeStr.split(":")
            val hour = timeParts.getOrNull(0)?.toIntOrNull() ?: 0
            val minute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0
            val second = timeParts.getOrNull(2)?.toIntOrNull() ?: 0
            val localTime = LocalTime.of(hour, minute, second)
            
            val zdt = ZonedDateTime.of(date, localTime, zoneId)
            val hktZdt = zdt.withZoneSameInstant(ZoneId.of("Asia/Hong_Kong"))
            
            val adjustedDate = hktZdt.toLocalDate()
            val adjustedTime = String.format("%02d:%02d", hktZdt.hour, hktZdt.minute)
            Pair(adjustedDate, adjustedTime)
        } catch (e: Exception) {
            Pair(date, timeStr.substring(0, 5))
        }
    }

    private fun scanTimeAndTz(
        lines: List<String>,
        startIndex: Int,
        currentMarket: Market,
        currentCurrency: String,
        tradeDate: LocalDate
    ): LongBridgeScanResult {
        val timeRegex = Regex("""(\d{2}:\d{2}:\d{2})\s*"?\s*(HKT|EST|EDT)""")
        for (idx in startIndex until minOf(lines.size, startIndex + 15)) {
            val scanLine = lines[idx]
            if (scanLine.startsWith("OS") && idx > startIndex) break
            val match = timeRegex.find(scanLine)
            if (match != null) {
                val tz = match.groupValues[2]
                val market = if (tz == "HKT") Market.HK else Market.US
                val currency = if (tz == "HKT") "HKD" else "USD"
                val rawTime = match.groupValues[1]
                val (adjustedDate, adjustedTime) = convertTimezone(tradeDate, rawTime, tz)
                return LongBridgeScanResult(market, currency, adjustedDate, adjustedTime)
            }
        }
        return LongBridgeScanResult(currentMarket, currentCurrency, tradeDate, null)
    }

    internal fun normalizeCjkCompatChars(text: String): String {
        var result = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC)
        result = result.replace(Regex("[\u0000-\u0008\u000B-\u000C\u000E-\u001F]"), " ")
        result = result
            .replace('\u2F29', '\u5C0F') // ⼩ -> 小
            .replace('\u2ECB', '\u8F66') // ⻋ -> 车
            .replace('\u2F50', '\u6BD4') // ⽐ -> 比
            .replace('\u2F72', '\u79be') // ⽲ -> 禾
            .replace('\u2EA0', '\u6C11') // ⺠ -> 民
            .replace('\u2EC5', '\u89C1') // ⻅ -> 见
            .replace('\u2EE9', '\u9EC4') // ⻩ -> 黄
            .replace('\u2EF0', '\u9F99') // ⻰ -> 龙
            .replace('\u6236', '\u6237') // 戶 -> 户
        return result
    }
}
