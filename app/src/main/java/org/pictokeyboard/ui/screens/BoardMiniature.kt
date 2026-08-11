package org.pictokeyboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.pictokeyboard.R
import org.pictokeyboard.data.arasaac.ArasaacUrls
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.data.db.PictoEntity
import org.pictokeyboard.ime.KeyboardMetrics
import org.pictokeyboard.ui.theme.CategoryColors
import org.pictokeyboard.ui.theme.PictoTheme
import org.pictokeyboard.ui.theme.Spacing
import java.io.File

/**
 * A live miniature of the caregiver's actual board, in two sizes.
 *
 * [BoardMiniature] is the strip on a board card: a fixed-height picture of the
 * board, enough to recognise it by. [BoardLayoutPreview] is the scale model on
 * the board's Layout tab, where the columns, rows and captions being edited have
 * to be visible while they are being dragged.
 *
 * Both are built from the real categories and pictos rather than from an
 * illustration, so a miniature doubles as setup confirmation: *this is what they
 * will see*.
 *
 * They share the token layer with the keyboard — the same wash, frames and chip
 * treatment — but deliberately not a rendering path. The keyboard is a View
 * hierarchy; making these reuse it would be over-engineering. What they do share
 * is [KeyboardMetrics], so the model and the thing modelled cannot disagree
 * about how tall a board of *n* rows is.
 */
@Composable
internal fun BoardMiniature(
    categories: List<CategoryEntity>,
    pictos: List<PictoEntity>,
    modifier: Modifier = Modifier,
    caption: @Composable (() -> Unit)? = null,
) {
    MiniatureFrame(modifier = modifier) {
        if (categories.isEmpty()) {
            EmptyBoardHint()
        } else {
            MiniBoard(
                categories = categories,
                pictos = pictos,
                spineWidth = SPINE_WIDTH,
                bodyHeight = BOARD_HEIGHT,
                columns = CARD_COLUMNS,
                rows = CARD_ROWS,
                showLabels = false,
            )
        }
        caption?.invoke()
    }
}

/**
 * The board at the size and shape the keyboard will actually draw it: [columns]
 * across, [rows] deep, captions on or off.
 *
 * Its height is not a design choice but the keyboard's own arithmetic, scaled
 * down by however much narrower this is than the screen — so a caregiver who
 * drags **rows** to 8 on a short phone sees the same clipped last row the
 * keyboard gives them, rather than a promise the keyboard will not keep.
 *
 * [chromeDp] is what the keyboard will stack above and below this board, which
 * is why it arrives as a number rather than being worked out here: whether the
 * board strip is among it depends on how many boards exist, which is the calling
 * screen's business and not the miniature's.
 */
@Composable
internal fun BoardLayoutPreview(
    categories: List<CategoryEntity>,
    pictos: List<PictoEntity>,
    columns: Int,
    rows: Int,
    showLabels: Boolean,
    chromeDp: Int,
    modifier: Modifier = Modifier,
) {
    // The window's size rather than the display's: in split screen the keyboard
    // is sized to the window it opens in, and so is its model.
    val window = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val captionDp = dimensionResource(R.dimen.kb_caption_height).value.toInt()
    MiniatureFrame(modifier = modifier) {
        if (categories.isEmpty()) {
            EmptyBoardHint()
        } else {
            BoxWithConstraints {
                val geometry = with(density) {
                    miniatureGeometry(
                        previewWidthDp = maxWidth.value.toInt(),
                        screen = KeyboardMetrics.Screen(
                            widthPx = window.width.toDp().value.toInt(),
                            heightPx = window.height.toDp().value.toInt(),
                        ),
                        chromeDp = chromeDp,
                        grid = KeyboardMetrics.Grid(
                            columns = columns,
                            rows = rows,
                            captionPx = if (showLabels) captionDp else 0,
                        ),
                    )
                }
                MiniBoard(
                    categories = categories,
                    pictos = pictos,
                    spineWidth = geometry.spineWidthDp.dp,
                    bodyHeight = geometry.bodyHeightDp.dp,
                    columns = columns,
                    rows = rows,
                    showLabels = showLabels,
                )
            }
        }
    }
}

