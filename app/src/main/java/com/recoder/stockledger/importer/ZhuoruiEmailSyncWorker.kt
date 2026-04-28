package com.recoder.stockledger.importer

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.recoder.stockledger.StockLedgerApplication
import com.recoder.stockledger.StockLedgerPreferences
import com.recoder.stockledger.data.ZhuoruiEmailSyncConfig
import java.util.concurrent.TimeUnit

class ZhuoruiEmailSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val preferences = applicationContext.getSharedPreferences(
            StockLedgerPreferences.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        val config = ZhuoruiEmailSyncConfig(
            imapHost = preferences.getString(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_IMAP_HOST, "").orEmpty(),
            imapPort = preferences.getString(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_IMAP_PORT, "993").orEmpty(),
            account = preferences.getString(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_ACCOUNT, "").orEmpty(),
            password = preferences.getString(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_PASSWORD, "").orEmpty(),
            folder = preferences.getString(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_FOLDER, "INBOX").orEmpty().ifBlank { "INBOX" },
        )
        if (!config.isComplete()) return Result.success()

        val application = applicationContext as? StockLedgerApplication ?: return Result.failure()
        return runCatching {
            val result = application.repository.syncZhuoruiMailbox(
                config = config,
                lastSyncAtMillis = preferences.getLong(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_LAST_SYNC_AT, 0L),
            )
            val syncAt = result.latestSeenMessageAt ?: System.currentTimeMillis()
            val message = when {
                result.importedCount > 0 ->
                    "自动同步完成：新增 ${result.importedCount} 条，重复 ${result.duplicateCount} 条"
                result.duplicateCount > 0 ->
                    "自动同步完成：没有新增记录，重复 ${result.duplicateCount} 条"
                else ->
                    "自动同步完成：未发现可导入的新邮件"
            }
            preferences.edit()
                .putLong(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_LAST_SYNC_AT, syncAt)
                .putString(StockLedgerPreferences.KEY_ZHUORUI_EMAIL_LAST_SYNC_MESSAGE, message)
                .apply()
            Result.success()
        }.getOrElse { error ->
            preferences.edit()
                .putString(
                    StockLedgerPreferences.KEY_ZHUORUI_EMAIL_LAST_SYNC_MESSAGE,
                    "自动同步失败：${error.message ?: "请检查 IMAP 配置"}",
                )
                .apply()
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "zhuorui-email-sync"

        fun schedule(
            context: Context,
            config: ZhuoruiEmailSyncConfig,
        ) {
            if (!config.isComplete()) return
            val request = PeriodicWorkRequestBuilder<ZhuoruiEmailSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request,
                )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
