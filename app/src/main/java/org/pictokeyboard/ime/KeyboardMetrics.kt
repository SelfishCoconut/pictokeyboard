package org.pictokeyboard.ime

/**
 * How tall the picto body should be, in pixels.
 *
 * The **Visible rows (height)** slider wrote a value nobody read: the body took
 * its height from `@dimen/kb_body_height`, hardcoded at 280dp, so dragging the
 * slider from 4 to 8 changed nothing at all. `gridColumns` was read (it sets the
 * span count); `gridRows` was simply dropped.
 *
 * Rows are converted through the tile size rather than through a fixed per-row
 * dimension, because picto tiles are square and take their width from the span
 * count. A fixed row height would agree with the tiles at one column count and
 * disagree at every other, leaving either a clipped final row or a strip of dead
 * space under the grid.
 *
 * Pure and dependency-free so it can be tested, which matters here: this repo
 * has no instrumented tests, and the clamp below is the part that would
 * otherwise only be discovered by a user whose keyboard ate their screen.
 */
internal object KeyboardMetrics {

    /**
     * The most of the display the keyboard may take.
     *
     * At 8 rows on a short phone the unclamped height is most of the screen,
     * which leaves the person no view of what they are writing into -- and on an
     * AAC keyboard the field being typed into is half the conversation. 60% is
     * roughly where a tall keyboard stops being tall and starts being the whole
     * device.
     */
    const val MAX_SCREEN_FRACTION = 0.6f

    /** Never smaller than this, whatever the arithmetic says. */
    const val MIN_ROWS = 1

    /**
     * [screenWidthPx] and [screenHeightPx] are the display's, [categoryStripPx]
     * the width of the category spine down the leading edge. [columns] and
     * [rows] come straight from the settings store.
     */
    fun bodyHeightPx(
        screenWidthPx: Int,
        screenHeightPx: Int,
        categoryStripPx: Int,
        columns: Int,
        rows: Int,
    ): Int {
        // Tiles are square, so a row is exactly as tall as a tile is wide.
        val gridWidth = (screenWidthPx - categoryStripPx).coerceAtLeast(0)
        val tile = gridWidth / columns.coerceAtLeast(1)
        val desired = tile * rows.coerceAtLeast(MIN_ROWS)
        val ceiling = (screenHeightPx * MAX_SCREEN_FRACTION).toInt()
        // One row always fits, even when the ceiling is smaller than a tile --
        // a keyboard showing nothing is worse than one that is slightly too tall.
        return desired.coerceAtMost(ceiling).coerceAtLeast(minOf(tile, ceiling).coerceAtLeast(1))
    }
}
