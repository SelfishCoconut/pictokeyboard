package org.pictokeyboard.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.pictokeyboard.R
import org.pictokeyboard.data.db.BoardEntity
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.data.db.UsageEntity
import org.pictokeyboard.data.repo.BoardSummary
import org.pictokeyboard.data.repo.IconChoice
import org.pictokeyboard.data.seed.CategoryTemplate
import org.pictokeyboard.ui.ConfigViewModel
import org.pictokeyboard.ui.theme.PictoKeyboardTheme
import org.pictokeyboard.ui.theme.ScreenPreviews

/**
 * One board, as it is actually worked on: **Categories** and **Layout**, with
 * **Try it** in the top bar.
 *
 * The two tabs are the two halves of building a board, and they were previously
 * in different places — categories behind a board card, and the grid it is drawn
 * on buried in global Settings, where one set of values had to serve every
 * board. Columns, rows and captions describe the *situation* rather than the
 * person (#31), so they belong on the situation.
 *
 * **Try it** is what the app did not have at all: a way to see the board without
 * leaving for another app.
 */
@Composable
fun BoardDetailScreen(
    viewModel: ConfigViewModel,
    boardId: String,
    status: KeyboardStatus,
    onBack: () -> Unit,
    onOpenCategory: (String) -> Unit,
    onEnableKeyboard: () -> Unit,
    onSelectKeyboard: () -> Unit,
) {
    val summaries by viewModel.boardSummaries.collectAsStateWithLifecycle()
    val pictoCounts by viewModel.categoryPictoCounts.collectAsStateWithLifecycle()
    val summary = summaries.firstOrNull { it.board.id == boardId }

    if (summary == null) {
        // The first frame before the query lands, or a board deleted from
        // another screen. Either way there is nothing to draw but the way out.
        MissingBoardScaffold(onBack)
        return
    }

    BoardDetailContent(
        summary = summary,
        pictoCounts = pictoCounts,
        // Everywhere a category could go. Empty on a device with one board, and
        // the Move action then never appears.
        otherBoards = summaries.filter { it.board.id != boardId },
        onMoveCategory = viewModel::moveCategoryToBoard,
        onUndoMove = viewModel::restoreCategory,
        // How many boards the keyboard will offer as tabs. The Layout preview
        // needs it to know whether the strip costs the grid any height.
        keyboardBoardCount = summaries.count { it.board.showInKeyboard },
        status = status,
        onBack = onBack,
        onOpenCategory = onOpenCategory,
        onSaveBoard = viewModel::saveBoard,
        onReorder = viewModel::reorderCategories,
        onMove = viewModel::moveCategory,
        loadSuggested = { viewModel.topUsed() },
        onAddFromTemplate = { template ->
            viewModel.addCategoryFromTemplate(template, summary.board.language)
        },
        onAddSuggested = { name, records -> viewModel.addSuggestedCategory(name, records) },
        onAddBlank = { edit ->
            viewModel.addCategory(edit.name, edit.color, edit.borderStyle, edit.borderWidthDp, edit.icon)
        },
        onUpdateCategory = { category, icon -> viewModel.updateCategory(category, icon) },
        onDeleteCategory = viewModel::deleteCategory,
        onEnableKeyboard = onEnableKeyboard,
        onSelectKeyboard = onSelectKeyboard,
        onSaveBoardIcon = viewModel::saveBoardIcon,
        // The only place the picker meets the view model. Everything below this
        // call stays previewable without one -- see IconPickerSlot. One slot
        // serves the category editor and the board editor alike, since IconOwner
        // already carries everything that differs between them.
        pickerDialog = { owner, onDismissPicker, onPicked ->
            IconPickerDialog(
                viewModel = viewModel,
                owner = owner,
                language = summary.board.language,
                onDismiss = onDismissPicker,
                onPicked = onPicked,
            )
        },
    )
}

/** Which half of the board is on screen. */
private enum class BoardTab(val label: Int) {
    Categories(R.string.board_tab_categories),
    Layout(R.string.board_tab_layout),
}

/**
 * Stateless board detail. Tab and dialog state is local; the board is not.
 *
 * This function owns the screen's five pieces of local state and nothing else —
 * the chrome around them is [BoardDetailScaffold] and the things drawn over them
 * are [BoardDetailOverlays]. That split is not cosmetic: every one of those
 * states is written from one place and read from two, and with the scaffold
 * inlined here the writes were forty lines away from the reads.
 */
