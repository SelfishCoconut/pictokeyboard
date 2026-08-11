package org.pictokeyboard.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.pictokeyboard.R
import org.pictokeyboard.data.db.BoardEntity
import org.pictokeyboard.data.repo.BoardSummary
import org.pictokeyboard.ui.theme.PictoKeyboardTheme
import org.pictokeyboard.ui.theme.ScreenPreviews
import org.pictokeyboard.ui.theme.Spacing

// Making a board. The sheet that asks where it should come from, and the dialog
// that names it. Both live here rather than in BoardsScreen, which is a list.

/**
 * Where a new board comes from: nothing, or a board that already exists.
 *
 * Copying is first and listed board by board because it is the common case. A
 * school board is a home board with different words in it, and a caregiver who
 * has already spent an evening choosing categories, colours and frames should
 * not spend a second one to get a variant. Starting from scratch is the honest
 * other answer, not the default.
 *
 * Before this the only way to a second board was **Duplicate** inside another
 * board's overflow menu (#135), which is a place nobody looks for *new*.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewBoardSheet(
    boards: List<BoardSummary>,
    onDismiss: () -> Unit,
    onCopy: (BoardEntity) -> Unit,
    onScratch: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                stringResource(R.string.boards_new_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                stringResource(R.string.boards_new_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (boards.isNotEmpty()) {
                Text(
                    stringResource(R.string.boards_new_copy_label),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 4.dp),
                )
                boards.forEach { summary ->
                    NewBoardCard(
                        title = summary.board.name,
                        subtitle = stringResource(
                            R.string.boards_new_copy_desc,
                            summary.categories.size,
                            summary.pictoCount,
                        ),
                        onClick = { onCopy(summary.board) },
                    )
                }
            }

            Text(
                stringResource(R.string.boards_new_scratch_label),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 4.dp),
            )
            NewBoardCard(
                title = stringResource(R.string.boards_new_scratch),
                subtitle = stringResource(R.string.boards_new_scratch_desc),
                onClick = onScratch,
            )
        }
    }
}

/** One option on the sheet, shaped like the category chooser's cards. */
@Composable
private fun NewBoardCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Names the board before it exists.
 *
 * Asked on both routes, copy included, because a board cannot be renamed
 * afterwards from anywhere in the app: a copy left as *Home (copy)* stays that
 * on the keyboard's tab strip, in front of the person using it. The field
 * arrives filled in with a sensible answer, so the caregiver who does not care
 * presses one button.
 */
@Composable
internal fun BoardNameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    val trimmed = name.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.boards_new_name_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.boards_new_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            // A board with a blank name is a blank tab on the keyboard, which is
            // unreachable for someone who cannot read the pictogram beside it.
            TextButton(onClick = { onConfirm(trimmed) }, enabled = trimmed.isNotEmpty()) {
                Text(stringResource(R.string.boards_new_name_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@ScreenPreviews
@Composable
private fun BoardNameDialogPreview() {
    PictoKeyboardTheme {
        BoardNameDialog(initialName = "Médico", onDismiss = {}, onConfirm = {})
    }
}
