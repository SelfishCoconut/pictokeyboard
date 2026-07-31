package org.pictokeyboard.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.pictokeyboard.R
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.data.db.UsageEntity
import org.pictokeyboard.data.seed.CategoryTemplate
import org.pictokeyboard.data.seed.CategoryTemplates
import org.pictokeyboard.ui.ConfigViewModel
import org.pictokeyboard.ui.theme.PictoKeyboardTheme

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
                FloatingActionButton(
                    onClick = { dialog = CategoryDialog.Chooser },
                    // Explicitly the accent. The default is `primaryContainer`,
                    // which in this palette is the near-invisible `line` -- and the
                    // one button that creates things should not be the quietest
                    // thing on the screen.
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
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
            NewCategoryChooserSheet(
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

@Preview(name = "Categories", showBackground = true)
@Composable
private fun CategoriesScreenPreview() {
    // Built from the real templates, so the preview shows the actual palette.
    val categories = CategoryTemplates.all.take(PREVIEW_ROWS).mapIndexed { i, template ->
        CategoryEntity(
            id = template.id,
            name = template.name("es"),
            colorArgb = template.color.toInt(),
            iconArasaacId = template.iconArasaacId,
            position = i,
            builtin = true,
        )
    }
    PictoKeyboardTheme {
        CategoriesScreenContent(
            categories = categories,
            language = "es",
            onBack = {},
            onOpenCategory = {},
            onReorder = {},
            loadSuggested = { emptyList() },
            onAddFromTemplate = {},
            onAddSuggested = { _, _ -> },
            onAddBlank = { _, _, _, _ -> },
            onUpdate = {},
            onDelete = {},
        )
    }
}

/** Enough rows for the preview to show the palette without scrolling. */
private const val PREVIEW_ROWS = 5