@Composable
internal fun BoardDetailContent(
    summary: BoardSummary,
    pictoCounts: Map<String, Int>,
    otherBoards: List<BoardSummary>,
    onMoveCategory: (CategoryEntity, String, (CategoryEntity) -> Unit) -> Unit,
    onUndoMove: (CategoryEntity) -> Unit,
    keyboardBoardCount: Int,
    status: KeyboardStatus,
    onBack: () -> Unit,
    onOpenCategory: (String) -> Unit,
    onSaveBoard: (BoardEntity) -> Unit,
    onSaveBoardIcon: (BoardEntity, IconChoice) -> Unit,
    onReorder: (List<CategoryEntity>) -> Unit,
    onMove: (CategoryEntity, Boolean) -> Unit,
    loadSuggested: suspend () -> List<UsageEntity>,
    onAddFromTemplate: (CategoryTemplate) -> Unit,
    onAddSuggested: (String, List<UsageEntity>) -> Unit,
    onAddBlank: (CategoryEdit) -> Unit,
    onUpdateCategory: (CategoryEntity, IconChoice) -> Unit,
    onDeleteCategory: (CategoryEntity) -> Unit,
    onEnableKeyboard: () -> Unit,
    onSelectKeyboard: () -> Unit,
    pickerDialog: IconPickerSlot,
) {
    var tab by rememberSaveable { mutableStateOf(BoardTab.Categories) }
    var dialog by remember { mutableStateOf<CategoryDialog?>(null) }
    var reordering by remember { mutableStateOf(false) }
    var trying by rememberSaveable { mutableStateOf(false) }
    var moving by remember { mutableStateOf<CategoryEntity?>(null) }
    val snackbars = remember { SnackbarHostState() }

    BoardDetailScaffold(
        summary = summary,
        pictoCounts = pictoCounts,
        keyboardBoardCount = keyboardBoardCount,
        snackbars = snackbars,
        tab = tab,
        onSelectTab = { tab = it },
        reordering = reordering,
        onToggleReorder = { reordering = !reordering },
        onBack = onBack,
        onTryIt = { trying = true },
        onNewCategory = { dialog = CategoryDialog.Chooser },
        onOpenCategory = onOpenCategory,
        onSaveBoard = onSaveBoard,
        onSaveBoardIcon = onSaveBoardIcon,
        onReorder = onReorder,
        onMove = onMove,
        onEditCategory = { dialog = CategoryDialog.Edit(it) },
        onDeleteCategory = { dialog = CategoryDialog.Delete(it) },
        // Absent rather than disabled when this is the only board: there is
        // nowhere to move to, and a control that can never become enabled is a
        // question with no answer.
        onMoveToBoard = if (otherBoards.isEmpty()) null else ({ moving = it }),
        pickerDialog = pickerDialog,
    )

    BoardDetailOverlays(
        summary = summary,
        dialog = dialog,
        onDialog = { dialog = it },
        loadSuggested = loadSuggested,
        onAddFromTemplate = onAddFromTemplate,
        onAddSuggested = onAddSuggested,
        onAddBlank = onAddBlank,
        onUpdateCategory = onUpdateCategory,
        onDeleteCategory = onDeleteCategory,
        pickerDialog = pickerDialog,
        trying = trying,
        status = status,
        onEnableKeyboard = onEnableKeyboard,
        onSelectKeyboard = onSelectKeyboard,
        onStopTrying = { trying = false },
        moving = moving,
        otherBoards = otherBoards,
        snackbars = snackbars,
        onStopMoving = { moving = null },
        onMoveCategory = onMoveCategory,
        onUndoMove = onUndoMove,
    )
}

