package com.recoder.stockledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recoder.stockledger.data.HoldingUiModel
import com.recoder.stockledger.data.Market
import com.recoder.stockledger.data.PriceTrend
import com.recoder.stockledger.data.SellCandidateUiModel
import com.recoder.stockledger.ui.theme.BackgroundPrimary
import com.recoder.stockledger.ui.theme.ForegroundPrimary
import com.recoder.stockledger.ui.theme.ForegroundSecondary
import com.recoder.stockledger.ui.theme.MarketNeutral
import com.recoder.stockledger.ui.theme.MarketUp
import com.recoder.stockledger.ui.theme.MarketUpSoft
import com.recoder.stockledger.ui.theme.SurfaceInverse
import com.recoder.stockledger.ui.theme.SurfaceMuted
import com.recoder.stockledger.ui.theme.SurfaceSecondary

@Composable
fun EnhancedHoldingsCard(
    item: HoldingUiModel,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, color = ForegroundPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${item.code} · ${item.market.label} · ${item.quantityLabel} · ${item.costLabel}",
                color = ForegroundSecondary,
                fontSize = 12.sp,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(item.priceLabel, color = ForegroundPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = "当日 ${item.dayProfitLabel} (${item.dayProfitPercentLabel})",
                color = item.dayTrend.color(),
                fontSize = 12.sp,
            )
            Text(
                text = "持仓 ${item.totalProfitLabel} (${item.totalProfitPercentLabel})",
                color = item.totalTrend.color(),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
fun PreciseSellCandidateSection(
    candidates: List<SellCandidateUiModel>,
    selectedSymbol: String?,
    selectedMarket: Market?,
    onSelected: (SellCandidateUiModel) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceSecondary)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("选择持仓股票", color = ForegroundPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (candidates.isEmpty()) {
            Text("当前没有持仓记录，可在下方输入股票代码进行沽空。", color = ForegroundSecondary, fontSize = 13.sp)
        } else {
            candidates.forEach { candidate ->
                val isSelected = selectedSymbol.equals(candidate.symbol, ignoreCase = true) &&
                    selectedMarket == candidate.market
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) BackgroundPrimary else SurfaceMuted)
                        .border(
                            width = if (isSelected) 1.dp else 0.dp,
                            color = if (isSelected) SurfaceInverse else Color.Transparent,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .clickable { onSelected(candidate) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${candidate.name} ${candidate.symbol}",
                            color = ForegroundPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${candidate.market.label} · ${candidate.quantityLabel} · ${candidate.costLabel}",
                            color = ForegroundSecondary,
                            fontSize = 12.sp,
                        )
                    }

                    if (isSelected) {
                        PillLabel(
                            text = "已选择",
                            background = MarketUpSoft,
                            foreground = MarketUp,
                        )
                    }
                }
            }
        }
    }
}

private fun PriceTrend.color(): Color = when (this) {
    PriceTrend.UP -> MarketUp
    PriceTrend.DOWN -> com.recoder.stockledger.ui.theme.MarketDown
    PriceTrend.NEUTRAL -> MarketNeutral
}
