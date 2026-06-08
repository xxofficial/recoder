package com.recoder.stockledger.data.importer

import java.io.InputStream

interface PdfTextExtractor {
    fun extractText(inputStream: InputStream, password: String?): String
}
