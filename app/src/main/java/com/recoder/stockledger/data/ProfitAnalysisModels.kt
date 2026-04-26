package com.recoder.stockledger.data

import java.time.LocalDate

data class ProfitAnalysisUiModel(
    val dailyPoints: List<ProfitAnalysisPointUiModel> = listOf(
        ProfitAnalysisPointUiModel(
            date = LocalDate.now(),
            dailyProfitCny = 0.0,
            cumulativeProfitCny = 0.0,
            totalAssetsCny = 0.0,
            netInflowCny = 0.0,
            dailyReturnPercent = 0.0,
            cumulativeReturnPercent = 0.0,
        ),
    ),
    val securityAnalyses: List<SecurityProfitAnalysisUiModel> = emptyList(),
    val netInflowCny: Double = 0.0,
    val latestDate: LocalDate = LocalDate.now(),
)

data class ProfitAnalysisPointUiModel(
    val date: LocalDate,
    val dailyProfitCny: Double,
    val cumulativeProfitCny: Double,
    val totalAssetsCny: Double = 0.0,
    val netInflowCny: Double = 0.0,
    val dailyReturnPercent: Double = 0.0,
    val cumulativeReturnPercent: Double = 0.0,
)

data class SecurityProfitAnalysisUiModel(
    val symbol: String,
    val name: String,
    val market: Market,
    val dailyPoints: List<SecurityProfitPointUiModel> = emptyList(),
)

data class SecurityProfitPointUiModel(
    val date: LocalDate,
    val dailyProfitCny: Double,
    val cumulativeProfitCny: Double,
)
