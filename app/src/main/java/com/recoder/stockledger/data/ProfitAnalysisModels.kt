package com.recoder.stockledger.data

import com.recoder.stockledger.data.local.TransactionEntity
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
    val totalCommissionCny: Double = 0.0,
    val totalTaxCny: Double = 0.0,
    val securityTradeCount: Int = 0,
    val transactions: List<TransactionEntity> = emptyList(),
    val isHistoricalDataFallback: Boolean = false,
)

data class ProfitAnalysisPointUiModel(
    val date: LocalDate,
    val dailyProfitCny: Double,
    val cumulativeProfitCny: Double,
    val totalAssetsCny: Double = 0.0,
    val netInflowCny: Double = 0.0,
    val dailyReturnPercent: Double = 0.0,
    val cumulativeReturnPercent: Double = 0.0,
    val dailySecurityTradeCount: Int = 0,
    val dailyBuyCount: Int = 0,
    val dailySellCount: Int = 0,
    val dailyCommissionCny: Double = 0.0,
    val dailyTaxCny: Double = 0.0,
)

data class SecurityProfitAnalysisUiModel(
    val symbol: String,
    val name: String,
    val market: Market,
    val dailyPoints: List<SecurityProfitPointUiModel> = emptyList(),
    val totalProfitCny: Double = dailyPoints.lastOrNull()?.cumulativeProfitCny ?: 0.0,
    val stockProfitCny: Double = totalProfitCny,
    val derivativeProfitCny: Double = 0.0,
    val returnRatePercent: Double = 0.0,
)

data class SecurityProfitPointUiModel(
    val date: LocalDate,
    val dailyProfitCny: Double,
    val cumulativeProfitCny: Double,
    val closePrice: Double? = null,
)

fun ProfitAnalysisUiModel.scaled(ratio: Double): ProfitAnalysisUiModel {
    return this.copy(
        dailyPoints = this.dailyPoints.map { pt ->
            pt.copy(
                dailyProfitCny = pt.dailyProfitCny * ratio,
                cumulativeProfitCny = pt.cumulativeProfitCny * ratio,
                totalAssetsCny = pt.totalAssetsCny * ratio,
                netInflowCny = pt.netInflowCny * ratio,
                dailyCommissionCny = pt.dailyCommissionCny * ratio,
                dailyTaxCny = pt.dailyTaxCny * ratio
            )
        },
        securityAnalyses = this.securityAnalyses.map { sa ->
            sa.copy(
                dailyPoints = sa.dailyPoints.map { spt ->
                    spt.copy(
                        dailyProfitCny = spt.dailyProfitCny * ratio,
                        cumulativeProfitCny = spt.cumulativeProfitCny * ratio
                    )
                },
                totalProfitCny = sa.totalProfitCny * ratio,
                stockProfitCny = sa.stockProfitCny * ratio,
                derivativeProfitCny = sa.derivativeProfitCny * ratio,
            )
        },
        netInflowCny = this.netInflowCny * ratio,
        totalCommissionCny = this.totalCommissionCny * ratio,
        totalTaxCny = this.totalTaxCny * ratio
    )
}
