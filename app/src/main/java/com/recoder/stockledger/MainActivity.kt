package com.recoder.stockledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.recoder.stockledger.ui.StockLedgerApp
import com.recoder.stockledger.ui.theme.StockLedgerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StockLedgerTheme {
                StockLedgerApp()
            }
        }
    }
}

