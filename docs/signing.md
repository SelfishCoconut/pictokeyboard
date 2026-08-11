# Signing — the runbook

Written to be followed alone. Every command here runs on your machine, reads
nothing back to anybody, and can be re-run from scratch. Nothing in this
document requires an agent, an editor, or a network connection except the two
steps that talk to GitHub and Play, which say so.

If you only want the short version:

```sh
./scripts/make-upload-key.sh --rotate --prompt --push   # start again, my password, tell CI
./scripts/sign-release.sh                               # prove it works
```

---

## The two keys

Play App Signing is mandatory for any app new to the store, so this is not a
choice to weigh. What it means in practice is that **there are two keys and only
one of them is yours**.

| | Upload key | App signing key |
|---|---|---|
| Signs | what you upload | what users install |
| Held by | you | Google |
| If lost | reset it from the Console, keep publishing | **unrecoverable** |

Play checks your upload signature, strips it, and re-signs with the key it
holds. That is the entire point of the arrangement: the key that can never be
replaced is one you never have, and therefore cannot lose.

So the file this runbook produces is *recoverable*. Treat it carefully, but not
fearfully — the reset is a support request and a wait, not the end of the app.

---

## 1. Mint the key

```sh
./scripts/make-upload-key.sh
```

RSA 4096, valid for 10 000 days. Play refuses an upload certificate that expires
before 2033-10-22; this one runs to the 2050s, so the key outlives the app rather
than the other way round.

It writes two files to `~/.pictokeyboard/`, both mode 600:

| File | What it is |
|---|---|
| `upload.jks` | the keystore, alias `upload` |
| `upload-key.password` | a generated password, 33 random bytes |

**If you would rather choose the password yourself** and keep it in a password
manager, use `--prompt`. Nothing is then written to disk but the keystore, and
every later command asks you for it.

```sh
./scripts/make-upload-key.sh --prompt
```

Either way the password never becomes a command-line argument — `keytool` is
given `-storepass:env` or `-storepass:file`, because anything in `argv` can be
read out of `/proc` by any other process on the machine for as long as the
command runs.

### Back it up

Copy `~/.pictokeyboard/` somewhere that is not this machine and not this
repository. `.gitignore` already refuses `*.jks`, `*.keystore` and
`keystore.properties`, so an accidental `git add -A` will not commit it — but
that only protects the repository, not you.

---

## 2. Replacing a key you already made

```sh
./scripts/make-upload-key.sh --rotate
```

The script never overwrites a keystore silently, and asks one question before it
proceeds, because the answer changes everything:

- **Play has never accepted an upload signed by it** — rotating is free. The old
  key is *moved* into `~/.pictokeyboard/archive/`, not deleted.
- **Play has accepted one** — stop. Play binds the app to the first upload key it
  sees and rejects everything signed by anything else. The way back is
  *Console ▸ Setup ▸ App signing ▸ Request upload key reset*, which is a support
  request and a wait.

Rotating changes the fingerprint, so CI's pin has to move with it — `--push`
does both in one go (step 3). If you forget, the next release build fails with
*"signed by the wrong key"*, which is the failure you want: loud, and before
anything is uploaded.

---

## 3. Give the key to CI

```sh
./scripts/make-upload-key.sh --push-only
```

Needs `gh` logged in. It sets four secrets and one variable:

| Name | | |
|---|---|---|
| `KEYSTORE_BASE64` | secret | the keystore itself |
| `KEYSTORE_PASSWORD` | secret | |
| `KEY_PASSWORD` | secret | same value; keytool keeps them separable, we do not |
| `KEY_ALIAS` | secret | `upload` |
| `UPLOAD_KEY_SHA256` | variable | the pin. A certificate fingerprint is public by design |

**`KEYSTORE_FILE` is not a secret.** `release.yml` decodes `KEYSTORE_BASE64` to a
temporary file and passes *that path* as `KEYSTORE_FILE`, which is the name
`build.gradle.kts` reads. Setting a secret by that name does nothing, and the
build comes out unsigned.

Check what landed:

```sh
gh secret list && gh variable list
```

---

## 4. Build a signed release yourself

```sh
./scripts/sign-release.sh
```

Builds the AAB and the APK, then verifies both: `apksigner` for the APK, and a
certificate-fingerprint comparison for the AAB.

The AAB is checked by fingerprint rather than with `jarsigner -verify` because
every Android signing certificate is self-signed — jarsigner reports *"signer
errors"* and a non-zero status for a perfectly good bundle. The useful question
is not whether the certificate chains to a certificate authority, which it never
will, but whether it is the certificate you meant.

To check a build you already have, without rebuilding:

```sh
./scripts/sign-release.sh --verify
```

Set `VERSION_CODE` for anything you intend to upload. **Play burns a
`versionCode` permanently on upload, including on an upload it then rejects**, so
never reuse one. CI passes the workflow run number, which only goes up.

---

## 5. The first upload, which is also enrolment

There is no separate "enrol in Play App Signing" button. It happens as a side
effect of the first upload:

1. Create the app in the Console and start a release on the closed-test track.
2. Under **App signing**, change nothing. The default — Google generates and
   holds the app signing key — is what you want.
3. Upload the AAB.

Enrolment is complete at that moment. Afterwards the Console shows both
certificates; the upload one must match what `--show` prints:

```sh
./scripts/make-upload-key.sh --show
```

> **If you want the same key across multiple stores** — F-Droid, direct download
> — that is the one decision that must be made *before* this upload, because it
> is the only moment Play offers *Change app signing key ▸ export and upload*.
> Choosing it means the unrecoverable key becomes yours to lose. See
> [`owner-setup.md`](./owner-setup.md) §4, which lays out what that costs against
> the alternatives.

---

## What CI does with all this

`release.yml`, on a tag:

1. Fails immediately if any of the four secrets is missing. It used to build
   unsigned instead, and the verification step was conditional on the same
   secret — so a missing secret skipped its own safety net and published an
   unsigned AAB with nothing red anywhere.
2. Decodes the keystore, builds, and signs.
3. Verifies the APK *and* the AAB, and fails if the AAB's fingerprint is not the
   pinned one.
4. Publishes to a GitHub Release only if all of that held.

`workflow_dispatch` stays permissive on purpose: rehearsing the build before the
secrets exist is useful, and it publishes nothing.
