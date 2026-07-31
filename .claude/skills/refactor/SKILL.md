---
name: refactor
description: Turn prototype code into production code safely — pick a seam, characterize it with tests, change it, verify. Use for "refactor X", "clean this up", "this is prototype code", "restructure the architecture".
---

# Prototype to production

A prototype earned its shape by being written fast to answer a question. Refactoring it is not punishment for that; it is the next phase. The failure mode to avoid is the rewrite-by-vibes: a large, unverifiable diff that changes structure and behavior at once, so that when something breaks nobody can tell which half did it.

The rule: **behavior-preserving changes and behavior-changing changes never share a commit.**

## 1. Pick one seam

Not "the architecture". One seam — a class, a boundary, a single responsibility that wants to move. If the change cannot be described in one sentence without "and", it is too big; split it.

Choose by pain, not by tidiness: the code you keep having to work around, the class every feature has to touch, the boundary where bugs recur. `codebase-sanity` can rank these if you need evidence.

## 2. Characterize before you change

Prototype code is usually untested, so there is nothing holding behavior in place. Before touching it, write **characterization tests**: tests that assert what the code *currently does*, including behavior you think is wrong.

- Do not fix bugs while characterizing. Encode the buggy behavior, note it, fix it later as a separate, visible change.
- Cover the paths you are about to disturb, plus the boundaries: empty input, null, first and last element, error paths.
- If a piece genuinely cannot be tested where it stands, that untestability *is* the thing to fix first — usually by extracting logic out of a framework class (Activity, `InputMethodService`, Composable) into a plain function or class that takes its dependencies as parameters. That extraction is itself mechanical and low-risk.

Run them. They must pass against the unmodified code — a characterization test that fails immediately is describing something other than reality.

## 3. Change in small, reversible steps

Prefer the mechanical refactorings the IDE and compiler can verify: extract function, extract class, rename, move, introduce parameter, replace conditional with polymorphism. Run tests after each step, commit after each green step.

Common Android prototype-to-production moves:

- Logic out of `Activity`/`Service`/Composable into a ViewModel or plain Kotlin class.
- Direct DAO/network access out of the UI, behind a repository interface.
- Manual singletons and `object` service locators into constructor injection — which is also what makes the code testable.
- `SharedPreferences` scattered through the codebase into one typed settings class.
- Nullable-everything data classes into types that make invalid states unrepresentable (sealed classes, non-null fields, value classes).
- Silent `catch {}` into explicit `Result`/sealed error types.

## 4. Verify

Characterization tests still pass, unchanged. If you had to edit a test to make it pass, you changed behavior — stop and be explicit about it: either it was intended (then it is a separate commit with its own reasoning) or it was not (then it is a bug you just introduced).

Then run the app (`run` skill) and exercise the path by hand. Unit tests do not catch lifecycle, threading, or integration regressions, which are exactly what Android refactors break.

## 5. Then change behavior, separately

With structure sound and tests in place, fix the bugs you noted in step 2 — each one now a small diff against a tested baseline, easy to review and easy to revert.

## What not to do

- Do not refactor and add a feature in the same change.
- Do not "improve" code you are not otherwise touching; unrelated churn hides the real diff from review.
- Do not delete code you do not understand. Find out what depends on it first — the answer is sometimes "the manifest" or "a KSP-generated caller", which greps do not reveal.
- Do not start a second seam before the first is green and committed.
