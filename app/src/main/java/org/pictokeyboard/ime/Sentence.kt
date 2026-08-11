package org.pictokeyboard.ime

import org.pictokeyboard.tts.TtsManager

/**
 * The phrase built so far, as the sentence bar shows it.
 *
 * **A mirror, never a buffer.** Every picto is committed to the host field the
 * instant it is tapped, exactly as before; this only remembers what went past so
 * it can be displayed and re-spoken. That distinction is the whole safety
 * argument: a buffer can strand a finished sentence behind a send button the
 * user never finds, and for someone whose only voice is this keyboard, a stranded
 * sentence is a conversation that did not happen.
 *
 * Each word keeps its own language, because a board may mix them and a Spanish
 * voice reading an English word is not the word.
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
     * Backspace deletes a whole word from the field, so the bar has to lose
     * exactly one too or the two drift apart and the bar stops being a mirror.
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

    /** Empties the bar. The field is deliberately left alone — see [Sentence]. */
    fun cleared(): Sentence = Sentence()

    /** Just the words, for aligning this phrase against the field's own. */
    fun texts(): List<String> = words.map { it.text }

    /**
     * Where the word at [index] sits inside [display], so it can be highlighted.
     *
     * Computed rather than stored because [display] is built on demand and the
     * separator is this class's business: a caller that measured the string
     * itself would have to know how wide a middot is.
     */
    fun range(index: Int): IntRange? {
        if (index !in words.indices) return null
        val start = words.take(index).sumOf { it.text.length + SEPARATOR.length }
        return start until start + words[index].text.length
    }

    val isEmpty: Boolean get() = words.isEmpty()

    /**
     * What the bar shows: words separated by a middot, which reads as a phrase
     * without implying the punctuation of one.
     */
    fun display(): String = words.joinToString(SEPARATOR) { it.text }

    /**
     * What a screen reader hears: the same words separated by commas.
     *
     * The visible middot is punctuation TalkBack pronounces — "yo middot comer
     * middot galleta" — so the readout gets its own separator rather than having
     * the display's typography spoken at it.
     */
    fun spokenDescription(): String = words.joinToString(", ") { it.text }

    /** The phrase for `speakSequence`, each word still in its own language. */
    fun parts(): List<TtsManager.Part> = words

    private companion object {
        const val SEPARATOR = " · "
    }
}
