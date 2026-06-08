package org.pictokeyboard.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import org.pictokeyboard.R
import org.pictokeyboard.data.arasaac.ArasaacOptions
import org.pictokeyboard.data.arasaac.ArasaacResult
import org.pictokeyboard.data.arasaac.ImageCache
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.data.db.PictoEntity
import org.pictokeyboard.ui.ConfigViewModel
import org.pictokeyboard.ui.SearchState
import java.io.File

/**
 * Full-screen, visual ARASAAC picker. Search, tap thumbnails to select several
 * at once, then Add. Long-press a result to fine-tune its text, set a frame
 * colour and customize the pictogram with a live preview. The toolbar also
 * imports a picture from the device (with a crop step) or borrows existing
 * pictos from other categories.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AddPictosScreen(
    viewModel: ConfigViewModel,
    categoryId: String,
    defaultLanguage: String,
    onBack: () -> Unit,
) {
    val searchState by viewModel.search.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var language by remember { mutableStateOf(defaultLanguage) }
    val selected = remember { mutableStateListOf<ArasaacResult>() }
    var detail by remember { mutableStateOf<ArasaacResult?>(null) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var cropped by remember { mutableStateOf<Bitmap?>(null) }
    var pickFromCategories by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> if (uri != null) importUri = uri }

    LaunchedEffect(Unit) { viewModel.clearSearch() }

    fun runSearch() {
        if (query.isNotBlank()) viewModel.runSearch(query, language)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_pictos_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { pickFromCategories = true }) {
                        Icon(Icons.Filled.LibraryAdd, contentDescription = stringResource(R.string.add_from_categories))
                    }
                    IconButton(onClick = { importLauncher.launch("image/*") }) {
                        Icon(Icons.Filled.Image, contentDescription = stringResource(R.string.import_image))
                    }
                },
            )
        },
        bottomBar = {
            if (selected.isNotEmpty()) {
                Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.add_selected_count, selected.size),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = {
                            viewModel.addPictos(categoryId, selected.toList(), language) {
                                viewModel.clearSearch()
                                onBack()
                            }
                        }) {
                            Text(stringResource(R.string.picto_add_selected, selected.size))
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.picto_search_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                trailingIcon = {
                    IconButton(onClick = { runSearch() }) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.picto_search))
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch() }),
            )
            LanguageChips(language) { language = it }

            Box(modifier = Modifier.fillMaxSize()) {
                when (val s = searchState) {
                    SearchState.Idle ->
                        Text(
                            stringResource(R.string.add_empty_prompt),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        )
                    SearchState.Loading ->
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    SearchState.Empty ->
                        Text(stringResource(R.string.picto_no_results), Modifier.align(Alignment.Center))
                    SearchState.Error ->
                        Text(stringResource(R.string.picto_search_error), Modifier.align(Alignment.Center))
                    is SearchState.Results -> ResultsGrid(
                        items = s.items,
                        selectedIds = selected.map { it.id }.toSet(),
                        onToggle = { r -> if (!selected.removeAll { it.id == r.id }) selected.add(r) },
                        onLongPick = { detail = it },
                    )
                }
            }
        }
    }

    detail?.let { result ->
        PictoDetailDialog(
            arasaacId = result.id,
            initialText = result.keyword,
            initialLanguage = language,
            onDismiss = { detail = null },
            onConfirm = { spoken, label, lang, options, colorOverride ->
                viewModel.addPicto(categoryId, result, spoken, label, lang, options, colorOverride) {}
                selected.removeAll { it.id == result.id }
                detail = null
            },
        )
    }

    // Imported image: crop first, then fill in the picto's details.
    importUri?.let { uri ->
        CropImageDialog(
            imageUri = uri,
            viewModel = viewModel,
            onDismiss = { importUri = null },
            onCropped = { bmp -> cropped = bmp; importUri = null },
        )
    }
    cropped?.let { bmp ->
        CustomImageDialog(
            bitmap = bmp,
            initialLanguage = language,
            onDismiss = { cropped = null },
            onConfirm = { spoken, label, lang, colorOverride ->
                viewModel.addCroppedImagePicto(categoryId, bmp, spoken, label, lang, colorOverride) {}
                cropped = null
            },
        )
    }

    if (pickFromCategories) {
        ImportFromCategoriesDialog(
            viewModel = viewModel,
            currentCategoryId = categoryId,
            onDismiss = { pickFromCategories = false },
            onAdd = { pairs ->
                viewModel.copyPictosInto(categoryId, pairs) {}
                pickFromCategories = false
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResultsGrid(
    items: List<ArasaacResult>,
    selectedIds: Set<Int>,
    onToggle: (ArasaacResult) -> Unit,
    onLongPick: (ArasaacResult) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.id }) { item ->
            val isSelected = item.id in selectedIds
            val ring = if (isSelected) MaterialTheme.colorScheme.primary else Color(0x22000000)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.combinedClickable(
                    onClick = { onToggle(item) },
                    onLongClick = { onLongPick(item) },
                ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .border(if (isSelected) 3.dp else 1.dp, ring, RoundedCornerShape(12.dp))
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .padding(6.dp),
                ) {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = item.keyword,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(24.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
                Text(item.keyword, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

@Composable
private fun PictoDetailDialog(
    arasaacId: Int,
    initialText: String,
    initialLanguage: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, ArasaacOptions, Int?) -> Unit,
) {
    var spoken by remember { mutableStateOf(initialText) }
    var label by remember { mutableStateOf(initialText) }
    var language by remember { mutableStateOf(initialLanguage) }
    var skin by remember { mutableStateOf<String?>(null) }
    var hair by remember { mutableStateOf<String?>(null) }
    var color by remember { mutableStateOf(true) }
    var frameColor by remember { mutableStateOf<Int?>(null) }

    val options = ArasaacOptions(skin = skin, hair = hair, color = color)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.picto_edit_details)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AsyncImage(
                    model = ImageCache.imageUrl(arasaacId, options),
                    contentDescription = null,
                    modifier = Modifier
                        .size(120.dp)
                        .align(Alignment.CenterHorizontally)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .padding(8.dp),
                )
                OutlinedTextField(
                    value = spoken,
                    onValueChange = { spoken = it },
                    label = { Text(stringResource(R.string.picto_spoken_text)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.picto_label)) },
                    singleLine = true,
                )
                LanguageChips(language) { language = it }

                Text(stringResource(R.string.picto_frame_color), style = MaterialTheme.typography.labelLarge)
                PictoColorPicker(selected = frameColor, onSelect = { frameColor = it })

                Text(stringResource(R.string.picto_customize), style = MaterialTheme.typography.labelLarge)
                SwatchRow(
                    label = stringResource(R.string.picto_skin),
                    values = ArasaacOptions.SKIN_TONES,
                    selected = skin,
                    colorFor = ::skinSwatch,
                    onSelect = { skin = it },
                )
                SwatchRow(
                    label = stringResource(R.string.picto_hair),
                    values = ArasaacOptions.HAIR_COLORS,
                    selected = hair,
                    colorFor = ::hairSwatch,
                    onSelect = { hair = it },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.picto_color), modifier = Modifier.weight(1f))
                    Switch(checked = color, onCheckedChange = { color = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (spoken.isNotBlank()) onConfirm(spoken.trim(), label.trim(), language, options, frameColor) },
                enabled = spoken.isNotBlank(),
            ) { Text(stringResource(R.string.add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun SwatchRow(
    label: String,
    values: List<String>,
    selected: String?,
    colorFor: (String) -> Color,
    onSelect: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Swatch(fill = MaterialTheme.colorScheme.surfaceVariant, isSelected = selected == null, isNone = true) {
                onSelect(null)
            }
            values.forEach { value ->
                Swatch(fill = colorFor(value), isSelected = selected == value, isNone = false) {
                    onSelect(value)
                }
            }
        }
    }
}

@Composable
private fun Swatch(fill: Color, isSelected: Boolean, isNone: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .background(fill, CircleShape)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0x44000000),
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    ) {
        if (isNone) {
            Text("—", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CustomImageDialog(
    bitmap: Bitmap,
    initialLanguage: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Int?) -> Unit,
) {
    var spoken by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var language by remember { mutableStateOf(initialLanguage) }
    var frameColor by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_image_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(120.dp)
                        .align(Alignment.CenterHorizontally)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .padding(8.dp),
                )
                OutlinedTextField(
                    value = spoken,
                    onValueChange = { spoken = it },
                    label = { Text(stringResource(R.string.picto_spoken_text)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.picto_label)) },
                    singleLine = true,
                )
                LanguageChips(language) { language = it }
                Text(stringResource(R.string.picto_frame_color), style = MaterialTheme.typography.labelLarge)
                PictoColorPicker(selected = frameColor, onSelect = { frameColor = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (spoken.isNotBlank()) onConfirm(spoken.trim(), label.trim(), language, frameColor) },
                enabled = spoken.isNotBlank(),
            ) { Text(stringResource(R.string.add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/**
 * Lets the admin borrow pictos from other categories into the current one. Each
 * chosen picto is copied keeping its original category's colour, so it stays
 * recognisable on the board.
 */
