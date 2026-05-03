package com.recoder.stockledger.data.repository

import android.util.Log
import com.recoder.stockledger.data.BrokerPlatform
import com.recoder.stockledger.data.ExchangeRateRefreshResult
import com.recoder.stockledger.data.ExchangeRates
import com.recoder.stockledger.data.ImportSourceChannel
import com.recoder.stockledger.data.Market
import com.recoder.stockledger.data.TradeFeeEstimate
import com.recoder.stockledger.data.TradeFeeEstimateContext
import com.recoder.stockledger.data.TradeFeeEstimator
import com.recoder.stockledger.data.TradeType
import com.recoder.stockledger.data.ZhuoruiEmailSyncConfig
import com.recoder.stockledger.data.importer.HsbcNotificationParser
import com.recoder.stockledger.data.importer.HsbcNotificationStatus
import com.recoder.stockledger.data.importer.ParsedZhuoruiEmail
import com.recoder.stockledger.data.importer.ZhuoruiEmailParser
import com.recoder.stockledger.data.importer.ZhuoruiStatementPdfParser
import com.recoder.stockledger.data.local.LedgerDao
import com.recoder.stockledger.data.local.QuoteSnapshotEntity
import com.recoder.stockledger.data.local.TransactionEntity
import com.recoder.stockledger.data.rateToCny
import jakarta.mail.BodyPart
import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.Multipart
import jakarta.mail.Session
import jakarta.mail.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.Charset
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.sin

data class QuoteRequest(
    val symbol: String,
    val name: String,
    val market: Market,
)

data class SecurityLookupResult(
    val symbol: String,
    val name: String,
    val market: Market,
)

data class HistoricalClosePoint(
    val symbol: String,
    val market: Market,
    val date: LocalDate,
    val closePrice: Double,
)

data class TradeDraftInput(
    val tradeType: TradeType,
    val platform: BrokerPlatform,
    val sourceChannel: ImportSourceChannel? = null,
    val externalReference: String? = null,
    val market: Market,
    val symbol: String,
    val name: String,
    val tradeDate: String,
    val price: Double,
    val quantity: Int,
    val commission: Double,
    val tax: Double,
    val note: String,
    val tradeTime: String,
    val createdAt: Long,
)

data class ImportedBackup(
    val displayCurrencyName: String?,
    val transactionCount: Int,
    val enabledPlatforms: List<BrokerPlatform>,
    val selectedPlatform: BrokerPlatform?,
)

enum class TradeImportOutcome {
    IMPORTED,
    DUPLICATE,
    CANCELLED,
    UNSUPPORTED,
}

data class TradeImportResult(
    val outcome: TradeImportOutcome,
    val message: String,
    val externalReference: String? = null,
)

data class ZhuoruiMailboxSyncResult(
    val importedCount: Int,
    val duplicateCount: Int,
    val ignoredCount: Int,
    val latestSeenMessageAt: Long?,
)

interface QuoteDataSource {
    val isConfigured: Boolean
    val providerLabel: String

    suspend fun refreshQuotes(requests: List<QuoteRequest>): List<QuoteSnapshotEntity>

    suspend fun fetchHistoricalCloses(
        requests: List<QuoteRequest>,
        lookbackDays: Int,
    ): List<HistoricalClosePoint>

    suspend fun searchSecurities(
        keyword: String,
        market: Market,
        limit: Int = 6,
    ): List<SecurityLookupResult>
}

