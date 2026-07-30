---
name: picto
description: Work with ARASAAC pictograms in PictoKeyboard — search and add pictograms, seed categories, manage the image cache, and keep licence attribution correct. Use for "add a pictogram", "add a category", "change the seed data", "the pictogram images are wrong".
---

# ARASAAC pictograms

Pictograms come from **ARASAAC** (Aragonese Centre of Augmentative and Alternative Communication, Government of Aragón), created by Sergio Palao. They are licensed **CC BY-NC-SA**. That licence is a constraint on the whole project, not a footnote — see the attribution section below before changing anything user-visible.

## Where things live

| Concern | Location |
|---|---|
| API client | `data/arasaac/ArasaacApi.kt`, `ArasaacDto.kt` |
| Fetch + map to domain | `data/arasaac/ArasaacRepository.kt` |
| Image download and on-disk cache | `data/arasaac/ImageCache.kt` |
| Render options (colour, skin, hair) | `data/arasaac/ArasaacOptions.kt` |
| Persistence | `data/db/` — `PictoEntity`, `CategoryEntity`, `UsageEntity` and their DAOs |
| Default categories and pictograms | `data/seed/DefaultData.kt`, `CategoryTemplates.kt` |

Endpoints in use:

- Search: `https://api.arasaac.org/api/pictograms/{language}/search/{text}`
- Static image: `https://static.arasaac.org/pictograms/{id}/{id}_500.png`
- Rendered image with options: `https://api.arasaac.org/api/pictograms/{id}?{options}`

## Adding a pictogram

1. Find the ARASAAC id — search the API for the word in the target language rather than guessing an id. An id is stable; a search result is not.
2. Add through the existing repository path, so the entity, the cache and the usage tracking all stay consistent. Do not write directly to the DAO from UI code.
3. Confirm the image actually resolves at the static URL before committing an id. A wrong id renders as a blank tile with no error.
4. The word the pictogram types is a **user-visible string** — it belongs in `strings.xml` for every shipped locale, not hardcoded. See the `i18n` skill.

## Adding or changing a category

Seed data lives in `data/seed/`. Changing it affects **new installs only** unless a migration is written — existing users keep the categories already in their database. Decide deliberately which you want, and say which you chose:

- New installs only: edit the seed.
- Existing users too: seed edit **plus** a Room migration. Never bump the schema version without a migration; that wipes user data, and in this app that data is someone's personal vocabulary.

Users can create their own pictograms and categories. Any seed change must not clobber or reorder user-created entries.

## Image cache

`ImageCache` keys files by id plus the render options hash (`arasaac_{id}_{optionsKey}.png`). Two rules:

- Changing the cache key format orphans every cached file. If you change it, handle the old files, or users silently re-download everything on a connection they may not have.
- The keyboard must work **offline**. A cache miss with no network is a normal state, not an error — it needs a graceful placeholder, never a crash or an empty tile with no explanation. This app is someone's voice; failing closed is not acceptable.

## Attribution and licensing (do not skip)

`NOTICE` records the ARASAAC attribution and the app displays it on screen, as ARASAAC's terms require. Consequences that are easy to get wrong:

- **The attribution must stay reachable in the UI.** A redesign that drops the credit screen is a licence violation, not a cosmetic change.
- **NC means non-commercial.** No ads, no paid tier, no in-app purchases while ARASAAC pictograms ship or are fetched. This directly constrains any future monetisation, and it is worth stating plainly before someone plans one.
- **SA means share-alike.** Derived pictogram artwork carries the same licence.
- If pictograms are ever bundled rather than fetched on demand, the attribution requirements still apply and the app-size and licensing picture both change — treat that as a decision worth writing down.

When touching anything that changes where pictograms come from, how they are displayed, or how the app is distributed, re-check the attribution is still correct and still visible, and flag it in the PR.

## Testing

- Repository and mapping logic: plain unit tests with a fake API — no network in tests.
- Cache behavior: verify miss, hit, and offline-miss paths explicitly.
- Never hit the real ARASAAC API from CI. It is a public service run by a public body; hammering it from automation is both rude and unreliable.
