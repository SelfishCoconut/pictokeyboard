package org.pictokeyboard.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.pictokeyboard.R

/**
 * Two families, split by *who reads them*.
 *
 * **Atkinson Hyperlegible** was engineered by the Braille Institute for low
 * vision — its `I`/`l`/`1` and `b`/`d`/`p`/`q` are drawn so they cannot be
 * confused. It sets everything the person with the impairment reads: picto
 * labels, category names, the sentence bar, the blind-mode caption. It also sets
 * the app's headings, which ties the caregiver's tool visually to the surface it
 * configures.
 *
 * **Figtree** is a neutral workhorse for the caregiver's admin chrome: body copy,
 * labels, numbers, settings rows.
 *
 * The face engineered for low vision owns the user's surface; the workhorse owns
 * the admin surface. Both are bundled in `res/font` rather than fetched through
 * the Play Services font provider — the app is offline-first and must not wait on
 * a download to draw a word someone is trying to say.
 */
private val Atkinson = FontFamily(
    Font(R.font.atkinson_hyperlegible_regular, FontWeight.Normal),
    Font(R.font.atkinson_hyperlegible_bold, FontWeight.Bold),
)

private val Figtree = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_medium, FontWeight.Medium),
)

/**
 * Everything is in sp so it scales with the system font setting, and every line
 * height is generous enough that a 200% scale wraps rather than clips.
 */
internal val AppTypography = Typography(
    // Display and headline: Atkinson, bold. The app's own voice.
    displayLarge = heading(38.sp),
    displayMedium = heading(34.sp),
    displaySmall = heading(30.sp),
    headlineLarge = heading(28.sp),
    headlineMedium = heading(26.sp),
    headlineSmall = heading(24.sp),

    // Titles: Atkinson, bold. Card and section headings.
    titleLarge = heading(19.sp),
    titleMedium = heading(17.sp),
    titleSmall = heading(15.sp),

    // Body and captions: Figtree. The caregiver's reading matter.
    bodyLarge = body(16.sp),
    bodyMedium = body(15.sp),
    bodySmall = body(13.sp),

    // Labels: Figtree medium. Buttons, settings rows, numbers.
    labelLarge = label(14.sp),
    labelMedium = label(13.sp),
    labelSmall = label(12.sp),
)

/**
 * The picto caption, on the keyboard and in the board miniature.
 *
 * It rises from 12sp to 14sp and gains weight 700, and — separately but
 * inseparably — stops being `maxLines = 1` with an ellipsis. Truncating the word
 * the user is trying to say is a defect, not a layout compromise; the label wraps
 * to a second line instead.
 */
val PictoLabelStyle = TextStyle(
    fontFamily = Atkinson,
    fontWeight = FontWeight.Bold,
    fontSize = 14.sp,
    lineHeight = 17.sp,
)

private fun heading(size: androidx.compose.ui.unit.TextUnit) = TextStyle(
    fontFamily = Atkinson,
    fontWeight = FontWeight.Bold,
    fontSize = size,
    lineHeight = size * HEADING_LINE_HEIGHT,
)

private fun body(size: androidx.compose.ui.unit.TextUnit) = TextStyle(
    fontFamily = Figtree,
    fontWeight = FontWeight.Normal,
    fontSize = size,
    lineHeight = size * TEXT_LINE_HEIGHT,
)

private fun label(size: androidx.compose.ui.unit.TextUnit) = TextStyle(
    fontFamily = Figtree,
    fontWeight = FontWeight.Medium,
    fontSize = size,
    lineHeight = size * TEXT_LINE_HEIGHT,
)

private const val HEADING_LINE_HEIGHT = 1.25f
private const val TEXT_LINE_HEIGHT = 1.45f
