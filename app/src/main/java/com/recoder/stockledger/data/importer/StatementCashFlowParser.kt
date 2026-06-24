package com.recoder.stockledger.data.importer

import com.recoder.stockledger.data.ImportSourceChannel
import com.recoder.stockledger.data.Market
import com.recoder.stockledger.data.TradeType
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

object StatementCashFlowParser {
    private val isoDateRegex = Regex("""\b\d{4}-\d{2}-\d{2}\b""")
    private val dotDateRegex = Regex("""\b\d{4}\.\d{2}\.\d{2}\b""")
    private val hsbcDateRegex = Regex("""\b\d{1,2}[A-Z]{3}\d{4}\b""", RegexOption.IGNORE_CASE)
    private val numberRegex = Regex("""[-+]?\d[\d,]*(?:\.\d+)?-?""")
    private val rateRegex = Regex("""@\s*\d+(?:\.\d+)?""")
    private val monthMap = mapOf(
        "JAN" to 1, "FEB" to 2, "MAR" to 3, "APR" to 4,
        "MAY" to 5, "JUN" to 6, "JUL" to 7, "AUG" to 8,
        "SEP" to 9, "OCT" to 10, "NOV" to 11, "DEC" to 12
    )

    private data class CashContext(val market: Market, val currencyCode: String)

    private data class FxDetails(
        val fromCurrency: String,
        val toCurrency: String?,
        val rate: Double?
    )

    private data class CashMovement(
        val type: TradeType,
        val date: LocalDate,
        val context: CashContext,
        val amount: Double,
        val symbol: String?,
        val description: String,
        val rawLine: String,
        val sourceIndex: Int,
        val fxDetails: FxDetails?
    )

