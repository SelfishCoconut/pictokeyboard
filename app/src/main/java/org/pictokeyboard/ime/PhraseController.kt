package org.pictokeyboard.ime

import android.view.inputmethod.InputConnection
import org.pictokeyboard.R
import org.pictokeyboard.tts.TtsManager

/**
 * The phrase, and everything done to it a word at a time.
 *
 * Two things that belong together: [Sentence] (what this keyboard has committed,
 * and in which language each word was) and [WordNavigator] (where in it the user
 * is standing). It used to own a strip of screen showing both, and #148 took
 * that away — it was a second copy of text the host's own field was already
 * displaying, for 30dp out of the grid.
 *
 * **So why does [Sentence] still exist with nothing drawing it?** Because the
 * field's characters are not enough to act on. 🔊 has to know which language each
 * word was written in — a Spanish voice reading an English word is not the word
 * — and Beautify has to hand the model the same. Neither can be recovered from a
 * string. What is gone is the *display*, not the record.
 *
 * The invariant it always carried survives too, and matters more now that it is
 * invisible: **a record, never a buffer.** Every word reaches the host's field
 * the instant it is tapped. Nothing here can hold a finished sentence back
 * behind a button somebody never finds, which for a user whose only voice is
 * this keyboard is a conversation that did not happen.
 *
 * @param announce says what an otherwise invisible action did. With no strip to
 *   watch, a key whose press changes nothing perceivable reads as broken.
 */
class PhraseController(
    private val connection: () -> InputConnection?,
    private val tts: TtsManager,
    private val fallbackLanguage: () -> String,
    private val announce: (Int) -> Unit,
) {

    /** The phrase written so far, in the order it was written. */
    var sentence = Sentence()
        private set

    /** Where in the phrase the arrows are standing (#143). */
    val navigator = WordNavigator(connection = connection, phraseWords = { sentence.texts() })

    /**
     * A tapped pictogram, committed and recorded.
     *
     * Returns the exact string put at the end of the field, or null when it went
     * into the middle instead — which is the one thing Beautify's bookkeeping has
     * to know, because its whole method is tracking the literal characters at the
     * *end* of the field and the end is no longer where this keyboard wrote.
     */
    fun add(text: String, language: String, addSpaceAfter: Boolean): String? {
        if (navigator.hasPlace) {
            val landed = navigator.insert(text)
            // The field took the word but the record could not say where. It
            // empties rather than claiming an order it cannot vouch for, since
            // 🔊 would otherwise read the phrase back wrong.
            sentence = landed?.let { sentence.insertAt(it, text, language) } ?: sentence.cleared()
            return null
        }
        val committed = if (addSpaceAfter) "$text " else text
        // Committed first, and unconditionally. The record is updated afterwards
        // because it describes what the field already has.
        connection()?.commitText(committed, 1)
        sentence = sentence.plus(text, language)
        return committed
    }

    /**
     * One press of an arrow: select the next or previous word and say it.
     *
     * The word is highlighted by the host itself — the selection is a real one —
     * so this adds the two channels the host cannot: the word spoken aloud, in
     * the language of the *word* rather than of the interface, and the same said
     * to a screen reader. A board may mix languages, and an English voice reading
     * `galleta` is not the word; where the record cannot identify it, the board's
     * own language is the best guess there is.
     */
    fun step(forward: Boolean) {
        val stepped = navigator.step(forward)
        if (stepped == null) {
            announce(R.string.kb_no_words)
            return
        }
        val language = stepped.phraseIndex
            ?.let { sentence.parts().getOrNull(it)?.language }
            ?: fallbackLanguage()
        tts.speak(stepped.text, language)
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
            is PhraseEffect.Removed -> sentence.removeAt(effect.index)
            PhraseEffect.Untouched -> sentence
            PhraseEffect.Lost -> sentence.cleared()
        }
        return true
    }

    /** Mirrors a backspace that took the last word out of the field. */
    fun dropLast() {
        sentence = sentence.dropLast()
    }

    /**
     * Forgets the phrase. The field is deliberately left alone.
     *
     * Deliberately asymmetric with backspace: ✕ means "I have finished with this
     * phrase", not "undo what I said". Reaching into the host field to delete a
     * sentence the user already sent would be the one destructive thing on this
     * keyboard.
     *
     * [aloud] because since #148 nothing on screen changes when this is pressed.
     * The only observable effect is on what 🔊 and ✨ will do next, and a key
     * that appears to do nothing is a key that reads as broken. Silent when the
     * caller is a new field or an app switch rather than the user.
     */
    fun clear(aloud: Boolean = false) {
        val hadWords = !sentence.isEmpty
        sentence = sentence.cleared()
        navigator.stop()
        if (aloud && hadWords) announce(R.string.kb_sentence_cleared)
    }

    /** Speaks the whole phrase back, each word still in its own voice. */
    fun speakAll() {
        if (sentence.isEmpty) {
            announce(R.string.kb_sentence_empty)
            return
        }
        tts.speakSequence(sentence.parts())
    }
}
