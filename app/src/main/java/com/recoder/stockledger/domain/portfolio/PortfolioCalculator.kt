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
    val quantity: Int,
    val commission: Double,
    val tax: Double,
    val createdAt: Long,
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
    val quantity: Int,
    val averageCost: Double,
    val remainingCost: Double,
    val realizedProfit: Double,
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
                        val amountCny = convertToCny(transaction.price * transaction.quantity, transaction.market, exchangeRates)
                        cashBalanceCny += amountCny
                        totalDepositCny += amountCny
                    }

                    TradeType.WITHDRAW -> {
                        val amountCny = convertToCny(transaction.price * transaction.quantity, transaction.market, exchangeRates)
                        cashBalanceCny -= amountCny
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

                    TradeType.TRANSFER_IN -> {
                        if (transaction.symbol == "CASH") {
                            val amountCny = convertToCny(transaction.price * transaction.quantity, transaction.market, exchangeRates)
                            cashBalanceCny += amountCny
                        } else {
                            val key = positionKey(transaction.symbol, transaction.market)
                            val current = positions[key] ?: PortfolioPosition(
                                symbol = transaction.symbol,
                                name = transaction.name,
                                market = transaction.market,
                                quantity = 0,
                                averageCost = 0.0,
                                remainingCost = 0.0,
                                realizedProfit = 0.0,
                            )
                            val nextQuantity = current.quantity + transaction.quantity
                            val nextRemaining = current.remainingCost + (transaction.price * transaction.quantity)
                            positions[key] = current.copy(
                                quantity = nextQuantity,
                                remainingCost = nextRemaining,
                                averageCost = if (nextQuantity == 0) 0.0 else nextRemaining / nextQuantity,
                            )
                        }
                    }

                    TradeType.TRANSFER_OUT -> {
                        if (transaction.symbol == "CASH") {
                            val amountCny = convertToCny(transaction.price * transaction.quantity, transaction.market, exchangeRates)
                            cashBalanceCny -= amountCny
                        } else {
                            val key = positionKey(transaction.symbol, transaction.market)
                            val current = positions[key] ?: PortfolioPosition(
                                symbol = transaction.symbol,
                                name = transaction.name,
                                market = transaction.market,
                                quantity = 0,
                                averageCost = 0.0,
                                remainingCost = 0.0,
                                realizedProfit = 0.0,
                            )
                            val nextQuantity = current.quantity - transaction.quantity
                            val nextRemaining = current.remainingCost - (transaction.price * transaction.quantity)
                            positions[key] = current.copy(
                                quantity = nextQuantity,
                                remainingCost = nextRemaining,
                                averageCost = if (nextQuantity == 0) 0.0 else nextRemaining / nextQuantity,
                            )
                        }
                    }
                }
            }

        val quoteMap = quotes.associateBy { positionKey(it.symbol, it.market) }
        val holdingsValueCny = positions.values.sumOf { position ->
            val quote = quoteMap[positionKey(position.symbol, position.market)]
            convertToCny(position.quantity * (quote?.currentPrice ?: position.averageCost), position.market, exchangeRates)
        }
        val holdingsCostCny = positions.values.sumOf { position ->
            convertToCny(position.remainingCost, position.market, exchangeRates)
        }
        val unrealizedProfitCny = holdingsValueCny - holdingsCostCny
        val dayProfitCny = positions.values.sumOf { position ->
            val quote = quoteMap[positionKey(position.symbol, position.market)] ?: return@sumOf 0.0
            val current = quote.currentPrice ?: return@sumOf 0.0
            val previous = quote.previousClose ?: return@sumOf 0.0
            convertToCny((current - previous) * position.quantity, position.market, exchangeRates)
        }
        val previousHoldingsValueCny = positions.values.sumOf { position ->
            val quote = quoteMap[positionKey(position.symbol, position.market)] ?: return@sumOf 0.0
            val previous = quote.previousClose ?: return@sumOf 0.0
            convertToCny(previous * position.quantity, position.market, exchangeRates)
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
            quantity = 0,
            averageCost = 0.0,
            remainingCost = 0.0,
            realizedProfit = 0.0,
        )

        val cashDelta = if (transaction.tradeType == TradeType.BUY) {
            -convertToCny(
                transaction.price * transaction.quantity + transaction.commission + transaction.tax,
                transaction.market,
                exchangeRates,
            )
        } else {
            convertToCny(
                transaction.price * transaction.quantity - transaction.commission - transaction.tax,
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
        if (current.quantity < 0) {
            val coverQuantity = minOf(-current.quantity, transaction.quantity)
            val coverProfit = (current.averageCost - transaction.price) * coverQuantity
            val coverFees = transaction.commission + transaction.tax
            val remainingBuyQty = transaction.quantity - coverQuantity
            return if (remainingBuyQty > 0) {
                PortfolioPosition(
                    symbol = transaction.symbol,
                    name = transaction.name,
                    market = transaction.market,
                    quantity = remainingBuyQty,
                    remainingCost = transaction.price * remainingBuyQty,
                    averageCost = transaction.price,
                    realizedProfit = current.realizedProfit + coverProfit - coverFees,
                )
            } else {
                val nextQuantity = current.quantity + transaction.quantity
                val nextRemaining = if (nextQuantity == 0) {
                    0.0
                } else {
                    current.remainingCost * (nextQuantity.toDouble() / current.quantity.toDouble())
                }
                current.copy(
                    quantity = nextQuantity,
                    remainingCost = nextRemaining,
                    averageCost = if (nextQuantity == 0) 0.0 else nextRemaining / nextQuantity,
                    realizedProfit = current.realizedProfit + coverProfit - coverFees,
                )
            }
        }

        val buyCost = transaction.price * transaction.quantity + transaction.commission + transaction.tax
        val nextQuantity = current.quantity + transaction.quantity
        val nextRemaining = current.remainingCost + buyCost
        return current.copy(
            quantity = nextQuantity,
            remainingCost = nextRemaining,
            averageCost = if (nextQuantity == 0) 0.0 else nextRemaining / nextQuantity,
        )
    }

    private fun applySell(current: PortfolioPosition, transaction: PortfolioTrade): PortfolioPosition {
        if (current.quantity > 0) {
            val closeQuantity = minOf(current.quantity, transaction.quantity)
            val removedCost = current.averageCost * closeQuantity
            val closeProceeds = transaction.price * closeQuantity
            val closeProfit = closeProceeds - removedCost
            val remainingSellQty = transaction.quantity - closeQuantity
            return if (remainingSellQty > 0) {
                PortfolioPosition(
                    symbol = transaction.symbol,
                    name = transaction.name,
                    market = transaction.market,
                    quantity = -remainingSellQty,
                    remainingCost = -(transaction.price * remainingSellQty),
                    averageCost = transaction.price,
                    realizedProfit = current.realizedProfit + closeProfit,
                )
            } else {
                val nextQuantity = current.quantity - closeQuantity
                val nextRemaining = if (nextQuantity == 0) 0.0 else current.remainingCost - removedCost
                current.copy(
                    quantity = nextQuantity,
                    remainingCost = nextRemaining,
                    averageCost = if (nextQuantity == 0) 0.0 else nextRemaining / nextQuantity,
                    realizedProfit = current.realizedProfit + closeProfit,
                )
            }
        }

        val nextQuantity = current.quantity - transaction.quantity
        val nextRemaining = current.remainingCost - (transaction.price * transaction.quantity)
        return current.copy(
            quantity = nextQuantity,
            remainingCost = nextRemaining,
            averageCost = if (nextQuantity == 0) 0.0 else nextRemaining / nextQuantity,
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
