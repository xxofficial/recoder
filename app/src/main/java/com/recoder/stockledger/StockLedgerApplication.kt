package com.recoder.stockledger

import android.app.Application
import androidx.room.Room
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
        ).fallbackToDestructiveMigration().build()
    }

    val repository: DefaultLedgerRepository by lazy {
        DefaultLedgerRepository(
            dao = database.ledgerDao(),
            quoteDataSource = TencentSinaQuoteDataSource(),
            exchangeRateDataSource = FrankfurterExchangeRateDataSource(applicationContext),
        )
    }
}
