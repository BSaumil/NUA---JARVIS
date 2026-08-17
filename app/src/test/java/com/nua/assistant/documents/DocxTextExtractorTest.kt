package com.nua.assistant.documents

import org.junit.Assert.assertEquals
import org.junit.Test

class DocxTextExtractorTest {

    private fun documentXml(body: String) = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
          <w:body>$body</w:body>
        </w:document>
    """.trimIndent()

    @Test
    fun `extracts text from a single paragraph`() {
        val xml = documentXml("<w:p><w:r><w:t>Hello world</w:t></w:r></w:p>")
        assertEquals("Hello world", extractTextFromDocumentXml(xml))
    }

    @Test
    fun `joins multiple paragraphs with newlines`() {
        val xml = documentXml(
            "<w:p><w:r><w:t>First paragraph</w:t></w:r></w:p>" +
                "<w:p><w:r><w:t>Second paragraph</w:t></w:r></w:p>",
        )
        assertEquals("First paragraph\nSecond paragraph", extractTextFromDocumentXml(xml))
    }

    @Test
    fun `concatenates multiple runs within one paragraph`() {
        val xml = documentXml("<w:p><w:r><w:t>Hello </w:t></w:r><w:r><w:t>world</w:t></w:r></w:p>")
        assertEquals("Hello world", extractTextFromDocumentXml(xml))
    }

    @Test
    fun `empty paragraphs are skipped rather than producing blank lines`() {
        val xml = documentXml("<w:p></w:p><w:p><w:r><w:t>Content</w:t></w:r></w:p><w:p></w:p>")
        assertEquals("Content", extractTextFromDocumentXml(xml))
    }

    @Test
    fun `document with no text runs yields empty string`() {
        val xml = documentXml("<w:p></w:p>")
        assertEquals("", extractTextFromDocumentXml(xml))
    }
}
