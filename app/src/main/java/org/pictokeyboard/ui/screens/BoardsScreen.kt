package org.pictokeyboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.pictokeyboard.R
import org.pictokeyboard.data.db.BoardEntity
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.data.db.PictoEntity
import org.pictokeyboard.data.repo.BoardSummary
import org.pictokeyboard.ui.theme.PictoKeyboardTheme
import org.pictokeyboard.ui.theme.ScreenPreviews
import org.pictokeyboard.ui.theme.Spacing

/**
 * The Boards tab: the caregiver's content, as the app's home.
 *
 * It replaces a dashboard whose top half was about the app rather than about
 * anything the caregiver made — setup status, a "build your board" call to
 * action and a tips card, all onboarding, all permanently pinned above the one
 * thing worth looking at.
 *
 * The setup steps survive, but only while there is something to do: once the
 * keyboard is enabled and selected the card is gone for good rather than
 * collapsing into a tick that keeps its place at the top of the app. The CTA is
 * gone entirely — the board card beneath it went to the same screen — and the
 * tips and gesture reference moved to Settings → About → Help, which is where
 * something read once belongs.
 *
 * Until multi-board editing lands this screen holds exactly one card. That is
 * honest, it teaches the concept, and nothing about the screen changes when a
 * second board becomes possible.
 */
@Composable
fun BoardsScreen(
    summaries: List<BoardSummary>,
    status: KeyboardStatus,
    onOpenBoard: (BoardSummary) -> Unit,
    onUseBoard: (BoardEntity) -> Unit,
    onDuplicateBoard: (BoardEntity) -> Unit,
    onExportBoard: (BoardEntity) -> Unit,
    onDeleteBoard: (BoardEntity) -> Unit,
    onEnableKeyboard: () -> Unit,
    onSelectKeyboard: () -> Unit,
    onOpenDiscover: () -> Unit,
) {
    BoardsScreenContent(
        summaries = summaries,
        status = status,
        onOpenBoard = onOpenBoard,
        onUseBoard = onUseBoard,
        onDuplicateBoard = onDuplicateBoard,
        onExportBoard = onExportBoard,
        onDeleteBoard = onDeleteBoard,
        onEnableKeyboard = onEnableKeyboard,
        onSelectKeyboard = onSelectKeyboard,
        onOpenDiscover = onOpenDiscover,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoardsScreenContent(
    summaries: List<BoardSummary>,
    status: KeyboardStatus,
    onOpenBoard: (BoardSummary) -> Unit,
    onUseBoard: (BoardEntity) -> Unit,
    onDuplicateBoard: (BoardEntity) -> Unit,
    onExportBoard: (BoardEntity) -> Unit,
    onDeleteBoard: (BoardEntity) -> Unit,
    onEnableKeyboard: () -> Unit,
    onSelectKeyboard: () -> Unit,
    onOpenDiscover: () -> Unit,
) {
    // Which board's Try it sheet is open, if any. Held here rather than on the
    // card so the sheet outlives the dropdown menu that asked for it.
    var trying by remember { mutableStateOf<BoardEntity?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.boards_title)) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.sm,
                bottom = Spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            // Only while there is something to do. Once the keyboard is enabled
            // and selected this disappears for good rather than collapsing to a
            // tick that goes on taking space at the top of the app forever.
            if (!status.ready) {
                item(key = "setup") {
                    SetupStepsCard(status = status, onEnable = onEnableKeyboard, onSelect = onSelectKeyboard)
                }
            }

            if (summaries.isEmpty()) {
                item(key = "empty") { BoardsEmptyState(onOpenDiscover = onOpenDiscover) }
            } else {
                items(summaries, key = { it.board.id }) { summary ->
                    BoardCard(
                        summary = summary,
                        onOpen = { onOpenBoard(summary) },
                        onUse = { onUseBoard(summary.board) },
                        // The keyboard shows the board in use, so trying one
                        // hands it over first. Same rule as opening a board to
                        // edit it: you work on the board that is live.
                        onTryIt = {
                            onUseBoard(summary.board)
                            trying = summary.board
                        },
                        onDuplicate = { onDuplicateBoard(summary.board) },
                        onExport = { onExportBoard(summary.board) },
                        onDelete = { onDeleteBoard(summary.board) },
                    )
                }
            }
        }
    }

    trying?.let { board ->
        TryItSheet(
            boardName = board.name,
            status = status,
            onEnableKeyboard = onEnableKeyboard,
            onSelectKeyboard = onSelectKeyboard,
            onDismiss = { trying = null },
        )
    }
}

