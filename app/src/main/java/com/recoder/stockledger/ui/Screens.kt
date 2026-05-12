package com.recoder.stockledger.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import com.recoder.stockledger.ui.theme.BackgroundPrimary
import com.recoder.stockledger.ui.theme.ForegroundMuted
import com.recoder.stockledger.ui.theme.ForegroundPrimary
import com.recoder.stockledger.ui.theme.ForegroundSecondary
import com.recoder.stockledger.ui.theme.MarketDown
import com.recoder.stockledger.ui.theme.MarketUp
import com.recoder.stockledger.ui.theme.StockLedgerTheme
import com.recoder.stockledger.ui.theme.SurfaceSecondary
import java.time.LocalDate

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HoldingsRoute(
    summary: PortfolioSummary,
    displayCurrency: DisplayCurrency,
    holdings: List<HoldingUiModel>,
    selectedPlatform: BrokerPlatform?,
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
    pdfImportPassword: String,
    pdfImportStatusMessage: String?,
    pdfImportProgressFraction: Float?,
    hasFailedPdfImports: Boolean,
    onPdfImportPasswordChange: (String) -> Unit,
    onImportPdfStatements: (BrokerPlatform) -> Unit,
    onRetryFailedPdfImport: (BrokerPlatform) -> Unit,
    onDestinationSelected: (TopLevelDestination) -> Unit,
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
                TradeActionButtons(
                    onBuyClick = onBuyClick,
                    onSellClick = onSellClick,
                    onDepositClick = onDepositClick,
                    onWithdrawClick = onWithdrawClick,
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

                // PDF Statement Import Section - Available for Configurable Platforms
                if (selectedPlatform != BrokerPlatform.UNSPECIFIED && selectedPlatform?.isConfigurable == true) {
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
                            "支持导入${selectedPlatform.label}的日结单（PDF格式），可同时选择多个文件批量导入。",
                            color = ForegroundSecondary,
                            fontSize = 13.sp,
                        )
                        InputFieldBlock(
                            label = "PDF结单密码",
                            value = pdfImportPassword,
                            placeholder = "如无密码可留空",
                            isPassword = true,
                            keyboardType = KeyboardType.Password,
                            supportingText = "按当前交易平台分别保存",
                            onValueChange = onPdfImportPasswordChange,
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
) {
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

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 20.dp, end = 20.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item(key = "search_header") {
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
                                    .clickable {
                                        if (selectedTransactionIds.isNotEmpty()) showBatchDeleteDialog = true
                                    }
                                    .background(if (selectedTransactionIds.isNotEmpty()) MarketDown else SurfaceSecondary)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = "删除(${selectedTransactionIds.size})",
                                    color = if (selectedTransactionIds.isNotEmpty()) BackgroundPrimary else ForegroundMuted,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
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
                                Text("批量删除", color = ForegroundPrimary, fontSize = 13.sp)
                            }
                        }
                    }
                }

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
) {
    var symbolValue by remember { mutableStateOf(TextFieldValue(state.symbolOrName, TextRange(state.symbolOrName.length))) }
    var priceValue by remember { mutableStateOf(TextFieldValue(state.priceLabel, TextRange(state.priceLabel.length))) }
    var quantityValue by remember { mutableStateOf(TextFieldValue(state.quantityLabel, TextRange(state.quantityLabel.length))) }
    var commissionValue by remember { mutableStateOf(TextFieldValue(state.commissionLabel, TextRange(state.commissionLabel.length))) }
    var taxValue by remember { mutableStateOf(TextFieldValue(state.taxLabel, TextRange(state.taxLabel.length))) }
    var timeValue by remember { mutableStateOf(TextFieldValue(state.tradeTime, TextRange(state.tradeTime.length))) }
    var noteValue by remember { mutableStateOf(TextFieldValue(state.note, TextRange(state.note.length))) }

    LaunchedEffect(state.symbolOrName) {
        if (symbolValue.text != state.symbolOrName) {
            symbolValue = TextFieldValue(state.symbolOrName, TextRange(state.symbolOrName.length))
        }
    }
    LaunchedEffect(state.priceLabel) {
        if (priceValue.text != state.priceLabel) {
            priceValue = TextFieldValue(state.priceLabel, TextRange(state.priceLabel.length))
        }
    }
    LaunchedEffect(state.quantityLabel) {
        if (quantityValue.text != state.quantityLabel) {
            quantityValue = TextFieldValue(state.quantityLabel, TextRange(state.quantityLabel.length))
        }
    }
    LaunchedEffect(state.commissionLabel) {
        if (commissionValue.text != state.commissionLabel) {
            commissionValue = TextFieldValue(state.commissionLabel, TextRange(state.commissionLabel.length))
        }
    }
    LaunchedEffect(state.taxLabel) {
        if (taxValue.text != state.taxLabel) {
            taxValue = TextFieldValue(state.taxLabel, TextRange(state.taxLabel.length))
        }
    }
    LaunchedEffect(state.tradeTime) {
        if (timeValue.text != state.tradeTime) {
            timeValue = TextFieldValue(state.tradeTime, TextRange(state.tradeTime.length))
        }
    }
    LaunchedEffect(state.note) {
        if (noteValue.text != state.note) {
            noteValue = TextFieldValue(state.note, TextRange(state.note.length))
        }
    }

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
                    InputFieldBlock(
                        label = "证券代码 / 名称",
                        value = symbolValue,
                        supportingText = symbolLookup.message,
                        supportingColor = when (symbolLookup.state) {
                            SymbolLookupState.INVALID -> MarketDown
                            SymbolLookupState.RESOLVED -> MarketUp
                            else -> ForegroundMuted
                        },
                        onValueChange = {
                            symbolValue = it
                            onSymbolChange(it.text)
                        },
                    )
                    if (symbolSuggestions.isNotEmpty()) {
                        SymbolSuggestionSection(
                            suggestions = symbolSuggestions,
                            onSelected = onSymbolSuggestionSelected,
                        )
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
                    InputFieldBlock(
                        label = "交易时间",
                        value = timeValue,
                        placeholder = "HH:MM:SS",
                        supportingText = "24小时制",
                        modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Text,
                        onValueChange = {
                            timeValue = it
                            onTimeChange(it.text)
                        },
                    )
                }

                if (isSecurityTrade) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        InputFieldBlock(
                            label = "成交价格",
                            value = priceValue,
                            modifier = Modifier.weight(1f),
                            keyboardType = KeyboardType.Decimal,
                            onValueChange = {
                                priceValue = it
                                onPriceChange(it.text)
                            },
                        )
                        InputFieldBlock(
                            label = "成交数量",
                            value = quantityValue,
                            modifier = Modifier.weight(1f),
                            keyboardType = KeyboardType.Number,
                            onValueChange = {
                                quantityValue = it
                                onQuantityChange(it.text)
                            },
                        )
                    }
                } else {
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
                        label = if (state.selectedType == TradeType.DEPOSIT) {
                            "入金金额 ($cashCurrencyCode)"
                        } else {
                            "出金金额 ($cashCurrencyCode)"
                        },
                        value = priceValue,
                        supportingText = "当前按${cashCurrencyLabel}录入，保存后会自动折算到资产汇总。",
                        keyboardType = KeyboardType.Decimal,
                        onValueChange = {
                            priceValue = it
                            onPriceChange(it.text)
                        },
                    )
                }

                if (isSecurityTrade) {
                    TradeEntryFeeCard(
                        commission = commissionValue,
                        tax = taxValue,
                        feeEstimateStatus = state.feeEstimateStatus,
                        feeEstimateSummary = state.feeEstimateSummary,
                        feeEstimateDetail = state.feeEstimateDetail,
                        canAutoEstimateFees = state.canAutoEstimateFees,
                        onCommissionChange = {
                            commissionValue = it
                            onCommissionChange(it.text)
                        },
                        onTaxChange = {
                            taxValue = it
                            onTaxChange(it.text)
                        },
                        onRecalculateFees = onRecalculateFees,
                    )
                }

                TradeEntryNoteField(
                    note = noteValue,
                    onValueChange = {
                        noteValue = it
                        onNoteChange(it.text)
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
            pdfImportPassword = "",
            pdfImportStatusMessage = null,
            pdfImportProgressFraction = null,
            hasFailedPdfImports = false,
            onPdfImportPasswordChange = {},
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


