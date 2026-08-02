package org.pictokeyboard.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import org.pictokeyboard.R
import org.pictokeyboard.data.db.BorderStyles
import org.pictokeyboard.data.db.PictoEntity
import org.pictokeyboard.data.repo.IconChoice
import org.pictokeyboard.data.repo.asIconChoice
import org.pictokeyboard.data.repo.previewModel
import org.pictokeyboard.ui.ConfigViewModel
import org.pictokeyboard.ui.SearchState

/**
 * The category's picto, shown first in the editor because it is what the
 * communicator actually navigates by — on a keyboard for someone who may not
 * read, the category strip is the primary control and a label-only entry in it
 * carries no meaning.
 *
 * Tapping the tile opens [IconPickerDialog]. Removing is deliberately
 * kept as a separate, labelled action rather than an option buried in the
 * picker, since it is the one choice that undoes a picture.
 */
@Composable
fun IconField(
    icon: IconChoice,
    accent: Color,
    onChoose: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconPreviewTile(icon = icon, accent = accent, onClick = onChoose)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = onChoose) {
                Text(stringResource(R.string.category_picto_choose))
            }
            // Kept mounted and disabled rather than removed when there is nothing
            // to clear: unmounting it drops accessibility focus mid-gesture, which
            // silently throws a screen-reader user back to the top of the editor.
            TextButton(onClick = onClear, enabled = icon != IconChoice.None) {
                Text(stringResource(R.string.category_picto_remove))
            }
        }
    }
}

/**
 * The chosen picto as the keyboard will draw it. The frame is always solid here:
 * this tile is about the picture, and the frame style has its own preview
 * further down the editor.
 */
@Composable
private fun IconPreviewTile(icon: IconChoice, accent: Color, onClick: () -> Unit) {
    val model = icon.previewModel()
    // Pictos are drawn for a white background, so the plate stays white under one.
    // The empty state is text, not a picto, and needs the themed pair instead --
    // on white it would be near-invisible in dark mode.
    val plate = if (model == null) MaterialTheme.colorScheme.surfaceVariant else Color.White
    val spoken = icon.label
        ?.let { stringResource(R.string.category_picto_chosen_named, it) }
        ?: stringResource(R.string.category_picto_chosen)
    Box(
        contentAlignment = Alignment.Center,
        // A floor rather than a fixed size, so "No picto" can grow at a large font
        // scale instead of clipping to a sliver. The picto itself is bounded
        // below -- without that the tile stretches to fill the row.
        modifier = Modifier
            .sizeIn(minWidth = 72.dp, minHeight = 72.dp)
            .background(plate, RoundedCornerShape(12.dp))
            .categoryFrame(accent, BorderStyles.DEFAULT_WIDTH_DP.dp, BorderStyles.SOLID, 12.dp)
            .clickable(onClick = onClick, onClickLabel = stringResource(R.string.category_picto_choose))
            .padding(8.dp)
            // Polite, so closing the picker speaks the picto just chosen. Without
            // it the caregiver who cannot see the tile gets no confirmation at all
            // and cannot tell a right pick from a mis-tap.
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        if (model == null) {
            Text(
                stringResource(R.string.category_picto_none),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            AsyncImage(
                model = model,
                contentDescription = spoken,
                // 72dp tile less its 8dp padding. Fixed, not fillMaxSize: the box
                // has no maximum any more, so the image would drive it as wide as
                // the row allows and shove the buttons off the dialog.
                modifier = Modifier.size(56.dp),
            )
        }
    }
}

/**
 * Where a category's picto can come from: one of the category's own symbols
 * first, then ARASAAC, a photo, or the camera.
 *
 * [categoryId] is null while a category is still being created, which is exactly
 * when it has no symbols of its own to offer — that section then disappears
 * rather than showing an empty row.
 */
@Composable
fun IconPickerDialog(
    viewModel: ConfigViewModel,
    categoryId: String?,
    language: String,
    onDismiss: () -> Unit,
    onPicked: (IconChoice) -> Unit,
) {
    val context = LocalContext.current
    var searching by remember { mutableStateOf(false) }
    var cropping by remember { mutableStateOf<Uri?>(null) }
    var saveFailed by remember { mutableStateOf(false) }

    val own by produceState(initialValue = emptyList<PictoEntity>(), categoryId) {
        value = categoryId?.let { viewModel.pictosOnce(it) }.orEmpty()
    }

    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> if (uri != null) cropping = uri }

    // The camera writes into a file we own, so no CAMERA permission is needed:
    // the picture is taken by whichever camera app the device already trusts.
    var pendingShot by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { saved -> if (saved) cropping = pendingShot }
    val hasCamera = remember(context) { context.hasCameraApp() }
    val takePicture = {
        val uri = context.newCameraUri()
        pendingShot = uri
        cameraLauncher.launch(uri)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.category_picto_title)) },
        text = {
            SourceList(
                own = own,
                saveFailed = saveFailed,
                sources = IconSources(
                    onPick = onPicked,
                    onSearch = { searching = true },
                    onPhoto = { photoLauncher.launch("image/*") },
                    onCamera = takePicture.takeIf { hasCamera },
                ),
            )
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )

    if (searching) {
        ArasaacIconSearchDialog(
            viewModel = viewModel,
            language = language,
            onDismiss = { searching = false },
            onPicked = { id, keyword ->
                searching = false
                onPicked(IconChoice.Arasaac(id, keyword))
            },
        )
    }

    cropping?.let { uri ->
        CropToIcon(uri, viewModel, onDismiss = { cropping = null }) { icon ->
            cropping = null
            if (icon == null) saveFailed = true else onPicked(icon)
        }
    }
}

