package org.pictokeyboard.data.pkb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The `.pkb` archive: what a caregiver's whole backup travels in.
 *
 * This is the only backup there is. Nothing goes to a server, so a file that
 * loses a photograph loses it for good — which is why the round trip is
 * asserted on bytes rather than on "an image is present".
 */
class PkbArchiveTest {

    private val photo = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)

    @Test
    fun carriesAPhotographThroughUnchanged() {
        val digest = Sha256.hex(photo)
        val written = ByteArrayOutputStream()

        PkbArchive.write(
            out = written,
            document = documentWithPhoto(digest),
            media = listOf(PkbMedia(digest) { photo.inputStream() }),
        )

        val bytes = written.toByteArray()
        val restored = mutableMapOf<String, ByteArray>()
        PkbArchive.read({ bytes.inputStream() }) { name, stream ->
            restored[name] = stream.readBytes()
        }.getOrThrow()

        assertArrayEquals(photo, restored[digest])
    }

    /**
     * The media entry is written *before* the manifest on purpose. A file whose
     * entries arrive in a helpful order proves nothing — the reader has to know
     * it cannot read the version yet, rather than happen to have read it.
     */
    @Test
    fun refusesAFileFromANewerVersionWithoutImportingAnyOfIt() {
        val digest = Sha256.hex(photo)
        val archive = zipOf(
            PkbArchive.MEDIA_PREFIX + digest to photo,
            PkbArchive.MANIFEST_ENTRY to manifestJson(PkbArchive.FORMAT_VERSION + 1),
            PkbArchive.BOARDS_ENTRY to "{\"boards\":[]}".toByteArray(),
        )

        val arrived = mutableListOf<String>()
        val result = PkbArchive.read({ archive.inputStream() }) { name, _ -> arrived += name }

        assertTrue("expected a failure", result.isFailure)
        assertTrue(
            "expected a newer-format failure, got ${result.exceptionOrNull()}",
            result.exceptionOrNull() is PkbFailure.NewerFormat,
        )
        assertEquals("nothing may be written before the version is known", emptyList<String>(), arrived)
    }

    /**
     * The attack this format is shaped to make impossible: an entry name that
     * walks out of the media directory and overwrites something else. Content
     * addressing means the only names we accept are ones we could have computed
     * ourselves, so a name from the file is never a path.
     */
    @Test
    fun refusesAnEntryTryingToEscapeTheMediaDirectory() {
        val archive = zipOf(
            PkbArchive.MANIFEST_ENTRY to manifestJson(PkbArchive.FORMAT_VERSION),
            PkbArchive.BOARDS_ENTRY to "{\"boards\":[]}".toByteArray(),
            PkbArchive.MEDIA_PREFIX + "../../evil" to photo,
        )

        val arrived = mutableListOf<String>()
        val result = PkbArchive.read({ archive.inputStream() }) { name, _ -> arrived += name }

        assertTrue(
            "expected an unsafe-entry failure, got ${result.exceptionOrNull()}",
            result.exceptionOrNull() is PkbFailure.UnsafeEntry,
        )
        assertEquals("a name we did not compute must never reach a caller", emptyList<String>(), arrived)
    }

    @Test
    fun storesOnePhotographOnceHoweverManyPictosUseIt() {
        val digest = Sha256.hex(photo)
        val written = ByteArrayOutputStream()

        PkbArchive.write(
            out = written,
            document = documentWithPhoto(digest),
            media = List(3) { PkbMedia(digest) { photo.inputStream() } },
        )

        val bytes = written.toByteArray()
        var handedOver = 0
        PkbArchive.read({ bytes.inputStream() }) { _, stream ->
            stream.readBytes()
            handedOver++
        }.getOrThrow()

        assertEquals(1, handedOver)
    }

    @Test
    fun refusesMediaWhoseBytesDoNotMatchTheirName() {
        val archive = zipOf(
            PkbArchive.MANIFEST_ENTRY to manifestJson(PkbArchive.FORMAT_VERSION),
            PkbArchive.BOARDS_ENTRY to "{\"boards\":[]}".toByteArray(),
            PkbArchive.MEDIA_PREFIX + Sha256.hex(photo) to byteArrayOf(9, 9, 9),
        )

        val result = PkbArchive.read({ archive.inputStream() }) { _, stream -> stream.readBytes() }

        assertTrue(
            "expected a malformed failure, got ${result.exceptionOrNull()}",
            result.exceptionOrNull() is PkbFailure.Malformed,
        )
    }

    /**
     * The likeliest way a real caregiver's file goes wrong: the copy to Drive
     * was interrupted, or the card was pulled. The zip machinery throws its own
     * exception here, and it has to reach the screen as something the app knows
     * how to say in Spanish rather than as a stack trace.
     */
    @Test
    fun refusesATruncatedArchive() {
        val digest = Sha256.hex(photo)
        val whole = ByteArrayOutputStream().also {
            PkbArchive.write(it, documentWithPhoto(digest), listOf(PkbMedia(digest) { photo.inputStream() }))
        }.toByteArray()
        val half = whole.copyOf(whole.size / 2)

        val result = PkbArchive.read({ half.inputStream() }) { _, stream -> stream.readBytes() }

        assertTrue(
            "expected a malformed failure, got ${result.exceptionOrNull()}",
            result.exceptionOrNull() is PkbFailure.Malformed,
        )
    }

    @Test
    fun refusesSomethingThatIsNotAnArchiveAtAll() {
        val notAZip = "a photograph someone renamed to .pkb".toByteArray()

        val result = PkbArchive.read({ notAZip.inputStream() }) { _, _ -> }

        assertTrue(
            "expected a malformed failure, got ${result.exceptionOrNull()}",
            result.exceptionOrNull() is PkbFailure.Malformed,
        )
    }

    private fun manifestJson(formatVersion: Int) = (
        """{"formatVersion":$formatVersion,"appVersion":"9.9","exportedAt":"2026-08-03T00:00:00Z",""" +
            """"boardCount":0,"categoryCount":0,"pictoCount":0,"mediaCount":1}"""
        ).toByteArray()

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun documentWithPhoto(digest: String) = PkbDocument(
        boards = listOf(
            PkbBoard(
                name = "Casa",
                colorArgb = -14405057,
                position = 0,
                language = "es",
                categories = listOf(
                    PkbCategory(
                        name = "Personas",
                        colorArgb = -14405057,
                        position = 0,
                        pictos = listOf(
                            PkbPicto(
                                label = "mamá",
                                spokenText = "mamá",
                                language = "es",
                                media = digest,
                                position = 0,
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )
}
