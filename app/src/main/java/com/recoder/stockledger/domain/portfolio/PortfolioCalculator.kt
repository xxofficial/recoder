package com.recoder.stockledger.domain.portfolio

import com.recoder.stockledger.data.ExchangeRates
import com.recoder.stockledger.data.Market
import com.recoder.stockledger.data.TradeType
import com.recoder.stockledger.data.rateToCny
import java.time.LocalDate

data class PortfolioTrade(
    val tradeType: TradeType,
    val market: Market,
    val symbol: String,
    val name: String,
    val tradeDate: String,
    val tradeTime: String,
    val price: Double,
    val quantity: Double,
    val commission: Double,
    val tax: Double,
    val createdAt: Long,
    val assetType: String = "",
)

data class PortfolioQuote(
    val symbol: String,
    val market: Market,
    val currentPrice: Double?,
    val previousClose: Double?,
)

data class PortfolioPosition(
    val symbol: String,
    val name: String,
    val market: Market,
    val quantity: Double,
    val averageCost: Double,
    val remainingCost: Double,
    val realizedProfit: Double,
    val assetType: String = "",
)

data class PortfolioSnapshot(
    val positions: Map<String, PortfolioPosition>,
    val totalAssetsCny: Double,
    val holdingsValueCny: Double,
    val cashBalanceCny: Double,
    val totalDepositCny: Double,
    val totalWithdrawCny: Double,
    val netInflowCny: Double,
    val unrealizedProfitCny: Double,
    val unrealizedProfitPercent: Double,
    val dayProfitCny: Double,
    val dayProfitPercent: Double,
    val totalCommissionCny: Double,
    val totalTaxCny: Double,
    val securityTradeCount: Int,
    val buyTradeCount: Int,
    val sellTradeCount: Int,
)

