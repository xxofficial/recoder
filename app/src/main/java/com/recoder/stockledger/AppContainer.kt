package com.recoder.stockledger

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.recoder.stockledger.data.importer.ImportCoordinator
import com.recoder.stockledger.data.local.StockLedgerDatabase
import com.recoder.stockledger.data.repository.AutoNameRepairingStockLedgerRepository
import com.recoder.stockledger.data.repository.DefaultLedgerRepository
import com.recoder.stockledger.data.repository.FrankfurterExchangeRateDataSource
import com.recoder.stockledger.data.repository.ImportRepository
import com.recoder.stockledger.data.repository.LedgerRepository
import com.recoder.stockledger.data.repository.MarketDataRepository
import com.recoder.stockledger.data.repository.QuoteDataSource
import com.recoder.stockledger.data.repository.StockLedgerRepository
import com.recoder.stockledger.data.repository.TencentSinaQuoteDataSource
import com.recoder.stockledger.data.settings.SharedPreferencesStockLedgerSettingsStore
import com.recoder.stockledger.data.settings.StockLedgerSettingsStore
import com.recoder.stockledger.platform.BrokerPlatformRegistry
import com.recoder.stockledger.ui.LedgerViewModel

class AppContainer(
    private val application: Application,
) {
    private val applicationContext: Context = application.applicationContext

    val settingsStore: StockLedgerSettingsStore by lazy {
        SharedPreferencesStockLedgerSettingsStore(applicationContext)
    }

    val database: StockLedgerDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            StockLedgerDatabase::class.java,
            "stock-ledger.db",
        ).addMigrations(
            StockLedgerDatabase.MIGRATION_1_2,
            StockLedgerDatabase.MIGRATION_2_3,
            StockLedgerDatabase.MIGRATION_3_4,
            StockLedgerDatabase.MIGRATION_4_5,
            StockLedgerDatabase.MIGRATION_5_6,
            StockLedgerDatabase.MIGRATION_6_7,
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    val quoteDataSource: QuoteDataSource by lazy {
        TencentSinaQuoteDataSource()
    }

    private val exchangeRateDataSource: FrankfurterExchangeRateDataSource by lazy {
        FrankfurterExchangeRateDataSource(applicationContext)
    }

    val repository: StockLedgerRepository by lazy {
        AutoNameRepairingStockLedgerRepository(
            DefaultLedgerRepository(
                context = applicationContext,
                dao = database.ledgerDao(),
                quoteDataSource = quoteDataSource,
                exchangeRateDataSource = exchangeRateDataSource,
                platformFeePlanSelectionProvider = settingsStore::loadPlatformFeePlanSelections,
            )
        )
    }

    val ledgerRepository: LedgerRepository
        get() = repository

    val marketDataRepository: MarketDataRepository
        get() = repository

    val importRepository: ImportRepository
        get() = repository

    val importCoordinator: ImportCoordinator by lazy {
        ImportCoordinator(
            ledgerRepository = ledgerRepository,
            marketDataRepository = marketDataRepository,
            importRepository = importRepository,
        )
    }

    val platformRegistry: BrokerPlatformRegistry by lazy {
        BrokerPlatformRegistry()
    }

    val ledgerViewModelFactory: LedgerViewModel.Factory by lazy {
        LedgerViewModel.Factory(
            application = application,
            repository = repository,
            settingsStore = settingsStore,
        )
    }
}
