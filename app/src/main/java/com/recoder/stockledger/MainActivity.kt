package com.recoder.stockledger

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.recoder.stockledger.data.repository.TradeImportOutcome
import com.recoder.stockledger.ui.StockLedgerApp
import com.recoder.stockledger.ui.theme.StockLedgerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StockLedgerTheme {
                StockLedgerApp()
            }
        }
        consumeSharedNotificationText(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeSharedNotificationText(intent)
    }

    private fun consumeSharedNotificationText(incomingIntent: Intent?) {
        val sharedText = extractSharedText(incomingIntent) ?: return
        incomingIntent?.action = Intent.ACTION_MAIN
        incomingIntent?.type = null
        incomingIntent?.removeExtra(Intent.EXTRA_TEXT)
        incomingIntent?.removeExtra(Intent.EXTRA_SUBJECT)

        val repository = (application as StockLedgerApplication).repository
        lifecycleScope.launch {
            val result = repository.importSharedTradeText(sharedText)
            if (result.outcome == TradeImportOutcome.IMPORTED) {
                runCatching {
                    repository.refreshQuotesForPortfolio(repository.transactions.first())
                }
            }
            Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun extractSharedText(incomingIntent: Intent?): String? {
        if (incomingIntent?.action != Intent.ACTION_SEND) return null
        val type = incomingIntent.type.orEmpty()
        if (!type.startsWith("text/")) return null

        val combined = listOf(
            incomingIntent.getStringExtra(Intent.EXTRA_SUBJECT),
            incomingIntent.getStringExtra(Intent.EXTRA_TEXT),
        ).filterNotNull()
            .map(String::trim)
            .filter(String::isNotBlank)
            .joinToString(separator = "\n\n")

        return combined.takeIf { it.isNotBlank() }
    }
}

