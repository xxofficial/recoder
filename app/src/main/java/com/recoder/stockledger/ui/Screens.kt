package com.recoder.stockledger.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recoder.stockledger.data.BrokerPlatform
import com.recoder.stockledger.data.DisplayCurrency
import com.recoder.stockledger.data.HoldingUiModel
import com.recoder.stockledger.data.Market
import com.recoder.stockledger.data.MarketFilter
import com.recoder.stockledger.data.PortfolioSummary
import com.recoder.stockledger.data.RefreshState
import com.recoder.stockledger.data.SecuritySuggestionUiModel
import com.recoder.stockledger.data.SellCandidateUiModel
import com.recoder.stockledger.data.SymbolLookupState
import com.recoder.stockledger.data.SymbolLookupUiModel
import com.recoder.stockledger.data.TradeFormState
import com.recoder.stockledger.data.TradeType
import com.recoder.stockledger.data.TransactionFilter
import com.recoder.stockledger.data.TransactionSection
import com.recoder.stockledger.data.TransactionUiModel
import com.recoder.stockledger.data.ZhuoruiEmailManualSyncOptions
import com.recoder.stockledger.data.ZhuoruiEmailSyncConfig
import com.recoder.stockledger.data.local.LedgerEntity
import com.recoder.stockledger.ui.theme.BackgroundPrimary
import com.recoder.stockledger.ui.theme.ForegroundMuted
import com.recoder.stockledger.ui.theme.ForegroundPrimary
import com.recoder.stockledger.ui.theme.ForegroundSecondary
import com.recoder.stockledger.ui.theme.MarketDown
import com.recoder.stockledger.ui.theme.MarketUp
import com.recoder.stockledger.ui.theme.BorderSubtle
import com.recoder.stockledger.ui.theme.StockLedgerTheme
import com.recoder.stockledger.ui.theme.SurfaceSecondary
import java.time.LocalDate
import kotlin.math.absoluteValue


