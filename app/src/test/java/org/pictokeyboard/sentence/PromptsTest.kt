package org.pictokeyboard.sentence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The retry clause (#177), and the fact that it is only ever an addition.
 *
 * The rest of `Prompts` is prose judged by #42's eval set rather than by
 * assertions. What is testable here is the part with a rule: a first attempt is
 * asked exactly what it was always asked, a retry is asked the same thing plus
 * a sentence about what not to say, and the sentence is in the right language.
 */
class PromptsTest {

    @Test
    fun `a first attempt is asked exactly what it was always asked`() {
        assertEquals(Prompts.systemPrompt("es"), Prompts.systemPrompt("es", emptyList()))
        assertEquals(Prompts.systemPrompt("en"), Prompts.systemPrompt("en", emptyList()))
    }

    /**
     * Only ever added to. A retry that quietly *replaced* a rule would be this
     * file loosening the instructions at the moment the model has already shown
     * it needs them.
     */
    @Test
    fun `the clause is added to the prompt, never instead of it`() {
        val retried = Prompts.systemPrompt("es", listOf("galleta"))
        assertTrue(retried.startsWith(Prompts.systemPrompt("es")))
    }

    @Test
    fun `a rejected word is named, in the language being written`() {
        assertTrue(Prompts.systemPrompt("es", listOf("galleta")).contains("No uses estas palabras"))
        assertTrue(Prompts.systemPrompt("en", listOf("biscuit")).contains("Do not use these words"))
    }

    /**
     * The validator reports the word as it stood in the candidate — `galleta.`
     * with the full stop — because a log should show what was in the sentence.
     * A prompt telling the model not to say "galleta." is asking about a
     * different string from the one it wrote.
     */
    @Test
    fun `the word is named without the punctuation it was found with`() {
        val clause = clause("es", listOf("galleta.", "¿fría?"))
        assertTrue(clause, clause.contains("galleta, fría."))
        assertFalse("the full stop travelled into the clause", clause.contains("galleta."))
    }

    @Test
    fun `the same word twice is named once`() {
        val clause = clause("es", listOf("galleta.", "galleta"))
        assertEquals(1, clause.split("galleta").size - 1)
    }

    /** Blank is empty. A clause about nothing is noise in a small model's context. */
    @Test
    fun `nothing worth saying adds nothing`() {
        assertEquals(Prompts.systemPrompt("es"), Prompts.systemPrompt("es", listOf("  ", ".")))
    }

    // --- #182: a reasoning block is not a candidate --------------------------

    /**
     * The exact shape Qwen3.5 produces, captured off the device.
     *
     * Its non-thinking mode opens every assistant turn with an *empty* think
     * block, so "the first non-empty line" was `<think>` and the validator was
     * asked to judge a tag as a sentence.
     */
    @Test
    fun `an empty reasoning block is not the sentence`() {
        assertEquals("Quiero agua.", Prompts.firstCandidate("<think>\n\n</think>\nQuiero agua."))
    }

    @Test
    fun `a reasoning block with reasoning in it is dropped whole`() {
        val raw = "<think>\nThe user tapped agua. I should say it naturally.\n</think>\nQuiero agua."
        assertEquals("Quiero agua.", Prompts.firstCandidate(raw))
    }

    /** Whatever surrounds the tags, including the model's usual quoting. */
    @Test
    fun `the quote stripping still applies after the block`() {
        assertEquals("Quiero agua.", Prompts.firstCandidate("  <think></think>\n\n  \"Quiero agua.\"\nI rewrote it."))
    }

    /**
     * The model spent its whole budget thinking and never wrote a sentence.
     * Nothing is the honest answer — the validator records that as EMPTY, which
     * is a different thing from inventing a word.
     */
    @Test
    fun `an unterminated block leaves no candidate`() {
        assertEquals("", Prompts.firstCandidate("<think>\nstill thinking, and out of tokens"))
    }

    /**
     * Only a block the turn *opens* with is a wrapper. One appearing after a
     * sentence is not, and cutting from there would throw away an answer the
     * model had already given.
     */
    @Test
    fun `a block after the answer does not eat the answer`() {
        assertEquals("Quiero agua.", Prompts.firstCandidate("Quiero agua.\n<think>was that right?</think>"))
    }

    /** The model this ships with today emits no block at all. */
    @Test
    fun `output with no block is untouched`() {
        assertEquals("Quiero agua.", Prompts.firstCandidate("Quiero agua.\nI rewrote your sentence."))
    }

    // --- #184: the examples are turns, not text ------------------------------

    /**
     * The regression guard for #184.
     *
     * An example written into the instruction is text a small model continues
     * rather than a pattern it applies: measured on a device, `agua querer ir
     * casa` came back as *"Una galleta."*, lifted whole out of the prompt. They
     * belong in `initialMessages`, and this fails if anybody moves them back.
     */
    @Test
    fun `no worked example is written into the instruction`() {
        for (language in listOf("es", "en")) {
            val prompt = Prompts.systemPrompt(language)
            for (example in Prompts.examples(language)) {
                assertFalse("$language names an example's answer", prompt.contains(example.sentence))
                assertFalse("$language names an example's input", prompt.contains(example.tapped))
            }
        }
    }

    @Test
    fun `both languages have examples, written in their own language`() {
        assertTrue(Prompts.examples("es").isNotEmpty())
        assertTrue(Prompts.examples("en").isNotEmpty())
        assertTrue(Prompts.examples("es").any { it.sentence.contains("Quiero") })
        assertTrue(Prompts.examples("en").any { it.sentence.contains("I want") })
    }

    /** An unknown language falls back to English, exactly as the instruction does. */
    @Test
    fun `an unknown language gets the same set as English`() {
        assertEquals(Prompts.examples("en"), Prompts.examples("fr"))
    }

    /**
     * Just the retry clause.
     *
     * Asserted on rather than the whole prompt because a `contains` over the
     * whole thing can be answered by the instruction rather than by the clause —
     * which is how two of these tests passed the wrong thing on their first run,
     * back when the examples still lived in the instruction text.
     */
    private fun clause(language: String, avoid: List<String>): String =
        Prompts.systemPrompt(language, avoid).removePrefix(Prompts.systemPrompt(language))
}
