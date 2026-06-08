package org.pictokeyboard.ime

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.core.graphics.ColorUtils
import org.pictokeyboard.data.db.BorderStyles

/** Shared drawable/colour helpers for the keyboard views. */
object ViewStyles {

    /**
     * Rounded rectangle with a coloured stroke (used for picto/category frames).
     * [borderStyle] is one of [BorderStyles]; dashed/dotted draw a patterned line.
     */
    fun framedTile(
        colorArgb: Int,
        strokeWidthPx: Int,
        cornerRadiusPx: Float,
        fillArgb: Int,
        borderStyle: String = BorderStyles.SOLID,
    ): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
            setColor(fillArgb)
            when (borderStyle) {
                BorderStyles.DASHED ->
                    setStroke(strokeWidthPx, colorArgb, (strokeWidthPx * 3).toFloat(), (strokeWidthPx * 2).toFloat())
                BorderStyles.DOTTED ->
                    setStroke(strokeWidthPx, colorArgb, strokeWidthPx.toFloat(), strokeWidthPx.toFloat())
                else -> setStroke(strokeWidthPx, colorArgb)
            }
        }

    /** A semi-transparent version of [colorArgb] for unselected category chips. */
    fun tint(colorArgb: Int, alpha: Int): Int =
        ColorUtils.setAlphaComponent(colorArgb, alpha)

    /** Returns black or white, whichever contrasts better with [background]. */
    fun contrastText(background: Int): Int {
        val luminance = ColorUtils.calculateLuminance(background)
        return if (luminance > 0.5) Color.BLACK else Color.WHITE
    }
}
