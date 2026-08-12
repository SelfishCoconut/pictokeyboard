package org.pictokeyboard.sentence

/**
 * The words a language may add freely, and the ones that reverse meaning.
 *
 * This is the vocabulary half of the rule in §12.5 of the UI architecture
 * proposal: an expansion may add function words — articles, prepositions,
 * conjunctions, auxiliaries, inflection — and may not add content words. Which
 * words are which is a per-language fact, so it lives here rather than in the
 * checker.
 *
 * **Copulas and auxiliaries are function words on purpose.** `ser`, `estar` and
 * `haber` are exactly what a telegraphic phrase is missing: *yo bien* becomes
 * *estoy bien* by adding one. Treating them as content would reject every
 * correct expansion of the commonest case in the language. `tener`, by contrast,
 * is content — *tengo hambre* says something *yo hambre* did not, and the user
 * has to have asked for it.
 *
 * The lists are deliberately generous. A function word wrongly listed as content
 * costs a regeneration; a content word wrongly listed as function is a hole in
 * the safety property, so anything carrying meaning stays out.
 */
internal data class Lexicon(
    val functionWords: Set<String>,
    val negators: Set<String>,
    /** Inflections no suffix rule will reach, mapped to the stem of their lemma. */
    val irregulars: Map<String, String>,
    val suffixes: List<String>,
    /** Vowel changes a stem takes under stress, undone before comparing. */
    val stemChanges: List<Pair<Regex, String>> = emptyList(),
) {
    companion object {
        fun of(language: String): Lexicon =
            if (language.lowercase().startsWith("en")) English else Spanish

        /**
         * `ie`/`ue` are the stressed forms of `e`/`o` in Spanish stem-changing
         * verbs — *querer* → *quiero*, *poder* → *puedo*, *dormir* → *duermo*.
         * Undoing them is what lets the commonest AAC verb in the language match
         * its own conjugation.
         *
         * Not after `q` or `g`. There the `u` is silent — a spelling device that
         * makes the `g` hard and the `q` possible at all — and not a vowel that
         * ever alternated with anything. Without the exclusion the rule reads the
         * `ue` inside *querer* itself and rewrites it to *qorer*, so the one verb
         * this was written for is the one it breaks.
         */
        private val SPANISH_STEM_CHANGES = listOf(
            Regex("(?<![qg])ie") to "e",
            Regex("(?<![qg])ue") to "o",
        )

        val Spanish = Lexicon(
            functionWords = SPANISH_FUNCTION_WORDS,
            negators = setOf(
                "no", "ni", "nunca", "jamas", "nada", "nadie", "ninguno", "ninguna",
                "ningun", "tampoco",
            ),
            irregulars = SPANISH_IRREGULARS,
            suffixes = SPANISH_SUFFIXES,
            stemChanges = SPANISH_STEM_CHANGES,
        )

        val English = Lexicon(
            functionWords = ENGLISH_FUNCTION_WORDS,
            negators = setOf("no", "not", "never", "none", "nothing", "nobody", "neither", "nor"),
            irregulars = ENGLISH_IRREGULARS,
            suffixes = ENGLISH_SUFFIXES,
        )
    }
}

// Why `pero` is not a function word here, even though it is one.
//
// §12.5 says conjunctions may be added freely. #45's own worked example says the
// opposite, and names this very sentence: expanding `yo bien querer comida` into
// "estoy bien, pero quiero comida" invents `pero`, "a contrast the user never
// expressed". Both cannot hold, and the example is the one that is right --
// because the rule it belongs to is not really about parts of speech, it is
// about meaning, and an adversative conjunction carries some.
//
// "Estoy bien y quiero comida" and "estoy bien pero quiero comida" are different
// claims about the same words. So are "quiero agua" and "quiero agua porque
// tengo sed". Additive and alternative connectives -- y, o, and, or -- do not do
// this: they join what is there without asserting a relation between the halves.
//
// So the adversatives and causals are left out of the freely-addable sets, which
// puts them under the ordinary "content out is a subset of content in" rule with
// no new machinery: the model may not add one, and may use one the user tapped.
//
//   Spanish: pero, sino, aunque, porque, pues
//   English: but, because, so
//
// `although`, `though` and `however` were never in the English list, so they
// were already being treated this way.

/**
 * Articles, prepositions, additive conjunctions, pronouns, and every form of the
 * three verbs Spanish uses to hold a sentence together. Accents are already
 * stripped by the time these are consulted, so they are written without them.
 */
