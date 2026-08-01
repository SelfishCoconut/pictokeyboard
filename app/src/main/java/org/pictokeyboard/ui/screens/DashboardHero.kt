package org.pictokeyboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.pictokeyboard.R
import org.pictokeyboard.data.arasaac.ArasaacUrls
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.data.db.PictoEntity
import org.pictokeyboard.ui.theme.CategoryColors
import org.pictokeyboard.ui.theme.PictoTheme
import org.pictokeyboard.ui.theme.Spacing
import java.io.File

/**
 * The dashboard hero: a live miniature of the caregiver's actual board.
 *
 * It replaces a gradient panel with a stock illustration — the template answer,
 * and one that said nothing about this particular installation. The most
 * characteristic object in this product's world is the board, so the hero shows
 * the board, built from the real categories and pictos. That makes it setup
 * confirmation as well as decoration: *this is what they will see*. Tapping it
 * opens the editor, and the counts fold into its caption, which is what let the
 * two big-number stat cards below it be deleted outright.
 *
 * It shares the token layer with the keyboard — the same wash, frames and chip
 * treatment — but deliberately not a rendering path. The keyboard is a View
 * hierarchy; making a hero reuse it would be over-engineering.
 */
@Composable
internal fun BoardMiniature(
    categories: List<CategoryEntity>,
    pictos: List<PictoEntity>,
    pictoCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PictoTheme.colors
    val description = if (categories.isEmpty()) {
        stringResource(R.string.dashboard_board_empty)
    } else {
        stringResource(R.string.dashboard_board_a11y, categories.size, pictoCount)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            // One tap target with one label: the tiles inside are a picture of the
            // board, not a dozen separate things to swipe through in TalkBack.
            .semantics(mergeDescendants = true) { contentDescription = description }
            .clip(MaterialTheme.shapes.large)
            .border(1.dp, colors.lineStrong, MaterialTheme.shapes.large)
            .background(colors.paper)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        if (categories.isEmpty()) {
            EmptyBoardHint()
        } else {
            MiniBoard(categories = categories, pictos = pictos)
        }
        BoardCaption(categoryCount = categories.size, pictoCount = pictoCount)
    }
}

@Composable
private fun MiniBoard(categories: List<CategoryEntity>, pictos: List<PictoEntity>) {
    val selected = categories.first()
    Row(modifier = Modifier.height(BOARD_HEIGHT)) {
        Column(
            modifier = Modifier
                .width(SPINE_WIDTH)
                .fillMaxHeight()
                .padding(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            categories.take(SPINE_CHIPS).forEach { category ->
                MiniChip(category = category, selected = category.id == selected.id)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                // The signature wash, at the same 6% the keyboard uses.
                .background(Color(CategoryColors.wash(selected.colorArgb)))
                .padding(Spacing.xs),
        ) {
            MiniGrid(pictos = pictos, categoryColor = selected.colorArgb)
        }
    }
}

/** One category on the miniature spine, mirroring the keyboard's chip. */
@Composable
private fun MiniChip(category: CategoryEntity, selected: Boolean) {
    val hue = Color(category.colorArgb)
    val paper = PictoTheme.colors.paper.toArgb()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(CHIP_HEIGHT)
            .then(
                if (selected) {
                    // Square grid-facing corners, as on the keyboard: the selected
                    // chip reads as flowing into the board.
                    Modifier.background(
                        hue,
                        RoundedCornerShape(topStart = CHIP_CORNER, bottomStart = CHIP_CORNER),
                    )
                } else {
                    // Rounded rect, not a circle: the miniature is meant to be a
                    // faithful small copy of the spine, and the keyboard's chips
                    // are rectangles.
                    // outlineOn, not the raw hue: half the palette cannot reach
                    // 3:1 against paper on its own, same as the real spine.
                    Modifier
                        .background(
                            Color(CategoryColors.tintSoft(category.colorArgb)),
                            RoundedCornerShape(CHIP_CORNER),
                        )
                        .border(
                            1.dp,
                            Color(CategoryColors.outlineOn(category.colorArgb, paper)),
                            RoundedCornerShape(CHIP_CORNER),
                        )
                },
            ),
    ) {
        CategoryGlyph(category = category, size = CHIP_GLYPH)
    }
}

@Composable
private fun MiniGrid(pictos: List<PictoEntity>, categoryColor: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        pictos.take(GRID_COLUMNS * GRID_ROWS).chunked(GRID_COLUMNS).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                row.forEach { picto ->
                    MiniTile(
                        picto = picto,
                        frame = Color(picto.colorArgbOverride ?: categoryColor),
                        modifier = Modifier.weight(1f),
                    )
                }
                // Keep the last row's tiles the same width as the rest rather than
                // letting three pictos stretch to fill four columns.
                repeat(GRID_COLUMNS - row.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MiniTile(picto: PictoEntity, frame: Color, modifier: Modifier = Modifier) {
    val colors = PictoTheme.colors
    Box(
        modifier = modifier
            .aspectRatio(1f)
            // `tile`, so the artwork stays legible in dark mode too.
            .background(colors.tile, RoundedCornerShape(TILE_CORNER))
            .border(2.dp, frame, RoundedCornerShape(TILE_CORNER))
            .padding(2.dp),
    ) {
        val model = pictoModel(picto)
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** The category's own pictogram, on the white disc that keeps line art readable. */
@Composable
private fun CategoryGlyph(category: CategoryEntity, size: androidx.compose.ui.unit.Dp) {
    val colors = PictoTheme.colors
    val model: Any? = category.iconImagePath?.let { File(it) }
        ?: category.iconArasaacId?.let { ArasaacUrls.image(it) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .background(colors.tile, CircleShape)
            .padding(1.dp),
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun BoardCaption(categoryCount: Int, pictoCount: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PictoTheme.colors.card)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            stringResource(R.string.dashboard_board_title),
            style = MaterialTheme.typography.titleLarge,
            color = PictoTheme.colors.ink,
        )
        Text(
            stringResource(R.string.dashboard_board_counts, categoryCount, pictoCount),
            style = MaterialTheme.typography.bodyMedium,
            color = PictoTheme.colors.inkSoft,
        )
    }
}

@Composable
private fun EmptyBoardHint() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = BOARD_HEIGHT)
            .padding(Spacing.lg),
    ) {
        Text(
            stringResource(R.string.dashboard_board_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = PictoTheme.colors.inkSoft,
        )
    }
}

/** A local image if one was imported, otherwise the ARASAAC URL. */
private fun pictoModel(picto: PictoEntity): Any? {
    val path = picto.imagePath
    if (path != null && File(path).exists()) return File(path)
    return picto.arasaacId?.let { ArasaacUrls.image(it) }
}

private val BOARD_HEIGHT = 168.dp
private val SPINE_WIDTH = 56.dp
private val CHIP_HEIGHT = 44.dp
private val CHIP_CORNER = 12.dp
private val CHIP_GLYPH = 30.dp
private val TILE_CORNER = 8.dp
private const val SPINE_CHIPS = 3
private const val GRID_COLUMNS = 4
private const val GRID_ROWS = 2
