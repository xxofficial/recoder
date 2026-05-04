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
    val tradeTime: String? = null,
    val commission: Double? = null,
    val platformFee: Double? = null,
    val tax: Double? = null,
    val tradeRef: String,
    val rawLine: String,
)

object ZhuoruiStatementPdfParser {
    private const val TAG = "ZhuoruiStmtPdfParser"

    private val datePattern = Regex("""(\d{4})-(\d{2})-(\d{2})""")
    private val totalLinePattern = Regex("""合计Total\((\w+)\)""")
    private val marketCurrencyPattern = Regex("""(US|HK)/(NASDAQ|NYSE|AMEX|BATS|SEHK|Mutual Fund)\s+(USD|HKD|CNY)""")
    private val numericPattern = Regex("""-?[\d,]+\.?\d*""")

    private val DIRECTION_BUY = setOf("买入", "買入", "BUY")
    private val DIRECTION_SELL = setOf("沽出", "卖出", "賣出", "SELL")

    private val FEE_KEYWORDS = listOf(
        "经纪佣", "Commission",
        "交收费", "Settlement Fee",
        "证监会费", "SEC Regulation Fee",
        "交易活动费", "Transaction Activity Fee",
        "Finra", "平台", "综合会计追踪", "Consolidated Audit",
        "应计利息", "Accrued Interest", "小计"
    )

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
                // 记录前30行用于诊断
                val previewLines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }.take(30)
                Log.d(TAG, "PDF文本前30行预览:\n${previewLines.joinToString("\n")}")
                // 保存提取的文本到缓存目录，方便诊断
                if (cacheDir != null) {
                    try {
                        val ts = System.currentTimeMillis()
                        val debugFile = File(cacheDir, "zhuorui_pdf_extract_$ts.txt")
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
        Log.d(TAG, "parseText: 共${lines.size}行")
        val trades = mutableListOf<ParsedStatementTrade>()

        var i = 0
        while (i < lines.size) {
            val line = lines[i]

            // 跳过基金成交信息区域
            if (line.contains("基金成交信息") || line.contains("Fund Transaction Details")) {
                i++
                i = skipHeaderLines(lines, i)
                i = findSectionEnd(lines, i)
                continue
            }

            // 跳过未交收证券交易区域
            if (line.contains("未交收证券交易") || line.contains("Unsettled Securities Transaction")) {
                i++
                i = skipHeaderLines(lines, i)
                i = findSectionEnd(lines, i)
                continue
            }

            // 检测股票成交信息区域
            if (line.contains("成交信息") || line.contains("Transaction Details")) {
                Log.d(TAG, "找到股票成交信息区域: $line")
                i++
                val sectionStart = i
                i = skipHeaderLines(lines, i)
                val sectionEnd = findSectionEnd(lines, i)

                // 检测格式
                val headerWindow = lines.subList(
                    sectionStart,
                    minOf(sectionStart + 15, lines.size)
                )
                val isFormatA = headerWindow.any {
                    it.contains("买卖方向") || it.contains("Trade Direction")
                }
                val isFormatB = headerWindow.any {
                    it.contains("买/沽") || it.contains("B/S")
                } || headerWindow.any {
                    Regex("""^(US|HK)-(NASDAQ|NYSE|AMEX|BATS|SEHK)-(\w{3})$""").find(it) != null
                }

                Log.d(TAG, "格式检测: A=$isFormatA, B=$isFormatB")

                val sectionTrades = when {
                    isFormatA -> parseFormatA(lines, i, sectionEnd)
                    isFormatB -> parseFormatB(lines, i, sectionEnd)
                    else -> {
                        Log.w(TAG, "无法识别成交信息格式，回退到格式A")
                        parseFormatA(lines, i, sectionEnd)
                    }
                }
                trades.addAll(sectionTrades)
                i = sectionEnd
                continue
            }
            i++
        }

        Log.d(TAG, "解析完成, 共${trades.size}条交易记录")
        return trades
    }

    // ===== 格式A解析（新版，2026-02起） =====

    private fun parseFormatA(
        lines: List<String>,
        startIndex: Int,
        endIndex: Int,
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
                        parseFormatADetailLines(detailLines, currentDirection, trades)
                    }
                    currentDirection = resolveDirection(line)
                    detailLines.clear()
                    Log.d(TAG, "格式A 方向: $currentDirection at line $i: $line")
                }

                isTotalLine(line) -> {
                    if (currentDirection != null && detailLines.isNotEmpty()) {
                        parseFormatADetailLines(detailLines, currentDirection, trades)
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
            parseFormatADetailLines(detailLines, currentDirection, trades)
        }

        return trades
    }

    private fun parseFormatADetailLines(
        detailLines: List<String>,
        direction: TradeType,
        trades: MutableList<ParsedStatementTrade>,
    ) {
        val mergedLines = mergeFormatADetailLines(detailLines)
        Log.d(TAG, "格式A 解析${mergedLines.size}行明细 (direction=$direction)")

        for (line in mergedLines) {
            val trade = parseFormatATradeLine(line, direction)
            if (trade != null) {
                trades.add(trade)
                Log.d(TAG, "格式A 成功解析: ${trade.tradeType} ${trade.symbol} x${trade.quantity} @${trade.price}")
            } else {
                Log.w(TAG, "格式A 行解析失败: ${line.take(150)}")
            }
        }
    }

    private fun mergeFormatADetailLines(lines: List<String>): List<String> {
        val merged = mutableListOf<String>()
        var current = ""

        for (line in lines) {
            if (isFeeLine(line)) continue

            val dateMatch = datePattern.find(line)
            val lineStartsWithDate = dateMatch != null && dateMatch.range.first < 15
            val lineStartsWithStockName = !lineStartsWithDate && line.isNotBlank() &&
                !line[0].isDigit() && line[0] != '-'

            if (current.isNotBlank()) {
                val currentDateCount = datePattern.findAll(current).count()
                val currentIsComplete = currentDateCount >= 2

                when {
                    currentIsComplete && lineStartsWithStockName -> {
                        merged.add(current.trim())
                        current = line
                    }

                    !currentIsComplete && lineStartsWithDate -> {
                        current = "$current $line"
                    }

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

    private fun parseFormatATradeLine(line: String, direction: TradeType): ParsedStatementTrade? {
        Log.d(TAG, "格式A parseTradeLine: $line")

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

        val firstDateStart = dateMatches[0].range.first
        val namePart = line.substring(0, firstDateStart).trim()

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

        val (stockCode, stockName) = extractStockCodeAndName(namePart)

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
        val tradeRef = "${tradeDateStr.replace("-", "")}-${stockCode}-${direction.name}-${quantity}"

        Log.d(TAG, "格式A 解析成功: $direction $symbol($stockName) x$quantity @$price amount=$amount $currencyCode")
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

    // ===== 格式B解析（旧版，~2026-01） =====

    private fun parseFormatB(
        lines: List<String>,
        startIndex: Int,
        endIndex: Int,
    ): List<ParsedStatementTrade> {
        val trades = mutableListOf<ParsedStatementTrade>()
        var i = startIndex
        var currentMarket = Market.US
        var currentCurrency = "USD"

        while (i < endIndex) {
            val line = lines[i]

            // 检测市场分类行
            val marketMatch = Regex("""^(US|HK)-(NASDAQ|NYSE|AMEX|BATS|SEHK)-(\w{3})$""").find(line)
            if (marketMatch != null) {
                currentMarket = resolveMarketFromExchange(marketMatch.groupValues[2])
                currentCurrency = marketMatch.groupValues[3]
                i++
                continue
            }

            // 检测交易块开始（以日期开头）
            val dateMatch = datePattern.find(line)
            if (dateMatch != null && dateMatch.range.first < 5) {
                val blockLines = mutableListOf(line)
                var dateCountInBlock = 1
                i++
                while (i < endIndex) {
                    val nextLine = lines[i]
                    val nextDate = datePattern.find(nextLine)
                    if (nextDate != null && nextDate.range.first < 5) {
                        dateCountInBlock++
                        if (dateCountInBlock > 2) break
                    }
                    if (Regex("""^(US|HK)-(NASDAQ|NYSE|AMEX|BATS|SEHK)-""").find(nextLine) != null) break
                    if (isFeeLine(nextLine)) break
                    if (nextLine.contains("重要提") || nextLine.contains("Important")) break
                    blockLines.add(nextLine)
                    i++
                }

                val merged = blockLines.joinToString(" ").trim()
                if (merged.isNotBlank()) {
                    val trade = parseFormatBTradeLine(merged, currentMarket, currentCurrency)
                    if (trade != null) {
                        trades.add(trade)
                        Log.d(TAG, "格式B 成功解析: ${trade.tradeType} ${trade.symbol} x${trade.quantity} @${trade.price}")
                    } else {
                        Log.w(TAG, "格式B 块解析失败: ${merged.take(150)}")
                    }
                }
                continue
            }

            i++
        }

        return trades
    }

    private fun parseFormatBTradeLine(
        line: String,
        defaultMarket: Market,
        defaultCurrency: String,
    ): ParsedStatementTrade? {
        Log.d(TAG, "格式B parseTradeLine: $line")

        val tokens = line.split(Regex("""\s+"""))

        // 找前两个日期
        var dateIdx1 = -1
        var dateIdx2 = -1
        for (idx in tokens.indices) {
            if (datePattern.matches(tokens[idx])) {
                if (dateIdx1 == -1) dateIdx1 = idx
                else if (dateIdx2 == -1) {
                    dateIdx2 = idx
                    break
                }
            }
        }
        if (dateIdx1 == -1 || dateIdx2 == -1) {
            Log.w(TAG, "格式B 日期不足2个")
            return null
        }

        val tradeDateStr = tokens[dateIdx1]
        val tradeDate = try {
            LocalDate.parse(tradeDateStr)
        } catch (e: Exception) {
            Log.w(TAG, "格式B 日期解析失败: $tradeDateStr", e)
            return null
        }

        // 编号在第二个日期之后
        var refIdx = dateIdx2 + 1
        if (refIdx >= tokens.size || !tokens[refIdx].all(Char::isDigit)) {
            Log.w(TAG, "格式B 编号缺失")
            return null
        }
        val ref = tokens[refIdx]

        // 方向在编号之后
        var dirIdx = refIdx + 1
        if (dirIdx >= tokens.size || !isDirection(tokens[dirIdx])) {
            Log.w(TAG, "格式B 方向缺失")
            return null
        }
        val direction = resolveDirection(tokens[dirIdx])

        // 最后三个token应该是股数、价格、金额
        if (tokens.size < dirIdx + 4) {
            Log.w(TAG, "格式B token不足")
            return null
        }

        val qtyToken = tokens[tokens.size - 3]
        val priceToken = tokens[tokens.size - 2]
        val amountToken = tokens[tokens.size - 1]

        val quantity = qtyToken.replace(",", "").toIntOrNull()
        val price = priceToken.replace(",", "").toDoubleOrNull()
        val clearingBalance = amountToken.replace(",", "").toDoubleOrNull()

        if (quantity == null || price == null || clearingBalance == null) {
            Log.w(TAG, "格式B 数字解析失败: qty=$qtyToken, price=$priceToken, amount=$amountToken")
            return null
        }
        val amount = kotlin.math.abs(clearingBalance)

        // 中间的token是代码和名称
        val codeAndNameTokens = tokens.subList(dirIdx + 1, tokens.size - 3)
        if (codeAndNameTokens.isEmpty()) {
            Log.w(TAG, "格式B 代码名称缺失")
            return null
        }
        val stockCode = codeAndNameTokens[0]
        val stockName = codeAndNameTokens.drop(1).joinToString(" ")

        val symbol = resolveSymbol(stockCode, defaultMarket)
        val tradeRef = "${tradeDateStr.replace("-", "")}-${stockCode}-${direction.name}-${quantity}-$ref"

        Log.d(TAG, "格式B 解析成功: $direction $symbol($stockName) x$quantity @$price")
        return ParsedStatementTrade(
            sourceChannel = ImportSourceChannel.ZHUORUI_STATEMENT,
            tradeType = direction,
            market = defaultMarket,
            symbol = symbol,
            name = stockName,
            currencyCode = defaultCurrency,
            price = price,
            quantity = quantity,
            amount = amount,
            tradeDate = tradeDate,
            tradeRef = tradeRef,
            rawLine = line,
        )
    }

    // ===== 辅助函数 =====

    private fun skipHeaderLines(lines: List<String>, startIndex: Int): Int {
        var i = startIndex
        while (i < lines.size) {
            val line = lines[i]
            if (isDirection(line) || isTotalLine(line)) break
            if (
                line.contains("Trade Direction") || line.contains("Stock Code") ||
                line.contains("Market") || line.contains("Currency") ||
                line.contains("Trade Date") || line.contains("Sett Date") ||
                line.contains("Quantity") || line.contains("Price") ||
                line.contains("Clearing Balance") || line.contains("Clear Balance") ||
                line.contains("S/R") ||
                line.contains("Fund Code") || line.contains("买卖方向") ||
                line.contains("代码名称") || line.contains("市场/交易所") ||
                line.contains("币种") || line.contains("交易日期") || line.contains("交易时间") ||
                line.contains("交收日期") || line.contains("股数") ||
                line.contains("均价") || line.contains("成交单价") || line.contains("清算金额") ||
                line.contains("申/赎") || line.contains("编号") || line.contains("Ref.") ||
                line.contains("买/沽") || line.contains("B/S") || line.contains("内容") ||
                line.contains("Description")
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
            if (line.contains("重要提示") || line.contains("Important Notes")) return i
            if (i > startIndex && (line.contains("成交信息") || line.contains("Transaction Details"))) return i
            if (i > startIndex && (line.contains("基金成交信息") || line.contains("Fund Transaction Details"))) return i
            if (i > startIndex && (line.contains("未交收证券交易") || line.contains("Unsettled Securities Transaction"))) return i
            if (
                line.contains("当月资金提存交易") || line.contains("Monthly Withdrawals") ||
                line.contains("当日资金提存交易") || line.contains("Today Withdrawals")
            ) {
                return i
            }
        }
        return lines.size
    }

    private fun normalizeCjkCompatChars(text: String): String {
        // Step 1: NFKC 处理大部分 Kangxi Radicals
        var result = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC)
        // Step 2: 手动补全 NFKC 未覆盖的 CJK Radicals Supplement，
        // 以及将 NFKC 产生的繁体形式（如 戶）转为简体（户）
        result = result
            .replace('\u2EA0', '\u6C11') // ⺠ -> 民
            .replace('\u2EC5', '\u89C1') // ⻅ -> 见
            .replace('\u2EE9', '\u9EC4') // ⻩ -> 黄
            .replace('\u2EF0', '\u9F99') // ⻰ -> 龙
            .replace('\u6236', '\u6237') // 戶 -> 户 (NFKC 将 ⼾ 映射为 戶)
        return result
    }

    private fun isFeeLine(line: String): Boolean {
        return FEE_KEYWORDS.any { line.contains(it) }
    }

    private fun isDirection(line: String): Boolean {
        val trimmed = line.trim()
        return DIRECTION_BUY.any { trimmed.contains(it) } ||
            DIRECTION_SELL.any { trimmed.contains(it) }
    }

    private fun resolveDirection(line: String): TradeType {
        val trimmed = line.trim()
        return when {
            DIRECTION_BUY.any { trimmed.contains(it) } -> TradeType.BUY
            DIRECTION_SELL.any { trimmed.contains(it) } -> TradeType.SELL
            else -> TradeType.BUY
        }
    }

    private fun isTotalLine(line: String): Boolean {
        return totalLinePattern.containsMatchIn(line)
    }

    private fun parseNumbers(numbersPart: String): List<Double> {
        val numbers = numericPattern.findAll(numbersPart).map {
            it.value.replace(",", "").toDouble()
        }.toList()

        if (numbers.size == 2) {
            val first = numbers[0]
            val second = numbers[1]
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
        val cleaned = marketCurrencyPattern.replace(namePart, "").trim()
        if (cleaned.isBlank()) return Pair("", "")

        val parts = cleaned.split("\\s+".toRegex(), limit = 2)
        val stockCode = parts[0].trim()
        val stockName = parts.getOrNull(1)?.trim() ?: stockCode
        return Pair(stockCode, stockName)
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
