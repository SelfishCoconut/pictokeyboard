package org.pictokeyboard.data.pkb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.pictokeyboard.data.db.BoardEntity
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.data.db.PictoEntity
import org.pictokeyboard.data.prefs.Settings

/**
 * Turning the device's rows into a document and back.
 *
 * The two rules this has to keep are both about not destroying work a caregiver
 * spent months on: an import adds, and an export leaves the credential behind.
 */
class PkbMappingTest {

    private val board = BoardEntity(
        id = "board-1",
        name = "Casa",
        colorArgb = -14405057,
        position = 0,
        active = true,
        iconArasaacId = 2248,
        columns = 5,
        rows = 3,
        language = "es",
    )

    private val category = CategoryEntity(
        id = "cat-1",
        name = "Personas",
        boardId = "board-1",
        colorArgb = -65536,
        position = 0,
        iconImagePath = "/data/pictos/custom_a.png",
    )

    private val picto = PictoEntity(
        id = "picto-1",
        categoryId = "cat-1",
        label = "mamá",
        spokenText = "mamá",
        language = "es",
        imagePath = "/data/pictos/custom_b.png",
        position = 0,
    )

    private val digests = mapOf(
        "/data/pictos/custom_a.png" to "a".repeat(64),
        "/data/pictos/custom_b.png" to "b".repeat(64),
    )

    @Test
    fun importingTheSameFileTwiceYieldsTwoBoards() {
        val document = PkbMapping.toDocument(
            boards = listOf(board),
            categories = listOf(category),
            pictos = listOf(picto),
            settings = null,
            digestOf = digests::get,
        )

        val first = PkbMapping.toEntities(document, ids(), mediaPath = { null })
        val second = PkbMapping.toEntities(document, ids(), mediaPath = { null })

        assertNotEquals(first.boards.single().id, second.boards.single().id)
        assertEquals(
            "a category must follow its own board in, not the other import's",
            second.boards.single().id,
            second.categories.single().boardId,
        )
        assertEquals(
            second.categories.single().id,
            second.pictos.single().categoryId,
        )
    }

    @Test
    fun anImportedBoardIsNeverTheActiveOne() {
        val document = PkbMapping.toDocument(listOf(board), listOf(category), listOf(picto), null, digests::get)

        val imported = PkbMapping.toEntities(document, ids(), mediaPath = { null })

        assertFalse(
            "importing must not silently change which board the keyboard is showing",
            imported.boards.single().active,
        )
    }

    @Test
    fun theFileLeavesThePinBehind() {
        val document = PkbMapping.toDocument(
            boards = listOf(board),
            categories = emptyList(),
            pictos = emptyList(),
            settings = Settings(hasPin = true, ttsRate = 1.4f),
            digestOf = digests::get,
        )

        val imported = PkbMapping.toEntities(document, ids(), mediaPath = { null })

        assertEquals("the settings that describe the person do travel", 1.4f, imported.settings!!.ttsRate, 0f)
        assertFalse(
            "a device that believes a PIN is set, holding no hash to check it against, is locked out",
            imported.settings.hasPin,
        )
    }

    @Test
    fun aPictoWhosePhotoDidNotArriveKeepsItsLabel() {
        val document = PkbMapping.toDocument(listOf(board), listOf(category), listOf(picto), null, digests::get)

        val imported = PkbMapping.toEntities(document, ids(), mediaPath = { null })

        assertNull(imported.pictos.single().imagePath)
        assertEquals("mamá", imported.pictos.single().label)
    }

    @Test
    fun aPhotoThatDidArriveIsPointedAtItsNewFile() {
        val document = PkbMapping.toDocument(listOf(board), listOf(category), listOf(picto), null, digests::get)

        val imported = PkbMapping.toEntities(
            document,
            ids(),
            mediaPath = { digest -> "/new/cache/$digest.png" },
        )

        assertEquals("/new/cache/${"b".repeat(64)}.png", imported.pictos.single().imagePath)
        assertEquals("/new/cache/${"a".repeat(64)}.png", imported.categories.single().iconImagePath)
    }

    @Test
    fun theLayoutTheAuthorIntendedSurvivesTheRoundTrip() {
        val document = PkbMapping.toDocument(listOf(board), listOf(category), listOf(picto), null, digests::get)

        val imported = PkbMapping.toEntities(document, ids(), mediaPath = { null }).boards.single()

        assertEquals(5, imported.columns)
        assertEquals(3, imported.rows)
        assertEquals("Casa", imported.name)
        assertEquals(2248, imported.iconArasaacId)
    }

    /** Ids a test can tell apart, standing in for `UUID.randomUUID()`. */
    private fun ids(): () -> String {
        var next = 0
        val run = counter++
        return { "id-$run-${next++}" }
    }

    private companion object {
        var counter = 0
    }
}
