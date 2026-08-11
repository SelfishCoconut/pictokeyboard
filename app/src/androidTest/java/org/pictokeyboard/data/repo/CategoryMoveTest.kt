package org.pictokeyboard.data.repo

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.pictokeyboard.data.arasaac.ImageCache
import org.pictokeyboard.data.db.AppDatabase
import org.pictokeyboard.data.db.BoardEntity
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.data.db.PictoEntity

/**
 * Moving a category from one board to another, and putting it back (#119).
 *
 * The assertion that matters here is not that `boardId` changed — it is that the
 * pictograms are still there afterwards. A category is a container for somebody's
 * words, and the obvious implementation of this feature (upsert with REPLACE)
 * deletes and re-inserts the row, which cascades and takes every picto in it. The
 * caregiver would see the category arrive on the other board, empty, with no
 * error and nothing to undo.
 *
 * So: move it, and count the words.
 */
@RunWith(AndroidJUnit4::class)
class CategoryMoveTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: PictoRepository

    private companion object {
        const val HOME = "board-home"
        const val SCHOOL = "board-school"
        const val FOOD = "category-food"
        const val PICTO_COUNT = 3

        /** Where the category sits on the source board — not 0, so a restore has something to get wrong. */
        const val ORIGINAL_POSITION = 1
    }

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            // The categories table has a foreign key onto boards, and this test
            // is entirely about that column.
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE)
            .build()
        repo = PictoRepository(
            boardDao = db.boardDao(),
            categoryDao = db.categoryDao(),
            pictoDao = db.pictoDao(),
            usageDao = db.usageDao(),
            imageCache = ImageCache(context, OkHttpClient()),
        )
        runBlocking { seed() }
    }

    @After
    fun tearDown() = db.close()

    /** Two boards; one category with words in it, and a neighbour to sit beside. */
    private suspend fun seed() {
        db.boardDao().upsert(board(HOME, "Casa", position = 0))
        db.boardDao().upsert(board(SCHOOL, "Colegio", position = 1))
        db.categoryDao().upsert(category("category-people", HOME, "Personas", position = 0))
        db.categoryDao().upsert(category(FOOD, HOME, "Comida", position = ORIGINAL_POSITION))
        // A category already on the destination, so the moved one has to land
        // *after* it rather than on top of it.
        db.categoryDao().upsert(category("category-lessons", SCHOOL, "Clases", position = 0))
        repeat(PICTO_COUNT) { index ->
            db.pictoDao().upsert(
                PictoEntity(
                    id = "picto-$index",
                    categoryId = FOOD,
                    label = "palabra $index",
                    spokenText = "palabra $index",
                    language = "es",
                    position = index,
                ),
            )
        }
    }

    private fun board(id: String, name: String, position: Int) = BoardEntity(
        id = id,
        name = name,
        colorArgb = BoardEntity.DEFAULT_COLOR_ARGB,
        position = position,
        active = position == 0,
    )

    private fun category(id: String, boardId: String, name: String, position: Int) = CategoryEntity(
        id = id,
        boardId = boardId,
        name = name,
        colorArgb = 0xFFFF9800.toInt(),
        position = position,
    )

    @Test
    fun movingACategoryTakesItsPictogramsWithIt() = runBlocking {
        repo.moveCategoryToBoard(db.categoryDao().getById(FOOD)!!, SCHOOL)

        assertEquals(SCHOOL, db.categoryDao().getById(FOOD)!!.boardId)
        assertEquals(
            "the category arrived without the words in it",
            PICTO_COUNT,
            db.pictoDao().getByCategory(FOOD).size,
        )
    }

    @Test
    fun aMovedCategoryLandsAfterWhatIsAlreadyThere() = runBlocking {
        repo.moveCategoryToBoard(db.categoryDao().getById(FOOD)!!, SCHOOL)

        val destination = db.categoryDao().getByBoard(SCHOOL)
        assertEquals(listOf("Clases", "Comida"), destination.map { it.name })
    }

    @Test
    fun theSourceBoardKeepsEverythingElse() = runBlocking {
        repo.moveCategoryToBoard(db.categoryDao().getById(FOOD)!!, SCHOOL)

        assertEquals(listOf("Personas"), db.categoryDao().getByBoard(HOME).map { it.name })
    }

    /**
     * Undo is the whole safety net for an action offered with no confirmation in
     * front of it, so "back where it was" has to mean the position too — not just
     * the right board with the row shuffled to the end of it.
     */
    @Test
    fun undoPutsTheCategoryBackWhereItWas() = runBlocking {
        val previous = repo.moveCategoryToBoard(db.categoryDao().getById(FOOD)!!, SCHOOL)
        repo.restoreCategory(previous)

        val restored = db.categoryDao().getById(FOOD)!!
        assertEquals(HOME, restored.boardId)
        assertEquals(ORIGINAL_POSITION, restored.position)
        assertEquals(PICTO_COUNT, db.pictoDao().getByCategory(FOOD).size)
    }
}
