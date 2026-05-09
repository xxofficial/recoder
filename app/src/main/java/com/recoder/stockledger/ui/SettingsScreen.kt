package com.recoder.stockledger.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recoder.stockledger.data.BrokerPlatform
import com.recoder.stockledger.data.PlatformFeePlanUiModel
import com.recoder.stockledger.data.ZhuoruiPromoConfig
import com.recoder.stockledger.data.PdfImportMode
import com.recoder.stockledger.ui.theme.BackgroundPrimary
import com.recoder.stockledger.ui.theme.ForegroundMuted
import com.recoder.stockledger.ui.theme.ForegroundPrimary
import com.recoder.stockledger.ui.theme.ForegroundSecondary
import com.recoder.stockledger.ui.theme.MarketUp
import com.recoder.stockledger.ui.theme.StockLedgerTheme
import com.recoder.stockledger.ui.theme.SurfaceSecondary
import java.time.LocalDate

@Composable
fun SettingsRoute(
    selectedPlatform: BrokerPlatform?,
    selectedPlatformFeePlan: PlatformFeePlanUiModel?,
    onPlatformFeePlanSelected: (String) -> Unit,
    zhuoruiPromoConfig: ZhuoruiPromoConfig,
    onZhuoruiPromoChange: (ZhuoruiPromoConfig) -> Unit,
    onSaveZhuoruiPromo: () -> Unit,
    alibabaBailianApiKey: String,
    pdfImportMode: PdfImportMode,
    textImportModel: String,
    llmApiBaseUrl: String,
    onAlibabaBailianApiKeyChange: (String) -> Unit,
    onPdfImportModeChange: (PdfImportMode) -> Unit,
    onTextImportModelChange: (String) -> Unit,
    onLlmApiBaseUrlChange: (String) -> Unit,
    onPlatformClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    var promoSaveMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary),
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenHeader(
                title = "设置",
                onBack = onBackClick,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // Fee plan section
                selectedPlatformFeePlan?.let { feePlan ->
                    val selectedOption = feePlan.options.firstOrNull { it.isSelected } ?: feePlan.options.first()
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
                        Text("费率方案", color = ForegroundPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "只影响后续自动估算，不会改动已保存记录；切换后录入页会立刻按新方案重新估算。",
                            color = ForegroundSecondary,
                            fontSize = 13.sp,
                        )
                        Text(
                            "当前平台：${feePlan.platform.label}",
                            color = ForegroundMuted,
                            fontSize = 12.sp,
                        )
                        FilterChipWrapRow(
                            options = feePlan.options,
                            selected = selectedOption,
                            label = { it.label },
                            onSelected = { option ->
                                if (!option.isSelected) {
                                    onPlatformFeePlanSelected(option.id)
                                }
                            },
                        )
                        if (feePlan.options.size == 1) {
                            Text(
                                "当前只收录 1 套公开方案，后续如果平台公布更多完整费率规则，我再继续补。",
                                color = ForegroundMuted,
                                fontSize = 12.sp,
                            )
                        }
                        Text(
                            "当前：${feePlan.selectedPlanLabel}",
                            color = ForegroundPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            feePlan.selectedPlanDescription,
                            color = ForegroundMuted,
                            fontSize = 12.sp,
                        )
                    }
                }

                // Zhuorui promo config section
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
                        Text("卓锐新客免佣设置", color = ForegroundPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "新客户在指定天数内享受免佣金优惠（平台费照收）。设置后自动应用于费用估算。",
                            color = ForegroundSecondary,
                            fontSize = 13.sp,
                        )

                        ZhuoruiPromoDateField(
                            label = "免佣开始日期",
                            value = zhuoruiPromoConfig.startDate,
                            onValueChange = { date ->
                                onZhuoruiPromoChange(zhuoruiPromoConfig.copy(startDate = date))
                            },
                        )

                        InputFieldBlock(
                            label = "免佣天数",
                            value = zhuoruiPromoConfig.durationDays.toString(),
                            placeholder = "100",
                            keyboardType = KeyboardType.Number,
                            supportingText = "默认 100 天",
                            onValueChange = { value ->
                                val days = value.toIntOrNull()?.coerceIn(1, 9999) ?: return@InputFieldBlock
                                onZhuoruiPromoChange(zhuoruiPromoConfig.copy(durationDays = days))
                            },
                        )

                        val endDate = zhuoruiPromoConfig.endDate
                        if (endDate != null) {
                            val isActive = !LocalDate.now().isAfter(endDate)
                            Text(
                                text = if (isActive) {
                                    "免佣截止日期：${endDate}（生效中）"
                                } else {
                                    "免佣截止日期：${endDate}（已过期）"
                                },
                                color = if (isActive) MarketUp else ForegroundMuted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlineActionButton(
                                text = "清除设置",
                                onClick = {
                                    onZhuoruiPromoChange(ZhuoruiPromoConfig())
                                    onSaveZhuoruiPromo()
                                    promoSaveMessage = "已清除免佣设置"
                                },
                                modifier = Modifier.weight(1f),
                            )
                            FilledActionButton(
                                text = "保存",
                                onClick = {
                                    onSaveZhuoruiPromo()
                                    promoSaveMessage = "免佣设置已保存"
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }

                        promoSaveMessage?.let { message ->
                            Text(
                                text = message,
                                color = ForegroundMuted,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }

                // Zhuorui PDF Import settings
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
                        Text("卓锐结单PDF导入设置", color = ForegroundPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "选择解析PDF结单的方式。正则匹配在本地运行，大模型方式需要联网并消耗API额度。",
                            color = ForegroundSecondary,
                            fontSize = 13.sp,
                        )

                        FilterChipWrapRow(
                            options = PdfImportMode.entries,
                            selected = pdfImportMode,
                            label = { it.label },
                            onSelected = onPdfImportModeChange,
                        )

                        if (pdfImportMode != PdfImportMode.REGEX) {
                            InputFieldBlock(
                                label = "API Key",
                                value = alibabaBailianApiKey,
                                placeholder = "请输入 API Key",
                                isPassword = true,
                                onValueChange = onAlibabaBailianApiKeyChange,
                            )

                            InputFieldBlock(
                                label = "文本模型名称",
                                value = textImportModel,
                                placeholder = "默认 deepseek-chat",
                                onValueChange = onTextImportModelChange,
                            )

                            InputFieldBlock(
                                label = "API Base URL",
                                value = llmApiBaseUrl,
                                placeholder = "默认 https://api.deepseek.com/v1",
                                supportingText = "OpenAI接口协议",
                                onValueChange = onLlmApiBaseUrlChange,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZhuoruiPromoDateField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    val context = LocalContext.current
    val selectedDate = remember(value) {
        runCatching { LocalDate.parse(value) }.getOrNull() ?: LocalDate.now()
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        InputFieldBlock(
            label = label,
            value = value,
            placeholder = "请选择日期",
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
        if (value.isNotBlank()) {
            Text(
                text = "清空日期",
                color = ForegroundMuted,
                fontSize = 12.sp,
                modifier = Modifier.clickable { onValueChange("") },
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun SettingsRoutePreview() {
    StockLedgerTheme {
        SettingsRoute(
            selectedPlatform = BrokerPlatform.ZHUORUI,
            selectedPlatformFeePlan = PreviewFixtures.feePlan,
            onPlatformFeePlanSelected = {},
            zhuoruiPromoConfig = PreviewFixtures.zhuoruiPromoConfig,
            onZhuoruiPromoChange = {},
            onSaveZhuoruiPromo = {},
            alibabaBailianApiKey = "",
            pdfImportMode = PdfImportMode.REGEX,
            textImportModel = "",
            onAlibabaBailianApiKeyChange = {},
            onPdfImportModeChange = {},
            onTextImportModelChange = {},
            onPlatformClick = {},
            onBackClick = {},
            llmApiBaseUrl = "",
            onLlmApiBaseUrlChange = {},
        )
    }
}
