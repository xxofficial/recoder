package com.recoder.stockledger.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TransactionEntity::class, QuoteSnapshotEntity::class, LedgerEntity::class, HistoricalCloseEntity::class],
    version = 7,
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

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    ALTER TABLE transactions
                    ADD COLUMN sourceChannel TEXT
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    ALTER TABLE transactions
                    ADD COLUMN externalReference TEXT
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. 创建 ledgers 表
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ledgers` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `type` TEXT NOT NULL, 
                        `description` TEXT NOT NULL, 
                        `partners` TEXT NOT NULL, 
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                
                // 2. 插入初始默认账本
                database.execSQL(
                    """
                    INSERT OR IGNORE INTO `ledgers` (`id`, `name`, `type`, `description`, `partners`, `createdAt`)
                    VALUES (1, '默认个人账本', 'PERSONAL', '系统自动创建的默认个人账本', '我', 1700000000000)
                    """.trimIndent()
                )

                // 3. 升级 transactions 表，增加 ledgerId 和 investorName 字段
                database.execSQL(
                    """
                    ALTER TABLE `transactions` ADD COLUMN `ledgerId` INTEGER NOT NULL DEFAULT 1
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    ALTER TABLE `transactions` ADD COLUMN `investorName` TEXT
                    """.trimIndent()
                )

                // 4. 创建索引
                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_transactions_ledgerId` ON `transactions` (`ledgerId`)
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_transactions_symbol_market` ON `transactions` (`symbol`, `market`)
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    ALTER TABLE transactions ADD COLUMN assetType TEXT NOT NULL DEFAULT 'STOCK'
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    ALTER TABLE transactions ADD COLUMN underlyingSymbol TEXT
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    ALTER TABLE transactions ADD COLUMN expiryDate TEXT
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    ALTER TABLE transactions ADD COLUMN strikePrice REAL
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    ALTER TABLE transactions ADD COLUMN optionType TEXT
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `transactions_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `tradeType` TEXT NOT NULL, 
                        `platform` TEXT NOT NULL, 
                        `sourceChannel` TEXT, 
                        `externalReference` TEXT, 
                        `market` TEXT NOT NULL, 
                        `symbol` TEXT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `tradeDate` TEXT NOT NULL, 
                        `tradeTime` TEXT NOT NULL, 
                        `price` REAL NOT NULL, 
                        `quantity` REAL NOT NULL, 
                        `commission` REAL NOT NULL, 
                        `tax` REAL NOT NULL, 
                        `note` TEXT NOT NULL, 
                        `createdAt` INTEGER NOT NULL, 
                        `ledgerId` INTEGER NOT NULL, 
                        `investorName` TEXT, 
                        `assetType` TEXT NOT NULL, 
                        `underlyingSymbol` TEXT, 
                        `expiryDate` TEXT, 
                        `strikePrice` REAL, 
                        `optionType` TEXT
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    INSERT INTO `transactions_new` (
                        `id`, `tradeType`, `platform`, `sourceChannel`, `externalReference`, 
                        `market`, `symbol`, `name`, `tradeDate`, `tradeTime`, `price`, 
                        `quantity`, `commission`, `tax`, `note`, `createdAt`, `ledgerId`, 
                        `investorName`, `assetType`, `underlyingSymbol`, `expiryDate`, 
                        `strikePrice`, `optionType`
                    )
                    SELECT 
                        `id`, `tradeType`, `platform`, `sourceChannel`, `externalReference`, 
                        `market`, `symbol`, `name`, `tradeDate`, `tradeTime`, `price`, 
                        CAST(`quantity` AS REAL), `commission`, `tax`, `note`, `createdAt`, `ledgerId`, 
                        `investorName`, `assetType`, `underlyingSymbol`, `expiryDate`, 
                        `strikePrice`, `optionType`
                    FROM `transactions`
                    """.trimIndent()
                )
                database.execSQL("DROP TABLE `transactions`")
                database.execSQL("ALTER TABLE `transactions_new` RENAME TO `transactions`")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_symbol_market` ON `transactions` (`symbol`, `market`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_ledgerId` ON `transactions` (`ledgerId`)")
            }
        }

        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `historical_closes` (
                        `symbol` TEXT NOT NULL,
                        `market` TEXT NOT NULL,
                        `date` TEXT NOT NULL,
                        `closePrice` REAL NOT NULL,
                        PRIMARY KEY(`symbol`, `market`, `date`)
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
