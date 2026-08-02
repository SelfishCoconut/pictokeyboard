package org.pictokeyboard.ui.screens

import org.pictokeyboard.R

/**
 * What a picto is being chosen *for*.
 *
 * A category and a board store the same two columns and are drawn by the same
 * code, so they share one picker (#54). What differs is only which symbols can
 * be promoted and what the dialog calls itself — which is exactly what this
 * type carries.
 */
sealed interface IconOwner {

    /** Wording for the dialog title, the "own symbols" header and the tile's announcement. */
    val titleRes: Int
    val fromOwnRes: Int
    val chosenRes: Int
    val chosenNamedRes: Int

    /**
     * A category. [id] is null while one is still being created, which is
     * exactly when it has no symbols of its own to offer.
     */
    data class Category(val id: String?) : IconOwner {
        override val titleRes = R.string.category_picto_title
        override val fromOwnRes = R.string.category_picto_from_own
        override val chosenRes = R.string.category_picto_chosen
        override val chosenNamedRes = R.string.category_picto_chosen_named
    }

    /** A board. Always saved by the time its picto can be chosen, so [id] is not null. */
    data class Board(val id: String) : IconOwner {
        override val titleRes = R.string.board_picto_title
        override val fromOwnRes = R.string.board_picto_from_own
        override val chosenRes = R.string.board_picto_chosen
        override val chosenNamedRes = R.string.board_picto_chosen_named
    }
}
