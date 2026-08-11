package org.pictokeyboard.sentence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The safety property of the sentence-help milestone (#45).
 *
 * Everything here is about one question: can the model say something the user
 * did not? The cases are grouped by the three ways it could — inventing a
 * content word, adding a negation, and dropping one — plus the expansions that
 * must keep working, because a validator that rejects everything is safe and
 * useless.
 */
class SentenceValidatorTest {

    private val validator = SentenceValidator()

    private fun es(vararg words: String) = words.map { TypedWord(it, "es") }
    private fun en(vararg words: String) = words.map { TypedWord(it, "en") }

    private fun check(typed: List<TypedWord>, candidate: String, language: String = "es") =
        validator.check(typed, candidate, language)

    private fun assertAccepted(typed: List<TypedWord>, candidate: String, language: String = "es") =
        assertEquals(
            "should have accepted \"$candidate\"",
            Verdict.Accepted,
            check(typed, candidate, language),
        )

    private fun assertRejected(
        typed: List<TypedWord>,
        candidate: String,
        reason: Rejection,
        language: String = "es",
    ) {
        val verdict = check(typed, candidate, language)
        assertTrue(
            "should have rejected \"$candidate\", got $verdict",
            verdict is Verdict.Rejected && verdict.reason == reason,
        )
    }

    // --- the expansions that have to keep working ----------------------------

    /** The worked example from §12.1 of the proposal. */
    @Test
    fun theExampleFromTheSpecSurvives() {
        assertAccepted(es("yo", "bien", "querer", "comida"), "estoy bien y quiero comida")
    }

    /**
     * The commonest shape in the language and the one that would break first:
     * *querer* is a stem-changing verb, so *quiero* has to be recognised as the
     * same word the user tapped.
     */
    @Test
    fun aStemChangingVerbMatchesItsOwnConjugation() {
        assertAccepted(es("yo", "querer", "agua"), "quiero agua")
        assertAccepted(es("yo", "poder", "ir"), "yo puedo ir")
        assertAccepted(es("yo", "dormir"), "estoy durmiendo")
    }

    /** Copulas and auxiliaries are exactly what a telegraphic phrase is missing. */
    @Test
    fun addingSerAndEstarAndHaberIsAllowed() {
        assertAccepted(es("yo", "contento"), "estoy contento")
        assertAccepted(es("mama", "profesora"), "mi mama es profesora")
        assertAccepted(es("yo", "comer"), "he comido")
    }

    @Test
    fun articlesPrepositionsAndConjunctionsAreFree() {
        assertAccepted(es("ir", "casa", "abuela"), "voy a la casa de la abuela")
    }

    @Test
    fun anIrregularVerbMatchesItsLemma() {
        assertAccepted(es("yo", "tener", "hambre"), "tengo hambre")
        assertAccepted(es("yo", "ir", "colegio"), "voy al colegio")
        assertAccepted(es("yo", "ver", "pelicula"), "vi una pelicula")
    }

    @Test
    fun englishExpandsToo() {
        assertAccepted(en("i", "want", "water"), "I want some water", language = "en")
        assertAccepted(en("i", "go", "school"), "I went to school", language = "en")
        assertAccepted(en("i", "eat", "apple"), "I am eating an apple", language = "en")
    }

    /** Accents are the expansion's job to add, and must not make a word foreign. */
    @Test
    fun anAddedAccentIsStillTheSameWord() {
        assertAccepted(es("yo", "comer", "manana"), "comere manana")
    }

    /** Punctuation an expansion adds is not a new word. */
    @Test
    fun trailingPunctuationDoesNotCount() {
        assertAccepted(es("yo", "cansado"), "estoy cansado.")
        assertAccepted(es("agua"), "¿agua?")
    }

    // --- inventing a content word --------------------------------------------

    @Test
    fun aContentWordTheUserNeverTappedIsRejected() {
        assertRejected(
            es("yo", "querer", "agua"),
            "quiero agua fria",
            Rejection.ADDED_CONTENT_WORD,
        )
    }

    /**
     * The politeness case, which is the one a caregiver would never notice and
     * the user might not mean. Adding *por favor* is putting manners into
     * somebody's mouth.
     */
    @Test
    fun invitedPolitenessIsStillInvention() {
        assertRejected(
            es("yo", "querer", "galleta"),
            "quiero una galleta, gracias",
            Rejection.ADDED_CONTENT_WORD,
        )
    }

    @Test
    fun theRejectionNamesTheWordThatCausedIt() {
        val verdict = check(es("yo", "querer", "agua"), "quiero agua caliente")
        assertEquals(
            Verdict.Rejected(Rejection.ADDED_CONTENT_WORD, listOf("caliente")),
            verdict,
        )
    }

    /** Dropping a word the user did tap is allowed; only additions are meaning. */
    @Test
    fun leavingOneOfTheUsersOwnWordsOutIsNotRejectedHere() {
        assertAccepted(es("yo", "querer", "agua"), "quiero")
    }

    @Test
    fun nothingUsableIsRejected() {
        assertRejected(es("yo", "querer"), "   ", Rejection.EMPTY)
    }

    // --- negation, which reverses rather than embroiders ----------------------

    @Test
    fun addingANegationTheUserDidNotTapIsRejected() {
        assertRejected(es("yo", "querer", "comer"), "no quiero comer", Rejection.ADDED_NEGATION)
    }

    @Test
    fun droppingTheUsersNegationIsRejected() {
        assertRejected(es("yo", "no", "querer", "comer"), "quiero comer", Rejection.DROPPED_NEGATION)
    }

    /** Spanish piles negators up for one negation; that is concord, not meaning. */
    @Test
    fun spanishNegativeConcordIsNotAnAddedNegation() {
        assertAccepted(es("yo", "no", "querer", "nada"), "no quiero nada")
    }

    @Test
    fun anEnglishContractionCountsAsANegation() {
        assertRejected(en("i", "want", "water"), "I don't want water", Rejection.ADDED_NEGATION, "en")
        assertRejected(en("i", "want", "water"), "I dont want water", Rejection.ADDED_NEGATION, "en")
    }

    @Test
    fun theUsersNegationMaySurfaceAsAContraction() {
        assertAccepted(en("i", "not", "want", "water"), "I don't want water", language = "en")
    }

    /**
     * A word merely ending in the same two letters is not a negation. Without
     * this, every Spanish expansion containing *importante* — and every English
     * one containing *present* — would be thrown away.
     */
    @Test
    fun aWordThatMerelyEndsInNtIsNotANegation() {
        assertAccepted(en("i", "want", "present"), "I want a present", language = "en")
        assertAccepted(es("yo", "querer", "importante"), "quiero lo importante")
    }

    // --- mixed-language boards ------------------------------------------------

    /**
     * A board may mix languages, and *water* is not *agua*. The English word
     * cannot license a Spanish content word it merely translates.
     */
    @Test
    fun aWordInAnotherLanguageDoesNotLicenseItsTranslation() {
        assertRejected(
            listOf(TypedWord("yo", "es"), TypedWord("querer", "es"), TypedWord("water", "en")),
            "quiero agua",
            Rejection.ADDED_CONTENT_WORD,
        )
    }

    @Test
    fun aWordKeptInItsOwnLanguageIsStillLicensed() {
        assertAccepted(
            listOf(TypedWord("yo", "es"), TypedWord("querer", "es"), TypedWord("water", "en")),
            "quiero water",
        )
    }
}
