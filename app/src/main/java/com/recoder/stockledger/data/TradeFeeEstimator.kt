package com.recoder.stockledger.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.math.min

enum class FeeEstimateCoverage {
    FULL,
    PARTIAL,
    UNSUPPORTED,
}

data class TradeFeePlanOption(
    val id: String,
    val label: String,
    val description: String,
)

data class TradeFeeEstimateContext(
    val monthlyTurnoverHkdBeforeTrade: Double? = null,
    val zhuoruiCommissionFreeEndDate: LocalDate? = null,
    val tradeDate: LocalDate? = null,
)

data class TradeFeeEstimate(
    val coverage: FeeEstimateCoverage,
    val commission: Double,
    val tax: Double,
    val summary: String,
    val detail: String,
    val canAutoApply: Boolean,
)

data class TradeFeeProfile(
    val coverage: FeeEstimateCoverage,
    val planId: String,
    val planLabel: String,
    val note: String,
)

object TradeFeeEstimator {
    private const val PLAN_EAST_MONEY_STANDARD = "east_money_standard"
    private const val PLAN_LONGBRIDGE_PUBLIC_PROMO = "longbridge_public_promo"
    private const val PLAN_HSBC_STANDARD = "hsbc_standard"
    private const val PLAN_HSBC_TRADE25 = "hsbc_trade25"
    private const val PLAN_WEBULL_PUBLIC_PROMO = "webull_public_promo"
    private const val PLAN_ZHUORUI_NEW_CUSTOMER = "zhuorui_new_customer"
    private const val PLAN_ZHUORUI_LEGACY_CUSTOMER = "zhuorui_legacy_customer"
    private const val TRADE25_MONTHLY_TURNOVER_LIMIT_HKD = 250_000.0

    private val eastMoneyPlans = listOf(
        TradeFeePlanOption(
            id = PLAN_EAST_MONEY_STANDARD,
            label = "标准公开价",
            description = "按东方财富国际当前公开费率估算，适合普通港美股交易。",
        ),
    )

    private val longbridgePlans = listOf(
        TradeFeePlanOption(
            id = PLAN_LONGBRIDGE_PUBLIC_PROMO,
            label = "公开活动价",
            description = "按长桥官网当前公开的 0 佣金活动价估算；未收录未公开完整阶梯的平台费细则。",
        ),
    )

    private val hsbcPlans = listOf(
        TradeFeePlanOption(
            id = PLAN_HSBC_STANDARD,
            label = "标准公开价",
            description = "按汇丰标准公开佣金估算，适合普通账户；不含 Trade25 月费方案。",
        ),
        TradeFeePlanOption(
            id = PLAN_HSBC_TRADE25,
            label = "Trade25",
            description = "会根据当月累计成交额自动判断是否仍处于 HKD250,000 免佣区间；月费 HKD25 不分摊到单笔。",
        ),
    )

    private val webullPlans = listOf(
        TradeFeePlanOption(
            id = PLAN_WEBULL_PUBLIC_PROMO,
            label = "当前公开费率",
            description = "按 Webull 官网当前公开的 0 佣金、0 平台费活动价估算。",
        ),
    )

    private val zhuoruiPlans = listOf(
        TradeFeePlanOption(
            id = PLAN_ZHUORUI_NEW_CUSTOMER,
            label = "2025 新客费率",
            description = "适合 2025-03-19 起的新客户，含官网当前公开的平台费。",
        ),
        TradeFeePlanOption(
            id = PLAN_ZHUORUI_LEGACY_CUSTOMER,
            label = "老客费率待核",
            description = "仅自动计入公开可确认项，不自动加入老客户差异化平台费，适合先记账后人工复核。",
        ),
    )

    fun availablePlans(platform: BrokerPlatform): List<TradeFeePlanOption> = when (platform) {
        BrokerPlatform.ALIPAY, BrokerPlatform.UNSPECIFIED -> emptyList()
        BrokerPlatform.EAST_MONEY -> eastMoneyPlans
        BrokerPlatform.LONGBRIDGE -> longbridgePlans
        BrokerPlatform.HSBC -> hsbcPlans
        BrokerPlatform.WEBULL -> webullPlans
        BrokerPlatform.ZHUORUI -> zhuoruiPlans
    }

