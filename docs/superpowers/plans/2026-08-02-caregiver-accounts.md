# Caregiver Accounts (#79) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A caregiver can sign in to PictoKeyboard with Google or with an email
and password, reset a forgotten password, and sign out — with no board data
moving anywhere yet.

**Architecture:** A new `data/auth` package wraps `supabase-kt`'s `Auth` plugin
behind an `AuthRepository` that exposes one `StateFlow<AccountState>`. The UI
layer never sees a Supabase type. The client is built lazily in `ServiceLocator`
and is **absent entirely** when the build carries no Supabase credentials, so a
fork, a CI run and a contributor's first clone all still build and run.

**Tech Stack:** supabase-kt 3.7.0 (`auth-kt`, `compose-auth`), Ktor OkHttp
engine, Jetpack Compose, Navigation Compose, existing manual `ServiceLocator` DI.

## Global Constraints

- **The IME must not link Supabase.** No import of `io.github.jan.supabase.*`
  anywhere under `org.pictokeyboard.ime`. Task 7 enforces this with a test.
- **Signed out is a first-class state.** Nothing in the app may require an
  account to work. No screen gains a sign-in wall.
- **The `service_role` key never enters the repository or the APK.** Only the
  `anon` key, which is public by design because RLS is the security boundary.
- **Credentials arrive through `local.properties` → `BuildConfig`**, never
  hardcoded. `local.properties` is already gitignored.
- **Every new user-facing string exists in both `values/strings.xml` and
  `values-es/strings.xml`.** Lint has `MissingTranslation` as an *error* and
  will fail the build. Never translate the product name "PictoKeyboard".
- **Every new screen gets `@ScreenPreviews`** (light, dark, `fontScale = 2f`).
- **Build command:** `ANDROID_HOME=$HOME/Android/Sdk ./gradlew <task> --max-workers=4`.
  Never omit `--max-workers=4`.
- **CI gates:** `spotlessCheck`, `detekt` (baseline: new findings fail), `lint`
  (`abortOnError = true`), `testDebugUnitTest`.

## Prerequisites the human must supply

These cannot be produced from inside the repository. Task 1 is written so that
everything else can be built and tested *without* them; only manual verification
is blocked.

1. A Supabase project → **Project URL** and **anon public key**.
2. A Google Cloud OAuth **Web client ID** (used as `serverClientId`), plus an
   **Android** OAuth client registered with the debug and release SHA-1
   fingerprints. The Web client ID and secret go into Supabase's Google auth
   provider.
3. A custom SMTP provider (Resend or Postmark free tier) configured in Supabase
   → Auth → SMTP Settings. **Without this, email sign-up is limited to a handful
   of messages an hour and is not shippable.**

## File Structure

| File | Responsibility |
|---|---|
| `gradle/libs.versions.toml` | Version catalog entries for supabase-kt and Ktor |
| `app/build.gradle.kts` | Dependencies; `BuildConfig` fields read from `local.properties` |
| `data/auth/SupabaseConfig.kt` | Reads `BuildConfig`, answers "is this build wired to a Supabase project?" |
| `data/auth/AccountState.kt` | The only auth type the UI sees, plus the mapping from Supabase's `SessionStatus` |
| `data/auth/AuthRepository.kt` | Every call into `supabase.auth`; exposes `StateFlow<AccountState>` |
| `ui/account/AccountViewModel.kt` | Form state, in-flight state, error-to-string mapping |
| `ui/screens/AccountScreen.kt` | The signed-out and signed-in screens |
| `ui/screens/AccountForms.kt` | Email/password form, password-reset dialog |
| `ui/screens/SettingsSections.kt` | The Account row in Settings |
| `ui/MainActivity.kt` | `Routes.ACCOUNT` and its `composable` |

---

### Task 1: Wire the dependency and the credentials, with a build that survives their absence

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/org/pictokeyboard/data/auth/SupabaseConfig.kt`
- Test: `app/src/test/java/org/pictokeyboard/data/auth/SupabaseConfigTest.kt`

**Interfaces:**
- Produces: `SupabaseConfig(url: String, anonKey: String)` with
  `val isConfigured: Boolean`, and `SupabaseConfig.fromBuildConfig(): SupabaseConfig`.

- [ ] **Step 1: Add the version catalog entries**

In `gradle/libs.versions.toml` under `[versions]`:

```toml
supabase = "3.7.0"
ktor = "3.0.3"
```

Under `[libraries]`:

```toml
supabase-bom = { group = "io.github.jan-tennert.supabase", name = "bom", version.ref = "supabase" }
supabase-auth = { group = "io.github.jan-tennert.supabase", name = "auth-kt" }
supabase-compose-auth = { group = "io.github.jan-tennert.supabase", name = "compose-auth" }
ktor-client-okhttp = { group = "io.ktor", name = "ktor-client-okhttp", version.ref = "ktor" }
```

- [ ] **Step 2: Add the dependencies and the BuildConfig fields**

In `app/build.gradle.kts`, above the `android { }` block:

```kotlin
// Supabase credentials. The anon key is public by design -- row-level security
// is the boundary, not secrecy -- but it still arrives through local.properties
// rather than the repository, so a fork does not inherit this project's backend.
// The service_role key must NEVER appear here or anywhere else in the repo.
val supabaseProps = java.util.Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun supabaseSecret(name: String): String =
    System.getenv(name) ?: supabaseProps.getProperty(name).orEmpty()
