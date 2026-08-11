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
| 4 | Release signing key registered | **yours** — before the first upload |
| 5 | Play Console account, and the closed test it requires | **yours** — start this first, it takes weeks |

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

## 4. Also before any public release

- **Release signing.** The upload key is supplied to CI through `KEYSTORE_FILE`,
  `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`. Without them the release
  build is left *unsigned* rather than falling back to the debug key, and
  `release.yml`'s apksigner step is what catches that before the upload spends a
  `versionCode` permanently.

  ```sh
  keytool -list -v -keystore <release.jks> -alias <alias> | grep SHA1
  ```

- **Update the privacy policy when sentence help ships (#46).** The model runs
  on the device and nothing is sent anywhere, but the policy currently does not
  mention a model existing at all. One paragraph, both languages, same release.

---

## 5. The Play Console account — the long pole

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
