package com.recoder.stockledger.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TransactionEntity::class, QuoteSnapshotEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class StockLedgerDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao
}
