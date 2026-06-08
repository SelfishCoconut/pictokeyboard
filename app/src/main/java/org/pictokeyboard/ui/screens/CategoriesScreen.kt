package org.pictokeyboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import org.pictokeyboard.data.db.BorderStyles
import org.pictokeyboard.R
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.data.seed.CategoryTemplate
import org.pictokeyboard.data.seed.CategoryTemplates

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    viewModel: org.pictokeyboard.ui.ConfigViewModel,
    onBack: (() -> Unit)? = null,
    onOpenCategory: (String) -> Unit,
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var creatingBlank by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<CategoryEntity?>(null) }
    var reordering by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.categories_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    if (categories.size > 1) {
                        TextButton(onClick = { reordering = !reordering }) {
                            Text(stringResource(if (reordering) R.string.reorder_done else R.string.reorder))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!reordering) {
                FloatingActionButton(onClick = { creating = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.category_add))
                }
            }
        },
    ) { padding ->
        // Working copy so a drag can reorder locally; the DB is updated on drop.
        val items = remember { mutableStateListOf<CategoryEntity>() }
        var draggedId by remember { mutableStateOf<String?>(null) }
        var dragOffsetY by remember { mutableFloatStateOf(0f) }
        val listState = rememberLazyListState()

        // Keep the working list in sync with the DB whenever no drag is active.
        LaunchedEffect(categories, reordering) {
            if (draggedId == null) {
                items.clear()
                items.addAll(categories)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(items, key = { _, c -> c.id }) { index, category ->
                val dragging = category.id == draggedId
                val dragModifier = if (reordering) {
                    Modifier.pointerInput(items.size, category.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { draggedId = category.id; dragOffsetY = 0f },
                            onDragEnd = {
                                draggedId = null
                                dragOffsetY = 0f
                                viewModel.reorderCategories(items.toList())
                            },
                            onDragCancel = { draggedId = null; dragOffsetY = 0f },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffsetY += amount.y
                                val info = listState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { it.key == draggedId }
                                if (info != null) {
                                    val middle = info.offset + dragOffsetY + info.size / 2f
                                    val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { other ->
                                        other.key != draggedId &&
                                            middle.toInt() in other.offset..(other.offset + other.size)
                                    }
                                    if (target != null) {
                                        val from = items.indexOfFirst { it.id == draggedId }
                                        val to = items.indexOfFirst { it.id == target.key }
                                        if (from != -1 && to != -1 && from != to) {
                                            items.add(to, items.removeAt(from))
                                            // Keep the dragged card under the finger after the swap.
                                            dragOffsetY += if (to > from) -target.size.toFloat() else target.size.toFloat()
                                        }
                                    }
                                }
                            },
                        )
                    }
                } else {
                    Modifier
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(dragModifier)
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffsetY else 0f },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color(0xFFFFFFFF), CircleShape)
                                .border(category.borderWidthDp.dp, Color(category.colorArgb), CircleShape)
                                .padding(4.dp),
                        ) {
                            val iconModel: Any? = category.iconImagePath?.let { java.io.File(it) }
                                ?: category.iconArasaacId?.let {
                                    "https://static.arasaac.org/pictograms/$it/${it}_500.png"
                                }
                            if (iconModel != null) {
                                coil.compose.AsyncImage(
                                    model = iconModel,
                                    contentDescription = category.name,
                                    modifier = Modifier.size(34.dp),
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(category.name, style = MaterialTheme.typography.titleMedium)
                            if (category.builtin) {
                                Text(stringResource(R.string.category_builtin), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (reordering) {
                            Icon(
                                Icons.Filled.DragHandle,
                                contentDescription = stringResource(R.string.reorder_drag),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            IconButton(onClick = { editing = category }) {
                                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit))
                            }
                            IconButton(onClick = { deleting = category }) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                            }
                            TextButton(onClick = { onOpenCategory(category.id) }) {
                                Text(stringResource(R.string.pictos_title))
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        val suggestedName = stringResource(R.string.category_suggested)
        NewCategoryChooserDialog(
            language = settings.defaultLanguage,
            suggestedName = suggestedName,
            loadSuggested = { viewModel.topUsed() },
            onDismiss = { creating = false },
            onBlank = { creating = false; creatingBlank = true },
            onTemplate = { template ->
                viewModel.addCategoryFromTemplate(template, settings.defaultLanguage)
                creating = false
            },
            onSuggested = { records ->
                viewModel.addSuggestedCategory(suggestedName, records)
                creating = false
            },
        )
    }
    if (creatingBlank) {
        CategoryEditDialog(
            initial = null,
            onDismiss = { creatingBlank = false },
            onSave = { name, color, style, width ->
                viewModel.addCategory(name, color, style, width)
                creatingBlank = false
            },
        )
    }
    editing?.let { cat ->
        CategoryEditDialog(
            initial = cat,
            onDismiss = { editing = null },
            onSave = { name, color, style, width ->
                viewModel.updateCategory(
                    cat.copy(name = name, colorArgb = color, borderStyle = style, borderWidthDp = width),
                )
                editing = null
            },
        )
    }
    deleting?.let { cat ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(cat.name) },
            text = { Text(stringResource(R.string.category_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteCategory(cat); deleting = null }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

private fun arasaacThumb(id: Int): String =
    "https://static.arasaac.org/pictograms/$id/${id}_300.png"

@Composable
private fun NewCategoryChooserDialog(
    language: String,
    suggestedName: String,
    loadSuggested: suspend () -> List<org.pictokeyboard.data.db.UsageEntity>,
    onDismiss: () -> Unit,
    onBlank: () -> Unit,
    onTemplate: (CategoryTemplate) -> Unit,
    onSuggested: (List<org.pictokeyboard.data.db.UsageEntity>) -> Unit,
) {
    val suggested by produceState(initialValue = emptyList<org.pictokeyboard.data.db.UsageEntity>()) {
        value = loadSuggested()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.category_add)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.category_new_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (suggested.isNotEmpty()) {
                    ChooserCard(
                        accent = Color(0xFF00897B),
                        title = suggestedName,
                        subtitle = stringResource(R.string.category_suggested_desc),
                        thumbs = suggested.mapNotNull { it.arasaacId }.take(4).map(::arasaacThumb),
                        highlighted = true,
                        onClick = { onSuggested(suggested) },
                    )
                }

                ChooserCard(
                    accent = MaterialTheme.colorScheme.outline,
                    title = stringResource(R.string.category_blank),
                    subtitle = stringResource(R.string.category_blank_desc),
                    thumbs = emptyList(),
                    onClick = onBlank,
                )

                Text(
                    stringResource(R.string.category_from_template),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 4.dp),
                )
                CategoryTemplates.all.forEach { template ->
                    ChooserCard(
                        accent = Color(template.color),
                        title = template.name(language),
                        subtitle = stringResource(R.string.category_pictos_count, template.pictos.size),
                        thumbs = template.pictos.take(4).map { arasaacThumb(it.arasaacId) },
                        onClick = { onTemplate(template) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ChooserCard(
    accent: Color,
    title: String,
    subtitle: String,
    thumbs: List<String>,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(accent, CircleShape),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (thumbs.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    thumbs.forEach { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.White, RoundedCornerShape(8.dp))
                                .border(2.dp, accent, RoundedCornerShape(8.dp))
                                .padding(3.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryEditDialog(
    initial: CategoryEntity?,
    onDismiss: () -> Unit,
    onSave: (String, Int, String, Int) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var color by remember { mutableStateOf(initial?.colorArgb ?: CategoryPalette.first().toInt()) }
    var borderStyle by remember { mutableStateOf(initial?.borderStyle ?: BorderStyles.SOLID) }
    var borderWidth by remember { mutableStateOf(initial?.borderWidthDp ?: BorderStyles.DEFAULT_WIDTH_DP) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (initial == null) R.string.category_add else R.string.category_edit))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.category_name)) },
                    singleLine = true,
                )
                Text(stringResource(R.string.category_frame_color), style = MaterialTheme.typography.labelLarge)
                ColorPalettePicker(selected = color, onSelect = { color = it })

                Text(stringResource(R.string.category_frame_style), style = MaterialTheme.typography.labelLarge)
                BorderStylePicker(color = Color(color), selected = borderStyle, onSelect = { borderStyle = it })

                Text(stringResource(R.string.category_frame_thickness), style = MaterialTheme.typography.labelLarge)
                ThicknessPicker(color = Color(color), selected = borderWidth, onSelect = { borderWidth = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onSave(name.trim(), color, borderStyle, borderWidth) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
