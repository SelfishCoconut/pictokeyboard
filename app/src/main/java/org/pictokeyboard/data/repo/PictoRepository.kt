package org.pictokeyboard.data.repo

import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.pictokeyboard.data.arasaac.ArasaacOptions
import org.pictokeyboard.data.arasaac.ArasaacResult
import org.pictokeyboard.data.arasaac.ImageCache
import org.pictokeyboard.data.db.BoardDao
import org.pictokeyboard.data.db.BoardEntity
import org.pictokeyboard.data.db.BorderStyles
import org.pictokeyboard.data.db.CategoryDao
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.data.db.PictoDao
import org.pictokeyboard.data.db.PictoEntity
import org.pictokeyboard.data.db.UsageDao
import org.pictokeyboard.data.db.UsageEntity
import org.pictokeyboard.data.prefs.LegacyBoardLayout
import org.pictokeyboard.data.seed.CategoryTemplate
import org.pictokeyboard.data.seed.DefaultData
import java.io.File
import java.util.UUID

/**
 * Single source of truth for categories and pictos. Handles seeding default
 * categories on first launch and downloading ARASAAC images into the cache so
 * the keyboard works offline.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PictoRepository(
    private val boardDao: BoardDao,
    private val categoryDao: CategoryDao,
    private val pictoDao: PictoDao,
    private val usageDao: UsageDao,
    private val imageCache: ImageCache,
) {
    // --- Boards --------------------------------------------------------------

    fun observeBoards(): Flow<List<BoardEntity>> = boardDao.observeAll()

    fun observeActiveBoard(): Flow<BoardEntity?> = boardDao.observeActive()

    /** The boards the keyboard shows as tabs — see [BoardEntity.showInKeyboard]. */
    fun observeKeyboardBoards(): Flow<List<BoardEntity>> = boardDao.observeVisible()

    suspend fun activeBoard(): BoardEntity? = boardDao.getActive()

    suspend fun board(id: String): BoardEntity? = boardDao.getById(id)

    suspend fun addBoard(
        name: String,
        colorArgb: Int = BoardEntity.DEFAULT_COLOR_ARGB,
        language: String = "es",
    ): BoardEntity {
        val board = BoardEntity(
            id = "board-" + UUID.randomUUID(),
            name = name,
            colorArgb = colorArgb,
            position = boardDao.maxPosition() + 1,
            language = language,
        )
        boardDao.upsert(board)
        return board
    }

    suspend fun updateBoard(board: BoardEntity) = boardDao.update(board)

    suspend fun deleteBoard(board: BoardEntity) = boardDao.delete(board)

    suspend fun reorderBoards(ordered: List<BoardEntity>) {
        // In-place UPDATE, never an upsert with REPLACE: replacing a board row
        // deletes it, and every category on it cascades away with its pictos.
        boardDao.updateAll(ordered.mapIndexed { i, b -> b.copy(position = i) })
    }

    suspend fun setActiveBoard(id: String) = boardDao.setActive(id)

    /**
     * Everything the boards list draws, for every board, as one flow.
     *
     * Assembled here rather than per card so the screen issues three queries
     * regardless of how many boards there are. `heroPictos` covers only the
     * first category of each board — that is all a miniature shows — so the
     * picto query stays bounded no matter how large the vocabulary grows.
     */
    fun observeBoardSummaries(): Flow<List<BoardSummary>> =
        combine(
            boardDao.observeAll(),
            categoryDao.observeAll(),
            pictoDao.observeCountsByBoard(),
        ) { boards, categories, counts ->
            Triple(boards, categories, counts)
        }.flatMapLatest { (boards, categories, counts) ->
            val byBoard = categories.groupBy { it.boardId }
            val firstCategoryIds = boards.mapNotNull { byBoard[it.id]?.firstOrNull()?.id }
            val pictoCounts = counts.associate { it.boardId to it.pictoCount }

            val heroPictos = if (firstCategoryIds.isEmpty()) {
                flowOf(emptyList())
            } else {
                pictoDao.observeByCategories(firstCategoryIds)
            }
            heroPictos.map { pictos ->
                val pictosByCategory = pictos.groupBy { it.categoryId }
                boards.map { board ->
                    val boardCategories = byBoard[board.id].orEmpty()
                    BoardSummary(
                        board = board,
                        categories = boardCategories,
                        heroPictos = boardCategories.firstOrNull()
                            ?.let { pictosByCategory[it.id] }
                            .orEmpty(),
                        pictoCount = pictoCounts[board.id] ?: 0,
                    )
                }
            }
        }

    /**
     * Copies [board] and everything on it under a new name.
     *
     * Every id is regenerated: a duplicate that shared picto ids with its
     * original would have edits to one silently appear in the other, which is
     * the opposite of what duplicating a board is for. Cached image paths are
     * shared deliberately — the file on disk is the same picture, and copying
     * it would double the storage a caregiver's boards cost for nothing.
     */
    suspend fun duplicateBoard(board: BoardEntity, newName: String): BoardEntity {
        val copy = board.copy(
            id = "board-" + UUID.randomUUID(),
            name = newName,
            position = boardDao.maxPosition() + 1,
            // Duplicating must not yank the keyboard out from under whoever is
            // using it. The copy is created dormant; switching to it is a
            // separate, deliberate action.
            active = false,
        )
        boardDao.upsert(copy)
        categoryDao.getByBoard(board.id).forEach { category ->
            val newCategory = category.copy(id = "cat-" + UUID.randomUUID(), boardId = copy.id)
            categoryDao.upsert(newCategory)
            pictoDao.upsertAll(
                pictoDao.getByCategory(category.id).map { picto ->
                    picto.copy(id = "pic-" + UUID.randomUUID(), categoryId = newCategory.id)
                },
            )
        }
        return copy
    }

    /**
     * Copies the layout a pre-board install kept globally onto the board the
     * migration created, then drops the old keys.
     *
     * Called once on start. The Room migration cannot do this itself — the
     * values live in DataStore, which a `SupportSQLiteDatabase` cannot read —
     * so without this step someone who had chosen 6 columns would silently get
     * 4 back after upgrading. Each field is applied only if it was actually
     * set, so a value the user never touched keeps the board's own default.
     */
    suspend fun adoptLegacyBoardLayout(legacy: LegacyBoardLayout) {
        val board = boardDao.getById(BoardEntity.DEFAULT_ID) ?: boardDao.getActive() ?: return
        boardDao.update(board.withLegacyLayout(legacy))
    }

    // --- Categories and pictos ----------------------------------------------

    fun observeCategories(boardId: String): Flow<List<CategoryEntity>> =
        categoryDao.observeByBoard(boardId)

    /**
     * Categories of whichever board is in use, following a board switch without
     * the caller having to re-subscribe.
     */
    fun observeActiveBoardCategories(): Flow<List<CategoryEntity>> =
        boardDao.observeActive().flatMapLatest { board ->
            if (board == null) flowOf(emptyList()) else categoryDao.observeByBoard(board.id)
        }

    fun observePictos(categoryId: String): Flow<List<PictoEntity>> =
        pictoDao.observeByCategory(categoryId)

    /**
     * How many pictograms sit in each category, keyed by category id.
     *
     * One query for the whole list rather than a subscription per row: the
     * board detail draws the count on every category at once, and a per-row
     * flow would open as many cursors as the caregiver has categories.
     */
    fun observeCategoryPictoCounts(): Flow<Map<String, Int>> =
        pictoDao.observeCountsByCategory().map { counts ->
            counts.associate { it.categoryId to it.pictoCount }
        }

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
    suspend fun seedIfEmpty(language: String, boardName: String) {
        // A board must exist before categories, and categories before pictos:
        // both are foreign keys. On an upgraded install the migration has
        // already created the board, and `INSERT OR IGNORE` semantics come from
        // the count check rather than from the DAO.
        if (boardDao.count() == 0) {
            boardDao.upsert(
                BoardEntity(
                    id = BoardEntity.DEFAULT_ID,
                    name = boardName,
                    colorArgb = BoardEntity.DEFAULT_COLOR_ARGB,
                    position = 0,
                    active = true,
                    language = language,
                ),
            )
        }
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

    /**
     * The board a new category belongs to: whichever one is in use.
     *
     * Not a parameter, because nothing can name a different board until there
     * is a UI that shows more than one — #32 and #33. Falls back to the
     * migrated board's id so a category can still be created in the window
     * before the first board is marked active, rather than failing the foreign
     * key and losing the caregiver's work.
     */
    private suspend fun activeBoardId(): String =
        boardDao.getActive()?.id ?: BoardEntity.DEFAULT_ID

    suspend fun addCategory(
        name: String,
        colorArgb: Int,
        borderStyle: String = BorderStyles.SOLID,
        borderWidthDp: Int = BorderStyles.DEFAULT_WIDTH_DP,
        icon: IconChoice = IconChoice.None,
    ): CategoryEntity {
        val (iconArasaacId, iconImagePath) = resolveIcon(icon)
        val board = activeBoardId()
        val cat = CategoryEntity(
            id = "cat-" + UUID.randomUUID(),
            name = name,
            boardId = board,
            colorArgb = colorArgb,
            position = categoryDao.maxPosition(board) + 1,
            builtin = false,
            iconArasaacId = iconArasaacId,
            iconImagePath = iconImagePath,
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
            val board = activeBoardId()
            val cat = CategoryEntity(
                id = "cat-" + UUID.randomUUID(),
                name = template.name(language),
                boardId = board,
                colorArgb = template.color.toInt(),
                position = categoryDao.maxPosition(board) + 1,
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

    /**
     * Saves the category editor's changes, [icon] included. The icon is resolved
     * — and downloaded, if it is a fresh ARASAAC pick — before the single row
     * update, so the keyboard chip never flickers through a half-applied state.
     */
    suspend fun updateCategory(category: CategoryEntity, icon: IconChoice) {
        val (iconArasaacId, iconImagePath) = resolveIcon(icon)
        categoryDao.update(category.copy(iconArasaacId = iconArasaacId, iconImagePath = iconImagePath))
    }

    /**
     * Saves the board's picto — the one its keyboard tab is drawn with.
     *
     * Resolved through the same [resolveIcon] a category goes through, so an
     * ARASAAC pick made here is cached on the way in and the tab keeps drawing
     * it offline. That sharing is the point: a board and a category store the
     * same two columns, and a second copy of this would be a second place for
     * the caching to be forgotten.
     */
    suspend fun updateBoardIcon(board: BoardEntity, icon: IconChoice) {
        val (iconArasaacId, iconImagePath) = resolveIcon(icon)
        boardDao.update(board.copy(iconArasaacId = iconArasaacId, iconImagePath = iconImagePath))
    }

    /** Every picto on [boardId], for the board picker's "a symbol already on this board". */
    suspend fun boardPictos(boardId: String): List<PictoEntity> = pictoDao.getByBoard(boardId)

    /**
     * Turns a picker choice into the (id, path) pair a category or board stores.
     *
     * A fresh ARASAAC pick is cached here so the keyboard keeps showing it
     * offline. A failed download still stores the id: the chip then renders from
     * the CDN, and [warmImageCache] retries on a later launch — a category that
     * shows a picto only when online beats one that shows nothing ever.
     */
    private suspend fun resolveIcon(icon: IconChoice): Pair<Int?, String?> = when (icon) {
        IconChoice.None -> null to null
        is IconChoice.Arasaac -> icon.id to imageCache.downloadArasaac(icon.id)
        is IconChoice.Local -> icon.arasaacId to icon.path
    }

    /**
     * Saves [bitmap] into the image cache and returns its path, for a photo the
     * caregiver picked as a category's picto rather than as a picto of its own.
     */
    suspend fun saveImage(bitmap: Bitmap): String? = imageCache.saveBitmap(bitmap)

    suspend fun deleteCategory(category: CategoryEntity) = categoryDao.delete(category)

    suspend fun reorderCategories(ordered: List<CategoryEntity>) {
        // Must be an in-place UPDATE: an upsert with REPLACE would delete and
        // re-insert each category row, cascade-deleting all of its pictos.
        categoryDao.updateAll(ordered.mapIndexed { i, c -> c.copy(position = i) })
    }

    /**
     * Moves [category] to the end of [targetBoardId]'s list, and returns the row
     * exactly as it was so the move can be undone (#119).
     *
     * Its pictograms come with it and are never touched: a picto belongs to a
     * category, not to a board, so the whole move is one column on one row. That
     * is the reason this is cheap enough to offer an undo for at all — the
     * inverse is [restoreCategory], writing the returned row straight back.
     *
     * An UPDATE for the same reason [reorderCategories] is: an upsert with
     * REPLACE would delete and re-insert the category, and the cascade would
     * take every picto in it. That would make "move a category" delete the
     * caregiver's words, which is the exact failure this feature must not have.
     *
     * The source board is allowed to end up with no categories. A board with
     * nothing on it is a board halfway through being built, and refusing the
     * last move would block the most ordinary use of this — emptying one board
     * into another before deleting it.
     */
    suspend fun moveCategoryToBoard(category: CategoryEntity, targetBoardId: String): CategoryEntity {
        val previous = categoryDao.getById(category.id) ?: category
        categoryDao.update(
            previous.copy(
                boardId = targetBoardId,
                position = categoryDao.maxPosition(targetBoardId) + 1,
            ),
        )
        return previous
    }

    /** Puts a moved category back on the board and at the position it came from. */
    suspend fun restoreCategory(category: CategoryEntity) = categoryDao.update(category)

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
        val board = activeBoardId()
        val cat = CategoryEntity(
            id = "cat-" + UUID.randomUUID(),
            name = name,
            boardId = board,
            colorArgb = colorArgb,
            position = categoryDao.maxPosition(board) + 1,
            builtin = false,
        )
        categoryDao.upsert(cat)
        val existing = imagesByUsageIdentity()
        val pictos = records.mapIndexed { i, u ->
            async {
                PictoEntity(
                    id = "pic-" + UUID.randomUUID(),
                    categoryId = cat.id,
                    label = u.label,
                    spokenText = u.spokenText,
                    language = u.language,
                    arasaacId = u.arasaacId,
                    // The picture the user has already seen, first. For an
                    // ARASAAC symbol a re-download would do; for a photograph
                    // nothing else exists, and taking only the second branch is
                    // what made every photo in Suggested come out blank (#153).
                    imagePath = existing[u.id] ?: u.arasaacId?.let { imageCache.downloadArasaac(it) },
                    position = i,
                )
            }
        }
        pictoDao.upsertAll(pictos.awaitAll())
        cat
    }

    /**
     * Every picture on the phone, indexed by the identity the usage table uses.
     *
     * The usage row deliberately stores no path — its whole point is to survive
     * the picto being edited, moved or deleted — so the image has to be found
     * again from the pictos that are still here. Keyed by
     * [UsageEntity.keyFor] with the same `spokenText.ifBlank { label }` fallback
     * [recordUsage] applies, or the two would disagree for a picto that has only
     * a label and the lookup would silently miss.
     *
     * **Only files that are really there.** A row can outlive its image — the OS
     * clearing app cache, a restore onto a new device — and copying a dead path
     * would hand `keyboardImageModel` something that looks resolvable and is not,
     * costing the ARASAAC fallback that would otherwise have drawn the symbol.
     *
     * One `getAll` rather than a query per record: this runs once, for at most a
     * couple of dozen records, against a table that is already small enough to
     * be read whole elsewhere.
     */
    private suspend fun imagesByUsageIdentity(): Map<String, String> =
        pictoDao.getAll()
            .mapNotNull { picto ->
                val path = picto.imagePath?.takeIf { File(it).isFile } ?: return@mapNotNull null
                val text = picto.spokenText.ifBlank { picto.label }
                if (text.isBlank()) null else UsageEntity.keyFor(picto.arasaacId, text, picto.language) to path
            }
            .toMap()
}
