package org.pictokeyboard.sentence

import java.text.Normalizer
import java.util.Locale

/**
 * Reduces a word to something two inflections of it both land on.
 *
 * Not a lemmatiser and not trying to be. The only question asked of it is
 * "could these two words be the same word?", and the answer is allowed to be
 * approximate in one direction only: **saying no when the answer was yes costs a
 * regeneration; saying yes when the answer was no is a hole in the safety
 * property.** So every rule here is one that merges inflections of a lemma, and
 * none is one that merges words a speaker would call different.
 *
 * The parts, in order: strip accents, strip the trailing punctuation an
 * expansion adds, look the word up in the irregular table, undo the stressed
 * vowel change, then remove the longest inflectional suffix that leaves a stem
 * worth comparing.
 */
internal object WordKey {

    /**
     * Below this, suffix stripping stops. Spanish stems like *ir* and *ver* are
     * three characters and a shorter stem stops discriminating between words at
     * all — *comer* and *comprar* would meet at *com*.
     */
    private const val MIN_STEM = 3

    private val ACCENTS = Regex("\\p{Mn}+")
    private val NOT_LETTERS = Regex("[^\\p{L}\\p{N}']")

    /**
     * The comparison key for [word], or null when nothing of it survives.
     *
     * The order is strip, unstress, strip, and it is not interchangeable. The
     * Spanish gerund ends `-iendo`, which contains the very `ie` the unstressing
     * rule exists to undo — run it first and *comiendo* becomes *comendo*, which
     * no longer ends in a suffix the stripper knows, and the commonest verb form
     * in the language stops matching its own infinitive. Taking the ending off
     * first leaves only the stem for the vowel rule to look at, which is the only
     * place it was ever meant to apply. The second strip is for plurals of words
     * whose singular still ends in an inflection: *mujeres* → *mujer* → *muj*.
     */
    fun of(word: String, lexicon: Lexicon): String? {
        val plain = normalise(word)
        return when {
            plain.isEmpty() -> null
            plain in lexicon.irregulars -> lexicon.irregulars[plain]
            else -> {
                val bare = stripOnce(plain, lexicon) ?: plain
                val stem = unstress(bare, lexicon)
                stripOnce(stem, lexicon) ?: stem
            }
        }
    }

    /**
     * Lowercased, unaccented and stripped of punctuation.
     *
     * Accents go because an expansion writes *comió* where the board holds
     * *comio*, and because Spanish inflection moves the accent around the same
     * lemma. The apostrophe survives normalisation so English contractions still
     * carry their `n't`.
     */
    fun normalise(word: String): String =
        Normalizer.normalize(word.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(ACCENTS, "")
            .replace(NOT_LETTERS, "")

    private fun unstress(word: String, lexicon: Lexicon): String {
        for ((stressed, plain) in lexicon.stemChanges) {
            if (stressed.containsMatchIn(word)) return stressed.replaceFirst(word, plain)
        }
        return word
    }

    /** The longest inflection that can come off and still leave [MIN_STEM]. */
    private fun stripOnce(word: String, lexicon: Lexicon): String? {
        for (suffix in lexicon.suffixes) {
            if (word.length - suffix.length >= MIN_STEM && word.endsWith(suffix)) {
                return word.dropLast(suffix.length)
            }
        }
        return null
    }
}