class PortfolioCalculator {
    fun calculate(
        transactions: List<PortfolioTrade>,
        quotes: List<PortfolioQuote>,
        exchangeRates: ExchangeRates,
    ): PortfolioSnapshot {
        val positions = linkedMapOf<String, PortfolioPosition>()
        var cashBalanceCny = 0.0
        var totalDepositCny = 0.0
        var totalWithdrawCny = 0.0
        var totalCommissionCny = 0.0
        var totalTaxCny = 0.0
        var securityTradeCount = 0
        var buyTradeCount = 0
        var sellTradeCount = 0

        transactions
            .sortedWith(compareBy<PortfolioTrade>({ effectiveTradeDate(it).toString() }, { it.tradeTime }, { it.createdAt }))
            .forEach { transaction ->
                when (transaction.tradeType) {
                    TradeType.DEPOSIT -> {
                        val mult = if (isOption(transaction.symbol, transaction.assetType)) 100.0 else 1.0
                        val amountCny = convertToCny(transaction.price * transaction.quantity * mult, transaction.market, exchangeRates)
                        if (transaction.market == Market.CASH || transaction.symbol == "CASH") {
                            cashBalanceCny += amountCny
                        } else {
                            val key = positionKey(transaction.symbol, transaction.market)
                            val current = positions[key] ?: PortfolioPosition(
                                symbol = transaction.symbol,
                                name = transaction.name,
                                market = transaction.market,
                                quantity = 0.0,
                                averageCost = 0.0,
                                remainingCost = 0.0,
                                realizedProfit = 0.0,
                                assetType = transaction.assetType,
                            )
                            val rawQty = current.quantity + transaction.quantity
                            val nextQuantity = cleanQuantity(rawQty)
                            val nextRemaining = if (nextQuantity == 0.0) 0.0 else current.remainingCost + (transaction.price * transaction.quantity * mult)
                            positions[key] = current.copy(
                                quantity = nextQuantity,
                                remainingCost = nextRemaining,
                                averageCost = if (nextQuantity == 0.0) 0.0 else nextRemaining / (nextQuantity * mult),
                            )
                        }
                        totalDepositCny += amountCny
                    }

                    TradeType.WITHDRAW -> {
                        val mult = if (isOption(transaction.symbol, transaction.assetType)) 100.0 else 1.0
                        val amountCny = convertToCny(transaction.price * transaction.quantity * mult, transaction.market, exchangeRates)
                        if (transaction.market == Market.CASH || transaction.symbol == "CASH") {
                            cashBalanceCny -= amountCny
                        } else {
                            val key = positionKey(transaction.symbol, transaction.market)
                            val current = positions[key]
                            if (current != null) {
                                val rawQty = current.quantity - transaction.quantity
                                val nextQuantity = cleanQuantity(rawQty)
                                val nextRemaining = if (nextQuantity == 0.0) 0.0 else current.remainingCost - (transaction.price * transaction.quantity * mult)
                                positions[key] = current.copy(
                                    quantity = nextQuantity,
                                    remainingCost = nextRemaining,
                                    averageCost = if (nextQuantity == 0.0) 0.0 else nextRemaining / (nextQuantity * mult),
                                )
                            }
                        }
                        totalWithdrawCny += amountCny
                    }

                    TradeType.INTEREST -> {
                        val amountCny = convertToCny(kotlin.math.abs(transaction.price * transaction.quantity), transaction.market, exchangeRates)
                        cashBalanceCny -= amountCny
                    }

                    TradeType.BUY, TradeType.SELL -> {
                        totalCommissionCny += convertToCny(transaction.commission, transaction.market, exchangeRates)
                        totalTaxCny += convertToCny(transaction.tax, transaction.market, exchangeRates)
                        securityTradeCount += 1
                        if (transaction.tradeType == TradeType.BUY) buyTradeCount++ else sellTradeCount++
                        cashBalanceCny += applySecurityTrade(transaction, positions, exchangeRates)
                    }

                    TradeType.EXPIRE -> {
                        applyExpire(transaction, positions)
                    }

                    TradeType.TRANSFER_IN -> {
                        val mult = if (isOption(transaction.symbol, transaction.assetType)) 100.0 else 1.0
                        val amountCny = convertToCny(transaction.price * transaction.quantity * mult, transaction.market, exchangeRates)
                        if (transaction.market == Market.CASH || transaction.symbol == "CASH") {
                            cashBalanceCny += amountCny
                        } else {
                            val key = positionKey(transaction.symbol, transaction.market)
                            val current = positions[key] ?: PortfolioPosition(
                                symbol = transaction.symbol,
                                name = transaction.name,
                                market = transaction.market,
                                quantity = 0.0,
                                averageCost = 0.0,
                                remainingCost = 0.0,
                                realizedProfit = 0.0,
                                assetType = transaction.assetType,
                            )
                            val rawQty = current.quantity + transaction.quantity
                            val nextQuantity = cleanQuantity(rawQty)
                            val nextRemaining = if (nextQuantity == 0.0) 0.0 else current.remainingCost + (transaction.price * transaction.quantity * mult)
                            positions[key] = current.copy(
                                quantity = nextQuantity,
                                remainingCost = nextRemaining,
                                averageCost = if (nextQuantity == 0.0) 0.0 else nextRemaining / (nextQuantity * mult),
                            )
                        }
                        totalDepositCny += amountCny
                    }

                    TradeType.TRANSFER_OUT -> {
                        val mult = if (isOption(transaction.symbol, transaction.assetType)) 100.0 else 1.0
                        val amountCny = convertToCny(transaction.price * transaction.quantity * mult, transaction.market, exchangeRates)
                        if (transaction.market == Market.CASH || transaction.symbol == "CASH") {
                            cashBalanceCny -= amountCny
                        } else {
                            val key = positionKey(transaction.symbol, transaction.market)
                            val current = positions[key] ?: PortfolioPosition(
                                symbol = transaction.symbol,
                                name = transaction.name,
                                market = transaction.market,
                                quantity = 0.0,
                                averageCost = 0.0,
                                remainingCost = 0.0,
                                realizedProfit = 0.0,
                                assetType = transaction.assetType,
                            )
                            val rawQty = current.quantity - transaction.quantity
                            val nextQuantity = cleanQuantity(rawQty)
                            val nextRemaining = if (nextQuantity == 0.0) 0.0 else current.remainingCost - (transaction.price * transaction.quantity * mult)
                            positions[key] = current.copy(
                                quantity = nextQuantity,
                                remainingCost = nextRemaining,
                                averageCost = if (nextQuantity == 0.0) 0.0 else nextRemaining / (nextQuantity * mult),
                            )
                        }
                        totalWithdrawCny += amountCny
                    }

                    TradeType.SPLIT -> {
                        val key = positionKey(transaction.symbol, transaction.market)
                        val current = positions[key]
                        if (current != null && !isAlmostZero(current.quantity)) {
                            val rawQty = current.quantity * transaction.price
                            val nextQuantity = cleanQuantity(rawQty)
                            val mult = if (isOption(transaction.symbol, transaction.assetType)) 100.0 else 1.0
                            positions[key] = current.copy(
                                quantity = nextQuantity,
                                averageCost = if (nextQuantity == 0.0) 0.0 else current.remainingCost / (nextQuantity * mult),
                            )
                        }
                    }
                }
            }

        val quoteMap = quotes.associateBy { positionKey(it.symbol, it.market) }
        val holdingsValueCny = positions.values.sumOf { position ->
            val quote = quoteMap[positionKey(position.symbol, position.market)]
            val mult = if (isOption(position.symbol, position.assetType)) 100.0 else 1.0
            convertToCny(position.quantity * (quote?.currentPrice ?: position.averageCost) * mult, position.market, exchangeRates)
        }
        val holdingsCostCny = positions.values.sumOf { position ->
            convertToCny(position.remainingCost, position.market, exchangeRates)
        }
        val unrealizedProfitCny = holdingsValueCny - holdingsCostCny
        val dayProfitCny = positions.values.sumOf { position ->
            val quote = quoteMap[positionKey(position.symbol, position.market)] ?: return@sumOf 0.0
            val current = quote.currentPrice ?: return@sumOf 0.0
            val previous = quote.previousClose ?: return@sumOf 0.0
            val mult = if (isOption(position.symbol, position.assetType)) 100.0 else 1.0
            convertToCny((current - previous) * position.quantity * mult, position.market, exchangeRates)
        }
        val previousHoldingsValueCny = positions.values.sumOf { position ->
            val quote = quoteMap[positionKey(position.symbol, position.market)] ?: return@sumOf 0.0
            val previous = quote.previousClose ?: return@sumOf 0.0
            val mult = if (isOption(position.symbol, position.assetType)) 100.0 else 1.0
            convertToCny(previous * position.quantity * mult, position.market, exchangeRates)
        }
        val netInflowCny = totalDepositCny - totalWithdrawCny
        val totalAssetsCny = holdingsValueCny + cashBalanceCny
        val previousAssetValueCny = previousHoldingsValueCny + cashBalanceCny

        return PortfolioSnapshot(
            positions = positions,
            totalAssetsCny = totalAssetsCny,
            holdingsValueCny = holdingsValueCny,
            cashBalanceCny = cashBalanceCny,
            totalDepositCny = totalDepositCny,
            totalWithdrawCny = totalWithdrawCny,
            netInflowCny = netInflowCny,
            unrealizedProfitCny = unrealizedProfitCny,
            unrealizedProfitPercent = if (holdingsCostCny == 0.0) 0.0 else {
                (unrealizedProfitCny / holdingsCostCny) * 100.0
            },
            dayProfitCny = dayProfitCny,
            dayProfitPercent = if (previousAssetValueCny == 0.0) 0.0 else {
                (dayProfitCny / previousAssetValueCny) * 100.0
            },
            totalCommissionCny = totalCommissionCny,
            totalTaxCny = totalTaxCny,
            securityTradeCount = securityTradeCount,
            buyTradeCount = buyTradeCount,
            sellTradeCount = sellTradeCount,
        )
    }

