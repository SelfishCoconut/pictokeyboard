package org.pictokeyboard.ui.account

import androidx.annotation.StringRes
import org.pictokeyboard.R
import org.pictokeyboard.data.auth.AuthFailure

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
    AuthFailure.Server -> R.string.account_error_server
    AuthFailure.Offline -> R.string.account_error_offline
}
