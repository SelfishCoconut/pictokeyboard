package org.pictokeyboard.sentence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What comes back from a model, cleaned up enough to be judged.
 *
 * The prompt text itself is prose that #42's eval set scores rather than
 * something assertions can check. What is testable is the part with a rule:
 * which of the model's several lines is the candidate, and what gets stripped
 * off it before anybody looks.
 *
 * The retry clause that used to be tested here went with #186 — with the
 * validator no longer refusing anything, there are no rejections to feed back
 * into a retry that no longer happens.
 */
class PromptsTest {

    // --- #182: a reasoning block is not a candidate --------------------------

    /**
     * The exact shape Qwen3.5 produces, captured off the device.
     *
     * Its non-thinking mode opens every assistant turn with an *empty* think
     * block, so "the first non-empty line" was `<think>` and the validator was
     * handed a tag to judge. Since #186 nothing would refuse it, so the tag
     * would go straight into somebody's message — which makes this matter more
     * than it did when it was found, not less.
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
        assertEquals(
            "Quiero agua.",
            Prompts.firstCandidate("  <think></think>\n\n  \"Quiero agua.\"\nI rewrote it."),
        )
    }

    /**
     * The model spent its whole budget thinking and never wrote a sentence.
     * Nothing is the honest answer, and nothing is what the keyboard shows.
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

    // --- The wrappers small models put round an answer -----------------------

    @Test
    fun `a second line is an explanation, not a candidate`() {
        assertEquals("Quiero agua.", Prompts.firstCandidate("Quiero agua.\nI rewrote your sentence."))
    }

    @Test
    fun `surrounding quotes come off, of either kind`() {
        assertEquals("Quiero agua.", Prompts.firstCandidate("\"Quiero agua.\""))
        assertEquals("Quiero agua.", Prompts.firstCandidate("'Quiero agua.'"))
    }

    @Test
    fun `nothing at all is nothing`() {
        assertEquals("", Prompts.firstCandidate(""))
        assertEquals("", Prompts.firstCandidate("   \n\n  "))
    }

    // --- The instruction -----------------------------------------------------

    @Test
    fun `each language is instructed in its own language`() {
        assertTrue(Prompts.systemPrompt("es").contains("teclado de pictogramas"))
        assertTrue(Prompts.systemPrompt("en").contains("pictogram keyboard"))
    }

    /** An unknown language falls back to English rather than to nothing. */
    @Test
    fun `an unknown language gets the English instruction`() {
        assertEquals(Prompts.systemPrompt("en"), Prompts.systemPrompt("fr"))
    }
}
