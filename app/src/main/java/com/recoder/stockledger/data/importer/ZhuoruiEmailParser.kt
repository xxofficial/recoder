package com.recoder.stockledger.data.importer

import com.recoder.stockledger.data.ImportSourceChannel
import com.recoder.stockledger.data.Market
import com.recoder.stockledger.data.TradeType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.text.Normalizer

data class ParsedZhuoruiEmail(
    val sourceChannel: ImportSourceChannel,
    val tradeType: TradeType,
    val market: Market,
    val symbol: String,
    val name: String,
    val currencyCode: String,
    val price: Double,
    val quantity: Int,
    val tradeDateTime: LocalDateTime,
    val externalReference: String,
    val rawText: String,
)

object ZhuoruiEmailParser {
    fun parse(rawText: String): ParsedZhuoruiEmail? {
        val normalizedText = normalizeRawText(rawText)
        val lines = normalizedText
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "[图片]" && !it.startsWith("Fwd:", ignoreCase = true) }
            .toList()
        if (lines.isEmpty()) return null

        val joinedText = lines.joinToString(separator = "\n")
        val headerMatch = headerRegex.find(joinedText) ?: return null
        val accountId = headerMatch.groupValues[1]
        val market = resolveMarket(headerMatch.groupValues[2]) ?: return null
        val tradeDateTime = runCatching {
            LocalDateTime.parse(headerMatch.groupValues[3], dateTimeFormatter)
        }.getOrNull() ?: return null
        val tradeType = resolveTradeType(headerMatch.groupValues[4]) ?: return null

        val detailContent = extractDetailContent(joinedText) ?: return null
        val detailTokens = detailContent
            .replace(Regex("\\s+"), " ")
            .trim()
            .split(" ")
            .filter { it.isNotBlank() }
        if (detailTokens.size < 7) return null

        val cumulativeAmount = detailTokens.last().sanitizeNumber() ?: return null
        val cumulativeQuantity = detailTokens.getOrNull(detailTokens.lastIndex - 1)?.sanitizeInteger() ?: return null
        val amount = detailTokens.getOrNull(detailTokens.lastIndex - 2)?.sanitizeNumber() ?: return null
        val quantity = detailTokens.getOrNull(detailTokens.lastIndex - 3)?.sanitizeInteger() ?: return null
        val price = detailTokens.getOrNull(detailTokens.lastIndex - 4)?.sanitizeNumber() ?: return null
        val tokensBeforePrice = detailTokens.dropLast(5)
        val currencyResolution = resolveCurrencyAndSymbol(tokensBeforePrice) ?: return null
        val rawSymbol = currencyResolution.first
        val currencyCode = currencyResolution.second
        val nameTokens = tokensBeforePrice.dropLast(currencyResolution.third)
        val name = nameTokens.joinToString(separator = " ").trim()
        if (name.isBlank()) return null
        val symbol = resolveSymbol(rawSymbol, market) ?: return null
        val externalReference = buildExternalReference(
            accountId = accountId,
            tradeType = tradeType,
            symbol = symbol,
            quantity = quantity,
            price = price,
            tradeDateTime = tradeDateTime,
        )

        if (cumulativeQuantity < quantity || cumulativeAmount < amount) return null

        return ParsedZhuoruiEmail(
            sourceChannel = ImportSourceChannel.ZHUORUI_EMAIL,
            tradeType = tradeType,
            market = market,
            symbol = symbol,
            name = name,
            currencyCode = currencyCode,
            price = price,
            quantity = quantity,
            tradeDateTime = tradeDateTime,
            externalReference = externalReference,
            rawText = rawText,
        )
    }

    private fun normalizeRawText(rawText: String): String =
        Normalizer.normalize(rawText.replace('\u00A0', ' '), Normalizer.Form.NFKC)

    private fun resolveTradeType(rawSide: String): TradeType? = when (rawSide) {
        "买入" -> TradeType.BUY
        "卖出" -> TradeType.SELL
        else -> null
    }

    private fun resolveMarket(rawMarket: String): Market? = when (rawMarket) {
        "美股" -> Market.US
        "港股" -> Market.HONG_KONG
        "A股" -> Market.A_SHARE
        else -> null
    }

    private fun resolveSymbol(rawSymbol: String, market: Market): String? {
        val compact = rawSymbol.trim().uppercase()
        return when (market) {
            Market.US -> compact.takeIf { it.matches(Regex("[A-Z][A-Z0-9.-]{0,9}")) }
            Market.HONG_KONG -> compact
                .removePrefix("HK")
                .removeSuffix(".HK")
                .filter(Char::isDigit)
                .takeIf { it.length in 1..5 }
                ?.padStart(4, '0')
                ?.plus(".HK")
            Market.A_SHARE -> compact
                .removePrefix("SH")
                .removePrefix("SZ")
                .substringBefore(".")
                .filter(Char::isDigit)
                .takeIf { it.length == 6 }
            Market.CASH -> null
        }
    }

    private fun resolveCurrencyAndSymbol(tokensBeforePrice: List<String>): Triple<String, String, Int>? {
        if (tokensBeforePrice.size < 2) return null
        for (currencyTokenCount in 1..3) {
            if (tokensBeforePrice.size <= currencyTokenCount) continue
            val currency = tokensBeforePrice
                .takeLast(currencyTokenCount)
                .joinToString(separator = "")
                .uppercase()
            if (currency !in supportedCurrencies) continue
            val symbol = tokensBeforePrice.getOrNull(tokensBeforePrice.lastIndex - currencyTokenCount) ?: continue
            return Triple(symbol, currency, currencyTokenCount + 1)
        }
        return null
    }

    private fun extractDetailContent(joinedText: String): String? {
        val labelsAnchor = "累计成交金额"
        val startIndex = joinedText.indexOf(labelsAnchor)
        if (startIndex < 0) return null
        val content = joinedText.substring(startIndex + labelsAnchor.length)
        val endIndex = footerAnchors
            .map { anchor -> content.indexOf(anchor).takeIf { it >= 0 } }
            .filterNotNull()
            .minOrNull()
            ?: content.length
        return content.substring(0, endIndex).trim().takeIf { it.isNotBlank() }
    }

    private fun buildExternalReference(
        accountId: String,
        tradeType: TradeType,
        symbol: String,
        quantity: Int,
        price: Double,
        tradeDateTime: LocalDateTime,
    ): String {
        val normalizedPrice = price.toString().replace(".", "_")
        return listOf(
            "ZR",
            accountId,
            tradeDateTime.format(referenceFormatter),
            tradeType.name,
            symbol.replace(".", "_"),
            quantity.toString(),
            normalizedPrice,
        ).joinToString(separator = "-")
    }

    private val headerRegex = Regex(
        """您(\d+)(美股|港股|A股)账(?:户|戶)于(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})成功(买入|卖出)证券""",
    )

    private fun String.sanitizeNumber(): Double? = replace(",", "").toDoubleOrNull()
    private fun String.sanitizeInteger(): Int? = replace(",", "").toIntOrNull()

    private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val referenceFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
    private val supportedCurrencies = setOf("USD", "HKD", "CNY")
    private val footerAnchors = listOf("卓锐证券为", "卓锐证券官网", "风险提示", "*此为系统邮件", "免责声明", "下载卓锐证券")
}
