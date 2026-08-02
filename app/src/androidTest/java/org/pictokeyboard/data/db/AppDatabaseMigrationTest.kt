package org.pictokeyboard.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The v3 → v4 migration, against a database with real content in it.
 *
 * A migration that loses user data is not a bug that shows up in review or in a
 * crash report — it shows up as a caregiver opening the app after an update and
 * finding the vocabulary they spent evenings building is gone. For this product
 * that is worse still: the board *is* someone's ability to speak.
 *
 * So this test does not check that the migration runs. It checks that
 * everything which was in the database before it is still there afterwards, in
 * the same order, with the same content.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private companion object {
        const val TEST_DB = "migration-test.db"
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    private val migration = AppDatabase.migration3To4("PictoKeyboard")

    /**
     * Seeds a v3 database with two categories and three pictos across them.
     *
     * Deliberately more than one of each and deliberately out of insertion
     * order, so an assertion about ordering has something to fail on.
     */
    private fun seedV3() {
        helper.createDatabase(TEST_DB, 3).use { db ->
            db.execSQL(
                "INSERT INTO categories " +
                    "(id, name, colorArgb, position, builtin, iconArasaacId, iconImagePath, " +
                    "borderStyle, borderWidthDp) VALUES " +
                    "('cat-food', 'Comida', ${0xFFFF9800.toInt()}, 1, 1, 4610, '/data/food.png', 'dashed', 5)",
            )
            db.execSQL(
                "INSERT INTO categories " +
                    "(id, name, colorArgb, position, builtin, iconArasaacId, iconImagePath, " +
                    "borderStyle, borderWidthDp) VALUES " +
                    "('cat-people', 'Personas', ${0xFFFFC107.toInt()}, 0, 1, 34560, NULL, 'solid', 3)",
            )
            db.execSQL(
                "INSERT INTO pictos (id, categoryId, label, spokenText, language, arasaacId, " +
                    "imagePath, position, colorArgbOverride) VALUES " +
                    "('pic-yo', 'cat-people', 'yo', 'yo', 'es', 6632, '/data/yo.png', 0, NULL)",
            )
            db.execSQL(
                "INSERT INTO pictos (id, categoryId, label, spokenText, language, arasaacId, " +
                    "imagePath, position, colorArgbOverride) VALUES " +
                    "('pic-mama', 'cat-people', 'mamá', 'mamá', 'es', 2458, NULL, 1, ${0xFF00897B.toInt()})",
            )
            db.execSQL(
                "INSERT INTO pictos (id, categoryId, label, spokenText, language, arasaacId, " +
                    "imagePath, position, colorArgbOverride) VALUES " +
                    "('pic-agua', 'cat-food', 'agua', 'agua', 'es', 2349, NULL, 0, NULL)",
            )
        }
    }

    @Test
    fun migratesToV4AndKeepsEveryCategoryInOrder() {
        seedV3()

        helper.runMigrationsAndValidate(TEST_DB, 4, true, migration).use { db ->
            db.query("SELECT id, name, position, boardId FROM categories ORDER BY position ASC").use { c ->
                assertEquals("category count", 2, c.count)

                assertTrue(c.moveToFirst())
                assertEquals("cat-people", c.getString(0))
                assertEquals("Personas", c.getString(1))
                assertEquals(0, c.getInt(2))
                assertEquals(BoardEntity.DEFAULT_ID, c.getString(3))

                assertTrue(c.moveToNext())
                assertEquals("cat-food", c.getString(0))
                assertEquals("Comida", c.getString(1))
                assertEquals(1, c.getInt(2))
                assertEquals(BoardEntity.DEFAULT_ID, c.getString(3))
            }
        }
    }

    /**
     * The assertion this file exists for.
     *
     * The migration recreates `categories`, which means dropping it. With
     * foreign keys enforced, `DROP TABLE` performs an implicit `DELETE FROM`
     * that fires `pictos`' `ON DELETE CASCADE` — silently deleting every
     * pictogram in the database while the migration reports success. Room runs
     * migrations with enforcement off so it does not happen, but that is a
     * property of the framework rather than of this code, and it is exactly the
     * kind of thing a version bump could change underneath us.
     */
    @Test
    fun migrationDoesNotCascadeAwayThePictos() {
        seedV3()

        helper.runMigrationsAndValidate(TEST_DB, 4, true, migration).use { db ->
            db.query("SELECT COUNT(*) FROM pictos").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("every picto survives the migration", 3, c.getInt(0))
            }
            db.query(
                "SELECT label, spokenText, language, arasaacId, imagePath, position, colorArgbOverride " +
                    "FROM pictos WHERE id = 'pic-mama'",
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("mamá", c.getString(0))
                assertEquals("mamá", c.getString(1))
                assertEquals("es", c.getString(2))
                assertEquals(2458, c.getInt(3))
                assertTrue("imagePath stays null", c.isNull(4))
                assertEquals(1, c.getInt(5))
                assertEquals(0xFF00897B.toInt(), c.getInt(6))
            }
        }
    }

    @Test
    fun seedsExactlyOneActiveBoardEveryCategoryPointsAt() {
        seedV3()

        helper.runMigrationsAndValidate(TEST_DB, 4, true, migration).use { db ->
            db.query("SELECT id, name, active, position FROM boards").use { c ->
                assertEquals("exactly one board", 1, c.count)
                assertTrue(c.moveToFirst())
                assertEquals(BoardEntity.DEFAULT_ID, c.getString(0))
                assertEquals("PictoKeyboard", c.getString(1))
                assertEquals("the board is active", 1, c.getInt(2))
                assertEquals(0, c.getInt(3))
            }
            db.query("SELECT COUNT(*) FROM categories WHERE boardId <> '${BoardEntity.DEFAULT_ID}'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("no category is left off the board", 0, c.getInt(0))
            }
        }
    }

    /**
     * Per-category frame style and thickness are user choices from #15, and the
     * migration copies them column by column — the kind of thing that is easy
     * to drop when a table is recreated by hand.
     */
    @Test
    fun keepsPerCategoryFrameStyling() {
        seedV3()

        helper.runMigrationsAndValidate(TEST_DB, 4, true, migration).use { db ->
            db.query(
                "SELECT borderStyle, borderWidthDp, builtin, iconArasaacId, iconImagePath " +
                    "FROM categories WHERE id = 'cat-food'",
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("dashed", c.getString(0))
                assertEquals(5, c.getInt(1))
                assertEquals(1, c.getInt(2))
                assertEquals(4610, c.getInt(3))
                assertEquals("/data/food.png", c.getString(4))
            }
        }
    }

    /**
     * Deleting a board has to take its categories, and their pictos, with it —
     * the cascade the new foreign key exists for. Asserted after the migration
     * rather than on a freshly built database, because a recreated table is
     * where a foreign key most easily goes missing.
     */
    @Test
    fun deletingABoardCascadesToCategoriesAndPictos() {
        seedV3()

        helper.runMigrationsAndValidate(TEST_DB, 4, true, migration).use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("DELETE FROM boards WHERE id = '${BoardEntity.DEFAULT_ID}'")

            db.query("SELECT COUNT(*) FROM categories").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("categories cascade with the board", 0, c.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM pictos").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("pictos cascade with their categories", 0, c.getInt(0))
            }
        }
    }
}
