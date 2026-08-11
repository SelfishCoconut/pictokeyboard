#!/usr/bin/env bash
# Mint the Play upload key, and put its four secrets where CI reads them.
#
# This is the key you sign an upload with. It is NOT the key the app is signed
# with as far as users are concerned -- under Play App Signing that one is
# generated and held by Google and never leaves the Console. The practical
# difference is what happens when one is lost: an upload key can be reset from
# the Console with a support request, the app signing key cannot be replaced at
# all. That asymmetry is why enrolling is worth doing on day one, and why this
# script is allowed to exist: losing what it produces is survivable.
#
# Run once. It refuses to overwrite an existing keystore, because a keystore
# that has already signed an upload is the only thing Play will accept from
# then on, and an accidental second run would otherwise end the account's
# ability to publish.
#
#   ./scripts/make-upload-key.sh              # mint the key
#   ./scripts/make-upload-key.sh --push       # mint it and set the CI secrets
#   ./scripts/make-upload-key.sh --push-only  # secrets from a key already minted
#
# Env:
#   KEY_DIR   where the key and its password live (default ~/.pictokeyboard)
#   REPO      the repository to set secrets on (default: the current one)

set -euo pipefail

KEY_DIR="${KEY_DIR:-$HOME/.pictokeyboard}"
KEYSTORE="$KEY_DIR/upload.jks"
PASSFILE="$KEY_DIR/upload-key.password"
ALIAS="upload"

push=0
mint=1
case "${1:-}" in
  --push)      push=1 ;;
  --push-only) push=1; mint=0 ;;
  "")          ;;
  *) echo "unknown argument: $1" >&2; exit 2 ;;
esac

die() { echo "error: $*" >&2; exit 1; }

if [ "$mint" = 1 ]; then
  # Never clobber. A signing key is not a build output.
  [ -e "$KEYSTORE" ] && die "$KEYSTORE already exists. Refusing to overwrite a signing key.
       If you genuinely want a new one, move the old one aside yourself and be
       certain it has never signed an upload Play has accepted."

  command -v keytool >/dev/null || die "keytool not on PATH (install a JDK)."

  mkdir -p "$KEY_DIR"
  chmod 700 "$KEY_DIR"

  # Generated here rather than chosen, because a key whose password someone can
  # remember is a key whose password someone can guess. It is written to a file
  # next to the keystore, mode 600 -- the same posture as the keystore itself,
  # and the same one build.gradle.kts already assumes for local signing.
  umask 077
  KEYSTORE_PASSWORD="$(openssl rand -base64 33)"
  export KEYSTORE_PASSWORD

  # -storepass:env, not -storepass. The password would otherwise sit in argv,
  # where every other process on the machine can read it out of /proc while
  # keytool runs.
  #
  # 10000 days is a little over 27 years. Play requires an upload certificate
  # valid past 2033-10-22 and refuses one that expires sooner; the usual advice
  # is 25 years or more so that the key outlives the app rather than the other
  # way round.
  #
  # The distinguished name is cosmetic under Play App Signing -- users are shown
  # the certificate Google generates, never this one -- so it identifies the
  # key's job rather than claiming an organisation that does not exist.
  keytool -genkeypair \
    -keystore "$KEYSTORE" \
    -storetype PKCS12 \
    -alias "$ALIAS" \
    -keyalg RSA \
    -keysize 4096 \
    -validity 10000 \
    -dname "CN=PictoKeyboard upload key, O=PictoKeyboard" \
    -storepass:env KEYSTORE_PASSWORD \
    -keypass:env KEYSTORE_PASSWORD

  printf '%s' "$KEYSTORE_PASSWORD" > "$PASSFILE"
  chmod 600 "$PASSFILE" "$KEYSTORE"

  cat <<EOF

Minted $KEYSTORE
  alias:    $ALIAS
  password: $PASSFILE

Back both files up somewhere that is not this machine and not this repository.
If they are lost you can still publish -- Play can reset an upload key -- but it
is a support request and a wait, so treat it as inconvenience insurance rather
than as a reason to be casual.
EOF
fi

[ -f "$KEYSTORE" ] || die "No keystore at $KEYSTORE. Run without --push-only first."
[ -f "$PASSFILE" ] || die "No password file at $PASSFILE."

echo
echo "Certificate fingerprints -- the Console shows these back to you after the"
echo "first upload, and they must match:"
keytool -list -v -keystore "$KEYSTORE" -alias "$ALIAS" \
  -storepass:file "$PASSFILE" 2>/dev/null |
  grep -E 'SHA1:|SHA256:|Valid from' | sed 's/^/  /'

if [ "$push" = 1 ]; then
  command -v gh >/dev/null || die "gh not on PATH."
  repo_args=()
  [ -n "${REPO:-}" ] && repo_args=(--repo "$REPO")

  echo
  echo "Setting the four secrets release.yml reads:"
  # Piped on stdin, never passed as an argument, for the same /proc reason.
  base64 -w0 < "$KEYSTORE" | gh secret set KEYSTORE_BASE64 "${repo_args[@]}"
  gh secret set KEYSTORE_PASSWORD "${repo_args[@]}" < "$PASSFILE"
  gh secret set KEY_PASSWORD      "${repo_args[@]}" < "$PASSFILE"
  printf '%s' "$ALIAS" | gh secret set KEY_ALIAS "${repo_args[@]}"
  echo "  done."
fi