```

Inside `defaultConfig { }`:

```kotlin
// Empty on any machine that has not been given a project -- a fresh clone,
// CI, a fork. SupabaseConfig.isConfigured reads exactly this, and the app
// hides the Account section rather than crashing or offering a dead button.
buildConfigField("String", "SUPABASE_URL", "\"${supabaseSecret("SUPABASE_URL")}\"")
buildConfigField("String", "SUPABASE_ANON_KEY", "\"${supabaseSecret("SUPABASE_ANON_KEY")}\"")
```

In `dependencies { }`, after the Retrofit block:

```kotlin
implementation(platform(libs.supabase.bom))
implementation(libs.supabase.auth)
implementation(libs.supabase.compose.auth)
implementation(libs.ktor.client.okhttp)
```

- [ ] **Step 3: Write the failing test**

Create `app/src/test/java/org/pictokeyboard/data/auth/SupabaseConfigTest.kt`:

```kotlin
package org.pictokeyboard.data.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A build with no Supabase credentials is a supported build, not a broken one:
 * a fork, a fresh clone and every CI run are all in that state. What must never
 * happen is an Account section that is visible and dead.
 */
class SupabaseConfigTest {

    @Test
    fun `a build with no credentials is not configured`() {
        assertFalse(SupabaseConfig("", "").isConfigured)
    }

    @Test
    fun `a half-filled build is not configured`() {
        assertFalse(SupabaseConfig("https://abc.supabase.co", "").isConfigured)
        assertFalse(SupabaseConfig("", "anon-key").isConfigured)
    }

    @Test
    fun `blank is not the same as absent`() {
        assertFalse(SupabaseConfig("   ", "  ").isConfigured)
    }

    @Test
    fun `both values present means configured`() {
        assertTrue(SupabaseConfig("https://abc.supabase.co", "anon-key").isConfigured)
    }
}
```

- [ ] **Step 4: Run it and watch it fail**

Run: `ANDROID_HOME=$HOME/Android/Sdk ./gradlew testDebugUnitTest --tests '*SupabaseConfigTest*' --max-workers=4`
Expected: FAIL — unresolved reference `SupabaseConfig`.

- [ ] **Step 5: Write the implementation**

Create `app/src/main/java/org/pictokeyboard/data/auth/SupabaseConfig.kt`:

```kotlin
package org.pictokeyboard.data.auth

import org.pictokeyboard.BuildConfig

/**
 * Whether this build is wired to a Supabase project, and to which one.
 *
 * A build with no credentials is normal and supported: a fresh clone, a fork,
 * and every CI run are all in that state. Accounts then do not exist in the UI
 * at all -- which is the honest presentation, and is also why nothing else in
 * the app may depend on an account existing.
 *
 * The anon key is public by design; row-level security is the boundary. It is
 * still kept out of the repository so that a fork does not silently inherit
 * this project's backend.
 */
data class SupabaseConfig(val url: String, val anonKey: String) {

    val isConfigured: Boolean = url.isNotBlank() && anonKey.isNotBlank()

