package org.pictokeyboard.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A board is one named, self-contained set of categories and pictos — *Home*,
 * *School*, *Doctor's appointment*.
 *
 * The rule it encodes: **settings describe the person; boards describe the
 * situation.** Voice, rate, PIN and interface language belong to whoever is
 * holding the phone and stay on `Settings`. Columns, rows, captions, frame
 * defaults and the board's language belong to the situation — a doctor board
 * wants 3x3 huge tiles and a chat board 5x5 — so they live here, where a
 * downloaded pack can arrive with the layout its author intended instead of
 * being flattened by one global compromise.
 */
@Entity(
    tableName = "boards",
    indices = [Index("position")],
)
data class BoardEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** ARGB colour identifying the board, used for its keyboard tab border. */
    val colorArgb: Int,
    /** Sort order in the boards list and in the keyboard tab strip. */
    val position: Int,
    /**
     * The board the keyboard is currently showing.
     *
     * Exactly one row is true. Enforced by [BoardDao.setActive] rather than by
     * the schema, because SQLite has no partial-unique constraint that Room can
     * express portably here.
     */
    val active: Boolean = false,
    /** Optional ARASAAC id of the board's own picto. */
    val iconArasaacId: Int? = null,
    /** Local cached file path of the board's picto, if any. */
    val iconImagePath: String? = null,
    /**
     * Controlled-vocabulary tag ids, comma-separated — never free text, which
     * fragments on contact with users and stops filtering anything within a
     * week. The vocabulary itself arrives with Discover in #37; this field is
     * the storage for it.
     *
     * Delimited rather than a join table because tags are a fixed, short list
     * that is only ever read whole.
     */
    @ColumnInfo(defaultValue = "")
    val tags: String = "",
    /** Whether this board appears in the keyboard's tab strip. */
    @ColumnInfo(defaultValue = "1")
    val showInKeyboard: Boolean = true,

    // --- Layout: describes the situation, not the person ---------------------

    @ColumnInfo(defaultValue = "4")
    val columns: Int = DEFAULT_COLUMNS,
    @ColumnInfo(defaultValue = "4")
    val rows: Int = DEFAULT_ROWS,
    @ColumnInfo(defaultValue = "1")
    val showLabels: Boolean = true,
    /** Default frame style for categories on this board; one of [BorderStyles]. */
    @ColumnInfo(defaultValue = "solid")
    val borderStyle: String = BorderStyles.SOLID,
    /** Default frame thickness in dp for categories on this board. */
    @ColumnInfo(defaultValue = "3")
    val borderWidthDp: Int = BorderStyles.DEFAULT_WIDTH_DP,
    /**
     * Language of this board's vocabulary — the language new pictos default to
     * and the voice they are spoken in.
     *
     * Distinct from `Settings.defaultLanguage`, which is the *interface*
     * language of the config app. A caregiver may well run the app in Spanish
     * while building an English board for school.
     */
    @ColumnInfo(defaultValue = "es")
    val language: String = "es",

    // --- Provenance ----------------------------------------------------------

    /** Where this board came from: null for hand-built, otherwise a catalogue id. */
    val source: String? = null,
    val sourceVersion: String? = null,
    val author: String? = null,
    /** SPDX-ish licence id, derived from contents rather than chosen — see #38. */
    val licence: String? = null,
) {
    /** The board's tags as ids, in declaration order, with blanks dropped. */
    val tagIds: List<String>
        get() = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    companion object {
        /**
         * Id of the single board every pre-multi-board install is migrated onto.
         *
         * A constant rather than a generated UUID so the migration that
         * backfills `categories.boardId` can name it in plain SQL, and so a
         * later migration can still find it.
         */
        const val DEFAULT_ID = "board-default"

        const val DEFAULT_COLUMNS = 4
        const val DEFAULT_ROWS = 4

        /**
         * Colour of the migrated board, matching the `accent` token so the
         * board a user already had reads as the app's own rather than picking a
         * category hue that means something else in AAC colour coding.
         */
        const val DEFAULT_COLOR_ARGB: Int = 0xFF24303F.toInt()

        /** Ranges the layout sliders and any imported pack are clamped to. */
        val COLUMN_RANGE = 2..6
        val ROW_RANGE = 2..8

        fun encodeTags(ids: List<String>): String = ids.filter { it.isNotBlank() }.joinToString(",")

        /**
         * Clamps a column count from outside — an imported pack, or a value
         * inherited from before boards existed — to what the grid can draw.
         */
        fun clampColumns(value: Int): Int = value.coerceIn(COLUMN_RANGE)

        fun clampRows(value: Int): Int = value.coerceIn(ROW_RANGE)
    }
}
