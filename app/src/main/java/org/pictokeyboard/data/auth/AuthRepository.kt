package org.pictokeyboard.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.pictokeyboard.BuildConfig
import java.security.MessageDigest
import java.util.UUID

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

    /**
     * Null when this build has no Google OAuth client, which hides the button
     * rather than disabling it — a disabled control asks the caregiver to work
     * out what is wrong with their phone.
     */
    val googleServerClientId: String? = BuildConfig.GOOGLE_SERVER_CLIENT_ID.takeIf { it.isNotBlank() }

    val client: SupabaseClient? = if (!config.isConfigured) {
        null
    } else {
        // Auth only. The ComposeAuth plugin used to be installed here to drive
        // Google sign-in; #93 replaced it with a direct Credential Manager call,
        // because its result type cannot tell a missing Google account apart
        // from a caregiver dismissing the sheet.
        createSupabaseClient(config.url, config.anonKey) {
            install(Auth)
            // Only for delete-account. Removing an auth.users row needs the
            // secret key, which must never be in the APK, so the one thing this
            // client cannot do itself it asks an Edge Function to do. See #83.
            install(Functions)
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
     * Google sign-in, asked of Credential Manager directly.
     *
     * `compose-auth`'s `rememberSignInWithGoogle` would be less code, but it
     * reports a phone with **no Google account** as `ClosedByUser` — the same
     * case as a caregiver dismissing the sheet. One of those needs a sentence
     * and the other needs silence, so the distinction has to survive as far as
     * the caller, and only the raw exception carries it. See #93.
     *
     * The nonce ties the token Google issues to this one request: it is sent to
     * Google hashed and to Supabase raw, so a token lifted from another exchange
     * cannot be replayed into this one.
     */
    suspend fun signInWithGoogle(context: Context): Result<Unit> {
        val supabase = client ?: return Result.failure(IllegalStateException("Supabase not configured"))
        val serverClientId = googleServerClientId
            ?: return Result.failure(IllegalStateException("No Google OAuth client in this build"))

        val rawNonce = UUID.randomUUID().toString()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setServerClientId(serverClientId)
                    // False on purpose: filtering to accounts that have already
                    // used this app leaves a first-time caregiver with an empty
                    // sheet, which is the same dead end in a different costume.
                    .setFilterByAuthorizedAccounts(false)
                    .setNonce(sha256(rawNonce))
                    .build(),
            )
            .build()

        return runCatching {
            val response = CredentialManager.create(context).getCredential(context, request)
            val googleId = GoogleIdTokenCredential.createFrom(response.credential.data)
            supabase.auth.signInWith(IDToken) {
                idToken = googleId.idToken
                provider = Google
                nonce = rawNonce
            }
        }
    }

    /**
     * Deleting the account, which is the one thing here that cannot be undone.
     *
     * The work happens in the `delete-account` Edge Function because removing an
     * `auth.users` row needs the secret key, and the secret key must never be in
     * the APK. The caller is taken from the JWT on this request, so this cannot
     * be asked to delete anyone else — there is deliberately no user id to pass.
     *
     * The sign-out afterwards is **local on purpose**. A global sign-out asks
     * the server to revoke a session belonging to a user that no longer exists;
     * that call can fail, and a failure there would be reported as a failed
     * deletion after the account had already gone — the one outcome worse than
     * either success or failure on its own.
     */
    suspend fun deleteAccount(): Result<Unit> = call { supabase ->
        val response = supabase.functions.invoke("delete-account")
        check(response.status.isSuccess()) {
            "delete-account returned ${response.status.value}"
        }
        supabase.auth.signOut(SignOutScope.LOCAL)
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

/** Lowercase hex SHA-256, the form Google expects a nonce to arrive in. */
private fun sha256(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
