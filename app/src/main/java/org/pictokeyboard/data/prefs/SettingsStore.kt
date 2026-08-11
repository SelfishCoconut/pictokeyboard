package org.pictokeyboard.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Everything that describes the *person* holding the phone.
 *
 * Columns, rows, captions, frame defaults and the vocabulary's language used to
 * live here too, and moved onto `BoardEntity` in #31. They describe the
 * *situation*: a doctor board wants 3x3 huge tiles and a chat board 5x5, and one
 * global value forced every board to compromise.
 */
data class Settings(
    /**
     * Language of the config app's own interface.
     *
     * No longer the language of the vocabulary — that is `BoardEntity.language`,
     * per board. A caregiver may well run the app in Spanish while building an
     * English board for school.
     *
     * Defaulted from the phone rather than to a constant (#137). This value is
     * not only a placeholder: it is what both `ConfigViewModel.settings` and the
     * keyboard show until DataStore has read from disk, and `MainActivity` hands
     * it straight to `setApplicationLocales` — which **restarts the activity**.
     * A constant that disagreed with the stored value would therefore cost a
     * visible flash of the wrong language and a second recreate on every cold
     * start, so the placeholder has to be the same answer the store will give.
     */
    val defaultLanguage: String = AppLanguages.systemDefault(),
    val addSpaceAfter: Boolean = true,
    val speakOnTap: Boolean = true,
    val ttsRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val hasPin: Boolean = false,
    /** Eyes-free gesture keyboard. Toggled by a two-finger double-tap. */
    val blindMode: Boolean = false,
    /**
     * A short buzz confirming that a key was pressed.
     *
     * On by default, because for a user who cannot read the text field to check,
     * touch is the confirmation channel that does not depend on vision or
     * literacy. It is still a *second* channel — the pressed face carries the
     * same message — so turning it off loses nothing for a user who can see.
     *
     * The system-wide haptic setting is honoured independently of this one: this
     * turns haptics off for the keyboard alone, without touching a device
     * setting that may be someone's only feedback everywhere else.
     */
    val hapticFeedback: Boolean = true,
    /**
     * Withdraws every contrast concession the palette makes (#109).
     *
     * Off by default: the soft palette is the right default and this is the
     * right option, not the other way round. It reaches the keyboard as well as
     * the app, because the person who needs it is holding the keyboard.
     */
    val highContrast: Boolean = false,
)

/**
 * Layout values written by a version of the app that had no boards. Every field
 * is nullable because the user may have changed some and not others, and a
 * value that was never set must not overwrite the board's own default with a
 * guess.
 */
data class LegacyBoardLayout(val columns: Int?, val rows: Int?, val showLabels: Boolean?, val language: String?)

/**
 * Single-profile settings backed by DataStore. The admin PIN is stored only as
 * a salted SHA-256 hash; the raw PIN is never persisted.
 */
class SettingsStore(private val context: Context) {

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            // Absent until the caregiver picks one, so an install that has never
            // been told otherwise follows the phone (#137) rather than sitting
            // on a hardcoded "es". Writing a resolved value here instead would
            // freeze the very first launch's answer forever.
            defaultLanguage = p[KEY_LANGUAGE] ?: AppLanguages.systemDefault(),
            addSpaceAfter = p[KEY_ADD_SPACE] ?: true,
            speakOnTap = p[KEY_SPEAK] ?: true,
            ttsRate = p[KEY_TTS_RATE] ?: 1.0f,
            ttsPitch = p[KEY_TTS_PITCH] ?: 1.0f,
            hasPin = p[KEY_PIN_HASH] != null,
            blindMode = p[KEY_BLIND_MODE] ?: false,
            hapticFeedback = p[KEY_HAPTICS] ?: true,
            highContrast = p[KEY_HIGH_CONTRAST] ?: false,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setDefaultLanguage(value: String) = edit { it[KEY_LANGUAGE] = value }
    suspend fun setAddSpaceAfter(value: Boolean) = edit { it[KEY_ADD_SPACE] = value }
    suspend fun setSpeakOnTap(value: Boolean) = edit { it[KEY_SPEAK] = value }
    suspend fun setTtsRate(value: Float) = edit { it[KEY_TTS_RATE] = value.coerceIn(0.5f, 2.0f) }
    suspend fun setTtsPitch(value: Float) = edit { it[KEY_TTS_PITCH] = value.coerceIn(0.5f, 2.0f) }
    suspend fun setBlindMode(value: Boolean) = edit { it[KEY_BLIND_MODE] = value }
    suspend fun setHapticFeedback(value: Boolean) = edit { it[KEY_HAPTICS] = value }
    suspend fun setHighContrast(value: Boolean) = edit { it[KEY_HIGH_CONTRAST] = value }

