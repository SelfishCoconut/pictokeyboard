package org.pictokeyboard.data.auth

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The mapping the whole account UI reads from.
 *
 * A pure function over a plain snapshot rather than over Supabase's own
 * `SessionStatus`, which cannot be constructed in a JVM test without a client —
 * so testing it any other way would mean not testing it.
 */
class AccountStateTest {

    @Test
    fun `an unconfigured build has no account, session or not`() {
        assertEquals(AccountState.Unavailable, accountStateOf(configured = false, session = null))
        // Even with a cached session: a build with no backend has no business
        // showing an account, and a stale session must not resurrect one.
        assertEquals(
            AccountState.Unavailable,
            accountStateOf(configured = false, session = SessionSnapshot("a@b.com")),
        )
    }

    @Test
    fun `no session on a configured build is signed out`() {
        assertEquals(AccountState.SignedOut, accountStateOf(configured = true, session = null))
    }

    @Test
    fun `a session carries the email through for the account screen`() {
        assertEquals(
            AccountState.SignedIn("a@b.com"),
            accountStateOf(configured = true, session = SessionSnapshot("a@b.com")),
        )
    }

    @Test
    fun `a session without an email is still signed in`() {
        // Google can return a user with no email claim. Reporting that as signed
        // out would strand the caregiver in a loop: signing in, and appearing
        // not to have.
        assertEquals(
            AccountState.SignedIn(null),
            accountStateOf(configured = true, session = SessionSnapshot(null)),
        )
    }
}
