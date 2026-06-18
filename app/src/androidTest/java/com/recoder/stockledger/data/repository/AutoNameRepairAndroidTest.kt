package com.recoder.stockledger.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.recoder.stockledger.data.BrokerPlatform
import com.recoder.stockledger.data.Market
import com.recoder.stockledger.data.TradeType
import com.recoder.stockledger.data.local.LedgerEntity
import com.recoder.stockledger.data.local.StockLedgerDatabase
import com.recoder.stockledger.data.ImportSourceChannel
import com.recoder.stockledger.data.importer.ParsedStatementTrade
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class AutoNameRepairAndroidTest {

    private lateinit var db: StockLedgerDatabase
    private lateinit var repository: AutoNameRepairingStockLedgerRepository
    private lateinit var baseRepo: DefaultLedgerRepository

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, StockLedgerDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        baseRepo = DefaultLedgerRepository(
            context = context,
            dao = db.ledgerDao(),
            quoteDataSource = FakeQuoteDataSource(),
            exchangeRateDataSource = FrankfurterExchangeRateDataSource(context),
            platformFeePlanSelectionProvider = { emptyMap() }
        )

        repository = AutoNameRepairingStockLedgerRepository(baseRepo)

        // Seed a default ledger because transaction references require a valid ledger
        runBlocking {
            db.ledgerDao().insertLedger(
                LedgerEntity(
                    id = 1L,
                    name = "Test Ledger",
                    type = "PERSONAL",
                    description = "Test Description",
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun testAutoNameRepairOnImport() = runBlocking {
        // 1. Prepare a mock parsed trade with symbol "AAPL" and an inconsistent name "Apple Inc."
        // We expect the Sina suggest/lookup (which in FakeQuoteDataSource resolves to "苹果") to replace it.
        val parsedTrade = ParsedStatementTrade(
            sourceChannel = ImportSourceChannel.PDF_STATEMENT,
            tradeType = TradeType.BUY,
            market = Market.US,
            symbol = "AAPL",
            name = "Apple Inc.", // Inconsistent name
            currencyCode = "USD",
            price = 150.0,
            quantity = 10.0,
            amount = 1500.0,
            tradeDate = LocalDate.of(2026, 6, 1),
            tradeTime = "10:00",
            commission = 1.0,
            tax = 0.0,
            platformFee = 0.0,
            tradeRef = "TEST-REF-001",
            rawLine = "AAPL BUY 10 @ 150"
        )

        // 2. Perform the import using our decorated repository
        val results = repository.importParsedTrades(
            parsedTrades = listOf(parsedTrade),
            platform = BrokerPlatform.ZHUORUI,
            ledgerId = 1L
        )

        assertEquals(1, results.size)
        assertEquals(TradeImportOutcome.IMPORTED, results[0].outcome)

        // 3. Query the database directly to verify if name repair was triggered and the name has been unified to "苹果"
        val txs = db.ledgerDao().getAllTransactions()
        assertEquals(1, txs.size)
        assertEquals("AAPL", txs[0].symbol)
        assertEquals("苹果", txs[0].name) // Verified: corrected from "Apple Inc." to "苹果"!
    }

    @Test
    fun testBatchSymbolUnification() = runBlocking {
        // 1. Prepare two mock parsed trades:
        // - Trade 1 has empty symbol but name "蜜雪集团" (representing pre-listing/IPO statement entry)
        // - Trade 2 has symbol "02097.HK" and name "蜜雪集团" (representing post-listing statement entry)
        val trade1 = ParsedStatementTrade(
            sourceChannel = ImportSourceChannel.PDF_STATEMENT,
            tradeType = TradeType.BUY,
            market = Market.HK,
            symbol = "",
            name = "蜜雪集团",
            currencyCode = "HKD",
            price = 24.0,
            quantity = 20.0,
            amount = 480.0,
            tradeDate = LocalDate.of(2025, 6, 1),
            tradeTime = "09:30",
            commission = 1.0,
            tax = 0.0,
            platformFee = 0.0,
            tradeRef = "TEST-REF-IPO-1",
            rawLine = "BUY 20 @ 24"
        )

        val trade2 = ParsedStatementTrade(
            sourceChannel = ImportSourceChannel.PDF_STATEMENT,
            tradeType = TradeType.BUY,
            market = Market.HK,
            symbol = "02097.HK",
            name = "蜜雪集团",
            currencyCode = "HKD",
            price = 24.0,
            quantity = 20.0,
            amount = 480.0,
            tradeDate = LocalDate.of(2025, 7, 1),
            tradeTime = "09:30",
            commission = 1.0,
            tax = 0.0,
            platformFee = 0.0,
            tradeRef = "TEST-REF-IPO-2",
            rawLine = "BUY 20 @ 24"
        )

        // 2. Perform the import in a batch
        val results = baseRepo.importParsedTrades(
            parsedTrades = listOf(trade1, trade2),
            platform = BrokerPlatform.LONGBRIDGE,
            ledgerId = 1L
        )

        assertEquals(2, results.size)
        assertEquals(TradeImportOutcome.IMPORTED, results[0].outcome)
        assertEquals(TradeImportOutcome.IMPORTED, results[1].outcome)

        // 3. Query the database to verify if BOTH trades now have the unified symbol "02097.HK"
        val txs = db.ledgerDao().getAllTransactions()
        assertEquals(2, txs.size)
        assertEquals("02097.HK", txs[0].symbol)
        assertEquals("02097.HK", txs[1].symbol)
    }

    @Test
    fun testStockSymbolNotMatchedToOption() = runBlocking {
        // Seed an option transaction in the database first
        val optionTx = com.recoder.stockledger.data.local.TransactionEntity(
            tradeType = "BUY",
            platform = "LONGBRIDGE",
            market = "US",
            symbol = "TSLA 260601C500",
            name = "特斯拉",
            tradeDate = "2026-05-18",
            tradeTime = "23:09",
            price = 0.68,
            quantity = 1.0,
            commission = 0.0,
            tax = 0.0,
            note = "",
            createdAt = System.currentTimeMillis(),
            ledgerId = 1L,
            assetType = "OPTION"
        )
        db.ledgerDao().insertTransaction(optionTx)

        // Now import a stock transaction with empty symbol but name "特斯拉"
        val stockTrade = ParsedStatementTrade(
            sourceChannel = ImportSourceChannel.PDF_STATEMENT,
            tradeType = TradeType.BUY,
            market = Market.US,
            symbol = "",
            name = "特斯拉",
            currencyCode = "USD",
            price = 329.0,
            quantity = 0.3,
            amount = 99.0,
            tradeDate = LocalDate.of(2025, 6, 11),
            tradeTime = "22:27",
            commission = 0.0,
            tax = 0.0,
            platformFee = 0.0,
            tradeRef = "OS20250612173762",
            rawLine = "TSLA STOCK BUY",
            assetType = "STOCK"
        )

        val results = baseRepo.importParsedTrades(
            parsedTrades = listOf(stockTrade),
            platform = BrokerPlatform.LONGBRIDGE,
            ledgerId = 1L
        )

        assertEquals(TradeImportOutcome.IMPORTED, results[0].outcome)

        val allTx = db.ledgerDao().getAllTransactions()
        val importedStockTx = allTx.find { it.externalReference == "PDF-STMT-OS20250612173762" }
        assertNotNull(importedStockTx)
        // Verify it was NOT assigned the option symbol!
        assertNotEquals("TSLA 260601C500", importedStockTx!!.symbol)
    }

    @Test
    fun testSeedIfEmptyRepairsOptionSymbolInStock() = runBlocking {
        // Seed a corrupted transaction: assetType = STOCK but symbol = TSLA 260601C500
        val corruptedTx = com.recoder.stockledger.data.local.TransactionEntity(
            tradeType = "BUY",
            platform = "LONGBRIDGE",
            market = "US",
            symbol = "TSLA 260601C500",
            name = "特斯拉",
            tradeDate = "2025-06-11",
            tradeTime = "22:27",
            price = 329.0,
            quantity = 0.3,
            commission = 0.0,
            tax = 0.0,
            note = "",
            createdAt = System.currentTimeMillis(),
            ledgerId = 1L,
            assetType = "STOCK"
        )
        val id = db.ledgerDao().insertTransaction(corruptedTx)

        // Call seedIfEmpty via baseRepo
        baseRepo.seedIfEmpty()

        // Verify it was repaired to TSLA
        val repaired = db.ledgerDao().getAllTransactions().find { it.id == id }
        assertNotNull(repaired)
        assertEquals("TSLA", repaired!!.symbol)
    }

    @Test
    fun testSeedIfEmptyKeepsLongBridgeSpxsAsSpxs() = runBlocking {
        val spxsTx = com.recoder.stockledger.data.local.TransactionEntity(
            tradeType = "BUY",
            platform = "LONGBRIDGE",
            sourceChannel = "PDF_STATEMENT",
            externalReference = "长桥-STMT-OS-SPXS-1",
            market = "US",
            symbol = "SPXS",
            name = "3 倍做空标普 500 ETF",
            tradeDate = "2026-03-03",
            tradeTime = "22:30",
            price = 10.0,
            quantity = 1.0,
            commission = 0.0,
            tax = 0.0,
            note = "电子结单自动导入 · SPXS | 3 倍做空标普 500 ETF",
            createdAt = System.currentTimeMillis(),
            ledgerId = 1L,
            assetType = "STOCK"
        )
        val id = db.ledgerDao().insertTransaction(spxsTx)

        baseRepo.seedIfEmpty()

        val repaired = db.ledgerDao().getAllTransactions().find { it.id == id }
        assertNotNull(repaired)
        assertEquals("SPXS", repaired!!.symbol)
        assertEquals("3 倍做空标普 500 ETF", repaired.name)
    }

    @Test
    fun testSeedIfEmptyRestoresPollutedLongBridgeSpxsRecords() = runBlocking {
        val polluted = listOf(
            "BUY" to "SPXS | 3 倍做空标普 500 ETF",
            "DIVIDEND" to "SPXS.US Cash Dividend: 0.38267 USD per Share",
            "TAX" to "SPXS.US Cash Dividend: 0.38267 USD per Share Withholding Tax/Dividend Fee"
        ).mapIndexed { index, (type, notePreview) ->
            com.recoder.stockledger.data.local.TransactionEntity(
                tradeType = type,
                platform = "LONGBRIDGE",
                sourceChannel = "PDF_STATEMENT",
                externalReference = "长桥-STMT-SPY-POLLUTED-$index",
                market = "US",
                symbol = "SPY",
                name = "SPDR标普500 ETF",
                tradeDate = "2026-04-01",
                tradeTime = "09:00",
                price = 1.0,
                quantity = 1.0,
                commission = 0.0,
                tax = 0.0,
                note = "电子结单自动导入 · $notePreview",
                createdAt = System.currentTimeMillis() + index,
                ledgerId = 1L,
                assetType = "STOCK"
            )
        }
        polluted.forEach { db.ledgerDao().insertTransaction(it) }

        baseRepo.seedIfEmpty()

        val all = db.ledgerDao().getAllTransactions()
        assertEquals(3, all.size)
        all.forEach { tx ->
            assertEquals("SPXS", tx.symbol)
            assertEquals("3 倍做空标普 500 ETF", tx.name)
        }
    }

    @Test
    fun testSeedIfEmptyStillNormalizesExplicitSpy() = runBlocking {
        val spyTx = com.recoder.stockledger.data.local.TransactionEntity(
            tradeType = "BUY",
            platform = "LONGBRIDGE",
            market = "US",
            symbol = ".INX",
            name = "SPDR 标普500 ETF",
            tradeDate = "2026-03-03",
            tradeTime = "22:30",
            price = 10.0,
            quantity = 1.0,
            commission = 0.0,
            tax = 0.0,
            note = "",
            createdAt = System.currentTimeMillis(),
            ledgerId = 1L,
            assetType = "STOCK"
        )
        val id = db.ledgerDao().insertTransaction(spyTx)

        baseRepo.seedIfEmpty()

        val repaired = db.ledgerDao().getAllTransactions().find { it.id == id }
        assertNotNull(repaired)
        assertEquals("SPY", repaired!!.symbol)
        assertEquals("SPDR标普500 ETF", repaired.name)
    }

    @Test
    fun testSearchDoesNotMapLeveragedSpxsNameToSpy() = runBlocking {
        val suggestions = baseRepo.searchSecurities("3 倍做空标普 500 ETF", Market.US)

        assertTrue(suggestions.none { it.symbol == "SPY" })
    }

    @Test
    fun testSearchStillMapsExplicitSpyName() = runBlocking {
        val suggestions = baseRepo.searchSecurities("SPDR 标普500 ETF", Market.US)

        assertTrue(suggestions.any { it.symbol == "SPY" && it.name == "SPDR标普500 ETF" })
    }
}

