package org.pictokeyboard.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.pictokeyboard.R
import org.pictokeyboard.ui.theme.PictoKeyboardTheme
import org.pictokeyboard.ui.theme.ScreenPreviews

/**
 * The confirmation, which exists to correct the obvious wrong reading.
 *
 * There is no grace period and no countdown: "delete at any point" is only
 * honest if it means now. The dialog carries the whole explanation instead,
 * because the alternative to explaining is a caregiver who reads "delete
 * account" as "delete my child's vocabulary" and never touches it again.
 *
 * In its own file rather than beside the screen that opens it: this is the one
 * piece of the account UI whose exact wording is load-bearing, and it is easier
 * to notice a careless edit to it here than buried among five previews.
 */
@Composable
internal fun DeleteAccountDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.account_delete_title)) },
        text = { Text(stringResource(R.string.account_delete_body)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(stringResource(R.string.account_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/**
 * The deletion confirmation, previewed on its own.
 *
 * It carries the longest body text in the app and it is the one dialog where
 * clipping would be dangerous rather than untidy — the sentence that gets cut
 * at `fontScale = 2f` is the one saying the boards on this phone are safe.
 */
@ScreenPreviews
@Composable
private fun AccountDeleteDialogPreview() {
    PictoKeyboardTheme {
        DeleteAccountDialog(onDismiss = {}, onConfirm = {})
    }
}
