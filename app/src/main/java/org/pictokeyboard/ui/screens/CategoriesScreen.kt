package org.pictokeyboard.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.pictokeyboard.R
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.data.db.UsageEntity
import org.pictokeyboard.data.repo.CategoryIcon
import org.pictokeyboard.data.seed.CategoryTemplate
import org.pictokeyboard.data.seed.CategoryTemplates
import org.pictokeyboard.ui.ConfigViewModel
import org.pictokeyboard.ui.theme.PictoKeyboardTheme
import org.pictokeyboard.ui.theme.ScreenPreviews

/** Stateful wrapper: the board's category list, owned by the view model. */
@Composable
fun CategoriesScreen(
    viewModel: ConfigViewModel,
    onBack: (() -> Unit)? = null,
    onOpenCategory: (String) -> Unit,
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val board by viewModel.activeBoard.collectAsStateWithLifecycle()
    // The board's language, not the app's. What language a category's words are
    // in is a property of the vocabulary being built, and a caregiver may well
    // run the app in Spanish while building an English board for school (#31).
    val boardLanguage = board?.language ?: "es"

    CategoriesScreenContent(
        categories = categories,
        language = boardLanguage,
        onBack = onBack,
        onOpenCategory = onOpenCategory,
        onReorder = viewModel::reorderCategories,
        // moveCategory has existed on the view model since #14 and was called
        // from nowhere; the drag gesture was the only route into reordering.
        onMove = viewModel::moveCategory,
        loadSuggested = { viewModel.topUsed() },
        onAddFromTemplate = { template -> viewModel.addCategoryFromTemplate(template, boardLanguage) },
        onAddSuggested = { name, records -> viewModel.addSuggestedCategory(name, records) },
        onAddBlank = { edit ->
            viewModel.addCategory(edit.name, edit.color, edit.borderStyle, edit.borderWidthDp, edit.icon)
        },
        onUpdate = { category, icon -> viewModel.updateCategory(category, icon) },
        onDelete = viewModel::deleteCategory,
        // The only place the picker meets the view model. Everything below this
        // call stays previewable without one -- see CategoryPickerSlot.
        pickerDialog = { categoryId, onDismissPicker, onPicked ->
            CategoryIconPickerDialog(
                viewModel = viewModel,
                categoryId = categoryId,
                language = boardLanguage,
                onDismiss = onDismissPicker,
                onPicked = onPicked,
            )
        },
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
    onMove: (CategoryEntity, Boolean) -> Unit,
    loadSuggested: suspend () -> List<UsageEntity>,
    onAddFromTemplate: (CategoryTemplate) -> Unit,
    onAddSuggested: (String, List<UsageEntity>) -> Unit,
    onAddBlank: (CategoryEdit) -> Unit,
    onUpdate: (CategoryEntity, CategoryIcon) -> Unit,
    onDelete: (CategoryEntity) -> Unit,
    pickerDialog: CategoryPickerSlot,
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
                AddFab(
                    contentDescription = stringResource(R.string.category_add),
                    onClick = { dialog = CategoryDialog.Chooser },
                )
            }
        },
    ) { padding ->
        ReorderableCategoryList(
            categories = categories,
            reordering = reordering,
            modifier = Modifier.padding(padding),
            onReorder = onReorder,
            onMove = onMove,
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
        pickerDialog = pickerDialog,
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
    onAddBlank: (CategoryEdit) -> Unit,
    onUpdate: (CategoryEntity, CategoryIcon) -> Unit,
    onDelete: (CategoryEntity) -> Unit,
    pickerDialog: CategoryPickerSlot,
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
            onSave = { edit ->
                onAddBlank(edit)
                onDismiss()
            },
            pickerDialog = pickerDialog,
        )

        is CategoryDialog.Edit -> CategoryEditDialog(
            initial = dialog.category,
            onDismiss = onDismiss,
            onSave = { edit ->
                // The icon travels beside the entity rather than on it: choosing
                // an ARASAAC picto may still need downloading, so the repository
                // is what turns the choice into stored columns.
                onUpdate(
                    dialog.category.copy(
                        name = edit.name,
                        colorArgb = edit.color,
                        borderStyle = edit.borderStyle,
                        borderWidthDp = edit.borderWidthDp,
                    ),
                    edit.icon,
                )
                onDismiss()
            },
            pickerDialog = pickerDialog,
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

@ScreenPreviews
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
            onMove = { _, _ -> },
            loadSuggested = { emptyList() },
            onAddFromTemplate = {},
            onAddSuggested = { _, _ -> },
            onAddBlank = {},
            onUpdate = { _, _ -> },
            onDelete = {},
            // No view model in a preview, so no picker. The editor still renders;
            // tapping "choose picto" simply does nothing here.
            pickerDialog = { _, _, _ -> },
        )
    }
}

/** Enough rows for the preview to show the palette without scrolling. */
private const val PREVIEW_ROWS = 5
