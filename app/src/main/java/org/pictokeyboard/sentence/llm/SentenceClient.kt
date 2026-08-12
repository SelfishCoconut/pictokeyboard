package org.pictokeyboard.sentence.llm

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import org.pictokeyboard.sentence.TypedWord
import java.util.concurrent.atomic.AtomicInteger

/** What the keyboard got back, once everything that can go wrong has. */
sealed interface BeautifyOutcome {
    data class Sentence(val text: String) : BeautifyOutcome

    /** The model ran and said nothing usable. Worth telling the user. */
    data object NothingPassed : BeautifyOutcome

    /** No model. The keyboard behaves exactly as it does without the feature. */
    data object Unavailable : BeautifyOutcome
}

/**
 * The keyboard's end of the binder, and its insulation from everything on the
 * other side.
 *
 * **Every failure here is [BeautifyOutcome.Unavailable], never an exception.**
 * The `:llm` process can be killed at any moment — that is the *point* of it
 * being a separate process (#44) — so a dead binder is the ordinary case rather
 * than an error. `DeadObjectException`, a service that never binds, a phone with
 * no weights downloaded: all the same answer, and none of them reaches the IME
 * as a throw. A keyboard that crashes because a rephrase failed would have taken
 * away someone's voice to save them a typo.
 *
 * Callbacks are hopped onto the main thread, because binder replies land on a
 * pool thread and everything they touch is a View.
 */
class SentenceClient(private val context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private val nextRequestId = AtomicInteger(1)
    private var service: ISentenceService? = null
    private var bound = false

    /** The one request the keyboard cares about; anything older is stale. */
    private var currentRequestId = 0

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = ISentenceService.Stub.asInterface(binder)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // The model process died. Nothing to recover: the next press binds
            // again, and until then Beautify reports itself unavailable.
            service = null
        }
    }

    /** Binds if it is not already bound. Safe to call repeatedly. */
    fun bind() {
        if (bound) return
        bound = runCatching {
            context.bindService(
                Intent(context, SentenceService::class.java),
                connection,
                Context.BIND_AUTO_CREATE,
            )
        }.getOrDefault(false)
    }

    /**
     * Unbinds, which is what lets the system reclaim the model's process.
     *
     * Called when sentence help is switched off and when the keyboard goes away,
     * so a feature nobody is using is not several hundred megabytes resident.
     */
    fun unbind() {
        if (!bound) return
        runCatching { context.unbindService(connection) }
        bound = false
        service = null
    }

    /** True only if a press right now would actually be served. */
    fun isReady(): Boolean = runCatching { service?.isReady() == true }.getOrDefault(false)

    /**
     * Asks for a rephrase of [typed]; [onResult] runs on the main thread.
     *
     * Any earlier request is cancelled first. Only one rephrase can be wanted at
     * a time — a second press replaces the first — and a reply for a superseded
     * request is dropped rather than applied, which is what stops a slow answer
     * for one field landing in the next one.
     *
     * @param unvalidated asks for the model's own answer with the harness off
     *   (#167). Passed on as a request; `SentenceService` decides, and it says no
     *   in every build that is not a debug one.
     */
    fun beautify(
        typed: List<TypedWord>,
        language: String,
        variant: Int,
        unvalidated: Boolean,
        onResult: (BeautifyOutcome) -> Unit,
    ) {
        val remote = service
        if (remote == null) {
            bind()
            onResult(BeautifyOutcome.Unavailable)
            return
        }

        cancelCurrent()
        val requestId = nextRequestId.incrementAndGet()
        currentRequestId = requestId

        val callback = object : ISentenceCallback.Stub() {
            override fun onSentence(id: Int, text: String?) = deliver(id) {
                text?.takeIf { it.isNotBlank() }
                    ?.let(BeautifyOutcome::Sentence)
                    ?: BeautifyOutcome.NothingPassed
            }

            override fun onNothing(id: Int, reason: Int) = deliver(id) {
                if (reason == SentenceResult.UNAVAILABLE.ordinal) {
                    BeautifyOutcome.Unavailable
                } else {
                    BeautifyOutcome.NothingPassed
                }
            }

            private fun deliver(id: Int, outcome: () -> BeautifyOutcome) {
                val result = outcome()
                main.post { if (id == currentRequestId) onResult(result) }
            }
        }

        runCatching {
            remote.beautify(
                requestId,
                typed.map { it.text }.toTypedArray(),
                typed.map { it.language }.toTypedArray(),
                language,
                variant,
                unvalidated,
                callback,
            )
        }.onFailure {
            // Most likely DeadObjectException: the model process went away
            // between the null check above and this call.
            service = null
            onResult(BeautifyOutcome.Unavailable)
        }
    }

    /**
     * Abandons whatever is in flight.
     *
     * Called when the input target changes, so a sentence generated for one app
     * can never arrive in another (#46).
     */
    fun cancelCurrent() {
        val id = currentRequestId
        currentRequestId = 0
        if (id != 0) runCatching { service?.cancel(id) }
    }
}
