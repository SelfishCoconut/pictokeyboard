package org.pictokeyboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import org.pictokeyboard.R
import org.pictokeyboard.data.db.PictoEntity
import org.pictokeyboard.ui.ConfigViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PictosScreen(
    viewModel: ConfigViewModel,
    categoryId: String,
    onBack: () -> Unit,
    onAddPictos: () -> Unit,
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val category = categories.firstOrNull { it.id == categoryId }
    val pictos by remember(categoryId) { viewModel.pictos(categoryId) }.collectAsState(initial = emptyList())
    val categoryColor = category?.colorArgb ?: 0xFF9E9E9E.toInt()
    val borderStyle = category?.borderStyle ?: org.pictokeyboard.data.db.BorderStyles.SOLID
    val borderWidthDp = category?.borderWidthDp ?: org.pictokeyboard.data.db.BorderStyles.DEFAULT_WIDTH_DP

    var editing by remember { mutableStateOf<PictoEntity?>(null) }
    var reordering by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(category?.name ?: stringResource(R.string.pictos_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (pictos.size > 1) {
                        TextButton(onClick = { reordering = !reordering }) {
                            Text(stringResource(if (reordering) R.string.reorder_done else R.string.reorder))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!reordering) {
                FloatingActionButton(onClick = onAddPictos) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.picto_add))
                }
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 110.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(pictos, key = { _, p -> p.id }) { index, picto ->
                PictoTile(
                    picto = picto,
                    frameColor = Color(picto.colorArgbOverride ?: categoryColor),
                    borderStyle = borderStyle,
                    borderWidthDp = borderWidthDp,
                    showLabel = settings.showLabels,
                    reordering = reordering,
                    canMoveUp = index > 0,
                    canMoveDown = index < pictos.lastIndex,
                    onMoveUp = { viewModel.movePicto(pictos, picto, up = true) },
                    onMoveDown = { viewModel.movePicto(pictos, picto, up = false) },
                    onClick = { editing = picto },
                )
            }
        }
    }

    editing?.let { picto ->
        EditPictoDialog(
            picto = picto,
            onDismiss = { editing = null },
            onSave = { viewModel.updatePicto(it); editing = null },
            onDelete = { viewModel.deletePicto(picto); editing = null },
        )
    }
}

@Composable
private fun PictoTile(
    picto: PictoEntity,
    frameColor: Color,
    borderStyle: String,
    borderWidthDp: Int,
    showLabel: Boolean,
    reordering: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (reordering) Modifier else Modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(Color.White, RoundedCornerShape(12.dp))
                .categoryFrame(frameColor, borderWidthDp.dp, borderStyle, 12.dp)
                .padding(8.dp),
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
        }
        if (reordering) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.move_up))
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.move_down))
                }
            }
        } else if (showLabel && picto.label.isNotBlank()) {
            Text(
                picto.label,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun EditPictoDialog(
    picto: PictoEntity,
    onDismiss: () -> Unit,
    onSave: (PictoEntity) -> Unit,
    onDelete: () -> Unit,
) {
    var spoken by remember { mutableStateOf(picto.spokenText) }
    var label by remember { mutableStateOf(picto.label) }
    var language by remember { mutableStateOf(picto.language) }
    var frameColor by remember { mutableStateOf(picto.colorArgbOverride) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.picto_edit)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val model: Any = picto.imagePath?.let { File(it) }
                    ?: picto.arasaacId?.let { "https://static.arasaac.org/pictograms/$it/${it}_500.png" }
                    ?: R.drawable.ic_picto_placeholder
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 120.dp),
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
                TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (spoken.isNotBlank()) {
                        onSave(
                            picto.copy(
                                spokenText = spoken.trim(),
                                label = label.trim(),
                                language = language,
                                colorArgbOverride = frameColor,
                            ),
                        )
                    }
                },
                enabled = spoken.isNotBlank(),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
