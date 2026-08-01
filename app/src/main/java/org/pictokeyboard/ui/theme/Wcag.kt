package org.pictokeyboard.ui.theme

import kotlin.math.pow

/**
 * WCAG 2.1 relative luminance and contrast ratio, over packed ARGB ints.
 *
 * Deliberately free of `android.graphics` and `androidx.core.graphics.ColorUtils`
 * so the token contrast table can be asserted by a plain JUnit test rather than
 * one that needs Robolectric to stand up a framework. Verification that is slow
 * or awkward to run is verification that stops being run.
 */
object Wcag {

    /** Body text must reach this against its background. */
    const val BODY_TEXT = 4.5

    /** Large text and the boundaries of interactive controls. */
    const val LARGE_TEXT_AND_UI = 3.0

    /**
     * The luminance at which black and white contrast equally against a colour.
     *
     * Derived, not guessed: solving `1.05 / (L + 0.05) = (L + 0.05) / 0.05`
     * gives `L = sqrt(0.0525) - 0.05`. At exactly this point both choices sit at
     * 4.58:1, so [contrastText] clears [BODY_TEXT] for *every* possible colour.
     *
     * The obvious-looking 0.5 threshold is wrong, and wrong in a way that only
     * shows up on mid-tone hues: a colour at L = 0.3 would be given white at
     * 3.0:1 when black was available at 7.0:1.
     */
    const val BLACK_WHITE_CROSSOVER = 0.1791

    /** Relative luminance of [argb]. The alpha channel is ignored. */
    fun relativeLuminance(argb: Int): Double {
        val r = channel((argb shr RED_SHIFT) and BYTE)
        val g = channel((argb shr GREEN_SHIFT) and BYTE)
        val b = channel(argb and BYTE)
        return RED_WEIGHT * r + GREEN_WEIGHT * g + BLUE_WEIGHT * b
    }

    /** Contrast ratio between two opaque colours, from 1.0 to 21.0. */
    fun contrastRatio(argb: Int, otherArgb: Int): Double {
        val a = relativeLuminance(argb)
        val b = relativeLuminance(otherArgb)
        return (maxOf(a, b) + FLARE) / (minOf(a, b) + FLARE)
    }

    /** One channel, linearised out of sRGB's transfer curve. */
    private fun channel(value: Int): Double {
        val v = value / BYTE.toDouble()
        return if (v <= KNEE) v / KNEE_SLOPE else ((v + OFFSET) / (1 + OFFSET)).pow(GAMMA)
    }

    // The coefficients of the WCAG 2.1 relative-luminance formula. Named only
    // because an unnamed constant is a lint finding; the formula is the authority.
    private const val RED_WEIGHT = 0.2126
    private const val GREEN_WEIGHT = 0.7152
    private const val BLUE_WEIGHT = 0.0722

    /** The 0.05 added to both luminances, modelling ambient screen flare. */
    private const val FLARE = 0.05

    private const val KNEE = 0.03928
    private const val KNEE_SLOPE = 12.92
    private const val OFFSET = 0.055
    private const val GAMMA = 2.4

    private const val BYTE = 0xFF
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
}