/** The screen's chrome: top bar, snackbars, the add button, and the two tabs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoardDetailScaffold(
    summary: BoardSummary,
    pictoCounts: Map<String, Int>,
    keyboardBoardCount: Int,
    snackbars: SnackbarHostState,
    tab: BoardTab,
    onSelectTab: (BoardTab) -> Unit,
    reordering: Boolean,
    onToggleReorder: () -> Unit,
    onBack: () -> Unit,
    onTryIt: () -> Unit,
    onNewCategory: () -> Unit,
    onOpenCategory: (String) -> Unit,
    onSaveBoard: (BoardEntity) -> Unit,
    onSaveBoardIcon: (BoardEntity, IconChoice) -> Unit,
    onReorder: (List<CategoryEntity>) -> Unit,
    onMove: (CategoryEntity, Boolean) -> Unit,
    onEditCategory: (CategoryEntity) -> Unit,
    onDeleteCategory: (CategoryEntity) -> Unit,
    onMoveToBoard: ((CategoryEntity) -> Unit)?,
    pickerDialog: IconPickerSlot,
) {
    Scaffold(
        topBar = { BoardDetailTopBar(summary.board.name, onBack, onTryIt) },
        snackbarHost = { SnackbarHost(snackbars) },
        floatingActionButton = {
            AddCategoryFab(
                visible = tab == BoardTab.Categories && !reordering,
                onClick = onNewCategory,
            )
        },
    ) { padding ->
        BoardDetailBody(
            tab = tab,
            onSelectTab = onSelectTab,
            summary = summary,
            pictoCounts = pictoCounts,
            keyboardBoardCount = keyboardBoardCount,
            reordering = reordering,
            onToggleReorder = onToggleReorder,
            onOpenCategory = onOpenCategory,
            onSaveBoard = onSaveBoard,
            onSaveBoardIcon = onSaveBoardIcon,
            onReorder = onReorder,
            onMove = onMove,
            pickerDialog = pickerDialog,
            onEditCategory = onEditCategory,
            onDeleteCategory = onDeleteCategory,
            onMoveToBoard = onMoveToBoard,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

/**
 * Adding a category, offered only on the tab that holds categories and never
 * while the list is being reordered — a FAB over a drag is a target the
 * caregiver hits by accident mid-gesture.
 */
@Composable
private fun AddCategoryFab(visible: Boolean, onClick: () -> Unit) {
    if (!visible) return
    AddFab(contentDescription = stringResource(R.string.category_add), onClick = onClick)
}

/**
 * Everything the board detail draws over itself: the category dialogs, Try it,
 * and the move sheet with the snackbar that undoes it.
 */
@Composable
private fun BoardDetailOverlays(
    summary: BoardSummary,
    dialog: CategoryDialog?,
    onDialog: (CategoryDialog?) -> Unit,
    loadSuggested: suspend () -> List<UsageEntity>,
    onAddFromTemplate: (CategoryTemplate) -> Unit,
    onAddSuggested: (String, List<UsageEntity>) -> Unit,
    onAddBlank: (CategoryEdit) -> Unit,
    onUpdateCategory: (CategoryEntity, IconChoice) -> Unit,
    onDeleteCategory: (CategoryEntity) -> Unit,
    pickerDialog: IconPickerSlot,
    trying: Boolean,
    status: KeyboardStatus,
    onEnableKeyboard: () -> Unit,
    onSelectKeyboard: () -> Unit,
    onStopTrying: () -> Unit,
    moving: CategoryEntity?,
    otherBoards: List<BoardSummary>,
    snackbars: SnackbarHostState,
    onStopMoving: () -> Unit,
    onMoveCategory: (CategoryEntity, String, (CategoryEntity) -> Unit) -> Unit,
    onUndoMove: (CategoryEntity) -> Unit,
) {
    CategoryDialogs(
        dialog = dialog,
        language = summary.board.language,
        onDismiss = { onDialog(null) },
        onWantBlank = { onDialog(CategoryDialog.Blank) },
        loadSuggested = loadSuggested,
        onAddFromTemplate = onAddFromTemplate,
        onAddSuggested = onAddSuggested,
        onAddBlank = onAddBlank,
        onUpdate = onUpdateCategory,
        onDelete = onDeleteCategory,
        pickerDialog = pickerDialog,
    )

    if (trying) {
        TryItSheet(
            boardName = summary.board.name,
            status = status,
            onEnableKeyboard = onEnableKeyboard,
            onSelectKeyboard = onSelectKeyboard,
            onDismiss = onStopTrying,
        )
    }

    // Owned here rather than inside the sheet, because the sheet dismisses
    // itself the moment a destination is picked and takes its own scope with it.
    // The undo snackbar has to outlive the thing that triggered it.
    val undoScope = rememberCoroutineScope()

    moving?.let { category ->
        MoveCategoryFlow(
            category = category,
            boards = otherBoards,
            snackbars = snackbars,
            scope = undoScope,
            onDismiss = onStopMoving,
            onMoveCategory = onMoveCategory,
            onUndoMove = onUndoMove,
        )
    }
}

