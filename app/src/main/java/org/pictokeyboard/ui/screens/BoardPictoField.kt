package org.pictokeyboard.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import org.pictokeyboard.R
import org.pictokeyboard.data.db.BoardEntity
import org.pictokeyboard.data.repo.IconChoice
import org.pictokeyboard.data.repo.currentIcon
import org.pictokeyboard.ui.theme.CategoryColors
import org.pictokeyboard.ui.theme.PictoTheme

/**
 * The board's own picto — what its keyboard tab is drawn with.
 *
 * The tab strip is navigation for the communicator, and until now every tab in
 * it was a word. On a keyboard whose user may not read, a strip of words is a
 * strip of nothing: the picture is what makes *the doctor board* findable at
 * the doctor's (#54).
 *
 * The same control the category editor has, from the same file — a board and a
 * category store the same two columns, so a second picker here would be a
 * second place for the offline caching to be forgotten.
 */
@Composable
internal fun BoardPictoField(
    board: BoardEntity,
    onSaveIcon: (BoardEntity, IconChoice) -> Unit,
    pickerDialog: IconPickerSlot,
) {
    var picking by remember { mutableStateOf(false) }

    SettingsGroup(stringResource(R.string.board_picto)) {
        IconField(
            icon = board.currentIcon(),
            // The board's own colour, so the frame here means what it will mean
            // on the keyboard tab -- but lifted through outlineOn, exactly as
            // BoardTabAdapter lifts it, and for the same reason: the default
            // board colour is a navy chosen against the light theme, and drawn
            // raw on the dark editor it is a frame nobody can see. This is the
            // defect a light-only screenshot passes every time.
            accent = Color(CategoryColors.outlineOn(board.colorArgb, PictoTheme.colors.paper.toArgb())),
            owner = IconOwner.Board(board.id),
            onChoose = { picking = true },
            onClear = { onSaveIcon(board, IconChoice.None) },
        )
        Hint(stringResource(R.string.board_picto_hint))
    }

    if (picking) {
        pickerDialog(
            IconOwner.Board(board.id),
            { picking = false },
            { chosen ->
                picking = false
                onSaveIcon(board, chosen)
            },
        )
    }
}
