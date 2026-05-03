package com.recoder.stockledger.data.importer.vision

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object PdfPageRenderer {
    private const val TAG = "PdfPageRenderer"

    /**
     * Render all pages of a PDF to JPEG-compressed byte arrays.
     *
     * @param inputStream PDF input stream. Will be consumed.
     * @param password PDF password if encrypted, null otherwise.
     * @param maxDimension Maximum width/height in pixels for the output bitmap.
     *        Default 2048 gives good readability while keeping API payload reasonable.
     * @param jpegQuality JPEG quality 0-100. Default 85.
     * @return List of page images as JPEG byte arrays.
     */
    suspend fun renderPages(
        inputStream: InputStream,
        password: String? = null,
        maxDimension: Int = 2048,
        jpegQuality: Int = 85,
    ): List<ByteArray> {
        // PdfRenderer requires a seekable file descriptor, so copy to temp file.
        val tempFile = File.createTempFile("zhuorui_stmt_", ".pdf")
        tempFile.deleteOnExit()
        FileOutputStream(tempFile).use { out ->
            inputStream.copyTo(out)
        }

        return try {
            val descriptor = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            descriptor.use { pfd ->
                val renderer = PdfRenderer(pfd)
                val images = mutableListOf<ByteArray>()
                renderer.use { pdf ->
                    for (pageIndex in 0 until pdf.pageCount) {
                        pdf.openPage(pageIndex).use { page ->
                            val scale = maxDimension.toFloat() / maxOf(page.width, page.height)
                            val width = (page.width * scale).toInt().coerceAtLeast(1)
                            val height = (page.height * scale).toInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val jpegBytes = bitmapToJpeg(bitmap, jpegQuality)
                            bitmap.recycle()
                            images.add(jpegBytes)
                            Log.d(TAG, "Rendered page ${pageIndex + 1}/${pdf.pageCount}: ${width}x${height}, ${jpegBytes.size} bytes")
                        }
                    }
                }
                images
            }
        } finally {
            tempFile.delete()
        }
    }

    private fun bitmapToJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }
}
