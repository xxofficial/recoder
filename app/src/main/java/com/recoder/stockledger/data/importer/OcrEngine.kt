package com.recoder.stockledger.data.importer

import java.io.InputStream

interface OcrEngine {
    fun recognizeText(inputStream: InputStream): String?
}
