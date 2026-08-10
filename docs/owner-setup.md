# Owner setup — the steps only you can do

Everything here needs access I do not have: repository settings, the Supabase
dashboard, and the Play Console. Nothing in the codebase can substitute for
them, and several fail *quietly* if skipped — so each step says where to click,
and how to tell it actually worked rather than only looked like it did.

## Where this stands

| # | Step | State |
|---|---|---|
| 1 | GitHub Pages set to *GitHub Actions* | **done** |
| 2 | `SUPABASE_URL` + `SUPABASE_PUBLISHABLE_KEY` secrets | **done** |
| 3 | `functions` added to the required checks | **done** — 11 checks now |
| 4 | Merge #100 and #101 | **done** — the site is live |
| 5 | **Custom SMTP** | **yours — do this first**, it unblocks 6 |
| 6 | Magic Link email template | **yours** — blocked by 5, see below |
| 7 | Play Console answers | **yours** — at listing time |

The site is published and all six pages load:

- <https://selfishcoconut.github.io/pictokeyboard/privacy/>
- <https://selfishcoconut.github.io/pictokeyboard/delete-account/>
- <https://selfishcoconut.github.io/pictokeyboard/es/privacidad/>
- <https://selfishcoconut.github.io/pictokeyboard/es/eliminar-cuenta/>

---

## 5. Custom SMTP — now a prerequisite, not a pre-release chore

**This is a correction.** An earlier version of this document had the email
template as step 3 and custom SMTP as a "before you publish" item. That order is
wrong on the free tier.

