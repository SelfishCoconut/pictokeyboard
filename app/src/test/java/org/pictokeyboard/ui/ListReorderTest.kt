package org.pictokeyboard.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListReorderTest {

    private val list = listOf("a", "b", "c")

    @Test
    fun `moving up swaps with the previous item`() {
        assertEquals(listOf("a", "c", "b"), movedBy(list, { it == "c" }, up = true))
    }

    @Test
    fun `moving down swaps with the next item`() {
        assertEquals(listOf("b", "a", "c"), movedBy(list, { it == "a" }, up = false))
    }

    @Test
    fun `the first item cannot move up`() {
        assertNull(movedBy(list, { it == "a" }, up = true))
    }

    @Test
    fun `the last item cannot move down`() {
        assertNull(movedBy(list, { it == "c" }, up = false))
    }

    @Test
    fun `an absent item returns null`() {
        assertNull(movedBy(list, { it == "z" }, up = true))
    }

    @Test
    fun `an empty list returns null`() {
        assertNull(movedBy(emptyList<String>(), { true }, up = true))
    }

    @Test
    fun `the source list is not mutated`() {
        val original = listOf("a", "b", "c")
        movedBy(original, { it == "a" }, up = false)
        assertEquals(listOf("a", "b", "c"), original)
    }
}
