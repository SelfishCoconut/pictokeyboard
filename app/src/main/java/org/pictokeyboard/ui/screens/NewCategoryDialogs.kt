package org.pictokeyboard.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.pictokeyboard.R
import org.pictokeyboard.data.arasaac.ArasaacUrls
import org.pictokeyboard.data.db.BorderStyles
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.data.db.UsageEntity
import org.pictokeyboard.data.repo.IconChoice
import org.pictokeyboard.data.repo.currentIcon
import org.pictokeyboard.data.seed.CategoryTemplate
import org.pictokeyboard.data.seed.CategoryTemplates
import org.pictokeyboard.ui.theme.PictoTheme
import org.pictokeyboard.ui.theme.Spacing

// Creating and editing a category: the chooser that offers a template, the
// usage-derived suggestion or a blank one, and the name/colour editor shared by
// the blank and edit flows.

/**
 * How to create a category: from usage, blank, or from one of the templates.
 *
 * A bottom sheet rather than an `AlertDialog`. As a dialog this was a small box
 * scrolling a list of ten template cards inside itself, with the scroll boundary
 * invisible — so the templates below the fold were easy to miss entirely, and the
 * dialog was simultaneously too big for its content and too small for its list. A
 * sheet is the component for "pick one of many": it opens tall, scrolls as one
 * surface, and drags away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewCategoryChooserSheet(
    language: String,
    suggestedName: String,
    loadSuggested: suspend () -> List<UsageEntity>,
    onDismiss: () -> Unit,
    onBlank: () -> Unit,
    onTemplate: (CategoryTemplate) -> Unit,
    onSuggested: (List<org.pictokeyboard.data.db.UsageEntity>) -> Unit,
) {
    val suggested by produceState(initialValue = emptyList<org.pictokeyboard.data.db.UsageEntity>()) {
        value = loadSuggested()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                stringResource(R.string.category_add),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                stringResource(R.string.category_new_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (suggested.isNotEmpty()) {
                ChooserCard(
                    // The one option that is not a category colour, so it takes
                    // the product accent. It used to be a hardcoded teal -- the
                    // last of the old brand palette hiding in a Composable.
                    accent = MaterialTheme.colorScheme.primary,
                    title = suggestedName,
                    subtitle = stringResource(R.string.category_suggested_desc),
                    thumbs = suggested.mapNotNull { it.arasaacId }.take(CHOOSER_THUMBS)
                        .map { ArasaacUrls.image(it, ArasaacUrls.THUMB) },
                    highlighted = true,
                    onClick = { onSuggested(suggested) },
                )
            }

            ChooserCard(
                accent = MaterialTheme.colorScheme.outline,
                title = stringResource(R.string.category_blank),
                subtitle = stringResource(R.string.category_blank_desc),
                thumbs = emptyList(),
                onClick = onBlank,
            )

            Text(
                stringResource(R.string.category_from_template),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 4.dp),
            )
            CategoryTemplates.all.forEach { template ->
                ChooserCard(
                    accent = Color(template.color),
                    title = template.name(language),
                    subtitle = stringResource(R.string.category_pictos_count, template.pictos.size),
                    thumbs = template.pictos.take(CHOOSER_THUMBS)
                        .map { ArasaacUrls.image(it.arasaacId, ArasaacUrls.THUMB) },
                    onClick = { onTemplate(template) },
                )
            }
        }
    }
}

@Composable
private fun ChooserCard(
    accent: Color,
    title: String,
    subtitle: String,
    thumbs: List<String>,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        // `card`, not surfaceVariant: that role resolves to the decorative `line`,
        // which put this card's inkSoft subtitle at 4.36:1 -- and made `highlighted`
        // a no-op, since secondaryContainer resolves to the same colour. The
        // suggestion is now distinguished by an accent outline instead of a fill
        // that was never visible.
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(
            width = if (highlighted) 2.dp else 1.dp,
            color = if (highlighted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            },
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(accent, CircleShape),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (thumbs.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    thumbs.forEach { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            modifier = Modifier
                                .size(44.dp)
                                .background(PictoTheme.colors.tile, RoundedCornerShape(8.dp))
                                .border(2.dp, accent, RoundedCornerShape(8.dp))
                                .padding(3.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CategoryEditDialog(
    initial: CategoryEntity?,
    onDismiss: () -> Unit,
    onSave: (CategoryEdit) -> Unit,
    pickerDialog: CategoryPickerSlot,
) {
    var edit by remember { mutableStateOf(initial.toEdit()) }
    var picking by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (initial == null) R.string.category_add else R.string.category_edit))
        },
        text = {
            CategoryEditForm(edit = edit, onChange = { edit = it }, onChoosePicto = { picking = true })
        },
        confirmButton = {
            TextButton(
                onClick = { if (edit.name.isNotBlank()) onSave(edit.copy(name = edit.name.trim())) },
                enabled = edit.name.isNotBlank(),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )

    if (picking) {
        pickerDialog(
            initial?.id,
            { picking = false },
            {
                edit = edit.copy(icon = it)
                picking = false
            },
        )
    }
}

/**
 * How the editor reaches the picto picker.
 *
 * The picker needs the ConfigViewModel (ARASAAC search, the category's own
 * symbols, saving a cropped photo), and this file is deliberately free of it so
 * the screen stays previewable and testable without one. So it arrives as a
 * slot: the viewModel-aware caller supplies the real dialog, and a preview or a
 * test supplies an empty lambda.
 */
