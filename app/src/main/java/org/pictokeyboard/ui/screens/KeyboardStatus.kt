package org.pictokeyboard.ui.screens

import android.content.ComponentName
import android.content.Context
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

/** Re-reads the keyboard status every time the screen resumes (e.g. back from system settings). */
@Composable
internal fun rememberKeyboardStatus(): KeyboardStatus {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var status by remember { mutableStateOf(readKeyboardStatus(context)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) status = readKeyboardStatus(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return status
}
