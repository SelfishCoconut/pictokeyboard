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

    /**
     * Where the runtime keeps the weights repacked into the layout its kernels
     * want, so that work happens once instead of on every load (#155).
     *
     * It has to exist before the engine is told about it. LiteRT will not create
     * it, and when it is missing XNNPACK logs that it could neither read nor
     * write the cache and carries on — silently, from Kotlin's side, paying the
     * full repacking cost every time. That is why this survived until the first
     * run against real weights.
     */
    val cacheDirectory: File = File(directory, CACHE_DIRECTORY)

    fun isDownloaded(): Boolean = file.isFile && file.length() == ModelSpec.SIZE_BYTES

    /** How far a previous attempt got, for a `Range` request. */
    fun partialBytes(): Long = if (partial.isFile) partial.length() else 0

    fun prepareDirectory() {
        directory.mkdirs()
    }

    /**
     * Makes [cacheDirectory], reporting whether there is now somewhere to write.
     *
     * False is not a failure worth stopping for: the model still loads, just
     * without a cache, which is exactly what happens today.
     */
    fun prepareCache(): Boolean = cacheDirectory.isDirectory || cacheDirectory.mkdirs()

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

    /**
     * Removes the model, any half-finished attempt, and the repacked cache.
     *
     * The cache goes too, and must: it is derived from weights that are being
     * deleted, it is worth hundreds of megabytes, and somebody who just pressed
     * a button offering to free that space would still be short of it (#155).
     */
    fun delete() {
        file.delete()
        partial.delete()
        cacheDirectory.deleteRecursively()
    }

    /**
     * Bytes on disk for this feature, so Settings can name a number (#48).
     *
     * The cache counts. It is space this feature is using and that deleting the
     * model gives back, so leaving it out would understate the offer by however
     * large the repacked weights happen to be.
     */
    fun bytesOnDisk(): Long =
        (if (file.isFile) file.length() else 0) + partialBytes() + cacheBytes()

    private fun cacheBytes(): Long =
        cacheDirectory.walkBottomUp().filter(File::isFile).sumOf(File::length)

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
        const val CACHE_DIRECTORY = "cache"
        const val DIGEST_BUFFER = 1 shl 16
    }
}
