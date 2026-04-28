package com.recoder.stockledger

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.recoder.stockledger.data.BrokerPlatform
import com.recoder.stockledger.data.repository.DefaultLedgerRepository
import com.recoder.stockledger.data.repository.FrankfurterExchangeRateDataSource
import com.recoder.stockledger.data.repository.TencentSinaQuoteDataSource
import com.recoder.stockledger.data.local.StockLedgerDatabase

class StockLedgerApplication : Application() {
    val database: StockLedgerDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            StockLedgerDatabase::class.java,
            "stock-ledger.db",
        ).addMigrations(
            StockLedgerDatabase.MIGRATION_1_2,
            StockLedgerDatabase.MIGRATION_2_3,
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    val repository: DefaultLedgerRepository by lazy {
        DefaultLedgerRepository(
            dao = database.ledgerDao(),
            quoteDataSource = TencentSinaQuoteDataSource(),
            exchangeRateDataSource = FrankfurterExchangeRateDataSource(applicationContext),
            platformFeePlanSelectionProvider = ::loadPlatformFeePlanSelections,
        )
    }

    private fun loadPlatformFeePlanSelections(): Map<BrokerPlatform, String> {
        val preferences = applicationContext.getSharedPreferences(
            StockLedgerPreferences.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        val serialized = preferences.getString(
            StockLedgerPreferences.KEY_PLATFORM_FEE_PLAN_SELECTIONS,
            null,
        ).orEmpty()
        if (serialized.isBlank()) return emptyMap()
        return serialized.split("|")
            .mapNotNull { entry ->
                val separatorIndex = entry.indexOf('=')
                if (separatorIndex <= 0 || separatorIndex >= entry.lastIndex) {
                    return@mapNotNull null
                }
                val platform = BrokerPlatform.entries.firstOrNull { it.name == entry.substring(0, separatorIndex) }
                    ?: return@mapNotNull null
                val planId = entry.substring(separatorIndex + 1)
                platform to planId
            }
            .toMap()
    }
}
