package org.pictokeyboard.sentence

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #45's safety property, as a behaviour rather than a rule.
 *
 * The engine is scripted, so these run with no weights, no `:llm` process and no
 * device — which is the point: whether a model may put words in someone's mouth
 * must not be a question that needs 347 MB and an emulator to answer.
 */
class BeautifierTest {

    private fun words(vararg pairs: Pair<String, String>) =
        pairs.map { TypedWord(it.first, it.second) }

    /** Hands back the scripted answers in order, then repeats the last one. */
    private fun engineReturning(vararg answers: String?) = SentenceEngine { _, _, variant ->
        answers.getOrNull(variant) ?: answers.lastOrNull()
    }

    @Test
    fun `a clean expansion is accepted`() = runTest {
        val result = Beautifier(engineReturning("Quiero agua.")).beautify(
            typed = words("yo" to "es", "querer" to "es", "agua" to "es"),
            language = "es",
        )
        assertEquals(Beautified.Sentence("Quiero agua."), result)
    }

    /**
     * The failure this whole milestone exists to prevent: the model inventing a
     * contrast the user never expressed.
     */
    @Test
    fun `an invented content word is rejected, and a clean retry wins`() = runTest {
        val beautifier = Beautifier(
            engineReturning("Estoy bien, pero quiero comida.", "Estoy bien y quiero comida."),
        )
        val result = beautifier.beautify(
            typed = words("yo" to "es", "bien" to "es", "querer" to "es", "comida" to "es"),
            language = "es",
        )
        assertEquals(Beautified.Sentence("Estoy bien y quiero comida."), result)
        assertEquals(listOf(Rejection.ADDED_CONTENT_WORD), beautifier.lastRejections.map { it.reason })
    }

    @Test
    fun `nothing is shown when every attempt invents something`() = runTest {
        val beautifier = Beautifier(engineReturning("Quiero agua fría."))
        val result = beautifier.beautify(
            typed = words("yo" to "es", "querer" to "es", "agua" to "es"),
            language = "es",
        )
        assertEquals(Beautified.NothingPassed, result)
        assertEquals(ModelSpec.MAX_ATTEMPTS, beautifier.lastRejections.size)
    }

    /** Adding a negation reverses the meaning, which is worse than clumsy grammar. */
    @Test
    fun `an added negation is rejected`() = runTest {
        val beautifier = Beautifier(engineReturning("No quiero agua."))
        val result = beautifier.beautify(
            typed = words("yo" to "es", "querer" to "es", "agua" to "es"),
            language = "es",
        )
        assertEquals(Beautified.NothingPassed, result)
        assertTrue(beautifier.lastRejections.all { it.reason == Rejection.ADDED_NEGATION })
    }

    /** And dropping one the user tapped reverses it just as completely. */
    @Test
    fun `a dropped negation is rejected`() = runTest {
        val beautifier = Beautifier(engineReturning("Quiero comer."))
        val result = beautifier.beautify(
            typed = words("yo" to "es", "no" to "es", "querer" to "es", "comer" to "es"),
            language = "es",
        )
        assertEquals(Beautified.NothingPassed, result)
        assertTrue(beautifier.lastRejections.all { it.reason == Rejection.DROPPED_NEGATION })
    }

    @Test
    fun `a keeper of the user's negation is accepted`() = runTest {
        val result = Beautifier(engineReturning("No quiero comer.")).beautify(
            typed = words("yo" to "es", "no" to "es", "querer" to "es", "comer" to "es"),
            language = "es",
        )
        assertEquals(Beautified.Sentence("No quiero comer."), result)
    }

    @Test
    fun `an unreachable model is unavailable, not a rejection`() = runTest {
        val result = Beautifier(engineReturning(null)).beautify(
            typed = words("yo" to "es", "querer" to "es"),
            language = "es",
        )
        assertEquals(Beautified.Unavailable, result)
    }

