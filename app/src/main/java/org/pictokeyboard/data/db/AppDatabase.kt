package org.pictokeyboard.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CategoryEntity::class, PictoEntity::class, UsageEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun pictoDao(): PictoDao
    abstract fun usageDao(): UsageDao

    companion object {
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

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pictokeyboard.db",
                )
                    // Foreign keys must be enabled for cascade deletes to work.
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
