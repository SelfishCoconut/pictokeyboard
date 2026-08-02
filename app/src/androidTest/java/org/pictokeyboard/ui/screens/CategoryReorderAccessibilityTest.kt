package org.pictokeyboard.ui.screens

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.pictokeyboard.R
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.ui.theme.PictoKeyboardTheme

/**
 * Reordering categories without a drag gesture.
 *
 * The serious half of #21. Reorder was `detectDragGesturesAfterLongPress` only,
 * and TalkBack owns touch — so the gesture could never reach the pointer-input
 * node, and the drag handle announced *"Drag to reorder"* to the one user who
 * could not do it. Switch Access and D-pad had no route either. WCAG 2.1.1
 * Keyboard (A) and 2.5.7 Dragging Movements (AA).
 *
 * Worse than unreachable: entering reorder mode **removed** edit, delete and
 * open, so a screen-reader user who got there could do nothing at all.
 *
 * These assert the route exists and is correctly bounded. A drag gesture is not
 * asserted here — this is about the path that does *not* need one.
 */
@RunWith(AndroidJUnit4::class)
class CategoryReorderAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    private val res = InstrumentationRegistry.getInstrumentation().targetContext.resources

    private val categories = listOf("Comida", "Acciones", "Gente").mapIndexed { i, name ->
        CategoryEntity(id = "c$i", name = name, colorArgb = 0xFFF57C00.toInt(), position = i)
    }

    private fun show(reordering: Boolean, onMove: (CategoryEntity, Boolean) -> Unit = { _, _ -> }) {
        compose.setContent {
            PictoKeyboardTheme {
                ReorderableCategoryList(
                    categories = categories,
                    reordering = reordering,
                    onReorder = {},
                    onMove = onMove,
                    onEdit = {},
                    onDelete = {},
                    onOpen = {},
                )
            }
        }
    }

    @Test
    fun reorderModeOffersAButtonPerDirectionOnEveryRow() {
        show(reordering = true)
        assertEquals(
            3,
            compose.onAllNodesWithContentDescription(res.getString(R.string.move_up)).fetchSemanticsNodes().size,
        )
        assertEquals(
            3,
            compose.onAllNodesWithContentDescription(res.getString(R.string.move_down)).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun theEndsOfTheListDisableTheDirectionThatWouldFallOff() {
        show(reordering = true)
        compose.onAllNodesWithContentDescription(res.getString(R.string.move_up))[0].assertIsNotEnabled()
        compose.onAllNodesWithContentDescription(res.getString(R.string.move_up))[1].assertIsEnabled()
        compose.onAllNodesWithContentDescription(res.getString(R.string.move_down))[2].assertIsNotEnabled()
        compose.onAllNodesWithContentDescription(res.getString(R.string.move_down))[0].assertIsEnabled()
    }

    @Test
    fun movingAsksForTheRightCategoryAndDirection() {
        val moves = mutableListOf<Pair<String, Boolean>>()
        show(reordering = true) { category, up -> moves += category.name to up }
        compose.onAllNodesWithContentDescription(res.getString(R.string.move_down))[0].performClick()
        compose.onAllNodesWithContentDescription(res.getString(R.string.move_up))[2].performClick()
        assertEquals(listOf("Comida" to false, "Gente" to true), moves)
    }

    @Test
    fun theDragHandleNoLongerNamesAGestureItCannotReceive() {
        show(reordering = true)
        // It keeps its icon as a visual affordance, but announcing "Drag to
        // reorder" beside two working buttons is instructions the user cannot
        // follow competing with the route that works.
        assertTrue(
            compose.onAllNodesWithContentDescription(res.getString(R.string.reorder_drag))
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun normalModeKeepsTheRowActionsAndOffersNoMoveButtons() {
        show(reordering = false)
        assertTrue(
            compose.onAllNodesWithContentDescription(res.getString(R.string.move_up))
                .fetchSemanticsNodes().isEmpty(),
        )
        // One per row, and crucially not zero: reorder mode used to *remove*
        // these, leaving a screen-reader user with no action at all.
        assertEquals(
            categories.size,
            compose.onAllNodesWithContentDescription(res.getString(R.string.category_more))
                .fetchSemanticsNodes().size,
        )
    }
}
