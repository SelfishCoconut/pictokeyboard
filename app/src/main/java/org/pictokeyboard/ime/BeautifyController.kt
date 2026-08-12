package org.pictokeyboard.ime

import android.content.Context
import android.view.inputmethod.InputConnection
import org.pictokeyboard.R
import org.pictokeyboard.sentence.ModelStore
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
 * the phrase currently is.
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

    /**
     * True when the feature is on and the weights are on this phone.
     *
     * **Deliberately not "the engine has finished loading" (#157).** That is what
     * this asked before, and the answer arrives *later than the question*: the
     * key is drawn the moment settings load, the binder connects a beat after
     * that, and the weights take a further two seconds. Nothing told the keyboard
     * when readiness finally arrived, so the key was drawn hidden and stayed
     * hidden — on every cold start of the keyboard, which is most of them, since
     * Android reclaims an IME process the moment it is off screen. Only opening
     * Settings and touching a switch brought it back.
     *
     * Waiting was never necessary anyway. `SentenceService` loads the weights on
     * demand inside the request it is given, so a press during those two seconds
     * is served — just slowly, which is the wait Settings already warns about.
     */
    val isAvailable: Boolean get() = client != null

    /**
     * Binds or releases the model process as the setting changes.
     *
     * **Gated on the weights actually being on disk, not just on the setting.**
     * Binding starts `:llm`, and that process costs about 115 MB resident before
     * a single weight is loaded -- it is the runtime's native library mapped in.
     * Somebody who has switched the feature on but not yet downloaded the model
     * would otherwise pay all of that for a button that cannot appear.
     *
     * Unbinding is what lets Android reclaim it when the feature is switched off,
     * which is the other half of #48's promise that turning it off is easy and
     * means something.
     */
    fun setEnabled(enabled: Boolean) {
        if (enabled && ModelStore(context).isDownloaded()) {
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

    /**
     * Every one of these repaints, and that is the fix rather than a detail.
     *
     * `BeautifyEdit` has always dropped the applied state when a word arrives or
     * leaves — the undo window really was over. What nothing did was *say so*,
     * so the key went on showing ↺ for a rephrase that could no longer be
     * undone, and pressing it rephrased instead (#166). A glyph that lies about
     * what the key will do is worse than no glyph on a surface this small.
     *
     * Unconditional because the alternative is comparing the old state to the
     * new in three places to save a `findViewById` and four setters on a tap
     * that already does more work than that.
     */
    fun onCommitted(text: String) {
        edit = edit.plus(text)
        onStateChanged()
    }

    fun onDeleted(count: Int) {
        edit = edit.minus(count)
        onStateChanged()
    }

    fun onPhraseCleared() {
        edit = edit.cleared()
        onStateChanged()
    }

    /**
     * The user moved on, so the rephrase is accepted (#166).
     *
     * Undo is offered for the moment right after a rephrase, not indefinitely:
     * once the sentence has been sent, or the user has gone off to the letter
     * keyboard, a ↺ still sitting on the key is an invitation to lose it.
     *
     * **Which presses count is the keyboard's call, not this class's** (#173),
     * and it is a narrow list — see `PictoKeyboardService.KEEPS_UNDO`. Reading
     * the sentence back with 🔊 and ringing the bell both leave the window open,
     * because neither is moving on and neither touches the field. Hearing it is
     * how somebody who cannot read the field checks a rephrase, and a check that
     * destroys what it is checking is worse than no check.
     */
    fun onAccepted() {
        if (!edit.canUndo) return
        edit = edit.accepted()
        onStateChanged()
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
     * key. The phrase's key row divides one strip between everything that acts
     * on the phrase, and an Undo that only does something in one state is a key
     * that is dead most of the time.
     */
    fun press(typed: List<TypedWord>) {
        if (edit.canUndo) {
            undo()
            return
        }
        if (working) return
        // Silence was the worst of the three answers this key could give (#166).
        // A press with nothing to rephrase is what happens after typing on the
        // letter keyboard, after the host app empties its own field, and after a
        // phrase is forgotten because the field moved on -- and all three looked
        // exactly like a keyboard that had stopped working.
        if (!edit.canBeautify || typed.isEmpty()) {
            announce(R.string.kb_beautify_empty)
            return
        }
        val remote = client ?: run {
            announce(R.string.kb_beautify_unavailable)
            return
        }

        working = true
        // Said as well as drawn. The glyph answers "did my tap land?" for anyone
        // watching the key; this answers it for the user who cannot see it, and
        // TalkBack does not announce a content-description change on a view that
        // has just been disabled.
        announce(R.string.kb_beautify_working)
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
        if (connection()?.replaceTail(edit.inField, sentence) != true) {
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
        if (connection()?.replaceTail(applied, edit.typed) != true) {
            announce(R.string.kb_beautify_changed)
            edit = edit.cleared()
        } else {
            edit = edit.undone()
            announce(R.string.kb_beautify_undone)
        }
        onStateChanged()
    }
}

/**
 * Replaces [expected] immediately before the cursor with [replacement],
 * refusing if the field does not actually end with [expected].
 *
 * **The check is the whole point.** The host app can rewrite its own field at
 * any moment — an autocorrect, a paste, a chat app clearing the box when a
 * message sends — and a blind `deleteSurroundingText` of the length we think we
 * wrote would delete whatever happens to be there instead. Refusing costs a
 * rephrase; guessing costs somebody's message.
 *
 * `beginBatchEdit` so the host sees one change rather than a delete followed by
 * an insert: an app watching its own field would otherwise see it momentarily
 * empty and may act on that.
 *
 * A free function rather than a member because it needs nothing from the
 * controller but the connection it is called on, and [BeautifyController] is at
 * the size where the next thing added to it has to earn its place.
 */
private fun InputConnection.replaceTail(expected: String, replacement: String): Boolean {
    if (expected.isEmpty()) return false
    if (getTextBeforeCursor(expected.length, 0)?.toString() != expected) return false
    beginBatchEdit()
    val deleted = deleteSurroundingText(expected.length, 0)
    val inserted = commitText(replacement, 1)
    endBatchEdit()
    return deleted && inserted
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
