package org.pictokeyboard.ime

import android.content.Context
import android.view.View
import android.view.inputmethod.InputConnection
import android.widget.HorizontalScrollView
import android.widget.TextView
import org.pictokeyboard.R
import org.pictokeyboard.tts.TtsManager

/**
 * The phrase, the bar that shows it, and everything done to it a word at a time.
 *
 * Three things that had grown apart inside `PictoKeyboardService` and belong
 * together: [Sentence] (what has been said), [WordNavigator] (where in it the
 * user is standing), and the strip of screen that draws both. Keeping them in
 * one place is what makes the invariant checkable — **the bar is a mirror,
 * never a buffer.** Every word reaches the host's field the instant it is
 * tapped; nothing here can hold a finished sentence back behind a button
 * somebody never finds, which for a user whose only voice is this keyboard is a
 * conversation that did not happen.
 *
 * The keyboard keeps what is genuinely its own: which key was pressed, and the
 * separate bookkeeping Beautify needs about the literal characters in the field.
 */
class PhraseController(
    private val connection: () -> InputConnection?,
    private val strings: () -> Context,
    private val palette: () -> KeyboardPalette?,
    private val tts: TtsManager,
    private val fallbackLanguage: () -> String,
    private val announce: (Int) -> Unit,
) {

    /** The phrase written so far, in the order it was written. */
    var sentence = Sentence()
        private set

    /** Where in the phrase the arrows are standing (#143). */
    val navigator = WordNavigator(connection = connection, barWords = { sentence.texts() })

    /**
     * Shown in place of the phrase while something else needs the row, in the
     * alarm colour — the bell's countdown being the only thing that does (#144).
     *
     * A field rather than a separate view because the row already exists, is
     * already about words rather than keys, and is the widest thing on this
     * keyboard: a call the user did not mean to start has to be unmissable.
     */
    var alert: CharSequence? = null

    private var view: TextView? = null
    private var scroll: HorizontalScrollView? = null

    fun attach(text: TextView, strip: HorizontalScrollView) {
        view = text
        scroll = strip
    }

    /** Draws the phrase, the highlight, and what a screen reader should hear. */
    fun render() {
        val text = view ?: return
        val skin = palette()
        val message = alert
        if (message != null) {
            text.text = message
            text.contentDescription = message
            skin?.let { text.setTextColor(it.danger) }
            scroll?.post { scroll?.fullScroll(View.FOCUS_LEFT) }
            return
        }
        val display = sentence.display()
        val highlight = navigator.barIndex
            ?.takeIf { navigator.isNavigating }
            ?.let { sentence.range(it) }
        skin?.let { text.setTextColor(it.ink) }
        text.text = if (highlight == null || skin == null) {
            display
        } else {
            PhraseText.highlighted(display, highlight, skin.accent, skin.onAccent)
        }
        // The middot is typography; a screen reader would pronounce it.
        text.contentDescription = if (sentence.isEmpty) {
            strings().getString(R.string.kb_sentence_empty)
        } else {
            strings().getString(R.string.kb_sentence_a11y, sentence.spokenDescription())
        }
        scroll?.post { scroll?.let { PhraseText.reveal(it, text, highlight) } }
    }

    /**
     * A tapped pictogram, committed and mirrored.
     *
     * Returns the exact string put at the end of the field, or null when it went
     * into the middle instead — which is the one thing Beautify's bookkeeping has
     * to know, because its whole method is tracking the literal characters at the
     * *end* of the field and the end is no longer where this keyboard wrote.
     */
    fun add(text: String, language: String, addSpaceAfter: Boolean): String? {
        if (navigator.hasPlace) {
            val landed = navigator.insert(text)
            // The field took the word but the bar could not say where. A mirror
            // that has lost track empties rather than showing a phrase the field
            // does not hold.
            sentence = landed?.let { sentence.insertAt(it, text, language) } ?: sentence.cleared()
            render()
            return null
        }
        val committed = if (addSpaceAfter) "$text " else text
        // Committed first, and unconditionally. The bar is told afterwards
        // because it mirrors what the field already has.
        connection()?.commitText(committed, 1)
        sentence = sentence.plus(text, language)
        render()
        return committed
    }

    /**
     * One press of an arrow: select the next or previous word and say it.
     *
     * Spoken in the language of the *word* wherever the bar can identify it — a
     * board may mix languages, and an English voice reading `galleta` is not the
     * word. Where it cannot, the board's own language is the best guess there is.
     */
    fun step(forward: Boolean) {
        val stepped = navigator.step(forward)
        if (stepped == null) {
            announce(R.string.kb_no_words)
            return
        }
        render()
        val language = stepped.barIndex
            ?.let { sentence.parts().getOrNull(it)?.language }
            ?: fallbackLanguage()
        tts.speak(stepped.text, language)
        view?.announceForAccessibility(strings().getString(R.string.kb_word_a11y, stepped.text))
    }

    /**
     * Deletes the highlighted word, wherever it is in the phrase.
     *
     * Returns false when there was nothing selected, so the keyboard can fall
     * back to taking the last word instead — which is what backspace has always
     * done and still does when the arrows are not in use.
     */
    fun removeSelected(): Boolean {
        val effect = navigator.deleteSelected() ?: return false
        sentence = when (effect) {
            is BarEffect.Removed -> sentence.removeAt(effect.index)
            BarEffect.Untouched -> sentence
            BarEffect.Lost -> sentence.cleared()
        }
        render()
        return true
    }

    /** Mirrors a backspace that took the last word out of the field. */
    fun dropLast() {
        sentence = sentence.dropLast()
        render()
    }

    /** Empties the bar. The field is deliberately left alone — see [Sentence]. */
    fun clear() {
        sentence = sentence.cleared()
        navigator.stop()
        render()
    }

    /** Speaks the whole phrase back, each word still in its own voice. */
    fun speakAll() {
        if (sentence.isEmpty) return
        tts.speakSequence(sentence.parts())
    }
}
