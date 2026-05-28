package com.recoder.stockledger.data.importer

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.Test
import java.io.File

class HsbcStatementPdfDiagnosticTest {

    @Test
    fun extractAllHsbcPdfs() {
        val statementsDir = File("e:\\AndroidWorkSpace\\recoder\\Statements")
        if (!statementsDir.exists()) {
            println("Statements directory not found")
            return
        }

        val pdfFiles = statementsDir.listFiles { f -> f.extension.equals("pdf", ignoreCase = true) }
        if (pdfFiles == null || pdfFiles.isEmpty()) {
            println("No PDF files found in Statements")
            return
        }

        val outDir = File(statementsDir, "extracted_hsbc")
        outDir.mkdirs()

        println("Found ${pdfFiles.size} PDF files:")
        for (file in pdfFiles) {
            println("Extracting ${file.name}...")
            try {
                // Try to load without password first
                var doc: PDDocument? = null
                try {
                    doc = PDDocument.load(file)
                } catch (e: Exception) {
                    println("  Failed to load without password: ${e.message}. Trying common password...")
                    // If it requires a password, maybe there is a known password? 
                    // Let's try "581577" which was used in USmartStatementParserTest.kt
                    try {
                        doc = PDDocument.load(file, "581577")
                    } catch (e2: Exception) {
                        println("  Failed with password 581577: ${e2.message}")
                        throw e2
                    }
                }

                doc?.use { document ->
                    val stripper = PDFTextStripper().apply {
                        sortByPosition = true
                    }
                    val rawText = stripper.getText(document)
                    val txtFile = File(outDir, file.name.replace(".pdf", ".txt", ignoreCase = true))
                    txtFile.writeText(rawText, Charsets.UTF_8)
                    println("  Successfully extracted ${rawText.length} chars to ${txtFile.name}")
                    
                    // Print the first 10 lines of the extracted text
                    val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(20)
                    println("  Preview:")
                    lines.forEach { println("    $it") }
                }
            } catch (e: Exception) {
                println("  Failed to process ${file.name}: ${e.message}")
            }
        }
    }
}
