# Owner setup — the steps only you can do

Everything here needs access I do not have: repository settings and the Play
Console. Nothing in the codebase can substitute for them, and several fail
*quietly* if skipped — so each step says where to click, and how to tell it
actually worked rather than only looked like it did.

## Where this stands

| # | Step | State |
|---|---|---|
| 1 | GitHub Pages set to *GitHub Actions* | **done** |
| 2 | `database` and `functions` removed from the required checks | **done** (2026-08-11) |
| 3 | Play Console answers | **yours** — at listing time |
| 4 | Upload key, and the secrets CI reads | **yours** — two commands, [`signing.md`](./signing.md) |
| 5 | Privacy policy, for the model and the bell | **done** (#152) |
| 6 | Play Console account, and the closed test it requires | **yours** — start this first, it takes weeks |

The site is published and both pages load:

- <https://selfishcoconut.github.io/pictokeyboard/privacy/>
- <https://selfishcoconut.github.io/pictokeyboard/es/privacidad/>

> **What used to be here.** Steps 5 to 7 were the Supabase project, Google
> sign-in for the web deletion page, custom SMTP and the Magic Link template.
> None of them exist on `main` any more (#119): there is no server to configure.
> The version of this document that describes them is on the **`marketplace`**
> branch, along with the code it was describing, and it is accurate there.

---

## 2. Remove `database` and `functions` from the required checks — done

Kept here because the *shape* of the problem recurs: **a required status check is
named after its job, so renaming or removing a job in a workflow strands the
check**, and a required check that never reports blocks every pull request
forever with nothing red to fix. `instrumented.yml` carries the same warning
next to its API-level matrix for that reason.

The rule, whenever a required job changes name: **untick it in Settings first,
then change the workflow.** In that order.

Nine checks are required now: `quality`, `build`, `unit`, `compliance`,
`codeql`, `secret scan`, `dependency audit`, `emulator (API 26)`,
`emulator (API 35)`.

```sh
# What is actually required, which is the only answer that counts:
gh api repos/SelfishCoconut/pictokeyboard/branches/main/protection \
  --jq '.required_status_checks.contexts'
```

`emulator (API 36)` now runs too and is deliberately **not** required, so that
API 35 can be retired from the matrix and from this list together, in that
order, whenever you want to.

---

## 3. Play Console — at listing time

The answers are worked out in [`play-data-safety.md`](./play-data-safety.md),
derived from the code rather than from memory, with the commands that produce
them. Every word of the listing itself — both languages, the categorisation, the
content-rating and target-audience answers — is written out in
[`play-listing.md`](./play-listing.md), ready to paste.

**The URLs Play asks for:**

| Field | Value |
|---|---|
| Privacy policy URL | `https://selfishcoconut.github.io/pictokeyboard/privacy/` |
| Account deletion URL | *leave empty — the app has no accounts* |

Play only requires a deletion URL from apps that let users create an account.
This one does not, so the field stays empty. If it is filled in anyway with a
page that does not offer deletion, Play checks it and rejects the listing.

If you add a Spanish store listing, use `…/es/privacidad/`. It is linked from
the English page and declared with `hreflang`, so a Spanish-speaking caregiver is
never stranded on an English page.

**Data safety form**, in short: nothing is collected and nothing is shared. The
app has no server. Read the table in `play-data-safety.md` before answering, and
re-run the checks in it — the form must agree with the binary, and Play scans
for disagreement automatically.

**Expect the strict review tier.** An IME can observe what is typed, so be ready
to answer *"what can this keyboard see?"* in writing. The answer, and the place
in the code where it is enforced, is in `play-data-safety.md`.

---

## 4. Play App Signing — the upload key

Play App Signing is **mandatory** for any app new to the store, so this is not a
choice to weigh, only a thing to do correctly once. There is no separate
"enrol" button: enrolment happens as a side effect of the first upload, and the
only decision on that screen is which key becomes the app signing key.

**Two keys, and the difference matters.**

| | Upload key | App signing key |
|---|---|---|
| Signs | what you upload | what users install |
| Held by | you | Google |
| If lost | request a reset in the Console, keep publishing | **unrecoverable** |

Play checks the upload signature, strips it, and re-signs with the app signing
key it holds. That is the whole point of the scheme: the key that can never be
replaced is one you cannot lose, because you never have it.

### Doing it

Step by step, with everything it can go wrong on:
**[`signing.md`](./signing.md)** — a runbook meant to be followed alone.

```sh
./scripts/make-upload-key.sh --rotate --prompt --push   # mint it, your password, tell CI
./scripts/sign-release.sh                               # prove it signs
```

A key minted earlier is on this machine at `~/.pictokeyboard/upload.jks`. It was
made during an agent session, so `--rotate` above replaces it with one that was
not; the old one is archived rather than deleted, and doing this costs nothing
until Play has accepted an upload. After that it stops being a script and
becomes a support request — `signing.md` §2 is the one to read before rotating
anything.

Fingerprints are deliberately not written down here. They change when the key
changes, and a document with a stale fingerprint in it is worse than one with
none. Ask the key:

```sh
./scripts/make-upload-key.sh --show
```

### Then, in the Console

Create the app, start a release on the closed-test track, and under **App
signing** change nothing — the default is Google generating and holding the app
signing key, which is what you want. Upload the AAB. Enrolment is complete at
that moment; there is no separate button for it.

Afterwards the Console shows both certificates back. The upload one must match
what `--show` prints, and that fingerprint is also pinned in CI as the
repository variable `UPLOAD_KEY_SHA256` — so a build signed by the wrong key
fails before it can spend a `versionCode`.

### One decision this repository forces

`release.yml` publishes an **APK to GitHub Releases** as well as the AAB to Play.
Under the default arrangement those two are signed by *different* certificates —
the GitHub APK by your upload key, the Play build by Google's app signing key.
Android refuses to update an installed app across a certificate change, so
somebody who sideloads from GitHub cannot later move to the Play version without
uninstalling first, which erases their boards.

Three ways out, none of them free:

- **Accept it**, and say so plainly on the Release page: sideload *or* Play, pick
  one. Cheapest, and reasonable while the GitHub APK is mainly for testers.
- **Provide your own app signing key** (Console ▸ *Change app signing key* ▸
  export and upload) so both are signed identically. This gives up exactly the
  protection Play App Signing exists to provide — the unrecoverable key becomes
  yours to lose.
- **Stop publishing APKs** once Play is live, and point the Release page at the
  store.

This does not block enrolment, and the first upload does not settle it, so it can
wait — but not past the first person who sideloads.

## 5. Also before any public release

- **~~Update the privacy policy when sentence help ships (#46).~~** Done in
  #152, in both languages: the model, the 347 MB download from Hugging Face,
  and `CALL_PHONE` for the bell.

  **One thing here is still yours.** `CALL_PHONE` may require a declaration
  form in the Play Console — it is on Google's sensitive list, and the app's
  justification is short and true: one key, pressed by a person who cannot
  speak, dials a number their own caregiver typed into the app. Nothing reads
  the call log, nothing places a call the user did not press. Check whether the
  console asks for it at listing time; `docs/play-data-safety.md` treats it as
  blocking until you confirm either way.

- **Stale secrets.** `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` are still set
  on the repository. Nothing on `main` reads them since #119. They are still
  live for the **`marketplace`** branch, so this is a note rather than an
  instruction — delete them only if that branch is abandoned.

---

## 6. The Play Console account — the long pole

**Start this before anything else on this page.** Everything else here is an
afternoon; this one is measured in weeks, and no amount of finished code shortens
it.

A developer account is a one-off 25 USD registration. The part that costs time is
what comes after it: an account registered as an **individual** (rather than an
organisation) must run a **closed test with at least 12 testers who stay opted in
for 14 continuous days** before it may even apply for production access. The
14 days restart if the tester count drops below 12, so recruit more than twelve.

Two consequences worth planning around:

- **Twelve real Google accounts.** Family, colleagues, the speech therapists this
  app was built with. An emulator does not count and neither does a second
  account of your own.
- **The clock cannot start until there is a signed build to test**, which means
  §4's signing key is the real prerequisite, not the store listing text.

Registering as an organisation avoids the 12-tester requirement but needs a
D-U-N-S number and takes its own time to verify. For one person shipping one
app, the closed test is usually the shorter road — it just has to be started
early.
