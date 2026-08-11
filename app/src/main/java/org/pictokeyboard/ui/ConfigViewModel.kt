package org.pictokeyboard.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.pictokeyboard.App
import org.pictokeyboard.R
import org.pictokeyboard.data.arasaac.ArasaacOptions
import org.pictokeyboard.data.arasaac.ArasaacResult
import org.pictokeyboard.data.db.BoardEntity
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.data.db.PictoEntity
import org.pictokeyboard.data.db.UsageEntity
import org.pictokeyboard.data.pkb.PkbImportSummary
import org.pictokeyboard.data.prefs.Settings
import org.pictokeyboard.data.repo.BoardSummary
import org.pictokeyboard.data.repo.IconChoice
import org.pictokeyboard.data.seed.CategoryTemplate
import java.io.InputStream
import java.io.OutputStream

/** Search panel UI state for the ARASAAC picker. */
sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Results(val items: List<ArasaacResult>) : SearchState
    data object Empty : SearchState
    data object Error : SearchState
}

class ConfigViewModel : ViewModel() {

    private companion object {
        /** Frame colour for the auto-generated Suggested category (teal). */
        const val SUGGESTED_COLOR = 0xFF00897B.toInt()
    }

    private val locator = App.locator()
    private val repo = locator.pictoRepository
    private val settingsStore = locator.settings
    private val backup = locator.backupManager
    private val pkb = locator.pkbBackup
    private val arasaac = locator.arasaacRepository

