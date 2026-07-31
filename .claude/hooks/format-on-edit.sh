#!/bin/sh
# PostToolUse hook (Edit|Write): auto-format touched Kotlin files.
#
# Reads the hook JSON on stdin and extracts .tool_input.file_path. Only acts on
# .kt/.kts files inside the project, and only if the project actually has a
# formatter configured. Exits 0 unconditionally: formatting is a convenience,
# never a reason to fail a tool call.
#
# Formatter preference:
#   1. ktlint on PATH        -- fastest, no JVM/Gradle startup
#   2. Gradle spotlessApply  -- slower, but works with the project's own config
#
# Gradle is only used when a spotless config is present, because a cold Gradle
# invocation on every edit is too slow to be worth it otherwise.

file_path=$(python3 -c 'import json,sys; print(json.load(sys.stdin).get("tool_input",{}).get("file_path",""))' 2>/dev/null)

case "$file_path" in
  *.kt|*.kts) ;;
  *) exit 0 ;;
esac

[ -f "$file_path" ] || exit 0

project_dir="${CLAUDE_PROJECT_DIR:-.}"
cd "$project_dir" 2>/dev/null || exit 0

if command -v ktlint >/dev/null 2>&1; then
  ktlint --format --log-level=none "$file_path" >/dev/null 2>&1
  exit 0
fi

if [ -f "./gradlew" ] && grep -rqs "spotless" build.gradle.kts build.gradle app/build.gradle.kts 2>/dev/null; then
  # -q keeps the hook silent; the file is rewritten in place.
  ./gradlew -q spotlessApply >/dev/null 2>&1
fi

exit 0
