package org.pictokeyboard.sentence

/** What a rephrase attempt produced. */
sealed interface Beautified {

    /** A sentence to put in the field. */
    data class Sentence(val text: String) : Beautified

    /**
     * The model answered with nothing usable, so the user's own words stand.
     *
     * Deliberately distinct from a failure: the machinery worked and produced
     * nothing to say. #46 requires that this leaves the field untouched and says
     * so, rather than showing a near-miss and hoping. Since #186 it is the only
     * way a rephrase comes back empty-handed — the validator no longer refuses
     * anything.
     */
    data object NothingPassed : Beautified

    /** No model here — not downloaded, not loaded, or the process is gone. */
    data object Unavailable : Beautified
}

/**
 * One candidate sentence from a model.
 *
 * An interface so the loop below can be tested against a scripted engine, with
 * no weights, no `:llm` process and no device. See `BeautifierTest`.
 */
fun interface SentenceEngine {

    /**
     * @param variant which attempt this is. It moves the sampler, and on the
     *   shipped model it moves it very little (#177) — what it reliably does is
     *   make a second *press* a different question from the first.
     * @return the raw text, or null if the model could not be reached.
     */
    suspend fun generate(typed: List<TypedWord>, language: String, variant: Int): String?
}

/**
 * Generate a sentence, judge it, and hand it over anyway (#186).
 *
 * **The validator no longer decides.** #45 built it as a gate: a candidate that
 * added a content word was discarded whole, and the user's own words stood. That
 * refused a great deal somebody would have wanted — tapping `agua` alone can
 * only ever become *"Quiero agua."* by adding a verb nobody tapped — and it was
 * built on the assumption that a wrong sentence is unrecoverable.
 *
 * It is not. ✨ becomes ↺, and since #173 that undo survives 🔊, the bell, and
 * everything else that does not change the text. What was missing was never a
 * stricter filter; it was knowing what the sentence *says*, which is why the
 * keyboard now reads it aloud as it arrives.
 *
 * So [validator] still runs and its verdict is still recorded in
 * [lastFindings] — `SentenceService` logs it, and #42 scores exactly that — but
 * the candidate is applied either way. Keeping the judgement whole while
 * removing its veto is what makes this decision reversible.
 *
 * Retries are down to one case: a model that answered with nothing at all.
 */
class Beautifier(private val engine: SentenceEngine, private val validator: SentenceValidator = SentenceValidator()) {

    /**
     * What the last call generated, and what the validator made of it.
     *
     * Kept even though nothing acts on it (#186). It is the only record of what
     * the model added to somebody's words, `SentenceService` logs it in a debug
     * build (#167), and #42's eval set scores precisely this. A judgement nobody
     * enforces is still a judgement worth having written down.
     */
    var lastFindings: List<Discarded> = emptyList()
        private set

    /**
     * One sentence, judged but not gated.
     *
     * [attempts] is spent only on a model that answered with nothing — there is
     * no other way to fail now, and asking again for a sentence that was merely
     * *judged* would be asking again for a sentence we are about to use.
     */
    suspend fun beautify(
        typed: List<TypedWord>,
        language: String,
        variant: Int = 0,
        attempts: Int = ModelSpec.MAX_ATTEMPTS,
    ): Beautified {
        val findings = mutableListOf<Discarded>()
        var outcome: Beautified = Beautified.NothingPassed
        var attempt = 0

        while (typed.isNotEmpty() && attempt < attempts && outcome == Beautified.NothingPassed) {
            outcome = attemptOnce(typed, language, variant + attempt, findings)
            attempt++
        }

        lastFindings = findings
        return outcome
    }

    /** One generation, judged for the record. [findings] collects what it made of it. */
    private suspend fun attemptOnce(
        typed: List<TypedWord>,
        language: String,
        variant: Int,
        findings: MutableList<Discarded>,
    ): Beautified {
        val raw = engine.generate(typed, language, variant) ?: return Beautified.Unavailable
        val candidate = Prompts.firstCandidate(raw)
        // Empty is the one answer that cannot be used: there is nothing to put
        // in the field. Everything else goes in, whatever the validator thinks.
        if (candidate.isBlank()) {
            findings += Discarded(candidate, Rejection.EMPTY, emptyList())
            return Beautified.NothingPassed
        }
        (validator.check(typed, candidate, language) as? Verdict.Rejected)?.let {
            findings += Discarded(candidate, it.reason, it.words)
        }
        return Beautified.Sentence(candidate)
    }
}

/**
 * A candidate the model produced, and what the validator made of it (#186).
 *
 * Named for what it used to mean. Nothing is discarded now except an empty
 * answer — this is the record of what the model added to somebody's words, not
 * a list of things thrown away.
 */
data class Discarded(val candidate: String, val reason: Rejection, val words: List<String>)
