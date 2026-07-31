#!/bin/sh
# PostToolUse hook (Edit|Write): warn when localized strings.xml files drift.
#
# Fires only when the edited file is a res/values*/strings.xml. Compares the
# string/plurals/string-array keys in the default locale (res/values/strings.xml)
# against every other res/values-*/strings.xml, and reports keys that are
# missing or orphaned.
#
# Emits additionalContext so the model sees the drift immediately, in the same
# turn it made the edit. Never blocks: a missing translation is worth knowing
# about, not worth failing an edit over.

file_path=$(python3 -c 'import json,sys; print(json.load(sys.stdin).get("tool_input",{}).get("file_path",""))' 2>/dev/null)

case "$file_path" in
  */res/values/strings.xml|*/res/values-*/strings.xml) ;;
  *) exit 0 ;;
esac

python3 - "$file_path" <<'PY'
import json
import pathlib
import re
import sys

edited = pathlib.Path(sys.argv[1])

# .../res/values-es/strings.xml -> .../res
res_dir = edited.parent.parent
default = res_dir / "values" / "strings.xml"
if not default.is_file():
    sys.exit(0)

TAG = re.compile(r"<(?:string|plurals|string-array)\b([^>]*?)/?>")
NAME = re.compile(r'\bname="([^"]+)"')
UNTRANSLATABLE = re.compile(r'\btranslatable="false"')


def keys(path):
    """Resource keys in `path`, excluding those marked translatable="false".

    Strings that must not be translated (glyph literals, brand names, licence
    credits, format patterns) are legitimately absent from every other locale.
    Counting them as drift would make this hook cry wolf on every edit.
    """
    try:
        text = path.read_text(encoding="utf-8")
    except OSError:
        return set()
    found = set()
    for attrs in TAG.findall(text):
        if UNTRANSLATABLE.search(attrs):
            continue
        name = NAME.search(attrs)
        if name:
            found.add(name.group(1))
    return found


base = keys(default)
if not base:
    sys.exit(0)

problems = []
for locale_dir in sorted(res_dir.glob("values-*")):
    strings = locale_dir / "strings.xml"
    if not strings.is_file():
        continue
    have = keys(strings)
    missing = sorted(base - have)
    orphan = sorted(have - base)
    if missing or orphan:
        parts = []
        if missing:
            shown = ", ".join(missing[:8]) + ("..." if len(missing) > 8 else "")
            parts.append(f"missing {len(missing)} ({shown})")
        if orphan:
            shown = ", ".join(orphan[:8]) + ("..." if len(orphan) > 8 else "")
            parts.append(f"{len(orphan)} not in default locale ({shown})")
        problems.append(f"  {locale_dir.name}: " + "; ".join(parts))

if not problems:
    sys.exit(0)

msg = (
    "String resource drift detected after this edit:\n"
    + "\n".join(problems)
    + "\n\nA string present in one locale and absent in another is a half-shipped "
    "feature: users of the other locale see the raw key or the default-language "
    "text. Add the missing keys now (translate, or add with a clear TODO), and "
    "delete keys that no longer exist in the default locale."
)

print(json.dumps({
    "hookSpecificOutput": {
        "hookEventName": "PostToolUse",
        "additionalContext": msg,
    }
}))
PY

exit 0
