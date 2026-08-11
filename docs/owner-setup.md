# Owner setup — the steps only you can do

Everything here needs access I do not have: repository settings and the Play
Console. Nothing in the codebase can substitute for them, and several fail
*quietly* if skipped — so each step says where to click, and how to tell it
actually worked rather than only looked like it did.

## Where this stands

| # | Step | State |
|---|---|---|
| 1 | GitHub Pages set to *GitHub Actions* | **done** |
| 2 | `database` and `functions` removed from the required checks | **yours — do this once** |
| 3 | Play Console answers | **yours** — at listing time |
| 4 | Release signing key registered | **yours** — before the first upload |

The site is published and both pages load:

- <https://selfishcoconut.github.io/pictokeyboard/privacy/>
- <https://selfishcoconut.github.io/pictokeyboard/es/privacidad/>

> **What used to be here.** Steps 5 to 7 were the Supabase project, Google
> sign-in for the web deletion page, custom SMTP and the Magic Link template.
> None of them exist on `main` any more (#119): there is no server to configure.
> The version of this document that describes them is on the **`marketplace`**
> branch, along with the code it was describing, and it is accurate there.

---

## 2. Remove `database` and `functions` from the required checks

**Do this first, before merging anything else**, or `main` becomes unmergeable.

Those two jobs ran the Supabase migrations and the account-deletion Edge
Function. Both are gone, so neither will ever report a result again — and a
required status check that never reports does not fail the branch protection, it
*blocks* it. Every pull request from here on would sit at "Expected — Waiting for
status to be reported" forever, with no way to merge and nothing red to fix.

**Settings ▸ Branches ▸ `main` ▸ Require status checks to pass**, then untick:

- `database`
- `functions`

Nine checks should remain: `quality`, `build`, `unit`, `compliance`, `codeql`,
`secret scan`, `dependency audit`, `emulator (API 26)`, `emulator (API 35)`.

**How to tell it worked:** open any pull request and confirm the checks list
shows nine entries and no "Expected" rows. If two rows sit at *Expected* and
never move, this step has not been done.

```sh
# Or from the command line, which is faster and shows the real state:
gh api repos/SelfishCoconut/pictokeyboard/branches/main/protection \
  --jq '.required_status_checks.contexts'
```

---

## 3. Play Console — at listing time

The answers are worked out in [`play-data-safety.md`](./play-data-safety.md),
derived from the code rather than from memory, with the commands that produce
them.

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
