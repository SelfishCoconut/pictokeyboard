package org.pictokeyboard.ui.screens

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import io.github.jan.supabase.compose.auth.composable.NativeSignInResult
import io.github.jan.supabase.compose.auth.composable.rememberSignInWithGoogle
import io.github.jan.supabase.compose.auth.composeAuth
import org.pictokeyboard.App
import org.pictokeyboard.R
import org.pictokeyboard.ui.account.AccountForm
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
internal fun AccountMessage(messageRes: Int?, busy: Boolean) {
    val text = when {
        busy -> stringResource(R.string.account_working)
        messageRes != null -> stringResource(messageRes)
        else -> null
    } ?: return

    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        // The generic failure is the only error this screen reports, so colour
        // is decided by which message it is rather than by a separate flag.
        color = if (messageRes == R.string.account_error_generic) {
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
 * Google sign-in through Credential Manager, or **null** when this build has no
 * OAuth client — in which case the button is absent rather than disabled.
 *
 * The result handling is the part worth reading. `ClosedByUser` is not a
 * failure: a caregiver who opened the sheet and changed their mind has done
 * nothing wrong, and telling them "that did not work" teaches them to distrust
 * a control that behaved correctly. Only a network or provider error reports.
 */
@Composable
internal fun rememberGoogleSignIn(onFailure: () -> Unit): (() -> Unit)? {
    val repo = App.locator().authRepository
    // No client and no OAuth id mean the same thing to the caller: there is no
    // Google button to draw.
    val supabase = repo.client?.takeIf { repo.googleServerClientId != null } ?: return null

    val state = supabase.composeAuth.rememberSignInWithGoogle(
        onResult = { result ->
            when (result) {
                is NativeSignInResult.Success, is NativeSignInResult.ClosedByUser -> Unit
                is NativeSignInResult.NetworkError, is NativeSignInResult.Error -> onFailure()
            }
        },
    )
    return { state.startFlow() }
}
