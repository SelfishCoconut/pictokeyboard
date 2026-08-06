# Owner setup — the steps only you can do

Everything here needs access I do not have: repository settings, the Supabase
dashboard, and the Play Console. Nothing in the codebase can substitute for
them, and several of them fail *quietly* if skipped — so each step says exactly
where to click, and how to tell it actually worked rather than only looked like
it did.

## Where this stands

| # | Step | State |
|---|---|---|
| 1 | GitHub Pages set to *GitHub Actions* | **done** — verified via the API |
| 2 | `SUPABASE_URL` + `SUPABASE_PUBLISHABLE_KEY` secrets | **done** — both present |
| 3 | Add `functions` to the required checks | **yours** — 2 minutes |
| 4 | Magic Link email template on the hosted project | **yours** — 5 minutes, and it fails silently if skipped |
| 5 | Merge #100, then #101 | **yours** — #101 is what publishes the URLs |
| 6 | A sending address + custom SMTP (#92) | **yours** — before any public release; a free route exists |
| 7 | Play Console answers | **yours** — at listing time, not before |

Steps 3–5 are what stand between here and a working web deletion page. Step 4
is the one most likely to be missed.

---

## 1. GitHub Pages — done

Set to build from **GitHub Actions**, confirmed against the API
(`build_type: workflow`). No branch and no folder to pick: the workflow
`.github/workflows/pages.yml` supplies the files.

Nothing has been published yet, because that workflow only runs on pushes to
`main` that touch `site/**` — see step 5.

## 2. Repository secrets — done

Both `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` exist. They are deliberately
not in the repository: the publishable key is public by design — row-level
security is the boundary, not secrecy — but keeping it out means a fork does not
inherit your backend. The deploy workflow substitutes them into `site/**/*.html`
and `site/assets/*.js` at publish time.

> **Never add the secret key** (`sb_secret_…`, formerly `service_role`) to this
> repository. Nothing in the site needs it, and `pages.yml` greps for it and
> aborts the deploy rather than publishing anything that resembles one.

---

## 3. Add `functions` to the required checks

**Why it matters:** `functions` is the only job that exercises the endpoint that
can *destroy an account*. Today it is not required, so it can go red and still
not block a merge. I flagged this instead of changing branch protection myself.

**Clicks:**

1. <https://github.com/SelfishCoconut/pictokeyboard/settings/branches>
2. Next to the rule for `main`, click **Edit**.
3. Find **Require status checks to pass before merging** (already ticked).
4. In the search box under it, type `functions`.
5. Pick **`functions`** from the dropdown — the plain one. If you also see
   `functions / functions`, take the bare `functions`; that is the name the
   other ten entries use and it is what the job reports.
6. **Save changes** at the bottom. It is easy to miss.

**Check it worked:** the list should now hold **11** entries —

```
quality, build, unit, compliance, codeql, secret scan,
dependency audit, emulator (API 26), emulator (API 35), database, functions
```

Then open PR #101 and confirm *functions* sits under **Required** rather than
in the optional group below.

**Is it safe to require?** Yes — `functions` is not path-filtered, so it runs on
every pull request and cannot leave a PR permanently pending. It has already
passed on #101. It costs about 1m40s.

---

## 4. Make Supabase send a **code**, not just a link

**Why it matters, and why it is easy to miss.** The web deletion page asks for a
**6-digit code**, not a link: a code needs no redirect URL allow-listed and
cannot strand someone on a page they did not come from. Supabase's *default*
Magic Link email contains only a link — I confirmed this against a local stack,
where the default email arrives with **no digits in it anywhere**. So with the
default template, the code box on the page can never be filled, and the failure
looks like the person typing it made a mistake.

The template now lives in the repository at `supabase/templates/magic_link.html`
and is wired into `supabase/config.toml`, which covers local development and the
CI job. **The hosted project keeps its own copy**, so it must be set once by
hand.

**Clicks:**

1. Go straight to <https://supabase.com/dashboard/project/_/auth/templates> —
   the `_` resolves to your project. Via the sidebar it is **Auth → Email
   Templates**, which is easy to miss because it is not under a heading called
   "Emails".
2. Select the **Magic Link** template — listed in the docs as *"Magic link or
   OTP"*. *(Not "Confirm signup", which is for registration. Magic Link is the
   template `signInWithOtp` uses, and that is what the deletion page calls.)*
3. Set **Subject heading** to exactly:

   ```
   Your PictoKeyboard sign-in code
   ```

