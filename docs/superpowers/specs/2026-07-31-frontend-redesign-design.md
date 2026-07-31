# PictoKeyboard frontend redesign — design

**Date:** 2026-07-31
**Status:** approved (design), pending implementation plan
**Scope:** the whole frontend — the IME keyboard surface and the caregiver configuration app — plus the UI-layer structural work that turns prototype code into production code.

---

## 1. Why

Two problems, one project.

**A design problem.** The app already contains a colour system that carries meaning: the ARASAAC / Fitzgerald AAC code, in which a category's hue encodes a part of speech (people, verbs, food, feelings…). Today that meaning is spent on a 3dp frame, while a teal brand colour — which means nothing to the end user — owns every large surface and competes with it. For a user who may not read, every saturated colour that does not carry meaning is noise.

**A maturity problem.** The codebase is a prototype and was written like one. In the UI layer specifically: `notifyDataSetChanged()` on both RecyclerView adapters, a 100-line bitmap renderer performing file I/O inline in an `InputMethodService`, `Class.forName("org.pictokeyboard.ui.MainActivity")` where a class reference belongs, hardcoded hex in `colors.xml` with no `values-night`, screens that take the ViewModel directly (so nothing is previewable or testable), the ARASAAC image URL hand-built in five separate places, and single files of 645 and 486 lines. One test exists in the entire repository.

### Two users, two surfaces

| | Keyboard (IME) | Config app |
|---|---|---|
| Used by | the person with the impairment | a parent or speech therapist |
| Frequency | constantly, in every app | during setup and occasional edits |
| Priority | legibility, target size, unambiguous feedback | clarity, density, control |

These are different products for different people and should not look identical. The design makes the split explicit, down to the typeface.

---

## 2. Design direction

### 2.1 Thesis

**The chrome goes chromatically silent so that category colour can be the only saturated colour in the product.**

Chroma is a scarce resource spent exclusively on meaning. Chrome is neutral; the accent is a near-black slate rather than a hue. Nothing on screen is coloured unless the colour says something.

### 2.2 Signature — the colour flow

The element the keyboard is remembered by.

The selected category's hue does not stay inside the category strip. It bleeds across the whole surface:

- the sentence bar's left rail takes the category colour at full saturation
- a 6% wash of it sits behind the picto grid
- picto frames carry it (as they already do)
- the action row's accent picks it up

Tap *Comida* and the entire keyboard reads orange. Tap *Acciones* and it reads green.

This gives a non-reading user an instant, full-field, pre-linguistic signal of context — precisely the job AAC colour coding exists to do, currently applied to a hairline. It is the one deliberate risk in this design, and it is justified because it converts a decorative palette into a wayfinding system.

### 2.3 Colour tokens

| Token | Light | Dark | Job |
|---|---|---|---|
| `paper` | `#F3F1ED` | `#15130F` | app + keyboard chrome background |
| `tile` | `#FFFFFF` | `#FFFFFF` | picto tiles, cards |
| `ink` | `#191713` | `#F0EDE6` | primary text |
| `inkSoft` | `#6A645C` | `#A39C92` | secondary text |
| `line` | `#E2DED6` | `#2C2822` | hairlines, unpressed key fill |
| `accent` | `#24303F` | `#C9D6E6` | buttons, focus ring, switches |

`paper` is derived rather than chosen: exactly one step below tile white so white tiles float, and near-zero chroma so it clashes with none of the 26 category hues.

**Picto tiles stay white in dark mode.** ARASAAC pictograms are black line work; a dark tile destroys their legibility. In dark mode the tiles read as white cards glowing against warm black. This is a functional constraint, not a stylistic one, and must not be "fixed" later.

**Category chroma.** The existing 26-colour palette is kept unchanged — it is the AAC code and users' saved data references those ARGB values. It gains derived roles:

- `catFill` — 100%, selected category chip, blind-mode surface
- `catTint` — 24% light / 32% dark, pressed states
- `catTintSoft` — 12%, unselected category chip fill
- `catWash` — 6%, the grid background wash
- `catHairline` — 100% at 1.5dp, unselected chip outline

**High contrast** (new user setting): `paper` → pure white / pure black, `ink` → pure black / pure white, `line` → `ink`, frame widths ×1.5, labels to weight 700.

Contrast is verified in the token layer, in both schemes, to 4.5:1 for body text and 3:1 for large text and UI affordances, per the `compose-ui` skill.

### 2.4 Typography

Two families, split by *who reads them*.

**Atkinson Hyperlegible** (Braille Institute; engineered for low vision — unmistakable `I`/`l`/`1` and `b`/`d`/`p`/`q`) sets everything the disabled user reads: picto labels, category names on the keyboard, the sentence bar, the blind-mode caption. It also sets the app's headings, which ties the caregiver's tool visually to the surface it configures.

**Figtree** handles the caregiver's admin chrome: body copy, labels, numbers, settings rows.

The face engineered for low vision owns the user's surface; a neutral workhorse owns the admin surface.

