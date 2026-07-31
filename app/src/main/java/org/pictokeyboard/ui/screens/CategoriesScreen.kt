package org.pictokeyboard.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import org.pictokeyboard.R
import org.pictokeyboard.data.arasaac.ArasaacUrls
import org.pictokeyboard.data.db.BorderStyles
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.data.db.UsageEntity
import org.pictokeyboard.data.seed.CategoryTemplate
import org.pictokeyboard.data.seed.CategoryTemplates
import org.pictokeyboard.ui.ConfigViewModel

/** Stateful wrapper: the board's category list, owned by the view model. */
@Composable
fun CategoriesScreen(
    viewModel: ConfigViewModel,
    onBack: (() -> Unit)? = null,
    onOpenCategory: (String) -> Unit,
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    CategoriesScreenContent(
        categories = categories,
        language = settings.defaultLanguage,
        onBack = onBack,
        onOpenCategory = onOpenCategory,
        onReorder = viewModel::reorderCategories,
        loadSuggested = { viewModel.topUsed() },
        onAddFromTemplate = { template -> viewModel.addCategoryFromTemplate(template, settings.defaultLanguage) },
        onAddSuggested = { name, records -> viewModel.addSuggestedCategory(name, records) },
        onAddBlank = { name, color, style, width -> viewModel.addCategory(name, color, style, width) },
        onUpdate = viewModel::updateCategory,
        onDelete = viewModel::deleteCategory,
    )
}

/**
 * Which dialog the screen is showing. One value rather than four independent
 * flags, so "editing and deleting at once" cannot be expressed.
 */
private sealed interface CategoryDialog {
    /** Choose how to create: from a template, from usage, or blank. */
    data object Chooser : CategoryDialog
    data object Blank : CategoryDialog
    data class Edit(val category: CategoryEntity) : CategoryDialog
    data class Delete(val category: CategoryEntity) : CategoryDialog
}

/** Stateless category list. Dialog visibility is local; the data is not. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreenContent(
    categories: List<CategoryEntity>,
    language: String,
    onBack: (() -> Unit)?,
    onOpenCategory: (String) -> Unit,
    onReorder: (List<CategoryEntity>) -> Unit,
    loadSuggested: suspend () -> List<UsageEntity>,
    onAddFromTemplate: (CategoryTemplate) -> Unit,
    onAddSuggested: (String, List<UsageEntity>) -> Unit,
    onAddBlank: (String, Int, String, Int) -> Unit,
    onUpdate: (CategoryEntity) -> Unit,
    onDelete: (CategoryEntity) -> Unit,
) {
    var dialog by remember { mutableStateOf<CategoryDialog?>(null) }
    var reordering by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CategoriesTopBar(
                canReorder = categories.size > 1,
                reordering = reordering,
                onBack = onBack,
                onToggleReorder = { reordering = !reordering },
            )
        },
        floatingActionButton = {
            if (!reordering) {
                FloatingActionButton(onClick = { dialog = CategoryDialog.Chooser }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.category_add))
                }
            }
        },
    ) { padding ->
        ReorderableCategoryList(
            categories = categories,
            reordering = reordering,
            modifier = Modifier.padding(padding),
            onReorder = onReorder,
            onEdit = { dialog = CategoryDialog.Edit(it) },
            onDelete = { dialog = CategoryDialog.Delete(it) },
            onOpen = onOpenCategory,
        )
    }

    CategoryDialogs(
        dialog = dialog,
        language = language,
        onDismiss = { dialog = null },
        onWantBlank = { dialog = CategoryDialog.Blank },
        loadSuggested = loadSuggested,
        onAddFromTemplate = onAddFromTemplate,
        onAddSuggested = onAddSuggested,
        onAddBlank = onAddBlank,
        onUpdate = onUpdate,
        onDelete = onDelete,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoriesTopBar(
    canReorder: Boolean,
    reordering: Boolean,
    onBack: (() -> Unit)?,
    onToggleReorder: () -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(R.string.categories_title)) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                    )
                }
            }
        },
        actions = {
            if (canReorder) {
                TextButton(onClick = onToggleReorder) {
                    Text(stringResource(if (reordering) R.string.reorder_done else R.string.reorder))
                }
            }
        },
    )
}

/** Renders whichever dialog [dialog] names, or nothing when it is null. */
@Composable
private fun CategoryDialogs(
    dialog: CategoryDialog?,
    language: String,
    onDismiss: () -> Unit,
    onWantBlank: () -> Unit,
    loadSuggested: suspend () -> List<UsageEntity>,
    onAddFromTemplate: (CategoryTemplate) -> Unit,
    onAddSuggested: (String, List<UsageEntity>) -> Unit,
    onAddBlank: (String, Int, String, Int) -> Unit,
    onUpdate: (CategoryEntity) -> Unit,
    onDelete: (CategoryEntity) -> Unit,
) {
    when (dialog) {
        null -> Unit

        CategoryDialog.Chooser -> {
            val suggestedName = stringResource(R.string.category_suggested)
            NewCategoryChooserDialog(
                language = language,
                suggestedName = suggestedName,
                loadSuggested = loadSuggested,
                onDismiss = onDismiss,
                onBlank = onWantBlank,
                onTemplate = { template ->
                    onAddFromTemplate(template)
                    onDismiss()
                },
                onSuggested = { records ->
                    onAddSuggested(suggestedName, records)
                    onDismiss()
                },
            )
        }

        CategoryDialog.Blank -> CategoryEditDialog(
            initial = null,
            onDismiss = onDismiss,
            onSave = { name, color, style, width ->
                onAddBlank(name, color, style, width)
                onDismiss()
            },
        )

        is CategoryDialog.Edit -> CategoryEditDialog(
            initial = dialog.category,
            onDismiss = onDismiss,
            onSave = { name, color, style, width ->
                onUpdate(
                    dialog.category.copy(
                        name = name,
                        colorArgb = color,
                        borderStyle = style,
                        borderWidthDp = width,
                    ),
                )
                onDismiss()
            },
        )

        is CategoryDialog.Delete -> DeleteCategoryDialog(
            category = dialog.category,
            onDismiss = onDismiss,
            onConfirm = {
                onDelete(dialog.category)
                onDismiss()
            },
        )
    }
}

