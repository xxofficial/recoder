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
import com.recoder.stockledger.data.ZhuoruiEmailSyncConfig
import java.util.concurrent.TimeUnit

class ZhuoruiEmailSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val application = applicationContext as? StockLedgerApplication ?: return Result.failure()
        val settingsStore = application.container.settingsStore
        val config = settingsStore.loadZhuoruiEmailSyncConfig()
        if (!config.isComplete()) return Result.success()

        return runCatching {
            val result = application.container.importRepository.syncZhuoruiMailbox(
                config = config,
                lastSyncAtMillis = settingsStore.loadZhuoruiEmailLastSyncAt(),
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
            settingsStore.saveZhuoruiEmailLastSyncAt(syncAt)
            settingsStore.saveZhuoruiEmailSyncStatusMessage(message)
            Result.success()
        }.getOrElse { error ->
            settingsStore.saveZhuoruiEmailSyncStatusMessage(
                "自动同步失败：${error.message ?: "请检查 IMAP 配置"}",
            )
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
