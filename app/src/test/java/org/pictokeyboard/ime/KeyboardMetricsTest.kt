package org.pictokeyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The keyboard's height arithmetic.
 *
 * Worth testing rather than eyeballing: the slider's whole range has to stay
 * usable on the smallest screen the app supports, and there are no instrumented
 * tests in this repo to catch it if it does not.
 */
class KeyboardMetricsTest {

    // A 1080x1920 phone at 3x, with the 96dp category spine.
    private val width = 1080
    private val height = 1920
    private val strip = 288

    private fun body(columns: Int, rows: Int, w: Int = width, h: Int = height) =
        KeyboardMetrics.bodyHeightPx(w, h, strip, columns, rows)

    @Test
    fun `more rows makes a taller keyboard`() {
        assertTrue(body(columns = 4, rows = 6) > body(columns = 4, rows = 4))
    }

    @Test
    fun `the slider moves the height until the clamp binds, then holds`() {
        // 2..8 is the range SettingsSections offers. Height must never go *down*
        // as rows go up, and must keep rising until the ceiling is reached.
        //
        // It does flatten at the top, and that is deliberate rather than a
        // regression of #17: at 4 columns on a 1080x1920 screen a row is 198px,
        // so 6 rows already wants 1188px against a 1152px ceiling. Past that
        // point the extra rows exist and scroll -- they are simply not all
        // *visible*, which is the honest reading of a bounded "visible rows".
        val heights = (2..8).map { body(columns = 4, rows = it) }
        heights.zipWithNext { a, b -> assertTrue("height went down: $heights", b >= a) }

        val ceiling = (height * KeyboardMetrics.MAX_SCREEN_FRACTION).toInt()
        val rising = heights.takeWhile { it < ceiling }
        assertTrue("nothing rose before the clamp: $heights", rising.size >= 3)
        rising.zipWithNext { a, b -> assertTrue("a step did nothing: $heights", b > a) }
        assertTrue("the clamp never bound, so it is untested here", heights.last() == ceiling)
    }

    @Test
    fun `a row is as tall as a tile is wide`() {
        // (1080 - 288) / 4 = 198 per tile, so four rows is 792.
        assertEquals(792, body(columns = 4, rows = 4))
    }

    @Test
    fun `more columns makes each row shorter`() {
        assertTrue(body(columns = 6, rows = 4) < body(columns = 3, rows = 4))
    }

    @Test
    fun `the maximum setting never eats the screen`() {
        // The acceptance criterion from #17: the keyboard must leave the person a
        // view of the field they are typing into, which on an AAC keyboard is
        // half the conversation.
        val tallest = body(columns = 2, rows = 8)
        assertTrue(
            "8 rows at 2 columns took $tallest of $height",
            tallest <= (height * KeyboardMetrics.MAX_SCREEN_FRACTION).toInt(),
        )
    }

    @Test
    fun `the clamp holds on a small screen too`() {
        val small = body(columns = 2, rows = 8, w = 480, h = 800)
        assertTrue(small <= (800 * KeyboardMetrics.MAX_SCREEN_FRACTION).toInt())
    }

    @Test
    fun `the keyboard is never zero-height`() {
        // Degenerate inputs a corrupt settings store could produce. A keyboard
        // that renders as nothing is unrecoverable from the keyboard itself.
        assertTrue(body(columns = 0, rows = 0) >= 1)
        assertTrue(body(columns = 4, rows = 4, w = 0, h = 0) >= 1)
        assertTrue(body(columns = 99, rows = 1) >= 1)
    }
}
