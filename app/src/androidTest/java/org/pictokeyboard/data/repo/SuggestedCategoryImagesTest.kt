package org.pictokeyboard.data.repo

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.pictokeyboard.data.arasaac.ImageCache
import org.pictokeyboard.data.db.AppDatabase
import org.pictokeyboard.data.db.BoardEntity
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.data.db.PictoEntity
import java.io.File

/**
 * The Suggested category keeps its pictures (#153).
 *
 * The bug it pins: every pictogram made from a **photograph** came out blank,
 * because the new picto's image was built as `arasaacId?.let { download(it) }`
 * and a photograph has no ARASAAC id. ARASAAC symbols in the same category were
 * fine, which is what made it look like a copying failure rather than a branch
 * that was never taken.
 *
 * A caregiver reaches for Suggested precisely because it holds the words that
 * matter most to this person — and the words that matter most are the ones they
 * photographed: their kitchen, their bus stop, their grandmother. Those are the
 * exact pictures that disappeared.
 *
 * No network here. The ARASAAC case is given a file that already exists, so the
 * assertion is about which branch is taken rather than about a download.
 */
@RunWith(AndroidJUnit4::class)
class SuggestedCategoryImagesTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: PictoRepository
    private lateinit var photo: File
    private lateinit var symbol: File
    private lateinit var lost: File

    private companion object {
        const val BOARD = "board-home"
        const val CATEGORY = "category-food"
        const val SYMBOL_ID = 2462
    }

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE)
            .build()
        repo = PictoRepository(
            boardDao = db.boardDao(),
            categoryDao = db.categoryDao(),
            pictoDao = db.pictoDao(),
            usageDao = db.usageDao(),
            imageCache = ImageCache(context, OkHttpClient()),
        )
        // Real files, because the fix deliberately refuses to copy a path that
        // no longer resolves -- see `lost` below.
        photo = File.createTempFile("photo", ".png", context.cacheDir).apply { writeBytes(byteArrayOf(1)) }
        symbol = File.createTempFile("symbol", ".png", context.cacheDir).apply { writeBytes(byteArrayOf(2)) }
        lost = File(context.cacheDir, "deleted-${System.nanoTime()}.png")
        runBlocking { seed() }
    }

    @After
    fun tearDown() {
        db.close()
        photo.delete()
        symbol.delete()
    }

    /** One board, one category, three pictos: a photo, a symbol, and a ghost. */
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
                name = "Comida",
                colorArgb = 0xFFFF9800.toInt(),
                position = 0,
            ),
        )
        db.pictoDao().upsert(picto("picto-photo", "galleta", imagePath = photo.absolutePath))
        db.pictoDao().upsert(
            picto("picto-symbol", "agua", arasaacId = SYMBOL_ID, imagePath = symbol.absolutePath),
        )
        // Its file is gone -- app cache cleared, or a restore onto a new phone.
        db.pictoDao().upsert(picto("picto-lost", "abuela", imagePath = lost.absolutePath))
    }

    private fun picto(id: String, word: String, arasaacId: Int? = null, imagePath: String? = null) =
        PictoEntity(
            id = id,
            categoryId = CATEGORY,
            label = word,
            spokenText = word,
            language = "es",
            arasaacId = arasaacId,
            imagePath = imagePath,
            position = 0,
        )

    /** Uses each word, then accepts Suggested, and hands back what it built. */
    private suspend fun suggested(): Map<String, PictoEntity> {
        listOf("picto-photo", "picto-symbol", "picto-lost").forEach { id ->
            repo.recordUsage(db.pictoDao().getAll().first { it.id == id })
        }
        val category = repo.addSuggestedCategory("Sugeridos", 0xFF00897B.toInt(), repo.topUsed(10))
        return db.pictoDao().getByCategory(category.id).associateBy { it.spokenText }
    }

    @Test
    fun aPhotographKeepsItsPicture() = runBlocking {
        assertEquals(
            "the photo came through Suggested with no image, which draws as blank",
            photo.absolutePath,
            suggested()["galleta"]?.imagePath,
        )
    }

    @Test
    fun anArasaacSymbolKeepsTheFileAlreadyOnDisk() = runBlocking {
        // Re-downloading would also work, but only if there is a network. The
        // picture is already here, so it is used, and the keyboard shows
        // something on a phone that is offline.
        assertEquals(symbol.absolutePath, suggested()["agua"]?.imagePath)
    }

    @Test
    fun aPictureThatIsNoLongerOnDiskIsNotCarriedOver() = runBlocking {
        // Copying a dead path would hand the keyboard something that looks
        // resolvable and is not, costing the ARASAAC fallback in the one case
        // where there is one. Here there is no id either, so blank is the
        // honest answer -- but it must be blank rather than a broken path.
        assertNull(suggested()["abuela"]?.imagePath)
    }

    @Test
    fun everySuggestedWordIsStillThere() = runBlocking {
        assertEquals(setOf("galleta", "agua", "abuela"), suggested().keys)
    }
}
