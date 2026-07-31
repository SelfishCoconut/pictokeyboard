package org.pictokeyboard.ui.theme

/**
 * The roles derived from a category's hue.
 *
 * The 26 palette values themselves are never touched — they are the AAC code and
 * users' saved data references those exact ARGB ints. What the redesign adds is
 * a set of *roles* built from whichever hue a category was given, so one colour
 * can flow across the whole keyboard: full saturation on the selected chip, a 6%
 * wash behind the grid, a hairline around unselected chips.
 *
 * Packed ARGB ints rather than Compose `Color`, because the keyboard is a View
 * hierarchy and the config app is Compose — both need the same answers, and this
 * is the only representation both speak. The tints carry their alpha, so they
 * composite over whatever background they are drawn on and stay correct in dark
 * mode without a second set of values.
 */
object CategoryColors {

    /** Selected category chip, and the blind-mode surface. */
    fun fill(argb: Int): Int = opaque(argb)

    /**
     * Pressed states. Heavier in dark mode: a 24% wash that reads clearly over
     * paper white is nearly invisible over warm black.
     */
    fun tint(argb: Int, dark: Boolean): Int =
        withAlpha(argb, if (dark) TINT_ALPHA_DARK else TINT_ALPHA_LIGHT)

    /** Unselected category chip fill. */
    fun tintSoft(argb: Int): Int = withAlpha(argb, TINT_SOFT_ALPHA)

    /**
     * The wash behind the picto grid — the signature of the design. Tap *Comida*
     * and the whole keyboard reads orange; tap *Acciones* and it reads green.
     * That gives a non-reading user a full-field, pre-linguistic signal of which
     * context they are in, which is the job AAC colour coding exists to do and
     * is currently spent on a 3dp frame.
     *
     * 6% is low on purpose: it has to sit under white tiles without dropping
     * their contrast, so it must register as a cast rather than a colour.
     */
    fun wash(argb: Int): Int = withAlpha(argb, WASH_ALPHA)

    /** Outline of an unselected category chip, drawn at 1.5dp. */
    fun hairline(argb: Int): Int = opaque(argb)

    /**
     * Black or white over [background], whichever the user can actually read.
     *
     * Guaranteed to clear [Wcag.BODY_TEXT] for any colour — see
     * [Wcag.BLACK_WHITE_CROSSOVER] for why the threshold is 0.179 and not the
     * 0.5 that looks right.
     */
    fun contrastText(background: Int): Int =
        if (Wcag.relativeLuminance(background) > Wcag.BLACK_WHITE_CROSSOVER) BLACK else WHITE

    private fun withAlpha(argb: Int, alpha: Int): Int =
        (argb and RGB_MASK) or (alpha shl ALPHA_SHIFT)

    private fun opaque(argb: Int): Int = argb or ALPHA_OPAQUE

    private const val RGB_MASK = 0x00FFFFFF
    private const val ALPHA_SHIFT = 24
    private const val ALPHA_OPAQUE = 0xFF shl ALPHA_SHIFT
    private const val BLACK = 0xFF000000.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    // Percentages from the design, as 8-bit alpha: 24%, 32%, 12%, 6%.
    private const val TINT_ALPHA_LIGHT = 61
    private const val TINT_ALPHA_DARK = 82
    private const val TINT_SOFT_ALPHA = 31
    private const val WASH_ALPHA = 15

    /** The hairline weight, in dp. */
    const val HAIRLINE_WIDTH_DP = 1.5f
}
