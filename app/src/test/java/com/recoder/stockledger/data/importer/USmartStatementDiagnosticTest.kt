package com.recoder.stockledger.data.importer

import org.junit.Test
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File

class USmartStatementDiagnosticTest {

    private val statementsDir = File("../Statements/uSMART")
    private val password = TestConfig.getPassword("usmart", "581577")
    private val outputDir = File("../Statements/uSMART/extracted")

    @Test
    fun `extract text from all uSMART PDFs for diagnosis`() {
        if (!statementsDir.exists()) {
            println("Statements directory not found, skipping")
            return
        }
        outputDir.mkdirs()

        val pdfFiles = statementsDir.listFiles { f -> f.extension == "pdf" }?.sortedBy { it.name } ?: return
        println("Found ${pdfFiles.size} PDF files")

        for (pdf in pdfFiles) {
            println("\n========== ${pdf.name} ==========")
            try {
                val doc = PDDocument.load(pdf, password)
                doc.use { document ->
                    if (document.isEncrypted) {
                        document.setAllSecurityToBeRemoved(true)
                    }
                    val stripper = PDFTextStripper().apply {
                        sortByPosition = true
                    }
                    val text = stripper.getText(document)
                    val outFile = File(outputDir, pdf.nameWithoutExtension + ".txt")
                    outFile.writeText(text, Charsets.UTF_8)
                    println("Pages: ${document.numberOfPages}, Text length: ${text.length}")
                    println("Saved to: ${outFile.absolutePath}")
                    
                    // Print first 100 lines
                    val lines = text.lines().filter { it.trim().isNotEmpty() }.take(100)
                    for ((idx, line) in lines.withIndex()) {
                        println("  L${idx + 1}: $line")
                    }
                }
            } catch (e: Exception) {
                println("ERROR: ${e.message}")
            }
        }
    }
}