/**
 * The crop step a photo or a camera shot goes through on its way to becoming a
 * category picto, reusing the same square cropper as the picto importer.
 * [onResult] gets null when the cropped image could not be written to the cache.
 */
@Composable
private fun CropToIcon(
    uri: Uri,
    viewModel: ConfigViewModel,
    onDismiss: () -> Unit,
    onResult: (IconChoice?) -> Unit,
) {
    CropImageDialog(
        imageUri = uri,
        viewModel = viewModel,
        onDismiss = onDismiss,
        onCropped = { bitmap ->
            viewModel.saveIconImage(bitmap) { path -> onResult(path?.let(IconChoice::Local)) }
        },
    )
}

/**
 * The four places a category picto can come from. [onCamera] is null when no app
 * on the device can take a picture, and that source is then simply not offered.
 */
private class IconSources(
    val onPick: (IconChoice) -> Unit,
    val onSearch: () -> Unit,
    val onPhoto: () -> Unit,
    val onCamera: (() -> Unit)?,
)

/**
 * The picker's body. The category's own symbols come first because that is
 * nearly always the right answer and the fastest one; the remaining sources are
 * plain labelled buttons so the whole list is usable without seeing the images.
 */
@Composable
private fun SourceList(
    own: List<PictoEntity>,
    saveFailed: Boolean,
    sources: IconSources,
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (saveFailed) {
            Text(
                stringResource(R.string.category_picto_save_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                // Assertive: a failed save looks exactly like a successful one to
                // someone who cannot see the tile, and they will re-shoot the photo
                // indefinitely unless the failure interrupts them.
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }
        // Gated on what can actually be offered, not on the raw list: a picto
        // still waiting on its first download has nothing to promote, and
        // filtering inside the row would leave this header standing over nothing.
        val offerable = own.mapNotNull { picto -> picto.asIconChoice()?.let { picto to it } }
        if (offerable.isNotEmpty()) {
            Text(
                stringResource(R.string.category_picto_from_own),
                style = MaterialTheme.typography.labelLarge,
            )
            OwnPictoRow(pictos = offerable, onPick = sources.onPick)
        }
        SourceButton(
            icon = Icons.Filled.Search,
            label = stringResource(R.string.category_picto_source_arasaac),
            onClick = sources.onSearch,
        )
        SourceButton(
            icon = Icons.Filled.Image,
            label = stringResource(R.string.category_picto_source_photo),
            onClick = sources.onPhoto,
        )
        sources.onCamera?.let { onCamera ->
            SourceButton(
                icon = Icons.Filled.PhotoCamera,
                label = stringResource(R.string.category_picto_source_camera),
                onClick = onCamera,
            )
        }
    }
}

/** The category's own symbols, offered first — usually the right picture already. */
@Composable
private fun OwnPictoRow(
    pictos: List<Pair<PictoEntity, IconChoice>>,
    onPick: (IconChoice) -> Unit,
) {
    val choose = stringResource(R.string.category_picto_choose)
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        pictos.forEach { (picto, choice) ->
            val name = picto.label.ifBlank { picto.spokenText }
            Box(
                contentAlignment = Alignment.Center,
                // The name goes on the tappable box itself rather than on the
                // image inside it, so the node a screen reader focuses is the same
                // node that carries the name -- not two nodes that have to be
                // merged for the announcement to make sense.
                modifier = Modifier
                    .size(72.dp)
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .clickable(onClickLabel = choose, role = Role.Button) { onPick(choice) }
                    .semantics(mergeDescendants = true) { contentDescription = name }
                    .padding(6.dp),
            ) {
                AsyncImage(
                    model = choice.previewModel(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** One labelled source. The label is always shown: an icon alone reads as nothing to TalkBack. */
@Composable
private fun SourceButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

/**
 * Single-select ARASAAC search. Deliberately not the multi-select grid used when
 * adding pictos: a category has exactly one picto, so a tap is the whole choice.
 */
@Composable
private fun ArasaacIconSearchDialog(
    viewModel: ConfigViewModel,
    language: String,
    onDismiss: () -> Unit,
    onPicked: (Int, String) -> Unit,
) {
    val state by viewModel.search.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var lang by remember { mutableStateOf(language) }

    // The search flow is shared with the add-pictos screen; clear it on the way
    // in and out so neither screen inherits the other's results.
    LaunchedEffect(Unit) { viewModel.clearSearch() }

    fun run() {
        if (query.isNotBlank()) viewModel.runSearch(query, lang)
    }

    AlertDialog(
        onDismissRequest = {
            viewModel.clearSearch()
            onDismiss()
        },
        title = { Text(stringResource(R.string.category_picto_source_arasaac)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.picto_search_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { run() }) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = stringResource(R.string.picto_search),
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { run() }),
                )
                LanguageChips(lang) { lang = it }
                SearchResults(state = state) { id, keyword ->
                    viewModel.clearSearch()
                    onPicked(id, keyword)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = {
                viewModel.clearSearch()
                onDismiss()
            }) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/** ARASAAC results, one tap being the whole choice. Bounded so the dialog still fits on a small screen. */
@Composable
private fun SearchResults(state: SearchState, onPick: (Int, String) -> Unit) {
    // Hoisted: stringResource cannot be called inside a semantics lambda.
    val searchingLabel = stringResource(R.string.category_picto_searching)
    Box(
        // A live region because every one of these states arrives with no other
        // signal: without it, searching offline is indistinguishable from silence
        // and the caregiver has no way to learn the search failed.
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp, max = 320.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        when (state) {
            SearchState.Idle -> Unit
            // A bare spinner announces nothing, so the state is said in words.
            SearchState.Loading -> CircularProgressIndicator(
                Modifier
                    .align(Alignment.Center)
                    .semantics { contentDescription = searchingLabel },
            )
            SearchState.Empty ->
                Text(stringResource(R.string.picto_no_results), Modifier.align(Alignment.Center))
            SearchState.Error ->
                Text(stringResource(R.string.picto_search_error), Modifier.align(Alignment.Center))
            is SearchState.Results -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 88.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.items, key = { it.id }) { item ->
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = item.keyword,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                            .clickable(
                                onClickLabel = stringResource(R.string.category_picto_choose),
                                role = Role.Button,
                            ) { onPick(item.id, item.keyword) }
                            .padding(6.dp),
                    )
                }
            }
        }
    }
}
