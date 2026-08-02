package org.pictokeyboard.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.pictokeyboard.R
import org.pictokeyboard.data.arasaac.ArasaacResult
import org.pictokeyboard.ui.SearchState
import org.pictokeyboard.ui.theme.PictoTheme

// The pieces AddPictosScreen is assembled from: its bar, search box, and the
// results grid. Separated so the screen file reads as a layout.

/** Search box, language chips and results, stacked. */
@Composable
internal fun AddPictosBody(
    searchState: SearchState,
    query: String,
    language: String,
    selectedIds: Set<Int>,
    modifier: Modifier = Modifier,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onLanguage: (String) -> Unit,
    onToggle: (ArasaacResult) -> Unit,
    onLongPick: (ArasaacResult) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SearchField(query = query, onQueryChange = onQueryChange, onSearch = onSearch)
        LanguageChips(language, onLanguage)
        SearchResults(
            searchState = searchState,
            selectedIds = selectedIds,
            onToggle = onToggle,
            onLongPick = onLongPick,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddPictosTopBar(onBack: () -> Unit, onPickFromCategories: () -> Unit, onImportImage: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.add_pictos_title)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
        },
        actions = {
            IconButton(onClick = onPickFromCategories) {
                Icon(Icons.Filled.LibraryAdd, contentDescription = stringResource(R.string.add_from_categories))
            }
            IconButton(onClick = onImportImage) {
                Icon(Icons.Filled.Image, contentDescription = stringResource(R.string.import_image))
            }
        },
    )
}

/** Sticky bar showing how many results are ticked, with the commit button. */
@Composable
internal fun SelectionBar(count: Int, onAdd: () -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.add_selected_count, count),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onAdd) {
                Text(stringResource(R.string.picto_add_selected, count))
            }
        }
    }
}

@Composable
internal fun SearchField(query: String, onQueryChange: (String) -> Unit, onSearch: () -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        label = { Text(stringResource(R.string.picto_search_hint)) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        trailingIcon = {
            IconButton(onClick = onSearch) {
                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.picto_search))
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
    )
}

/** The prompt, spinner, error or grid, depending on where the search got to. */
@Composable
internal fun SearchResults(
    searchState: SearchState,
    selectedIds: Set<Int>,
    onToggle: (ArasaacResult) -> Unit,
    onLongPick: (ArasaacResult) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (searchState) {
            SearchState.Idle ->
                Text(
                    stringResource(R.string.add_empty_prompt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                )
            SearchState.Loading ->
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            SearchState.Empty ->
                Text(stringResource(R.string.picto_no_results), Modifier.align(Alignment.Center))
            SearchState.Error ->
                Text(stringResource(R.string.picto_search_error), Modifier.align(Alignment.Center))
            is SearchState.Results -> ResultsGrid(
                items = searchState.items,
                selectedIds = selectedIds,
                onToggle = onToggle,
                onLongPick = onLongPick,
            )
        }
    }
}

@Composable
internal fun ResultsGrid(
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
            ResultTile(
                item = item,
                isSelected = item.id in selectedIds,
                onToggle = { onToggle(item) },
                onLongPick = { onLongPick(item) },
            )
        }
    }
}

/**
 * One search result, tickable.
 *
 * Selection used to be carried by a border width and a check mark whose
 * `contentDescription` was null -- nothing a screen reader could observe. Tapping
 * through 40 results, a TalkBack user could not tell what was already chosen.
 *
 * `combinedClickable` rather than `toggleable`, because the long press is a real
 * second action (customise before adding) and not a shortcut. That is also why
 * both click labels are set: TalkBack owns touch, so an unlabelled long press is
 * one the user it matters to can never perform.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResultTile(
    item: ArasaacResult,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onLongPick: () -> Unit,
) {
    // outline, not a black wash: a picto tile is always white, so its edge has to
    // be a token that holds 3:1 in both schemes.
    val ring = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    // Hoisted: stringResource cannot be called inside a semantics lambda.
    val keyword = item.keyword
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .combinedClickable(
                onClickLabel = stringResource(R.string.a11y_toggle_selection),
                onClick = onToggle,
                onLongClickLabel = stringResource(R.string.picto_customize),
                onLongClick = onLongPick,
            )
            .semantics(mergeDescendants = true) {
                role = Role.Checkbox
                contentDescription = keyword
                toggleableState = if (isSelected) ToggleableState.On else ToggleableState.Off
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .border(if (isSelected) 3.dp else 1.dp, ring, RoundedCornerShape(12.dp))
                .background(PictoTheme.colors.tile, RoundedCornerShape(12.dp))
                .padding(6.dp),
        ) {
            AsyncImage(
                model = item.imageUrl,
                // Named by the merged parent above, so the image would only repeat it.
                contentDescription = null,
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
