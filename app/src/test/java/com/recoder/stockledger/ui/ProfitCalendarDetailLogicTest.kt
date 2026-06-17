package com.recoder.stockledger.ui

import com.recoder.stockledger.data.Market
import com.recoder.stockledger.data.SecurityProfitAnalysisUiModel
import com.recoder.stockledger.data.SecurityProfitPointUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ProfitCalendarDetailLogicTest {
    @Test
    fun parsesYearMode() {
        assertEquals(ProfitCalendarDetailMode.YEAR, parseProfitCalendarDetailMode("year"))
    }

    @Test
    fun monthDatesAlwaysCoverSixWeeksStartingSunday() {
        val dates = buildProfitCalendarDetailMonthDates(YearMonth.of(2026, 4))

        assertEquals(42, dates.size)
        assertEquals(LocalDate.of(2026, 3, 29), dates.first())
        assertEquals(LocalDate.of(2026, 5, 9), dates.last())
        assertEquals(LocalDate.of(2026, 4, 1), dates[3])
    }

    @Test
    fun dayWindowUsesExactAnchorDate() {
        val window = resolveProfitCalendarDetailWindow(
            mode = ProfitCalendarDetailMode.DAY,
            scope = ProfitCalendarDetailScope.DAY,
            anchorDate = LocalDate.of(2026, 4, 29),
        )

        assertEquals(LocalDate.of(2026, 4, 29), window.start)
        assertEquals(LocalDate.of(2026, 4, 29), window.end)
        assertEquals(ProfitCalendarDetailScope.DAY, window.scope)
    }

    @Test
    fun yearWindowUsesWholeAnchorYear() {
        val window = resolveProfitCalendarDetailWindow(
            mode = ProfitCalendarDetailMode.YEAR,
            scope = ProfitCalendarDetailScope.YEAR,
            anchorDate = LocalDate.of(2026, 6, 17),
        )

        assertEquals(LocalDate.of(2026, 1, 1), window.start)
        assertEquals(LocalDate.of(2026, 12, 31), window.end)
        assertEquals(ProfitCalendarDetailScope.YEAR, window.scope)
    }

    @Test
    fun weekWindowStartsOnSundayAndCanCrossMonth() {
        val window = resolveProfitCalendarDetailWindow(
            mode = ProfitCalendarDetailMode.WEEK,
            scope = ProfitCalendarDetailScope.WEEK,
            anchorDate = LocalDate.of(2026, 5, 1),
        )

        assertEquals(LocalDate.of(2026, 4, 26), window.start)
        assertEquals(LocalDate.of(2026, 5, 2), window.end)
        assertEquals(ProfitCalendarDetailScope.WEEK, window.scope)
    }

    @Test
    fun blankResetForDayOrWeekCanUseWholeMonth() {
        val window = resolveProfitCalendarDetailWindow(
            mode = ProfitCalendarDetailMode.DAY,
            scope = ProfitCalendarDetailScope.MONTH,
            anchorDate = LocalDate.of(2026, 2, 18),
        )

        assertEquals(LocalDate.of(2026, 2, 1), window.start)
        assertEquals(LocalDate.of(2026, 2, 28), window.end)
        assertEquals(ProfitCalendarDetailScope.MONTH, window.scope)
    }

    @Test
    fun blankResetForMonthCanUseWholeYear() {
        val window = resolveProfitCalendarDetailWindow(
            mode = ProfitCalendarDetailMode.MONTH,
            scope = ProfitCalendarDetailScope.YEAR,
            anchorDate = LocalDate.of(2026, 6, 17),
        )

        assertEquals(LocalDate.of(2026, 1, 1), window.start)
        assertEquals(LocalDate.of(2026, 12, 31), window.end)
        assertEquals(ProfitCalendarDetailScope.YEAR, window.scope)
    }

    @Test
    fun securityRowsAreRebasedToSelectedWindow() {
        val rows = buildProfitCalendarDetailSecurityRows(
            securityAnalyses = listOf(
                SecurityProfitAnalysisUiModel(
                    symbol = "00700",
                    name = "Tencent",
                    market = Market.HK,
                    dailyPoints = listOf(
                        SecurityProfitPointUiModel(
                            date = LocalDate.of(2026, 4, 28),
                            dailyProfitCny = 100.0,
                            cumulativeProfitCny = 100.0,
                        ),
                        SecurityProfitPointUiModel(
                            date = LocalDate.of(2026, 4, 29),
                            dailyProfitCny = 70.0,
                            cumulativeProfitCny = 170.0,
                        ),
                        SecurityProfitPointUiModel(
                            date = LocalDate.of(2026, 4, 30),
                            dailyProfitCny = -20.0,
                            cumulativeProfitCny = 150.0,
                        ),
                    ),
                ),
            ),
            rangeStart = LocalDate.of(2026, 4, 29),
            rangeEnd = LocalDate.of(2026, 4, 30),
            netInflowCny = 1000.0,
        )

        assertEquals(1, rows.size)
        assertEquals(50.0, rows.first().amountCny, 0.0001)
        assertEquals(5.0, rows.first().returnPercent, 0.0001)
    }

    @Test
    fun securityRowsAreEmptyWhenNoPointFallsInWindow() {
        val rows = buildProfitCalendarDetailSecurityRows(
            securityAnalyses = listOf(
                SecurityProfitAnalysisUiModel(
                    symbol = "AAPL",
                    name = "Apple",
                    market = Market.US,
                    dailyPoints = listOf(
                        SecurityProfitPointUiModel(
                            date = LocalDate.of(2026, 3, 1),
                            dailyProfitCny = 12.0,
                            cumulativeProfitCny = 12.0,
                        ),
                    ),
                ),
            ),
            rangeStart = LocalDate.of(2026, 4, 1),
            rangeEnd = LocalDate.of(2026, 4, 30),
            netInflowCny = 1000.0,
        )

        assertTrue(rows.isEmpty())
    }
}
