package org.pictokeyboard.data.pkb

import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
import java.io.File

/**
 * A photograph survives the trip out and back (#119).
 *
 * This is the case the format exists for. A caregiver who photographs their own
 * kitchen, their own bus stop, the actual face of the actual grandmother has
 * built something no catalogue can replace, and those pictures live as files in
 * `filesDir/pictos` with only a path in the database pointing at them. Send the
 * rows without the bytes and the board arrives as a grid of captions.
 *
 * So the assertion is not that a path came back — a path that points at nothing
 * would satisfy that. It is that the file is **there on the receiving device**
 * and holds **the same bytes**, and that the path was rewritten to it rather
 * than left pointing at the sending phone's private storage.
 */
@RunWith(AndroidJUnit4::class)
class CustomImageRoundTripTest {

    private lateinit var db: AppDatabase
    private lateinit var backup: PkbBackup
    private lateinit var imageCache: ImageCache

    private lateinit var photo: File
    private lateinit var photoBytes: ByteArray

    private companion object {
        const val BOARD = "board-home"
        const val CATEGORY = "category-people"
        const val PICTO = "picto-abuela"
        const val LABEL = "abuela"
        const val SIZE = 24
    }

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE)
            .build()
        imageCache = ImageCache(context, OkHttpClient())
        backup = PkbBackup(
            db = db,
            settingsStore = SettingsStore(context),
            imageCache = imageCache,
            appVersion = "test",
        )
        photo = writeAPhotograph()
        photoBytes = photo.readBytes()
        runBlocking { seed() }
    }

    @After
    fun tearDown() {
        db.close()
        photo.delete()
        // The imported copies are content-addressed, so they would otherwise
        // survive into the next test's cache and make it pass for free.
        imageCache.fileForImported(Sha256.hex(photoBytes)).delete()
    }

    /** A real PNG rather than arbitrary bytes: this is what the app actually stores. */
    private fun writeAPhotograph(): File {
        val file = imageCache.fileForCustom("roundtrip-source")
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(0x1A, 0x56, 0xA8))
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    private suspend fun seed() {
        db.boardDao().upsert(
            BoardEntity(
                id = BOARD,
                name = "Casa",
                colorArgb = BoardEntity.DEFAULT_COLOR_ARGB,
                position = 0,
                active = true,
            ),
        )
        db.categoryDao().upsert(
            CategoryEntity(
                id = CATEGORY,
                boardId = BOARD,
                name = "Personas",
                colorArgb = 0xFFFF9800.toInt(),
                position = 0,
            ),
        )
        db.pictoDao().upsert(
            PictoEntity(
                id = PICTO,
                categoryId = CATEGORY,
                label = LABEL,
                spokenText = LABEL,
                language = "es",
                position = 0,
                // No arasaacId: this symbol exists only as a file on this phone.
                imagePath = photo.absolutePath,
            ),
        )
    }

    private fun export(boardId: String?): ByteArray {
        val bytes = ByteArrayOutputStream()
        runBlocking { backup.exportTo(bytes, boardId) }.getOrThrow()
        return bytes.toByteArray()
    }

    /** The picto that came back in, on whichever board the import created. */
    private suspend fun importedPicto(packed: ByteArray): PictoEntity {
        val summary = runBlocking { backup.importFrom { packed.inputStream() } }.getOrThrow()
        assertEquals("the photograph was not in the archive", 1, summary.media)

        val arrivedBoard = db.boardDao().getAll().single { it.id != BOARD }
        val arrivedCategory = db.categoryDao().getByBoard(arrivedBoard.id).single()
        return db.pictoDao().getByCategory(arrivedCategory.id).single()
    }

    /** The arrived picture as a file, failing with a useful sentence if there isn't one. */
    private fun pictureOf(picto: PictoEntity): File {
        val path = picto.imagePath
        assertNotNull("the picto came back with no picture at all", path)
        val file = File(requireNotNull(path))
        assertTrue("the path points at a file that is not there: $path", file.isFile)
        return file
    }

    @Test
    fun aPhotographOnOneBoardArrivesWithTheBoard() = runBlocking {
        val arrived = importedPicto(export(BOARD))

        assertEquals(LABEL, arrived.label)
        assertArrayEquals(
            "the picture that arrived is not the one that was sent",
            photoBytes,
            pictureOf(arrived).readBytes(),
        )
    }

    /**
     * And it is a copy on this device, not the sending phone's path. The two
     * differ because the imported file is named for the digest of its own bytes,
     * which is what lets two boards share one photograph without a second copy.
     */
    @Test
    fun theArrivedPictoPointsAtThisDevicesCopy() = runBlocking {
        val arrived = importedPicto(export(BOARD))
        val picture = pictureOf(arrived)

        assertNotEquals(
            "the imported row still points at the file the sender's copy lived in",
            photo.absolutePath,
            arrived.imagePath,
        )
        assertTrue(
            "an imported photograph should be stored under its own digest, was ${picture.name}",
            picture.name.startsWith("pkb_"),
        )
    }

    /** The whole-device backup is the same archive, so it carries them too. */
    @Test
    fun aWholeDeviceBackupCarriesPhotographsAsWell() = runBlocking {
        val arrived = importedPicto(export(null))

        assertArrayEquals(photoBytes, pictureOf(arrived).readBytes())
    }
}
