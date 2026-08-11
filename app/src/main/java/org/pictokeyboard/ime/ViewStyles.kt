package org.pictokeyboard.ime

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.StateSet
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.ImageViewCompat
import org.pictokeyboard.R
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

    /**
     * Repaints the six keys from [skin] (#109).
     *
     * `bg_key_recessive.xml` and `bg_key_primary.xml` already carry the right
     * *idea* — quiet by default, unmistakable when pressed, because "did my tap
     * land?" deserves an unambiguous answer. What an XML selector cannot do is
     * change colour at runtime, and high contrast has to reach the action row or
     * the mode stops at the edge of the grid.
     *
     * So the same two selectors are rebuilt here from tokens. The shape is
     * copied deliberately rather than shared with the XML: those files remain
     * the layout's defaults, drawn for the frame before this runs.
     */
    fun applyKeyColors(root: View, skin: KeyboardPalette) {
        val radius = root.resources.displayMetrics.density * KEY_CORNER_DP

        fun recessive() = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), solid(skin.accent, radius))
            addState(
                StateSet.WILD_CARD,
                framedTile(
                    colorArgb = skin.lineStrong,
                    strokeWidthPx = (root.resources.displayMetrics.density * KEY_STROKE_DP).toInt(),
                    cornerRadiusPx = radius,
                    fillArgb = skin.line,
                ),
            )
        }

        fun primary() = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), solid(skin.ink, radius))
            addState(StateSet.WILD_CARD, solid(skin.accent, radius))
        }

        // Label and glyph colours flip with the pressed face, so they are state
        // lists too -- a fixed colour would be unreadable on one of the two.
        val recessiveContent = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()),
            intArrayOf(skin.onAccent, skin.ink),
        )
        val primaryContent = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()),
            intArrayOf(skin.paper, skin.onAccent),
        )

        RECESSIVE_KEYS.forEach { id ->
            root.findViewById<View>(id)?.let { key ->
                key.background = recessive()
                when (key) {
                    is TextView -> key.setTextColor(recessiveContent)
                    is ImageView -> ImageViewCompat.setImageTintList(key, recessiveContent)
                }
            }
        }
        root.findViewById<TextView>(R.id.key_space)?.let { key ->
            key.background = primary()
            key.setTextColor(primaryContent)
        }

        // The alarm key is filled rather than outlined, and its pressed face goes
        // to ink rather than to the accent: flipping to the same blue as every
        // other key would read as "this is a normal key now", which is the one
        // thing it must never say. See drawable/bg_key_assistance.xml.
        root.findViewById<ImageView>(R.id.key_assistance)?.let { key ->
            key.background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), solid(skin.ink, radius))
                addState(StateSet.WILD_CARD, solid(skin.danger, radius))
            }
            ImageViewCompat.setImageTintList(
                key,
                ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()),
                    intArrayOf(skin.paper, skin.onDanger),
                ),
            )
        }
    }

    private fun solid(argb: Int, cornerRadiusPx: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
            setColor(argb)
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

    private const val KEY_CORNER_DP = 10f
    private const val KEY_STROKE_DP = 1.5f

    /**
     * Every key drawn with the recessive face; space is the one primary key and
     * the alarm bell is the one filled one.
     *
     * Beautify was missing from this list, so on high contrast it kept the
     * layout's default grey while the keys either side of it repainted.
     */
    private val RECESSIVE_KEYS = listOf(
        R.id.key_prev_word,
        R.id.key_next_word,
        R.id.key_backspace,
        R.id.key_beautify,
        R.id.key_speak,
        R.id.key_clear,
        R.id.key_switch,
        R.id.key_enter,
    )
}