    // --- Layout values inherited from before boards existed ------------------

    /**
     * The grid layout a pre-#31 install kept globally, if it ever set one.
     *
     * The Room migration that creates the first board cannot read this: these
     * values are in DataStore, and a `SupportSQLiteDatabase` has no way to
     * reach it. So the migration seeds the board at the schema defaults and the
     * app adopts the real values on next start, which is the only way the
     * promise that "the keyboard behaves identically after upgrading" is
     * actually kept for someone who had set 6 columns.
     *
     * Returns null when none of the keys were ever written — a fresh install,
     * where the board's own defaults are already correct.
     */
    suspend fun legacyBoardLayout(): LegacyBoardLayout? {
        val p = context.dataStore.data.first()
        val columns = p[KEY_COLUMNS]
        val rows = p[KEY_ROWS]
        val showLabels = p[KEY_SHOW_LABELS]
        if (columns == null && rows == null && showLabels == null) return null
        return LegacyBoardLayout(
            columns = columns,
            rows = rows,
            showLabels = showLabels,
            language = p[KEY_LANGUAGE],
        )
    }

    /**
     * Drops the migrated keys once a board owns them.
     *
     * `KEY_LANGUAGE` deliberately survives: it goes on being the *interface*
     * language, and is only copied to the board rather than moved.
     */
    suspend fun clearLegacyBoardLayout() = edit {
        it.remove(KEY_COLUMNS)
        it.remove(KEY_ROWS)
        it.remove(KEY_SHOW_LABELS)
    }

    // --- PIN ---------------------------------------------------------------

    suspend fun hasPin(): Boolean =
        context.dataStore.data.first()[KEY_PIN_HASH] != null

    suspend fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val saltHex = salt.toHex()
        edit {
            it[KEY_PIN_SALT] = saltHex
            it[KEY_PIN_HASH] = hashPin(pin, saltHex)
        }
    }

    suspend fun clearPin() = edit {
        it.remove(KEY_PIN_HASH)
        it.remove(KEY_PIN_SALT)
    }

    suspend fun verifyPin(pin: String): Boolean {
        val p = context.dataStore.data.first()
        val salt = p[KEY_PIN_SALT] ?: return false
        val stored = p[KEY_PIN_HASH] ?: return false
        return hashPin(pin, salt) == stored
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private fun hashPin(pin: String, saltHex: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(saltHex.toByteArray())
        return md.digest(pin.toByteArray()).toHex()
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }

    companion object {
        private val KEY_LANGUAGE = stringPreferencesKey("language")
        private val KEY_COLUMNS = intPreferencesKey("grid_columns")
        private val KEY_ROWS = intPreferencesKey("grid_rows")
        private val KEY_SHOW_LABELS = booleanPreferencesKey("show_labels")
        private val KEY_ADD_SPACE = booleanPreferencesKey("add_space")
        private val KEY_SPEAK = booleanPreferencesKey("speak_on_tap")
        private val KEY_TTS_RATE = floatPreferencesKey("tts_rate")
        private val KEY_TTS_PITCH = floatPreferencesKey("tts_pitch")
        private val KEY_PIN_HASH = stringPreferencesKey("pin_hash")
        private val KEY_PIN_SALT = stringPreferencesKey("pin_salt")
        private val KEY_BLIND_MODE = booleanPreferencesKey("blind_mode")
        private val KEY_HAPTICS = booleanPreferencesKey("haptic_feedback")
        private val KEY_HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
    }
}