    fun defaultPlanId(platform: BrokerPlatform): String =
        availablePlans(platform).firstOrNull()?.id.orEmpty()

    fun resolvePlanId(platform: BrokerPlatform, selectedPlanId: String?): String {
        val availablePlans = availablePlans(platform)
        return availablePlans.firstOrNull { it.id == selectedPlanId }?.id
            ?: availablePlans.firstOrNull()?.id
            .orEmpty()
    }

    fun profile(
        platform: BrokerPlatform,
        market: Market,
        planId: String = defaultPlanId(platform),
    ): TradeFeeProfile {
        val resolvedPlan = resolvePlan(platform, planId)
        return when (platform) {
            BrokerPlatform.ALIPAY -> TradeFeeProfile(
                coverage = FeeEstimateCoverage.UNSUPPORTED,
                planId = resolvedPlan.id,
                planLabel = resolvedPlan.label,
                note = "支付宝当前按现金/理财入口处理，暂不支持证券交易费用自动计算。",
            )

            BrokerPlatform.EAST_MONEY -> when (market) {
                Market.HONG_KONG -> TradeFeeProfile(
                    coverage = FeeEstimateCoverage.FULL,
                    planId = resolvedPlan.id,
                    planLabel = resolvedPlan.label,
                    note = "按东方财富国际标准公开价估算，已包含佣金、平台使用费和官方代收费。",
                )

                Market.US -> TradeFeeProfile(
                    coverage = FeeEstimateCoverage.FULL,
                    planId = resolvedPlan.id,
                    planLabel = resolvedPlan.label,
                    note = "按东方财富国际标准公开价估算，已包含佣金、平台使用费和公开列示的美股代收费。",
                )

                else -> TradeFeeProfile(
                    coverage = FeeEstimateCoverage.UNSUPPORTED,
                    planId = resolvedPlan.id,
                    planLabel = resolvedPlan.label,
                    note = "东方财富当前仅内置港股和美股自动费率，A股请先手动填写。",
                )
            }

            BrokerPlatform.LONGBRIDGE -> when (market) {
                Market.HONG_KONG -> TradeFeeProfile(
                    coverage = FeeEstimateCoverage.PARTIAL,
                    planId = resolvedPlan.id,
                    planLabel = resolvedPlan.label,
                    note = "按长桥官网当前公开活动价估算：佣金 0、平台费 0；已计入公开可确认的港股法定收费，未含可能因账户计划不同产生的额外交收费。",
                )

                Market.US -> TradeFeeProfile(
                    coverage = FeeEstimateCoverage.PARTIAL,
                    planId = resolvedPlan.id,
                    planLabel = resolvedPlan.label,
                    note = "按长桥官网公开固定费率估算：佣金 0、平台费 0.005 美元/股（最低 1 美元）；已计入 SEC/FINRA 卖出规费，未含官网未公开列示的第三方结算费。",
                )

                else -> TradeFeeProfile(
                    coverage = FeeEstimateCoverage.UNSUPPORTED,
                    planId = resolvedPlan.id,
                    planLabel = resolvedPlan.label,
                    note = "长桥当前仅内置港股和美股自动费率。",
                )
            }

            BrokerPlatform.HSBC -> when (market) {
                Market.HONG_KONG -> when (resolvedPlan.id) {
                    PLAN_HSBC_TRADE25 -> TradeFeeProfile(
                        coverage = FeeEstimateCoverage.PARTIAL,
                        planId = resolvedPlan.id,
                        planLabel = resolvedPlan.label,
                        note = "按汇丰 Trade25 估算：会根据当月累计成交额自动判断是否仍在 HKD250,000 免佣区间内；月费 HKD25 不分摊到单笔，法定收费照常计入。",
                    )

                    else -> TradeFeeProfile(
                        coverage = FeeEstimateCoverage.PARTIAL,
                        planId = resolvedPlan.id,
                        planLabel = resolvedPlan.label,
                        note = "按汇丰标准公开价估算：港股在线佣金 0.25%，最低 100 港元；已计入公开可确认的港股法定收费。",
                    )
                }

                Market.US -> when (resolvedPlan.id) {
                    PLAN_HSBC_TRADE25 -> TradeFeeProfile(
                        coverage = FeeEstimateCoverage.PARTIAL,
                        planId = resolvedPlan.id,
                        planLabel = resolvedPlan.label,
                        note = "按汇丰 Trade25 估算：会根据当月累计成交额自动判断是否仍在 HKD250,000 免佣区间内；月费 HKD25 不分摊到单笔，SEC/FINRA 卖出规费照常计入。",
                    )

                    else -> TradeFeeProfile(
                        coverage = FeeEstimateCoverage.PARTIAL,
                        planId = resolvedPlan.id,
                        planLabel = resolvedPlan.label,
                        note = "按汇丰标准公开价估算：美股前 1000 股 18 美元，超出部分每股加 0.015 美元；按汇丰官网当前说明，SEC fee 自 2025-05-14 起为 0%，因此这里只计入 FINRA 卖出规费。",
                    )
                }

                else -> TradeFeeProfile(
                    coverage = FeeEstimateCoverage.UNSUPPORTED,
                    planId = resolvedPlan.id,
                    planLabel = resolvedPlan.label,
                    note = "汇丰当前仅内置港股和美股自动费率。",
                )
            }

            BrokerPlatform.WEBULL -> when (market) {
                Market.HONG_KONG -> TradeFeeProfile(
                    coverage = FeeEstimateCoverage.PARTIAL,
                    planId = resolvedPlan.id,
                    planLabel = resolvedPlan.label,
                    note = "按 Webull 官网当前公开活动价估算：佣金 0、平台费 0；已计入公开可确认的港股法定收费，未含官网未公开列示的交收费。",
                )

                Market.US -> TradeFeeProfile(
                    coverage = FeeEstimateCoverage.PARTIAL,
                    planId = resolvedPlan.id,
                    planLabel = resolvedPlan.label,
                    note = "按 Webull 官网当前公开活动价估算：佣金 0、平台费 0；已计入 SEC/FINRA 卖出规费，未含官网未公开列示的第三方结算费。",
                )

                else -> TradeFeeProfile(
                    coverage = FeeEstimateCoverage.UNSUPPORTED,
                    planId = resolvedPlan.id,
                    planLabel = resolvedPlan.label,
                    note = "Webull 当前仅内置港股和美股自动费率。",
                )
            }

            BrokerPlatform.ZHUORUI -> when (market) {
                Market.HONG_KONG -> when (resolvedPlan.id) {
                    PLAN_ZHUORUI_LEGACY_CUSTOMER -> TradeFeeProfile(
                        coverage = FeeEstimateCoverage.PARTIAL,
                        planId = resolvedPlan.id,
                        planLabel = resolvedPlan.label,
                        note = "按卓锐老客费率待核方案估算：已保留公开可确认的佣金和港股法定收费，未自动加入老客户差异化平台费。",
                    )

                    else -> TradeFeeProfile(
                        coverage = FeeEstimateCoverage.PARTIAL,
                        planId = resolvedPlan.id,
                        planLabel = resolvedPlan.label,
                        note = "按卓锐官网当前新客费率估算，已包含佣金、平台费和官方代收费。",
                    )
                }

                Market.US -> when (resolvedPlan.id) {
                    PLAN_ZHUORUI_LEGACY_CUSTOMER -> TradeFeeProfile(
                        coverage = FeeEstimateCoverage.PARTIAL,
                        planId = resolvedPlan.id,
                        planLabel = resolvedPlan.label,
                        note = "按卓锐老客费率待核方案估算：已保留公开可确认的佣金和美股代收费，未自动加入老客户差异化平台费。",
                    )

                    else -> TradeFeeProfile(
                        coverage = FeeEstimateCoverage.PARTIAL,
                        planId = resolvedPlan.id,
                        planLabel = resolvedPlan.label,
                        note = "按卓锐官网当前新客费率估算，已包含佣金、平台费和公开列示的美股代收费。",
                    )
                }

                Market.A_SHARE -> when (resolvedPlan.id) {
                    PLAN_ZHUORUI_LEGACY_CUSTOMER -> TradeFeeProfile(
                        coverage = FeeEstimateCoverage.PARTIAL,
                        planId = resolvedPlan.id,
                        planLabel = resolvedPlan.label,
                        note = "按卓锐老客费率待核方案估算：已保留公开可确认的佣金和交易代收费，未自动加入老客户差异化平台费。",
                    )

                    else -> TradeFeeProfile(
                        coverage = FeeEstimateCoverage.PARTIAL,
                        planId = resolvedPlan.id,
                        planLabel = resolvedPlan.label,
                        note = "按卓锐官网当前新客 A 股费率估算；ETF、组合费等特殊场景仍建议人工复核。",
                    )
                }

                else -> TradeFeeProfile(
                    coverage = FeeEstimateCoverage.UNSUPPORTED,
                    planId = resolvedPlan.id,
                    planLabel = resolvedPlan.label,
                    note = "卓锐当前仅内置港股、美股和 A 股自动费率。",
                )
            }

            BrokerPlatform.UNSPECIFIED -> TradeFeeProfile(
                coverage = FeeEstimateCoverage.UNSUPPORTED,
                planId = resolvedPlan.id,
                planLabel = resolvedPlan.label,
                note = "请先选择有效交易平台后再自动估算费用。",
            )
        }
    }