typealias CategoryPickerSlot =
    @Composable (categoryId: String?, onDismiss: () -> Unit, onPicked: (IconChoice) -> Unit) -> Unit

/** Everything the category editor collects, saved in one write. */
data class CategoryEdit(
    val name: String,
    val color: Int,
    val borderStyle: String,
    val borderWidthDp: Int,
    val icon: IconChoice,
)

/** The editor's starting values: an existing category's, or the defaults for a new one. */
private fun CategoryEntity?.toEdit() = CategoryEdit(
    name = this?.name ?: "",
    color = this?.colorArgb ?: CategoryPalette.first().argb.toInt(),
    borderStyle = this?.borderStyle ?: BorderStyles.SOLID,
    borderWidthDp = this?.borderWidthDp ?: BorderStyles.DEFAULT_WIDTH_DP,
    icon = this?.currentIcon() ?: IconChoice.None,
)

/**
 * The category editor: **picto · name · colour · frame style · frame thickness**,
 * in that order, because the picto is what the communicator navigates by and the
 * name is what the caregiver reads.
 */
@Composable
private fun CategoryEditForm(
    edit: CategoryEdit,
    onChange: (CategoryEdit) -> Unit,
    onChoosePicto: () -> Unit,
) {
    val accent = Color(edit.color)
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.category_picto), style = MaterialTheme.typography.labelLarge)
        IconField(
            icon = edit.icon,
            accent = accent,
            onChoose = onChoosePicto,
            onClear = { onChange(edit.copy(icon = IconChoice.None)) },
        )

        OutlinedTextField(
            value = edit.name,
            onValueChange = { onChange(edit.copy(name = it)) },
            label = { Text(stringResource(R.string.category_name)) },
            singleLine = true,
        )
        Text(stringResource(R.string.category_frame_color), style = MaterialTheme.typography.labelLarge)
        ColorPalettePicker(selected = edit.color, onSelect = { onChange(edit.copy(color = it)) })

        Text(stringResource(R.string.category_frame_style), style = MaterialTheme.typography.labelLarge)
        BorderStylePicker(
            color = accent,
            selected = edit.borderStyle,
            onSelect = { onChange(edit.copy(borderStyle = it)) },
        )

        Text(stringResource(R.string.category_frame_thickness), style = MaterialTheme.typography.labelLarge)
        ThicknessPicker(
            color = accent,
            selected = edit.borderWidthDp,
            onSelect = { onChange(edit.copy(borderWidthDp = it)) },
        )
    }
}

/** Thumbnails previewed on a chooser card. */
private const val CHOOSER_THUMBS = 4
