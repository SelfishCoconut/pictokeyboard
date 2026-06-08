package org.pictokeyboard.ime

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/** A FrameLayout that forces its height to equal its measured width. */
class SquareFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }
}
