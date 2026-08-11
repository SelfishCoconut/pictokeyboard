package org.pictokeyboard.sentence

/**
 * What the model is told, in the language it is being asked to write.
 *
 * **One prompt per language, not one prompt with a language named in it.** A
 * 0.6B model asked in English to "reply in Spanish" drifts back into English
 * halfway through a sentence, and the failure is silent — the validator would
 * accept `I want water` as an expansion of `yo querer agua`, because every
 * content word maps and only the language is wrong. Writing the instructions in
 * Spanish keeps the model in Spanish.
 *
 * **The prompt is not the safety mechanism.** Every rule stated here is also
 * enforced by [SentenceValidator] after generation, and a candidate that breaks
 * one is discarded whatever the prompt said. The rules are here because a model
 * that has been told them fails less often, which means fewer retries and a
 * faster answer — not because asking politely is protection (#45).
 *
 * Versioned so a change to the wording is visible next to the scores it changed
 * (#42, #43).
 */
object Prompts {

    /** Bumped on any change to the text below, and recorded with eval results. */
    const val VERSION = 1

    /**
     * The examples do most of the work at this size. Each one is a real shape
     * from the seeded boards — a bare verb chain, a negation, a single word, and
     * one that is already a sentence and must come back untouched.
     */
    private val SPANISH = """
        Eres parte de un teclado de pictogramas. El usuario toca imágenes y sale una
        lista de palabras sueltas. Tu trabajo es escribir esa lista como una frase
        natural en español.

        Reglas:
        - Usa SOLO las palabras que te da el usuario. No añadas ninguna palabra con
          significado propio: ni sustantivos, ni verbos, ni adjetivos, ni adverbios.
        - Sí puedes añadir artículos, preposiciones, conjunciones y verbos auxiliares,
          y puedes conjugar, concordar en género y número, y ordenar.
        - No añadas "no", "nunca", "nada" ni ninguna otra negación si el usuario no la
          ha puesto. Si el usuario la ha puesto, consérvala.
        - Si la lista ya es una frase correcta, devuélvela igual.
        - Responde solo con la frase. Sin comillas y sin explicaciones.

        Ejemplos:
        yo querer agua -> Quiero agua.
        mamá venir casa -> Mamá viene a casa.
        yo no querer comer -> No quiero comer.
        galleta -> Una galleta.
        yo estar cansado -> Estoy cansado.
    """.trimIndent()

    private val ENGLISH = """
        You are part of a pictogram keyboard. The user taps pictures and gets a list
        of separate words. Your job is to write that list as a natural English
        sentence.

        Rules:
        - Use ONLY the words the user gave you. Do not add any word that carries
          meaning of its own: no nouns, no verbs, no adjectives, no adverbs.
        - You may add articles, prepositions, conjunctions and auxiliary verbs, and
          you may inflect, agree and reorder.
        - Do not add "not", "never", "nothing" or any other negation if the user did
          not tap one. If the user did tap one, keep it.
        - If the list is already a correct sentence, return it unchanged.
        - Reply with the sentence only. No quotes and no explanation.

        Examples:
        I want water -> I want water.
        mum come home -> Mum is coming home.
        I not want eat -> I do not want to eat.
        biscuit -> A biscuit.
        I be tired -> I am tired.
    """.trimIndent()

    /** The system instruction for [language], falling back to English. */
    fun systemPrompt(language: String): String =
        if (language == "es") SPANISH else ENGLISH

    /**
     * The user turn: the tapped words, and nothing else.
     *
     * Deliberately bare. Wrapping them in a sentence of instruction ("please
     * rewrite the following:") gives a small model more English to copy when it
     * is supposed to be writing Spanish, and gives it a second thing in the turn
     * that it might answer instead.
     */
    fun userTurn(typed: List<TypedWord>): String = typed.joinToString(" ") { it.text }

    /**
     * What comes back, cleaned up enough to be judged.
     *
     * Small instruction-tuned models wrap answers in quotes, prefix them with a
     * restatement, or hand back several lines. Only the first non-empty line is
     * taken: a second line is the model explaining itself, and explanations are
     * not candidates. Nothing here is allowed to *fix* a candidate — stripping a
     * quote is presentation, and anything beyond that would be this file quietly
     * editing what the validator is about to check.
     */
    fun firstCandidate(raw: String): String =
        raw.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .trim()
}
