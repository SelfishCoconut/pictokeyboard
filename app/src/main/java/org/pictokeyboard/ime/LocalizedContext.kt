package org.pictokeyboard.ime

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate

/**
 * The app's chosen UI language, as a single language tag, or null when nothing
 * has been chosen and the system locale should stand.
 *
 * Reads the same store the config app writes through
 * `AppCompatDelegate.setApplicationLocales`, which works from any component in
 * the process -- a service included. What does *not* work from a service is the
 * automatic application of it: below API 33 appcompat backports the per-app
 * locale by hooking `AppCompatActivity.attachBaseContext`, so an
 * `InputMethodService` never sees it. On API 33+ the platform `LocaleManager`
 * does apply process-wide and this becomes a no-op that agrees with itself.
 */
fun currentAppLanguage(): String? =
    AppCompatDelegate.getApplicationLocales().toLanguageTags().takeIf { it.isNotBlank() }

/**
 * A context whose resources resolve in [language].
 *
 * The keyboard needs this for both `getString` *and* the `LayoutInflater` that
 * builds the key row -- miss either and the keys disagree with the toasts. That
 * is the shape the bug had: on a device set to `en-US` with the board language
 * Spanish, the caregiver's screens were fully Spanish while the key the AAC user
 * actually looks at said `space` instead of `espacio`.
 *
 * Which is the wrong side for it to land on. The caregiver configures in one
 * language and the person with the impairment is shown another, on the one
 * surface they use constantly and are least able to reinterpret -- and because
 * category names and picto labels come from the database, they *are* Spanish,
 * so the single English key reads as a defect rather than as a setting.
 */
fun Context.localizedFor(language: String?): Context {
    if (language == null) return this
    val config = Configuration(resources.configuration).apply {
        setLocales(LocaleList.forLanguageTags(language))
    }
    return createConfigurationContext(config)
}
