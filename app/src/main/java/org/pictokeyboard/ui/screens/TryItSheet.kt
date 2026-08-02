package org.pictokeyboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.flow.first
import org.pictokeyboard.R
import org.pictokeyboard.ui.theme.Spacing

/**
 * **Try it**: a real text field with the real keyboard under it.
 *
 * This is the feedback loop the app did not have. To see what they had built a
 * caregiver had to leave for WhatsApp, which means every edit-check cycle
 * crossed an app boundary — so in practice the check stopped happening and
 * boards shipped to the communicator untested.
 *
 * It is deliberately *not* a rendering of the keyboard drawn in Compose. The
 * question being asked is "does the thing I built work", and only the keyboard
 * itself can answer that: this field takes the same [android.view.inputmethod
 * .InputConnection] any other app's field would, so the pictos commit text, the
 * voice speaks and the frames are the real ones. A replica would answer a
 * different question, and would drift.
 *
 * Which board appears is whichever is in use, so every route in here makes the
 * board being tried the board in use first — see `Routes.BOARD` in MainActivity.
 *
 * When the keyboard is not yet enabled and selected, the field would summon
 * whatever other keyboard the phone has, which looks like PictoKeyboard being
 * broken. So the setup steps take the sheet instead, and the field waits.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TryItSheet(
    boardName: String,
    status: KeyboardStatus,
    onEnableKeyboard: () -> Unit,
    onSelectKeyboard: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // The sheet handles the IME inset itself, below, rather than being
        // padded away from it: the whole point is for the keyboard and the field
        // to share the screen.
        contentWindowInsets = { WindowInsets(0) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.lg)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                stringResource(R.string.try_it_title, boardName),
                style = MaterialTheme.typography.titleLarge,
            )
            if (status.ready) {
                TryItField(sheetState)
            } else {
                Text(
                    stringResource(R.string.try_it_not_ready),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SetupStepsCard(
                    status = status,
                    onEnable = onEnableKeyboard,
                    onSelect = onSelectKeyboard,
                )
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.try_it_done))
            }
        }
    }
}

/**
 * The field itself, focused once the sheet has finished arriving.
 *
 * The wait is not a guess at an animation length: a focus request made while the
 * sheet is still settling is dropped, and the caregiver gets a sheet with no
 * keyboard and no clue that tapping the field is what they were meant to do.
 * [sheetState] is what says the sheet has landed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TryItField(sheetState: SheetState) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(sheetState, focusRequester) {
        snapshotFlow { sheetState.currentValue }.first { it == SheetValue.Expanded }
        focusRequester.requestFocus()
    }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(stringResource(R.string.try_it_field)) },
        placeholder = { Text(stringResource(R.string.try_it_placeholder)) },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
    )
}
