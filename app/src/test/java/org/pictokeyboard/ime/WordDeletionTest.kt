package org.pictokeyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Characterization tests for whole-word backspace. These describe what the code
 * does today, including anything that looks surprising -- they exist to catch a
 * change in behaviour, not to assert what the behaviour ought to be.
 */
class WordDeletionTest {

    private fun lengthOf(text: String) =
        PictoKeyboardService.trailingWordLength(text)

    @Test
    fun `deletes the word and the space the keyboard appended after it`() {
        assertEquals(4, lengthOf("yo comer pan "))
    }

    @Test
    fun `deletes a bare trailing word when no space follows`() {
        assertEquals(3, lengthOf("yo comer pan"))
    }

    @Test
    fun `deletes every trailing space plus the word before them`() {
        // "pan" plus all three trailing spaces.
        assertEquals(6, lengthOf("yo pan   "))
    }

    @Test
    fun `empty input deletes nothing`() {
        assertEquals(0, lengthOf(""))
    }

    @Test
    fun `whitespace-only input is consumed entirely`() {
        assertEquals(3, lengthOf("   "))
    }

    @Test
    fun `a single word is consumed entirely`() {
        assertEquals(3, lengthOf("pan"))
    }

    @Test
    fun `newlines count as whitespace`() {
        // The newline is consumed as trailing whitespace, then "hola" with it.
        assertEquals(5, lengthOf("hola\n"))
    }
}
