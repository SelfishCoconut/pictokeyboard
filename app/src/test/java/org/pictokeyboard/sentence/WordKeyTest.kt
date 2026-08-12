package org.pictokeyboard.sentence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The traps in reducing a Spanish word to something its own inflections share.
 *
 * [SentenceValidatorTest] covers what the rule is *for*. This covers the places
 * the reduction itself goes wrong, because each of them was a real bug and each
 * would come back the moment someone reorders the pipeline or tidies the lists.
 */
class WordKeyTest {

    private fun es(word: String) = WordKey.of(word, Lexicon.Spanish)
    private fun en(word: String) = WordKey.of(word, Lexicon.English)

    private fun assertSameWord(a: String, b: String) =
        assertEquals("\"$a\" and \"$b\" should reduce alike", es(a), es(b))

    /**
     * `qu` and `gu` are spelling, not vowels. Undoing a stem change inside them
     * turns *querer* into *qorer* — and *querer* is the commonest verb an AAC
     * board carries, so this rule breaks the one word it was written for.
     */
    @Test
    fun theSilentUAfterQAndGIsNotAStemChange() {
        assertSameWord("querer", "quiero")
        assertSameWord("querer", "quieres")
        assertSameWord("guerra", "guerras")
    }

    /** The real stem changes, which the rule above must not stop working. */
    @Test
    fun aStressedStemVowelIsUndone() {
        assertSameWord("poder", "puedo")
        assertSameWord("contar", "cuento")
        assertSameWord("pensar", "pienso")
    }

    /**
     * The gerund ends `-iendo`, whose `ie` is an ending rather than a stem
     * change. Unstressing before stripping leaves *comendo*, which ends in no
     * suffix the stripper knows.
     */
    @Test
    fun theGerundEndingIsStrippedBeforeTheVowelRuleSeesIt() {
        assertSameWord("comer", "comiendo")
        assertSameWord("escribir", "escribiendo")
        assertSameWord("beber", "bebiendo")
    }

    /** An `-ir` verb raises its stem vowel where no suffix rule can reach it. */
    @Test
    fun theRaisedIrVerbsAreListed() {
        assertSameWord("dormir", "durmiendo")
        assertSameWord("pedir", "pido")
        assertSameWord("sentir", "siento")
    }

    /**
     * A noun whose singular already ends in what looks like an infinitive needs
     * two strips to meet its own plural.
     */
    @Test
    fun aPluralMeetsItsSingular() {
        assertSameWord("mujer", "mujeres")
        assertSameWord("lugar", "lugares")
        assertSameWord("galleta", "galletas")
        assertSameWord("casa", "casas")
    }

    @Test
    fun accentsAndPunctuationDoNotMakeANewWord() {
        assertSameWord("comere", "comeré")
        assertEquals(es("agua"), es("¿agua?"))
    }

    /** Reducing has to stop somewhere, or every short verb meets every other. */
    @Test
    fun unrelatedWordsStayApart() {
        assertNotEquals(es("comer"), es("comprar"))
        assertNotEquals(es("agua"), es("abuela"))
        assertNotEquals(es("querer"), es("quitar"))
    }

    @Test
    fun englishInflectionsMeetToo() {
        assertEquals(en("want"), en("wanted"))
        assertEquals(en("want"), en("wants"))
        assertEquals(en("go"), en("went"))
        assertEquals(en("eat"), en("eating"))
        assertNotEquals(en("want"), en("water"))
    }

    /**
     * A pronoun glued to a verb is the same verb (#165). *Quiero irme a casa*
     * is the natural expansion of `yo querer ir casa`, and before this rule the
     * `irme` in it was an invented word and the sentence was thrown away.
     */
    @Test
    fun aPronounGluedToAVerbIsStillThatVerb() {
        assertSameWord("ir", "irme")
        assertSameWord("levantar", "levantarme")
        assertSameWord("duchar", "ducharme")
        assertSameWord("sentar", "sentarse")
        assertSameWord("comer", "comerla")
        assertSameWord("ayudar", "ayudarte")
        assertSameWord("beber", "beberlo")
    }

    /** Two of them stack, and the accent goes with the stress that moved. */
    @Test
    fun twoPronounsComeOffTogether() {
        assertSameWord("dar", "dármelo")
        assertSameWord("comer", "comérselo")
        assertSameWord("poner", "ponérnoslo")
    }

    /** A gerund takes them too — *diciéndolo*, *lavándose*. */
    @Test
    fun aGerundCarriesThemAsWell() {
        assertSameWord("lavar", "lavandose")
        assertSameWord("comer", "comiendolo")
    }

    /**
     * The half of the rule that keeps it honest.
     *
     * Every one of these ends in something on the enclitic list, and none of
     * them is a verb with a pronoun on it. The host check — the remainder has
     * to look like an infinitive or a gerund — is the only thing standing
     * between this rule and ordinary nouns licensing verbs they have nothing to
     * do with, which is the one direction [WordKey] is not allowed to fail in.
     */
    @Test
    fun anOrdinaryWordThatMerelyEndsInAPronounIsLeftAlone() {
        // Each of these reaches its verb *only* by the enclitic route, so a
        // collision here would be this rule's doing and nothing else's.
        assertNotEquals(es("parte"), es("parar"))
        assertNotEquals(es("aparte"), es("aparar"))
        assertNotEquals(es("gente"), es("generar"))
    }

    @Test
    fun aWordWithNothingInItHasNoKey() {
        assertEquals(null, es("¿"))
        assertEquals(null, es("   "))
    }
}
