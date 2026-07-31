package org.pictokeyboard.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import org.pictokeyboard.R
import org.pictokeyboard.data.arasaac.ArasaacOptions
import org.pictokeyboard.data.arasaac.ArasaacUrls
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.data.db.PictoEntity
import org.pictokeyboard.ui.ConfigViewModel
import java.io.File

// The dialogs behind the add-pictos screen: the ARASAAC detail sheet, the
// imported-image sheet, and borrowing pictos from another category. They live
// here so AddPictosScreen.kt stays the screen rather than the screen plus its
// four modal flows.

@Composable
internal fun PictoDetailDialog(
    arasaacId: Int,
    initialText: String,
    initialLanguage: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, ArasaacOptions, Int?) -> Unit,
) {
    var spoken by remember { mutableStateOf(initialText) }
    var label by remember { mutableStateOf(initialText) }
    var language by remember { mutableStateOf(initialLanguage) }
    var skin by remember { mutableStateOf<String?>(null) }
    var hair by remember { mutableStateOf<String?>(null) }
    var color by remember { mutableStateOf(true) }
    var frameColor by remember { mutableStateOf<Int?>(null) }

    val options = ArasaacOptions(skin = skin, hair = hair, color = color)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.picto_edit_details)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AsyncImage(
                    model = ArasaacUrls.customizedOrPlain(arasaacId, options),
                    contentDescription = null,
                    modifier = Modifier
                        .size(120.dp)
                        .align(Alignment.CenterHorizontally)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .padding(8.dp),
                )
                OutlinedTextField(
                    value = spoken,
                    onValueChange = { spoken = it },
                    label = { Text(stringResource(R.string.picto_spoken_text)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.picto_label)) },
                    singleLine = true,
                )
                LanguageChips(language) { language = it }

                Text(stringResource(R.string.picto_frame_color), style = MaterialTheme.typography.labelLarge)
                PictoColorPicker(selected = frameColor, onSelect = { frameColor = it })

                Text(stringResource(R.string.picto_customize), style = MaterialTheme.typography.labelLarge)
                SwatchRow(
                    label = stringResource(R.string.picto_skin),
                    values = ArasaacOptions.SKIN_TONES,
                    selected = skin,
                    colorFor = ::skinSwatch,
                    onSelect = { skin = it },
                )
                SwatchRow(
                    label = stringResource(R.string.picto_hair),
                    values = ArasaacOptions.HAIR_COLORS,
                    selected = hair,
                    colorFor = ::hairSwatch,
                    onSelect = { hair = it },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.picto_color), modifier = Modifier.weight(1f))
                    Switch(checked = color, onCheckedChange = { color = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (spoken.isNotBlank()) onConfirm(spoken.trim(), label.trim(), language, options, frameColor)
                },
                enabled = spoken.isNotBlank(),
            ) { Text(stringResource(R.string.add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun SwatchRow(
    label: String,
    values: List<String>,
    selected: String?,
    colorFor: (String) -> Color,
    onSelect: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Swatch(fill = MaterialTheme.colorScheme.surfaceVariant, isSelected = selected == null, isNone = true) {
                onSelect(null)
            }
            values.forEach { value ->
                Swatch(fill = colorFor(value), isSelected = selected == value, isNone = false) {
                    onSelect(value)
                }
            }
        }
    }
}

@Composable
private fun Swatch(fill: Color, isSelected: Boolean, isNone: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .background(fill, CircleShape)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else SWATCH_EDGE,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    ) {
        if (isNone) {
            Text("—", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun CustomImageDialog(
    bitmap: Bitmap,
    initialLanguage: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Int?) -> Unit,
) {
    var spoken by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var language by remember { mutableStateOf(initialLanguage) }
    var frameColor by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_image_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(120.dp)
                        .align(Alignment.CenterHorizontally)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .padding(8.dp),
                )
                OutlinedTextField(
                    value = spoken,
                    onValueChange = { spoken = it },
                    label = { Text(stringResource(R.string.picto_spoken_text)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.picto_label)) },
                    singleLine = true,
                )
                LanguageChips(language) { language = it }
                Text(stringResource(R.string.picto_frame_color), style = MaterialTheme.typography.labelLarge)
                PictoColorPicker(selected = frameColor, onSelect = { frameColor = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (spoken.isNotBlank()) onConfirm(spoken.trim(), label.trim(), language, frameColor) },
                enabled = spoken.isNotBlank(),
            ) { Text(stringResource(R.string.add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/**
 * Lets the admin borrow pictos from other categories into the current one. Each
 * chosen picto is copied keeping its original category's colour, so it stays
 * recognisable on the board.
 */
@Composable
internal fun ImportFromCategoriesDialog(
    viewModel: ConfigViewModel,
    currentCategoryId: String,
    onDismiss: () -> Unit,
    onAdd: (List<Pair<PictoEntity, Int>>) -> Unit,
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val others = categories.filter { it.id != currentCategoryId }
    val groups by produceState(initialValue = emptyList<Pair<CategoryEntity, List<PictoEntity>>>(), others) {
        value = others.map { it to viewModel.pictosOnce(it.id) }.filter { it.second.isNotEmpty() }
    }
    val selectedIds = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_from_categories_title)) },
        text = {
            if (groups.isEmpty()) {
                Text(
                    stringResource(R.string.add_from_categories_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    groups.forEach { (category, pictos) ->
                        val accent = Color(category.colorArgb)
                        Text(
                            category.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = accent,
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            pictos.forEach { picto ->
                                val isSelected = picto.id in selectedIds
                                val pictoColor = Color(picto.colorArgbOverride ?: category.colorArgb)
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .background(Color.White, RoundedCornerShape(12.dp))
                                        .categoryFrame(
                                            pictoColor,
                                            if (isSelected) 4.dp else 2.dp,
                                            category.borderStyle,
                                            12.dp,
                                        )
                                        .clickable {
                                            if (!selectedIds.remove(picto.id)) selectedIds.add(picto.id)
                                        }
                                        .padding(6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val model: Any = picto.imagePath?.let { File(it) }
                                        ?: picto.arasaacId?.let { ArasaacUrls.image(it) }
                                        ?: R.drawable.ic_picto_placeholder
                                    AsyncImage(
                                        model = model,
                                        contentDescription = picto.label,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(22.dp)
                                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(14.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val chosen = groups.flatMap { (cat, pics) -> pics.map { cat to it } }
                        .filter { it.second.id in selectedIds }
                        .map { (cat, p) -> p to (p.colorArgbOverride ?: cat.colorArgb) }
                    onAdd(chosen)
                },
                enabled = selectedIds.isNotEmpty(),
            ) { Text(stringResource(R.string.picto_add_selected, selectedIds.size)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

private fun skinSwatch(value: String): Color = SKIN_SWATCHES[value] ?: SWATCH_UNKNOWN

private fun hairSwatch(value: String): Color = HAIR_SWATCHES[value] ?: SWATCH_UNKNOWN

/**
 * Approximations of ARASAAC's skin and hair option values, used only to tint the
 * picker swatches -- the pictogram itself is recoloured by ARASAAC's own API, so
 * these need to be recognisable rather than exact. The keys are ARASAAC's
 * spellings, including "assian", and must match them to keep the API happy.
 */
private val SKIN_SWATCHES = mapOf(
    "white" to Color(0xFFF1C9A5),
    "mulatto" to Color(0xFFD49E7A),
    "aztec" to Color(0xFFB87A4B),
    "black" to Color(0xFF6B4423),
    "assian" to Color(0xFFF0C27B),
)

private val HAIR_SWATCHES = mapOf(
    "blonde" to Color(0xFFE6C76E),
    "brown" to Color(0xFF8B5A2B),
    "darkBrown" to Color(0xFF4B2E1E),
    "gray" to Color(0xFFBDBDBD),
    "darkGray" to Color(0xFF616161),
    "red" to Color(0xFFB5482E),
    "black" to Color(0xFF1A1A1A),
)

/** Shown for an option this build does not have a colour for. */
private val SWATCH_UNKNOWN = Color(0xFFBDBDBD)

/** Hairline ring around an unselected swatch, so a pale one still has an edge. */
private val SWATCH_EDGE = Color(0x44000000)
