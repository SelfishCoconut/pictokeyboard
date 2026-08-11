#!/usr/bin/env bash
# Mint the Play upload key, and put its four secrets where CI reads them.
#
# Run this yourself. Nothing here needs anyone else present, and a signing key
# is the last thing that should be made by a process you did not watch.
#
# This is the key you sign an upload with. It is NOT the key the app is signed
# with as far as users are concerned -- under Play App Signing that one is
# generated and held by Google and never leaves the Console. The practical
# difference is what happens when one is lost: an upload key can be reset from
# the Console with a support request, the app signing key cannot be replaced at
# all. That asymmetry is why the thing this script produces is recoverable, and
# why rotating it before the first upload costs nothing.
#
#   ./scripts/make-upload-key.sh                 mint it
#   ./scripts/make-upload-key.sh --prompt        ... typing the password instead
#   ./scripts/make-upload-key.sh --rotate        replace a key, archiving the old
#   ./scripts/make-upload-key.sh --push          mint, then set the CI secrets
#   ./scripts/make-upload-key.sh --push-only     secrets from a key already minted
#   ./scripts/make-upload-key.sh --show          fingerprints, and nothing else
#
# --prompt and --rotate combine with the others: `--rotate --prompt --push` is
# the full "start again, my password, tell CI" run.
#
# Env:
#   KEY_DIR   where the key and its password live (default ~/.pictokeyboard)
#   REPO      the repository to set secrets on (default: the current one)

set -euo pipefail

KEY_DIR="${KEY_DIR:-$HOME/.pictokeyboard}"
KEYSTORE="$KEY_DIR/upload.jks"
PASSFILE="$KEY_DIR/upload-key.password"
ALIAS="upload"

mint=1; push=0; rotate=0; prompt=0
for arg in "$@"; do
  case "$arg" in
    --push)      push=1 ;;
    --push-only) push=1; mint=0 ;;
    --show)      mint=0 ;;
    --rotate)    rotate=1 ;;
    --prompt)    prompt=1 ;;
    -h|--help)   sed -n '2,30p' "$0"; exit 0 ;;
    *) echo "unknown argument: $arg" >&2; exit 2 ;;
  esac
done

die() { echo "error: $*" >&2; exit 1; }

# Passed to keytool as -storepass:env, never as -storepass. An argument sits in
# argv, where any other process on the machine can read it out of /proc for as
# long as keytool runs. This variable is exported for the same reason and unset
# as soon as it is not needed.
read_password() {
  if [ "$prompt" = 1 ]; then
    local a b
    read -rsp "Password for the new keystore: " a; echo
    read -rsp "Again: "                        b; echo
    [ "$a" = "$b" ] || die "They do not match."
    [ ${#a} -ge 12 ] || die "Too short. Twelve characters at the very least."
    KEYSTORE_PASSWORD="$a"
  else
    command -v openssl >/dev/null || die "openssl not on PATH (or use --prompt)."
    # Generated rather than chosen, because a password someone can remember is a
    # password someone can guess. Written to a file next to the keystore, mode
    # 600 -- the same posture as the keystore itself, and the one
    # build.gradle.kts already assumes for signing a build by hand.
    KEYSTORE_PASSWORD="$(openssl rand -base64 33)"
  fi
  export KEYSTORE_PASSWORD
}

if [ "$mint" = 1 ]; then
  command -v keytool >/dev/null || die "keytool not on PATH (install a JDK)."

  if [ -e "$KEYSTORE" ]; then
    # Never silently. After Play has accepted one upload, the key that signed it
    # is the only key it will accept from then on, and a second run of this
    # script would otherwise end the account's ability to ship an update.
    [ "$rotate" = 1 ] || die "$KEYSTORE already exists.
       Before your first upload to Play, replacing it is free:  --rotate
       After it, Play will only accept the key it has already seen, and the way
       back is an upload key reset in the Console, not this script."

    cat <<'WARNING'
About to replace the existing upload key.

  Safe   if Play has never accepted an upload signed by it.
  NOT    if it has -- Play will reject everything signed by the new one, and the
         only way forward is Console > Setup > App signing > Request upload key
         reset, which is a support request and a wait.

WARNING
    printf 'Has Play ever accepted an upload signed by this key? [yes/No] '
    read -r answer
    case "$answer" in
      [Nn]|[Nn][Oo]|"") ;;
      *) die "Then do not rotate. Request an upload key reset in the Console instead." ;;
    esac

    stamp="$(date +%Y%m%d-%H%M%S)"
    mkdir -p "$KEY_DIR/archive"
    chmod 700 "$KEY_DIR/archive"
    mv "$KEYSTORE" "$KEY_DIR/archive/upload-$stamp.jks"
    # An if, not `[ -f ] && mv`, whose failure under `set -e` depends on rules
    # nobody should have to recall to read this.
    if [ -f "$PASSFILE" ]; then
      mv "$PASSFILE" "$KEY_DIR/archive/upload-$stamp.password"
    fi
    echo "Old key archived in $KEY_DIR/archive/ -- moved, not deleted."
    echo
  fi

  mkdir -p "$KEY_DIR"
  chmod 700 "$KEY_DIR"
  umask 077
  read_password

  # 10000 days is a little over 27 years. Play requires an upload certificate
  # valid past 2033-10-22 and refuses one that expires sooner; the usual advice
  # is 25 years or more, so that the key outlives the app rather than the other
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

  chmod 600 "$KEYSTORE"
  if [ "$prompt" = 1 ]; then
    rm -f "$PASSFILE"
    stored="not stored -- you typed it, so keep it wherever you keep such things"
  else
    printf '%s' "$KEYSTORE_PASSWORD" > "$PASSFILE"
    chmod 600 "$PASSFILE"
    stored="$PASSFILE"
  fi
  # KEYSTORE_PASSWORD deliberately stays in scope: the fingerprint listing below
  # needs it, and asking for a password twice in the same run is how people
  # start pasting it onto command lines.

  cat <<EOF

