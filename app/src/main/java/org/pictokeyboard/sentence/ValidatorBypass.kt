package org.pictokeyboard.sentence

/**
 * Whether a rephrase may skip [SentenceValidator] — and the only place that
 * decides (#167).
 *
 * **Why a bypass exists at all.** When the keyboard says *"Left as you wrote
 * it"*, two very different things may have happened: the model wrote something
 * good and the harness threw it away, or the model wrote nonsense. #165 was the
 * first kind, and it was found by hand-tracing the lexicon rather than by asking
 * the running app — which is the wrong way round. Judging the prompt and the
 * weights (#42) means seeing what they actually said, and that cannot be done
 * while every answer is filtered.
 *
 * **Why it can never ship.** Content lemmas out ⊆ content lemmas in is the one
 * property that makes it honest to say this keyboard speaks *as* the user and
 * never *for* them. An installable build where a 0.6B model's output goes
 * unchecked into a non-speaking person's message is not a diagnostic — it is
 * precisely the failure the whole milestone was designed around. The right fix
 * for a harness that rejects good sentences is a better harness (#165), not no
 * harness.
 *
 * So [allowed] takes the build type as an argument rather than reading
 * `BuildConfig.DEBUG` itself. That is what lets a unit test ask the *release*
 * question — running in a debug build, where `BuildConfig.DEBUG` is true and an
 * inline check could only ever assert itself. `ValidatorBypassTest` asks it, and
 * also checks by reading the sources that nothing else in the app turns
 * validation off.
 */
object ValidatorBypass {

    /**
     * @param requested what the caregiver's switch says, as it arrived over the
     *   binder from the keyboard process.
     * @param debugBuild `BuildConfig.DEBUG` at the call site. Pass it; do not
     *   read it here, or the guard becomes untestable.
     */
    fun allowed(requested: Boolean, debugBuild: Boolean): Boolean = requested && debugBuild
}