private val SPANISH_FUNCTION_WORDS = setOf(
    // articles and determiners
    "el", "la", "los", "las", "un", "una", "unos", "unas", "lo", "al", "del",
    "este", "esta", "estos", "estas", "ese", "esa", "esos", "esas", "aquel",
    "aquella", "mi", "mis", "tu", "tus", "su", "sus", "nuestro", "nuestra",
    // pronouns
    "yo", "me", "mi", "conmigo", "te", "ti", "contigo", "el", "ella", "ello",
    "se", "si", "nosotros", "nosotras", "vosotros", "vosotras", "ellos", "ellas",
    "usted", "ustedes", "le", "les", "nos", "os",
    // prepositions and conjunctions
    "a", "ante", "bajo", "con", "contra", "de", "desde", "durante", "en", "entre",
    "hacia", "hasta", "mediante", "para", "por", "segun", "sin", "sobre", "tras",
    "y", "e", "o", "u", "que", "como",
    "cuando", "donde", "mientras", "si",
    // pero, sino, aunque, porque and pues are deliberately absent -- see the
    // note above SPANISH_FUNCTION_WORDS.
    // ser
    "ser", "soy", "eres", "es", "somos", "sois", "son", "era", "eras", "eramos",
    "eran", "fui", "fuiste", "fue", "fuimos", "fueron", "sere", "sera", "seran",
    "seria", "sea", "seas", "seamos", "sean", "siendo", "sido",
    // estar
    "estar", "estoy", "estas", "esta", "estamos", "estais", "estan", "estaba",
    "estabas", "estabamos", "estaban", "estuve", "estuviste", "estuvo",
    "estuvimos", "estuvieron", "estare", "estara", "estaran", "estaria", "este",
    "esten", "estando", "estado",
    // haber
    "haber", "he", "has", "ha", "hay", "hemos", "habeis", "han", "habia",
    "habias", "habiamos", "habian", "hube", "hubo", "habra", "habran", "habria",
    "haya", "hayan", "habiendo", "habido",
)

/**
 * Spanish inflections no suffix rule reaches, mapped to the stem their lemma
 * reduces to. Only content verbs are here — the copulas and auxiliaries are
 * function words above and never need matching.
 */
private val SPANISH_IRREGULARS = mapOf(
    // tener
    "tengo" to "ten", "tienes" to "ten", "tiene" to "ten", "tenemos" to "ten",
    "tienen" to "ten", "tuve" to "ten", "tuvo" to "ten", "tuvieron" to "ten",
    "tendra" to "ten", "tenga" to "ten", "tener" to "ten", "teniendo" to "ten",
    // ir
    "voy" to "ir", "vas" to "ir", "va" to "ir", "vamos" to "ir", "van" to "ir",
    "iba" to "ir", "iban" to "ir", "ire" to "ir", "ira" to "ir", "vaya" to "ir",
    "yendo" to "ir", "ido" to "ir", "ir" to "ir",
    // hacer
    "hago" to "hac", "haces" to "hac", "hace" to "hac", "hacemos" to "hac",
    "hacen" to "hac", "hice" to "hac", "hizo" to "hac", "hicieron" to "hac",
    "hara" to "hac", "haga" to "hac", "hacer" to "hac", "haciendo" to "hac",
    // decir
    "digo" to "dec", "dices" to "dec", "dice" to "dec", "decimos" to "dec",
    "dicen" to "dec", "dije" to "dec", "dijo" to "dec", "dijeron" to "dec",
    "dira" to "dec", "diga" to "dec", "decir" to "dec", "diciendo" to "dec",
    // saber
    "se" to "sab", "sabes" to "sab", "sabe" to "sab", "sabemos" to "sab",
    "saben" to "sab", "supe" to "sab", "supo" to "sab", "sabra" to "sab",
    "sepa" to "sab", "saber" to "sab", "sabiendo" to "sab",
    // ver
    "veo" to "ver", "ves" to "ver", "ve" to "ver", "vemos" to "ver",
    "ven" to "ver", "vi" to "ver", "vio" to "ver", "vieron" to "ver",
    "vera" to "ver", "vea" to "ver", "ver" to "ver", "viendo" to "ver",
    "visto" to "ver",
    // dar
    "doy" to "dar", "das" to "dar", "da" to "dar", "damos" to "dar",
    "dan" to "dar", "di" to "dar", "dio" to "dar", "dieron" to "dar",
    "dara" to "dar", "de" to "dar", "dar" to "dar", "dando" to "dar",
    // venir
    "vengo" to "ven", "vienes" to "ven", "viene" to "ven", "venimos" to "ven",
    "vienen" to "ven", "vine" to "ven", "vino" to "ven", "vinieron" to "ven",
    "vendra" to "ven", "venga" to "ven", "venir" to "ven", "viniendo" to "ven",
    // poner
    "pongo" to "pon", "pones" to "pon", "pone" to "pon", "ponemos" to "pon",
    "ponen" to "pon", "puse" to "pon", "puso" to "pon", "pusieron" to "pon",
    "pondra" to "pon", "ponga" to "pon", "poner" to "pon", "poniendo" to "pon",
    // querer and poder keep their stem change in the present, which the vowel
    // rule handles, but raise it unpredictably in the preterite, which it cannot.
    "quise" to "quer", "quiso" to "quer", "quisieron" to "quer",
    "pude" to "pod", "pudo" to "pod", "pudieron" to "pod",
    // -ir verbs raise the stem vowel to `u` or `i` in the gerund and the third
    // person preterite — dormir → durmiendo, pedir → pidió. It is a closed set
    // of common verbs, and cheaper to list than to infer from a stem that no
    // longer says which conjugation it came from.
    "durmiendo" to "dorm", "durmio" to "dorm", "durmieron" to "dorm",
    "muriendo" to "mor", "murio" to "mor", "murieron" to "mor",
    "pidiendo" to "ped", "pidio" to "ped", "pidieron" to "ped", "pido" to "ped",
    "pides" to "ped", "pide" to "ped", "piden" to "ped",
    "sirviendo" to "serv", "sirvio" to "serv", "sirvo" to "serv", "sirve" to "serv",
    "sintiendo" to "sent", "sintio" to "sent", "siento" to "sent",
    "sientes" to "sent", "siente" to "sent", "sienten" to "sent",
)

