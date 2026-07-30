package org.pictokeyboard.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.ArrayDeque
import java.util.Locale

/**
 * Wraps Android [TextToSpeech]. Each utterance carries its own language tag, so
 * a Spanish category name followed by an English picto are each spoken in the
 * right voice instead of one accent mangling the other. Multi-part sequences are
 * chained on completion: the engine's language is only switched once the
 * previous part has finished speaking, which is the only reliable way to get
 * different voices within one announcement.
 */
class TtsManager(context: Context) : TextToSpeech.OnInitListener {

    /** A single thing to say in a given [language] ("es"/"en"). */
    data class Part(val text: String, val language: String)

    private var ready = false
    private var rate = 1.0f
    private var pitch = 1.0f
    private val tts = TextToSpeech(context.applicationContext, this)

    private val mainHandler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<Part>()

    /** Bumped on every new request so stale completion callbacks are ignored. */
    private var sequenceId = 0

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            applyParams()
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) = continueSequence(utteranceId)

                @Deprecated("deprecated in API 21")
                override fun onError(utteranceId: String?) = continueSequence(utteranceId)
                override fun onError(utteranceId: String?, errorCode: Int) = continueSequence(utteranceId)
            })
        }
    }

    fun setParams(rate: Float, pitch: Float) {
        this.rate = rate
        this.pitch = pitch
        if (ready) applyParams()
    }

    private fun applyParams() {
        tts.setSpeechRate(rate)
        tts.setPitch(pitch)
    }

    /** Speaks a single [text] in [language], interrupting anything in progress. */
    fun speak(text: String, language: String) {
        speakSequence(listOf(Part(text, language)))
    }

    /** Speaks [parts] back-to-back, each in its own language. */
    fun speakSequence(parts: List<Part>) {
        if (!ready) return
        val pending = parts.filter { it.text.isNotBlank() }
        sequenceId++
        queue.clear()
        queue.addAll(pending)
        speakNext(sequenceId, flush = true)
    }

    private fun continueSequence(utteranceId: String?) {
        val id = utteranceId?.substringAfterLast('-')?.toIntOrNull() ?: return
        mainHandler.post { speakNext(id, flush = false) }
    }

    private fun speakNext(id: Int, flush: Boolean) {
        if (id != sequenceId) return // a newer request superseded this one
        val part = queue.pollFirst() ?: return
        tts.language = localeFor(part.language)
        val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts.speak(part.text, mode, null, "seq-$id")
    }

    fun stop() {
        if (ready) {
            sequenceId++
            queue.clear()
            tts.stop()
        }
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }

    private fun localeFor(language: String): Locale = when (language.lowercase()) {
        "en" -> Locale.ENGLISH
        else -> Locale("es", "ES")
    }
}
