package org.pictokeyboard.sentence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The validator can be switched off for debugging (#167), and this is what keeps
 * that out of anybody's hands.
 *
 * Modelled on `AppHasNoAccountsTest`, and for the same reason: the property being
 * defended is a decision somebody could undo by accident, one edit at a time,
 * with everything still compiling and everything still working on a desk. The
 * difference is what it costs when it goes wrong. An accounts import breaks a
 * keyboard in a waiting room; this one puts a 0.6B model's invention into a
 * non-speaking person's message, in their name, with nobody able to read it back
 * and check.
 *
 * Two halves, because one alone would not be worth much. The first asks the
 * *release* question, which an inline `BuildConfig.DEBUG` could never be made to
 * answer from a debug build. The second reads the sources, because the guard
 * only guards what actually goes through it.
 */
class ValidatorBypassTest {

    private companion object {
        /** Unit tests run with the module directory as the working directory. */
        const val APP_SOURCES = "src/main/java/org/pictokeyboard"

        /** The one file allowed to turn the harness off. */
        const val GATEKEEPER = "SentenceService.kt"
    }

    @Test
    fun `a release build refuses, whatever it was asked for`() {
        assertFalse("a shipped build must never skip the validator", ValidatorBypass.allowed(true, false))
        assertFalse(ValidatorBypass.allowed(false, false))
    }

    @Test
    fun `a debug build obeys the switch, in both directions`() {
        assertTrue(ValidatorBypass.allowed(true, true))
        assertFalse("off is off even in a debug build", ValidatorBypass.allowed(false, true))
    }

    /**
     * The guard is only worth having if everything goes through it.
     *
     * `validate = false` is the one way to reach the bypass in `Beautifier`, and
     * **nothing may write it as a literal** — not even the gatekeeper, which
     * passes a value it computed from [ValidatorBypass]. A hardcoded `false`
     * anywhere is a second door standing open, and this is the check that
     * notices one being cut.
     */
    @Test
    fun `nothing in the app hardcodes the validator off`() {
        val sources = File(APP_SOURCES)
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

        // A guard that passes because it found nothing is worse than no guard.
        check(sources.isNotEmpty()) { "found no sources under $APP_SOURCES -- has the package moved?" }

        val offenders = sources
            .filter { "validate = false" in it.readText() }
            .map { it.name }

        assertEquals(
            "these files skip the sentence validator without asking ValidatorBypass",
            emptyList<String>(),
            offenders,
        )
    }

    /**
     * And that the one place doing the asking is asking the right question.
     *
     * A literal `ValidatorBypass.allowed(unvalidated, BuildConfig.DEBUG)` rather
     * than any old call, because `allowed(unvalidated, true)` would compile,
     * pass every other test here, and ship the bypass.
     */
    @Test
    fun `the model process asks against the build type and nothing else`() {
        val service = File(APP_SOURCES).walkTopDown().first { it.name == GATEKEEPER }.readText()
        assertTrue(
            "$GATEKEEPER must gate the bypass on BuildConfig.DEBUG",
            "ValidatorBypass.allowed(unvalidated, BuildConfig.DEBUG)" in service,
        )
    }
}
