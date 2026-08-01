package org.pictokeyboard.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.data.db.PictoEntity
import java.io.File

/**
 * [currentIcon] and [previewModel] decide two things that fail silently rather
 * than loudly: whether reopening the editor offers to retry a download that
 * never happened, and whether the preview shows the same image the keyboard
 * will. Both render as a blank tile when wrong, with no exception to trace.
 */
class CategoryIconTest {

    private fun category(arasaacId: Int? = null, imagePath: String? = null) = CategoryEntity(
        id = "cat-1",
        name = "Food",
        colorArgb = 0,
        position = 0,
        iconArasaacId = arasaacId,
        iconImagePath = imagePath,
    )

    private fun picto(arasaacId: Int? = null, imagePath: String? = null) = PictoEntity(
        id = "pic-1",
        categoryId = "cat-1",
        label = "apple",
        spokenText = "apple",
        language = "en",
        arasaacId = arasaacId,
        imagePath = imagePath,
        position = 0,
    )

    @Test
    fun `a category with no picto starts on None`() {
        assertEquals(CategoryIcon.None, category().currentIcon())
    }

    @Test
    fun `a cached picto starts on the cached file`() {
        assertEquals(
            CategoryIcon.Local("/data/pictos/arasaac_2309.png", 2309),
            category(arasaacId = 2309, imagePath = "/data/pictos/arasaac_2309.png").currentIcon(),
        )
    }

    @Test
    fun `an imported photo keeps no arasaac id`() {
        assertEquals(
            CategoryIcon.Local("/data/pictos/custom_abc.png", null),
            category(imagePath = "/data/pictos/custom_abc.png").currentIcon(),
        )
    }

    @Test
    fun `an uncached arasaac picto starts on the id so saving retries the download`() {
        // The regression this guards: reading it back as Local(null-path) would
        // store a path that was never written, leaving the category permanently
        // blank offline. Happens on any install seeded without a connection.
        assertEquals(CategoryIcon.Arasaac(2309), category(arasaacId = 2309).currentIcon())
    }

    @Test
    fun `None has nothing to draw`() {
        assertNull(CategoryIcon.None.previewModel())
    }

    @Test
    fun `an uncached pick previews from the CDN`() {
        assertEquals(
            "https://static.arasaac.org/pictograms/2309/2309_500.png",
            CategoryIcon.Arasaac(2309).previewModel(),
        )
    }

    @Test
    fun `a promoted picto keeps its arasaac id for the CDN fallback`() {
        assertEquals(
            CategoryIcon.Local("/data/pictos/arasaac_2309.png", 2309),
            picto(arasaacId = 2309, imagePath = "/data/pictos/arasaac_2309.png").asCategoryIcon(),
        )
    }

    @Test
    fun `a picto with nothing cached yet is promoted by id`() {
        assertEquals(CategoryIcon.Arasaac(2309), picto(arasaacId = 2309).asCategoryIcon())
    }

    @Test
    fun `a picto with no image at all cannot be promoted`() {
        // The picker skips these rather than offering a tile that draws nothing.
        assertNull(picto().asCategoryIcon())
    }

    @Test
    fun `a cached file wins over the CDN`() {
        // Offline is the normal case for this app, not the exceptional one.
        assertEquals(
            File("/data/pictos/arasaac_2309.png"),
            CategoryIcon.Local("/data/pictos/arasaac_2309.png", 2309).previewModel(),
        )
    }
}
