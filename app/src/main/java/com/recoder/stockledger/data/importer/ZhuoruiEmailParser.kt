package com.recoder.stockledger.data.importer

import android.util.Log
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
    val quantity: Double,
    val tradeDateTime: LocalDateTime,
    val externalReference: String,
    val rawText: String,
)

object ZhuoruiEmailParser {
    private const val TAG = "ZhuoruiEmailParser"

    fun parse(rawText: String): ParsedZhuoruiEmail? {
        val normalizedText = normalizeRawText(rawText)
        val lines = normalizedText
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "[图片]" && !it.startsWith("Fwd:", ignoreCase = true) }
            .toList()
        if (lines.isEmpty()) {
            Log.w(TAG, "parse: 过滤后无有效行")
            return null
        }

        val joinedText = lines.joinToString(separator = "\n")
        val headerMatch = headerRegex.find(joinedText)
        if (headerMatch == null) {
            Log.w(TAG, "parse: 邮件头正则不匹配, 前100字=${joinedText.take(100).replace("\n", "\\n")}")
            return null
        }
        val accountId = headerMatch.groupValues[1]
        val market = resolveMarket(headerMatch.groupValues[2])
        if (market == null) {
            Log.w(TAG, "parse: 市场类型无法识别: '${headerMatch.groupValues[2]}'")
            return null
        }
        val tradeDateTime = runCatching {
            LocalDateTime.parse(headerMatch.groupValues[3], dateTimeFormatter)
        }.getOrNull()
        if (tradeDateTime == null) {
            Log.w(TAG, "parse: 日期时间解析失败: '${headerMatch.groupValues[3]}'")
            return null
        }
        val tradeType = resolveTradeType(headerMatch.groupValues[4])
        if (tradeType == null) {
            Log.w(TAG, "parse: 交易类型无法识别: '${headerMatch.groupValues[4]}'")
            return null
        }

        val detailContent = extractDetailContent(joinedText)
        if (detailContent == null) {
            Log.w(TAG, "parse: 未找到'累计成交金额'锚点或详情为空")
            return null
        }
        val detailTokens = detailContent
            .replace(Regex("\\s+"), " ")
            .trim()
            .split(" ")
            .filter { it.isNotBlank() }
        if (detailTokens.size < 7) {
            Log.w(TAG, "parse: 详情token不足7个: ${detailTokens.size}个, tokens=$detailTokens")
            return null
        }

        val amount = detailTokens.getOrNull(detailTokens.lastIndex - 2)?.sanitizeNumber()
        if (amount == null) {
            Log.w(TAG, "parse: 成交金额解析失败: '${detailTokens.getOrNull(detailTokens.lastIndex - 2)}'")
            return null
        }
        val quantity = detailTokens.getOrNull(detailTokens.lastIndex - 3)?.sanitizeNumber()
        if (quantity == null) {
            Log.w(TAG, "parse: 成交数量解析失败: '${detailTokens.getOrNull(detailTokens.lastIndex - 3)}'")
            return null
        }
        val price = detailTokens.getOrNull(detailTokens.lastIndex - 4)?.sanitizeNumber()
        if (price == null) {
            Log.w(TAG, "parse: 价格解析失败: '${detailTokens.getOrNull(detailTokens.lastIndex - 4)}'")
            return null
        }
        val tokensBeforePrice = detailTokens.dropLast(5)
        val currencyResolution = resolveCurrencyAndSymbol(tokensBeforePrice)
        if (currencyResolution == null) {
            Log.w(TAG, "parse: 币种/代码解析失败: tokensBeforePrice=$tokensBeforePrice")
            return null
        }
        val rawSymbol = currencyResolution.first
        val currencyCode = currencyResolution.second
        val nameTokens = tokensBeforePrice.dropLast(currencyResolution.third)
        val name = nameTokens.joinToString(separator = " ").trim()
        if (name.isBlank()) {
            Log.w(TAG, "parse: 证券名称为空: rawSymbol=$rawSymbol")
            return null
        }
        val symbol = resolveSymbol(rawSymbol, market)
        if (symbol == null) {
            Log.w(TAG, "parse: 代码格式无效: rawSymbol='$rawSymbol', market=$market")
            return null
        }
        val externalReference = buildExternalReference(
            accountId = accountId,
            tradeType = tradeType,
            symbol = symbol,
            quantity = quantity,
            price = price,
            tradeDateTime = tradeDateTime,
        )

        Log.d(TAG, "parse: 解析成功 $tradeType $symbol x$quantity @$price ($market)")
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
        "港股" -> Market.HK
        "A股" -> Market.A_SHARE
        else -> null
    }

    private fun resolveSymbol(rawSymbol: String, market: Market): String? {
        val compact = rawSymbol.trim().uppercase()
        return when (market) {
            Market.US -> compact.takeIf { it.matches(Regex("[A-Z][A-Z0-9.-]{0,9}")) }
            Market.HK -> compact
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
        quantity: Double,
        price: Double,
        tradeDateTime: LocalDateTime,
    ): String {
        val normalizedPrice = price.toString().replace(".", "_")
        val qtyStr = if (quantity % 1.0 == 0.0) quantity.toInt().toString() else quantity.toString()
        val normalizedQuantity = qtyStr.replace(".", "_")
        return listOf(
            "ZR",
            accountId,
            tradeDateTime.format(referenceFormatter),
            tradeType.name,
            symbol.replace(".", "_"),
            normalizedQuantity,
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
