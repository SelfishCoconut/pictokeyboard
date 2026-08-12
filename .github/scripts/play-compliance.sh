#!/usr/bin/env bash
# Play Store configuration compliance checks.
#
# Static checks only -- they read build config and the manifest. They catch the
# rejections that come from configuration, which is the majority of them. They
# do NOT verify Data Safety accuracy, privacy policy content, or store listing
# completeness; those are human judgement (see the play-release skill).
#
# Env:
#   MODULE                  app module directory (default: app)
#   PLAY_TARGET_SDK_FLOOR   minimum targetSdk Play currently accepts

set -uo pipefail

MODULE="${MODULE:-app}"
FLOOR="${PLAY_TARGET_SDK_FLOOR:-35}"
GRADLE="$MODULE/build.gradle.kts"
MANIFEST="$MODULE/src/main/AndroidManifest.xml"

fail=0
warn=0

err()  { echo "::error::$*";   fail=1; }
warning() { echo "::warning::$*"; warn=1; }
ok()   { echo "  ok: $*"; }

[ -f "$GRADLE" ]   || { err "No build file at $GRADLE"; exit 1; }
[ -f "$MANIFEST" ] || { err "No manifest at $MANIFEST"; exit 1; }

echo "== Play Store compliance =="

# --- targetSdk floor -------------------------------------------------------
target_sdk=$(grep -oP 'targetSdk\s*=\s*\K[0-9]+' "$GRADLE" | head -1)
if [ -z "$target_sdk" ]; then
  warning "Could not determine targetSdk from $GRADLE -- check it manually."
elif [ "$target_sdk" -lt "$FLOOR" ]; then
  err "targetSdk is $target_sdk, below Play's required floor of $FLOOR. Uploads will be rejected."
else
  ok "targetSdk $target_sdk meets the floor of $FLOOR"
fi

# --- R8 / minification -----------------------------------------------------
# Look inside the release block only; a global grep gives false results.
release_block=$(awk '/release[[:space:]]*\{/,/^[[:space:]]*\}/' "$GRADLE")
if echo "$release_block" | grep -q 'isMinifyEnabled\s*=\s*true'; then
  ok "R8 enabled for release"
  echo "$release_block" | grep -q 'isShrinkResources\s*=\s*true' \
    || warning "isShrinkResources is not enabled; the bundle will be larger than necessary."
else
  warning "isMinifyEnabled is false for release. Not a rejection, but ships unobfuscated and oversized. If you enable it, test the release build on a device first -- Room/Moshi/Retrofit break silently without keep rules."
fi

# --- signing ---------------------------------------------------------------
if grep -q 'signingConfig' "$GRADLE"; then
  grep -q 'signingConfigs\.getByName("debug")' "$GRADLE" \
    && err "Release appears to use the debug signing config. Play rejects debug-signed artifacts."
  ok "a release signingConfig is present"
else
  err "No signingConfig found. A release build must be signed with the upload key."
fi

# --- versionCode -----------------------------------------------------------
if grep -qP 'versionCode\s*=\s*[0-9]+\s*$' "$GRADLE"; then
  vc=$(grep -oP 'versionCode\s*=\s*\K[0-9]+' "$GRADLE" | head -1)
  warning "versionCode is hardcoded to $vc. Play permanently rejects a re-used versionCode -- derive it from the CI run number or the tag instead."
else
  ok "versionCode is computed, not hardcoded"
fi

# --- manifest flags --------------------------------------------------------
grep -q 'android:debuggable="true"' "$MANIFEST" \
  && err "android:debuggable=\"true\" in the manifest. Play rejects debuggable release builds."

grep -q 'usesCleartextTraffic="true"' "$MANIFEST" \
  && warning "usesCleartextTraffic=\"true\" permits unencrypted HTTP. Justify it or remove it."

# --- backup ----------------------------------------------------------------
# Auto Backup is on by default and its default rules include filesDir and the
# databases directory, so an app that says nothing here ships every board, every
# photograph and the usage tally to the user's Drive without ever mentioning it.
# That is not a Play rejection by itself -- it is a rejection when the privacy
# policy or the listing says the data stays on the device, which is the whole
# reason this check exists.
if grep -q 'android:allowBackup="false"' "$MANIFEST"; then
  ok "backup is off entirely"
elif grep -q 'android:dataExtractionRules=' "$MANIFEST" \
  && grep -q 'android:fullBackupContent=' "$MANIFEST"; then
  ok "backup is governed by explicit rules for both API ranges"
else
  err "Auto Backup has no rules. Set android:dataExtractionRules (API 31+) AND android:fullBackupContent (26-30), or android:allowBackup=\"false\". Default rules upload filesDir and every database to the user's Drive -- which contradicts a privacy policy that says otherwise."
fi

# --- permissions -----------------------------------------------------------
echo "-- declared permissions (each must be justified in Data Safety):"
perms=$(grep -oP '(?<=uses-permission android:name=")[^"]+' "$MANIFEST" || true)
if [ -z "$perms" ]; then
  ok "no permissions declared"
else
  echo "$perms" | sed 's/^/     /'
  # CALL_PHONE is not on Play's restricted list and needs no declaration form,
  # but it is the permission a keyboard is least expected to hold, so a reviewer
  # will ask about it directly. The warning exists to make somebody answer that
  # question before the upload rather than during the review.
  for dangerous in RECORD_AUDIO READ_CONTACTS READ_SMS RECEIVE_SMS ACCESS_FINE_LOCATION \
                   READ_EXTERNAL_STORAGE QUERY_ALL_PACKAGES REQUEST_INSTALL_PACKAGES \
                   CALL_PHONE; do
    echo "$perms" | grep -q "$dangerous" \
      && warning "Sensitive permission $dangerous needs a written justification in Data Safety, and may need an in-console declaration -- check the current restricted-permissions list before uploading."
  done
fi

# --- privacy policy --------------------------------------------------------
if ! find . -iname "PRIVACY*" -not -path "./.git/*" | grep -q .; then
  warning "No privacy policy document found in the repo. Play requires a policy at a public URL for every app."
fi

echo
if [ "$fail" -ne 0 ]; then
  echo "FAILED: blocking Play compliance problems above."
  exit 1
fi
[ "$warn" -ne 0 ] && echo "Passed with warnings -- review them before uploading."
echo "No blocking Play configuration problems found."
echo "Remember: Data Safety accuracy, privacy policy content and listing assets are NOT checked here."
exit 0