    fun estimate(
        platform: BrokerPlatform,
        market: Market,
        tradeType: TradeType,
        price: Double,
        quantity: Int,
        planId: String = defaultPlanId(platform),
        context: TradeFeeEstimateContext = TradeFeeEstimateContext(),
    ): TradeFeeEstimate {
        val profile = profile(platform, market, planId)
        if (profile.coverage == FeeEstimateCoverage.UNSUPPORTED) {
            return unsupportedEstimate(profile.note)
        }
        if (!tradeType.isSecurityTrade || price <= 0.0 || quantity <= 0) {
            return unsupportedEstimate(profile.note)
        }
        return when (platform) {
            BrokerPlatform.EAST_MONEY -> estimateEastMoney(market, tradeType, price, quantity, profile)
            BrokerPlatform.LONGBRIDGE -> estimateLongbridge(market, tradeType, price, quantity, profile)
            BrokerPlatform.HSBC -> estimateHsbc(market, tradeType, price, quantity, profile, context)
            BrokerPlatform.WEBULL -> estimateWebull(market, tradeType, price, quantity, profile)
            BrokerPlatform.ZHUORUI -> estimateZhuorui(market, tradeType, price, quantity, profile, context)
            else -> unsupportedEstimate(profile.note)
        }
    }

    private fun estimateEastMoney(
        market: Market,
        tradeType: TradeType,
        price: Double,
        quantity: Int,
        profile: TradeFeeProfile,
    ): TradeFeeEstimate = when (market) {
        Market.HONG_KONG -> {
            val amount = amount(price, quantity)
            val commission = bd(0)
            val platformFee = bd(15)
            val charges = hkMarketCharges(amount, settlementRule = HkSettlementRule.EAST_MONEY)
            buildEstimate(
                coverage = profile.coverage,
                market = market,
                planLabel = profile.planLabel,
                commissionComponents = listOf("佣金" to commission, "平台使用费" to platformFee),
                taxComponents = charges,
                note = profile.note,
            )
        }

        Market.US -> {
            val shareCount = bd(quantity)
            val amount = amount(price, quantity)
            var commission = max(bd("0.0049") * shareCount, bd("0.99"))
            var platformFee = max(bd("0.0050") * shareCount, bd("1.00"))
            if (price < 1.0) {
                val brokerCap = amount * bd("0.01")
                val brokerTotal = commission + platformFee
                if (brokerTotal > brokerCap) {
                    commission = bd(0)
                    platformFee = brokerCap
                }
            }
            val charges = mutableListOf<Pair<String, BigDecimal>>()
            charges += "结算费" to max(bd("0.003") * shareCount, bd("0.01"))
            if (tradeType == TradeType.SELL) {
                charges += usSection31Fee(amount)
                charges += usFinraTaf(shareCount)
            }
            buildEstimate(
                coverage = profile.coverage,
                market = market,
                planLabel = profile.planLabel,
                commissionComponents = listOf("佣金" to commission, "平台使用费" to platformFee),
                taxComponents = charges,
                note = profile.note,
            )
        }

        else -> unsupportedEstimate(profile.note)
    }

