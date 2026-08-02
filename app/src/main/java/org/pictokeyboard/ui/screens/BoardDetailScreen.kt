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
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.pictokeyboard.R
import org.pictokeyboard.data.db.BoardEntity
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.data.db.PictoEntity
import org.pictokeyboard.data.db.UsageEntity
import org.pictokeyboard.data.repo.BoardSummary
import org.pictokeyboard.data.repo.CategoryIcon
import org.pictokeyboard.data.seed.CategoryTemplate
import org.pictokeyboard.data.seed.CategoryTemplates
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
        // The only place the picker meets the view model. Everything below this
        // call stays previewable without one -- see CategoryPickerSlot.
        pickerDialog = { categoryId, onDismissPicker, onPicked ->
            CategoryIconPickerDialog(
                viewModel = viewModel,
                categoryId = categoryId,
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

/** Stateless board detail. Tab and dialog state is local; the board is not. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoardDetailContent(
    summary: BoardSummary,
    pictoCounts: Map<String, Int>,
    status: KeyboardStatus,
    onBack: () -> Unit,
    onOpenCategory: (String) -> Unit,
    onSaveBoard: (BoardEntity) -> Unit,
    onReorder: (List<CategoryEntity>) -> Unit,
    onMove: (CategoryEntity, Boolean) -> Unit,
    loadSuggested: suspend () -> List<UsageEntity>,
    onAddFromTemplate: (CategoryTemplate) -> Unit,
    onAddSuggested: (String, List<UsageEntity>) -> Unit,
    onAddBlank: (CategoryEdit) -> Unit,
    onUpdateCategory: (CategoryEntity, CategoryIcon) -> Unit,
    onDeleteCategory: (CategoryEntity) -> Unit,
    onEnableKeyboard: () -> Unit,
    onSelectKeyboard: () -> Unit,
    pickerDialog: CategoryPickerSlot,
) {
    var tab by rememberSaveable { mutableStateOf(BoardTab.Categories) }
    var dialog by remember { mutableStateOf<CategoryDialog?>(null) }
    var reordering by remember { mutableStateOf(false) }
    var trying by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            BoardDetailTopBar(
                name = summary.board.name,
                onBack = onBack,
                onTryIt = { trying = true },
            )
        },
        floatingActionButton = {
            if (tab == BoardTab.Categories && !reordering) {
                AddFab(
                    contentDescription = stringResource(R.string.category_add),
                    onClick = { dialog = CategoryDialog.Chooser },
                )
            }
        },
    ) { padding ->
        BoardDetailBody(
            tab = tab,
            onSelectTab = { tab = it },
            summary = summary,
            pictoCounts = pictoCounts,
            reordering = reordering,
            onToggleReorder = { reordering = !reordering },
            onOpenCategory = onOpenCategory,
            onSaveBoard = onSaveBoard,
            onReorder = onReorder,
            onMove = onMove,
            onEditCategory = { dialog = CategoryDialog.Edit(it) },
            onDeleteCategory = { dialog = CategoryDialog.Delete(it) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }

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
    )
}

/** Everything the board detail draws over itself: the category dialogs, and Try it. */
@Composable
private fun BoardDetailOverlays(
    summary: BoardSummary,
    dialog: CategoryDialog?,
    onDialog: (CategoryDialog?) -> Unit,
    loadSuggested: suspend () -> List<UsageEntity>,
    onAddFromTemplate: (CategoryTemplate) -> Unit,
    onAddSuggested: (String, List<UsageEntity>) -> Unit,
    onAddBlank: (CategoryEdit) -> Unit,
    onUpdateCategory: (CategoryEntity, CategoryIcon) -> Unit,
    onDeleteCategory: (CategoryEntity) -> Unit,
    pickerDialog: CategoryPickerSlot,
    trying: Boolean,
    status: KeyboardStatus,
    onEnableKeyboard: () -> Unit,
    onSelectKeyboard: () -> Unit,
    onStopTrying: () -> Unit,
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
}

/** The tab strip and whichever half of the board is selected. */
@Composable
private fun BoardDetailBody(
    tab: BoardTab,
    onSelectTab: (BoardTab) -> Unit,
    summary: BoardSummary,
    pictoCounts: Map<String, Int>,
    reordering: Boolean,
    onToggleReorder: () -> Unit,
    onOpenCategory: (String) -> Unit,
    onSaveBoard: (BoardEntity) -> Unit,
    onReorder: (List<CategoryEntity>) -> Unit,
    onMove: (CategoryEntity, Boolean) -> Unit,
    onEditCategory: (CategoryEntity) -> Unit,
    onDeleteCategory: (CategoryEntity) -> Unit,
    modifier: Modifier = Modifier,
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
            )

            BoardTab.Layout -> BoardLayoutTab(summary = summary, onSaveBoard = onSaveBoard)
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

/** Enough rows for a preview to show the palette without scrolling. */
private const val PREVIEW_CATEGORIES = 5
private const val PREVIEW_PICTOS = 12

/** Preview counts fan out per category, so the row's second line varies. */
private const val PREVIEW_COUNT_STEP = 4

internal fun previewBoardSummary(
    columns: Int = BoardEntity.DEFAULT_COLUMNS,
    rows: Int = BoardEntity.DEFAULT_ROWS,
    showLabels: Boolean = true,
): BoardSummary {
    // Built from the real templates, so the preview shows the actual palette.
    val categories = CategoryTemplates.all.take(PREVIEW_CATEGORIES).mapIndexed { i, template ->
        CategoryEntity(
            id = template.id,
            boardId = "b1",
            name = template.name("es"),
            colorArgb = template.color.toInt(),
            iconArasaacId = template.iconArasaacId,
            position = i,
            builtin = true,
        )
    }
    return BoardSummary(
        board = BoardEntity(
            id = "b1",
            name = "Casa",
            colorArgb = BoardEntity.DEFAULT_COLOR_ARGB,
            position = 0,
            active = true,
            columns = columns,
            rows = rows,
            showLabels = showLabels,
        ),
        categories = categories,
        heroPictos = List(PREVIEW_PICTOS) { index ->
            PictoEntity(
                id = "p$index",
                categoryId = categories.first().id,
                label = "palabra $index",
                spokenText = "palabra $index",
                language = "es",
                position = index,
            )
        },
        pictoCount = 84,
    )
}

@Composable
private fun BoardDetailPreview(summary: BoardSummary) {
    PictoKeyboardTheme {
        BoardDetailContent(
            summary = summary,
            pictoCounts = summary.categories
                .mapIndexed { i, category -> category.id to i * PREVIEW_COUNT_STEP }
                .toMap(),
            status = KeyboardStatus(enabled = true, selected = true),
            onBack = {},
            onOpenCategory = {},
            onSaveBoard = {},
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
