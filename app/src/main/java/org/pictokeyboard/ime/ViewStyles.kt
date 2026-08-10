package org.pictokeyboard.ime

import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.StateSet
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
     *
     * The unselected chip keeps [borderStyle] and [strokeWidthPx] -- the frame
     * settings the caregiver chose -- because dashed-versus-solid and 1dp-versus-8dp
     * are identity channels that owe nothing to colour, and dropping them from the
     * spine would have thrown away the one cue that works in greyscale. Its stroke
     * is [CategoryColors.outlineOn] rather than the raw hue, since half the palette
     * cannot reach 3:1 against `paper` on its own.
     */
    fun categoryChip(
        colorArgb: Int,
        selected: Boolean,
        backgroundArgb: Int,
        metrics: ChipMetrics,
    ): GradientDrawable =
        if (selected) {
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(CategoryColors.fill(colorArgb))
                cornerRadii = notchedCorners(metrics.cornerRadiusPx, metrics.rtl)
            }
        } else {
            framedTile(
                colorArgb = CategoryColors.outlineOn(colorArgb, backgroundArgb),
                strokeWidthPx = metrics.strokeWidthPx,
                cornerRadiusPx = metrics.cornerRadiusPx,
                fillArgb = CategoryColors.tintSoft(colorArgb),
                borderStyle = metrics.borderStyle,
            )
        }

    /**
     * A picto key with a pressed face.
     *
     * The action row already answers "did my tap land?" by flipping to the
     * accent (`drawable/bg_key_recessive.xml`), on the reasoning that for a user
     * with a tremor or poor motor control a subtle answer is no answer. The grid
     * — the part anyone actually taps — answered nothing at all.
     *
     * It cannot flip the same way. ARASAAC art is black line work on a white
     * tile, so filling the interior with a saturated hue puts black lines on
     * midnight blue for half the palette. The pictogram is the content; it stays
     * legible or the cue is worthless.
     *
     * So the press is carried by the **frame**, which is chrome, and by a wash
     * that is nowhere near the artwork's tonal range:
     *
     *  - the stroke thickens to [PRESSED_STROKE_MULTIPLIER]× and goes to the
     *    hue at full saturation
     *  - the interior takes a [CategoryColors.wash] of the same hue — 6%, enough
     *    to read as "lit" beside its neighbours, far too light to fight the art
     *
     * Both cues survive greyscale, which the fill-flip alone would not.
     */
    fun pressableTile(
        colorArgb: Int,
        strokeWidthPx: Int,
        cornerRadiusPx: Float,
        fillArgb: Int,
        borderStyle: String = BorderStyles.SOLID,
    ): StateListDrawable =
        StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_pressed),
                framedTile(
                    colorArgb = CategoryColors.fill(colorArgb),
                    strokeWidthPx = strokeWidthPx * PRESSED_STROKE_MULTIPLIER,
                    cornerRadiusPx = cornerRadiusPx,
                    fillArgb = CategoryColors.over(fillArgb, CategoryColors.wash(colorArgb)),
                    borderStyle = borderStyle,
                ),
            )
            addState(
                StateSet.WILD_CARD,
                framedTile(colorArgb, strokeWidthPx, cornerRadiusPx, fillArgb, borderStyle),
            )
        }

    /** A chip that shows what selecting it would look like, while held. */
    fun pressableChip(
        colorArgb: Int,
        selected: Boolean,
        backgroundArgb: Int,
        metrics: ChipMetrics,
    ): StateListDrawable =
        StateListDrawable().apply {
            // The pressed face of an unselected chip is its selected face. The
            // affordance and the outcome are then the same picture, which is one
            // less thing to learn.
            addState(
                intArrayOf(android.R.attr.state_pressed),
                categoryChip(colorArgb, selected = true, backgroundArgb, metrics),
            )
            addState(StateSet.WILD_CARD, categoryChip(colorArgb, selected, backgroundArgb, metrics))
        }

    /** How a chip is drawn, as opposed to what colour it is. */
    data class ChipMetrics(
        val cornerRadiusPx: Float,
        val strokeWidthPx: Int,
        val borderStyle: String = BorderStyles.SOLID,
        val rtl: Boolean = false,
    )

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

    /**
     * How much thicker the frame goes while held. Two is legible at arm's
     * length without the tile appearing to change size, which a larger jump
     * does — and an apparent size change on a grid someone is aiming at is a
     * usability problem rather than feedback.
     */
    private const val PRESSED_STROKE_MULTIPLIER = 2
}