class DefaultLedgerRepository(
    private val dao: LedgerDao,
    private val quoteDataSource: QuoteDataSource,
    private val exchangeRateDataSource: FrankfurterExchangeRateDataSource,
    private val platformFeePlanSelectionProvider: () -> Map<BrokerPlatform, String> = { emptyMap() },
) {
    val transactions: Flow<List<TransactionEntity>> = dao.observeTransactions()
    val quotes: Flow<List<QuoteSnapshotEntity>> = dao.observeQuotes()
    private val _historicalCloses = MutableStateFlow<List<HistoricalClosePoint>>(emptyList())
    val historicalCloses: StateFlow<List<HistoricalClosePoint>> = _historicalCloses
    private val _exchangeRates = MutableStateFlow(exchangeRateDataSource.currentRates())
    val exchangeRates: StateFlow<ExchangeRates> = _exchangeRates

    val isUsingRealtimeQuotes: Boolean
        get() = quoteDataSource.isConfigured

    val quoteProviderLabel: String
        get() = quoteDataSource.providerLabel

    suspend fun seedIfEmpty() {
        // Intentionally empty. The app should not ship with sample ledger data.
    }

    suspend fun purgeLegacySeedData() {
        dao.deleteLegacySeedTransactions()
        if (dao.transactionCount() == 0) {
            dao.clearQuotes()
        }
    }

    suspend fun addTrade(input: TradeDraftInput) {
        dao.insertTransaction(
            TransactionEntity(
                tradeType = input.tradeType.name,
                platform = input.platform.name,
                sourceChannel = input.sourceChannel?.name,
                externalReference = input.externalReference,
                market = input.market.name,
                symbol = input.symbol,
                name = input.name,
                tradeDate = input.tradeDate,
                tradeTime = input.tradeTime,
                price = input.price,
                quantity = input.quantity,
                commission = input.commission,
                tax = input.tax,
                note = input.note,
                createdAt = input.createdAt,
            ),
        )
    }

    suspend fun updateTrade(
        transactionId: Long,
        input: TradeDraftInput,
    ) {
        dao.updateTransaction(
            TransactionEntity(
                id = transactionId,
                tradeType = input.tradeType.name,
                platform = input.platform.name,
                sourceChannel = input.sourceChannel?.name,
                externalReference = input.externalReference,
                market = input.market.name,
                symbol = input.symbol,
                name = input.name,
                tradeDate = input.tradeDate,
                tradeTime = input.tradeTime,
                price = input.price,
                quantity = input.quantity,
                commission = input.commission,
                tax = input.tax,
                note = input.note,
                createdAt = input.createdAt,
            ),
        )
    }

    suspend fun deleteTrade(transactionId: Long): Int = dao.deleteTransactionById(transactionId)

    suspend fun deleteTransactionsByIds(ids: List<Long>): Int = dao.deleteTransactionsByIds(ids)

    suspend fun replaceTransactions(transactions: List<TransactionEntity>) {
        dao.replaceTransactions(transactions)
    }

    suspend fun exportBackup(
        outputStream: OutputStream,
        displayCurrencyName: String,
        enabledPlatforms: List<BrokerPlatform>,
        selectedPlatform: BrokerPlatform?,
    ) = withContext(Dispatchers.IO) {
        val transactionsSnapshot = transactions.first()
        val payload = JSONObject().apply {
            put("version", 3)
            put("exportedAt", System.currentTimeMillis())
            put("displayCurrency", displayCurrencyName)
            put("enabledPlatforms", org.json.JSONArray().apply {
                enabledPlatforms.forEach { put(it.name) }
            })
            put("selectedPlatform", selectedPlatform?.name)
            put("transactions", org.json.JSONArray().apply {
                transactionsSnapshot.forEach { transaction ->
                    put(
                        JSONObject().apply {
                            put("id", transaction.id)
                            put("tradeType", transaction.tradeType)
                            put("platform", transaction.platform)
                            put("sourceChannel", transaction.sourceChannel)
                            put("externalReference", transaction.externalReference)
                            put("market", transaction.market)
                            put("symbol", transaction.symbol)
                            put("name", transaction.name)
                            put("tradeDate", transaction.tradeDate)
                            put("tradeTime", transaction.tradeTime)
                            put("price", transaction.price)
                            put("quantity", transaction.quantity)
                            put("commission", transaction.commission)
                            put("tax", transaction.tax)
                            put("note", transaction.note)
                            put("createdAt", transaction.createdAt)
                        },
                    )
                }
            })
        }
        outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload.toString(2)) }
    }

    suspend fun importBackup(inputStream: InputStream): ImportedBackup = withContext(Dispatchers.IO) {
        val json = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val payload = JSONObject(json)
        val transactionsArray = payload.optJSONArray("transactions") ?: org.json.JSONArray()
        val enabledPlatforms = buildList {
            val array = payload.optJSONArray("enabledPlatforms") ?: org.json.JSONArray()
            for (index in 0 until array.length()) {
                val name = array.optString(index)
                BrokerPlatform.entries.firstOrNull { it.name == name && it.isConfigurable }?.let(::add)
            }
        }
        val selectedPlatform = payload.optString("selectedPlatform")
            .takeIf { it.isNotBlank() }
            ?.let { name -> BrokerPlatform.entries.firstOrNull { it.name == name && it.isConfigurable } }
        val importedTransactions = buildList {
            for (index in 0 until transactionsArray.length()) {
                val item = transactionsArray.optJSONObject(index) ?: continue
                add(
                    TransactionEntity(
                        id = item.optLong("id"),
                        tradeType = item.optString("tradeType"),
                        platform = item.optString("platform").ifBlank { BrokerPlatform.UNSPECIFIED.name },
                        sourceChannel = item.optString("sourceChannel").takeIf { it.isNotBlank() },
                        externalReference = item.optString("externalReference").takeIf { it.isNotBlank() },
                        market = item.optString("market"),
                        symbol = item.optString("symbol"),
                        name = item.optString("name"),
                        tradeDate = item.optString("tradeDate"),
                        tradeTime = item.optString("tradeTime"),
                        price = item.optDouble("price"),
                        quantity = item.optInt("quantity"),
                        commission = item.optDouble("commission"),
                        tax = item.optDouble("tax"),
                        note = item.optString("note"),
                        createdAt = item.optLong("createdAt"),
                    ),
                )
            }
        }
        val restoredPlatforms = if (enabledPlatforms.isNotEmpty()) {
            enabledPlatforms
        } else {
            importedTransactions
                .mapNotNull { transaction ->
                    BrokerPlatform.entries.firstOrNull {
                        it.name == transaction.platform && it.isConfigurable
                    }
                }
                .distinctBy { it.name }
        }
        replaceTransactions(importedTransactions)
        ImportedBackup(
            displayCurrencyName = payload.optString("displayCurrency").takeIf { it.isNotBlank() },
            transactionCount = importedTransactions.size,
            enabledPlatforms = restoredPlatforms,
            selectedPlatform = selectedPlatform?.takeIf { it in restoredPlatforms },
        )
    }

    suspend fun importSharedTradeText(
        rawText: String,
        receivedAtMillis: Long = System.currentTimeMillis(),
    ): TradeImportResult {
        HsbcNotificationParser.parse(rawText)?.let { parsed ->
            return importParsedHsbcNotification(parsed, receivedAtMillis)
        }
        ZhuoruiEmailParser.parse(rawText)?.let { parsed ->
            return importParsedZhuoruiEmail(parsed)
        }
        val preview = rawText.take(200).replace("\n", "\\n")
        Log.w(TAG, "手动导入解析失败: 未匹配任何格式, 正文前200字=$preview")
        return TradeImportResult(
            outcome = TradeImportOutcome.UNSUPPORTED,
            message = "未识别出可导入的交易邮件或通知格式",
        )
    }

    suspend fun importHsbcNotificationText(
        rawText: String,
        receivedAtMillis: Long = System.currentTimeMillis(),
    ): TradeImportResult {
        val parsed = HsbcNotificationParser.parse(rawText)
            ?: return TradeImportResult(
                outcome = TradeImportOutcome.UNSUPPORTED,
                message = "未识别出可导入的汇丰通知格式",
            )
        return importParsedHsbcNotification(parsed, receivedAtMillis)
    }

    private suspend fun importParsedHsbcNotification(
        parsed: com.recoder.stockledger.data.importer.ParsedHsbcNotification,
        receivedAtMillis: Long,
    ): TradeImportResult {
        if (parsed.status == HsbcNotificationStatus.CANCELLED) {
            return TradeImportResult(
                outcome = TradeImportOutcome.CANCELLED,
                message = "已识别为撤销/未成交通知，未生成交易记录",
                externalReference = parsed.externalReference,
            )
        }

        val price = parsed.price
            ?: return TradeImportResult(
                outcome = TradeImportOutcome.UNSUPPORTED,
                message = "通知缺少成交价，暂时无法自动导入",
                externalReference = parsed.externalReference,
            )

        val existing = dao.findTransactionByExternalReference(
            platform = BrokerPlatform.HSBC.name,
            externalReference = parsed.externalReference,
        )
        if (existing != null) {
            return TradeImportResult(
                outcome = TradeImportOutcome.DUPLICATE,
                message = "交易编号 ${parsed.externalReference} 已存在，已跳过重复导入",
                externalReference = parsed.externalReference,
            )
        }

        val receivedAt = Instant.ofEpochMilli(receivedAtMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
        val feeEstimate = estimateImportedTradeFees(
            tradeType = parsed.tradeType,
            platform = BrokerPlatform.HSBC,
            market = parsed.market,
            price = price,
            quantity = parsed.quantity,
            tradeDate = receivedAt.toLocalDate().toString(),
            tradeTime = receivedAt.toLocalTime().format(timeFormatter),
        )
        // Resolve stock name: if parsed name is same as symbol, look up from quote snapshots
        val resolvedName = if (parsed.name == parsed.symbol || parsed.name.isBlank()) {
            dao.findStockNameFromQuotes(parsed.symbol, parsed.market.name) ?: parsed.name
        } else {
            parsed.name
        }

        addTrade(
            TradeDraftInput(
                tradeType = parsed.tradeType,
                platform = BrokerPlatform.HSBC,
                sourceChannel = parsed.sourceChannel,
                externalReference = parsed.externalReference,
                market = parsed.market,
                symbol = parsed.symbol,
                name = resolvedName,
                tradeDate = receivedAt.toLocalDate().toString(),
                price = price,
                quantity = parsed.quantity,
                commission = feeEstimate.commission,
                tax = feeEstimate.tax,
                note = buildImportedNote(
                    sourceChannel = parsed.sourceChannel,
                    externalReference = parsed.externalReference,
                    rawText = parsed.rawText,
                    suffix = importedFeeNoteSuffix(feeEstimate),
                ),
                tradeTime = receivedAt.toLocalTime().format(timeFormatter),
                createdAt = receivedAtMillis,
            ),
        )
        return TradeImportResult(
            outcome = TradeImportOutcome.IMPORTED,
            message = "已自动导入汇丰${parsed.tradeType.label}记录 ${parsed.symbol} ${parsed.quantity} 股，费用已按方案估算",
            externalReference = parsed.externalReference,
        )
    }

    private suspend fun importParsedZhuoruiEmail(
        parsed: ParsedZhuoruiEmail,
    ): TradeImportResult {
        val existing = dao.findTransactionByExternalReference(
            platform = BrokerPlatform.ZHUORUI.name,
            externalReference = parsed.externalReference,
        )
        if (existing != null) {
            return TradeImportResult(
                outcome = TradeImportOutcome.DUPLICATE,
                message = "卓锐交易 ${parsed.symbol} ${parsed.quantity} 股已存在，已跳过重复导入",
                externalReference = parsed.externalReference,
            )
        }

        val createdAt = parsed.tradeDateTime
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val feeEstimate = estimateImportedTradeFees(
            tradeType = parsed.tradeType,
            platform = BrokerPlatform.ZHUORUI,
            market = parsed.market,
            price = parsed.price,
            quantity = parsed.quantity,
            tradeDate = parsed.tradeDateTime.toLocalDate().toString(),
            tradeTime = parsed.tradeDateTime.toLocalTime().format(timeFormatter),
        )
        addTrade(
            TradeDraftInput(
                tradeType = parsed.tradeType,
                platform = BrokerPlatform.ZHUORUI,
                sourceChannel = parsed.sourceChannel,
                externalReference = parsed.externalReference,
                market = parsed.market,
                symbol = parsed.symbol,
                name = parsed.name,
                tradeDate = parsed.tradeDateTime.toLocalDate().toString(),
                price = parsed.price,
                quantity = parsed.quantity,
                commission = feeEstimate.commission,
                tax = feeEstimate.tax,
                note = buildImportedNote(
                    sourceChannel = parsed.sourceChannel,
                    externalReference = parsed.externalReference,
                    rawText = parsed.rawText,
                    suffix = importedFeeNoteSuffix(feeEstimate),
                ),
                tradeTime = parsed.tradeDateTime.toLocalTime().format(timeFormatter),
                createdAt = createdAt,
            ),
        )
        return TradeImportResult(
            outcome = TradeImportOutcome.IMPORTED,
            message = "已自动导入卓锐${parsed.tradeType.label}记录 ${parsed.symbol} ${parsed.quantity} 股，费用已按方案估算",
            externalReference = parsed.externalReference,
        )
    }

    suspend fun importZhuoruiStatementPdf(
        inputStream: java.io.InputStream,
        password: String,
    ): List<TradeImportResult> = withContext(Dispatchers.IO) {
        Log.d(TAG, "开始导入PDF结单, password长度=${password.length}")
        val parsedTrades = ZhuoruiStatementPdfParser.parsePdf(inputStream, password)
        Log.d(TAG, "PDF解析完成, 找到${parsedTrades.size}条交易记录")
        if (parsedTrades.isEmpty()) {
            Log.w(TAG, "PDF结单中未找到可导入的交易记录")
            return@withContext listOf(
                TradeImportResult(
                    outcome = TradeImportOutcome.UNSUPPORTED,
                    message = "PDF结单中未找到可导入的交易记录",
                )
            )
        }

        val results = mutableListOf<TradeImportResult>()
        for (parsed in parsedTrades) {
            val externalReference = "ZR-STMT-${parsed.tradeRef}"
            val existing = dao.findTransactionByExternalReference(
                platform = BrokerPlatform.ZHUORUI.name,
                externalReference = externalReference,
            )
            if (existing != null) {
                results.add(
                    TradeImportResult(
                        outcome = TradeImportOutcome.DUPLICATE,
                        message = "交易 ${parsed.symbol} ${parsed.quantity} 股已存在，已跳过",
                        externalReference = externalReference,
                    )
                )
                continue
            }

            val feeEstimate = estimateImportedTradeFees(
                tradeType = parsed.tradeType,
                platform = BrokerPlatform.ZHUORUI,
                market = parsed.market,
                price = parsed.price,
                quantity = parsed.quantity,
                tradeDate = parsed.tradeDate.toString(),
                tradeTime = "00:00",
            )
            addTrade(
                TradeDraftInput(
                    tradeType = parsed.tradeType,
                    platform = BrokerPlatform.ZHUORUI,
                    sourceChannel = parsed.sourceChannel,
                    externalReference = externalReference,
                    market = parsed.market,
                    symbol = parsed.symbol,
                    name = parsed.name,
                    tradeDate = parsed.tradeDate.toString(),
                    price = parsed.price,
                    quantity = parsed.quantity,
                    commission = feeEstimate.commission,
                    tax = feeEstimate.tax,
                    note = buildImportedNote(
                        sourceChannel = parsed.sourceChannel,
                        externalReference = externalReference,
                        rawText = parsed.rawLine,
                        suffix = importedFeeNoteSuffix(feeEstimate),
                    ),
                    tradeTime = "00:00",
                    createdAt = System.currentTimeMillis(),
                ),
            )
            results.add(
                TradeImportResult(
                    outcome = TradeImportOutcome.IMPORTED,
                    message = "已导入${parsed.tradeType.label} ${parsed.symbol} ${parsed.quantity} 股",
                    externalReference = externalReference,
                )
            )
        }

        if (results.any { it.outcome == TradeImportOutcome.IMPORTED }) {
            refreshQuotesForPortfolio(transactions.first())
        }

        results
    }

    suspend fun deleteHolding(symbol: String, market: Market): Int {
        return dao.deleteHolding(symbol = symbol, market = market.name)
    }

    suspend fun lookupSecurity(rawInput: String, market: Market): SecurityLookupResult? {
        val normalizedSymbol = normalizeLookupSymbol(rawInput, market) ?: return null
        val quote = quoteDataSource.refreshQuotes(
            listOf(
                QuoteRequest(
                    symbol = normalizedSymbol,
                    name = normalizedSymbol,
                    market = market,
                ),
            ),
        ).firstOrNull() ?: return null

        return SecurityLookupResult(
            symbol = quote.symbol,
            name = quote.name.ifBlank { normalizedSymbol },
            market = market,
        )
    }

    suspend fun searchSecurities(
        rawInput: String,
        market: Market,
        limit: Int = 6,
    ): List<SecurityLookupResult> = quoteDataSource.searchSecurities(rawInput, market, limit)

    suspend fun refreshQuotes(requests: List<QuoteRequest>): Long {
        val deduped = requests.distinctBy { "${it.market.name}:${it.symbol}" }
        val refreshed = quoteDataSource.refreshQuotes(deduped)
        if (deduped.isNotEmpty() && refreshed.isEmpty()) {
            throw IOException("未能从 ${quoteDataSource.providerLabel} 获取行情")
        }
        if (refreshed.isNotEmpty()) {
            dao.upsertQuotes(refreshed)
        }
        return dao.latestQuoteRefreshTimestamp() ?: System.currentTimeMillis()
    }

    suspend fun refreshQuotesForPortfolio(transactions: List<TransactionEntity>): Long {
        val securityTransactions = transactions
            .filter { transaction ->
                val tradeType = TradeType.valueOf(transaction.tradeType)
                tradeType.isSecurityTrade &&
                    transaction.symbol.isNotBlank() &&
                    transaction.market != Market.CASH.name
            }
        val requests = securityTransactions
            .groupBy { it.market to it.symbol }
            .map { (_, grouped) ->
                val head = grouped.first()
                QuoteRequest(
                    symbol = head.symbol,
                    name = head.name,
                    market = Market.valueOf(head.market),
                )
            }

        if (requests.isEmpty()) {
            dao.clearQuotes()
            _historicalCloses.value = emptyList()
            return System.currentTimeMillis()
        }

        val earliestTradeDate = securityTransactions
            .mapNotNull { parseTradeDateOrNull(it.tradeDate) }
            .minOrNull()
            ?: LocalDate.now()
        val lookbackDays = ChronoUnit.DAYS
            .between(earliestTradeDate, LocalDate.now())
            .toInt()
            .coerceAtLeast(90)
            .coerceAtMost(2_400) + 10

        _historicalCloses.value = runCatching {
            quoteDataSource.fetchHistoricalCloses(requests, lookbackDays)
        }.getOrElse { emptyList() }

        return refreshQuotes(requests)
    }

    suspend fun refreshExchangeRates(): ExchangeRateRefreshResult {
        val result = exchangeRateDataSource.refreshRates()
        _exchangeRates.value = result.rates
        return result
    }

    private fun normalizeLookupSymbol(rawInput: String, market: Market): String? {
        val compact = rawInput.trim()
            .uppercase()
            .replace(" ", "")

        return when (market) {
            Market.A_SHARE -> {
                val digits = compact
                    .removePrefix("SH")
                    .removePrefix("SZ")
                    .substringBefore(".")
                    .filter(Char::isDigit)
                digits.takeIf { it.length == 6 }
            }

            Market.HONG_KONG -> {
                val digits = compact
                    .removePrefix("HK")
                    .removeSuffix(".HK")
                    .filter(Char::isDigit)
                digits.takeIf { it.length in 1..5 }?.padStart(4, '0')?.let { "$it.HK" }
            }

            Market.US -> {
                val symbol = compact
                    .removePrefix("US.")
                    .removePrefix("US")
                    .removePrefix("GB_")
                    .removeSuffix(".US")
                    .replace("_", ".")
                symbol.takeIf {
                    it.matches(Regex("[A-Z][A-Z0-9.-]{0,9}")) &&
                        it.any(Char::isLetter)
                }
            }

            Market.CASH -> null
        }
    }

    private fun parseTradeDateOrNull(value: String): LocalDate? = runCatching {
        LocalDate.parse(value)
    }.getOrNull()

    private fun buildImportedNote(
        sourceChannel: ImportSourceChannel,
        externalReference: String,
        rawText: String,
        suffix: String? = null,
    ): String {
        val preview = rawText
            .replace('\n', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(80)
        val suffixLabel = suffix?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
        return "${sourceChannel.label}自动导入 · 交易编号 $externalReference${if (preview.isBlank()) "" else " · $preview"}$suffixLabel"
    }

    private suspend fun estimateImportedTradeFees(
        tradeType: TradeType,
        platform: BrokerPlatform,
        market: Market,
        price: Double,
        quantity: Int,
        tradeDate: String,
        tradeTime: String,
    ): TradeFeeEstimate {
        val selectedPlanId = TradeFeeEstimator.resolvePlanId(
            platform,
            platformFeePlanSelectionProvider()[platform],
        )
        val context = if (platform == BrokerPlatform.HSBC) {
            TradeFeeEstimateContext(
                monthlyTurnoverHkdBeforeTrade = calculateHsbcMonthlyTurnoverHkdBeforeTrade(
                    tradeDate = tradeDate,
                    tradeTime = tradeTime,
                ),
            )
        } else {
            TradeFeeEstimateContext()
        }
        return TradeFeeEstimator.estimate(
            platform = platform,
            market = market,
            tradeType = tradeType,
            price = price,
            quantity = quantity,
            planId = selectedPlanId,
            context = context,
        )
    }

    private suspend fun calculateHsbcMonthlyTurnoverHkdBeforeTrade(
        tradeDate: String,
        tradeTime: String,
    ): Double {
        val draftDate = runCatching { LocalDate.parse(tradeDate) }.getOrNull() ?: return 0.0
        val draftTime = runCatching { LocalTime.parse(tradeTime, timeFormatter) }.getOrNull() ?: LocalTime.MAX
        return transactions.first()
            .asSequence()
            .mapNotNull { transaction ->
                val platform = runCatching { BrokerPlatform.valueOf(transaction.platform) }.getOrDefault(BrokerPlatform.UNSPECIFIED)
                if (platform != BrokerPlatform.HSBC) return@mapNotNull null
                val existingTradeType = runCatching { TradeType.valueOf(transaction.tradeType) }.getOrNull()
                    ?: return@mapNotNull null
                if (!existingTradeType.isSecurityTrade) return@mapNotNull null
                val existingDate = runCatching { LocalDate.parse(transaction.tradeDate) }.getOrNull()
                    ?: return@mapNotNull null
                if (existingDate.year != draftDate.year || existingDate.month != draftDate.month) {
                    return@mapNotNull null
                }
                val existingTime = runCatching { LocalTime.parse(transaction.tradeTime, timeFormatter) }.getOrNull() ?: LocalTime.MAX
                val isBeforeDraft = existingDate.isBefore(draftDate) ||
                    (existingDate == draftDate && existingTime <= draftTime)
                if (!isBeforeDraft) return@mapNotNull null
                val market = runCatching { Market.valueOf(transaction.market) }.getOrNull()
                    ?: return@mapNotNull null
                amountToHkdEquivalent(transaction.price * transaction.quantity, market)
            }
            .sum()
    }

    private fun amountToHkdEquivalent(amount: Double, market: Market): Double {
        if (market == Market.HONG_KONG) return amount
        val currentRates = exchangeRates.value
        val amountCny = amount * currentRates.rateToCny(market)
        val hkdRateToCny = currentRates.rateToCny(Market.HONG_KONG)
        return if (hkdRateToCny <= 1e-6) amount else amountCny / hkdRateToCny
    }

    private fun importedFeeNoteSuffix(estimate: TradeFeeEstimate): String {
        return when (estimate.coverage) {
            com.recoder.stockledger.data.FeeEstimateCoverage.FULL ->
                "费用已按当前费率方案自动计算"
            com.recoder.stockledger.data.FeeEstimateCoverage.PARTIAL ->
                "费用已按当前费率方案估算，建议复核"
            com.recoder.stockledger.data.FeeEstimateCoverage.UNSUPPORTED ->
                "费用暂未自动计算"
        }
    }

    suspend fun syncZhuoruiMailbox(
        config: ZhuoruiEmailSyncConfig,
        lastSyncAtMillis: Long,
        fetchCount: Int = DEFAULT_MAIL_FETCH_BATCH_SIZE,
        earliestReceivedAtMillis: Long? = null,
    ): ZhuoruiMailboxSyncResult = withContext(Dispatchers.IO) {
        require(config.isComplete()) { "邮箱配置不完整" }

        val store = openImapStore(config)
        try {
            val folder = store.getFolder(config.folder).apply { open(Folder.READ_ONLY) }
            try {
                val total = folder.messageCount
                if (total <= 0) {
                    return@withContext ZhuoruiMailboxSyncResult(
                        importedCount = 0,
                        duplicateCount = 0,
                        ignoredCount = 0,
                        latestSeenMessageAt = null,
                    )
                }

                val startIndex = (total - fetchCount.coerceIn(1, 500) + 1).coerceAtLeast(1)
                val messages = folder.getMessages(startIndex, total)
                    .sortedBy { messageTimestampMillis(it) ?: 0L }

                var importedCount = 0
                var duplicateCount = 0
                var ignoredCount = 0
                var latestSeenMessageAt: Long? = null
                val defaultThreshold = (lastSyncAtMillis - MAIL_RESYNC_LOOKBACK_MS)
                    .coerceAtLeast(System.currentTimeMillis() - MAIL_MAX_LOOKBACK_MS)
                val scanThreshold = earliestReceivedAtMillis ?: defaultThreshold
                Log.d(TAG, "卓锐邮箱同步开始: 共${messages.size}封邮件, scanThreshold=$scanThreshold")

                for (message in messages) {
                    val messageTimestamp = messageTimestampMillis(message)
                    val subject = runCatching { message.subject }.getOrNull() ?: "(无主题)"
                    if (messageTimestamp != null) {
                        latestSeenMessageAt = maxOf(latestSeenMessageAt ?: 0L, messageTimestamp)
                        if (messageTimestamp < scanThreshold) {
                            Log.d(TAG, "跳过邮件(时间早于阈值): subject=$subject, ts=$messageTimestamp")
                            continue
                        }
                    }

                    val rawText = extractMessageText(message)
                    if (rawText.isBlank()) {
                        Log.w(TAG, "邮件正文为空, 跳过: subject=$subject")
                        ignoredCount += 1
                        continue
                    }
                    val normalizedText = rawText
                        .replace('\u0000', ' ')
                        .trim()
                    val parsed = ZhuoruiEmailParser.parse(normalizedText)
                    if (parsed == null) {
                        val preview = normalizedText.take(200).replace("\n", "\\n")
                        Log.w(TAG, "邮件解析失败: subject=$subject, 正文前200字=$preview")
                        ignoredCount += 1
                        continue
                    }
                    val result = importParsedZhuoruiEmail(parsed)
                    when (result.outcome) {
                        TradeImportOutcome.IMPORTED -> {
                            Log.d(TAG, "导入成功: ${parsed.tradeType} ${parsed.symbol} x${parsed.quantity} @${parsed.price}")
                            importedCount += 1
                        }
                        TradeImportOutcome.DUPLICATE -> {
                            Log.d(TAG, "重复记录, 跳过: ${parsed.symbol} ${parsed.tradeDateTime}")
                            duplicateCount += 1
                        }
                        else -> {
                            Log.w(TAG, "导入失败: ${parsed.symbol}, outcome=${result.outcome}, msg=${result.message}")
                            ignoredCount += 1
                        }
                    }
                }

                if (importedCount > 0) {
                    refreshQuotesForPortfolio(transactions.first())
                }

                Log.d(TAG, "卓锐邮箱同步完成: imported=$importedCount, duplicate=$duplicateCount, ignored=$ignoredCount")
                ZhuoruiMailboxSyncResult(
                    importedCount = importedCount,
                    duplicateCount = duplicateCount,
                    ignoredCount = ignoredCount,
                    latestSeenMessageAt = latestSeenMessageAt,
                )
            } finally {
                runCatching { folder.close(false) }
            }
        } finally {
            runCatching { store.close() }
        }
    }

    private fun openImapStore(config: ZhuoruiEmailSyncConfig): Store {
        val session = Session.getInstance(
            java.util.Properties().apply {
                put("mail.store.protocol", "imaps")
                put("mail.imaps.host", config.imapHost)
                put("mail.imaps.port", config.resolvedPort().toString())
                put("mail.imaps.ssl.enable", "true")
                put("mail.imaps.timeout", "15000")
                put("mail.imaps.connectiontimeout", "15000")
                put("mail.mime.allowutf8", "true")
            },
        )
        return session.getStore("imaps").apply {
            connect(config.imapHost, config.resolvedPort(), config.account, config.password)
        }
    }

    private fun extractMessageText(message: Message): String {
        val subject = message.subject.orEmpty().trim()
        val body = extractPartText(message).trim()
        return listOf(subject, body)
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n\n")
    }

    private fun extractPartText(part: jakarta.mail.Part): String {
        return when {
            part.isMimeType("text/plain") -> part.content?.toString().orEmpty()
            part.isMimeType("text/html") -> htmlToPlainText(part.content?.toString().orEmpty())
            part.isMimeType("multipart/*") -> {
                val multipart = part.content as? Multipart ?: return ""
                buildString {
                    for (index in 0 until multipart.count) {
                        val bodyPart = multipart.getBodyPart(index)
                        appendLine(extractBodyPartText(bodyPart))
                    }
                }
            }

            else -> ""
        }
    }

    private fun extractBodyPartText(bodyPart: BodyPart): String = extractPartText(bodyPart)

    private fun htmlToPlainText(html: String): String {
        if (html.isBlank()) return ""
        return html
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>"), "\n")
            .replace(Regex("(?i)</div>"), "\n")
            .replace(Regex("(?i)</tr>"), "\n")
            .replace(Regex("(?i)</td>"), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("\\s+"), " ")
            .replace(" 股票名称 ", "\n股票名称\n")
            .replace(" 股票代码 ", "\n股票代码\n")
            .replace(" 币种 ", "\n币种\n")
            .replace(" 成交价格 ", "\n成交价格\n")
            .replace(" 成交数量 ", "\n成交数量\n")
            .replace(" 成交金额 ", "\n成交金额\n")
            .replace(" 累计成交数量 ", "\n累计成交数量\n")
            .replace(" 累计成交金额 ", "\n累计成交金额\n")
            .trim()
    }

    private fun messageTimestampMillis(message: Message): Long? =
        message.receivedDate?.time ?: message.sentDate?.time

    private companion object {
        const val TAG = "LedgerRepository"
        val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        const val DEFAULT_MAIL_FETCH_BATCH_SIZE = 200
        const val MAIL_RESYNC_LOOKBACK_MS = 24L * 60 * 60 * 1000
        const val MAIL_MAX_LOOKBACK_MS = 14L * 24 * 60 * 60 * 1000
    }
}

class TencentSinaQuoteDataSource : QuoteDataSource {
    override val isConfigured: Boolean = true
    override val providerLabel: String = "腾讯主源 / 新浪兜底"

    override suspend fun refreshQuotes(requests: List<QuoteRequest>): List<QuoteSnapshotEntity> =
        withContext(Dispatchers.IO) {
            if (requests.isEmpty()) return@withContext emptyList()

            val deduped = requests.distinctBy { requestKey(it.symbol, it.market) }
            Log.d(TAG, "Refreshing ${deduped.size} quote(s) from $providerLabel")

            val (tencentEligible, sinaOnly) = deduped.partition { request ->
                request.market != Market.US
            }

            val tencentQuotes = runCatching { fetchTencentQuotes(tencentEligible) }
                .onFailure { Log.e(TAG, "Tencent quote fetch failed", it) }
                .getOrElse { emptyMap() }

            val missing = tencentEligible.filterNot { request ->
                tencentQuotes.containsKey(requestKey(request.symbol, request.market))
            }
            val fallbackRequests = missing + sinaOnly

            if (fallbackRequests.isEmpty()) {
                Log.d(TAG, "Tencent returned all ${tencentQuotes.size} quote(s)")
                return@withContext deduped.mapNotNull { request ->
                    tencentQuotes[requestKey(request.symbol, request.market)]
                }
            }

            Log.w(TAG, "Tencent missed ${missing.size} quote(s), fallback to Sina for ${fallbackRequests.size} quote(s)")
            val sinaQuotes = runCatching { fetchSinaQuotes(fallbackRequests) }
                .onFailure { Log.e(TAG, "Sina quote fetch failed", it) }
                .getOrElse { emptyMap() }

            val merged = deduped.mapNotNull { request ->
                val key = requestKey(request.symbol, request.market)
                tencentQuotes[key] ?: sinaQuotes[key]
            }

            Log.d(
                TAG,
                "Quote refresh completed: tencent=${tencentQuotes.size}, sina=${sinaQuotes.size}, merged=${merged.size}",
            )
            merged
        }

    override suspend fun fetchHistoricalCloses(
        requests: List<QuoteRequest>,
        lookbackDays: Int,
    ): List<HistoricalClosePoint> = withContext(Dispatchers.IO) {
        requests.flatMap { request ->
            runCatching {
                fetchHistoricalClosesForRequest(
                    request = request,
                    lookbackDays = lookbackDays,
                )
            }.onFailure {
                Log.w(TAG, "Historical close fetch failed for ${request.market}:${request.symbol}: ${it.message}")
            }.getOrDefault(emptyList())
        }
    }

    override suspend fun searchSecurities(
        keyword: String,
        market: Market,
        limit: Int,
    ): List<SecurityLookupResult> = withContext(Dispatchers.IO) {
        if (market == Market.CASH) return@withContext emptyList()

        val raw = keyword.trim()
        if (raw.isBlank()) return@withContext emptyList()

        val encodedKeyword = URLEncoder.encode(raw, Charsets.UTF_8.name())
        val body = httpGet(
            url = "http://suggest3.sinajs.cn/suggest/type=11,12,31,41&key=$encodedKeyword&name=suggestdata",
            headers = mapOf(
                "Referer" to "https://finance.sina.com.cn/",
                "User-Agent" to WEB_USER_AGENT,
            ),
            charsetName = "GB18030",
        )

        parseSinaSuggestResponse(body, market).take(limit)
    }

    private fun fetchHistoricalClosesForRequest(
        request: QuoteRequest,
        lookbackDays: Int,
    ): List<HistoricalClosePoint> {
        val historyCode = resolveHistoryCode(request)
        val endpoint = when (request.market) {
            Market.A_SHARE, Market.US -> "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get"
            Market.HONG_KONG -> "https://web.ifzq.gtimg.cn/appstock/app/hkfqkline/get"
            Market.CASH -> return emptyList()
        }
        val body = httpGet(
            url = "$endpoint?param=$historyCode,day,,,$lookbackDays,qfq",
            headers = mapOf(
                "Referer" to "https://gu.qq.com/",
                "User-Agent" to WEB_USER_AGENT,
            ),
            charsetName = "UTF-8",
        )
        val data = JSONObject(body).optJSONObject("data") ?: return emptyList()
        val historyPayload = data.optJSONObject(historyCode)
            ?: data.keys().asSequence().mapNotNull { key -> data.optJSONObject(key) }.firstOrNull()
            ?: return emptyList()
        val series = historyPayload.optJSONArray("qfqday")
            ?: historyPayload.optJSONArray("day")
            ?: return emptyList()

        return buildList {
            for (index in 0 until series.length()) {
                val row = series.optJSONArray(index) ?: continue
                val date = row.optString(0)
                    .takeIf { it.isNotBlank() }
                    ?.let(::parseHistoricalDateOrNull)
                    ?: continue
                val closePrice = row.optString(2).toDoubleOrNull() ?: continue
                add(
                    HistoricalClosePoint(
                        symbol = request.symbol,
                        market = request.market,
                        date = date,
                        closePrice = closePrice,
                    ),
                )
            }
        }
    }

    private fun fetchTencentQuotes(requests: List<QuoteRequest>): Map<String, QuoteSnapshotEntity> {
        val codeToRequest = requests.associateBy(::normalizeTencentCode)
        val url = "http://qt.gtimg.cn/q=${codeToRequest.keys.joinToString(",")}"
        val body = httpGet(
            url = url,
            headers = mapOf(
                "Referer" to "https://gu.qq.com/",
                "User-Agent" to WEB_USER_AGENT,
            ),
            charsetName = "GB18030",
        )

        return body.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { line -> parseTencentLine(line, codeToRequest) }
            .associateBy { requestKey(it.symbol, Market.valueOf(it.market)) }
    }

    private fun fetchSinaQuotes(requests: List<QuoteRequest>): Map<String, QuoteSnapshotEntity> {
        val codeToRequest = requests.associateBy(::normalizeSinaCode)
        val url = "http://hq.sinajs.cn/list=${codeToRequest.keys.joinToString(",")}"
        val body = httpGet(
            url = url,
            headers = mapOf(
                "Referer" to "https://finance.sina.com.cn/",
                "User-Agent" to WEB_USER_AGENT,
            ),
            charsetName = "GB18030",
        )

        return body.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { line -> parseSinaLine(line, codeToRequest) }
            .associateBy { requestKey(it.symbol, Market.valueOf(it.market)) }
    }

    private fun parseTencentLine(
        line: String,
        codeToRequest: Map<String, QuoteRequest>,
    ): QuoteSnapshotEntity? {
        val normalizedCode = line.substringAfter("v_", "")
            .substringBefore("=")
            .trim()
        val request = codeToRequest[normalizedCode] ?: return null
        val payload = line.substringAfter("=\"", "")
            .substringBeforeLast("\"", "")
        if (payload.isBlank()) {
            Log.w(TAG, "Tencent payload blank for $normalizedCode")
            return null
        }

        val parts = payload.split("~")
        if (parts.size < 5) {
            Log.w(TAG, "Tencent payload too short for $normalizedCode: ${parts.size}")
            return null
        }

        val currentPrice = parts.getOrNull(3)?.toDoubleOrNull()
        val previousClose = parts.getOrNull(4)?.toDoubleOrNull()
        if (currentPrice == null || previousClose == null) {
            Log.w(TAG, "Tencent missing price for $normalizedCode")
            return null
        }

        return QuoteSnapshotEntity(
            symbol = request.symbol,
            market = request.market.name,
            name = parts.getOrNull(1).orEmpty().ifBlank { request.name },
            currentPrice = currentPrice,
            previousClose = previousClose,
            lastUpdatedAt = parseTencentTimestamp(parts.getOrNull(30)),
        )
    }

    private fun parseSinaLine(
        line: String,
        codeToRequest: Map<String, QuoteRequest>,
    ): QuoteSnapshotEntity? {
        val normalizedCode = line.substringAfter("hq_str_", "")
            .substringBefore("=")
            .trim()
        val request = codeToRequest[normalizedCode] ?: return null
        val payload = line.substringAfter("=\"", "")
            .substringBeforeLast("\"", "")
        if (payload.isBlank()) {
            Log.w(TAG, "Sina payload blank for $normalizedCode")
            return null
        }

        val parts = payload.split(",")
        return when (request.market) {
            Market.A_SHARE -> parseSinaAShare(request, parts)
            Market.HONG_KONG -> parseSinaHongKong(request, parts)
            Market.US -> parseSinaUs(request, parts)
            Market.CASH -> null
        }
    }

    private fun parseSinaAShare(
        request: QuoteRequest,
        parts: List<String>,
    ): QuoteSnapshotEntity? {
        if (parts.size < 32) {
            Log.w(TAG, "Sina A-share payload too short for ${request.symbol}: ${parts.size}")
            return null
        }

        val currentPrice = parts.getOrNull(3)?.toDoubleOrNull()
        val previousClose = parts.getOrNull(2)?.toDoubleOrNull()
        if (currentPrice == null || previousClose == null) {
            Log.w(TAG, "Sina A-share missing price for ${request.symbol}")
            return null
        }

        return QuoteSnapshotEntity(
            symbol = request.symbol,
            market = request.market.name,
            name = parts.firstOrNull().orEmpty().ifBlank { request.name },
            currentPrice = currentPrice,
            previousClose = previousClose,
            lastUpdatedAt = parseSinaTimestamp(
                date = parts.getOrElse(30) { "" },
                time = parts.getOrElse(31) { "" },
            ),
        )
    }

    private fun parseSinaHongKong(
        request: QuoteRequest,
        parts: List<String>,
    ): QuoteSnapshotEntity? {
        if (parts.size < 18) {
            Log.w(TAG, "Sina HK payload too short for ${request.symbol}: ${parts.size}")
            return null
        }

        val currentPrice = parts.getOrNull(6)?.toDoubleOrNull()
        val previousClose = parts.getOrNull(3)?.toDoubleOrNull()
        if (currentPrice == null || previousClose == null) {
            Log.w(TAG, "Sina HK missing price for ${request.symbol}")
            return null
        }

        return QuoteSnapshotEntity(
            symbol = request.symbol,
            market = request.market.name,
            name = parts.getOrNull(1).orEmpty().ifBlank { request.name },
            currentPrice = currentPrice,
            previousClose = previousClose,
            lastUpdatedAt = parseSinaHongKongTimestamp(parts.getOrElse(17) { "" }),
        )
    }

    private fun parseSinaUs(
        request: QuoteRequest,
        parts: List<String>,
    ): QuoteSnapshotEntity? {
        if (parts.size < 27) {
            Log.w(TAG, "Sina US payload too short for ${request.symbol}: ${parts.size}")
            return null
        }

        val currentPrice = parts.getOrNull(1)?.toDoubleOrNull()
        val previousClose = parts.getOrNull(26)?.toDoubleOrNull()
        if (currentPrice == null || previousClose == null) {
            Log.w(TAG, "Sina US missing price for ${request.symbol}")
            return null
        }

        return QuoteSnapshotEntity(
            symbol = request.symbol,
            market = request.market.name,
            name = parts.firstOrNull().orEmpty().ifBlank { request.name },
            currentPrice = currentPrice,
            previousClose = previousClose,
            lastUpdatedAt = parseSinaUsTimestamp(parts.getOrElse(3) { "" }),
        )
    }

    private fun parseSinaSuggestResponse(
        body: String,
        market: Market,
    ): List<SecurityLookupResult> {
        val payload = body.substringAfter("=\"", "")
            .substringBeforeLast("\"", "")
        if (payload.isBlank()) return emptyList()

        return payload.split(";")
            .mapNotNull { entry ->
                val fields = entry.split(",")
                val typeCode = fields.getOrNull(1)?.trim().orEmpty()
                val code = fields.getOrNull(2)?.trim().orEmpty()
                val name = fields.getOrNull(4)?.trim().orEmpty()
                if (code.isBlank() || name.isBlank()) return@mapNotNull null

                when (market) {
                    Market.A_SHARE -> {
                        if (typeCode !in setOf("11", "12") || code.length != 6) return@mapNotNull null
                        SecurityLookupResult(
                            symbol = code,
                            name = name,
                            market = Market.A_SHARE,
                        )
                    }

                    Market.HONG_KONG -> {
                        if (typeCode != "31") return@mapNotNull null
                        SecurityLookupResult(
                            symbol = "${code.padStart(4, '0')}.HK",
                            name = name,
                            market = Market.HONG_KONG,
                        )
                    }

                    Market.US -> {
                        if (typeCode != "41") return@mapNotNull null
                        SecurityLookupResult(
                            symbol = code.uppercase(),
                            name = name,
                            market = Market.US,
                        )
                    }

                    Market.CASH -> null
                }
            }
            .distinctBy { requestKey(it.symbol, it.market) }
    }

    private fun normalizeTencentCode(request: QuoteRequest): String = when (request.market) {
        Market.A_SHARE -> {
            val raw = request.symbol.substringBefore(".")
            if (raw.startsWith("6")) "sh$raw" else "sz$raw"
        }

        Market.HONG_KONG -> {
            val raw = request.symbol.substringBefore(".").padStart(5, '0')
            "hk$raw"
        }

        Market.US -> error("US market does not use Tencent quotes")
        Market.CASH -> error("Cash market does not support Tencent quotes")
    }

    private fun normalizeSinaCode(request: QuoteRequest): String = when (request.market) {
        Market.A_SHARE -> {
            val raw = request.symbol.substringBefore(".")
            if (raw.startsWith("6")) "sh$raw" else "sz$raw"
        }

        Market.HONG_KONG -> {
            val raw = request.symbol.substringBefore(".").padStart(5, '0')
            "hk$raw"
        }

        Market.US -> "gb_${request.symbol.lowercase()}"
        Market.CASH -> error("Cash market does not support Sina quotes")
    }

    private fun resolveHistoryCode(request: QuoteRequest): String = when (request.market) {
        Market.A_SHARE -> {
            val raw = request.symbol.substringBefore(".")
            if (raw.startsWith("6")) "sh$raw" else "sz$raw"
        }

        Market.HONG_KONG -> {
            val raw = request.symbol.substringBefore(".").padStart(5, '0')
            "hk$raw"
        }

        Market.US -> {
            val normalized = resolveUsHistorySymbol(request.symbol)
            "us$normalized"
        }

        Market.CASH -> ""
    }

    private fun resolveUsHistorySymbol(symbol: String): String {
        if ('.' in symbol) return symbol.uppercase()

        val body = httpGet(
            url = "http://qt.gtimg.cn/q=us${symbol.uppercase()}",
            headers = mapOf(
                "Referer" to "https://gu.qq.com/",
                "User-Agent" to WEB_USER_AGENT,
            ),
            charsetName = "GB18030",
        )
        val payload = body.substringAfter("=\"", "")
            .substringBeforeLast("\"", "")
        val fields = payload.split("~")
        return fields.getOrNull(2)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.uppercase()
            ?: symbol.uppercase()
    }

    private fun httpGet(
        url: String,
        headers: Map<String, String>,
        charsetName: String,
    ): String {
        val targetUrl = URL(url)
        return runCatching {
            executeHttpGet(
                targetUrl = targetUrl,
                headers = headers,
                charsetName = charsetName,
            )
        }.recoverCatching { originalError ->
            if (targetUrl.protocol != "http") throw originalError

            val resolvedIps = resolveIpv4WithDoh(targetUrl.host)
            if (resolvedIps.isEmpty()) {
                throw originalError
            }

            Log.w(TAG, "Retry ${targetUrl.host} with DoH IPs: ${resolvedIps.joinToString()}")
            var lastError: Throwable = originalError
            for (ip in resolvedIps) {
                val ipUrl = URL("${targetUrl.protocol}://$ip${targetUrl.file}")
                runCatching {
                    return@recoverCatching executeHttpGet(
                        targetUrl = ipUrl,
                        headers = headers + ("Host" to targetUrl.host),
                        charsetName = charsetName,
                    )
                }.onFailure { error ->
                    lastError = error
                    Log.w(TAG, "Retry with $ip failed for ${targetUrl.host}: ${error.message}")
                }
            }
            throw lastError
        }.getOrThrow()
    }

    private fun executeHttpGet(
        targetUrl: URL,
        headers: Map<String, String>,
        charsetName: String,
    ): String {
        val connection = (targetUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = 2_500
            readTimeout = 2_500
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }

        return try {
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charset.forName(charsetName))?.use { it.readText() }.orEmpty()
            if (statusCode !in 200..299) {
                throw IOException("HTTP $statusCode for $targetUrl: ${body.take(120)}")
            }
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun resolveIpv4WithDoh(host: String): List<String> {
        val encodedHost = URLEncoder.encode(host, Charsets.UTF_8.name())
        val resolverUrl = URL("https://1.1.1.1/dns-query?name=$encodedHost&type=A")
        val connection = (resolverUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 2_500
            readTimeout = 2_500
            setRequestProperty("Accept", "application/dns-json")
            setRequestProperty("User-Agent", WEB_USER_AGENT)
        }

        return try {
            val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(response)
            val answers = json.optJSONArray("Answer") ?: return emptyList()
            buildList {
                for (index in 0 until answers.length()) {
                    val record = answers.optJSONObject(index) ?: continue
                    if (record.optInt("type") == 1) {
                        val ip = record.optString("data")
                        if (ip.isNotBlank()) add(ip)
                    }
                }
            }
        } catch (error: Exception) {
            Log.w(TAG, "DoH resolve failed for $host: ${error.message}")
            emptyList()
        } finally {
            connection.disconnect()
        }
    }

    private fun parseTencentTimestamp(raw: String?): Long {
        if (raw.isNullOrBlank()) return System.currentTimeMillis()
        val formatter = if (raw.contains('/')) {
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
        } else {
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
        }
        return runCatching {
            LocalDateTime.parse(raw, formatter)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrElse { System.currentTimeMillis() }
    }

    private fun parseSinaTimestamp(date: String, time: String): Long {
        if (date.isBlank() || time.isBlank()) return System.currentTimeMillis()
        return runCatching {
            LocalDateTime.parse(
                "$date $time",
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            ).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrElse { System.currentTimeMillis() }
    }

    private fun parseSinaHongKongTimestamp(raw: String): Long {
        if (raw.isBlank()) return System.currentTimeMillis()
        return runCatching {
            LocalDateTime.parse(
                "$raw:00",
                DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            ).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrElse { System.currentTimeMillis() }
    }

    private fun parseSinaUsTimestamp(raw: String): Long {
        if (raw.isBlank()) return System.currentTimeMillis()
        return runCatching {
            LocalDateTime.parse(
                raw,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            ).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrElse { System.currentTimeMillis() }
    }

    private fun parseHistoricalDateOrNull(raw: String): LocalDate? = runCatching {
        LocalDate.parse(raw)
    }.getOrNull()

    private fun requestKey(symbol: String, market: Market): String = "${market.name}:$symbol"

    private companion object {
        const val TAG = "QuoteDataSource"
        const val WEB_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0 Mobile Safari/537.36"
    }
}

class FakeQuoteDataSource : QuoteDataSource {
    override val isConfigured: Boolean = false
    override val providerLabel: String = "演示行情"

    private var cycle: Int = 0

    override suspend fun refreshQuotes(requests: List<QuoteRequest>): List<QuoteSnapshotEntity> {
        cycle += 1
        val now = System.currentTimeMillis()
        return requests.map { request ->
            val quote = baseQuotes[request.symbol] ?: BaseQuote(10.0, 9.9)
            if (request.symbol == "0941.HK" && cycle % 4 == 0) {
                QuoteSnapshotEntity(
                    symbol = request.symbol,
                    market = request.market.name,
                    name = request.name,
                    currentPrice = null,
                    previousClose = quote.previousClose,
                    lastUpdatedAt = now,
                )
            } else {
                val drift = sin((cycle + request.symbol.hashCode()) * 0.13) *
                    if (request.market == Market.A_SHARE) 2.4 else 3.2
                val current = (quote.basePrice + drift).coerceAtLeast(1.0)
                QuoteSnapshotEntity(
                    symbol = request.symbol,
                    market = request.market.name,
                    name = request.name,
                    currentPrice = current,
                    previousClose = quote.previousClose,
                    lastUpdatedAt = now,
                )
            }
        }
    }

    override suspend fun fetchHistoricalCloses(
        requests: List<QuoteRequest>,
        lookbackDays: Int,
    ): List<HistoricalClosePoint> {
        val startDate = LocalDate.now().minusDays(lookbackDays.toLong())
        return requests.flatMap { request ->
            val quote = baseQuotes[request.symbol] ?: BaseQuote(10.0, 9.9)
            generateSequence(startDate) { current ->
                current.plusDays(1).takeIf { !it.isAfter(LocalDate.now()) }
            }.filterNot { it.dayOfWeek.value >= 6 }
                .mapIndexed { index, date ->
                    val drift = sin((index + request.symbol.hashCode()) * 0.11) *
                        if (request.market == Market.A_SHARE) 2.1 else 2.8
                    HistoricalClosePoint(
                        symbol = request.symbol,
                        market = request.market,
                        date = date,
                        closePrice = (quote.basePrice + drift).coerceAtLeast(1.0),
                    )
                }.toList()
        }
    }

    override suspend fun searchSecurities(
        keyword: String,
        market: Market,
        limit: Int,
    ): List<SecurityLookupResult> {
        val normalizedKeyword = keyword.trim()
        if (normalizedKeyword.isBlank()) return emptyList()

        return fakeNames.mapNotNull { (symbol, name) ->
            val currentMarket = when {
                symbol.endsWith(".HK") -> Market.HONG_KONG
                symbol.all { it.isLetterOrDigit() || it == '.' || it == '-' } &&
                    symbol.any(Char::isLetter) &&
                    !symbol.all(Char::isDigit) -> Market.US
                else -> Market.A_SHARE
            }
            if (currentMarket != market) return@mapNotNull null

            val displaySymbol = when (currentMarket) {
                Market.HONG_KONG -> symbol
                Market.US -> symbol
                Market.A_SHARE -> symbol.substringBefore(".")
                Market.CASH -> symbol
            }
            if (
                displaySymbol.contains(normalizedKeyword, ignoreCase = true) ||
                name.contains(normalizedKeyword, ignoreCase = true)
            ) {
                SecurityLookupResult(
                    symbol = symbol,
                    name = name,
                    market = currentMarket,
                )
            } else {
                null
            }
        }.take(limit)
    }

    private data class BaseQuote(
        val basePrice: Double,
        val previousClose: Double,
    )

    private companion object {
        val baseQuotes = mapOf(
            "300750" to BaseQuote(201.80, 195.40),
            "0700.HK" to BaseQuote(305.40, 308.85),
            "0941.HK" to BaseQuote(75.10, 74.60),
            "AAPL" to BaseQuote(271.06, 273.43),
        )
        val fakeNames = mapOf(
            "300750" to "宁德时代",
            "0700.HK" to "腾讯控股",
            "0941.HK" to "中国移动",
            "AAPL" to "苹果",
        )
    }
}
