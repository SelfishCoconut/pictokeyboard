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
 * The thesis: **the chrome goes chromatically silent so category colour can be
 * the only saturated colour in the product.** A category's hue encodes a part of
 * speech (the ARASAAC / Fitzgerald AAC code), which is real information; a brand
 * teal is not. For a user who may not read, every saturated colour that does not
 * carry meaning is noise, so chroma is spent exclusively on meaning and the
 * accent is a near-black slate rather than a hue.
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
     * mode the tiles read as white cards glowing against warm black. This is a
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
     * **Decorative only.** Dividers and unpressed key fill. It sits at 1.19:1
     * light / 1.27:1 dark, so anything the user has to *find* uses [lineStrong]
     * instead. A hairline between two rows of text carries no information and
     * may be this quiet; the same colour drawn around a control is an
     * accessibility defect — invisible to a sighted developer on a good screen,
     * and disabling on a phone in sunlight, which is where this app gets used.
     */
    val line: Color,
    /** Boundaries of interactive controls. Verified at 3:1 against [paper]. */
    val lineStrong: Color,
    /** Buttons, focus rings, switches. A slate, deliberately not a hue. */
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
 * `paper` is derived rather than chosen: exactly one step below tile white so
 * white tiles float, and near-zero chroma so it clashes with none of the 26
 * category hues.
 */
internal val LightTokens = PictoColors(
    paper = Color(0xFFF3F1ED),
    card = Color(0xFFFFFFFF),
    tile = Color(0xFFFFFFFF),
    onTile = Color(0xFF191713),
    onTileSoft = Color(0xFF6A645C),
    ink = Color(0xFF191713),
    inkSoft = Color(0xFF6A645C),
    line = Color(0xFFE2DED6),
    lineStrong = Color(0xFF8A8378),
    accent = Color(0xFF24303F),
    onAccent = Color(0xFFFFFFFF),
    danger = Color(0xFFA02B22),
    onDanger = Color(0xFFFFFFFF),
    isDark = false,
)

internal val DarkTokens = PictoColors(
    paper = Color(0xFF15130F),
    card = Color(0xFF221E19),
    // Unchanged from light on purpose -- see the KDoc on tile/onTile.
    tile = Color(0xFFFFFFFF),
    onTile = Color(0xFF191713),
    onTileSoft = Color(0xFF6A645C),
    ink = Color(0xFFF0EDE6),
    inkSoft = Color(0xFFA39C92),
    line = Color(0xFF2C2822),
    // Lifted from the design's #6E675C, which cleared 3:1 against `paper` but
    // reached only 2.96:1 against the lighter `card` -- and a control outline has
    // to hold on whichever surface it lands on, not just the darker one.
    lineStrong = Color(0xFF767065),
    accent = Color(0xFFC9D6E6),
    onAccent = Color(0xFF15130F),
    danger = Color(0xFFFFB4AB),
    onDanger = Color(0xFF15130F),
    isDark = true,
)

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
