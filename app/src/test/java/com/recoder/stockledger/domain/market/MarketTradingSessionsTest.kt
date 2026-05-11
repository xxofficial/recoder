package com.recoder.stockledger.domain.market

import com.recoder.stockledger.data.Market
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class MarketTradingSessionsTest {
    @Test
    fun `a share and hong kong do not open on weekend`() {
        val saturdayMorning = Instant.parse("2026-05-09T02:00:00Z")

        assertFalse(MarketTradingSessions.hasOpenedForTrading(Market.A_SHARE, saturdayMorning))
        assertFalse(MarketTradingSessions.hasOpenedForTrading(Market.HK, saturdayMorning))
        assertNull(MarketTradingSessions.realtimeTradeDateFor(Market.A_SHARE, saturdayMorning))
        assertNull(MarketTradingSessions.realtimeTradeDateFor(Market.HK, saturdayMorning))
    }

    @Test
    fun `a share and hong kong wait until local market has opened`() {
        val mondayBeforeOpen = Instant.parse("2026-05-11T01:00:00Z")
        val mondayAfterOpen = Instant.parse("2026-05-11T01:31:00Z")

        assertFalse(MarketTradingSessions.hasOpenedForTrading(Market.A_SHARE, mondayBeforeOpen))
        assertFalse(MarketTradingSessions.hasOpenedForTrading(Market.HK, mondayBeforeOpen))
        assertEquals(LocalDate.parse("2026-05-11"), MarketTradingSessions.realtimeTradeDateFor(Market.A_SHARE, mondayAfterOpen))
        assertEquals(LocalDate.parse("2026-05-11"), MarketTradingSessions.realtimeTradeDateFor(Market.HK, mondayAfterOpen))
    }

    @Test
    fun `us market uses new york local time for daylight saving time`() {
        val beforeOpenInDst = Instant.parse("2026-07-06T13:29:00Z")
        val afterOpenInDst = Instant.parse("2026-07-06T13:30:00Z")

        assertFalse(MarketTradingSessions.hasOpenedForTrading(Market.US, beforeOpenInDst))
        assertEquals(LocalDate.parse("2026-07-06"), MarketTradingSessions.realtimeTradeDateFor(Market.US, afterOpenInDst))
    }

    @Test
    fun `us market uses new york local time for standard time`() {
        val beforeOpenInStandardTime = Instant.parse("2026-01-05T14:29:00Z")
        val afterOpenInStandardTime = Instant.parse("2026-01-05T14:30:00Z")

        assertFalse(MarketTradingSessions.hasOpenedForTrading(Market.US, beforeOpenInStandardTime))
        assertEquals(LocalDate.parse("2026-01-05"), MarketTradingSessions.realtimeTradeDateFor(Market.US, afterOpenInStandardTime))
    }

    @Test
    fun `us market remains on prior new york trade date during china early morning`() {
        val chinaSaturdayMorningAfterUsOpen = Instant.parse("2026-07-10T22:00:00Z")

        assertTrue(MarketTradingSessions.hasOpenedForTrading(Market.US, chinaSaturdayMorningAfterUsOpen))
        assertEquals(LocalDate.parse("2026-07-10"), MarketTradingSessions.realtimeTradeDateFor(Market.US, chinaSaturdayMorningAfterUsOpen))
    }
}
