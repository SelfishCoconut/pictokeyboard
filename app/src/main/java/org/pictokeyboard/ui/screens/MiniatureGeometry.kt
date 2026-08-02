package org.pictokeyboard.ui.screens

import org.pictokeyboard.ime.KeyboardMetrics

/** Body height and spine width of a board miniature, in dp. */
internal data class MiniatureGeometry(val bodyHeightDp: Int, val spineWidthDp: Int)

/**
 * Width of the keyboard's category spine, as `@dimen/kb_category_width` sets it.
 *
 * A constant rather than a `dimensionResource` read, so the geometry below stays
 * a pure function a unit test can call.
 */
internal const val KEYBOARD_STRIP_DP = 96

/**
 * The keyboard's own body geometry, scaled to a preview [previewWidthDp] wide.
 *
 * Delegating to [KeyboardMetrics] rather than inventing a preview size is the
 * whole point: the two cannot then disagree, including about the ceiling that
 * stops a tall board eating the screen. A preview that ignored that ceiling
 * would show eight rows where the keyboard draws five — a promise about
 * behaviour, made in the one place a caregiver goes to check behaviour.
 *
 * Everything is in dp, which the metrics take as happily as pixels: they are
 * ratios, so the unit cancels.
 */
internal fun miniatureGeometry(
    previewWidthDp: Int,
    screenWidthDp: Int,
    screenHeightDp: Int,
    columns: Int,
    rows: Int,
): MiniatureGeometry {
    val body = KeyboardMetrics.bodyHeightPx(
        screenWidthPx = screenWidthDp,
        screenHeightPx = screenHeightDp,
        categoryStripPx = KEYBOARD_STRIP_DP,
        columns = columns,
        rows = rows,
    )
    // Guarded because a screen can never be 0dp wide but a preview harness can
    // report one, and a preview that divides by it takes the tooling down with it.
    val scale = if (screenWidthDp > 0) previewWidthDp.toFloat() / screenWidthDp else 1f
    return MiniatureGeometry(
        bodyHeightDp = (body * scale).toInt().coerceAtLeast(1),
        spineWidthDp = (KEYBOARD_STRIP_DP * scale).toInt().coerceAtLeast(1),
    )
}
