package org.pictokeyboard.ui.screens

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.pictokeyboard.R
import org.pictokeyboard.data.db.BorderStyles
import org.pictokeyboard.ui.theme.PictoKeyboardTheme

/**
 * The controls that are *entirely drawn* — a colour, a stroke, a thickness —
 * and so have nothing but semantics to describe them.
 *
 * This app ships a blind mode, so a caregiver who cannot see these swatches is a
 * user the project has already decided exists. Before #21 they met 26
 * indistinguishable targets, and `BorderStylePicker` was literally
 * `.clickable { }` over an empty body: no name, no role, no state.
 *
 * Assertions resolve their expected strings through resources rather than
 * hardcoding English, so the suite passes on a Spanish emulator too — otherwise
 * this would be a locale test wearing an accessibility test's clothes.
 */
@RunWith(AndroidJUnit4::class)
class StylePickerAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    private val res = InstrumentationRegistry.getInstrumentation().targetContext.resources

    private fun string(id: Int) = res.getString(id)

    @Test
    fun everyColourAnnouncesItsOwnName() {
        compose.setContent {
            PictoKeyboardTheme {
                ColorPalettePicker(selected = CategoryPalette.first().argb.toInt(), onSelect = {})
            }
        }
        // Not just "some node exists" -- every swatch in the palette must be
        // reachable by a name of its own, which is the whole complaint.
        CategoryPalette.forEach { swatch ->
            compose.onNodeWithContentDescription(string(swatch.nameRes)).assertExists()
        }
    }

    @Test
    fun theChosenColourReportsThatItIsChosen() {
        val chosen = CategoryPalette[2]
        val other = CategoryPalette[5]
        compose.setContent {
            PictoKeyboardTheme {
                ColorPalettePicker(selected = chosen.argb.toInt(), onSelect = {})
            }
        }
        compose.onNodeWithContentDescription(string(chosen.nameRes)).assertIsSelected()
        compose.onNodeWithContentDescription(string(other.nameRes)).assertIsNotSelected()
    }

    @Test
    fun aColourCanBeChosenByName() {
        var picked: Int? = null
        val target = CategoryPalette[4]
        compose.setContent {
            PictoKeyboardTheme {
                ColorPalettePicker(selected = 0, onSelect = { picked = it })
            }
        }
        compose.onNodeWithContentDescription(string(target.nameRes)).performClick()
        assertEquals(target.argb.toInt(), picked)
    }

    @Test
    fun frameStylesAreNamedAndSelectable() {
        var picked: String? = null
        compose.setContent {
            PictoKeyboardTheme {
                BorderStylePicker(
                    color = androidx.compose.ui.graphics.Color.Red,
                    selected = BorderStyles.SOLID,
                    onSelect = { picked = it },
                )
            }
        }
        compose.onNodeWithContentDescription(string(R.string.frame_style_solid)).assertIsSelected()
        compose.onNodeWithContentDescription(string(R.string.frame_style_dashed)).assertIsNotSelected()
        compose.onNodeWithContentDescription(string(R.string.frame_style_dotted)).performClick()
        assertEquals(BorderStyles.DOTTED, picked)
    }

    @Test
    fun thicknessesAreNamedByWeightNotByNumber() {
        var picked: Int? = null
        compose.setContent {
            PictoKeyboardTheme {
                ThicknessPicker(
                    color = androidx.compose.ui.graphics.Color.Red,
                    selected = BorderStyles.WIDTHS_DP.first(),
                    onSelect = { picked = it },
                )
            }
        }
        // "3 dp" means nothing to someone who cannot see the result.
        compose.onNodeWithContentDescription(string(R.string.frame_thickness_thin)).assertIsSelected()
        compose.onNodeWithContentDescription(string(R.string.frame_thickness_extra_thick)).performClick()
        assertEquals(BorderStyles.WIDTHS_DP.last(), picked)
    }

    @Test
    fun theInheritOptionIsNamedToo() {
        var picked: Int? = -1
        compose.setContent {
            PictoKeyboardTheme {
                PictoColorPicker(selected = null, onSelect = { picked = it })
            }
        }
        compose.onNodeWithContentDescription(string(R.string.picto_color_inherit)).performClick()
        assertEquals(null, picked)
    }
}
