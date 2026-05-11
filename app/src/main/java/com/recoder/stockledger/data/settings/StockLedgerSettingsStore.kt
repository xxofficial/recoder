package com.recoder.stockledger.data.settings

import android.content.Context
import com.recoder.stockledger.StockLedgerPreferences
import com.recoder.stockledger.data.BrokerPlatform
import com.recoder.stockledger.data.DisplayCurrency
import com.recoder.stockledger.data.PdfImportMode
import com.recoder.stockledger.data.TradeFeeEstimator
import com.recoder.stockledger.data.ZhuoruiEmailSyncConfig
import com.recoder.stockledger.data.ZhuoruiPromoConfig

interface StockLedgerSettingsStore {
    fun loadDisplayCurrency(): DisplayCurrency
    fun saveDisplayCurrency(currency: DisplayCurrency)
    fun loadSelectedPlatform(): BrokerPlatform?
    fun saveSelectedPlatform(platform: BrokerPlatform?)
    fun loadEnabledPlatforms(): List<BrokerPlatform>
    fun saveEnabledPlatforms(platforms: List<BrokerPlatform>)
    fun loadPlatformFeePlanSelections(): Map<BrokerPlatform, String>
    fun savePlatformFeePlanSelections(selections: Map<BrokerPlatform, String>)
    fun loadZhuoruiPromoConfig(): ZhuoruiPromoConfig
    fun saveZhuoruiPromoConfig(config: ZhuoruiPromoConfig)
    fun loadZhuoruiEmailSyncConfig(): ZhuoruiEmailSyncConfig
    fun saveZhuoruiEmailSyncConfig(config: ZhuoruiEmailSyncConfig)
    fun loadZhuoruiEmailAutoImportEnabled(): Boolean
    fun saveZhuoruiEmailAutoImportEnabled(enabled: Boolean)
    fun loadZhuoruiEmailSyncStatusMessage(): String?
    fun saveZhuoruiEmailSyncStatusMessage(message: String?)
    fun loadZhuoruiEmailLastSyncAt(): Long
    fun saveZhuoruiEmailLastSyncAt(timestampMillis: Long)
    fun loadZhuoruiStatementPdfPassword(): String
    fun saveZhuoruiStatementPdfPassword(password: String)
    fun loadAlibabaBailianApiKey(): String
    fun saveAlibabaBailianApiKey(key: String)
    fun loadPdfImportMode(): PdfImportMode
    fun savePdfImportMode(mode: PdfImportMode)
    fun loadTextImportModel(): String
    fun saveTextImportModel(model: String)
    fun loadLlmApiBaseUrl(): String
    fun saveLlmApiBaseUrl(url: String)
}

