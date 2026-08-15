package com.nua.assistant.documents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentTypeTest {

    @Test
    fun `pdf mime type maps to PDF`() {
        assertEquals(DocumentType.PDF, documentTypeForMime("application/pdf"))
    }

    @Test
    fun `docx mime type maps to WORD`() {
        assertEquals(
            DocumentType.WORD,
            documentTypeForMime("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        )
    }

    @Test
    fun `any image mime type maps to IMAGE`() {
        assertEquals(DocumentType.IMAGE, documentTypeForMime("image/jpeg"))
        assertEquals(DocumentType.IMAGE, documentTypeForMime("image/png"))
    }

    @Test
    fun `unsupported mime type maps to nothing`() {
        assertNull(documentTypeForMime("application/vnd.ms-excel"))
        assertNull(documentTypeForMime(null))
    }
}
