package com.recoder.stockledger.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tradeType: String,
    val platform: String,
    val sourceChannel: String? = null,
    val externalReference: String? = null,
    val market: String,
    val symbol: String,
    val name: String,
    val tradeDate: String,
    val tradeTime: String,
    val price: Double,
    val quantity: Int,
    val commission: Double,
    val tax: Double,
    val note: String,
    val createdAt: Long,
)