    /**
     * A rejected candidate must not simply be asked for again at the same
     * sampler position, or the retry returns the same sentence and the same
     * rejection, three times, for nothing.
     */
    @Test
    fun `each attempt asks for a different variant`() = runTest {
        val seen = mutableListOf<Int>()
        val engine = SentenceEngine { _, _, variant ->
            seen += variant
            "Quiero agua fría."
        }
        Beautifier(engine).beautify(
            typed = words("yo" to "es", "querer" to "es", "agua" to "es"),
            language = "es",
            variant = 5,
        )
        assertEquals(listOf(5, 6, 7), seen)
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
        val result = Beautifier(engineReturning("\"Quiero agua.\"\nI rewrote your sentence.")).beautify(
            typed = words("yo" to "es", "querer" to "es", "agua" to "es"),
            language = "es",
        )
        assertEquals(Beautified.Sentence("Quiero agua."), result)
    }

    @Test
    fun `English works the same way`() = runTest {
        val beautifier = Beautifier(engineReturning("I want some cold water.", "I want the water."))
        val result = beautifier.beautify(
            typed = words("I" to "en", "want" to "en", "water" to "en"),
            language = "en",
        )
        assertEquals(Beautified.Sentence("I want the water."), result)
        assertEquals(listOf(Rejection.ADDED_CONTENT_WORD), beautifier.lastRejections.map { it.reason })
    }

    // --- #167: what was thrown away, and what happens without the harness ----

    /**
     * The candidate text, not only the reason it went.
     *
     * *"Left as you wrote it"* reads the same whether the harness threw away a
     * good sentence or the model produced nonsense, and those want opposite
     * fixes. #165 was the first kind and was found by hand-tracing the lexicon,
     * because nothing recorded what the model had actually said.
     */
    @Test
    fun `a discarded candidate keeps its text alongside the verdict`() = runTest {
        val beautifier = Beautifier(engineReturning("Quiero agua fría.", "Quiero agua."))
        beautifier.beautify(
            typed = words("yo" to "es", "querer" to "es", "agua" to "es"),
            language = "es",
        )
        // The offending word arrives as it appeared, punctuation included: this
        // is what gets printed, and a log that says `fría.` is showing what was
        // in the sentence rather than a tidied version of it.
        assertEquals(
            listOf(Discarded("Quiero agua fría.", Rejection.ADDED_CONTENT_WORD, listOf("fría."))),
            beautifier.lastRejections,
        )
    }

    /**
     * With the harness off, the model's first answer stands — invented word and
     * all. That is the whole diagnostic value of it, and the whole reason
     * `ValidatorBypass` will not let a shipped build ask for it.
     */
    @Test
    fun `without the validator, a candidate that adds a word is applied anyway`() = runTest {
        val beautifier = Beautifier(engineReturning("Quiero agua fría ahora mismo."))
        val result = beautifier.beautify(
            typed = words("yo" to "es", "querer" to "es", "agua" to "es"),
            language = "es",
            validate = false,
        )
        assertEquals(Beautified.Sentence("Quiero agua fría ahora mismo."), result)
        assertTrue("nothing was discarded", beautifier.lastRejections.isEmpty())
    }

    /** Empty is still empty: there would be nothing to put in the field. */
    @Test
    fun `without the validator, an empty answer is still nothing`() = runTest {
        val result = Beautifier(engineReturning("   ")).beautify(
            typed = words("yo" to "es", "querer" to "es", "agua" to "es"),
            language = "es",
            validate = false,
        )
        assertEquals(Beautified.NothingPassed, result)
    }

    /** The harness is on unless somebody says otherwise, at every call site. */
    @Test
    fun `validation is the default`() = runTest {
        val result = Beautifier(engineReturning("Quiero agua fría.")).beautify(
            typed = words("yo" to "es", "querer" to "es", "agua" to "es"),
            language = "es",
        )
        assertEquals(Beautified.NothingPassed, result)
    }
}