/** The bordered card every miniature sits in. */
@Composable
private fun MiniatureFrame(modifier: Modifier, content: @Composable () -> Unit) {
    val colors = PictoTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .border(1.dp, colors.lineStrong, MaterialTheme.shapes.large)
            .background(colors.paper),
    ) {
        content()
    }
}

@Composable
private fun MiniBoard(
    categories: List<CategoryEntity>,
    pictos: List<PictoEntity>,
    spineWidth: Dp,
    bodyHeight: Dp,
    columns: Int,
    rows: Int,
    showLabels: Boolean,
) {
    val selected = categories.first()
    val density = LocalDensity.current
    Row(modifier = Modifier.height(bodyHeight)) {
        Column(
            modifier = Modifier
                .width(spineWidth)
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
                // The keyboard's grid scrolls; this one cannot, so it cuts off
                // instead. Without the clip the rows that do not fit are not
                // dropped but squeezed towards zero height, and their contents
                // draw over each other (#134) -- a preview showing a defect the
                // keyboard does not have, on the screen a caregiver goes to to
                // check the keyboard.
                .clipToBounds()
                // The signature wash, at the same 6% the keyboard uses.
                .background(Color(CategoryColors.wash(selected.colorArgb)))
                .padding(Spacing.xs),
        ) {
            // The miniature is a picture of a keyboard, not text to read: at a
            // 200% font scale a caption inside a 20dp tile would shoulder the
            // grid apart and stop the model being to scale, while the control it
            // illustrates is named in full by the switch beside it. So type
            // inside the model keeps its own scale.
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
                MiniGrid(
                    pictos = pictos,
                    categoryColor = selected.colorArgb,
                    columns = columns,
                    rows = rows,
                    showLabels = showLabels,
                )
            }
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
private fun MiniGrid(
    pictos: List<PictoEntity>,
    categoryColor: Int,
    columns: Int,
    rows: Int,
    showLabels: Boolean,
) {
    // Measured against the height it wants rather than the height it has, and
    // aligned to the top so the overflow falls off the bottom edge for the clip
    // above to remove. A plain Column would instead hand the last rows whatever
    // few pixels remained and let them collapse into one another.
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier.wrapContentHeight(Alignment.Top, unbounded = true),
    ) {
        pictos.take(columns * rows).chunked(columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                row.forEach { picto ->
                    MiniTile(
                        picto = picto,
                        frame = Color(picto.colorArgbOverride ?: categoryColor),
                        showLabel = showLabels,
                        modifier = Modifier.weight(1f),
                    )
                }
                // Keep the last row's tiles the same width as the rest rather than
                // letting three pictos stretch to fill four columns.
                repeat(columns - row.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MiniTile(
    picto: PictoEntity,
    frame: Color,
    showLabel: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = PictoTheme.colors
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // 4:3, the shape the keyboard's own tiles took on when the board
                // strip and the phrase keys started costing height (#36).
                .aspectRatio(1f / KeyboardMetrics.TILE_ASPECT)
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
        // Below the tile rather than inside it, exactly as on the keyboard —
        // which is why turning captions on costs height rather than picture.
        if (showLabel) {
            Text(
                picto.label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The category's own pictogram, on the white disc that keeps line art readable. */
@Composable
private fun CategoryGlyph(category: CategoryEntity, size: Dp) {
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

/**
 * The card strip is a picture to recognise a board by, not a scale model.
 *
 * Three rows rather than two since the tiles became 4:3 (#36): at the strip's
 * fixed height, two rows of the shorter tile left a band of empty wash under
 * them that read as a board with nothing in it.
 */
private const val CARD_COLUMNS = 4
private const val CARD_ROWS = 3
