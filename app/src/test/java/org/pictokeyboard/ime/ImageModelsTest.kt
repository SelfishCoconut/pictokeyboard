package org.pictokeyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.pictokeyboard.data.arasaac.ArasaacUrls
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.data.db.PictoEntity
import java.io.File

/**
 * The fallback order these resolvers encode used to live inline in
 * `onBindViewHolder`, where it could only be checked by running the keyboard.
 * Pulling it into a pure function is what makes it testable; these tests are the
 * reason the `File.exists()` stat is worth keeping at all, since dropping it
 * silently loses the last case in each list.
 */
class ImageModelsTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun picto(arasaacId: Int? = null, imagePath: String? = null) =
        PictoEntity(
            id = "1",
            categoryId = "food",
            label = "pan",
            spokenText = "pan",
            language = "es",
            arasaacId = arasaacId,
            imagePath = imagePath,
            position = 0,
        )

    private fun category(iconArasaacId: Int? = null, iconImagePath: String? = null) =
        CategoryEntity(
            id = "food",
            name = "Comida",
            colorArgb = 0xFFF57C00.toInt(),
            position = 0,
            iconArasaacId = iconArasaacId,
            iconImagePath = iconImagePath,
        )

    @Test
    fun `a cached file that exists is preferred over the network`() {
        val file = temp.newFile("pan.png")
        assertEquals(file, picto(arasaacId = 2462, imagePath = file.path).keyboardImageModel())
    }

    @Test
    fun `a cached file that has vanished falls back to ARASAAC`() {
        // The case the stat exists for: the OS clearing app cache, or a restore
        // onto a new device, leaves the path in the database and nothing on disk.
        val gone = File(temp.root, "deleted.png").path
        assertEquals(ArasaacUrls.image(2462), picto(arasaacId = 2462, imagePath = gone).keyboardImageModel())
    }

    @Test
    fun `a vanished file with no ARASAAC id draws the placeholder`() {
        val gone = File(temp.root, "deleted.png").path
        assertNull(picto(imagePath = gone).keyboardImageModel())
    }

    @Test
    fun `an uncached ARASAAC picto loads from the network`() {
        assertEquals(ArasaacUrls.image(2462), picto(arasaacId = 2462).keyboardImageModel())
    }

    @Test
    fun `a picto with neither source has no model`() {
        assertNull(picto().keyboardImageModel())
    }

    @Test
    fun `a category icon follows the same order`() {
        val file = temp.newFile("comida.png")
        assertEquals(file, category(iconArasaacId = 2462, iconImagePath = file.path).keyboardIconModel())
        val gone = File(temp.root, "deleted.png").path
        assertEquals(
            ArasaacUrls.image(2462),
            category(iconArasaacId = 2462, iconImagePath = gone).keyboardIconModel(),
        )
    }

    @Test
    fun `a category with no picto is name-only`() {
        assertNull(category().keyboardIconModel())
    }
}
