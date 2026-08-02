package org.pictokeyboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import org.pictokeyboard.R
import org.pictokeyboard.data.arasaac.ArasaacUrls
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.ui.theme.PictoTheme
import org.pictokeyboard.ui.theme.Spacing
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
    onMove: (CategoryEntity, Boolean) -> Unit,
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
        itemsIndexed(items, key = { _, c -> c.id }) { index, category ->
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
                canMoveUp = index > 0,
                canMoveDown = index < items.lastIndex,
                onMoveUp = { onMove(category, true) },
                onMoveDown = { onMove(category, false) },
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

/**
 * One category.
 *
 * Opening the category is the common action by a wide margin, so the whole row
 * does it and the "Pictos" text button is gone. Edit and delete were two icon
 * buttons competing for the same width as the name — which is why "Sentimientos"
 * broke mid-word as "Sentiment / os" — and they move into an overflow, giving the
 * name the room it needs.
 *
 * The colour becomes a full-height bar down the leading edge rather than a ring
 * around the icon, so scanning the list reads as the AAC colour code itself.
 */
@Composable
private fun CategoryRow(
    category: CategoryEntity,
    reordering: Boolean,
    dragging: Boolean,
    dragOffsetY: Float,
    modifier: Modifier = Modifier,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
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
            // Min-intrinsic height is what lets the colour bar below fill the row:
            // it has no height of its own to contribute.
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .then(
                    if (reordering) {
                        Modifier
                    } else {
                        Modifier.clickable(role = Role.Button, onClick = onOpen)
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(COLOR_BAR_WIDTH)
                    .fillMaxHeight()
                    .background(Color(category.colorArgb)),
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.md, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                CategoryIcon(category)
                Column(modifier = Modifier.weight(1f)) {
                    Text(category.name, style = MaterialTheme.typography.titleMedium)
                    if (category.builtin) {
                        Text(
                            stringResource(R.string.category_builtin),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (reordering) {
                    ReorderControls(
                        canMoveUp = canMoveUp,
                        canMoveDown = canMoveDown,
                        onMoveUp = onMoveUp,
                        onMoveDown = onMoveDown,
                    )
                } else {
                    CategoryOverflow(onEdit = onEdit, onDelete = onDelete)
                }
            }
        }
    }
}

/**
 * Reorder without dragging.
 *
 * Drag stays as the fast path, but it cannot be the only one. TalkBack owns
 * touch, so a `detectDragGesturesAfterLongPress` gesture can never reach the
 * pointer-input node -- and the drag handle that read "Drag to reorder" was
 * naming a gesture the user it was speaking to could not perform. Switch Access
 * and D-pad had no route either. WCAG 2.1.1 Keyboard (A) and 2.5.7 Dragging
 * Movements (AA).
 *
 * Worse than unreachable: entering reorder mode *removed* edit, delete and open,
 * so a screen-reader user who reached this state could do nothing at all.
 *
 * The handle keeps its icon as a visual affordance but drops its description,
 * now that it is no longer the only route and would otherwise announce an
 * instruction that competes with the two working buttons beside it.
 * [PictoScreen][PictosScreen] already does exactly this; this is that pattern,
 * moved across.
 */
@Composable
private fun ReorderControls(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.move_up))
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.move_down))
        }
        Icon(
            Icons.Filled.DragHandle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Edit and delete, out of the row's way until they are wanted. */
@Composable
private fun CategoryOverflow(onEdit: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.category_more),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.edit)) },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = {
                    open = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete)) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    open = false
                    onDelete()
                },
            )
        }
    }
}

/**
 * The category's pictogram on a white disc. The fill is `tile` rather than the
 * scheme's surface because ARASAAC artwork is black line work that a dark disc
 * would swallow — the same reason picto tiles stay white in dark mode.
 *
 * The coloured ring is gone: the row's leading bar now carries the colour, and
 * two statements of the same hue on one row is one too many.
 */
@Composable
private fun CategoryIcon(category: CategoryEntity) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .background(PictoTheme.colors.tile, CircleShape)
            .border(1.dp, PictoTheme.colors.line, CircleShape)
            .padding(Spacing.xs),
    ) {
        val iconModel: Any? = category.iconImagePath?.let { File(it) }
            ?: category.iconArasaacId?.let { ArasaacUrls.image(it) }
        if (iconModel != null) {
            AsyncImage(
                model = iconModel,
                contentDescription = null,
                modifier = Modifier.size(34.dp),
            )
        }
    }
}

private val COLOR_BAR_WIDTH = 8.dp
