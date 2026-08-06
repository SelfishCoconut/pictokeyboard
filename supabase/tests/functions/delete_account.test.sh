#!/usr/bin/env bash
# What the delete-account function refuses, and what it actually removes (#83).
#
# These are the assertions that cannot be made in SQL: the database tests prove
# that removing an `auth.users` row anonymises correctly, but only a request can
# prove the function will not remove somebody *else's* row. The secret key lives
# in this function and nowhere near the APK, so this endpoint is the one place a
# caregiver's account can be destroyed -- it gets tested like it.
#
# Run against a local stack:  npx supabase start && supabase/tests/functions/delete_account.test.sh
set -uo pipefail

# CI installs the CLI on PATH; locally it arrives through npx.
if command -v supabase >/dev/null 2>&1; then SUPABASE=(supabase); else SUPABASE=(npx --yes supabase); fi
STATUS=$("${SUPABASE[@]}" status -o json 2>/dev/null) || { echo "no local stack -- run 'supabase start'"; exit 1; }
API=$(echo "$STATUS" | python3 -c 'import sys,json;print(json.load(sys.stdin)["API_URL"])')
PUBLISHABLE=$(echo "$STATUS" | python3 -c 'import sys,json;print(json.load(sys.stdin)["PUBLISHABLE_KEY"])')
SECRET=$(echo "$STATUS" | python3 -c 'import sys,json;print(json.load(sys.stdin)["SECRET_KEY"])')
FN="$API/functions/v1/delete-account"

pass=0; fail=0
check() { # check <description> <expected> <actual>
  if [ "$2" = "$3" ]; then echo "ok   - $1"; pass=$((pass+1));
  else echo "FAIL - $1 (expected '$2', got '$3')"; fail=$((fail+1)); fi
}

# A user the test owns, plus a bystander it must not be able to touch.
mkuser() { # mkuser <email> -> id
  curl -s -X POST "$API/auth/v1/admin/users" \
    -H "apikey: $SECRET" -H "Authorization: Bearer $SECRET" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"password\":\"correct-horse-battery\",\"email_confirm\":true}" |
    python3 -c 'import sys,json;print(json.load(sys.stdin).get("id",""))'
}

token_for() { # token_for <email> -> access token
  curl -s -X POST "$API/auth/v1/token?grant_type=password" \
    -H "apikey: $PUBLISHABLE" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"password\":\"correct-horse-battery\"}" |
    python3 -c 'import sys,json;print(json.load(sys.stdin).get("access_token",""))'
}

user_exists() { # user_exists <id> -> yes|no
  local code
  code=$(curl -s -o /dev/null -w '%{http_code}' "$API/auth/v1/admin/users/$1" \
    -H "apikey: $SECRET" -H "Authorization: Bearer $SECRET")
  [ "$code" = "200" ] && echo yes || echo no
}

stamp=$(date +%s)
victim_email="victim-$stamp@example.test"
caller_email="caller-$stamp@example.test"
VICTIM=$(mkuser "$victim_email")
CALLER=$(mkuser "$caller_email")
[ -z "$VICTIM" ] || [ -z "$CALLER" ] && { echo "could not seed users"; exit 1; }
TOKEN=$(token_for "$caller_email")
[ -z "$TOKEN" ] && { echo "could not sign in as the caller"; exit 1; }

# --- Refusals ---------------------------------------------------------------

code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$FN" -H "apikey: $PUBLISHABLE")
check "no JWT is refused" "401" "$code"

code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$FN" \
  -H "apikey: $PUBLISHABLE" -H "Authorization: Bearer not.a.jwt")
check "a malformed JWT is refused" "401" "$code"

# A structurally valid token signed with the wrong key -- the shape a stale or
# forged token actually arrives in.
STALE='eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEiLCJyb2xlIjoiYXV0aGVudGljYXRlZCIsImV4cCI6MTAwMDAwMDAwMH0.Zm9yZ2Vk'
code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$FN" \
  -H "apikey: $PUBLISHABLE" -H "Authorization: Bearer $STALE")
check "a stale or forged JWT is refused" "401" "$code"

# --- CORS, because one of the two callers is a browser -----------------------
#
# The web deletion page calls this function from a different origin, so the
# browser sends a preflight first and refuses to make the real request unless it
# succeeds. `withSupabase` is documented to handle CORS; this asserts it, so an
# upgrade that changes that is caught here rather than by a caregiver meeting a
# page that does nothing.
#
# The failure this prevents is a quiet one: the request never leaves the
# browser, the page shows no error the user can act on, and the only deletion
# route that works without the app is the one that has broken.
ORIGIN='https://selfishcoconut.github.io'

headers_of() { # headers_of <curl args...> -> response headers
  curl -s -D - -o /dev/null "$@"
}

header_value() { # header_value <headers> <name> -> value, or empty
  printf '%s\n' "$1" | tr -d '\r' | grep -i "^$2:" | head -1 | cut -d' ' -f2-
}

preflight=$(headers_of -X OPTIONS "$FN" \
  -H "Origin: $ORIGIN" \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: authorization, content-type, apikey')
preflight_code=$(printf '%s\n' "$preflight" | head -1 | awk '{print $2}')
case "$preflight_code" in 200|204) preflight_ok=yes ;; *) preflight_ok="no ($preflight_code)" ;; esac
check "the CORS preflight is answered" "yes" "$preflight_ok"
check "the preflight allows the calling origin" "yes" \
  "$([ -n "$(header_value "$preflight" 'access-control-allow-origin')" ] && echo yes || echo no)"
check "the preflight allows the authorization header" "yes" \
  "$(printf '%s' "$(header_value "$preflight" 'access-control-allow-headers')" |
     grep -qi 'authorization\|\*' && echo yes || echo no)"

# A browser cannot read *any* response -- including an error -- without the
# allow-origin header on the real response too. Checked on the 401 because that
# is the response the page has to be able to show a message for.
refusal=$(headers_of -X POST "$FN" -H "apikey: $PUBLISHABLE" -H "Origin: $ORIGIN")
check "a refusal is still readable by the browser" "yes" \
  "$([ -n "$(header_value "$refusal" 'access-control-allow-origin')" ] && echo yes || echo no)"

# --- The one that matters ---------------------------------------------------

# A valid caller naming somebody else in the body. The function must delete the
# caller (whom the JWT identifies) and leave the named account alone.
code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$FN" \
  -H "apikey: $PUBLISHABLE" -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"user_id\":\"$VICTIM\",\"userId\":\"$VICTIM\",\"sub\":\"$VICTIM\"}")
check "a signed-in caregiver's own deletion succeeds" "200" "$code"
check "the account named in the body is untouched" "yes" "$(user_exists "$VICTIM")"
check "the account the JWT identifies is gone" "no" "$(user_exists "$CALLER")"

# --- Cleanup ----------------------------------------------------------------

curl -s -o /dev/null -X DELETE "$API/auth/v1/admin/users/$VICTIM" \
  -H "apikey: $SECRET" -H "Authorization: Bearer $SECRET"

echo "---"
echo "$pass passed, $fail failed"
[ "$fail" -eq 0 ]
