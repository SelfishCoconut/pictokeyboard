package org.pictokeyboard.sentence.llm;

/**
 * How the :llm process answers. `oneway` so the service never blocks on the
 * keyboard: an IME that stalls on a binder call is an IME that stops accepting
 * taps, and #44's rule is that nothing about this feature can slow typing down.
 */
oneway interface ISentenceCallback {

    /** A candidate that passed the validator. */
    void onSentence(int requestId, String text);

    /**
     * No sentence. `reason` is a SentenceResult ordinal: nothing survived the
     * validator, or the model was not reachable at all. The two are separate
     * because only one of them is worth telling the user about.
     */
    void onNothing(int requestId, int reason);
}
