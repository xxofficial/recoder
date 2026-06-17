package com.recoder.stockledger.data.repository

import android.content.Context
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
import com.recoder.stockledger.data.YahooSplitEvent
import com.recoder.stockledger.data.ZhuoruiEmailSyncConfig
import com.recoder.stockledger.data.importer.HsbcNotificationParser
import com.recoder.stockledger.data.importer.HsbcNotificationStatus
import com.recoder.stockledger.data.importer.ParsedZhuoruiEmail
import com.recoder.stockledger.data.importer.ZhuoruiEmailParser
import com.recoder.stockledger.data.importer.ZhuoruiStatementPdfParser
import com.recoder.stockledger.data.importer.ParsedStatementTrade
import com.recoder.stockledger.data.local.LedgerDao
import com.recoder.stockledger.data.local.LedgerEntity
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.recoder.stockledger.data.local.HistoricalCloseEntity
import com.recoder.stockledger.data.local.toDomain
import com.recoder.stockledger.data.local.toEntity
import com.recoder.stockledger.domain.market.MarketTradingSessions
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
    val assetType: String = "STOCK",
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
    val quantity: Double,
    val commission: Double,
    val tax: Double,
    val note: String,
    val tradeTime: String,
    val createdAt: Long,
    val ledgerId: Long = 1L,
    val investorName: String? = null,
    val assetType: String = "STOCK",
    val underlyingSymbol: String? = null,
    val expiryDate: String? = null,
    val strikePrice: Double? = null,
    val optionType: String? = null,
)

