package org.pictokeyboard.ui.screens

import android.content.ComponentName
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

// Whether the keyboard is live, read from the system rather than from our own
// state -- the user enables and selects it in Android settings, and can turn it
// off there at any time without the app being told. Split out of the dashboard
// when that screen became the Boards tab (#32); the Boards tab shows the setup
// card only while this reports something still to do.

/** Whether the PictoKeyboard IME is currently enabled / selected as active. */
data class KeyboardStatus(val enabled: Boolean, val selected: Boolean) {
    val ready: Boolean get() = enabled && selected
}

private fun readKeyboardStatus(context: Context): KeyboardStatus {
    val imeId = ComponentName(
        context.packageName,
        "org.pictokeyboard.ime.PictoKeyboardService",
    ).flattenToShortString()
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val enabled = imm.enabledInputMethodList.any { it.id == imeId }
    val selected = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.DEFAULT_INPUT_METHOD,
    ) == imeId
    return KeyboardStatus(enabled, selected)
}

/**
 * The keyboard status, kept current while the screen is on it.
 *
 * Two sources, because the two setup steps leave by different doors.
 *
 * **Enabling** goes to system settings, which is another activity: the app
 * pauses and resumes, and the resume re-reads. **Selecting** does not go
 * anywhere — `showInputMethodPicker()` is a system dialog drawn over our own
 * activity, which therefore never pauses and never resumes. Picking
 * PictoKeyboard in it changed the setting and left the screen still insisting
 * the user go and pick it (#133); the only way out was to go and type in some
 * other app, which is what finally produced a resume.
 *
 * So the setting itself is watched. A [ContentObserver] on
 * `DEFAULT_INPUT_METHOD` reports the change as it happens, whoever made it —
 * the picker, system settings, or the user switching keyboards from the
 * notification shade.
 */
@Composable
internal fun rememberKeyboardStatus(): KeyboardStatus {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var status by remember { mutableStateOf(readKeyboardStatus(context)) }
    DisposableEffect(owner, context) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) status = readKeyboardStatus(context)
        }
        owner.lifecycle.addObserver(lifecycleObserver)

        val resolver = context.contentResolver
        val settingObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                status = readKeyboardStatus(context)
            }
        }
        resolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD),
            false,
            settingObserver,
        )

        onDispose {
            owner.lifecycle.removeObserver(lifecycleObserver)
            resolver.unregisterContentObserver(settingObserver)
        }
    }
    return status
}
