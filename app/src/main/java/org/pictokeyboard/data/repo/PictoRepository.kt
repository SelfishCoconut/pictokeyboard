package org.pictokeyboard.data.repo

import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import org.pictokeyboard.data.arasaac.ArasaacOptions
import org.pictokeyboard.data.arasaac.ArasaacResult
import org.pictokeyboard.data.arasaac.ImageCache
import org.pictokeyboard.data.db.BorderStyles
import org.pictokeyboard.data.db.CategoryDao
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.data.db.PictoDao
import org.pictokeyboard.data.db.PictoEntity
import org.pictokeyboard.data.db.UsageDao
import org.pictokeyboard.data.db.UsageEntity
import org.pictokeyboard.data.seed.CategoryTemplate
import org.pictokeyboard.data.seed.DefaultData
import java.util.UUID

/**
 * Single source of truth for categories and pictos. Handles seeding default
 * categories on first launch and downloading ARASAAC images into the cache so
 * the keyboard works offline.
 */
class PictoRepository(
    private val categoryDao: CategoryDao,
    private val pictoDao: PictoDao,
    private val usageDao: UsageDao,
    private val imageCache: ImageCache,
) {
    fun observeCategories(): Flow<List<CategoryEntity>> = categoryDao.observeAll()

    fun observePictos(categoryId: String): Flow<List<PictoEntity>> =
        pictoDao.observeByCategory(categoryId)

    /** Live total number of pictograms across all categories (for the dashboard). */
    fun observePictoCount(): Flow<Int> = pictoDao.observeCount()

    suspend fun categories(): List<CategoryEntity> = categoryDao.getAll()

    suspend fun pictos(categoryId: String): List<PictoEntity> =
        pictoDao.getByCategory(categoryId)

    /**
     * Seeds the default ARASAAC-style categories and a starter set of pictos.
     *
     * Rows are inserted up front with no image path so seeding can't be left
     * half-done by a cancelled download: until the local copy is cached the board
     * renders straight from the ARASAAC URL (via the picto's arasaacId). The
     * pictos are seeded whenever the table is empty, which also recovers installs
     * whose first seeding was interrupted after the categories but before the
     * pictos. Both ids are stable, so this is idempotent. Image caching then runs
     * separately and resumes across launches.
     */
    suspend fun seedIfEmpty(language: String) {
        // Categories must exist before pictos (foreign key).
        if (categoryDao.count() == 0) {
            categoryDao.upsertAll(DefaultData.categories(language))
        }
        if (pictoDao.getAll().isEmpty()) {
            pictoDao.upsertAll(DefaultData.pictos(language))
        }
        warmImageCache()
    }

    /**
     * Downloads any still-missing ARASAAC images for seeded rows and fills in
     * their local paths, so the board works offline once warmed. Idempotent and
     * resumable: rows that already have a cached image are skipped, so an
     * interrupted run simply continues on the next launch.
     */
    private suspend fun warmImageCache() = coroutineScope {
        categoryDao.getAll()
            .filter { it.iconArasaacId != null && it.iconImagePath == null }
            .map { c ->
                async {
                    imageCache.downloadArasaac(c.iconArasaacId!!)?.let { path ->
                        categoryDao.update(c.copy(iconImagePath = path))
                    }
                }
            }
            .awaitAll()
        pictoDao.getAll()
            .filter { it.arasaacId != null && it.imagePath == null }
            .map { p ->
                async {
                    imageCache.downloadArasaac(p.arasaacId!!)?.let { path ->
                        pictoDao.update(p.copy(imagePath = path))
                    }
                }
            }
            .awaitAll()
    }

    // --- Categories --------------------------------------------------------

    suspend fun addCategory(
        name: String,
        colorArgb: Int,
        borderStyle: String = BorderStyles.SOLID,
        borderWidthDp: Int = BorderStyles.DEFAULT_WIDTH_DP,
    ): CategoryEntity {
        val cat = CategoryEntity(
            id = "cat-" + UUID.randomUUID(),
            name = name,
            colorArgb = colorArgb,
            position = categoryDao.maxPosition() + 1,
            builtin = false,
            borderStyle = borderStyle,
            borderWidthDp = borderWidthDp,
        )
        categoryDao.upsert(cat)
        return cat
    }

    /**
     * Creates a new custom category pre-filled from a [template] (name, colour,
     * icon and a starter set of pictos), downloading all images. The admin can
     * then rename, recolour, reorder, add to or trim it like any other category.
     */
    suspend fun addCategoryFromTemplate(template: CategoryTemplate, language: String): CategoryEntity =
        coroutineScope {
            val cat = CategoryEntity(
                id = "cat-" + UUID.randomUUID(),
                name = template.name(language),
                colorArgb = template.color.toInt(),
                position = categoryDao.maxPosition() + 1,
                builtin = false,
                iconArasaacId = template.iconArasaacId,
                iconImagePath = imageCache.downloadArasaac(template.iconArasaacId),
            )
            categoryDao.upsert(cat)
            val pictos = template.pictos.mapIndexed { i, tp ->
                async {
                    val word = if (language == "en") tp.en else tp.es
                    PictoEntity(
                        id = "pic-" + UUID.randomUUID(),
                        categoryId = cat.id,
                        label = word,
                        spokenText = word,
                        language = language,
                        arasaacId = tp.arasaacId,
                        imagePath = imageCache.downloadArasaac(tp.arasaacId),
                        position = i,
                    )
                }
            }
            pictoDao.upsertAll(pictos.awaitAll())
            cat
        }

    suspend fun updateCategory(category: CategoryEntity) = categoryDao.update(category)

    suspend fun deleteCategory(category: CategoryEntity) = categoryDao.delete(category)

    suspend fun reorderCategories(ordered: List<CategoryEntity>) {
        // Must be an in-place UPDATE: an upsert with REPLACE would delete and
        // re-insert each category row, cascade-deleting all of its pictos.
        categoryDao.updateAll(ordered.mapIndexed { i, c -> c.copy(position = i) })
    }

    // --- Pictos ------------------------------------------------------------

    /**
     * Adds an ARASAAC pictogram to a category, downloading & caching its image.
     * [options] applies ARASAAC customization (skin tone, hair colour, colour /
     * black-and-white).
     */
    suspend fun addArasaacPicto(
        categoryId: String,
        result: ArasaacResult,
        spokenText: String,
        label: String,
        language: String,
        options: ArasaacOptions = ArasaacOptions(),
        colorArgbOverride: Int? = null,
    ): PictoEntity {
        val path = imageCache.downloadArasaac(result.id, options)
        val picto = PictoEntity(
            id = "pic-" + UUID.randomUUID(),
            categoryId = categoryId,
            label = label,
            spokenText = spokenText,
            language = language,
            arasaacId = result.id,
            imagePath = path,
            position = pictoDao.maxPosition(categoryId) + 1,
            colorArgbOverride = colorArgbOverride,
        )
        pictoDao.upsert(picto)
        return picto
    }

    /** Adds a picto from a user-imported image file (no ARASAAC id). */
    suspend fun addCustomPicto(
        categoryId: String,
        imageUri: Uri,
        spokenText: String,
        label: String,
        language: String,
        colorArgbOverride: Int? = null,
    ): PictoEntity? {
        val path = imageCache.importFromUri(imageUri) ?: return null
        return addImagePicto(categoryId, path, spokenText, label, language, colorArgbOverride)
    }

    /** Decodes [imageUri] into a bitmap for cropping. */
    suspend fun loadImage(imageUri: Uri): Bitmap? = imageCache.loadDownsampled(imageUri)

    /** Saves [bitmap] (a cropped import) to the cache and adds it as a picto. */
    suspend fun addBitmapPicto(
        categoryId: String,
        bitmap: Bitmap,
        spokenText: String,
        label: String,
        language: String,
        colorArgbOverride: Int? = null,
    ): PictoEntity? {
        val path = imageCache.saveBitmap(bitmap) ?: return null
        return addImagePicto(categoryId, path, spokenText, label, language, colorArgbOverride)
    }

    /** Adds a picto from an image already saved in the cache (e.g. a cropped import). */
    suspend fun addImagePicto(
        categoryId: String,
        imagePath: String,
        spokenText: String,
        label: String,
        language: String,
        colorArgbOverride: Int? = null,
    ): PictoEntity {
        val picto = PictoEntity(
            id = "pic-" + UUID.randomUUID(),
            categoryId = categoryId,
            label = label,
            spokenText = spokenText,
            language = language,
            arasaacId = null,
            imagePath = imagePath,
            position = pictoDao.maxPosition(categoryId) + 1,
            colorArgbOverride = colorArgbOverride,
        )
        pictoDao.upsert(picto)
        return picto
    }

    /**
     * Copies [source] (from another category) into [categoryId] as a new picto
     * that keeps [colorArgbOverride] — the source category's colour — so the user
     * can tell where it came from. The cached image file is shared.
     */
    suspend fun copyPictoInto(
        categoryId: String,
        source: PictoEntity,
        colorArgbOverride: Int,
    ): PictoEntity {
        val picto = source.copy(
            id = "pic-" + UUID.randomUUID(),
            categoryId = categoryId,
            position = pictoDao.maxPosition(categoryId) + 1,
            colorArgbOverride = colorArgbOverride,
        )
        pictoDao.upsert(picto)
        return picto
    }

    suspend fun updatePicto(picto: PictoEntity) = pictoDao.update(picto)

    suspend fun deletePicto(picto: PictoEntity) = pictoDao.delete(picto)

    suspend fun reorderPictos(ordered: List<PictoEntity>) {
        pictoDao.updateAll(ordered.mapIndexed { i, p -> p.copy(position = i) })
    }

    // --- Usage & the "Suggested" category ----------------------------------

    /** Records one use of [picto] (called when it is typed/spoken). */
    suspend fun recordUsage(picto: PictoEntity) {
        val text = picto.spokenText.ifBlank { picto.label }
        if (text.isBlank()) return
        usageDao.record(
            UsageEntity(
                id = UsageEntity.keyFor(picto.arasaacId, text, picto.language),
                arasaacId = picto.arasaacId,
                label = picto.label.ifBlank { text },
                spokenText = text,
                language = picto.language,
            ),
            now = System.currentTimeMillis(),
        )
    }

    /** Most-used words, highest first. Empty until the keyboard has been used. */
    suspend fun topUsed(limit: Int): List<UsageEntity> = usageDao.topUsed(limit)

    suspend fun usedCount(): Int = usageDao.usedCount()

    /** Creates a category whose pictos are the most-used words, in usage order. */
    suspend fun addSuggestedCategory(
        name: String,
        colorArgb: Int,
        records: List<UsageEntity>,
    ): CategoryEntity = coroutineScope {
        val cat = CategoryEntity(
            id = "cat-" + UUID.randomUUID(),
            name = name,
            colorArgb = colorArgb,
            position = categoryDao.maxPosition() + 1,
            builtin = false,
        )
        categoryDao.upsert(cat)
        val pictos = records.mapIndexed { i, u ->
            async {
                PictoEntity(
                    id = "pic-" + UUID.randomUUID(),
                    categoryId = cat.id,
                    label = u.label,
                    spokenText = u.spokenText,
                    language = u.language,
                    arasaacId = u.arasaacId,
                    imagePath = u.arasaacId?.let { imageCache.downloadArasaac(it) },
                    position = i,
                )
            }
        }
        pictoDao.upsertAll(pictos.awaitAll())
        cat
    }
}
