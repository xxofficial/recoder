package com.recoder.stockledger

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class StockLedgerApplication : Application() {
    val container: AppContainer by lazy {
        AppContainer(this)
    }

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
    }
}