    private fun estimateLongbridge(
        market: Market,
        tradeType: TradeType,
        price: Double,
        quantity: Int,
        profile: TradeFeeProfile,
    ): TradeFeeEstimate = when (market) {
        Market.HONG_KONG -> {
            val amount = amount(price, quantity)
            val charges = hkMarketCharges(amount, settlementRule = HkSettlementRule.NONE)
            buildEstimate(
                coverage = profile.coverage,
                market = market,
                planLabel = profile.planLabel,
                commissionComponents = listOf("佣金" to bd(0), "平台费" to bd(0)),
                taxComponents = charges,
                note = profile.note,
            )
        }

        Market.US -> {
            val shareCount = bd(quantity)
            val amount = amount(price, quantity)
            val platformFee = max(bd("0.0050") * shareCount, bd("1.00"))
            val charges = mutableListOf<Pair<String, BigDecimal>>()
            if (tradeType == TradeType.SELL) {
                charges += usSection31Fee(amount)
                charges += usFinraTaf(shareCount)
            }
            buildEstimate(
                coverage = profile.coverage,
                market = market,
                planLabel = profile.planLabel,
                commissionComponents = listOf("佣金" to bd(0), "平台费" to platformFee),
                taxComponents = charges,
                note = profile.note,
            )
        }

        else -> unsupportedEstimate(profile.note)
    }

