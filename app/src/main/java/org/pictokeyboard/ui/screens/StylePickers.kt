package org.pictokeyboard.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.pictokeyboard.R
import org.pictokeyboard.data.db.BorderStyles
import org.pictokeyboard.ui.theme.CategoryColors
import org.pictokeyboard.ui.theme.PictoTheme
import org.pictokeyboard.ui.theme.Spacing

// How a picto or category frame is chosen: its colour, its stroke style and its
// thickness. Split out of Components.kt, which had become a grab-bag of pickers,
// the language selector and system-settings helpers.

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPalettePicker(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CategoryPalette.forEach { swatch ->
            ColorSwatch(
                argb = swatch.argb.toInt(),
                name = stringResource(swatch.nameRes),
                selected = swatch.argb.toInt() == selected,
                onClick = { onSelect(swatch.argb.toInt()) },
            )
        }
    }
}

/**
 * One colour in a palette picker.
 *
 * The swatch is the one place a raw hue legitimately fills a control, so its
 * outline and its check mark both have to work against any of the 26 values: the
 * outline comes from the token layer, and the check is chosen by
 * [CategoryColors.contrastText] rather than by comparing luminance against 0.5 —
 * which picked white on mid-tone hues where only black was readable.
 */
@Composable
private fun ColorSwatch(argb: Int, name: String, selected: Boolean, onClick: () -> Unit) {
    val colors = PictoTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            // selectable BEFORE padding: the pointer-input node takes the bounds
            // it is given, so putting the inset first would have shrunk the target
            // to the 40dp circle while the code still said `touchTarget`.
            .size(Spacing.touchTarget)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            // mergeDescendants so the check mark does not announce separately;
            // `selected` on the selectable above already carries the state, which
            // is what TalkBack reads after the name.
            .semantics(mergeDescendants = true) { contentDescription = name }
            .padding(Spacing.xs)
            .background(Color(argb), CircleShape)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) colors.accent else colors.lineStrong,
                shape = CircleShape,
            ),
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = Color(CategoryColors.contrastText(argb)),
                modifier = Modifier.padding(2.dp),
            )
        }
    }
}

/**
 * Draws a rounded frame around the content in [color], matching the keyboard's
 * picto frames. [style] is one of [BorderStyles] (solid/dashed/dotted).
 */
fun Modifier.categoryFrame(
    color: Color,
    widthDp: Dp,
    style: String,
    cornerRadius: Dp,
): Modifier = drawBehind {
    val w = widthDp.toPx()
    if (w <= 0f) return@drawBehind
    val effect = when (style) {
        BorderStyles.DASHED ->
            PathEffect.dashPathEffect(floatArrayOf(w * DASH_ON, w * DASH_OFF))
        BorderStyles.DOTTED -> PathEffect.dashPathEffect(floatArrayOf(w, w))
        else -> null
    }
    val inset = w / 2f
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(cornerRadius.toPx()),
        style = Stroke(width = w, pathEffect = effect),
    )
}

/** Colour palette with a leading "inherit category colour" option (null). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PictoColorPicker(
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val inherits = selected == null
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(Spacing.touchTarget)
                .selectable(
                    selected = inherits,
                    role = Role.RadioButton,
                    onClick = { onSelect(null) },
                )
                .padding(Spacing.xs)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .border(
                    width = if (inherits) 3.dp else 1.dp,
                    color = if (inherits) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    shape = CircleShape,
                ),
        ) {
            Icon(
                Icons.Filled.Block,
                contentDescription = stringResource(R.string.picto_color_inherit),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        CategoryPalette.forEach { swatch ->
            ColorSwatch(
                argb = swatch.argb.toInt(),
                name = stringResource(swatch.nameRes),
                selected = swatch.argb.toInt() == selected,
                onClick = { onSelect(swatch.argb.toInt()) },
            )
        }
    }
}

/** Solid / dashed / dotted frame-style selector, previewing each style. */
@Composable
fun BorderStylePicker(
    color: Color,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BorderStyles.ALL.forEach { style ->
            val isSelected = style == selected
            val name = stringResource(borderStyleName(style))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .background(PictoTheme.colors.tile, RoundedCornerShape(12.dp))
                    .categoryFrame(color, 3.dp, style, 12.dp)
                    .border(
                        width = if (isSelected) 3.dp else 0.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(12.dp),
                    )
                    // Was a bare `.clickable { }` over an empty body: no name, no
                    // role, no state. The whole control is drawn, so nothing but
                    // semantics can describe it.
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(style) }
                    .semantics { contentDescription = name },
            ) {}
        }
    }
}

@StringRes
private fun borderStyleName(style: String): Int = when (style) {
    BorderStyles.DASHED -> R.string.frame_style_dashed
    BorderStyles.DOTTED -> R.string.frame_style_dotted
    else -> R.string.frame_style_solid
}

/** Stroke-thickness selector (the presets in [BorderStyles.WIDTHS_DP]). */
@Composable
fun ThicknessPicker(
    color: Color,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BorderStyles.WIDTHS_DP.forEach { width ->
            val isSelected = width == selected
            val name = stringResource(thicknessName(width))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .background(PictoTheme.colors.tile, RoundedCornerShape(12.dp))
                    .categoryFrame(color, width.dp, BorderStyles.SOLID, 12.dp)
                    .border(
                        width = if (isSelected) 3.dp else 0.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(width) }
                    .semantics { contentDescription = name },
            ) {}
        }
    }
}

/**
 * Named by weight rather than by "3 dp", because the number is meaningless to
 * someone who cannot see the result and the presets are ordered by exactly this.
 * Falls through to the nearest heavier name if [BorderStyles.WIDTHS_DP] ever
 * grows, which is wrong-but-announced rather than silent.
 */
@StringRes
private fun thicknessName(widthDp: Int): Int = when {
    widthDp <= BorderStyles.WIDTHS_DP[0] -> R.string.frame_thickness_thin
    widthDp <= BorderStyles.WIDTHS_DP[1] -> R.string.frame_thickness_medium
    widthDp <= BorderStyles.WIDTHS_DP[2] -> R.string.frame_thickness_thick
    else -> R.string.frame_thickness_extra_thick
}

// Dash geometry as multiples of the stroke width, so the pattern keeps its
// proportions at any thickness. Matches ViewStyles.framedTile on the keyboard.
private const val DASH_ON = 3
private const val DASH_OFF = 2
