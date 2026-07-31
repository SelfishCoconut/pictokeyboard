package org.pictokeyboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import org.pictokeyboard.R
import org.pictokeyboard.data.arasaac.ArasaacUrls
import org.pictokeyboard.data.db.CategoryEntity
import java.io.File

/**
 * The category list, draggable into a new order while [reordering].
 *
 * It keeps a working copy of [categories] so a drag can rearrange rows under
 * the finger without waiting on the database; [onReorder] persists the result
 * once the finger lifts. The copy re-syncs whenever no drag is in flight, so an
 * edit made elsewhere still lands.
 */
@Composable
internal fun ReorderableCategoryList(
    categories: List<CategoryEntity>,
    reordering: Boolean,
    modifier: Modifier = Modifier,
    onReorder: (List<CategoryEntity>) -> Unit,
    onEdit: (CategoryEntity) -> Unit,
    onDelete: (CategoryEntity) -> Unit,
    onOpen: (String) -> Unit,
) {
    val items = remember { mutableStateListOf<CategoryEntity>() }
    val drag = remember { DragState() }
    val listState = rememberLazyListState()

    LaunchedEffect(categories, reordering) {
        if (drag.draggedId == null) {
            items.clear()
            items.addAll(categories)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(items, key = { _, c -> c.id }) { _, category ->
            CategoryRow(
                category = category,
                reordering = reordering,
                dragging = category.id == drag.draggedId,
                dragOffsetY = drag.offsetY,
                modifier = if (reordering) {
                    Modifier.dragToReorder(items, category, listState, drag, onReorder)
                } else {
                    Modifier
                },
                onEdit = { onEdit(category) },
                onDelete = { onDelete(category) },
                onOpen = { onOpen(category.id) },
            )
        }
    }
}

/** Which row is being dragged, and how far it has travelled. */
private class DragState {
    var draggedId by mutableStateOf<String?>(null)
    var offsetY by mutableFloatStateOf(0f)

    fun release() {
        draggedId = null
        offsetY = 0f
    }
}

/** Long-press-then-drag on a row, rearranging [items] as the finger moves. */
private fun Modifier.dragToReorder(
    items: MutableList<CategoryEntity>,
    category: CategoryEntity,
    listState: LazyListState,
    drag: DragState,
    onReorder: (List<CategoryEntity>) -> Unit,
): Modifier = pointerInput(items.size, category.id) {
    detectDragGesturesAfterLongPress(
        onDragStart = {
            drag.draggedId = category.id
            drag.offsetY = 0f
        },
        onDragEnd = {
            val reordered = items.toList()
            drag.release()
            onReorder(reordered)
        },
        onDragCancel = { drag.release() },
        onDrag = { change, amount ->
            change.consume()
            drag.offsetY += amount.y
            drag.offsetY += swapUnderFinger(
                visible = listState.layoutInfo.visibleItemsInfo
                    .map { RowBounds(it.key, it.offset, it.size) },
                draggedId = drag.draggedId,
                dragOffsetY = drag.offsetY,
                items = items,
            )
        },
    )
}

/** Position and height of one visible row, as the drag maths needs it. */
internal data class RowBounds(val key: Any?, val offset: Int, val size: Int)

/**
 * Moves the dragged row to whichever row its middle currently overlaps, and
 * returns the offset correction that keeps the card under the finger after the
 * swap (0f when nothing moved).
 */
internal fun swapUnderFinger(
    visible: List<RowBounds>,
    draggedId: String?,
    dragOffsetY: Float,
    items: MutableList<CategoryEntity>,
): Float {
    val target = rowUnderFinger(visible, draggedId, dragOffsetY) ?: return 0f
    val from = items.indexOfFirst { it.id == draggedId }
    val to = items.indexOfFirst { it.id == target.key }
    if (from !in items.indices || to !in items.indices || from == to) return 0f
    items.add(to, items.removeAt(from))
    return if (to > from) -target.size.toFloat() else target.size.toFloat()
}

/** The row the dragged card's middle currently sits over, if any. */
private fun rowUnderFinger(visible: List<RowBounds>, draggedId: String?, dragOffsetY: Float): RowBounds? {
    val dragged = visible.firstOrNull { it.key == draggedId } ?: return null
    val middle = dragged.offset + dragOffsetY + dragged.size / 2f
    return visible.firstOrNull { other ->
        other.key != draggedId && middle.toInt() in other.offset..(other.offset + other.size)
    }
}

@Composable
private fun CategoryRow(
    category: CategoryEntity,
    reordering: Boolean,
    dragging: Boolean,
    dragOffsetY: Float,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier)
            // Lift the dragged card above its neighbours so it reads as picked up.
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
            CategoryIcon(category)
            Column(modifier = Modifier.weight(1f)) {
                Text(category.name, style = MaterialTheme.typography.titleMedium)
                if (category.builtin) {
                    Text(
                        stringResource(R.string.category_builtin),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            if (reordering) {
                Icon(
                    Icons.Filled.DragHandle,
                    contentDescription = stringResource(R.string.reorder_drag),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                }
                TextButton(onClick = onOpen) {
                    Text(stringResource(R.string.pictos_title))
                }
            }
        }
    }
}

/**
 * The category's pictogram in a ring of its own colour. The fill stays white
 * because ARASAAC artwork is black line work that a dark disc would swallow.
 */
@Composable
private fun CategoryIcon(category: CategoryEntity) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White, CircleShape)
            .border(category.borderWidthDp.dp, Color(category.colorArgb), CircleShape)
            .padding(4.dp),
    ) {
        val iconModel: Any? = category.iconImagePath?.let { File(it) }
            ?: category.iconArasaacId?.let { ArasaacUrls.image(it) }
        if (iconModel != null) {
            AsyncImage(
                model = iconModel,
                contentDescription = category.name,
                modifier = Modifier.size(34.dp),
            )
        }
    }
}
