package org.pictokeyboard.data.repo

import org.pictokeyboard.data.db.BoardEntity
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.data.db.PictoEntity

/**
 * One board as its card on the boards list needs it: the board itself, its
 * categories, enough pictos to draw the miniature, and the totals shown beneath.
 *
 * [heroPictos] is only the first category's pictos — the miniature shows the
 * board as the keyboard opens on it, which is the first category — so this stays
 * small however large the board is.
 */
data class BoardSummary(
    val board: BoardEntity,
    val categories: List<CategoryEntity>,
    val heroPictos: List<PictoEntity>,
    val pictoCount: Int,
) {
    val categoryCount: Int get() = categories.size
}
