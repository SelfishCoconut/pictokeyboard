package org.pictokeyboard.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.pictokeyboard.R

@Database(
    entities = [BoardEntity::class, CategoryEntity::class, PictoEntity::class, UsageEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun boardDao(): BoardDao
    abstract fun categoryDao(): CategoryDao
    abstract fun pictoDao(): PictoDao
    abstract fun usageDao(): UsageDao

    companion object {
        /** The last schema without boards, and the first one with them. */
        private const val VERSION_PRE_BOARDS = 3
        private const val VERSION_BOARDS = 4

        @Volatile
        private var instance: AppDatabase? = null

        /** v2 adds the usage table that powers the "Suggested" category. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `usage` (" +
                        "`id` TEXT NOT NULL, `arasaacId` INTEGER, `label` TEXT NOT NULL, " +
                        "`spokenText` TEXT NOT NULL, `language` TEXT NOT NULL, " +
                        "`count` INTEGER NOT NULL, `lastUsedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
            }
        }

        /** v3 adds category frame style/width and the per-picto colour override. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `categories` ADD COLUMN `borderStyle` TEXT NOT NULL DEFAULT 'solid'")
                db.execSQL("ALTER TABLE `categories` ADD COLUMN `borderWidthDp` INTEGER NOT NULL DEFAULT 3")
                db.execSQL("ALTER TABLE `pictos` ADD COLUMN `colorArgbOverride` INTEGER")
            }
        }

        /**
         * v4 introduces the board: every category now belongs to one.
         *
         * The categories table is recreated rather than altered because SQLite
         * cannot `ALTER TABLE ADD COLUMN` a NOT NULL column carrying a
         * REFERENCES clause — the only forms it allows there default to NULL,
         * and a nullable board id would make "which board is this category on"
         * unanswerable for exactly the rows the migration exists to fix.
         *
         * The create/copy/drop/rename dance is the documented recipe for this,
         * and it has one sharp edge: with foreign keys enforced, `DROP TABLE
         * categories` performs an implicit `DELETE FROM`, which fires
         * `pictos`' `ON DELETE CASCADE` and takes every pictogram in the
         * database with it. Room runs migrations with enforcement off, so it
         * does not happen — but nothing in this file would tell you that, which
         * is why `AppDatabaseMigrationTest` seeds pictos before migrating and
         * asserts they are all still there afterwards. That assertion is not
         * incidental coverage; it is the one guarding against silently wiping
         * every word a user has.
         */
        fun migration3To4(defaultBoardName: String) = object : Migration(VERSION_PRE_BOARDS, VERSION_BOARDS) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `boards` (" +
                        "`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`colorArgb` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                        "`active` INTEGER NOT NULL, " +
                        "`iconArasaacId` INTEGER, `iconImagePath` TEXT, " +
                        "`tags` TEXT NOT NULL DEFAULT '', " +
                        "`showInKeyboard` INTEGER NOT NULL DEFAULT 1, " +
                        "`columns` INTEGER NOT NULL DEFAULT 4, " +
                        "`rows` INTEGER NOT NULL DEFAULT 4, " +
                        "`showLabels` INTEGER NOT NULL DEFAULT 1, " +
                        "`borderStyle` TEXT NOT NULL DEFAULT 'solid', " +
                        "`borderWidthDp` INTEGER NOT NULL DEFAULT 3, " +
                        "`language` TEXT NOT NULL DEFAULT 'es', " +
                        "`source` TEXT, `sourceVersion` TEXT, `author` TEXT, `licence` TEXT, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_boards_position` ON `boards` (`position`)")

                // The one board every existing install lands on. Its layout is
                // left at the schema defaults here and adopted from the user's
                // real global settings on next start -- a Room migration cannot
                // read DataStore, which is where those values live. See
                // PictoRepository.adoptLegacyBoardLayout.
                db.execSQL(
                    "INSERT OR IGNORE INTO `boards` " +
                        "(`id`, `name`, `colorArgb`, `position`, `active`) VALUES " +
                        "('${BoardEntity.DEFAULT_ID}', ?, ?, 0, 1)",
                    arrayOf<Any>(defaultBoardName, BoardEntity.DEFAULT_COLOR_ARGB),
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `categories_new` (" +
                        "`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`boardId` TEXT NOT NULL DEFAULT '${BoardEntity.DEFAULT_ID}', " +
                        "`colorArgb` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                        "`builtin` INTEGER NOT NULL, " +
                        "`iconArasaacId` INTEGER, `iconImagePath` TEXT, " +
                        "`borderStyle` TEXT NOT NULL DEFAULT 'solid', " +
                        "`borderWidthDp` INTEGER NOT NULL DEFAULT 3, " +
                        "PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`boardId`) REFERENCES `boards`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL(
                    "INSERT INTO `categories_new` " +
                        "(`id`, `name`, `boardId`, `colorArgb`, `position`, `builtin`, " +
                        "`iconArasaacId`, `iconImagePath`, `borderStyle`, `borderWidthDp`) " +
                        "SELECT `id`, `name`, '${BoardEntity.DEFAULT_ID}', `colorArgb`, `position`, " +
                        "`builtin`, `iconArasaacId`, `iconImagePath`, `borderStyle`, `borderWidthDp` " +
                        "FROM `categories`",
                )
                db.execSQL("DROP TABLE `categories`")
                db.execSQL("ALTER TABLE `categories_new` RENAME TO `categories`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_position` ON `categories` (`position`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_boardId` ON `categories` (`boardId`)")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pictokeyboard.db",
                )
                    // Foreign keys must be enabled for cascade deletes to work.
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        // The migrated board is named after the app, which is a
                        // brand name and identical in every locale — so which
                        // context supplies it does not matter, and reading it
                        // here keeps the name in one place rather than
                        // hardcoding a second copy in SQL.
                        migration3To4(context.getString(R.string.app_name)),
                    )
                    .build()
                    .also { instance = it }
            }
    }
}
