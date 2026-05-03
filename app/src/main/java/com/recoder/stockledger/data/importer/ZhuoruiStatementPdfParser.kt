package com.recoder.stockledger.data.importer

import android.util.Log
import com.recoder.stockledger.data.ImportSourceChannel
import com.recoder.stockledger.data.Market
import com.recoder.stockledger.data.TradeType
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream
import java.time.LocalDate

data class ParsedStatementTrade(
    val sourceChannel: ImportSourceChannel,
    val tradeType: TradeType,
    val market: Market,
    val symbol: String,
    val name: String,
    val currencyCode: String,
    val price: Double,
    val quantity: Int,
    val amount: Double,
    val tradeDate: LocalDate,
    val tradeRef: String,
    val rawLine: String,
)

object ZhuoruiStatementPdfParser {
    private const val TAG = "ZhuoruiStmtPdfParser"

    private val datePattern = Regex("""(\d{4})-(\d{2})-(\d{2})""")
    private val totalLinePattern = Regex("""合计Total\((\w+)\)""")
    private val marketCurrencyPattern = Regex("""(US|HK)/(NASDAQ|NYSE|AMEX|BATS|SEHK|Mutual Fund)\s+(USD|HKD|CNY)""")
    private val numericPattern = Regex("""-?[\d,]+\.?\d*""")

    // CJK compatibility characters in PDF: ⼊(U+2F0A) for 入, ⽇(U+2F47) for 日, etc.
    private val DIRECTION_BUY = setOf("买⼊", "买入", "買入", "BUY")
    private val DIRECTION_SELL = setOf("沽出", "卖出", "賣出", "SELL")
    private val DIRECTION_SUBSCRIBE = setOf("申购", "申購")
    private val DIRECTION_REDEEM = setOf("赎回", "贖回")

    fun parsePdf(inputStream: InputStream, password: String): List<ParsedStatementTrade> {
        Log.d(TAG, "开始解析PDF文件, password长度=${password.length}")
        return try {
            val document = PDDocument.load(inputStream, password)
            Log.d(TAG, "PDF文档加载成功, 页数=${document.numberOfPages}")
            document.use { doc ->
                val stripper = PDFTextStripper().apply {
                    sortByPosition = true
                }
                val text = stripper.getText(doc)
                Log.d(TAG, "PDF文本提取成功, 文本长度=${text.length}")
                Log.d(TAG, "PDF文本前1000字:\n${text.take(1000)}")
                val trades = parseText(text)
                Log.d(TAG, "解析完成, 找到${trades.size}条交易记录")
                trades
            }
        } catch (e: Exception) {
            Log.e(TAG, "PDF解析失败: ${e.message}", e)
            emptyList()
        }
    }

    fun parseText(text: String): List<ParsedStatementTrade> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        Log.d(TAG, "parseText: 共${lines.size}行")
        val trades = mutableListOf<ParsedStatementTrade>()

        var i = 0
        while (i < lines.size) {
            val line = lines[i]

            // Detect transaction sections
            val isStockSection = line.contains("成交信息") || line.contains("Transaction Details")
            val isFundSection = line.contains("基⾦成交信息") || line.contains("Fund Transaction Details")

            if (isStockSection || isFundSection) {
                Log.d(TAG, "找到交易区域: $line (stock=$isStockSection)")
                i++
                i = skipHeaderLines(lines, i)
                val sectionEnd = findSectionEnd(lines, i)
                Log.d(TAG, "区域范围: $i..$sectionEnd")
                val sectionTrades = parseTradeGroups(lines, i, sectionEnd, isFund = isFundSection)
                trades.addAll(sectionTrades)
                i = sectionEnd
                continue
            }
            i++
        }

