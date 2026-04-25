package com.recoder.stockledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recoder.stockledger.data.HoldingUiModel
import com.recoder.stockledger.data.Market
import com.recoder.stockledger.data.PriceTrend
import com.recoder.stockledger.data.RefreshState
import com.recoder.stockledger.data.SecuritySuggestionUiModel
import com.recoder.stockledger.data.SellCandidateUiModel
import com.recoder.stockledger.data.TradeType
import com.recoder.stockledger.data.TransactionUiModel
import com.recoder.stockledger.ui.theme.BackgroundPrimary
import com.recoder.stockledger.ui.theme.BorderSubtle
import com.recoder.stockledger.ui.theme.ForegroundMuted
import com.recoder.stockledger.ui.theme.ForegroundPrimary
import com.recoder.stockledger.ui.theme.ForegroundSecondary
import com.recoder.stockledger.ui.theme.MarketDown
import com.recoder.stockledger.ui.theme.MarketDownSoft
import com.recoder.stockledger.ui.theme.MarketNeutral
import com.recoder.stockledger.ui.theme.MarketUp
import com.recoder.stockledger.ui.theme.MarketUpSoft
import com.recoder.stockledger.ui.theme.SurfaceInverse
import com.recoder.stockledger.ui.theme.SurfaceMuted
import com.recoder.stockledger.ui.theme.SurfaceSecondary

enum class TopLevelDestination(
    val label: String,
    val icon: ImageVector,
) {
    HOLDINGS("持仓", Icons.Filled.AccountBalanceWallet),
    TRANSACTIONS("流水", Icons.AutoMirrored.Filled.List),
}

@Composable
fun StatusBarStub() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "9:41",
            color = ForegroundPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, ForegroundMuted, RoundedCornerShape(8.dp)),
        )
    }
}

@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = ForegroundPrimary,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = onBack),
            )
        } else {
            Spacer(modifier = Modifier.size(24.dp))
        }

        Text(
            text = title,
            color = ForegroundPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(modifier = Modifier.size(24.dp))
    }
}

@Composable
fun SummaryMetric(
    label: String,
    value: String,
    hint: String,
    valueColor: Color = ForegroundPrimary,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(label, color = ForegroundSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(6.dp))
        Text(value, color = valueColor, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(hint, color = ForegroundMuted, fontSize = 12.sp)
    }
}

@Composable
fun RefreshStatusCard(
    state: RefreshState,
    message: String,
) {
    val accent = when (state) {
        RefreshState.IDLE -> ForegroundPrimary
        RefreshState.REFRESHING -> MarketUp
        RefreshState.FRESH -> MarketUp
        RefreshState.FAILED -> MarketNeutral
    }
    val badgeText = when (state) {
        RefreshState.IDLE -> "下拉刷新"
        RefreshState.REFRESHING -> "刷新中"
        RefreshState.FRESH -> "刚更新"
        RefreshState.FAILED -> "稍后重试"
    }
    val badgeBackground = when (state) {
        RefreshState.IDLE -> BackgroundPrimary
        RefreshState.REFRESHING -> MarketUpSoft
        RefreshState.FRESH -> MarketUpSoft
        RefreshState.FAILED -> SurfaceMuted
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceSecondary)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(state.title, color = ForegroundPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(message, color = ForegroundMuted, fontSize = 12.sp)
            }
        }

        PillLabel(
            text = badgeText,
            background = badgeBackground,
            foreground = accent,
        )
    }
}

