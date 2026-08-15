package com.nua.assistant.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VisionAnalyzerTest {

    @Test
    fun `known category maps to its enum value`() {
        val analysis = parseVisionAnalysis("receipt", "A grocery receipt totaling 42 dollars.")
        assertEquals(VisionCategory.RECEIPT, analysis?.category)
    }

    @Test
    fun `category is case-insensitive`() {
        val analysis = parseVisionAnalysis("Plant", "A potted succulent.")
        assertEquals(VisionCategory.PLANT, analysis?.category)
    }

    @Test
    fun `unknown category falls back to OTHER rather than failing`() {
        val analysis = parseVisionAnalysis("spaceship", "Something unrecognizable.")
        assertEquals(VisionCategory.OTHER, analysis?.category)
    }

    @Test
    fun `blank description yields no analysis`() {
        val analysis = parseVisionAnalysis("document", "")
        assertNull(analysis)
    }
}
