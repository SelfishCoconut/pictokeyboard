package org.pictokeyboard.ime

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

/**
 * Walking the phrase a word at a time, in the host app's own field (#143).
 *
 * Backspace used to be the whole edit vocabulary of this keyboard, and it can
 * only take the last word: to fix `galleta` in `yo · querer · galleta` you had
 * to delete two correct words to reach it and then find them again on the board.
 * The arrows reach any word directly.
 *
 * **The selection is real.** Each step calls `setSelection` on the host's field,
 * so the word is highlighted exactly as selected text is highlighted everywhere
 * else on the phone — in the message the user is actually writing, not only in
 * this keyboard's copy of it. That is what makes the highlight trustworthy, and
 * it is what makes backspace delete the right characters: the field is told
 * which ones, rather than the keyboard guessing how many.
 *
 * The sentence bar is kept in step through [FieldWords.align], which refuses to
 * guess. When the bar and the field have drifted apart — after a rephrase, most
 * obviously — the bar yields, because it is a mirror and a mirror that has lost
 * track must say so.
 *
 * @param barWords what the sentence bar currently shows, read on demand: the
 *   phrase changes under this class between one press and the next.
 */
class WordNavigator(private val connection: () -> InputConnection?, private val barWords: () -> List<String>) {

    /** The word selected in the field, or null when the arrows are not in use. */
    var selected: WordSpan? = null
        private set

    /** Which sentence-bar word [selected] is, when the two still agree. */
    var barIndex: Int? = null
        private set

    /** True when the last read produced a bar-to-field mapping at all. */
    private var aligned = false

    /** True when the highlighted bar word is exactly this one field word. */
    private var exact = false

    /**
     * The selection as the framework last reported it.
     *
     * Kept because [FieldReader]'s fallback path has no other way to turn "some
     * text before the cursor" into a position, and -1 is the framework's own way
     * of saying it does not know yet.
     */
    private var selStart = -1
    private var selEnd = -1

    val isNavigating: Boolean get() = selected != null

    /**
     * True when the next word the user taps has somewhere of its own to go —
     * after the highlighted word, or into the hole a delete left behind.
     *
     * Distinct from [isNavigating] because a delete ends the highlight but not
     * the position: that is exactly the moment the user reaches for the picto
     * they actually meant.
     */
    val hasPlace: Boolean get() = selected != null || barIndex != null

    /** A new field: forget the phrase, and take its starting caret. */
    fun onStartInput(info: EditorInfo?) {
        selStart = info?.initialSelStart ?: -1
        selEnd = info?.initialSelEnd ?: -1
        forget()
    }

    /**
     * The caret moved.
     *
     * Our own `setSelection` comes back through here with the values we set, so
     * only a position we did not choose ends navigation — which is the user
     * tapping into their own text, and a highlight left over that would be a
     * highlight on a word they are no longer on.
     */
    fun onSelectionChanged(newStart: Int, newEnd: Int) {
        selStart = newStart
        selEnd = newEnd
        val span = selected
        if (span != null && (span.start != newStart || span.end != newEnd)) forget()
    }

    /** One press of an arrow: select the next or previous word, and report it. */
    fun step(forward: Boolean): Stepped? {
        val window = readWindow() ?: return null
        val spans = window.spans()
        val target = FieldWords.step(spans, window.selStart, window.selEnd, forward)
        return target?.let { span -> Stepped(window.textOf(span), select(window, spans, span)) }
    }

    /**
     * Removes the selected word, and says what that did to the bar.
     *
     * The caret is deliberately left in the hole rather than sent to the end:
     * the next picto the user taps belongs where the wrong one was, which is the
     * repair the arrows exist to make possible. [insert] picks it up from there.
     */
    fun deleteSelected(): BarEffect? {
        val span = selected ?: return null
        val ic = connection() ?: return null
        val window = readWindow() ?: return null
        val range = FieldWords.deletionRange(window.text, window.offset, span)
        val effect = removalEffect()
        ic.beginBatchEdit()
        ic.setSelection(range.start, range.end)
        ic.commitText("", 1)
        ic.endBatchEdit()
        selected = null
        selStart = range.start
        selEnd = range.start
        barIndex = (effect as? BarEffect.Removed)?.index
        exact = barIndex != null
        return effect
    }