/** The tab strip and whichever half of the board is selected. */
@Composable
private fun BoardDetailBody(
    tab: BoardTab,
    onSelectTab: (BoardTab) -> Unit,
    summary: BoardSummary,
    pictoCounts: Map<String, Int>,
    keyboardBoardCount: Int,
    reordering: Boolean,
    onToggleReorder: () -> Unit,
    onOpenCategory: (String) -> Unit,
    onSaveBoard: (BoardEntity) -> Unit,
    onSaveBoardIcon: (BoardEntity, IconChoice) -> Unit,
    onReorder: (List<CategoryEntity>) -> Unit,
    onMove: (CategoryEntity, Boolean) -> Unit,
    pickerDialog: IconPickerSlot,
    onEditCategory: (CategoryEntity) -> Unit,
    onDeleteCategory: (CategoryEntity) -> Unit,
    modifier: Modifier = Modifier,
    onMoveToBoard: ((CategoryEntity) -> Unit)? = null,
) {
    Column(modifier = modifier) {
        PrimaryTabRow(selectedTabIndex = tab.ordinal) {
            BoardTab.entries.forEach { entry ->
                Tab(
                    selected = tab == entry,
                    onClick = { onSelectTab(entry) },
                    text = { Text(stringResource(entry.label)) },
                )
            }
        }
        when (tab) {
            BoardTab.Categories -> BoardCategoriesTab(
                categories = summary.categories,
                pictoCounts = pictoCounts,
                reordering = reordering,
                onToggleReorder = onToggleReorder,
                onOpenCategory = onOpenCategory,
                onReorder = onReorder,
                onMove = onMove,
                onEdit = onEditCategory,
                onDelete = onDeleteCategory,
                onMoveToBoard = onMoveToBoard,
            )

            BoardTab.Layout -> BoardLayoutTab(
                summary = summary,
                keyboardBoardCount = keyboardBoardCount,
                onSaveBoard = onSaveBoard,
                onSaveBoardIcon = onSaveBoardIcon,
                pickerDialog = pickerDialog,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoardDetailTopBar(name: String, onBack: () -> Unit, onTryIt: () -> Unit) {
    TopAppBar(
        title = {
            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }
        },
        actions = {
            TextButton(onClick = onTryIt) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text(stringResource(R.string.try_it))
            }
        },
    )
}

/** Nothing to show but the way back — see the null case in [BoardDetailScreen]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MissingBoardScaffold(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {}
    }
}

// --- Previews ---------------------------------------------------------------

/** Preview counts fan out per category, so the row's second line varies. */
private const val PREVIEW_COUNT_STEP = 4

@Composable
private fun BoardDetailPreview(summary: BoardSummary) {
    PictoKeyboardTheme {
        BoardDetailContent(
            summary = summary,
            pictoCounts = summary.categories
                .mapIndexed { i, category -> category.id to i * PREVIEW_COUNT_STEP }
                .toMap(),
            otherBoards = emptyList(),
            onMoveCategory = { _, _, _ -> },
            onUndoMove = {},
            keyboardBoardCount = 1,
            status = KeyboardStatus(enabled = true, selected = true),
            onBack = {},
            onOpenCategory = {},
            onSaveBoard = {},
            onSaveBoardIcon = { _, _ -> },
            onReorder = {},
            onMove = { _, _ -> },
            loadSuggested = { emptyList() },
            onAddFromTemplate = {},
            onAddSuggested = { _, _ -> },
            onAddBlank = {},
            onUpdateCategory = { _, _ -> },
            onDeleteCategory = {},
            onEnableKeyboard = {},
            onSelectKeyboard = {},
            // No view model in a preview, so no picker. The editor still renders;
            // tapping "choose picto" simply does nothing here.
            pickerDialog = { _, _, _ -> },
        )
    }
}

@ScreenPreviews
@Composable
private fun BoardDetailCategoriesPreview() {
    BoardDetailPreview(previewBoardSummary())
}