    companion object {
        fun fromBuildConfig(): SupabaseConfig =
            SupabaseConfig(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
    }
}
```

- [ ] **Step 6: Run the test and the whole build**

Run: `ANDROID_HOME=$HOME/Android/Sdk ./gradlew testDebugUnitTest assembleDebug --max-workers=4`
Expected: PASS, and `assembleDebug` succeeds **with no credentials in
`local.properties`** — that is the point of the task.

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts \
  app/src/main/java/org/pictokeyboard/data/auth/SupabaseConfig.kt \
  app/src/test/java/org/pictokeyboard/data/auth/SupabaseConfigTest.kt
git commit -m "Add supabase-kt and read its credentials from local.properties

A build with no credentials stays a working build: SupabaseConfig reports
it as unconfigured and accounts disappear from the UI, so a fork and CI
both still compile and run.

Refs #79"
```

---

### Task 2: AccountState — the only auth type the UI ever sees

**Files:**
- Create: `app/src/main/java/org/pictokeyboard/data/auth/AccountState.kt`
- Test: `app/src/test/java/org/pictokeyboard/data/auth/AccountStateTest.kt`

**Interfaces:**
- Consumes: `SupabaseConfig` from Task 1.
- Produces: `sealed interface AccountState { Unavailable, Loading, SignedOut, data class SignedIn(val email: String?) }`
  and `fun accountStateOf(configured: Boolean, session: SessionSnapshot?): AccountState`,
  where `SessionSnapshot(val email: String?)` is a plain data class this file
  also declares. Task 3 maps Supabase's `SessionStatus` into `SessionSnapshot`.

Keeping the mapping a pure function over a plain snapshot is what makes it
unit-testable: `SessionStatus` cannot be constructed in a JVM test without a
client.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/org/pictokeyboard/data/auth/AccountStateTest.kt`:

```kotlin
package org.pictokeyboard.data.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountStateTest {

    @Test
    fun `an unconfigured build has no account, signed in or not`() {
        assertEquals(AccountState.Unavailable, accountStateOf(configured = false, session = null))
        assertEquals(
            AccountState.Unavailable,
            accountStateOf(configured = false, session = SessionSnapshot("a@b.com")),
        )
    }

    @Test
    fun `no session on a configured build is signed out`() {
        assertEquals(AccountState.SignedOut, accountStateOf(configured = true, session = null))
    }

    @Test
    fun `a session carries the email through for the account screen`() {
        assertEquals(
            AccountState.SignedIn("a@b.com"),
            accountStateOf(configured = true, session = SessionSnapshot("a@b.com")),
        )
    }

    @Test
    fun `a session without an email is still signed in`() {
        // Google sign-in can in principle return a user with no email claim.
        // Showing "signed out" there would strand the caregiver in a loop of
        // signing in and appearing not to be.
        assertEquals(
            AccountState.SignedIn(null),
            accountStateOf(configured = true, session = SessionSnapshot(null)),
        )
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `ANDROID_HOME=$HOME/Android/Sdk ./gradlew testDebugUnitTest --tests '*AccountStateTest*' --max-workers=4`
Expected: FAIL — unresolved reference `AccountState`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/org/pictokeyboard/data/auth/AccountState.kt`:

```kotlin
package org.pictokeyboard.data.auth

/**
 * Who, if anyone, is signed in — expressed without a single Supabase type, so
 * that the UI layer cannot come to depend on the auth library and the whole
 * mapping stays unit-testable off-device.
 */
sealed interface AccountState {

    /**
     * This build has no Supabase project behind it. Accounts are not offered
     * at all rather than offered and broken.
     */
    data object Unavailable : AccountState

    /** The stored session has not been read back yet. The first frame only. */
    data object Loading : AccountState

    /** Configured, nobody signed in. The app's normal, permanent, supported state. */
    data object SignedOut : AccountState

    /** [email] is null when the provider returned no email claim. */
    data class SignedIn(val email: String?) : AccountState
}

/** The parts of a Supabase session this app cares about. */
data class SessionSnapshot(val email: String?)

/**
 * Pure mapping, so it can be tested without a client: [SessionStatus] cannot be
 * constructed in a JVM test.
 *
 * `configured` wins over the session because an unconfigured build has no
 * business showing an account at all, whatever happens to be cached.
 */
fun accountStateOf(configured: Boolean, session: SessionSnapshot?): AccountState = when {
    !configured -> AccountState.Unavailable
    session == null -> AccountState.SignedOut
    else -> AccountState.SignedIn(session.email)
}
```

- [ ] **Step 4: Run the test**

Run: `ANDROID_HOME=$HOME/Android/Sdk ./gradlew testDebugUnitTest --tests '*AccountStateTest*' --max-workers=4`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/pictokeyboard/data/auth/AccountState.kt \
  app/src/test/java/org/pictokeyboard/data/auth/AccountStateTest.kt
git commit -m "Express who is signed in without a Supabase type

The UI never sees SessionStatus, which keeps the auth library out of the
Compose layer and makes the whole mapping testable on the JVM.

Refs #79"
```

---

### Task 3: AuthRepository — every call into Supabase, in one file

**Files:**
- Create: `app/src/main/java/org/pictokeyboard/data/auth/AuthRepository.kt`
- Modify: `app/src/main/java/org/pictokeyboard/di/ServiceLocator.kt`

**Interfaces:**
- Consumes: `SupabaseConfig`, `AccountState`, `SessionSnapshot`, `accountStateOf`.
- Produces:
  - `class AuthRepository(config: SupabaseConfig)` with
    `val state: StateFlow<AccountState>`,
    `suspend fun signUp(email: String, password: String): Result<Unit>`,
    `suspend fun signIn(email: String, password: String): Result<Unit>`,
    `suspend fun sendRecoveryEmail(email: String): Result<Unit>`,
    `suspend fun signOut(): Result<Unit>`,
    `val client: SupabaseClient?` (null when unconfigured; Task 5 needs it for
    Compose Auth).
  - `ServiceLocator.authRepository: AuthRepository`

- [ ] **Step 1: Write the implementation**

There is no unit test in this task: every method is a thin call into a network
client that cannot be constructed in a JVM test. The behaviour worth proving —
the state mapping — was proved in Task 2, and the wiring is proved by Task 6's
instrumented test. Do not write a test that only asserts a mock was called.

Create `app/src/main/java/org/pictokeyboard/data/auth/AuthRepository.kt`:

```kotlin
package org.pictokeyboard.data.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SessionStatus
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Every call into Supabase Auth, in one place.
 *
 * Constructed even when the build has no credentials — it then holds no client
 * and reports [AccountState.Unavailable] forever, which keeps ServiceLocator
 * free of a nullable dependency that every caller would have to remember to
 * check.
 *
 * Lives in the config app only. The IME must never construct this; see the
 * test in ImeHasNoSupabaseTest.
 */
class AuthRepository(private val config: SupabaseConfig, scope: CoroutineScope) {

    val client: SupabaseClient? = if (!config.isConfigured) {
        null
    } else {
        createSupabaseClient(config.url, config.anonKey) {
            install(Auth)
        }
    }

    val state: StateFlow<AccountState> = when (val supabase = client) {
        null -> MutableStateFlow(AccountState.Unavailable)
        else -> supabase.auth.sessionStatus
            .map { status ->
                when (status) {
                    // NotAuthenticated arrives both before and after a sign-in
                    // attempt; only a real session means signed in.
                    is SessionStatus.Authenticated ->
                        accountStateOf(true, SessionSnapshot(status.session.user?.email))
                    is SessionStatus.Initializing -> AccountState.Loading
                    else -> AccountState.SignedOut
                }
            }
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
     * Signing out is not a delete. Every board stays on this device and the app
     * returns to behaving exactly as it does for someone who never signed in.
     */
    suspend fun signOut(): Result<Unit> = call { it.auth.signOut() }

    /**
     * Runs [block] against the client, turning both "no client" and a thrown
     * network or credential error into a Result the UI can render. Nothing here
     * swallows a failure silently: a caller that ignores the Result is the bug,
     * and there are none.
     */
    private suspend fun call(block: suspend (SupabaseClient) -> Unit): Result<Unit> {
        val supabase = client ?: return Result.failure(IllegalStateException("Supabase not configured"))
        return runCatching { block(supabase) }
    }
}
```

- [ ] **Step 2: Wire it into ServiceLocator**

In `app/src/main/java/org/pictokeyboard/di/ServiceLocator.kt`, add the imports
and this property after `backupManager`:

```kotlin
/**
 * Accounts. Constructed on a scope that lives as long as the process, because
 * the session flow has to outlive any one screen.
 *
 * The IME shares this ServiceLocator, so this property being *constructed* is
 * not the same as the keyboard using it — but nothing under `ime/` may touch
 * it, and ImeHasNoSupabaseTest proves it does not.
 */
val authRepository = AuthRepository(
    config = SupabaseConfig.fromBuildConfig(),
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
)
```

- [ ] **Step 3: Build**

Run: `ANDROID_HOME=$HOME/Android/Sdk ./gradlew assembleDebug testDebugUnitTest --max-workers=4`
Expected: BUILD SUCCESSFUL. If `SessionStatus`'s member names do not match,
correct them against the version resolved by the BOM — do not guess twice.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/org/pictokeyboard/data/auth/AuthRepository.kt \
  app/src/main/java/org/pictokeyboard/di/ServiceLocator.kt
git commit -m "AuthRepository: every Supabase Auth call in one place

Constructed even on an unconfigured build, where it holds no client and
reports Unavailable forever -- so no caller has to null-check a service.

Refs #79"
```

---

### Task 4: The Account screen, signed out — email and password

**Files:**
- Create: `app/src/main/java/org/pictokeyboard/ui/account/AccountViewModel.kt`
- Create: `app/src/main/java/org/pictokeyboard/ui/screens/AccountScreen.kt`
- Create: `app/src/main/java/org/pictokeyboard/ui/screens/AccountForms.kt`
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-es/strings.xml`
- Test: `app/src/test/java/org/pictokeyboard/ui/account/AccountFormTest.kt`

**Interfaces:**
- Consumes: `AuthRepository`, `AccountState`.
- Produces:
  - `data class AccountForm(val email: String = "", val password: String = "")`
    with `val canSubmit: Boolean`
  - `class AccountViewModel : ViewModel()` exposing
    `val state: StateFlow<AccountState>`, `var form: AccountForm`,
    `val busy: StateFlow<Boolean>`, `val error: StateFlow<Int?>` (a string
    resource id), and `fun signIn()`, `fun signUp()`, `fun sendRecovery()`,
    `fun signOut()`, `fun clearError()`
  - `@Composable fun AccountScreen(viewModel: AccountViewModel, onBack: () -> Unit)`
  - `@Composable fun AccountScreenContent(state: AccountState, form: AccountForm, busy: Boolean, errorRes: Int?, onForm: (AccountForm) -> Unit, onSignIn: () -> Unit, onSignUp: () -> Unit, onRecover: () -> Unit, onSignOut: () -> Unit, onGoogle: (() -> Unit)?, onBack: () -> Unit)`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/org/pictokeyboard/ui/account/AccountFormTest.kt`:

```kotlin
package org.pictokeyboard.ui.account

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Submit is gated locally so that a caregiver on a slow connection is told
 * "that is not an email address" instantly, rather than after a round trip
 * that looks like the app hanging.
 */
class AccountFormTest {

    @Test
    fun `empty cannot be submitted`() {
        assertFalse(AccountForm().canSubmit)
    }

    @Test
    fun `an address without an at sign cannot be submitted`() {
        assertFalse(AccountForm(email = "alvar", password = "correct-horse").canSubmit)
    }

    @Test
    fun `a short password cannot be submitted`() {
        // Supabase's own default minimum is 6.
        assertFalse(AccountForm(email = "a@b.com", password = "12345").canSubmit)
    }

    @Test
    fun `surrounding whitespace does not block a valid address`() {
        // Keyboards add a trailing space after autocomplete constantly.
        assertTrue(AccountForm(email = " a@b.com ", password = "correct-horse").canSubmit)
    }

    @Test
    fun `a valid pair can be submitted`() {
        assertTrue(AccountForm(email = "a@b.com", password = "correct-horse").canSubmit)
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `ANDROID_HOME=$HOME/Android/Sdk ./gradlew testDebugUnitTest --tests '*AccountFormTest*' --max-workers=4`
Expected: FAIL — unresolved reference `AccountForm`.

- [ ] **Step 3: Write AccountForm and the view model**

Create `app/src/main/java/org/pictokeyboard/ui/account/AccountViewModel.kt`:

```kotlin
package org.pictokeyboard.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.pictokeyboard.App
import org.pictokeyboard.R
import org.pictokeyboard.data.auth.AccountState

/** What the signed-out form holds, and whether it is worth sending. */
data class AccountForm(val email: String = "", val password: String = "") {

    /**
     * Trimmed, because a soft keyboard's autocomplete adds a trailing space far
     * more often than a caregiver notices, and "invalid email" for an address
     * that looks perfectly correct is a maddening dead end.
     */
    val canSubmit: Boolean
        get() {
            val trimmed = email.trim()
            return trimmed.contains('@') &&
                trimmed.substringAfter('@').contains('.') &&
                password.length >= MIN_PASSWORD
        }

    companion object {
        /** Supabase's own default floor. Rejecting locally saves a round trip. */
        const val MIN_PASSWORD = 6
    }
}

class AccountViewModel : ViewModel() {

    private val repo = App.locator().authRepository

    val state: StateFlow<AccountState> = repo.state

    private val _form = MutableStateFlow(AccountForm())
    val form: StateFlow<AccountForm> = _form

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    /** A string resource id, so the repository never builds user-facing text. */
    private val _error = MutableStateFlow<Int?>(null)
    val error: StateFlow<Int?> = _error

    fun setForm(value: AccountForm) {
        _form.value = value
    }

    fun clearError() {
        _error.value = null
    }

    fun signIn() = run { repo.signIn(form.value.email.trim(), form.value.password) }

    fun signUp() = run { repo.signUp(form.value.email.trim(), form.value.password) }

    fun sendRecovery() = run(R.string.account_recovery_sent) {
        repo.sendRecoveryEmail(form.value.email.trim())
    }

    fun signOut() = run { repo.signOut() }

    /**
     * One in-flight call at a time, with the failure surfaced rather than
     * dropped. [successMessage] is shown for the actions whose success is
     * otherwise invisible — sending a recovery email changes nothing on screen,
     * and silence there reads as a broken button.
     */
    private fun run(successMessage: Int? = null, block: suspend () -> Result<Unit>) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val result = block()
            _busy.value = false
            _error.value = when {
                result.isSuccess -> successMessage
                else -> R.string.account_error_generic
            }
        }
    }
}
```

- [ ] **Step 4: Run the test**

Run: `ANDROID_HOME=$HOME/Android/Sdk ./gradlew testDebugUnitTest --tests '*AccountFormTest*' --max-workers=4`
Expected: PASS.

- [ ] **Step 5: Add the strings, both locales**

In `app/src/main/res/values/strings.xml`:

```xml
<string name="account_title">Account</string>
<string name="account_settings_row">Account</string>
<string name="account_signed_out_title">Keep your boards safe</string>
<string name="account_signed_out_body">Sign in and your boards come back on a new phone. The keyboard works exactly the same either way — an account is never needed to speak.</string>
<string name="account_email">Email</string>
<string name="account_password">Password</string>
<string name="account_sign_in">Sign in</string>
<string name="account_sign_up">Create an account</string>
<string name="account_forgot">Forgot your password?</string>
<string name="account_recovery_sent">Check your email for a link to set a new password.</string>
<string name="account_google">Continue with Google</string>
<string name="account_signed_in_as">Signed in as %1$s</string>
<string name="account_signed_in_no_email">Signed in</string>
<string name="account_sign_out">Sign out</string>
<string name="account_sign_out_note">Your boards stay on this phone. Signing out never deletes anything.</string>
<string name="account_error_generic">That did not work. Check your connection and try again.</string>
<string name="account_working">Working…</string>
```

In `app/src/main/res/values-es/strings.xml`:

```xml
<string name="account_title">Cuenta</string>
<string name="account_settings_row">Cuenta</string>
<string name="account_signed_out_title">Guarda tus tableros</string>
<string name="account_signed_out_body">Inicia sesión y tus tableros vuelven en un teléfono nuevo. El teclado funciona igual de todos modos: nunca hace falta una cuenta para hablar.</string>
<string name="account_email">Correo electrónico</string>
<string name="account_password">Contraseña</string>
<string name="account_sign_in">Iniciar sesión</string>
<string name="account_sign_up">Crear una cuenta</string>
<string name="account_forgot">¿Has olvidado la contraseña?</string>
<string name="account_recovery_sent">Mira tu correo: te hemos enviado un enlace para poner una contraseña nueva.</string>
<string name="account_google">Continuar con Google</string>
<string name="account_signed_in_as">Sesión iniciada como %1$s</string>
<string name="account_signed_in_no_email">Sesión iniciada</string>
<string name="account_sign_out">Cerrar sesión</string>
<string name="account_sign_out_note">Tus tableros se quedan en este teléfono. Cerrar sesión no borra nada.</string>
<string name="account_error_generic">No ha funcionado. Comprueba la conexión e inténtalo de nuevo.</string>
<string name="account_working">Trabajando…</string>
```

- [ ] **Step 6: Write the screen**

Create `app/src/main/java/org/pictokeyboard/ui/screens/AccountForms.kt` holding
`EmailPasswordForm` and the error line, and
`app/src/main/java/org/pictokeyboard/ui/screens/AccountScreen.kt` holding
`AccountScreen` (stateful, reads the view model) and `AccountScreenContent`
(stateless, previewable). Follow `SettingsScreen.kt`'s split exactly — the
stateful wrapper owns the view model and nothing else.

Required behaviour, each one non-negotiable:

- The error line is a `LiveRegionMode.Assertive` semantics node. A failed
  sign-in that only changes pixels is invisible to a caregiver using TalkBack,
  and they will retype the password indefinitely.
- The busy state disables submit and announces `account_working`. A bare
  spinner announces nothing.
- `AccountState.Unavailable` renders nothing and the route is unreachable —
  Task 6 keeps the Settings row hidden in that state.
- Sign out shows `account_sign_out_note` next to the button, not behind a
  confirmation. It is not a destructive action and must not be dressed as one.

- [ ] **Step 7: Add the previews**

At the bottom of `AccountScreen.kt`, `@ScreenPreviews` for: signed out with an
empty form; signed out with an error showing; and signed in as
`caregiver@example.com`. Follow `SettingsScreenPreview`'s shape.

- [ ] **Step 8: Verify**

Run: `ANDROID_HOME=$HOME/Android/Sdk ./gradlew spotlessApply testDebugUnitTest lintDebug --max-workers=4`
Expected: all green. `lintDebug` is what catches a string added to one locale
and not the other.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/org/pictokeyboard/ui/account app/src/main/java/org/pictokeyboard/ui/screens/Account*.kt app/src/main/res/values/strings.xml app/src/main/res/values-es/strings.xml app/src/test/java/org/pictokeyboard/ui/account
git commit -m "Account screen: sign in, sign up and password recovery by email

Submit is gated on the form locally so a bad address fails instantly
instead of after a round trip that reads as a hang. Errors are an
assertive live region -- a failure that only changes pixels is invisible
to TalkBack and gets the password retyped forever.

Refs #79"
```

---

### Task 5: Google sign-in

**Files:**
- Modify: `app/src/main/java/org/pictokeyboard/data/auth/AuthRepository.kt`
- Modify: `app/src/main/java/org/pictokeyboard/ui/screens/AccountScreen.kt`
- Modify: `app/build.gradle.kts` (the `GOOGLE_SERVER_CLIENT_ID` BuildConfig field)

**Interfaces:**
- Consumes: `AuthRepository.client`.
- Produces: `AuthRepository.googleServerClientId: String?` — null when absent,
  which hides the Google button exactly as an unconfigured build hides the
  whole screen.

- [ ] **Step 1: Add the client id as a BuildConfig field**

In `app/build.gradle.kts`, inside `defaultConfig`, next to the two Supabase
fields:

```kotlin
// The OAuth *web* client id, not the Android one -- Credential Manager wants
// the server client id. Empty on a build with no Google client, which hides
// the button rather than offering one that cannot work.
buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"${supabaseSecret("GOOGLE_SERVER_CLIENT_ID")}\"")
```

- [ ] **Step 2: Install Compose Auth on the client**

In `AuthRepository.kt`, change the client construction:

```kotlin
val googleServerClientId: String? = BuildConfig.GOOGLE_SERVER_CLIENT_ID.takeIf { it.isNotBlank() }

val client: SupabaseClient? = if (!config.isConfigured) {
    null
} else {
    createSupabaseClient(config.url, config.anonKey) {
        install(Auth)
        // Only when there is a client id to give it. Installing it with an
        // empty id fails at sign-in time rather than at construction, which
        // surfaces as an unexplained failure on the caregiver's first tap.
        googleServerClientId?.let { id ->
            install(ComposeAuth) { googleNativeLogin(serverClientId = id) }
        }
    }
}
```

- [ ] **Step 3: Wire the button**

In `AccountScreen.kt`'s stateful wrapper:

```kotlin
val supabase = App.locator().authRepository.client
val googleAction = supabase?.takeIf { App.locator().authRepository.googleServerClientId != null }
    ?.composeAuth
    ?.rememberSignInWithGoogle(
        onResult = { result ->
            // A dismissed sheet is not an error and must not be reported as
            // one; a caregiver who changed their mind has done nothing wrong.
            if (result is NativeSignInResult.Error) viewModel.reportGoogleFailure()
        },
    )
```

Pass `onGoogle = googleAction?.let { { it.startFlow() } }` into
`AccountScreenContent`, which renders the button only when it is non-null.

Add `fun reportGoogleFailure()` to `AccountViewModel`, setting
`_error.value = R.string.account_error_generic`.

- [ ] **Step 4: Build and check the button disappears without a client id**

Run: `ANDROID_HOME=$HOME/Android/Sdk ./gradlew assembleDebug lintDebug --max-workers=4`
Then, with no `GOOGLE_SERVER_CLIENT_ID` in `local.properties`, install and open
the Account screen: the Google button must be absent, not disabled.

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/org/pictokeyboard/data/auth/AuthRepository.kt app/src/main/java/org/pictokeyboard/ui/screens/AccountScreen.kt
git commit -m "Sign in with Google through Credential Manager

The button is absent, not disabled, when the build has no OAuth client:
a disabled control asks the caregiver to work out what is wrong with
their phone.

Refs #79"
```

---

### Task 6: The Account row in Settings, and the route

**Files:**
- Modify: `app/src/main/java/org/pictokeyboard/ui/screens/SettingsScreen.kt`
- Modify: `app/src/main/java/org/pictokeyboard/ui/screens/SettingsSections.kt`
- Modify: `app/src/main/java/org/pictokeyboard/ui/MainActivity.kt`
- Test: `app/src/androidTest/java/org/pictokeyboard/ui/screens/AccountSettingsRowTest.kt`

**Interfaces:**
- Consumes: `AccountState`, `AccountScreen`.
- Produces: `Routes.ACCOUNT = "account"`.

- [ ] **Step 1: Write the failing instrumented test**

Create `app/src/androidTest/java/org/pictokeyboard/ui/screens/AccountSettingsRowTest.kt`:

```kotlin
package org.pictokeyboard.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.pictokeyboard.data.auth.AccountState

/**
 * An unconfigured build must not show an Account row at all. A row that opens
 * a screen with nothing on it is worse than no row: it reads as a broken app.
 */
@RunWith(AndroidJUnit4::class)
class AccountSettingsRowTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun unavailableHidesTheRow() {
        compose.setContent {
            AccountSettingsRow(state = AccountState.Unavailable, onOpen = {})
        }
        compose.onNodeWithText("Account").assertDoesNotExist()
    }

    @Test
    fun signedOutOffersTheRow() {
        compose.setContent {
            AccountSettingsRow(state = AccountState.SignedOut, onOpen = {})
        }
        compose.onNodeWithText("Account").assertIsDisplayed()
    }

    @Test
    fun signedInShowsTheEmailOnTheRow() {
        compose.setContent {
            AccountSettingsRow(state = AccountState.SignedIn("a@b.com"), onOpen = {})
        }
        compose.onNodeWithText("a@b.com", substring = true).assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `ANDROID_HOME=$HOME/Android/Sdk ./gradlew connectedDebugAndroidTest --tests '*AccountSettingsRowTest*' --max-workers=4`
Expected: FAIL — unresolved reference `AccountSettingsRow`.

Note: if the whole instrumented suite fails en masse straight after using the
app by hand, that is stale app data, not a defect — clear it and re-run.

- [ ] **Step 3: Implement the row**

Add to `SettingsSections.kt`:

```kotlin
/**
 * The way into the Account screen, and the only place the app mentions accounts.
 *
 * Absent — not disabled — on a build with no Supabase project. Showing a row
 * that opens an empty screen reads as a broken app rather than as a build
 * without a backend.
 */
@Composable
internal fun AccountSettingsRow(state: AccountState, onOpen: () -> Unit) {
    if (state == AccountState.Unavailable) return
    val subtitle = when (state) {
        is AccountState.SignedIn ->
            state.email ?: stringResource(R.string.account_signed_in_no_email)
        else -> stringResource(R.string.account_signed_out_title)
    }
    SettingsGroup(stringResource(R.string.account_settings_row)) {
        // Follow the existing row idiom in this file rather than inventing one.
        NavigationRow(title = stringResource(R.string.account_settings_row), subtitle = subtitle, onClick = onOpen)
    }
}
```

If `SettingsSections.kt` has no `NavigationRow`, use the same construction the
About row uses and keep the two consistent.

- [ ] **Step 4: Thread it through Settings and add the route**

`SettingsScreenContent` gains `accountState: AccountState` and
`onOpenAccount: () -> Unit`, rendered above the security group. Every existing
`@ScreenPreviews` in the file gains `accountState = AccountState.SignedOut`.

In `MainActivity.kt`: add `const val ACCOUNT = "account"` to `Routes`, and

```kotlin
composable(Routes.ACCOUNT) {
    AccountScreen(viewModel = viewModel(), onBack = { nav.popBackStack() })
}
```

- [ ] **Step 5: Run the test**

Run: `ANDROID_HOME=$HOME/Android/Sdk ./gradlew connectedDebugAndroidTest --tests '*AccountSettingsRowTest*' --max-workers=4`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/org/pictokeyboard/ui app/src/androidTest/java/org/pictokeyboard/ui/screens/AccountSettingsRowTest.kt
git commit -m "Reach the account from Settings

Absent rather than disabled on a build with no backend.

Refs #79"
```

---

### Task 7: Prove the keyboard has not gained a dependency on any of this

**Files:**
- Test: `app/src/test/java/org/pictokeyboard/ime/ImeHasNoSupabaseTest.kt`

This is the task that protects the rule the whole spec is built on. It is a
source-level test rather than a runtime one because the failure it guards
against — someone importing `supabase` into a keyboard file — is introduced at
edit time and is trivially caught there.

- [ ] **Step 1: Write the test**

```kotlin
package org.pictokeyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * PictoKeyboard is how someone speaks. If a token refresh, a paused Supabase
 * project or an expired session can stand between a person and their words,
 * the product has failed at the only thing it does.
 *
 * So the IME does not link the auth stack at all — not lazily, not behind a
 * flag. This asserts it at the source level, because that is where the mistake
 * is made: someone adds an import to a keyboard file and everything still
 * compiles and still runs on their desk.
 */
class ImeHasNoSupabaseTest {

    @Test
    fun `no keyboard source imports supabase or the auth package`() {
        val imeSources = File("src/main/java/org/pictokeyboard/ime")
            .walkTopDown()
            .filter { it.extension == "kt" }
            .toList()

        // A guard that silently passes because it found no files is worse than
        // no guard, so the fixture proves itself first.
        assert(imeSources.isNotEmpty()) { "found no IME sources - has the package moved?" }

        val offenders = imeSources.filter { file ->
            file.readLines().any { line ->
                line.startsWith("import ") &&
                    ("supabase" in line || "org.pictokeyboard.data.auth" in line)
            }
        }

        assertEquals("these keyboard files reach into the auth stack", emptyList<File>(), offenders)
    }
}
```

- [ ] **Step 2: Run it**

Run: `ANDROID_HOME=$HOME/Android/Sdk ./gradlew testDebugUnitTest --tests '*ImeHasNoSupabaseTest*' --max-workers=4`
Expected: PASS. If the working directory makes the relative path wrong, the
self-check in the test fails loudly rather than passing vacuously — fix the
path, never delete the check.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/org/pictokeyboard/ime/ImeHasNoSupabaseTest.kt
git commit -m "Assert the keyboard never links the auth stack

The rule the whole accounts design rests on, enforced where the mistake
gets made rather than discovered on a device with no signal.

Refs #79"
```

---

### Task 8: Manual verification and the pull request

Everything above can be built and tested with no Supabase project. This task
cannot, and is where the prerequisites are finally needed.

- [ ] **Step 1: Fill in `local.properties`**

```properties
SUPABASE_URL=https://<project>.supabase.co
SUPABASE_ANON_KEY=<anon public key>
GOOGLE_SERVER_CLIENT_ID=<OAuth web client id>
```

Confirm `git status` shows nothing to commit. If `local.properties` appears,
stop and fix `.gitignore` before going further.

- [ ] **Step 2: Configure Supabase**

Auth → Providers → Google: on, with the web client id and secret.
Auth → SMTP: the Resend or Postmark credentials. **Verify a confirmation email
actually arrives before calling this done** — the built-in mailer will send the
first few and then silently stop.

- [ ] **Step 3: Run the app on the emulator**

Build, install, and walk: sign up by email → confirmation email arrives →
sign in → the Settings row shows the address → sign out → boards all still
present → sign in with Google → forgot password → recovery email arrives.

Then, with aeroplane mode on: open the keyboard and type. It must behave
exactly as it does today.

- [ ] **Step 4: Full gate**

Run: `ANDROID_HOME=$HOME/Android/Sdk ./gradlew spotlessCheck detekt lintDebug testDebugUnitTest --max-workers=4`
Expected: all green.

- [ ] **Step 5: Open the pull request**

The body must contain `Closes #79` so the board automation advances the card.

---

## Self-review notes

**Spec coverage.** #79's acceptance criteria map as: Google → Task 5; email,
password and reset → Tasks 3–4; SMTP → Task 8 step 2; Settings section → Task 6;
sign out leaving boards alone → Task 4 step 6 and Task 8 step 3; credentials via
`local.properties` → Task 1; IME not linking Supabase → Task 7; TalkBack → Task 4
step 6; previews → Task 4 step 7.

**Deliberately not in this plan.** Account deletion is #83, not #79 — but note
that **Play will reject an app that creates accounts without a deletion path**,
so #79 must not reach a production track before #83 lands. An internal-testing
track is fine.