/**
 * Ordered longest first, because stripping `-amos` before `-os` is the
 * difference between *hablamos* → *habl* and *hablamos* → *hablam*.
 *
 * The infinitive endings `-ar`, `-er` and `-ir` are in here, which is what lets
 * *querer* meet *quiero*. They also truncate nouns that happen to end the same
 * way — *mujer* → *muj* — and that is why [WordKey] strips more than one suffix:
 * *mujeres* → *mujer* → *muj* lands in the same place, where a single pass would
 * have left the singular and the plural looking like different words.
 */
private val SPANISH_SUFFIXES = listOf(
    "ariamos", "eriamos", "iriamos", "ieramos", "iesemos", "abamos", "aramos",
    "aremos", "eremos", "iremos", "ieseis", "ierais", "abais", "arais",
    "arian", "erian", "irian", "ieron", "iendo", "aria", "eria", "iria",
    "aban", "aron", "ando", "amos", "emos", "imos", "aste", "iste", "iera",
    "iese", "abas", "aba", "ara", "ase", "ado", "ada", "ida", "ido", "are",
    "ere", "ire", "era", "ira", "ar", "er", "ir", "an", "en", "as", "es",
    "os", "a", "e", "i", "o", "s",
)

private val ENGLISH_FUNCTION_WORDS = setOf(
    // articles and determiners
    "a", "an", "the", "this", "that", "these", "those", "my", "your", "his",
    "her", "its", "our", "their", "some", "any",
    // pronouns
    "i", "me", "you", "he", "she", "it", "we", "they", "him", "them", "us",
    "myself", "yourself", "himself", "herself", "itself", "ourselves",
    // prepositions and conjunctions
    "of", "to", "in", "on", "at", "by", "for", "with", "about", "from", "into",
    "over", "under", "up", "down", "out", "off", "and", "or",
    "if", "when", "while", "as", "than", "then", "there", "here",
    // but, because and so are deliberately absent -- see the note above
    // SPANISH_FUNCTION_WORDS.
    // be
    "be", "am", "is", "are", "was", "were", "been", "being",
    // have and do, as auxiliaries
    "have", "has", "had", "having", "do", "does", "did", "doing", "done",
    // modals
    "will", "would", "shall", "should", "can", "could", "may", "might", "must",
)

private val ENGLISH_IRREGULARS = mapOf(
    "went" to "go", "gone" to "go", "goes" to "go", "going" to "go", "go" to "go",
    "ate" to "eat", "eaten" to "eat", "eats" to "eat", "eating" to "eat", "eat" to "eat",
    "drank" to "drink", "drunk" to "drink", "drinking" to "drink", "drinks" to "drink",
    "slept" to "sleep", "sleeping" to "sleep", "sleeps" to "sleep",
    "felt" to "feel", "feeling" to "feel", "feels" to "feel",
    "said" to "say", "saying" to "say", "says" to "say",
    "made" to "make", "making" to "make", "makes" to "make",
    "took" to "take", "taken" to "take", "taking" to "take", "takes" to "take",
    "gave" to "give", "given" to "give", "giving" to "give", "gives" to "give",
    "saw" to "see", "seen" to "see", "seeing" to "see", "sees" to "see",
    "came" to "come", "coming" to "come", "comes" to "come",
    "got" to "get", "getting" to "get", "gets" to "get",
)

private val ENGLISH_SUFFIXES = listOf("ingly", "edly", "ing", "ied", "ies", "ed", "es", "ly", "s")
