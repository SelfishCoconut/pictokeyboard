package org.pictokeyboard.ime

import android.content.Context
import android.view.inputmethod.InputConnection
import org.pictokeyboard.R
import org.pictokeyboard.sentence.TypedWord
import org.pictokeyboard.sentence.llm.BeautifyOutcome
import org.pictokeyboard.sentence.llm.SentenceClient

/**
 * Everything Beautify does to the host's field, kept out of the keyboard.
 *
 * `PictoKeyboardService` is already the largest class in the app and this is a
 * self-contained job: hold the binder to the model process, track what the
 * keyboard put in the field, and swap one for the other safely. The service
 * keeps the parts that are genuinely its own — which key was pressed, and what
 * the sentence bar currently says.
 *
 * @param connection read on demand rather than held, because the field the user
 *   is in can change between a press and the answer coming back.
 * @param announce says what happened, out loud and to TalkBack: a rephrase
 *   changes text the user may not be able to read back, so the fact that it
 *   happened cannot be carried by the field alone.
 */
class BeautifyController(
    private val context: Context,
    private val connection: () -> InputConnection?,
    private val onStateChanged: () -> Unit,
    private val announce: (Int) -> Unit,
) {

    var edit = BeautifyEdit()
        private set

    /** True while a rephrase is in flight, so a second press cannot queue one. */
    var working = false
        private set

    private var client: SentenceClient? = null

    /** True only if a press right now would actually be served. */
    val isAvailable: Boolean get() = client?.isReady() == true

    /**
     * Binds or releases the model process as the setting changes.
     *
     * Unbinding is what lets Android reclaim several hundred megabytes when the
     * feature is switched off, which is the other half of #48's promise that
     * turning it off is easy and means something.
     */
    fun setEnabled(enabled: Boolean) {
        if (enabled) {
            val existing = client ?: SentenceClient(context).also { client = it }
            existing.bind()
        } else {
            client?.unbind()
            client = null
            edit = edit.cleared()
        }
    }

    fun release() {
        client?.unbind()
        client = null
    }

    // --- Keeping the tracked range in step with the field --------------------

    fun onCommitted(text: String) {
        edit = edit.plus(text)
    }

    fun onDeleted(count: Int) {
        edit = edit.minus(count)
    }

    fun onPhraseCleared() {
        edit = edit.cleared()
    }

    /**
     * A different field. Anything in flight is abandoned so a sentence generated
     * for one app can never arrive in another (#46).
     */
    fun onTargetChanged() {
        edit = edit.cleared()
        client?.cancelCurrent()
        working = false
    }

    // --- The press -----------------------------------------------------------

    /**
     * One button, two jobs: rephrase, or put the typed words back.
     *
     * It becomes Undo the moment a rephrase is applied rather than being a second
     * key. The sentence bar has room for four controls at 48dp and already has
     * three, and an Undo that only does something in one state is a key that is
     * dead most of the time.
     */
    fun press(typed: List<TypedWord>) {
        if (edit.canUndo) {
            undo()
            return
        }
        if (working || !edit.canBeautify || typed.isEmpty()) return
        val remote = client ?: return

        working = true
        onStateChanged()
        remote.beautify(typed = typed, language = dominantLanguage(typed), variant = edit.variant) { outcome ->
            working = false
            when (outcome) {
                is BeautifyOutcome.Sentence -> apply(outcome.text)
                // "It ran, and would not say it any other way without putting
                // words in your mouth." The words are left exactly as typed,
                // which is what #45 requires when nothing passes the validator.
                BeautifyOutcome.NothingPassed -> announce(R.string.kb_beautify_nothing)
                BeautifyOutcome.Unavailable -> announce(R.string.kb_beautify_unavailable)
            }
            onStateChanged()
        }
    }

    /**
     * Swaps the typed words for [sentence], but only if the field still holds
     * what this keyboard put there.
     */
    private fun apply(sentence: String) {
        if (!replaceTail(edit.inField, sentence)) {
            announce(R.string.kb_beautify_changed)
            edit = edit.cleared()
            return
        }
        edit = edit.applying(sentence)
        announce(R.string.kb_beautify_applied)
    }

    /** Puts back the exact words the user tapped, checked the same way. */
    private fun undo() {
        val applied = edit.applied ?: return
        if (!replaceTail(applied, edit.typed)) {
            announce(R.string.kb_beautify_changed)
            edit = edit.cleared()
        } else {
            edit = edit.undone()
            announce(R.string.kb_beautify_undone)
        }
        onStateChanged()
    }

    /**
     * Replaces [expected] immediately before the cursor with [replacement],
     * refusing if the field does not actually end with [expected].
     *
     * **The check is the whole point.** The host app can rewrite its own field at
     * any moment — an autocorrect, a paste, a chat app clearing the box when a
     * message sends — and a blind `deleteSurroundingText` of the length we think
     * we wrote would delete whatever happens to be there instead. Refusing costs
     * a rephrase; guessing costs somebody's message.
     *
     * `beginBatchEdit` so the host sees one change rather than a delete followed
     * by an insert: an app watching its own field would otherwise see it
     * momentarily empty and may act on that.
     */
    private fun replaceTail(expected: String, replacement: String): Boolean {
        if (expected.isEmpty()) return false
        val ic = connection() ?: return false
        if (ic.getTextBeforeCursor(expected.length, 0)?.toString() != expected) return false
        ic.beginBatchEdit()
        val deleted = ic.deleteSurroundingText(expected.length, 0)
        val inserted = ic.commitText(replacement, 1)
        ic.endBatchEdit()
        return deleted && inserted
    }
}

/**
 * Which language a mixed phrase is mostly in.
 *
 * The language of the *words*, not of the interface and not of the phone. A
 * board may mix languages, and a caregiver may well run the app in Spanish
 * while the person using it has an English board — so the only honest answer
 * to "what language is this sentence in" is the one most of its pictos are
 * labelled in. Ties go to the first word, which sets the sentence's shape.
 */
private fun dominantLanguage(typed: List<TypedWord>): String =
    typed.map { it.language }
        .filter { it.isNotBlank() }
        .groupingBy { it }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key
        .orEmpty()
