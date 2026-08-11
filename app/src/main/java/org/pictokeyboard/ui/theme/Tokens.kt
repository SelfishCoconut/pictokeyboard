package org.pictokeyboard.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The product's colour tokens.
 *
 * The thesis: **category colour is the only saturated colour in the content.** A
 * category's hue encodes a part of speech (the ARASAAC / Fitzgerald AAC code),
 * which is real information. For a user who may not read, a saturated colour
 * that does not carry meaning is noise, so no hue competes with a category hue
 * in the grid where somebody is reading colour to find a word.
 *
 * The accent was a near-black slate on that argument, taken further than the
 * argument required: it made the *chrome* chromatically silent too. It is now a
 * blue, and the thesis is unchanged — the chrome is furniture a caregiver reads
 * words in, not a surface anybody decodes. `paper` went pure white with it, so
 * the blue lands on white and the grid it surrounds keeps its monopoly on
 * meaning-bearing colour.
 *
 * Every pair here is contrast-verified from its actual value in
 * `TokenContrastTest` — 4.5:1 for body text, 3:1 for large text and UI
 * affordances, in both schemes. That test is the contract: changing a token
 * re-runs the table, and a token that cannot pass does not ship.
 */
@Immutable
data class PictoColors(
    /** Chrome background, for both the app and the keyboard. */
    val paper: Color,
    /** The config app's raised surface. Unlike [tile] it follows the scheme. */
    val card: Color,
    /**
     * Surfaces that carry ARASAAC artwork. **Always white, in both schemes** —
     * the pictograms are black line work and a dark tile destroys them. In dark
     * mode the tiles read as white cards glowing against blue-black. This is a
     * legibility constraint, not a stylistic one, and must not be "fixed" later.
     */
    val tile: Color,
    /**
     * Content drawn on a [tile]. Scheme-invariant for the same reason the tile
     * is: with a white tile in dark mode, the dark scheme's [ink] would land at
     * 1.17:1 and vanish. Picto labels sit on the tile, so this is the token they
     * use — never [ink].
     */
    val onTile: Color,
    /** Secondary content on a [tile]; scheme-invariant, see [onTile]. */
    val onTileSoft: Color,
    /** Primary text on [paper] or [card]. */
    val ink: Color,
    /** Secondary text — captions, hints, supporting copy. */
    val inkSoft: Color,
    /**
     * **Decorative only.** Dividers and unpressed key fill. It sits at 1.27:1
     * light / 1.36:1 dark, so anything the user has to *find* uses [lineStrong]
     * instead. A hairline between two rows of text carries no information and
     * may be this quiet; the same colour drawn around a control is an
     * accessibility defect — invisible to a sighted developer on a good screen,
     * and disabling on a phone in sunlight, which is where this app gets used.
     */
    val line: Color,
    /** Boundaries of interactive controls. Verified at 3:1 against [paper]. */
    val lineStrong: Color,
    /**
     * Buttons, focus rings, switches. A blue, and confined to chrome — see the
     * thesis above for why it may not enter the pictogram grid.
     */
    val accent: Color,
    /** Content on [accent]. */
    val onAccent: Color,
    /** Destructive actions and error text. */
    val danger: Color,
    /** Content on [danger]. */
    val onDanger: Color,
    /** Lets non-Composable helpers pick the scheme-dependent category alphas. */
    val isDark: Boolean,
)

/**
 * White paper, blue chrome.
 *
 * `paper` was a warm off-white, one step below tile white so white tiles would
 * float against it. It is now pure white, and tiles are separated by their
 * outline instead — asked for directly, and the cheapest of the three ways the
 * blue could have been spent.
 *
 * **The accent is the only thing that became a hue, and the category colours are
 * untouched.** The thesis at the top of this file still holds: a category's hue
 * encodes a part of speech and is real information, so chroma in the *content*
 * area stays reserved for it. A blue button is chrome — it sits in the app's
 * furniture, where a caregiver is reading words, not in the grid where somebody
 * is reading colour to find a verb. That line is the whole reason this is a
 * blue accent on white rather than a blue wash behind everything.
 */
internal val LightTokens = PictoColors(
    paper = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    tile = Color(0xFFFFFFFF),
    onTile = Color(0xFF101828),
    onTileSoft = Color(0xFF5A6472),
    ink = Color(0xFF101828),
    inkSoft = Color(0xFF4A5568),
    line = Color(0xFFDCE5F0),
    // 4.31:1 on white. It used to clear 3:1 with little to spare against a
    // darker paper; pure white is the hardest background an outline can have,
    // so it was darkened rather than merely re-hued.
    lineStrong = Color(0xFF5A7CA6),
    // 7.14:1 against white — comfortably past the 3:1 a control owes, because
    // this also has to carry white text at body size on a filled button.
    accent = Color(0xFF1A56A8),
    onAccent = Color(0xFFFFFFFF),
    danger = Color(0xFFA02B22),
    onDanger = Color(0xFFFFFFFF),
    isDark = false,
)

/**
 * The same product at night, and the reason the blue is a *lighter* one here.
 *
 * A deep blue accent that reads well on white is close to invisible on a dark
 * chrome — #1A56A8 on this `paper` is 1.5:1. Dark mode is not the light palette
 * dimmed; the accent and its content swap ends of the scale, exactly as the
 * slate did before it.
 *
 * `paper` and `card` moved from warm black to a blue-black, so the chrome reads
 * as the same product in both schemes rather than as blue by day and brown by
 * night.
 */