Both are OFL and confirmed reachable via Google Fonts. They are **bundled into `res/font`**, not loaded through the Play Services font provider — the app is offline-first by design and must not depend on a downloadable-font round trip.

Scale (sp, all scaling with the system font setting):

| Role | Size / weight | Family |
|---|---|---|
| display | 30 / 700 | Atkinson |
| headline | 24 / 700 | Atkinson |
| title | 19 / 700 | Atkinson |
| body | 16 / 400 | Figtree |
| label | 14 / 500 | Figtree |
| caption | 13 / 400 | Figtree |
| **picto label** | **14 / 700** | **Atkinson** |

The picto label rises from 12sp and stops being `maxLines=1` with ellipsis — truncating the word the user is trying to say is a defect, and `maxLines=1` on content the user must read violates the `compose-ui` skill. It wraps to two lines and shrinks to fit instead.

---

## 3. The keyboard

```
┌────────────────────────────────────────────────┐
│▐  yo · comer · galleta        🔊    ⌫    ✕    │ sentence bar  56dp
│▐← rail in live category colour                 │
├──────┬─────────────────────────────────────────┤
│ ███  │ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐    │
│ PER  │ │  💧  │ │  🍞  │ │  🥛  │ │  🍎  │    │
│ SONAS│ │ agua │ │ pan  │ │leche │ │manzana│   │
│ ─────│ └──────┘ └──────┘ └──────┘ └──────┘    │  6% category
│ ACC  │ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐    │  wash behind
│ IONES│ │  🍌  │ │  🍪  │ │  🍇  │ │  🍊  │    │  the grid
│ ─────│ └──────┘ └──────┘ └──────┘ └──────┘    │
├──────┴─────────────────────────────────────────┤
│      ␣ espacio             ↵ enviar      🌐    │ action row  56dp
└────────────────────────────────────────────────┘
```

### 3.1 Sentence bar (new)

**Mirror semantics.** Tapping a picto still commits its word into the host field immediately — the core promise ("types into any app as you tap") is unchanged, and no phrase can ever be stranded unsent. The bar is a readout of what has been built.

| Control | Effect |
|---|---|
| the phrase | words joined with `·`, scrolled to the end, most recent visible |
| `🔊` | re-speaks the whole phrase, each word in its own language via `TtsManager.speakSequence` |
| `⌫` | removes the last word from **both** the bar and the field |
| `✕` | clears the bar only; the field is left untouched |

The bar resets when the input target changes (`onStartInput` with a different field), because a phrase belonging to a previous app must not leak into the next one.

**Backspace moves from the action row into the bar.** This is an information-architecture correction, not a space saving: word-level operations (speak / undo / clear) belong with the phrase; field-level operations (space / enter) belong in the action row. `⌫` continues to delete a whole word via the existing `deleteLastWord()` / `trailingWordLength()`.

### 3.2 Action row

Five identical grey buttons become three with real hierarchy: **space** as the wide primary, **enviar** distinct, **globe** recessive at the trailing edge.

**The ⚙ key is removed.** It launched the full caregiver app straight from the end user's keyboard — an escape hatch mid-conversation for a user who cannot easily recover from it. Caregivers open PictoKeyboard from the launcher. The `Class.forName(...)` reflection goes with it.

### 3.3 Category spine

- **Selected:** `catFill` at 100%, auto-contrast text, and a notch pointing into the grid.
- **Unselected:** `catTintSoft` fill plus a `catHairline` outline.

Today unselected chips use a 20% tint *and* a full-weight coloured stroke, so all eight categories shout equally and selection is hard to read.

### 3.4 Press feedback (new)

There is currently **no press feedback of any kind** on picto tiles. For a motor-impaired user this means no confirmation that a tap landed.

Every tile and key gains:

- a pressed state — scale-down, `catTint` flood, 1.5dp inset
- `HapticFeedbackConstants.KEYBOARD_TAP` on key-down, honouring the system haptic setting and a new in-app toggle

### 3.5 Blind mode

Currently a flat `#1565C0` rectangle drawn in `onDraw`. It becomes the same thesis at maximum scale:

- the surface fills with the **current category's colour** at full saturation
- the current picto renders large above the caption
- caption and hint auto-contrast against that colour

A low-vision user gets a full-screen colour cue of which category they are in. The gesture contract is unchanged.

### 3.6 Bug: the dead "Visible rows" setting

`Settings.gridRows` is written by the Settings slider and by `SettingsStore.setGridRows`, but **`PictoKeyboardService` never reads it** — `kb_body_height` is a hardcoded `280dp`. The slider currently does nothing.

`gridRows` will actually drive the keyboard body height. This is a behaviour change and ships in its own commit, separate from the refactor that makes it reachable.

---

## 4. The config app

### 4.1 Dashboard

The gradient hero and the two big-number stat cards are the template answer and are removed.

**The hero becomes a live miniature of the real board**, built from the user's actual categories and pictos. The most characteristic thing in this product's world is the board, so the hero shows the board. It doubles as setup confirmation — *this is what they will see* — and taps through to the editor.