class SharedPreferencesStockLedgerSettingsStore(
    context: Context,
) : StockLedgerSettingsStore {
    private val preferences = context.getSharedPreferences(
        StockLedgerPreferences.PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun loadDisplayCurrency(): DisplayCurrency {
        val savedName = preferences.getString(
            StockLedgerPreferences.KEY_DISPLAY_CURRENCY,
            DisplayCurrency.CNY.name,
        )
        return DisplayCurrency.entries.firstOrNull { it.name == savedName } ?: DisplayCurrency.CNY
    }

    override fun saveDisplayCurrency(currency: DisplayCurrency) {
        preferences.edit()
            .putString(StockLedgerPreferences.KEY_DISPLAY_CURRENCY, currency.name)
            .apply()
    }

    override fun loadSelectedPlatform(): BrokerPlatform? {
        val savedName = preferences.getString(StockLedgerPreferences.KEY_SELECTED_PLATFORM, null).orEmpty()
        return BrokerPlatform.entries.firstOrNull { it.name == savedName && it.isConfigurable }
    }

    override fun saveSelectedPlatform(platform: BrokerPlatform?) {
        preferences.edit()
            .putString(StockLedgerPreferences.KEY_SELECTED_PLATFORM, platform?.name)
            .apply()
    }

    override fun loadEnabledPlatforms(): List<BrokerPlatform> {
        val saved = preferences.getStringSet(StockLedgerPreferences.KEY_ENABLED_PLATFORMS, null)
        if (saved.isNullOrEmpty()) return BrokerPlatform.configurableEntries
        return BrokerPlatform.configurableEntries.filter { it.name in saved }
            .ifEmpty { BrokerPlatform.configurableEntries }
    }

    override fun saveEnabledPlatforms(platforms: List<BrokerPlatform>) {
        preferences.edit()
            .putStringSet(
                StockLedgerPreferences.KEY_ENABLED_PLATFORMS,
                platforms.filter { it.isConfigurable }.map { it.name }.toSet(),
            )
            .apply()
    }

    override fun loadPlatformFeePlanSelections(): Map<BrokerPlatform, String> {
        val serialized = preferences.getString(
            StockLedgerPreferences.KEY_PLATFORM_FEE_PLAN_SELECTIONS,
            null,
        ).orEmpty()
        if (serialized.isBlank()) return emptyMap()
        return serialized.split("|")
            .mapNotNull { entry ->
                val separatorIndex = entry.indexOf('=')
                if (separatorIndex <= 0 || separatorIndex >= entry.lastIndex) {
                    return@mapNotNull null
                }
                val platform = BrokerPlatform.entries.firstOrNull {
                    it.name == entry.substring(0, separatorIndex)
                } ?: return@mapNotNull null
                val resolvedPlanId = TradeFeeEstimator.resolvePlanId(platform, entry.substring(separatorIndex + 1))
                if (resolvedPlanId.isBlank()) {
                    null
                } else {
                    platform to resolvedPlanId
                }
            }
            .toMap()
    }

    override fun savePlatformFeePlanSelections(selections: Map<BrokerPlatform, String>) {
        val serialized = selections.entries.joinToString("|") { (platform, planId) ->
            "${platform.name}=$planId"
        }
        preferences.edit()
            .putString(StockLedgerPreferences.KEY_PLATFORM_FEE_PLAN_SELECTIONS, serialized.ifBlank { null })
            .apply()
    }

    override fun loadZhuoruiPromoConfig(): ZhuoruiPromoConfig = ZhuoruiPromoConfig(
        startDate = preferences.getString(StockLedgerPreferences.KEY_ZHUORUI_PROMO_START_DATE, null).orEmpty(),
        durationDays = preferences.getInt(StockLedgerPreferences.KEY_ZHUORUI_PROMO_DURATION_DAYS, 100),
    )

    override fun saveZhuoruiPromoConfig(config: ZhuoruiPromoConfig) {
        preferences.edit()
            .putString(StockLedgerPreferences.KEY_ZHUORUI_PROMO_START_DATE, config.startDate)
            .putInt(StockLedgerPreferences.KEY_ZHUORUI_PROMO_DURATION_DAYS, config.durationDays)
            .apply()
    }

    override fun loadZhuoruiEmailSyncConfig(): ZhuoruiEmailSyncConfig = ZhuoruiEmailSyncConfig(
        imapHost = preferences.getString(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_IMAP_HOST, "").orEmpty(),
        imapPort = preferences.getString(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_IMAP_PORT, "993").orEmpty(),
        account = preferences.getString(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_ACCOUNT, "").orEmpty(),
        password = preferences.getString(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_PASSWORD, "").orEmpty(),
        folder = preferences.getString(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_FOLDER, "INBOX").orEmpty().ifBlank { "INBOX" },
    )

    override fun saveZhuoruiEmailSyncConfig(config: ZhuoruiEmailSyncConfig) {
        preferences.edit()
            .putString(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_IMAP_HOST, config.imapHost)
            .putString(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_IMAP_PORT, config.imapPort)
            .putString(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_ACCOUNT, config.account)
            .putString(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_PASSWORD, config.password)
            .putString(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_FOLDER, config.folder)
            .apply()
    }

    override fun loadZhuoruiEmailAutoImportEnabled(): Boolean =
        preferences.getBoolean(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_AUTO_IMPORT_ENABLED, false)

    override fun saveZhuoruiEmailAutoImportEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_AUTO_IMPORT_ENABLED, enabled)
            .apply()
    }

    override fun loadZhuoruiEmailSyncStatusMessage(): String? =
        preferences.getString(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_LAST_SYNC_MESSAGE, null)

    override fun saveZhuoruiEmailSyncStatusMessage(message: String?) {
        preferences.edit()
            .putString(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_LAST_SYNC_MESSAGE, message)
            .apply()
    }

    override fun loadZhuoruiEmailLastSyncAt(): Long =
        preferences.getLong(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_LAST_SYNC_AT, 0L)

    override fun saveZhuoruiEmailLastSyncAt(timestampMillis: Long) {
        preferences.edit()
            .putLong(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_LAST_SYNC_AT, timestampMillis)
            .apply()
    }

    override fun loadZhuoruiStatementPdfPassword(): String =
        preferences.getString(StockLedgerPreferences.KEY_ZHUORUI_STATEMENT_PDF_PASSWORD, "").orEmpty()

    override fun saveZhuoruiStatementPdfPassword(password: String) {
        preferences.edit()
            .putString(StockLedgerPreferences.KEY_ZHUORUI_STATEMENT_PDF_PASSWORD, password)
            .apply()
    }

    override fun loadAlibabaBailianApiKey(): String =
        preferences.getString(StockLedgerPreferences.KEY_ALIBABA_BAILIAN_API_KEY, "").orEmpty()

    override fun saveAlibabaBailianApiKey(key: String) {
        preferences.edit()
            .putString(StockLedgerPreferences.KEY_ALIBABA_BAILIAN_API_KEY, key)
            .apply()
    }

    override fun loadPdfImportMode(): PdfImportMode {
        val name = preferences.getString(StockLedgerPreferences.KEY_ZHUORUI_PDF_IMPORT_MODE, PdfImportMode.REGEX.name)
        return PdfImportMode.entries.firstOrNull { it.name == name } ?: PdfImportMode.REGEX
    }

    override fun savePdfImportMode(mode: PdfImportMode) {
        preferences.edit()
            .putString(StockLedgerPreferences.KEY_ZHUORUI_PDF_IMPORT_MODE, mode.name)
            .apply()
    }

    override fun loadTextImportModel(): String =
        preferences.getString(StockLedgerPreferences.KEY_TEXT_IMPORT_MODEL, "").orEmpty()

    override fun saveTextImportModel(model: String) {
        preferences.edit()
            .putString(StockLedgerPreferences.KEY_TEXT_IMPORT_MODEL, model)
            .apply()
    }

    override fun loadLlmApiBaseUrl(): String =
        preferences.getString(StockLedgerPreferences.KEY_VISION_API_BASE_URL, "").orEmpty()

    override fun saveLlmApiBaseUrl(url: String) {
        preferences.edit()
            .putString(StockLedgerPreferences.KEY_VISION_API_BASE_URL, url)
            .apply()
    }
}
