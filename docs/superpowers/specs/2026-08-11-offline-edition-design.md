# The offline edition

**Date:** 2026-08-11
**Status:** approved, ready to implement

## What this is

PictoKeyboard becomes an app that works entirely on one device. No server, no
account, no sign-in of any kind. Boards travel between devices as files a
caregiver sends through the share sheet, and categories move between boards
inside the app.

The marketplace and account work is not deleted. It moves to a `marketplace`
branch and keeps its issues, so it can come back as a whole rather than being
reconstructed from memory.

## Why

The marketplace carried a backend, an auth stack, an OAuth client, a web
deletion page, row-level security policies and an Edge Function — for a feature
whose user-visible half (publishing, browsing a catalogue) was never built. Every
one of those is a maintenance surface and a privacy surface, and the app's users
are disabled people and their caregivers. An app with no network account cannot
leak one.

File sharing gives back most of what the marketplace promised. A caregiver who
has built a good board can already send it to another caregiver; they simply send
it themselves instead of publishing it.

## Scope

### 1. Branches

`marketplace` is cut from the current working state (`main` plus the unmerged
Google-deletion commit) and pushed. Every cloud issue is retargeted there.

`main` then takes the offline edition as new commits on top — history is not
rewritten. The marketplace code stays reachable in `main`'s history, which is
what makes this reversible.

**Branch protection must change in the same change.** `main` currently requires
the status checks `database` and `functions`, which are the Supabase database
and Edge Function suites. Once `supabase/` is gone those checks never report, and
a required check that never reports blocks every merge to `main` forever. Both
are removed from the required set.

### 2. Removals

| Removed | Reason |
| --- | --- |
| `data/auth/` — `AccountState`, `AuthFailure`, `AuthRepository`, `SupabaseConfig` | no sign-in |
| `ui/account/` — `AccountNotice`, `AccountViewModel` | no sign-in |
| `ui/screens/AccountScreen`, `AccountForms`, `AccountDeleteDialog` | no sign-in |
| supabase-bom, supabase-auth, supabase-functions, ktor, androidx-credentials ×2, googleid | ~4 MB and the app's only auth surface |
| `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `GOOGLE_SERVER_CLIENT_ID` build config fields | nothing reads them |
| `supabase/` — migrations, Edge Function, templates, SQL and function tests | no server |
| `site/delete-account/`, `site/es/eliminar-cuenta/`, `site/assets/delete-account.js` | no account to delete |
| Discover tab and `DiscoverScreen` | a placeholder for the catalogue |
| 34 account strings × 2 locales | |
| `AccountStateTest`, `AuthFailureTest`, `SupabaseConfigTest`, `AccountFormTest`, `AccountNoticeTest`, `AccountSettingsRowTest`, `AccountDeletionTest` | test removed code |
| `database` / `functions` CI jobs and their required-check entries | nothing left to run |

**Kept deliberately:**

- **ARASAAC search and the image cache.** A setup-time fetch is not an account,
  and without it there is no way to get a pictogram into the app. The app is
  still offline in the sense that matters: it works with no network once a board
  is built, and nothing about the user leaves the device.
- **The privacy policy page and its Spanish translation**, minus the paragraph
  about accounts. An app still needs one.
- **Room, its migrations and every exported schema.** Nothing in the database was
  ever account-aware, so the schema does not move and the migration tests keep
  passing unchanged.

`ImeHasNoSupabaseTest` currently asserts the IME does not link the auth stack. It
becomes `AppHasNoAccountsTest` and asserts it of the whole application rather
than one package. That is the guard that stops any of this growing back by
accident.

### 3. Navigation

Three tabs become two: **Boards** and **Settings**. The boards empty state
pointed at Discover; it now points at the board creation flow and at import,
which are the two real ways to get a first board.

### 4. Import, export and sharing

The `.pkb` format already exists and is tested: a ZIP holding a manifest, the
board graph as JSON, and media addressed by the SHA-256 of their own bytes.
Import is additive and never overwrites, media are staged and verified before
anything is committed, and an entry name that is not a digest is refused, so a
hostile archive cannot write outside the cache.

It is reachable today only as a whole-device backup in Settings. Three changes:

**Per-board export moves from JSON to `.pkb`.** `BoardCard`'s export writes the
legacy JSON document, which carries no media — so exporting a board silently
drops every photograph and every imported drawing on it. Same format as the
whole-device backup, scoped to one board.

**A board export goes to the share sheet; the whole-device backup keeps the file
picker.** The board file is written into a `FileProvider` directory under
`cacheDir` and handed to `ShareCompat.IntentBuilder`, which reaches WhatsApp,
Gmail, Drive and Nearby Share. This is `androidx.core`; no new dependency.

The two are deliberately different, and the difference is what each one is for.
Exporting a board is something a caregiver does in order to *give it to
somebody*, so it opens the sheet and skips "save it, then find it again". A
backup is for a destination that will still exist when the phone does not — a
memory card, a Drive folder — which the share sheet cannot reach, so Settings
still writes through the system file picker. Neither grows a second button.

**Voice settings travel with a backup and not with a board.** A backup is this
caregiver's phone arriving on their next phone, so speech rate, pitch and
blind-mode should follow them. A single board is handed to someone else, and it
is not for one caregiver to reset how another's user sounds.

**Import keeps accepting legacy JSON.** Backups already written by shipped builds
have to keep loading. The picker offers `.pkb` first; a `.json` file is routed to
the existing `BackupManager`.

This closes issue #39.

### 5. Moving categories between boards

`CategoryEntity` already carries `boardId`, and pictos hang off `categoryId`
rather than the board, so a move is one `boardId` write plus a position at the
tail of the destination. Pictos follow with no work.

The interface is one action, not a menu: **Move** on the category row opens a
sheet listing the other boards, and the move is confirmed by a snackbar carrying
**Undo**. Undo restores the original board and position. A board that would be
left with no categories is still a legal board, so nothing is blocked.

The destination sheet lists only boards other than the current one. With a single
board the action is hidden rather than shown disabled — there is nowhere to move
to, and a disabled control that never becomes enabled is noise.

### 6. Theme

White paper, blue chrome. The change is confined to the chrome tokens; the
category hues are untouched, because a category's hue encodes a part of speech
and is the one place saturated colour carries meaning.

| Token | Was | Now |
| --- | --- | --- |
| `paper` | `#F3F1ED` | `#FFFFFF` |
| `card` | `#FFFFFF` | `#FFFFFF` |
| `ink` | `#191713` | `#101828` |
| `inkSoft` | `#6A645C` | blue-grey, contrast-verified |
| `line` | `#E2DED6` | blue-tinted hairline |
| `lineStrong` | `#8A8378` | blue-grey, ≥3:1 on both `paper` and `card` |
| `accent` | `#24303F` slate | `#1A56A8` |

