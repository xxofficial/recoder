package com.recoder.stockledger.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [
        androidx.room.Index(value = ["symbol", "market"]),
        androidx.room.Index(value = ["ledgerId"]),
    ],
)
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
    val quantity: Double,
    val commission: Double,
    val tax: Double,
    val note: String,
    val createdAt: Long,
    val ledgerId: Long = 1L,
    val investorName: String? = null,
    val assetType: String = "STOCK",
    val underlyingSymbol: String? = null,
    val expiryDate: String? = null,
    val strikePrice: Double? = null,
    val optionType: String? = null,
    val fxFromCurrency: String? = null,
    val fxFromAmount: Double? = null,
    val fxToCurrency: String? = null,
    val fxToAmount: Double? = null,
    val fxRate: Double? = null,
)