    private fun estimateHsbc(
        market: Market,
        tradeType: TradeType,
        price: Double,
        quantity: Int,
        profile: TradeFeeProfile,
        context: TradeFeeEstimateContext,
    ): TradeFeeEstimate = when (market) {
        Market.HONG_KONG -> {
            val amount = amount(price, quantity)
            val charges = hkMarketCharges(amount, settlementRule = HkSettlementRule.NONE)
            val commission = when (profile.planId) {
                PLAN_HSBC_TRADE25 -> hsbcTrade25CommissionHk(
                    standardCommission = max(amount * bd("0.0025"), bd(100)),
                    monthlyTurnoverHkdBeforeTrade = context.monthlyTurnoverHkdBeforeTrade,
                )

                else -> max(amount * bd("0.0025"), bd(100))
            }
            buildEstimate(
                coverage = profile.coverage,
                market = market,
                planLabel = profile.planLabel,
                commissionComponents = listOf("在线佣金" to commission),
                taxComponents = charges,
                note = hsbcEstimateNote(profile, context.monthlyTurnoverHkdBeforeTrade),
            )
        }

        Market.US -> {
            val amount = amount(price, quantity)
            val standardCommission = if (quantity <= 1000) {
                bd(18)
            } else {
                bd(18) + bd(quantity - 1000) * bd("0.015")
            }
            val commission = when (profile.planId) {
                PLAN_HSBC_TRADE25 -> hsbcTrade25CommissionUs(
                    standardCommission = standardCommission,
                    monthlyTurnoverHkdBeforeTrade = context.monthlyTurnoverHkdBeforeTrade,
                )

                else -> standardCommission
            }
            val charges = mutableListOf<Pair<String, BigDecimal>>()
            if (tradeType == TradeType.SELL) {
                charges += hsbcUsFinraFee(bd(quantity))
            }
            buildEstimate(
                coverage = profile.coverage,
                market = market,
                planLabel = profile.planLabel,
                commissionComponents = listOf("在线佣金" to commission),
                taxComponents = charges,
                note = hsbcEstimateNote(profile, context.monthlyTurnoverHkdBeforeTrade),
            )
        }

        else -> unsupportedEstimate(profile.note)
    }

