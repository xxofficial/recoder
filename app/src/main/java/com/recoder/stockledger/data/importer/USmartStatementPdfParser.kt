package com.recoder.stockledger.data.importer

import android.util.Log
import com.recoder.stockledger.data.ImportSourceChannel
import com.recoder.stockledger.data.Market
import com.recoder.stockledger.data.TradeType
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.ZoneId

/**
 * Parser for uSMART Securities monthly PDF statements.
 *
 * The PDF text is extracted as multi-line content where each field occupies its own line.
 * A state-machine approach reads fields sequentially within each trade block.
 *
 * Trade block structure:
 * ```
 * SYMBOL (name       ← symbol + name start (may span multiple lines until ')')
 * continuation)
 * 港股/美股/A股通     ← market (may repeat for multiple trades under same symbol)
 * 买入/卖出          ← direction
 * 200               ← quantity
 * HKD/USD/CNY       ← currency
 * 34.2200           ← price
 * 6,844.00          ← amount
 * 2026-02-05        ← trade date
 * 否                ← forced liquidation flag
 * 2026-02-09        ← settlement date
 * (fee name-value pairs follow, shared across all trades in this symbol group)
 * ```
 *
 * Commission = 佣金 + 平台费.
 * Tax = 印花税 + 交收费 + 交易费 + 证监会交易征费 + 财汇局交易征费 + 交易活动费 + 证监会规费 + 交易系统使用费用.
 * When multiple trades share a fee block, fees are distributed proportionally by trade amount.
 */
object USmartStatementPdfParser {
    private const val TAG = "USmartStmtPdfParser"
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    // ===== Noise / Page Footer =====
    private val NOISE_PATTERNS = listOf(
        "證監會中央編號", "SFC CE NO",
        "uSMART", "uSmart Securities",
        "地址:", "地址：",
        "電話:", "電話：",
        "电话:", "电话：",
        "傳真:", "傳真：",
        "传真:", "传真：",
        "郵箱:", "郵箱：",
        "邮箱:", "邮箱：",
        "www.usmart",
    )

    // ===== Markets =====
    private val MARKET_SET = setOf("港股", "美股", "A股通")

    // ===== Buy / Sell =====
    private val BUY_SET = setOf("买入")
    private val SELL_SET = setOf("卖出", "賣出")

    // ===== Currencies =====
    private val CURRENCY_SET = setOf("HKD", "USD", "CNY")

    // ===== Fee Classification =====
    private val COMMISSION_KEYS = setOf("佣金")
    private val PLATFORM_KEYS = setOf("平台费")
    private val TAX_KEYS = setOf(
        "印花税", "交收费", "交易费",
        "证监会交易征费", "财汇局交易征费",
        "交易活动费", "证监会规费",
        "交易系统使用费用",
    )
    private val SUMMARY_KEYS = setOf(
        "交易费用合计", "交易费用汇总",
        "交易金额合计", "交易金额",
        "变动金额合计", "变动金额总计", "变动金额汇总",
        "佣金汇总", "平台费汇总",
        "总买入金额", "总卖出金额",
    )

    // ===== Horizontal State-Machine Parser Patterns =====
    private val symbolRegex = Regex("""^([A-Z0-9.]{1,6})(?:\s+|\s*\()(.*)""")
    private val tradeRegex = Regex("""(港股|美股|A股通)\s+(买入|买\u02c5|买⼊|卖出|賣出)\s+([\d,]+)\s+(HKD|USD|CNY)\s+([\d,.]+)\s+([\d,.]+)\s+(\d{4}-\d{2}-\d{2})\s+(是|否)\s+(\d{4}-\d{2}-\d{2})""")
    private val feePairRegex = Regex("""(印花税|交收费|交易费|证监会交易征费|财汇局交易征费|交易系统使用费用|交易活动费|证监会规费|佣金|平台费)\s+([\d,.-]+)""")

    // ===== Date Patterns =====
    private val STMT_DATE = Regex("""结单日期[：:]\s*(\d{4}-\d{2})""")

    // ===== Table Headers (to skip) =====
    private val TABLE_HEADER_WORDS = setOf(
        "证券/编号", "市场", "买/卖", "数量", "币种",
        "价格", "金额", "成交时间", "是否强平", "交收日期",
    )

    // ===== Section Markers =====
    private val END_MARKERS = setOf("重要提示")
    private val SECTIONS_AFTER_TRADES = setOf("资金明细", "证券提存", "融资利息")

    // ===== Interest =====
    private val INTEREST_HEADER = "融资利息"

    private data class RawTrade(
        val market: Market,
        val tradeType: TradeType,
        val quantity: Int,
        val currency: String,
        val price: Double,
        val amount: Double,
        val tradeDate: LocalDate,
    )

    private data class GroupResult(val trades: List<ParsedStatementTrade>, val nextIndex: Int)

    // ===== Public API =====

    fun parse(inputStream: InputStream, password: String? = null): List<ParsedStatementTrade> {
        val rawText = extractText(inputStream, password)
        if (rawText.isBlank()) return emptyList()
        return parseText(rawText)
    }

