package org.pictokeyboard.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.pictokeyboard.data.db.BoardEntity
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.data.db.PictoEntity
import java.io.File

/**
 * [currentIcon] and [previewModel] decide two things that fail silently rather
 * than loudly: whether reopening the editor offers to retry a download that
 * never happened, and whether the preview shows the same image the keyboard
 * will. Both render as a blank tile when wrong, with no exception to trace.
 */
class IconChoiceTest {

    private fun category(arasaacId: Int? = null, imagePath: String? = null) = CategoryEntity(
        id = "cat-1",
        name = "Food",
        colorArgb = 0,
        position = 0,
        iconArasaacId = arasaacId,
        iconImagePath = imagePath,
    )

    private fun picto(
        arasaacId: Int? = null,
        imagePath: String? = null,
        label: String = "apple",
        spokenText: String = "apple",
    ) = PictoEntity(
        id = "pic-1",
        categoryId = "cat-1",
        label = label,
        spokenText = spokenText,
        language = "en",
        arasaacId = arasaacId,
        imagePath = imagePath,
        position = 0,
    )

    private fun board(arasaacId: Int? = null, imagePath: String? = null) = BoardEntity(
        id = "board-1",
        name = "Doctor",
        colorArgb = 0,
        position = 0,
        iconArasaacId = arasaacId,
        iconImagePath = imagePath,
    )

    @Test
    fun `a category with no picto starts on None`() {
        assertEquals(IconChoice.None, category().currentIcon())
    }

    // --- Boards ------------------------------------------------------------
    //
    // A board reaches the picker through the same type as a category (#54), so
    // these prove the two entities really do resolve alike rather than merely
    // sharing a name. A board that resolved differently would show the caregiver
    // a picto the keyboard tab does not draw.

    @Test
    fun `a board with no picto starts on None`() {
        assertEquals(IconChoice.None, board().currentIcon())
    }

    @Test
    fun `a board with a cached picto starts on the cached file`() {
        assertEquals(
            IconChoice.Local("/data/pictos/arasaac_2309.png", 2309),
            board(arasaacId = 2309, imagePath = "/data/pictos/arasaac_2309.png").currentIcon(),
        )
    }

    @Test
    fun `a board whose download never landed starts on the id, so saving retries it`() {
        // The whole reason boards and categories share this function: storing a
        // path that was never written leaves a tab permanently blank on a device
        // that is perfectly able to fetch the image.
        assertEquals(IconChoice.Arasaac(2309), board(arasaacId = 2309).currentIcon())
    }

    @Test
    fun `a board and a category with the same columns resolve identically`() {
        assertEquals(
            category(arasaacId = 2309, imagePath = "/p.png").currentIcon(),
            board(arasaacId = 2309, imagePath = "/p.png").currentIcon(),
        )
        assertEquals(category(arasaacId = 7).currentIcon(), board(arasaacId = 7).currentIcon())
        assertEquals(category().currentIcon(), board().currentIcon())
    }

    @Test
    fun `a cached picto starts on the cached file`() {
        assertEquals(
            IconChoice.Local("/data/pictos/arasaac_2309.png", 2309),
            category(arasaacId = 2309, imagePath = "/data/pictos/arasaac_2309.png").currentIcon(),
        )
    }

    @Test
    fun `an imported photo keeps no arasaac id`() {
        assertEquals(
            IconChoice.Local("/data/pictos/custom_abc.png", null),
            category(imagePath = "/data/pictos/custom_abc.png").currentIcon(),
        )
    }

    @Test
    fun `an uncached arasaac picto starts on the id so saving retries the download`() {
        // The regression this guards: reading it back as Local(null-path) would
        // store a path that was never written, leaving the category permanently
        // blank offline. Happens on any install seeded without a connection.
        assertEquals(IconChoice.Arasaac(2309), category(arasaacId = 2309).currentIcon())
    }

    @Test
    fun `None has nothing to draw`() {
        assertNull(IconChoice.None.previewModel())
    }

    @Test
    fun `an uncached pick previews from the CDN`() {
        assertEquals(
            "https://static.arasaac.org/pictograms/2309/2309_500.png",
            IconChoice.Arasaac(2309).previewModel(),
        )
    }

    @Test
    fun `a promoted picto keeps its arasaac id for the CDN fallback`() {
        assertEquals(
            IconChoice.Local("/data/pictos/arasaac_2309.png", 2309, "apple"),
            picto(arasaacId = 2309, imagePath = "/data/pictos/arasaac_2309.png").asIconChoice(),
        )
    }

    @Test
    fun `a picto with nothing cached yet is promoted by id`() {
        assertEquals(IconChoice.Arasaac(2309, "apple"), picto(arasaacId = 2309).asIconChoice())
    }

    @Test
    fun `a promoted picto carries its name so a screen reader can confirm the pick`() {
        // Without this the tile announces "Current category picto" for every
        // choice, and a caregiver who cannot see it has no way to tell a mis-tap
        // from the symbol they meant.
        assertEquals("apple", picto(arasaacId = 2309).asIconChoice()?.label)
    }

    @Test
    fun `a picto with a blank label falls back to what it speaks`() {
        // Matches what the picker row already announces, so choosing one does not
        // rename it halfway through the gesture.
        assertEquals(
            "manzana",
            picto(arasaacId = 2309, label = "  ", spokenText = "manzana").asIconChoice()?.label,
        )
    }

    @Test
    fun `a picto read back from the database has no name to announce`() {
        // No column stores it, so the tile falls back to the generic description
        // rather than inventing one. Honest silence beats a wrong name.
        assertNull(category(arasaacId = 2309).currentIcon().label)
        assertNull(category(imagePath = "/data/pictos/custom_abc.png").currentIcon().label)
    }

    @Test
    fun `a picto with no image at all cannot be promoted`() {
        // The picker skips these rather than offering a tile that draws nothing.
        assertNull(picto().asIconChoice())
    }

    @Test
    fun `a cached file wins over the CDN`() {
        // Offline is the normal case for this app, not the exceptional one.
        assertEquals(
            File("/data/pictos/arasaac_2309.png"),
            IconChoice.Local("/data/pictos/arasaac_2309.png", 2309).previewModel(),
        )
    }
}
