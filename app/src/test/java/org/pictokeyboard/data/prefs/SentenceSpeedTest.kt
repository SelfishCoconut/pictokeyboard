package org.pictokeyboard.data.prefs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pictokeyboard.sentence.ModelSpec

/**
 * The three answers a benchmark can give, and why they are three (#145).
 *
 * "Never measured", "measured and slow" and "the test did not finish" ask the
 * caregiver for different things, and collapsing any two of them would put a
 * verdict on a phone nobody has timed. The distinction lives in the type rather
 * than in the screen, so it cannot be lost by a later edit to the layout.
 */
class SentenceSpeedTest {

    @Test
    fun `a phone that has never been timed says nothing`() {
        assertTrue(Settings().sentenceSpeed == null)
    }

    @Test
    fun `a run that did not finish is not a slow run`() {
        // Zero is what a failed attempt records. It must not read as "instant",
        // and it must not read as "over budget" either.
        val failed = SentenceSpeed(loadMillis = 4_000, generateMillis = 0)
        assertFalse(failed.measured)
    }

    @Test
    fun `a real measurement is a measurement however slow it is`() {
        // Nothing here refuses a phone. Eight seconds is a number the caregiver
        // is shown and left to decide about.
        assertTrue(SentenceSpeed(loadMillis = 6_000, generateMillis = 8_000).measured)
        assertTrue(SentenceSpeed(loadMillis = 0, generateMillis = 1).measured)
    }

    @Test
    fun `the budget is the one from the issue, not a rounder number`() {
        // #44 asks for a full sentence in under two seconds. The check in
        // settings reads this constant, so moving the goalposts means moving
        // them here where the reason is written down.
        assertTrue(ModelSpec.BUDGET_MILLIS == 2_000)
    }
}
