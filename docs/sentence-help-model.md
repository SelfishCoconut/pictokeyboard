# The sentence-help model, and why it is this one

Closes the model-choice half of #43. The executable version of everything here
is `ModelSpec.kt`; this file is the reasoning, so that a later change has
something to argue with.

## The decision

| | |
|---|---|
| **Model** | Qwen3 0.6B, int4 block-32, the `nothink` build |
| **File** | `qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm` |
| **Size** | 347 MB |
| **Source** | [`litert-community/Qwen3-0.6B-int4`](https://huggingface.co/litert-community/Qwen3-0.6B-int4) |
| **Licence** | Apache-2.0 |
| **Runtime** | `com.google.ai.edge.litertlm:litertlm-android:0.16.0`, Apache-2.0 |
| **Floor** | 3 GB total RAM, and an `arm64-v8a` or `x86_64` processor |

## Why not Gemma

#43 predicted "Gemma 3 1B where the device can take it, Qwen2.5 0.5B as the
fallback", and expected the licence asymmetry — Apache-2.0 against Google's own
terms — to be the tiebreak if the two scored closely.

**The tiebreak never got that far, because every Gemma build on Hugging Face is
gated.** `litert-community/Gemma3-1B-IT`, `litert-community/gemma-3-270m-it` and
`litert-community/embeddinggemma-300m` all report `gated: auto`: you must accept
terms while signed in before the file will download.

That is not a licensing preference, it is an architectural block. Downloading a
gated file needs an access token, and an access token in a public APK is a
credential anybody can read out of it — one that is attached to a person's
account, that can be revoked, and whose revocation would break the feature for
every user at once. There is no version of "download the weights on demand"
(#44) that works with a gated repository.

So the choice was among the ungated Apache-2.0 models, and it was decided by
reading the repositories rather than by preference:

| Candidate | Gated | Licence | Why not |
|---|---|---|---|
| Gemma 3 1B / 270M | **yes** | Gemma Terms | Needs a token in the APK |
| Qwen2.5 0.5B Instruct | no | Apache-2.0 | 546 MB at q8, and superseded |
| SmolLM2 360M | no | Apache-2.0 | Very little Spanish in its training mix |
| granite-4.0-350m | no | Apache-2.0 | Worth measuring, English-leaning |
| **Qwen3 0.6B int4** | **no** | **Apache-2.0** | **chosen** |

Qwen3 0.6B is also simply the better instrument for this job than the 0.5B the
issue predicted: newer, explicitly multilingual, and available quantised to int4
at 347 MB rather than 546 MB at q8. The `nothink` variant matters on its own —
a reasoning trace is wasted latency when the whole task is re-saying eight words
somebody already chose, and #44's budget is a full sentence in under two seconds.

## Why LiteRT-LM rather than MediaPipe

`com.google.mediapipe:tasks-genai` reads `.task` bundles and is the older, more
widely documented path. `litertlm-android` reads `.litertlm` and is what Google
now points at. Two things settled it:

- **The ungated models are published in `.litertlm`.** Qwen3-0.6B-int4 ships
  `.litertlm` only. Choosing MediaPipe would have meant choosing Qwen2.5 0.5B q8
  — 200 MB more, for an older model.
- It is at **0.16.0**, not the `0.9.0-beta` that a stale search result suggests.

The cost is real and worth stating: `litertlm-android` carries a ~21 MB native
library for `arm64-v8a` (and ~25 MB for `x86_64`), and that ships in the APK
whether or not anybody turns the feature on. A Play AAB delivers only the
device's own ABI, so the figure a user pays is roughly **+21 MB on a 29 MB app**.
There is no 32-bit ARM build at all, which is why `Capability` checks the ABI
before anything offers a download.

## Quantisation

int4 block-32 is what the repository publishes and what makes 0.6B fit in 347 MB
rather than ~1.2 GB at f32. **The quality cost has not been measured** — that is
#42's job, and until the eval set exists this is an assumption rather than a
finding. It is recorded here as an assumption on purpose.

## The floor, and what happens below it

`ModelSpec.MIN_TOTAL_RAM_BYTES` is 3 GB of *total* device RAM. The weights are
347 MB; the runtime needs those plus a KV cache and its own arenas, so budget
about a gigabyte resident in `:llm`. On a 2 GB phone that is most of what is left
after the foreground app, and the process Android reclaims first is whichever is
cheapest to lose.

Below the floor — or on a 32-bit processor, or with no room on disk — Settings
says so plainly instead of offering a download that would fail. Storage is judged
last of the three, because it is the only one that can change: a phone that is
full today can be cleared tonight, while a processor cannot.

## The prompt

`Prompts.kt`, version 1. One prompt per language, written *in* that language:
a 0.6B model told in English to answer in Spanish drifts back into English
mid-sentence, and that failure is silent — the validator would accept
`I want water` as an expansion of `yo querer agua`, because every content word
maps and only the language is wrong.

The prompt is **not** the safety mechanism. Every rule it states is enforced
again by `SentenceValidator` after generation, and a candidate that breaks one is
discarded whatever the prompt said. The rules are in the prompt because a model
that has been told them fails less often, which means fewer retries and a faster
answer.

## Attribution

Qwen3 is Apache-2.0, which requires the licence and attribution to travel with
redistribution. The app does not redistribute the weights — it downloads them
from the publisher — but it does name the model, and Settings → About carries
the model name, its source and its licence.

## Speed, measured on the phone rather than assumed

`DeviceCapability` checks a processor, some memory and some free disk, and none
of those is speed. A phone can clear all three and still take eight seconds to
produce a sentence — and eight seconds mid-conversation is not a feature, it is a
person waiting while somebody else fills the silence.

So `SentenceBenchmark` (#145) runs the model once on a fixed short phrase as soon
as the download verifies, and Settings shows what it found:

- **how long one sentence took**, with the weights already warm. This is the
  figure #44's two-second budget is about, and the one paid every time.
- **how long loading took**, paid once per keyboard session by the first sentence.
- whether the run finished at all. *"The test did not finish"* is a different
  sentence from *"this phone is slow"*, and they ask for different things. A
  failed run is stored as a zero and said differently.

**The number never disables anything.** A phone over the budget is told, not
refused: a caregiver whose child has no other way to build a sentence may well
decide four seconds is worth it, and that decision is not the app's to make.

The feature is also labelled experimental where it is switched on. It was already
off by default; what was missing was the sentence saying that this is new, that
it may be slow, and that turning it off costs nothing.

## Still open

- **#42 has not been built.** There is no eval set and no measured score for this
  model, this quantisation or this prompt. Everything above is a decision made on
  licensing, availability and size — all of which are facts — plus a prediction
  about quality, which is not. The prompt is versioned so that when #42 exists,
  its scores have something to attach to.
  **#145 measures speed, not quality.** A candidate the validator throws away
  still counts towards the timing, because the phone took exactly as long either
  way. The two questions are deliberately separate.
- No run against the real weights has happened yet on hardware anybody would use.
  The benchmark's *failure* path is exercised end to end — a deliberately corrupt
  347 MB file produces `INVALID_ARGUMENT: Invalid magic number`, `LiteRtEngine`
  returns false rather than taking the process with it, and Settings says the test
  did not finish — but no real number has been produced by this code yet.
