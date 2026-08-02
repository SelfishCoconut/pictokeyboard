package org.pictokeyboard.data.repo

import org.pictokeyboard.data.db.BoardEntity
import org.pictokeyboard.data.prefs.LegacyBoardLayout

/**
 * This board with the layout a pre-board install kept globally applied to it.
 *
 * Kept pure and out of the repository because all the behaviour worth testing
 * is in which fields it takes and which it leaves alone. A value the user never
 * set arrives null and must leave the board's own default standing rather than
 * overwrite it with a guess — that is the difference between an upgrade nobody
 * notices and one where a caregiver's carefully chosen 6-column board silently
 * becomes 4 the next morning.
 *
 * It lives in the repo package rather than beside [BoardEntity]: the db layer
 * should not know that DataStore, or a previous version of the app, exists.
 */
internal fun BoardEntity.withLegacyLayout(legacy: LegacyBoardLayout): BoardEntity = copy(
    columns = legacy.columns?.let(BoardEntity::clampColumns) ?: columns,
    rows = legacy.rows?.let(BoardEntity::clampRows) ?: rows,
    showLabels = legacy.showLabels ?: showLabels,
    language = legacy.language ?: language,
)