`tile` and `onTile` do not move in any scheme. They are white and near-black
because ARASAAC artwork is black line work that a dark tile destroys; this is a
legibility constraint, not a style.

The dark scheme takes the same treatment with a lighter blue, since a deep blue
accent on near-black chrome cannot clear 4.5:1. The two high-contrast palettes
are already pure black and white and do not change — high contrast is where the
blue is deliberately spent, not saved.

The keyboard's own palette (`res/values/colors.xml`, `values-night`, and
`ime/KeyboardPalette`) follows the same tokens, so the keyboard and the app do not
wear two designs.

**Every new value is added to `TokenContrastTest`**, which fails the build on any
pair below 4.5:1 for body text or 3:1 for large text and controls. A colour that
cannot pass does not ship, and the blue is chosen to pass rather than the test
adjusted to admit it.

### 7. Play data safety

The Data Safety table becomes a single row: nothing collected, nothing shared.
The consequence worth stating is that the **Account Deletion URL field is left
empty** — Play only requires it of apps that let users create an account, and
filling it with a page that cannot delete anything is a rejection. `docs/`
carries both that table and the owner's remaining Play steps.

### 8. Issues

A `Marketplace edition` milestone collects #37, #38, #40, #41, #92 and #98, each
with a comment naming the `marketplace` branch. #39 is closed by this work.
#113 (owner setup decisions) is re-scoped, since most of what it tracked was
Supabase configuration that no longer exists on `main`.

## Testing

- `AppHasNoAccountsTest` — no auth or Supabase import anywhere in the app.
- `TokenContrastTest` — extended to every changed token, in all four palettes.
- `PkbArchiveTest`, `PkbMappingTest` — unchanged, still passing.
- New: per-board export contains exactly that board's categories, pictos and
  media, and nothing from any other board.
- New (`CategoryMoveTest`, instrumented): moving a category changes its
  `boardId`, lands after what is already on the destination, leaves the source
  board's other categories alone, **keeps its pictograms**, and is reversed
  exactly — board *and* position — by undo. The picto assertion is the one that
  matters: the obvious implementation upserts with REPLACE, which cascades and
  silently deletes every word in the category.
- New (`BoardExportTest`, instrumented): a one-board export contains that board
  and nothing from any other, and carries no voice settings; a whole-device
  export still contains every board and does carry them.
- Migration tests unchanged — the schema does not move.

Gate: `spotlessApply detekt lint testDebugUnitTest` clean, plus the instrumented
suite on API 26 and 35.

## What this gives up

Two things, stated plainly rather than discovered later.

A caregiver can no longer restore their boards by signing in on a new phone.
Their backup is a file, and a file they did not keep is a backup they do not
have. The export flow is therefore more prominent than a cloud-backed app would
need it to be, and it is the first thing the boards screen offers once a board
exists.

And there is no catalogue: a caregiver starting from nothing gets the seeded
default categories and their own work, with no library of other people's boards
to draw on. The seeded set and the category templates carry more weight than they
did, and #37 remains the answer if that turns out not to be enough.