@Composable
private fun DeleteCategoryDialog(category: CategoryEntity, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(category.name) },
        text = { Text(stringResource(R.string.category_delete_confirm)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun NewCategoryChooserDialog(
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.category_add)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.category_new_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (suggested.isNotEmpty()) {
                    ChooserCard(
                        accent = Color(0xFF00897B),
                        title = suggestedName,
                        subtitle = stringResource(R.string.category_suggested_desc),
                        thumbs = suggested.mapNotNull { it.arasaacId }.take(4)
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
                        thumbs = template.pictos.take(4)
                            .map { ArasaacUrls.image(it.arasaacId, ArasaacUrls.THUMB) },
                        onClick = { onTemplate(template) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
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
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
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
                                .background(Color.White, RoundedCornerShape(8.dp))
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
private fun CategoryEditDialog(
    initial: CategoryEntity?,
    onDismiss: () -> Unit,
    onSave: (String, Int, String, Int) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var color by remember { mutableStateOf(initial?.colorArgb ?: CategoryPalette.first().toInt()) }
    var borderStyle by remember { mutableStateOf(initial?.borderStyle ?: BorderStyles.SOLID) }
    var borderWidth by remember { mutableStateOf(initial?.borderWidthDp ?: BorderStyles.DEFAULT_WIDTH_DP) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (initial == null) R.string.category_add else R.string.category_edit))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.category_name)) },
                    singleLine = true,
                )
                Text(stringResource(R.string.category_frame_color), style = MaterialTheme.typography.labelLarge)
                ColorPalettePicker(selected = color, onSelect = { color = it })

                Text(stringResource(R.string.category_frame_style), style = MaterialTheme.typography.labelLarge)
                BorderStylePicker(color = Color(color), selected = borderStyle, onSelect = { borderStyle = it })

                Text(stringResource(R.string.category_frame_thickness), style = MaterialTheme.typography.labelLarge)
                ThicknessPicker(color = Color(color), selected = borderWidth, onSelect = { borderWidth = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onSave(name.trim(), color, borderStyle, borderWidth) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
