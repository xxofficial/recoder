package com.recoder.stockledger.data.local

import androidx.room.Entity

@Entity(tableName = "quote_snapshots", primaryKeys = ["symbol", "market"])
data class QuoteSnapshotEntity(
    val symbol: String,
    val market: String,
    val name: String,
    val currentPrice: Double?,
    val previousClose: Double?,
    val lastUpdatedAt: Long,
)