    private fun isOption(symbol: String, assetType: String): Boolean {
        return assetType.uppercase(java.util.Locale.US) == "OPTION" || isOptionSymbol(symbol)
    }

    private fun isOptionSymbol(symbol: String): Boolean {
        val parts = symbol.trim().split(" ")
        if (parts.size != 2) return false
        val optPart = parts[1]
        if (optPart.length < 8) return false
        val datePart = optPart.substring(0, 6)
        if (!datePart.all { it.isDigit() }) return false
        val typeChar = optPart[6]
        if (typeChar != 'C' && typeChar != 'P') return false
        return true
    }

    private fun applySecurityTrade(
        transaction: PortfolioTrade,
        positions: MutableMap<String, PortfolioPosition>,
        exchangeRates: ExchangeRates,
    ): Double {
        val key = positionKey(transaction.symbol, transaction.market)
        val current = positions[key] ?: PortfolioPosition(
            symbol = transaction.symbol,
            name = transaction.name,
            market = transaction.market,
            quantity = 0.0,
            averageCost = 0.0,
            remainingCost = 0.0,
            realizedProfit = 0.0,
            assetType = transaction.assetType,
        )

        val mult = if (isOption(transaction.symbol, transaction.assetType)) 100.0 else 1.0
        val cashDelta = if (transaction.tradeType == TradeType.BUY) {
            -convertToCny(
                transaction.price * transaction.quantity * mult + transaction.commission + transaction.tax,
                transaction.market,
                exchangeRates,
            )
        } else {
            convertToCny(
                transaction.price * transaction.quantity * mult - transaction.commission - transaction.tax,
                transaction.market,
                exchangeRates,
            )
        }

        positions[key] = if (transaction.tradeType == TradeType.BUY) {
            applyBuy(current, transaction)
        } else {
            applySell(current, transaction)
        }
        return cashDelta
    }

