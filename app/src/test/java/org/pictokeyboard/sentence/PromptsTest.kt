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

    /**
     * Just the retry clause.
     *
     * Asserted on rather than the whole prompt because the Spanish prompt's own
     * examples contain `galleta -> Una galleta.`, so a `contains` over the whole
     * thing is answered by the example rather than by the clause — which is how
     * two of these tests passed the wrong thing on the first run.
     */
    private fun clause(language: String, avoid: List<String>): String =
        Prompts.systemPrompt(language, avoid).removePrefix(Prompts.systemPrompt(language))
}