    fun parse(
        lines: List<String>,
        platformPrefix: String,
        compactCentAmount: Boolean = false,
        skipLine: (String) -> Boolean = { false },
    ): List<ParsedStatementTrade> {
        val movements = mutableListOf<CashMovement>()
        var currentContext: CashContext? = null

        lines.forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isBlank() || skipLine(line)) return@forEachIndexed
            cashContextFromHeader(line)?.let {
                currentContext = it
                return@forEachIndexed
            }
            parseMovementLine(line, index, currentContext, compactCentAmount)?.let { movements.add(it) }
        }

        return buildTrades(movements, platformPrefix)
    }

    private fun parseMovementLine(
        line: String,
        sourceIndex: Int,
        fallbackContext: CashContext?,
        compactCentAmount: Boolean,
    ): CashMovement? {
        val dateHit = extractDate(line) ?: return null
        val date = dateHit.first
        val textWithoutDate = line.replace(dateHit.second, " ")
        val type = classify(textWithoutDate) ?: return null
        val fxDetails = if (type == TradeType.FX_CONVERSION) extractFxDetails(textWithoutDate) else null
        val explicitContext = extractCurrency(textWithoutDate)?.let(::cashContextFromCurrency)
        val context = when {
            type == TradeType.FX_CONVERSION && isFxOut(textWithoutDate) && fxDetails != null ->
                cashContextFromCurrency(fxDetails.fromCurrency)
            type == TradeType.FX_CONVERSION && isFxIn(textWithoutDate) && fxDetails?.toCurrency != null ->
                cashContextFromCurrency(fxDetails.toCurrency)
            else -> explicitContext ?: fallbackContext
        } ?: return null

        val amount = extractAmount(textWithoutDate, compactCentAmount) ?: return null
        if (amount == 0.0) return null

        val symbol = when (type) {
            TradeType.DIVIDEND, TradeType.TAX -> extractSecuritySymbol(textWithoutDate)
            else -> null
        }

        return CashMovement(
            type = type,
            date = date,
            context = context,
            amount = amount,
            symbol = symbol,
            description = textWithoutDate.replace(Regex("""\s+"""), " ").trim(),
            rawLine = line,
            sourceIndex = sourceIndex,
            fxDetails = fxDetails
        )
    }

    private fun buildTrades(
        movements: List<CashMovement>,
        platformPrefix: String,
    ): List<ParsedStatementTrade> {
        val dividends = movements.filter { it.type == TradeType.DIVIDEND }
        val taxes = movements.filter { it.type == TradeType.TAX }
        val usedTaxIndexes = mutableSetOf<Int>()
        val result = mutableListOf<ParsedStatementTrade>()

        val fxMovements = movements.filter { it.type == TradeType.FX_CONVERSION }
        result.addAll(buildFxTrades(fxMovements, platformPrefix))

        dividends.forEach { dividend ->
            val tax = taxes.firstOrNull { candidate ->
                candidate.sourceIndex !in usedTaxIndexes && isDividendTaxPair(dividend, candidate)
            }
            if (tax != null) usedTaxIndexes.add(tax.sourceIndex)
            result.add(dividendToTrade(dividend, tax, platformPrefix))
        }

        taxes.filter { it.sourceIndex !in usedTaxIndexes }
            .forEach { result.add(cashMovementToTrade(it, platformPrefix)) }

        movements
            .filter { it.type != TradeType.DIVIDEND && it.type != TradeType.TAX && it.type != TradeType.FX_CONVERSION }
            .forEach { result.add(cashMovementToTrade(it, platformPrefix)) }

        return result.sortedWith(compareBy<ParsedStatementTrade> { it.tradeDate }.thenBy { it.createdAt ?: 0L })
    }

    private fun buildFxTrades(
        movements: List<CashMovement>,
        platformPrefix: String,
    ): List<ParsedStatementTrade> {
        val used = mutableSetOf<Int>()
        val trades = mutableListOf<ParsedStatementTrade>()
        movements.forEach { movement ->
            if (movement.sourceIndex in used) return@forEach
            val pair = movements.firstOrNull { candidate ->
                candidate.sourceIndex !in used &&
                    candidate.sourceIndex != movement.sourceIndex &&
                    isFxPair(movement, candidate)
            }
            used.add(movement.sourceIndex)
            if (pair != null) used.add(pair.sourceIndex)
            trades.add(fxMovementToTrade(movement, pair, platformPrefix))
        }
        return trades
    }

    private fun dividendToTrade(
        dividend: CashMovement,
        tax: CashMovement?,
        platformPrefix: String,
    ): ParsedStatementTrade {
        val gross = abs(dividend.amount)
        val taxAmount = tax?.amount?.let(::abs) ?: 0.0
        val symbol = dividend.symbol ?: tax?.symbol ?: "CASH"
        val ordered = listOfNotNull(dividend, tax).sortedBy { it.sourceIndex }
        val ref = listOf(
            platformPrefix,
            "DIV",
            compactDate(dividend.date),
            dividend.context.currencyCode,
            symbol,
            formatRefNumber(gross),
            formatRefNumber(taxAmount),
            ordered.first().sourceIndex.toString()
        ).joinToString("-")

        return baseTrade(
            movement = dividend,
            tradeType = TradeType.DIVIDEND,
            symbol = symbol,
            name = if (symbol == "CASH") "现金分红" else symbol,
            amount = gross,
            tradeRef = ref,
            rawLine = ordered.joinToString(" | ") { it.rawLine },
            tax = taxAmount
        )
    }

    private fun cashMovementToTrade(
        movement: CashMovement,
        platformPrefix: String,
    ): ParsedStatementTrade {
        val amount = if (movement.type == TradeType.OTHER) {
            if (isOtherExpense(movement.description)) -abs(movement.amount) else movement.amount
        } else {
            abs(movement.amount)
        }
        val symbol = when (movement.type) {
            TradeType.TAX -> movement.symbol ?: "CASH"
            TradeType.OTHER, TradeType.DEPOSIT, TradeType.WITHDRAW -> "CASH"
            else -> movement.symbol ?: "CASH"
        }
        val refType = when (movement.type) {
            TradeType.TAX -> "TAX"
            TradeType.OTHER -> "OTH"
            TradeType.DEPOSIT -> "DEP"
            TradeType.WITHDRAW -> "WTH"
            else -> movement.type.name
        }
        val ref = listOf(
            platformPrefix,
            refType,
            compactDate(movement.date),
            movement.context.currencyCode,
            formatRefNumber(amount),
            movement.sourceIndex.toString()
        ).joinToString("-")
        val name = when (movement.type) {
            TradeType.TAX -> if (symbol == "CASH") "税费" else symbol
            TradeType.OTHER -> movement.description.ifBlank { "其他" }
            TradeType.DEPOSIT -> "存入资金"
            TradeType.WITHDRAW -> "提取资金"
            else -> movement.description
        }

        return baseTrade(
            movement = movement,
            tradeType = movement.type,
            symbol = symbol,
            name = name,
            amount = amount,
            tradeRef = ref,
            rawLine = movement.rawLine,
            tax = if (movement.type == TradeType.TAX) 0.0 else 0.0
        )
    }

    private fun fxMovementToTrade(
        movement: CashMovement,
        pair: CashMovement?,
        platformPrefix: String,
    ): ParsedStatementTrade {
        val ordered = listOfNotNull(movement, pair).sortedBy { it.sourceIndex }
        val outMovement = ordered.firstOrNull { isFxOut(it.description) || it.amount < 0 }
        val inMovement = ordered.firstOrNull { isFxIn(it.description) || it.amount > 0 }
        val details = ordered.mapNotNull { it.fxDetails }.firstOrNull()
        val fromCurrency = details?.fromCurrency ?: outMovement?.context?.currencyCode ?: movement.context.currencyCode
        val toCurrency = details?.toCurrency ?: inMovement?.context?.currencyCode
        val fromAmount = outMovement?.amount?.let(::abs)
        val toAmount = inMovement?.amount?.let(::abs)
        val displayAmount = fromAmount ?: toAmount ?: abs(movement.amount)
        val primaryContext = cashContextFromCurrency(fromCurrency)
        val pairLabel = if (toCurrency != null) "$fromCurrency->$toCurrency" else fromCurrency
        val refParts = mutableListOf(
            platformPrefix,
            "FX",
            compactDate(movement.date),
            pairLabel,
            fromAmount?.let(::formatRefNumber) ?: "NA",
            toAmount?.let(::formatRefNumber) ?: "NA"
        )
        details?.rate?.let { refParts.add(formatRefNumber(it)) }
        refParts.add(ordered.first().sourceIndex.toString())

        return ParsedStatementTrade(
            sourceChannel = ImportSourceChannel.PDF_STATEMENT,
            tradeType = TradeType.FX_CONVERSION,
            market = primaryContext.market,
            symbol = "CASH",
            name = if (toCurrency != null) "货币兑换 $fromCurrency -> $toCurrency" else "货币兑换 $fromCurrency",
            currencyCode = primaryContext.currencyCode,
            price = displayAmount,
            quantity = 1.0,
            amount = displayAmount,
            tradeDate = movement.date,
            tradeTime = tradeTimeForMarket(primaryContext.market),
            commission = 0.0,
            platformFee = 0.0,
            tax = 0.0,
            tradeRef = refParts.joinToString("-"),
            rawLine = ordered.joinToString(" | ") { it.rawLine },
            createdAt = createdAt(movement.date, primaryContext.market),
            assetType = "STOCK",
            fxFromCurrency = fromCurrency,
            fxFromAmount = fromAmount,
            fxToCurrency = toCurrency,
            fxToAmount = toAmount,
            fxRate = details?.rate
        )
    }

    private fun baseTrade(
        movement: CashMovement,
        tradeType: TradeType,
        symbol: String,
        name: String,
        amount: Double,
        tradeRef: String,
        rawLine: String,
        tax: Double,
    ): ParsedStatementTrade {
        return ParsedStatementTrade(
            sourceChannel = ImportSourceChannel.PDF_STATEMENT,
            tradeType = tradeType,
            market = movement.context.market,
            symbol = symbol,
            name = name,
            currencyCode = movement.context.currencyCode,
            price = amount,
            quantity = 1.0,
            amount = amount,
            tradeDate = movement.date,
            tradeTime = tradeTimeForMarket(movement.context.market),
            commission = 0.0,
            platformFee = 0.0,
            tax = tax,
            tradeRef = tradeRef,
            rawLine = rawLine,
            createdAt = createdAt(movement.date, movement.context.market),
            assetType = "STOCK"
        )
    }

    private fun classify(line: String): TradeType? {
        return when {
            isFx(line) -> TradeType.FX_CONVERSION
            isOther(line) -> TradeType.OTHER
            isTax(line) -> TradeType.TAX
            isDividend(line) -> TradeType.DIVIDEND
            isDeposit(line) -> TradeType.DEPOSIT
            isWithdraw(line) -> TradeType.WITHDRAW
            else -> null
        }
    }

    private fun isDividendTaxPair(dividend: CashMovement, tax: CashMovement): Boolean {
        if (dividend.date != tax.date) return false
        if (dividend.context.currencyCode != tax.context.currencyCode) return false
        val dividendSymbol = dividend.symbol ?: "CASH"
        val taxSymbol = tax.symbol ?: "CASH"
        if (dividendSymbol != "CASH" && taxSymbol != "CASH" && dividendSymbol != taxSymbol) return false
        return normalizePairText(dividend.description).contains(normalizePairText(tax.description)) ||
            normalizePairText(tax.description).contains(normalizePairText(dividend.description)) ||
            dividendSymbol == taxSymbol
    }

    private fun isFxPair(first: CashMovement, second: CashMovement): Boolean {
        if (first.date != second.date) return false
        if (isFxOut(first.description) == isFxOut(second.description)) return false
        if (isFxIn(first.description) == isFxIn(second.description)) return false

        val firstDetails = first.fxDetails
        val secondDetails = second.fxDetails
        if (firstDetails != null && secondDetails != null) {
            if (firstDetails.fromCurrency != secondDetails.fromCurrency) return false
            if (firstDetails.toCurrency != secondDetails.toCurrency) return false
            val firstRate = firstDetails.rate
            val secondRate = secondDetails.rate
            if (firstRate != null && secondRate != null && abs(firstRate - secondRate) > 0.0000001) return false
        }
        return true
    }

    private fun isDividend(line: String): Boolean {
        return line.contains("现金分红") ||
            line.contains("代收股息") ||
            line.contains("股息", ignoreCase = true) ||
            line.contains("DIVIDEND", ignoreCase = true) ||
            line.contains("PAYMENT IN LIEU", ignoreCase = true)
    }

    private fun isTax(line: String): Boolean {
        return line.contains("股息税") ||
            line.contains("扣收") ||
            line.contains("公司行动其他费用") ||
            line.contains("WITHHOLDING TAX", ignoreCase = true) ||
            line.contains("DIVIDEND FEE", ignoreCase = true) ||
            line.contains(" TAX", ignoreCase = true)
    }

    private fun isOther(line: String): Boolean {
        return line.contains("活动礼包") ||
            line.contains("现金奖励") ||
            line.contains("CASH REWARD", ignoreCase = true) ||
            line.contains("PROMOTION REWARD", ignoreCase = true) ||
            line.contains("BONUS", ignoreCase = true) ||
            isOtherExpense(line)
    }

    private fun isOtherExpense(line: String): Boolean {
        return line.contains("SAFE CUSTODY CHARGE", ignoreCase = true) ||
            line.contains("SAFE CUSTODY", ignoreCase = true) ||
            line.contains("TRADE25", ignoreCase = true) ||
            line.contains("TRADE 25", ignoreCase = true)
    }

    private fun isDeposit(line: String): Boolean {
        return (line.contains("存入资金") || line.contains("入金") || line.contains("DEPOSIT", ignoreCase = true)) && !isFx(line)
    }

    private fun isWithdraw(line: String): Boolean {
        return (line.contains("提取资金") || line.contains("出金") || line.contains("WITHDRAW", ignoreCase = true) || line.contains("WITHDRAWAL", ignoreCase = true)) && !isFx(line)
    }

    private fun isFx(line: String): Boolean {
        return line.contains("货币兑换") ||
            line.contains("貨幣兌換") ||
            line.contains("换汇") ||
            line.contains("換匯") ||
            line.contains("FOREIGN EXCHANGE", ignoreCase = true) ||
            line.contains("FX CONVERSION", ignoreCase = true)
    }

    private fun isFxOut(line: String): Boolean {
        return line.contains("出账") ||
            line.contains("出賬") ||
            line.contains("OUT", ignoreCase = true) ||
            line.contains("DEBIT", ignoreCase = true)
    }

    private fun isFxIn(line: String): Boolean {
        return line.contains("入账") ||
            line.contains("入賬") ||
            line.contains(" IN ", ignoreCase = true) ||
            line.contains("CREDIT", ignoreCase = true)
    }

    private fun extractFxDetails(line: String): FxDetails? {
        Regex(
            """\b(USD|HKD|CNY)\b\s*(?:换汇至|換匯至|至|TO)\s*(USD|HKD|CNY)\b(?:\s*@\s*([0-9]+(?:\.[0-9]+)?))?""",
            RegexOption.IGNORE_CASE
        ).find(line)?.let { match ->
            return FxDetails(
                fromCurrency = match.groupValues[1].uppercase(Locale.US),
                toCurrency = match.groupValues[2].uppercase(Locale.US),
                rate = match.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }?.toDoubleOrNull()
            )
        }

        val match = Regex(
            """\b(USD|HKD|CNY)\b.*?(?:换汇至|換匯至|至|TO)\s*(USD|HKD|CNY)\b(?:\s*@\s*([0-9]+(?:\.[0-9]+)?))?""",
            RegexOption.IGNORE_CASE
        ).find(line) ?: return null
        return FxDetails(
            fromCurrency = match.groupValues[1].uppercase(Locale.US),
            toCurrency = match.groupValues[2].takeIf { it.isNotBlank() }?.uppercase(Locale.US),
            rate = match.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }?.toDoubleOrNull()
        )
    }

    private fun extractDate(line: String): Pair<LocalDate, String>? {
        isoDateRegex.find(line)?.let {
            return LocalDate.parse(it.value, DateTimeFormatter.ISO_LOCAL_DATE) to it.value
        }
        dotDateRegex.find(line)?.let {
            return LocalDate.parse(it.value, DateTimeFormatter.ofPattern("yyyy.MM.dd")) to it.value
        }
        hsbcDateRegex.find(line)?.let { match ->
            parseHsbcDate(match.value)?.let { return it to match.value }
        }
        return null
    }

    private fun parseHsbcDate(value: String): LocalDate? {
        val match = Regex("""^(\d{1,2})([A-Z]{3})(\d{4})$""", RegexOption.IGNORE_CASE).find(value) ?: return null
        val day = match.groupValues[1].toInt()
        val month = monthMap[match.groupValues[2].uppercase(Locale.US)] ?: return null
        val year = match.groupValues[3].toInt()
        return LocalDate.of(year, month, day)
    }

    private fun extractCurrency(line: String): String? {
        Regex("""\b(USD|HKD|CNY)\b""", RegexOption.IGNORE_CASE).find(line)?.let {
            return it.groupValues[1].uppercase(Locale.US)
        }
        return when {
            line.contains("港元") -> "HKD"
            line.contains("美元") -> "USD"
            line.contains("人民币") -> "CNY"
            else -> null
        }
    }

    private fun extractAmount(line: String, compactCentAmount: Boolean): Double? {
        val withoutRates = rateRegex.replace(line, " ")
        return numberRegex.findAll(withoutRates)
            .mapNotNull { parseAmount(it.value, compactCentAmount) }
            .lastOrNull()
    }

    private fun parseAmount(raw: String, compactCentAmount: Boolean): Double? {
        var value = raw.trim()
        var negative = false
        if (value.endsWith("-")) {
            negative = true
            value = value.dropLast(1)
        }
        if (value.startsWith("-")) {
            negative = true
            value = value.drop(1)
        }
        val normalized = value.replace(",", "")
        val parsed = if (compactCentAmount &&
            !normalized.contains(".") &&
            normalized.length > 2 &&
            normalized.all(Char::isDigit)
        ) {
            val whole = normalized.dropLast(2).ifBlank { "0" }
            val cents = normalized.takeLast(2)
            "$whole.$cents".toDoubleOrNull()
        } else {
            normalized.toDoubleOrNull()
        } ?: return null
        return if (negative) -parsed else parsed
    }

    private fun cashContextFromHeader(line: String): CashContext? {
        val compact = line.trim().uppercase(Locale.US)
        return when (compact) {
            "HKD", "港元" -> cashContextFromCurrency("HKD")
            "USD", "美元" -> cashContextFromCurrency("USD")
            "CNY", "人民币" -> cashContextFromCurrency("CNY")
            else -> null
        }
    }

    private fun cashContextFromCurrency(currencyCode: String): CashContext {
        return when (currencyCode.uppercase(Locale.US)) {
            "USD" -> CashContext(Market.US, "USD")
            "HKD" -> CashContext(Market.HK, "HKD")
            "CNY" -> CashContext(Market.CASH, "CNY")
            else -> CashContext(Market.CASH, currencyCode.uppercase(Locale.US))
        }
    }

    private fun extractSecuritySymbol(text: String): String? {
        Regex("""\b([A-Z][A-Z0-9.\-]*)\.US\b""", RegexOption.IGNORE_CASE).find(text)?.let {
            return it.groupValues[1].uppercase(Locale.US)
        }
        Regex("""\b([A-Z][A-Z0-9.\-]*)\(US[A-Z0-9]+\)""", RegexOption.IGNORE_CASE).find(text)?.let {
            return it.groupValues[1].uppercase(Locale.US)
        }
        Regex("""\b(\d{1,5})\.HK\b""", RegexOption.IGNORE_CASE).find(text)?.let {
            return it.groupValues[1].padStart(4, '0') + ".HK"
        }
        Regex("""\b([A-Z][A-Z0-9.\-]{0,10})\s+(?:CASH\s+)?DIVIDEND\b""", RegexOption.IGNORE_CASE).find(text)?.let {
            return it.groupValues[1].uppercase(Locale.US)
        }
        return null
    }

    private fun normalizePairText(value: String): String {
        return value
            .replace("WITHHOLDING TAX", "", ignoreCase = true)
            .replace("DIVIDEND FEE", "", ignoreCase = true)
            .replace("CASH DIVIDEND", "", ignoreCase = true)
            .replace("DIVIDEND", "", ignoreCase = true)
            .replace("扣收", "")
            .replace("股息税", "")
            .replace("税", "")
            .replace(Regex("""\s+"""), "")
            .trim()
    }

    private fun tradeTimeForMarket(market: Market): String = when (market) {
        Market.US -> "21:35"
        else -> "09:35"
    }

    private fun createdAt(date: LocalDate, market: Market): Long {
        val hour = if (market == Market.US) 21 else 9
        return date.atTime(hour, 35).atZone(ZoneId.of("UTC+8")).toInstant().toEpochMilli()
    }

    private fun compactDate(date: LocalDate): String = date.format(DateTimeFormatter.BASIC_ISO_DATE)

    private fun formatRefNumber(value: Double): String = value.toString().trimEnd('0').trimEnd('.')
}
