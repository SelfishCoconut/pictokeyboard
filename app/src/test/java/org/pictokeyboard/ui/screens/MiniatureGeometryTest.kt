package org.pictokeyboard.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pictokeyboard.ime.KeyboardMetrics

/**
 * The board's Layout tab draws a preview of the board being edited, and the
 * whole reason it is there is so a caregiver can judge **columns** and **rows**
 * without opening another app.
 *
 * That only holds if the preview and the keyboard agree. These pin the ways they
 * could quietly stop agreeing: the preview inventing its own height, the preview
 * ignoring the ceiling that stops a tall board eating the screen, or the scale
 * drifting so a wide board looks narrow.
 */
class MiniatureGeometryTest {

    private val screenWidth = 400
    private val screenHeight = 800

    private fun geometry(previewWidth: Int, columns: Int = 4, rows: Int = 4) = miniatureGeometry(
        previewWidthDp = previewWidth,
        screenWidthDp = screenWidth,
        screenHeightDp = screenHeight,
        columns = columns,
        rows = rows,
    )

    @Test
    fun atFullScreenWidthThePreviewIsTheKeyboardsOwnGeometry() {
        val expected = KeyboardMetrics.bodyHeightPx(
            screenWidthPx = screenWidth,
            screenHeightPx = screenHeight,
            categoryStripPx = KEYBOARD_STRIP_DP,
            columns = 4,
            rows = 4,
        )
        val geometry = geometry(previewWidth = screenWidth)
        assertEquals(expected, geometry.bodyHeightDp)
        assertEquals(KEYBOARD_STRIP_DP, geometry.spineWidthDp)
    }

    @Test
    fun halfTheWidthIsHalfTheModel() {
        val full = geometry(previewWidth = screenWidth)
        val half = geometry(previewWidth = screenWidth / 2)
        assertEquals(full.bodyHeightDp / 2, half.bodyHeightDp)
        assertEquals(full.spineWidthDp / 2, half.spineWidthDp)
    }

    @Test
    fun moreRowsMakeATallerPreview() {
        val short = geometry(previewWidth = 300, rows = 2)
        val tall = geometry(previewWidth = 300, rows = 4)
        assertTrue("4 rows should preview taller than 2", tall.bodyHeightDp > short.bodyHeightDp)
    }

    @Test
    fun theCeilingTheKeyboardObeysIsVisibleInThePreviewToo() {
        // 8 rows of 2 columns is far taller than any screen, and the keyboard
        // refuses to draw more than MAX_SCREEN_FRACTION of it. A preview that
        // showed all eight would be promising behaviour the keyboard will not
        // deliver -- in the one place a caregiver goes to check behaviour.
        val geometry = geometry(previewWidth = screenWidth, columns = 2, rows = 8)
        val ceiling = (screenHeight * KeyboardMetrics.MAX_SCREEN_FRACTION).toInt()
        assertEquals(ceiling, geometry.bodyHeightDp)
    }

    @Test
    fun aZeroWidthScreenDoesNotTakeThePreviewDown() {
        // Never true on a device; routinely true in a @Preview harness, where a
        // division by it would be a crash in the tooling rather than a bug in
        // the app -- and the tooling is where these screens are checked.
        val geometry = miniatureGeometry(
            previewWidthDp = 320,
            screenWidthDp = 0,
            screenHeightDp = 0,
            columns = 4,
            rows = 4,
        )
        assertTrue(geometry.bodyHeightDp >= 1)
        assertTrue(geometry.spineWidthDp >= 1)
    }
}
