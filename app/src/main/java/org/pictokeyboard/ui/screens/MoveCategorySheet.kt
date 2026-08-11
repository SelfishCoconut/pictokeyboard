package org.pictokeyboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.pictokeyboard.R
import org.pictokeyboard.data.db.BoardEntity
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.data.repo.BoardSummary
import org.pictokeyboard.ui.theme.PictoTheme
import org.pictokeyboard.ui.theme.Spacing

/** The board's colour, as a dot the same size the board card gives its picto. */
private const val BOARD_DOT_DP = 32

/** Tall enough to show several boards, short enough to stay a sheet. */
private val SHEET_MAX_HEIGHT = 420.dp

/**
 * Where to move a category to.
 *
 * A list of boards and nothing else — no confirm step. Moving a category is
 * undoable in one tap from the snackbar that follows, and a confirmation dialog
 * in front of a reversible action is a tax on the caregiver who meant it, paid
 * every time, to save the one who did not a single tap.
 *
 * Only boards other than the one the category is on are offered, and the caller
 * does not open this at all when there are none — an empty sheet, or a sheet
 * holding only the board you are already on, answers a question nobody asked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoveCategorySheet(
    categoryName: String,
    boards: List<BoardSummary>,
    onDismiss: () -> Unit,
    onPick: (BoardEntity) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                stringResource(R.string.category_move_title, categoryName),
                style = MaterialTheme.typography.titleMedium,
                color = PictoTheme.colors.ink,
            )
            Text(
                stringResource(R.string.category_move_body),
                style = MaterialTheme.typography.bodyMedium,
                color = PictoTheme.colors.inkSoft,
                modifier = Modifier.padding(bottom = Spacing.sm),
            )
            LazyColumn(
                modifier = Modifier.heightIn(max = SHEET_MAX_HEIGHT),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                items(boards, key = { it.board.id }) { summary ->
                    DestinationBoardRow(summary = summary, onClick = { onPick(summary.board) })
                }
            }
        }
    }
}

/**
 * One board a category can be moved onto, showing what is already on it.
 *
 * The counts are there because the question a caregiver is actually answering is
 * "which of my boards is the one about school?", and after a few boards the
 * names stop being enough on their own.
 */
@Composable
private fun DestinationBoardRow(summary: BoardSummary, onClick: () -> Unit) {
    val board = summary.board
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = PictoTheme.colors.card,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.touchTarget),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Box(
                modifier = Modifier
                    .size(BOARD_DOT_DP.dp)
                    .clip(CircleShape)
                    .background(Color(board.colorArgb)),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    board.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = PictoTheme.colors.ink,
                )
                Text(
                    pluralStringResource(
                        R.plurals.category_symbol_count,
                        summary.pictoCount,
                        summary.pictoCount,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = PictoTheme.colors.inkSoft,
                )
            }
        }
    }
}

/**
 * Picking a destination board, moving the category there, and offering the way
 * back.
 *
 * Its own composable rather than a block inside the screen because the undo is
 * the interesting part and it deserves to be readable: the move hands back the
 * row as it was, the snackbar holds that row for as long as it is on screen, and
 * tapping **Undo** writes it straight back. Nothing is remembered after the
 * snackbar goes, which is the point — an undo that survives the message that
 * offered it is a second, invisible piece of state to get wrong.
 */
@Composable
internal fun MoveCategoryFlow(
    category: CategoryEntity,
    boards: List<BoardSummary>,
    snackbars: SnackbarHostState,
    onDismiss: () -> Unit,
    onMoveCategory: (CategoryEntity, String, (CategoryEntity) -> Unit) -> Unit,
    onUndoMove: (CategoryEntity) -> Unit,
) {
    val scope = rememberCoroutineScope()
    // `LocalResources`, not `LocalContext.current.getString`. The snackbar's
    // text is built inside a coroutine after the move lands, and reading it
    // through the context there is what `LocalContextGetResourceValueCall`
    // exists to stop: the value would outlive a locale change that recomposed
    // everything around it, leaving one sentence in the previous language.
    val resources = LocalResources.current

    MoveCategorySheet(
        categoryName = category.name,
        boards = boards,
        onDismiss = onDismiss,
        onPick = { destination ->
            onDismiss()
            onMoveCategory(category, destination.id) { previous ->
                scope.launch {
                    // Long rather than short: undo is the whole safety net for an
                    // action with no confirmation in front of it, and a caregiver
                    // has to notice the row has gone before they can decide they
                    // did not mean it.
                    val outcome = snackbars.showSnackbar(
                        message = resources.getString(
                            R.string.category_moved,
                            category.name,
                            destination.name,
                        ),
                        actionLabel = resources.getString(R.string.undo),
                        duration = SnackbarDuration.Long,
                    )
                    if (outcome == SnackbarResult.ActionPerformed) onUndoMove(previous)
                }
            }
        },
    )
}
