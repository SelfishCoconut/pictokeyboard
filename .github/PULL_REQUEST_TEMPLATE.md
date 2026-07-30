## What

<!-- One or two sentences. What changes for a user of the app? -->

Closes #

## Why

<!-- The problem this solves. Link the issue's acceptance criteria if they need context. -->

## How

<!-- Only the parts a reviewer could not infer from the diff: design decisions,
     trade-offs, anything you tried first that did not work. -->

## Verification

<!-- What you actually ran and observed. "CI is green" is not verification of behavior. -->

- [ ] Ran on a device/emulator and exercised the changed path
- [ ] Unit tests added or updated
- [ ] Instrumented tests pass (if UI or IME behavior changed)

## Checklist

- [ ] Accessibility: labels present, 48dp touch targets, readable at 2x font scale, no color-only meaning
- [ ] Strings: no hardcoded user-visible text; every locale updated
- [ ] No new permission (or: justified below, and Data Safety updated)
- [ ] Docs/README updated if behavior or setup changed

<!-- Add the `automerge` label to have this squash-merged automatically once CI passes. -->
