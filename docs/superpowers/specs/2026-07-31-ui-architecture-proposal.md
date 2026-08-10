# PictoKeyboard — information architecture proposal

**Date:** 2026-07-31 · revised 2026-08-01 after review
**Status:** filed — milestones **Domains and Discover** (#31–#41) and **Sentence help** (#42–#50)
**Scope:** the whole product surface — config app and keyboard — reorganised from zero.

This does not treat the current layout as a starting point. It lists what the
product does, adds what it must do, and then arranges all of it around one idea.

**Revision 2** folds in the review: pictos identify categories *and* boards, the
keyboard gets a board tab strip, Discover carries boards and categories only —
with a bundled seed catalogue, name search and tag filters — and symbol
libraries come out of it entirely.

---

## 1. Two products in one APK

| | Who | Where | What they need |
|---|---|---|---|
| **The keyboard** | the communicator (AAC user; may not read, may have low vision or a tremor) | any app with a text field | large targets, no chrome, nothing that can be broken by mistake |
| **The config app** | the caregiver (parent or speech therapist — **confirmed: caregiver**) | launcher icon | to build and maintain vocabulary quickly, on a phone, often one-handed, often while supervising someone |

These have almost nothing in common. Today they share a bottom navigation
metaphor and a settings screen that mixes both. They should share only the
palette, the type and the data.

---

## 2. Everything the app does today

### 2.1 Keyboard

1. Vertical category strip, tap to switch
2. Picto grid, tap to insert
3. Insert `spokenText` into the focused field
4. Optional trailing space after each word
5. Speak aloud on tap (TTS)
6. Optional captions under pictos
7. Space key
8. Backspace deletes the **whole last word**, not a character
9. Enter — honours the editor's action (send / newline)
10. Switch to another keyboard
11. Open PictoKeyboard settings from the keyboard *(⚙ — already agreed for removal)*
12. "This category is empty" hint
13. Category colour floods the board; selected chip notches into the grid
14. **Send a picto as an image** to apps that accept image input, with baked-on ARASAAC attribution *(in progress, uncommitted)*
15. **Eyes-free mode** — the whole surface becomes one gesture pad: vertical swipe = category, horizontal swipe = picto, tap = speak, double tap = write, long press = delete, two-finger double tap = leave
16. Records every inserted word (feeds "Suggested")
17. Light / dark
18. Works offline — every image is cached locally

### 2.2 Config app

**Dashboard** — setup status (enable IME, select IME), live board miniature, "build your board" CTA, tips, eyes-free gesture reference

**Categories** — list · add blank · add from template (5 built-in) · add "Suggested" from usage · edit name, colour, frame style, frame thickness · delete · reorder by drag and by move up/down

**Pictos, per category** — grid · add · edit label, spoken text, colour override · delete · reorder

**Add pictos** — ARASAAC search in the board language · multi-select · per-symbol customisation (skin tone, hair colour, black & white) · copy from another category (keeps its source colour) · import an image from the gallery with a cropper

**Settings** — language (es/en) · grid columns 2–6 · grid rows 2–8 *(dead, #17)* · show captions · space after word · speak on tap · TTS rate · TTS pitch · eyes-free mode · PIN set/remove · export JSON · import JSON

**About** — version, ARASAAC attribution and licence

**Unlock** — PIN gate on app launch

### 2.3 What is true of all of it

Single profile. **Single board.** ARASAAC only. Local only — no account, no sync,
no sharing. Two languages.

---

## 3. What is missing

Found while inventorying, independent of the redesign:

- **A category's picto cannot be chosen.** `CategoryEntity` already carries `iconArasaacId` and `iconImagePath`, and the picto is already drawn on the keyboard chip, in the category row and in the dashboard miniature — but the only thing that ever *writes* those fields is a template. A category created blank, or from usage, has no picto and no way to get one. The plumbing is finished; the control was never built.
- **No way to try the keyboard inside the app.** The caregiver must open WhatsApp to see what they built. Every AAC editor has a preview.
- **No camera.** Gallery import exists; photographing the actual cup, the actual teacher, is the single most-used custom-symbol path in real AAC use.
- **No recorded speech.** A caregiver's own recorded voice beats TTS for young children and for words TTS mispronounces. This is table stakes in the category.
- **No search over the vocabulary you already have.** Fine at 40 pictos, unusable at 400.
- **No undo.** Delete a category and its pictos cascade, silently and permanently.
- **Usage is recorded without consent or filtering** (#19) — including in password fields.
- **Usage is collected but never shown.** The caregiver would act on "words used most this week"; instead it only seeds one auto-category.
- **No profiles.** One device, one communicator.
- **About occupies a quarter of the navigation bar** for a screen read once.

---

## 4. What has to fit later

From your brief:

- **A marketplace of keyboards organised by Domain**
- **More symbol sources than ARASAAC**
- **Publishing by users** — future, not now

Designing these in now costs almost nothing. Bolting them on later costs a
second redesign.

---

## 5. The organising idea

> **A board is a Domain. Settings describe the person; boards describe the situation.**

Today there is one flat list of categories and one set of global settings. The
Domain idea only works if the unit of content is a **board** — a named,
self-contained set of categories and pictos, with its own layout.

*Home*, *School*, *Doctor's appointment*, *Restaurant*, *Grandma's house*.

That single change does four things at once:

1. **It gives the marketplace a unit.** A Domain pack installs as a board. Nothing else in the app has to know the marketplace exists.
2. **It fixes the layout settings.** Columns, rows and captions are properties of a *situation*, not of a person: a Doctor board wants 3×3 huge tiles, a chat board wants 5×5. Today they are global, so every board would have to compromise. Moving them onto the board also makes a downloaded pack self-describing — it arrives with the layout its author intended.
3. **It makes sharing possible.** Export already produces a JSON of categories and pictos. That JSON *is* a board. Give it a name, an author and a licence and it is publishable — the format barely changes.
4. **It gives the keyboard something worth switching.** At the doctor's, you switch board from inside WhatsApp and the vocabulary is right there. That is the payoff of Domains at the point of use, and it is invisible if there is only ever one board.

**The split, stated once:**

| Belongs to the **person** (global settings) | Belongs to the **situation** (per board) |
|---|---|
| voice, rate, pitch | grid columns and rows |
| speak on tap | show captions |
| space after word | default frame style and thickness |
| press feedback, high contrast | category order |
| eyes-free mode | board language |
| interface language | the categories and pictos themselves |
| PIN, backup, consent | its picto, colour and tags |

### 5.1 A picto identifies everything, at every scale

The product's whole premise is that a picture beats a word for the person using
it. That should hold all the way up the hierarchy, not just at the leaves:

| | Identified by |
|---|---|
| **Picto** | its image, plus an optional caption |
| **Category** | **a picto**, a colour, a name |
| **Board** | **a picto**, a colour, a name |

A board tab reading *Colegio* is a word. A board tab showing a school building
is not, and that is the difference between a communicator navigating
independently and one waiting to be told which tab to press. The same argument
that justified the picto grid justifies this.

Both already have `colorArgb`. Category already has the icon fields and draws
them. Board is new, so it gets both from the start.

---

## 6. The new map

**Three tabs.** Not four — About is not a destination, it is a settings row.

```
┌─ Boards ──────────── Discover ──────────── Settings ─┐

Boards                     Discover                 Settings
 └ Board detail             └ search + filters       └ Keyboard
    ├ Categories               ├ Boards              └ Voice
    │  └ Category              └ Categories          └ Language
    │     └ Pictos                 └ detail          └ Access & safety
    │        └ Add pictos              └ install     └ Backup
    │           ├ ARASAAC                            └ About
    │           ├ Photos / Camera
    │           ├ Other categories
    │           └ (future libraries)
    ├ Layout
    └ Try it
```

Everything reachable in at most three taps from a tab. Adding a symbol —
the most frequent task — is three: board → category → add.

### Why these three

Borrowed deliberately:

- **TD Snap, Proloquo2Go** — vocabulary ships curated and is customised, never built from an empty screen; editing is guarded. Our template catalogue is the same instinct, undersold.
- **CoughDrop** — boards are first-class objects with a public gallery you can copy and fork. The closest existing thing to what you described, and proof the model works in AAC.
- **Telegram sticker packs** — install a pack, use it inside a keyboard, attribution travels with the pack. Exactly the marketplace shape, already familiar to every user.
- **Google Play / Obsidian community plugins** — Browse → detail → Install → Installed, with author and licence always on the detail page.
- **Spotify** — your library first, discovery second, settings out of the way.
- **Duolingo** — onboarding is a banner that completes and disappears, never a permanent card.
- **Gboard** — the keyboard has no settings key; configuration lives in the app.

---

## 7. Screen by screen

### 7.1 Boards — the home

Your content *is* the home. No dashboard of cards about the app.

```
╔═══════════════════════════════════════╗
║  Boards                          ⋮    ║
╟───────────────────────────────────────╢
║ ┌───────────────────────────────────┐ ║   ← only while incomplete,
║ │ ① Enable PictoKeyboard   [Enable] │ ║     then gone for good
║ │ ② Select it              [Select] │ ║
║ └───────────────────────────────────┘ ║
║                                       ║
║ ┌───────────────────────────────────┐ ║
║ │ ▐▌▐▌  ▢ ▢ ▢ ▢     ● IN USE        │ ║   ← live miniature,
║ │ ▐▌▐▌  ▢ ▢ ▢ ▢                     │ ║     the real board
║ │ ⬚ Home                            │ ║   ← the board's picto
║ │ 6 categories · 84 symbols      ⋮  │ ║
║ └───────────────────────────────────┘ ║
║ ┌───────────────────────────────────┐ ║
║ │ ▐▌  ▢ ▢ ▢                         │ ║
║ │ ⬚ Doctor                          │ ║
║ │ 3 categories · 22 symbols      ⋮  │ ║
║ └───────────────────────────────────┘ ║
║                              ⊕ Board  ║
╚═══════════════════════════════════════╝
   ▣ Boards      ◈ Discover      ⚙ Settings
```

The miniature is real, not an illustration — the component already exists.
Overflow per board: **Use this board · Try it · Duplicate · Export · Delete**,
later **Publish**.

Until multi-board ships, this screen holds exactly one card. That is fine: it
is honest, it teaches the concept, and the day a second board can exist nothing
about the screen changes.

### 7.2 Board detail

```
╔═══════════════════════════════════════╗
║  ←  ⬚ Home                  ▶ Try it  ║
╟───────────────────────────────────────╢
║  [ Categories ]  [ Layout ]           ║
╟───────────────────────────────────────╢
║  ▌⬚ Food           12 symbols     ⋮   ║   ← colour bar + picto
║  ▌⬚ Feelings        8 symbols     ⋮   ║
║  ▌⬚ People         14 symbols     ⋮   ║
║  ⣿ drag to reorder                    ║
║                          ⊕ Category   ║
╚═══════════════════════════════════════╝
```

**Layout** holds what belongs to the situation: columns, rows, captions,
default frame, and **Show in keyboard** — a per-board switch controlling whether
this board gets a tab on the keyboard. A half-built board should not appear in
front of the communicator, and without this switch every experiment does.

**Try it** opens a sheet: a real text field with the real keyboard above it.
This is the missing feedback loop, and it costs one screen.

### 7.3 Editing a category — and its picto

The category editor becomes: **picto · name · colour · frame style · frame
thickness**, in that order, because the picto is what the communicator actually
navigates by.

The picto picker is the same source row as §7.4 — ARASAAC search, a photo, the
camera, or one of the category's own symbols promoted to represent it. That last
one is the fastest path and should be offered first: nine times out of ten the
right picture for *Food* is already inside *Food*.

Board editing gets the identical control, for the identical reason.

### 7.4 Category → pictos → add

Unchanged in substance, three changes in shape:

- **Add pictos gets a source row**, because ARASAAC stops being the only one:

```
╔═══════════════════════════════════════╗
║  ←  Add to “Food”                     ║
╟───────────────────────────────────────╢
║ 🔍 apple                              ║
╟───────────────────────────────────────╢
║ [ARASAAC] [Photos] [Camera] [My board]║   ← source, not a mode
╟───────────────────────────────────────╢
║  ▢ ▢ ▢ ▢                              ║
║  ▢ ▢ ▢ ▢          3 selected  [Add]   ║
╚═══════════════════════════════════════╝
```

  Today these are three separate entry points that look unrelated. As a source
  row they are one task with a choice, and a new library is a new chip.

- **Camera** joins Photos. Photographing the real object is how real boards get built.
- **Record a voice** on the picto editor, next to spoken text. Optional, overrides TTS.

### 7.5 Discover

**Two content types, nothing else: boards and categories.** No symbol libraries —
a caregiver looking for a *School* board does not want to be handed a
13 000-symbol library and told to start typing. Libraries are a source *inside*
the add-picto flow (§7.4), which is the only place the distinction matters.

```
╔═══════════════════════════════════════╗
║  Discover                             ║
╟───────────────────────────────────────╢
║ 🔍 school                             ║
║ [ Boards ] [ Categories ]             ║   ← what am I looking for
║ ⌗ Place  ⌗ People  ⌗ Situation  …     ║   ← tag filters, scrollable
╟───────────────────────────────────────╢
║ ┌───────────────────────────────────┐ ║
║ │ ⬚  School day                     │ ║   ← the board's picto
║ │    ⌗Place ⌗Situation              │ ║
║ │    5 categories · 46 symbols      │ ║
║ │    PictoKeyboard      [ Install ] │ ║
║ └───────────────────────────────────┘ ║
║ ┌───────────────────────────────────┐ ║
║ │ ⬚  Talking to Mum                 │ ║
║ │    ⌗People ⌗Home                  │ ║
║ │    4 categories · 31 symbols      │ ║
║ └───────────────────────────────────┘ ║
╚═══════════════════════════════════════╝
```

**A board and a category are both installable**, and they install differently:
a board becomes a new board; a category is added *into* a board you pick. That
second flow is the one that already exists as "copy from another category" —
same idea, wider source.

#### The catalogue has to ship in the box

Discover is empty until users publish, and users will not publish into an empty
app. So the first catalogue is **ours**: a bundled set of boards and categories,
read from an asset, indexed and filtered by exactly the same code that will
later read the network.

This is worth more than a placeholder. It means Discover:

- works on day one, offline, with no account and no backend;
- gives the caregiver somewhere to start other than an empty grid — which is the single hardest moment in every AAC product;
- makes the eventual marketplace a **data-source change, not a redesign**.

The five existing `CategoryTemplates` are the seed of the seed. They should be
promoted out of the "new category" dialog into the catalogue, and joined by real
boards — *School day*, *Doctor's appointment*, *Mealtime*, *Playground*,
*Talking to Mum* — because a board is the unit people actually want.

#### Tags

Free-text tags fragment on contact with users and make filtering useless within
a week. A small **controlled vocabulary**, grouped into facets, stays useful:

| Facet | Tags |
|---|---|
| **Place** | Home · School · Hospital · Shop · Restaurant · Outdoors · Transport |
| **People** | Family · Friends · Teacher · Carer · Doctor |
| **Situation** | Mealtime · Bedtime · Play · Appointment · Shopping · Travel · Emergency |
| **Topic** | Food · Feelings · Body · Animals · Clothes · Colours · Numbers · Time |

Multi-select within a facet, AND across facets: *Place: School* + *Situation:
Mealtime* finds the school-canteen board, which is exactly the query a parent
has. Tags are translated like any other string, and the tag *ids* are stable so
a Spanish user and an English user filter the same catalogue.

When user publishing arrives, authors pick from this list. They do not type.

### 7.6 Settings

| Group | Rows |
|---|---|
| **Keyboard** | speak on tap · space after word · press feedback · high contrast · eyes-free mode (+ gesture reference) |
| **Voice** | engine · rate · pitch · **Test voice** |
| **Language** | interface · default board language |
| **Access & safety** | PIN · what the PIN protects · **usage data on/off, off by default** |
| **Backup** | export all · import · (per-board export lives on the board) |
| **About** | version · ARASAAC attribution and licence · open-source licences · help |

Two things worth calling out. **Test voice** — rate and pitch sliders you cannot
hear are guesswork. **Usage data** — a switch, default off, plain sentence about
what is stored and where. That is how #19 gets fixed honestly rather than
quietly.

### 7.7 The keyboard

```
┌────────────────────────────────────────┐
│ ▔▔▔▔▔▔▔    ────────   ────────         │  ← 3dp colour top border, active only
│ ⬚ Casa    ⬚ Cole     ⬚ Médico     ▸    │  ← board tabs, scroll sideways
├────────────────────────────────────────┤
│  quiero  agua  por favor        🔊  ✕  │  ← sentence bar
├──┬─────────────────────────────────────┤
│▐▌│  ▢ ▢ ▢ ▢                            │
│▐▌│  ▢ ▢ ▢ ▢                            │
│▐▌│  ▢ ▢ ▢ ▢                            │
├──┴─────────────────────────────────────┤
│   ␣ space      ⌫ delete       ↵ send   │
└────────────────────────────────────────┘
```

**Two axes, and they mean different things.** Horizontal along the top: which
*board* — which situation you are in. Vertical down the left: which *category* —
which kind of word. Once learned it does not need re-learning, and it maps onto
the two things a communicator actually changes.

The tab carries the board's picto, its name and its colour as a **top border** —
3dp on the active tab, 1dp and desaturated on the rest, so the strip reads as
tabs rather than as another row of buttons. The active board's colour is already
what floods the grid below, so the border and the board agree.

Other changes:

- **Sentence bar** (#16) — mirrors the field, tap to speak the whole sentence, ✕ clears. Instant typing is preserved; the bar is a mirror, not a staging area.
- **⚙ removed**, as agreed. Configuration is the app's job.
- Eyes-free mode is untouched — a full-surface gesture pad, unaffected by all of the above.

#### The height budget, honestly

Chrome now costs roughly **board tabs 34dp + sentence bar 44dp + action row 48dp
= 126dp** before a single tile is drawn. On a 360×640dp phone with a typical
280dp keyboard that leaves about 150dp for the board — two rows of four, and
cramped ones.

The answer is that **the keyboard grows, not the grid shrinks.** IME height is
ours to set; a board with tabs should request more of it, not squeeze the thing
the product exists for. Three supporting decisions:

1. **The tab strip renders only when two or more boards are visible in the keyboard.** Single-board users pay nothing, which is also everyone on day one.
2. **Tiles need not be square.** A 4:3 tile fits a third row in the same space and loses nothing — ARASAAC artwork is not square-cropped.
3. **Rows becomes a real setting** (it is dead today, #17) and lives on the board, so a *Doctor* board can ask for 3×3 large tiles and a chat board for 5×4.

If those three are not enough on small screens, the tab strip is the piece to
make collapsible — not the grid.

---

## 8. What this implies for the data

Small, and mostly additive.

```
Board(id, name, colorArgb, position, active,
      iconArasaacId, iconImagePath,                 ← its picto
      tags,                                         ← controlled vocabulary
      showInKeyboard,                               ← appears in the tab strip
      columns, rows, showLabels, borderStyle, borderWidthDp,
      language,
      source, sourceVersion, author, licence)       ← marketplace fields

Category(… + boardId, tags)                          ← one new column + tags
        (iconArasaacId / iconImagePath already exist — now editable)

Picto(… + sourceLibrary, sourceId, licence,          ← provenance
        audioPath)                                   ← recorded speech
```

- `Category` gains `boardId`. Migration seeds one board named after the app and points every existing category at it. Nothing is lost.
- `Category.tags` is what lets a category be found in Discover on its own, not only as part of a board.
- Layout fields move off `Settings` onto `Board`, seeded from the current global values.
- `Picto` gains provenance: which library, which id, under what licence. **This is required, not optional** — see below.
- `sourceLibrary` also removes the assumption that `arasaacId` means "remote image", which is currently baked into three call sites.

---

## 9. The marketplace has a licence problem, and it is solvable

ARASAAC symbols are **CC BY-NC-SA 4.0**. The NC clause is not decorative: a
marketplace that takes money for boards containing ARASAAC symbols is
commercial use, and the SA clause means anything derived from them must carry
the same licence.

This does not block the marketplace. It shapes it:

1. **Every picto carries its source and licence.** Without per-symbol provenance you cannot tell a sellable board from an unsellable one, and the question becomes unanswerable after the fact. This is the reason `sourceLibrary`/`licence` are in the schema above rather than deferred.
2. **A board's licence is derived, not chosen.** Compute it from its contents and show it on the board and on the Discover card. A board with one ARASAAC symbol in it is NC, and the publish flow says so before the user gets attached to a price.
3. **Free sharing is unaffected.** Sharing an NC board for free, with attribution, under the same licence, is exactly what CC BY-NC-SA permits. **Ship that first.** It is the whole social value of the marketplace and it carries no licence risk at all.
4. **The bundled seed catalogue is in the clear.** It is ARASAAC artwork, distributed free, attributed, share-alike — the licence's intended case. It ships with the attribution the app already carries.
5. **Paid packs require commercially-licensable symbols.** If money is ever involved, the paid tier can only contain symbols from libraries that permit it — the caregiver's own photos, or a library licensed for commercial use. The source row in §7.4 is what makes that possible.
6. **Attribution already travels.** The baked-on credit on shared images is the same obligation at a different scale; packs need the equivalent.

My recommendation: **free sharing first, publishing second, money last if ever.**
The interesting part of your idea — a parent finding a *Doctor* board someone
already built instead of building it at 11pm the night before an appointment —
needs no payment at all.

---

## 10. What this replaces

| Today | Becomes |
|---|---|
| Dashboard of cards | Boards list; setup becomes a self-dismissing banner |
| Tips card, blind-controls card | Settings → About → Help |
| About tab | Settings → About |
| Categories tab | inside a board |
| Global columns / rows / captions | per board |
| Templates buried in "new category" | the seed of the Discover catalogue |
| Category picto settable only by a template | a picker in the category editor |
| Three unrelated ways to add a symbol | one source row |
| Export/import JSON | the same JSON, now a shareable board |

Nothing is deleted. Two screens (Dashboard, About) stop being destinations, and
one concept (board) is inserted above categories.

---

## 11. Sequencing

Two independent tracks. Each step is shippable on its own and useful on its own.

### Track A — Domains and Discover

| # | Step | Unlocks |
|---|---|---|
| **A1** · #31 | `Board` entity — picto, colour, tags, layout, `showInKeyboard` — plus migration | everything |
| **A2** · #32 | Three-tab shell; Boards list; About and help fold into Settings | the new map |
| **A3** · #33 | Board detail with Categories + Layout; **Try it** | the missing feedback loop |
| **A4** · #34 | **Picto picker** on the category and board editors | §5.1, and it closes an existing gap |
| **A5** · #35 | Source row on Add pictos; **camera** | more libraries later |
| **A6** · #36 | **Board tab strip** + sentence bar on the keyboard (#16, #17) | Domains at the point of use |
| **A7** · #37 | Bundled seed catalogue; Discover with search, Boards/Categories filter and tags | marketplace shell, no network |
| **A8** · #38 | Per-picto provenance and derived board licence | legal precondition for sharing |
| **A9** · #39 | Export/import a board or category as a pack file; share sheet | free sharing between caregivers |
| **A10** · #40 | Remote catalogue | the marketplace |
| **A11** · #41 | User publishing, authors picking from the tag vocabulary | your future feature |

Steps A1–A7 are worth doing whether or not the marketplace ever exists. A8 must
land before A9, and A9 before any money is discussed.

A4 is small and independently valuable — it can jump the queue at any point,
because the fields and the rendering already exist.

### Track B — Sentence help

Depends on **A6 (#36) only** (it needs the sentence bar to live in). Nothing else in
Track A blocks it, and it blocks nothing in Track A. See §12.

---

## 12. Milestone: sentence help

**Status:** proposed milestone, several issues. Not a single feature.

> ### Amended 2026-08-10 — simplified to one button
>
> The interaction described below was an automatic suggestion chip with a
> three-way choice, plus an intent row, plus an optional screen-reading service.
> That is a lot of surface on a keyboard whose argument is that it is simple, so
> it was cut back to **one Beautify button and one Undo** (#46).
>
> | Was | Now |
> |---|---|
> | Suggestion generated automatically, chip appears | Nothing runs until Beautify is pressed |
> | Keep / try again / send as typed | Press applies it; press again cycles variants; Undo restores |
> | Intent row, five choices (#47) | **Closed** — negation moved into the validator as a hard constraint (#45) |
> | Read the conversation, `AccessibilityService` (#49) | **Closed** — cost far exceeded the gain |
> | Privacy and Play declarations for the above (#50) | **Closed** — residue folded into #43, #46 and #48 |
> | Settings: tone, length, intent toggle, app allowlist | Settings: on/off, model state, delete weights, one privacy sentence |
>
> §§12.2–12.3 and 12.8–12.10 below are amended in place. Everything about the
> model itself — the eval set, selection, the separate process, the validator —
> is unchanged, and that is most of the milestone.

### 12.1 What it does

The board produces telegraphic language, because that is what a grid of content
words produces:

> **They type:** `yo bien querer comida`
> **Offered:** *estoy bien, pero quiero comida*

An on-device small language model conjugates the verbs, adds the function words
and fixes the order. With the conversation visible — the other person just said
*"Hola, ¿cómo estás?"* — it can also tell that *bien* is an answer rather than a
new topic, which is the difference between a good expansion and a guess.

This is well-trodden ground in AAC research and shipping products: utterance
expansion from keywords is what Google's Look to Speak and the KWickChat line of
work do. It is not speculative. What is delicate is the ethics, not the
feasibility.

### 12.2 The rule everything else follows from

> **It suggests. It never rewrites.**

Putting words into a disabled person's mouth is the central risk of this whole
idea, and it has a name in the field: the device speaking *for* the user rather
than *as* them. In the example above the model invented *pero* — a contrastive
relation the user never expressed. That one is harmless. The same mechanism can
invent politeness, hedging, agreement or refusal, and the user may not be able
to read the result to catch it.

So:

- **The raw words still go into the field the instant a picto is tapped.** Mirror semantics, already decided for the sentence bar, already the right answer. Nothing waits on the model.
- **Nothing is generated until Beautify is pressed.** The tap is the consent, and it is the only thing that starts a model call. *(Amended: the tap now happens before the user reads the result rather than after. What makes that safe is the validator below, which bounds the worst case to a clumsy rearrangement of the user's own words, plus an Undo that is always one tap away.)*
- **Two or three variants, not one.** Choosing is agency; accepting is not. Pressing Beautify again cycles them.
- **The original is always one tap away** after a beautify.
- **Off by default.** Turning it on is the caregiver's decision, and turning it off is the user's.

### 12.3 Intent — the bit the grid cannot express

> **Amended 2026-08-10: the intent row is closed (#47). The problem it names is
> real and is solved differently.** Negation — the half that reverses meaning —
> is now a hard constraint in the validator (#45): a candidate may not introduce
> a negator the user did not type, nor drop one they did. That is enforcement
> rather than an affordance, and unlike a row of buttons it works without anyone
> tapping anything. Statement-vs-question is left to inference, scored separately
> in #42, and is one Undo from reversible. The analysis below stands; only the
> remedy changed.

A picto grid loses two things that change meaning completely, and no amount of
context recovers them:

- **Is this a statement or a question?** `agua` may be *I want water*, *do you want water?*, or *there is water*.
- **Is this affirmative or negative?** *I want water* and *I don't want water* are one tapped picto apart and opposite in meaning.

A model guessing wrong on either says **the opposite of what the user meant**.
That is a different class of failure from clumsy grammar, and it is the strongest
argument for asking rather than inferring. The user is the only one who knows.

So: a small **intent row**, five choices, each a picto with a word.

| | Means | Why it is in the list |
|---|---|---|
| **Tell** | statement | the default |
| **Ask** | question | unrecoverable from content words |
| **Want** | request | the most common AAC speech act by a wide margin |
| **No** | negation / refusal | reverses meaning; must never be guessed |
| **Feel** | expressive, emphatic | carries the emphasis you asked for |

**It must not block the suggestion.** Message rate is the scarcest resource an
AAC user has — typical composition runs 10–15 words per minute against 150 for
speech — so a mandatory extra tap on every single utterance is an expensive
tax. The design that costs nothing:

> The guessed suggestion appears immediately, **and** the intent row appears
> beside it. Tapping an intent regenerates with that steer. Ignoring the row
> costs nothing and changes nothing.

That is faithful to the toggle you described and strictly cheaper than gating
generation behind a choice. **Toggle on** → the row is shown and a guess is
offered. **Toggle off** → the guess alone, inferred from the conversation, every
time. (If you would rather it genuinely block until the user picks, that is a
one-line behavioural change — but I would ship the non-blocking version first and
see whether anyone wants the other.)

Two details that matter:

- **Intent resets every utterance.** A sticky *No* would be catastrophic, and a sticky *Ask* merely baffling.
- **Intent is a cheap substitute for screen context.** One tap tells the model the speech act — which is most of what reading the conversation was for. That means **B6 delivers much of B8's value with no permission at all**, and it is a good reason to build it first and treat screen reading as an enhancement rather than a prerequisite.

### 12.4 Keep it, try again, or send it as typed

Three outcomes, always available, never a dead end:

| | Does | Note |
|---|---|---|
| **Keep** | nothing — it is already in the field | the default |
| **Try again** | regenerates, cycling variants and honouring the current intent | unlimited |
| **Send as typed** | restores the raw telegraphic words | always reachable, before and after accepting |

The last one is the one that makes the whole feature safe to ship. Ungrammatical
words the user chose beat a fluent sentence they did not, and *the user decides
which*, every time.

**One technical trap.** The raw words are already committed to the field, so
accepting a rewrite means replacing text that is already there — and reverting
means putting it back. The host app can modify that field underneath us at any
moment. Reverting must restore the exact range this keyboard committed, tracked
explicitly; a blind `deleteSurroundingText` of "however many characters we think
we wrote" will eventually eat someone's message. This belongs in the acceptance
criteria, not in a code comment.

### 12.5 A constraint you can actually enforce

"Do not add meaning" is a prompt instruction, and prompt instructions are not
guarantees. Make it a validator instead:

> **Content lemmas out ⊆ content lemmas in.** Function words — articles,
> prepositions, conjunctions, auxiliaries, inflection — may be added freely.
> Nouns, verbs, adjectives and adverbs may not.

A candidate that introduces a new content word is **discarded and regenerated**,
not shown. This is a few hundred lines with a stop-word list per language, it is
unit-testable without the model, and it converts the core safety property from a
hope into a check. It is the single most important piece of this milestone.

### 12.6 Which model — decide with an eval, not a datasheet

| | Size (Q4) | Licence | Concern |
|---|---|---|---|
| **Gemma 3 270M** | ~200 MB | Gemma Terms | Very likely too weak for reliable Spanish morphology |
| **Qwen2.5 0.5B Instruct** | ~350 MB | Apache 2.0 | Cleanest licence; Spanish at 0.5B is uneven |
| **Gemma 3 1B** | ~800 MB | Gemma Terms | Best quality of the three; heaviest, and needs ≥4 GB RAM |

My recommendation is **Gemma 3 1B where the device can take it, Qwen2.5 0.5B as
the fallback** — because an expansion the user cannot trust is worse than no
expansion, and 270M will not hold Spanish agreement and clitics reliably.

But that is a prediction, and **the first issue in this milestone should be the
eval, not the integration**: 100–150 telegraphic inputs in Spanish and English,
each with acceptable expansions, scored for grammaticality *and* for the
content-word constraint above. Cheap to build, settles the question in a day,
and it becomes the regression suite for every prompt change afterwards. Choosing
the model before building it is guessing with a 800 MB download attached.

Note the licence asymmetry: Qwen is Apache 2.0, Gemma ships under Google's own
terms which permit redistribution but attach use restrictions. Given how much
care this project already takes over licensing, that belongs in the decision.

### 12.7 Where it runs — not in the keyboard

**The model must not load into the IME process.** An `InputMethodService` is
lightweight and Android kills it readily under memory pressure; a keyboard that
dies mid-conversation is a catastrophic failure for an AAC user, and a
several-hundred-megabyte model in that process makes it likely.

So: a **separate bound service in its own process**, `:llm`, holding the model.
The IME talks to it asynchronously and **degrades to raw output whenever it is
absent, busy, still loading or killed**. The keyboard must be exactly as usable
with the feature broken as without it.

Runtime: MediaPipe LLM Inference is the least work for Gemma; llama.cpp via JNI
takes both and gives more control. Ship the weights as an **on-demand download**,
never in the APK. Check RAM and refuse gracefully on devices that cannot hold it.

Latency budget: first token under 500 ms, full sentence under 2 s on a mid-range
phone. Past that the suggestion arrives after the user has already sent, and a
suggestion nobody waits for is dead weight.

### 12.8 Reading the conversation

> **Amended 2026-08-10: closed, not deferred (#49).** The section below argued
> this was worth it because #47 had already delivered most of its value with no
> permission at all, leaving this as an optional enhancement. With #47 closed
> that argument inverts — this would become the *only* source of context, and so
> a much larger commitment than the text below assumes. Weighed again on those
> terms it does not survive: a permission that reads every word on every screen,
> plus a disclosure flow, an allowlist screen and a riskier Play review, bought
> against better expansions in a feature that already works on the typed words
> alone. Reopen only if #42 shows context-free expansion failing badly enough
> that prompt work cannot fix it — evidence, not appetite, should decide it.

The IME can already see the field it is typing into, via
`InputConnection.getExtractedText()` — that gives the draft, and in a few apps a
little more. It cannot see what the *other person* said, which is exactly the
context that makes the expansion good. That needs an
**`AccessibilityService` with `canRetrieveWindowContent`**, and that permission
is the most sensitive thing in this proposal.

It can read every word on screen in every app: the other person's private
messages, a bank balance, someone else's password as they type it. Handle it
accordingly:

- **Prominent disclosure and explicit opt-in**, separate from enabling sentence help at all. Sentence help must work — worse, but work — with screen reading off.
- **Per-app allowlist.** The caregiver names the messaging apps. Everywhere else the service reads nothing.
- **Memory only.** The last few messages, held for the length of the composition, never written to disk, never logged, never in a crash report, and dropped when the input target changes.
- **Nothing leaves the device.** The on-device model is what makes that claim true, and it is a much better reason to choose a small local model than the cost saving. It should be stated on the settings screen in one plain sentence, because it is the thing a parent will actually want to know.
- **A visible, always-available off switch** in the keyboard itself, not buried in the app.

**Play policy:** Google restricts `AccessibilityService` to genuine accessibility
uses and removes apps that stray. This use is squarely within the intent — it is
an accessibility tool, for an accessibility purpose, in an accessibility app —
which is the strongest position available, but it still needs
`isAccessibilityTool="true"`, a privacy policy that describes it, and a longer
review. Budget for the review, and do not ship it in the same release as
anything else you need out quickly.

### 12.9 Where it appears

Nowhere new. **The sentence bar from A6 is already the right home:**

Before pressing — one extra button, and nothing else changes:

```
┌────────────────────────────────────────┐
│  yo  bien  querer  comida    ✨  🔊  ✕ │  ← ✨ = Beautify
├──┬─────────────────────────────────────┤
```

After pressing — the bar shows the rephrasing, with one way back:

```
┌────────────────────────────────────────┐
│  estoy bien y quiero comida  ✨  ↩  🔊 │  ← ✨ again = next variant, ↩ = undo
├──┬─────────────────────────────────────┤
```

No second row, no chip, no popup. Beautify is one key in a row that already
exists, and Undo takes the place of the clear button while a rephrasing is
showing.

Settings gains one group, **Sentence help**: on/off · model and download state ·
delete weights · one plain sentence saying it all happens on the phone.
Everything else it needs already exists.

### 12.10 The issues in this milestone

| | Issue | Note |
|---|---|---|
| **B1** · #42 | **Eval set** — 100–150 telegraphic inputs, ES + EN, with acceptable expansions | do this first; it decides B2 |
| **B2** · #43 | Model selection and quantisation against B1 | |
| **B3** · #44 | `:llm` process, bound service, on-demand weight download, RAM capability check | |
| **B4** · #45 | **Content-word validator** and its unit tests | the safety property |
| **B4** · #45 | **Negation as a hard constraint**, on top of the content-word rule | took over #47's job |
| **B5** · #46 | **Beautify button** in the sentence bar — press to rephrase, press again to cycle, undo in one tap | depends on A6 |
| **B6** · #48 | Settings group, off by default | |
| ~~#47~~ | ~~Intent row~~ | **closed 2026-08-10** — extra surface; negation moved to B4 |
| ~~#49~~ | ~~`AccessibilityService`, per-app allowlist~~ | **closed 2026-08-10** — cost exceeded the gain |
| ~~#50~~ | ~~Privacy policy, Play declarations for the above~~ | **closed 2026-08-10** — residue folded into B2, B5, B6 |

The milestone is now **six issues, no special permissions, and one new button.**
Five of the six are about the model rather than the interface, which is the
right proportion: the hard part was never the UI, and the UI it ended up needing
is a single key.

---

## 13. Open questions

1. **Naming.** "Board" is the AAC term of art (*tablero de comunicación* in Spanish, which is the right word there). A lay parent may not know it. Alternative: "Keyboards". I recommend **Boards / Tableros**, with the marketplace calling them **Domain packs**.
2. **The tag vocabulary above is a first draft.** Four facets, 26 tags. It needs one pass from someone who has actually built boards for a child before it is frozen — once packs carry tag ids, changing them is a migration.
3. **Profiles.** Multiple boards are not multiple people. If two children share a tablet they need separate voices, PINs and usage data. Out of scope here; the `Board` entity does not preclude it.
4. **Does the communicator switch boards, or only the caregiver?** Proposed: anyone, always, PIN or not — the tab strip is navigation, not configuration.
5. **How many board tabs before the strip stops working?** Scrolling hides boards off-screen, and a communicator who cannot see a tab cannot know it exists. Proposed cap for the visible set: **5**, with the rest reachable from the app. Worth testing rather than deciding on paper.
