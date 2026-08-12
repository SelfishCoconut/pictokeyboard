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
    const val VERSION = 2

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

    /**
     * The system instruction for [language], falling back to English.
     *
     * @param avoid words an earlier attempt was rejected for inventing (#177).
     *   Empty on a first attempt, which is the ordinary case.
     */
    fun systemPrompt(language: String, avoid: List<String> = emptyList()): String {
        val base = if (language == "es") SPANISH else ENGLISH
        return base + avoidClause(language, avoid)
    }

    /**
     * The one thing that measurably makes a retry different (#177).
     *
     * The variant was supposed to do this by moving the sampler, and on this
     * model it does not: swept on a device at seeds 1 and 2, `agua` came back as
     * *"Una galleta."* at every temperature from 0.0 to 1.5 and only moved at
     * 2.0 — where the answer degrades to *"Una agua."*. The whole 0.2–0.95 ladder
     * `LiteRtEngine` was climbing sits inside that flat region, so three attempts
     * bought three copies of the same rejection.
     *
     * Naming the rejected word moved it on the first try, at the temperature the
     * feature actually runs at: *"Una galleta."* became *"agua."*. That is the
     * difference between a prompt nudge and a sampler nudge — the prompt really
     * did change, and a model that cannot be shaken loose by randomness can be
     * told.
     *
     * **What did not work, so that nobody adds it back.** A second clause naming
     * the previous *answer* — `Ya escribiste «Quiero.». Escríbelo de otra
     * manera.` — was built, shipped to the device, and confirmed present in the
     * prompt by logging it. The model returned `Quiero.` again, and again. A ban
     * on a word it can simply not say is obeyed; an instruction to find another
     * phrasing, when it believes there is only one, is not. That clause is gone
     * rather than left in to dilute a 0.6B model's context with an instruction it
     * ignores.
     *
     * **Still not the safety mechanism.** [SentenceValidator] checks the answer
     * whatever this said, exactly as with every other rule in this file. A model
     * that ignores the clause is caught the same way it was before.
     */
    private fun avoidClause(language: String, avoid: List<String>): String {
        // A non-empty list can still have nothing to say -- a "word" that was
        // pure punctuation comes out blank -- and a clause about nothing is two
        // wasted lines in a small model's context.
        val words = avoid.map(::bareWord).filter { it.isNotEmpty() }.distinct()
        if (words.isEmpty()) return ""
        val named = words.joinToString(", ")
        return if (language == "es") {
            "\n\nNo uses estas palabras, no las ha tocado nadie: $named."
        } else {
            "\n\nDo not use these words, nobody tapped them: $named."
        }
    }

    /**
     * A rejected word as it should appear in the clause.
     *
     * The validator reports the word as it stood in the candidate, punctuation
     * and all — `galleta.` — because that is what makes a log honest about what
     * was in the sentence. A prompt wants the word.
     */
    private fun bareWord(word: String): String = word.trim().trim { it in PUNCTUATION }

    private const val PUNCTUATION = ".,;:!?¡¿\"'»«"

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