@Composable
fun JointSplitCard(
    contributions: List<PartnerContribution>,
    displayCurrency: DisplayCurrency,
    selectedPartner: String?,
    onPartnerClick: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = SurfaceSecondary,
                shape = RoundedCornerShape(16.dp),
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "合资出资及权益分摊",
                color = ForegroundPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Box(
                modifier = Modifier
                    .background(
                        color = androidx.compose.ui.graphics.Color(0xFFE5A93B).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = "合资模式",
                    color = androidx.compose.ui.graphics.Color(0xFFE5A93B),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            contributions.forEach { contribution ->
                val isSelected = selectedPartner == contribution.name
                val border = if (isSelected) {
                    androidx.compose.foundation.BorderStroke(1.5.dp, androidx.compose.ui.graphics.Color(0xFFE5A93B))
                } else {
                    null
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = BackgroundPrimary,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .let { 
                            if (border != null) it.border(border, RoundedCornerShape(12.dp)) else it
                        }
                        .clickable {
                            if (isSelected) {
                                onPartnerClick(null)
                            } else {
                                onPartnerClick(contribution.name)
                            }
                        }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = contribution.name,
                                color = ForegroundPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = ForegroundMuted.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(6.dp),
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = String.format("%.2f%%", contribution.ratio * 100),
                                    color = ForegroundSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = androidx.compose.ui.graphics.Color(0xFFE5A93B).copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp),
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = "当前视角",
                                        color = androidx.compose.ui.graphics.Color(0xFFE5A93B),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                        Text(
                            text = String.format("净入金: %s%,.2f", displayCurrency.symbol, contribution.netContributionCny),
                            color = ForegroundMuted,
                            fontSize = 12.sp,
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = String.format("权益市值: %s%,.2f", displayCurrency.symbol, contribution.assetsShareCny),
                            color = ForegroundPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "分配盈亏: ",
                                color = ForegroundMuted,
                                fontSize = 12.sp,
                            )
                            val pnl = contribution.pnlShareCny
                            val pnlText = if (pnl >= 0.0) {
                                String.format("+%s%,.2f", displayCurrency.symbol, pnl)
                            } else {
                                String.format("-%s%,.2f", displayCurrency.symbol, pnl.absoluteValue)
                            }
                            val pnlColor = if (pnl >= 0.0) MarketUp else MarketDown
                            Text(
                                text = pnlText,
                                color = pnlColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HoldingsRoute(
    summary: PortfolioSummary,
    displayCurrency: DisplayCurrency,
    holdings: List<HoldingUiModel>,
    selectedPlatform: BrokerPlatform?,
    activeLedgerType: String = "",
    partnerContributions: List<PartnerContribution> = emptyList(),
    selectedPartner: String? = null,
    onPartnerClick: (String?) -> Unit = {},
    onPlatformClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDisplayCurrencySelected: (DisplayCurrency) -> Unit,
    onRefresh: () -> Unit,
    onDestinationSelected: (TopLevelDestination) -> Unit,
    onHoldingClick: (HoldingUiModel) -> Unit = {},
) {
    val pullRefreshState = rememberPullRefreshState(
        refreshing = summary.refreshState == RefreshState.REFRESHING,
        onRefresh = onRefresh,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PlatformTopBar(
                selectedPlatform = selectedPlatform,
                onClick = onPlatformClick,
                onSettingsClick = onSettingsClick,
                modifier = Modifier.statusBarsPadding(),
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .pullRefresh(pullRefreshState)
                    .padding(start = 20.dp, end = 20.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "summary_header") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        InlineCurrencyDropdown(
                            title = "总资产",
                            selected = displayCurrency,
                            onSelected = onDisplayCurrencySelected,
                        )
                        Text(summary.totalAssets, color = ForegroundPrimary, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                        Row {
                            Text("今日盈亏 ", color = ForegroundMuted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(
                                summary.dayProfit,
                                color = if (summary.dayProfit.startsWith("-")) MarketDown else MarketUp,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                item(key = "summary_metrics") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = SurfaceSecondary,
                                shape = RoundedCornerShape(16.dp),
                            )
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            SummaryMetric(
                                label = "净入金",
                                value = summary.totalCost,
                                hint = summary.totalCostHint,
                                modifier = Modifier.weight(1f),
                            )
                            SummaryMetric(
                                label = "可用现金",
                                value = summary.cashBalance,
                                hint = summary.cashBalanceHint,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            SummaryMetric(
                                label = "持仓浮盈",
                                value = summary.totalProfit,
                                hint = summary.totalProfitHint,
                                valueColor = if (summary.totalProfit.startsWith("-")) MarketDown else MarketUp,
                                modifier = Modifier.weight(1f),
                            )
                            SummaryMetric(
                                label = "持仓总市值",
                                value = summary.holdingsValue,
                                hint = "按现价估算",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                if (activeLedgerType == "JOINT" && partnerContributions.isNotEmpty()) {
                    item(key = "joint_split_card") {
                        JointSplitCard(
                            contributions = partnerContributions,
                            displayCurrency = displayCurrency,
                            selectedPartner = selectedPartner,
                            onPartnerClick = onPartnerClick,
                        )
                    }
                }

                item(key = "trade_stats") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = SurfaceSecondary,
                                shape = RoundedCornerShape(16.dp),
                            )
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("交易统计", color = ForegroundPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            SummaryMetric(
                                label = "总手续费",
                                value = summary.totalFee,
                                hint = summary.totalFeeHint,
                                modifier = Modifier.weight(1f),
                            )
                            SummaryMetric(
                                label = "交易次数",
                                value = summary.tradeCount,
                                hint = summary.tradeCountHint,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                item(key = "holdings_title") {
                    Text("持仓列表", color = ForegroundPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }

                item(key = "holdings_list") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = SurfaceSecondary,
                                shape = RoundedCornerShape(16.dp),
                            )
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (holdings.isEmpty()) {
                            Text("当前范围内还没有持仓。", color = ForegroundMuted, fontSize = 14.sp)
                        } else {
                            holdings.forEach { item ->
                                EnhancedHoldingsCard(item = item, onClick = { onHoldingClick(item) })
                            }
                        }
                    }
                }
            }
        }

        PullRefreshIndicator(
            refreshing = summary.refreshState == RefreshState.REFRESHING,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = SurfaceSecondary,
            contentColor = ForegroundPrimary,
        )

        if (summary.showPullRefreshTime && !summary.refreshTimeLabel.isNullOrBlank()) {
            Text(
                text = "上次刷新 ${summary.refreshTimeLabel}",
                color = ForegroundSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 96.dp)
                    .background(
                        color = SurfaceSecondary,
                        shape = RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        BottomPillNavigation(
            current = TopLevelDestination.HOLDINGS,
            onDestinationSelected = onDestinationSelected,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
fun OperationsRoute(
    selectedPlatform: BrokerPlatform?,
    onPlatformClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onBuyClick: () -> Unit,
    onSellClick: () -> Unit,
    onDepositClick: () -> Unit,
    onWithdrawClick: () -> Unit,
    onTransferClick: () -> Unit = {},
    onInterestClick: () -> Unit,
    onDividendClick: () -> Unit = {},
    onTaxClick: () -> Unit = {},
    onExportBackupClick: () -> Unit,
    onImportBackupClick: () -> Unit,
    backupStatusMessage: String?,
    hsbcImportDraftText: String,
    hsbcImportStatusMessage: String?,
    onHsbcImportDraftTextChange: (String) -> Unit,
    onImportHsbcNotificationText: () -> Unit,
    zhuoruiEmailSyncConfig: ZhuoruiEmailSyncConfig,
    zhuoruiEmailManualSyncOptions: ZhuoruiEmailManualSyncOptions,
    zhuoruiEmailAutoImportEnabled: Boolean,
    zhuoruiEmailSyncStatusMessage: String?,
    onZhuoruiEmailSyncConfigChange: (ZhuoruiEmailSyncConfig) -> Unit,
    onZhuoruiEmailManualSyncOptionsChange: (ZhuoruiEmailManualSyncOptions) -> Unit,
    onSaveZhuoruiEmailSyncConfig: () -> Unit,
    onSyncZhuoruiMailboxNow: () -> Unit,
    onEnableZhuoruiEmailAutoImport: () -> Unit,
    onDisableZhuoruiEmailAutoImport: () -> Unit,
    pdfImportStatusMessage: String?,
    pdfImportProgressFraction: Float?,
    hasFailedPdfImports: Boolean,
    onImportPdfStatements: (BrokerPlatform) -> Unit,
    onRetryFailedPdfImport: (BrokerPlatform) -> Unit,
    onDestinationSelected: (TopLevelDestination) -> Unit,
    ledgers: List<LedgerEntity> = emptyList(),
    onSplitClick: () -> Unit = {},
    isSyncingSplits: Boolean = false,
    splitsSyncStatusMessage: String? = null,
    onSyncSplitsClick: () -> Unit = {},
    expiredOptions: List<HoldingUiModel> = emptyList(),
    isClearingExpiredOptions: Boolean = false,
    onClearExpiredOptions: () -> Unit = {},
) {
    var showZhuoruiManualSyncOptions by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PlatformTopBar(
                selectedPlatform = selectedPlatform,
                onClick = onPlatformClick,
                onSettingsClick = onSettingsClick,
                modifier = Modifier.statusBarsPadding(),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                if (expiredOptions.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = SurfaceSecondary,
                                shape = RoundedCornerShape(16.dp),
                            )
                            .border(
                                width = 1.dp,
                                color = MarketDown.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(16.dp),
                            )
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = "Expired Options",
                                tint = MarketDown,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "检测到已到期期权",
                                color = ForegroundPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Text(
                            text = "系统检测到有已到期的期权持仓。一键清理会自动生成“期权到期（EXPIRE）”流水，将持仓数量清零，剩余成本全部结转至已实现盈亏，不产生现金流量。",
                            color = ForegroundSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            expiredOptions.forEach { option ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(BackgroundPrimary, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = option.name,
                                            color = ForegroundPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = option.code,
                                            color = ForegroundMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Text(
                                        text = option.quantityLabel,
                                        color = ForegroundSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        FilledActionButton(
                            text = if (isClearingExpiredOptions) "正在清理中..." else "一键清理已到期持仓",
                            onClick = onClearExpiredOptions,
                            enabled = !isClearingExpiredOptions,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                TradeActionButtons(
                    onBuyClick = onBuyClick,
                    onSellClick = onSellClick,
                    onDepositClick = onDepositClick,
                    onWithdrawClick = onWithdrawClick,
                    onTransferClick = onTransferClick,
                    onInterestClick = onInterestClick,
                    onSplitClick = onSplitClick,
                    onDividendClick = onDividendClick,
                    onTaxClick = onTaxClick,
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = SurfaceSecondary,
                            shape = RoundedCornerShape(16.dp),
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("数据备份", color = ForegroundPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "导出会保存当前交易记录和当前全局平台选择；导入会覆盖本地交易数据并重新刷新行情。",
                        color = ForegroundSecondary,
                        fontSize = 13.sp,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FilledActionButton(
                            text = "导出备份",
                            onClick = onExportBackupClick,
                            modifier = Modifier.weight(1f),
                        )
                        OutlineActionButton(
                            text = "导入备份",
                            onClick = onImportBackupClick,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (!backupStatusMessage.isNullOrBlank()) {
                        Text(
                            text = backupStatusMessage,
                            color = ForegroundMuted,
                            fontSize = 12.sp,
                        )
                    }
                }

                if (selectedPlatform == BrokerPlatform.HSBC) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = SurfaceSecondary,
                                shape = RoundedCornerShape(16.dp),
                            )
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("汇丰短信导入", color = ForegroundPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "把汇丰成交短信完整复制到这里，点“解析导入”后会自动识别买入、卖出和撤销通知。",
                            color = ForegroundSecondary,
                            fontSize = 13.sp,
                        )
                        InputFieldBlock(
                            label = "短信文本",
                            value = hsbcImportDraftText,
                            placeholder = "粘贴汇丰短信全文",
                            singleLine = false,
                            supportingText = "支持直接粘贴短信 App 里的原文内容",
                            onValueChange = onHsbcImportDraftTextChange,
                        )
                        FilledActionButton(
                            text = "解析导入",
                            onClick = onImportHsbcNotificationText,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        hsbcImportStatusMessage?.takeIf { it.isNotBlank() }?.let { message ->
                            Text(
                                text = message,
                                color = ForegroundMuted,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }

                if (selectedPlatform == BrokerPlatform.ZHUORUI) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = SurfaceSecondary,
                                shape = RoundedCornerShape(16.dp),
                            )
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("卓锐邮箱自动导入", color = ForegroundPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("自动同步", color = ForegroundPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Switch(
                                checked = zhuoruiEmailAutoImportEnabled,
                                onCheckedChange = { checked ->
                                    if (checked) onEnableZhuoruiEmailAutoImport() else onDisableZhuoruiEmailAutoImport()
                                },
                            )
                        }
                        if (!zhuoruiEmailSyncStatusMessage.isNullOrBlank()) {
                            Text(
                                text = zhuoruiEmailSyncStatusMessage,
                                color = ForegroundMuted,
                                fontSize = 12.sp,
                            )
                        }
                        InputFieldBlock(
                            label = "IMAP 地址",
                            value = zhuoruiEmailSyncConfig.imapHost,
                            placeholder = "例如 imap.qq.com",
                            onValueChange = { value ->
                                onZhuoruiEmailSyncConfigChange(zhuoruiEmailSyncConfig.copy(imapHost = value))
                            },
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            InputFieldBlock(
                                label = "端口",
                                value = zhuoruiEmailSyncConfig.imapPort,
                                placeholder = "993",
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f),
                                onValueChange = { value ->
                                    onZhuoruiEmailSyncConfigChange(zhuoruiEmailSyncConfig.copy(imapPort = value))
                                },
                            )
                            InputFieldBlock(
                                label = "文件夹",
                                value = zhuoruiEmailSyncConfig.folder,
                                placeholder = "INBOX",
                                modifier = Modifier.weight(1f),
                                onValueChange = { value ->
                                    onZhuoruiEmailSyncConfigChange(zhuoruiEmailSyncConfig.copy(folder = value))
                                },
                            )
                        }
                        InputFieldBlock(
                            label = "邮箱账号",
                            value = zhuoruiEmailSyncConfig.account,
                            placeholder = "your@mail.com",
                            onValueChange = { value ->
                                onZhuoruiEmailSyncConfigChange(zhuoruiEmailSyncConfig.copy(account = value))
                            },
                        )
                        InputFieldBlock(
                            label = "授权码 / 密码",
                            value = zhuoruiEmailSyncConfig.password,
                            placeholder = "建议填写邮箱 IMAP 授权码",
                            isPassword = true,
                            keyboardType = KeyboardType.Password,
                            onValueChange = { value ->
                                onZhuoruiEmailSyncConfigChange(zhuoruiEmailSyncConfig.copy(password = value))
                            },
                        )
                        // 目标账本选择
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("默认导入目标账本", color = ForegroundSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            var expanded by remember { mutableStateOf(false) }
                            val selectedLedger = ledgers.firstOrNull { it.id == zhuoruiEmailSyncConfig.targetLedgerId } ?: ledgers.firstOrNull()
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = SurfaceSecondary,
                                        shape = RoundedCornerShape(12.dp),
                                    )
                                    .clickable { expanded = true }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = selectedLedger?.name ?: "默认个人账本",
                                        color = ForegroundPrimary,
                                        fontSize = 15.sp,
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.ArrowDropDown,
                                        contentDescription = "选择导入目标账本",
                                        tint = ForegroundMuted,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                    modifier = Modifier.background(SurfaceSecondary)
                                ) {
                                    ledgers.forEach { ledger ->
                                        DropdownMenuItem(
                                            text = { Text(ledger.name, color = ForegroundPrimary) },
                                            onClick = {
                                                onZhuoruiEmailSyncConfigChange(zhuoruiEmailSyncConfig.copy(targetLedgerId = ledger.id))
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text("高级同步选项", color = ForegroundSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "仅对本次“立即同步”生效，不会保存，也不会影响后台自动同步。",
                                        color = ForegroundMuted,
                                        fontSize = 12.sp,
                                    )
                                }
                                Icon(
                                    imageVector = if (showZhuoruiManualSyncOptions) {
                                        Icons.Filled.KeyboardArrowUp
                                    } else {
                                        Icons.Filled.KeyboardArrowDown
                                    },
                                    contentDescription = if (showZhuoruiManualSyncOptions) "收起高级同步选项" else "展开高级同步选项",
                                    tint = ForegroundMuted,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable { showZhuoruiManualSyncOptions = !showZhuoruiManualSyncOptions },
                                )
                            }

                            if (showZhuoruiManualSyncOptions) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    InputFieldBlock(
                                        label = "拉取封数",
                                        value = zhuoruiEmailManualSyncOptions.fetchCount,
                                        placeholder = "80",
                                        keyboardType = KeyboardType.Number,
                                        supportingText = "建议 50 - 200",
                                        modifier = Modifier.weight(1f),
                                        onValueChange = { value ->
                                            onZhuoruiEmailManualSyncOptionsChange(
                                                zhuoruiEmailManualSyncOptions.copy(fetchCount = value),
                                            )
                                        },
                                    )
                                    ManualSyncDateField(
                                        label = "最早到达日期",
                                        value = zhuoruiEmailManualSyncOptions.earliestReceivedAt,
                                        modifier = Modifier.weight(1f),
                                        onValueChange = { value ->
                                            onZhuoruiEmailManualSyncOptionsChange(
                                                zhuoruiEmailManualSyncOptions.copy(earliestReceivedAt = value),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlineActionButton(
                                text = "保存配置",
                                onClick = onSaveZhuoruiEmailSyncConfig,
                                modifier = Modifier.weight(1f),
                            )
                            FilledActionButton(
                                text = "立即同步",
                                onClick = onSyncZhuoruiMailboxNow,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                if (selectedPlatform?.supportsPdfImport == true) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = SurfaceSecondary,
                                shape = RoundedCornerShape(16.dp),
                            )
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("电子结单导入", color = ForegroundPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "支持导入${selectedPlatform.label}的电子结单（PDF格式），结单密码和解析方式在设置中维护。",
                            color = ForegroundSecondary,
                            fontSize = 13.sp,
                        )
                        FilledActionButton(
                            text = "选择PDF文件导入",
                            onClick = { onImportPdfStatements(selectedPlatform) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        pdfImportProgressFraction?.let { fraction ->
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            )
                        }
                        pdfImportStatusMessage?.takeIf { it.isNotBlank() }?.let { message ->
                            Text(
                                text = message,
                                color = ForegroundMuted,
                                fontSize = 12.sp,
                            )
                            if (hasFailedPdfImports) {
                                OutlineActionButton(
                                    text = "重试失败文件",
                                    onClick = { onRetryFailedPdfImport(selectedPlatform) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }

                // Stock splits sync section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = SurfaceSecondary,
                            shape = RoundedCornerShape(16.dp),
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("持仓股票拆折算同步", color = ForegroundPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "如果你的持仓股票发生过拆股或并股（例如SNXX1、ZSL等），可以联网自动同步并自动生成折算交易记录，修正持仓数量与均价。",
                        color = ForegroundSecondary,
                        fontSize = 13.sp,
                    )

                    if (splitsSyncStatusMessage != null) {
                        Text(
                            text = splitsSyncStatusMessage,
                            color = ForegroundMuted,
                            fontSize = 13.sp,
                        )
                    }

                    FilledActionButton(
                        text = if (isSyncingSplits) "同步中..." else "立即同步折算记录",
                        onClick = onSyncSplitsClick,
                        enabled = !isSyncingSplits,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        BottomPillNavigation(
            current = TopLevelDestination.OPERATIONS,
            onDestinationSelected = onDestinationSelected,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsRoute(
    sections: List<TransactionSection>,
    selectedPlatform: BrokerPlatform?,
    selectedTradeFilter: TransactionFilter,
    selectedMarketFilter: MarketFilter,
    transactionKeyword: String,
    transactionDateStart: String,
    transactionDateEnd: String,
    onTradeFilterSelected: (TransactionFilter) -> Unit,
    onMarketFilterSelected: (MarketFilter) -> Unit,
    onTransactionKeywordChange: (String) -> Unit,
    onTransactionDateRangeChange: (String?, String?) -> Unit,
    onResetFilters: () -> Unit,
    onEditTradeClick: (Long) -> Unit,
    onPlatformClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDestinationSelected: (TopLevelDestination) -> Unit,
    batchSelectionMode: Boolean = false,
    selectedTransactionIds: Set<Long> = emptySet(),
    onEnterBatchMode: () -> Unit = {},
    onExitBatchMode: () -> Unit = {},
    onToggleSelection: (Long) -> Unit = {},
    onSelectAll: (List<Long>) -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    ledgers: List<LedgerEntity> = emptyList(),
    activeLedgerId: Long = 1L,
    onMoveTransactionsToLedger: (Long) -> Unit = {},
) {
    BackHandler(enabled = batchSelectionMode) {
        onExitBatchMode()
    }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var draftTradeFilter by remember { mutableStateOf(selectedTradeFilter) }
    var draftMarketFilter by remember { mutableStateOf(selectedMarketFilter) }
    var draftStartDate by remember { mutableStateOf(transactionDateStart) }
    var draftEndDate by remember { mutableStateOf(transactionDateEnd) }

    val hasActiveFilters = selectedTradeFilter != TransactionFilter.ALL ||
        selectedMarketFilter != MarketFilter.ALL ||
        transactionKeyword.isNotBlank() ||
        transactionDateStart.isNotBlank() ||
        transactionDateEnd.isNotBlank()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PlatformTopBar(
                selectedPlatform = selectedPlatform,
                onClick = onPlatformClick,
                onSettingsClick = onSettingsClick,
                modifier = Modifier.statusBarsPadding(),
            )

            // Sticky Search/Batch Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp)
            ) {
                if (batchSelectionMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val allIds = sections.flatMap { section -> section.items.map { it.id } }
                        val allSelected = allIds.isNotEmpty() && allIds.all { it in selectedTransactionIds }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (allSelected) onExitBatchMode() else onSelectAll(allIds)
                                }
                                .background(SurfaceSecondary)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = if (allSelected) "取消全选" else "全选",
                                color = ForegroundPrimary,
                                fontSize = 13.sp,
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "已选 ${selectedTransactionIds.size} 笔",
                            color = ForegroundMuted,
                            fontSize = 13.sp,
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onExitBatchMode() }
                                .background(SurfaceSecondary)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text("取消", color = ForegroundPrimary, fontSize = 13.sp)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InputFieldBlock(
                            label = "",
                            value = transactionKeyword,
                            placeholder = "搜索证券名称或代码",
                            modifier = Modifier.weight(1f),
                            onValueChange = onTransactionKeywordChange,
                        )
                        FilterActionButton(
                            active = hasActiveFilters,
                            onClick = {
                                draftTradeFilter = selectedTradeFilter
                                draftMarketFilter = selectedMarketFilter
                                draftStartDate = transactionDateStart
                                draftEndDate = transactionDateEnd
                                showFilterSheet = true
                            },
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onEnterBatchMode() }
                                .background(SurfaceSecondary)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text("批量", color = ForegroundPrimary, fontSize = 13.sp)
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 20.dp, end = 20.dp),
                contentPadding = PaddingValues(top = 6.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                if (sections.isEmpty()) {
                    item(key = "empty_state") {
                        Text("当前条件下没有流水记录。", color = ForegroundMuted, fontSize = 14.sp)
                    }
                } else {
                    sections.forEach { section ->
                        item(key = "title_${section.title}") {
                            Text(
                                text = section.title,
                                color = ForegroundSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        item(key = "group_${section.title}") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = SurfaceSecondary,
                                        shape = RoundedCornerShape(16.dp),
                                    )
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                section.items.forEach { item ->
                                    TransactionRow(
                                        item = item,
                                        onClick = {
                                            if (batchSelectionMode) {
                                                onToggleSelection(item.id)
                                            } else {
                                                onEditTradeClick(item.id)
                                            }
                                        },
                                        onLongClick = {
                                            if (!batchSelectionMode) {
                                                onEnterBatchMode()
                                                onToggleSelection(item.id)
                                            } else {
                                                onToggleSelection(item.id)
                                            }
                                        },
                                        isSelected = item.id in selectedTransactionIds,
                                        showCheckbox = batchSelectionMode,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        var showMoveLedgerDialog by remember { mutableStateOf(false) }

        if (batchSelectionMode && selectedTransactionIds.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 90.dp, start = 20.dp, end = 20.dp)
                    .fillMaxWidth()
                    .background(
                        color = SurfaceSecondary,
                        shape = RoundedCornerShape(20.dp),
                    )
                    .padding(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showBatchDeleteDialog = true }
                            .background(MarketDown.copy(alpha = 0.15f))
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "删除所选 (${selectedTransactionIds.size})",
                            color = MarketDown,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showMoveLedgerDialog = true }
                            .background(BackgroundPrimary)
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "变更账本",
                            color = ForegroundPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        if (showMoveLedgerDialog) {
            AlertDialog(
                onDismissRequest = { showMoveLedgerDialog = false },
                title = { Text("迁移交易至账本", color = ForegroundPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("请选择要将选中的 ${selectedTransactionIds.size} 笔交易记录迁移到哪个账本：", color = ForegroundSecondary, fontSize = 13.sp)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                        ) {
                            ledgers.forEach { ledger ->
                                val isCurrent = ledger.id == activeLedgerId
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            color = if (isCurrent) SurfaceSecondary else BackgroundPrimary,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            if (!isCurrent) {
                                                onMoveTransactionsToLedger(ledger.id)
                                                showMoveLedgerDialog = false
                                            }
                                        }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(ledger.name, color = if (isCurrent) ForegroundMuted else ForegroundPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        val typeLabel = when (ledger.type) {
                                            "PERSONAL" -> "个人"
                                            "JOINT" -> "合资"
                                            "MANAGED" -> "代操"
                                            else -> ledger.type
                                        }
                                        Text(typeLabel, color = ForegroundMuted, fontSize = 11.sp)
                                    }
                                    if (isCurrent) {
                                        Text("当前账本", color = ForegroundMuted, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showMoveLedgerDialog = false }) {
                        Text("取消", color = ForegroundSecondary)
                    }
                }
            )
        }

        BottomPillNavigation(
            current = TopLevelDestination.TRANSACTIONS,
            onDestinationSelected = onDestinationSelected,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (showBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text("批量删除") },
            text = { Text("确认删除选中的 ${selectedTransactionIds.size} 笔记录？删除后无法恢复。") },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            showBatchDeleteDialog = false
                            onDeleteSelected()
                        }
                        .background(MarketDown)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text("删除", color = BackgroundPrimary, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showBatchDeleteDialog = false }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text("取消", color = ForegroundPrimary)
                }
            },
        )
    }

    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            containerColor = BackgroundPrimary,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("筛选流水", color = ForegroundPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("交易类型", color = ForegroundSecondary, fontSize = 13.sp)
                    FilterChipWrapRow(
                        options = TransactionFilter.entries,
                        selected = draftTradeFilter,
                        label = { it.label },
                        onSelected = { draftTradeFilter = it },
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("市场", color = ForegroundSecondary, fontSize = 13.sp)
                    FilterChipWrapRow(
                        options = MarketFilter.entries,
                        selected = draftMarketFilter,
                        label = { it.label },
                        onSelected = { draftMarketFilter = it },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilterDateField(
                        label = "开始日期",
                        value = draftStartDate,
                        modifier = Modifier.weight(1f),
                        onValueChange = { draftStartDate = it },
                    )
                    FilterDateField(
                        label = "结束日期",
                        value = draftEndDate,
                        modifier = Modifier.weight(1f),
                        onValueChange = { draftEndDate = it },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlineActionButton(
                        text = "重置条件",
                        onClick = {
                            onResetFilters()
                            showFilterSheet = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                    FilledActionButton(
                        text = "确定",
                        onClick = {
                            val (normalizedStart, normalizedEnd) = normalizeDateRange(draftStartDate, draftEndDate)
                            onTradeFilterSelected(draftTradeFilter)
                            onMarketFilterSelected(draftMarketFilter)
                            onTransactionDateRangeChange(normalizedStart, normalizedEnd)
                            showFilterSheet = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Stable
class InputFieldState(initialValue: String) {
    var textFieldValue by mutableStateOf(TextFieldValue(initialValue, TextRange(initialValue.length)))
        private set
    private val sentHistory = mutableListOf<String>()
    private var preProgrammaticValue: String? = null

    fun updateFromUser(newValue: TextFieldValue, onValueChange: (String) -> Unit) {
        val text = newValue.text
        if (preProgrammaticValue != null && text == preProgrammaticValue) {
            return
        }
        preProgrammaticValue = null

        val textChanged = text != textFieldValue.text
        textFieldValue = newValue
        if (textChanged) {
            if (sentHistory.isEmpty() || sentHistory.last() != text) {
                sentHistory.add(text)
                if (sentHistory.size > 15) {
                    sentHistory.removeAt(0)
                }
            }
            onValueChange(text)
        }
    }

    fun updateFromState(stateValue: String) {
        if (stateValue != textFieldValue.text) {
            if (stateValue in sentHistory) {
                return
            }
            if (textFieldValue.text.isNotEmpty()) {
                preProgrammaticValue = textFieldValue.text
            }
            textFieldValue = TextFieldValue(stateValue, TextRange(stateValue.length))
            sentHistory.clear()
            sentHistory.add(stateValue)
        }
    }
}

@Composable
fun TradeEntryRoute(
    state: TradeFormState,
    isEditing: Boolean,
    displayCurrency: DisplayCurrency,
    availablePlatforms: List<BrokerPlatform>,
    sellCandidates: List<SellCandidateUiModel>,
    symbolLookup: SymbolLookupUiModel,
    symbolSuggestions: List<SecuritySuggestionUiModel>,
    canSubmit: Boolean,
    validationMessage: String?,
    onBackClick: () -> Unit,
    onTradePlatformSelected: (BrokerPlatform) -> Unit,
    onSellCandidateSelected: (SellCandidateUiModel) -> Unit,
    onSymbolSuggestionSelected: (SecuritySuggestionUiModel) -> Unit,
    onMarketSelected: (Market) -> Unit,
    onCashCurrencySelected: (DisplayCurrency) -> Unit,
    onSymbolChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onTimeChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onQuantityChange: (String) -> Unit,
    onCommissionChange: (String) -> Unit,
    onTaxChange: (String) -> Unit,
    onRecalculateFees: () -> Unit,
    onNoteChange: (String) -> Unit,
    onDeleteTradeClick: (() -> Unit)? = null,
    onSubmit: () -> Unit,
    activeLedgerType: String = "",
    activeLedgerPartners: String = "",
    onInvestorSelected: ((String?) -> Unit)? = null,
    onAssetTypeSelected: ((String) -> Unit)? = null,
    onOptionUnderlyingSymbolChanged: ((String) -> Unit)? = null,
    onOptionExpiryDateChanged: ((String) -> Unit)? = null,
    onOptionTypeSelected: ((String) -> Unit)? = null,
    onOptionStrikePriceChanged: ((String) -> Unit)? = null,
    onOptionUnderlyingSuggestionSelected: ((SecuritySuggestionUiModel) -> Unit)? = null,
) {
    val symbolState = remember { InputFieldState(state.symbolOrName) }
    val priceState = remember { InputFieldState(state.priceLabel) }
    val quantityState = remember { InputFieldState(state.quantityLabel) }
    val commissionState = remember { InputFieldState(state.commissionLabel) }
    val taxState = remember { InputFieldState(state.taxLabel) }
    val timeState = remember { InputFieldState(state.tradeTime) }
    val noteState = remember { InputFieldState(state.note) }
    val optionUnderlyingState = remember { InputFieldState(state.optionUnderlyingSymbol) }
    val optionStrikeState = remember { InputFieldState(state.optionStrikePriceLabel) }

    LaunchedEffect(state.symbolOrName) { symbolState.updateFromState(state.symbolOrName) }
    LaunchedEffect(state.priceLabel) { priceState.updateFromState(state.priceLabel) }
    LaunchedEffect(state.quantityLabel) { quantityState.updateFromState(state.quantityLabel) }
    LaunchedEffect(state.commissionLabel) { commissionState.updateFromState(state.commissionLabel) }
    LaunchedEffect(state.taxLabel) { taxState.updateFromState(state.taxLabel) }
    LaunchedEffect(state.tradeTime) { timeState.updateFromState(state.tradeTime) }
    LaunchedEffect(state.note) { noteState.updateFromState(state.note) }
    LaunchedEffect(state.optionUnderlyingSymbol) { optionUnderlyingState.updateFromState(state.optionUnderlyingSymbol) }
    LaunchedEffect(state.optionStrikePriceLabel) { optionStrikeState.updateFromState(state.optionStrikePriceLabel) }

    val isSecurityTrade = state.selectedType.isSecurityTrade
    val isSellTrade = state.selectedType == TradeType.SELL
    val (tradeTypeBadgeBackground, tradeTypeBadgeForeground) = tradeTypeColors(state.selectedType)
    var showDeleteDialog by remember { mutableStateOf(false) }
    val cashCurrency = when (state.market) {
        Market.US -> DisplayCurrency.USD
        Market.HK -> DisplayCurrency.HKD
        else -> DisplayCurrency.CNY
    }
    val cashCurrencyCode = cashCurrency.code
    val cashCurrencyLabel = cashCurrency.label

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(BackgroundPrimary),
    ) {
        ScreenHeader(
            title = if (isEditing) "编辑记录" else "录入交易",
            onBack = onBackClick,
            trailingContent = if (isEditing && onDeleteTradeClick != null) {
                {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "筛选",
                        tint = MarketDown,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable { showDeleteDialog = true },
                    )
                }
            } else {
                null
            },
        )

        Box(
            modifier = Modifier.weight(1f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 132.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("交易类型", color = ForegroundSecondary, fontSize = 14.sp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = SurfaceSecondary,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PillLabel(
                            text = state.selectedType.label,
                            background = tradeTypeBadgeBackground,
                            foreground = tradeTypeBadgeForeground,
                        )
                    }
                }

                PlatformDropdownField(
                    selectedPlatform = state.platform,
                    availablePlatforms = availablePlatforms,
                    onSelected = onTradePlatformSelected,
                )

                if (isSecurityTrade) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("市场", color = ForegroundSecondary, fontSize = 14.sp)
                        TradeEntryMarketSelector(
                            selected = state.market,
                            onSelected = onMarketSelected,
                        )
                    }
                }

                if (isSecurityTrade) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("资产类型", color = ForegroundSecondary, fontSize = 14.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("STOCK" to "股票", "OPTION" to "期权").forEach { (typeVal, typeLbl) ->
                                val selected = state.assetType == typeVal
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            color = if (selected) SurfaceSecondary else BackgroundPrimary,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .border(
                                            width = if (selected) 0.dp else 1.dp,
                                            color = BorderSubtle,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { onAssetTypeSelected?.invoke(typeVal) }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = typeLbl,
                                        color = if (selected) ForegroundPrimary else ForegroundSecondary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }
                }

                if (isSecurityTrade) {
                    if (state.assetType == "OPTION") {
                        InputFieldBlock(
                            label = "正股代码",
                            value = optionUnderlyingState.textFieldValue,
                            supportingText = symbolLookup.message,
                            supportingColor = when (symbolLookup.state) {
                                SymbolLookupState.INVALID -> MarketDown
                                SymbolLookupState.RESOLVED -> MarketUp
                                else -> ForegroundMuted
                            },
                            onValueChange = {
                                optionUnderlyingState.updateFromUser(it) { text ->
                                    onOptionUnderlyingSymbolChanged?.invoke(text)
                                }
                            },
                        )
                        if (symbolSuggestions.isNotEmpty()) {
                            SymbolSuggestionSection(
                                suggestions = symbolSuggestions,
                                onSelected = onOptionUnderlyingSuggestionSelected ?: {},
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            TradeEntryDateField(
                                label = "到期日",
                                value = state.optionExpiryDate,
                                onValueChange = { onOptionExpiryDateChanged?.invoke(it) },
                                modifier = Modifier.weight(1f),
                            )
                            InputFieldBlock(
                                label = "行权价",
                                value = optionStrikeState.textFieldValue,
                                modifier = Modifier.weight(1f),
                                keyboardType = KeyboardType.Decimal,
                                onValueChange = {
                                    optionStrikeState.updateFromUser(it) { text ->
                                        onOptionStrikePriceChanged?.invoke(text)
                                    }
                                },
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("期权类型", color = ForegroundSecondary, fontSize = 14.sp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("CALL" to "看涨 Call", "PUT" to "看跌 Put").forEach { (typeVal, typeLbl) ->
                                    val selected = state.optionType == typeVal
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(
                                                color = if (selected) SurfaceSecondary else BackgroundPrimary,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .border(
                                                width = if (selected) 0.dp else 1.dp,
                                                color = BorderSubtle,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable { onOptionTypeSelected?.invoke(typeVal) }
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = typeLbl,
                                            color = if (selected) ForegroundPrimary else ForegroundSecondary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        InputFieldBlock(
                            label = "证券代码 / 名称",
                            value = symbolState.textFieldValue,
                            supportingText = symbolLookup.message,
                            supportingColor = when (symbolLookup.state) {
                                SymbolLookupState.INVALID -> MarketDown
                                SymbolLookupState.RESOLVED -> MarketUp
                                else -> ForegroundMuted
                            },
                            onValueChange = {
                                symbolState.updateFromUser(it) { text ->
                                    onSymbolChange(text)
                                }
                            },
                        )
                        if (symbolSuggestions.isNotEmpty()) {
                            SymbolSuggestionSection(
                                suggestions = symbolSuggestions,
                                onSelected = onSymbolSuggestionSelected,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    TradeEntryDateField(
                        value = state.tradeDate,
                        onValueChange = onDateChange,
                        modifier = Modifier.weight(1f),
                    )
                    TradeEntryTimeField(
                        value = timeState.textFieldValue.text,
                        onValueChange = { filtered ->
                            timeState.updateFromUser(TextFieldValue(filtered, selection = TextRange(filtered.length))) { text ->
                                onTimeChange(text)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                if (isSecurityTrade) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        InputFieldBlock(
                            label = if (state.selectedType == TradeType.SPLIT) "折算比例" else "成交价格",
                            value = priceState.textFieldValue,
                            modifier = if (state.selectedType == TradeType.SPLIT) Modifier.fillMaxWidth() else Modifier.weight(1f),
                            keyboardType = KeyboardType.Decimal,
                            onValueChange = {
                                priceState.updateFromUser(it) { text ->
                                    onPriceChange(text)
                                }
                            },
                        )
                        if (state.selectedType != TradeType.SPLIT) {
                            InputFieldBlock(
                                label = "成交数量",
                                value = quantityState.textFieldValue,
                                modifier = Modifier.weight(1f),
                                keyboardType = KeyboardType.Number,
                                onValueChange = {
                                    quantityState.updateFromUser(it) { text ->
                                        onQuantityChange(text)
                                    }
                                },
                            )
                        }
                    }
                } else {
                    val partners = remember(activeLedgerPartners) {
                        activeLedgerPartners.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    }
                    LaunchedEffect(partners, state.investorName, isEditing) {
                        if (activeLedgerType == "JOINT" && state.investorName == null && partners.isNotEmpty() && !isEditing) {
                            onInvestorSelected?.invoke(partners.first())
                        }
                    }

                    if (activeLedgerType == "JOINT" && (state.selectedType == TradeType.DEPOSIT || state.selectedType == TradeType.WITHDRAW)) {
                        if (partners.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val label = if (state.selectedType == TradeType.DEPOSIT) "出资人 (入金人)" else "撤资人 (出金人)"
                                Text(label, color = ForegroundSecondary, fontSize = 14.sp)
                                var expanded by remember { mutableStateOf(false) }
                                val currentInvestor = state.investorName?.trim()?.takeIf { it in partners } ?: partners.firstOrNull().orEmpty()
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            color = SurfaceSecondary,
                                            shape = RoundedCornerShape(12.dp),
                                        )
                                        .clickable { expanded = true }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = currentInvestor,
                                            color = ForegroundPrimary,
                                            fontSize = 15.sp,
                                        )
                                        Icon(
                                            imageVector = Icons.Filled.ArrowDropDown,
                                            contentDescription = "选择出资人",
                                            tint = ForegroundMuted,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    
                                    DropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false },
                                        modifier = Modifier.background(SurfaceSecondary)
                                    ) {
                                        partners.forEach { partner ->
                                            DropdownMenuItem(
                                                text = { Text(partner, color = ForegroundPrimary) },
                                                onClick = {
                                                    onInvestorSelected?.invoke(partner)
                                                    expanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("货币种类", color = ForegroundSecondary, fontSize = 14.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            DisplayCurrency.entries.forEach { currency ->
                                FilterChip(
                                    text = currency.label,
                                    selected = cashCurrency == currency,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onCashCurrencySelected(currency) },
                                )
                            }
                        }
                    }
                    InputFieldBlock(
                        label = when (state.selectedType) {
                            TradeType.DEPOSIT -> "入金金额 ($cashCurrencyCode)"
                            TradeType.INTEREST -> "利息金额 ($cashCurrencyCode)"
                            else -> "出金金额 ($cashCurrencyCode)"
                        },
                        value = priceState.textFieldValue,
                        supportingText = "当前按${cashCurrencyLabel}录入，保存后会自动折算到资产汇总。",
                        keyboardType = KeyboardType.Decimal,
                        onValueChange = {
                            priceState.updateFromUser(it) { text ->
                                onPriceChange(text)
                            }
                        },
                    )
                }

                if (isSecurityTrade && state.selectedType != TradeType.SPLIT) {
                    TradeEntryFeeCard(
                        commission = commissionState.textFieldValue,
                        tax = taxState.textFieldValue,
                        feeEstimateStatus = state.feeEstimateStatus,
                        feeEstimateSummary = state.feeEstimateSummary,
                        feeEstimateDetail = state.feeEstimateDetail,
                        canAutoEstimateFees = state.canAutoEstimateFees,
                        onCommissionChange = {
                            commissionState.updateFromUser(it) { text ->
                                onCommissionChange(text)
                            }
                        },
                        onTaxChange = {
                            taxState.updateFromUser(it) { text ->
                                onTaxChange(text)
                            }
                        },
                        onRecalculateFees = onRecalculateFees,
                    )
                }

                TradeEntryNoteField(
                    note = noteState.textFieldValue,
                    onValueChange = {
                        noteState.updateFromUser(it) { text ->
                            onNoteChange(text)
                        }
                    },
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (validationMessage != null && validationMessage != symbolLookup.message) {
                    Text(
                        text = validationMessage,
                        color = MarketDown,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                FilledActionButton(
                    text = if (isEditing) {
                        "保存修改"
                    } else {
                        when (state.selectedType) {
                            TradeType.BUY -> "确认买入"
                            TradeType.SELL -> "确认卖出"
                            TradeType.DEPOSIT -> "确认入金"
                            TradeType.WITHDRAW -> "确认出金"
                            TradeType.INTEREST -> "确认支付利息"
                            TradeType.TRANSFER_IN -> "确认转入"
                            TradeType.TRANSFER_OUT -> "确认转出"
                            TradeType.SPLIT -> "确认折算记录"
                            TradeType.EXPIRE -> "确认过期失效"
                            TradeType.DIVIDEND -> "确认分红"
                            TradeType.TAX -> "确认税费支出"
                        }
                    },
                    onClick = onSubmit,
                    enabled = canSubmit,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (showDeleteDialog && onDeleteTradeClick != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("删除记录") },
                text = { Text("确认删除这条${state.selectedType.label}记录吗？删除后无法恢复。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            onDeleteTradeClick()
                        },
                    ) {
                        Text("删除", color = MarketDown)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("取消")
                    }
                },
            )
        }
    }
}

@Composable
fun CurrentPlatformBanner(
    selectedPlatform: BrokerPlatform?,
    title: String = "当前平台",
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = SurfaceSecondary,
                shape = RoundedCornerShape(16.dp),
            )
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectedPlatform != null) {
            PlatformLogoBadge(
                platform = selectedPlatform,
                modifier = Modifier.size(40.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = ForegroundSecondary, fontSize = 13.sp)
            Text(
                text = selectedPlatform?.label ?: "汇总",
                color = ForegroundPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, color = ForegroundMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun FilterActionButton(
    active: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .background(
                color = if (active) ForegroundPrimary else SurfaceSecondary,
                shape = RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.FilterList,
            contentDescription = "筛选",
            tint = if (active) BackgroundPrimary else ForegroundPrimary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = if (active) "已筛选" else "筛选",
            color = if (active) BackgroundPrimary else ForegroundPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun <T> FilterChipWrapRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(3).forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowOptions.forEach { option ->
                    FilterChip(
                        text = label(option),
                        selected = option == selected,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelected(option) },
                    )
                }
                repeat(3 - rowOptions.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FilterDateField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
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
private fun PlatformDropdownField(
    selectedPlatform: BrokerPlatform,
    availablePlatforms: List<BrokerPlatform>,
    onSelected: (BrokerPlatform) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("交易平台", color = ForegroundSecondary, fontSize = 14.sp)
        Box {
            InputFieldBlock(
                label = "",
                value = selectedPlatform.label,
                trailingIcon = Icons.Filled.ArrowDropDown,
                onClick = { expanded = true },
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(BackgroundPrimary),
            ) {
                availablePlatforms.forEach { platform ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PlatformLogoBadge(
                                    platform = platform,
                                    modifier = Modifier.size(28.dp),
                                )
                                Text(platform.label)
                            }
                        },
                        onClick = {
                            onSelected(platform)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ManualSyncDateField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    val context = LocalContext.current
    val selectedDate = remember(value) {
        runCatching { LocalDate.parse(value) }.getOrNull() ?: LocalDate.now()
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        InputFieldBlock(
            label = label,
            value = value,
            placeholder = "请选择",
            trailingIcon = Icons.Filled.DateRange,
            supportingText = "留空则按增量拉取",
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
        Text(
            text = if (value.isBlank()) "未限制开始日期" else "清空日期",
            color = ForegroundMuted,
            fontSize = 12.sp,
            modifier = Modifier.clickable { onValueChange("") },
        )
    }
}

private fun normalizeDateRange(
    startDate: String,
    endDate: String,
): Pair<String?, String?> {
    val normalizedStart = startDate.takeIf { it.isNotBlank() }
    val normalizedEnd = endDate.takeIf { it.isNotBlank() }
    val parsedStart = normalizedStart?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val parsedEnd = normalizedEnd?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    return if (parsedStart != null && parsedEnd != null && parsedStart.isAfter(parsedEnd)) {
        normalizedEnd to normalizedStart
    } else {
        normalizedStart to normalizedEnd
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun HoldingsRoutePreview() {
    StockLedgerTheme {
        HoldingsRoute(
            summary = PreviewFixtures.portfolioSummary,
            displayCurrency = DisplayCurrency.CNY,
            holdings = PreviewFixtures.holdings,
            selectedPlatform = BrokerPlatform.HSBC,
            onPlatformClick = {},
            onSettingsClick = {},
            onDisplayCurrencySelected = {},
            onRefresh = {},
            onDestinationSelected = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun OperationsRoutePreview() {
    StockLedgerTheme {
        OperationsRoute(
            selectedPlatform = BrokerPlatform.HSBC,
            onPlatformClick = {},
            onSettingsClick = {},
            onBuyClick = {},
            onSellClick = {},
            onDepositClick = {},
            onWithdrawClick = {},
            onInterestClick = {},
            onExportBackupClick = {},
            onImportBackupClick = {},
            backupStatusMessage = "最近备份已完成",
            hsbcImportDraftText = PreviewFixtures.hsbcImportDraftText,
            hsbcImportStatusMessage = "已解析 1 条",
            onHsbcImportDraftTextChange = {},
            onImportHsbcNotificationText = {},
            zhuoruiEmailSyncConfig = ZhuoruiEmailSyncConfig(),
            zhuoruiEmailManualSyncOptions = ZhuoruiEmailManualSyncOptions(),
            zhuoruiEmailAutoImportEnabled = false,
            zhuoruiEmailSyncStatusMessage = null,
            onZhuoruiEmailSyncConfigChange = {},
            onZhuoruiEmailManualSyncOptionsChange = {},
            onSaveZhuoruiEmailSyncConfig = {},
            onSyncZhuoruiMailboxNow = {},
            onEnableZhuoruiEmailAutoImport = {},
            onDisableZhuoruiEmailAutoImport = {},
            pdfImportStatusMessage = null,
            pdfImportProgressFraction = null,
            hasFailedPdfImports = false,
            onImportPdfStatements = {},
            onRetryFailedPdfImport = {},
            onDestinationSelected = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun TransactionsRoutePreview() {
    StockLedgerTheme {
        TransactionsRoute(
            sections = PreviewFixtures.transactionSections,
            selectedPlatform = BrokerPlatform.HSBC,
            selectedTradeFilter = TransactionFilter.ALL,
            selectedMarketFilter = MarketFilter.ALL,
            transactionKeyword = "",
            transactionDateStart = "",
            transactionDateEnd = "",
            onTradeFilterSelected = {},
            onMarketFilterSelected = {},
            onTransactionKeywordChange = {},
            onTransactionDateRangeChange = { _, _ -> },
            onResetFilters = {},
            onEditTradeClick = {},
            onPlatformClick = {},
            onSettingsClick = {},
            onDestinationSelected = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun TradeEntryRoutePreview() {
    StockLedgerTheme {
        TradeEntryRoute(
            state = PreviewFixtures.tradeEntryState,
            isEditing = false,
            displayCurrency = DisplayCurrency.USD,
            availablePlatforms = BrokerPlatform.configurableEntries,
            sellCandidates = PreviewFixtures.sellCandidates,
            symbolLookup = PreviewFixtures.symbolLookup,
            symbolSuggestions = PreviewFixtures.symbolSuggestions,
            canSubmit = true,
            validationMessage = null,
            onBackClick = {},
            onTradePlatformSelected = {},
            onSellCandidateSelected = {},
            onSymbolSuggestionSelected = {},
            onMarketSelected = {},
            onCashCurrencySelected = {},
            onSymbolChange = {},
            onDateChange = {},
            onTimeChange = {},
            onPriceChange = {},
            onQuantityChange = {},
            onCommissionChange = {},
            onTaxChange = {},
            onRecalculateFees = {},
            onNoteChange = {},
            onSubmit = {},
        )
    }
}

private fun formatTimeInput(input: String): String {
    val digits = input.filter { it.isDigit() }.take(6)
    val sb = java.lang.StringBuilder()
    for (i in digits.indices) {
        val char = digits[i]
        when (i) {
            0 -> if (char > '2') continue
            1 -> {
                val prev = digits[0]
                if (prev == '2' && char > '3') continue
            }
            2 -> if (char > '5') continue
            4 -> if (char > '5') continue
        }
        sb.append(char)
        if ((i == 1 && digits.length > 2) || (i == 3 && digits.length > 4)) {
            sb.append(":")
        }
    }
    return sb.toString()
}

@Composable
fun PlatformTransferDialog(
    enabledPlatforms: List<BrokerPlatform>,
    getCashBalance: (BrokerPlatform, DisplayCurrency) -> Double,
    onDismiss: () -> Unit,
    onConfirm: (
        isStock: Boolean,
        symbol: String,
        name: String,
        market: Market,
        quantity: Double,
        amount: Double,
        currency: DisplayCurrency,
        sourcePlatform: BrokerPlatform,
        targetPlatform: BrokerPlatform,
        tradeDate: String,
        tradeTime: String,
    ) -> Unit,
) {
    var isStock by remember { mutableStateOf(true) }
    var sourcePlatform by remember { mutableStateOf(enabledPlatforms.firstOrNull { it != BrokerPlatform.UNSPECIFIED } ?: BrokerPlatform.HSBC) }
    var targetPlatform by remember { mutableStateOf(enabledPlatforms.firstOrNull { it != BrokerPlatform.UNSPECIFIED && it != sourcePlatform } ?: BrokerPlatform.ZHUORUI) }
    
    var symbol by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var market by remember { mutableStateOf(Market.US) }
    var quantityStr by remember { mutableStateOf("") }
    
    var currency by remember { mutableStateOf(DisplayCurrency.CNY) }
    var amountStr by remember { mutableStateOf("") }
    
    LaunchedEffect(isStock, sourcePlatform, currency) {
        if (!isStock) {
            val balance = getCashBalance(sourcePlatform, currency)
            amountStr = if (balance <= 0.0) "" else String.format(java.util.Locale.US, "%.2f", balance)
        }
    }
    
    var tradeDate by remember { mutableStateOf(LocalDate.now().toString()) }
    val initialTime = remember { java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")) }
    var timeValue by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(initialTime, androidx.compose.ui.text.TextRange(initialTime.length))) }
    
    val isValidTime = remember(timeValue.text) {
        runCatching { java.time.LocalTime.parse(timeValue.text) }.isSuccess
    }

    var sourceExpanded by remember { mutableStateOf(false) }
    var targetExpanded by remember { mutableStateOf(false) }
    var marketExpanded by remember { mutableStateOf(false) }
    var currencyExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "平台间资产转仓",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = ForegroundPrimary,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceSecondary, RoundedCornerShape(8.dp))
                        .padding(3.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isStock) BackgroundPrimary else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { isStock = true }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "股票转仓",
                            color = if (isStock) ForegroundPrimary else ForegroundSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (!isStock) BackgroundPrimary else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { isStock = false }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "资金划转",
                            color = if (!isStock) ForegroundPrimary else ForegroundSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("转出平台", color = ForegroundSecondary, fontSize = 12.sp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceSecondary, RoundedCornerShape(8.dp))
                            .clickable { sourceExpanded = true }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(sourcePlatform.label, color = ForegroundPrimary, fontSize = 14.sp)
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = ForegroundSecondary,
                            )
                        }
                        DropdownMenu(
                            expanded = sourceExpanded,
                            onDismissRequest = { sourceExpanded = false },
                            modifier = Modifier.background(BackgroundPrimary),
                        ) {
                            enabledPlatforms.filter { it != BrokerPlatform.UNSPECIFIED }.forEach { platform ->
                                DropdownMenuItem(
                                    text = { Text(platform.label, color = ForegroundPrimary) },
                                    onClick = {
                                        sourcePlatform = platform
                                        sourceExpanded = false
                                        if (targetPlatform == platform) {
                                            targetPlatform = enabledPlatforms.firstOrNull { it != BrokerPlatform.UNSPECIFIED && it != platform } ?: BrokerPlatform.UNSPECIFIED
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("转入平台", color = ForegroundSecondary, fontSize = 12.sp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceSecondary, RoundedCornerShape(8.dp))
                            .clickable { targetExpanded = true }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(targetPlatform.label, color = ForegroundPrimary, fontSize = 14.sp)
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = ForegroundSecondary,
                            )
                        }
                        DropdownMenu(
                            expanded = targetExpanded,
                            onDismissRequest = { targetExpanded = false },
                            modifier = Modifier.background(BackgroundPrimary),
                        ) {
                            enabledPlatforms.filter { it != BrokerPlatform.UNSPECIFIED && it != sourcePlatform }.forEach { platform ->
                                DropdownMenuItem(
                                    text = { Text(platform.label, color = ForegroundPrimary) },
                                    onClick = {
                                        targetPlatform = platform
                                        targetExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                val context = androidx.compose.ui.platform.LocalContext.current
                val calendar = remember { java.util.Calendar.getInstance() }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("转移日期", color = ForegroundSecondary, fontSize = 12.sp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceSecondary, RoundedCornerShape(8.dp))
                                .clickable {
                                    val dateParts = tradeDate.split("-").mapNotNull { it.toIntOrNull() }
                                    val y = dateParts.getOrNull(0) ?: calendar.get(java.util.Calendar.YEAR)
                                    val m = dateParts.getOrNull(1)?.minus(1) ?: calendar.get(java.util.Calendar.MONTH)
                                    val d = dateParts.getOrNull(2) ?: calendar.get(java.util.Calendar.DAY_OF_MONTH)
                                    DatePickerDialog(
                                        context,
                                        { _, year, month, dayOfMonth ->
                                            tradeDate = java.time.LocalDate.of(year, month + 1, dayOfMonth).toString()
                                        },
                                        y,
                                        m,
                                        d
                                    ).show()
                                }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                        ) {
                            Text(tradeDate, color = ForegroundPrimary, fontSize = 14.sp)
                        }
                    }
                    
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("转移时间", color = ForegroundSecondary, fontSize = 12.sp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceSecondary, RoundedCornerShape(8.dp))
                                .clickable {
                                    val timeParts = timeValue.text.split(":").mapNotNull { it.toIntOrNull() }
                                    val hour = timeParts.getOrNull(0) ?: calendar.get(java.util.Calendar.HOUR_OF_DAY)
                                    val minute = timeParts.getOrNull(1) ?: calendar.get(java.util.Calendar.MINUTE)
                                    TimePickerDialog(
                                        context,
                                        { _, hourOfDay, minuteOfHour ->
                                            val formattedTime = String.format(java.util.Locale.US, "%02d:%02d:00", hourOfDay, minuteOfHour)
                                            timeValue = androidx.compose.ui.text.input.TextFieldValue(formattedTime, selection = androidx.compose.ui.text.TextRange(formattedTime.length))
                                        },
                                        hour,
                                        minute,
                                        true // is24HourView
                                    ).show()
                                }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                        ) {
                            Text(timeValue.text, color = ForegroundPrimary, fontSize = 14.sp)
                        }
                    }
                }

                if (isStock) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("股票代码", color = ForegroundSecondary, fontSize = 12.sp)
                        InputFieldBlockWithoutTime(
                            value = symbol,
                            placeholder = "例如: AAPL",
                            onValueChange = { symbol = it },
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("所属市场", color = ForegroundSecondary, fontSize = 12.sp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceSecondary, RoundedCornerShape(8.dp))
                                .clickable { marketExpanded = true }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(market.label, color = ForegroundPrimary, fontSize = 14.sp)
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = ForegroundSecondary,
                                )
                            }
                            DropdownMenu(
                                expanded = marketExpanded,
                                onDismissRequest = { marketExpanded = false },
                                modifier = Modifier.background(BackgroundPrimary),
                            ) {
                                listOf(Market.US, Market.HK, Market.A_SHARE).forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text(m.label, color = ForegroundPrimary) },
                                        onClick = {
                                            market = m
                                            marketExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("数量 (股)", color = ForegroundSecondary, fontSize = 12.sp)
                        InputFieldBlockWithoutTime(
                            value = quantityStr,
                            placeholder = "要转移的股数",
                            keyboardType = KeyboardType.Decimal,
                            onValueChange = { quantityStr = it.filter { c -> c.isDigit() || c == '.' } },
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("币种", color = ForegroundSecondary, fontSize = 12.sp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceSecondary, RoundedCornerShape(8.dp))
                                .clickable { currencyExpanded = true }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(currency.label, color = ForegroundPrimary, fontSize = 14.sp)
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = ForegroundSecondary,
                                )
                            }
                            DropdownMenu(
                                expanded = currencyExpanded,
                                onDismissRequest = { currencyExpanded = false },
                                modifier = Modifier.background(BackgroundPrimary),
                            ) {
                                DisplayCurrency.entries.forEach { cur ->
                                    DropdownMenuItem(
                                        text = { Text(cur.label, color = ForegroundPrimary) },
                                        onClick = {
                                            currency = cur
                                            currencyExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("划转金额", color = ForegroundSecondary, fontSize = 12.sp)
                        InputFieldBlockWithoutTime(
                            value = amountStr,
                            placeholder = "要划转的金额",
                            keyboardType = KeyboardType.Decimal,
                            onValueChange = { amountStr = it },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val qty = quantityStr.toDoubleOrNull() ?: 0.0
                    val amt = amountStr.toDoubleOrNull() ?: 0.0
                    if (sourcePlatform != BrokerPlatform.UNSPECIFIED && targetPlatform != BrokerPlatform.UNSPECIFIED && isValidTime) {
                        onConfirm(
                            isStock,
                            symbol,
                            name,
                            market,
                            qty,
                            amt,
                            currency,
                            sourcePlatform,
                            targetPlatform,
                            tradeDate,
                            timeValue.text,
                        )
                        onDismiss()
                    }
                },
                enabled = (if (isStock) {
                    symbol.isNotBlank() && (quantityStr.toDoubleOrNull() ?: 0.0) > 0.0
                } else {
                    (amountStr.toDoubleOrNull() ?: 0.0) > 0.0
                }) && isValidTime
            ) {
                Text("确定", color = androidx.compose.ui.graphics.Color(0xFFE5A93B), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = ForegroundSecondary)
            }
        },
        containerColor = BackgroundPrimary,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputFieldBlockWithoutTime(
    value: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit,
) {
    androidx.compose.material3.TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = ForegroundMuted, fontSize = 14.sp) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
        colors = androidx.compose.material3.TextFieldDefaults.colors(
            focusedTextColor = ForegroundPrimary,
            unfocusedTextColor = ForegroundPrimary,
            focusedContainerColor = SurfaceSecondary,
            unfocusedContainerColor = SurfaceSecondary,
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.fillMaxWidth(),
    )
}


