package org.pictokeyboard.sentence.llm

import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.pictokeyboard.sentence.ModelSpec
import org.pictokeyboard.sentence.ModelStore
import org.pictokeyboard.sentence.Prompts
import org.pictokeyboard.sentence.SentenceEngine
import org.pictokeyboard.sentence.TypedWord

/**
 * The model itself, on LiteRT-LM.
 *
 * **This class only ever exists in the `:llm` process.** Everything it holds —
 * the engine handle, the weights behind it, the arenas the runtime allocates —
 * is the several hundred megabytes that #44 exists to keep out of the keyboard.
 *
 * A fresh conversation per request, deliberately. The turns are independent: a
 * caregiver rephrasing a sentence in Messages has nothing to say to a rephrase
 * they did an hour ago in WhatsApp, and a conversation that accumulated them
 * would both waste context and let one app's words influence another's.
 */
class LiteRtEngine(private val store: ModelStore) : SentenceEngine {

    private var engine: Engine? = null

    /** True once weights are loaded and a request would be served rather than dropped. */
    val isReady: Boolean get() = engine != null

    /**
     * Loads the weights. Slow — seconds — so it never runs on a caller's thread.
     *
     * Returns false rather than throwing on every failure path. A model that
     * will not load is a feature that is off, and the keyboard's contract is
     * that it behaves exactly as it does without this. Nothing here is allowed
     * to become an exception that reaches the IME.
     */
    suspend fun load(): Boolean = withContext(Dispatchers.IO) {
        if (engine != null) return@withContext true
        if (!store.isDownloaded()) return@withContext false
        // Before the engine is told where its cache is, because LiteRT will not
        // make the directory and says so only in logcat (#155).
        if (!store.prepareCache()) Log.w(TAG, "No weight cache directory; every load will repack")
        runCatching {
            Engine(
                EngineConfig(
                    modelPath = store.file.absolutePath,
                    // CPU rather than GPU. The GPU path needs delegate support
                    // this model and this range of phones cannot be assumed to
                    // have, and falling back after a failed init costs the user
                    // the wait twice. Sentence help is a handful of tokens on
                    // demand, not a streaming chat.
                    backend = Backend.CPU(),
                    maxNumTokens = MAX_TOKENS,
                    cacheDir = store.cacheDirectory.absolutePath,
                ),
            ).also {
                it.initialize()
                engine = it
            }
            true
        }.getOrElse { error ->
            Log.w(TAG, "Sentence model failed to load; the feature stays off", error)
            close()
            false
        }
    }

    override suspend fun generate(typed: List<TypedWord>, language: String, variant: Int): String? =
        withContext(Dispatchers.Default) {
            val active = engine ?: return@withContext null
            runCatching {
                active.createConversation(
                    ConversationConfig(
                        systemInstruction = Contents.of(Prompts.systemPrompt(language)),
                        samplerConfig = samplerFor(variant),
                        // The `nothink` build should not emit a reasoning trace
                        // anyway; saying so explicitly means a swapped-in model
                        // that would cannot silently spend the latency budget on
                        // thinking nobody reads.
                        thinkingConfig = ThinkingConfig(enableThinking = false),
                        maxOutputToken = ModelSpec.MAX_OUTPUT_TOKENS,
                    ),
                ).use { conversation ->
                    conversation.sendMessage(Prompts.userTurn(typed)).contents.toString()
                }
            }.getOrElse { error ->
                Log.w(TAG, "Generation failed", error)
                null
            }
        }

    /**
     * Temperature climbs with the variant.
     *
     * The first attempt is nearly greedy, because the best answer to "say these
     * eight words properly" is usually the obvious one. Later attempts — a
     * second press of Beautify, or a retry after the validator rejected
     * something — have to land somewhere else to be worth making, and at a fixed
     * low temperature they would return the same sentence and the same rejection.
     */
    private fun samplerFor(variant: Int): SamplerConfig {
        val temperature = (BASE_TEMPERATURE + variant * TEMPERATURE_STEP).coerceAtMost(MAX_TEMPERATURE)
        return SamplerConfig(topK = TOP_K, topP = TOP_P, temperature = temperature, seed = variant)
    }

    fun close() {
        runCatching { engine?.close() }
        engine = null
    }

    private companion object {
        const val TAG = "SentenceEngine"
        const val MAX_TOKENS = 512
        const val TOP_K = 40
        const val TOP_P = 0.95
        const val BASE_TEMPERATURE = 0.2
        const val TEMPERATURE_STEP = 0.25
        const val MAX_TEMPERATURE = 1.0
    }
}
