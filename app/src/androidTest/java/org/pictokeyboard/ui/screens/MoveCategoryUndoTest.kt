package org.pictokeyboard.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.pictokeyboard.R
import org.pictokeyboard.data.db.BoardEntity
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.data.repo.BoardSummary
import org.pictokeyboard.ui.theme.PictoKeyboardTheme

/**
 * The way back from a move (#119).
 *
 * `CategoryMoveTest` already proves the database half — the row moves, the words
 * come with it, and a restore puts both back. All of that passed while the undo
 * was unreachable, because the failure was not in the move: picking a
 * destination dismisses the sheet, and the coroutine that was to show the
 * snackbar was launched from the sheet's own scope, which the dismissal
 * cancelled in the same frame. The move worked. The snackbar offering to reverse
 * it simply never appeared, and a caregiver who mis-tapped had no way back.
 *
 * So this test drives the flow the way the screen does — the sheet really leaves
 * the composition when it dismisses — and asserts on the thing that has to
 * outlive it.
 */
@RunWith(AndroidJUnit4::class)
class MoveCategoryUndoTest {

    @get:Rule
    val compose = createComposeRule()

    private val res = InstrumentationRegistry.getInstrumentation().targetContext.resources

    private companion object {
        const val CATEGORY_NAME = "Comida"
        const val DESTINATION_NAME = "Colegio"
        const val ORIGINAL_POSITION = 1
        const val PICTO_COUNT = 3
    }

    private val category = CategoryEntity(
        id = "category-food",
        boardId = "board-home",
        name = CATEGORY_NAME,
        colorArgb = 0xFFFF9800.toInt(),
        position = ORIGINAL_POSITION,
    )

    private val destination = BoardEntity(
        id = "board-school",
        name = DESTINATION_NAME,
        colorArgb = BoardEntity.DEFAULT_COLOR_ARGB,
        position = 1,
        active = false,
    )

    /** What the repository hands back for the undo to write. */
    private val previous = category.copy()

    private var undone: CategoryEntity? = null

    /**
     * The screen's own arrangement, reduced to what this is about: the sheet is
     * rendered conditionally, and dismissing it takes it out of the composition.
     * Keeping that shape is the whole point — a host that left the sheet mounted
     * would pass whether or not the bug was there.
     */
    @Composable
    private fun Host() {
        val snackbars = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        var moving by remember { mutableStateOf<CategoryEntity?>(category) }

        Scaffold(snackbarHost = { SnackbarHost(snackbars) }) { insets ->
            Box(Modifier.padding(insets))
            moving?.let {
                MoveCategoryFlow(
                    category = it,
                    boards = listOf(
                        BoardSummary(
                            board = destination,
                            categories = emptyList(),
                            heroPictos = emptyList(),
                            pictoCount = PICTO_COUNT,
                        ),
                    ),
                    snackbars = snackbars,
                    scope = scope,
                    onDismiss = { moving = null },
                    onMoveCategory = { _, _, onMoved -> onMoved(previous) },
                    onUndoMove = { undone = it },
                )
            }
        }
    }

    private fun pickTheDestination() {
        compose.setContent { PictoKeyboardTheme { Host() } }
        compose.onNodeWithText(DESTINATION_NAME).performClick()
    }

    private fun movedMessage() =
        res.getString(R.string.category_moved, CATEGORY_NAME, DESTINATION_NAME)

    @Test
    fun theUndoSnackbarOutlivesTheSheetThatTriggeredIt() {
        pickTheDestination()

        compose.waitUntil {
            compose.onAllNodesWithText(movedMessage()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(res.getString(R.string.undo)).assertExists()
    }

    /** And the offer is real: taking it hands back the row exactly as it was. */
    @Test
    fun takingTheUndoHandsBackTheRowAsItWas() {
        pickTheDestination()

        compose.waitUntil {
            compose.onAllNodesWithText(movedMessage()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(res.getString(R.string.undo)).performClick()
        compose.waitForIdle()

        // Whole-row equality, deliberately: "back where it was" has to mean the
        // board *and* the position, and comparing the entity says both at once.
        assertEquals("undo did not hand back the row as it was", previous, undone)
    }
}
