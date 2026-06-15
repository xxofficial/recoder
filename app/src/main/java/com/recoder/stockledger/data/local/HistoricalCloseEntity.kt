package com.recoder.stockledger.data.local

import androidx.room.Entity
import com.recoder.stockledger.data.Market
import com.recoder.stockledger.data.repository.HistoricalClosePoint
import java.time.LocalDate

@Entity(tableName = "historical_closes", primaryKeys = ["symbol", "market", "date"])
data class HistoricalCloseEntity(
    val symbol: String,
    val market: String,
    val date: String, // YYYY-MM-DD
    val closePrice: Double,
)

fun HistoricalClosePoint.toEntity() = HistoricalCloseEntity(
    symbol = symbol,
    market = market.name,
    date = date.toString(),
    closePrice = closePrice,
)

fun HistoricalCloseEntity.toDomain() = HistoricalClosePoint(
    symbol = symbol,
    market = Market.fromString(market) ?: Market.CASH,
    date = LocalDate.parse(date),
    closePrice = closePrice,
)
