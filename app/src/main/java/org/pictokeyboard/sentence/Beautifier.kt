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
     * @param variant which attempt this is. It moves the sampler — though on
     *   the shipped model that turns out to change very little, which is why
     *   [avoid] exists (#177).
     * @param avoid content words an earlier attempt was rejected for inventing.
     *   This, not [variant], is what actually makes a retry different on the
     *   shipped model (#177).
     * @return the raw text, or null if the model could not be reached.
     */
    suspend fun generate(
        typed: List<TypedWord>,
        language: String,
        variant: Int,
        avoid: List<String>,
    ): String?
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
            // Every word a previous attempt was rejected for goes into the next
            // attempt's prompt (#177). The variant still advances, but it is no
            // longer what this loop depends on: measured on a device, this model
            // returns the identical candidate at every temperature the feature
            // can use, so a retry that only moved the sampler bought nothing but
            // the wait. Naming the word moves it.
            outcome = attemptOnce(
                typed = typed,
                language = language,
                ask = Ask(
                    variant = variant + attempt,
                    validate = validate,
                    // Only the invented words are worth naming: a candidate
                    // rejected over a negation carries no word list, and an
                    // empty one has nothing to name.
                    avoid = discarded
                        .filter { it.reason == Rejection.ADDED_CONTENT_WORD }
                        .flatMap { it.words },
                ),
                discarded = discarded,
            )
            attempt++
        }

        lastRejections = discarded
        return outcome
    }

    /** One generation, judged. [discarded] collects what was thrown away and why. */
    private suspend fun attemptOnce(
        typed: List<TypedWord>,
        language: String,
        ask: Ask,
        discarded: MutableList<Discarded>,
    ): Beautified {
        val (variant, validate, avoid) = ask
        val raw = engine.generate(typed, language, variant, avoid) ?: return Beautified.Unavailable
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

/**
 * One question for the model: which sampler position, whether the answer will be
 * checked, and what it must not say again.
 *
 * A holder rather than four more parameters. `attemptOnce` was at detekt's limit
 * and the three belong together anyway — they are what distinguishes this
 * attempt from the last one.
 */
private data class Ask(val variant: Int, val validate: Boolean, val avoid: List<String>)