4. Replace the whole **Message body** with the contents of
   `supabase/templates/magic_link.html`. Reproduced here so you can paste
   without leaving this page — if the two ever differ, **the file wins**:

   ```html
   <h2>Your PictoKeyboard sign-in code</h2>

   <p>Enter this code on the page you came from:</p>

   <p style="font-size: 28px; font-weight: bold; letter-spacing: 4px; margin: 24px 0;">
     {{ .Token }}
   </p>

   <p>The code expires shortly and can only be used once.</p>

   <p style="color: #5b6069; font-size: 14px;">
     Prefer a link? <a href="{{ .ConfirmationURL }}">Sign in here</a> instead.
   </p>

   <p style="color: #5b6069; font-size: 14px;">
     If you did not ask to sign in, you can ignore this email — nothing has
     changed, and no account has been created.
   </p>
   ```

5. **Save**.

The one line that matters is `{{ .Token }}` — that is the 6-digit code.
`{{ .ConfirmationURL }}` stays underneath as a fallback for people who would
rather click than type.

**Check it worked:** open the deletion page, enter an address that has an
account, press *Email me a code instead*, and confirm the email contains six
digits you can type into the box.

> ### The two ways this test lies to you
>
> Until step 6's custom SMTP exists, Supabase's built-in mailer:
>
> 1. **delivers only to addresses on the project team.** Test with an address
>    that is a member of the Supabase project — anything else silently goes
>    nowhere, and the page will show a generic failure that has nothing to do
>    with your template.
> 2. **sends roughly two messages an hour.** If you test twice in quick
>    succession, the second attempt fails with a rate-limit error. The page
>    says so honestly, in the caregiver's own language, and points at
>    `pictokeyboard@duck.com` — but do not read that as the template being
>    wrong. Wait, or check the Supabase **Auth Logs**.

---

## 5. Merge #100, then #101 — in that order

**Why the order:** #101 is stacked on #100 (its base branch is
`feat/83-deletion-app`). GitHub retargets #101 to `main` automatically the
moment #100 merges. Merging out of order is not possible; merging #100 first
just makes #101's diff shrink to only its own changes.

**#101 is the merge that publishes the URLs.** `pages.yml` triggers on pushes to
`main` touching `site/**`, and `site/` arrives with #101. Until then
<https://selfishcoconut.github.io/pictokeyboard/> is a 404 — expected, not a
fault.

**Steps:**

1. **#100** already has auto-merge armed (squash), so it merges itself the
   moment its checks are green. Its earlier failures were all Actions outage
   debris — six jobs died in *Set up job* with `Service Unavailable`. I have
   re-run them. If any is still red for a real reason:

   ```sh
   gh pr checks 100                 # see what is actually failing
   gh run rerun <run-id> --failed   # only re-runs the failed jobs
   ```

2. Confirm it landed:

   ```sh
   gh pr view 100 --json state,mergedAt
   ```

3. **#101** — do step 3 above *first* if you want `functions` enforced on it,
   then merge it the same way. It carries `Closes #83`, so merging it closes the
   issue.

4. Watch the deploy the first time, because it is the first time this workflow
   has ever run:

   ```sh
   gh run list --workflow=pages.yml --limit 1
   ```

   If a secret were missing it would fail immediately with
   `Missing required configuration:` and name it. Both are present, so the
   expected outcome is green, followed by these four URLs going live:

   - <https://selfishcoconut.github.io/pictokeyboard/privacy/>
   - <https://selfishcoconut.github.io/pictokeyboard/delete-account/>
   - <https://selfishcoconut.github.io/pictokeyboard/es/privacidad/>
   - <https://selfishcoconut.github.io/pictokeyboard/es/eliminar-cuenta/>

5. Once live, do the step 4 check against the real page.

---

## 6. Before any public release

### An address to send *from* — and whether it has to cost money

`pictokeyboard.app` is **not registered** — the registry returns "object not
found". That is why `verify@pictokeyboard.app` never received anything during
#79's verification.

The constraint is not really "own a domain", it is **DMARC alignment**: the mail
has to be signed by whoever owns the domain in the `From:` address, or receivers
treat it as forgery. That leaves three routes.

**Route A — a domain you own (~€12/year for `.app`).** The clean answer. You add
SPF and DKIM records, and mail from `noreply@pictokeyboard.app` is aligned,
inbox-bound, and looks like the app. Best deliverability and it is the address
caregivers would expect.

**Route B — Gmail SMTP with an App Password (free).** Google signs the mail with
gmail.com's own DKIM, so it is properly aligned and lands in inboxes. Free
Gmail allows on the order of 500 messages a day, which is far beyond what this
app will send.

