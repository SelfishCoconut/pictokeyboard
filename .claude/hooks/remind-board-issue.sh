#!/bin/sh
# PreToolUse hook (Bash): keep the Kanban board honest.
#
# When Claude creates a feature/fix branch or opens a PR, remind it that the
# change must trace to a GitHub issue, and that the PR body must close that
# issue so the board automation can advance the card.
#
# Reads the hook JSON on stdin; emits additionalContext ONLY for matching
# commands and is a silent no-op for every other Bash call. Never denies a
# tool call -- it informs, it does not gate.

cmd=$(python3 -c 'import json,sys; print(json.load(sys.stdin).get("tool_input",{}).get("command",""))' 2>/dev/null)

case "$cmd" in
  *"git switch -c "*|*"git checkout -b "*|*"gh pr create"*)
    python3 - <<'PY'
import json

msg = (
    "Board workflow: this change MUST trace to a GitHub issue. If no issue "
    "exists yet, create one NOW -- use the feature-request skill, or "
    "`gh issue create` with the right label and milestone -- before the branch "
    "or PR. The PR body must contain \"Closes #<n>\" so the board automation "
    "advances the card (Backlog -> In Progress -> Verify) and the issue closes "
    "on merge. Do not open a feature PR without a linked issue."
)

print(json.dumps({
    "hookSpecificOutput": {
        "hookEventName": "PreToolUse",
        "additionalContext": msg,
    }
}))
PY
    ;;
esac

exit 0
