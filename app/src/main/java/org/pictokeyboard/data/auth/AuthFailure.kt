package org.pictokeyboard.data.auth

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import java.io.IOException

/**
 * Why an account action did not work, in the terms the caregiver needs.
 *
 * Deliberately coarser than Supabase's eighty-odd error codes: this is the set
 * of *different things to do next*, and two codes that call for the same next
 * step are the same failure. The UI turns these into sentences; nothing here is
 * user-facing text, so the data layer stays free of anything translatable.
 */
enum class AuthFailure {
    /** The address and password together were rejected. */
    Credentials,

    /** The account exists and the password was right, but the link is unclicked. */
    EmailNotConfirmed,

    /** Sign-up onto an address that already has an account. */
    EmailTaken,

    /** The server's password floor is above ours. */
    WeakPassword,

    /** The server would not accept the address at all. */
    InvalidEmail,

    /** Rate limited — the built-in mailer gives out after a few an hour. */
    TooManyAttempts,

    /** New accounts are switched off on the project. */
    SignupDisabled,

    /** There is no Google account on this phone to sign in with. */
    NoGoogleAccount,

    /** The server answered, and the answer was not one we have advice for. */
    Server,

    /** The request never got an answer. **The only failure that is the network's.** */
    Offline,
}

/**
 * The code-to-failure table.
 *
 * Anything not named here is [AuthFailure.Server] rather than a guess: a
 * wrong-but-specific sentence costs a caregiver more than an honest vague one.
 */
internal fun authFailureOf(code: AuthErrorCode?): AuthFailure = when (code) {
    AuthErrorCode.InvalidCredentials -> AuthFailure.Credentials
    AuthErrorCode.EmailNotConfirmed -> AuthFailure.EmailNotConfirmed
    AuthErrorCode.UserAlreadyExists, AuthErrorCode.EmailExists -> AuthFailure.EmailTaken
    AuthErrorCode.WeakPassword -> AuthFailure.WeakPassword
    AuthErrorCode.EmailAddressInvalid -> AuthFailure.InvalidEmail
    AuthErrorCode.OverEmailSendRateLimit,
    AuthErrorCode.OverRequestRateLimit,
    AuthErrorCode.OverSmsSendRateLimit,
    -> AuthFailure.TooManyAttempts
    AuthErrorCode.SignupDisabled -> AuthFailure.SignupDisabled
    else -> AuthFailure.Server
}

/**
 * What went wrong, from whatever the client threw.
 *
 * The split that matters is the last two branches. Supabase wraps transport
 * errors in `HttpRequestException`, which is an [IOException]; every HTTP error
 * response arrives as a `RestException`, which is not. So "blame the
 * connection" is reachable only when there genuinely was no answer.
 */
fun Throwable?.toAuthFailure(): AuthFailure = when (this) {
    is AuthRestException -> authFailureOf(errorCode)
    is IOException -> AuthFailure.Offline
    else -> AuthFailure.Server
}

/**
 * What went wrong with a Google sign-in, or **null** meaning *say nothing*.
 *
 * Null is a real answer here, not a missing one. A caregiver who opened the
 * sheet and changed their mind has done nothing wrong, and reporting a failure
 * at them teaches distrust of a control that behaved exactly as asked.
 *
 * The reason this reads the exception itself instead of `compose-auth`'s result
 * type: that type folds a missing Google account into the same `ClosedByUser`
 * case as a genuine dismissal, and those two need opposite responses. By the
 * time the result exists the distinction is gone, so the call has to be ours.
 *
 * [NoCredentialException] is matched before the cancellation case deliberately —
 * both descend from `GetCredentialException`, and being explicit about which
 * wins costs nothing and survives a reshuffle of the hierarchy.
 */
fun Throwable.toGoogleFailure(): AuthFailure? = when (this) {
    is NoCredentialException -> AuthFailure.NoGoogleAccount
    is GetCredentialCancellationException -> null
    is IOException -> AuthFailure.Offline
    else -> AuthFailure.Server
}