    private fun estimateWebull(
        market: Market,
        tradeType: TradeType,
        price: Double,
        quantity: Int,
        profile: TradeFeeProfile,
    ): TradeFeeEstimate = when (market) {
        Market.HONG_KONG -> {
            val amount = amount(price, quantity)
            val charges = hkMarketCharges(amount, settlementRule = HkSettlementRule.NONE)
            buildEstimate(
                coverage = profile.coverage,
                market = market,
                planLabel = profile.planLabel,
                commissionComponents = listOf("佣金" to bd(0), "平台费" to bd(0)),
                taxComponents = charges,
                note = profile.note,
            )
        }

        Market.US -> {
            val amount = amount(price, quantity)
            val charges = mutableListOf<Pair<String, BigDecimal>>()
            if (tradeType == TradeType.SELL) {
                charges += usSection31Fee(amount)
                charges += usFinraTaf(bd(quantity))
            }
            buildEstimate(
                coverage = profile.coverage,
                market = market,
                planLabel = profile.planLabel,
                commissionComponents = listOf("佣金" to bd(0), "平台费" to bd(0)),
                taxComponents = charges,
                note = profile.note,
            )
        }

        else -> unsupportedEstimate(profile.note)
    }

    private fun estimateZhuorui(
        market: Market,
        tradeType: TradeType,
        price: Double,
        quantity: Int,
        profile: TradeFeeProfile,
        context: TradeFeeEstimateContext = TradeFeeEstimateContext(),
    ): TradeFeeEstimate = when (market) {
        Market.HONG_KONG -> {
            val amount = amount(price, quantity)
            val standardCommission = max(amount * bd("0.0003"), bd(3))
            val commission = if (isZhuoruiCommissionFree(profile, context)) bd(0) else standardCommission
            val platformFee = if (profile.planId == PLAN_ZHUORUI_LEGACY_CUSTOMER) bd(0) else bd(12)
            val charges = hkMarketCharges(amount, settlementRule = HkSettlementRule.ZHUORUI)
            buildEstimate(
                coverage = profile.coverage,
                market = market,
                planLabel = profile.planLabel,
                commissionComponents = listOf("佣金" to commission, "平台费" to platformFee),
                taxComponents = charges,
                note = profile.note,
            )
        }

        Market.US -> {
            val shareCount = bd(quantity)
            val amount = amount(price, quantity)
            val standardCommission = max(bd("0.0049") * shareCount, bd("0.99"))
            val commission = if (isZhuoruiCommissionFree(profile, context)) bd(0) else standardCommission
            val platformFee = if (profile.planId == PLAN_ZHUORUI_LEGACY_CUSTOMER) {
                bd(0)
            } else {
                max(bd("0.0049") * shareCount, bd("0.99"))
            }
            val charges = mutableListOf<Pair<String, BigDecimal>>()
            charges += "交收费" to max(bd("0.003") * shareCount, bd("0.40"))
            if (tradeType == TradeType.SELL) {
                charges += usFinraTaf(shareCount)
                charges += usSection31Fee(amount)
            }
            buildEstimate(
                coverage = profile.coverage,
                market = market,
                planLabel = profile.planLabel,
                commissionComponents = listOf("佣金" to commission, "平台费" to platformFee),
                taxComponents = charges,
                note = profile.note,
            )
        }

        Market.A_SHARE -> {
            val amount = amount(price, quantity)
            val standardCommission = max(amount * bd("0.0003"), bd(3))
            val commission = if (isZhuoruiCommissionFree(profile, context)) bd(0) else standardCommission
            val platformFee = if (profile.planId == PLAN_ZHUORUI_LEGACY_CUSTOMER) bd(0) else bd(12)
            val charges = mutableListOf<Pair<String, BigDecimal>>()
            charges += "经手费" to max(amount * bd("0.0000341"), bd("0.01"))
            charges += "证管费" to max(amount * bd("0.00002"), bd("0.01"))
            charges += "过户费" to max(amount * bd("0.00003"), bd("0.01"))
            if (tradeType == TradeType.SELL) {
                charges += "交易印花税" to max(amount * bd("0.0005"), bd("0.01"))
            }
            buildEstimate(
                coverage = profile.coverage,
                market = market,
                planLabel = profile.planLabel,
                commissionComponents = listOf("佣金" to commission, "平台费" to platformFee),
                taxComponents = charges,
                note = profile.note,
            )
        }

        else -> unsupportedEstimate(profile.note)
    }

