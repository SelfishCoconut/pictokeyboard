package org.pictokeyboard.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The colour arithmetic behind a pressed picto key (#16).
 *
 * Worth testing rather than eyeballing, because the thing it protects is not
 * "does the tile look nice pressed" but **"can the user still see the
 * pictogram"**. ARASAAC art is black line work on an opaque white tile, and the
 * press tints that tile with the category hue. Tint it too far and the content
 * of an AAC key — the word the user is reaching for — competes with its own
 * feedback.
 *
 * A wrong constant here is invisible in review and only shows up on the darker
 * half of the palette, which is exactly the kind of bug that ships.
 */
class PressedTileColorTest {

    private val white = 0xFFFFFFFF.toInt()

    /** The darkest colour in the palette is the one that endangers legibility. */
    private val navy = 0xFF1A237E.toInt()

    private fun pressedFill(hue: Int) = CategoryColors.over(white, CategoryColors.wash(hue))

    @Test
    fun `a fully transparent overlay leaves the base untouched`() {
        assertEquals(white, CategoryColors.over(white, 0x00FF0000))
    }

    @Test
    fun `a fully opaque overlay replaces the base`() {
        val red = 0xFFFF0000.toInt()
        assertEquals(red, CategoryColors.over(white, red))
    }

    @Test
    fun `the result is always opaque, whatever the overlay's alpha`() {
        // GradientDrawable is handed this value directly; a translucent result
        // would let the keyboard background show through the tile instead of
        // the white the artwork needs.
        for (alpha in 0..255) {
            val overlay = (alpha shl 24) or 0x1A237E
            assertEquals(
                "alpha=$alpha",
                0xFF,
                (CategoryColors.over(white, overlay) ushr 24) and 0xFF,
            )
        }
    }

    @Test
    fun `the pressed tile stays light enough to read black line art on`() {
        // Black text on the pressed fill must still clear WCAG AA body text.
        // If this fails, the wash is too strong and the pictogram is fighting
        // the feedback that is meant to confirm it.
        for (hue in listOf(navy, 0xFF000000.toInt(), 0xFFB71C1C.toInt(), 0xFF1B5E20.toInt())) {
            val fill = pressedFill(hue)
            assertTrue(
                "black is unreadable on the pressed fill for hue ${hue.toUInt().toString(16)}",
                Wcag.contrastRatio(0xFF000000.toInt(), fill) >= Wcag.BODY_TEXT,
            )
        }
    }

    @Test
    fun `the pressed tile is still visibly different from the resting one`() {
        // The other failure mode: a wash so faint that nothing appears to
        // happen. "A subtle answer is no answer" -- drawable/bg_key_recessive.
        val fill = pressedFill(navy)
        assertTrue("pressed fill is indistinguishable from white", fill != white)
    }
}