Minted $KEYSTORE
  alias:    $ALIAS
  password: $stored

Back it up somewhere that is not this machine and not this repository. Losing it
is survivable -- Play can reset an upload key -- but the reset is a support
request and a wait, so treat the backup as saving yourself a fortnight.
EOF
fi

[ -f "$KEYSTORE" ] || die "No keystore at $KEYSTORE. Run without --push-only or --show first."

# -storepass:file when there is one, an interactive prompt when there is not.
# Either way the password does not become an argument.
#
# Assigns to globals rather than echoing them back, because a prompt inside
# `$(...)` runs in a subshell and the password it reads dies with it -- which
# looks exactly like a wrong password, and took a test to notice.
pass_flag="-storepass:env"; pass_val="KEYSTORE_PASSWORD"
if [ -z "${KEYSTORE_PASSWORD:-}" ]; then
  if [ -f "$PASSFILE" ]; then
    pass_flag="-storepass:file"; pass_val="$PASSFILE"
  else
    read -rsp "Keystore password: " KEYSTORE_PASSWORD; echo
    export KEYSTORE_PASSWORD
  fi
fi

fingerprints=$(keytool -list -v -keystore "$KEYSTORE" -alias "$ALIAS" \
  "$pass_flag" "$pass_val" 2>/dev/null) || die "Wrong password, or no '$ALIAS' alias in $KEYSTORE."

sha256=$(echo "$fingerprints" | grep -oP 'SHA256:\s*\K[0-9A-F:]+' | head -1)

echo
echo "Certificate fingerprints. The Console shows these back to you after the"
echo "first upload, and they must match:"
echo "$fingerprints" | grep -E 'SHA1:|SHA256:|Valid from' | sed 's/^/  /'

if [ "$push" = 1 ]; then
  command -v gh >/dev/null || die "gh not on PATH."
  repo_args=()
  [ -n "${REPO:-}" ] && repo_args=(--repo "$REPO")

  echo
  echo "Setting what release.yml reads:"
  # Piped on stdin, never passed as an argument, for the same /proc reason.
  base64 -w0 < "$KEYSTORE" | gh secret set KEYSTORE_BASE64 "${repo_args[@]}"
  if [ -f "$PASSFILE" ]; then
    gh secret set KEYSTORE_PASSWORD "${repo_args[@]}" < "$PASSFILE"
    gh secret set KEY_PASSWORD      "${repo_args[@]}" < "$PASSFILE"
  else
    printf '%s' "$KEYSTORE_PASSWORD" | gh secret set KEYSTORE_PASSWORD "${repo_args[@]}"
    printf '%s' "$KEYSTORE_PASSWORD" | gh secret set KEY_PASSWORD      "${repo_args[@]}"
  fi
  printf '%s' "$ALIAS" | gh secret set KEY_ALIAS "${repo_args[@]}"

  # Not a secret -- a certificate fingerprint is public by design. It is set
  # here so that a rotation cannot leave CI pinned to the key you just replaced.
  gh variable set UPLOAD_KEY_SHA256 "${repo_args[@]}" \
    --body "$(echo "$sha256" | tr -d ':' | tr '[:upper:]' '[:lower:]')"
  echo "  four secrets and the pinned fingerprint. Done."
fi

unset KEYSTORE_PASSWORD 2>/dev/null || true
