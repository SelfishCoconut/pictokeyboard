package org.pictokeyboard.data.pkb

import org.pictokeyboard.data.db.BoardEntity
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.data.db.PictoEntity
import org.pictokeyboard.data.prefs.Settings

/** Rows ready to be written, with every id newly minted. */
data class PkbImport(
    val boards: List<BoardEntity>,
    val categories: List<CategoryEntity>,
    val pictos: List<PictoEntity>,
    val settings: Settings?,
)

/**
 * Between the database's rows and the document that travels in a `.pkb`.
 *
 * Deliberately free of Android, of Room and of the filesystem: the caller does
 * the reading and the writing and hands the results in as two functions. That
 * is what lets the two rules below be proved by a plain JVM test rather than by
 * a caregiver discovering them.
 */
object PkbMapping {

    /**
     * @param digestOf the content digest of a local media path, or null when
     *   the file behind it has gone. A missing photo costs its picto a picture,
     *   never the export.
     */
    fun toDocument(
        boards: List<BoardEntity>,
        categories: List<CategoryEntity>,
        pictos: List<PictoEntity>,
        settings: Settings?,
        digestOf: (path: String) -> String?,
    ): PkbDocument {
        val categoriesByBoard = categories.groupBy { it.boardId }
        val pictosByCategory = pictos.groupBy { it.categoryId }
        return PkbDocument(
            boards = boards.sortedBy { it.position }.map { board ->
                board.toPkb(
                    digestOf,
                    categoriesByBoard[board.id].orEmpty().sortedBy { it.position }.map { category ->
                        category.toPkb(
                            digestOf,
                            pictosByCategory[category.id].orEmpty().sortedBy { it.position }
                                .map { it.toPkb(digestOf) },
                        )
                    },
                )
            },
            settings = settings?.toPkb(),
        )
    }

    /**
     * @param newId mints an id per row. Every id in the document was thrown
     *   away when it was written, so nothing here can collide with a board the
     *   caregiver already has — importing the same file twice gives them two
     *   boards, and the first one is untouched. Deleting the spare is a decision
     *   they can see; being silently overwritten is not.
     * @param mediaPath where a digest's bytes ended up on this device, or null
     *   if that photo was not in the file. A picto whose photo did not arrive
     *   keeps its label and loses its picture, which is honest; a dangling path
     *   would be a broken image forever.
     */
    fun toEntities(
        document: PkbDocument,
        newId: () -> String,
        mediaPath: (digest: String) -> String?,
    ): PkbImport {
        val boards = mutableListOf<BoardEntity>()
        val categories = mutableListOf<CategoryEntity>()
        val pictos = mutableListOf<PictoEntity>()

        document.boards.forEachIndexed { boardIndex, board ->
            val boardId = newId()
            boards += board.toEntity(boardId, boardIndex, mediaPath)
            board.categories.forEachIndexed { categoryIndex, category ->
                val categoryId = newId()
                categories += category.toEntity(categoryId, boardId, categoryIndex, mediaPath)
                category.pictos.forEachIndexed { pictoIndex, picto ->
                    pictos += picto.toEntity(newId(), categoryId, pictoIndex, mediaPath)
                }
            }
        }

        return PkbImport(boards, categories, pictos, document.settings?.toSettings())
    }

    // --- to the document -----------------------------------------------------

    private fun BoardEntity.toPkb(
        digestOf: (String) -> String?,
        categories: List<PkbCategory>,
    ) = PkbBoard(
        name = name,
        colorArgb = colorArgb,
        position = position,
        language = language,
        iconArasaacId = iconArasaacId,
        iconMedia = iconImagePath?.let(digestOf),
        tags = tags,
        showInKeyboard = showInKeyboard,
        columns = columns,
        rows = rows,
        showLabels = showLabels,
        borderStyle = borderStyle,
        borderWidthDp = borderWidthDp,
        source = source,
        sourceVersion = sourceVersion,
        author = author,
        licence = licence,
        categories = categories,
    )

    private fun CategoryEntity.toPkb(
        digestOf: (String) -> String?,
        pictos: List<PkbPicto>,
    ) = PkbCategory(
        name = name,
        colorArgb = colorArgb,
        position = position,
        builtin = builtin,
        iconArasaacId = iconArasaacId,
        iconMedia = iconImagePath?.let(digestOf),
        borderStyle = borderStyle,
        borderWidthDp = borderWidthDp,
        pictos = pictos,
    )

    private fun PictoEntity.toPkb(digestOf: (String) -> String?) = PkbPicto(
        label = label,
        spokenText = spokenText,
        language = language,
        position = position,
        arasaacId = arasaacId,
        media = imagePath?.let(digestOf),
        colorArgbOverride = colorArgbOverride,
    )

    /**
     * `hasPin` is dropped rather than carried. Sending it without the hash and
     * salt it refers to — which are a credential and stay on the device — would
     * leave the new phone believing a PIN is set and holding no way to check
     * one, which locks the caregiver out of their own settings.
     */
    private fun Settings.toPkb() = PkbSettings(
        defaultLanguage = defaultLanguage,
        addSpaceAfter = addSpaceAfter,
        speakOnTap = speakOnTap,
        ttsRate = ttsRate,
        ttsPitch = ttsPitch,
        blindMode = blindMode,
    )

    // --- back to rows --------------------------------------------------------

    private fun PkbBoard.toEntity(
        id: String,
        position: Int,
        mediaPath: (String) -> String?,
    ) = BoardEntity(
        id = id,
        name = name,
        colorArgb = colorArgb,
        position = position,
        // Which board the keyboard is showing is a property of the device in
        // the room, not of the file. An import must never move it.
        active = false,
        iconArasaacId = iconArasaacId,
        iconImagePath = iconMedia?.let(mediaPath),
        tags = tags,
        showInKeyboard = showInKeyboard,
        columns = BoardEntity.clampColumns(columns),
        rows = BoardEntity.clampRows(rows),
        showLabels = showLabels,
        borderStyle = borderStyle,
        borderWidthDp = borderWidthDp,
        language = language,
        source = source,
        sourceVersion = sourceVersion,
        author = author,
        licence = licence,
    )

    private fun PkbCategory.toEntity(
        id: String,
        boardId: String,
        position: Int,
        mediaPath: (String) -> String?,
    ) = CategoryEntity(
        id = id,
        name = name,
        boardId = boardId,
        colorArgb = colorArgb,
        position = position,
        builtin = builtin,
        iconArasaacId = iconArasaacId,
        iconImagePath = iconMedia?.let(mediaPath),
        borderStyle = borderStyle,
        borderWidthDp = borderWidthDp,
    )

    private fun PkbPicto.toEntity(
        id: String,
        categoryId: String,
        position: Int,
        mediaPath: (String) -> String?,
    ) = PictoEntity(
        id = id,
        categoryId = categoryId,
        label = label,
        spokenText = spokenText,
        language = language,
        arasaacId = arasaacId,
        imagePath = media?.let(mediaPath),
        position = position,
        colorArgbOverride = colorArgbOverride,
    )

    private fun PkbSettings.toSettings() = Settings(
        defaultLanguage = defaultLanguage,
        addSpaceAfter = addSpaceAfter,
        speakOnTap = speakOnTap,
        ttsRate = ttsRate,
        ttsPitch = ttsPitch,
        blindMode = blindMode,
    )
}
