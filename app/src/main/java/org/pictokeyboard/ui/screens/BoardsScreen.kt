package org.pictokeyboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
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
    onImportBoard: () -> Unit,
    onCreateBoard: (String) -> Unit,
    onCopyBoard: (BoardEntity, String) -> Unit,
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
        onImportBoard = onImportBoard,
        onCreateBoard = onCreateBoard,
        onCopyBoard = onCopyBoard,
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
    onImportBoard: () -> Unit,
    onCreateBoard: (String) -> Unit,
    onCopyBoard: (BoardEntity, String) -> Unit,
) {
    // Which board's Try it sheet is open, if any. Held here rather than on the
    // card so the sheet outlives the dropdown menu that asked for it.
    var trying by remember { mutableStateOf<BoardEntity?>(null) }
    // How far through making a board the caregiver is, or null for "not making
    // one". One value rather than a flag per step, so "choosing a source and
    // naming it at once" cannot be expressed.
    var newBoard by remember { mutableStateOf<NewBoardStep?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.boards_title)) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            // Extended rather than an icon alone: "+" on a list of boards could
            // mean a board, a category or a word, and the caregiver reading it
            // is usually mid-task and not fluent in the app.
            ExtendedFloatingActionButton(
                onClick = { newBoard = NewBoardStep.ChoosingSource },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.boards_new)) },
            )
        },
    ) { padding ->
        BoardsList(
            summaries = summaries,
            status = status,
            padding = padding,
            onOpenBoard = onOpenBoard,
            onUseBoard = onUseBoard,
            // The keyboard shows the board in use, so trying one hands it over
            // first. Same rule as opening a board to edit it: you work on the
            // board that is live.
            onTryBoard = { board ->
                onUseBoard(board)
                trying = board
            },
            onDuplicateBoard = onDuplicateBoard,
            onExportBoard = onExportBoard,
            onDeleteBoard = onDeleteBoard,
            onEnableKeyboard = onEnableKeyboard,
            onSelectKeyboard = onSelectKeyboard,
            onImportBoard = onImportBoard,
        )
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

    NewBoardFlow(
        step = newBoard,
        summaries = summaries,
        onStep = { newBoard = it },
        onCreateBoard = onCreateBoard,
        onCopyBoard = onCopyBoard,
    )
}

/** The cards themselves, and the setup banner above them while it is still owed. */
@Composable
private fun BoardsList(
    summaries: List<BoardSummary>,
    status: KeyboardStatus,
    padding: PaddingValues,
    onOpenBoard: (BoardSummary) -> Unit,
    onUseBoard: (BoardEntity) -> Unit,
    onTryBoard: (BoardEntity) -> Unit,
    onDuplicateBoard: (BoardEntity) -> Unit,
    onExportBoard: (BoardEntity) -> Unit,
    onDeleteBoard: (BoardEntity) -> Unit,
    onEnableKeyboard: () -> Unit,
    onSelectKeyboard: () -> Unit,
    onImportBoard: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(
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
            item(key = "empty") { BoardsEmptyState(onImportBoard = onImportBoard) }
        } else {
            items(summaries, key = { it.board.id }) { summary ->
                BoardCard(
                    summary = summary,
                    onOpen = { onOpenBoard(summary) },
                    onUse = { onUseBoard(summary.board) },
                    onTryIt = { onTryBoard(summary.board) },
                    onDuplicate = { onDuplicateBoard(summary.board) },
                    onExport = { onExportBoard(summary.board) },
                    onDelete = { onDeleteBoard(summary.board) },
                )
            }
        }
    }
}

/**
 * How far through making a board the caregiver is.
 *
 * [Naming.source] is null on the from-scratch route, which is why the step
 * carries a nullable board rather than the screen holding one: `null` alone
 * cannot tell "make an empty one" apart from "nobody has asked for a board".
 */
private sealed interface NewBoardStep {
    data object ChoosingSource : NewBoardStep
    data class Naming(val source: BoardEntity?) : NewBoardStep
}

/** Whichever of the two steps [step] names, or nothing when it is null. */
@Composable
private fun NewBoardFlow(
    step: NewBoardStep?,
    summaries: List<BoardSummary>,
    onStep: (NewBoardStep?) -> Unit,
    onCreateBoard: (String) -> Unit,
    onCopyBoard: (BoardEntity, String) -> Unit,
) {
    when (step) {
        null -> Unit

        NewBoardStep.ChoosingSource -> NewBoardSheet(
            boards = summaries,
            onDismiss = { onStep(null) },
            onCopy = { board -> onStep(NewBoardStep.Naming(board)) },
            onScratch = { onStep(NewBoardStep.Naming(null)) },
        )

        is NewBoardStep.Naming -> {
            val source = step.source
            BoardNameDialog(
                initialName = if (source != null) {
                    stringResource(R.string.boards_copy_name, source.name)
                } else {
                    stringResource(R.string.boards_new_default_name)
                },
                onDismiss = { onStep(null) },
                onConfirm = { name ->
                    onStep(null)
                    if (source != null) onCopyBoard(source, name) else onCreateBoard(name)
                },
            )
        }
    }
}

/**
 * No boards at all.
 *
 * This pointed at Discover, which was going to be a catalogue of other people's
 * boards and never became one. With no server there is nothing to browse, so it
 * offers the thing that actually exists: opening a `.pkb` somebody sent you.
 * Starting from an empty grid is the hardest moment in every AAC product, and a
 * board built by another caregiver is the shortest way out of it.
 */
@Composable
private fun BoardsEmptyState(onImportBoard: () -> Unit) {
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
        Button(onClick = onImportBoard) {
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
            onImportBoard = {},
            onCreateBoard = {},
            onCopyBoard = { _, _ -> },
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
            onImportBoard = {},
            onCreateBoard = {},
            onCopyBoard = { _, _ -> },
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
            onImportBoard = {},
            onCreateBoard = {},
            onCopyBoard = { _, _ -> },
        )
    }
}