    private fun applyBuy(current: PortfolioPosition, transaction: PortfolioTrade): PortfolioPosition {
        val mult = if (isOption(transaction.symbol, transaction.assetType)) 100.0 else 1.0
        if (current.quantity < 0.0) {
            val coverQuantity = minOf(-current.quantity, transaction.quantity)
            val coverProfit = (current.averageCost - transaction.price) * coverQuantity * mult
            val coverFees = transaction.commission + transaction.tax
            val rawRemainingBuyQty = transaction.quantity - coverQuantity
            val remainingBuyQty = cleanQuantity(rawRemainingBuyQty)
            return if (remainingBuyQty > 0.0) {
                PortfolioPosition(
                    symbol = transaction.symbol,
                    name = transaction.name,
                    market = transaction.market,
                    quantity = remainingBuyQty,
                    remainingCost = transaction.price * remainingBuyQty * mult,
                    averageCost = transaction.price,
                    realizedProfit = current.realizedProfit + coverProfit - coverFees,
                    assetType = transaction.assetType,
                )
            } else {
                val rawNextQty = current.quantity + transaction.quantity
                val nextQuantity = cleanQuantity(rawNextQty)
                val nextRemaining = if (nextQuantity == 0.0) {
                    0.0
                } else {
                    current.remainingCost * (nextQuantity / current.quantity)
                }
                current.copy(
                    quantity = nextQuantity,
                    remainingCost = nextRemaining,
                    averageCost = if (nextQuantity == 0.0) 0.0 else nextRemaining / (nextQuantity * mult),
                    realizedProfit = current.realizedProfit + coverProfit - coverFees,
                )
            }
        }

        val buyCost = transaction.price * transaction.quantity * mult + transaction.commission + transaction.tax
        val rawNextQty = current.quantity + transaction.quantity
        val nextQuantity = cleanQuantity(rawNextQty)
        val nextRemaining = if (nextQuantity == 0.0) 0.0 else current.remainingCost + buyCost
        return current.copy(
            quantity = nextQuantity,
            remainingCost = nextRemaining,
            averageCost = if (nextQuantity == 0.0) 0.0 else nextRemaining / (nextQuantity * mult),
        )
    }

