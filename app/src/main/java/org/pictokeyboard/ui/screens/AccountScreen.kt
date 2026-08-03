package org.pictokeyboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.pictokeyboard.R
import org.pictokeyboard.data.auth.AccountState
import org.pictokeyboard.ui.account.AccountForm
import org.pictokeyboard.ui.account.AccountNotice
import org.pictokeyboard.ui.account.AccountViewModel
import org.pictokeyboard.ui.theme.PictoKeyboardTheme
import org.pictokeyboard.ui.theme.ScreenPreviews
import org.pictokeyboard.ui.theme.Spacing

/**
 * Signing in, and signing out.
 *
 * An account is **optional and always will be**: it exists so a caregiver's
 * boards survive a lost phone, and nothing else in the app requires one. That
 * is why this screen never nags, never blocks, and says plainly that the
 * keyboard works the same either way.
 *
 * Stateful wrapper only, so [AccountScreenContent] stays previewable without a
 * view model — matching the split `SettingsScreen` already uses.
 */
@Composable
fun AccountScreen(onBack: () -> Unit, viewModel: AccountViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    AccountScreenContent(
        state = state,
        form = form,
        busy = busy,
        notice = message,
        onGoogle = rememberGoogleSignIn(onFailure = viewModel::reportGoogleFailure),
        onBack = onBack,
        onForm = viewModel::setForm,
        onSignIn = viewModel::signIn,
        onSignUp = viewModel::signUp,
        onRecover = viewModel::sendRecovery,
        onSignOut = viewModel::signOut,
    )
}

/** Stateless account screen. Everything arrives as a value or a callback. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountScreenContent(
    state: AccountState,
    form: AccountForm,
    busy: Boolean,
    notice: AccountNotice?,
    /** Null when this build has no Google OAuth client; the button is then absent. */
    onGoogle: (() -> Unit)?,
    onBack: () -> Unit,
    onForm: (AccountForm) -> Unit,
    onSignIn: () -> Unit,
    onSignUp: () -> Unit,
    onRecover: () -> Unit,
    onSignOut: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            when (state) {
                // Unreachable in practice: the Settings row that opens this
                // screen is absent on a build with no backend. Handled anyway,
                // because a deep link or a restored back stack can still land
                // here, and a blank screen would read as a crash.
                AccountState.Unavailable, AccountState.Loading -> Unit

                AccountState.SignedOut -> SignedOutBody(
                    form = form,
                    busy = busy,
                    notice = notice,
                    onGoogle = onGoogle,
                    onForm = onForm,
                    onSignIn = onSignIn,
                    onSignUp = onSignUp,
                    onRecover = onRecover,
                )

                is AccountState.SignedIn -> SignedInBody(
                    email = state.email,
                    busy = busy,
                    notice = notice,
                    onSignOut = onSignOut,
                )
            }
        }
    }
}

@Composable
private fun SignedOutBody(
    form: AccountForm,
    busy: Boolean,
    notice: AccountNotice?,
    onGoogle: (() -> Unit)?,
    onForm: (AccountForm) -> Unit,
    onSignIn: () -> Unit,
    onSignUp: () -> Unit,
    onRecover: () -> Unit,
) {
    Text(
        stringResource(R.string.account_signed_out_title),
        style = MaterialTheme.typography.titleLarge,
    )
    Hint(stringResource(R.string.account_signed_out_body))
    // Said out loud rather than implied. #79 ships sign-in with no sync behind
    // it, and a caregiver who signs in believing their boards are now backed up
    // -- and then loses the phone -- was misled by this screen.
    Hint(stringResource(R.string.account_nothing_syncs_yet))
    AccountMessage(notice = notice, busy = busy)
    onGoogle?.let { google ->
        OutlinedButton(onClick = google, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.account_google))
        }
    }
    EmailPasswordForm(
        form = form,
        busy = busy,
        onForm = onForm,
        onSignIn = onSignIn,
        onSignUp = onSignUp,
        onRecover = onRecover,
    )
}

@Composable
private fun SignedInBody(
    email: String?,
    busy: Boolean,
    notice: AccountNotice?,
    onSignOut: () -> Unit,
) {
    Text(
        email?.let { stringResource(R.string.account_signed_in_as, it) }
            ?: stringResource(R.string.account_signed_in),
        style = MaterialTheme.typography.titleMedium,
    )
    AccountMessage(notice = notice, busy = busy)
    OutlinedButton(
        onClick = onSignOut,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.account_sign_out))
    }
    // Beside the button, not behind a confirmation. Signing out is not
    // destructive, and dressing it as though it were would teach caregivers to
    // fear a control that costs them nothing.
    Hint(stringResource(R.string.account_sign_out_note))
}

/**
 * The way into the account screen, and the only place the app mentions accounts.
 *
 * **Absent, not disabled**, on a build with no Supabase project and on the first
 * frame before the stored session is read back. A dead row does not read as
 * "this build has no backend" — it reads as a broken app, and drawing "signed
 * out" during [AccountState.Loading] and correcting it a moment later tells a
 * caregiver their account has gone.
 *
 * The row says what tapping it *does* rather than repeating the group's title:
 * with both reading "Account", TalkBack announced the word twice and the second
 * one carried no information.
 */
@Composable
internal fun AccountSettingsRow(state: AccountState, onOpen: () -> Unit) {
    // Label first, hint second -- and the hint is dropped once signed in, where
    // the address itself is the whole story.
    val (label, hint) = when (state) {
        AccountState.Unavailable, AccountState.Loading -> return
        AccountState.SignedOut ->
            stringResource(R.string.account_sign_in) to stringResource(R.string.account_signed_out_title)
        is AccountState.SignedIn ->
            (state.email ?: stringResource(R.string.account_signed_in)) to null
    }
    SettingsGroup(stringResource(R.string.account_title)) {
        NavigationRow(label, onOpen)
        hint?.let { Hint(it) }
    }
}

// --- Previews ---------------------------------------------------------------

@Composable
private fun AccountPreview(
    state: AccountState,
    form: AccountForm = AccountForm(),
    busy: Boolean = false,
    notice: AccountNotice? = null,
    onGoogle: (() -> Unit)? = {},
) {
    PictoKeyboardTheme {
        AccountScreenContent(
            state = state,
            form = form,
            busy = busy,
            notice = notice,
            onGoogle = onGoogle,
            onBack = {},
            onForm = {},
            onSignIn = {},
            onSignUp = {},
            onRecover = {},
            onSignOut = {},
        )
    }
}

@ScreenPreviews
@Composable
private fun AccountSignedOutPreview() {
    AccountPreview(AccountState.SignedOut)
}

/**
 * The failure path, which is the one most likely to be unreadable in dark mode.
 *
 * A mistyped password rather than a dead network, because that is the failure a
 * caregiver meets most often and the longest of the nine sentences.
 */
@ScreenPreviews
@Composable
private fun AccountErrorPreview() {
    AccountPreview(
        state = AccountState.SignedOut,
        form = AccountForm(email = "caregiver@example.com", password = "hunter22"),
        notice = AccountNotice(R.string.account_error_credentials, isError = true),
    )
}

@ScreenPreviews
@Composable
private fun AccountSignedInPreview() {
    AccountPreview(AccountState.SignedIn("caregiver@example.com"))
}
