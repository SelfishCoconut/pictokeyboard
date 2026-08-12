package org.pictokeyboard.sentence

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the rephrase loop does now that the validator does not decide (#186).
 *
 * The engine is scripted, so these run with no weights, no `:llm` process and no
 * device — which is the point: what the keyboard will put in somebody's field
 * must not be a question that needs 347 MB and an emulator to answer.
 *
 * `SentenceValidatorTest` still covers the judgement itself. It is still made,
 * still recorded, and still the thing #42 scores; it simply no longer refuses.
 */
class BeautifierTest {

    private fun words(vararg pairs: Pair<String, String>) =
        pairs.map { TypedWord(it.first, it.second) }

    /** Hands back the scripted answers in order, then repeats the last one. */
    private fun engineReturning(vararg answers: String?) = SentenceEngine { _, _, variant ->
        answers.getOrNull(variant) ?: answers.lastOrNull()
    }

    private val aguaPhrase get() = words("yo" to "es", "querer" to "es", "agua" to "es")

    @Test
    fun `a clean expansion is used`() = runTest {
        val result = Beautifier(engineReturning("Quiero agua.")).beautify(aguaPhrase, "es")
        assertEquals(Beautified.Sentence("Quiero agua."), result)
    }

    /**
     * The decision in #186, as a behaviour. This was the case that used to cost
     * three generations and end in "left as you wrote it".
     */
    @Test
    fun `a sentence that adds a word is used anyway`() = runTest {
        val result = Beautifier(engineReturning("Quiero agua fría.")).beautify(aguaPhrase, "es")
        assertEquals(Beautified.Sentence("Quiero agua fría."), result)
    }

    /** Including a negation the user never tapped — asked directly, and decided. */
    @Test
    fun `a sentence that adds a negation is used anyway`() = runTest {
        val result = Beautifier(engineReturning("No quiero agua.")).beautify(aguaPhrase, "es")
        assertEquals(Beautified.Sentence("No quiero agua."), result)
    }

    /**
     * Not enforced, but not forgotten either. This is what `SentenceService`
     * logs and what #42 scores, and it is the only record that a word was added
     * to somebody's sentence.
     */
    @Test
    fun `what the validator made of it is still recorded`() = runTest {
        val beautifier = Beautifier(engineReturning("Quiero agua fría."))
        beautifier.beautify(aguaPhrase, "es")
        assertEquals(
            listOf(Discarded("Quiero agua fría.", Rejection.ADDED_CONTENT_WORD, listOf("fría."))),
            beautifier.lastFindings,
        )
    }

    @Test
    fun `a sentence the validator is happy with leaves no finding`() = runTest {
        val beautifier = Beautifier(engineReturning("Quiero agua."))
        beautifier.beautify(aguaPhrase, "es")
        assertTrue(beautifier.lastFindings.isEmpty())
    }

    /** One generation, not three. The wait is the thing this buys back. */
    @Test
    fun `a usable answer is asked for exactly once`() = runTest {
        var calls = 0
        val engine = SentenceEngine { _, _, _ ->
            calls++
            "Quiero agua fría."
        }
        Beautifier(engine).beautify(aguaPhrase, "es")
        assertEquals(1, calls)
    }

    /** Empty is the one answer that cannot be used, and the only retry left. */
    @Test
    fun `an empty answer is retried`() = runTest {
        val beautifier = Beautifier(engineReturning("   ", "Quiero agua."))
        assertEquals(Beautified.Sentence("Quiero agua."), beautifier.beautify(aguaPhrase, "es"))
    }

    @Test
    fun `nothing usable at all leaves the user's words alone`() = runTest {
        val result = Beautifier(engineReturning("")).beautify(aguaPhrase, "es")
        assertEquals(Beautified.NothingPassed, result)
    }

    @Test
    fun `a model that cannot be reached is unavailable, not empty`() = runTest {
        val result = Beautifier(engineReturning(null)).beautify(aguaPhrase, "es")
        assertEquals(Beautified.Unavailable, result)
    }

    /** Never call a model to rephrase nothing. */
    @Test
    fun `an empty phrase never reaches the model`() = runTest {
        var called = false
        val engine = SentenceEngine { _, _, _ ->
            called = true
            "anything"
        }
        assertEquals(Beautified.NothingPassed, Beautifier(engine).beautify(emptyList(), "es"))
        assertTrue(!called)
    }

    /** Small models wrap answers in quotes and explain themselves afterwards. */
    @Test
    fun `only the first line is taken, and quotes are stripped`() = runTest {
        val result = Beautifier(engineReturning("\"Quiero agua.\"\nI rewrote your sentence."))
            .beautify(aguaPhrase, "es")
        assertEquals(Beautified.Sentence("Quiero agua."), result)
    }

    /** A second press is a fresh question, and moves the sampler with it. */
    @Test
    fun `the variant is passed through to the model`() = runTest {
        val seen = mutableListOf<Int>()
        val engine = SentenceEngine { _, _, variant ->
            seen += variant
            "Quiero agua."
        }
        Beautifier(engine).beautify(aguaPhrase, "es", variant = 5)
        assertEquals(listOf(5), seen)
    }

    @Test
    fun `English works the same way`() = runTest {
        val result = Beautifier(engineReturning("I want the water."))
            .beautify(words("I" to "en", "want" to "en", "water" to "en"), "en")
        assertEquals(Beautified.Sentence("I want the water."), result)
    }
}
