package org.pictokeyboard.ime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pictokeyboard.data.db.PictoEntity

class PictoDiffTest {

    private fun picto(id: String, label: String = "pan", position: Int = 0) =
        PictoEntity(
            id = id,
            categoryId = "food",
            label = label,
            spokenText = label,
            language = "es",
            position = position,
        )

    private val diff = PictoAdapter.DIFF

    @Test
    fun `items are the same when ids match`() {
        assertTrue(diff.areItemsTheSame(picto("1"), picto("1", label = "otro")))
    }

    @Test
    fun `items differ when ids differ`() {
        assertFalse(diff.areItemsTheSame(picto("1"), picto("2")))
    }

    @Test
    fun `contents are the same for an identical entity`() {
        assertTrue(diff.areContentsTheSame(picto("1"), picto("1")))
    }

    @Test
    fun `a changed label is a content change`() {
        assertFalse(diff.areContentsTheSame(picto("1"), picto("1", label = "leche")))
    }

    @Test
    fun `a changed position is a content change`() {
        assertFalse(diff.areContentsTheSame(picto("1"), picto("1", position = 3)))
    }
}
