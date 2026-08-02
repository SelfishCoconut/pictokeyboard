package org.pictokeyboard.ui.screens

import androidx.annotation.StringRes
import org.pictokeyboard.R

// The category colour palette and its names. Split out of Components.kt when the
// names arrived: a file whose single top-level class is PaletteColor should be
// called that, and Components.kt had already become a grab-bag.

/**
 * One colour in the palette, with the name it announces.
 *
 * The name is not decoration. A colour swatch has no text and no shape to tell
 * it from its 25 neighbours, so without [nameRes] TalkBack reads every one of
 * them identically -- and this app ships a blind mode, which means a caregiver
 * who cannot see the swatches is a user the project has already decided exists.
 * In AAC the hue carries the meaning, so these are named rather than numbered.
 */
data class PaletteColor(val argb: Long, @StringRes val nameRes: Int)

/** ARASAAC-style frame colour palette offered when creating categories. */
val CategoryPalette: List<PaletteColor> = listOf(
    PaletteColor(0xFFFFC107, R.string.color_amber), // people
    PaletteColor(0xFF4CAF50, R.string.color_green), // actions
    PaletteColor(0xFFFF9800, R.string.color_orange), // food
    PaletteColor(0xFFF44336, R.string.color_red), // feelings
    PaletteColor(0xFF2196F3, R.string.color_blue), // places
    PaletteColor(0xFF9C27B0, R.string.color_purple), // objects
    PaletteColor(0xFF9E9E9E, R.string.color_grey), // time
    PaletteColor(0xFF00BCD4, R.string.color_cyan),
    PaletteColor(0xFF8BC34A, R.string.color_light_green),
    PaletteColor(0xFFE91E63, R.string.color_pink),
    PaletteColor(0xFF3F51B5, R.string.color_indigo),
    PaletteColor(0xFF795548, R.string.color_brown),
    PaletteColor(0xFF009688, R.string.color_teal),
    PaletteColor(0xFFFF5722, R.string.color_deep_orange),
    PaletteColor(0xFF607D8B, R.string.color_blue_grey),
    PaletteColor(0xFFFFEB3B, R.string.color_yellow),
    PaletteColor(0xFFCDDC39, R.string.color_lime),
    PaletteColor(0xFF673AB7, R.string.color_deep_purple),
    PaletteColor(0xFF03A9F4, R.string.color_light_blue),
    PaletteColor(0xFFFF4081, R.string.color_hot_pink),
    PaletteColor(0xFFB71C1C, R.string.color_dark_red),
    PaletteColor(0xFF1B5E20, R.string.color_dark_green),
    PaletteColor(0xFF4E342E, R.string.color_dark_brown),
    PaletteColor(0xFF455A64, R.string.color_slate),
    PaletteColor(0xFFFFFFFF, R.string.color_white),
    PaletteColor(0xFF000000, R.string.color_black),
)

/**
 * The name for an arbitrary colour value, or null if it is not one of ours.
 *
 * Used where a hue arrives from the database rather than from the picker -- a
 * category saved before a palette change, or one restored from a backup.
 */
@StringRes
fun paletteNameFor(argb: Int): Int? =
    CategoryPalette.firstOrNull { it.argb.toInt() == argb }?.nameRes
