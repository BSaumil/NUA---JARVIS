package com.nua.assistant.documents

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.StringReader
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.SAXParserFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

/**
 * Extracts plain text from a .docx file's word/document.xml. A .docx is just a zip of
 * XML, and both java.util.zip and a SAX parser are part of the standard library already
 * available on Android, so no Office document parsing library is needed just to read the
 * text back out.
 */
@Singleton
class DocxTextExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun extract(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val documentXml = context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    generateSequence { zip.nextEntry }
                        .firstOrNull { it.name == "word/document.xml" }
                        ?.let { zip.readBytes().toString(Charsets.UTF_8) }
                }
            } ?: return@withContext null
            extractTextFromDocumentXml(documentXml).takeIf { it.isNotBlank() }
        } catch (t: Exception) {
            null
        }
    }
}

private class DocxTextHandler : DefaultHandler() {
    val paragraphs = StringBuilder()
    private val currentParagraph = StringBuilder()
    private var insideTextRun = false

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
        if (localPart(localName, qName) == "t") insideTextRun = true
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        if (insideTextRun) currentParagraph.append(ch, start, length)
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        val name = localPart(localName, qName)
        if (name == "t") insideTextRun = false
        if (name == "p") {
            if (currentParagraph.isNotBlank()) paragraphs.append(currentParagraph).append('\n')
            currentParagraph.clear()
        }
    }

    /** Namespace-aware parsing is off by default, so qName ("w:t") is what actually arrives — localName falls back to it. */
    private fun localPart(localName: String?, qName: String?): String? =
        localName?.takeIf { it.isNotEmpty() } ?: qName?.substringAfterLast(':')
}

/** Pure so it's testable without a real .docx file: walks word/document.xml's `<w:t>` text runs into plain text, one line per `<w:p>` paragraph. */
internal fun extractTextFromDocumentXml(xml: String): String {
    val handler = DocxTextHandler()
    val parser = SAXParserFactory.newInstance().newSAXParser()
    parser.parse(InputSource(StringReader(xml)), handler)
    return handler.paragraphs.toString().trim()
}
