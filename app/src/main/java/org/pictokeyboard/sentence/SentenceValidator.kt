package org.pictokeyboard.sentence

/** Why a candidate expansion was thrown away. */
enum class Rejection {
    /** It says a word about the world that the user never tapped. */
    ADDED_CONTENT_WORD,

    /** The user did not say no, and it does. */
    ADDED_NEGATION,

    /** The user said no, and it does not. */
    DROPPED_NEGATION,

    /** Nothing usable came back. */
    EMPTY,
}

/** The outcome of checking one candidate. */
sealed interface Verdict {
    data object Accepted : Verdict

    /** [words] are the ones that caused it, for the log and for the eval report. */
    data class Rejected(val reason: Rejection, val words: List<String> = emptyList()) : Verdict
}

/**
 * The check that stops the model putting words in someone's mouth.
 *
 * §12.5 of the UI architecture proposal, made executable:
 *
 * > **Content lemmas out ⊆ content lemmas in.** Function words — articles,
 * > prepositions, conjunctions, auxiliaries, inflection — may be added freely.
 * > Nouns, verbs, adjectives and adverbs may not.
 *
 * A prompt asking a model not to add meaning is a hope. This is a check, it runs
 * on every candidate before anything reaches the screen, and a candidate that
 * fails is discarded and regenerated rather than shown. That converts the
 * central safety property of the whole milestone — *the device speaks as the
 * user, never for them* — from an instruction into an invariant.
 *
 * **Negation is separate and stricter** (§12.3, after the intent row was cut).
 * Adding or dropping a negator does not merely embroider what the user said, it
 * reverses it, and a user who cannot read the result cannot catch it. So the set
 * of negators must match exactly in both directions: the model may not add *no*
 * to words the user did not negate, and may not quietly drop the *no* they
 * tapped. English contractions count — `don't` negates as surely as `not` does.
 *
 * Pure, and free of Android: everything interesting about this is decided by a
 * unit test rather than by a caregiver.
 */
class SentenceValidator {

    /**
     * Checks [candidate] against the words the user actually tapped.
     *
     * @param typed the words as tapped, each with the language of its own picto,
     *   because a board may mix them and *water* is not *agua*.
     * @param candidate the model's proposed sentence.
     * @param language the language [candidate] is written in.
     */
    fun check(typed: List<TypedWord>, candidate: String, language: String): Verdict {
        val lexicon = Lexicon.of(language)
        val candidateWords = candidate.split(WHITESPACE).filter { it.isNotBlank() }
        return when {
            candidateWords.isEmpty() -> Verdict.Rejected(Rejection.EMPTY)
            else -> negationVerdict(typed, candidateWords, lexicon)
                ?: contentVerdict(typed, candidateWords, lexicon)
        }
    }

    /** Content lemmas out ⊆ content lemmas in, which is the rule itself. */
    private fun contentVerdict(
        typed: List<TypedWord>,
        candidateWords: List<String>,
        lexicon: Lexicon,
    ): Verdict {
        // Each tapped word is licensed under its own language's rules *and* under
        // the candidate's. A board may mix languages, and an expansion that
        // carries an English word through into a Spanish sentence has not
        // invented anything — the user tapped it. Only the surface form travels:
        // this never lets "water" license "agua", because the two share no key
        // under either lexicon.
        val allowed = buildSet {
            typed.forEach { word ->
                WordKey.of(word.text, Lexicon.of(word.language))?.let(::add)
                WordKey.of(word.text, lexicon)?.let(::add)
            }
        }
        val invented = candidateWords
            .filter { isContent(it, lexicon) }
            .filter { WordKey.of(it, lexicon) !in allowed }

        return if (invented.isEmpty()) {
            Verdict.Accepted
        } else {
            Verdict.Rejected(Rejection.ADDED_CONTENT_WORD, invented)
        }
    }

    /**
     * Negation is counted per side rather than per word: the user tapping one
     * negator and the model writing two — *no quiero nada* for *no querer* — is
     * ordinary Spanish concord, not an added meaning. What matters is only
     * whether each side negates at all.
     */
    private fun negationVerdict(
        typed: List<TypedWord>,
        candidateWords: List<String>,
        lexicon: Lexicon,
    ): Verdict.Rejected? {
        val userNegated = typed.any { negates(it.text, Lexicon.of(it.language)) }
        val offered = candidateWords.filter { negates(it, lexicon) }
        return when {
            offered.isNotEmpty() && !userNegated ->
                Verdict.Rejected(Rejection.ADDED_NEGATION, offered)
            offered.isEmpty() && userNegated ->
                Verdict.Rejected(Rejection.DROPPED_NEGATION)
            else -> null
        }
    }

    /**
     * English hides negation inside a contraction, and `don't` is far more
     * likely out of a model than `do not`. Stripping the apostrophe first would
     * turn it into `dont`, which no list would match.
     */
    private fun negates(word: String, lexicon: Lexicon): Boolean {
        val plain = WordKey.normalise(word)
        // The second clause is the same contraction with the apostrophe already
        // lost, which is why it has to name the auxiliaries rather than take any
        // word ending in those two letters.
        val contracted = plain.endsWith("n't") ||
            (plain.endsWith("nt") && plain.dropLast(2) in ENGLISH_CONTRACTIBLE)
        return plain in lexicon.negators || contracted
    }

    /**
     * Negations are excluded here because they were already judged, exactly, by
     * [negationVerdict]. Leaving them in would have `don't` — a licensed way of
     * writing the `not` the user tapped — come back around as an invented
     * content word and reject the very expansion the negation rule just allowed.
     */
    private fun isContent(word: String, lexicon: Lexicon): Boolean {
        val plain = WordKey.normalise(word)
        return plain.isNotEmpty() && plain !in lexicon.functionWords && !negates(word, lexicon)
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")

        /**
         * The auxiliaries an apostrophe-less `n't` can be stuck to. Without this
         * the fallback would read any word ending in `nt` — *important*,
         * *presente* — as a negation and reject every expansion containing one.
         */
        val ENGLISH_CONTRACTIBLE = setOf(
            "do", "does", "did", "is", "are", "was", "were", "have", "has", "had",
            "ca", "wo", "would", "should", "could", "must", "ai",
        )
    }
}

/** One word as the user tapped it, keeping the language of the picto it came from. */
data class TypedWord(val text: String, val language: String)
