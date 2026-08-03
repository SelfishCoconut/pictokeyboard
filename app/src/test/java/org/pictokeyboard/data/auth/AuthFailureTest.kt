package org.pictokeyboard.data.auth

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.NoCredentialException
import io.github.jan.supabase.auth.exception.AuthErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

/**
 * Which server answer is which kind of failure.
 *
 * Every one of these was a single "check your connection" line until a run
 * against a real Supabase project showed a wrong password being reported as a
 * network problem — advice that sends a caregiver to their router while their
 * account sits there working.
 */
class AuthFailureTest {

    @Test
    fun `a rejected password is a credentials failure`() {
        assertEquals(AuthFailure.Credentials, authFailureOf(AuthErrorCode.InvalidCredentials))
    }

    @Test
    fun `an unconfirmed address is its own failure`() {
        // Distinct from Credentials on purpose: the password was right, and
        // telling someone it was wrong sends them to reset a password that works.
        assertEquals(AuthFailure.EmailNotConfirmed, authFailureOf(AuthErrorCode.EmailNotConfirmed))
    }

    @Test
    fun `both forms of already-registered are the same failure`() {
        assertEquals(AuthFailure.EmailTaken, authFailureOf(AuthErrorCode.UserAlreadyExists))
        assertEquals(AuthFailure.EmailTaken, authFailureOf(AuthErrorCode.EmailExists))
    }

    @Test
    fun `a password the server thinks is weak is a weak password`() {
        assertEquals(AuthFailure.WeakPassword, authFailureOf(AuthErrorCode.WeakPassword))
    }

    @Test
    fun `an address the server will not accept is an invalid address`() {
        assertEquals(AuthFailure.InvalidEmail, authFailureOf(AuthErrorCode.EmailAddressInvalid))
    }

    @Test
    fun `every rate limit is too many attempts`() {
        // The built-in mailer runs out after a handful of messages an hour, so
        // this is the one a caregiver testing sign-up will actually hit.
        assertEquals(AuthFailure.TooManyAttempts, authFailureOf(AuthErrorCode.OverEmailSendRateLimit))
        assertEquals(AuthFailure.TooManyAttempts, authFailureOf(AuthErrorCode.OverRequestRateLimit))
        assertEquals(AuthFailure.TooManyAttempts, authFailureOf(AuthErrorCode.OverSmsSendRateLimit))
    }

    @Test
    fun `signups being switched off is not the caregiver's fault`() {
        assertEquals(AuthFailure.SignupDisabled, authFailureOf(AuthErrorCode.SignupDisabled))
    }

    @Test
    fun `a code with nothing to say about it is a server failure`() {
        // Not Offline: the server answered. Blaming the connection for a reply
        // that arrived is the exact bug this whole file exists to prevent.
        assertEquals(AuthFailure.Server, authFailureOf(AuthErrorCode.UnexpectedFailure))
        assertEquals(AuthFailure.Server, authFailureOf(null))
    }

    @Test
    fun `a transport error is the only thing that blames the connection`() {
        assertEquals(AuthFailure.Offline, IOException("unreachable").toAuthFailure())
    }

    @Test
    fun `a failure with no exception at all is a server failure`() {
        assertEquals(AuthFailure.Server, (null as Throwable?).toAuthFailure())
    }

    @Test
    fun `a phone with no Google account is its own failure`() {
        // The case this mapping exists for. AAC devices are routinely school
        // managed or bought cheap, so "no Google account" is ordinary, not
        // exotic -- and it needs a sentence, not silence.
        assertEquals(
            AuthFailure.NoGoogleAccount,
            NoCredentialException("no credentials available").toGoogleFailure(),
        )
    }

    @Test
    fun `dismissing the Google sheet says nothing at all`() {
        // Null means say nothing. Someone who opened the sheet and changed
        // their mind has done nothing wrong, and reporting a failure at them
        // teaches distrust of a control that behaved correctly.
        assertNull(GetCredentialCancellationException("user cancelled").toGoogleFailure())
    }

    @Test
    fun `a credential error we have no advice for is a server failure`() {
        assertEquals(
            AuthFailure.Server,
            GetCredentialUnknownException("something else").toGoogleFailure(),
        )
    }

    @Test
    fun `a Google sign-in with no connection still blames the connection`() {
        assertEquals(AuthFailure.Offline, IOException("unreachable").toGoogleFailure())
    }
}