@Composable
private fun ImportFromCategoriesDialog(
    viewModel: ConfigViewModel,
    currentCategoryId: String,
    onDismiss: () -> Unit,
    onAdd: (List<Pair<PictoEntity, Int>>) -> Unit,
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val others = categories.filter { it.id != currentCategoryId }
    val groups by produceState(initialValue = emptyList<Pair<CategoryEntity, List<PictoEntity>>>(), others) {
        value = others.map { it to viewModel.pictosOnce(it.id) }.filter { it.second.isNotEmpty() }
    }
    val selectedIds = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_from_categories_title)) },
        text = {
            if (groups.isEmpty()) {
                Text(
                    stringResource(R.string.add_from_categories_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    groups.forEach { (category, pictos) ->
                        val accent = Color(category.colorArgb)
                        Text(
                            category.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = accent,
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            pictos.forEach { picto ->
                                val isSelected = picto.id in selectedIds
                                val pictoColor = Color(picto.colorArgbOverride ?: category.colorArgb)
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .background(Color.White, RoundedCornerShape(12.dp))
                                        .categoryFrame(pictoColor, if (isSelected) 4.dp else 2.dp, category.borderStyle, 12.dp)
                                        .clickable {
                                            if (!selectedIds.remove(picto.id)) selectedIds.add(picto.id)
                                        }
                                        .padding(6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val model: Any = picto.imagePath?.let { File(it) }
                                        ?: picto.arasaacId?.let { "https://static.arasaac.org/pictograms/$it/${it}_500.png" }
                                        ?: R.drawable.ic_picto_placeholder
                                    AsyncImage(
                                        model = model,
                                        contentDescription = picto.label,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(22.dp)
                                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(14.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val chosen = groups.flatMap { (cat, pics) -> pics.map { cat to it } }
                        .filter { it.second.id in selectedIds }
                        .map { (cat, p) -> p to (p.colorArgbOverride ?: cat.colorArgb) }
                    onAdd(chosen)
                },
                enabled = selectedIds.isNotEmpty(),
            ) { Text(stringResource(R.string.picto_add_selected, selectedIds.size)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

private fun skinSwatch(value: String): Color = when (value) {
    "white" -> Color(0xFFF1C9A5)
    "mulatto" -> Color(0xFFD49E7A)
    "aztec" -> Color(0xFFB87A4B)
    "black" -> Color(0xFF6B4423)
    "assian" -> Color(0xFFF0C27B)
    else -> Color(0xFFBDBDBD)
}

private fun hairSwatch(value: String): Color = when (value) {
    "blonde" -> Color(0xFFE6C76E)
    "brown" -> Color(0xFF8B5A2B)
    "darkBrown" -> Color(0xFF4B2E1E)
    "gray" -> Color(0xFFBDBDBD)
    "darkGray" -> Color(0xFF616161)
    "red" -> Color(0xFFB5482E)
    "black" -> Color(0xFF1A1A1A)
    else -> Color(0xFFBDBDBD)
}
