package com.recoder.stockledger.data.importer

import com.recoder.stockledger.data.ImportSourceChannel
import com.recoder.stockledger.data.Market
import com.recoder.stockledger.data.TradeType

enum class HsbcNotificationStatus {
    EXECUTED,
    CANCELLED,
}

data class ParsedHsbcNotification(
    val sourceChannel: ImportSourceChannel,
    val status: HsbcNotificationStatus,
    val tradeType: TradeType,
    val market: Market,
    val symbol: String,
    val name: String,
    val currencyCode: String,
    val price: Double?,
    val quantity: Int,
    val externalReference: String,
    val rawText: String,
)

object HsbcNotificationParser {
    fun parse(rawText: String): ParsedHsbcNotification? {
        val compactText = rawText
            .replace('\u00A0', ' ')
            .trim()
        if (compactText.isBlank()) return null

        return parseSms(compactText) ?: parseEmail(compactText)
    }

    private fun parseSms(rawText: String): ParsedHsbcNotification? {
        smsExecutedRegex.find(rawText)?.let { match ->
            val side = match.groupValues[1]
            val quantity = match.groupValues[2].toIntOrNull() ?: return null
            val symbol = match.groupValues[3]
            val currencyCode = match.groupValues[4]
            val price = match.groupValues[5].sanitizeNumber() ?: return null
            val externalReference = match.groupValues[10]
            val (normalizedSymbol, market) = resolveSymbolAndMarket(symbol, currencyCode) ?: return null

            return ParsedHsbcNotification(
                sourceChannel = ImportSourceChannel.HSBC_SMS,
                status = HsbcNotificationStatus.EXECUTED,
                tradeType = resolveTradeType(side) ?: return null,
                market = market,
                symbol = normalizedSymbol,
                name = normalizedSymbol,
                currencyCode = currencyCode,
                price = price,
                quantity = quantity,
                externalReference = externalReference,
                rawText = rawText,
            )
        }

        smsCancelledRegex.find(rawText)?.let { match ->
            val side = match.groupValues[1]
            val quantity = match.groupValues[2].toIntOrNull() ?: return null
            val symbol = match.groupValues[3]
            val externalReference = match.groupValues[4]
            val (normalizedSymbol, market) = resolveSymbolAndMarket(symbol, null) ?: return null

            return ParsedHsbcNotification(
                sourceChannel = ImportSourceChannel.HSBC_SMS,
                status = HsbcNotificationStatus.CANCELLED,
                tradeType = resolveTradeType(side) ?: return null,
                market = market,
                symbol = normalizedSymbol,
                name = normalizedSymbol,
                currencyCode = "",
                price = null,
                quantity = quantity,
                externalReference = externalReference,
                rawText = rawText,
            )
        }

        return null
    }

    private fun parseEmail(rawText: String): ParsedHsbcNotification? {
        if (!rawText.contains("交易編號") || !rawText.contains("交易狀況")) return null

        val externalReference = emailReferenceRegex.find(rawText)?.groupValues?.getOrNull(1) ?: return null
        val statusText = emailStatusRegex.find(rawText)?.groupValues?.getOrNull(1).orEmpty()
        val side = emailSideRegex.find(rawText)?.groupValues?.getOrNull(1) ?: return null
        val symbolBlock = emailSymbolRegex.find(rawText) ?: return null
        val securityName = symbolBlock.groupValues[1].trim().ifBlank { symbolBlock.groupValues[2].trim() }
        val symbol = symbolBlock.groupValues[2].trim()
        val quantity = emailQuantityRegex.find(rawText)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null
        val priceMatch = emailPriceRegex.find(rawText)
        val currencyCode = priceMatch?.groupValues?.getOrNull(1).orEmpty()
        val price = priceMatch?.groupValues?.getOrNull(2)?.sanitizeNumber()
        val (normalizedSymbol, market) = resolveSymbolAndMarket(symbol, currencyCode.ifBlank { null }) ?: return null

        return ParsedHsbcNotification(
            sourceChannel = ImportSourceChannel.HSBC_EMAIL,
            status = if (statusText.contains("執行")) HsbcNotificationStatus.EXECUTED else HsbcNotificationStatus.CANCELLED,
            tradeType = resolveTradeType(side) ?: return null,
            market = market,
            symbol = normalizedSymbol,
            name = securityName,
            currencyCode = currencyCode,
            price = price,
            quantity = quantity,
            externalReference = externalReference,
            rawText = rawText,
        )
    }

