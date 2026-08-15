package com.nua.assistant.documents

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_PAGES = 10
private const val RENDER_SCALE = 2

/**
 * Renders a PDF's pages to bitmaps via Android's built-in PdfRenderer (no PDF library
 * dependency needed) and hands each page to [DocumentAnalyzer.transcribePage] for
 * OCR-via-Claude-vision, since PdfRenderer only rasterizes pages — it doesn't expose the
 * PDF's text layer, and plenty of real-world PDFs (scans) don't have one anyway. Capped
 * at [MAX_PAGES] so a large PDF doesn't turn into dozens of Claude calls.
 */
@Singleton
class PdfTextExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentAnalyzer: DocumentAnalyzer,
) {
    suspend fun extract(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val pageCount = minOf(renderer.pageCount, MAX_PAGES)
                    val pageImages = (0 until pageCount).mapNotNull { index -> renderPage(renderer, index) }
                    val transcripts = pageImages.mapNotNull { base64 ->
                        documentAnalyzer.transcribePage(base64, "image/png").takeIf { it.isNotBlank() }
                    }
                    transcripts.joinToString("\n\n").takeIf { it.isNotBlank() }
                }
            }
        } catch (t: Exception) {
            null
        }
    }

    private fun renderPage(renderer: PdfRenderer, index: Int): String? = try {
        renderer.openPage(index).use { page ->
            val bitmap = Bitmap.createBitmap(page.width * RENDER_SCALE, page.height * RENDER_SCALE, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        }
    } catch (t: Exception) {
        null
    }
}
