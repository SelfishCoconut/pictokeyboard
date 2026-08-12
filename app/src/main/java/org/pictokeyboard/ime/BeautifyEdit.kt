package org.pictokeyboard.ime

/**
 * The exact characters this keyboard has put in the field for the current
 * phrase, and the rephrase it has swapped in for them.
 *
 * **This exists because of the one real trap in #46.** Beautify replaces text
 * that is *already committed*, and the host app can change that text underneath
 * us at any moment — an autocorrect, a paste, a chat app clearing the box when a
 * message sends. Deleting "however many characters we think we wrote" would
 * eventually eat somebody's message, and the person least able to notice is the
 * one this keyboard is for.
 *
 * So the range is tracked as the literal string, and every edit is *checked*
 * against the field before it happens: if what is sitting before the cursor is
 * not what this class says it put there, the swap is refused rather than
 * attempted. Refusing costs a rephrase. Guessing costs a sentence.
 *
 * Pure and free of Android, so all of that is decided by a unit test rather than
 * by an emulator with a chat app open.
 */
data class BeautifyEdit(
    /** Everything committed for this phrase, spacing included, as tapped. */
    val typed: String = "",
    /** What was swapped in, if a rephrase is currently applied. */
    val applied: String? = null,
    /** How many rephrases have been asked for, which cycles the variants. */
    val variant: Int = 0,
) {

    /** What should be sitting immediately before the cursor right now. */
    val inField: String get() = applied ?: typed

    val canBeautify: Boolean get() = typed.isNotBlank()

    /** Undo is offered only while a rephrase is actually in place. */
    val canUndo: Boolean get() = applied != null

    /** A picto was tapped and its text committed. */
    fun plus(text: String): BeautifyEdit =
        // A rephrase followed by another word is no longer a rephrase of
        // anything this class can undo: the field now holds the model's sentence
        // and the new word together, and "the words you typed" is no longer a
        // string that ever existed. The applied state is dropped and what is in
        // the field becomes the new baseline.
        BeautifyEdit(typed = inField + text)

    /**
     * A backspace removed [count] characters from the field.
     *
     * Undo is dropped for the same reason as above, and the baseline shrinks by
     * what actually left the field.
     */
    fun minus(count: Int): BeautifyEdit =
        BeautifyEdit(typed = inField.dropLast(count.coerceAtMost(inField.length)))

    /** A rephrase was accepted into the field. */
    fun applying(sentence: String): BeautifyEdit =
        copy(applied = sentence, variant = variant + 1)

    /** Undo put the typed words back. */
    fun undone(): BeautifyEdit = copy(applied = null)

    /**
     * The rephrase stands, and the undo window is over (#166).
     *
     * The sentence in the field is the one the user is keeping, so it becomes
     * the new baseline exactly as an extra word would have made it. The variant
     * goes with it, because the next press is a fresh question rather than the
     * next answer to the old one.
     *
     * Reached only when the user genuinely moved on — sent the sentence, or left
     * for the letter keyboard. Speaking it, walking it and calling for help do
     * not come here (#173).
     */
    fun accepted(): BeautifyEdit = BeautifyEdit(typed = inField)

    /**
     * The phrase is over: a new field, a cleared bar, or a selection replaced.
     *
     * Everything goes, including the variant, so nothing leaks from one app into
     * the next (#46).
     */
    fun cleared(): BeautifyEdit = BeautifyEdit()
}
