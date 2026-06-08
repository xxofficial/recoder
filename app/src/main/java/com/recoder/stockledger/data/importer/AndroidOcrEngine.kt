package com.recoder.stockledger.data.importer

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class AndroidOcrEngine : OcrEngine {
    private val TAG = "AndroidOcrEngine"

    override fun recognizeText(inputStream: InputStream): String? {
        var tempFile: File? = null
        try {
            // Write input stream to a temporary file because PdfRenderer requires a file descriptor
            tempFile = File.createTempFile("pdf_ocr", ".pdf")
            FileOutputStream(tempFile).use { out ->
                inputStream.copyTo(out)
            }

            val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount
            val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            val sb = StringBuilder()

            for (i in 0 until pageCount) {
                Log.d(TAG, "OCR processing page $i of $pageCount")
                val page = renderer.openPage(i)
                
                // Render at 2x resolution to improve text recognition accuracy
                val width = page.width * 2
                val height = page.height * 2
                
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val image = InputImage.fromBitmap(bitmap, 0)
                try {
                    val visionText = Tasks.await(recognizer.process(image))
                    
                    // Group and sort all OCR lines spatially to reconstruct the horizontal layout
                    val allLines = mutableListOf<com.google.mlkit.vision.text.Text.Line>()
                    for (block in visionText.textBlocks) {
                        allLines.addAll(block.lines)
                    }

                    // Sort lines primarily by vertical top coordinate
                    allLines.sortBy { it.boundingBox?.top ?: 0 }

                    // Group lines into rows with a vertical tolerance (e.g. 15 pixels at 2x resolution)
                    val groupedRows = mutableListOf<MutableList<com.google.mlkit.vision.text.Text.Line>>()
                    val yTolerance = 15
                    
                    for (line in allLines) {
                        val lineTop = line.boundingBox?.top ?: 0
                        var placed = false
                        for (row in groupedRows) {
                            val rowAvgTop = row.map { it.boundingBox?.top ?: 0 }.average().toInt()
                            if (Math.abs(lineTop - rowAvgTop) <= yTolerance) {
                                row.add(line)
                                placed = true
                                break
                            }
                        }
                        if (!placed) {
                            groupedRows.add(mutableListOf(line))
                        }
                    }

                    // Sort each row horizontally from left to right and join them
                    for (row in groupedRows) {
                        row.sortBy { it.boundingBox?.left ?: 0 }
                        val rowText = row.joinToString(" ") { it.text }
                        sb.append(rowText).append("\n")
                    }
                    sb.append("\n")
                } catch (e: Exception) {
                    Log.e(TAG, "ML Kit OCR failed on page $i", e)
                } finally {
                    bitmap.recycle()
                }
            }
            renderer.close()
            pfd.close()
            return sb.toString()
        } catch (e: Exception) {
            Log.e(TAG, "AndroidOcrEngine execution failed", e)
            return null
        } finally {
            tempFile?.delete()
        }
    }
}
