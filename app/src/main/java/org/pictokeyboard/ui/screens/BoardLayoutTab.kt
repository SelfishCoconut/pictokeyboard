package org.pictokeyboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.pictokeyboard.R
import org.pictokeyboard.data.db.BoardEntity
import org.pictokeyboard.data.repo.BoardSummary
import org.pictokeyboard.ui.theme.PictoKeyboardTheme
import org.pictokeyboard.ui.theme.ScreenPreviews
import org.pictokeyboard.ui.theme.Spacing

/**
 * The Layout half of the board detail: everything about a board that describes
 * the *situation* rather than the person holding the phone.
 *
 * These lived in global Settings, where one grid had to serve every board — so a
 * doctor's board wanting 3 huge tiles and a chat board wanting 5 small ones
 * could not both be right. #31 moved the values onto the board; this is where
 * they are finally edited.
 *
 * The board is drawn above the controls at the size the keyboard will draw it,
 * so **columns**, **rows** and **captions** can be judged where they are set. A
 * setting whose effect is only visible in another app is a setting nobody tunes.
 */
@Composable
internal fun BoardLayoutTab(
    summary: BoardSummary,
    keyboardBoardCount: Int,
    onSaveBoard: (BoardEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val board = summary.board
    // Keyed on the stored value: a drag moves the draft without touching the
    // database, and the key changes back only once the write has landed. Writing
    // on every frame of a slider drag would be one Room transaction per pixel.
    var columns by remember(board.columns) { mutableIntStateOf(board.columns) }
    var rows by remember(board.rows) { mutableIntStateOf(board.rows) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        BoardLayoutPreview(
            categories = summary.categories,
            pictos = summary.heroPictos,
            columns = columns,
            rows = rows,
            showLabels = board.showLabels,
            chromeDp = keyboardChromeDp(hasBoardTabs = keyboardBoardCount >= 2),
        )

        SettingsGroup(stringResource(R.string.board_layout_group_grid)) {
            SliderRow(
                label = stringResource(R.string.board_layout_columns),
                value = columns.toFloat(),
                range = BoardEntity.COLUMN_RANGE.first.toFloat()..BoardEntity.COLUMN_RANGE.last.toFloat(),
                steps = stepsBetween(BoardEntity.COLUMN_RANGE),
                valueText = columns.toString(),
                onChange = { columns = it.toInt() },
                onChangeFinished = { onSaveBoard(board.copy(columns = columns)) },
            )
            SliderRow(
                label = stringResource(R.string.board_layout_rows),
                value = rows.toFloat(),
                range = BoardEntity.ROW_RANGE.first.toFloat()..BoardEntity.ROW_RANGE.last.toFloat(),
                steps = stepsBetween(BoardEntity.ROW_RANGE),
                valueText = rows.toString(),
                onChange = { rows = it.toInt() },
                onChangeFinished = { onSaveBoard(board.copy(rows = rows)) },
            )
            SwitchRow(
                label = stringResource(R.string.board_layout_captions),
                checked = board.showLabels,
                onChange = { onSaveBoard(board.copy(showLabels = it)) },
            )
        }

        FrameDefaultsGroup(summary = summary, onSaveBoard = onSaveBoard)
        BoardLanguageGroup(board = board, onSaveBoard = onSaveBoard)
        BoardVisibilityGroup(board = board, onSaveBoard = onSaveBoard)
    }
}

/**
 * The language of this board's *words* — which is not the language of the app.
 *
 * A caregiver may well run the app in Spanish while building an English board
 * for school, so these are two settings and not one (#31).
 */
@Composable
private fun BoardLanguageGroup(board: BoardEntity, onSaveBoard: (BoardEntity) -> Unit) {
    SettingsGroup(stringResource(R.string.board_layout_group_language)) {
        Hint(stringResource(R.string.board_layout_language_hint))
        LanguageChips(
            selected = board.language,
            onSelect = { onSaveBoard(board.copy(language = it)) },
        )
    }
}

/**
 * Whether this board reaches the keyboard at all.
 *
 * Without it, every half-built experiment appears in front of the person using
 * the keyboard the moment it is created. The board strip that reads this arrives
 * with #36; the switch is here because this is where a board is built, and the
 * moment it is needed is the moment before the board is finished.
 */
@Composable
private fun BoardVisibilityGroup(board: BoardEntity, onSaveBoard: (BoardEntity) -> Unit) {
    SettingsGroup(stringResource(R.string.board_layout_group_keyboard)) {
        SwitchRow(
            label = stringResource(R.string.board_layout_show_in_keyboard),
            checked = board.showInKeyboard,
            onChange = { onSaveBoard(board.copy(showInKeyboard = it)) },
        )
        Hint(stringResource(R.string.board_layout_show_in_keyboard_hint))
    }
}

/**
 * The frame a *new* category on this board starts with.
 *
 * A default rather than a restyling: categories already made keep the frame they
 * were given, because a board-wide restyle would silently overwrite deliberate
 * choices — and on an AAC board a frame is meaning, not decoration.
 */
@Composable
private fun FrameDefaultsGroup(summary: BoardSummary, onSaveBoard: (BoardEntity) -> Unit) {
    val board = summary.board
    SettingsGroup(stringResource(R.string.board_layout_group_frames)) {
        Hint(stringResource(R.string.board_layout_frames_hint))
        // Drawn in a real category's colour rather than the board's: the board's
        // colour is the one that identifies it on the keyboard's tab strip, and
        // no frame is ever actually drawn in it.
        val sample = Color(summary.categories.firstOrNull()?.colorArgb ?: board.colorArgb)
        BorderStylePicker(
            color = sample,
            selected = board.borderStyle,
            onSelect = { onSaveBoard(board.copy(borderStyle = it)) },
        )
        ThicknessPicker(
            color = sample,
            selected = board.borderWidthDp,
            onSelect = { onSaveBoard(board.copy(borderWidthDp = it)) },
        )
    }
}

/**
 * What the keyboard stacks above and below the board, in dp.
 *
 * Read from the same dimensions the keyboard's own layout is built from, so the
 * preview cannot promise the grid space the furniture has already taken. The
 * board strip is conditional for exactly the reason it is on the keyboard: a
 * caregiver with one board never sees it, so their preview must not budget for
 * it and show them a shorter grid than they will get.
 */
@Composable
private fun keyboardChromeDp(hasBoardTabs: Boolean): Int {
    val tabs = if (hasBoardTabs) dimensionResource(R.dimen.kb_tab_height) else 0.dp
    val bar = dimensionResource(R.dimen.kb_sentence_bar_height)
    val actions = dimensionResource(R.dimen.kb_action_row_height)
    val hairline = dimensionResource(R.dimen.kb_hairline)
    return (tabs + bar + actions + hairline).value.toInt()
}

/** The explanatory line under a control, in the settings screen's voice. */
@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Slider stops between the ends of [range], so the control offers exactly the
 * values the board can hold. Derived rather than written out, so widening
 * `COLUMN_RANGE` cannot leave a slider that skips its new end.
 */
private fun stepsBetween(range: IntRange): Int = (range.last - range.first - 1).coerceAtLeast(0)

@ScreenPreviews
@Composable
private fun BoardLayoutTabPreview() {
    PictoKeyboardTheme {
        BoardLayoutTab(summary = previewBoardSummary(), keyboardBoardCount = 1, onSaveBoard = {})
    }
}

/**
 * A wide, shallow board with captions off — the doctor's-appointment shape — on
 * a keyboard that also carries a board strip, so the preview is the shorter one.
 */
@ScreenPreviews
@Composable
private fun BoardLayoutTabWideTilesPreview() {
    PictoKeyboardTheme {
        BoardLayoutTab(
            summary = previewBoardSummary(columns = 2, rows = 3, showLabels = false),
            keyboardBoardCount = 2,
            onSaveBoard = {},
        )
    }
}