    fun parseText(rawText: String): List<ParsedStatementTrade> {
        val normalized = normalizeText(rawText)
        val allLines = normalized.lines().map { it.trim() }

        // Extract statement month for interest records
        val stmtMonth = allLines.firstNotNullOfOrNull { STMT_DATE.find(it)?.groupValues?.get(1) }

        // Filter noise lines (page footers and standalone page numbers)
        val lines = allLines.filter { 
            it.isNotEmpty() && !isNoiseLine(it) && !it.matches(Regex("""^\d{1,2}$""")) 
        }
        Log.d(TAG, "Lines: ${allLines.size} raw → ${lines.size} after noise filter, stmtMonth=$stmtMonth")

        // ---- Phase 0: Scan for interest in cash flow section anywhere in the lines ----
        val cashFlowInterests = mutableListOf<ParsedStatementTrade>()
        val cashFlowInterestRegex = Regex("""^融资利息\s+(HKD|USD|CNY)\s+([-]?[\d,.]+)\s+(\d{4}-\d{2}-\d{2})""")
        for (line in lines) {
            val match = cashFlowInterestRegex.find(line)
            if (match != null) {
                val curr = match.groupValues[1]
                val rawAmt = match.groupValues[2].replace(",", "").toDoubleOrNull() ?: 0.0
                val amount = Math.abs(rawAmt)
                val dateStr = match.groupValues[3]
                val tDate = LocalDate.parse(dateStr, dateFormatter)
                if (amount != 0.0) {
                    val market = when (curr) {
                        "USD" -> Market.US
                        "CNY" -> Market.A_SHARE
                        else -> Market.HK
                    }
                    val tradeTime = getTradeTimeForMarket(market)
                    val hour = getHourForMarket(market)
                    val tradeRef = "INT-$dateStr-$curr"
                    cashFlowInterests.add(
                        ParsedStatementTrade(
                            sourceChannel = ImportSourceChannel.PDF_STATEMENT,
                            tradeType = TradeType.INTEREST,
                            market = market,
                            symbol = "INTEREST",
                            name = "融资利息",
                            currencyCode = curr,
                            price = amount,
                            quantity = 1,
                            amount = amount,
                            tradeDate = tDate,
                            tradeTime = tradeTime,
                            tradeRef = tradeRef,
                            rawLine = line,
                            createdAt = tDate.atTime(hour, 35)
                                .atZone(ZoneId.of("UTC+8"))
                                .toInstant()
                                .toEpochMilli(),
                        )
                    )
                    Log.d(TAG, "Interest (cash flow): $curr $amount on $tDate")
                }
            }
        }

        val trades = mutableListOf<ParsedStatementTrade>()
        var i = 0

        // ---- Phase 1: Find 交易明细 section ----
        while (i < lines.size && !lines[i].contains("交易明细")) i++
        if (i >= lines.size) {
            Log.w(TAG, "交易明细 section not found")
            return cashFlowInterests.distinctBy { it.tradeRef }
        }
        i++ // skip section header

        // ---- Phase 2: Parse trade blocks ----
        var currentSymbol: String? = null
        val currentTrades = mutableListOf<RawTrade>()
        val currentNameFragments = mutableListOf<String>()
        var currentNameClosed = false
        var totalCommission = 0.0
        var totalPlatformFee = 0.0
        var totalTax = 0.0

        fun commitCurrentSymbolGroup() {
            if (currentSymbol != null && currentTrades.isNotEmpty()) {
                val fullName = currentNameFragments
                    .map { cleanFragment(it) }
                    .filter { it.isNotEmpty() }
                    .joinToString("")
                
                // Distribute fees proportionally on distinct trades to avoid double counting layout duplicates
                val uniqueTrades = currentTrades.distinct()
                val groupTotalAmount = uniqueTrades.sumOf { it.amount }
                val effectiveCommission = totalCommission + totalPlatformFee
                
                uniqueTrades.forEachIndexed { idx, t ->
                    val ratio = if (groupTotalAmount > 0) t.amount / groupTotalAmount else 1.0 / uniqueTrades.size
                    val market = t.market
                    val symbol = resolveSymbol(currentSymbol!!, market)
                    val dateStr = t.tradeDate.format(dateFormatter)
                    val priceStr = String.format("%.4f", t.price)
                    val tradeRef = "YL-$dateStr-$symbol-${t.tradeType.name}-${t.quantity}-$priceStr-$idx"
                    
                    val tradeTime = getTradeTimeForMarket(market)
                    val hour = getHourForMarket(market)
                    
                    val finalCommission = BigDecimal(effectiveCommission * ratio).setScale(2, RoundingMode.HALF_UP).toDouble()
                    val finalTax = BigDecimal(totalTax * ratio).setScale(2, RoundingMode.HALF_UP).toDouble()

                    trades.add(
                        ParsedStatementTrade(
                            sourceChannel = ImportSourceChannel.PDF_STATEMENT,
                            tradeType = t.tradeType,
                            market = market,
                            symbol = symbol,
                            name = fullName.ifEmpty { symbol },
                            currencyCode = t.currency,
                            price = t.price,
                            quantity = t.quantity,
                            amount = t.amount,
                            tradeDate = t.tradeDate,
                            tradeTime = tradeTime,
                            commission = finalCommission,
                            platformFee = null, // already folded into commission
                            tax = finalTax,
                            tradeRef = tradeRef,
                            rawLine = "$symbol $fullName ${t.tradeType.name} ${t.quantity}@${t.price}",
                            createdAt = t.tradeDate.atTime(hour, 35)
                                .atZone(ZoneId.of("UTC+8"))
                                .toInstant()
                                .toEpochMilli(),
                        )
                    )
                }
            }
            
            // Reset
            currentSymbol = null
            currentTrades.clear()
            currentNameFragments.clear()
            currentNameClosed = false
            totalCommission = 0.0
            totalPlatformFee = 0.0
            totalTax = 0.0
        }

        while (i < lines.size) {
            val line = lines[i]
            
            // Stop conditions for trade section
            if (END_MARKERS.any { line.contains(it) }) break
            if (SECTIONS_AFTER_TRADES.any { line.contains(it) }) break
            if (line.contains("持仓明细") || line.contains("资金出入") || line.contains("融资利息")) break

            // Check for new symbol start (supports both standard symbol + name and standalone ticker lines)
            val symMatch = symbolRegex.find(line)
            val isStandaloneSymbol = line.matches(Regex("""^(?:[A-Z.]{1,6}|[0-9]{5,6})$""")) &&
                    line !in MARKET_SET && line !in CURRENCY_SET && line !in BUY_SET && line !in SELL_SET && line != "否" && line != "是"
            
            if (symMatch != null || isStandaloneSymbol) {
                commitCurrentSymbolGroup()
                
                if (symMatch != null) {
                    currentSymbol = symMatch.groupValues[1]
                    val remainder = symMatch.groupValues[2].trim()
                    
                    val hasOpenParen = line.contains("(") || line.contains("（")
                    val hasCloseParen = line.contains(")") || line.contains("）")
                    currentNameClosed = !hasOpenParen || hasCloseParen

                    if (isValidNameFragment(remainder)) {
                        currentNameFragments.add(remainder)
                    }
                } else {
                    currentSymbol = line
                    currentNameClosed = false
                }
                i++
                continue
            }

            // Check for trade detail matches
            val tradeMatch = tradeRegex.find(line)
            if (tradeMatch != null) {
                if (currentSymbol != null) {
                    val marketStr = tradeMatch.groupValues[1]
                    val dirStr = tradeMatch.groupValues[2]
                    val qty = tradeMatch.groupValues[3].replace(",", "").toInt()
                    val curr = tradeMatch.groupValues[4]
                    val price = tradeMatch.groupValues[5].replace(",", "").toDouble()
                    val amount = tradeMatch.groupValues[6].replace(",", "").toDouble()
                    val tradeDate = LocalDate.parse(tradeMatch.groupValues[7])
                    
                    val market = when (marketStr) {
                        "美股" -> Market.US
                        "A股通" -> Market.A_SHARE
                        else -> Market.HK
                    }
                    
                    val tradeType = when {
                        BUY_SET.any { dirStr.contains(it) } -> TradeType.BUY
                        SELL_SET.any { dirStr.contains(it) } -> TradeType.SELL
                        else -> TradeType.BUY
                    }
                    
                    currentTrades.add(
                        RawTrade(market, tradeType, qty, curr, price, amount, tradeDate)
                    )
                    
                    // Check for name fragments in the same line
                    val before = line.substring(0, tradeMatch.range.first).trim()
                    if (!currentNameClosed && isValidNameFragment(before)) {
                        currentNameFragments.add(before)
                        if (before.contains(")") || before.contains("）")) {
                            currentNameClosed = true
                        }
                    }
                }
                i++
                continue
            }

            // Check for fee key-value matches
            val feeMatches = feePairRegex.findAll(line).toList()
            if (feeMatches.isNotEmpty()) {
                if (currentSymbol != null) {
                    for (feeMatch in feeMatches) {
                        val key = feeMatch.groupValues[1]
                        val value = feeMatch.groupValues[2].replace(",", "").toDoubleOrNull() ?: 0.0
                        when {
                            COMMISSION_KEYS.contains(key) -> totalCommission += value
                            PLATFORM_KEYS.contains(key) -> totalPlatformFee += value
                            TAX_KEYS.contains(key) -> totalTax += value
                        }
                    }
                }
                i++
                continue
            }

            // Otherwise, it could be a name fragment
            if (currentSymbol != null && !currentNameClosed) {
                if (isValidNameFragment(line)) {
                    currentNameFragments.add(line)
                    if (line.contains(")") || line.contains("）")) {
                        currentNameClosed = true
                    }
                }
            }
            i++
        }

        // Commit the final trade block
        commitCurrentSymbolGroup()

        // ---- Phase 3: Find and parse 融资利息 summary section ----
        val summaryInterests = mutableListOf<ParsedStatementTrade>()
        while (i < lines.size) {
            if (END_MARKERS.any { lines[i].contains(it) }) break
            if (lines[i].contains(INTEREST_HEADER) && isInterestSectionHeader(lines, i)) {
                val interestTrades = parseInterestSection(lines, i, stmtMonth)
                summaryInterests.addAll(interestTrades.trades)
                i = interestTrades.nextIndex
                break
            }
            i++
        }

        // De-duplicate interests by tradeRef to handle cases where it's both in cash flow and summary
        val allInterests = (cashFlowInterests + summaryInterests).distinctBy { it.tradeRef }
        trades.addAll(allInterests)

        Log.d(TAG, "Parsed ${trades.size} trades total")
        return trades
    }

