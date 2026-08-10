package org.pictokeyboard.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.pictokeyboard.R
import org.pictokeyboard.data.auth.AccountState
import org.pictokeyboard.ui.account.AccountForm
import org.pictokeyboard.ui.theme.PictoKeyboardTheme

/**
 * Deleting an account, which is the only control in this app that destroys
 * something a caregiver cannot get back (#83).
 *
 * These assertions are about the words as much as the wiring. A caregiver
 * reading "delete" while holding a phone full of their child's vocabulary has
 * every reason to think the boards are about to go too, and the only thing that
 * tells them otherwise is the sentence in the confirmation. So the copy is
 * tested, not just the callback.
 */
@RunWith(AndroidJUnit4::class)
class AccountDeletionTest {

    @get:Rule
    val compose = createComposeRule()

    private val res = InstrumentationRegistry.getInstrumentation().targetContext.resources

    private var deletions = 0

    private fun setSignedIn() {
        deletions = 0
        compose.setContent {
            PictoKeyboardTheme {
                AccountScreenContent(
                    state = AccountState.SignedIn("caregiver@example.com"),
                    form = AccountForm(),
                    busy = false,
                    notice = null,
                    onGoogle = null,
                    onBack = {},
                    onForm = {},
                    onSignIn = {},
                    onSignUp = {},
                    onRecover = {},
                    onSignOut = {},
                    onDeleteAccount = { deletions++ },
                )
            }
        }
    }

    @Test
    fun signedInOffersAWayToDeleteTheAccount() {
        // Play requires this to exist in the app at all, and GDPR requires it to
        // exist full stop.
        setSignedIn()
        compose.onNodeWithText(res.getString(R.string.account_delete)).assertIsDisplayed()
    }

    @Test
    fun deletingAsksBeforeItActs() {
        setSignedIn()
        compose.onNodeWithText(res.getString(R.string.account_delete)).performClick()

        compose.onNodeWithText(res.getString(R.string.account_delete_title)).assertIsDisplayed()
        assertEquals("tapping delete must not delete anything on its own", 0, deletions)
    }

    @Test
    fun theConfirmationSaysTheBoardsOnThisPhoneAreSafe() {
        // The sentence this whole screen turns on. Without it, the most
        // frightening reading of "delete my account" is also the most obvious
        // one, and it is wrong.
        setSignedIn()
        compose.onNodeWithText(res.getString(R.string.account_delete)).performClick()
        compose.onNodeWithText(res.getString(R.string.account_delete_body)).assertIsDisplayed()
    }

    @Test
    fun backingOutDeletesNothing() {
        setSignedIn()
        compose.onNodeWithText(res.getString(R.string.account_delete)).performClick()
        compose.onNodeWithText(res.getString(R.string.cancel)).performClick()

        assertEquals("cancelling a deletion must delete nothing", 0, deletions)
        compose.onNodeWithText(res.getString(R.string.account_delete_title)).assertDoesNotExist()
    }

    @Test
    fun confirmingDeletesOnce() {
        setSignedIn()
        compose.onNodeWithText(res.getString(R.string.account_delete)).performClick()
        compose.onNodeWithText(res.getString(R.string.account_delete_confirm)).performClick()

        assertEquals(1, deletions)
    }
}
