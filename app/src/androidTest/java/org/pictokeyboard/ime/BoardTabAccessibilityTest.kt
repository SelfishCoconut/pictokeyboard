package org.pictokeyboard.ime

import android.view.View
import android.widget.FrameLayout
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.pictokeyboard.R
import org.pictokeyboard.data.db.BoardEntity

/**
 * The board strip, as the accessibility framework sees it.
 *
 * Switching board is the communicator's own navigation — "which situation am I
 * speaking from" — so it has to be answerable without sight. A row of buttons
 * that all announce as buttons does not answer it: nothing says which board is
 * in use, and the strip's entire purpose is to say exactly that.
 *
 * A JVM test cannot catch this. The role and the selected state are written onto
 * a real `View` through a real accessibility delegate during a real bind, and
 * only the framework's own node reports them back.
 */
@RunWith(AndroidJUnit4::class)
class BoardTabAccessibilityTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun board(id: String, name: String) =
        BoardEntity(id = id, name = name, colorArgb = 0xFF24303F.toInt(), position = 0)

    /** Binds one tab and hands back its view, exactly as the strip would. */
    private fun bind(selected: Boolean): View {
        val adapter = BoardTabAdapter(onClick = {})
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
        // submitList diffs asynchronously; bind directly so the test is about the
        // binding rather than about AsyncListDiffer's scheduling.
        holder.bind(BoardTabAdapter.Row(board("b1", "Médico"), selected = selected, iconModel = null))
        return holder.itemView.findViewById(R.id.board_tab_root)
    }

    private fun nodeOf(view: View): AccessibilityNodeInfoCompat =
        AccessibilityNodeInfoCompat.obtain().also { info ->
            view.onInitializeAccessibilityNodeInfo(info.unwrap())
            // The delegate is what sets the role and the state, and it is not
            // consulted by onInitializeAccessibilityNodeInfo alone.
            androidx.core.view.ViewCompat.getAccessibilityDelegate(view)
                ?.onInitializeAccessibilityNodeInfo(view, info)
        }

    @Test
    fun aTabAnnouncesTheBoardItOpens() {
        val view = bind(selected = false)
        assertEquals("Médico", view.findViewById<android.widget.TextView>(R.id.board_tab_name).text)
        assertTrue("a tab must be operable", view.isClickable)
    }

    @Test
    fun aTabAnnouncesAsATabRatherThanAsAButton() {
        // Without this the strip reads as a row of unrelated buttons, and the one
        // question it exists to answer -- which board am I on -- has no answer.
        val node = nodeOf(bind(selected = false))
        assertEquals(context.getString(R.string.kb_board_tab_role), node.roleDescription)
    }

    @Test
    fun theBoardInUseSaysSo() {
        assertTrue("the active tab must report selected", nodeOf(bind(selected = true)).isSelected)
        assertFalse("an inactive tab must not", nodeOf(bind(selected = false)).isSelected)
    }

    @Test
    fun theStateIsCarriedByMoreThanColour() {
        // The active tab's 3dp border against 1dp is the cue that survives
        // greyscale, a colour-vision deficiency and a photograph of the screen.
        val active = bind(selected = true).findViewById<View>(R.id.board_tab_border)
        val idle = bind(selected = false).findViewById<View>(R.id.board_tab_border)
        assertTrue(
            "the active tab's border must be visibly thicker, not just a different hue",
            active.layoutParams.height > idle.layoutParams.height,
        )
    }
}
