#!/usr/bin/env bash
#
# Verify every module the published pages import at runtime.
#
# site/ is static HTML with no build step, so its imports are resolved by the
# browser, from a CDN, at the moment a caregiver opens the page. Nothing else in
# CI would notice a specifier that 404s -- the page would simply do nothing, on
# the one route Play requires to work, for whoever is trying to delete their
# account.
#
# The point of this script is the distinction the naive check misses. Two very
# different failures used to wear the same red X:
#
#   404, or the module is missing an export -> the page is broken for real
#                                              users, right now. Fail at once.
#   522 / 503 / timeout / DNS               -> the CDN is having a moment. It
#                                              says nothing about this commit.
#
# Retrying the second is correct. Retrying the first would only delay a genuine
# breakage by the length of the backoff, so a 4xx exits immediately -- and the
# message says which happened, so nobody re-runs a real failure or opens an
# investigation into a blip.
set -euo pipefail

ATTEMPTS=${ATTEMPTS:-4}
BACKOFF=${BACKOFF:-5}
# An argument only so the failure paths can be exercised against a fixture. A
# check whose own error handling has never been run is a check nobody has tested.
ROOT=${1:-site}

# Read the specifiers out of the pages rather than listing them here. A copy in
# this script is a copy that goes stale, and it would go stale silently in the
# direction that matters: the page upgraded, the check still green on the old URL.
mapfile -t specifiers < <(grep -rhoE "https://esm\.sh/[^'\"]+" "$ROOT" | sort -u)

if [ ${#specifiers[@]} -eq 0 ]; then
  echo "No remote imports found in $ROOT -- nothing to check."
  exit 0
fi

echo "Checking ${#specifiers[@]} remote import(s) from $ROOT:"

failed=0
for url in "${specifiers[@]}"; do
  attempt=1
  while :; do
    # --location: esm.sh redirects to the versioned build.
    # --max-time: a hung connection is a transient failure, not a wait forever.
    code=$(curl -sS -o /dev/null -L --max-time 30 -w '%{http_code}' "$url" || echo 000)

    case "$code" in
      2*)
        echo "  ok    $url ($code)"
        break
        ;;
      4*)
        # A wrong version or a removed package. No amount of waiting fixes it.
        echo "  BROKEN $url -> HTTP $code"
        echo "         The page imports a module that does not exist. This is a"
        echo "         real failure: the deletion page is broken for users now."
        failed=1
        break
        ;;
      *)
        # 5xx, or 000 for a network/DNS/timeout failure.
        if [ "$attempt" -ge "$ATTEMPTS" ]; then
          echo "  UNREACHABLE $url -> HTTP $code after $ATTEMPTS attempts"
          echo "         The CDN did not answer. This is not about this commit,"
          echo "         but the page cannot load its module while it lasts."
          failed=1
          break
        fi
        echo "  retry $url -> HTTP $code (attempt $attempt/$ATTEMPTS)"
        sleep $((BACKOFF * attempt))
        attempt=$((attempt + 1))
        ;;
    esac
  done
done

[ "$failed" -eq 0 ] || exit 1

# Reachable is not the same as usable: esm.sh answers 200 for a URL whose module
# exports nothing we call. The page calls exactly one thing.
if command -v deno >/dev/null 2>&1; then
  for url in "${specifiers[@]}"; do
    case "$url" in
      *supabase-js*)
        deno eval "
          import { createClient } from '$url'
          if (typeof createClient !== 'function') {
            console.error('  BROKEN $url exports no createClient')
            Deno.exit(1)
          }
          console.log('  ok    $url exports createClient')
        "
        ;;
    esac
  done
fi

echo "Every remote import the pages use resolves."
