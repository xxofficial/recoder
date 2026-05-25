@file:Repository("https://repo1.maven.org/maven2/")
@file:DependsOn("org.apache.pdfbox:pdfbox:2.0.29")

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File

fun extract(pdfPath: String, password: String?) {
    println("Extracting $pdfPath...")
    val doc = if (password != null) PDDocument.load(File(pdfPath), password) else PDDocument.load(File(pdfPath))
    doc.use {
        val stripper = PDFTextStripper()
        val text = stripper.getText(it)
        File("extracted.txt").writeText(text)
        println("Done.")
    }
}

extract("Statements/20260401-10090910-80205330-M21.pdf", "581577")