    private fun resolveTradeType(rawSide: String): TradeType? = when {
        rawSide.contains("買入") || rawSide.contains("买入") -> TradeType.BUY
        rawSide.contains("沽出") || rawSide.contains("賣出") || rawSide.contains("卖出") -> TradeType.SELL
        else -> null
    }

    private fun resolveSymbolAndMarket(
        rawSymbol: String,
        currencyCode: String?,
    ): Pair<String, Market>? {
        val compactSymbol = rawSymbol.trim().uppercase()
        return when {
            compactSymbol.endsWith(".HK") -> compactSymbol to Market.HK
            compactSymbol.all(Char::isDigit) && compactSymbol.length in 1..5 -> {
                val normalized = compactSymbol.padStart(4, '0') + ".HK"
                normalized to Market.HK
            }

            compactSymbol.all(Char::isDigit) && compactSymbol.length == 6 -> compactSymbol to Market.A_SHARE
            compactSymbol.matches(Regex("[A-Z][A-Z0-9.-]{0,9}")) -> compactSymbol to when (currencyCode) {
                "HKD" -> Market.HK
                else -> Market.US
            }

            else -> null
        }
    }

    private fun String.sanitizeNumber(): Double? = replace(",", "").toDoubleOrNull()

    private const val HSBC_BRAND_TOKEN = "[滙汇][豐丰]"
    private const val HSBC_SIDE_TOKEN = "買入|买入|沽出|賣出|卖出"

    private val smsExecutedRegex = Regex(
        """(?:【$HSBC_BRAND_TOKEN】)?\s*$HSBC_BRAND_TOKEN[:：]?\s*($HSBC_SIDE_TOKEN)\s*(\d+)\s*股\s*([A-Z0-9.-]+)\s*[，,]\s*成交[價价]\s*([A-Z]{3})\s*([\d,]+(?:\.\d+)?)\s*[，,]\s*共($HSBC_SIDE_TOKEN)\s*(\d+)\s*股\s*[，,]\s*未($HSBC_SIDE_TOKEN)\s*(\d+)\s*股[。.]?\s*([A-Z]\d+)""",
    )

    private val smsCancelledRegex = Regex(
        """(?:【$HSBC_BRAND_TOKEN】)?\s*$HSBC_BRAND_TOKEN[:：]?\s*未能($HSBC_SIDE_TOKEN)\s*(\d+)\s*股\s*([A-Z0-9.-]+)\s*[，,]\s*指示取消[。.]?\s*([A-Z]\d+)""",
    )

    private val emailReferenceRegex = Regex("""交易編號\s*[:：]\s*([A-Z]\d+)""")
    private val emailStatusRegex = Regex("""交易狀況\s*[:：]\s*([^\r\n]+)""")
    private val emailSideRegex = Regex("""指示類別\s*[:：]\s*([^\r\n]+)""")
    private val emailSymbolRegex = Regex("""股票名稱/\s*股票編號\s*[:：]\s*(.*?)\s*\(([A-Z0-9.-]+)\)""")
    private val emailQuantityRegex = Regex("""已成交數量\(股/單位\)\s*[:：]\s*(\d+)""")
    private val emailPriceRegex = Regex("""成交價\s*[:：]\s*([A-Z]{3})\s*([\d,]+(?:\.\d+)?)""")
}
