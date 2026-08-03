package org.pictokeyboard.ui.account

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.pictokeyboard.App
import org.pictokeyboard.R
import org.pictokeyboard.data.auth.AccountState
import org.pictokeyboard.data.auth.toAuthFailure

/** What the signed-out form holds, and whether it is worth sending. */
data class AccountForm(val email: String = "", val password: String = "") {

    /**
     * The address as it should be sent: trimmed, because a soft keyboard's
     * autocomplete adds a trailing space far more often than a caregiver
     * notices.
     */
    val submittedEmail: String get() = email.trim()

    /**
     * Deliberately not a full RFC check — that rejects valid addresses and
     * teaches caregivers to distrust the field. This catches the two mistakes
     * that actually happen, and lets the server be the authority on the rest.
     *
     * The password is never trimmed: a leading space is a legitimate character,
     * and stripping it would lock someone out of their own account.
     */
    val canSubmit: Boolean
        get() = submittedEmail.contains('@') &&
            submittedEmail.substringAfterLast('@').contains('.') &&
            password.length >= MIN_PASSWORD

    companion object {
        /** Supabase's own default floor. Checking locally saves a round trip. */
        const val MIN_PASSWORD = 6
    }
}

/**
 * The account screen's state.
 *
 * Errors are exposed as string resource ids rather than text, so the data layer
 * never builds anything user-facing and every message stays translatable.
 */
class AccountViewModel : ViewModel() {

    private val repo = App.locator().authRepository

    val state: StateFlow<AccountState> = repo.state

    private val _form = MutableStateFlow(AccountForm())
    val form: StateFlow<AccountForm> = _form

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _message = MutableStateFlow<AccountNotice?>(null)
    val message: StateFlow<AccountNotice?> = _message

    fun setForm(value: AccountForm) {
        _form.value = value
        // A stale error under a field the caregiver is actively fixing reads as
        // "still wrong" and makes them undo a correct edit.
        _message.value = null
    }

    fun clearMessage() {
        _message.value = null
    }

    fun signIn() = run { repo.signIn(form.value.submittedEmail, form.value.password) }

    fun signUp() = run(R.string.account_check_your_email) {
        repo.signUp(form.value.submittedEmail, form.value.password)
    }

    fun sendRecovery() = run(R.string.account_recovery_sent) {
        repo.sendRecoveryEmail(form.value.submittedEmail)
    }

    fun signOut() = run { repo.signOut() }

    /**
     * Google sign-in, which needs a [Context] because Credential Manager has to
     * put a sheet on screen. It is passed in and never held.
     *
     * [noticeForGoogle] rather than the default mapping, because this is the one
     * path where a failure can correctly produce **no message at all**.
     */
    fun signInWithGoogle(context: Context) =
        run(failureNotice = ::noticeForGoogle) { repo.signInWithGoogle(context) }

    /**
     * One call in flight at a time, with the failure surfaced rather than
     * dropped.
     *
     * [successMessage] exists for the actions whose success is otherwise
     * invisible — sending a recovery email changes nothing on screen, and
     * silence there reads as a button that did not work.
     *
     * [failureNotice] is overridable because Google sign-in is the one action
     * whose failure can legitimately be worth saying nothing about.
     */
    private fun run(
        successMessage: Int? = null,
        // The exception decides the sentence. Reporting every failure as one
        // line was how a wrong password came back as "check your connection" --
        // advice for a problem the caregiver did not have.
        failureNotice: (Throwable) -> AccountNotice? = {
            AccountNotice(messageFor(it.toAuthFailure()), isError = true)
        },
        block: suspend () -> Result<Unit>,
    ) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val result = block()
            _busy.value = false
            _message.value = result.fold(
                onSuccess = { successMessage?.let { AccountNotice(it, isError = false) } },
                onFailure = failureNotice,
            )
        }
    }
}
