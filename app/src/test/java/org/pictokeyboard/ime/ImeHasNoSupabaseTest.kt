package org.pictokeyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * PictoKeyboard is how someone speaks. If a token refresh, a paused Supabase
 * project or an expired session can stand between a person and their words,
 * the product has failed at the only thing it does.
 *
 * So the keyboard does not link the auth stack at all — not lazily, not behind
 * a flag. `ServiceLocator` is shared with the IME, so the account repository
 * being *reachable* is not the same as it being used; this asserts nothing
 * under `ime/` reaches for it.
 *
 * A source-level check rather than a runtime one, because that is where the
 * mistake gets made: someone adds an import to a keyboard file, and everything
 * still compiles and still runs perfectly on a desk with wifi.
 */
class ImeHasNoSupabaseTest {

    private companion object {
        /** Unit tests run with the module directory as the working directory. */
        const val IME_SOURCES = "src/main/java/org/pictokeyboard/ime"

        val FORBIDDEN = listOf("supabase", "org.pictokeyboard.data.auth", "io.ktor")
    }

    @Test
    fun `no keyboard source imports the auth stack`() {
        val sources = File(IME_SOURCES)
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

        // A guard that passes because it found nothing is worse than no guard,
        // so the fixture proves itself before it proves anything else.
        check(sources.isNotEmpty()) { "found no IME sources under $IME_SOURCES -- has the package moved?" }

        val offenders = sources.filter { file ->
            file.readLines().any { line ->
                line.startsWith("import ") && FORBIDDEN.any { it in line }
            }
        }.map { it.name }

        assertEquals(
            "these keyboard files reach into the auth stack, which must never happen",
            emptyList<String>(),
            offenders,
        )
    }
}
