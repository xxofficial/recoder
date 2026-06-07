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
            quantity = 10,
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
}
