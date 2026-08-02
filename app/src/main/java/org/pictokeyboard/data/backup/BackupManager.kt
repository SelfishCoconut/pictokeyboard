package org.pictokeyboard.data.backup

import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.pictokeyboard.data.arasaac.ImageCache
import org.pictokeyboard.data.db.CategoryDao
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.data.db.PictoDao
import org.pictokeyboard.data.db.PictoEntity

/**
 * Exports and imports one board as JSON. On import, ARASAAC images are
 * re-downloaded from their ids so a board can move between devices offline of
 * the original image cache. (Custom uploaded images are not yet embedded.)
 *
 * Everything here is scoped to a single board. It was global until #31 gave
 * categories an owner, which was indistinguishable from correct while exactly
 * one board existed -- and would have exported every board into a file
 * describing one, then wiped all of them on the next import.
 *
 * The richer pack format, with a name, an author and a licence, is #39.
 */
class BackupManager(
    private val categoryDao: CategoryDao,
    private val pictoDao: PictoDao,
    private val imageCache: ImageCache,
    moshi: Moshi,
) {
    private val adapter = moshi.adapter(BackupDto::class.java).indent("  ")

    suspend fun export(language: String, boardId: String): String = withContext(Dispatchers.IO) {
        val boardCategories = categoryDao.getByBoard(boardId)
        val categoryIds = boardCategories.map { it.id }.toSet()
        val categories = boardCategories.map {
            BackupCategory(
                it.id,
                it.name,
                it.colorArgb,
                it.position,
                it.builtin,
                it.iconArasaacId,
                it.borderStyle,
                it.borderWidthDp,
            )
        }
        val pictos = pictoDao.getAll().filter { it.categoryId in categoryIds }.map {
            BackupPicto(
                it.id,
                it.categoryId,
                it.label,
                it.spokenText,
                it.language,
                it.arasaacId,
                it.position,
                it.colorArgbOverride,
            )
        }
        adapter.toJson(BackupDto(1, language, categories, pictos))
    }

    /**
     * Replaces the contents of [boardId] with the imported board. Returns the
     * parsed language.
     *
     * Importing *onto* a board rather than creating one is deliberate for now:
     * it is the behaviour the settings screen has always had. #39 adds import
     * as a new board, which is what a pack shared by another caregiver wants.
     */
    suspend fun import(json: String, boardId: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val dto = adapter.fromJson(json) ?: error("Invalid backup file")

            categoryDao.clearBoard(boardId)

            categoryDao.upsertAll(
                dto.categories.map {
                    CategoryEntity(
                        id = it.id,
                        boardId = boardId,
                        name = it.name,
                        colorArgb = it.colorArgb,
                        position = it.position,
                        builtin = it.builtin,
                        iconArasaacId = it.iconArasaacId,
                        iconImagePath = it.iconArasaacId?.let { id -> imageCache.downloadArasaac(id) },
                        borderStyle = it.borderStyle,
                        borderWidthDp = it.borderWidthDp,
                    )
                },
            )
            pictoDao.upsertAll(
                dto.pictos.map {
                    PictoEntity(
                        id = it.id,
                        categoryId = it.categoryId,
                        label = it.label,
                        spokenText = it.spokenText,
                        language = it.language,
                        arasaacId = it.arasaacId,
                        imagePath = it.arasaacId?.let { id -> imageCache.downloadArasaac(id) },
                        position = it.position,
                        colorArgbOverride = it.colorArgbOverride,
                    )
                },
            )
            dto.language
        }
    }
}
