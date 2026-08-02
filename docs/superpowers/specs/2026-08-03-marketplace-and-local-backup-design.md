# PictoKeyboard — marketplace accounts and local backup

**Date:** 2026-08-03
**Status:** design agreed, supersedes `2026-08-02-accounts-and-sync-design.md`
**Scope:** what the server holds and who may read it, and how a caregiver moves
their boards between devices without one.

**This replaces cloud sync entirely.** The previous design backed a caregiver's
personal boards up to Supabase and restored them on a new device. That is gone.
Personal boards now never leave the device except in a file the caregiver
exports themselves.

---

## 1. What changed, and why it is a better product

The earlier design put every caregiver's vocabulary on a server so it could come
back on a new phone. It worked, but it meant the app held — for every user, in
one place — the names of a disabled child's family, their medical words, and
their daily routine.

Replacing it with an export file the caregiver controls buys three things:

1. **The personal data problem disappears.** There is nothing to breach, nothing
   to declare in Data Safety beyond an email address, and no reason for anyone
   to trust us with their child's vocabulary.
2. **The export can carry photos.** Sync could not: photographs of an
   identifiable child on a shared server was the one thing the old design would
   not do. A file on the caregiver's own storage has no such problem, so the
   backup is finally *complete* rather than "everything except the parts that
   mattered most".
3. **No conflict resolution, ever.** No tombstones, no last-write-wins, no
   silent loss of one device's edits.

The cost is honest and should be stated in the UI: **a caregiver who never
exports has no backup.** Sync protected the person who never thought about it;
a file does not. §6 is about making that as hard to get wrong as possible.

## 2. What the server holds

Only two things, plus what Supabase Auth manages for itself.

| Table | Holds | Why it exists |
|---|---|---|
| `auth.users` | email, password hash, Google identity | Supabase's own; the account |
| `profiles` | `id`, `display_name` | So a published board can name its author |
| `published_boards` | catalogue metadata: name, description, tags, language, picto, author, licence, official flag | What the Discover list draws |
| `published_board_payloads` | the board itself, as one jsonb | What a download actually fetches |

**No personal boards. No categories. No pictos. No settings. No usage.** If it
is not in that table list, it is not on the server.

## 3. Who may do what

| Action | Signed out | Signed in |
|---|---|---|
| Browse the catalogue — names, pictos, authors, tags, counts | **Yes** | Yes |
| Download an **official** board (one we publish) | **Yes** | Yes |
| Download a **community** board | **No** | Yes |
| Publish a board | **No** | Yes |
| Withdraw or edit your own published board | **No** | Yes (own only) |

Two rules underneath that table, and they are the whole security model:

**Seeing is not downloading.** A signed-out caregiver sees the full community
catalogue — that is what makes signing up worth doing — but cannot install from
it. So the catalogue *metadata* and the board *payload* are two tables with two
different policies. Putting them in one table and hoping the client does not
read a column is not a boundary; the anon key ships inside the APK.

**Official boards are the signed-out on-ramp.** A caregiver who has just
installed the app must be able to get a working board immediately, with no
account and no typing. Those are ours, we vouch for them, and they carry nothing
personal.

### 3.1 Why downloading needs an account at all

Worth writing down, because it is friction and friction needs a reason.

A community board is one caregiver's work handed to a stranger. An account gives
that exchange the two things it otherwise lacks: someone to attribute it to, and
someone to answer a report about it. It also gives us a way to rate-limit
scraping of the whole catalogue, which is the failure mode that would make
authors stop publishing.

None of this applies to browsing, which is why browsing stays open.

## 4. Publishing

### 4.1 A published board is a snapshot

Publishing **copies**; it does not reference. The board travels as one jsonb
payload frozen at publish time.

If the catalogue pointed at the author's live board instead, then editing their
own copy would silently rewrite what everyone had already downloaded, and
deleting their account would take the board out from under people relying on it.

### 4.2 Published boards carry no photographs

**A published board contains ARASAAC ids and text. Never a photo, never a
recording.**

This is a firm rule, not a default. The photos in a caregiver's board are of a
real child, their real teacher, their real classroom door. Publishing one to a
public catalogue is irreversible, and the caregiver taking the photo is very
often not the person who could consent to it being published.

So the publish flow strips them, says it has, and shows exactly which symbols
will arrive label-only for whoever downloads it. A caregiver who wants to share
photos shares an export file with someone they chose (§6), which is a private
act between two people rather than a public one.

Export therefore carries photos and publish does not, and that asymmetry is the
point rather than an inconsistency.

### 4.3 Author survives account deletion

