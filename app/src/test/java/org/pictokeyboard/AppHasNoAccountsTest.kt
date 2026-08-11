package org.pictokeyboard

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * This app has no account, no server and no sign-in, and this is what keeps it
 * that way.
 *
 * It began as `ImeHasNoSupabaseTest`, which asserted the same thing of the
 * keyboard package alone — the argument being that a token refresh, a paused
 * project or an expired session must never stand between a person and their
 * words, even while the rest of the app could sign in. #119 removed the accounts
 * entirely, so the guard widened to match: the scope is now every source file in
 * the application.
 *
 * A source-level check rather than a runtime one, because that is where the
 * mistake gets made. Somebody adds a dependency and an import, and everything
 * still compiles and still runs perfectly on a desk with wifi — the failure only
 * shows up on a caregiver's phone in a waiting room with no signal, which is
 * exactly where this app has to work.
 *
 * The forbidden list names *stacks*, not features. Restoring accounts is a
 * legitimate decision somebody may take later; doing it by accident, one import
 * at a time, is not. That work lives on the `marketplace` branch, where this
 * test does not.
 */
class AppHasNoAccountsTest {

    private companion object {
        /** Unit tests run with the module directory as the working directory. */
        const val APP_SOURCES = "src/main/java/org/pictokeyboard"

        val FORBIDDEN = listOf(
            "supabase",
            "org.pictokeyboard.data.auth",
            "io.ktor",
            "androidx.credentials",
            "com.google.android.libraries.identity",
        )
    }

    @Test
    fun `no source imports an authentication stack`() {
        val sources = File(APP_SOURCES)
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

        // A guard that passes because it found nothing is worse than no guard,
        // so the fixture proves itself before it proves anything else.
        check(sources.isNotEmpty()) { "found no sources under $APP_SOURCES -- has the package moved?" }

        val offenders = sources.filter { file ->
            file.readLines().any { line ->
                line.startsWith("import ") && FORBIDDEN.any { it in line }
            }
        }.map { it.name }

        assertEquals(
            "these files reach for an auth stack; this app signs nobody in",
            emptyList<String>(),
            offenders,
        )
    }
}