Scope discipline: this is a Compose component that reads real data and shares the **token layer** (colours, frames, type) with the IME. It does **not** share a rendering path with the View-based keyboard; that would be over-engineering for a hero.

Counts fold into its caption (`7 categorías · 84 pictos`), so the two stat cards are removed entirely.

The setup checklist stays — it is genuinely useful and reads live device state — but goes quiet, collapsing to a single line once the keyboard is enabled and selected.

### 4.2 Categories

- the whole row opens the category, because that is the common action
- edit and delete move into an overflow menu
- the category colour becomes a full-height left bar, so the list itself reads as the colour code
- names get room, so "Sentimientos" stops breaking as "Sentimient / os"

### 4.3 Settings

One flat wall of sliders and switches becomes grouped cards:

**Idioma · Teclado · Voz · Accesibilidad · Seguridad · Copia de seguridad**

New controls: **high contrast** and **haptic feedback**. The "Visible rows" slider becomes functional (§3.6).

### 4.4 Dialogs

The oversized `AlertDialog`s that currently scroll internally — the new-category chooser and the picto detail — become bottom sheets. Small confirmations stay dialogs.

---

## 5. Structural work (prototype → production)

Confined to the UI layer. Per the `refactor` skill: **behaviour-preserving and behaviour-changing changes never share a commit**, and no refactor ships in the same change as a feature.

| # | Change | Kind |
|---|---|---|
| 1 | Characterization tests over the UI-layer logic being disturbed | new tests |
| 2 | `ListAdapter` + `DiffUtil` replacing `notifyDataSetChanged()` in `PictoAdapter` and `CategoryAdapter` | behaviour-preserving |
| 3 | `labeledImage` / `sendPictoAsImage` extracted from `PictoKeyboardService` into `ime/PictoImageSharer`, file I/O moved off the main thread | behaviour-preserving |
| 4 | Screens take state + lambdas instead of `ConfigViewModel`; wiring moves to the `NavHost` | behaviour-preserving |
| 5 | One `ArasaacUrls` helper replacing the URL hand-built in five places | behaviour-preserving |
| 6 | `AddPictosScreen.kt` (645 lines) and `CategoriesScreen.kt` (486) split by component | behaviour-preserving |
| 7 | Token layer + `values-night` + bundled fonts; all screens restyled | visual |
| 8 | Sentence bar, press feedback + haptics, high contrast, ⚙ removal, `gridRows` fix | behaviour-changing |

Item 4 is the keystone: extracting logic out of framework classes is what makes previews and tests possible at all, and the `refactor` skill names that untestability as the thing to fix first.

### Quality floor

Applied throughout, per `compose-ui`:

- no hardcoded `Color(0xFF…)` in any Composable — this removes the existing `Color(0xFFFFFFFF)`, `Color(0x33000000)` and `Color.Black` literals in `Components.kt` and the raw hex in `colors.xml`
- 48dp minimum touch targets
- text in sp, everything else in dp
- `contentDescription` on meaningful icons, explicit `null` on decorative ones
- `Modifier.toggleable` / `selectable` with a `Role` on state-bearing controls, not bare `clickable`
- stable parameters; `key` on every lazy item
- `@Preview` on every non-trivial Composable covering default, empty, dark, and `fontScale = 2f`

### Localization

Spanish strings land in the same commit as the English ones. `strings.xml` is **already drifting** — 175 keys in `values/` against 170 in `values-es/` — and that gap is closed as part of this work. The `strings-sync` hook reports drift on every edit; it must be silent before any PR merges.

### Verification

- unit tests green, including the new characterization tests
- `./gradlew lintDebug detekt` clean, with `MissingTranslation` / `ExtraTranslation` unsuppressed
- the app run on a device or emulator and each redesigned surface exercised by hand — unit tests do not catch lifecycle, threading or IME integration regressions, which are exactly what this kind of work breaks
- `a11y-reviewer` run before the final PR
- `ime-reviewer` run on any PR touching `PictoKeyboardService`

---

## 6. Delivery

Work traces to GitHub issues and ships as sequenced PRs, each with `Closes #n` so the board automation advances the card.

| PR | Contents | Kind |
|---|---|---|
| 1 | Structural items 1–6 | behaviour-preserving |
| 2 | Token layer, fonts, `values-night`, all screens restyled (item 7) | visual |
| 3 | Sentence bar, press feedback + haptics, high contrast, ⚙ removal, `gridRows` fix (item 8) | behaviour-changing |

PR 1 must be green and merged before PR 2 starts: the `refactor` skill's rule is to not begin a second seam before the first is green and committed.

---

## 7. Explicitly out of scope

- the psychologist web portal (the next phase; its seam remains the JSON export/import in `data/backup`)
- Room migrations and embedding custom images in backups — both real, both flagged in project memory, neither a frontend concern
- dwell / hold-to-activate and repeat-tap lockout — considered and declined for this round
- key-size presets — considered and declined; the existing columns and rows sliders stay, with rows made functional
- any change to the 26-colour category palette values, which users' saved data depends on
