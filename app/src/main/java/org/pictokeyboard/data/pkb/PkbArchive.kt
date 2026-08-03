package org.pictokeyboard.data.pkb

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** A photograph or recording, addressed by the SHA-256 of its own bytes. */
class PkbMedia(val digest: String, val open: () -> InputStream)

/**
 * Why an import stopped.
 *
 * Typed rather than a string, because the caregiver reads this in their own
 * language and the message they see comes from a string resource. The text on
 * each case is for a log, not for a person.
 */
sealed class PkbFailure(message: String) : Exception(message) {
    /** Written by a later version of the app than this one. */
    class NewerFormat(val fileVersion: Int) :
        PkbFailure("File format version $fileVersion is newer than $FORMAT_VERSION")

    /** An entry named something we could not have written. */
    class UnsafeEntry(val entryName: String) : PkbFailure("Refusing an archive entry named '$entryName'")

    /** Not a `.pkb` at all, or truncated, or with nothing in it. */
    class Malformed(reason: String) : PkbFailure(reason)

    companion object {
        internal const val FORMAT_VERSION = PkbArchive.FORMAT_VERSION
    }
}

/**
 * Content addressing: a file's name in the archive comes from its bytes.
 *
 * Two consequences, and both are the reason it is done this way. The same photo
 * used by three pictos is stored once. And an entry name from an untrusted
 * archive is never used as a path, because the only names accepted are ones
 * this object could have produced itself.
 */
object Sha256 {
    private const val HEX = "0123456789abcdef"

    /** A SHA-256 rendered as hex is this long, and a valid entry name is too. */
    private const val DIGEST_LENGTH = 64
    private const val BYTE_MASK = 0xFF
    private const val LOW_NIBBLE = 0x0F
    private const val NIBBLE_BITS = 4

    fun hex(bytes: ByteArray): String = digest().let {
        it.update(bytes)
        it.toHex()
    }

    fun hex(stream: InputStream): String {
        val md = digest()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            md.update(buffer, 0, read)
        }
        return md.toHex()
    }

    /** The digest accumulated so far, for a stream that was hashed as it was read. */
    fun hex(digest: MessageDigest): String = digest.toHex()

    /** True for a name this object could have produced, and nothing else. */
    fun isDigest(value: String): Boolean =
        value.length == DIGEST_LENGTH && value.all { it in HEX }

    private fun digest() = MessageDigest.getInstance("SHA-256")

    private fun MessageDigest.toHex(): String = buildString(DIGEST_LENGTH) {
        for (byte in digest()) {
            val v = byte.toInt() and BYTE_MASK
            append(HEX[v ushr NIBBLE_BITS])
            append(HEX[v and LOW_NIBBLE])
        }
    }
}

/**
 * Reads and writes the `.pkb` file: a ZIP holding a manifest, the whole board
 * graph as JSON, and the media that graph refers to.
 *
 * The Moshi instance is private on purpose. This is a file format every future
 * version of the app has to be able to read, so it must not change shape
 * because somebody added an adapter to the container's shared Moshi.
 */
object PkbArchive {

    /** The version this build writes, and the highest it can read. */
    const val FORMAT_VERSION = 1

    const val MANIFEST_ENTRY = "manifest.json"
    const val BOARDS_ENTRY = "boards.json"
    const val MEDIA_PREFIX = "media/"

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val manifestAdapter = moshi.adapter(PkbManifest::class.java).indent("  ")
    private val documentAdapter = moshi.adapter(PkbDocument::class.java).indent("  ")

    fun write(
        out: OutputStream,
        document: PkbDocument,
        media: List<PkbMedia>,
        appVersion: String = "",
        exportedAt: String = Instant.now().toString(),
    ) {
        val distinct = media.distinctBy { it.digest }
        ZipOutputStream(out).use { zip ->
            zip.entry(MANIFEST_ENTRY) {
                it.write(
                    manifestAdapter
                        .toJson(document.manifest(appVersion, exportedAt, distinct.size))
                        .toByteArray(),
                )
            }
            zip.entry(BOARDS_ENTRY) { it.write(documentAdapter.toJson(document).toByteArray()) }
            for (item in distinct) {
                zip.entry(MEDIA_PREFIX + item.digest) { target ->
                    item.open().use { it.copyTo(target) }
                }
            }
        }
    }

    /**
     * Reads [source] twice: once to decide whether the file is one we may read
     * at all, and only then to hand its media over.
     *
     * The two passes are the whole point. A `.pkb` is a ZIP, and a ZIP's entries
     * arrive in whatever order the file says — so a single pass would have
     * already written a caregiver's cache full of photographs by the time it
     * reached a manifest saying the file came from a newer app. "Fails with a
     * stated reason rather than importing half of itself" has to mean *none* of
     * itself, and that costs one extra read of a local file.
     *
     * [source] is a factory rather than a stream because there is nothing to
     * rewind: in the app it opens the same content uri twice.
     */
    fun read(
        source: () -> InputStream,
        mediaSink: (digest: String, stream: InputStream) -> Unit,
    ): Result<PkbDocument> = runCatching {
        val document = source().use(::validate)
        source().use { input -> streamMedia(input, mediaSink) }
        document
    }.recoverCatching { throw it.asPkbFailure() }

