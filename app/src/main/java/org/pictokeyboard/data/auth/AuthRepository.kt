package org.pictokeyboard.data.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Every call into Supabase Auth, in one place.
 *
 * Constructed even when the build carries no credentials — it then holds no
 * client and reports [AccountState.Unavailable] forever. That keeps
 * `ServiceLocator` free of a nullable service every caller would have to
 * remember to check, and makes "no backend" a state rather than an absence.
 *
 * **Lives in the config app only.** The IME must never construct or import
 * this: PictoKeyboard is how someone speaks, and a token refresh must never be
 * able to stand between a person and their words. `ImeHasNoSupabaseTest`
 * asserts that no keyboard source reaches it.
 */
class AuthRepository(config: SupabaseConfig, scope: CoroutineScope) {

    val client: SupabaseClient? = if (!config.isConfigured) {
        null
    } else {
        createSupabaseClient(config.url, config.anonKey) {
            install(Auth)
        }
    }

    val state: StateFlow<AccountState> = when (val supabase = client) {
        null -> MutableStateFlow(AccountState.Unavailable)
        else ->
            supabase.auth.sessionStatus
                .map { status ->
                    when (status) {
                        is SessionStatus.Authenticated ->
                            accountStateOf(true, SessionSnapshot(status.session.user?.email))
                        // Everything else -- refreshing, signed out, or a session
                        // that could not be restored -- is signed out as far as the
                        // UI is concerned. Only a live session means signed in.
                        is SessionStatus.Initializing -> AccountState.Loading
                        else -> AccountState.SignedOut
                    }
                }
                // Eagerly: the account row in Settings must not have to subscribe
                // before it knows whether to draw itself.
                .stateIn(scope, SharingStarted.Eagerly, AccountState.Loading)
    }

    suspend fun signUp(email: String, password: String): Result<Unit> = call {
        it.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = call {
        it.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun sendRecoveryEmail(email: String): Result<Unit> = call {
        it.auth.resetPasswordForEmail(email)
    }

    /**
     * Signing out is not a delete.
     *
     * Every board stays on this device and the app returns to behaving exactly
     * as it does for someone who never signed in. The button says so, because
     * read the other way this would be the most frightening control in the app.
     */
    suspend fun signOut(): Result<Unit> = call { it.auth.signOut() }

    /**
     * Runs [block] against the client, turning both "no client" and any thrown
     * network or credential error into a [Result] the caller must handle.
     *
     * Nothing here swallows a failure: a caller that drops the Result is the
     * bug, and there are none.
     */
    private suspend fun call(block: suspend (SupabaseClient) -> Unit): Result<Unit> {
        val supabase = client ?: return Result.failure(IllegalStateException("Supabase not configured"))
        return runCatching { block(supabase) }
    }
}
