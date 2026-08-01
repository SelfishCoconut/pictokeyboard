package org.pictokeyboard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun PictoKeyboardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val tokens = if (darkTheme) DarkTokens else LightTokens
    CompositionLocalProvider(LocalPictoColors provides tokens) {
        MaterialTheme(
            colorScheme = tokens.toColorScheme(darkTheme),
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

/**
 * Publishes the tokens through Material's own roles, so every stock component --
 * `Card`, `Switch`, `TopAppBar`, `AlertDialog` -- inherits the palette without
 * being passed a colour. Without this, Material keeps drawing its default violet
 * baseline in the gaps, which is how a "redesigned" app ends up wearing two
 * palettes at once.
 *
 * There is no second or third hue to map: this design has exactly one accent and
 * it is a slate. `secondary` and `tertiary` therefore resolve to that same accent
 * or to neutral fills, rather than to colours nobody chose.
 */
internal fun PictoColors.toColorScheme(darkTheme: Boolean): ColorScheme {
    val base = if (darkTheme) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = line,
        onPrimaryContainer = ink,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = line,
        onSecondaryContainer = ink,
        tertiary = accent,
        onTertiary = onAccent,
        tertiaryContainer = line,
        onTertiaryContainer = ink,

        background = paper,
        onBackground = ink,
        surface = paper,
        onSurface = ink,
        // `card`, not `line`. Material guarantees `onSurfaceVariant` is readable on
        // `surfaceVariant`, and inkSoft on line is 4.36:1 -- just under the 4.5:1
        // that guarantee is worth. `line` keeps its job as `outlineVariant`.
        surfaceVariant = card,
        onSurfaceVariant = inkSoft,

        // Cards and sheets. Material picks between these five by elevation; they
        // all resolve to the one card surface, because a design with a single
        // raised plane should not grow five of them by accident. `Card` itself
        // reaches for `surfaceContainerHighest`, so leaving that one as `line` was
        // enough to turn every plain card beige.
        surfaceContainerLowest = card,
        surfaceContainerLow = card,
        surfaceContainer = card,
        surfaceContainerHigh = card,
        surfaceContainerHighest = card,

        // Material tints elevated surfaces with `primary`. With a slate accent
        // that turns every card faintly blue, so elevation is carried by the
        // shadow and the outline instead.
        surfaceTint = Color.Transparent,

        outline = lineStrong,
        outlineVariant = line,

        error = danger,
        onError = onDanger,
        errorContainer = line,
        onErrorContainer = danger,

        inverseSurface = ink,
        inverseOnSurface = paper,
    )
}
