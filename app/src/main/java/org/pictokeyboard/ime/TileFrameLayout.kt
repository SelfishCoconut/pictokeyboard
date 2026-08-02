package org.pictokeyboard.ime

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * A FrameLayout whose height is a fixed fraction of its measured width.
 *
 * It was square — the obvious shape for square artwork, and what the grid drew
 * until the board strip and the sentence bar arrived above it (#36). Chrome has
 * to be paid for out of something, and a quarter off each tile's height buys a
 * whole extra row of words in the same space. The pictogram inside still fits
 * its own aspect, so it loses height and never shape.
 *
 * The ratio is [KeyboardMetrics.TILE_ASPECT], the same constant the height
 * budget multiplies by. If the two ever disagreed, the grid would compute room
 * for *n* rows and then draw rows of a different height — which shows up as a
 * final row sliced in half — so both read it from one place.
 */
class TileFrameLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) :
    FrameLayout(context, attrs, defStyle) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = (MeasureSpec.getSize(widthMeasureSpec) * KeyboardMetrics.TILE_ASPECT).toInt()
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
    }
}
