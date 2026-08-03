package org.pictokeyboard.data.auth

/**
 * Who, if anyone, is signed in — expressed without a single Supabase type.
 *
 * Two things follow from that. The Compose layer cannot come to depend on the
 * auth library, so swapping or removing it stays a change to one package; and
 * the mapping below is a pure function, so it can be proved on the JVM instead
 * of on a device with a live project behind it.
 */
sealed interface AccountState {

    /**
     * This build has no Supabase project behind it. Accounts are not offered at
     * all rather than offered and broken — see [SupabaseConfig].
     */
    data object Unavailable : AccountState

    /** The stored session has not been read back yet. The first frame only. */
    data object Loading : AccountState

    /**
     * Configured, nobody signed in.
     *
     * The app's normal, permanent, fully supported state — not a state to be
     * nagged out of. A caregiver who never signs in loses nothing but the
     * backup.
     */
    data object SignedOut : AccountState

    /** [email] is null when the provider returned no email claim. */
    data class SignedIn(val email: String?) : AccountState
}

/**
 * The parts of a Supabase session this app cares about, as a plain value.
 *
 * Exists so [accountStateOf] can be tested: `SessionStatus` cannot be built in
 * a JVM test without a client.
 */
data class SessionSnapshot(val email: String?)

/**
 * `configured` wins over the session, because a build with no backend has no
 * business showing an account whatever happens to be cached on the device.
 */
fun accountStateOf(configured: Boolean, session: SessionSnapshot?): AccountState = when {
    !configured -> AccountState.Unavailable
    session == null -> AccountState.SignedOut
    else -> AccountState.SignedIn(session.email)
}