On account deletion, published boards are **anonymised rather than removed**:
`author_id` is nulled and the displayed author becomes *Anonymous*. Deleting
them would take a board out from under caregivers relying on it, and under GDPR
the personal data in a published board is the author's name, not the vocabulary.

`author_id` is nullable **only** for that. The insert policy is what stops it
being null at creation — a nullable column with no insert policy would let
anyone publish as nobody.

## 5. Row-level security

RLS is the only boundary there is, because the anon key is public by design.

| Table | `anon` | `authenticated` |
|---|---|---|
| `profiles` | select (display names are public — they appear as authorship) | select all; update own |
| `published_boards` | select where not withdrawn | select where not withdrawn; insert as self; update/delete own |
| `published_board_payloads` | select **only where the parent board is official** | select any live board; insert/update/delete own |

The anon payload policy is the one that implements "see but do not download",
and it is the one to read twice in review.

## 6. Local backup: export and import

### 6.1 Format

One file, `.pkb`, which is a ZIP:

```
manifest.json     format version, app version, exported-at, counts
boards.json       every board, category and picto — the whole graph
media/<sha256>    photos and recordings, content-addressed
```

- **Content-addressed media.** The same photo used by three pictos is stored
  once, and the name is derived from the bytes rather than taken from the
  archive — so a malicious archive cannot write outside the cache directory by
  naming an entry `../../`. Entry names from an untrusted file are never used
  as paths.
- **A format version in the manifest**, checked on import. A newer file fails
  with a message saying so rather than importing half of itself.
- **ZIP rather than base64-in-JSON**: photos are the bulk of the file and
  base64 would add a third to it for nothing.

### 6.2 What it contains

Everything the device holds about boards: every board, its categories, its
pictos, layout, colours, order, languages, **and the photos and recordings**.
Plus voice settings, which describe the person and should follow them.

Not the PIN — a credential does not belong in a file that gets emailed around —
and not usage statistics.

### 6.3 Import is additive

Import **adds** boards; it never replaces the device's own. Ids are regenerated
on the way in, so importing a file twice yields two boards rather than silently
overwriting the caregiver's edits to the first one. A caregiver who wants the
old one gone deletes it, which is a decision they can see.

### 6.4 Making "nobody exports" less true

A file backup only protects people who use it, so:

- Export is one action from Settings that takes **everything**, not a per-board
  chore.
- The app tells the caregiver, on the boards screen, how long it has been since
  their last export — quietly, and only once it has been a while.
- Exporting writes through the system file picker, so it lands in Drive or Files
  and not only on the phone that is about to break.

## 7. What this deletes from the previous design

| Was | Now |
|---|---|
| #80 board schema, RLS and push | **Superseded** — no personal boards on the server |
| #81 restore onto a new device | **Superseded** by export/import (§6) |
| #82 voice settings sync | **Superseded** — settings ride in the export file |
| Tombstones, `sync_deletions`, union-on-sign-in, last-write-wins | All gone. Nothing to reconcile |
| #79 accounts | **Still valid and already built** — an account is now for the marketplace rather than for backup |
| #83 deletion and compliance | **Shrinks**: no personal board data to delete, but account deletion is still a Play requirement and published boards still anonymise |

The one thing #79 must change: its screen currently says signing in does not
back anything up *yet*. That "yet" is now wrong — it never will — and the copy
becomes "an account is for sharing boards with other caregivers".

## 8. Testing

| Layer | What is proved |
|---|---|
| Unit | Export/import round-trips a board graph with photos, byte-identical media |
| Unit | A media entry named `../../evil` cannot escape the cache directory |
| Unit | A newer format version fails with a stated reason, not a partial import |
| Unit | Import is additive: importing twice yields two boards, and the first is untouched |
| Unit | The export payload excludes the PIN and usage statistics |
| Unit | The publish payload excludes every photo and recording (§4.2) |
| Manual | Signed out: an official board installs; a community board shows but will not |
| Manual | Signed in: a community board installs |

## 9. Decomposition

1. **Export and import everything, with photos** — the `.pkb` format, both
   directions, and the round-trip tests. No server, no account.
2. **Catalogue schema and policies** — `profiles`, `published_boards`,
   `published_board_payloads`, and the RLS of §5.
3. **Discover reads the catalogue** — browse signed out, install official boards
   signed out, community boards prompt for sign-in.
4. **Publishing** — the flow, the photo-stripping of §4.2, the derived licence.
5. **Deletion and compliance** — account deletion, published-board anonymisation,
   privacy policy and Data Safety, now much smaller than it was.
