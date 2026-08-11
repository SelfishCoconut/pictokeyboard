package org.pictokeyboard.ui.screens

import org.pictokeyboard.data.db.BoardEntity
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.data.db.PictoEntity
import org.pictokeyboard.data.repo.BoardSummary
import org.pictokeyboard.data.seed.CategoryTemplates

// A board to draw previews of, shared by every screen that shows one.
//
// It lived in BoardDetailScreen.kt and was reached from BoardLayoutTab.kt as
// well, which made a file about one screen the owner of another screen's test
// data. Here it belongs to neither.

/** Enough rows for a preview to show the palette without scrolling. */
private const val PREVIEW_CATEGORIES = 5
private const val PREVIEW_PICTOS = 12

internal fun previewBoardSummary(
    columns: Int = BoardEntity.DEFAULT_COLUMNS,
    rows: Int = BoardEntity.DEFAULT_ROWS,
    showLabels: Boolean = true,
    iconArasaacId: Int? = null,
): BoardSummary {
    // Built from the real templates, so the preview shows the actual palette.
    val categories = CategoryTemplates.all.take(PREVIEW_CATEGORIES).mapIndexed { i, template ->
        CategoryEntity(
            id = template.id,
            boardId = "b1",
            name = template.name("es"),
            colorArgb = template.color.toInt(),
            iconArasaacId = template.iconArasaacId,
            position = i,
            builtin = true,
        )
    }
    return BoardSummary(
        board = BoardEntity(
            id = "b1",
            name = "Casa",
            colorArgb = BoardEntity.DEFAULT_COLOR_ARGB,
            position = 0,
            active = true,
            columns = columns,
            rows = rows,
            showLabels = showLabels,
            iconArasaacId = iconArasaacId,
        ),
        categories = categories,
        heroPictos = List(PREVIEW_PICTOS) { index ->
            PictoEntity(
                id = "p$index",
                categoryId = categories.first().id,
                label = "palabra $index",
                spokenText = "palabra $index",
                language = "es",
                position = index,
            )
        },
        pictoCount = 84,
    )
}
