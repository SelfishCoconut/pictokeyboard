package org.pictokeyboard.ime

import android.graphics.drawable.GradientDrawable
import org.pictokeyboard.data.db.BorderStyles
import org.pictokeyboard.ui.theme.CategoryColors

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

    /**
     * One category chip on the spine.
     *
     * Selection used to be carried by fill opacity alone, while every chip also
     * wore a full-weight stroke in its own colour -- so all eight categories
     * shouted equally and the selected one was hard to pick out. It is now carried
     * three ways at once, because meaning must never rest on colour alone:
     *
     *  - **fill** -- the hue at full saturation, against a 12% tint
     *  - **contrast** -- an auto-contrast label on the selected chip
     *  - **shape** -- the selected chip squares off its grid-facing corners so it
     *    reads as flowing into the board, while unselected chips stay fully
     *    rounded and separate
     *
     * The shape cue is the one that survives greyscale, a colour-vision
     * deficiency, and a photograph of the screen.
     */
    fun categoryChip(
        colorArgb: Int,
        selected: Boolean,
        cornerRadiusPx: Float,
        hairlineWidthPx: Int,
        rtl: Boolean = false,
    ): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            if (selected) {
                setColor(CategoryColors.fill(colorArgb))
                cornerRadii = notchedCorners(cornerRadiusPx, rtl)
            } else {
                setColor(CategoryColors.tintSoft(colorArgb))
                cornerRadius = cornerRadiusPx
                setStroke(hairlineWidthPx, CategoryColors.hairline(colorArgb))
            }
        }

    /**
     * Corner radii with the grid-facing pair squared off. The grid sits after the
     * spine in layout order, so that is the end side -- which mirrors under RTL.
     */
    private fun notchedCorners(radius: Float, rtl: Boolean): FloatArray =
        // topLeft, topRight, bottomRight, bottomLeft -- each an x,y pair.
        if (rtl) {
            floatArrayOf(FLAT, FLAT, radius, radius, radius, radius, FLAT, FLAT)
        } else {
            floatArrayOf(radius, radius, FLAT, FLAT, FLAT, FLAT, radius, radius)
        }

    private const val FLAT = 0f
}
