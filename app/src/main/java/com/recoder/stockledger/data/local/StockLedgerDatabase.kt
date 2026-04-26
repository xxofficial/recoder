package com.recoder.stockledger.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TransactionEntity::class, QuoteSnapshotEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class StockLedgerDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    ALTER TABLE transactions
                    ADD COLUMN platform TEXT NOT NULL DEFAULT 'UNSPECIFIED'
                    """.trimIndent(),
                )
            }
        }
    }
}
