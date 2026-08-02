package org.pictokeyboard.ime

import org.junit.Assert.assertEquals
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
}
