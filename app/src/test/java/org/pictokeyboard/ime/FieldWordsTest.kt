package org.pictokeyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Finding words in somebody else's text field (#143).
 *
 * Everything here decides which characters `setSelection` and
 * `deleteSurroundingText` are pointed at, in a field this keyboard does not own.
 * Getting it wrong does not produce a wrong highlight — it produces a bite taken
 * out of a message the user was writing, and the person least able to notice is
 * the one this keyboard is for. So the arithmetic is settled here rather than in
 * an emulator with a chat app open.
 */
class FieldWordsTest {

    // --- spans -------------------------------------------------------------

    @Test
    fun `words are runs of non-whitespace, in the field's own positions`() {
        val spans = FieldWords.spans("yo quiero galleta", offset = 0, tailComplete = true)
        assertEquals(listOf(WordSpan(0, 2), WordSpan(3, 9), WordSpan(10, 17)), spans)
    }

    @Test
    fun `an offset window reports field positions, not window positions`() {
        val spans = FieldWords.spans(" quiero agua", offset = 100, tailComplete = true)
        assertEquals(listOf(WordSpan(101, 107), WordSpan(108, 112)), spans)
    }

    @Test
    fun `leading and trailing whitespace produce no empty words`() {
        val spans = FieldWords.spans("   hola  \n ", offset = 0, tailComplete = true)
        assertEquals(listOf(WordSpan(3, 7)), spans)
    }

    @Test
    fun `a word cut in half by the head of the window is dropped`() {
        // The window starts mid-`quiero`; selecting `iero` and deleting it would
        // take a bite out of a word the user never pointed at.
        val spans = FieldWords.spans("iero galleta", offset = 50, tailComplete = true)
        assertEquals(listOf(WordSpan(55, 62)), spans)
    }

    @Test
    fun `a word cut in half by the tail of the window is dropped`() {
        val spans = FieldWords.spans("yo quiero gall", offset = 0, tailComplete = false)
        assertEquals(listOf(WordSpan(0, 2), WordSpan(3, 9)), spans)
    }

    @Test
    fun `a complete tail keeps its last word`() {
        val spans = FieldWords.spans("yo quiero gall", offset = 0, tailComplete = true)
        assertEquals(3, spans.size)
    }

    @Test
    fun `a window starting at the field's own beginning keeps its first word`() {
        val spans = FieldWords.spans("yo quiero", offset = 0, tailComplete = true)
        assertEquals(WordSpan(0, 2), spans.first())
    }

    // --- step --------------------------------------------------------------

    private val phrase = FieldWords.spans("yo quiero galleta", offset = 0, tailComplete = true)

    @Test
    fun `backwards from the end of the phrase reaches the last word`() {
        assertEquals(WordSpan(10, 17), FieldWords.step(phrase, 17, 17, forward = false))
    }

    @Test
    fun `backwards again steps one word further back`() {
        assertEquals(WordSpan(3, 9), FieldWords.step(phrase, 10, 17, forward = false))
    }

    @Test
    fun `forwards from a selected word steps to the next one`() {
        assertEquals(WordSpan(10, 17), FieldWords.step(phrase, 3, 9, forward = true))
    }

    @Test
    fun `stepping past the last word stays on it rather than wrapping`() {
        // Wrapping is silent teleportation for somebody listening rather than
        // looking: pressing forward twice at the end should say the last word
        // twice, not jump back to the first.
        assertEquals(WordSpan(10, 17), FieldWords.step(phrase, 10, 17, forward = true))
    }

    @Test
    fun `stepping before the first word stays on it`() {
        assertEquals(WordSpan(0, 2), FieldWords.step(phrase, 0, 2, forward = false))
    }

    @Test
    fun `a caret inside a word selects that word rather than stepping over it`() {
        assertEquals(WordSpan(3, 9), FieldWords.step(phrase, 5, 5, forward = true))
        assertEquals(WordSpan(3, 9), FieldWords.step(phrase, 5, 5, forward = false))
    }

    @Test
    fun `a caret between words reaches the nearest one in that direction`() {
        assertEquals(WordSpan(10, 17), FieldWords.step(phrase, 9, 9, forward = true))
        assertEquals(WordSpan(3, 9), FieldWords.step(phrase, 9, 9, forward = false))
    }

    @Test
    fun `an empty field has nowhere to step`() {
        assertNull(FieldWords.step(emptyList(), 0, 0, forward = true))
    }

    // --- deletionRange -----------------------------------------------------

    @Test
    fun `deleting a word takes the space after it, so no double space is left`() {
        val text = "yo quiero galleta"
        val range = FieldWords.deletionRange(text, offset = 0, word = WordSpan(3, 9))
        assertEquals(WordSpan(3, 10), range)
        assertEquals("yo galleta", text.removeRange(range.start, range.end))
    }

    @Test
    fun `the last word takes the space before it instead`() {
        val text = "yo quiero galleta"
        val range = FieldWords.deletionRange(text, offset = 0, word = WordSpan(10, 17))
        assertEquals(WordSpan(9, 17), range)
        assertEquals("yo quiero", text.removeRange(range.start, range.end))
    }

    @Test
    fun `the only word in a field takes no space with it`() {
        val range = FieldWords.deletionRange("hola", offset = 0, word = WordSpan(0, 4))
        assertEquals(WordSpan(0, 4), range)
    }

    @Test
    fun `the first word of several takes the space after it`() {
        val text = "yo quiero"
        val range = FieldWords.deletionRange(text, offset = 0, word = WordSpan(0, 2))
        assertEquals("quiero", text.removeRange(range.start, range.end))
    }

    // --- align -------------------------------------------------------------

    @Test
    fun `a bar matching the whole field maps one to one`() {
        val map = FieldWords.align(listOf("yo", "quiero", "agua"), listOf("yo", "quiero", "agua"))
        assertEquals(mapOf(0 to 0, 1 to 1, 2 to 2), map)
    }

    @Test
    fun `the bar is found after text this keyboard did not write`() {
        val map = FieldWords.align(listOf("Hola", "yo", "quiero"), listOf("yo", "quiero"))
        assertEquals(mapOf(1 to 0, 2 to 1), map)
    }

    @Test
    fun `a repeated word resolves to the last run, where the user is working`() {
        val map = FieldWords.align(listOf("yo", "quiero", "yo"), listOf("yo"))
        assertEquals(mapOf(2 to 0), map)
    }

    @Test
    fun `one bar word may cover several field words`() {
        // A picto labelled `me gusta` is one word in the bar and two in the field.
        val map = FieldWords.align(listOf("me", "gusta", "esto"), listOf("me gusta", "esto"))
        assertEquals(mapOf(0 to 0, 1 to 0, 2 to 1), map)
    }

    @Test
    fun `a field that no longer contains the phrase yields nothing`() {
        // What a rephrase leaves behind: the field holds the model's sentence
        // and the bar still holds the typed words. Guessing here would put the
        // highlight on the wrong word, or delete it.
        assertNull(FieldWords.align(listOf("Quiero", "una", "galleta."), listOf("yo", "querer", "galleta")))
    }

    @Test
    fun `a bar longer than the field yields nothing`() {
        assertNull(FieldWords.align(listOf("yo"), listOf("yo", "quiero")))
    }

    @Test
    fun `an empty bar yields nothing`() {
        assertNull(FieldWords.align(listOf("yo"), emptyList()))
    }
}