    private fun cleanFragment(frag: String): String {
        var s = frag.trim()
        while (s.startsWith("(") || s.startsWith("（")) {
            s = s.substring(1).trim()
        }
        while (s.endsWith(")") || s.endsWith("）")) {
            s = s.substring(0, s.length - 1).trim()
        }
        return s
    }

    private fun isValidNameFragment(frag: String): Boolean {
        val s = cleanFragment(frag)
        if (s.isEmpty()) return false
        if (isNoiseLine(s)) return false
        if (s in MARKET_SET) return false
        if (s in CURRENCY_SET) return false
        if (s == "买入" || s == "买\u02c5" || s == "买入" || s == "买⼊" || s == "卖出" || s == "賣出" || s == "是" || s == "否") return false
        if (s.matches(Regex("""^\d+$"""))) return false
        if (s.matches(Regex("""^\d{4}-\d{2}-\d{2}$"""))) return false
        if (SUMMARY_KEYS.any { s.contains(it) }) return false
        if (TABLE_HEADER_WORDS.any { s.contains(it) }) return false
        return true
    }

    // ===== Interest Section =====

    /**
     * Returns true if the "融资利息" at [index] is the summary section header
     * (followed by sub-header "币种"), not a line item in the cash flow section.
     */
    private fun isInterestSectionHeader(lines: List<String>, index: Int): Boolean {
        return index + 1 < lines.size && lines[index + 1].contains("币种")
    }

