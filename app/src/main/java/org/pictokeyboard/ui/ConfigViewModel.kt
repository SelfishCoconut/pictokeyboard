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
import org.pictokeyboard.data.prefs.Settings
import org.pictokeyboard.data.repo.BoardSummary
import org.pictokeyboard.data.repo.CategoryIcon
import org.pictokeyboard.data.seed.CategoryTemplate

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
        icon: CategoryIcon = CategoryIcon.None,
    ) = viewModelScope.launch {
        repo.addCategory(name, colorArgb, borderStyle, borderWidthDp, icon)
    }

    fun updateCategory(category: CategoryEntity, icon: CategoryIcon) = viewModelScope.launch {
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
    fun saveBoard(board: BoardEntity) = viewModelScope.launch {
        repo.updateBoard(
            board.copy(
                columns = BoardEntity.clampColumns(board.columns),
                rows = BoardEntity.clampRows(board.rows),
            ),
        )
    }

    fun setAddSpace(value: Boolean) = viewModelScope.launch { settingsStore.setAddSpaceAfter(value) }
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

    // --- Backup ------------------------------------------------------------

    /** Exports [boardId], or the board in use when no board is named. */
    suspend fun exportJson(boardId: String? = null): String {
        val board = boardId?.let { repo.board(it) } ?: repo.activeBoard() ?: return ""
        return backup.export(language = board.language, boardId = board.id)
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