    private fun applySell(current: PortfolioPosition, transaction: PortfolioTrade): PortfolioPosition {
        val mult = if (isOption(transaction.symbol, transaction.assetType)) 100.0 else 1.0
        if (current.quantity > 0.0) {
            val closeQuantity = minOf(current.quantity, transaction.quantity)
            val removedCost = current.averageCost * closeQuantity * mult
            val closeProceeds = transaction.price * closeQuantity * mult
            val closeProfit = closeProceeds - removedCost
            val rawRemainingSellQty = transaction.quantity - closeQuantity
            val remainingSellQty = cleanQuantity(rawRemainingSellQty)
            return if (remainingSellQty > 0.0) {
                PortfolioPosition(
                    symbol = transaction.symbol,
                    name = transaction.name,
                    market = transaction.market,
                    quantity = -remainingSellQty,
                    remainingCost = -(transaction.price * remainingSellQty * mult),
                    averageCost = transaction.price,
                    realizedProfit = current.realizedProfit + closeProfit,
                    assetType = transaction.assetType,
                )
            } else {
                val rawNextQty = current.quantity - closeQuantity
                val nextQuantity = cleanQuantity(rawNextQty)
                val nextRemaining = if (nextQuantity == 0.0) 0.0 else current.remainingCost - removedCost
                current.copy(
                    quantity = nextQuantity,
                    remainingCost = nextRemaining,
                    averageCost = if (nextQuantity == 0.0) 0.0 else nextRemaining / (nextQuantity * mult),
                    realizedProfit = current.realizedProfit + closeProfit,
                )
            }
        }

        val rawNextQty = current.quantity - transaction.quantity
        val nextQuantity = cleanQuantity(rawNextQty)
        val nextRemaining = if (nextQuantity == 0.0) 0.0 else current.remainingCost - (transaction.price * transaction.quantity * mult)
        return current.copy(
            quantity = nextQuantity,
            remainingCost = nextRemaining,
            averageCost = if (nextQuantity == 0.0) 0.0 else nextRemaining / (nextQuantity * mult),
        )
    }
    private fun isAlmostZero(value: Double): Boolean = kotlin.math.abs(value) < 1e-6

    private fun cleanQuantity(qty: Double): Double = if (isAlmostZero(qty)) 0.0 else qty

    private fun applyExpire(
        transaction: PortfolioTrade,
        positions: MutableMap<String, PortfolioPosition>,
    ) {
        val key = positionKey(transaction.symbol, transaction.market)
        val current = positions[key] ?: return
        val qtyDelta = transaction.quantity
        val nextQuantity = if (current.quantity > 0.0) {
            maxOf(0.0, current.quantity - qtyDelta)
        } else {
            minOf(0.0, current.quantity + qtyDelta)
        }
        val fraction = if (current.quantity == 0.0) 1.0 else {
            val closed = current.quantity - nextQuantity
            Math.abs(closed / current.quantity)
        }
        val closedCost = current.remainingCost * fraction
        val coverProfit = -closedCost
        positions[key] = current.copy(
            quantity = nextQuantity,
            remainingCost = current.remainingCost - closedCost,
            averageCost = if (nextQuantity == 0.0) 0.0 else (current.remainingCost - closedCost) / (nextQuantity * (if (isOption(transaction.symbol, transaction.assetType)) 100.0 else 1.0)),
            realizedProfit = current.realizedProfit + coverProfit
        )
    }

    private fun effectiveTradeDate(transaction: PortfolioTrade): LocalDate {
        val date = LocalDate.parse(transaction.tradeDate)
        return if (transaction.market == Market.US && transaction.tradeTime < US_TIMEZONE_CUTOFF) {
            date.minusDays(1)
        } else {
            date
        }
    }

    private fun convertToCny(value: Double, market: Market, exchangeRates: ExchangeRates): Double =
        value * exchangeRates.rateToCny(market)

    private fun positionKey(symbol: String, market: Market): String = "${market.name}:$symbol"

    private companion object {
        const val US_TIMEZONE_CUTOFF = "06:00"
    }
}