    /**
     * Parses the interest summary section.
     *
     * Structure:
     * ```
     * 融资利息
     * 币种 利率/年化 本月累计利息
     * HKD 7.63% 27.12           ← currency + rate + amount (single-line or multi-line)
     * USD 6.63% 2.65
     * 重要提示       ← end
     * ```
     */
    private fun parseInterestSection(
        lines: List<String>,
        startIdx: Int,
        stmtMonth: String?,
    ): GroupResult {
        var i = startIdx + 1 // skip "融资利息"

        // Skip sub-headers (e.g. "币种 利率/年化 本月累计利息")
        while (i < lines.size) {
            val line = lines[i]
            if (END_MARKERS.any { line.contains(it) }) break
            if (CURRENCY_SET.any { line.startsWith(it) }) break
            i++
        }

        val tradeDate = if (stmtMonth != null) {
            LocalDate.parse("$stmtMonth-01").plusMonths(1).minusDays(1)
        } else {
            LocalDate.now()
        }

        val trades = mutableListOf<ParsedStatementTrade>()

        // Read currency / rate / amount triplets
        while (i < lines.size) {
            val line = lines[i]
            if (END_MARKERS.any { line.contains(it) }) break

            // 1. Try single line regex first: "HKD 7.63% 27.12"
            val singleMatch = Regex("""^(HKD|USD|CNY)\s+([\d,.]+%)\s+([-]?[\d,.]+)""").find(line)
            if (singleMatch != null) {
                val curr = singleMatch.groupValues[1]
                val amount = Math.abs(singleMatch.groupValues[3].replace(",", "").toDoubleOrNull() ?: 0.0)
                if (amount != 0.0) {
                    val market = when (curr) {
                        "USD" -> Market.US
                        "CNY" -> Market.A_SHARE
                        else -> Market.HK
                    }
                    val tradeTime = getTradeTimeForMarket(market)
                    val hour = getHourForMarket(market)
                    trades.add(
                        ParsedStatementTrade(
                            sourceChannel = ImportSourceChannel.PDF_STATEMENT,
                            tradeType = TradeType.INTEREST,
                            market = market,
                            symbol = "INTEREST",
                            name = "融资利息",
                            currencyCode = curr,
                            price = amount,
                            quantity = 1,
                            amount = amount,
                            tradeDate = tradeDate,
                            tradeTime = tradeTime,
                            tradeRef = "INT-${tradeDate.format(dateFormatter)}-$curr",
                            rawLine = line,
                            createdAt = tradeDate.atTime(hour, 35)
                                .atZone(ZoneId.of("UTC+8"))
                                .toInstant()
                                .toEpochMilli(),
                        )
                    )
                    Log.d(TAG, "Interest (summary single-line): $curr $amount on $tradeDate")
                }
                i++
                continue
            }

            // 2. Fallback to multi-line format
            val curr = CURRENCY_SET.firstOrNull { line == it }
            if (curr != null) {
                i++
                // Rate (e.g., "7.63%")
                if (i < lines.size && lines[i].contains("%")) i++ else break
                // Amount
                val amtStr = lines.getOrNull(i)?.replace(",", "") ?: break
                val amount = Math.abs(amtStr.toDoubleOrNull() ?: break)
                i++
                if (amount != 0.0) {
                    val market = when (curr) {
                        "USD" -> Market.US
                        "CNY" -> Market.A_SHARE
                        else -> Market.HK
                    }
                    val tradeTime = getTradeTimeForMarket(market)
                    val hour = getHourForMarket(market)
                    trades.add(
                        ParsedStatementTrade(
                            sourceChannel = ImportSourceChannel.PDF_STATEMENT,
                            tradeType = TradeType.INTEREST,
                            market = market,
                            symbol = "INTEREST",
                            name = "融资利息",
                            currencyCode = curr,
                            price = amount,
                            quantity = 1,
                            amount = amount,
                            tradeDate = tradeDate,
                            tradeTime = tradeTime,
                            tradeRef = "INT-${tradeDate.format(dateFormatter)}-$curr",
                            rawLine = "$curr interest $amount",
                            createdAt = tradeDate.atTime(hour, 35)
                                .atZone(ZoneId.of("UTC+8"))
                                .toInstant()
                                .toEpochMilli(),
                        )
                    )
                    Log.d(TAG, "Interest (summary multi-line): $curr $amount on $tradeDate")
                }
                continue
            }

            // If it doesn't match either, just skip line if it's a known header, otherwise break
            if (isTableHeader(line) || line.contains("币种") || line.contains("利率") || line.contains("累计利息")) {
                i++
                continue
            }
            break
        }

        return GroupResult(trades, i)
    }

    // ===== Utility =====

    private fun isNoiseLine(line: String): Boolean = NOISE_PATTERNS.any { line.contains(it) }

    private fun isTableHeader(line: String): Boolean = TABLE_HEADER_WORDS.any { line.contains(it) }

