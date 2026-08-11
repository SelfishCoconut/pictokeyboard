# PictoKeyboard — accounts and board sync

> **Parked — this describes work that is not on `main`.**
>
> `main` became the offline edition on 2026-08-11 (#119): no server, no account,
> no sign-in. Everything below still exists on the **`marketplace`** branch,
> which holds the state `main` had that day, and is accurate there. It is kept
> here as the record of why those decisions were made, so the work can come back
> whole rather than be rebuilt from memory.

**Date:** 2026-08-02
**Status:** **SUPERSEDED on 2026-08-03** by
`2026-08-03-marketplace-and-local-backup-design.md`. Cloud sync of personal
boards was dropped: boards now never leave the device except in an export file
the caregiver controls, and the server holds only accounts and the marketplace
catalogue. #79 (accounts) survives and shipped; #80, #81 and #82 are superseded.
Kept for the reasoning behind the decisions that carried over — tombstones,
photo privacy, and why the keyboard must never link the auth stack.
**Scope:** Supabase-backed caregiver accounts, cloud backup of boards and voice
settings, restore onto a new device, and self-service deletion.

Sharing between caregivers (#39, #40, #41) is **not** in this spec. It is
designed *for* here — the schema decisions below are the ones that make it
possible later without a rewrite — but nothing in this document ships a way for
one caregiver to see another's board.

---

## 1. The problem

A caregiver builds a board over months. The phone breaks, or the family gets a
new one, and the board is gone. Today the only recovery is the JSON export in
Settings, which requires the caregiver to have thought about it in advance —
which nobody does.

Secondarily: a caregiver who runs the app on a phone and a tablet maintains two
unrelated copies of the same vocabulary.

## 2. The rule this design is built on

**The keyboard must work with no network and no account, forever.**

PictoKeyboard is how someone speaks. If a token refresh, a paused Supabase
project or a forgotten password can stand between a person and their words, the
product has failed at the only thing it does. Therefore:

- **Room is the source of truth.** Supabase is a peer the app pushes to and can
  pull from. It is never read on the typing path.
- **The IME process never touches Supabase.** No auth client, no network client,
  no session in the keyboard service. Sync lives in the config app and a
  WorkManager worker.
- **Signed out is a first-class state**, not a degraded one. The app behaves
  exactly as it does today.

## 3. What syncs, and what deliberately does not

| Data | Syncs | Why |
|---|---|---|
| Boards, categories, pictos (text, ids, layout, colours, order) | **Yes** | This is the caregiver's work and the thing worth rescuing |
| Voice settings — TTS rate, pitch, speak-on-tap, trailing space, blind mode, interface language | **Yes** | Describes the person, so it should follow them to a new device |
| ARASAAC pictogram ids | **Yes** | An id, not an image. The CDN re-supplies the picture; nothing is redistributed, so CC BY-NC-SA is untouched |
| **Custom photos and camera shots** | **No** | Photographs of an identifiable disabled child, their teacher and their classroom. Storing those is a materially different privacy proposition, and avoiding it also keeps the free tier's shared 1 GB irrelevant |
| **The caregiver PIN** | **No** | A credential. Keeping it off the server costs ten seconds after a restore |
| **Usage statistics** | **No** | A tap-by-tap behavioural record of a disabled person's speech — the most sensitive thing the app holds. The cost of omitting it is a few weeks before Suggested is useful again |
| `boards.active` | **No** | Which board is in use is a property of the device in the room, not of the account |
| `imagePath` / `iconImagePath` | **No** | Local cache paths, meaningless on another device, and they point at the photos that do not sync |

**Restoring a board whose picto was a photo** yields a label-only picto with an
honest empty state naming what happened, never a broken image and never silence.

## 4. Data model

### 4.1 Server tables

All client-generated UUIDs. `BackupDto`'s KDoc already commits to stable ids
"preserved so this same shape can later be exchanged with the psychologist web
backend for sync" — so rows upsert by primary key with no id-mapping table.

Every table carries `owner_id uuid`, `updated_at timestamptz`, and
`deleted_at timestamptz null`.

| Table | Holds |
|---|---|
| `profiles` | `id` (= `auth.users.id`), `display_name`, `settings jsonb`, `created_at` |
| `boards` | the columns of `BoardEntity` minus `active`, `iconImagePath` |
| `categories` | the columns of `CategoryEntity` minus `iconImagePath` |
| `pictos` | the columns of `PictoEntity` minus `imagePath` (and, when #35 lands, minus `audioPath`) |

Voice settings live as one `settings jsonb` blob on `profiles` rather than a
column each: it is a single small object, always read and written whole, and
adding a setting must not require a server migration in lockstep with an app
release.

### 4.2 Row-level security

`owner_id = auth.uid()` for select, insert, update and delete, on every table.
With no sharing in v1 there are no cross-user policies to get wrong. The anon
key is public by design; RLS *is* the security boundary, so it must be enabled
on every table at creation and never disabled for convenience.

### 4.3 Local additions

One new Room table, `sync_deletions(entityType, entityId, deletedAt)`, written
by the repository's delete paths. Section 5.2 explains why it has to exist.

## 5. Sync

### 5.1 Push — automatic

Upsert every local board, category and picto row for this owner, plus any
recorded tombstones, in a single transactional RPC.

Full-state, not delta: no dirty flags, no change tracking, no per-row
bookkeeping to get out of step. A large board set is a few hundred rows and
about 75 KB, which does not justify the machinery delta sync would need.

Triggered on a debounce after any local mutation, when the config app goes to
background, and daily. WorkManager, network-connected constraint.

### 5.2 Deletes travel as tombstones, never as absence

The tempting version of §5.1 is "upsert what I have, and mark everything else
deleted". **This erases data as soon as there are two devices.** Device A holds
boards 1 and 2; device B holds 1, 2 and 3. A's next push marks 3 deleted, and
board 3 is gone from an account that never asked for it.

So push is **additive**, and a delete is only ever sent as an explicit tombstone
from `sync_deletions`, cleared once the server acknowledges it. Only an in-app
delete — an action the caregiver took and saw — removes anything.

### 5.3 First sign-in on a device — union, never replace

The account's boards and the device's boards are merged by id. Boards existing
only on the device are kept and pushed up; boards existing only in the account
are pulled down. Signing in cannot remove work.

The one untidiness: a fresh install seeds a starter board, so a caregiver
signing in on a new phone ends up with their real boards *and* an untouched
starter. The restore offers to remove the starter rather than deleting it
silently.

### 5.4 Restore — explicit

The union of §5.3 runs **once, automatically, at first sign-in on a device**,
and push is held until it completes. That is safe precisely because it cannot
remove anything, and making it a button would mean a caregiver who missed the
button gets an account that silently holds only half their vocabulary.

Every *later* pull is a named action in Settings and never happens on its own.
It pulls every row where `deleted_at is null` and unions into the local database
by id, on the same rule.

### 5.5 Accepted limitation

Two devices editing the same board at the same time: last write wins per row,
and the loser's edit is lost. This is what "auto-push, explicit restore" buys
instead of merge machinery, and it is stated in the UI rather than hidden.

`updated_at` exists per row from day one so that a later increment can do
better — true two-way sync becomes an addition, not a rewrite.

## 6. Authentication

`supabase-kt` — `auth-kt`, `postgrest-kt`, and `compose-auth` for native Google
sign-in.

- **Google** via Credential Manager (one-tap), needing a Google Cloud OAuth web
  client id plus an Android client registered with the release SHA-1.
- **Email and password**, with confirmation and password reset.
- **Custom SMTP is a prerequisite, not a nicety.** Supabase's built-in mailer is
  rate-limited to a handful of messages an hour — adequate for development,
  useless the first day two caregivers sign up at once. Resend or Postmark, free
  tier.
- **Sign out is not a delete.** Local boards stay, and the app returns to
  behaving exactly as it does signed-out. Stated plainly on the button.

Accounts are for **caregivers, who are adults**. The AAC user never has one and
is never asked to authenticate to speak.

## 7. Deletion

Two scopes, both immediate, both irreversible, both available in-app **and**
from a public web URL — Play requires the URL for someone who has already
uninstalled, and GDPR requires the path to exist at all.

| Action | Removes | Leaves |
|---|---|---|
| **Delete my cloud data** | every board, category, picto and profile row on the server | the account, and everything on this device, working exactly as signed-out |
| **Delete my account** | the above, plus the `auth.users` row | everything on this device |

Neither touches local data. A caregiver who deletes their account still has
their boards on the phone in their hand, and the confirmation says so — the
opposite reading would make this the most frightening button in the app.

No grace period. "Delete at any point" is honest only if it means now.

**Deleting cloud data must also stop sync**, or the next debounced push
faithfully re-uploads everything the caregiver just asked to be removed. So
"delete my cloud data" turns sync off and leaves it off until the caregiver
turns it back on; "delete my account" signs out. Any deletion path that leaves
an armed `SyncWorker` behind it is a bug, and §10 tests for exactly this.

### 7.1 Published boards survive, anonymised

This is a forward constraint on the schema, not code that ships here.

**Publishing copies; it does not reference.** A published board is a snapshot
into a separate `published_boards` table, detached from the author's profile at
the moment of publishing. If the catalogue instead pointed at the author's live
`boards` row, then deleting an account would either cascade-delete a board other
caregivers are using or leave a dangling reference — and editing your own board
would silently rewrite what everyone had already downloaded.

On account deletion, published boards are **anonymised rather than removed**:
`author_id` is nulled and the displayed author becomes *Anonymous*. Removal is
the wrong answer legally and practically. Under GDPR the personal data in a
published board is the author's name, not the vocabulary, so replacing the name
discharges erasure; and deleting the board would take it out from under
caregivers who are relying on it.

ARASAAC attribution is unaffected — it is owed to ARASAAC, not to the board's
author.

Because publishing is effectively irrevocable, the publish flow (#41) must say
so before it happens, and must not offer to publish a board carrying anything
personal. The rule that photos never leave the device already helps here.

## 8. Operations

- **The free tier pauses a project after 7 days of inactivity.** A scheduled
  GitHub Action pings the project daily so the wake-up never lands on a
  caregiver mid-restore.
- Supabase URL and anon key reach the app through `local.properties` and
  `BuildConfig`, not hardcoded. The `service_role` key never enters the
  repository or the APK under any circumstances.
- Schema lives in the repository as migration SQL, applied through the Supabase
  CLI, so the server's shape is reviewable in a pull request like everything
  else.

## 9. Compliance work that ships with the feature, not after

- **Privacy policy** covering the email address, the board vocabulary (which can
  contain a child's family names and medical words), and the voice settings —
  and stating plainly that photographs and usage statistics never leave the
  device.
- **Play Data Safety** declarations updated for account creation and for the
  data above. This widens the ground #50 already covers for sentence help.
- **In-app account deletion plus a public deletion URL** (§7). A Play
  requirement for any app that creates accounts.
- **Data export** already exists as the Settings JSON export and satisfies
  portability; the account screen should point at it.

## 10. Testing

| Layer | What is proved |
|---|---|
| Unit | Tombstone recording on every delete path; the union rule in §5.3 keeping both sides; the push payload omitting photos, PIN, usage and `active` |
| Unit | Settings JSONB round-trip, including an unknown key written by a newer app version |
| Instrumented | Sign-in on a device holding local boards does not lose them; restore onto an empty install rebuilds the board set; a picto whose photo did not sync renders its label-only state |
| Instrumented | The IME starts, draws and types with no Supabase dependency initialised at all |
| Instrumented | Deleting cloud data leaves no armed worker behind — nothing is re-uploaded afterwards (§7) |
| Manual | Deletion actually empties the tables, verified against the project |

## 11. Decomposition

Five issues, in order. Each is shippable on its own.

1. **#79 Caregiver accounts** — Supabase client, Google and email/password sign-in,
   password reset, the Account section in Settings, sign out. No sync.
2. **#80 Board schema, RLS and push** — server migrations, `sync_deletions`,
   `SyncWorker`, the additive push of §5.1–5.2.
3. **#81 Restore onto a new device** — pull, the union rule, the first-sign-in flow,
   the label-only state for a picto whose photo stayed behind.
4. **#82 Voice settings sync** — the `settings` blob on `profiles`.
5. **#83 Deletion and compliance** — both deletion scopes, the web URL, the privacy
   policy, the Play Data Safety update.

Sharing (#39, #40, #41) rebases onto this afterwards, using `published_boards`
from §7.1.
