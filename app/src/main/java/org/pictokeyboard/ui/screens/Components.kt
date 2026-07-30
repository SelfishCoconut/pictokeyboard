package org.pictokeyboard.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.pictokeyboard.data.db.BorderStyles

/** ARASAAC-style frame colour palette offered when creating categories. */
val CategoryPalette: List<Long> = listOf(
    0xFFFFC107, // amber / people
    0xFF4CAF50, // green / actions
    0xFFFF9800, // orange / food
    0xFFF44336, // red / feelings
    0xFF2196F3, // blue / places
    0xFF9C27B0, // purple / objects
    0xFF9E9E9E, // grey / time
    0xFF00BCD4, // cyan
    0xFF8BC34A, // light green
    0xFFE91E63, // pink
    0xFF3F51B5, // indigo
    0xFF795548, // brown
    0xFF009688, // teal
    0xFFFF5722, // deep orange
    0xFF607D8B, // blue grey
    0xFFFFEB3B, // yellow
    0xFFCDDC39, // lime
    0xFF673AB7, // deep purple
    0xFF03A9F4, // light blue
    0xFFFF4081, // hot pink
    0xFFB71C1C, // dark red
    0xFF1B5E20, // dark green
    0xFF4E342E, // dark brown
    0xFF455A64, // slate
    0xFFFFFFFF, // white
    0xFF000000, // black
)

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
        CategoryPalette.forEach { argb ->
            val color = Color(argb)
            val isSelected = argb.toInt() == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .background(color, CircleShape)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) Color.Black else Color(0x33000000),
                        shape = CircleShape,
                    )
                    .clickable { onSelect(argb.toInt()) },
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                        modifier = Modifier.padding(2.dp),
                    )
                }
            }
        }
    }
}

/** Spanish / English voice-and-text language selector, shared across screens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageChips(selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selected == "es",
            onClick = { onSelect("es") },
            label = { Text("Español") },
        )
        FilterChip(
            selected = selected == "en",
            onClick = { onSelect("en") },
            label = { Text("English") },
        )
    }
}

/** Opens the Android system input-method settings page. */
fun openInputMethodSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

/** Shows the keyboard picker so the user can switch to PictoKeyboard. */
fun showKeyboardPicker(context: Context) {
    (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
        .showInputMethodPicker()
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
        BorderStyles.DASHED -> PathEffect.dashPathEffect(floatArrayOf(w * 3, w * 2))
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
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .border(
                    width = if (selected == null) 3.dp else 1.dp,
                    color = if (selected == null) Color.Black else Color(0x33000000),
                    shape = CircleShape,
                )
                .clickable { onSelect(null) },
        ) {
            Icon(
                Icons.Filled.Block,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        CategoryPalette.forEach { argb ->
            val color = Color(argb)
            val isSelected = argb.toInt() == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .background(color, CircleShape)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) Color.Black else Color(0x33000000),
                        shape = CircleShape,
                    )
                    .clickable { onSelect(argb.toInt()) },
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                        modifier = Modifier.padding(2.dp),
                    )
                }
            }
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
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .categoryFrame(color, 3.dp, style, 12.dp)
                    .border(
                        width = if (isSelected) 3.dp else 0.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .clickable { onSelect(style) },
            ) {}
        }
    }
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
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .categoryFrame(color, width.dp, BorderStyles.SOLID, 12.dp)
                    .border(
                        width = if (isSelected) 3.dp else 0.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .clickable { onSelect(width) },
            ) {}
        }
    }
}