        Log.d(TAG, "解析完成, 共${trades.size}条交易记录")
        return trades
    }

    private fun skipHeaderLines(lines: List<String>, startIndex: Int): Int {
        var i = startIndex
        while (i < lines.size) {
            val line = lines[i]
            if (isDirection(line) || isTotalLine(line)) break
            if (line.contains("Trade Direction") || line.contains("Stock Code") ||
                line.contains("Market") || line.contains("Currency") ||
                line.contains("Trade Date") || line.contains("Sett Date") ||
                line.contains("Quantity") || line.contains("Price") ||
                line.contains("Clearing Balance") || line.contains("S/R") ||
                line.contains("Fund Code") || line.contains("买卖⽅向") ||
                line.contains("代码名称") || line.contains("市场/交易所") ||
                line.contains("币种") || line.contains("交易⽇期") ||
                line.contains("交收⽇期") || line.contains("股数") ||
                line.contains("均价") || line.contains("清算⾦额") ||
                line.contains("申/赎")
            ) {
                i++
                continue
            }
            break
        }
        return i
    }

    private fun findSectionEnd(lines: List<String>, startIndex: Int): Int {
        for (i in startIndex until lines.size) {
            val line = lines[i]
            if (line.contains("Account(") && line.contains("Statement")) return i
            if (line.contains("Zircon Securities")) return i
            if (line.contains("重要提⽰") || line.contains("Important Notes")) return i
            if (i > startIndex && (line.contains("成交信息") || line.contains("Transaction Details"))) return i
            if (i > startIndex && (line.contains("基⾦成交信息") || line.contains("Fund Transaction Details"))) return i
            if (line.contains("当⽉资⾦提存交易") || line.contains("Monthly Withdrawals")) return i
        }
        return lines.size
    }

    private fun parseTradeGroups(
        lines: List<String>,
        startIndex: Int,
        endIndex: Int,
        isFund: Boolean,
    ): List<ParsedStatementTrade> {
        val trades = mutableListOf<ParsedStatementTrade>()
        var i = startIndex
        var currentDirection: TradeType? = null
        val detailLines = mutableListOf<String>()

        while (i < endIndex) {
            val line = lines[i]
            when {
                isDirection(line) -> {
                    if (currentDirection != null && detailLines.isNotEmpty()) {
                        parseDetailLines(detailLines, currentDirection, isFund, trades)
                    }
                    currentDirection = resolveDirection(line)
                    detailLines.clear()
                    Log.d(TAG, "方向: $currentDirection at line $i: $line")
                }
                isTotalLine(line) -> {
                    if (currentDirection != null && detailLines.isNotEmpty()) {
                        parseDetailLines(detailLines, currentDirection, isFund, trades)
                    }
                    currentDirection = null
                    detailLines.clear()
                }
                currentDirection != null -> {
                    detailLines.add(line)
                }
            }
            i++
        }

        if (currentDirection != null && detailLines.isNotEmpty()) {
            parseDetailLines(detailLines, currentDirection, isFund, trades)
        }

        return trades
    }

    private fun parseDetailLines(
        detailLines: List<String>,
        direction: TradeType,
        isFund: Boolean,
        trades: MutableList<ParsedStatementTrade>,
    ) {
        val mergedLines = mergeDetailLines(detailLines)
        Log.d(TAG, "解析${mergedLines.size}行明细 (direction=$direction, isFund=$isFund)")

        for (line in mergedLines) {
            val trade = if (isFund) parseFundTradeLine(line, direction) else parseStockTradeLine(line, direction)
            if (trade != null) {
                trades.add(trade)
                Log.d(TAG, "成功解析: ${trade.tradeType} ${trade.symbol} x${trade.quantity} @${trade.price}")
            } else {
                Log.w(TAG, "行解析失败: ${line.take(150)}")
            }
        }
    }

    /**
     * Merge lines that belong together into complete trade lines.
     *
     * PDFBox may output each column value on a separate line:
     *   "MUU Direxion每⽇2倍做多MU ETF"  (stock name)
     *   "US/NASDAQ"  (market)
     *   "USD"  (currency)
     *   "2026-02-02"  (trade date)
     *   "2026-02-03"  (sett date)
     *   "1"  (quantity)
     *   "228.0000"  (price)
     *   "-229.39"  (clearing balance)
     *
     * Or merge adjacent columns into one line:
     *   "MUU Direxion每⽇2倍做多MU ETF US/NASDAQ USD 2026-02-02 2026-02-03 1 228.0000 -229.39"
     *
     * A new trade line starts when:
     * 1. The line starts with a date (position < 15) and current has stock info but no dates yet
     * 2. The line starts with a non-numeric, non-date token (stock name) and current is already a complete trade (has 2 dates)
     */
    private fun mergeDetailLines(lines: List<String>): List<String> {
        val merged = mutableListOf<String>()
        var current = ""

        for (line in lines) {
            val dateMatch = datePattern.find(line)
            val lineStartsWithDate = dateMatch != null && dateMatch.range.first < 15
            val lineStartsWithStockName = !lineStartsWithDate && line.isNotBlank() &&
                    !line[0].isDigit() && line[0] != '-'

            if (current.isNotBlank()) {
                val currentDateCount = datePattern.findAll(current).count()
                val currentIsComplete = currentDateCount >= 2

                when {
                    // Current is complete trade and new line starts with stock name → new trade
                    currentIsComplete && lineStartsWithStockName -> {
                        merged.add(current.trim())
                        current = line
                    }
                    // Current has stock info, new line starts with date → add date to current
                    !currentIsComplete && lineStartsWithDate -> {
                        current = "$current $line"
                    }
                    // Otherwise append to current
                    else -> {
                        current = "$current $line"
                    }
                }
            } else {
                current = line
            }
        }
        if (current.isNotBlank()) merged.add(current.trim())
        return merged
    }

    private fun parseStockTradeLine(line: String, direction: TradeType): ParsedStatementTrade? {
        Log.d(TAG, "parseStockTradeLine: $line")

        val dateMatches = datePattern.findAll(line).toList()
        if (dateMatches.size < 2) {
            Log.w(TAG, "日期不足2个: ${dateMatches.size}")
            return null
        }

        val tradeDateStr = dateMatches[0].groupValues.let { "${it[1]}-${it[2]}-${it[3]}" }
        val tradeDate = try {
            LocalDate.parse(tradeDateStr)
        } catch (e: Exception) {
            Log.w(TAG, "日期解析失败: $tradeDateStr", e)
            return null
        }

        // Extract text before first date = stock info part
        val firstDateStart = dateMatches[0].range.first
        val namePart = line.substring(0, firstDateStart).trim()

        // Find market/currency
        val mcMatch = marketCurrencyPattern.find(line)
        val market: Market
        val currencyCode: String
        if (mcMatch != null) {
            market = resolveMarketFromExchange(mcMatch.groupValues[2])
            currencyCode = mcMatch.groupValues[3]
        } else {
            market = Market.US
            currencyCode = "USD"
        }

        // Extract stock code and name (strip market/currency from namePart)
        val (stockCode, stockName) = extractStockCodeAndName(namePart)

        // Extract numbers after the second date
        val secondDateEnd = dateMatches[1].range.last + 1
        val numbersPart = line.substring(secondDateEnd).trim()
        val numbers = parseNumbers(numbersPart)

        if (numbers.size < 3) {
            Log.w(TAG, "数字不足3个: ${numbers.size}, numbersPart=$numbersPart, numbers=$numbers")
            return null
        }

        val quantity = numbers[0].toInt()
        val price = numbers[1]
        val clearingBalance = numbers[2]
        val amount = kotlin.math.abs(clearingBalance)

        val symbol = resolveSymbol(stockCode, market)
        val tradeRef = "${tradeDateStr.replace("-", "")}-${stockCode}-${direction.name}"

        Log.d(TAG, "解析成功: $direction $symbol($stockName) x$quantity @$price amount=$amount $currencyCode")
        return ParsedStatementTrade(
            sourceChannel = ImportSourceChannel.ZHUORUI_STATEMENT,
            tradeType = direction,
            market = market,
            symbol = symbol,
            name = stockName,
            currencyCode = currencyCode,
            price = price,
            quantity = quantity,
            amount = amount,
            tradeDate = tradeDate,
            tradeRef = tradeRef,
            rawLine = line,
        )
    }

    private fun parseFundTradeLine(line: String, direction: TradeType): ParsedStatementTrade? {
        Log.d(TAG, "parseFundTradeLine: $line")

        val dateMatches = datePattern.findAll(line).toList()
        if (dateMatches.size < 2) {
            Log.w(TAG, "基金行日期不足2个: ${dateMatches.size}")
            return null
        }

        val tradeDateStr = dateMatches[0].groupValues.let { "${it[1]}-${it[2]}-${it[3]}" }
        val tradeDate = try {
            LocalDate.parse(tradeDateStr)
        } catch (e: Exception) {
            Log.w(TAG, "基金日期解析失败: $tradeDateStr", e)
            return null
        }

        val firstDateStart = dateMatches[0].range.first
        val namePart = line.substring(0, firstDateStart).trim()

        val mcMatch = marketCurrencyPattern.find(line)
        val market: Market
        val currencyCode: String
        if (mcMatch != null) {
            market = resolveMarketFromExchange(mcMatch.groupValues[2])
            currencyCode = mcMatch.groupValues[3]
        } else {
            market = Market.HONG_KONG
            currencyCode = "HKD"
        }

        val (stockCode, stockName) = extractStockCodeAndName(namePart)
        val symbol = resolveSymbol(stockCode, market)

        val secondDateEnd = dateMatches[1].range.last + 1
        val numbersPart = line.substring(secondDateEnd).trim()
        val numbers = parseNumbers(numbersPart)

        if (numbers.size < 3) {
            Log.w(TAG, "基金数字不足3个: ${numbers.size}")
            return null
        }

        val quantity = numbers[0].toInt()
        val price = numbers[1]
        val clearingBalance = numbers[2]
        val amount = kotlin.math.abs(clearingBalance)

        val tradeRef = "${tradeDateStr.replace("-", "")}-${stockCode}-${direction.name}"

        Log.d(TAG, "基金解析成功: $direction $symbol x$quantity @$price")
        return ParsedStatementTrade(
            sourceChannel = ImportSourceChannel.ZHUORUI_STATEMENT,
            tradeType = direction,
            market = market,
            symbol = symbol,
            name = stockName,
            currencyCode = currencyCode,
            price = price,
            quantity = quantity,
            amount = amount,
            tradeDate = tradeDate,
            tradeRef = tradeRef,
            rawLine = line,
        )
    }

    /**
     * Parse numbers from a string. Handles both separated and merged number formats.
     * E.g., "4 445.5825 1,780.91" → [4.0, 445.5825, 1780.91]
     * Also handles merged case: "1228.0000 -229.39" → tries to split if needed.
     */
    private fun parseNumbers(numbersPart: String): List<Double> {
        val numbers = numericPattern.findAll(numbersPart).map {
            it.value.replace(",", "").toDouble()
        }.toList()

        // If we have exactly 2 numbers and the first looks like it could be a merged qty+price
        // (e.g., "1228.0000" = qty 1 + price 228.0000), try to split
        if (numbers.size == 2) {
            val first = numbers[0]
            val second = numbers[1]
            // Try splitting the first number: integer part as qty, decimal part as price
            val firstStr = numbersPart.trim().split("\\s+".toRegex()).firstOrNull() ?: ""
            val cleanFirst = firstStr.replace(",", "")
            val dotIndex = cleanFirst.indexOf('.')
            if (dotIndex > 0) {
                val intPart = cleanFirst.substring(0, dotIndex)
                val decPart = cleanFirst.substring(dotIndex)
                val qty = intPart.toIntOrNull()
                val price = decPart.toDoubleOrNull()
                if (qty != null && price != null && qty > 0 && price > 0) {
                    Log.d(TAG, "拆分合并数字: $cleanFirst → qty=$qty, price=$price")
                    return listOf(qty.toDouble(), price, second)
                }
            }
        }

        return numbers
    }

    private fun extractStockCodeAndName(namePart: String): Pair<String, String> {
        // Strip market/currency pattern from namePart
        val cleaned = marketCurrencyPattern.replace(namePart, "").trim()
        if (cleaned.isBlank()) return Pair("", "")

        val parts = cleaned.split("\\s+".toRegex(), limit = 2)
        val stockCode = parts[0].trim()
        val stockName = parts.getOrNull(1)?.trim() ?: stockCode
        return Pair(stockCode, stockName)
    }

    private fun isDirection(line: String): Boolean {
        val trimmed = line.trim()
        return DIRECTION_BUY.any { trimmed.contains(it) } ||
                DIRECTION_SELL.any { trimmed.contains(it) } ||
                DIRECTION_SUBSCRIBE.any { trimmed.contains(it) } ||
                DIRECTION_REDEEM.any { trimmed.contains(it) }
    }

    private fun resolveDirection(line: String): TradeType {
        val trimmed = line.trim()
        return when {
            DIRECTION_BUY.any { trimmed.contains(it) } -> TradeType.BUY
            DIRECTION_SELL.any { trimmed.contains(it) } -> TradeType.SELL
            DIRECTION_SUBSCRIBE.any { trimmed.contains(it) } -> TradeType.BUY
            DIRECTION_REDEEM.any { trimmed.contains(it) } -> TradeType.SELL
            else -> TradeType.BUY
        }
    }

    private fun isTotalLine(line: String): Boolean {
        return totalLinePattern.containsMatchIn(line)
    }

    private fun resolveMarketFromExchange(exchange: String): Market = when (exchange.uppercase()) {
        "NASDAQ", "NYSE", "AMEX", "BATS" -> Market.US
        "SEHK" -> Market.HONG_KONG
        "MUTUAL FUND" -> Market.HONG_KONG
        else -> Market.US
    }

    private fun resolveSymbol(stockCode: String, market: Market): String {
        return when (market) {
            Market.HONG_KONG -> {
                val digits = stockCode.filter(Char::isDigit)
                when {
                    digits.length == 5 -> digits + ".HK"
                    digits.length in 1..4 -> digits.padStart(4, '0') + ".HK"
                    digits.length > 5 -> digits + ".HK"
                    else -> stockCode
                }
            }
            Market.A_SHARE -> stockCode.filter(Char::isDigit)
            Market.US -> stockCode.uppercase()
            Market.CASH -> stockCode
        }
    }
}
