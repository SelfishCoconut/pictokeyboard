package org.pictokeyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bookkeeping behind Beautify's undo (#46).
 *
 * What is being asserted here is that the string this class claims is in the
 * field is always the string the keyboard actually put there — because that
 * claim is what the service checks against the real editor before it deletes
 * anything, and a wrong claim is how a keyboard eats someone's message.
 */
class BeautifyEditTest {

    @Test
    fun `a fresh phrase offers neither action`() {
        val edit = BeautifyEdit()
        assertFalse(edit.canBeautify)
        assertFalse(edit.canUndo)
    }

    @Test
    fun `committed words accumulate exactly, spacing included`() {
        val edit = BeautifyEdit().plus("yo ").plus("querer ").plus("agua ")
        assertEquals("yo querer agua ", edit.typed)
        assertEquals("yo querer agua ", edit.inField)
        assertTrue(edit.canBeautify)
        assertFalse(edit.canUndo)
    }

    @Test
    fun `applying a rephrase makes it what is in the field, and offers undo`() {
        val edit = BeautifyEdit().plus("yo ").plus("querer ").plus("agua ").applying("Quiero agua.")
        assertEquals("yo querer agua ", edit.typed)
        assertEquals("Quiero agua.", edit.inField)
        assertTrue(edit.canUndo)
    }

    @Test
    fun `undo restores the typed words exactly`() {
        val edit = BeautifyEdit().plus("yo ").plus("querer ").plus("agua ")
            .applying("Quiero agua.")
            .undone()
        assertEquals("yo querer agua ", edit.inField)
        assertFalse(edit.canUndo)
        assertNull(edit.applied)
    }

    /**
     * Pressing Beautify again has to land somewhere else, so the variant has to
     * keep climbing across presses rather than resetting with each one.
     */
    @Test
    fun `the variant advances with every rephrase`() {
        val once = BeautifyEdit().plus("yo ").applying("Yo.")
        assertEquals(1, once.variant)
        assertEquals(2, once.applying("Soy yo.").variant)
        // Undo is not a new phrase, so it does not rewind the cycle -- pressing
        // Beautify after an undo should offer the next variant, not the one that
        // was just rejected by hand.
        assertEquals(2, once.applying("Soy yo.").undone().variant)
    }

    /**
     * The dangerous case. Once another word is committed after a rephrase, the
     * field holds the model's sentence *and* the new word, and "the words you
     * typed" is no longer a string that ever existed there. Undo has to stop
     * being offered rather than delete a range that is now wrong.
     */
    @Test
    fun `typing after a rephrase drops undo and rebaselines on what is in the field`() {
        val edit = BeautifyEdit().plus("yo ").plus("querer ")
            .applying("Quiero.")
            .plus("agua ")
        assertFalse(edit.canUndo)
        assertEquals("Quiero.agua ", edit.typed)
        assertEquals("Quiero.agua ", edit.inField)
    }

    @Test
    fun `backspace shrinks the tracked range by what actually left the field`() {
        val edit = BeautifyEdit().plus("yo ").plus("querer ").minus("querer ".length)
        assertEquals("yo ", edit.typed)
    }

    @Test
    fun `backspacing past the start cannot go negative`() {
        val edit = BeautifyEdit().plus("yo ").minus(999)
        assertEquals("", edit.typed)
        assertFalse(edit.canBeautify)
    }

    @Test
    fun `backspace after a rephrase also drops undo`() {
        val edit = BeautifyEdit().plus("yo ").plus("querer ")
            .applying("Quiero.")
            .minus(1)
        assertFalse(edit.canUndo)
        assertEquals("Quiero", edit.typed)
    }

    /** Nothing may leak from one app into the next. */
    @Test
    fun `clearing drops everything including the variant`() {
        val edit = BeautifyEdit().plus("yo ").applying("Yo.").cleared()
        assertEquals(BeautifyEdit(), edit)
        assertEquals(0, edit.variant)
        assertFalse(edit.canBeautify)
        assertFalse(edit.canUndo)
    }

    /** Whitespace alone is not a phrase worth spending a model on. */
    @Test
    fun `blank committed text is not beautifiable`() {
        assertFalse(BeautifyEdit().plus("   ").canBeautify)
    }
}
