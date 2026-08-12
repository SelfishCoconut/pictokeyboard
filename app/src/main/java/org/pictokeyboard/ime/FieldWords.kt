package org.pictokeyboard.ime

/** One word in the host app's field, in that field's own character positions. */
data class WordSpan(val start: Int, val end: Int)

/**
 * Reading somebody else's text field as a list of words (#143).
 *
 * The arrows have to work in a field this keyboard does not own and cannot see
 * all of: the host may hold a whole email, the user may have typed part of it
 * with another keyboard, and the only handles the framework offers are "give me
 * some text before the cursor" and "give me some text after it". Everything here
 * is derived from that window, and everything here is pure — the decisions that
 * can corrupt somebody's message are decided by a unit test rather than by an
 * emulator with a chat app open.
 *
 * A word is a run of non-whitespace. Punctuation therefore travels with the word
 * it is attached to, which is what makes deleting `galleta.` from a rephrased
 * sentence remove the full stop with it rather than stranding one.
 */
object FieldWords {

    /** How much text to ask the host for on each side of the cursor. */
    const val WINDOW = 512

    private val WHITESPACE = Regex("\\s+")

    /**
     * The words in [window], in the field's own positions, where [window] begins
     * at field position [offset].
     *
     * A window is a slice, and a slice can cut a word in half. Half a word is
     * worse than no word — selecting it and pressing backspace would take a bite
     * out of text the user did not mean to touch — so a word touching a cut edge
     * is dropped. The head is cut whenever [offset] is past the start of the
     * field; the tail is cut unless [tailComplete] says the window reached the
     * end of it.
     */
    fun spans(window: CharSequence, offset: Int, tailComplete: Boolean): List<WordSpan> {
        val found = mutableListOf<WordSpan>()
        var i = 0
        while (i < window.length) {
            while (i < window.length && window[i].isWhitespace()) i++
            val start = i
            while (i < window.length && !window[i].isWhitespace()) i++
            if (i > start) found += WordSpan(offset + start, offset + i)
        }
        if (found.isNotEmpty() && offset > 0 && window.first().isWhitespace().not()) {
            found.removeAt(0)
        }
        if (found.isNotEmpty() && !tailComplete && window.last().isWhitespace().not()) {
            found.removeAt(found.lastIndex)
        }
        return found
    }

    /**
     * Where one press of an arrow lands, given the selection the field currently
     * reports.
     *
     * Four cases, in the order they are asked:
     *
     * - **The selection is exactly a word.** The user is already walking the
     *   phrase, so this is a step. It clamps at both ends rather than wrapping:
     *   wrapping from the last word to the first is silent teleportation for
     *   someone who is listening rather than looking, and pressing ▶ twice at the
     *   end should say the last word twice, not the first one.
     * - **The caret is inside a word.** The first press selects the word the user
     *   is standing in, rather than stepping over it.
     * - Otherwise the caret is between words, and the arrow reaches for the
     *   nearest word in the direction it points.
     * - Nothing to reach in that direction, so the nearest end.
     */
    fun step(spans: List<WordSpan>, selStart: Int, selEnd: Int, forward: Boolean): WordSpan? {
        if (spans.isEmpty()) return null
        val exact = spans.indexOfFirst { it.start == selStart && it.end == selEnd }
        val inside = spans.indexOfFirst { selStart > it.start && selEnd < it.end }
        val index = when {
            exact >= 0 -> exact + if (forward) 1 else -1
            inside >= 0 -> inside
            forward -> spans.indexOfFirst { it.start >= selEnd }.orElse(spans.lastIndex)
            else -> spans.indexOfLast { it.end <= selStart }.orElse(0)
        }
        return spans[index.coerceIn(0, spans.lastIndex)]
    }

    /**
     * The characters to remove so that deleting [word] does not leave a hole.
     *
     * One adjacent space goes with the word, or `yo quiero galleta` minus
     * `quiero` reads `yo  galleta` — two spaces the user cannot see, cannot
     * reach with the arrows, and would have to delete by feel. The space after
     * is preferred so that deleting the first word does not leave the phrase
     * beginning with one.
     */
    fun deletionRange(window: CharSequence, offset: Int, word: WordSpan): WordSpan {
        val after = word.end - offset
        val before = word.start - offset - 1
        return when {
            after in window.indices && window[after].isWhitespace() -> word.copy(end = word.end + 1)
            before in window.indices && window[before].isWhitespace() -> word.copy(start = word.start - 1)
            else -> word
        }
    }

    /**
     * [text] with whatever spaces the characters either side of [at] require.
     *
     * Inserting mid-phrase cannot use the add-a-space setting: that setting is
     * about what follows a word appended to the end, and here there is a word on
     * both sides. Reading the two characters actually there gets `yo agua
     * galleta` from `yo galleta` and from `yo  galleta` alike, and adds nothing
     * at the very start or the very end.
     */
    fun padded(window: CharSequence, offset: Int, at: Int, text: String): String {
        val i = at - offset
        val lead = i > 0 && i <= window.length && !window[i - 1].isWhitespace()
        val trail = i < window.length && i >= 0 && !window[i].isWhitespace()
        return (if (lead) " " else "") + text + (if (trail) " " else "")
    }

    /**
     * Which recorded word each field word belongs to, or null if the keyboard's
     * record and the field have drifted apart.
     *
     * The record holds what this keyboard committed, and the field may hold
     * anything besides — text typed with another keyboard, a quoted reply, a
     * draft. So the recorded words are looked for as a **run** inside the
     * field's, and only an exact match counts. The *last* matching run wins,
     * because a phrase that repeats a word (`yo quiero yo`) ends where the user
     * is working.
     *
     * One recorded word can hold more than one field word — a picto may be
     * labelled `me gusta` — so they are split before matching and several field
     * indices can point at the same recorded index. That is the whole reason
     * this returns a map instead of an offset.
     *
     * Null is the honest answer whenever no run matches: after a rephrase the
     * field holds the model's sentence and the record still holds the typed
     * words, and pretending to know which is which would delete the wrong one.
     */
    fun align(fieldWords: List<String>, phraseWords: List<String>): Map<Int, Int>? {
        val flat = phraseWords.flatMapIndexed { index, word ->
            word.split(WHITESPACE).filter { it.isNotEmpty() }.map { it to index }
        }
        if (flat.isEmpty() || flat.size > fieldWords.size) return null
        val start = (fieldWords.size - flat.size downTo 0).firstOrNull { at ->
            flat.indices.all { fieldWords[at + it] == flat[it].first }
        }
        return start?.let { at -> flat.indices.associate { (at + it) to flat[it].second } }
    }

    private fun Int.orElse(fallback: Int): Int = if (this >= 0) this else fallback
}
