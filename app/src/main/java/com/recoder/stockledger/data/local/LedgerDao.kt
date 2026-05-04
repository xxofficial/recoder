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
interface LedgerDao {
    @Query("SELECT * FROM transactions ORDER BY tradeDate DESC, tradeTime DESC, id DESC")
    fun observeTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM quote_snapshots")
    fun observeQuotes(): Flow<List<QuoteSnapshotEntity>>

    @Insert
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert
    suspend fun insertTransactions(transactions: List<TransactionEntity>)

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
    suspend fun clearTransactions()

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Upsert
    suspend fun upsertQuotes(quotes: List<QuoteSnapshotEntity>)

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun transactionCount(): Int

    @Query("DELETE FROM transactions WHERE createdAt BETWEEN 1 AND 5")
    suspend fun deleteLegacySeedTransactions()

    @Query("DELETE FROM quote_snapshots")
    suspend fun clearQuotes()

    @Query("DELETE FROM transactions WHERE symbol = :symbol AND market = :market")
    suspend fun deleteTransactionsByHolding(symbol: String, market: String): Int

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long): Int

    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteTransactionsByIds(ids: List<Long>): Int

    @Query("DELETE FROM quote_snapshots WHERE symbol = :symbol AND market = :market")
    suspend fun deleteQuoteByHolding(symbol: String, market: String)

    @Transaction
    suspend fun deleteHolding(symbol: String, market: String): Int {
        val deletedCount = deleteTransactionsByHolding(symbol, market)
        deleteQuoteByHolding(symbol, market)
        return deletedCount
    }

    @Transaction
    suspend fun replaceTransactions(transactions: List<TransactionEntity>) {
        clearTransactions()
        clearQuotes()
        insertTransactions(transactions)
    }

    @Query("SELECT MAX(lastUpdatedAt) FROM quote_snapshots")
    suspend fun latestQuoteRefreshTimestamp(): Long?

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
    ): TransactionEntity?
}
