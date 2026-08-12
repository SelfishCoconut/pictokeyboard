package org.pictokeyboard.ime

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * When the keyboard should forget a phrase, now that no key says so (#159).
 *
 * ✕ used to mean "I have finished with this phrase". It went because after #148
 * took the phrase strip there was nothing on screen to show it had worked, which
 * made it read as broken — but the job it did was real, and it moved into
 * `PhraseController.forgetIfFieldMovedOn`, which asks whether the field still
 * holds the words the record claims.
 *
 * `FieldWords.align` is what answers that. These cases pin the answers the
 * decision depends on; the plumbing that reads the field around it needs a live
 * `InputConnection` and is covered on a device instead.
 */
class PhraseForgettingTest {

    private fun forgets(field: String, phrase: List<String>): Boolean =
        FieldWords.align(field.split(" ").filter { it.isNotEmpty() }, phrase) == null

    @Test
    fun aFieldEmptiedByTheAppThatOwnsIt() {
        // The message was sent and the chat box cleared itself, keeping the same
        // editor -- so nothing tells the keyboard, and without this the next 🔊
        // would read the sent sentence back on top of whatever comes next.
        assert(forgets("", listOf("yo", "querer", "agua")))
    }

    @Test
    fun aFieldThatStillHoldsThePhrase() {
        assert(!forgets("yo querer agua", listOf("yo", "querer", "agua")))
    }

    @Test
    fun aFieldHoldingWordsThisKeyboardNeverWrote() {
        // Someone typed with another keyboard first, or the field was
        // pre-filled. The phrase is still there, at the end, and still ours.
        assert(!forgets("hola yo querer agua", listOf("yo", "querer", "agua")))
    }

    @Test
    fun aPictoLabelledWithTwoWords() {
        // `me gusta` is one recorded word and two field words. It must still
        // count as held, or every phrase containing one would be forgotten the
        // moment it was spoken.
        assert(!forgets("me gusta pan", listOf("me gusta", "pan")))
    }

    @Test
    fun aRephrasedFieldDisagreesOnPurpose() {
        // After Beautify the field holds the model's sentence and the record
        // holds the typed words. Alignment fails -- correctly -- which is why
        // the service refuses to run this check while a rephrase is applied:
        // undo needs the record to survive.
        assertNull(FieldWords.align(listOf("Quiero", "agua."), listOf("yo", "querer", "agua")))
        assertNotNull(FieldWords.align(listOf("yo", "querer", "agua"), listOf("yo", "querer", "agua")))
    }

    @Test
    fun aPhraseThatRepeatsAWord() {
        // `yo` twice, and the run that matters is the last one -- where the user
        // is working.
        assert(!forgets("yo querer yo", listOf("yo", "querer", "yo")))
    }
}
