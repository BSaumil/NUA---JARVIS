package com.nua.assistant.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val WCAG_AA_BODY = 4.5
private const val WCAG_AA_LARGE = 3.0
private const val WCAG_AAA_BODY = 7.0

private val SURFACES = mapOf(
    "background" to NuaPalette.BACKGROUND,
    "surface" to NuaPalette.SURFACE,
    "elevated surface" to NuaPalette.SURFACE_ELEVATED,
)

class NuaPaletteTest {

    // -----------------------------------------------------------------------------------
    // The spec gave exact values. These assertions exist so a "small tweak" to the palette
    // has to be a deliberate spec change, not a drift nobody notices.
    // -----------------------------------------------------------------------------------

    @Test
    fun `brand colours match the specified values exactly`() {
        assertEquals(0xFFF58C14L, NuaPalette.ORANGE)
        assertEquals(0xFF8B5CF6L, NuaPalette.VIOLET)
        assertEquals(0xFFEC4899L, NuaPalette.PINK)
    }

    @Test
    fun `dark surface and text tokens match the specified values exactly`() {
        assertEquals(0xFF08090DL, NuaPalette.BACKGROUND)
        assertEquals(0xFF11131AL, NuaPalette.SURFACE)
        assertEquals(0xFF181B24L, NuaPalette.SURFACE_ELEVATED)
        assertEquals(0xFFF5F7FAL, NuaPalette.TEXT_PRIMARY)
        assertEquals(0xFF9299A8L, NuaPalette.TEXT_SECONDARY)
    }

    @Test
    fun `status tokens match the specified values exactly`() {
        assertEquals(0xFF34D399L, NuaPalette.SUCCESS)
        assertEquals(0xFFFBBF24L, NuaPalette.WARNING)
        assertEquals(0xFFF87171L, NuaPalette.CRITICAL)
    }

    // -----------------------------------------------------------------------------------
    // Contrast. Accessibility is a stated Phase 12 requirement, so the palette is checked
    // rather than assumed — a future token change that quietly breaks legibility fails here.
    // -----------------------------------------------------------------------------------

    @Test
    fun `contrast ratio math matches known WCAG reference values`() {
        assertEquals(21.0, contrastRatio(0xFFFFFFFF, 0xFF000000), 0.01)
        assertEquals(1.0, contrastRatio(0xFF08090D, 0xFF08090D), 0.01)
    }

    @Test
    fun `primary text clears AAA on every surface`() {
        SURFACES.forEach { (name, surface) ->
            val ratio = contrastRatio(NuaPalette.TEXT_PRIMARY, surface)
            assertTrue("primary text on $name was $ratio", ratio >= WCAG_AAA_BODY)
        }
    }

    @Test
    fun `secondary text clears AA body text on every surface`() {
        SURFACES.forEach { (name, surface) ->
            val ratio = contrastRatio(NuaPalette.TEXT_SECONDARY, surface)
            assertTrue("secondary text on $name was $ratio", ratio >= WCAG_AA_BODY)
        }
    }

    @Test
    fun `status colours clear AA body text on every surface`() {
        val status = mapOf(
            "success" to NuaPalette.SUCCESS,
            "warning" to NuaPalette.WARNING,
            "critical" to NuaPalette.CRITICAL,
        )
        SURFACES.forEach { (surfaceName, surface) ->
            status.forEach { (statusName, color) ->
                val ratio = contrastRatio(color, surface)
                assertTrue("$statusName on $surfaceName was $ratio", ratio >= WCAG_AA_BODY)
            }
        }
    }

    @Test
    fun `the identity colour clears AA body text on every surface`() {
        // Orange carries NUA's identity and does appear as text, so it's held to the
        // stricter bar than the two accents below.
        SURFACES.forEach { (name, surface) ->
            val ratio = contrastRatio(NuaPalette.ORANGE, surface)
            assertTrue("orange on $name was $ratio", ratio >= WCAG_AA_BODY)
        }
    }

    @Test
    fun `accent colours clear AA large-text and UI-component contrast on every surface`() {
        // Violet and pink are accents — gradients, indicators, active states, large
        // display type — not body copy. Violet on the elevated surface sits just under
        // the 4.5 body threshold, which is exactly why they're documented as accents in
        // NuaColors.kt rather than being used for running text.
        listOf("violet" to NuaPalette.VIOLET, "pink" to NuaPalette.PINK).forEach { (name, color) ->
            SURFACES.forEach { (surfaceName, surface) ->
                val ratio = contrastRatio(color, surface)
                assertTrue("$name on $surfaceName was $ratio", ratio >= WCAG_AA_LARGE)
            }
        }
    }

    @Test
    fun `dark text is the legible choice on each brand colour`() {
        // Justifies onPrimary/onSecondary/onTertiary being the near-black background
        // rather than white in NuaTheme's Material3 mapping.
        listOf(NuaPalette.ORANGE, NuaPalette.VIOLET, NuaPalette.PINK).forEach { brand ->
            val onDark = contrastRatio(NuaPalette.BACKGROUND, brand)
            val onLight = contrastRatio(NuaPalette.TEXT_PRIMARY, brand)
            assertTrue("dark-on-brand ($onDark) should beat light-on-brand ($onLight)", onDark > onLight)
        }
    }
}
