package org.pictokeyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The keyboard's height arithmetic.
 *
 * Worth testing rather than eyeballing: the slider's whole range has to stay
 * usable on the smallest screen the app supports, and the board's Layout preview
 * draws through the same function, so a mistake here is wrong in two places at
 * once and looks self-consistent in both.
 */
class KeyboardMetricsTest {

    // A 1080x1920 phone at 3x, with the 96dp category spine.
    private val screen = KeyboardMetrics.Screen(widthPx = 1080, heightPx = 1920)
    private val strip = 288

    /** Roughly the tabs + sentence bar + action row on that phone. */
    private val chrome = 400

    private fun body(
        columns: Int,
        rows: Int,
        on: KeyboardMetrics.Screen = screen,
        chromePx: Int = 0,
        captionPx: Int = 0,
    ) = KeyboardMetrics.bodyHeightPx(
        on,
        strip,
        chromePx,
        KeyboardMetrics.Grid(columns, rows, captionPx),
    )

    @Test
    fun `more rows makes a taller keyboard`() {
        assertTrue(body(columns = 4, rows = 6) > body(columns = 4, rows = 4))
    }

    @Test
    fun `the slider moves the height until the clamp binds, then holds`() {
        // 2..8 is the range the board's Layout tab offers. Height must never go
        // *down* as rows go up, and must keep rising until the ceiling is
        // reached.
        //
        // It does flatten at the top, and that is deliberate rather than a
        // regression of #17: past that point the extra rows exist and scroll --
        // they are simply not all *visible*, which is the honest reading of a
        // bounded "visible rows".
        val heights = (2..8).map { body(columns = 4, rows = it) }
        heights.zipWithNext { a, b -> assertTrue("height went down: $heights", b >= a) }

        val ceiling = (screen.heightPx * KeyboardMetrics.MAX_SCREEN_FRACTION).toInt()
        val rising = heights.takeWhile { it < ceiling }
        assertTrue("nothing rose before the clamp: $heights", rising.size >= 3)
        rising.zipWithNext { a, b -> assertTrue("a step did nothing: $heights", b > a) }
        assertTrue("the clamp never bound, so it is untested here", heights.last() == ceiling)
    }

    @Test
    fun `a row is three quarters as tall as a tile is wide`() {
        // (1080 - 288) / 4 = 198 wide, so 148 tall at 4:3, and four rows is 592.
        assertEquals(592, body(columns = 4, rows = 4))
    }

    @Test
    fun `the four to three tile buys a row in the same height`() {
        // The trade #36 asks for: chrome above the grid is paid for out of tile
        // height rather than out of rows. Four rows of 4:3 fit in less than the
        // four square rows they replaced, and the difference is most of a row.
        val square = 198 * 4
        assertTrue("4:3 rows should be shorter than square ones", body(columns = 4, rows = 4) < square)
        assertTrue("and five of them should still fit", body(columns = 4, rows = 5) <= square)
    }

    @Test
    fun `captions are part of a row, so four rows means four rows you can see`() {
        // The bug this closes: the caption sits *below* the picture, and the
        // height only ever counted pictures. Four rows of tiles with words under
        // them needs four words' worth of height too -- without it the slider
        // said 4 and the keyboard showed three and a sliver, which is #17's
        // complaint in a new place.
        val caption = 84
        val bare = body(columns = 4, rows = 4)
        val captioned = body(columns = 4, rows = 4, captionPx = caption)
        assertEquals(bare + caption * 4, captioned)
    }

    @Test
    fun `more columns makes each row shorter`() {
        assertTrue(body(columns = 6, rows = 4) < body(columns = 3, rows = 4))
    }

    @Test
    fun `chrome comes out of the ceiling, not out of the grid`() {
        // The rule #36 states: the keyboard grows, the grid does not shrink. A
        // board that already fits keeps every row when the tab strip and the
        // sentence bar appear above it -- the keyboard just asks for more screen.
        val fits = body(columns = 4, rows = 4)
        assertEquals(fits, body(columns = 4, rows = 4, chromePx = chrome))
    }

    @Test
    fun `chrome does lower the ceiling for a board that was already at it`() {
        // The other half of the same rule: growth is not unbounded. Once the
        // clamp binds, the furniture has to come out of somewhere, and the
        // 60% ceiling is what the person keeps to see what they are writing.
        val clamped = body(columns = 2, rows = 8)
        val withChrome = body(columns = 2, rows = 8, chromePx = chrome)
        assertEquals(clamped - chrome, withChrome)
    }

    @Test
    fun `the maximum setting never eats the screen`() {
        // The acceptance criterion from #17: the keyboard must leave the person a
        // view of the field they are typing into, which on an AAC keyboard is
        // half the conversation. Chrome counts towards that budget.
        val tallest = body(columns = 2, rows = 8, chromePx = chrome) + chrome
        assertTrue(
            "8 rows at 2 columns took $tallest of ${screen.heightPx}",
            tallest <= (screen.heightPx * KeyboardMetrics.MAX_SCREEN_FRACTION).toInt(),
        )
    }

    @Test
    fun `the clamp holds on a small screen too`() {
        val small = KeyboardMetrics.Screen(widthPx = 480, heightPx = 800)
        val height = body(columns = 2, rows = 8, on = small, chromePx = chrome)
        assertTrue(height + chrome <= (800 * KeyboardMetrics.MAX_SCREEN_FRACTION).toInt())
    }

    @Test
    fun `the keyboard is never zero-height`() {
        // Degenerate inputs a corrupt store could produce, plus chrome taller
        // than the whole ceiling -- a keyboard that renders as nothing is
        // unrecoverable from the keyboard itself.
        assertTrue(body(columns = 0, rows = 0) >= 1)
        assertTrue(body(columns = 4, rows = 4, on = KeyboardMetrics.Screen(0, 0)) >= 1)
        assertTrue(body(columns = 99, rows = 1) >= 1)
        assertTrue(body(columns = 4, rows = 4, chromePx = 100_000) >= 1)
    }
}
