package org.pictokeyboard.data.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A build with no Supabase credentials is a supported build, not a broken one:
 * a fork, a fresh clone and every CI run are all in that state, and an account
 * is never required to use the keyboard.
 *
 * What must never happen is an Account section that is visible and dead, which
 * is what a half-filled or whitespace-only config would produce.
 */
class SupabaseConfigTest {

    @Test
    fun `a build with no credentials is not configured`() {
        assertFalse(SupabaseConfig("", "").isConfigured)
    }

    @Test
    fun `a half-filled build is not configured`() {
        // One value without the other cannot build a client, so offering the
        // account UI would strand the caregiver on a screen that can only fail.
        assertFalse(SupabaseConfig("https://abc.supabase.co", "").isConfigured)
        assertFalse(SupabaseConfig("", "anon-key").isConfigured)
    }

    @Test
    fun `whitespace is not a credential`() {
        // A stray space in local.properties survives Properties parsing intact,
        // and a non-empty-but-blank value would otherwise read as configured.
        assertFalse(SupabaseConfig("   ", "  ").isConfigured)
    }

    @Test
    fun `both values present means configured`() {
        assertTrue(SupabaseConfig("https://abc.supabase.co", "anon-key").isConfigured)
    }
}
