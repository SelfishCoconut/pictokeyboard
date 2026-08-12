package org.pictokeyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pictokeyboard.tts.TtsManager

/**
 * The phrase record's contract.
 *
 * The one that matters is that it stays a **record**: it can never hold a word
 * the field does not have, because a phrase stranded in a buffer is, for someone
 * whose only voice is this keyboard, a conversation that did not happen. These
 * pin the operations that could break that — a delete that removes the wrong
 * amount, a clear that reaches into the field, a phrase surviving into the next
 * app.
 *
 * Nothing draws this since #148, so the assertions are about `texts()` and
 * `parts()` — what 🔊 and Beautify actually consume — rather than about a strip
 * of screen that no longer exists.
 */
class SentenceTest {

    private fun sentence(vararg words: String) =
        words.fold(Sentence()) { acc, word -> acc.plus(word, "es") }

    @Test
    fun `words accumulate in the order they were written`() {
        assertEquals(listOf("yo", "comer", "galleta"), sentence("yo", "comer", "galleta").texts())
    }

    @Test
    fun `a backspace drops exactly one word, as it does in the field`() {
        assertEquals(listOf("yo", "comer"), sentence("yo", "comer", "galleta").dropLast().texts())
    }

    @Test
    fun `backspacing an empty phrase is harmless`() {
        // The field may well have text this keyboard never saw -- typed with
        // another keyboard, or left over from before. Backspace still works
        // there; the record simply has nothing of its own to give up.
        assertTrue(Sentence().dropLast().isEmpty)
    }

    @Test
    fun `clearing forgets everything`() {
        assertTrue(sentence("yo", "comer").cleared().isEmpty)
    }

    @Test
    fun `blank words are never recorded`() {
        // A picto with an empty spokenText commits nothing to the field, so it
        // must leave nothing here either -- otherwise 🔊 reads back a phrase
        // with a hole in it and the two stop agreeing.
        val after = Sentence().plus("  ", "es").plus("agua", "es").plus("", "en")
        assertEquals(listOf("agua"), after.texts())
    }

    @Test
    fun `each word keeps its own language for speaking back`() {
        // A board can mix languages, and a Spanish voice reading an English word
        // is not the word. TtsManager switches voice per part, so the parts have
        // to carry the language through. This is the whole reason the record
        // outlived the strip that used to show it.
        val mixed = Sentence().plus("quiero", "es").plus("water", "en")
        assertEquals(
            listOf(TtsManager.Part("quiero", "es"), TtsManager.Part("water", "en")),
            mixed.parts(),
        )
    }

    @Test
    fun `a sentence is a value, so nothing can mutate one already handed out`() {
        val first = sentence("yo")
        val second = first.plus("comer", "es")
        assertEquals(listOf("yo"), first.texts())
        assertEquals(listOf("yo", "comer"), second.texts())
    }

    // --- Editing the middle, which the arrows made possible (#143) ----------

    @Test
    fun `a word can be taken out of the middle`() {
        assertEquals(listOf("yo", "galleta"), sentence("yo", "quiero", "galleta").removeAt(1).texts())
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
        assertEquals(listOf("yo", "quiero", "agua"), repaired.texts())
    }

    @Test
    fun `an inserted word keeps its own language`() {
        val mixed = sentence("yo").insertAt(1, "water", "en")
        assertEquals(TtsManager.Part("water", "en"), mixed.parts()[1])
    }

    @Test
    fun `an out-of-range insert lands at the nearest end rather than failing`() {
        assertEquals(listOf("yo", "agua"), sentence("yo").insertAt(9, "agua", "es").texts())
        assertEquals(listOf("agua", "yo"), sentence("yo").insertAt(-3, "agua", "es").texts())
    }

    @Test
    fun `the words alone are what the field is aligned against`() {
        assertEquals(listOf("yo", "quiero"), sentence("yo", "quiero").texts())
    }
}
