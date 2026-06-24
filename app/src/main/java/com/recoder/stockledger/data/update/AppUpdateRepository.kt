package com.recoder.stockledger.data.update

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val tagName: String,
    val versionName: String,
    val releaseUrl: String,
    val assetName: String,
    val downloadUrl: String,
)

data class AppUpdateCheckResult(
    val currentVersionName: String,
    val latestUpdate: AppUpdateInfo?,
    val hasUpdate: Boolean,
)

data class AppUpdateUiState(
    val currentVersionName: String = "",
    val latestUpdate: AppUpdateInfo? = null,
    val hasUpdate: Boolean = false,
    val isChecking: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgressFraction: Float? = null,
    val downloadedApkPath: String? = null,
    val statusMessage: String? = null,
)

class AppUpdateRepository(
    private val context: Context,
    private val owner: String = "xxofficial",
    private val repo: String = "recoder",
) {
    fun currentVersionName(): String {
        @Suppress("DEPRECATION")
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return packageInfo.versionName.orEmpty().ifBlank { "0.0.0" }
    }

    fun checkLatestRelease(): AppUpdateCheckResult {
        val currentVersionName = currentVersionName()
        val latest = fetchLatestRelease()
        return AppUpdateCheckResult(
            currentVersionName = currentVersionName,
            latestUpdate = latest,
            hasUpdate = latest?.let { AppVersionComparator.isNewer(it.versionName, currentVersionName) } == true,
        )
    }

    fun downloadApk(
        update: AppUpdateInfo,
        onProgress: (Float?) -> Unit,
    ): File {
        val updatesDir = File(context.cacheDir, "updates").apply {
            mkdirs()
        }
        updatesDir.listFiles()?.forEach { file ->
            if (file.extension.equals("apk", ignoreCase = true)) {
                file.delete()
            }
        }

        val safeVersionName = update.versionName
            .ifBlank { update.tagName }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        val targetFile = File(updatesDir, "StockLedger-$safeVersionName.apk")
        val connection = (URL(update.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("User-Agent", USER_AGENT)
        }

        try {
            connection.connect()
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("APK 下载失败：HTTP $code")
            }

            val contentLength = connection.contentLengthLong.takeIf { it > 0L }
            var copied = 0L
            connection.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        copied += read
                        onProgress(contentLength?.let { copied.toFloat() / it.toFloat() })
                    }
                }
            }
            onProgress(1f)
            return targetFile
        } catch (error: Throwable) {
            targetFile.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchLatestRelease(): AppUpdateInfo? {
        val endpoint = "https://api.github.com/repos/$owner/$repo/releases/latest"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }

        try {
            connection.connect()
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) return null
            if (code !in 200..299) {
                throw IllegalStateException("检查更新失败：HTTP $code")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return parseLatestRelease(body)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 12_000
        private const val READ_TIMEOUT_MS = 20_000
        private const val USER_AGENT = "StockLedger-Android"

        fun parseLatestRelease(json: String): AppUpdateInfo? {
            val root = JSONObject(json)
            val tagName = root.optString("tag_name").trim()
            if (tagName.isBlank()) return null
            val releaseUrl = root.optString("html_url").trim()
            val assets = root.optJSONArray("assets") ?: return null

            val candidates = buildList {
                for (index in 0 until assets.length()) {
                    val asset = assets.optJSONObject(index) ?: continue
                    val name = asset.optString("name").trim()
                    val downloadUrl = asset.optString("browser_download_url").trim()
                    val contentType = asset.optString("content_type").trim()
                    val isApk = name.endsWith(".apk", ignoreCase = true) ||
                        contentType == "application/vnd.android.package-archive"
                    if (isApk && downloadUrl.isNotBlank()) {
                        add(asset)
                    }
                }
            }

            val selectedAsset = candidates
                .sortedWith(
                    compareByDescending<JSONObject> {
                        it.optString("name").startsWith("StockLedger-", ignoreCase = true)
                    }.thenBy { it.optString("name") },
                )
                .firstOrNull()
                ?: return null

            return AppUpdateInfo(
                tagName = tagName,
                versionName = tagName.trimStart('v', 'V'),
                releaseUrl = releaseUrl,
                assetName = selectedAsset.optString("name").trim(),
                downloadUrl = selectedAsset.optString("browser_download_url").trim(),
            )
        }
    }
}

object AppVersionComparator {
    fun isNewer(candidate: String, current: String): Boolean {
        val candidateParts = parseVersionParts(candidate) ?: return false
        val currentParts = parseVersionParts(current) ?: return false
        val maxSize = maxOf(candidateParts.size, currentParts.size)
        for (index in 0 until maxSize) {
            val left = candidateParts.getOrElse(index) { 0 }
            val right = currentParts.getOrElse(index) { 0 }
            if (left != right) return left > right
        }
        return false
    }

    fun parseVersionParts(value: String): List<Int>? {
        val cleaned = value.trim()
            .trimStart('v', 'V')
            .replace('-', '.')
            .replace('_', '.')
            .substringBefore('+')
        val parts = cleaned.split('.')
            .mapNotNull { part ->
                part.takeWhile { it.isDigit() }
                    .takeIf { it.isNotBlank() }
                    ?.toIntOrNull()
            }
        return parts.takeIf { it.isNotEmpty() }
    }
}