    private fun isZhuoruiCommissionFree(
        profile: TradeFeeProfile,
        context: TradeFeeEstimateContext,
    ): Boolean {
        if (profile.planId != PLAN_ZHUORUI_NEW_CUSTOMER) return false
        val endDate = context.zhuoruiCommissionFreeEndDate ?: return false
        val tradeDate = context.tradeDate ?: return false
        return !tradeDate.isAfter(endDate)
    }

    private fun hsbcTrade25CommissionHk(
        standardCommission: BigDecimal,
        monthlyTurnoverHkdBeforeTrade: Double?,
    ): BigDecimal = if (isTrade25FreeCommission(monthlyTurnoverHkdBeforeTrade)) {
        bd(0)
    } else {
        standardCommission
    }

    private fun hsbcTrade25CommissionUs(
        standardCommission: BigDecimal,
        monthlyTurnoverHkdBeforeTrade: Double?,
    ): BigDecimal = if (isTrade25FreeCommission(monthlyTurnoverHkdBeforeTrade)) {
        bd(0)
    } else {
        standardCommission
    }

    private fun isTrade25FreeCommission(monthlyTurnoverHkdBeforeTrade: Double?): Boolean =
        (monthlyTurnoverHkdBeforeTrade ?: 0.0) < TRADE25_MONTHLY_TURNOVER_LIMIT_HKD

    private fun hsbcEstimateNote(
        profile: TradeFeeProfile,
        monthlyTurnoverHkdBeforeTrade: Double?,
    ): String {
        if (profile.planId != PLAN_HSBC_TRADE25) return profile.note
        val turnover = monthlyTurnoverHkdBeforeTrade ?: 0.0
        val turnoverLabel = hkMoney(turnover)
        return if (turnover < TRADE25_MONTHLY_TURNOVER_LIMIT_HKD) {
            "按汇丰 Trade25 估算：本单前当月累计成交额约 $turnoverLabel，仍在 HKD250,000 免佣区间内；本单未计入月费 HKD25，法定收费照常计入。"
        } else {
            "按汇丰 Trade25 估算：本单前当月累计成交额约 $turnoverLabel，已超过 HKD250,000；本单佣金按标准公开价估算，月费 HKD25 仍未分摊到单笔。"
        }
    }

    private fun hkMarketCharges(
        amount: BigDecimal,
        settlementRule: HkSettlementRule,
    ): List<Pair<String, BigDecimal>> {
        val charges = mutableListOf<Pair<String, BigDecimal>>()
        charges += "印花税" to roundUpToInteger(amount * bd("0.001"))
        charges += "交易征费" to max(amount * bd("0.000027"), bd("0.01"))
        charges += "交易费" to max(amount * bd("0.0000565"), bd("0.01"))
        charges += "会财局征费" to max(amount * bd("0.0000015"), bd("0.01"))
        when (settlementRule) {
            HkSettlementRule.ZHUORUI -> charges += "交收费" to max(amount * bd("0.000052"), bd("0.01"))
            HkSettlementRule.EAST_MONEY -> {
                val raw = amount * bd("0.00005")
                charges += "结算费" to min(max(raw, bd("5.50")).toDouble(), 200.0).toBigDecimal().setScale(2, RoundingMode.HALF_UP)
            }

            HkSettlementRule.NONE -> Unit
        }
        return charges
    }

    private fun usSection31Fee(amount: BigDecimal): Pair<String, BigDecimal> =
        "SEC/Section 31 规费" to max(amount * bd("0.0000206"), bd("0.01"))

    private fun usFinraTaf(shareCount: BigDecimal): Pair<String, BigDecimal> {
        val raw = shareCount * bd("0.000195")
        val capped = min(max(raw, bd("0.01")).toDouble(), 9.79)
        return "FINRA 交易活动费" to capped.toBigDecimal().setScale(2, RoundingMode.HALF_UP)
    }

