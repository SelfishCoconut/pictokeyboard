---
name: feature-request
description: Turn a plain-language request into a labeled, board-ready GitHub issue, and optionally a feature branch. Use for "add a feature", "I want X", "turn this into an issue", "create a card for X", "file a bug for this".
---

# Request to board-ready issue

The front door for informal requests: convert "I want the text to be bigger in settings" into a properly-formed GitHub issue on the board, so the request -> issue -> PR -> merge loop starts clean. Board automation adds the issue to Backlog on creation — do not hand-place it.

This skill *orchestrates*. It does not re-implement PR creation (that is `commit-commands`) or duplicate any other skill's job.

## 1. Classify

- **Feature** — new user-visible capability. Template: `feature.yml`.
- **Bug** — existing behavior is wrong. Template: `bug.yml`. Needs steps to reproduce, expected vs actual, device and Android version.
- **Accessibility** — usability barrier for assistive-technology users. Template: `accessibility.yml`. Name the assistive technology and what is blocked.
- **Infrastructure** — build, CI, tooling. No user-visible change.

If the request contains several independent changes, file several issues. One issue, one deliverable, one PR.

## 2. Check for duplicates first

```sh
gh issue list --state all --search "<keywords>"
```

Adding to an existing issue beats opening a near-duplicate. If it is a duplicate, say so and add the new detail as a comment instead.

## 3. Write it

Title: imperative and specific — "Add adjustable speech rate to settings", not "speech stuff".

Body must answer:

- **What** the change is, in user-visible terms.
- **Why** — the problem it solves. A feature with no stated problem cannot be judged done.
- **Acceptance criteria** — a concrete, checkable list. This is the definition of done and what the PR is reviewed against.
- **Out of scope**, when the request could plausibly sprawl.

For accessibility issues, acceptance criteria must name the assistive technology and the passing behavior ("TalkBack announces the category name and selected state on each cell").

## 4. Create

```sh
gh issue create --title "..." --body "..." --label "<type>" --milestone "<milestone>"
```

Apply the type label; add area labels if the repo uses them. Report the issue number back to the user.

## 5. Branch, if asked

```sh
git switch -c <type>/<issue-number>-<short-slug>
```

The `remind-board-issue` hook fires here — that is expected. When the PR is opened, its body must contain `Closes #<n>` so the board advances and the issue closes on merge.

## Judgment

If the request is ambiguous in a way that changes what gets built, ask before filing. A wrong issue produces a wrong PR and wastes more time than the question would have. If it is ambiguous in a way that does not change the work, pick the sensible reading, state the assumption in the issue body, and proceed.
