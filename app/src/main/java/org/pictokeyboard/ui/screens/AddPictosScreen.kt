package org.pictokeyboard.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.pictokeyboard.data.arasaac.ArasaacOptions
import org.pictokeyboard.data.arasaac.ArasaacResult
import org.pictokeyboard.ui.ConfigViewModel
import org.pictokeyboard.ui.SearchState
import org.pictokeyboard.ui.theme.PictoKeyboardTheme

/**
 * Full-screen, visual ARASAAC picker. Search, tap thumbnails to select several
 * at once, then Add. Long-press a result to fine-tune its text, set a frame
 * colour and customize the pictogram with a live preview. The toolbar also
 * imports a picture from the device (with a crop step) or borrows existing
 * pictos from other categories.
 */
/**
 * Which modal the screen has open. One value rather than three loose flags, so
 * the crop-then-describe hand-off cannot leave both sheets showing at once.
 */
private sealed interface AddPictosModal {
    /** Imported image awaiting a crop. */
    data class Crop(val uri: Uri) : AddPictosModal

    /** Cropped bitmap awaiting its word and colour. */
    data class Describe(val bitmap: Bitmap) : AddPictosModal

    /** Borrowing pictos that already exist in another category. */
    data object Borrow : AddPictosModal
}

/**
 * Stateful wrapper. It owns the image picker and the modals that need the view
 * model, so [AddPictosScreenContent] -- the search-and-select part worth
 * previewing -- holds none of it.
 */
@Composable
fun AddPictosScreen(
    viewModel: ConfigViewModel,
    categoryId: String,
    defaultLanguage: String,
    onBack: () -> Unit,
) {
    val searchState by viewModel.search.collectAsStateWithLifecycle()
    var modal by remember { mutableStateOf<AddPictosModal?>(null) }
    var language by remember { mutableStateOf(defaultLanguage) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> if (uri != null) modal = AddPictosModal.Crop(uri) }

    LaunchedEffect(Unit) { viewModel.clearSearch() }

    AddPictosScreenContent(
        searchState = searchState,
        defaultLanguage = defaultLanguage,
        onBack = onBack,
        onLanguageChange = { language = it },
        onSearch = viewModel::runSearch,
        onAddSelected = { results, lang ->
            viewModel.addPictos(categoryId, results, lang) {
                viewModel.clearSearch()
                onBack()
            }
        },
        onAddOne = { result, spoken, label, lang, options, colorOverride ->
            viewModel.addPicto(categoryId, result, spoken, label, lang, options, colorOverride) {}
        },
        onImportImage = { importLauncher.launch("image/*") },
        onPickFromCategories = { modal = AddPictosModal.Borrow },
    )

    AddPictosModals(
        modal = modal,
        viewModel = viewModel,
        categoryId = categoryId,
        language = language,
        onModalChange = { modal = it },
    )
}

/** Renders whichever modal [modal] names, or nothing when it is null. */
@Composable
private fun AddPictosModals(
    modal: AddPictosModal?,
    viewModel: ConfigViewModel,
    categoryId: String,
    language: String,
    onModalChange: (AddPictosModal?) -> Unit,
) {
    when (modal) {
        null -> Unit

        // An imported image is cropped first, then described.
        is AddPictosModal.Crop -> CropImageDialog(
            imageUri = modal.uri,
            viewModel = viewModel,
            onDismiss = { onModalChange(null) },
            onCropped = { bmp -> onModalChange(AddPictosModal.Describe(bmp)) },
        )

        is AddPictosModal.Describe -> CustomImageDialog(
            bitmap = modal.bitmap,
            initialLanguage = language,
            onDismiss = { onModalChange(null) },
            onConfirm = { spoken, label, lang, colorOverride ->
                viewModel.addCroppedImagePicto(categoryId, modal.bitmap, spoken, label, lang, colorOverride) {}
                onModalChange(null)
            },
        )

        AddPictosModal.Borrow -> ImportFromCategoriesDialog(
            viewModel = viewModel,
            currentCategoryId = categoryId,
            onDismiss = { onModalChange(null) },
            onAdd = { pairs ->
                viewModel.copyPictosInto(categoryId, pairs) {}
                onModalChange(null)
            },
        )
    }
}

/** Stateless search-and-select screen for adding ARASAAC pictograms. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPictosScreenContent(
    searchState: SearchState,
    defaultLanguage: String,
    onBack: () -> Unit,
    onLanguageChange: (String) -> Unit,
    onSearch: (String, String) -> Unit,
    onAddSelected: (List<ArasaacResult>, String) -> Unit,
    onAddOne: (ArasaacResult, String, String, String, ArasaacOptions, Int?) -> Unit,
    onImportImage: () -> Unit,
    onPickFromCategories: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var language by remember { mutableStateOf(defaultLanguage) }
    val selected = remember { mutableStateListOf<ArasaacResult>() }
    var detail by remember { mutableStateOf<ArasaacResult?>(null) }

    Scaffold(
        topBar = {
            AddPictosTopBar(
                onBack = onBack,
                onPickFromCategories = onPickFromCategories,
                onImportImage = onImportImage,
            )
        },
        bottomBar = {
            if (selected.isNotEmpty()) {
                SelectionBar(
                    count = selected.size,
                    onAdd = { onAddSelected(selected.toList(), language) },
                )
            }
        },
    ) { padding ->
        AddPictosBody(
            searchState = searchState,
            query = query,
            language = language,
            selectedIds = selected.map { it.id }.toSet(),
            modifier = Modifier.padding(padding),
            onQueryChange = { query = it },
            onSearch = { if (query.isNotBlank()) onSearch(query, language) },
            onLanguage = {
                language = it
                onLanguageChange(it)
            },
            onToggle = { r -> if (!selected.removeAll { it.id == r.id }) selected.add(r) },
            onLongPick = { detail = it },
        )
    }

    detail?.let { result ->
        PictoDetailDialog(
            arasaacId = result.id,
            initialText = result.keyword,
            initialLanguage = language,
            onDismiss = { detail = null },
            onConfirm = { spoken, label, lang, options, colorOverride ->
                onAddOne(result, spoken, label, lang, options, colorOverride)
                // Adding it outright supersedes the tick in the results grid.
                selected.removeAll { it.id == result.id }
                detail = null
            },
        )
    }
}

@Preview(name = "Add pictos · empty", showBackground = true)
@Composable
private fun AddPictosEmptyPreview() {
    PictoKeyboardTheme {
        AddPictosScreenContent(
            searchState = SearchState.Idle,
            defaultLanguage = "es",
            onBack = {},
            onLanguageChange = {},
            onSearch = { _, _ -> },
            onAddSelected = { _, _ -> },
            onAddOne = { _, _, _, _, _, _ -> },
            onImportImage = {},
            onPickFromCategories = {},
        )
    }
}

@Preview(name = "Add pictos · no results", showBackground = true)
@Composable
private fun AddPictosEmptyResultsPreview() {
    PictoKeyboardTheme {
        AddPictosScreenContent(
            searchState = SearchState.Empty,
            defaultLanguage = "es",
            onBack = {},
            onLanguageChange = {},
            onSearch = { _, _ -> },
            onAddSelected = { _, _ -> },
            onAddOne = { _, _, _, _, _, _ -> },
            onImportImage = {},
            onPickFromCategories = {},
        )
    }
}
