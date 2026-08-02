package org.pictokeyboard.ui.screens

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.pictokeyboard.R
import org.pictokeyboard.ui.theme.PictoKeyboardTheme

/**
 * **Try it** is the app's only feedback loop: a real field with the real
 * keyboard under it, so a caregiver can see what they built without leaving for
 * WhatsApp (#33).
 *
 * Two things have to hold, and neither is visible from a screenshot of the happy
 * path. The field must arrive **focused**, because a field that needs tapping
 * first is a sheet that appears to do nothing. And when PictoKeyboard is not the
 * keyboard in use, there must be **no field at all** — focusing one would summon
 * whichever other keyboard the phone has, which reads as this one being broken,
 * in the screen whose whole job is to show that it works.
 */
@RunWith(AndroidJUnit4::class)
class TryItSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val res = InstrumentationRegistry.getInstrumentation().targetContext.resources

    private fun show(status: KeyboardStatus) {
        compose.setContent {
            PictoKeyboardTheme {
                TryItSheet(
                    boardName = "Casa",
                    status = status,
                    onEnableKeyboard = {},
                    onSelectKeyboard = {},
                    onDismiss = {},
                )
            }
        }
    }

    @Test
    fun theFieldArrivesFocusedSoTheKeyboardIsAlreadyUp() {
        show(KeyboardStatus(enabled = true, selected = true))
        compose.onNodeWithText(res.getString(R.string.try_it_field)).assertIsFocused()
    }

    @Test
    fun withNoKeyboardInUseTheSetupStepsTakeTheFieldsPlace() {
        show(KeyboardStatus(enabled = true, selected = false))
        compose.onNodeWithText(res.getString(R.string.try_it_field)).assertDoesNotExist()
        compose.onNodeWithText(res.getString(R.string.try_it_not_ready)).assertExists()
        // And the way out of that state is offered rather than described.
        compose.onNodeWithText(res.getString(R.string.onboarding_select_action)).assertExists()
    }
}
