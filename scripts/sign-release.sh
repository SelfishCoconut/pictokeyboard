#!/usr/bin/env bash
# Build a signed release AAB and APK on this machine, and verify what came out.
#
# CI does this on a tag. This is the same thing by hand, for when you want to
# see a signed artifact before trusting the pipeline with one -- or when you are
# doing the first upload yourself, which is also the moment Play App Signing
# enrolment happens.
#
#   ./scripts/sign-release.sh              build, sign, verify
#   ./scripts/sign-release.sh --verify     verify what is already built
#
# Env:
#   KEY_DIR         where the upload key lives (default ~/.pictokeyboard)
#   VERSION_CODE    defaults to 1. Only matters for something you will upload:
#                   Play burns a versionCode permanently on upload, including on
#                   an upload it then rejects, so never reuse one.
#   ANDROID_HOME    SDK root, for apksigner (default ~/Android/Sdk)
#   GRADLE_WORKERS  parallelism cap (default 4). Lower it if the machine
#                   struggles; an unbounded release build is a lot of cores.

set -euo pipefail

cd "$(dirname "$0")/.."

KEY_DIR="${KEY_DIR:-$HOME/.pictokeyboard}"
KEYSTORE="$KEY_DIR/upload.jks"
PASSFILE="$KEY_DIR/upload-key.password"
ALIAS="upload"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"

verify_only=0
[ "${1:-}" = "--verify" ] && verify_only=1

die() { echo "error: $*" >&2; exit 1; }

[ -f "$KEYSTORE" ] || die "No upload key at $KEYSTORE. Run scripts/make-upload-key.sh first."

if [ -f "$PASSFILE" ]; then
  KEYSTORE_PASSWORD="$(cat "$PASSFILE")"
else
  read -rsp "Keystore password: " KEYSTORE_PASSWORD; echo
fi
export KEYSTORE_PASSWORD

if [ "$verify_only" = 0 ]; then
  # The four names build.gradle.kts reads, which are not the four names the CI
  # secrets go by -- release.yml decodes KEYSTORE_BASE64 to a file and passes
  # its path as KEYSTORE_FILE. Getting this wrong produces an unsigned build
  # rather than an error, which is why it is written down in one place.
  export KEYSTORE_FILE="$KEYSTORE"
  export KEY_PASSWORD="$KEYSTORE_PASSWORD"
  export KEY_ALIAS="$ALIAS"
  export VERSION_CODE="${VERSION_CODE:-1}"

  echo "Building with versionCode $VERSION_CODE..."
  ./gradlew bundleRelease assembleRelease --max-workers="${GRADLE_WORKERS:-4}"
fi

aab=$(ls app/build/outputs/bundle/release/*.aab 2>/dev/null | head -1) \
  || die "No bundle built."
apk=$(ls app/build/outputs/apk/release/*.apk 2>/dev/null | head -1) \
  || die "No APK built."

echo
echo "== Verifying =="

# The APK, with the tool that understands APK signature schemes.
build_tools=$(ls -d "$ANDROID_HOME"/build-tools/* 2>/dev/null | sort -V | tail -1) \
  || die "No build-tools under $ANDROID_HOME."
"$build_tools/apksigner" verify --print-certs "$apk" | sed 's/^/  /'

# The AAB, by fingerprint rather than `jarsigner -verify`. Every Android signing
# certificate is self-signed, so jarsigner reports "signer errors" and a
# non-zero status for a perfectly good bundle. The question worth asking is not
# whether the certificate chains to a CA -- it never will -- but whether it is
# the certificate we meant.
expected=$(keytool -list -v -keystore "$KEYSTORE" -alias "$ALIAS" \
    -storepass:env KEYSTORE_PASSWORD 2>/dev/null \
  | grep -oP 'SHA256:\s*\K[0-9A-F:]+' | head -1 | tr -d ':' | tr '[:upper:]' '[:lower:]')
actual=$(unzip -p "$aab" 'META-INF/*.RSA' \
  | keytool -printcert \
  | grep -oP 'SHA256:\s*\K[0-9A-F:]+' | head -1 | tr -d ':' | tr '[:upper:]' '[:lower:]')

[ -n "$actual" ] || die "$aab carries no signature."
[ "$actual" = "$expected" ] || die "The bundle is signed by a different key than $KEYSTORE.
       bundle: $actual
       key:    $expected"

unset KEYSTORE_PASSWORD KEY_PASSWORD 2>/dev/null || true

cat <<EOF
  AAB signed by the expected key ($actual).

Upload this one:
  $aab

This is what people sideload, and it is signed by the upload key rather than by
the app signing key Play will use, so it is not interchangeable with a Play
install:
  $apk

If this fingerprint is not what CI has pinned, set it:
  gh variable set UPLOAD_KEY_SHA256 --body $actual
EOF