    /**
     * Normalize CJK Compatibility Ideographs and Kangxi Radicals to standard characters.
     * The uSMART PDFs use CJK Radicals (e.g., ⾦ U+2F91 instead of 金 U+91D1) which
     * must be normalized for keyword matching.
     */
    private fun normalizeText(text: String): String {
        val nfkc = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC)
        val sb = StringBuilder(nfkc.length)
        for (char in nfkc) {
            val normalizedChar = CJK_RADICALS_MAP[char.code]?.toChar() ?: char
            sb.append(normalizedChar)
        }
        var result = sb.toString()
        result = result
            .replace('\u2EA0', '\u6C11') // ⺠ → 民
            .replace('\u2EC5', '\u89C1') // ⻅ → 见
            .replace('\u2EE9', '\u9EC4') // ⻩ → 黄
            .replace('\u2EF0', '\u9F99') // ⻰ → 龙
            .replace('\u2ED8', '\u9752') // ⻘ → 青
            .replace('\u6236', '\u6237') // 戶 → 户
        return result
    }

    private val CJK_RADICALS_MAP = mapOf(
        0x2EA6 to 0x4E2C, // ⺦ (0x2ea6) -> 丬 (0x4e2c)
        0x2EB0 to 0x7E9F, // ⺰ (0x2eb0) -> 纟 (0x7e9f)
        0x2EC5 to 0x89C1, // ⻅ (0x2ec5) -> 见 (0x89c1)
        0x2EC8 to 0x8BA0, // ⻈ (0x2ec8) -> 讠 (0x8ba0)
        0x2EC9 to 0x8D1D, // ⻉ (0x2ec9) -> 贝 (0x8d1d)
        0x2ECB to 0x8F66, // ⻋ (0x2ecb) -> 车 (0x8f66)
        0x2ED0 to 0x9485, // ⻐ (0x2ed0) -> 钅 (0x9485)
        0x2ED3 to 0x957F, // ⻓ (0x2ed3) -> 长 (0x957f)
        0x2ED4 to 0x95E8, // ⻔ (0x2ed4) -> 门 (0x95e8)
        0x2ED9 to 0x97E6, // ⻙ (0x2ed9) -> 韦 (0x97e6)
        0x2EDA to 0x9875, // ⻚ (0x2eda) -> 页 (0x9875)
        0x2EDB to 0x98CE, // ⻛ (0x2edb) -> 风 (0x98ce)
        0x2EDC to 0x98DE, // ⻜ (0x2edc) -> 飞 (0x98de)
        0x2EE0 to 0x9963, // ⻠ (0x2ee0) -> 饣 (0x9963)
        0x2EE2 to 0x9A6C, // ⻢ (0x2ee2) -> 马 (0x9a6c)
        0x2EE5 to 0x9C7C, // ⻥ (0x2ee5) -> 鱼 (0x9c7c)
        0x2EE6 to 0x9E1F, // ⻦ (0x2ee6) -> 鸟 (0x9e1f)
        0x2EE7 to 0x5364, // ⻧ (0x2ee7) -> 卤 (0x5364)
        0x2EE8 to 0x9EA6, // ⻨ (0x2ee8) -> 麦 (0x9ea6)
        0x2EE9 to 0x9EC4, // ⻩ (0x2ee9) -> 黄 (0x9ec4)
        0x2EEA to 0x9EFE, // ⻪ (0x2eea) -> 黾 (0x9efe)
        0x2EEB to 0x6589, // ⻫ (0x2eeb) -> 斉 (0x6589)
        0x2EEC to 0x9F50, // ⻬ (0x2eec) -> 齐 (0x9f50)
        0x2EED to 0x6B6F, // ⻭ (0x2eed) -> 歯 (0x6b6f)
        0x2EEE to 0x9F7F, // ⻮ (0x2eee) -> 齿 (0x9f7f)
        0x2EEF to 0x7ADC, // ⻯ (0x2eef) -> 竜 (0x7adc)
        0x2EF0 to 0x9F99, // ⻰ (0x2ef0) -> 龙 (0x9f99)
        0x2EF2 to 0x4E80, // ⻲ (0x2ef2) -> 亀 (0x4e80)
        0x2EF3 to 0x9F9F, // ⻳ (0x2ef3) -> 龟 (0x9f9f)
        0x2F00 to 0x4E00, // ⼀ (0x2f00) -> 一 (0x4e00)
        0x2F01 to 0x4E28, // ⼁ (0x2f01) -> 丨 (0x4e28)
        0x2F02 to 0x4E36, // ⼂ (0x2f02) -> 丶 (0x4e36)
        0x2F03 to 0x4E3F, // ⼃ (0x2f03) -> 丿 (0x4e3f)
        0x2F04 to 0x4E59, // ⼄ (0x2f04) -> 乙 (0x4e59)
        0x2F05 to 0x4E85, // ⼅ (0x2f05) -> 亅 (0x4e85)
        0x2F06 to 0x4E8C, // ⼆ (0x2f06) -> 二 (0x4e8c)
        0x2F07 to 0x4EA0, // ⼇ (0x2f07) -> 亠 (0x4ea0)
        0x2F08 to 0x4EBA, // ⼈ (0x2f08) -> 人 (0x4eba)
        0x2F09 to 0x513F, // ⼉ (0x2f09) -> 儿 (0x513f)
        0x2F0A to 0x5165, // ⼊ (0x2f0a) -> 入 (0x5165)
        0x2F0B to 0x516B, // ⼋ (0x2f0b) -> 八 (0x516b)
        0x2F0C to 0x5182, // ⼌ (0x2f0c) -> 冂 (0x5182)
        0x2F0D to 0x5196, // ⼍ (0x2f0d) -> 安装 (0x5196)
        0x2F0E to 0x51AB, // ⼎ (0x2f0e) -> 冫 (0x51ab)
        0x2F0F to 0x51E0, // ⼏ (0x2f0f) -> 几 (0x51e0)
        0x2F10 to 0x51F5, // ⼐ (0x2f10) -> 凵 (0x51f5)
        0x2F11 to 0x5200, // ⼑ (0x2f11) -> 刀 (0x5200)
        0x2F12 to 0x529B, // ⼒ (0x2f12) -> 力 (0x529b)
        0x2F13 to 0x52F9, // ⼓ (0x2f13) -> 勹 (0x52f9)
        0x2F14 to 0x5315, // ⼔ (0x2f14) -> 匕 (0x5315)
        0x2F15 to 0x531A, // ⼕ (0x2f15) -> 匚 (0x531a)
        0x2F16 to 0x5338, // ⼖ (0x2f16) -> 匸 (0x5338)
        0x2F17 to 0x5341, // ⼗ (0x2f17) -> 十 (0x5341)
        0x2F18 to 0x535C, // ⼘ (0x2f18) -> 卜 (0x535c)
        0x2F19 to 0x5369, // ⼙ (0x2f19) -> 卩 (0x5369)
        0x2F1A to 0x5382, // ⼚ (0x2f1a) -> 厂 (0x5382)
        0x2F1B to 0x53B6, // ⼛ (0x2f1b) -> 厶 (0x53b6)
        0x2F1C to 0x53C8, // ⼜ (0x2f1c) -> 又 (0x53c8)
        0x2F1D to 0x53E3, // ⼝ (0x2f1d) -> 口 (0x53e3)
        0x2F1E to 0x56D7, // ⼞ (0x2f1e) -> 囗 (0x56d7)
        0x2F1F to 0x571F, // ⼟ (0x2f1f) -> 土 (0x571f)
        0x2F20 to 0x58EB, // ⼠ (0x2f20) -> 士 (0x58eb)
        0x2F21 to 0x5902, // ⼡ (0x2f21) -> 夂 (0x5902)
        0x2F22 to 0x590A, // ⼢ (0x2f22) -> 夊 (0x590a)
        0x2F23 to 0x5915, // ⼣ (0x2f23) -> 夕 (0x5915)
        0x2F24 to 0x5927, // ⼤ (0x2f24) -> 大 (0x5927)
        0x2F25 to 0x5973, // ⼥ (0x2f25) -> 女 (0x5973)
        0x2F26 to 0x5B50, // ⼦ (0x2f26) -> 子 (0x5b50)
        0x2F27 to 0x5B80, // ⼧ (0x2f27) -> 宀 (0x5b80)
        0x2F28 to 0x5BF8, // ⼨ (0x2f28) -> 寸 (0x5bf8)
        0x2F29 to 0x5C0F, // ⼩ (0x2f29) -> 小 (0x5c0f)
        0x2F2A to 0x5C22, // ⼪ (0x2f2a) -> 尢 (0x5c22)
        0x2F2B to 0x5C38, // ⼫ (0x2f2b) -> 尸 (0x5c38)
        0x2F2C to 0x5C6E, // ⼬ (0x2f2c) -> 屮 (0x5c6e)
        0x2F2D to 0x5C71, // ⼭ (0x2f2d) -> 山 (0x5c71)
        0x2F2E to 0x5DDB, // ⼮ (0x2f2e) -> 巛 (0x5ddb)
        0x2F2F to 0x5DE5, // ⼯ (0x2f2f) -> 工 (0x5de5)
        0x2F30 to 0x5DF1, // ⼰ (0x2f30) -> 己 (0x5df1)
        0x2F31 to 0x5DFE, // ⼱ (0x2f31) -> 巾 (0x5dfe)
        0x2F32 to 0x5E72, // ⼲ (0x2f32) -> 干 (0x5e72)
        0x2F33 to 0x5E7A, // ⼳ (0x2f33) -> 幺 (0x5e7a)
        0x2F34 to 0x5E7F, // ⼴ (0x2f34) -> 广 (0x5e7f)
        0x2F35 to 0x5EF4, // ⼵ (0x2f35) -> 廴 (0x5ef4)
        0x2F36 to 0x5EFE, // ⼶ (0x2f36) -> 廾 (0x5efe)
        0x2F37 to 0x5F0B, // ⼷ (0x2f37) -> 弋 (0x5f0b)
        0x2F38 to 0x5F13, // ⼸ (0x2f38) -> 弓 (0x5f13)
        0x2F39 to 0x5F50, // ⼹ (0x2f39) -> 彐 (0x5f50)
        0x2F3A to 0x5F61, // ⼺ (0x2f3a) -> 彡 (0x5f61)
        0x2F3B to 0x5F73, // ⼻ (0x2f3b) -> 彳 (0x5f73)
        0x2F3C to 0x5FC3, // ⼼ (0x2f3c) -> 心 (0x5fc3)
        0x2F3D to 0x6208, // ⼽ (0x2f3d) -> 戈 (0x6208)
        0x2F3E to 0x6236, // ⼾ (0x2f3e) -> 戶 (0x6236)
        0x2F3F to 0x624B, // ⼿ (0x2f3f) -> 手 (0x624b)
        0x2F40 to 0x652F, // ⽀ (0x2f40) -> 支 (0x652f)
        0x2F41 to 0x6534, // ⽁ (0x2f41) -> 攴 (0x6534)
        0x2F42 to 0x6587, // ⽂ (0x2f42) -> 文 (0x6587)
        0x2F43 to 0x6597, // ⽃ (0x2f43) -> 斗 (0x6597)
        0x2F44 to 0x65A4, // ⽄ (0x2f44) -> 斤 (0x65a4)
        0x2F45 to 0x65B9, // ⽅ (0x2f45) -> 方 (0x65b9)
        0x2F46 to 0x65E0, // ⽆ (0x2f46) -> 无 (0x65e0)
        0x2F47 to 0x65E5, // ⽇ (0x2f47) -> 日 (0x65e5)
        0x2F48 to 0x66F0, // ⽈ (0x2f48) -> 曰 (0x66f0)
        0x2F49 to 0x6708, // ⽉ (0x2f49) -> 月 (0x6708)
        0x2F4A to 0x6728, // ⽊ (0x2f4a) -> 木 (0x6728)
        0x2F4B to 0x6B20, // ⽋ (0x2f4b) -> 欠 (0x6b20)
        0x2F4C to 0x6B62, // ⽌ (0x2f4c) -> 止 (0x6b62)
        0x2F4D to 0x6B79, // ⽍ (0x2f4d) -> 歹 (0x6b79)
        0x2F4E to 0x6BB3, // ⽎ (0x2f4e) -> 殳 (0x6bb3)
        0x2F4F to 0x6BCB, // ⽏ (0x2f4f) -> 毋 (0x6bcb)
        0x2F50 to 0x6BD4, // ⽐ (0x2f50) -> 比 (0x6bd4)
        0x2F51 to 0x6BDB, // ⽑ (0x2f51) -> 毛 (0x6bdb)
        0x2F52 to 0x6C0F, // ⽒ (0x2f52) -> 氏 (0x6c0f)
        0x2F53 to 0x6C14, // ⽓ (0x2f53) -> 气 (0x6c14)
        0x2F54 to 0x6C34, // ⽔ (0x2f54) -> 水 (0x6c34)
        0x2F55 to 0x706B, // ⽕ (0x2f55) -> 火 (0x706b)
        0x2F56 to 0x722A, // ⽖ (0x2f56) -> 爪 (0x722a)
        0x2F57 to 0x7236, // ⽗ (0x2f57) -> 父 (0x7236)
        0x2F58 to 0x723B, // ⽘ (0x2f58) -> 爻 (0x723b)
        0x2F59 to 0x723F, // ⽙ (0x2f59) -> 爿 (0x723f)
        0x2F5A to 0x7247, // ⽚ (0x2f5a) -> 片 (0x7247)
        0x2F5B to 0x7259, // ⽛ (0x2f5b) -> 牙 (0x7259)
        0x2F5C to 0x725B, // ⽜ (0x2f5c) -> 牛 (0x725b)
        0x2F5D to 0x72AC, // ⽝ (0x2f5d) -> 犬 (0x72ac)
        0x2F5E to 0x7384, // ⽞ (0x2f5e) -> 玄 (0x7384)
        0x2F5F to 0x7389, // ⽟ (0x2f5f) -> 玉 (0x7389)
        0x2F60 to 0x74DC, // ⽠ (0x2f60) -> 瓜 (0x74dc)
        0x2F61 to 0x74E6, // ⽡ (0x2f61) -> 瓦 (0x74e6)
        0x2F62 to 0x7518, // ⽢ (0x2f62) -> 甘 (0x7518)
        0x2F63 to 0x751F, // ⽣ (0x2f63) -> 生 (0x751f)
        0x2F64 to 0x7528, // ⽤ (0x2f64) -> 用 (0x7528)
        0x2F65 to 0x7530, // ⽥ (0x2f65) -> 田 (0x7530)
        0x2F66 to 0x758B, // ⽦ (0x2f66) -> 疋 (0x758b)
        0x2F67 to 0x7592, // ⽧ (0x2f67) -> 疒 (0x7592)
        0x2F68 to 0x7676, // ⽨ (0x2f68) -> 癶 (0x7676)
        0x2F69 to 0x767D, // ⽩ (0x2f69) -> 白 (0x767d)
        0x2F6A to 0x76AE, // ⽪ (0x2f6a) -> 皮 (0x76ae)
        0x2F6B to 0x76BF, // ⽫ (0x2f6b) -> 皿 (0x76bf)
        0x2F6C to 0x76EE, // ⽬ (0x2f6c) -> 目 (0x76ee)
        0x2F6D to 0x77DB, // ⽭ (0x2f6d) -> 矛 (0x77db)
        0x2F6E to 0x77E2, // ⽮ (0x2f6e) -> 矢 (0x77e2)
        0x2F6F to 0x77F3, // ⽯ (0x2f6f) -> 石 (0x77f3)
        0x2F70 to 0x793A, // ⽰ (0x2f70) -> 示 (0x793a)
        0x2F71 to 0x79B8, // ⽱ (0x2f71) -> 禸 (0x79b8)
        0x2F72 to 0x79BE, // ⽲ (0x2f72) -> 禾 (0x79be)
        0x2F73 to 0x7A74, // ⽳ (0x2f73) -> 穴 (0x7a74)
        0x2F74 to 0x7ACB, // ⽴ (0x2f74) -> 立 (0x7acb)
        0x2F75 to 0x7AF9, // ⽵ (0x2f75) -> 竹 (0x7af9)
        0x2F76 to 0x7C73, // ⽶ (0x2f76) -> 米 (0x7c73)
        0x2F77 to 0x7CF8, // ⽷ (0x2f77) -> 糸 (0x7cf8)
        0x2F78 to 0x7F36, // ⽸ (0x2f78) -> 缶 (0x7f36)
        0x2F79 to 0x7F51, // ⽹ (0x2f79) -> 网 (0x7f51)
        0x2F7A to 0x7F8A, // ⽺ (0x2f7a) -> 羊 (0x7f8a)
        0x2F7B to 0x7FBD, // ⽻ (0x2f7b) -> 羽 (0x7fbd)
        0x2F7C to 0x8001, // ⽼ (0x2f7c) -> 老 (0x8001)
        0x2F7D to 0x800C, // ⽽ (0x2f7d) -> 而 (0x800c)
        0x2F7E to 0x8012, // ⽾ (0x2f7e) -> 耒 (0x8012)
        0x2F7F to 0x8033, // ⽿ (0x2f7f) -> 耳 (0x8033)
        0x2F80 to 0x807F, // ⾀ (0x2f80) -> 聿 (0x807f)
        0x2F81 to 0x8089, // ⾁ (0x2f81) -> 肉 (0x8089)
        0x2F82 to 0x81E3, // ⾂ (0x2f82) -> 臣 (0x81e3)
        0x2F83 to 0x81EA, // ⾃ (0x2f83) -> 自 (0x81ea)
        0x2F84 to 0x81F3, // ⾄ (0x2f84) -> 至 (0x81f3)
        0x2F85 to 0x81FC, // ⾅ (0x2f85) -> 臼 (0x81fc)
        0x2F86 to 0x820C, // ⾆ (0x2f86) -> 舌 (0x820c)
        0x2F87 to 0x821B, // ⾇ (0x2f87) -> 舛 (0x821b)
        0x2F88 to 0x821F, // ⾈ (0x2f88) -> 舟 (0x821f)
        0x2F89 to 0x826E, // ⾉ (0x2f89) -> 艮 (0x826e)
        0x2F8A to 0x8272, // ⾊ (0x2f8a) -> 色 (0x8272)
        0x2F8B to 0x8278, // ⾋ (0x2f8b) -> 艸 (0x8278)
        0x2F8C to 0x864D, // ⾌ (0x2f8c) -> 虍 (0x864d)
        0x2F8D to 0x866B, // ⾍ (0x2f8d) -> 虫 (0x866b)
        0x2F8E to 0x8840, // ⾎ (0x2f8e) -> 血 (0x8840)
        0x2F8F to 0x884C, // ⾏ (0x2f8f) -> 行 (0x884c)
        0x2F90 to 0x8863, // ⾐ (0x2f90) -> 衣 (0x8863)
        0x2F91 to 0x897E, // ⾑ (0x2f91) -> 襾 (0x897e)
        0x2F92 to 0x898B, // ⾒ (0x2f92) -> 見 (0x898b)
        0x2F93 to 0x89D2, // ⾓ (0x2f93) -> 角 (0x89d2)
        0x2F94 to 0x8A00, // ⾔ (0x2f94) -> 言 (0x8a00)
        0x2F95 to 0x8C37, // ⾕ (0x2f95) -> 谷 (0x8c37)
        0x2F96 to 0x8C46, // ⾖ (0x2f96) -> 豆 (0x8c46)
        0x2F97 to 0x8C55, // ⾗ (0x2f97) -> 豕 (0x8c55)
        0x2F98 to 0x8C78, // ⾘ (0x2f98) -> 豸 (0x8c78)
        0x2F99 to 0x8C9D, // ⾙ (0x2f99) -> 貝 (0x8c9d)
        0x2F9B to 0x8D70, // ⾛ (0x2f9b) -> 走 (0x8d70)
        0x2F9C to 0x8DB3, // ⾜ (0x2f9c) -> 足 (0x8db3)
        0x2F9D to 0x8EAB, // ⾝ (0x2f9d) -> 身 (0x8eab)
        0x2F9E to 0x8ECA, // ⾞ (0x2f9e) -> 車 (0x8eca)
        0x2F9F to 0x8F9B, // ⾟ (0x2f9f) -> 辛 (0x8f9b)
        0x2FA0 to 0x8FB0, // ⾠ (0x2fa0) -> 辰 (0x8fb0)
        0x2FA1 to 0x8FB5, // ⾡ (0x2fa1) -> 辵 (0x8fb5)
        0x2FA2 to 0x9091, // ⾢ (0x2fa2) -> 邑 (0x9091)
        0x2FA3 to 0x9149, // ⾣ (0x2fa3) -> 酉 (0x9149)
        0x2FA4 to 0x91C6, // ⾤ (0x2fa4) -> 釆 (0x91c6)
        0x2FA5 to 0x91CC, // ⾥ (0x2fa5) -> 里 (0x91cc)
        0x2FA6 to 0x91D1, // ⾦ (0x2fa6) -> 金 (0x91d1)
        0x2FA7 to 0x9577, // ⾧ (0x2fa7) -> 長 (0x9577)
        0x2FA8 to 0x9580, // ⾨ (0x2fa8) -> 門 (0x9580)
        0x2FA9 to 0x961C, // ⾩ (0x2fa9) -> 阜 (0x961c)
        0x2FAA to 0x96B6, // ⾪ (0x2faa) -> 隶 (0x96b6)
        0x2FAB to 0x96B9, // ⾫ (0x2fab) -> 隹 (0x96b9)
        0x2FAC to 0x96E8, // ⾬ (0x2fac) -> 雨 (0x96e8)
        0x2FAD to 0x9751, // ⾭ (0x2fad) -> 靑 (0x9751)
        0x2FAE to 0x975E, // ⾮ (0x2fae) -> 非 (0x975e)
        0x2FAF to 0x9762, // ⾯ (0x2faf) -> 面 (0x9762)
        0x2FB0 to 0x9769, // ⾰ (0x2fb0) -> 革 (0x9769)
        0x2FB1 to 0x97CB, // ⾱ (0x2fb1) -> 韋 (0x97cb)
        0x2FB2 to 0x97ED, // ⾲ (0x2fb2) -> 韭 (0x97ed)
        0x2FB3 to 0x97F3, // ⾳ (0x2fb3) -> 音 (0x97f3)
        0x2FB4 to 0x9801, // ⾴ (0x2fb4) -> 頁 (0x9801)
        0x2FB5 to 0x98A8, // ⾵ (0x2fb5) -> 風 (0x98a8)
        0x2FB6 to 0x98DB, // ⾶ (0x2fb6) -> 飛 (0x98db)
        0x2FB7 to 0x98DF, // ⾷ (0x2fb7) -> 食 (0x98df)
        0x2FB8 to 0x9996, // ⾸ (0x2fb8) -> 首 (0x9996)
        0x2FB9 to 0x9999, // ⾹ (0x2fb9) -> 香 (0x9999)
        0x2FBA to 0x99AC, // ⾺ (0x2fba) -> 馬 (0x99ac)
        0x2FBB to 0x9AA8, // ⾻ (0x2fbb) -> 骨 (0x9aa8)
        0x2FBC to 0x9AD8, // ⾼ (0x2fbc) -> 高 (0x9ad8)
        0x2FBD to 0x9ADF, // ⾽ (0x2fbd) -> 髟 (0x9adf)
        0x2FBE to 0x9B25, // ⾾ (0x2fbe) -> 鬥 (0x9b25)
        0x2FBF to 0x9B2F, // ⾿ (0x2fbf) -> 鬯 (0x9b2f)
        0x2FC0 to 0x9B32, // ⿀ (0x2fc0) -> 鬲 (0x9b32)
        0x2FC1 to 0x9B3C, // ⿁ (0x2fc1) -> 鬼 (0x9b3c)
        0x2FC2 to 0x9B5A, // ⿂ (0x2fc2) -> 魚 (0x9b5a)
        0x2FC3 to 0x9CE5, // ⿃ (0x2fc3) -> 鳥 (0x9ce5)
        0x2FC4 to 0x9E75, // ⿄ (0x2fc4) -> 鹵 (0x9e75)
        0x2FC5 to 0x9E7F, // ⿅ (0x2fc5) -> 鹿 (0x9e7f)
        0x2FC6 to 0x9EA5, // ⿆ (0x2fc6) -> 麥 (0x9ea5)
        0x2FC7 to 0x9EBB, // ⿇ (0x2fc7) -> 麻 (0x9ebb)
        0x2FC8 to 0x9EC3, // ⿈ (0x2fc8) -> 黃 (0x9ec3)
        0x2FC9 to 0x9ECD, // ⿉ (0x2fc9) -> 黍 (0x9ecd)
        0x2FCA to 0x9ED1, // ⿊ (0x2fca) -> 黑 (0x9ed1)
        0x2FCB to 0x9EF9, // ⿋ (0x2fcb) -> 黹 (0x9ef9)
        0x2FCC to 0x9EFD, // ⿌ (0x2fcc) -> 黽 (0x9efd)
        0x2FCD to 0x9F0E, // ⿍ (0x2fcd) -> 鼎 (0x9f0e)
        0x2FCE to 0x9F13, // ⿎ (0x2fce) -> 鼓 (0x9f13)
        0x2FCF to 0x9F20, // ⿏ (0x2fcf) -> 鼠 (0x9f20)
        0x2FD0 to 0x9F3B, // ⿐ (0x2fd0) -> 鼻 (0x9f3b)
        0x2FD1 to 0x9F4A, // ⿑ (0x2fd1) -> 齊 (0x9f4a)
        0x2FD2 to 0x9F52, // ⿒ (0x2fd2) -> 齒 (0x9f52)
        0x2FD3 to 0x9F8D, // ⿓ (0x2fd3) -> 龍 (0x9f8d)
        0x2FD4 to 0x9F9C, // ⿔ (0x2fd4) -> 龜 (0x9f9c)
        0x2FD5 to 0x9FA0, // ⿕ (0x2fd5) -> 龠 (0x9fa0)
    )


    /**
     * Resolve raw symbol code to the format used in the app.
     * - HK: "01810" → "1810.HK"
     * - US: "SNDK" → "SNDK"
     * - A-share: "600519" → "600519"
     */
    private fun getTradeTimeForMarket(market: Market): String = when (market) {
        Market.US -> "21:35"
        else -> "09:35"
    }

    private fun getHourForMarket(market: Market): Int = when (market) {
        Market.US -> 21
        else -> 9
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
        Market.A_SHARE -> raw.filter(Char::isDigit)
        Market.US -> raw.uppercase()
        Market.CASH -> raw
    }

    /**
     * Extract text from PDF using standard PDFTextStripper with sortByPosition.
     * This produces per-line output matching the visual layout of the PDF table.
     */
    private fun extractText(inputStream: InputStream, password: String?): String {
        return try {
            val doc = if (!password.isNullOrBlank()) {
                PDDocument.load(inputStream, password).apply {
                    if (isEncrypted) setAllSecurityToBeRemoved(true)
                }
            } else {
                PDDocument.load(inputStream)
            }
            doc.use { document ->
                Log.d(TAG, "PDF loaded, pages=${document.numberOfPages}")
                val stripper = PDFTextStripper().apply {
                    sortByPosition = true
                }
                stripper.getText(document)
            }
        } catch (e: Exception) {
            Log.e(TAG, "uSMART PDF extraction failed", e)
            throw e
        }
    }
}
