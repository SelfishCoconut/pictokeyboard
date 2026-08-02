package org.pictokeyboard.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.pictokeyboard.R
import org.pictokeyboard.data.db.BoardEntity
import org.pictokeyboard.data.repo.BoardSummary
import org.pictokeyboard.ui.theme.CategoryColors
import org.pictokeyboard.ui.theme.PictoTheme
import org.pictokeyboard.ui.theme.Spacing

/**
 * One board.
 *
 * The whole card is a single accessibility node carrying the board's name, its
 * counts and whether it is in use — the miniature inside is a picture of the
 * board, not a dozen tiles to swipe through. The overflow stays a separate node
 * because it is a separate action.
 */
@Composable
internal fun BoardCard(
    summary: BoardSummary,
    onOpen: () -> Unit,
    onUse: () -> Unit,
    onTryIt: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val board = summary.board
    val description = if (board.active) {
        stringResource(R.string.boards_card_a11y_in_use, board.name, summary.categoryCount, summary.pictoCount)
    } else {
        stringResource(R.string.boards_card_a11y, board.name, summary.categoryCount, summary.pictoCount)
    }

    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .semantics(mergeDescendants = true) { contentDescription = description }
                .clickable(role = Role.Button, onClick = onOpen),
        ) {
            BoardMiniature(
                categories = summary.categories,
                pictos = summary.heroPictos,
                caption = {
                    BoardCardCaption(
                        summary = summary,
                        onUse = onUse,
                        onTryIt = onTryIt,
                        onDuplicate = onDuplicate,
                        onExport = onExport,
                        onDelete = onDelete,
                    )
                },
            )
        }
    }
}

@Composable
private fun BoardCardCaption(
    summary: BoardSummary,
    onUse: () -> Unit,
    onTryIt: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val board = summary.board
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.md, bottom = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    board.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = PictoTheme.colors.ink,
                )
                if (board.active) InUseBadge(board.colorArgb)
            }
            Text(
                stringResource(R.string.dashboard_board_counts, summary.categoryCount, summary.pictoCount),
                style = MaterialTheme.typography.bodyMedium,
                color = PictoTheme.colors.inkSoft,
            )
        }
        BoardOverflow(
            board = board,
            onUse = onUse,
            onTryIt = onTryIt,
            onDuplicate = onDuplicate,
            onExport = onExport,
            onDelete = onDelete,
        )
    }
}

/**
 * Marks the board the keyboard is actually showing.
 *
 * Its own colour rather than the theme's accent: the board's colour is what
 * identifies it on the keyboard's tab strip in #36, so the same hue meaning the
 * same board here is the point.
 */
@Composable
private fun InUseBadge(colorArgb: Int) {
    Surface(
        color = Color(CategoryColors.tintSoft(colorArgb)),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            stringResource(R.string.boards_in_use),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp),
        )
    }
}

@Composable
private fun BoardOverflow(
    board: BoardEntity,
    onUse: () -> Unit,
    onTryIt: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    // The menu as data: label, and what it does. A list rather than five
    // near-identical DropdownMenuItem blocks, so that closing the menu is stated
    // once — five copies of `open = false` is five chances for the next action
    // added here to leave the menu hanging open over the dialog it just opened.
    //
    // Order is deliberate. Try it sits above the housekeeping actions because it
    // is what a caregiver does after every edit, and until now doing it meant
    // leaving for another app.
    val actions: List<Pair<Int, () -> Unit>> = buildList {
        // Absent on the board already in use: an action that cannot change
        // anything is noise, and here it would read as "did that not work?"
        if (!board.active) add(R.string.boards_use_this to onUse)
        add(R.string.try_it to onTryIt)
        add(R.string.boards_duplicate to onDuplicate)
        add(R.string.boards_export to onExport)
        add(R.string.boards_delete to { confirmingDelete = true })
    }

    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.boards_more, board.name),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            actions.forEach { (label, action) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        open = false
                        action()
                    },
                )
            }
        }
    }

    if (confirmingDelete) {
        ConfirmDeleteBoardDialog(
            board = board,
            onDismiss = { confirmingDelete = false },
            onConfirm = {
                confirmingDelete = false
                onDelete()
            },
        )
    }
}

/**
 * Deleting a board takes every word on it, and there is no undo — so it is
 * always confirmed, and the dialog names what goes rather than asking "are you
 * sure?" about an unstated consequence.
 */
@Composable
private fun ConfirmDeleteBoardDialog(board: BoardEntity, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.boards_delete_confirm_title, board.name)) },
        text = { Text(stringResource(R.string.boards_delete_confirm_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.boards_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
