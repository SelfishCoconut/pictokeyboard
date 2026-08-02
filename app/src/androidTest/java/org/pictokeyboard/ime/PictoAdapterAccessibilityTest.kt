package org.pictokeyboard.ime

import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.pictokeyboard.data.db.PictoEntity

/**
 * The keyboard's keys, as the accessibility framework sees them.
 *
 * This is the coverage #25 needed and never had. The defect was that a picto
 * key's accessible name came only from its caption `TextView`, so turning off
 * **Show captions under pictos** — a shipped, supported setting — turned every
 * key on an AAC board into an anonymous button. On a keyboard whose keys *are*
 * the vocabulary, that is a total loss of function for a screen-reader user.
 *
 * A JVM test cannot catch it: the name is set on a real `View` during a real
 * `onBindViewHolder`, against a real layout. Hence instrumented.
 */
@RunWith(AndroidJUnit4::class)
class PictoAdapterAccessibilityTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun picto(label: String = "pan", spoken: String = "pan") =
        PictoEntity(
            id = "1",
            categoryId = "food",
            label = label,
            spokenText = spoken,
            language = "es",
            position = 0,
        )

    /** Binds one tile and hands back the row view, exactly as the grid would. */
    private fun bind(picto: PictoEntity, showLabels: Boolean): android.view.View {
        val adapter = PictoAdapter(onClick = {}, onLongClick = {})
        adapter.submit(
            pictos = listOf(picto),
            imageModels = emptyMap(),
            style = PictoAdapter.Style(categoryColor = 0xFFF57C00.toInt(), showLabels = showLabels),
        )
        val parent = FrameLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)
        // submitList diffs asynchronously; bind the item directly so the test is
        // about the binding rather than about AsyncListDiffer's scheduling.
        holder.bind(
            PictoAdapter.Tile(
                picto = picto,
                imageModel = null,
                frameColor = 0xFFF57C00.toInt(),
                borderStyle = org.pictokeyboard.data.db.BorderStyles.SOLID,
                borderWidthDp = org.pictokeyboard.data.db.BorderStyles.DEFAULT_WIDTH_DP,
                showLabel = showLabels && picto.label.isNotBlank(),
            ),
        )
        return holder.itemView
    }

    @Test
    fun aKeyIsNamedWhenCaptionsAreOn() {
        val view = bind(picto(), showLabels = true)
        assertEquals("pan", view.contentDescription)
    }

    @Test
    fun aKeyIsStillNamedWhenCaptionsAreOff() {
        // The regression that mattered: the name must come from the data, not
        // from whether the caption happens to be drawn.
        val view = bind(picto(), showLabels = false)
        assertEquals("pan", view.contentDescription)
    }

    @Test
    fun aKeyWithNoCaptionIsNamedFromItsSpokenText() {
        val view = bind(picto(label = "", spoken = "quiero"), showLabels = true)
        assertEquals("quiero", view.contentDescription)
    }

    @Test
    fun everyKeyIsReachableAsAButton() {
        val view = bind(picto(), showLabels = false)
        assertTrue("a key must be clickable to be operable", view.isClickable)
        assertNotNull(view.contentDescription)
        assertTrue("a key must not announce as empty", view.contentDescription.isNotBlank())
    }

    @Test
    fun theLayoutItselfLeavesNamingToTheData() {
        // The ImageView is deliberately contentDescription="@null": the row view
        // carries the name, and a described child would announce twice.
        val row = LayoutInflater.from(context)
            .inflate(org.pictokeyboard.R.layout.item_picto, FrameLayout(context), false)
        val image = row.findViewById<android.view.View>(org.pictokeyboard.R.id.picto_image)
        assertEquals(null, image.contentDescription)
    }
}