data class ImportedBackup(
    val displayCurrencyName: String?,
    val enabledPlatforms: List<BrokerPlatform>,
    val selectedPlatform: BrokerPlatform?,
    val ledgers: List<LedgerEntity>,
    val transactions: List<TransactionEntity>,
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
    val importedExternalReferences: List<String> = emptyList(),
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

interface LedgerRepository {
    val transactions: Flow<List<TransactionEntity>>
    val quotes: Flow<List<QuoteSnapshotEntity>>
    val ledgers: Flow<List<LedgerEntity>>

    suspend fun seedIfEmpty()
    suspend fun purgeLegacySeedData()
    suspend fun addTrade(input: TradeDraftInput)
    suspend fun updateTrade(transactionId: Long, input: TradeDraftInput)
    suspend fun deleteTrade(transactionId: Long): Int
    suspend fun deleteTransactionsByIds(ids: List<Long>): Int
    suspend fun replaceTransactions(transactions: List<TransactionEntity>)
    suspend fun exportBackup(
        outputStream: OutputStream,
        displayCurrencyName: String,
        enabledPlatforms: List<BrokerPlatform>,
        selectedPlatform: BrokerPlatform?,
        selectedLedgerIds: List<Long>,
        selectedPlatforms: List<String>,
    )
    suspend fun parseBackup(inputStream: InputStream): ImportedBackup
    suspend fun executeImportBackup(
        imported: ImportedBackup,
        selectedLedgerIds: List<Long>,
        selectedPlatforms: List<String>
    )
    suspend fun deleteHolding(symbol: String, market: Market): Int

    suspend fun getAllLedgers(): List<LedgerEntity>
    suspend fun getLedgerById(id: Long): LedgerEntity?
    suspend fun insertLedger(ledger: LedgerEntity): Long
    suspend fun updateLedger(ledger: LedgerEntity): Int
    suspend fun deleteLedgerWithTransactions(ledgerId: Long)
    suspend fun moveTransactionsToLedger(ids: List<Long>, targetLedgerId: Long): Int
}

interface MarketDataRepository {
    val historicalCloses: StateFlow<List<HistoricalClosePoint>>
    val exchangeRates: StateFlow<ExchangeRates>
    val isUsingRealtimeQuotes: Boolean
    val quoteProviderLabel: String

    suspend fun lookupSecurity(rawInput: String, market: Market): SecurityLookupResult?
    suspend fun searchSecurities(rawInput: String, market: Market, limit: Int = 6): List<SecurityLookupResult>
    suspend fun refreshQuotes(requests: List<QuoteRequest>): Long
    suspend fun refreshQuotesForPortfolio(transactions: List<TransactionEntity>, force: Boolean = false): Long
    suspend fun fetchStockSplits(symbol: String, period1: Long = 0L, period2: Long = 4102416000L): List<YahooSplitEvent>
    suspend fun repairSuspiciousStockNames(): Int
    suspend fun refreshExchangeRates(): ExchangeRateRefreshResult
}

interface ImportRepository {
    suspend fun importSharedTradeText(
        rawText: String,
        receivedAtMillis: Long = System.currentTimeMillis(),
    ): TradeImportResult

    suspend fun importHsbcNotificationText(
        rawText: String,
        receivedAtMillis: Long = System.currentTimeMillis(),
    ): TradeImportResult

    suspend fun importStatementPdf(
        inputStream: InputStream,
        password: String,
        platform: BrokerPlatform,
        ledgerId: Long = 1L,
    ): List<TradeImportResult>

    suspend fun importParsedTrades(
        parsedTrades: List<ParsedStatementTrade>,
        platform: BrokerPlatform = BrokerPlatform.ZHUORUI,
        ledgerId: Long = 1L,
    ): List<TradeImportResult>

    suspend fun syncZhuoruiMailbox(
        config: ZhuoruiEmailSyncConfig,
        lastSyncAtMillis: Long,
        fetchCount: Int = 200,
        earliestReceivedAtMillis: Long? = null,
    ): ZhuoruiMailboxSyncResult

    suspend fun repairNamesByExternalReferences(externalReferences: List<String>)
}

interface StockLedgerRepository : LedgerRepository, MarketDataRepository, ImportRepository

class DefaultLedgerRepository(
    private val context: Context,
    private val dao: LedgerDao,
    private val quoteDataSource: QuoteDataSource,
    private val exchangeRateDataSource: FrankfurterExchangeRateDataSource,
    private val platformFeePlanSelectionProvider: () -> Map<BrokerPlatform, String> = { emptyMap() },
) : StockLedgerRepository {
    override val transactions: Flow<List<TransactionEntity>> = dao.observeTransactions()
    override val quotes: Flow<List<QuoteSnapshotEntity>> = dao.observeQuotes()
    override val ledgers: Flow<List<LedgerEntity>> = dao.observeLedgers()
    private val _historicalCloses = MutableStateFlow<List<HistoricalClosePoint>>(emptyList())
    override val historicalCloses: StateFlow<List<HistoricalClosePoint>> = _historicalCloses
    private val _exchangeRates = MutableStateFlow(exchangeRateDataSource.currentRates())
    override val exchangeRates: StateFlow<ExchangeRates> = _exchangeRates
    private var lastQuotesRefreshTimeMillis: Long = 0L

    init {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cached = dao.getAllHistoricalCloses().map { it.toDomain() }
                _historicalCloses.value = cached
                Log.d("DefaultLedgerRepository", "Loaded ${cached.size} cached historical closes from database")
            } catch (e: Exception) {
                Log.e("DefaultLedgerRepository", "Failed to load cached historical closes", e)
            }
        }
    }

    override val isUsingRealtimeQuotes: Boolean
        get() = quoteDataSource.isConfigured

    override val quoteProviderLabel: String
        get() = quoteDataSource.providerLabel

    override suspend fun seedIfEmpty() {
        Log.d(TAG, "seedIfEmpty starting transaction cleanup and normalization...")
        try {
            val transactions = dao.getAllTransactions()
            Log.d(TAG, "Retrieved ${transactions.size} transactions from database.")
            var updatedCount = 0
            for (txn in transactions) {
                var updated = txn
                
                // 1. Clean name (CJK radicals & control characters)
                val cleanName = cleanNameString(txn.name)
                if (cleanName != txn.name) {
                    updated = updated.copy(name = cleanName)
                }
                
                // 2. HK Symbol normalization
                if (txn.market == Market.HK.name) {
                    val cleanSym = cleanHkSymbol(txn.symbol)
                    if (cleanSym != txn.symbol) {
                        updated = updated.copy(symbol = cleanSym)
                    }
                }
                
                // 3. US Index to ETF repair
                if (txn.market == Market.US.name) {
                    val cleanUpper = cleanName.uppercase().replace(" ", "")
                    if (txn.symbol == ".INX" || txn.symbol == "SPY" || cleanUpper.contains("标普500") || cleanUpper.contains("SP500") || cleanUpper.contains("SPY")) {
                        if (cleanUpper.contains("ETF") || cleanUpper.contains("SPDR")) {
                            if (txn.symbol != "SPY" || txn.name != "SPDR标普500 ETF") {
                                updated = updated.copy(symbol = "SPY", name = "SPDR标普500 ETF")
                            }
                        }
                    }
                    if (txn.symbol == ".NDX" || txn.symbol == ".IXIC" || txn.symbol == "QQQ" || cleanUpper.contains("纳指100") || cleanUpper.contains("纳斯达克100") || cleanUpper.contains("QQQ")) {
                        if (cleanUpper.contains("ETF") || cleanUpper.contains("INVESCO") || cleanUpper.contains("纳指") || cleanUpper.contains("纳斯达克")) {
                            if (txn.symbol != "QQQ" || txn.name != "Invesco NASDAQ 100 ETF") {
                                updated = updated.copy(symbol = "QQQ", name = "Invesco NASDAQ 100 ETF")
                            }
                        }
                    }
                }
                
                // 4. Option-formatted symbol in STOCK transaction repair
                if (txn.assetType == "STOCK" || txn.assetType == "stock") {
                    val optionRegex = Regex("""^([A-Za-z]+)\s*(\d{6})([CP])(\d+).*$""")
                    val match = optionRegex.matchEntire(updated.symbol)
                    if (match != null) {
                        val underlying = match.groupValues[1].uppercase()
                        if (updated.symbol != underlying) {
                            updated = updated.copy(symbol = underlying)
                        }
                    }
                }
                
                if (updated != txn) {
                    Log.d(TAG, "Updating transaction id=${txn.id}: symbol='${txn.symbol}'->'${updated.symbol}', name='${txn.name}'->'${updated.name}'")
                    val rows = dao.updateTransaction(updated)
                    Log.d(TAG, "Update result: $rows rows updated.")
                    updatedCount++
                }
            }
            Log.d(TAG, "seedIfEmpty completed. Total updated transactions: $updatedCount")
        } catch (e: Exception) {
            Log.e(TAG, "Error during seedIfEmpty migration", e)
        }
    }

    override suspend fun purgeLegacySeedData() {
        dao.deleteLegacySeedTransactions()
        if (dao.transactionCount() == 0) {
            dao.clearQuotes()
        }
    }

    override suspend fun addTrade(input: TradeDraftInput) {
        val sanitizedTradeDate = sanitizeOptionTradeDate(
            tradeType = input.tradeType.name,
            symbol = input.symbol,
            assetType = input.assetType,
            expiryDate = input.expiryDate,
            tradeDate = input.tradeDate
        )
        val resolvedName = resolveConsistentName(input.symbol, input.market, input.name)
        dao.insertTransaction(
            TransactionEntity(
                tradeType = input.tradeType.name,
                platform = input.platform.name,
                sourceChannel = input.sourceChannel?.name,
                externalReference = input.externalReference,
                market = input.market.name,
                symbol = input.symbol,
                name = resolvedName,
                tradeDate = sanitizedTradeDate,
                tradeTime = input.tradeTime,
                price = input.price,
                quantity = input.quantity,
                commission = input.commission,
                tax = input.tax,
                note = input.note,
                createdAt = input.createdAt,
                ledgerId = input.ledgerId,
                investorName = input.investorName,
                assetType = input.assetType,
                underlyingSymbol = input.underlyingSymbol,
                expiryDate = input.expiryDate,
                strikePrice = input.strikePrice,
                optionType = input.optionType,
            ),
        )
    }

    override suspend fun updateTrade(
        transactionId: Long,
        input: TradeDraftInput,
    ) {
        val sanitizedTradeDate = sanitizeOptionTradeDate(
            tradeType = input.tradeType.name,
            symbol = input.symbol,
            assetType = input.assetType,
            expiryDate = input.expiryDate,
            tradeDate = input.tradeDate
        )
        val resolvedName = resolveConsistentName(input.symbol, input.market, input.name)
        val entity = TransactionEntity(
            id = transactionId,
            tradeType = input.tradeType.name,
            platform = input.platform.name,
            sourceChannel = input.sourceChannel?.name,
            externalReference = input.externalReference,
            market = input.market.name,
            symbol = input.symbol,
            name = resolvedName,
            tradeDate = sanitizedTradeDate,
            tradeTime = input.tradeTime,
            price = input.price,
            quantity = input.quantity,
            commission = input.commission,
            tax = input.tax,
            note = input.note,
            createdAt = input.createdAt,
            ledgerId = input.ledgerId,
            investorName = input.investorName,
            assetType = input.assetType,
            underlyingSymbol = input.underlyingSymbol,
            expiryDate = input.expiryDate,
            strikePrice = input.strikePrice,
            optionType = input.optionType,
        )
        dao.updateTransaction(entity)

        val extRef = input.externalReference
        if (extRef != null && extRef.startsWith("transfer_")) {
            val otherTxs = dao.getAllTransactions().filter { it.externalReference == extRef && it.id != transactionId }
            for (other in otherTxs) {
                val updatedOther = other.copy(
                    market = entity.market,
                    symbol = entity.symbol,
                    name = entity.name,
                    tradeDate = entity.tradeDate,
                    tradeTime = entity.tradeTime,
                    price = entity.price,
                    quantity = entity.quantity,
                    ledgerId = entity.ledgerId,
                    investorName = entity.investorName,
                )
                dao.updateTransaction(updatedOther)
            }
        }
    }

    private suspend fun resolveConsistentName(symbol: String, market: Market, providedName: String): String {
        if (symbol.isBlank() || symbol == "CASH") {
            return providedName.ifBlank { "资金账户" }
        }

        // 1. Try to find from existing transactions to maintain ledger-wide consistency
        val existingName = dao.findStockNameFromTransactions(symbol, market.name)
        if (existingName != null && existingName.isNotBlank() && existingName != symbol) return existingName

        // 2. If provided name is "good" (not blank, not same as symbol), use it
        val isSuspicious = providedName.isBlank() || providedName == symbol
        if (!isSuspicious) return providedName

        // 3. Remote lookup fallback (searchSecurities / Suggest API)
        // Prioritize this over quotes as it usually provides better/more consistent names for search
        val lookup = lookupSecurity(symbol, market)
        if (lookup != null && lookup.name.isNotBlank() && lookup.name != lookup.symbol) {
            return lookup.name
        }

        // 4. Try to find from local quote snapshots (populated by refreshQuotes) as fallback
        val quoteName = dao.findStockNameFromQuotes(symbol, market.name)
        if (quoteName != null && quoteName.isNotBlank() && quoteName != symbol) return quoteName

        // 5. If we had a lookup result but it was just the symbol, return it instead of the suspicious provided name
        if (lookup != null && lookup.name.isNotBlank()) {
            return lookup.name
        }

        return providedName
    }

    override suspend fun deleteTrade(transactionId: Long): Int {
        val tx = dao.getTransactionById(transactionId)
        val extRef = tx?.externalReference
        if (extRef != null && extRef.startsWith("transfer_")) {
            return dao.deleteTransactionsByExternalReference(extRef)
        }
        return dao.deleteTransactionById(transactionId)
    }

    override suspend fun deleteTransactionsByIds(ids: List<Long>): Int {
        val transactionsToDelete = dao.getAllTransactions().filter { it.id in ids }
        val extRefs = transactionsToDelete.mapNotNull { it.externalReference }.filter { it.startsWith("transfer_") }.toSet()
        if (extRefs.isNotEmpty()) {
            var count = 0
            for (ref in extRefs) {
                count += dao.deleteTransactionsByExternalReference(ref)
            }
            val remainingIds = ids.filter { id ->
                val tx = transactionsToDelete.firstOrNull { it.id == id }
                tx == null || tx.externalReference == null || !tx.externalReference.startsWith("transfer_")
            }
            if (remainingIds.isNotEmpty()) {
                count += dao.deleteTransactionsByIds(remainingIds)
            }
            return count
        }
        return dao.deleteTransactionsByIds(ids)
    }

    override suspend fun replaceTransactions(transactions: List<TransactionEntity>) {
        dao.replaceTransactions(transactions)
    }

    override suspend fun exportBackup(
        outputStream: OutputStream,
        displayCurrencyName: String,
        enabledPlatforms: List<BrokerPlatform>,
        selectedPlatform: BrokerPlatform?,
        selectedLedgerIds: List<Long>,
        selectedPlatforms: List<String>,
    ) = withContext(Dispatchers.IO) {
        val allLedgers = getAllLedgers()
        val ledgersSnapshot = allLedgers.filter { it.id in selectedLedgerIds }
        val transactionsSnapshot = transactions.first().filter {
            it.ledgerId in selectedLedgerIds && it.platform in selectedPlatforms
        }
        val payload = JSONObject().apply {
            put("version", 4)
            put("exportedAt", System.currentTimeMillis())
            put("displayCurrency", displayCurrencyName)
            put("enabledPlatforms", org.json.JSONArray().apply {
                enabledPlatforms.forEach { put(it.name) }
            })
            put("selectedPlatform", selectedPlatform?.name)
            put("ledgers", org.json.JSONArray().apply {
                ledgersSnapshot.forEach { ledger ->
                    put(
                        JSONObject().apply {
                            put("id", ledger.id)
                            put("name", ledger.name)
                            put("type", ledger.type)
                            put("description", ledger.description)
                            put("partners", ledger.partners)
                            put("createdAt", ledger.createdAt)
                        }
                    )
                }
            })
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
                            put("ledgerId", transaction.ledgerId)
                            put("investorName", transaction.investorName)
                        },
                    )
                }
            })
        }
        outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload.toString(2)) }
    }

    override suspend fun parseBackup(inputStream: InputStream): ImportedBackup = withContext(Dispatchers.IO) {
        val json = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val payload = JSONObject(json)
        val transactionsArray = payload.optJSONArray("transactions") ?: org.json.JSONArray()
        val ledgersArray = payload.optJSONArray("ledgers")
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

        val parsedLedgers = buildList {
            if (ledgersArray != null) {
                for (index in 0 until ledgersArray.length()) {
                    val item = ledgersArray.optJSONObject(index) ?: continue
                    add(
                        LedgerEntity(
                            id = item.optLong("id"),
                            name = item.optString("name"),
                            type = item.optString("type"),
                            description = item.optString("description", ""),
                            partners = item.optString("partners", ""),
                            createdAt = item.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            } else {
                add(
                    LedgerEntity(
                        id = 1L,
                        name = "默认个人账本",
                        type = "PERSONAL",
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
        }

        val parsedTransactions = buildList {
            for (index in 0 until transactionsArray.length()) {
                val item = transactionsArray.optJSONObject(index) ?: continue
                add(
                    TransactionEntity(
                        id = item.optLong("id"),
                        tradeType = item.optString("tradeType"),
                        platform = item.optString("platform").ifBlank { BrokerPlatform.UNSPECIFIED.name },
                        sourceChannel = item.optString("sourceChannel").takeIf { it.isNotBlank() },
                        externalReference = item.optString("externalReference").takeIf { it.isNotBlank() },
                        market = Market.fromString(item.optString("market"))?.name ?: item.optString("market"),
                        symbol = item.optString("symbol"),
                        name = item.optString("name"),
                        tradeDate = item.optString("tradeDate"),
                        tradeTime = item.optString("tradeTime"),
                        price = item.optDouble("price"),
                        quantity = item.optDouble("quantity"),
                        commission = item.optDouble("commission"),
                        tax = item.optDouble("tax"),
                        note = item.optString("note"),
                        createdAt = item.optLong("createdAt"),
                        ledgerId = item.optLong("ledgerId", 1L),
                        investorName = item.optString("investorName").takeIf { it.isNotBlank() },
                    ),
                )
            }
        }

        ImportedBackup(
            displayCurrencyName = payload.optString("displayCurrency").takeIf { it.isNotBlank() },
            enabledPlatforms = enabledPlatforms,
            selectedPlatform = selectedPlatform,
            ledgers = parsedLedgers,
            transactions = parsedTransactions,
        )
    }

    override suspend fun executeImportBackup(
        imported: ImportedBackup,
        selectedLedgerIds: List<Long>,
        selectedPlatforms: List<String>
    ) = withContext(Dispatchers.IO) {
        val existingLedgers = getAllLedgers()
        val ledgerIdMap = mutableMapOf<Long, Long>()

        imported.ledgers.filter { it.id in selectedLedgerIds }.forEach { importedLedger ->
            val existing = existingLedgers.firstOrNull {
                it.name.equals(importedLedger.name, ignoreCase = true) && it.type == importedLedger.type
            }
            if (existing != null) {
                ledgerIdMap[importedLedger.id] = existing.id
            } else {
                val newLedger = LedgerEntity(
                    name = importedLedger.name,
                    type = importedLedger.type,
                    description = importedLedger.description,
                    partners = importedLedger.partners,
                    createdAt = importedLedger.createdAt
                )
                val newId = insertLedger(newLedger)
                ledgerIdMap[importedLedger.id] = newId
            }
        }

        val transactionsToImport = imported.transactions.filter {
            it.ledgerId in selectedLedgerIds && it.platform in selectedPlatforms
        }

        val symbolToName = transactionsToImport
            .filter { it.symbol.isNotBlank() && it.name.isNotBlank() }
            .groupBy { it.market to it.symbol }
            .mapValues { (_, txns) ->
                txns.maxByOrNull { "${it.tradeDate} ${it.tradeTime}" }?.name ?: ""
            }

        val allExistingTransactions = dao.getAllTransactions()
        val transactionsToInsert = mutableListOf<TransactionEntity>()

        transactionsToImport.forEach { txn ->
            val targetLedgerId = ledgerIdMap[txn.ledgerId] ?: return@forEach
            val consistentName = symbolToName[txn.market to txn.symbol]
            val finalName = if (!consistentName.isNullOrBlank()) consistentName else txn.name

            val sanitizedTradeDate = sanitizeOptionTradeDate(
                tradeType = txn.tradeType,
                symbol = txn.symbol,
                assetType = txn.assetType,
                expiryDate = txn.expiryDate,
                tradeDate = txn.tradeDate
            )

            val isDuplicate = allExistingTransactions.any { existing ->
                existing.ledgerId == targetLedgerId &&
                existing.platform == txn.platform &&
                existing.symbol == txn.symbol &&
                existing.market == txn.market &&
                existing.tradeDate == sanitizedTradeDate &&
                existing.tradeTime == txn.tradeTime &&
                existing.tradeType == txn.tradeType &&
                existing.quantity == txn.quantity &&
                java.lang.Math.abs(existing.price - txn.price) < 0.0001
            }

            if (!isDuplicate) {
                transactionsToInsert.add(
                    txn.copy(
                        id = 0,
                        ledgerId = targetLedgerId,
                        name = finalName,
                        tradeDate = sanitizedTradeDate
                    )
                )
            }
        }

        if (transactionsToInsert.isNotEmpty()) {
            dao.insertTransactions(transactionsToInsert)
        }
    }

    override suspend fun importSharedTradeText(
        rawText: String,
        receivedAtMillis: Long,
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

    override suspend fun importHsbcNotificationText(
        rawText: String,
        receivedAtMillis: Long,
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
        val resolvedName = resolveConsistentName(parsed.symbol, parsed.market, parsed.name)

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
        ledgerId: Long = 1L,
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
                ledgerId = ledgerId,
            ),
        )
        return TradeImportResult(
            outcome = TradeImportOutcome.IMPORTED,
            message = "已自动导入卓锐${parsed.tradeType.label}记录 ${parsed.symbol} ${parsed.quantity} 股，费用已按方案估算",
            externalReference = parsed.externalReference,
        )
    }

    override suspend fun importStatementPdf(
        inputStream: InputStream,
        password: String,
        platform: BrokerPlatform,
        ledgerId: Long,
    ): List<TradeImportResult> = withContext(Dispatchers.IO) {
        Log.d(TAG, "开始导入PDF结单, platform=${platform.name}, password长度=${password.length}, ledgerId=$ledgerId")
        val parsedTrades = when (platform) {
            BrokerPlatform.USMART -> {
                com.recoder.stockledger.data.importer.USmartStatementPdfParser.parse(inputStream, password)
            }
            BrokerPlatform.HSBC -> {
                com.recoder.stockledger.data.importer.HsbcStatementPdfParser.parse(inputStream, password)
            }
            BrokerPlatform.LONGBRIDGE -> {
                com.recoder.stockledger.data.importer.LongBridgeStatementPdfParser.parsePdf(inputStream, password, context.cacheDir)
            }
            else -> {
                ZhuoruiStatementPdfParser.parsePdf(inputStream, password, context.cacheDir)
            }
        }
        Log.d(TAG, "PDF解析完成, 找到${parsedTrades.size}条交易记录")
        if (parsedTrades.isEmpty()) {
            Log.w(TAG, "PDF结单中未找到可导入的交易记录")
            return@withContext emptyList()
        }

        val results = mutableListOf<TradeImportResult>()
        val matchedDbTxIds = mutableSetOf<Long>()
        val normalizedTrades = normalizeStatementTrades(parsedTrades)
        for (parsed in normalizedTrades) {
            var symbol = parsed.symbol.trim()
            var name = parsed.name.trim()
            if (parsed.tradeType.isSecurityTrade && symbol.isBlank() && name.isNotBlank()) {
                val resolvedSymbol = dao.findSymbolByName(name, parsed.market.name, parsed.assetType)
                if (resolvedSymbol != null && resolvedSymbol.isNotBlank()) {
                    symbol = resolvedSymbol
                    Log.d(TAG, "从历史交易中自动修复空代码: 证券名称=$name, 匹配代码=$symbol")
                } else {
                    val lookup = runCatching {
                        searchSecurities(name, parsed.market, limit = 1).firstOrNull()
                    }.getOrNull()
                    if (lookup != null && lookup.symbol.isNotBlank()) {
                        symbol = lookup.symbol
                        if (name.isBlank() || name == symbol) {
                            name = lookup.name
                        }
                        Log.d(TAG, "从网络检索中自动修复空代码: 证券名称=$name, 匹配代码=$symbol")
                    }
                }
            }
            val finalParsed = parsed.copy(symbol = symbol, name = name)
            
            val externalReference = "${platform.shortLabel}-STMT-${finalParsed.tradeRef}"
            val existing = dao.findTransactionByExternalReference(
                platform = platform.name,
                externalReference = externalReference,
            )
            if (existing != null) {
                Log.d(TAG, "发现重复交易(ExternalReference): 证券=${finalParsed.symbol}, 日期=${finalParsed.tradeDate}, 类型=${finalParsed.tradeType}, 数量=${finalParsed.quantity}, 价格=${finalParsed.price}, extRef=$externalReference")
                results.add(
                    TradeImportResult(
                        outcome = TradeImportOutcome.DUPLICATE,
                        message = "交易 ${finalParsed.symbol} ${finalParsed.quantity} 股已存在，已跳过",
                        externalReference = externalReference,
                    )
                )
                matchedDbTxIds.add(existing.id)
                continue
            }

            val duplicates = dao.findDuplicateTransactions(
                platform = platform.name,
                symbol = finalParsed.symbol,
                market = finalParsed.market.name,
                tradeDate = finalParsed.tradeDate.toString(),
                tradeType = finalParsed.tradeType.name,
                quantity = finalParsed.quantity,
                price = finalParsed.price,
            )
            val duplicate = duplicates.firstOrNull { it.id !in matchedDbTxIds }
            if (duplicate != null) {
                Log.d(TAG, "发现重复交易(内容匹配): 证券=${finalParsed.symbol}, 日期=${finalParsed.tradeDate}, 类型=${finalParsed.tradeType}, 数量=${finalParsed.quantity}, 价格=${finalParsed.price}")
                results.add(
                    TradeImportResult(
                        outcome = TradeImportOutcome.DUPLICATE,
                        message = "交易 ${finalParsed.symbol} ${finalParsed.quantity} 股已存在（按内容匹配），已跳过",
                        externalReference = externalReference,
                    )
                )
                matchedDbTxIds.add(duplicate.id)
                continue
            }

            val hasParsedFees = finalParsed.commission != null || finalParsed.tax != null || finalParsed.platformFee != null
            val feeEstimate = if (!hasParsedFees && finalParsed.tradeType.isSecurityTrade) {
                estimateImportedTradeFees(
                    tradeType = finalParsed.tradeType,
                    platform = platform,
                    market = finalParsed.market,
                    price = finalParsed.price,
                    quantity = finalParsed.quantity,
                    tradeDate = finalParsed.tradeDate.toString(),
                    tradeTime = finalParsed.tradeTime ?: "00:00",
                )
            } else null

            val commission = finalParsed.commission ?: feeEstimate?.commission ?: 0.0
            val tax = finalParsed.tax ?: feeEstimate?.tax ?: 0.0
            val noteSuffix = if (hasParsedFees) {
                "费用按结单原始数据导入"
            } else {
                feeEstimate?.let { importedFeeNoteSuffix(it) } ?: ""
            }

            addTrade(
                TradeDraftInput(
                    tradeType = finalParsed.tradeType,
                    platform = platform,
                    sourceChannel = finalParsed.sourceChannel,
                    externalReference = externalReference,
                    market = finalParsed.market,
                    symbol = finalParsed.symbol,
                    name = finalParsed.name,
                    tradeDate = finalParsed.tradeDate.toString(),
                    price = finalParsed.price,
                    quantity = finalParsed.quantity,
                    commission = commission,
                    tax = tax,
                    note = buildImportedNote(
                        sourceChannel = finalParsed.sourceChannel,
                        externalReference = externalReference,
                        rawText = finalParsed.rawLine,
                        suffix = noteSuffix,
                    ),
                    tradeTime = finalParsed.tradeTime ?: "00:00",
                    createdAt = finalParsed.createdAt ?: System.currentTimeMillis(),
                    ledgerId = ledgerId,
                    assetType = finalParsed.assetType,
                    underlyingSymbol = finalParsed.underlyingSymbol,
                    expiryDate = finalParsed.expiryDate,
                    strikePrice = finalParsed.strikePrice,
                    optionType = finalParsed.optionType,
                ),
            )
            results.add(
                TradeImportResult(
                    outcome = TradeImportOutcome.IMPORTED,
                    message = "已导入${finalParsed.tradeType.label} ${finalParsed.symbol} ${finalParsed.quantity} 股",
                    externalReference = externalReference,
                )
            )
        }

        if (results.any { it.outcome == TradeImportOutcome.IMPORTED }) {
            refreshQuotesForPortfolio(transactions.first())
        }

        results
    }

    override suspend fun importParsedTrades(
        parsedTrades: List<ParsedStatementTrade>,
        platform: BrokerPlatform,
        ledgerId: Long,
    ): List<TradeImportResult> = withContext(Dispatchers.IO) {
        val refPrefix = if (platform == BrokerPlatform.ZHUORUI) "ZR-STMT-" else "PDF-STMT-"
        val results = mutableListOf<TradeImportResult>()
        val matchedDbTxIds = mutableSetOf<Long>()
        val normalizedTrades = normalizeStatementTrades(parsedTrades)
        val batchSymbolMap = mutableMapOf<Triple<String, Market, String>, String>()
        for (trade in normalizedTrades) {
            val sym = trade.symbol.trim()
            val name = trade.name.trim()
            if (trade.tradeType.isSecurityTrade && sym.isNotBlank() && name.isNotBlank()) {
                batchSymbolMap[Triple(name, trade.market, trade.assetType)] = sym
            }
        }

        for (parsed in normalizedTrades) {
            var symbol = parsed.symbol.trim()
            var name = parsed.name.trim()
            if (parsed.tradeType.isSecurityTrade && symbol.isBlank() && name.isNotBlank()) {
                val batchSymbol = batchSymbolMap[Triple(name, parsed.market, parsed.assetType)]
                if (batchSymbol != null && batchSymbol.isNotBlank()) {
                    symbol = batchSymbol
                    Log.d(TAG, "从导入批次中自动修复空代码: 证券名称=$name, 匹配代码=$symbol")
                } else {
                    val resolvedSymbol = dao.findSymbolByName(name, parsed.market.name, parsed.assetType)
                    if (resolvedSymbol != null && resolvedSymbol.isNotBlank()) {
                        symbol = resolvedSymbol
                        Log.d(TAG, "从历史交易中自动修复空代码: 证券名称=$name, 匹配代码=$symbol")
                    } else {
                        val lookup = runCatching {
                            searchSecurities(name, parsed.market, limit = 1).firstOrNull()
                        }.getOrNull()
                        if (lookup != null && lookup.symbol.isNotBlank()) {
                            symbol = lookup.symbol
                            if (name.isBlank() || name == symbol) {
                                name = lookup.name
                            }
                            Log.d(TAG, "从网络检索中自动修复空代码: 证券名称=$name, 匹配代码=$symbol")
                        }
                    }
                }
            }
            val finalParsed = parsed.copy(symbol = symbol, name = name)
            val externalReference = "${refPrefix}${finalParsed.tradeRef}"
            val existing = dao.findTransactionByExternalReference(
                platform = platform.name,
                externalReference = externalReference,
            )
            if (existing != null) {
                val needUpdate = existing.symbol != finalParsed.symbol ||
                        existing.market != finalParsed.market.name ||
                        existing.name != finalParsed.name ||
                        existing.price != finalParsed.price ||
                        existing.quantity != finalParsed.quantity ||
                        existing.tradeDate != finalParsed.tradeDate.toString() ||
                        existing.tradeTime != (finalParsed.tradeTime ?: "") ||
                        existing.commission != (finalParsed.commission ?: 0.0) ||
                        existing.tax != (finalParsed.tax ?: 0.0) ||
                        existing.assetType != finalParsed.assetType ||
                        existing.underlyingSymbol != finalParsed.underlyingSymbol ||
                        existing.expiryDate != finalParsed.expiryDate ||
                        existing.strikePrice != finalParsed.strikePrice ||
                        existing.optionType != finalParsed.optionType

                if (needUpdate) {
                    val updated = existing.copy(
                        symbol = finalParsed.symbol,
                        market = finalParsed.market.name,
                        name = finalParsed.name,
                        price = finalParsed.price,
                        quantity = finalParsed.quantity,
                        tradeDate = finalParsed.tradeDate.toString(),
                        tradeTime = finalParsed.tradeTime ?: "",
                        commission = finalParsed.commission ?: 0.0,
                        tax = finalParsed.tax ?: 0.0,
                        assetType = finalParsed.assetType,
                        underlyingSymbol = finalParsed.underlyingSymbol,
                        expiryDate = finalParsed.expiryDate,
                        strikePrice = finalParsed.strikePrice,
                        optionType = finalParsed.optionType
                    )
                    dao.updateTransaction(updated)
                    Log.d(TAG, "更新已存在的交易(以修正解析结果): 证券=${finalParsed.symbol}, 日期=${finalParsed.tradeDate}, extRef=$externalReference")
                    results.add(
                        TradeImportResult(
                            outcome = TradeImportOutcome.IMPORTED,
                            message = "交易已更新修复: ${finalParsed.symbol}",
                            externalReference = externalReference,
                        )
                    )
                } else {
                    Log.d(TAG, "发现重复交易(ExternalReference): 证券=${finalParsed.symbol}, 日期=${finalParsed.tradeDate}, extRef=$externalReference")
                    results.add(
                        TradeImportResult(
                            outcome = TradeImportOutcome.DUPLICATE,
                            message = "交易 ${finalParsed.symbol} ${finalParsed.quantity} 股已存在，已跳过",
                            externalReference = externalReference,
                        )
                    )
                }
                matchedDbTxIds.add(existing.id)
                continue
            }

            val duplicates = dao.findDuplicateTransactions(
                platform = platform.name,
                symbol = finalParsed.symbol,
                market = finalParsed.market.name,
                tradeDate = finalParsed.tradeDate.toString(),
                tradeType = finalParsed.tradeType.name,
                quantity = finalParsed.quantity,
                price = finalParsed.price,
            )
            val duplicate = duplicates.firstOrNull { it.id !in matchedDbTxIds }
            if (duplicate != null) {
                Log.d(TAG, "发现重复交易(内容匹配): 证券=${finalParsed.symbol}, 日期=${finalParsed.tradeDate}, 类型=${finalParsed.tradeType}, 数量=${finalParsed.quantity}, 价格=${finalParsed.price}")
                results.add(
                    TradeImportResult(
                        outcome = TradeImportOutcome.DUPLICATE,
                        message = "交易 ${finalParsed.symbol} ${finalParsed.quantity} 股已存在（按内容匹配），已跳过",
                        externalReference = externalReference,
                    )
                )
                matchedDbTxIds.add(duplicate.id)
                continue
            }

            val hasParsedFees = finalParsed.commission != null || finalParsed.tax != null || finalParsed.platformFee != null
            val feeEstimate = if (!hasParsedFees && finalParsed.tradeType.isSecurityTrade) {
                estimateImportedTradeFees(
                    tradeType = finalParsed.tradeType,
                    platform = platform,
                    market = finalParsed.market,
                    price = finalParsed.price,
                    quantity = finalParsed.quantity,
                    tradeDate = finalParsed.tradeDate.toString(),
                    tradeTime = finalParsed.tradeTime ?: "00:00",
                )
            } else null

            val commission = finalParsed.commission ?: feeEstimate?.commission ?: 0.0
            val tax = finalParsed.tax ?: feeEstimate?.tax ?: 0.0
            val noteSuffix = if (hasParsedFees) {
                "费用按结单原始数据导入"
            } else {
                feeEstimate?.let { importedFeeNoteSuffix(it) } ?: ""
            }

            addTrade(
                TradeDraftInput(
                    tradeType = finalParsed.tradeType,
                    platform = platform,
                    sourceChannel = finalParsed.sourceChannel,
                    externalReference = externalReference,
                    market = finalParsed.market,
                    symbol = finalParsed.symbol,
                    name = finalParsed.name,
                    tradeDate = finalParsed.tradeDate.toString(),
                    price = finalParsed.price,
                    quantity = finalParsed.quantity,
                    commission = commission,
                    tax = tax,
                    note = buildImportedNote(
                        sourceChannel = finalParsed.sourceChannel,
                        externalReference = externalReference,
                        rawText = finalParsed.rawLine,
                        suffix = noteSuffix,
                    ),
                    tradeTime = finalParsed.tradeTime ?: "00:00",
                    createdAt = finalParsed.createdAt ?: System.currentTimeMillis(),
                    ledgerId = ledgerId,
                    assetType = finalParsed.assetType,
                    underlyingSymbol = finalParsed.underlyingSymbol,
                    expiryDate = finalParsed.expiryDate,
                    strikePrice = finalParsed.strikePrice,
                    optionType = finalParsed.optionType,
                ),
            )
            results.add(
                TradeImportResult(
                    outcome = TradeImportOutcome.IMPORTED,
                    message = "已导入${finalParsed.tradeType.label} ${finalParsed.symbol} ${finalParsed.quantity} 股",
                    externalReference = externalReference,
                )
            )
        }

        if (results.any { it.outcome == TradeImportOutcome.IMPORTED }) {
            refreshQuotesForPortfolio(transactions.first())
        }

        results
    }

    private fun normalizeStatementTrades(trades: List<ParsedStatementTrade>): List<ParsedStatementTrade> {
        val result = mutableListOf<ParsedStatementTrade>()
        val groups = LinkedHashMap<String, MutableList<ParsedStatementTrade>>()
        for (trade in trades) {
            val defaultTime = when (trade.market) {
                Market.HK, Market.A_SHARE -> "09:30"
                else -> "21:30"
            }
            val baseTime = trade.tradeTime?.ifBlank { null } ?: defaultTime
            val cleanBaseTime = if (baseTime.length >= 5) baseTime.substring(0, 5) else baseTime
            val key = "${trade.tradeDate}_${trade.symbol}_${trade.tradeType.name}_$cleanBaseTime"
            groups.getOrPut(key) { mutableListOf() }.add(trade.copy(tradeTime = cleanBaseTime))
        }
        var currentEpochTime = System.currentTimeMillis() - trades.size * 1000L
        for ((_, groupTrades) in groups) {
            for (idx in groupTrades.indices) {
                val t = groupTrades[idx]
                val baseTime = t.tradeTime ?: "09:30"
                val offsetTime = try {
                    val parts = baseTime.split(":")
                    val hour = parts.getOrNull(0)?.toIntOrNull() ?: 9
                    val min = parts.getOrNull(1)?.toIntOrNull() ?: 30
                    val newMin = min + idx
                    val overflowHours = newMin / 60
                    val finalMin = newMin % 60
                    val finalHour = (hour + overflowHours) % 24
                    String.format("%02d:%02d", finalHour, finalMin)
                } catch (e: Exception) {
                    baseTime
                }
                result.add(
                    t.copy(
                        tradeTime = offsetTime,
                        createdAt = currentEpochTime
                    )
                )
                currentEpochTime += 1000L
            }
        }
        return result
    }

    override suspend fun deleteHolding(symbol: String, market: Market): Int {
        return dao.deleteHolding(symbol = symbol, market = market.name)
    }

    override suspend fun getAllLedgers(): List<LedgerEntity> {
        return dao.getAllLedgers()
    }

    override suspend fun getLedgerById(id: Long): LedgerEntity? {
        return dao.getLedgerById(id)
    }

    override suspend fun insertLedger(ledger: LedgerEntity): Long {
        return dao.insertLedger(ledger)
    }

    override suspend fun updateLedger(ledger: LedgerEntity): Int {
        return dao.updateLedger(ledger)
    }

    override suspend fun deleteLedgerWithTransactions(ledgerId: Long) {
        dao.deleteLedgerWithTransactions(ledgerId)
    }

    override suspend fun moveTransactionsToLedger(ids: List<Long>, targetLedgerId: Long): Int {
        return dao.updateTransactionsLedger(ids, targetLedgerId)
    }

    override suspend fun lookupSecurity(rawInput: String, market: Market): SecurityLookupResult? {
        if (market == Market.CASH) return null
        val raw = rawInput.trim()
        if (raw.isBlank()) return null

        return runCatching {
            searchSecurities(raw, market, limit = 1).firstOrNull()
        }.getOrNull()
    }

    override suspend fun searchSecurities(
        rawInput: String,
        market: Market,
        limit: Int,
    ): List<SecurityLookupResult> {
        val suggestions = quoteDataSource.searchSecurities(rawInput, market, limit)
        val suspicious = suggestions.filter { it.name == it.symbol || it.name.isBlank() }

        if (suspicious.isNotEmpty()) {
            val requests = suspicious.map {
                QuoteRequest(
                    symbol = it.symbol,
                    name = it.name,
                    market = it.market,
                )
            }.distinct()
            val quotes = runCatching { quoteDataSource.refreshQuotes(requests) }.getOrDefault(emptyList())

            if (quotes.isNotEmpty()) {
                return suggestions.map { suggestion ->
                    if (suggestion.name == suggestion.symbol || suggestion.name.isBlank()) {
                        val improved = quotes.find {
                            it.symbol == suggestion.symbol &&
                                Market.fromString(it.market) == suggestion.market
                        }
                        if (improved != null && improved.name.isNotBlank() && improved.name != suggestion.symbol) {
                            return@map suggestion.copy(name = improved.name)
                        }
                    }
                    suggestion
                }
            }
        }
        return suggestions
    }

    override suspend fun refreshQuotes(requests: List<QuoteRequest>): Long {
        val deduped = requests.distinctBy { "${it.market.name}:${it.symbol}" }
        val refreshed = quoteDataSource.refreshQuotes(deduped)
        if (deduped.isNotEmpty() && refreshed.isEmpty()) {
            throw IOException("未能从 ${quoteDataSource.providerLabel} 获取行情")
        }

        // Align quotes with the latest historical close if the market is closed/pre-market
        val historical = _historicalCloses.value.groupBy { it.market to it.symbol }
        val now = Instant.now()
        val alignedRefreshed = refreshed.map { quote ->
            val market = Market.fromString(quote.market) ?: return@map quote
            val history = historical[market to quote.symbol] ?: return@map quote
            val latestPoint = history.maxByOrNull { it.date } ?: return@map quote
            
            if (!MarketTradingSessions.hasOpenedForTrading(market, now)) {
                if (latestPoint.closePrice != quote.currentPrice) {
                    Log.d("LedgerRepository", "Aligning quote for ${quote.symbol} (${quote.market}) to pre-market price ${latestPoint.closePrice} (was ${quote.currentPrice})")
                    return@map quote.copy(currentPrice = latestPoint.closePrice)
                }
            }
            quote
        }

        if (alignedRefreshed.isNotEmpty()) {
            dao.upsertQuotes(alignedRefreshed)
        }
        return dao.latestQuoteRefreshTimestamp() ?: System.currentTimeMillis()
    }

    override suspend fun refreshQuotesForPortfolio(transactions: List<TransactionEntity>, force: Boolean): Long {
        val now = System.currentTimeMillis()
        if (!force && now - lastQuotesRefreshTimeMillis < 60_000L) {
            return dao.latestQuoteRefreshTimestamp() ?: now
        }

        val securityTransactions = transactions
            .filter { transaction ->
                val tradeType = runCatching { TradeType.valueOf(transaction.tradeType) }.getOrNull()
                tradeType != null && tradeType.isSecurityTrade &&
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
                    market = Market.fromString(head.market) ?: Market.CASH,
                    assetType = head.assetType,
                )
            }

        if (requests.isEmpty()) {
            dao.clearQuotes()
            _historicalCloses.value = emptyList()
            return now
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

        val historicalClosesFromDb = dao.getAllHistoricalCloses().map { it.toDomain() }
        val historicalGrouped = historicalClosesFromDb.groupBy { it.market to it.symbol }

        val prefs = context.getSharedPreferences("historical_closes_prefs", Context.MODE_PRIVATE)
        val cacheExpiry = 12 * 60 * 60 * 1000L // 12 hours

        val (toFetch, toUseCache) = requests.partition { request ->
            val lastFetched = prefs.getLong("fetch_${request.market.name}_${request.symbol}", 0L)
            force || (now - lastFetched >= cacheExpiry) || !historicalGrouped.containsKey(request.market to request.symbol)
        }

        val fetchedPoints = if (toFetch.isNotEmpty()) {
            runCatching {
                quoteDataSource.fetchHistoricalCloses(toFetch, lookbackDays)
            }.getOrElse { emptyList() }
        } else {
            emptyList()
        }

        if (fetchedPoints.isNotEmpty()) {
            val fetchedGrouped = fetchedPoints.groupBy { it.market to it.symbol }
            dao.upsertHistoricalCloses(fetchedPoints.map { it.toEntity() })
            
            val edit = prefs.edit()
            fetchedGrouped.keys.forEach { (market, symbol) ->
                edit.putLong("fetch_${market.name}_$symbol", now)
            }
            edit.apply()
        }

        val combinedPoints = buildList {
            toUseCache.forEach { request ->
                addAll(historicalGrouped[request.market to request.symbol].orEmpty())
            }
            val fetchedGrouped = fetchedPoints.groupBy { it.market to it.symbol }
            toFetch.forEach { request ->
                val key = request.market to request.symbol
                val points = fetchedGrouped[key]
                if (!points.isNullOrEmpty()) {
                    addAll(points)
                } else {
                    addAll(historicalGrouped[key].orEmpty())
                }
            }
        }

        _historicalCloses.value = combinedPoints

        // Filter requests: only refresh quotes for active holdings (quantity != 0)
        val activeKeys = securityTransactions.groupBy { it.market to it.symbol }.filter { (_, txns) ->
            val sorted = txns.sortedWith(compareBy({ it.tradeDate }, { it.tradeTime }, { it.id }))
            val finalQty = sorted.fold(0.0) { qty, txn ->
                val tradeType = runCatching { TradeType.valueOf(txn.tradeType) }.getOrNull()
                when (tradeType) {
                    TradeType.BUY -> qty + txn.quantity
                    TradeType.SELL -> qty - txn.quantity
                    TradeType.TRANSFER_IN -> qty + txn.quantity
                    TradeType.TRANSFER_OUT -> qty - txn.quantity
                    TradeType.SPLIT -> qty * txn.price
                    else -> qty
                }
            }
            finalQty != 0.0
        }.keys

        val activeRequests = requests.filter { (it.market.name to it.symbol) in activeKeys }
        if (activeRequests.isNotEmpty()) {
            runCatching {
                refreshQuotes(activeRequests)
            }
        }
        
        lastQuotesRefreshTimeMillis = now
        return dao.latestQuoteRefreshTimestamp() ?: now
    }

    private fun httpGetSimple(url: String, headers: Map<String, String>): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }
        return try {
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (statusCode !in 200..299) {
                throw IOException("HTTP $statusCode for $url: ${body.take(120)}")
            }
            body
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun fetchStockSplits(
        symbol: String,
        period1: Long,
        period2: Long
    ): List<YahooSplitEvent> = withContext(Dispatchers.IO) {
        val list = mutableListOf<YahooSplitEvent>()
        val url = "https://query2.finance.yahoo.com/v8/finance/chart/$symbol?period1=$period1&period2=$period2&interval=1d&events=splits"
        try {
            val responseText = httpGetSimple(
                url = url,
                headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            )
            val json = JSONObject(responseText)
            val chart = json.optJSONObject("chart") ?: return@withContext emptyList()
            val resultArr = chart.optJSONArray("result") ?: return@withContext emptyList()
            if (resultArr.length() == 0) return@withContext emptyList()
            
            val result = resultArr.getJSONObject(0)
            val events = result.optJSONObject("events") ?: return@withContext emptyList()
            val splits = events.optJSONObject("splits") ?: return@withContext emptyList()
            
            val keys = splits.keys()
            while (keys.hasNext()) {
                val timestampKey = keys.next()
                val splitObj = splits.getJSONObject(timestampKey)
                val denominator = splitObj.optDouble("denominator", 1.0)
                val numerator = splitObj.optDouble("numerator", 1.0)
                val ratio = if (denominator != 0.0) numerator / denominator else 1.0
                
                val epochSeconds = timestampKey.toLongOrNull() ?: splitObj.optLong("date", 0L)
                if (epochSeconds > 0L) {
                    val dateStr = java.time.Instant.ofEpochSecond(epochSeconds)
                        .atZone(java.time.ZoneId.of("UTC"))
                        .toLocalDate()
                        .toString()
                    list.add(YahooSplitEvent(dateStr, ratio))
                }
            }
        } catch (e: java.lang.Exception) {
            Log.e("DefaultLedgerRepository", "Failed to fetch splits for $symbol from Yahoo: ${e.message}", e)
        }
        val sortedList = list.sortedBy { it.date }
        if (symbol == "1211.HK") {
            sortedList.filter { it.date != "2025-07-30" }
        } else {
            sortedList
        }
    }

    override suspend fun repairSuspiciousStockNames(): Int {
        val suspicious = dao.getAllTransactions().filter { (it.name.isBlank() || it.name == it.symbol) && it.symbol.isNotBlank() }
        if (suspicious.isEmpty()) return 0

        var fixedCount = 0
        suspicious.groupBy { it.market to it.symbol }.forEach { (key, txns) ->
            val (marketName, symbol) = key
            val market = Market.fromString(marketName) ?: return@forEach
            val resolvedName = resolveConsistentName(symbol, market, symbol)
            if (resolvedName != symbol) {
                txns.forEach { txn ->
                    dao.updateTransaction(txn.copy(name = resolvedName))
                    fixedCount++
                }
            }
        }
        return fixedCount
    }

    override suspend fun refreshExchangeRates(): ExchangeRateRefreshResult {
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

            Market.HK -> {
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
        quantity: Double,
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
        val draftTime = parseTradeTimeOrNull(tradeTime) ?: LocalTime.MAX
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
                val existingTime = parseTradeTimeOrNull(transaction.tradeTime) ?: LocalTime.MAX
                val isBeforeDraft = existingDate.isBefore(draftDate) ||
                    (existingDate == draftDate && existingTime <= draftTime)
                if (!isBeforeDraft) return@mapNotNull null
                val market = Market.fromString(transaction.market)
                    ?: return@mapNotNull null
                amountToHkdEquivalent(transaction.price * transaction.quantity, market)
            }
            .sum()
    }

    private fun amountToHkdEquivalent(amount: Double, market: Market): Double {
        if (market == Market.HK) return amount
        val currentRates = exchangeRates.value
        val amountCny = amount * currentRates.rateToCny(market)
        val hkdRateToCny = currentRates.rateToCny(Market.HK)
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

    override suspend fun syncZhuoruiMailbox(
        config: ZhuoruiEmailSyncConfig,
        lastSyncAtMillis: Long,
        fetchCount: Int,
        earliestReceivedAtMillis: Long?,
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

                val importedRefs = mutableListOf<String>()

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
                    val result = importParsedZhuoruiEmail(parsed, config.targetLedgerId)
                    when (result.outcome) {
                        TradeImportOutcome.IMPORTED -> {
                            Log.d(TAG, "导入成功: ${parsed.tradeType} ${parsed.symbol} x${parsed.quantity} @${parsed.price}")
                            importedCount += 1
                            result.externalReference?.let { importedRefs.add(it) }
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
                    importedExternalReferences = importedRefs,
                )
            } finally {
                runCatching { folder.close(false) }
            }
        } finally {
            runCatching { store.close() }
        }
    }

    override suspend fun repairNamesByExternalReferences(externalReferences: List<String>) {
        if (externalReferences.isEmpty()) return
        Log.d(TAG, "开始对导入的交易记录进行名称统一修复, 记录数=${externalReferences.size}")

        // 1. Fetch transactions matching external references
        val importedTxns = dao.getAllTransactions().filter { it.externalReference in externalReferences }
        if (importedTxns.isEmpty()) return

        // 2. Extract unique (symbol, market)
        val targetHoldings = importedTxns
            .filter { it.symbol.isNotBlank() && it.symbol != "CASH" && it.market != Market.CASH.name }
            .map { it.symbol to it.market }
            .distinct()

        for ((symbol, marketName) in targetHoldings) {
            val market = Market.fromString(marketName) ?: continue

            // 3. Retrieve correct name using lookupSecurity (which uses Sina Suggest API)
            val lookup = lookupSecurity(symbol, market)
            if (lookup != null && lookup.name.isNotBlank() && lookup.name != lookup.symbol) {
                val officialName = lookup.name
                Log.d(TAG, "获取到证券 ${symbol} (${market.name}) 的统一官方名称: $officialName")

                // 4. Update all transactions in the ledger with this symbol/market to use this official name
                val txnsToUpdate = dao.getAllTransactions().filter { it.symbol == symbol && it.market == marketName }
                for (txn in txnsToUpdate) {
                    if (txn.name != officialName) {
                        dao.updateTransaction(txn.copy(name = officialName))
                    }
                }
            }
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

    private fun parseTradeTimeOrNull(value: String): LocalTime? = runCatching {
        LocalTime.parse(value.trim())
    }.getOrNull()

    private fun isOptionSymbol(symbol: String): Boolean {
        val parts = symbol.trim().split(" ")
        if (parts.size != 2) return false
        val optPart = parts[1]
        if (optPart.length < 8) return false
        val datePart = optPart.substring(0, 6)
        if (!datePart.all { it.isDigit() }) return false
        val typeChar = optPart[6]
        if (typeChar != 'C' && typeChar != 'P') return false
        return true
    }

    private fun getExpiryDateForSymbol(symbol: String): LocalDate? {
        val clean = symbol.trim()
        val parts = clean.split(" ")
        if (parts.size != 2) return null
        val optPart = parts[1]
        if (optPart.length < 8) return null
        val datePart = optPart.substring(0, 6)
        if (!datePart.all { it.isDigit() }) return null
        val typeChar = optPart[6]
        if (typeChar != 'C' && typeChar != 'P') return null
        return runCatching {
            val year = "20" + datePart.substring(0, 2)
            val month = datePart.substring(2, 4)
            val day = datePart.substring(4, 6)
            LocalDate.parse("$year-$month-$day")
        }.getOrNull()
    }

    private fun sanitizeOptionTradeDate(
        tradeType: String,
        symbol: String,
        assetType: String?,
        expiryDate: String?,
        tradeDate: String
    ): String {
        if (tradeType == "EXPIRE") return tradeDate
        val isOpt = assetType == "OPTION" || isOptionSymbol(symbol)
        if (!isOpt) return tradeDate

        val expiry = expiryDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: getExpiryDateForSymbol(symbol)
            ?: return tradeDate

        val currentTradeDate = runCatching { LocalDate.parse(tradeDate) }.getOrNull() ?: return tradeDate
        if (currentTradeDate.isAfter(expiry)) {
            return expiry.toString()
        }
        return tradeDate
    }

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

    private var cachedCookie: String? = null
    private var cachedCrumb: String? = null
    private val historicalClosesCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<HistoricalClosePoint>>>()

    private fun isOptionSymbol(symbol: String): Boolean {
        val parts = symbol.trim().split(" ")
        if (parts.size != 2) return false
        val optPart = parts[1]
        if (optPart.length < 8) return false
        val datePart = optPart.substring(0, 6)
        if (!datePart.all { it.isDigit() }) return false
        val typeChar = optPart[6]
        if (typeChar != 'C' && typeChar != 'P') return false
        return true
    }

    private fun toYahooOptionSymbol(symbol: String): String? {
        val parts = symbol.trim().split(" ")
        if (parts.size != 2) return null
        val underlying = parts[0].uppercase()
        val optPart = parts[1]
        if (optPart.length < 8) return null
        val datePart = optPart.substring(0, 6)
        val typeChar = optPart[6]
        val strikeStr = optPart.substring(7)
        val strikeDouble = strikeStr.toDoubleOrNull() ?: return null
        val strikeInt = (strikeDouble * 1000).toLong()
        val strikePadded = String.format("%08d", strikeInt)
        return "$underlying$datePart$typeChar$strikePadded"
    }

    private fun fetchYahooCookie(): String? {
        val url = URL("https://fc.yahoo.com")
        var connection: HttpURLConnection? = null
        try {
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            connection.setRequestProperty("Accept", "*/*")
            
            // Connect and check status code first
            val responseCode = connection.responseCode
            Log.d("YahooOption", "Cookie page response code: $responseCode")
            
            val headerFields = connection.headerFields
            val cookies = headerFields.entries
                .filter { it.key?.equals("Set-Cookie", ignoreCase = true) == true }
                .flatMap { it.value }
                
            for (cookie in cookies) {
                if (cookie.contains("A3=")) {
                    val a3Cookie = cookie.substringBefore(";")
                    Log.d("YahooOption", "Successfully retrieved Yahoo cookie: $a3Cookie")
                    return a3Cookie
                }
            }
        } catch (e: Exception) {
            Log.e("YahooOption", "Failed to fetch Yahoo cookie: ${e.message}", e)
        } finally {
            connection?.disconnect()
        }
        return null
    }

    private fun fetchYahooCrumb(cookie: String): String? {
        val url = URL("https://query2.finance.yahoo.com/v1/test/getcrumb")
        var connection: HttpURLConnection? = null
        try {
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            connection.setRequestProperty("Cookie", cookie)
            connection.setRequestProperty("Accept", "*/*")
            
            val responseCode = connection.responseCode
            Log.d("YahooOption", "Crumb response code: $responseCode")
            if (responseCode in 200..299) {
                val crumb = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }.trim()
                Log.d("YahooOption", "Successfully retrieved Yahoo crumb: $crumb")
                return crumb
            } else {
                val errorMsg = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e("YahooOption", "Crumb failed with code $responseCode: $errorMsg")
            }
        } catch (e: Exception) {
            Log.e("YahooOption", "Failed to fetch Yahoo crumb: ${e.message}", e)
        } finally {
            connection?.disconnect()
        }
        return null
    }

    private fun fetchYahooOptionQuotes(
        yahooSymbols: List<String>,
        cookie: String,
        crumb: String
    ): String? {
        val symbolsStr = yahooSymbols.joinToString(",")
        val url = URL("https://query2.finance.yahoo.com/v7/finance/quote?symbols=$symbolsStr&crumb=$crumb")
        var connection: HttpURLConnection? = null
        try {
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            connection.setRequestProperty("Cookie", cookie)
            connection.setRequestProperty("Accept", "*/*")
            
            val responseCode = connection.responseCode
            Log.d("YahooOption", "Quotes response code: $responseCode")
            if (responseCode in 200..299) {
                return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                val errorBody = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                Log.e("YahooOption", "Yahoo quote request failed with status $responseCode: $errorBody")
            }
        } catch (e: Exception) {
            Log.e("YahooOption", "Failed to fetch Yahoo quotes: ${e.message}", e)
        } finally {
            connection?.disconnect()
        }
        return null
    }

    private fun parseYahooQuotes(
        responseJson: String,
        yahooToOriginal: Map<String, QuoteRequest>
    ): List<QuoteSnapshotEntity> {
        val list = mutableListOf<QuoteSnapshotEntity>()
        try {
            val json = JSONObject(responseJson)
            val resultList = json.optJSONObject("quoteResponse")?.optJSONArray("result")
            if (resultList != null) {
                for (i in 0 until resultList.length()) {
                    val item = resultList.optJSONObject(i) ?: continue
                    val yahooSymbol = item.optString("symbol")
                    val originalReq = yahooToOriginal[yahooSymbol] ?: continue
                    
                    val currentPrice = if (item.has("regularMarketPrice")) {
                        item.optDouble("regularMarketPrice").takeIf { !it.isNaN() }
                    } else null
                    
                    val previousClose = if (item.has("regularMarketPreviousClose")) {
                        item.optDouble("regularMarketPreviousClose").takeIf { !it.isNaN() }
                    } else null
                    
                    val shortName = item.optString("shortName", originalReq.name)
                    
                    list.add(
                        QuoteSnapshotEntity(
                            symbol = originalReq.symbol,
                            market = originalReq.market.name,
                            name = shortName,
                            currentPrice = currentPrice,
                            previousClose = previousClose,
                            lastUpdatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("YahooOption", "Failed to parse Yahoo quotes response: ${e.message}", e)
        }
        return list
    }

    override suspend fun refreshQuotes(requests: List<QuoteRequest>): List<QuoteSnapshotEntity> =
        withContext(Dispatchers.IO) {
            if (requests.isEmpty()) return@withContext emptyList()

            val deduped = requests.distinctBy { requestKey(it.symbol, it.market) }
            Log.d(TAG, "Refreshing ${deduped.size} quote(s) from $providerLabel")

            val (optionRequests, stockRequests) = deduped.partition {
                it.assetType == "OPTION" || isOptionSymbol(it.symbol)
            }

            val stockQuotes = if (stockRequests.isNotEmpty()) {
                val (tencentEligible, sinaOnly) = stockRequests.partition { request ->
                    request.market != Market.US
                }

                val tencentQuotes = runCatching { fetchTencentQuotes(tencentEligible) }
                    .onFailure { Log.e(TAG, "Tencent quote fetch failed", it) }
                    .getOrElse { emptyMap() }

                val missing = tencentEligible.filterNot { request ->
                    tencentQuotes.containsKey(requestKey(request.symbol, request.market))
                }
                val fallbackRequests = missing + sinaOnly

                val sinaQuotes = if (fallbackRequests.isNotEmpty()) {
                    Log.w(TAG, "Tencent missed ${missing.size} quote(s), fallback to Sina for ${fallbackRequests.size} quote(s)")
                    runCatching { fetchSinaQuotes(fallbackRequests) }
                        .onFailure { Log.e(TAG, "Sina quote fetch failed", it) }
                        .getOrElse { emptyMap() }
                } else emptyMap()

                stockRequests.mapNotNull { request ->
                    val key = requestKey(request.symbol, request.market)
                    tencentQuotes[key] ?: sinaQuotes[key]
                }
            } else emptyList()

            val optionQuotes = if (optionRequests.isNotEmpty()) {
                val yahooToOriginal = optionRequests.mapNotNull { req ->
                    val yahooSym = toYahooOptionSymbol(req.symbol) ?: return@mapNotNull null
                    yahooSym to req
                }.toMap()

                val yahooSymbols = yahooToOriginal.keys.toList()
                if (yahooSymbols.isNotEmpty()) {
                    var cookie = cachedCookie
                    var crumb = cachedCrumb
                    if (cookie == null || crumb == null) {
                        val newCookie = fetchYahooCookie()
                        if (newCookie != null) {
                            val newCrumb = fetchYahooCrumb(newCookie)
                            if (newCrumb != null) {
                                cookie = newCookie
                                crumb = newCrumb
                                cachedCookie = newCookie
                                cachedCrumb = newCrumb
                            }
                        }
                    }

                    if (cookie != null && crumb != null) {
                        var response = fetchYahooOptionQuotes(yahooSymbols, cookie, crumb)
                        if (response == null) {
                            // Clear cache and retry once
                            val newCookie = fetchYahooCookie()
                            if (newCookie != null) {
                                val newCrumb = fetchYahooCrumb(newCookie)
                                if (newCrumb != null) {
                                    cookie = newCookie
                                    crumb = newCrumb
                                    cachedCookie = newCookie
                                    cachedCrumb = newCrumb
                                    response = fetchYahooOptionQuotes(yahooSymbols, cookie, crumb)
                                }
                            }
                        }

                        if (response != null) {
                            parseYahooQuotes(response, yahooToOriginal)
                        } else emptyList()
                    } else emptyList()
                } else emptyList()
            } else emptyList()

            val merged = stockQuotes + optionQuotes
            Log.d(
                TAG,
                "Quote refresh completed: stock=${stockQuotes.size}, option=${optionQuotes.size}, merged=${merged.size}",
            )
            merged
        }

    override suspend fun fetchHistoricalCloses(
        requests: List<QuoteRequest>,
        lookbackDays: Int,
    ): List<HistoricalClosePoint> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cacheExpiry = 12 * 60 * 60 * 1000L // 12 hours

        requests.filter { it.assetType != "OPTION" }.flatMap { request ->
            val cacheKey = "${request.market.name}:${request.symbol}:$lookbackDays"
            val cachedVal = historicalClosesCache[cacheKey]
            if (cachedVal != null && now - cachedVal.first < cacheExpiry) {
                cachedVal.second
            } else {
                runCatching {
                    val points = fetchHistoricalClosesForRequest(
                        request = request,
                        lookbackDays = lookbackDays,
                    )
                    historicalClosesCache[cacheKey] = Pair(now, points)
                    points
                }.onFailure {
                    Log.w(TAG, "Historical close fetch failed for ${request.market}:${request.symbol}: ${it.message}")
                }.getOrDefault(emptyList())
            }
        }
    }


    override suspend fun searchSecurities(
        keyword: String,
        market: Market,
        limit: Int,
    ): List<SecurityLookupResult> = withContext(Dispatchers.IO) {
        if (market == Market.CASH) return@withContext emptyList()

        val raw = cleanNameString(keyword)
        if (raw.isBlank()) return@withContext emptyList()

        val cleanUpper = raw.uppercase().replace(" ", "")
        if (market == Market.US) {
            if (cleanUpper.contains("标普500") || cleanUpper.contains("SP500") || cleanUpper.contains("SPY")) {
                if (cleanUpper.contains("ETF") || cleanUpper.contains("SPDR")) {
                    return@withContext listOf(SecurityLookupResult(symbol = "SPY", name = "SPDR标普500 ETF", market = Market.US))
                }
            }
            if (cleanUpper.contains("纳指100") || cleanUpper.contains("纳斯达克100") || cleanUpper.contains("QQQ")) {
                if (cleanUpper.contains("ETF") || cleanUpper.contains("INVESCO") || cleanUpper.contains("纳指") || cleanUpper.contains("纳斯达克")) {
                    return@withContext listOf(SecurityLookupResult(symbol = "QQQ", name = "Invesco NASDAQ 100 ETF", market = Market.US))
                }
            }
        }

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
            Market.HK -> "https://web.ifzq.gtimg.cn/appstock/app/hkfqkline/get"
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
            .associateBy { requestKey(it.symbol, Market.fromString(it.market) ?: Market.CASH) }
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
            .associateBy { requestKey(it.symbol, Market.fromString(it.market) ?: Market.CASH) }
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
            Market.HK -> parseSinaHongKong(request, parts)
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

                    Market.HK -> {
                        if (typeCode != "31") return@mapNotNull null
                        SecurityLookupResult(
                            symbol = cleanHkSymbol(code),
                            name = name,
                            market = Market.HK,
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

        Market.HK -> {
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

        Market.HK -> {
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

        Market.HK -> {
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
                symbol.endsWith(".HK") -> Market.HK
                symbol.all { it.isLetterOrDigit() || it == '.' || it == '-' } &&
                    symbol.any(Char::isLetter) &&
                    !symbol.all(Char::isDigit) -> Market.US
                else -> Market.A_SHARE
            }
            if (currentMarket != market) return@mapNotNull null

            val displaySymbol = when (currentMarket) {
                Market.HK -> symbol
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

private fun cleanNameString(name: String): String {
    var result = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFKC)
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
    // Strip control characters (including \x01, \x02, etc.)
    result = result.replace(Regex("[\\u0000-\\u001F\\u007F-\\u009F]"), "")
    // Strip double quotes
    result = result.replace("\"", "")
    return result.trim()
}

private fun cleanHkSymbol(symbol: String): String {
    val clean = symbol.substringBefore(".").trim()
    val digits = clean.filter { it.isDigit() }
    if (digits.isBlank()) return symbol
    if (digits.length == 5 && digits.startsWith('0')) {
        return "${digits.substring(1)}.HK"
    }
    if (digits.length in 1..4) {
        return "${digits.padStart(4, '0')}.HK"
    }
    return symbol
}
