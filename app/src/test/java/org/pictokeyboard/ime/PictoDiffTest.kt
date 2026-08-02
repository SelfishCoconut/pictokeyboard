package org.pictokeyboard.ime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pictokeyboard.data.db.BorderStyles
import org.pictokeyboard.data.db.CategoryEntity
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

    /** A representative tile; each test varies one field with `copy`. */
    private fun tile(id: String, label: String = "pan", position: Int = 0) =
        PictoAdapter.Tile(
            picto = picto(id, label, position),
            imageModel = "https://static.arasaac.org/pictograms/1/1_500.png",
            frameColor = 0xFFF57C00.toInt(),
            borderStyle = BorderStyles.SOLID,
            borderWidthDp = BorderStyles.DEFAULT_WIDTH_DP,
            showLabel = true,
        )

    private val diff = PictoAdapter.DIFF

    @Test
    fun `items are the same when picto ids match`() {
        assertTrue(diff.areItemsTheSame(tile("1"), tile("1", label = "otro")))
    }

    @Test
    fun `items differ when picto ids differ`() {
        assertFalse(diff.areItemsTheSame(tile("1"), tile("2")))
    }

    @Test
    fun `contents are the same for an identical tile`() {
        assertTrue(diff.areContentsTheSame(tile("1"), tile("1")))
    }

    @Test
    fun `a changed label is a content change`() {
        assertFalse(diff.areContentsTheSame(tile("1"), tile("1", label = "leche")))
    }

    @Test
    fun `a changed position is a content change`() {
        assertFalse(diff.areContentsTheSame(tile("1"), tile("1", position = 3)))
    }

    // The four below are the point of folding style into the item. While style
    // lived in adapter fields, DiffUtil could not see any of these, and the
    // repaint depended on a submitList callback that is dropped whenever a newer
    // submit supersedes the diff.

    @Test
    fun `a changed frame colour is a content change`() {
        assertFalse(diff.areContentsTheSame(tile("1"), tile("1").copy(frameColor = 0xFF4CAF50.toInt())))
    }

    @Test
    fun `a changed border style is a content change`() {
        assertFalse(diff.areContentsTheSame(tile("1"), tile("1").copy(borderStyle = BorderStyles.DASHED)))
    }

    @Test
    fun `a changed border width is a content change`() {
        assertFalse(diff.areContentsTheSame(tile("1"), tile("1").copy(borderWidthDp = 8)))
    }

    @Test
    fun `hiding the caption is a content change`() {
        assertFalse(diff.areContentsTheSame(tile("1"), tile("1").copy(showLabel = false)))
    }

    @Test
    fun `a changed image model is a content change`() {
        assertFalse(diff.areContentsTheSame(tile("1"), tile("1").copy(imageModel = null)))
    }

    @Test
    fun `selection is a content change for a category row`() {
        val category = CategoryEntity(id = "food", name = "Comida", colorArgb = 0, position = 0)
        val unselected = CategoryAdapter.Row(category, selected = false, iconModel = null)
        val selected = CategoryAdapter.Row(category, selected = true, iconModel = null)
        assertTrue(CategoryAdapter.DIFF.areItemsTheSame(unselected, selected))
        assertFalse(CategoryAdapter.DIFF.areContentsTheSame(unselected, selected))
    }

    @Test
    fun `a chip that gains an icon is a content change`() {
        val category = CategoryEntity(id = "food", name = "Comida", colorArgb = 0, position = 0)
        val plain = CategoryAdapter.Row(category, selected = false, iconModel = null)
        val withIcon = plain.copy(iconModel = "https://static.arasaac.org/pictograms/2/2_500.png")
        assertTrue(CategoryAdapter.DIFF.areItemsTheSame(plain, withIcon))
        assertFalse(CategoryAdapter.DIFF.areContentsTheSame(plain, withIcon))
    }
}
