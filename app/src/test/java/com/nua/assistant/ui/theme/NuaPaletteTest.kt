package com.nua.assistant.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val WCAG_AA_BODY = 4.5
private const val WCAG_AA_LARGE = 3.0
private const val WCAG_AAA_BODY = 7.0

private val SURFACES = mapOf(
    "background" to NuaPalette.OBSIDIAN,
    "surface" to NuaPalette.GRAPHITE,
    "elevated surface" to NuaPalette.GRAPHITE_ELEVATED,
)

class NuaPaletteTest {

    // -----------------------------------------------------------------------------------
    // The spec gave exact values. These assertions exist so a "small tweak" to the palette
    // has to be a deliberate spec change, not a drift nobody notices.
    // -----------------------------------------------------------------------------------

    @Test
    fun `brand colours match the specified NUA Sovereign values exactly`() {
        assertEquals(0xFF6B1738L, NuaPalette.BURGUNDY)
        assertEquals(0xFF3C0B21L, NuaPalette.DEEP_WINE)
        assertEquals(0xFF8B5CF6L, NuaPalette.VIOLET)
    }

    @Test
    fun `dark surface and text tokens match the specified values exactly`() {
        assertEquals(0xFF111113L, NuaPalette.OBSIDIAN)
        assertEquals(0xFF25252AL, NuaPalette.GRAPHITE)
        assertEquals(0xFF2F2F36L, NuaPalette.GRAPHITE_ELEVATED)
        assertEquals(0xFFF6F0E5L, NuaPalette.TEXT_PRIMARY)
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
        assertEquals(1.0, contrastRatio(0xFF111113, 0xFF111113), 0.01)
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
    fun `violet clears AA large-text and UI-component contrast on every surface`() {
        // Violet is an accent — gradients, indicators, active states, large display type
        // — not body copy. It sits just under the 4.5 body threshold on the elevated
        // surface, which is exactly why it's documented as an accent in NuaColors.kt
        // rather than being used for running text.
        SURFACES.forEach { (surfaceName, surface) ->
            val ratio = contrastRatio(NuaPalette.VIOLET, surface)
            assertTrue("violet on $surfaceName was $ratio", ratio >= WCAG_AA_LARGE)
        }
    }

    @Test
    fun `burgundy and deep wine fail as foreground colour on dark surfaces -- they are filled-surface colours, not text`() {
        // The NUA Sovereign brand refresh's one genuinely new contrast property, pinned
        // explicitly rather than left to be rediscovered by accident: unlike the prior
        // Orange identity colour (bright, legible as foreground), Burgundy and Deep Wine
        // are both dark. Neither clears even the lenient AA-large (3.0) bar directly
        // against any dark surface -- they are only ever used as a *filled* surface
        // (button/card/hero background) with light text on top, or inside a gradient
        // where Violet's higher contrast carries the far stop's visibility. See
        // NuaPalette.kt's own doc comment and NuaTheme.kt's onPrimary/onTertiary mapping.
        listOf("burgundy" to NuaPalette.BURGUNDY, "deep wine" to NuaPalette.DEEP_WINE).forEach { (name, color) ->
            SURFACES.forEach { (surfaceName, surface) ->
                val ratio = contrastRatio(color, surface)
                assertTrue("$name on $surfaceName was $ratio, expected it to stay below AA-large", ratio < WCAG_AA_LARGE)
            }
        }
    }

    @Test
    fun `light text is the legible choice on burgundy and deep wine -- the opposite of the old orange identity`() {
        listOf(NuaPalette.BURGUNDY, NuaPalette.DEEP_WINE).forEach { brand ->
            val darkOn = contrastRatio(NuaPalette.OBSIDIAN, brand)
            val lightOn = contrastRatio(NuaPalette.TEXT_PRIMARY, brand)
            assertTrue("light-on-brand ($lightOn) should beat dark-on-brand ($darkOn) for $brand", lightOn > darkOn)
        }
    }

    @Test
    fun `dark text is the legible choice on violet, unchanged from before the brand refresh`() {
        val darkOn = contrastRatio(NuaPalette.OBSIDIAN, NuaPalette.VIOLET)
        val lightOn = contrastRatio(NuaPalette.TEXT_PRIMARY, NuaPalette.VIOLET)
        assertTrue("dark-on-violet ($darkOn) should beat light-on-violet ($lightOn)", darkOn > lightOn)
    }
}
