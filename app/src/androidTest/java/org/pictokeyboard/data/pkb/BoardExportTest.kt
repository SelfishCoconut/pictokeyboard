package org.pictokeyboard.data.pkb

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.pictokeyboard.data.arasaac.ImageCache
import org.pictokeyboard.data.db.AppDatabase
import org.pictokeyboard.data.db.BoardEntity
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.data.db.PictoEntity
import org.pictokeyboard.data.prefs.SettingsStore
import java.io.ByteArrayOutputStream

/**
 * Exporting one board rather than the whole device (#119).
 *
 * Two claims are worth a test. The file holds that board and nothing from any
 * other one — a caregiver sending "school" to a colleague must not also be
 * sending the board of private words they keep for home. And the voice settings
 * stay behind, because handing somebody a board is not licence to reach into
 * their device and reset how their user's voice sounds.
 */
@RunWith(AndroidJUnit4::class)
class BoardExportTest {

    private lateinit var db: AppDatabase
    private lateinit var backup: PkbBackup

    private companion object {
        const val HOME = "board-home"
        const val SCHOOL = "board-school"
    }

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE)
            .build()
        backup = PkbBackup(
            db = db,
            settingsStore = SettingsStore(context),
            imageCache = ImageCache(context, OkHttpClient()),
            appVersion = "test",
        )
        runBlocking { seed() }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seed() {
        listOf(HOME to "Casa", SCHOOL to "Colegio").forEachIndexed { index, (id, name) ->
            db.boardDao().upsert(
                BoardEntity(
                    id = id,
                    name = name,
                    colorArgb = BoardEntity.DEFAULT_COLOR_ARGB,
                    position = index,
                    active = index == 0,
                ),
            )
            db.categoryDao().upsert(
                CategoryEntity(
                    id = "category-$id",
                    boardId = id,
                    name = "Categoría de $name",
                    colorArgb = 0xFFFF9800.toInt(),
                    position = 0,
                ),
            )
            db.pictoDao().upsert(
                PictoEntity(
                    id = "picto-$id",
                    categoryId = "category-$id",
                    label = "palabra de $name",
                    spokenText = "palabra de $name",
                    language = "es",
                    position = 0,
                ),
            )
        }
    }

    private fun exported(boardId: String?): PkbDocument {
        val bytes = ByteArrayOutputStream()
        runBlocking { backup.exportTo(bytes, boardId) }.getOrThrow()
        val packed = bytes.toByteArray()
        return PkbArchive.read({ packed.inputStream() }, { _, _ -> }).getOrThrow()
    }

    @Test
    fun oneBoardExportsOnlyThatBoard() {
        val document = exported(SCHOOL)

        assertEquals(listOf("Colegio"), document.boards.map { it.name })
        assertEquals(
            "a word from another board reached the file",
            listOf("palabra de Colegio"),
            document.boards.flatMap { board -> board.categories.flatMap { it.pictos } }.map { it.label },
        )
    }

    /** The other direction, so the filter cannot pass by exporting nothing. */
    @Test
    fun theWholeDeviceStillExportsEveryBoard() {
        val document = exported(null)

        assertEquals(setOf("Casa", "Colegio"), document.boards.map { it.name }.toSet())
    }

    @Test
    fun oneBoardCarriesNoVoiceSettings() {
        assertNull(
            "a shared board must not reset the receiving caregiver's voice settings",
            exported(SCHOOL).settings,
        )
    }

    @Test
    fun theWholeDeviceBackupDoesCarryVoiceSettings() {
        assertNotNull(
            "a backup restoring onto a new phone should bring the voice with it",
            exported(null).settings,
        )
    }
}