@Composable
fun TradeActionButtons(
    onBuyClick: () -> Unit,
    onSellClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceSecondary)
            .padding(20.dp),
    ) {
        Text("快捷录入", color = ForegroundSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilledActionButton(
                text = "买入",
                onClick = onBuyClick,
                modifier = Modifier.weight(1f),
            )
            OutlineActionButton(
                text = "卖出",
                onClick = onSellClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun FilledActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) SurfaceInverse else ForegroundMuted)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = BackgroundPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun OutlineActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(BackgroundPrimary)
            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = ForegroundPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun HoldingsCard(
    item: HoldingUiModel,
    onDeleteClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, color = ForegroundPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "${item.code} · ${item.market.label} · ${item.quantityLabel} · ${item.costLabel}",
                color = ForegroundSecondary,
                fontSize = 12.sp,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(item.priceLabel, color = ForegroundPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = "${item.changeLabel} · ${item.pnlLabel}",
                color = item.trendColor(),
                fontSize = 12.sp,
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(MarketDownSoft)
                    .clickable(onClick = onDeleteClick)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "删除持仓",
                    tint = MarketDown,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "删除",
                    color = MarketDown,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
fun SellCandidateSection(
    candidates: List<SellCandidateUiModel>,
    selectedValue: String,
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
        Text("选择已买入股票", color = ForegroundPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (candidates.isEmpty()) {
            Text("当前没有可卖出的持仓，请先录入买入交易。", color = ForegroundMuted, fontSize = 13.sp)
        } else {
            candidates.forEach { candidate ->
                val isSelected = selectedValue.contains(candidate.symbol, ignoreCase = true) ||
                    selectedValue.contains(candidate.name, ignoreCase = true)
                SellCandidateRow(
                    candidate = candidate,
                    selected = isSelected,
                    onClick = { onSelected(candidate) },
                )
            }
        }
    }
}

@Composable
fun SellCandidateRow(
    candidate: SellCandidateUiModel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) BackgroundPrimary else SurfaceMuted)
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) SurfaceInverse else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
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

        if (selected) {
            PillLabel(
                text = "已选择",
                background = MarketUpSoft,
                foreground = MarketUp,
            )
        }
    }
}

@Composable
fun SymbolSuggestionSection(
    suggestions: List<SecuritySuggestionUiModel>,
    onSelected: (SecuritySuggestionUiModel) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceSecondary)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "候选股票",
            color = ForegroundSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        suggestions.forEach { suggestion ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BackgroundPrimary)
                    .clickable { onSelected(suggestion) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = suggestion.name,
                        color = ForegroundPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${suggestion.symbol} · ${suggestion.market.label}",
                        color = ForegroundSecondary,
                        fontSize = 12.sp,
                    )
                }

                PillLabel(
                    text = "补全",
                    background = SurfaceMuted,
                    foreground = ForegroundPrimary,
                )
            }
        }
    }
}

