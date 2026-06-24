package com.recoder.stockledger.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateRepositoryTest {
    @Test
    fun comparesSemanticVersions() {
        assertTrue(AppVersionComparator.isNewer("1.1.1", "1.1.0"))
        assertTrue(AppVersionComparator.isNewer("v1.10.0", "1.2.9"))
        assertFalse(AppVersionComparator.isNewer("v1.1.0", "1.1.0"))
        assertFalse(AppVersionComparator.isNewer("1.1.0", "1.1.0-debug"))
        assertFalse(AppVersionComparator.isNewer("not-a-version", "1.1.0"))
    }

    @Test
    fun parsesLatestReleaseAndPrefersStockLedgerApk() {
        val update = AppUpdateRepository.parseLatestRelease(
            """
            {
              "tag_name": "v1.2.0",
              "html_url": "https://github.com/xxofficial/recoder/releases/tag/v1.2.0",
              "assets": [
                {
                  "name": "notes.txt",
                  "content_type": "text/plain",
                  "browser_download_url": "https://example.com/notes.txt"
                },
                {
                  "name": "other.apk",
                  "content_type": "application/vnd.android.package-archive",
                  "browser_download_url": "https://example.com/other.apk"
                },
                {
                  "name": "StockLedger-1.2.0.apk",
                  "content_type": "application/vnd.android.package-archive",
                  "browser_download_url": "https://example.com/stockledger.apk"
                }
              ]
            }
            """.trimIndent(),
        )

        assertNotNull(update)
        assertEquals("v1.2.0", update?.tagName)
        assertEquals("1.2.0", update?.versionName)
        assertEquals("StockLedger-1.2.0.apk", update?.assetName)
        assertEquals("https://example.com/stockledger.apk", update?.downloadUrl)
    }

    @Test
    fun returnsNullWhenReleaseHasNoApk() {
        val update = AppUpdateRepository.parseLatestRelease(
            """
            {
              "tag_name": "v1.2.0",
              "html_url": "https://github.com/xxofficial/recoder/releases/tag/v1.2.0",
              "assets": [
                {
                  "name": "notes.txt",
                  "content_type": "text/plain",
                  "browser_download_url": "https://example.com/notes.txt"
                }
              ]
            }
            """.trimIndent(),
        )

        assertNull(update)
    }
}
