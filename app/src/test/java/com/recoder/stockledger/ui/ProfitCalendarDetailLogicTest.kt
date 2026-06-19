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
    fun wheelDateCoercesInvalidDaysForLeapAndCommonYears() {
        assertEquals(LocalDate.of(2024, 2, 29), coerceWheelDate(2024, 2, 31))
        assertEquals(LocalDate.of(2025, 2, 28), coerceWheelDate(2025, 2, 31))
    }

    @Test
    fun wheelDateClampsDayWhenMonthChanges() {
        val jan31 = LocalDate.of(2026, 1, 31)
        val maxDate = LocalDate.of(2026, 12, 31)

        assertEquals(LocalDate.of(2026, 2, 28), updateWheelDate(jan31, month = 2, maxDate = maxDate))
        assertEquals(LocalDate.of(2026, 4, 30), updateWheelDate(jan31, month = 4, maxDate = maxDate))
        assertEquals(LocalDate.of(2026, 12, 31), updateWheelDate(jan31, month = 12, maxDate = maxDate))
    }

    @Test
    fun wheelDateClampsToConfiguredBounds() {
        val minDate = LocalDate.of(2026, 3, 15)
        val maxDate = LocalDate.of(2026, 6, 19)

        assertEquals(
            LocalDate.of(2026, 3, 15),
            coerceWheelDate(2026, 1, 10, minDate = minDate, maxDate = maxDate),
        )
        assertEquals(
            LocalDate.of(2026, 6, 19),
            coerceWheelDate(2026, 12, 31, minDate = minDate, maxDate = maxDate),
        )
    }

    @Test
    fun advancedCustomRangeNormalizationOrdersAndClampsDates() {
        assertEquals(
            "2026-03-15" to "2026-06-19",
            normalizeAdvancedCustomDateRange(
                startDate = "2026-08-01",
                endDate = "2026-01-01",
                minDate = LocalDate.of(2026, 3, 15),
                maxDate = LocalDate.of(2026, 6, 19),
            ),
        )
        assertEquals(
            "2026-03-15" to "2026-06-19",
            normalizeAdvancedCustomDateRange(
                startDate = "",
                endDate = "",
                minDate = LocalDate.of(2026, 3, 15),
                maxDate = LocalDate.of(2026, 6, 19),
            ),
        )
    }

    @Test
    fun centeredWheelValueChoosesNearestVisibleItem() {
        val visibleItems = listOf(
            WheelVisibleItemInfo(value = 2025, offset = 45, size = 30),
            WheelVisibleItemInfo(value = 2026, offset = 75, size = 30),
            WheelVisibleItemInfo(value = 2027, offset = 105, size = 30),
        )

        assertEquals(
            2026,
            findCenteredWheelValue(
                visibleItems = visibleItems,
                viewportStartOffset = 0,
                viewportEndOffset = 152,
            ),
        )
    }

    @Test
    fun centeredWheelValueSupportsShortYearLists() {
        val firstYearCentered = listOf(
            WheelVisibleItemInfo(value = 2025, offset = 61, size = 30),
            WheelVisibleItemInfo(value = 2026, offset = 91, size = 30),
        )
        val secondYearCentered = listOf(
            WheelVisibleItemInfo(value = 2025, offset = 31, size = 30),
            WheelVisibleItemInfo(value = 2026, offset = 61, size = 30),
        )

        assertEquals(2025, findCenteredWheelValue(firstYearCentered, 0, 152))
        assertEquals(2026, findCenteredWheelValue(secondYearCentered, 0, 152))
    }

    @Test
    fun transactionDateRangeNormalizationHandlesEmptyAndReversedDates() {
        assertEquals(null to null, normalizeDateRange("", ""))
        assertEquals("2026-04-01" to null, normalizeDateRange("2026-04-01", ""))
        assertEquals(null to "2026-04-30", normalizeDateRange("", "2026-04-30"))
        assertEquals(
            "2026-04-01" to "2026-04-30",
            normalizeDateRange("2026-04-30", "2026-04-01"),
        )
    }

    @Test
    fun mapsAdvancedRangeToStockDetailRange() {
        assertEquals(DetailRange.ALL, mapAdvancedProfitRangeToDetailRange(AdvancedProfitRange.ALL))
        assertEquals(DetailRange.THIS_MONTH, mapAdvancedProfitRangeToDetailRange(AdvancedProfitRange.THIS_MONTH))
        assertEquals(DetailRange.ONE_MONTH, mapAdvancedProfitRangeToDetailRange(AdvancedProfitRange.ONE_MONTH))
        assertEquals(DetailRange.SIX_MONTHS, mapAdvancedProfitRangeToDetailRange(AdvancedProfitRange.SIX_MONTHS))
        assertEquals(DetailRange.THIS_YEAR, mapAdvancedProfitRangeToDetailRange(AdvancedProfitRange.THIS_YEAR))
        assertEquals(DetailRange.CUSTOM, mapAdvancedProfitRangeToDetailRange(AdvancedProfitRange.CUSTOM))
    }

    @Test
    fun stockDetailRouteCanCarryRangeAndCustomDates() {
        val plain = buildStockDetailRoute(
            baseRoute = "stock-detail",
            symbol = "AAPL",
            market = "美股",
        )
        val custom = buildStockDetailRoute(
            baseRoute = "stock-detail",
            symbol = "BRK B",
            market = "美股",
            range = AdvancedProfitRange.CUSTOM,
            customStart = "2026-04-01",
            customEnd = "2026-04-30",
        )

        assertEquals("stock-detail/AAPL/%E7%BE%8E%E8%82%A1", plain)
        assertEquals(
            "stock-detail/BRK+B/%E7%BE%8E%E8%82%A1?range=CUSTOM&customStart=2026-04-01&customEnd=2026-04-30",
            custom,
        )
        assertEquals("BRK B", decodeStockDetailNavArg("BRK+B"))
    }

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
    fun securityRowsRebaseStockAndDerivativeProfitSeparately() {
        val rows = buildProfitCalendarDetailSecurityRows(
            securityAnalyses = listOf(
                SecurityProfitAnalysisUiModel(
                    symbol = "AAPL",
                    name = "Apple",
                    market = Market.US,
                    dailyPoints = listOf(
                        SecurityProfitPointUiModel(
                            date = LocalDate.of(2026, 4, 20),
                            dailyProfitCny = 30.0,
                            cumulativeProfitCny = 30.0,
                            dailyStockProfitCny = 50.0,
                            dailyDerivativeProfitCny = -20.0,
                            cumulativeStockProfitCny = 50.0,
                            cumulativeDerivativeProfitCny = -20.0,
                        ),
                        SecurityProfitPointUiModel(
                            date = LocalDate.of(2026, 4, 21),
                            dailyProfitCny = -12.0,
                            cumulativeProfitCny = 18.0,
                            dailyStockProfitCny = 8.0,
                            dailyDerivativeProfitCny = -20.0,
                            cumulativeStockProfitCny = 58.0,
                            cumulativeDerivativeProfitCny = -40.0,
                        ),
                        SecurityProfitPointUiModel(
                            date = LocalDate.of(2026, 4, 22),
                            dailyProfitCny = -15.0,
                            cumulativeProfitCny = 3.0,
                            dailyStockProfitCny = 5.0,
                            dailyDerivativeProfitCny = -20.0,
                            cumulativeStockProfitCny = 63.0,
                            cumulativeDerivativeProfitCny = -60.0,
                        ),
                    ),
                ),
            ),
            rangeStart = LocalDate.of(2026, 4, 21),
            rangeEnd = LocalDate.of(2026, 4, 22),
            netInflowCny = 1000.0,
        )

        assertEquals(1, rows.size)
        assertEquals(-27.0, rows.first().amountCny, 0.0001)
        assertEquals(13.0, rows.first().stockProfitCny, 0.0001)
        assertEquals(-40.0, rows.first().derivativeProfitCny, 0.0001)
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