@Composable
fun TransactionRow(item: TransactionUiModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            PillLabel(
                text = item.tradeType.label,
                background = if (item.tradeType == TradeType.BUY) MarketUpSoft else MarketDownSoft,
                foreground = if (item.tradeType == TradeType.BUY) MarketUp else MarketDown,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(item.stockName, color = ForegroundPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(item.stockMeta, color = ForegroundSecondary, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(item.amountLabel, color = ForegroundPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                item.timeLabel,
                color = if (item.tradeType == TradeType.BUY) MarketUp else MarketDown,
                fontSize = 12.sp,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(item.feeLabel, color = ForegroundMuted, fontSize = 12.sp)
        }
    }
}

@Composable
fun PillLabel(
    text: String,
    background: Color,
    foreground: Color,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(text = text, color = foreground, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun FilterChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val background = when {
        selected -> SurfaceInverse
        text == "全部市场" -> Color(0xFFECEEF2)
        else -> BackgroundPrimary
    }
    val contentColor = if (selected) BackgroundPrimary else ForegroundPrimary

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .border(
                width = if (selected || text == "全部市场") 0.dp else 1.dp,
                color = BorderSubtle,
                shape = RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = contentColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun BottomPillNavigation(
    current: TopLevelDestination,
    onDestinationSelected: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 21.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(36.dp))
            .background(BackgroundPrimary)
            .border(1.dp, BorderSubtle, RoundedCornerShape(36.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TopLevelDestination.entries.forEach { destination ->
            val selected = destination == current
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(26.dp))
                    .background(if (selected) SurfaceInverse else Color.Transparent)
                    .clickable { onDestinationSelected(destination) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = destination.icon,
                    contentDescription = destination.label,
                    tint = if (selected) BackgroundPrimary else ForegroundMuted,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    destination.label,
                    color = if (selected) BackgroundPrimary else ForegroundMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
fun InputFieldBlock(
    label: String,
    value: String,
    trailingIcon: ImageVector? = null,
    supportingText: String? = null,
    supportingColor: Color = ForegroundMuted,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    onValueChange: ((String) -> Unit)? = null,
) {
    Column(modifier = modifier) {
        Text(label, color = ForegroundSecondary, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceSecondary)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onValueChange == null) {
                Text(value, color = ForegroundPrimary, fontSize = 16.sp)
            } else {
                EditableField(
                    value = value,
                    onValueChange = onValueChange,
                    keyboardType = keyboardType,
                    singleLine = singleLine,
                    modifier = Modifier.weight(1f),
                )
            }
            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(trailingIcon, contentDescription = null, tint = ForegroundMuted, modifier = Modifier.size(20.dp))
            }
        }
        if (!supportingText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = supportingText,
                color = supportingColor,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun EditableField(
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    singleLine: Boolean,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = singleLine,
        textStyle = TextStyle(
            color = ForegroundPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
        ),
        cursorBrush = SolidColor(ForegroundPrimary),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            keyboardType = keyboardType,
        ),
        decorationBox = { innerTextField ->
            if (value.isEmpty()) {
                Text("请输入", color = ForegroundMuted, fontSize = 16.sp)
            }
            innerTextField()
        },
    )
}

@Composable
fun MarketSelector(
    selected: Market,
    onSelected: (Market) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SelectableBlock("A股", selected == Market.A_SHARE, Modifier.weight(1f)) { onSelected(Market.A_SHARE) }
        SelectableBlock("港股", selected == Market.HONG_KONG, Modifier.weight(1f)) { onSelected(Market.HONG_KONG) }
    }
}

@Composable
fun TradeTypeSelector(
    selected: TradeType,
    onSelected: (TradeType) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceSecondary)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TradeType.entries.forEach { type ->
            val isSelected = type == selected
            val interactionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) BackgroundPrimary else Color.Transparent)
                    .clickable(interactionSource = interactionSource, indication = null) { onSelected(type) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = type.label,
                    color = if (type == TradeType.BUY) {
                        if (isSelected) MarketUp else ForegroundMuted
                    } else {
                        if (isSelected) MarketDown else ForegroundMuted
                    },
                    fontSize = 15.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
fun SelectableBlock(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) SurfaceInverse else BackgroundPrimary)
            .border(
                width = if (selected) 0.dp else 1.dp,
                color = BorderSubtle,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) BackgroundPrimary else ForegroundPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun FeeCard(
    commission: String,
    tax: String,
    onCommissionChange: (String) -> Unit,
    onTaxChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceMuted)
            .border(1.dp, Color(0xFFECEEF2), RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text("手续费 / 税费", color = ForegroundSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InputFieldBlock(
                label = "佣金",
                value = commission,
                onValueChange = onCommissionChange,
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
            InputFieldBlock(
                label = "税费",
                value = tax,
                onValueChange = onTaxChange,
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun NoteField(
    note: String,
    onValueChange: (String) -> Unit,
) {
    Column {
        Text("备注", color = ForegroundSecondary, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceSecondary)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            EditableField(
                value = note,
                onValueChange = onValueChange,
                keyboardType = KeyboardType.Text,
                singleLine = false,
            )
        }
    }
}

@Composable
fun DateInputField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    InputFieldBlock(
        label = "交易日期",
        value = value,
        trailingIcon = Icons.Filled.DateRange,
        onValueChange = onValueChange,
    )
}

private fun HoldingUiModel.trendColor(): Color = when (trend) {
    PriceTrend.UP -> MarketUp
    PriceTrend.DOWN -> MarketDown
    PriceTrend.NEUTRAL -> MarketNeutral
}
