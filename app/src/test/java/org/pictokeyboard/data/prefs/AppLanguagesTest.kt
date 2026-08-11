package org.pictokeyboard.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * #137: the app used to start in Spanish whatever the phone was set to.
 *
 * These run on the JVM with no device, which is the whole reason [AppLanguages]
 * takes a plain `List<Locale>` rather than the `LocaleList` the platform hands
 * it — the latter is stubbed out on the unit-test classpath and would assert
 * nothing.
 */
class AppLanguagesTest {

    @Test
    fun `a Spanish phone gets Spanish`() {
        assertEquals("es", AppLanguages.preferred(listOf(Locale("es", "ES"))))
    }

    @Test
    fun `an English phone gets English`() {
        assertEquals("en", AppLanguages.preferred(listOf(Locale("en", "GB"))))
    }

    /** The case that used to be answered in Spanish, which is the defect. */
    @Test
    fun `a phone in a language we do not speak gets English, not Spanish`() {
        assertEquals("en", AppLanguages.preferred(listOf(Locale("de", "DE"))))
        assertEquals("en", AppLanguages.preferred(listOf(Locale("fr", "FR"))))
        assertEquals("en", AppLanguages.preferred(listOf(Locale("ja", "JP"))))
    }

    /**
     * Android lets a user rank their languages, and ranking French above Spanish
     * while listing Spanish at all is a statement. Reading only the first entry
     * would throw that away and hand them the fallback.
     */
    @Test
    fun `the whole list is consulted, not just the first entry`() {
        assertEquals("es", AppLanguages.preferred(listOf(Locale("fr", "FR"), Locale("es", "AR"))))
        assertEquals("en", AppLanguages.preferred(listOf(Locale("fr", "FR"), Locale("en", "US"))))
    }

    /** Ranked first among ours wins, even with more of ours behind it. */
    @Test
    fun `the highest-ranked supported language wins`() {
        assertEquals("en", AppLanguages.preferred(listOf(Locale("en", "US"), Locale("es", "ES"))))
        assertEquals("es", AppLanguages.preferred(listOf(Locale("es", "ES"), Locale("en", "US"))))
    }

    /**
     * Latin American Spanish is Spanish. Matching whole tags rather than the
     * language would drop `es-419` and `es-MX` — a large share of the people
     * this app is for — into the English fallback.
     */
    @Test
    fun `regional variants land on their language`() {
        assertEquals("es", AppLanguages.preferred(listOf(Locale.forLanguageTag("es-419"))))
        assertEquals("es", AppLanguages.preferred(listOf(Locale("es", "MX"))))
        assertEquals("en", AppLanguages.preferred(listOf(Locale("en", "AU"))))
    }

    @Test
    fun `an empty list is English`() {
        assertEquals("en", AppLanguages.preferred(emptyList()))
    }

    /** What `locales_config.xml` and the `values-*` directories have to match. */
    @Test
    fun `only the languages the app is actually translated into are supported`() {
        assertEquals(listOf("es", "en"), AppLanguages.SUPPORTED)
    }
}
