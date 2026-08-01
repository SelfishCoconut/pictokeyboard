package org.pictokeyboard.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.pictokeyboard.App
import org.pictokeyboard.data.arasaac.ArasaacOptions
import org.pictokeyboard.data.arasaac.ArasaacResult
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.data.db.PictoEntity
import org.pictokeyboard.data.db.UsageEntity
import org.pictokeyboard.data.prefs.Settings
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
        repo.observeCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Live total pictogram count, shown on the dashboard. */
    val pictoCount: StateFlow<Int> =
        repo.observePictoCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val settings: StateFlow<Settings> =
        settingsStore.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Settings())

    private val _search = MutableStateFlow<SearchState>(SearchState.Idle)
    val search: StateFlow<SearchState> = _search

    init {
        viewModelScope.launch {
            repo.seedIfEmpty(settingsStore.current().defaultLanguage)
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
    fun setColumns(value: Int) = viewModelScope.launch { settingsStore.setGridColumns(value) }
    fun setRows(value: Int) = viewModelScope.launch { settingsStore.setGridRows(value) }
    fun setShowLabels(value: Boolean) = viewModelScope.launch { settingsStore.setShowLabels(value) }
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

    suspend fun exportJson(): String = backup.export(settingsStore.current().defaultLanguage)

    fun importJson(json: String, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        val result = backup.import(json)
        result.getOrNull()?.let { settingsStore.setDefaultLanguage(it) }
        onResult(result.isSuccess)
    }
}
