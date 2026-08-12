package org.pictokeyboard.ime

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.toArgb
import org.pictokeyboard.ui.theme.HIGH_CONTRAST_STROKE_SCALE
import org.pictokeyboard.ui.theme.PictoColors
import org.pictokeyboard.ui.theme.pictoColors

/**
 * The keyboard's colours, resolved from the same tokens the config app uses.
 *
 * The keyboard is a View hierarchy and the app is Compose, so they have always
 * had two representations of one palette — `values/colors.xml` and `Tokens.kt`,
 * kept in step by their names matching and by a comment asking nicely. That was
 * tolerable while both were static. High contrast (#109) makes it untenable:
 * four palettes across two type systems, and a mismatch shows up as the app
 * going pure black while the keyboard stays warm grey, on the one setting whose
 * entire purpose is that the user could not see the difference before.
 *
 * So the Kotlin tokens become the single source and this adapts them to ARGB
 * ints. `values/colors.xml` stays for what is genuinely static — the window
 * background before Compose starts, and the layout defaults this class then
 * paints over.
 *
 * **Why not a theme overlay**, which is the idiomatic Android answer: applying
 * one means rebuilding the view hierarchy, and #109 requires the keyboard to
 * repaint when the setting changes *without* being recreated. An IME's view is
 * created once and reused across every app the user types in; waiting for a
 * recreate could mean waiting until the phone is unlocked somewhere else.
 */
class KeyboardPalette(private val colors: PictoColors, val highContrast: Boolean) {

    val paper: Int = colors.paper.toArgb()
    val ink: Int = colors.ink.toArgb()
    val inkSoft: Int = colors.inkSoft.toArgb()
    val line: Int = colors.line.toArgb()
    val lineStrong: Int = colors.lineStrong.toArgb()
    val accent: Int = colors.accent.toArgb()
    val onAccent: Int = colors.onAccent.toArgb()

    /** The alarm key, and nothing else on this surface (#144). */
    val danger: Int = colors.danger.toArgb()
    val onDanger: Int = colors.onDanger.toArgb()

    /** Always white — see the KDoc on [PictoColors.tile]. Never high-contrasted. */
    val tile: Int = colors.tile.toArgb()
    val onTile: Int = colors.onTile.toArgb()

    /**
     * The caregiver's chosen frame width, widened in high contrast.
     *
     * Contrast and size are separate axes and WCAG only speaks to the first: an
     * outline can clear 3:1 and still be hard to find at 1dp for someone with
     * low acuity. Multiplied rather than replaced, so a board deliberately set
     * to a heavy frame stays heavier than one set to a light frame.
     */
    fun strokeWidth(px: Int): Int =
        if (highContrast) (px * HIGH_CONTRAST_STROKE_SCALE).toInt().coerceAtLeast(1) else px

    companion object {
        /**
         * [highContrast] comes from settings; dark mode comes from the
         * configuration of the context the keyboard is currently drawing in,
         * which is the only thing that knows whether the *host app's* window is
         * dark.
         */
        fun of(context: Context, highContrast: Boolean): KeyboardPalette {
            val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            return KeyboardPalette(pictoColors(dark = dark, highContrast = highContrast), highContrast)
        }
    }
}