internal val DarkTokens = PictoColors(
    paper = Color(0xFF0D131C),
    card = Color(0xFF161F2C),
    // Unchanged from light on purpose -- see the KDoc on tile/onTile.
    tile = Color(0xFFFFFFFF),
    onTile = Color(0xFF101828),
    onTileSoft = Color(0xFF5A6472),
    ink = Color(0xFFEDF1F7),
    inkSoft = Color(0xFFA3B0C2),
    line = Color(0xFF232E3E),
    // Verified against `card` as well as `paper` -- a control outline has to
    // hold on whichever surface it lands on, not just the darker one. 4.82:1 on
    // card, which is the binding constraint.
    lineStrong = Color(0xFF7A8CA3),
    accent = Color(0xFFA8C8EC),
    onAccent = Color(0xFF0D131C),
    danger = Color(0xFFFFB4AB),
    onDanger = Color(0xFF0D131C),
    isDark = true,
)

/**
 * The same roles, with every contrast concession withdrawn (#109).
 *
 * The default palette is deliberately soft — a `line` at 1.27:1 and an `inkSoft`
 * for supporting copy. Each of those trades contrast for calm, which is the
 * right default and the wrong only option. The users here
 * have a markedly higher rate of co-occurring visual impairment than the general
 * population, and until now the answer to "I cannot see this" was to change
 * nothing.
 *
 * Three rules, applied without exception:
 *
 *  - **`paper` and `ink` go pure.** White and black, not near-white and
 *    near-black, so every text pair on chrome sits at the maximum 21:1.
 *  - **`line` becomes `ink`.** The decorative/structural split is a luxury of a
 *    palette that can afford a hairline nobody needs to see. Here everything
 *    that bounds anything is at full strength.
 *  - **The `*Soft` tokens collapse into their full-strength siblings.** A
 *    secondary text colour *is* a contrast concession; there is nothing to
 *    soften it for.
 *
 * `tile` and `onTile` are the exception, and stay exactly as they are — they
 * are already pure white and near-black, and they are scheme-invariant for the
 * reason given on [PictoColors.tile]. Nothing here may darken a tile.
 */
internal val HighContrastLightTokens = PictoColors(
    paper = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    tile = Color(0xFFFFFFFF),
    onTile = Color(0xFF000000),
    onTileSoft = Color(0xFF000000),
    ink = Color(0xFF000000),
    inkSoft = Color(0xFF000000),
    line = Color(0xFF000000),
    lineStrong = Color(0xFF000000),
    accent = Color(0xFF000000),
    onAccent = Color(0xFFFFFFFF),
    // Still recognisably red -- "destructive" is carried by hue as well as by
    // words, and deleting a board is not a place to drop a channel. Dark enough
    // to clear body text on pure white by a wide margin.
    danger = Color(0xFF8B0000),
    onDanger = Color(0xFFFFFFFF),
    isDark = false,
)

internal val HighContrastDarkTokens = PictoColors(
    paper = Color(0xFF000000),
    card = Color(0xFF000000),
    // White on black chrome, exactly as in every other scheme. See the KDoc.
    tile = Color(0xFFFFFFFF),
    onTile = Color(0xFF000000),
    onTileSoft = Color(0xFF000000),
    ink = Color(0xFFFFFFFF),
    inkSoft = Color(0xFFFFFFFF),
    line = Color(0xFFFFFFFF),
    lineStrong = Color(0xFFFFFFFF),
    accent = Color(0xFFFFFFFF),
    onAccent = Color(0xFF000000),
    // Unchanged from the dark scheme, and that is the right answer rather than a
    // missed edit. A darker, more "serious" red was tried first and the
    // regression test caught it going 10.93:1 -> 9.20:1 -- high contrast making
    // a pair *worse*, which is the one thing this mode may never do. The gain
    // here comes from `paper` going pure black underneath it (10.93:1 -> 12.37:1),
    // not from restyling a colour that was already the lightest red still
    // reading as red.
    danger = Color(0xFFFFB4AB),
    onDanger = Color(0xFF000000),
    isDark = true,
)

/** The four palettes, chosen by scheme and by whether high contrast is on. */
fun pictoColors(dark: Boolean, highContrast: Boolean): PictoColors = when {
    highContrast && dark -> HighContrastDarkTokens
    highContrast -> HighContrastLightTokens
    dark -> DarkTokens
    else -> LightTokens
}

/**
 * How much wider frames are drawn in high contrast.
 *
 * A category frame is an outline, and an outline that clears 3:1 can still be
 * hard to see at 1dp for someone with low acuity — contrast and *size* are
 * separate axes, and WCAG only speaks to the first. The caregiver's chosen width
 * is multiplied rather than replaced, so a board set to a deliberate 8dp frame
 * stays proportionally bolder than one at 2dp.
 */
const val HIGH_CONTRAST_STROKE_SCALE = 1.5f

/**
 * The tokens with no Material 3 equivalent — [PictoColors.tile] and its content
 * colours. Everything that *does* map is also published through
 * `MaterialTheme.colorScheme`, so stock Material components inherit the palette
 * without being told about it.
 */
val LocalPictoColors: ProvidableCompositionLocal<PictoColors> =
    staticCompositionLocalOf { LightTokens }

/** Access to the tokens: `PictoTheme.colors.tile`. */
object PictoTheme {
    val colors: PictoColors
        @Composable @ReadOnlyComposable
        get() = LocalPictoColors.current
}

/**
 * The spacing scale. Inconsistent spacing reads as "unfinished" more than any
 * other single thing, so screens pick from these seven values and nothing else.
 */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /**
     * The floor for anything tappable. The icon inside may be 24dp; the target
     * may not.
     */
    val touchTarget = 48.dp
}
