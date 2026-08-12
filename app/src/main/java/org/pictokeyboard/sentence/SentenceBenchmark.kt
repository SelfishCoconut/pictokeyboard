package org.pictokeyboard.sentence

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.pictokeyboard.data.prefs.AppLanguages
import org.pictokeyboard.sentence.llm.BeautifyOutcome
import org.pictokeyboard.sentence.llm.SentenceClient
import kotlin.coroutines.resume

/** How long this phone actually took, or that the attempt did not finish. */
sealed interface BenchmarkResult {

    /**
     * @param loadMillis time from binding the model process to it being ready.
     *   Paid once per keyboard session, by the first sentence only.
     * @param generateMillis time for one sentence with the model already warm.
     *   Paid every time, and the number #44's budget is about.
     */
    data class Measured(val loadMillis: Long, val generateMillis: Long) : BenchmarkResult

    /**
     * Nothing was measured.
     *
     * Deliberately distinct from a slow measurement, and said differently in
     * settings: "this phone is slow" and "the test did not run" ask for
     * different things, and reporting the second as the first would be a
     * verdict on a phone nobody has actually timed.
     */
    data object Failed : BenchmarkResult
}

/**
 * Timing sentence help on the phone it will run on (#145).
 *
 * `DeviceCapability` checks a processor, some memory and some disk before
 * offering the download. All three are necessary and none of them is *speed*: a
 * phone can clear every bar and still take eight seconds to produce a sentence,
 * and eight seconds mid-conversation is a person waiting while somebody else
 * fills the silence.
 *
 * So the model is run once, here, and the number is shown. **The number never
 * disables anything.** It is the caregiver's decision — a phone that takes four
 * seconds may still be worth it to somebody who has no other way to build a
 * sentence — and this reports rather than acts.
 *
 * Runs through the same binder as everything else, so the weights are loaded in
 * `:llm` and never in the app's own process.
 */
class SentenceBenchmark(private val context: Context) {

    suspend fun run(language: String): BenchmarkResult = withContext(Dispatchers.Main) {
        val client = SentenceClient(context)
        client.bind()
        val startedAt = SystemClock.elapsedRealtime()
        // Polled rather than pushed: the service loads on its own coroutine and
        // has no "ready" callback, because nothing else in the app needs one --
        // the keyboard simply asks whether a press would be served.
        val loadMillis = withTimeoutOrNull(LOAD_TIMEOUT_MS) {
            while (!client.isReady()) delay(POLL_MS)
            SystemClock.elapsedRealtime() - startedAt
        }
        val result = if (loadMillis == null) BenchmarkResult.Failed else measure(client, language, loadMillis)
        client.unbind()
        result
    }

    /**
     * One sentence, timed.
     *
     * What is being measured is how long the phone takes, not whether the
     * sentence was any good — quality is #42's question and this is not it.
     */
    private suspend fun measure(client: SentenceClient, language: String, loadMillis: Long): BenchmarkResult {
        val startedAt = SystemClock.elapsedRealtime()
        val outcome = withTimeoutOrNull(GENERATE_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                client.beautify(phraseFor(language), language, variant = 0) { continuation.resume(it) }
            }
        }
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        return if (outcome == null || outcome == BeautifyOutcome.Unavailable) {
            BenchmarkResult.Failed
        } else {
            BenchmarkResult.Measured(loadMillis, elapsed)
        }
    }

    /**
     * A short, ordinary phrase — the shape of the thing this feature is for.
     *
     * In the caregiver's own language rather than one fixed language, because
     * the number is meant to predict what *they* will wait for, and the two
     * prompts differ.
     */
    private fun phraseFor(language: String): List<TypedWord> =
        if (language == AppLanguages.ENGLISH) {
            listOf(TypedWord("I", "en"), TypedWord("want", "en"), TypedWord("water", "en"))
        } else {
            listOf(TypedWord("yo", "es"), TypedWord("querer", "es"), TypedWord("agua", "es"))
        }

    private companion object {
        /** Loading 347 MB of weights off a slow phone's flash is not quick. */
        const val LOAD_TIMEOUT_MS = 90_000L
        const val GENERATE_TIMEOUT_MS = 60_000L
        const val POLL_MS = 200L
    }
}