    private fun hsbcUsFinraFee(shareCount: BigDecimal): Pair<String, BigDecimal> {
        val raw = shareCount * bd("0.000195")
        val capped = min(max(raw, bd("0.01")).toDouble(), 9.79)
        return "FINRA 交易活动费" to capped.toBigDecimal().setScale(2, RoundingMode.HALF_UP)
    }

    private fun buildEstimate(
        coverage: FeeEstimateCoverage,
        market: Market,
        planLabel: String,
        commissionComponents: List<Pair<String, BigDecimal>>,
        taxComponents: List<Pair<String, BigDecimal>>,
        note: String,
    ): TradeFeeEstimate {
        val commission = commissionComponents.sumOf { it.second.toDouble() }
        val tax = taxComponents.sumOf { it.second.toDouble() }
        val summaryPrefix = when (coverage) {
            FeeEstimateCoverage.FULL -> "已按公开费率自动填入"
            FeeEstimateCoverage.PARTIAL -> "已按公开费率估算后填入"
            FeeEstimateCoverage.UNSUPPORTED -> "暂不支持自动填入"
        }
        val detail = buildString {
            append("方案：")
            append(planLabel)
            append("。手续费 ")
            append(currency(market, commission))
            append(" = ")
            append(componentText(commissionComponents, market))
            append("；税费 ")
            append(currency(market, tax))
            append(" = ")
            append(componentText(taxComponents, market))
            append("。")
            append(note)
        }
        return TradeFeeEstimate(
            coverage = coverage,
            commission = roundMoney(commission),
            tax = roundMoney(tax),
            summary = "$summaryPrefix（$planLabel）：手续费 ${currency(market, commission)}，税费 ${currency(market, tax)}",
            detail = detail,
            canAutoApply = true,
        )
    }

    private fun componentText(components: List<Pair<String, BigDecimal>>, market: Market): String {
        if (components.isEmpty()) return "无"
        return components.joinToString(" + ") { (label, amount) ->
            "$label ${currency(market, amount.toDouble())}"
        }
    }

    private fun unsupportedEstimate(message: String): TradeFeeEstimate = TradeFeeEstimate(
        coverage = FeeEstimateCoverage.UNSUPPORTED,
        commission = 0.0,
        tax = 0.0,
        summary = message,
        detail = message,
        canAutoApply = false,
    )

    private fun resolvePlan(platform: BrokerPlatform, planId: String): TradeFeePlanOption {
        return availablePlans(platform).firstOrNull { it.id == planId }
            ?: availablePlans(platform).firstOrNull()
            ?: TradeFeePlanOption(
                id = "",
                label = "默认方案",
                description = "当前平台暂无可配置费率方案。",
            )
    }

    private fun amount(price: Double, quantity: Int): BigDecimal =
        bd(price).multiply(bd(quantity))

    private fun currency(market: Market, value: Double): String = when (market) {
        Market.HONG_KONG -> "HK$${money(value)}"
        Market.US -> "$${money(value)}"
        Market.A_SHARE, Market.CASH -> "¥${money(value)}"
    }

    private fun money(value: Double): String =
        bd(value).setScale(2, RoundingMode.HALF_UP).toPlainString()

    private fun hkMoney(value: Double): String = "HK$${money(value)}"

    private fun roundMoney(value: Double): Double =
        bd(value).setScale(2, RoundingMode.HALF_UP).toDouble()

    private fun roundUpToInteger(value: BigDecimal): BigDecimal =
        value.setScale(0, RoundingMode.UP).setScale(2, RoundingMode.HALF_UP)

    private fun max(left: BigDecimal, right: BigDecimal): BigDecimal =
        if (left >= right) left else right

    private fun bd(value: Double): BigDecimal = BigDecimal.valueOf(value)

    private fun bd(value: Int): BigDecimal = BigDecimal.valueOf(value.toLong())

    private fun bd(value: String): BigDecimal = BigDecimal(value)

    private enum class HkSettlementRule {
        NONE,
        ZHUORUI,
        EAST_MONEY,
    }
}
