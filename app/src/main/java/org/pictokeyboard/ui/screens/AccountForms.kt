package org.pictokeyboard.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import org.pictokeyboard.App
import org.pictokeyboard.R
import org.pictokeyboard.ui.account.AccountForm
import org.pictokeyboard.ui.account.AccountNotice
import org.pictokeyboard.ui.theme.Spacing

/**
 * Email and password, with the three actions that can follow.
 *
 * Submit is gated on [AccountForm.canSubmit] rather than on the server, so a
 * mistyped address fails instantly instead of after a round trip that is
 * indistinguishable from the app having hung.
 */
@Composable
internal fun EmailPasswordForm(
    form: AccountForm,
    busy: Boolean,
    onForm: (AccountForm) -> Unit,
    onSignIn: () -> Unit,
    onSignUp: () -> Unit,
    onRecover: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        OutlinedTextField(
            value = form.email,
            onValueChange = { onForm(form.copy(email = it)) },
            label = { Text(stringResource(R.string.account_email)) },
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.password,
            onValueChange = { onForm(form.copy(password = it)) },
            label = { Text(stringResource(R.string.account_password)) },
            singleLine = true,
            enabled = !busy,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = onSignIn,
            enabled = form.canSubmit && !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.account_sign_in))
        }
        OutlinedButton(
            onClick = onSignUp,
            enabled = form.canSubmit && !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.account_sign_up))
        }
        // Recovery needs only the address, so it stays available with the
        // password box empty -- which is exactly the state someone who has
        // forgotten it is in.
        TextButton(
            onClick = onRecover,
            enabled = form.submittedEmail.contains('@') && !busy,
        ) {
            Text(stringResource(R.string.account_forgot))
        }
    }
}

/**
 * The one line that reports what happened.
 *
 * An **assertive** live region, because a failed sign-in that only changes
 * pixels is invisible to a caregiver using TalkBack — and they will retype a
 * correct password indefinitely rather than learn the network was down.
 *
 * Carries successes too: sending a recovery email changes nothing else on
 * screen, and silence there reads as a button that did nothing.
 */
@Composable
internal fun AccountMessage(notice: AccountNotice?, busy: Boolean) {
    val text = when {
        busy -> stringResource(R.string.account_working)
        notice != null -> stringResource(notice.text)
        else -> null
    } ?: return

    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        // From the notice, not from which string it holds: there are nine error
        // messages now, and an id comparison would colour eight of them as if
        // nothing had gone wrong.
        color = if (notice?.isError == true) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Assertive },
    )
}

/**
 * Whether there is a Google button to draw, and what tapping it does — or
 * **null** when this build has no OAuth client, in which case the button is
 * absent rather than disabled.
 *
 * The sign-in itself belongs to the view model; all this contributes is the
 * [android.content.Context] that Credential Manager needs in order to put a
 * sheet on screen.
 */
@Composable
internal fun rememberGoogleSignIn(onSignIn: (Context) -> Unit): (() -> Unit)? {
    val repo = App.locator().authRepository
    // No client and no OAuth id mean the same thing to the caller: there is no
    // Google button to draw.
    repo.client?.takeIf { repo.googleServerClientId != null } ?: return null

    val context = LocalContext.current
    return { onSignIn(context) }
}