/** No boards at all — points at Discover rather than at an empty grid. */
@Composable
private fun BoardsEmptyState(onOpenDiscover: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            stringResource(R.string.boards_empty_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            stringResource(R.string.boards_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.lg),
        )
        Button(onClick = onOpenDiscover) {
            Text(stringResource(R.string.boards_empty_action))
        }
    }
}

// --- Previews ---------------------------------------------------------------

private const val PREVIEW_PICTOS = 8

private fun previewSummary(name: String, active: Boolean, id: String = "b1"): BoardSummary {
    val categories = listOf(
        CategoryEntity(
            id = "$id-people",
            boardId = id,
            name = "Personas",
            colorArgb = 0xFFFFC107.toInt(),
            position = 0,
        ),
        CategoryEntity(
            id = "$id-actions",
            boardId = id,
            name = "Acciones",
            colorArgb = 0xFF4CAF50.toInt(),
            position = 1,
        ),
        CategoryEntity(id = "$id-food", boardId = id, name = "Comida", colorArgb = 0xFFFF9800.toInt(), position = 2),
    )
    return BoardSummary(
        board = BoardEntity(
            id = id,
            name = name,
            colorArgb = BoardEntity.DEFAULT_COLOR_ARGB,
            position = 0,
            active = active,
        ),
        categories = categories,
        heroPictos = List(PREVIEW_PICTOS) { index ->
            PictoEntity(
                id = "$id-p$index",
                categoryId = "$id-people",
                label = "picto $index",
                spokenText = "picto $index",
                language = "es",
                position = index,
            )
        },
        pictoCount = 101,
    )
}

@ScreenPreviews
@Composable
private fun BoardsReadyPreview() {
    PictoKeyboardTheme {
        BoardsScreenContent(
            summaries = listOf(
                previewSummary("PictoKeyboard", active = true, id = "b1"),
                previewSummary("Médico", active = false, id = "b2"),
            ),
            status = KeyboardStatus(enabled = true, selected = true),
            onOpenBoard = {},
            onUseBoard = {},
            onDuplicateBoard = {},
            onExportBoard = {},
            onDeleteBoard = {},
            onEnableKeyboard = {},
            onSelectKeyboard = {},
            onOpenDiscover = {},
        )
    }
}

/** Setup unfinished: the banner is present and leaves once both steps are done. */
@ScreenPreviews
@Composable
private fun BoardsSetupPreview() {
    PictoKeyboardTheme {
        BoardsScreenContent(
            summaries = listOf(previewSummary("PictoKeyboard", active = true)),
            status = KeyboardStatus(enabled = true, selected = false),
            onOpenBoard = {},
            onUseBoard = {},
            onDuplicateBoard = {},
            onExportBoard = {},
            onDeleteBoard = {},
            onEnableKeyboard = {},
            onSelectKeyboard = {},
            onOpenDiscover = {},
        )
    }
}

/** A caregiver with nothing yet, pointed at Discover. */
@ScreenPreviews
@Composable
private fun BoardsEmptyPreview() {
    PictoKeyboardTheme {
        BoardsScreenContent(
            summaries = emptyList(),
            status = KeyboardStatus(enabled = true, selected = true),
            onOpenBoard = {},
            onUseBoard = {},
            onDuplicateBoard = {},
            onExportBoard = {},
            onDeleteBoard = {},
            onEnableKeyboard = {},
            onSelectKeyboard = {},
            onOpenDiscover = {},
        )
    }
}
