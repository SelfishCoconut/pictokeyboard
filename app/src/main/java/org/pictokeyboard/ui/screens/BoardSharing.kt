package org.pictokeyboard.ui.screens

import android.content.Context
import android.content.Intent
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import org.pictokeyboard.R
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * `.pkb` has no registered media type, so it travels as a generic binary and the
 * extension carries the meaning.
 *
 * Anything narrower would have Drive and Files refuse to hand the file back on
 * import, and would cut the share sheet down to the apps that claim a type
 * nobody registers.
 */
const val PKB_MIME = "application/octet-stream"

/**
 * Sending a board to somebody else.
 *
 * With no server there is no "publish" and no link — a caregiver who has built a
 * good board hands the file over themselves, through whatever they already use
 * to talk to the person they are giving it to. That is WhatsApp, or Gmail, or
 * Drive, or Nearby Share, and the system share sheet is how you reach all of
 * them without naming any of them.
 *
 * The whole-device backup in Settings deliberately does *not* go through here.
 * It writes through the file picker, because a backup's destination is a place
 * that will still exist when the phone does not — a memory card, a Drive folder
 * — and the share sheet cannot reach a memory card.
 */
object BoardSharing {

    /**
     * Under `cacheDir`, not `filesDir`: an exported board is a copy the system
     * may delete the moment the share is over, and the original is still in the
     * database. It is declared in `file_paths.xml` so `FileProvider` will grant
     * a read on it.
     */
    private const val SHARE_DIR = "board-exports"

    /**
     * Long enough that the receiving app has certainly finished reading, short
     * enough that a caregiver's cache does not accumulate every board they have
     * ever sent. A share cannot delete its own file — the other app reads the
     * uri after the chooser returns — so the sweep happens on the way in.
     */
    private val STALE_AFTER_MS = TimeUnit.HOURS.toMillis(1)

    /**
     * Everything a filesystem or another app might treat as structure rather
     * than as a name. Board names are typed by caregivers and reach us as
     * anything at all; `Comidas / bebidas` is a perfectly reasonable board name
     * and an unreasonable path.
     */
    private val UNSAFE_IN_NAME = Regex("""[^\p{L}\p{N} _-]""")

    /** How much of a board's name survives into the filename. */
    private const val MAX_NAME_LENGTH = 40

    /**
     * A file to export [boardName] into, in a swept directory.
     *
     * Dated, so a caregiver who exports the same board twice can tell which one
     * they just made.
     */
    fun fileFor(context: Context, boardName: String): File {
        val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        sweepStale(dir)
        return File(dir, "${safeName(boardName)}-${LocalDate.now()}.pkb")
    }

    /**
     * Hands [file] to the share sheet, titled with [boardName].
     *
     * `FLAG_GRANT_READ_URI_PERMISSION` is what makes the receiving app able to
     * open the uri at all; the provider is not exported, so without it every
     * target gets a `SecurityException` instead of a board.
     *
     * The chooser's title is resolved here rather than passed in. The caller is
     * a Composable, and reading a formatted string out of `LocalContext` there
     * is what `LocalContextGetResourceValueCall` exists to stop — the value
     * would be read once and then survive a locale change that recomposed
     * everything around it. This object is plain Kotlin holding the same
     * context, and resolves it at the moment the sheet opens.
     */
    fun share(context: Context, file: File, boardName: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = ShareCompat.IntentBuilder(context)
            .setType(PKB_MIME)
            .setStream(uri)
            // Subject fills an email's subject line; chooser title heads the
            // sheet itself. Both name the board, because "Send Casa" is what a
            // caregiver needs to confirm they picked the right one before they
            // pick a person to send it to.
            .setSubject(boardName)
            .setChooserTitle(context.getString(R.string.boards_share_chooser, boardName))
            .createChooserIntent()
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
    }

    /**
     * A board name reduced to something safe to put on a filesystem, or a plain
     * fallback when nothing of it survives — a board named entirely in emoji
     * still has to export.
     */
    private fun safeName(boardName: String): String =
        boardName.replace(UNSAFE_IN_NAME, " ")
            .trim()
            .replace(Regex(" +"), "-")
            .take(MAX_NAME_LENGTH)
            .ifBlank { "pictokeyboard" }

    private fun sweepStale(dir: File) {
        val cutoff = System.currentTimeMillis() - STALE_AFTER_MS
        dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
    }
}
