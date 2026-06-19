package com.recoder.stockledger.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ProfitAnalysisModelsTest {
    @Test
    fun `scaled also scales stock and derivative security profits`() {
        val model = ProfitAnalysisUiModel(
            dailyPoints = listOf(
                ProfitAnalysisPointUiModel(
                    date = LocalDate.of(2026, 4, 30),
                    dailyProfitCny = -100.0,
                    cumulativeProfitCny = -100.0,
                ),
            ),
            securityAnalyses = listOf(
                SecurityProfitAnalysisUiModel(
                    symbol = "AAPL",
                    name = "Apple Inc.",
                    market = Market.US,
                    dailyPoints = listOf(
                        SecurityProfitPointUiModel(
                            date = LocalDate.of(2026, 4, 30),
                            dailyProfitCny = -67.03,
                            cumulativeProfitCny = -67.03,
                        ),
                    ),
                    totalProfitCny = -67.03,
                    stockProfitCny = 0.0,
                    derivativeProfitCny = -67.03,
                ),
            ),
        )

        val scaled = model.scaled(0.5)
        val security = scaled.securityAnalyses.single()

        assertEquals(-33.515, security.totalProfitCny, 0.0001)
        assertEquals(0.0, security.stockProfitCny, 0.0001)
        assertEquals(-33.515, security.derivativeProfitCny, 0.0001)
        assertEquals(-33.515, security.dailyPoints.single().cumulativeProfitCny, 0.0001)
    }
}
