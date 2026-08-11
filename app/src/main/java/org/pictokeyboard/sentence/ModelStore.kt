package org.pictokeyboard.sentence

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * Where the weights live on disk, and the only place that decides whether what
 * is there is usable.
 *
 * In `filesDir`, not the cache: the system deletes cache directories under
 * storage pressure without asking, and a 347 MB download that evaporates on a
 * full phone would be re-fetched on somebody's mobile data. It is excluded from
 * backup for the same reason every other bulk file is — see `backup_rules.xml`.
 */
class ModelStore(context: Context) {

    private val directory = File(context.filesDir, DIRECTORY)

    /** The finished file. Present only once a download has completed and verified. */
    val file: File = File(directory, "v${ModelSpec.VERSION}-${ModelSpec.FILE_NAME}")

    /**
     * Where bytes land while they are still arriving.
     *
     * Separate from [file] so that the presence of [file] means "complete and
     * checked" and nothing else. A download written straight to its destination
     * and interrupted leaves a file that looks installed, and the next launch
     * hands a truncated model to the engine.
     */
    val partial: File = File(directory, "${file.name}.part")

    fun isDownloaded(): Boolean = file.isFile && file.length() == ModelSpec.SIZE_BYTES

    /** How far a previous attempt got, for a `Range` request. */
    fun partialBytes(): Long = if (partial.isFile) partial.length() else 0

    fun prepareDirectory() {
        directory.mkdirs()
    }

    /**
     * Promotes a completed [partial] to [file] once its digest matches.
     *
     * The check is here rather than in the downloader because this is the class
     * that decides what "installed" means. A resumed download that picked up a
     * different revision matches on length and not on content, and a model half
     * from one revision and half from another produces confident nonsense that
     * no validator would catch.
     */
    fun installIfVerified(): Boolean {
        if (!partial.isFile || partial.length() != ModelSpec.SIZE_BYTES) return false
        if (!sha256Matches(partial)) {
            partial.delete()
            return false
        }
        return partial.renameTo(file)
    }

    /** Removes both the model and any half-finished attempt. */
    fun delete() {
        file.delete()
        partial.delete()
    }

    /** Bytes on disk for this feature, so Settings can name a number (#48). */
    fun bytesOnDisk(): Long = (if (file.isFile) file.length() else 0) + partialBytes()

    private fun sha256Matches(target: File): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        target.inputStream().buffered().use { stream ->
            val buffer = ByteArray(DIGEST_BUFFER)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) } == ModelSpec.SHA256
    }

    private companion object {
        const val DIRECTORY = "sentence-model"
        const val DIGEST_BUFFER = 1 shl 16
    }
}
