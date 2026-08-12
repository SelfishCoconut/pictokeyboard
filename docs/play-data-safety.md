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
| **Everything** | **No** | **No** | — | — | — |

Nothing. There is no server, no account and no sign-in (#119), so there is no
destination for user data to be collected *to*. This is a stronger claim than
the app not sending anything: `AppHasNoAccountsTest` fails the build if any
source file so much as imports an authentication stack.

Explicitly **not** collected: email address, user ids, name, address, phone
number, location (coarse or precise), photos, audio, files, contacts, calendar,
app activity, search history, installed apps, device identifiers, advertising
id, crash logs, diagnostics, or performance data.

There is no analytics SDK, no crash reporter, and no advertising SDK in the
build.

## Answers to the questions that get asked twice

**"Does your app collect or share user data?"** — No.

**"Is data encrypted in transit?"** — Yes. Everything outbound goes to ARASAAC
over HTTPS, and no cleartext traffic is permitted (verified below).

There are two kinds of request, and the second one is easy to forget because it
carries text a person typed:

| Endpoint | Carries |
|---|---|
| `static.arasaac.org/pictograms/{id}...` | a pictogram id |
| `api.arasaac.org/api/pictograms/{lang}/search/{text}` | **the search word** |

`ArasaacApi.search` sends what the caregiver typed into the pictogram search box
— "comer", "happy" — to ARASAAC, which is unavoidable for a search and is the
same thing any dictionary lookup does. Neither request carries an account, a
device identifier, or anything typed on the *keyboard*; as with any web request,
ARASAAC sees the IP address.

**Why this is still "no data collected".** A search term is Play's *App activity
▸ Search history* type, so the question is real rather than rhetorical. It is not
declared because it meets the ephemeral-processing exemption: the term is sent to
service a request in real time and is never persisted — not by us, because there
is no server to persist it to, and not on the device either. The results are
cached as images; the query is not stored anywhere.

Written out because the answer used to be "the app sends a pictogram id and no
identifier of any kind", which was a true sentence about the wrong endpoint. The
form's answer did not change; the reason it can be defended did.

**"Can users request that data be deleted?"** — There is no account to delete
and nothing held off the device. Everything the app stores is on the phone, and
uninstalling or clearing app data removes all of it. **No Account Deletion URL
is required**, because the app has no account creation.

> The Data Safety form only demands a deletion URL from apps that let users
> create an account. This app does not, so the field is left empty rather than
> pointed at a page that would have to explain there is nothing to delete. The
> privacy policy URL is still required and still published.

**"Does your app collect data from children?"** — No. The app is operated by an
adult on the child's behalf, setup sits behind a PIN, and nothing in the app
ever asks for the name, age or diagnosis of the person using the keyboard.

## Sentence help: a model on the phone, and one download

Switching sentence help on (#43, #44) downloads about 347 MB of model weights
from `huggingface.co`, once, and runs them in a **separate process** (`:llm`) on
the device. It is off until a caregiver turns it on, so an install that never
does never contacts them.

| Question | Answer |
|---|---|
| Is typed content sent anywhere? | **No.** The words go over a binder to a process on the same phone. |
| Is the download data collection? | No. It is the app *fetching* a file; the request names the file and carries the IP address, as any download does. |
| Is anything reported back? | No. There is no telemetry, no "model used" ping, and nothing to send it to. |

This is the second destination in the binary, and it is the reason the privacy
policy no longer says there is only one. Both are fetches.

## The assistance contact and CALL_PHONE

The keyboard's bell (#144) calls one number a caregiver typed in.

- The name and number are stored **on the device**, in the app's own settings.
  They are never transmitted, so they are not "collected" in the Data Safety
  sense — but the policy says out loud that they are stored, because a stored
  phone number sounds worse than it is until it is explained.
- **No contacts permission.** The number is typed, not looked up. Asking for the
  address book to save eleven digits would be the wrong trade for every
  caregiver who never uses the feature.
- `CALL_PHONE` is requested at runtime, and **only once a number has been
  entered**. An install that never sets one is never asked.
- Refusing it is supported: the bell falls back to `ACTION_DIAL`, which opens
  the dialler with the number filled in.

> **Confirm before submitting:** as of writing, `CALL_PHONE` is *not* on Play's
> restricted-permissions list — that list is the Call Log and SMS groups,
> `MANAGE_EXTERNAL_STORAGE`, `QUERY_ALL_PACKAGES` and friends — so no
> Permissions Declaration Form is required for it. That list changes. Check it
> in the Console rather than trusting this line.

## Keyboards get the strict review tier

An IME can observe the text field it types into, so expect the question "what
can this keyboard see, and where does it go?" to be asked directly. The answer,
and where it is enforced:

- Typed content is delivered to the host app and **nowhere else**. There is no
  network call anywhere in the IME — including with sentence help on, where the
  words cross a binder to another process on the same phone and never a socket.
- The app tallies how often each pictogram is used, on-device, to order the most
  used first. It is a count per pictogram, not a record of what was said.
- **Password fields are excluded** from that tally.
- A **debug** build logs what the model was asked, what it answered, and what the
  validator made of it (#167, #186). That branch is behind `BuildConfig.DEBUG`,
  which is a compile-time constant R8 removes from the release build, so nothing
  is written to logcat by the app anybody installs. Recorded here because it is a
  real place typed content goes, in one build type, and this file is where the
  claim "nowhere else" has to survive being checked.
- `EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING` is honoured — when a host app
  asks the keyboard not to learn from a field, nothing is recorded.

## Android's own backup is turned off, on purpose

Auto Backup is on by default, and its default rules include `filesDir` and every
database. Left alone, this app would have shipped each board, the usage tally,
and `filesDir/pictos` — which holds the custom pictograms — to the user's Google
Drive. Those pictograms are photographs of somebody's kitchen, their bus stop,
their grandmother.

That is probably not a Data Safety disclosure: the platform performs it, and the
destination is the user's own account rather than a developer's. It *is* a
contradiction of a privacy policy that says the data stays on the phone, and the
listing goes further and tells the caregiver an exported file is the only backup
there is. Rather than rewrite three documents to describe a backup nobody asked
for, the app now matches what they say.

`data_extraction_rules.xml` (API 31+) excludes every domain from cloud backup
while leaving phone-to-phone transfer intact — that one is the user moving their
own data to their own next phone, directly, and switching it off would cost this
population more than anyone. `backup_rules.xml` says the same for API 26–30,
where the format cannot separate the two and the cloud exclusion takes the
transfer with it.

`play-compliance.sh` fails the build if both attributes ever go missing, because
the default is silent and the symptom is a promise quietly becoming untrue.

## Sharing a board is the user's action, not the app's

A caregiver can export a board as a `.pkb` file and send it through the system
share sheet. This is not data collection or sharing in the Data Safety sense:
the app hands a file to whichever app the user picked, at the moment they picked
it, and nothing is transmitted anywhere on its own. It is the same category as
the platform's own share sheet — user-initiated, user-directed, one file at a
time.

## The checks these answers come from

```sh
# Permissions: expect exactly INTERNET, ACCESS_NETWORK_STATE and CALL_PHONE,
# and no CAMERA and no READ_CONTACTS.
grep -n "uses-permission" app/src/main/AndroidManifest.xml

# Everything that leaves the device. Two hosts are actually contacted --
# arasaac.org and huggingface.co -- and both are fetches that carry nothing the
# user typed. The command also prints links the app only ever *displays*
# (creativecommons.org, apache.org, arasaac.org/terms-of-use, and our own
# privacy URL); those are never requested by the app. Anything outside those two
# groups is a finding.
grep -rhoE 'https?://[a-zA-Z0-9./-]+' --include='*.kt' app/src/main/java | sort -u

# No authentication stack anywhere in the app. This is the assertion that keeps
# the table above at "no" -- it runs in CI on every push, but run it by hand
# before a submission too, because it is the whole basis of the form.
ANDROID_HOME=$HOME/Android/Sdk ./gradlew testDebugUnitTest --tests '*AppHasNoAccountsTest*'

# No cleartext traffic, and not debuggable in release.
grep -nE 'usesCleartextTraffic|android:debuggable' app/src/main/AndroidManifest.xml

# The IME privacy signals.
grep -rn "NO_PERSONALIZED_LEARNING\|TYPE_TEXT_VARIATION_PASSWORD" \
  --include='*.kt' app/src/main/java
```

## Still to do before the first upload

- The store listing itself: the content rating questionnaire and the
  target-audience answers, which can only be answered in the Console. The icon,
  the feature graphic and the screenshots are in `media/`, and the screenshots
  were recaptured on 2026-08-12 against the current keyboard.
- Confirm the `CALL_PHONE` declaration question above in the Console.

> The privacy policy was updated for sentence help and the bell in #151, in both
> languages, in the release that ships them — which is the rule this file keeps
> restating and the one that is easiest to miss.

> If accounts ever return from the `marketplace` branch, **this table and the
> public privacy policy change in the same release that ships them** — not
> afterwards. The version of this document that describes them is on that
> branch.
