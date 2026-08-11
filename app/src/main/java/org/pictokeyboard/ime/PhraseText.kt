package org.pictokeyboard.ime

import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.TextView

/**
 * How the sentence bar shows the word the arrows are standing on (#143).
 *
 * The host app's field already highlights it — the selection there is a real
 * one. This is the second copy, for the user whose host app puts its field
 * somewhere they cannot see, or who is reading the bar because it is the only
 * place the phrase is set in a font they can manage.
 */
object PhraseText {

    /**
     * [display] with [range] painted as selected text.
     *
     * Both the background *and* the ink change, because the accent is dark
     * enough that leaving the ink alone would put near-black on near-black in
     * one of the four palettes. The keyboard's pressed keys already flip the
     * pair together for exactly this reason.
     */
    fun highlighted(display: String, range: IntRange, background: Int, foreground: Int): CharSequence {
        val start = range.first.coerceIn(0, display.length)
        val end = (range.last + 1).coerceIn(start, display.length)
        return SpannableString(display).apply {
            setSpan(BackgroundColorSpan(background), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(foreground), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /**
     * Brings the phrase's interesting end into view.
     *
     * With no [range] that is the newest word, because the phrase grows to the
     * right and what was just written is what the user is checking. While the
     * arrows are in use it is the highlighted word instead — a highlight that
     * has scrolled off the end of the strip tells nobody anything — placed a
     * third of the way in rather than hard against the edge, so the words either
     * side of it stay readable and the phrase keeps its context.
     */
    fun reveal(scroll: HorizontalScrollView, text: TextView, range: IntRange?) {
        val layout = text.layout
        if (range == null || layout == null) {
            scroll.fullScroll(View.FOCUS_RIGHT)
            return
        }
        val x = layout.getPrimaryHorizontal(range.first.coerceIn(0, text.text.length)).toInt()
        scroll.smoothScrollTo((x - scroll.width / LEAD_DIVISOR).coerceAtLeast(0), 0)
    }

    private const val LEAD_DIVISOR = 3
}