    val categories: StateFlow<List<CategoryEntity>> =
        repo.observeActiveBoardCategories()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * The board in use. Null only until the first read lands; every layout
     * control reads from it and writes back through [updateBoard].
     */
    val activeBoard: StateFlow<BoardEntity?> =
        repo.observeActiveBoard().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Live total pictogram count, shown on the dashboard. */
    val pictoCount: StateFlow<Int> =
        repo.observePictoCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val settings: StateFlow<Settings> =
        settingsStore.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Settings())

    /** Every board with the counts and miniature its card needs. */
    val boardSummaries: StateFlow<List<BoardSummary>> =
        repo.observeBoardSummaries()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Pictogram count per category id, for the board's Categories list. */
    val categoryPictoCounts: StateFlow<Map<String, Int>> =
        repo.observeCategoryPictoCounts()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _search = MutableStateFlow<SearchState>(SearchState.Idle)
    val search: StateFlow<SearchState> = _search

    init {
        viewModelScope.launch {
            val interfaceLanguage = settingsStore.current().defaultLanguage
            repo.seedIfEmpty(
                language = interfaceLanguage,
                boardName = locator.appContext.getString(R.string.app_name),
            )
            // Layout the user chose before boards existed lives in DataStore,
            // which the Room migration that created the board could not read.
            // Applying it here is what keeps the promise that the keyboard
            // behaves identically after upgrading.
            settingsStore.legacyBoardLayout()?.let { legacy ->
                repo.adoptLegacyBoardLayout(legacy)
                settingsStore.clearLegacyBoardLayout()
            }
        }
    }

    fun pictos(categoryId: String): Flow<List<PictoEntity>> = repo.observePictos(categoryId)

    // --- Categories --------------------------------------------------------

    fun addCategory(
        name: String,
        colorArgb: Int,
        borderStyle: String,
        borderWidthDp: Int,
        icon: IconChoice = IconChoice.None,
    ) = viewModelScope.launch {
        repo.addCategory(name, colorArgb, borderStyle, borderWidthDp, icon)
    }

    fun updateCategory(category: CategoryEntity, icon: IconChoice) = viewModelScope.launch {
        repo.updateCategory(category, icon)
    }

    fun deleteCategory(category: CategoryEntity) = viewModelScope.launch {
        repo.deleteCategory(category)
    }

    /** Creates a custom category pre-filled from a [template], then the admin can tweak it. */
    fun addCategoryFromTemplate(template: CategoryTemplate, language: String, onDone: () -> Unit = {}) =
        viewModelScope.launch {
            repo.addCategoryFromTemplate(template, language)
            onDone()
        }

    fun moveCategory(category: CategoryEntity, up: Boolean) {
        val reordered = movedBy(categories.value, { it.id == category.id }, up) ?: return
        viewModelScope.launch { repo.reorderCategories(reordered) }
    }

    /** Persists a drag-and-drop reordering of the categories. */
    fun reorderCategories(ordered: List<CategoryEntity>) = viewModelScope.launch {
        repo.reorderCategories(ordered)
    }

    /**
     * Moves [category] onto another board, handing back the row as it was (#119).
     *
     * The callback carries the undo rather than the screen remembering it. A
     * caregiver moving a category is looking at a list that is about to lose a
     * row, and the only moment the *old* board and position are knowable is
     * before the write — so the write is what hands them over, and
     * [restoreCategory] is the whole of the reversal.
     */
    fun moveCategoryToBoard(
        category: CategoryEntity,
        targetBoardId: String,
        onMoved: (CategoryEntity) -> Unit,
    ) = viewModelScope.launch {
        onMoved(repo.moveCategoryToBoard(category, targetBoardId))
    }

    /** Undoes a [moveCategoryToBoard], with the row it handed back. */
    fun restoreCategory(category: CategoryEntity) = viewModelScope.launch {
        repo.restoreCategory(category)
    }

    /** Most-used words (highest first) used to preview/build the Suggested category. */
    suspend fun topUsed(limit: Int = 24): List<UsageEntity> = repo.topUsed(limit)

    /** Builds a category from the most-used words, in usage order. */
    fun addSuggestedCategory(
        name: String,
        records: List<UsageEntity>,
        onDone: () -> Unit = {},
    ) = viewModelScope.launch {
        repo.addSuggestedCategory(name, SUGGESTED_COLOR, records)
        onDone()
    }

    // --- Pictos ------------------------------------------------------------

    fun runSearch(query: String, language: String) {
        if (query.isBlank()) {
            _search.value = SearchState.Idle
            return
        }
        _search.value = SearchState.Loading
        viewModelScope.launch {
            arasaac.search(query, language)
                .onSuccess { list ->
                    _search.value = if (list.isEmpty()) SearchState.Empty else SearchState.Results(list)
                }
                .onFailure { _search.value = SearchState.Error }
        }
    }

    fun clearSearch() {
        _search.value = SearchState.Idle
    }

    fun addPicto(
        categoryId: String,
        result: ArasaacResult,
        spokenText: String,
        label: String,
        language: String,
        options: ArasaacOptions = ArasaacOptions(),
        colorArgbOverride: Int? = null,
        onDone: () -> Unit,
    ) = viewModelScope.launch {
        repo.addArasaacPicto(categoryId, result, spokenText, label, language, options, colorArgbOverride)
        onDone()
    }

    /** Decodes an image the user picked, downsampled, for the cropper. */
    suspend fun decodeImage(imageUri: Uri): android.graphics.Bitmap? = repo.loadImage(imageUri)

    /**
     * Saves a cropped image to the cache for use as a category's picto, calling
     * back with its path — or with null if the write failed, which the picker
     * surfaces rather than silently leaving the category unchanged.
     */
    fun saveIconImage(bitmap: android.graphics.Bitmap, onSaved: (String?) -> Unit) =
        viewModelScope.launch { onSaved(repo.saveImage(bitmap)) }

    /** Adds a cropped image as a picto (the bitmap is saved to the cache). */
    fun addCroppedImagePicto(
        categoryId: String,
        bitmap: android.graphics.Bitmap,
        spokenText: String,
        label: String,
        language: String,
        colorArgbOverride: Int? = null,
        onDone: () -> Unit,
    ) = viewModelScope.launch {
        repo.addBitmapPicto(categoryId, bitmap, spokenText, label, language, colorArgbOverride)
        onDone()
    }

    /** All pictos of [categoryId], one-shot (used by the cross-category picker). */
    suspend fun pictosOnce(categoryId: String): List<PictoEntity> = repo.pictos(categoryId)

    /** Every picto on [boardId], one-shot, for the board's own picto picker. */
    suspend fun boardPictosOnce(boardId: String): List<PictoEntity> = repo.boardPictos(boardId)

    /**
     * Copies [sources] into [categoryId]. Each keeps [sourceColor] — the colour
     * of the category it came from — as its frame colour.
     */
    fun copyPictosInto(
        categoryId: String,
        sources: List<Pair<PictoEntity, Int>>,
        onDone: () -> Unit,
    ) = viewModelScope.launch {
        sources.forEach { (picto, color) -> repo.copyPictoInto(categoryId, picto, color) }
        onDone()
    }

    /** Adds several ARASAAC pictos at once (used by the multi-select picker). */
    fun addPictos(
        categoryId: String,
        results: List<ArasaacResult>,
        language: String,
        onDone: () -> Unit,
    ) = viewModelScope.launch {
        results.forEach { r ->
            repo.addArasaacPicto(categoryId, r, r.keyword, r.keyword, language)
        }
        onDone()
    }

    fun updatePicto(picto: PictoEntity) = viewModelScope.launch { repo.updatePicto(picto) }

    fun deletePicto(picto: PictoEntity) = viewModelScope.launch { repo.deletePicto(picto) }

    fun movePicto(pictos: List<PictoEntity>, picto: PictoEntity, up: Boolean) {
        val reordered = movedBy(pictos, { it.id == picto.id }, up) ?: return
        viewModelScope.launch { repo.reorderPictos(reordered) }
    }

    // --- Settings ----------------------------------------------------------

    fun setLanguage(value: String) = viewModelScope.launch { settingsStore.setDefaultLanguage(value) }
    // --- Boards --------------------------------------------------------------

    fun useBoard(id: String) = viewModelScope.launch { repo.setActiveBoard(id) }

    /**
     * Copies [board] under a "(copy)" name.
     *
     * The naming convention lives here rather than at the call site: it is a
     * property of what duplicating means, not of the screen that offers it, and
     * a composable reaching for a formatted resource has to go through the
     * context to do it.
     */
    fun duplicateBoard(board: BoardEntity) = viewModelScope.launch {
        repo.duplicateBoard(
            board,
            locator.appContext.getString(R.string.boards_copy_name, board.name),
        )
    }

    /**
     * Creates an empty board called [name] and reports its id.
     *
     * The id goes back to the caller so the screen can open the board it just
     * made: a caregiver who asks for a new board wants to put words in it, and
     * leaving them on the list to find it themselves is a step that exists only
     * because of how the code is arranged.
     *
     * The board takes the interface language as its vocabulary language, which
     * is a starting point rather than a claim — the Layout tab can change it,
     * and a caregiver building an English board in a Spanish app is exactly the
     * case that setting exists for.
     */
    fun addBoard(name: String, onCreated: (String) -> Unit = {}) = viewModelScope.launch {
        val board = repo.addBoard(name = name, language = settingsStore.current().defaultLanguage)
        onCreated(board.id)
    }

    /** [duplicateBoard] with the name chosen by the caregiver rather than derived. */
    fun copyBoard(board: BoardEntity, name: String, onCreated: (String) -> Unit = {}) =
        viewModelScope.launch {
            onCreated(repo.duplicateBoard(board, name).id)
        }

    /**
     * Deletes [board] and everything on it.
     *
     * Refuses the last board rather than leaving the keyboard with nothing to
     * show: an empty grid mid-conversation is the one failure this product
     * cannot have. The caller is told, so it can say so rather than appearing
     * to have ignored the tap.
     */
    fun deleteBoard(board: BoardEntity, onResult: (Boolean) -> Unit = {}) = viewModelScope.launch {
        val boards = repo.observeBoards().first()
        if (boards.size <= 1) {
            onResult(false)
            return@launch
        }
        repo.deleteBoard(board)
        // Deleting the board in use would leave none active, so hand over
        // first — to whichever board now sits where this one did.
        if (board.active) {
            boards.filter { it.id != board.id }.minByOrNull { it.position }
                ?.let { repo.setActiveBoard(it.id) }
        }
        onResult(true)
    }

    // --- Board layout ------------------------------------------------------
    //
    // Columns, rows, captions, frame defaults and the board's own language
    // describe the situation rather than the person, so they write to the board
    // rather than to Settings (#31) and are edited on the board's own detail
    // screen rather than in Settings (#33).

    /**
     * Saves an edited board — layout, language, visibility.
     *
     * The clamp is here rather than at each control, so no caller can persist a
     * grid the keyboard cannot draw: the same guard has to hold for a slider,
     * for an imported pack and for a value inherited from before boards existed.
     */
    /**
     * Saves the board's picto, separately from [saveBoard].
     *
     * Its own call because choosing a picto can mean a download, and folding
     * that into the entity write would make every layout slider drag wait on a
     * suspend function that has nothing to do with it.
     */
    fun saveBoardIcon(board: BoardEntity, icon: IconChoice) = viewModelScope.launch {
        repo.updateBoardIcon(board, icon)
    }

    fun saveBoard(board: BoardEntity) = viewModelScope.launch {
        repo.updateBoard(
            board.copy(
                columns = BoardEntity.clampColumns(board.columns),
                rows = BoardEntity.clampRows(board.rows),
            ),
        )
    }

    fun setAddSpace(value: Boolean) = viewModelScope.launch { settingsStore.setAddSpaceAfter(value) }
    fun setHaptics(value: Boolean) = viewModelScope.launch { settingsStore.setHapticFeedback(value) }
    fun setHighContrast(value: Boolean) = viewModelScope.launch { settingsStore.setHighContrast(value) }
    fun setSpeak(value: Boolean) = viewModelScope.launch { settingsStore.setSpeakOnTap(value) }
    fun setTtsRate(value: Float) = viewModelScope.launch { settingsStore.setTtsRate(value) }
    fun setTtsPitch(value: Float) = viewModelScope.launch { settingsStore.setTtsPitch(value) }
    fun setBlindMode(value: Boolean) = viewModelScope.launch { settingsStore.setBlindMode(value) }

    // --- PIN ---------------------------------------------------------------

    fun setPin(pin: String, onDone: () -> Unit) = viewModelScope.launch {
        settingsStore.setPin(pin)
        onDone()
    }

    fun removePin() = viewModelScope.launch { settingsStore.clearPin() }

    suspend fun verifyPin(pin: String): Boolean = settingsStore.verifyPin(pin)

    // --- Backup (#88, #119) --------------------------------------------------

    /**
     * Writes every board, symbol, photograph and voice setting into [out].
     *
     * Nothing in this app goes to a server, so a caregiver who never runs this
     * has no backup at all.
     */
    fun exportEverything(out: OutputStream, onResult: (Result<PkbImportSummary>) -> Unit) =
        viewModelScope.launch {
            onResult(out.use { pkb.exportTo(it) })
        }

    /**
     * Writes one board — its categories, its symbols and their photographs —
     * into [out], as the same `.pkb` archive.
     *
     * This replaced a JSON export that carried no media, so handing somebody a
     * board built from photographs of their own kitchen sent them the labels
     * and none of the pictures. The failure was silent, which is the worst
     * shape for it: the file arrived, opened, and was quietly worth less than
     * it looked.
     */
    fun exportBoard(
        boardId: String,
        out: OutputStream,
        onResult: (Result<PkbImportSummary>) -> Unit,
    ) = viewModelScope.launch {
        onResult(out.use { pkb.exportTo(it, boardId) })
    }

    /** Adds the contents of a `.pkb` to this device. Never replaces what is here. */
    fun importEverything(
        source: () -> InputStream,
        onResult: (Result<PkbImportSummary>) -> Unit,
    ) = viewModelScope.launch {
        onResult(pkb.importFrom(source))
    }

    fun importJson(json: String, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        val board = repo.activeBoard()
        if (board == null) {
            onResult(false)
            return@launch
        }
        val result = backup.import(json, board.id)
        // The imported file's language describes the *vocabulary*, so it lands
        // on the board rather than on the interface language it used to
        // overwrite — importing an English board no longer flips the whole app
        // to English behind the caregiver's back (#31).
        result.getOrNull()?.let { repo.updateBoard(board.copy(language = it)) }
        onResult(result.isSuccess)
    }
}
