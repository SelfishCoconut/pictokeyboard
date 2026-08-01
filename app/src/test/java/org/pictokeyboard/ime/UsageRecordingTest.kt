package org.pictokeyboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every tapped picto is written to the `usage` table, and that table is surfaced
 * to a *different person* — the caregiver — as the "Suggested" category in the
 * config app. So this decision is the only thing standing between a word typed
 * into a password box and that word appearing on someone else's screen.
 *
 * It fails silently in both directions: recording where it should not leaks, and
 * refusing where it should not just makes suggestions slightly worse. Only the
 * first is visible to anyone, and only long after the fact.
 */
class UsageRecordingTest {

    private fun field(inputType: Int, imeOptions: Int = EditorInfo.IME_ACTION_NONE) =
        EditorInfo().also {
            it.inputType = inputType
            it.imeOptions = imeOptions
        }

    @Test
    fun `an ordinary text field is recorded`() {
        assertTrue(field(InputType.TYPE_CLASS_TEXT).allowsUsageRecording())
    }

    @Test
    fun `a field that asks not to be learned from is not recorded`() {
        // The one signal a host app has to say "this is private" without
        // changing the keyboard the user sees: incognito tabs and private
        // messaging set it on otherwise ordinary text fields.
        assertFalse(
            field(
                inputType = InputType.TYPE_CLASS_TEXT,
                imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING,
            ).allowsUsageRecording(),
        )
    }

    @Test
    fun `the no-learning flag is honoured alongside an action`() {
        // imeOptions packs the action into the low bits, so anything that
        // compares the whole field instead of masking the flag misses this.
        assertFalse(
            field(
                inputType = InputType.TYPE_CLASS_TEXT,
                imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING,
            ).allowsUsageRecording(),
        )
    }

    @Test
    fun `a password field is not recorded`() {
        assertFalse(
            field(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD).allowsUsageRecording(),
        )
    }

    @Test
    fun `a visible password field is not recorded`() {
        // "Show password" makes it visible on screen, not less of a secret.
        assertFalse(
            field(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            ).allowsUsageRecording(),
        )
    }

    @Test
    fun `a web password field is not recorded`() {
        assertFalse(
            field(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            ).allowsUsageRecording(),
        )
    }

    @Test
    fun `a numeric PIN field is not recorded`() {
        assertFalse(
            field(
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
            ).allowsUsageRecording(),
        )
    }

    @Test
    fun `a URI field is still recorded even though it shares a variation with the PIN field`() {
        // TYPE_TEXT_VARIATION_URI and TYPE_NUMBER_VARIATION_PASSWORD are both
        // variation 0x10; only the class tells them apart. Matching on the
        // variation alone would quietly stop learning from every URL field.
        assertTrue(
            field(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI).allowsUsageRecording(),
        )
    }

    @Test
    fun `an ordinary number field is still recorded`() {
        assertTrue(field(InputType.TYPE_CLASS_NUMBER).allowsUsageRecording())
    }

    @Test
    fun `a password field is still refused when the host sets extra flags`() {
        // Real fields arrive with flags ORed in on top of the variation. A
        // straight equality check against the bare constant misses those.
        assertFalse(
            field(
                InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_PASSWORD or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
            ).allowsUsageRecording(),
        )
    }

    @Test
    fun `no field at all is not recorded`() {
        // Fails closed. Nothing was typed anywhere in this state, so refusing
        // costs a suggestion at most -- guessing the other way costs a secret.
        assertFalse((null as EditorInfo?).allowsUsageRecording())
    }
}
