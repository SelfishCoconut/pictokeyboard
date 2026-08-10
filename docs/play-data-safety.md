# Play Data Safety — what to declare, and why

The Data Safety form is filled in the Play Console, not in this repository, so
this file is the source the answers are copied from. It exists because the form
is answered from memory otherwise, and a form that disagrees with the binary is
a policy violation rather than a paperwork mistake — and it is found by
automated scanning, reliably.

Every answer below is derived from the code, with the check that produces it.
Re-run the checks before each submission; if one of them starts returning
something else, the form is now wrong.

## Data collected or shared

| Data type | Collected | Shared | Purpose | Linked to identity | User can delete |
|---|---|---|---|---|---|
| Email address | Yes — **only if** the caregiver creates an account | No | Account management | Yes | Yes |
| User IDs (Google account id) | Yes — only if "Continue with Google" is used | No | Account management | Yes | Yes |
| **Everything else** | **No** | **No** | — | — | — |

Explicitly **not** collected: name, address, phone number, location (coarse or
precise), photos, audio, files, contacts, calendar, app activity, search
history, installed apps, device identifiers, advertising id, crash logs,
diagnostics, or performance data.

There is no analytics SDK, no crash reporter, and no advertising SDK in the
build.

## Answers to the questions that get asked twice

**"Does your app collect or share user data?"** — Yes, but only an email address
or Google account id, and only when a caregiver chooses to create an account.
An account is optional; the keyboard is fully functional without one.

**"Is data encrypted in transit?"** — Yes. All requests are HTTPS; there is no
cleartext traffic permitted (verified below).

**"Can users request that data be deleted?"** — Yes, and it is immediate rather
than a request. In the app: Settings → Account → Delete my account. On the web,
without installing the app: <https://selfishcoconut.github.io/pictokeyboard/delete-account/>.
Play requires that second URL, and it is the one it will check.

**"Does your app collect data from children?"** — No. The app is operated by an
adult on the child's behalf, setup sits behind a PIN, and nothing in the app
ever asks for the name, age or diagnosis of the person using the keyboard.

## Keyboards get the strict review tier

An IME can observe the text field it types into, so expect the question "what
can this keyboard see, and where does it go?" to be asked directly. The answer,
and where it is enforced:

- Typed content is delivered to the host app and **nowhere else**. There is no
  network call anywhere in the IME.
- The app tallies how often each pictogram is used, on-device, to order the most
  used first. It is a count per pictogram, not a record of what was said.
- **Password fields are excluded** from that tally.
- `EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING` is honoured — when a host app
  asks the keyboard not to learn from a field, nothing is recorded.

`ImeHasNoSupabaseTest` asserts that no keyboard source file can even reach the
Supabase client, so a token refresh can never stand between a person and their
words.

## The checks these answers come from

```sh
# Permissions: expect exactly INTERNET and ACCESS_NETWORK_STATE, and no CAMERA.
grep -n "uses-permission" app/src/main/AndroidManifest.xml

# Everything that leaves the device. Expect only ARASAAC and Supabase.
grep -rhoE 'https?://[a-zA-Z0-9./-]+' --include='*.kt' app/src/main/java | sort -u

# Which files can reach the backend at all. Expect only the two auth files --
# anything else here means board content may now leave the phone, and both this
# document and the public privacy policy need updating before release.
grep -rln "supabase" --include='*.kt' app/src/main/java

# No cleartext traffic, and not debuggable in release.
grep -nE 'usesCleartextTraffic|android:debuggable' app/src/main/AndroidManifest.xml

# The IME privacy signals.
grep -rn "NO_PERSONALIZED_LEARNING\|TYPE_TEXT_VARIATION_PASSWORD" \
  --include='*.kt' app/src/main/java
```

## Still to do before the first upload

These are outside this change and remain open:

- Board publishing (#41) will send board content to the server for the first
  time. **The public privacy policy and this table must be updated in the same
  change that ships it**, not afterwards.
- Custom SMTP and the release SHA-1 (#92) — sign-in emails currently come from
  Supabase's shared sender, which is rate-limited and not suitable for release.
- The store listing itself: icon, feature graphic, screenshots, content rating
  questionnaire, and the target-audience answers.
