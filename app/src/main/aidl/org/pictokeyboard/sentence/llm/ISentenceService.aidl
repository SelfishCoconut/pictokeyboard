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
     *
     * `unvalidated` asks for the answer with SentenceValidator switched off
     * (#167), so the prompt and the weights can be judged on their own. It is a
     * request, not an instruction: the service runs it through
     * ValidatorBypass.allowed against BuildConfig.DEBUG, which is false in every
     * shipped build. Sending true from a release build changes nothing.
     */
    void beautify(
        int requestId,
        in String[] words,
        in String[] wordLanguages,
        String language,
        int variant,
        boolean unvalidated,
        ISentenceCallback callback);

    /**
     * Abandons a request. Called when the input target changes, so a generation
     * for one app can never land in another (#46).
     */
    void cancel(int requestId);

    /** True once weights are loaded and a request would actually be served. */
    boolean isReady();
}
