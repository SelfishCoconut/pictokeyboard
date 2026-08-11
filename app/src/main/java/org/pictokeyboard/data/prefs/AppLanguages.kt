package org.pictokeyboard.data.prefs

import android.content.res.Resources
import java.util.Locale

/**
 * The two languages this app is actually written in, and how a fresh install
 * picks between them.
 *
 * Kept beside [SettingsStore] rather than inside it because the interesting part
 * — which of the phone's languages wins — is a pure function of a list of
 * locales, and everything worth asserting about it can then be asserted without
 * a device, a DataStore or a stubbed `LocaleList` (#137).
 */
object AppLanguages {

    const val SPANISH = "es"
    const val ENGLISH = "en"

    /**
     * Must stay in step with `res/xml/locales_config.xml` and the
     * `values-*` string directories. A language named here with no translation
     * behind it is worse than one left out: the user would pick a language and
     * the app would answer in another one.
     */
    val SUPPORTED = listOf(SPANISH, ENGLISH)

    /**
     * The language a first run starts in: the phone's, when the phone speaks one
     * of ours, and English otherwise.
     *
     * **English rather than Spanish as the fallback**, which is the change #137
     * asks for. Spanish was hardcoded, so a phone set to English — or to German,
     * or to anything at all — got a Spanish interface over a board of Spanish
     * words. The vocabulary is the product, and a starter board in a language the
     * caregiver cannot read decides whether the app is worth setting up before
     * they have tapped anything.
     *
     * Read from the **system** configuration, never from the app's own. The
     * per-app language API stores its choice and appcompat restores it in
     * `attachBaseContext`, so by the time this runs the app's own configuration
     * reports the stored value back — a default that feeds on its own output
     * would never track the phone again.
     *
     * The null guard is for the JVM unit-test classpath, where `android.jar` is
     * stubbed and every static hands back a default. Answering [ENGLISH] there
     * is right for the same reason it is right on a French phone.
     */
    fun systemDefault(): String {
        val locales = Resources.getSystem()?.configuration?.locales ?: return ENGLISH
        return preferred((0 until locales.size()).map { locales[it] })
    }

    /**
     * The first supported language in [locales], or [ENGLISH] if it names none.
     *
     * The **whole** list is walked rather than just the first entry. Android lets
     * a user rank their languages, and someone whose phone reads French, then
     * Spanish has said something specific: give them Spanish, not the fallback.
     *
     * Matched on [Locale.getLanguage] rather than the whole tag, so `es-419`,
     * `es-MX` and `en-GB` land on the language they are written in instead of
     * falling through.
     */
    fun preferred(locales: List<Locale>): String =
        locales.firstNotNullOfOrNull { locale -> locale.language.takeIf { it in SUPPORTED } }
            ?: ENGLISH
}
