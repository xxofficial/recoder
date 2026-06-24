package com.recoder.stockledger.domain.portfolio

import com.recoder.stockledger.data.Market
import com.recoder.stockledger.data.TradeType
import com.recoder.stockledger.data.local.QuoteSnapshotEntity
import com.recoder.stockledger.data.local.TransactionEntity

fun TransactionEntity.toPortfolioTrade(): PortfolioTrade =
    PortfolioTrade(
        tradeType = TradeType.valueOf(tradeType),
        market = Market.fromString(market) ?: Market.CASH,
        symbol = symbol,
        name = name,
        tradeDate = tradeDate,
        tradeTime = tradeTime,
        price = price,
        quantity = quantity,
        commission = commission,
        tax = tax,
        createdAt = createdAt,
        assetType = assetType,
        underlyingSymbol = underlyingSymbol,
    )

fun QuoteSnapshotEntity.toPortfolioQuote(): PortfolioQuote =
    PortfolioQuote(
        symbol = symbol,
        market = Market.fromString(market) ?: Market.CASH,
        currentPrice = currentPrice,
        previousClose = previousClose,
    )
