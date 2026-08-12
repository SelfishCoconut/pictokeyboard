package org.pictokeyboard.sentence.llm;

import org.pictokeyboard.sentence.llm.ISentenceCallback;

/**
 * The keyboard's whole view of the model.
 *
 * Words travel as two parallel String arrays rather than as a Parcelable: a
 * board may mix languages, so each word carries its own, and two arrays keep
 * the binder contract free of a custom type that would have to be versioned
 * alongside it.
 */
interface ISentenceService {

    /**
     * Asks for a rephrase. Returns immediately; the answer arrives on `callback`.
     *
     * `variant` cycles the sampler, which is what makes pressing Beautify again
     * offer a different sentence instead of the same one.
     */
    void beautify(
        int requestId,
        in String[] words,
        in String[] wordLanguages,
        String language,
        int variant,
        ISentenceCallback callback);

    /**
     * Abandons a request. Called when the input target changes, so a generation
     * for one app can never land in another (#46).
     */
    void cancel(int requestId);

    /** True once weights are loaded and a request would actually be served. */
    boolean isReady();
}
