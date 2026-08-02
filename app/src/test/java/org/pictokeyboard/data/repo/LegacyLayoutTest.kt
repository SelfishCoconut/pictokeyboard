package org.pictokeyboard.data.repo

import org.junit.Assert.assertEquals
import org.junit.Test
import org.pictokeyboard.data.db.BoardEntity
import org.pictokeyboard.data.prefs.LegacyBoardLayout

/**
 * Adopting the layout an install kept globally before boards existed.
 *
 * The Room migration seeds the first board at the schema defaults because it
 * cannot read DataStore, so this is the step that actually keeps the promise
 * that the keyboard behaves identically after upgrading. Getting it wrong is
 * invisible in review and obvious to the one person who cannot report it: the
 * communicator, whose grid changed shape overnight.
 */
class LegacyLayoutTest {

    private val board = BoardEntity(
        id = BoardEntity.DEFAULT_ID,
        name = "PictoKeyboard",
        colorArgb = BoardEntity.DEFAULT_COLOR_ARGB,
        position = 0,
        active = true,
    )

    @Test
    fun takesEveryValueTheUserHadSet() {
        val migrated = board.withLegacyLayout(
            LegacyBoardLayout(columns = 6, rows = 7, showLabels = false, language = "en"),
        )

        assertEquals(6, migrated.columns)
        assertEquals(7, migrated.rows)
        assertEquals(false, migrated.showLabels)
        assertEquals("en", migrated.language)
    }

    /**
     * The case that makes the nullability worth having. Someone who changed
     * only the column count must keep the defaults for everything else, not
     * receive whatever a non-null placeholder would have carried.
     */
    @Test
    fun leavesUntouchedValuesAtTheBoardsOwnDefaults() {
        val migrated = board.withLegacyLayout(
            LegacyBoardLayout(columns = 6, rows = null, showLabels = null, language = null),
        )

        assertEquals(6, migrated.columns)
        assertEquals(BoardEntity.DEFAULT_ROWS, migrated.rows)
        assertEquals(true, migrated.showLabels)
        assertEquals("es", migrated.language)
    }

    @Test
    fun changesNothingWhenNothingWasEverSet() {
        val migrated = board.withLegacyLayout(
            LegacyBoardLayout(columns = null, rows = null, showLabels = null, language = null),
        )

        assertEquals(board, migrated)
    }

    /**
     * A stored value outside what the grid can draw — from a build with
     * different slider bounds, or a hand-edited preferences file — is clamped
     * rather than trusted. A zero-column grid draws nothing at all.
     */
    @Test
    fun clampsValuesTheGridCannotDraw() {
        val migrated = board.withLegacyLayout(
            LegacyBoardLayout(columns = 99, rows = 0, showLabels = null, language = null),
        )

        assertEquals(BoardEntity.COLUMN_RANGE.last, migrated.columns)
        assertEquals(BoardEntity.ROW_RANGE.first, migrated.rows)
    }

    /** Everything outside the layout is left exactly as it was. */
    @Test
    fun doesNotDisturbIdentityOrProvenance() {
        val migrated = board.copy(name = "Doctor", tags = "place-hospital", author = "someone")
            .withLegacyLayout(LegacyBoardLayout(columns = 3, rows = 3, showLabels = false, language = "en"))

        assertEquals("Doctor", migrated.name)
        assertEquals(listOf("place-hospital"), migrated.tagIds)
        assertEquals("someone", migrated.author)
        assertEquals(BoardEntity.DEFAULT_ID, migrated.id)
        assertEquals(true, migrated.active)
    }
}
