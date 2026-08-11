package org.pictokeyboard.data.pkb

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.pictokeyboard.data.arasaac.ImageCache
import org.pictokeyboard.data.db.AppDatabase
import org.pictokeyboard.data.db.BoardEntity
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.data.db.PictoEntity
import org.pictokeyboard.data.prefs.Settings
import org.pictokeyboard.data.prefs.SettingsStore
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/** What an import turned out to contain, for the sentence shown afterwards. */
data class PkbImportSummary(val boards: Int, val categories: Int, val pictos: Int, val media: Int)

/**
 * The whole backup, in both directions: the database and the image cache at one
 * end, a file the caregiver chose at the other.
 *
 * The format itself and the rules that make it safe live in [PkbArchive] and
 * [PkbMapping], which know nothing about Android and are tested without it.
 * What is left here is the part that genuinely needs a device — reading rows,
 * reading files, and putting both back.
 */
class PkbBackup(
    private val db: AppDatabase,
    private val settingsStore: SettingsStore,
    private val imageCache: ImageCache,
    private val appVersion: String,
) {
    private val boardDao = db.boardDao()
    private val categoryDao = db.categoryDao()
    private val pictoDao = db.pictoDao()

    /**
     * Writes what this device holds about boards into [out] — every board, or
     * the single [boardId] when one is named.
     *
     * A photo whose file has gone — cleared cache, restored phone — costs its
     * picto a picture and never the export. Losing one symbol is a bad day;
     * refusing to back anything up because of it is how a caregiver ends up
     * with no backup at all.
     *
     * **Voice settings travel with a whole-device backup and not with one
     * board.** A backup is this caregiver's own phone arriving on their next
     * phone, so their speech rate, pitch and blind-mode choice should follow
     * them. A single board is something they hand to somebody else, and it is
     * not for one caregiver to reach into another's device and reset how their
     * user's voice sounds. Same archive, and the difference is deliberate.
     */
    suspend fun exportTo(out: OutputStream, boardId: String? = null): Result<PkbImportSummary> =
        withContext(Dispatchers.IO) {
            runCatching {
                val boards = boardDao.getAll().filter { boardId == null || it.id == boardId }
                val boardIds = boards.map { it.id }.toSet()
                val categories = categoryDao.getAll().filter { it.boardId in boardIds }
                val categoryIds = categories.map { it.id }.toSet()
                val pictos = pictoDao.getAll().filter { it.categoryId in categoryIds }
                val settings = if (boardId == null) settingsStore.settings.first() else null

                val digestByPath = mediaPaths(boards, categories, pictos)
                    .mapNotNull { path ->
                        val file = File(path)
                        if (file.isFile) path to file.inputStream().use(Sha256::hex) else null
                    }
                    .toMap()

                val document = PkbMapping.toDocument(
                    boards = boards,
                    categories = categories,
                    pictos = pictos,
                    settings = settings,
                    digestOf = digestByPath::get,
                )
                val media = digestByPath.map { (path, digest) ->
                    PkbMedia(digest) { File(path).inputStream() }
                }

                PkbArchive.write(out, document, media, appVersion)

                PkbImportSummary(
                    boards = boards.size,
                    categories = categories.size,
                    pictos = pictos.size,
                    media = media.size,
                )
            }
        }

    /**
     * Adds everything in [source] to this device. Never replaces.
     *
     * Media land in a staging directory first and are only moved into the cache
     * once the whole file has been read and verified, and the rows go in inside
     * one transaction. A file that turns out to be truncated half way through
     * therefore leaves nothing behind — no half-created board, and no orphan
     * photographs taking up space nothing points at.
     */
    suspend fun importFrom(source: () -> InputStream): Result<PkbImportSummary> =
        withContext(Dispatchers.IO) {
            val staging = imageCache.importStagingDir()
            staging.empty()
            val outcome = runCatching { readAndCommit(source, staging) }
            staging.empty()
            outcome
        }

    private suspend fun readAndCommit(source: () -> InputStream, staging: File): PkbImportSummary {
        val staged = mutableMapOf<String, File>()
        val document = PkbArchive.read(source) { digest, stream ->
            val target = File(staging, digest)
            target.outputStream().use { stream.copyTo(it) }
            staged[digest] = target
        }.getOrThrow()

        // Only now, with the whole file read and every digest verified, do the
        // photographs leave staging.
        val committed = staged.mapValues { (digest, file) ->
            val target = imageCache.fileForImported(digest)
            if (!target.exists()) file.copyTo(target, overwrite = true)
            target.absolutePath
        }

        val imported = PkbMapping.toEntities(
            document = document,
            newId = { UUID.randomUUID().toString() },
            mediaPath = committed::get,
        )

        // Appended after what the caregiver already has, never over it.
        val startingPosition = boardDao.maxPosition() + 1
        db.withTransaction {
            imported.boards.forEachIndexed { index, board ->
                boardDao.upsert(board.copy(position = startingPosition + index))
            }
            categoryDao.upsertAll(imported.categories)
            pictoDao.upsertAll(imported.pictos)
        }
        imported.settings?.let { applySettings(it) }

        return PkbImportSummary(
            boards = imported.boards.size,
            categories = imported.categories.size,
            pictos = imported.pictos.size,
            media = committed.size,
        )
    }

    private fun File.empty() {
        listFiles()?.forEach { it.delete() }
    }

    private fun mediaPaths(
        boards: List<BoardEntity>,
        categories: List<CategoryEntity>,
        pictos: List<PictoEntity>,
    ): Set<String> = buildSet {
        boards.forEach { board -> board.iconImagePath?.let(::add) }
        categories.forEach { category -> category.iconImagePath?.let(::add) }
        pictos.forEach { picto -> picto.imagePath?.let(::add) }
    }

    /**
     * The interface language is left alone on purpose. Everything else here
     * describes how the person speaks and should follow them; which language
     * the caregiver reads the settings screen in is a property of whoever is
     * holding this particular phone.
     */
    private suspend fun applySettings(settings: Settings) {
        settingsStore.setAddSpaceAfter(settings.addSpaceAfter)
        settingsStore.setSpeakOnTap(settings.speakOnTap)
        settingsStore.setTtsRate(settings.ttsRate)
        settingsStore.setTtsPitch(settings.ttsPitch)
        settingsStore.setBlindMode(settings.blindMode)
    }
}
