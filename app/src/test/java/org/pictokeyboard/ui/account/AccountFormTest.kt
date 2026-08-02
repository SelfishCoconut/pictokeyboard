package org.pictokeyboard.ui.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Submit is gated locally so a caregiver on a slow connection is told "that is
 * not an email address" immediately, rather than after a round trip that is
 * indistinguishable from the app having hung.
 */
class AccountFormTest {

    @Test
    fun `empty cannot be submitted`() {
        assertFalse(AccountForm().canSubmit)
    }

    @Test
    fun `an address without an at sign cannot be submitted`() {
        assertFalse(AccountForm(email = "alvar", password = "correct-horse").canSubmit)
    }

    @Test
    fun `an address without a dot after the at sign cannot be submitted`() {
        assertFalse(AccountForm(email = "alvar@localhost", password = "correct-horse").canSubmit)
    }

    @Test
    fun `a short password cannot be submitted`() {
        // Supabase's own default floor is 6; rejecting here saves a round trip
        // that would come back as an opaque server error.
        assertFalse(AccountForm(email = "a@b.com", password = "12345").canSubmit)
    }

    @Test
    fun `surrounding whitespace does not block a valid address`() {
        // Soft keyboards add a trailing space after autocomplete constantly, and
        // "invalid email" for an address that looks perfectly correct on screen
        // is a dead end a caregiver cannot debug.
        assertTrue(AccountForm(email = " a@b.com ", password = "correct-horse").canSubmit)
    }

    @Test
    fun `the trimmed address is what gets sent`() {
        assertEquals("a@b.com", AccountForm(email = "  a@b.com  ").submittedEmail)
    }

    @Test
    fun `the password is never trimmed`() {
        // A leading or trailing space is a legitimate character in a password,
        // and silently stripping it locks the caregiver out of their own account.
        assertEquals(" hunter2 ", AccountForm(password = " hunter2 ").password)
    }

    @Test
    fun `a valid pair can be submitted`() {
        assertTrue(AccountForm(email = "a@b.com", password = "correct-horse").canSubmit)
    }
}
