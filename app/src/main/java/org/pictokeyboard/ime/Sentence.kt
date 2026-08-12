package org.pictokeyboard.ime

import org.pictokeyboard.tts.TtsManager

/**
 * The phrase built so far, as this keyboard committed it.
 *
 * **A record, never a buffer.** Every picto reaches the host field the instant
 * it is tapped; this only remembers what went past. That distinction is the
 * whole safety argument: a buffer can strand a finished sentence behind a send
 * button the user never finds, and for someone whose only voice is this
 * keyboard, a stranded sentence is a conversation that did not happen.
 *
 * **Nothing draws this any more** (#148) — the strip that did was a second copy
 * of what the host's own field already showed. It survives because the field's
 * characters are not enough to act on: each word keeps the language it was
 * written in, which 🔊 needs to pick a voice and Beautify needs to pick a
 * prompt, and neither can recover that from a string. A board may mix languages,
 * and a Spanish voice reading an English word is not the word.
 *
 * Immutable, and pure — the keyboard holds one of these and replaces it. That
 * keeps the interesting behaviour (what survives a delete, what a target change
 * clears) testable without an InputConnection.
 */
data class Sentence(val words: List<TtsManager.Part> = emptyList()) {

    /** Adds a word that has just been committed to the field. */
    fun plus(text: String, language: String): Sentence =
        if (text.isBlank()) this else Sentence(words + TtsManager.Part(text.trim(), language))

    /**
     * Drops the last word, mirroring a backspace.
     *
     * Backspace deletes a whole word from the field, so the record has to lose
     * exactly one too, or 🔊 reads back a phrase the field no longer holds.
     */
    fun dropLast(): Sentence = if (words.isEmpty()) this else Sentence(words.dropLast(1))

    /**
     * Drops the word at [index], mirroring a delete made with the arrows (#143).
     *
     * Unlike [dropLast] this can take a word out of the middle, which is the
     * only reason the arrows are worth having. Out-of-range is a no-op rather
     * than a crash: the caller's index comes from aligning this list against a
     * field that another app can rewrite between one press and the next.
     */
    fun removeAt(index: Int): Sentence =
        if (index in words.indices) Sentence(words.filterIndexed { i, _ -> i != index }) else this

    /** Puts a word in at [index], for a picto tapped while the arrows are in use. */
    fun insertAt(index: Int, text: String, language: String): Sentence =
        if (text.isBlank()) {
            this
        } else {
            Sentence(
                words.toMutableList().apply {
                    add(index.coerceIn(0, size), TtsManager.Part(text.trim(), language))
                },
            )
        }

    /** Forgets the phrase. The field is deliberately left alone — see [Sentence]. */
    fun cleared(): Sentence = Sentence()

    /** Just the words, for aligning this phrase against the field's own. */
    fun texts(): List<String> = words.map { it.text }

    val isEmpty: Boolean get() = words.isEmpty()

    /** The phrase for `speakSequence`, each word still in its own language. */
    fun parts(): List<TtsManager.Part> = words
}
