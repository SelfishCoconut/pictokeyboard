package org.pictokeyboard.data.auth

import org.pictokeyboard.BuildConfig

/**
 * Whether this build is wired to a Supabase project, and to which one.
 *
 * A build with no credentials is normal and supported: a fresh clone, a fork,
 * and every CI run are all in that state. Accounts then do not appear in the UI
 * at all rather than appearing and failing — which is only tolerable because
 * nothing in this app requires an account. The keyboard is how someone speaks,
 * and it works signed out, offline, forever.
 *
 * The anon key is public by design; row-level security is the boundary, not
 * secrecy. It is still kept out of the repository so a fork does not silently
 * inherit this project's backend and its storage quota.
 */
data class SupabaseConfig(val url: String, val anonKey: String) {

    /**
     * Blank rather than empty, because a stray space in `local.properties`
     * survives `Properties` parsing intact and would otherwise read as a
     * credential — producing exactly the visible-but-dead account UI this
     * type exists to prevent.
     */
    val isConfigured: Boolean = url.isNotBlank() && anonKey.isNotBlank()

    companion object {
        fun fromBuildConfig(): SupabaseConfig =
            SupabaseConfig(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
    }
}
