package org.pictokeyboard.ui.account

import androidx.annotation.StringRes
import org.pictokeyboard.R
import org.pictokeyboard.data.auth.AuthFailure
import org.pictokeyboard.data.auth.toGoogleFailure

/**
 * One line of feedback for the caregiver, and whether it is reporting a failure.
 *
 * The flag is carried rather than inferred from [text]: the screen used to pick
 * its colour by comparing the id against the single error string, which quietly
 * stopped working the moment there was more than one.
 */
data class AccountNotice(@StringRes val text: Int, val isError: Boolean)

/**
 * The sentence for each failure.
 *
 * Every one names something the caregiver can act on, and only [AuthFailure.Offline]
 * mentions the connection — sending someone to their router because a password was
 * mistyped costs them the one thing this screen was supposed to save them.
 */
@StringRes
internal fun messageFor(failure: AuthFailure): Int = when (failure) {
    AuthFailure.Credentials -> R.string.account_error_credentials
    AuthFailure.EmailNotConfirmed -> R.string.account_error_email_not_confirmed
    AuthFailure.EmailTaken -> R.string.account_error_email_taken
    AuthFailure.WeakPassword -> R.string.account_error_weak_password
    AuthFailure.InvalidEmail -> R.string.account_error_invalid_email
    AuthFailure.TooManyAttempts -> R.string.account_error_too_many
    AuthFailure.SignupDisabled -> R.string.account_error_signup_disabled
    AuthFailure.NoGoogleAccount -> R.string.account_error_no_google_account
    AuthFailure.Server -> R.string.account_error_server
    AuthFailure.Offline -> R.string.account_error_offline
}

/**
 * The line to show after a Google sign-in failed, or **null** to stay silent.
 *
 * Silence is the correct response to a dismissal, so this returns null rather
 * than some neutral notice: there is nothing to say, and saying it anyway is
 * how a control that worked properly starts to look broken.
 */
internal fun noticeForGoogle(error: Throwable): AccountNotice? =
    error.toGoogleFailure()?.let { AccountNotice(messageFor(it), isError = true) }
