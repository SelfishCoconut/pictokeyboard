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
