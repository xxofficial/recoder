package com.recoder.stockledger.data

import java.time.LocalDate

data class ProfitAnalysisUiModel(
    val dailyPoints: List<ProfitAnalysisPointUiModel> = listOf(
        ProfitAnalysisPointUiModel(
            date = LocalDate.now(),
            dailyProfitCny = 0.0,
            cumulativeProfitCny = 0.0,
        ),
    ),
    val netInflowCny: Double = 0.0,
    val latestDate: LocalDate = LocalDate.now(),
)

data class ProfitAnalysisPointUiModel(
    val date: LocalDate,
    val dailyProfitCny: Double,
    val cumulativeProfitCny: Double,
)
