package org.pictokeyboard.sentence.llm

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.pictokeyboard.BuildConfig
import org.pictokeyboard.sentence.Beautified
import org.pictokeyboard.sentence.Beautifier
import org.pictokeyboard.sentence.ModelStore
import org.pictokeyboard.sentence.TypedWord
import java.util.concurrent.ConcurrentHashMap

/** Why no sentence came back, as it crosses the binder. */
enum class SentenceResult {
    /** Candidates were generated and every one of them said something the user did not. */
    NOTHING_PASSED,

    /** No model: not downloaded, not loaded, or this process is on its way out. */
    UNAVAILABLE,
}

/**
 * Holds the model, in its own process, and nothing else.
 *
 * **The point of this class is where it runs, not what it does.** `:llm` in the
 * manifest is the whole safety argument of #44: an `InputMethodService` is
 * lightweight and Android kills it readily under memory pressure, and several
 * hundred megabytes of weights inside that process makes it the obvious thing to
 * reclaim. A keyboard dying mid-conversation is not a glitch for an AAC user —
 * it is losing the ability to speak, in the middle of speaking. So the weights
 * live over here, and when the system kills *this* process the keyboard does not
 * notice beyond a button that stops working.
 *
 * Everything it exposes is failure-tolerant for the same reason. There is no
 * path from here that throws into the IME.
 */
class SentenceService : Service() {

    private val scope = CoroutineScope(SupervisorJob())
    private lateinit var store: ModelStore
    private lateinit var engine: LiteRtEngine
    private lateinit var beautifier: Beautifier

    /**
     * In-flight work by request id, so [ISentenceService.cancel] can reach it.
     *
     * Concurrent because binder calls arrive on a pool thread while the
     * coroutines that clear entries finish on another.
     */
    private val inFlight = ConcurrentHashMap<Int, Job>()

    override fun onCreate() {
        super.onCreate()
        store = ModelStore(this)
        engine = LiteRtEngine(store)
        beautifier = Beautifier(engine)
        // Loading starts as soon as something binds rather than on the first
        // request, so the first press of Beautify is not the one that waits
        // several seconds for weights to page in.
        scope.launch { engine.load() }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        scope.cancel()
        engine.close()
        super.onDestroy()
    }

    private val binder = object : ISentenceService.Stub() {

        override fun isReady(): Boolean = engine.isReady

        override fun beautify(
            requestId: Int,
            words: Array<out String>?,
            wordLanguages: Array<out String>?,
            language: String?,
            variant: Int,
            callback: ISentenceCallback?,
        ) {
            val answer = callback ?: return
            val typed = typedWords(words, wordLanguages)
            if (typed.isEmpty()) {
                answer.reportNothing(requestId, SentenceResult.NOTHING_PASSED)
                return
            }

            val job = scope.launch {
                if (!engine.load()) {
                    answer.reportNothing(requestId, SentenceResult.UNAVAILABLE)
                    return@launch
                }
                val result = beautifier.beautify(typed, language.orEmpty(), variant)
                logAttempt(typed, language.orEmpty(), variant, result)
                when (result) {
                    is Beautified.Sentence -> runCatching { answer.onSentence(requestId, result.text) }
                    Beautified.NothingPassed -> answer.reportNothing(requestId, SentenceResult.NOTHING_PASSED)
                    Beautified.Unavailable -> answer.reportNothing(requestId, SentenceResult.UNAVAILABLE)
                }
            }
            inFlight[requestId] = job
            job.invokeOnCompletion { inFlight.remove(requestId) }
        }

        override fun cancel(requestId: Int) {
            inFlight.remove(requestId)?.cancel()
        }
    }

    /**
     * What the model said, and what the validator made of it — debug builds only
     * (#167).
     *
     * `Beautifier` has always collected every discarded candidate and nothing
     * has ever read it, so *"Left as you wrote it"* was the same message whether
     * the harness threw away a good sentence or the model produced nonsense.
     * #165 was the first of those and was found by hand-tracing the lexicon; one
     * press with this on would have shown it.
     *
     * **Guarded because it prints what somebody said.** This is an AAC keyboard:
     * the words going through here are a person's half of a conversation, and
     * logcat is readable by anyone with the phone plugged in. `BuildConfig.DEBUG`
     * is not a formality on this one — R8 removes the whole branch from a release
     * build, and `docs/play-data-safety.md`'s claim that typed content reaches
     * the host app and nowhere else depends on it staying that way.
     */
    private fun logAttempt(typed: List<TypedWord>, language: String, variant: Int, result: Beautified) {
        if (!BuildConfig.DEBUG) return
        val outcome = when (result) {
            is Beautified.Sentence -> "\"${result.text}\""
            Beautified.NothingPassed -> "nothing passed"
            Beautified.Unavailable -> "unavailable"
        }
        Log.d(TAG, "[$language v$variant] ${typed.joinToString(" ") { it.text }} -> $outcome")
        // What the validator made of it, for the record. Nothing acted on this
        // even before #186 removed its veto -- it is here so that "the model
        // added a word" is visible to whoever is judging the model (#42).
        beautifier.lastFindings.forEach {
            Log.d(TAG, "  judged \"${it.candidate}\" ${it.reason} ${it.words}")
        }
    }

    /**
     * Pairs the two parallel arrays back up, tolerating a short or absent
     * language array — a binder argument is whatever the other side sent, and
     * this process must not die because that was malformed.
     */
    private fun typedWords(words: Array<out String>?, languages: Array<out String>?): List<TypedWord> {
        val text = words ?: return emptyList()
        return text.mapIndexed { index, word ->
            TypedWord(word, languages?.getOrNull(index).orEmpty())
        }
    }

    /**
     * The callback is a binder into another process that may already be gone —
     * the keyboard is torn down whenever the user switches away — so every
     * delivery is allowed to fail silently. There is nobody left to tell.
     */
    private fun ISentenceCallback.reportNothing(requestId: Int, result: SentenceResult) {
        runCatching { onNothing(requestId, result.ordinal) }
    }

    companion object {
        private const val TAG = "PictoKeyboardLlm"

        /** Shared by the downloader; the service is the only thing that needs one. */
        val httpClient: OkHttpClient by lazy { OkHttpClient() }
    }
}