On **3 June 2026** Supabase stopped free-tier projects from customising auth
email templates while sending through the default email service — [their
changelog explains
why](https://supabase.com/changelog/46599-changes-to-email-template-customisation-on-free-tier):
people were standing up free projects, rewriting the auth templates with
phishing content, and using signup flows to deliver it. Projects created before
that date and paid plans are unaffected. **This project's Supabase project was
created around 3 August 2026**, so it is affected.

Configuring **any** custom SMTP restores template editing. So SMTP is not
optional polish here — it is the thing standing between you and a working web
deletion page, and it happens to fix three problems at once:

| Fixed by custom SMTP | Was |
|---|---|
| Template editing | Locked on free tier since June 2026 |
| Who can receive mail | Project team members only |
| Rate limit | 2/hour → 30/hour default |

### The constraint is DMARC alignment, not "own a domain"

Mail has to be signed by whoever owns the domain in the `From:` address, or
receivers treat it as forgery. Three routes:

**Route A — Gmail SMTP with an App Password. Free, and it genuinely works.**
Google signs the mail with gmail.com's own DKIM, so it is properly aligned and
lands in inboxes. Free Gmail allows on the order of 500 messages a day, far
beyond what this app will send.

1. The Google account needs **2-Step Verification** — App Passwords cannot be
   created without it, and cannot be created at all on Workspace/school
   accounts, Advanced Protection accounts, or accounts secured only by security
   keys.
2. Create one at <https://myaccount.google.com/apppasswords>. Copy the
   16 characters; it is shown once.
3. In Supabase, <https://supabase.com/dashboard/project/_/auth/smtp> →
   **Enable Custom SMTP**:

   | Field | Value |
   |---|---|
   | Sender email | your full Gmail address |
   | Sender name | `PictoKeyboard` |
   | Host | `smtp.gmail.com` |
   | Port | `587` |
   | Username | the same Gmail address |
   | Password | the App Password |

The trade-off is cosmetic and real: sign-in codes arrive from a personal-looking
address rather than from the app. For unblocking development and for a small
release, that is a fair trade. It is reversible at any time.

**Route B — a domain you own (~€12/year for `.app`).** The clean answer when the
app is public. `pictokeyboard.app` is **not registered** — the registry returns
"object not found", which is why `verify@pictokeyboard.app` never received
anything during #79's verification. With a domain you add SPF and DKIM, and mail
from `noreply@pictokeyboard.app` is aligned, inbox-bound and looks like the app.
Resend, Postmark, SendGrid, Mailgun and Amazon SES all work the same way:
verify the domain, add the DNS records, create an API key, and put the key in
the password field above. (Resend: host `smtp.resend.com`, port `587`, username
literally `resend`.)

**Route C — "verify a single sender" using `pictokeyboard@duck.com`. This does
not work.** It is the obvious free workaround and it fails for a checkable
reason: `duck.com` publishes

```
v=DMARC1; p=quarantine; pct=100; rua=mailto:…@ag.us.dmarcian.com
```

`p=quarantine` means mail claiming to be from `duck.com` that duck.com did not
sign goes to **spam** — and only DuckDuckGo can sign it. A sign-in code in a
spam folder is the same as no sign-in code. Do not spend an afternoon here.

**Check it worked:** the rate limit at
<https://supabase.com/dashboard/project/_/auth/rate-limits> should read 30/hour
rather than 2, and the Email Templates page should let you edit again.

All of this is about *sending*. Receiving stays at `pictokeyboard@duck.com` and
needs nothing.

---

## 6. Make Supabase send a **code**, not just a link

**Do step 5 first** — on the free tier this page is read-only until custom SMTP
exists. That is the notice you hit.

**Why it matters.** The web deletion page asks for a **6-digit code**, not a
link: a code needs no redirect URL allow-listed and cannot strand someone on a
page they did not come from. Supabase's *default* Magic Link email contains only
a link — confirmed against a local stack, where it arrives with **no digits in
it anywhere**. With the default template the code box can never be filled, and
the failure looks like the person typing it made a mistake.

The template lives in the repository at `supabase/templates/magic_link.html`,
wired into `supabase/config.toml`, and covered by the `functions` CI job. **The
hosted project keeps its own copy**, so it must also be set by hand.

**Clicks:**

1. <https://supabase.com/dashboard/project/_/auth/templates> — the `_` resolves
   to your project. Via the sidebar it is **Auth → Email Templates**.
2. Select the **Magic Link** template, listed in the docs as *"Magic link or
   OTP"*. Not "Confirm signup", which is for registration — Magic Link is what
   `signInWithOtp` uses, and that is what the deletion page calls.
3. Set **Subject heading** to exactly:

   ```
   Your PictoKeyboard sign-in code
   ```

4. Replace the **Message body** with the contents of
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

The line that matters is `{{ .Token }}` — that is the 6-digit code.
`{{ .ConfirmationURL }}` stays underneath as a fallback for people who would
rather click than type.

**Check it worked:** open
<https://selfishcoconut.github.io/pictokeyboard/delete-account/>, enter an
address that has an account, press *Email me a code instead*, and confirm the
email contains six digits you can type into the box. With step 5 done, this
works for any address, not only project members.

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
a Google account id), only if a caregiver chooses to create an account, both
marked *optional*. Nothing is shared with anyone. Nothing is collected from the
person using the keyboard.

**Expect the strict review tier.** An IME can observe what is typed, so be ready
to answer *"what can this keyboard see?"* in writing. The answer, and the place
in the code where it is enforced, is in `play-data-safety.md`.

---

## Also before any public release

- **Release SHA-1 (#92)** registered for Google sign-in, or *Continue with
  Google* works in debug builds and fails in the released one — a failure that
  only shows up after publishing.

  ```sh
  keytool -list -v -keystore <release.jks> -alias <alias> | grep SHA1
  ```

  Add it in the Google Cloud console alongside the debug fingerprint; keep both,
  so debug builds keep working.

- **Update the privacy policy when publishing ships (#41).** That feature sends
  board content to the server for the first time. The policy and the Data Safety
  answers must change in the **same** release, not after it — both language
  versions, and the store form.

- **Update the privacy policy when sentence help ships (#46).** The model runs
  on the device and nothing is sent anywhere, but the policy currently does not
  mention a model existing at all. One paragraph, both languages, same release.
