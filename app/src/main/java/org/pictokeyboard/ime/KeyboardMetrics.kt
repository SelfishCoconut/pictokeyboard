package org.pictokeyboard.ime

/**
 * How tall the picto body should be, in pixels.
 *
 * The **Visible rows** slider wrote a value nobody read: the body took its height
 * from `@dimen/kb_body_height`, hardcoded at 280dp, so dragging the slider from 4
 * to 8 changed nothing at all (#17). Rows are converted through the tile size
 * rather than through a fixed per-row dimension, because picto tiles take their
 * width from the span count. A fixed row height would agree with the tiles at one
 * column count and disagree at every other, leaving either a clipped final row or
 * a strip of dead space under the grid.
 *
 * Pure and dependency-free so it can be tested, which matters here: the clamp
 * below is the part that would otherwise only be discovered by a user whose
 * keyboard ate their screen.
 *
 * The board's Layout tab draws its preview through this too, so the model and the
 * keyboard cannot disagree about how tall a board of *n* rows is.
 */
internal object KeyboardMetrics {

    /**
     * The most of the display the keyboard may take, **chrome included**.
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
     * Tile height as a fraction of tile width — 4:3, wider than tall.
     *
     * Square tiles are the obvious choice for square artwork, and they are what
     * the keyboard drew until the board strip and the phrase keys arrived above
     * the grid (#36). Chrome has to be paid for out of something, and a quarter
     * off each row's height buys a whole extra row of words in the same space.
     * The pictogram inside still fits its own aspect, so it loses a quarter of
     * its height and none of its shape.
     *
     * One constant, so returning to square tiles is one edit rather than an
     * archaeology exercise.
     */
    const val TILE_ASPECT = 0.75f

    /** The display, or in split screen the window the keyboard opens in. */
    data class Screen(val widthPx: Int, val heightPx: Int)

    /**
     * The board's shape, and what one of its rows costs.
     *
     * [captionPx] is the word printed under each tile, and it is part of the row
     * rather than an extra: a caption sits *below* the picture, so with captions
     * on, four rows of pictures needs four captions' worth of height too.
     * Leaving it out is why "visible rows" used to mean four rows and a sliver of
     * a fifth — the setting counted pictures and the grid drew pictures with
     * words under them.
     */
    data class Grid(val columns: Int, val rows: Int, val captionPx: Int = 0)

    /**
     * Height of the picto body.
     *
     * [categoryStripPx] is the category spine down the leading edge, and
     * [chromePx] everything stacked above and below the body — the board tabs,
     * the phrase keys, the action row. Chrome is subtracted from the ceiling
     * rather than from the grid: **the keyboard grows, the grid does not
     * shrink**. Squeezing the thing the product exists for to make room for the
     * furniture around it is the wrong saving, so the keyboard simply asks for
     * more of the screen until the ceiling says no.
     */
    fun bodyHeightPx(screen: Screen, categoryStripPx: Int, chromePx: Int, grid: Grid): Int {
        val gridWidth = (screen.widthPx - categoryStripPx).coerceAtLeast(0)
        val tileWidth = gridWidth / grid.columns.coerceAtLeast(1)
        val row = (tileWidth * TILE_ASPECT).toInt() + grid.captionPx.coerceAtLeast(0)
        val desired = row * grid.rows.coerceAtLeast(MIN_ROWS)
        val ceiling = ((screen.heightPx * MAX_SCREEN_FRACTION).toInt() - chromePx).coerceAtLeast(1)
        // One row always fits, even when the ceiling is smaller than a row --
        // a keyboard showing nothing is worse than one that is slightly too tall.
        return desired.coerceAtMost(ceiling).coerceAtLeast(minOf(row, ceiling).coerceAtLeast(1))
    }
}
