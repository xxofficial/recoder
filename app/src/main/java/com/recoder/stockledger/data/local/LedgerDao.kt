package com.recoder.stockledger.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
@JvmSuppressWildcards
interface LedgerDao {
    @Query("SELECT * FROM transactions ORDER BY tradeDate DESC, tradeTime DESC, id DESC")
    fun observeTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions")
    suspend fun getAllTransactions(): List<TransactionEntity>

    @Query("SELECT * FROM quote_snapshots")
    fun observeQuotes(): Flow<List<QuoteSnapshotEntity>>

    @Insert
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert
    suspend fun insertTransactions(transactions: List<TransactionEntity>): List<Long>

    @Query(
        """
        SELECT * FROM transactions
        WHERE platform = :platform AND externalReference = :externalReference
        ORDER BY id DESC
        LIMIT 1
        """,
    )
    suspend fun findTransactionByExternalReference(
        platform: String,
        externalReference: String,
    ): TransactionEntity?

    @Query("DELETE FROM transactions")
    suspend fun clearTransactions(): Int

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity): Int

    @Upsert
    suspend fun upsertQuotes(quotes: List<QuoteSnapshotEntity>): List<Long>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun transactionCount(): Int

    @Query("DELETE FROM transactions WHERE createdAt BETWEEN 1 AND 5")
    suspend fun deleteLegacySeedTransactions(): Int

    @Query("DELETE FROM quote_snapshots")
    suspend fun clearQuotes(): Int

    @Query("DELETE FROM transactions WHERE symbol = :symbol AND market = :market")
    suspend fun deleteTransactionsByHolding(symbol: String, market: String): Int

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long): Int

    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteTransactionsByIds(ids: List<Long>): Int

    @Query("DELETE FROM quote_snapshots WHERE symbol = :symbol AND market = :market")
    suspend fun deleteQuoteByHolding(symbol: String, market: String): Int

    @Transaction
    suspend fun deleteHolding(symbol: String, market: String): Int {
        val deletedCount = deleteTransactionsByHolding(symbol, market)
        deleteQuoteByHolding(symbol, market)
        return deletedCount
    }

    @Transaction
    suspend fun replaceTransactions(transactions: List<TransactionEntity>): List<Long> {
        clearTransactions()
        clearQuotes()
        return insertTransactions(transactions)
    }

    @Query("SELECT MAX(lastUpdatedAt) FROM quote_snapshots")
    suspend fun latestQuoteRefreshTimestamp(): Long?

    @Query(
        """
        SELECT name FROM transactions
        WHERE symbol = :symbol AND market = :market AND name != ''
        ORDER BY tradeDate DESC, tradeTime DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun findStockNameFromTransactions(symbol: String, market: String): String?

    @Query(
        """
        SELECT name FROM quote_snapshots
        WHERE symbol = :symbol AND market = :market AND name != ''
        LIMIT 1
        """,
    )
    suspend fun findStockNameFromQuotes(symbol: String, market: String): String?

    @Query(
        """
        SELECT * FROM transactions
        WHERE platform = :platform
          AND symbol = :symbol
          AND market = :market
          AND tradeDate = :tradeDate
          AND tradeType = :tradeType
          AND quantity = :quantity
          AND ABS(price - :price) < 0.0001
        ORDER BY id DESC
        LIMIT 1
        """,
    )
    suspend fun findDuplicateTransaction(
        platform: String,
        symbol: String,
        market: String,
        tradeDate: String,
        tradeType: String,
        quantity: Int,
        price: Double,
    ): TransactionEntity?
}
