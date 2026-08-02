package org.pictokeyboard.ime

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.pictokeyboard.R

/**
 * The keyboard's own localization, which is the half of the per-app language
 * fix that the platform does not do for us.
 *
 * Below API 33 appcompat backports the per-app locale by hooking
 * `AppCompatActivity.attachBaseContext`, so an `InputMethodService` never sees
 * it and gets no configuration change to react to. The keyboard therefore builds
 * its own localized context — and the bug this guards against is subtle: it has
 * to cover both `getString` *and* the `LayoutInflater`, because missing either
 * makes the keys and the toasts disagree.
 *
 * That was #23's exact symptom. On a device set to `en-US` with the board
 * language Spanish, the caregiver's screens were fully Spanish while the key the
 * AAC user actually looks at said `space` instead of `espacio`.
 */
@RunWith(AndroidJUnit4::class)
class LocalizedContextTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun spanishResolvesSpanishStrings() {
        val es = context.localizedFor("es")
        assertEquals("espacio", es.getString(R.string.kb_space))
    }

    @Test
    fun englishResolvesEnglishStrings() {
        val en = context.localizedFor("en")
        assertEquals("space", en.getString(R.string.kb_space))
    }

    @Test
    fun theTwoLanguagesActuallyDiffer() {
        // Guards the test itself: if values-es ever stopped being packaged, every
        // assertion above would still pass by both resolving to English.
        assertNotEquals(
            context.localizedFor("en").getString(R.string.kb_space),
            context.localizedFor("es").getString(R.string.kb_space),
        )
    }

    @Test
    fun aNullLanguageLeavesTheContextAlone() {
        // No choice made means the system locale stands, rather than the app
        // silently forcing one.
        assertTrue(context.localizedFor(null) === context)
    }

    @Test
    fun theInflaterResolvesTheSameLanguageAsGetString() {
        // The failure mode the whole design is shaped around: a localized
        // getString with a system-locale LayoutInflater gives a keyboard whose
        // keys and whose toasts are in different languages.
        val es = context.localizedFor("es")
        val inflated = android.view.LayoutInflater.from(context)
            .cloneInContext(es)
            .inflate(R.layout.keyboard_view, null)
        val spaceKey = inflated.findViewById<android.widget.Button>(R.id.key_space)
        assertEquals(es.getString(R.string.kb_space), spaceKey.text.toString())
        assertEquals("espacio", spaceKey.text.toString())
    }
}
