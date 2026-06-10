package com.recoder.stockledger.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recoder.stockledger.R
import com.recoder.stockledger.data.DisplayCurrency
import com.recoder.stockledger.data.BrokerPlatform
import com.recoder.stockledger.data.FeeEstimateStatus
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
import java.time.LocalDate

enum class TopLevelDestination(
    val label: String,
    val icon: ImageVector,
) {
    HOLDINGS("持仓", Icons.Filled.AccountBalanceWallet),
    ANALYSIS("盈亏", Icons.Filled.BarChart),
    OPERATIONS("操作", Icons.Filled.AddCircle),
    TRANSACTIONS("流水", Icons.AutoMirrored.Filled.List),
}

@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
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

        if (trailingContent != null) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                trailingContent()
            }
        } else {
            Spacer(modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun CurrencySelector(
    selected: DisplayCurrency,
    onSelected: (DisplayCurrency) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(SurfaceSecondary)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DisplayCurrency.entries.forEach { currency ->
            val isSelected = currency == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isSelected) BackgroundPrimary else Color.Transparent)
                    .clickable { onSelected(currency) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = currency.label,
                    color = if (isSelected) ForegroundPrimary else ForegroundSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
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
        RefreshState.FRESH -> "刚刚刷新"
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
    onDepositClick: () -> Unit,
    onWithdrawClick: () -> Unit,
    onTransferClick: () -> Unit,
    onInterestClick: () -> Unit,
    enabled: Boolean = true,
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
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilledActionButton(
                text = "入金",
                onClick = onDepositClick,
                modifier = Modifier.weight(1f),
            )
            OutlineActionButton(
                text = "出金",
                onClick = onWithdrawClick,
                modifier = Modifier.weight(1f),
            )
            OutlineActionButton(
                text = "转仓",
                onClick = onTransferClick,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlineActionButton(
                text = "融资利息",
                onClick = onInterestClick,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.weight(1f))
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
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(BackgroundPrimary)
            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) ForegroundPrimary else ForegroundMuted,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun PlatformTopBar(
    selectedPlatform: BrokerPlatform?,
    onClick: () -> Unit,
    onSettingsClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectedPlatform != null) {
                PlatformLogoBadge(
                    platform = selectedPlatform,
                    modifier = Modifier.size(42.dp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceSecondary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "汇",
                        color = ForegroundPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selectedPlatform?.label ?: "汇总",
                    color = ForegroundPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = "切换平台",
                    tint = ForegroundPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        if (onSettingsClick != null) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "设置",
                tint = ForegroundPrimary,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = onSettingsClick),
            )
        }
    }
}

@Composable
fun InlineCurrencyDropdown(
    title: String,
    selected: DisplayCurrency,
    onSelected: (DisplayCurrency) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.clickable { expanded = true },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$title(${selected.code})",
            color = ForegroundPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = "切换显示货币",
            tint = ForegroundPrimary,
            modifier = Modifier.size(18.dp),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(BackgroundPrimary),
        ) {
            DisplayCurrency.entries.forEach { currency ->
                DropdownMenuItem(
                    text = {
                        Text("${currency.label}(${currency.code})")
                    },
                    trailingIcon = if (currency == selected) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = ForegroundPrimary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        onSelected(currency)
                        expanded = false
                    },
                )
            }
        }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionRow(
    item: TransactionUiModel,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    showCheckbox: Boolean = false,
) {
    val (badgeBackground, badgeForeground) = tradeTypeColors(item.tradeType)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BackgroundPrimary)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (showCheckbox) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(
                    checkedColor = ForegroundPrimary,
                    uncheckedColor = ForegroundMuted,
                ),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlatformLogoBadge(
                    platform = item.platform,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = item.primaryMeta,
                    color = ForegroundSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 54.dp, height = 48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(badgeBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = item.tradeType.label,
                        color = badgeForeground,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = item.stockName,
                        color = ForegroundPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    item.secondaryMeta?.takeIf { it.isNotBlank() }?.let { secondaryMeta ->
                        Text(
                            text = secondaryMeta,
                            color = ForegroundMuted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = item.amountLabel,
                color = ForegroundPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = item.timeLabel,
                color = badgeForeground,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = item.feeLabel,
                color = ForegroundMuted,
                fontSize = 12.sp,
            )
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
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    trailingIcon: ImageVector? = null,
    supportingText: String? = null,
    supportingColor: Color = ForegroundMuted,
    placeholder: String = "请输入",
    isPassword: Boolean = false,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
) {
    Column(modifier = modifier) {
        if (label.isNotBlank()) {
            Text(label, color = ForegroundSecondary, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceSecondary)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EditableField(
                value = value,
                onValueChange = onValueChange,
                placeholder = placeholder,
                isPassword = isPassword,
                keyboardType = keyboardType,
                singleLine = singleLine,
                modifier = Modifier.weight(1f),
            )
            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = ForegroundSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if (supportingText != null) {
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
fun InputFieldBlock(
    label: String,
    value: String,
    trailingIcon: ImageVector? = null,
    supportingText: String? = null,
    supportingColor: Color = ForegroundMuted,
    placeholder: String = "请输入",
    isPassword: Boolean = false,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    onValueChange: ((String) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Column(modifier = modifier) {
        if (label.isNotBlank()) {
            Text(label, color = ForegroundSecondary, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceSecondary)
                .clickable(enabled = onClick != null, onClick = { onClick?.invoke() })
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onValueChange == null) {
                Text(
                    text = value.ifBlank { "请选择" },
                    color = if (value.isBlank()) ForegroundMuted else ForegroundPrimary,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f),
                )
            } else {
                EditableField(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = placeholder,
                    isPassword = isPassword,
                    keyboardType = keyboardType,
                    singleLine = singleLine,
                    modifier = Modifier.weight(1f),
                )
            }
            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = ForegroundSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if (supportingText != null) {
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
fun TradeEntryMarketSelector(
    selected: Market,
    onSelected: (Market) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SelectableBlock("A股", selected == Market.A_SHARE, Modifier.weight(1f)) { onSelected(Market.A_SHARE) }
        SelectableBlock("港股", selected == Market.HK, Modifier.weight(1f)) { onSelected(Market.HK) }
        SelectableBlock("美股", selected == Market.US, Modifier.weight(1f)) { onSelected(Market.US) }
    }
}

@Composable
fun TradeEntryFeeCard(
    commission: TextFieldValue,
    tax: TextFieldValue,
    feeEstimateStatus: FeeEstimateStatus,
    feeEstimateSummary: String?,
    feeEstimateDetail: String?,
    canAutoEstimateFees: Boolean,
    onCommissionChange: (TextFieldValue) -> Unit,
    onTaxChange: (TextFieldValue) -> Unit,
    onRecalculateFees: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceMuted)
            .border(1.dp, Color(0xFFECEEF2), RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("手续费 / 税费", color = ForegroundSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (canAutoEstimateFees) {
                Text(
                    text = if (feeEstimateStatus == FeeEstimateStatus.MANUAL_OVERRIDE) "恢复自动" else "重新计算",
                    color = ForegroundMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable(onClick = onRecalculateFees),
                )
            }
        }
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
        feeEstimateSummary?.takeIf { it.isNotBlank() }?.let { summary ->
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = summary,
                color = if (feeEstimateStatus == FeeEstimateStatus.MANUAL_OVERRIDE) ForegroundSecondary else ForegroundMuted,
                fontSize = 12.sp,
                fontWeight = if (feeEstimateStatus == FeeEstimateStatus.MANUAL_OVERRIDE) FontWeight.Medium else FontWeight.Normal,
            )
        }
        feeEstimateDetail?.takeIf { it.isNotBlank() }?.let { detail ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = detail,
                color = ForegroundMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
fun TradeEntryFeeCard(
    commission: String,
    tax: String,
    feeEstimateStatus: FeeEstimateStatus,
    feeEstimateSummary: String?,
    feeEstimateDetail: String?,
    canAutoEstimateFees: Boolean,
    onCommissionChange: (String) -> Unit,
    onTaxChange: (String) -> Unit,
    onRecalculateFees: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceMuted)
            .border(1.dp, Color(0xFFECEEF2), RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("手续费 / 税费", color = ForegroundSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (canAutoEstimateFees) {
                Text(
                    text = if (feeEstimateStatus == FeeEstimateStatus.MANUAL_OVERRIDE) "恢复自动" else "重新计算",
                    color = ForegroundMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable(onClick = onRecalculateFees),
                )
            }
        }
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
        feeEstimateSummary?.takeIf { it.isNotBlank() }?.let { summary ->
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = summary,
                color = if (feeEstimateStatus == FeeEstimateStatus.MANUAL_OVERRIDE) ForegroundSecondary else ForegroundMuted,
                fontSize = 12.sp,
                fontWeight = if (feeEstimateStatus == FeeEstimateStatus.MANUAL_OVERRIDE) FontWeight.Medium else FontWeight.Normal,
            )
        }
        feeEstimateDetail?.takeIf { it.isNotBlank() }?.let { detail ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = detail,
                color = ForegroundMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
fun TradeEntryNoteField(
    note: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
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
fun TradeEntryNoteField(
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
fun TradeEntryDateField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "交易日期",
) {
    val context = LocalContext.current
    val selectedDate = remember(value) {
        runCatching { LocalDate.parse(value) }.getOrNull() ?: LocalDate.now()
    }
    InputFieldBlock(
        label = label,
        value = value,
        modifier = modifier,
        trailingIcon = Icons.Filled.DateRange,
        onClick = {
            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    onValueChange(LocalDate.of(year, month + 1, dayOfMonth).toString())
                },
                selectedDate.year,
                selectedDate.monthValue - 1,
                selectedDate.dayOfMonth,
            ).show()
        },
    )
}
  
@Composable
fun TradeEntryTimeField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val selectedTime = remember(value) {
        runCatching { java.time.LocalTime.parse(value) }.getOrNull() ?: java.time.LocalTime.now()
    }
    InputFieldBlock(
        label = "交易时间",
        value = value,
        modifier = modifier,
        trailingIcon = Icons.Filled.KeyboardArrowDown,
        onClick = {
            TimePickerDialog(
                context,
                { _, hourOfDay, minuteOfHour ->
                    val formatted = String.format(java.util.Locale.US, "%02d:%02d:00", hourOfDay, minuteOfHour)
                    onValueChange(formatted)
                },
                selectedTime.hour,
                selectedTime.minute,
                true // is24HourView
            ).show()
        },
    )
}

@Composable
private fun EditableField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String = "请输入",
    isPassword: Boolean = false,
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
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        cursorBrush = SolidColor(ForegroundPrimary),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            keyboardType = keyboardType,
        ),
        decorationBox = { innerTextField ->
            if (value.text.isEmpty()) {
                Text(placeholder, color = ForegroundMuted, fontSize = 16.sp)
            }
            innerTextField()
        },
    )
}

@Composable
private fun EditableField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "请输入",
    isPassword: Boolean = false,
    keyboardType: KeyboardType,
    singleLine: Boolean,
    modifier: Modifier = Modifier,
) {
    var textFieldValue by remember(value == "") { 
        mutableStateOf(TextFieldValue(value, TextRange(value.length))) 
    }

    LaunchedEffect(value) {
        if (textFieldValue.text != value) {
            textFieldValue = textFieldValue.copy(
                text = value,
                selection = TextRange(value.length)
            )
        }
    }

    EditableField(
        value = textFieldValue,
        onValueChange = {
            textFieldValue = it
            if (value != it.text) {
                onValueChange(it.text)
            }
        },
        placeholder = placeholder,
        isPassword = isPassword,
        keyboardType = keyboardType,
        singleLine = singleLine,
        modifier = modifier,
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
        SelectableBlock("港股", selected == Market.HK, Modifier.weight(1f)) { onSelected(Market.HK) }
        SelectableBlock("美股", selected == Market.US, Modifier.weight(1f)) { onSelected(Market.US) }
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
                    color = tradeTypeColors(type).let { (_, foreground) ->
                        if (isSelected) foreground else ForegroundMuted
                    },
                    fontSize = 15.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}
fun tradeTypeColors(type: TradeType): Pair<Color, Color> = when (type) {
    TradeType.BUY, TradeType.DEPOSIT, TradeType.TRANSFER_IN -> MarketUpSoft to MarketUp
    TradeType.SELL, TradeType.WITHDRAW, TradeType.TRANSFER_OUT, TradeType.INTEREST -> MarketDownSoft to MarketDown
    TradeType.SPLIT -> SurfaceSecondary to MarketNeutral
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

@Composable
fun BrokerPlatformSelector(
    selected: BrokerPlatform,
    onSelected: (BrokerPlatform) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BrokerPlatform.configurableEntries.forEach { platform ->
            FilterChip(
                text = platform.label,
                selected = platform == selected,
                onClick = { onSelected(platform) },
            )
        }
    }
}

@Composable
fun PlatformLogoBadge(
    platform: BrokerPlatform,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    val logoRes = platformLogoRes(platform)
    val fullBleedLogo = logoRes != null
    Box(
        modifier = modifier
            .clip(shape)
            .background(BackgroundPrimary)
            .then(
                if (fullBleedLogo) {
                    Modifier
                } else {
                    Modifier
                        .border(1.dp, BorderSubtle, shape)
                        .padding(6.dp)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (logoRes != null) {
            Image(
                painter = painterResource(logoRes),
                contentDescription = platform.label,
                modifier = if (fullBleedLogo) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
                contentScale = if (fullBleedLogo) ContentScale.Crop else ContentScale.Fit,
            )
        } else {
            Text(
                text = platform.shortLabel,
                color = ForegroundPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun platformLogoRes(platform: BrokerPlatform): Int? = when (platform) {
    BrokerPlatform.UNSPECIFIED -> R.drawable.platform_east_money
    BrokerPlatform.ALIPAY -> R.drawable.platform_alipay
    BrokerPlatform.EAST_MONEY -> R.drawable.platform_east_money
    BrokerPlatform.LONGBRIDGE -> R.drawable.platform_longbridge
    BrokerPlatform.HSBC -> R.drawable.platform_hsbc
    BrokerPlatform.USMART -> R.drawable.platform_usmart
    BrokerPlatform.ZHUORUI -> R.drawable.platform_zhuorui
    BrokerPlatform.CHIEF -> R.drawable.platform_chief
    BrokerPlatform.SCHWAB -> R.drawable.platform_schwab
}
