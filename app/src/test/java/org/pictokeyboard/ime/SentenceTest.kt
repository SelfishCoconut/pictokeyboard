package org.pictokeyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pictokeyboard.tts.TtsManager

/**
 * The sentence bar's contract.
 *
 * The one that matters is that it stays a **mirror**: it can never hold a word
 * the field does not have, because a phrase stranded in a buffer is, for someone
 * whose only voice is this keyboard, a conversation that did not happen. These
 * pin the operations that could break the mirror — a delete that removes the
 * wrong amount, a clear that reaches into the field, a phrase surviving into the
 * next app.
 */
class SentenceTest {

    private fun sentence(vararg words: String) =
        words.fold(Sentence()) { acc, word -> acc.plus(word, "es") }

    @Test
    fun `words accumulate in the order they were written`() {
        assertEquals("yo · comer · galleta", sentence("yo", "comer", "galleta").display())
    }

    @Test
    fun `a backspace drops exactly one word, as it does in the field`() {
        val after = sentence("yo", "comer", "galleta").dropLast()
        assertEquals("yo · comer", after.display())
    }

    @Test
    fun `backspacing an empty bar is harmless`() {
        // The field may well have text the bar never saw -- typed by another
        // keyboard, or left over from before. Backspace still works there; the
        // bar simply has nothing of its own to give up.
        assertTrue(Sentence().dropLast().isEmpty)
    }

    @Test
    fun `clearing empties the bar`() {
        assertTrue(sentence("yo", "comer").cleared().isEmpty)
    }

    @Test
    fun `blank words never enter the bar`() {
        // A picto with an empty spokenText commits nothing to the field, so it
        // must leave nothing here either -- otherwise the bar shows a phrase with
        // a hole in it and the two stop agreeing.
        val after = Sentence().plus("  ", "es").plus("agua", "es").plus("", "en")
        assertEquals("agua", after.display())
    }

    @Test
    fun `each word keeps its own language for speaking back`() {
        // A board can mix languages, and a Spanish voice reading an English word
        // is not the word. TtsManager switches voice per part, so the parts have
        // to carry the language through.
        val mixed = Sentence().plus("quiero", "es").plus("water", "en")
        assertEquals(
            listOf(TtsManager.Part("quiero", "es"), TtsManager.Part("water", "en")),
            mixed.parts(),
        )
    }

    @Test
    fun `the screen reader gets commas, not the typography`() {
        // TalkBack pronounces the middot -- "yo middot comer" -- so the readout
        // is separated differently from the display.
        assertEquals("yo, comer", sentence("yo", "comer").spokenDescription())
    }

    @Test
    fun `a sentence is a value, so nothing can mutate one already handed out`() {
        val first = sentence("yo")
        val second = first.plus("comer", "es")
        assertEquals("yo", first.display())
        assertEquals("yo · comer", second.display())
    }

    // --- Editing the middle, which the arrows made possible (#143) ----------

    @Test
    fun `a word can be taken out of the middle`() {
        assertEquals("yo · galleta", sentence("yo", "quiero", "galleta").removeAt(1).display())
    }

    @Test
    fun `removing a word nobody has leaves the phrase alone`() {
        // The index comes from aligning this phrase against a field another app
        // can rewrite between one press and the next, so it can go stale. A
        // stale index must be a no-op rather than a crash inside somebody's
        // chat window.
        val phrase = sentence("yo", "quiero")
        assertEquals(phrase, phrase.removeAt(7))
        assertEquals(phrase, phrase.removeAt(-1))
    }

    @Test
    fun `a word can be put back where the wrong one was`() {
        val repaired = sentence("yo", "quiero", "galleta").removeAt(2).insertAt(2, "agua", "es")
        assertEquals("yo · quiero · agua", repaired.display())
    }

    @Test
    fun `an inserted word keeps its own language`() {
        val mixed = sentence("yo").insertAt(1, "water", "en")
        assertEquals(TtsManager.Part("water", "en"), mixed.parts()[1])
    }

    @Test
    fun `an out-of-range insert lands at the nearest end rather than failing`() {
        assertEquals("yo · agua", sentence("yo").insertAt(9, "agua", "es").display())
        assertEquals("agua · yo", sentence("yo").insertAt(-3, "agua", "es").display())
    }

    @Test
    fun `the highlight range covers exactly the word, not its separators`() {
        val phrase = sentence("yo", "quiero", "galleta")
        val display = phrase.display()
        assertEquals("yo", display.substring(requireNotNull(phrase.range(0))))
        assertEquals("quiero", display.substring(requireNotNull(phrase.range(1))))
        assertEquals("galleta", display.substring(requireNotNull(phrase.range(2))))
    }

    @Test
    fun `there is no range for a word that is not there`() {
        assertNull(sentence("yo").range(4))
    }

    @Test
    fun `the words alone are what the field is aligned against`() {
        assertEquals(listOf("yo", "quiero"), sentence("yo", "quiero").texts())
    }
}
