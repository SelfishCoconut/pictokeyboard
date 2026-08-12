package org.pictokeyboard.sentence

/** What a rephrase attempt produced. */
sealed interface Beautified {

    /** A candidate that passed [SentenceValidator]. */
    data class Sentence(val text: String) : Beautified

    /**
     * Nothing survived the validator, so the user's own words stand.
     *
     * Deliberately distinct from a failure: the machinery worked, and what it
     * produced was rejected for saying something the user did not. #46 requires
     * that this leaves the field untouched and says so, rather than showing a
     * near-miss and hoping.
     */
    data object NothingPassed : Beautified

    /** No model here — not downloaded, not loaded, or the process is gone. */
    data object Unavailable : Beautified
}

/**
 * One candidate sentence from a model.
 *
 * An interface so the part of this that carries the safety property — the retry
 * loop below — can be tested against a scripted engine, with no weights, no
 * `:llm` process and no device. See `BeautifierTest`.
 */
fun interface SentenceEngine {

    /**
     * @param variant which attempt this is. It steers the sampler away from the
     *   answer already given, which is what makes "Beautify again" cycle rather
     *   than return the same sentence, and what makes a retry after a rejection
     *   worth doing at all.
     * @return the raw text, or null if the model could not be reached.
     */
    suspend fun generate(typed: List<TypedWord>, language: String, variant: Int): String?
}

/**
 * Generate, check, and try again — the loop that turns #45's validator from a
 * rule into a behaviour.
 *
 * **The validator's verdict is final and there is no repair path.** A candidate
 * that adds a content word is discarded whole; it is never trimmed back into
 * compliance, because a sentence with the invented word cut out of it is a
 * sentence the model did not write and nobody checked. Either an untouched
 * candidate passes or the user's own words stand.
 *
 * Bounded, because the failure mode is a model that cannot stop adding a word.
 * Retrying that forever would burn battery producing nothing while somebody
 * waits mid-conversation.
 */
class Beautifier(private val engine: SentenceEngine, private val validator: SentenceValidator = SentenceValidator()) {

    /**
     * What the last call generated and threw away, in order.
     *
     * The candidate text is kept beside the verdict, not just the verdict
     * (#167). Without it, *"Left as you wrote it"* is the same sentence whether
     * the model wrote something good that the harness refused or wrote nonsense
     * — and those want opposite fixes. `SentenceService` logs this in a debug
     * build; #42's eval harness is the other reader.
     */
    var lastRejections: List<Discarded> = emptyList()
        private set

    /**
     * @param validate false runs the model with the harness off (#167). Only
     *   `SentenceService` may pass it, only after [ValidatorBypass.allowed] has
     *   agreed, and that never agrees outside a debug build.
     */
    suspend fun beautify(
        typed: List<TypedWord>,
        language: String,
        variant: Int = 0,
        attempts: Int = ModelSpec.MAX_ATTEMPTS,
        validate: Boolean = true,
    ): Beautified {
        val discarded = mutableListOf<Discarded>()
        var outcome: Beautified = Beautified.NothingPassed
        var attempt = 0

        // Keeps going only while nothing has been settled: NothingPassed is both
        // the starting state and the answer if every attempt is rejected, which
        // is exactly the condition "carry on trying".
        while (typed.isNotEmpty() && attempt < attempts && outcome == Beautified.NothingPassed) {
            // The variant advances with each retry as well as with each press, so
            // a rejected candidate is not simply asked for again at the same
            // sampler position -- which would return the same rejected candidate.
            outcome = attemptOnce(typed, language, variant + attempt, validate, discarded)
            attempt++
        }

        lastRejections = discarded
        return outcome
    }

    /** One generation, judged. [discarded] collects what was thrown away and why. */
    private suspend fun attemptOnce(
        typed: List<TypedWord>,
        language: String,
        variant: Int,
        validate: Boolean,
        discarded: MutableList<Discarded>,
    ): Beautified {
        val raw = engine.generate(typed, language, variant) ?: return Beautified.Unavailable
        val candidate = Prompts.firstCandidate(raw)
        val verdict = when {
            // The harness off, so the model can be judged on its own (#167).
            // Empty is still empty -- there is nothing to put in the field, and
            // that is a fact about the output rather than a rule about meaning.
            !validate -> if (candidate.isBlank()) Verdict.Rejected(Rejection.EMPTY) else Verdict.Accepted
            else -> validator.check(typed, candidate, language)
        }
        return when (verdict) {
            is Verdict.Accepted -> Beautified.Sentence(candidate)
            is Verdict.Rejected -> {
                discarded += Discarded(candidate, verdict.reason, verdict.words)
                Beautified.NothingPassed
            }
        }
    }
}

/**
 * A candidate the model produced and the loop threw away.
 *
 * [candidate] is the part that was missing (#167): a reason on its own says the
 * harness fired, and only the text says whether it was right to.
 */
data class Discarded(val candidate: String, val reason: Rejection, val words: List<String>)
