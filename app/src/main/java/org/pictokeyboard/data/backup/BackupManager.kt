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
 * Exports/imports the whole board as JSON. On import, ARASAAC images are
 * re-downloaded from their ids so a board can move between devices offline of
 * the original image cache. (Custom uploaded images are not yet embedded.)
 */
class BackupManager(
    private val categoryDao: CategoryDao,
    private val pictoDao: PictoDao,
    private val imageCache: ImageCache,
    moshi: Moshi,
) {
    private val adapter = moshi.adapter(BackupDto::class.java).indent("  ")

    suspend fun export(language: String): String = withContext(Dispatchers.IO) {
        val categories = categoryDao.getAll().map {
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
        val pictos = pictoDao.getAll().map {
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

    /** Replaces the current board with the imported one. Returns parsed language. */
    suspend fun import(json: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val dto = adapter.fromJson(json) ?: error("Invalid backup file")

            pictoDao.clear()
            categoryDao.clear()

            categoryDao.upsertAll(
                dto.categories.map {
                    CategoryEntity(
                        id = it.id,
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