    /** Everything that can refuse the file, before a single byte is handed out. */
    private fun validate(input: InputStream): PkbDocument {
        var document: PkbDocument? = null
        var sawManifest = false
        ZipInputStream(input).use { zip ->
            zip.forEachEntry { entry ->
                when {
                    entry.name == MANIFEST_ENTRY -> {
                        sawManifest = true
                        checkReadable(zip.readEntry())
                    }

                    entry.name == BOARDS_ENTRY ->
                        document = documentAdapter.fromJson(zip.readEntry())

                    entry.name.startsWith(MEDIA_PREFIX) -> checkMediaName(entry.name)
                    // Anything else is from a later version that added an entry
                    // this one does not know about. Ignored, not fatal.
                }
            }
        }
        return document?.takeIf { sawManifest }
            ?: throw PkbFailure.Malformed("No $MANIFEST_ENTRY and $BOARDS_ENTRY in the file")
    }

    private fun checkReadable(manifestJson: String) {
        val manifest = manifestAdapter.fromJson(manifestJson)
            ?: throw PkbFailure.Malformed("Empty $MANIFEST_ENTRY")
        if (manifest.formatVersion > FORMAT_VERSION) {
            throw PkbFailure.NewerFormat(manifest.formatVersion)
        }
    }

    /**
     * The only media names accepted are ones [Sha256] could have produced, so a
     * name out of an untrusted file is never used as a path — `../../evil` is
     * not a digest and does not get as far as the filesystem.
     */
    private fun checkMediaName(entryName: String) {
        if (!Sha256.isDigest(entryName.removePrefix(MEDIA_PREFIX))) {
            throw PkbFailure.UnsafeEntry(entryName)
        }
    }

    private fun streamMedia(
        input: InputStream,
        mediaSink: (digest: String, stream: InputStream) -> Unit,
    ) {
        ZipInputStream(input).use { zip ->
            zip.forEachEntry { entry ->
                if (entry.name.startsWith(MEDIA_PREFIX)) {
                    val claimed = entry.name.removePrefix(MEDIA_PREFIX)
                    val digesting = DigestInputStream(
                        NonClosing(zip),
                        MessageDigest.getInstance("SHA-256"),
                    )
                    mediaSink(claimed, digesting)

                    // Whatever the sink chose not to read still counts: the name
                    // is a claim about the whole entry, so the whole entry is
                    // what gets hashed.
                    digesting.copyTo(NullOutputStream)

                    val actual = Sha256.hex(digesting.messageDigest)
                    if (actual != claimed) {
                        throw PkbFailure.Malformed(
                            "Entry $MEDIA_PREFIX$claimed does not contain what it says it does",
                        )
                    }
                }
            }
        }
    }

    private fun PkbDocument.manifest(appVersion: String, exportedAt: String, mediaCount: Int) =
        PkbManifest(
            formatVersion = FORMAT_VERSION,
            appVersion = appVersion,
            exportedAt = exportedAt,
            boardCount = boards.size,
            categoryCount = boards.sumOf { it.categories.size },
            pictoCount = boards.sumOf { board -> board.categories.sumOf { it.pictos.size } },
            mediaCount = mediaCount,
        )
}

private inline fun ZipInputStream.forEachEntry(action: (ZipEntry) -> Unit) {
    while (true) {
        val entry = nextEntry ?: break
        action(entry)
        closeEntry()
    }
}

private inline fun ZipOutputStream.entry(name: String, write: (OutputStream) -> Unit) {
    putNextEntry(ZipEntry(name))
    write(this)
    closeEntry()
}

/** Reads the current entry without letting a reader close the archive. */
private fun ZipInputStream.readEntry(): String = NonClosing(this).readBytes().decodeToString()

/**
 * Everything a caregiver can be handed — a photo renamed to `.pkb`, a
 * half-downloaded file, a zip of something else entirely — reaches the UI as
 * one type it knows how to explain.
 */
private fun Throwable.asPkbFailure(): PkbFailure =
    this as? PkbFailure ?: PkbFailure.Malformed(message ?: this::class.java.simpleName)

/**
 * The archive owns its stream. Handing a raw [ZipInputStream] to a caller that
 * closes what it reads would end the import at the first photograph.
 */
private class NonClosing(delegate: InputStream) : FilterInputStream(delegate) {
    override fun close() = Unit
}

/**
 * Somewhere to pour the rest of an entry the sink did not want.
 * `OutputStream.nullOutputStream()` would do, and needs API 33; this app runs
 * from 26.
 */
private object NullOutputStream : OutputStream() {
    override fun write(b: Int) = Unit
    override fun write(b: ByteArray, off: Int, len: Int) = Unit
}
