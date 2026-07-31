---
name: progress-report
description: Generate an on-demand progress summary — commits, merged PRs, closed issues, board movement, and open questions — since a given point. Use for "what's the status", "write a progress report", "what changed since X".
---

# Progress report

An on-demand summary of movement since a point the user specifies. Default: since the last report in `docs/reports/`, else the last 20 commits. Never invent a time window — state the one you used.

## Gather

```sh
git log --oneline --since=<date>
git diff --stat <ref>..HEAD
gh pr list --state merged --search "merged:>=<date>"
gh issue list --state closed --search "closed:>=<date>"
gh issue list --state open --label bug
```

Also worth pulling: the latest CI run status (`gh run list --limit 5`), and any release tags in the window.

## Write

Structure it as:

1. **Shipped** — what a user of the app would now notice. User-visible outcomes, not commit subjects. If nothing user-visible shipped, say that plainly; internal work is legitimate progress and does not need to be dressed up.
2. **Internal** — refactoring, CI, test coverage, dependency work. One or two lines.
3. **In flight** — open PRs and what blocks them.
4. **Open questions** — decisions waiting on the user. This is the section they actually need; put anything requiring a human call here, with the options and your recommendation.
5. **Health** — CI green or not, known bugs, anything trending badly.

## Rules

- Report what the evidence shows. If tests are failing, say so with the run link. If a feature is half-done, say half-done — a report that overstates progress is worse than no report.
- Cite issue and PR numbers so every claim is checkable.
- Keep it short. A progress report nobody reads has failed at its only job; prefer ten honest lines over three padded pages.
- Save to `docs/reports/YYYY-MM-DD.md` only if the user asks for it to be kept.
