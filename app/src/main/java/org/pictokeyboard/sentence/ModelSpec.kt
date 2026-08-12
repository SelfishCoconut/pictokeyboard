package org.pictokeyboard.sentence

/**
 * Which model sentence help runs, and everything needed to fetch and verify it.
 *
 * The decision itself, and the reasoning behind it, is in
 * `docs/sentence-help-model.md` (#43). What matters here:
 *
 * **Qwen3 0.6B, int4, the `nothink` build.** Apache-2.0, and — decisively —
 * **not gated**. The `litert-community` copies of every Gemma are gated on
 * Hugging Face, which means downloading one needs an access token, which means
 * shipping a credential inside a public APK. That is not a licensing preference,
 * it is the difference between a download that works for a stranger and one that
 * does not. The `nothink` variant matters too: a reasoning trace is wasted
 * latency when the whole job is to re-say eight words someone already chose.
 *
 * **The weights never ship in the APK** (#44). 347 MB against a 29 MB app, for a
 * feature that is off by default and that most people will never turn on.
 */
object ModelSpec {

    /** Bumped when the file changes, so an old download is replaced rather than trusted. */
    const val VERSION = 1

    const val FILE_NAME = "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm"

    const val URL =
        "https://huggingface.co/litert-community/Qwen3-0.6B-int4/resolve/main/$FILE_NAME"

    /** Exactly, so a truncated download is a failure rather than a corrupt model. */
    const val SIZE_BYTES = 347_251_840L

    /**
     * Hugging Face serves this as `x-linked-etag` on the file. Checked after the
     * last byte arrives: a resumed download that silently picked up a *different*
     * revision matches on length and not on content, and a model half from one
     * revision and half from another fails in ways no validator would catch.
     */
    const val SHA256 = "2df6821ec12702dafd33915e7a1a1adc7c4b053f3672fd9555dfaf3a114c4139"

    const val LICENCE = "Apache-2.0"
    const val LICENCE_URL = "https://www.apache.org/licenses/LICENSE-2.0"
    const val SOURCE_URL = "https://huggingface.co/litert-community/Qwen3-0.6B-int4"
    const val DISPLAY_NAME = "Qwen3 0.6B (int4)"

    /**
     * Total device RAM below which the feature is offered with a warning.
     *
     * The weights are 347 MB, and the runtime needs room for those plus a KV
     * cache and its own arenas — call it a gigabyte resident in the `:llm`
     * process. On a 2 GB phone that is most of what the system has left after
     * the foreground app, and the thing Android would kill to get it back is
     * whichever process is cheapest to lose. Worth saying before somebody spends
     * 347 MB of their data allowance finding out.
     *
     * **It was a floor, and the floor was wrong** (#171). Below this number the
     * feature used to be refused outright — and then a 2.4 GB emulator ran it,
     * repeatedly, at the same moment Settings was telling its owner the phone
     * did not have enough memory. One data point does not prove a phone this
     * size is comfortable; it does prove "cannot" was the wrong word, and every
     * phone excluded by a number nobody measured is a person who did not get the
     * feature. So this warns now, and #145's benchmark is the honest test:
     * measured on the phone in the caregiver's hand rather than reasoned about
     * here.
     */
    const val TIGHT_TOTAL_RAM_BYTES = 3L * 1024 * 1024 * 1024

    /**
     * LiteRT-LM 0.16.0 ships `arm64-v8a` and `x86_64` only — there is no 32-bit
     * ARM build. On a `armeabi-v7a` device the native library is simply absent
     * and loading it throws, so the capability check has to know that before
     * anything offers a download.
     */
    val SUPPORTED_ABIS = setOf("arm64-v8a", "x86_64")

    /**
     * Generous, and deliberately so. It bounds a runaway generation rather than
     * shaping the answer: an expansion of a picto phrase that runs past this has
     * gone wrong, and #46's latency budget will have given up long before.
     */
    const val MAX_OUTPUT_TOKENS = 64

    /**
     * How many times a candidate may be rejected by [SentenceValidator] before
     * the attempt is abandoned and the user's own words are left alone (#45).
     *
     * Bounded because the failure mode is a model that cannot stop adding a word
     * — retrying that forever would burn battery to produce nothing, while
     * somebody waits mid-conversation.
     */
    const val MAX_ATTEMPTS = 3

    /**
     * #44's budget for a full sentence, and the line [SentenceBenchmark] reports
     * against. Not enforced anywhere: a phone over it is told, not refused.
     */
    const val BUDGET_MILLIS = 2_000
}
