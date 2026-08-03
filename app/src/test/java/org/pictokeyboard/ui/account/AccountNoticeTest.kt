package org.pictokeyboard.ui.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.pictokeyboard.R
import org.pictokeyboard.data.auth.AuthFailure

/**
 * What the caregiver is actually told.
 *
 * The screen has one line to explain a failure, and it is read aloud
 * assertively, so a wrong line is worse than no line: it sends someone to fix
 * something that was never broken.
 */
class AccountNoticeTest {

    @Test
    fun `a wrong password never blames the connection`() {
        // The defect this file was written for. Caught on an emulator run
        // against a real project, where "Invalid login credentials" came back
        // as "check your connection and try again".
        assertNotEquals(R.string.account_error_offline, messageFor(AuthFailure.Credentials))
    }

    @Test
    fun `only a transport failure mentions the connection`() {
        assertEquals(R.string.account_error_offline, messageFor(AuthFailure.Offline))
    }

    @Test
    fun `every failure says something different`() {
        // Two failures sharing a line means one of them is being described by a
        // sentence written for the other.
        val messages = AuthFailure.entries.map(::messageFor)
        assertEquals(AuthFailure.entries.size, messages.toSet().size)
    }
}