- Requires **2-Step Verification** on the Google account — App Passwords cannot
  be created without it, and cannot be created at all on Workspace/school
  accounts, Advanced Protection accounts, or accounts using only security keys.
- Create one at <https://myaccount.google.com/apppasswords>.
- Host `smtp.gmail.com`, port `587`, username your full Gmail address, password
  the 16-character App Password.
- The trade-off is cosmetic and real: sign-in codes arrive from a personal-
  looking Gmail address rather than from the app.

**Route C — "verify a single sender" on SendGrid/Brevo using
`pictokeyboard@duck.com`. This does not work.** It is the obvious free
workaround and it fails for a specific, checkable reason: `duck.com` publishes

```
v=DMARC1; p=quarantine; pct=100; rua=mailto:…@ag.us.dmarcian.com
```

`p=quarantine` means mail claiming to be from `duck.com` that is not signed by
duck.com goes to **spam** — and only DuckDuckGo can sign it. A sign-in code in a
spam folder is the same as no sign-in code. Do not spend an afternoon on this
route.

All of this is about *sending*. Receiving stays at `pictokeyboard@duck.com` and
needs nothing at all.

### Custom SMTP (#92) — this gates the web deletion page, not just sign-up

The built-in mailer's two limits above are not merely inconvenient. *"Email me a
code"* is the **only** route available to someone who signed up with Google,
because that account has no password to type. So until SMTP is configured, a
Google-account caregiver **cannot delete their account from the web at all** —
they fall through to the email address on the page, which means a person doing
it by hand.

The page handles this honestly rather than hiding it: a rate-limit failure says
so plainly and points at `pictokeyboard@duck.com`. That is a fallback, not a
fix.

**Worked example with Resend** (free tier is ample here; Postmark, SendGrid,
Mailgun and Amazon SES all work the same way):

1. Create an account, add your domain, and add the DNS records it gives you.
   Wait for it to show **Verified**.
2. Create an **API key** with send permission. Copy it — it is shown once.
3. In Supabase: **Project Settings → Authentication → SMTP Settings** →
   **Enable Custom SMTP**, and fill in:

   | Field | Value |
   |---|---|
   | Sender email | `noreply@yourdomain` |
   | Sender name | `PictoKeyboard` |
   | Host | `smtp.resend.com` |
   | Port | `587` |
   | Username | `resend` |
   | Password | the API key from step 2 |

4. **Check the rate limit separately.** Enabling SMTP lifts the team-only
   restriction and moves the cap from 2/hour to a default of **30/hour** — it
   does not remove it. Adjust at
   <https://supabase.com/dashboard/project/_/auth/rate-limits> if 30 is ever
   tight. (SMTP settings themselves live at
   <https://supabase.com/dashboard/project/_/auth/smtp>.)
5. Verify by sending a code to an address that is **not** on the project team.
   That is the case that fails today, so it is the one that proves it works.

### Release SHA-1 (#92)

Register the release keystore's SHA-1 for Google sign-in, or *Continue with
Google* works in debug builds and fails in the released one — a failure that
only appears after publishing.

```sh
keytool -list -v -keystore <release.jks> -alias <alias> | grep SHA1
```

Add it in the Google Cloud console alongside the debug fingerprint; keep both,
so debug builds keep working.

### Update the privacy policy when publishing ships (#41)

That feature sends board content to the server for the first time. The policy
and the Data Safety answers must change in the **same** release, not after it —
both language versions of the policy, and the store form.

---

## 7. Play Console — at listing time

The answers are worked out in [`play-data-safety.md`](./play-data-safety.md),
derived from the code rather than from memory, with the commands that produce
them.

**The two URLs Play asks for:**

| Field | Value |
|---|---|
| Privacy policy URL | `https://selfishcoconut.github.io/pictokeyboard/privacy/` |
| Account deletion URL | `https://selfishcoconut.github.io/pictokeyboard/delete-account/` |

Play **visits** the deletion URL and checks it works for someone without the app
installed. It does. It must stay reachable for as long as the app is listed.

If you add a Spanish store listing, use `…/es/privacidad/` and
`…/es/eliminar-cuenta/`. Both are linked from the English pages and declared
with `hreflang`, so a Spanish-speaking caregiver is never stranded on an English
page.

**Data safety form**, in short: the only thing collected is an email address (or
a Google account id), only if a caregiver chooses to create an account, and both
are marked *optional*. Nothing is shared with anyone. Nothing is collected from
the person using the keyboard.

**Expect the strict review tier.** An IME can observe what is typed, so be ready
to answer *"what can this keyboard see?"* in writing. The answer, and the place
in the code where it is enforced, is in `play-data-safety.md`.
