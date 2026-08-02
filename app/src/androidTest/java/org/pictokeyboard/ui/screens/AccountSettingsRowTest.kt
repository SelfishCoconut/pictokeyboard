package org.pictokeyboard.ui.screens

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.pictokeyboard.R
import org.pictokeyboard.data.auth.AccountState
import org.pictokeyboard.ui.theme.PictoKeyboardTheme

/**
 * The Account row is the app's only mention of accounts, and on a build with no
 * Supabase project it must not exist at all.
 *
 * A row that opens a screen with nothing on it does not read as "this build has
 * no backend" — it reads as a broken app, to a caregiver who has no way to tell
 * the difference.
 */
@RunWith(AndroidJUnit4::class)
class AccountSettingsRowTest {

    @get:Rule
    val compose = createComposeRule()

    // Resolved from resources rather than hardcoded, so the suite does not
    // depend on the device locale -- the app ships ES and EN.
    private val res = InstrumentationRegistry.getInstrumentation().targetContext.resources
    private val accountLabel get() = res.getString(R.string.account_title)

    private fun setRow(state: AccountState) {
        compose.setContent {
            PictoKeyboardTheme {
                AccountSettingsRow(state = state, onOpen = {})
            }
        }
    }

    @Test
    fun unconfiguredBuildShowsNoAccountRowAtAll() {
        setRow(AccountState.Unavailable)
        compose.onNodeWithText(accountLabel).assertDoesNotExist()
    }

    @Test
    fun loadingShowsNothingRatherThanFlashingSignedOut() {
        // The first frame before the stored session is read back. Drawing
        // "signed out" here and correcting it a moment later tells a caregiver
        // their account is gone.
        setRow(AccountState.Loading)
        compose.onNodeWithText(accountLabel).assertDoesNotExist()
    }

    @Test
    fun signedOutOffersAWayIn() {
        setRow(AccountState.SignedOut)
        compose.onNodeWithText(res.getString(R.string.account_sign_in)).assertIsDisplayed()
    }

    @Test
    fun theWordAccountIsSaidOnceNotTwice() {
        // The group title and the row label were both "Account", so TalkBack
        // announced it twice and the second carried no information.
        setRow(AccountState.SignedOut)
        compose.onAllNodesWithText(accountLabel).assertCountEquals(1)
    }

    @Test
    fun signedInShowsTheEmailOnTheRow() {
        // So the caregiver can tell *which* account without opening the screen.
        setRow(AccountState.SignedIn("a@b.com"))
        compose.onNodeWithText("a@b.com", substring = true).assertIsDisplayed()
    }
}
