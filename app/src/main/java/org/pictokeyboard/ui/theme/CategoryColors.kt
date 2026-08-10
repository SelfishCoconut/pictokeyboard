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

    /**
     * [argb] adjusted until it is actually visible as an outline on [background].
     *
     * A category's raw hue cannot be trusted to bound a control. Against the light
     * `paper`, 13 of the 26 palette values fall below 3:1 — yellow reaches 1.08:1
     * and white 1.13:1 — so an unselected chip outlined in its own colour has, for
     * half the board, no edge at all. Against the dark `paper` a different 9 fail.
     * That is the primary navigation control of a keyboard used by someone who may
     * not read.
     *
     * The hue is blended toward black or white — whichever direction gains
     * contrast — by the smallest step that clears [Wcag.LARGE_TEXT_AND_UI]. Because
     * it is the smallest step, the colour still reads as that category's colour;
     * amber stays recognisably amber, it just stops being invisible.
     */
    fun outlineOn(argb: Int, background: Int): Int {
        if (Wcag.contrastRatio(argb, background) >= Wcag.LARGE_TEXT_AND_UI) return opaque(argb)
        val target = contrastText(background)
        for (step in 1..BLEND_STEPS) {
            val blended = blend(argb, target, step.toFloat() / BLEND_STEPS)
            if (Wcag.contrastRatio(blended, background) >= Wcag.LARGE_TEXT_AND_UI) return blended
        }
        // Unreachable for any real background: `target` is by construction the
        // higher-contrast of black and white, so the full blend always clears 3:1.
        return target
    }

    /**
     * [overlay] composited onto opaque [base], flattened to an opaque colour.
     *
     * Needed because the translucent helpers here — [wash], [tintSoft] — are
     * meant to sit on a *known* surface, and `GradientDrawable` given a
     * translucent fill lets whatever is behind the view show through instead.
     * On a picto tile that surface is opaque white even in dark mode, because
     * ARASAAC art is black line work, so the two must be flattened before they
     * reach the drawable rather than left to the compositor.
     */
    fun over(base: Int, overlay: Int): Int =
        blend(base, opaque(overlay), ((overlay ushr ALPHA_SHIFT) and BYTE) / MAX_ALPHA)

    /** Linear mix of two opaque colours, [amount] of the way from [from] to [to]. */
    private fun blend(from: Int, to: Int, amount: Float): Int {
        fun mix(shift: Int): Int {
            val a = (from shr shift) and BYTE
            val b = (to shr shift) and BYTE
            return (a + (b - a) * amount).toInt().coerceIn(0, BYTE)
        }
        return ALPHA_OPAQUE or
            (mix(RED_SHIFT) shl RED_SHIFT) or
            (mix(GREEN_SHIFT) shl GREEN_SHIFT) or
            mix(0)
    }

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
    private const val BYTE = 0xFF
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8

    /** Blend granularity: 5% steps, so the adjustment is as small as it can be. */
    private const val BLEND_STEPS = 20
    private const val BLACK = 0xFF000000.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    // Percentages from the design, as 8-bit alpha: 12% and 6%.
    private const val MAX_ALPHA = 255f
    private const val TINT_SOFT_ALPHA = 31
    private const val WASH_ALPHA = 15
}
