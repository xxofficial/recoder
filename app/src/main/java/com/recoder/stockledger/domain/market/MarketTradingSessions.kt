package com.recoder.stockledger.domain.market

import com.recoder.stockledger.data.Market
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object MarketTradingSessions {
    private val chinaZone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val hongKongZone: ZoneId = ZoneId.of("Asia/Hong_Kong")
    private val newYorkZone: ZoneId = ZoneId.of("America/New_York")

    fun realtimeTradeDateFor(market: Market, now: Instant): LocalDate? {
        if (market == Market.CASH) return null
        val zone = zoneFor(market)
        val localNow = now.atZone(zone)
        val localDate = localNow.toLocalDate()
        val localTime = localNow.toLocalTime()
        if (!isWeekday(localDate)) return null
        return if (localTime >= firstOpenTimeFor(market)) localDate else null
    }

    fun hasOpenedForTrading(market: Market, now: Instant): Boolean =
        realtimeTradeDateFor(market, now) != null

    private fun zoneFor(market: Market): ZoneId = when (market) {
        Market.A_SHARE -> chinaZone
        Market.HK -> hongKongZone
        Market.US -> newYorkZone
        Market.CASH -> chinaZone
    }

    private fun firstOpenTimeFor(market: Market): LocalTime = when (market) {
        Market.A_SHARE, Market.HK -> LocalTime.of(9, 30)
        Market.US -> LocalTime.of(9, 30)
        Market.CASH -> LocalTime.MAX
    }

    private fun isWeekday(date: LocalDate): Boolean =
        date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY
}
