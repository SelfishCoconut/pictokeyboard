package org.pictokeyboard.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import org.pictokeyboard.R
import org.pictokeyboard.data.pkb.PkbFailure
import org.pictokeyboard.ui.ConfigViewModel
import java.time.LocalDate

/*
 * The wiring the settings screen needs and a @Preview cannot supply: the two
 * file pickers a backup goes through, the permission the bell needs, and the
 * result types that carry an outcome back to the screen as a resource id rather
 * than as finished English.
 *
 * Kept beside SettingsScreen.kt rather than in it so that file stays the shape
 * of the screen.
 */

/** Dated, so a caregiver keeping several backups can tell them apart. */
private fun defaultBackupName(): String =
    "pictokeyboard-${LocalDate.now()}.pkb"

/** Whether the keyboard's bell can dial on its own, or only open the dialler. */
internal fun Context.canPlaceCalls(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Every reason an import can stop, in words the caregiver can act on. The
 * archive reports these as types precisely so this mapping can exist — an
 * English message out of an exception would reach a Spanish user untranslated.
 */
private fun Throwable.importFailureText(): Int = when (this) {
    is PkbFailure.NewerFormat -> R.string.settings_import_failed_newer
    is PkbFailure.UnsafeEntry -> R.string.settings_import_failed_unsafe
    else -> R.string.settings_import_failed
}

/**
 * How a backup turned out, held as a resource id and its arguments rather than
 * as finished text.
 *
 * Deliberately not a toast. A toast is gone before a caregiver reading the
 * screen with TalkBack reaches it, and the result of the only backup they have
 * is exactly the thing that must not evaporate. It is rendered as a live region
 * next to the buttons instead, and it stays there.
 */
data class BackupMessage(@StringRes val text: Int, val counts: BackupCounts? = null)

/** What a backup moved, in the order the sentence names them. */
data class BackupCounts(val boards: Int, val pictos: Int, val media: Int)

/** What the two backup buttons do, once their file pickers are registered. */
internal data class BackupActions(val save: () -> Unit, val restore: () -> Unit)

/**
 * The system file pickers a backup goes through, and what to say when they come
 * back.
 *
 * Through the picker rather than to a path of our own choosing so the archive
 * can land in Drive or Files, and not only on the phone that is about to break.
 */
@Composable
internal fun rememberBackupActions(
    viewModel: ConfigViewModel,
    onMessage: (BackupMessage) -> Unit,
): BackupActions {
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(PKB_MIME),
    ) { uri ->
        val out = uri?.let { context.contentResolver.openOutputStream(it) } ?: return@rememberLauncherForActivityResult
        viewModel.exportEverything(out) { result ->
            onMessage(
                result.fold(
                    onSuccess = {
                        BackupMessage(R.string.settings_export_done, BackupCounts(it.boards, it.pictos, it.media))
                    },
                    onFailure = { BackupMessage(R.string.settings_export_failed) },
                ),
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.importEverything(
            // Opened twice: the archive reads the manifest before it will write
            // a single photograph, so a file from a newer app imports none of
            // itself rather than half of it.
            source = { requireNotNull(context.contentResolver.openInputStream(uri)) },
        ) { result ->
            onMessage(
                result.fold(
                    onSuccess = {
                        BackupMessage(R.string.settings_import_done, BackupCounts(it.boards, it.pictos, it.media))
                    },
                    onFailure = { BackupMessage(it.importFailureText()) },
                ),
            )
        }
    }

    return BackupActions(
        save = { exportLauncher.launch(defaultBackupName()) },
        restore = { importLauncher.launch(arrayOf(PKB_MIME, "application/zip", "*/*")) },
    )
}
