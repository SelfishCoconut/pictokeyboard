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

/** Snapshot of all user-tunable keyboard settings. */
data class Settings(
    val defaultLanguage: String = "es",
    val gridColumns: Int = 4,
    val gridRows: Int = 4,
    val showLabels: Boolean = true,
    val addSpaceAfter: Boolean = true,
    val speakOnTap: Boolean = true,
    val ttsRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val hasPin: Boolean = false,
    /** Eyes-free gesture keyboard. Toggled by a two-finger double-tap. */
    val blindMode: Boolean = false,
)

/**
 * Single-profile settings backed by DataStore. The admin PIN is stored only as
 * a salted SHA-256 hash; the raw PIN is never persisted.
 */
class SettingsStore(private val context: Context) {

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            defaultLanguage = p[KEY_LANGUAGE] ?: "es",
            gridColumns = p[KEY_COLUMNS] ?: 4,
            gridRows = p[KEY_ROWS] ?: 4,
            showLabels = p[KEY_SHOW_LABELS] ?: true,
            addSpaceAfter = p[KEY_ADD_SPACE] ?: true,
            speakOnTap = p[KEY_SPEAK] ?: true,
            ttsRate = p[KEY_TTS_RATE] ?: 1.0f,
            ttsPitch = p[KEY_TTS_PITCH] ?: 1.0f,
            hasPin = p[KEY_PIN_HASH] != null,
            blindMode = p[KEY_BLIND_MODE] ?: false,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setDefaultLanguage(value: String) = edit { it[KEY_LANGUAGE] = value }
    suspend fun setGridColumns(value: Int) = edit { it[KEY_COLUMNS] = value.coerceIn(2, 6) }
    suspend fun setGridRows(value: Int) = edit { it[KEY_ROWS] = value.coerceIn(2, 8) }
    suspend fun setShowLabels(value: Boolean) = edit { it[KEY_SHOW_LABELS] = value }
    suspend fun setAddSpaceAfter(value: Boolean) = edit { it[KEY_ADD_SPACE] = value }
    suspend fun setSpeakOnTap(value: Boolean) = edit { it[KEY_SPEAK] = value }
    suspend fun setTtsRate(value: Float) = edit { it[KEY_TTS_RATE] = value.coerceIn(0.5f, 2.0f) }
    suspend fun setTtsPitch(value: Float) = edit { it[KEY_TTS_PITCH] = value.coerceIn(0.5f, 2.0f) }
    suspend fun setBlindMode(value: Boolean) = edit { it[KEY_BLIND_MODE] = value }

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
    }
}