    /**
     * Puts [text] in at the navigation point, spaced to fit, and reports which
     * bar word it became.
     *
     * "After the highlighted word", or into the hole a delete just left. The
     * spacing is read off the characters either side rather than taken from the
     * add-a-space setting, because that setting is about appending to the end of
     * a phrase and this is not that.
     *
     * Returns null when the bar cannot follow, which the caller must treat as
     * the bar having lost track rather than as nothing having happened — the
     * field is written either way.
     */
    fun insert(text: String): Int? {
        val ic = connection() ?: return null
        val window = readWindow() ?: return null
        val highlighted = selected
        val at = highlighted?.end ?: window.selStart
        if (!window.holds(at)) return null
        val padded = FieldWords.padded(window.text, window.offset, at, text)
        val lead = padded.length - padded.trimStart().length
        val span = WordSpan(at + lead, at + lead + text.length)
        // All three inside one batch, and that is load-bearing rather than
        // tidiness: each of them would otherwise be reported back through
        // `onUpdateSelection` on its own, and the caret position the commit
        // reports is not a word — so the highlight this method just established
        // would be thrown away by its own edit a moment later.
        ic.beginBatchEdit()
        ic.setSelection(at, at)
        ic.commitText(padded, 1)
        ic.setSelection(span.start, span.end)
        ic.endBatchEdit()
        selected = span
        selStart = span.start
        selEnd = span.end
        // After a word: one place further along. Into the hole a delete left:
        // exactly where the deleted word was, which is the whole point of
        // leaving the caret there.
        val landed = when {
            !aligned -> null
            highlighted != null -> barIndex?.plus(1)
            else -> barIndex
        }
        barIndex = landed
        exact = landed != null
        return landed
    }

    /**
     * Stops navigating without destroying anything.
     *
     * The selection is collapsed to the end of the highlighted word first,
     * because a live selection plus any key that commits text is a replacement:
     * pressing space with `galleta` highlighted would otherwise overwrite it.
     */
    fun stop() {
        val span = selected
        if (span != null) {
            connection()?.setSelection(span.end, span.end)
            selStart = span.end
            selEnd = span.end
        }
        forget()
    }

    private fun forget() {
        selected = null
        barIndex = null
        aligned = false
        exact = false
    }

    private fun readWindow(): FieldWindow? =
        connection()?.let { FieldReader.read(it, selStart, selEnd) }

    /** Selects [span] in the field and works out which bar word it is. */
    private fun select(window: FieldWindow, spans: List<WordSpan>, span: WordSpan): Int? {
        connection()?.setSelection(span.start, span.end)
        selected = span
        selStart = span.start
        selEnd = span.end
        val map = FieldWords.align(spans.map(window::textOf), barWords())
        aligned = map != null
        barIndex = map?.get(spans.indexOf(span))
        exact = barIndex != null && map?.count { it.value == barIndex } == 1
        return barIndex
    }

    /** What deleting the current selection does to the bar. */
    private fun removalEffect(): BarEffect = when {
        !aligned -> BarEffect.Lost
        // A word of the field the bar never claimed — text from another
        // keyboard, a quoted reply. The phrase is unaffected and saying it was
        // would throw away a phrase that is still correct.
        barIndex == null -> BarEffect.Untouched
        // One bar word covering several field words: a picto labelled `me
        // gusta`, half of which is about to go. Half a picto is not a word the
        // bar can show, so the bar gives up rather than lie.
        !exact -> BarEffect.Lost
        else -> BarEffect.Removed(requireNotNull(barIndex))
    }
}

/** A word the arrows arrived at, and where it is in the sentence bar. */
data class Stepped(val text: String, val barIndex: Int?)

/** What an edit under the arrows did to the sentence bar. */
sealed interface BarEffect {

    /** The bar word at [index] went with it. */
    data class Removed(val index: Int) : BarEffect

    /** The field word was never the bar's, so the phrase still stands. */
    data object Untouched : BarEffect

    /** The bar can no longer say what the field holds, and must be emptied. */
    data object Lost : BarEffect
}
