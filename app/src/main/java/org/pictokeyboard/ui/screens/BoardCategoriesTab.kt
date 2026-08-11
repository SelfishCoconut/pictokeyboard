package org.pictokeyboard.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.pictokeyboard.R
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.data.db.UsageEntity
import org.pictokeyboard.data.repo.IconChoice
import org.pictokeyboard.data.seed.CategoryTemplate
import org.pictokeyboard.ui.theme.Spacing

// The Categories half of the board detail screen (#33), and the dialogs that go
// with it. Until then this was a destination of its own reached from the board
// card; it is now a tab beside Layout, because a caregiver adding a category and
// a caregiver widening the grid are doing the same job on the same board.

/**
 * The board's categories: reorderable, each row carrying its colour, its picto
 * and how many symbols are inside it.
 *
 * The reorder toggle sits above the list rather than in the screen's top bar. It
 * belongs to this tab and not to the board — and at a 200% font scale a top bar
 * carrying a title, a back arrow, **Try it** and **Reorder** has nowhere left to
 * put the board's name.
 */
@Composable
internal fun BoardCategoriesTab(
    categories: List<CategoryEntity>,
    pictoCounts: Map<String, Int>,
    reordering: Boolean,
    onToggleReorder: () -> Unit,
    onOpenCategory: (String) -> Unit,
    onReorder: (List<CategoryEntity>) -> Unit,
    onMove: (CategoryEntity, Boolean) -> Unit,
    onEdit: (CategoryEntity) -> Unit,
    onDelete: (CategoryEntity) -> Unit,
    modifier: Modifier = Modifier,
    onMoveToBoard: ((CategoryEntity) -> Unit)? = null,
) {
    Column(modifier = modifier) {
        if (categories.size > 1) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg),
                horizontalAlignment = Alignment.End,
            ) {
                TextButton(onClick = onToggleReorder) {
                    Text(stringResource(if (reordering) R.string.reorder_done else R.string.reorder))
                }
            }
        }
        ReorderableCategoryList(
            categories = categories,
            pictoCounts = pictoCounts,
            reordering = reordering,
            onReorder = onReorder,
            onMove = onMove,
            onEdit = onEdit,
            onDelete = onDelete,
            onOpen = onOpenCategory,
            onMoveToBoard = onMoveToBoard,
        )
    }
}

/**
 * Which dialog the board detail is showing. One value rather than four
 * independent flags, so "editing and deleting at once" cannot be expressed.
 */
internal sealed interface CategoryDialog {
    /** Choose how to create: from a template, from usage, or blank. */
    data object Chooser : CategoryDialog
    data object Blank : CategoryDialog
    data class Edit(val category: CategoryEntity) : CategoryDialog
    data class Delete(val category: CategoryEntity) : CategoryDialog
}

/** Renders whichever dialog [dialog] names, or nothing when it is null. */
@Composable
internal fun CategoryDialogs(
    dialog: CategoryDialog?,
    language: String,
    onDismiss: () -> Unit,
    onWantBlank: () -> Unit,
    loadSuggested: suspend () -> List<UsageEntity>,
    onAddFromTemplate: (CategoryTemplate) -> Unit,
    onAddSuggested: (String, List<UsageEntity>) -> Unit,
    onAddBlank: (CategoryEdit) -> Unit,
    onUpdate: (CategoryEntity, IconChoice) -> Unit,
    onDelete: (CategoryEntity) -> Unit,
    pickerDialog: IconPickerSlot,
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
