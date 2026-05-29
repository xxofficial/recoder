package com.recoder.stockledger.data.importer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class ZhuoruiStatementPdfParserAndroidTest {

    @Test
    fun testHsbcParserOnAndroid() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val pdfFile = File(appContext.cacheDir, "eStatementFile_20260526113616.pdf")
        assertTrue("PDF file must exist in cache", pdfFile.exists())

        val inputStream = FileInputStream(pdfFile)
        val parsed = HsbcStatementPdfParser.parse(inputStream, null)
        println("=== PARSED TRADES ON ANDROID ===")
        parsed.forEach { t ->
            println("[${t.tradeType}] ${t.symbol} x${t.quantity} @${t.price} date=${t.tradeDate} ref=${t.tradeRef}")
        }
        val sqqqTrades = parsed.filter { it.symbol == "SQQQ" }
        assertEquals("Should parse all 4 SQQQ trades", 4, sqqqTrades.size)
    }
}
