# UI Layer Restructure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the UI layer testable and previewable without changing a single user-visible behaviour, so the visual redesign (#15) and the new keyboard behaviour (#16) land on solid code.

**Architecture:** Pull pure logic out of framework classes (`InputMethodService`, `ViewModel`, Composables) into plain Kotlin functions that take their dependencies as parameters, characterize each with tests before touching it, then restructure around those seams. Screens stop taking `ConfigViewModel` and start taking state + lambdas, which is what makes `@Preview` and unit tests possible at all.

**Tech Stack:** Kotlin, Jetpack Compose, RecyclerView `ListAdapter`/`DiffUtil`, JUnit 4, Room, Coil.

**Issue:** Closes #14
**Spec:** `docs/superpowers/specs/2026-07-31-frontend-redesign-design.md`

---

## Ground rules for this plan

Read these before Task 1. They come from the repo's `refactor` and `compose-ui` skills and they are what makes this diff reviewable:

1. **Nothing in this plan changes behaviour.** If you find a bug, do not fix it — write a characterization test that encodes the *buggy* behaviour, note it in the commit message, and leave it. #17 already tracks the one known bug (the dead `gridRows` slider); do not fix it here.
2. **A characterization test must pass against unmodified code.** If it fails the first time you run it, the test is wrong about reality, not the code.
3. **Commit after every green step.** Small reversible commits are the point.
4. **Do not start a task before the previous one is green and committed.**
5. The `format-on-edit` hook reformats Kotlin on save; do not fight it.

Verification command used throughout:

```bash
./gradlew :app:testDebugUnitTest
```

Full gate before the PR:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug detekt
```

---

## File structure

**Created**

| File | Responsibility |
|---|---|
| `app/src/main/java/org/pictokeyboard/data/arasaac/ArasaacUrls.kt` | The one place an ARASAAC image URL is built |
| `app/src/main/java/org/pictokeyboard/ui/ListReorder.kt` | Pure list-reorder helper, lifted out of `ConfigViewModel` |
| `app/src/main/java/org/pictokeyboard/ime/PictoImageSharer.kt` | Renders and writes the shareable picto image, off the main thread |
| `app/src/main/java/org/pictokeyboard/ui/screens/categories/CategoryRow.kt` | One category row |
| `app/src/main/java/org/pictokeyboard/ui/screens/categories/CategoryDialogs.kt` | New-category chooser, edit and delete dialogs |
| `app/src/main/java/org/pictokeyboard/ui/screens/addpictos/SearchResultsGrid.kt` | ARASAAC results grid |
| `app/src/main/java/org/pictokeyboard/ui/screens/addpictos/PictoDetailDialog.kt` | ARASAAC detail/customize dialog + swatches |
| `app/src/main/java/org/pictokeyboard/ui/screens/addpictos/CustomImageDialog.kt` | Cropped-image details dialog |
| `app/src/main/java/org/pictokeyboard/ui/screens/addpictos/ImportFromCategoriesDialog.kt` | Cross-category picto picker |
| `app/src/test/java/org/pictokeyboard/data/arasaac/ArasaacUrlsTest.kt` | |
| `app/src/test/java/org/pictokeyboard/ui/ListReorderTest.kt` | |
| `app/src/test/java/org/pictokeyboard/ime/WordDeletionTest.kt` | |
| `app/src/test/java/org/pictokeyboard/ime/PictoDiffTest.kt` | |

**Modified**

| File | Change |
|---|---|
| `ime/PictoAdapter.kt` | → `ListAdapter` + `DiffUtil` |
| `ime/CategoryAdapter.kt` | → `ListAdapter` + `DiffUtil` |
| `ime/PictoKeyboardService.kt` | image-sharing code removed; `Class.forName` removed |
| `ui/MainActivity.kt` | owns all ViewModel wiring |
| `ui/ConfigViewModel.kt` | `movedBy` moves out |
| `ui/screens/*.kt` | screens take state + lambdas |

---

## Task 1: One home for ARASAAC image URLs

There are **two competing sources of truth** for ARASAAC image URLs.

`ImageCache` already has a companion object that builds them — `arasaacImageUrl(id)` and `imageUrl(id, options)` — with three callers. Meanwhile **eight** other sites ignore it and hand-build the string themselves, in two sizes. A typo in any one of the eight is a silently broken image, and the helper being buried in a *cache* class is why nobody found it.

This task creates one home, `ArasaacUrls`, and deletes the `ImageCache` companion helpers.

**Call sites, verified:**

| Kind | Site |
|---|---|
| hand-built, 500px | `ime/CategoryAdapter.kt:62` |
| hand-built, 500px | `ime/PictoAdapter.kt:76` |
| hand-built, 500px | `ui/screens/PictosScreen.kt:171` |
| hand-built, 500px | `ui/screens/PictosScreen.kt:220` |
| hand-built, 500px | `ui/screens/AddPictosScreen.kt:579` |
| hand-built, 500px | `ui/screens/CategoriesScreen.kt:205` |
| hand-built, 300px | `ui/screens/CategoriesScreen.kt:311` (`arasaacThumb` helper) |
| hand-built, inside the helper | `data/arasaac/ImageCache.kt:94`, `:99` |
| via companion | `data/arasaac/ArasaacRepository.kt:17` → `ImageCache.arasaacImageUrl` |
| via companion | `data/arasaac/ImageCache.kt:40` → `imageUrl` |
| via companion | `ui/screens/AddPictosScreen.kt:349` → `ImageCache.imageUrl` |

**Files:**
- Create: `app/src/main/java/org/pictokeyboard/data/arasaac/ArasaacUrls.kt`
- Create: `app/src/test/java/org/pictokeyboard/data/arasaac/ArasaacUrlsTest.kt`
- Modify: every file in the table above

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/org/pictokeyboard/data/arasaac/ArasaacUrlsTest.kt`:

```kotlin
package org.pictokeyboard.data.arasaac

import org.junit.Assert.assertEquals
import org.junit.Test

class ArasaacUrlsTest {

    @Test
    fun `image url uses the 500px asset by default`() {
        assertEquals(
            "https://static.arasaac.org/pictograms/2462/2462_500.png",
            ArasaacUrls.image(2462),
        )
    }

    @Test
    fun `image url honours an explicit size`() {
        assertEquals(
            "https://static.arasaac.org/pictograms/2462/2462_300.png",
            ArasaacUrls.image(2462, ArasaacUrls.THUMB),
        )
    }

    @Test
    fun `customized url targets the api host and carries the options query`() {
        val options = ArasaacOptions(skin = "black", hair = "red")
        assertEquals(
            "https://api.arasaac.org/api/pictograms/2462?skin=black&hair=red",
            ArasaacUrls.customized(2462, options),
        )
    }

    @Test
    fun `customized url with default options has no query string`() {
        assertEquals(
            "https://api.arasaac.org/api/pictograms/2462",
            ArasaacUrls.customized(2462, ArasaacOptions()),
        )
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*ArasaacUrlsTest*'`
Expected: FAIL — `Unresolved reference: ArasaacUrls`

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/org/pictokeyboard/data/arasaac/ArasaacUrls.kt`:

```kotlin
package org.pictokeyboard.data.arasaac

/**
 * Builds ARASAAC image URLs. Every call site goes through here so the path
 * shape lives in exactly one place -- a typo in a hand-built URL shows up only
 * as a silently missing picture.
 *
 * Plain pictograms come from the static CDN; customized ones (skin/hair/colour)
 * are rendered on demand by the API host, which is the only one that serves
 * them.
 */
object ArasaacUrls {

    /** Full-size asset used for keys and detail views. */
    const val FULL = 500

    /** Smaller asset used for template and preview thumbnails. */
    const val THUMB = 300

    fun image(id: Int, size: Int = FULL): String =
        "https://static.arasaac.org/pictograms/$id/${id}_$size.png"

    fun customized(id: Int, options: ArasaacOptions): String =
        "https://api.arasaac.org/api/pictograms/$id${options.query()}"
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*ArasaacUrlsTest*'`
Expected: PASS, 4 tests

- [ ] **Step 5: Replace all eight call sites**

`ime/CategoryAdapter.kt:62` — inside the `iconModel` `when`:

```kotlin
category.iconArasaacId != null -> ArasaacUrls.image(category.iconArasaacId)
```

`ime/PictoAdapter.kt:76`:

```kotlin
} else if (picto.arasaacId != null) {
    image.load(ArasaacUrls.image(picto.arasaacId))
}
```

`ui/screens/PictosScreen.kt:171` and `:220` — both read:

```kotlin
?: picto.arasaacId?.let { ArasaacUrls.image(it) }
```

`ui/screens/CategoriesScreen.kt:205`:

```kotlin
?: category.iconArasaacId?.let { ArasaacUrls.image(it) }
```

`ui/screens/CategoriesScreen.kt:310-311` — delete the private helper entirely:

```kotlin
private fun arasaacThumb(id: Int): String =
    "https://static.arasaac.org/pictograms/$id/${id}_300.png"
```

and replace its two uses with `ArasaacUrls.image(it, ArasaacUrls.THUMB)`:

```kotlin
thumbs = suggested.mapNotNull { it.arasaacId }.take(4).map { ArasaacUrls.image(it, ArasaacUrls.THUMB) },
```

```kotlin
thumbs = template.pictos.take(4).map { ArasaacUrls.image(it.arasaacId, ArasaacUrls.THUMB) },
```

`ui/screens/AddPictosScreen.kt:579`:

```kotlin
?: picto.arasaacId?.let { ArasaacUrls.image(it) }
```

`ui/screens/AddPictosScreen.kt:349` — currently `ImageCache.imageUrl(arasaacId, options)`:

```kotlin
model = ArasaacUrls.customizedOrPlain(arasaacId, options),
```

`data/arasaac/ArasaacRepository.kt:17` — currently `ImageCache.arasaacImageUrl(dto.id)`:

```kotlin
imageUrl = ArasaacUrls.image(dto.id),
```

`data/arasaac/ImageCache.kt` — **delete the whole `companion object`** (lines 91-103), which is `arasaacImageUrl` and `imageUrl`. Then fix its one internal caller at line 40:

```kotlin
val url = ArasaacUrls.customizedOrPlain(id, options)
```

Add `import org.pictokeyboard.data.arasaac.ArasaacUrls` to each file outside the `data.arasaac` package.

Note the extra function this requires on `ArasaacUrls` — `customizedOrPlain` — which replaces `ImageCache.imageUrl`'s branch. Add it in Step 3 alongside the others:

```kotlin
    /**
     * The customized image when [options] asks for one, the plain CDN asset
     * otherwise. Only the API host renders customizations.
     */
    fun customizedOrPlain(id: Int, options: ArasaacOptions = ArasaacOptions()): String =
        if (options.isCustomized) customized(id, options) else image(id)
```

and cover it in Step 1's test file:

```kotlin
    @Test
    fun `customizedOrPlain falls back to the plain cdn asset`() {
        assertEquals(
            "https://static.arasaac.org/pictograms/2462/2462_500.png",
            ArasaacUrls.customizedOrPlain(2462, ArasaacOptions()),
        )
    }

    @Test
    fun `customizedOrPlain uses the api host when options are customized`() {
        assertEquals(
            "https://api.arasaac.org/api/pictograms/2462?skin=black",
            ArasaacUrls.customizedOrPlain(2462, ArasaacOptions(skin = "black")),
        )
    }
```

- [ ] **Step 6: Verify nothing hand-builds a URL any more**

Run:
```bash
grep -rn "static.arasaac.org\|api.arasaac.org/api/pictograms" app/src/main/java/ | grep -v "ArasaacUrls.kt\|ArasaacApi.kt\|ArasaacOptions.kt"
```
Expected: no output. (`ArasaacApi.kt` keeps its Retrofit path, `ServiceLocator` keeps the `baseUrl` — those are not image URLs.)

Then confirm the old helpers are gone:
```bash
grep -rn "arasaacImageUrl\|ImageCache.imageUrl" app/src/main/java/ app/src/test/java/
```
Expected: no output.

- [ ] **Step 7: Build and test**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/org/pictokeyboard/data/arasaac/ \
        app/src/test/java/org/pictokeyboard/data/arasaac/ \
        app/src/main/java/org/pictokeyboard/ime/ \
        app/src/main/java/org/pictokeyboard/ui/screens/
git commit -m "Build ARASAAC image URLs in one place

There were two competing sources of truth. ImageCache had a companion
object that built these URLs, with three callers -- while eight other
sites ignored it and hand-built the string themselves, in two sizes. A
typo in any of the eight showed up only as a silently missing picture,
and the helper being buried in a cache class is why nobody found it.

ArasaacUrls is now the only one, and ImageCache's companion is gone.

Behaviour-preserving: the produced URLs are byte-identical."
```

---

## Task 2: Characterize whole-word deletion

`PictoKeyboardService.trailingWordLength` is the only piece of the IME with real logic, it is already `internal` on the companion, and it is untested. Task 3 moves code out of this service, so pin its behaviour first.

**Files:**
- Create: `app/src/test/java/org/pictokeyboard/ime/WordDeletionTest.kt`
- Read only: `ime/PictoKeyboardService.kt:536-541`

- [ ] **Step 1: Write the characterization test**

Create `app/src/test/java/org/pictokeyboard/ime/WordDeletionTest.kt`:

```kotlin
package org.pictokeyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Characterization tests for whole-word backspace. These describe what the code
 * does today, including anything that looks surprising -- they exist to catch a
 * change in behaviour, not to assert what the behaviour ought to be.
 */
class WordDeletionTest {

    private fun lengthOf(text: String) =
        PictoKeyboardService.trailingWordLength(text)

    @Test
    fun `deletes the word and the space the keyboard appended after it`() {
        assertEquals(4, lengthOf("yo comer pan "))
    }

    @Test
    fun `deletes a bare trailing word when no space follows`() {
        assertEquals(3, lengthOf("yo comer pan"))
    }

    @Test
    fun `deletes every trailing space plus the word before them`() {
        assertEquals(5, lengthOf("yo pan   "))
    }

    @Test
    fun `empty input deletes nothing`() {
        assertEquals(0, lengthOf(""))
    }

    @Test
    fun `whitespace-only input is consumed entirely`() {
        assertEquals(3, lengthOf("   "))
    }

    @Test
    fun `a single word is consumed entirely`() {
        assertEquals(3, lengthOf("pan"))
    }

    @Test
    fun `newlines count as whitespace`() {
        assertEquals(4, lengthOf("hola\n"))
    }
}
```

- [ ] **Step 2: Run against unmodified code**

Run: `./gradlew :app:testDebugUnitTest --tests '*WordDeletionTest*'`
Expected: PASS, 7 tests. **If any test fails, the test is wrong — correct the test to match reality, do not touch the production code.**

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/org/pictokeyboard/ime/WordDeletionTest.kt
git commit -m "Characterize whole-word backspace before restructuring the IME

Pins trailingWordLength so the extraction in the next commit is provably
behaviour-preserving."
```

---

## Task 3: Lift list reordering out of ConfigViewModel

`ConfigViewModel.movedBy` (line 237) is a pure function trapped in a `ViewModel`, so it cannot be tested. It is also subtly tricky — the swap uses a nested `also` — which is exactly the kind of code that deserves a test.

**Files:**
- Create: `app/src/main/java/org/pictokeyboard/ui/ListReorder.kt`
- Create: `app/src/test/java/org/pictokeyboard/ui/ListReorderTest.kt`
- Modify: `ui/ConfigViewModel.kt:90-93`, `:198-201`, `:236-243`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/org/pictokeyboard/ui/ListReorderTest.kt`:

```kotlin
package org.pictokeyboard.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListReorderTest {

    private val list = listOf("a", "b", "c")

    @Test
    fun `moving up swaps with the previous item`() {
        assertEquals(listOf("a", "c", "b"), movedBy(list, { it == "c" }, up = true))
    }

    @Test
    fun `moving down swaps with the next item`() {
        assertEquals(listOf("b", "a", "c"), movedBy(list, { it == "a" }, up = false))
    }

    @Test
    fun `the first item cannot move up`() {
        assertNull(movedBy(list, { it == "a" }, up = true))
    }

    @Test
    fun `the last item cannot move down`() {
        assertNull(movedBy(list, { it == "c" }, up = false))
    }

    @Test
    fun `an absent item returns null`() {
        assertNull(movedBy(list, { it == "z" }, up = true))
    }

    @Test
    fun `an empty list returns null`() {
        assertNull(movedBy(emptyList<String>(), { true }, up = true))
    }

    @Test
    fun `the source list is not mutated`() {
        val original = listOf("a", "b", "c")
        movedBy(original, { it == "a" }, up = false)
        assertEquals(listOf("a", "b", "c"), original)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*ListReorderTest*'`
Expected: FAIL — `Unresolved reference: movedBy`

- [ ] **Step 3: Move the function, unchanged**

Create `app/src/main/java/org/pictokeyboard/ui/ListReorder.kt`:

```kotlin
package org.pictokeyboard.ui

/**
 * Returns [list] with the item matching [match] swapped one step toward the
 * start ([up]) or the end, or null when it cannot move -- no match, or already
 * at that end. Callers treat null as "nothing to persist".
 */
fun <T> movedBy(list: List<T>, match: (T) -> Boolean, up: Boolean): List<T>? {
    val i = list.indexOfFirst(match)
    if (i < 0) return null
    val j = if (up) i - 1 else i + 1
    if (j !in list.indices) return null
    return list.toMutableList().apply { this[i] = this[j].also { this[j] = this[i] } }
}
```

- [ ] **Step 4: Delete the private copy from ConfigViewModel**

In `ui/ConfigViewModel.kt`, delete lines 236-243 (the `private fun <T> movedBy` block and its KDoc). The two call sites at `:91` and `:199` need no edit — the top-level function is in the same package and resolves identically.

- [ ] **Step 5: Run the tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS, all tests including the 7 new ones

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/org/pictokeyboard/ui/ListReorder.kt \
        app/src/test/java/org/pictokeyboard/ui/ListReorderTest.kt \
        app/src/main/java/org/pictokeyboard/ui/ConfigViewModel.kt
git commit -m "Lift movedBy out of ConfigViewModel so it can be tested

Pure function, moved verbatim to a top-level declaration in the same
package, so both call sites resolve unchanged."
```

---

## Task 4: Extract picto image sharing out of the IME

`PictoKeyboardService.labeledImage` (lines 282-387) allocates two bitmaps, draws a card, and writes a file **on the main thread, inside an `InputMethodService`**. A slow write janks the keyboard of a user who may already struggle to tap accurately. It is also 105 lines of graphics code in a class whose job is input.

This task moves it and makes the file write suspend. The rendering itself is copied verbatim.

**Files:**
- Create: `app/src/main/java/org/pictokeyboard/ime/PictoImageSharer.kt`
- Modify: `ime/PictoKeyboardService.kt` — delete lines 211-391, add the call

- [ ] **Step 1: Create the sharer with the rendering moved verbatim**

Create `app/src/main/java/org/pictokeyboard/ime/PictoImageSharer.kt`. Copy the bodies of `sendPictoAsImage` and `labeledImage` from `PictoKeyboardService.kt` exactly as they are — same paint sizes, same `pad`/`corner`/`strokeWidth` ratios, same shrink-to-fit loops, same output filename — changing only what is listed below:

- the class takes `context: Context` instead of using service `this`
- `labeledImage` becomes `private suspend fun` and wraps its body in `withContext(Dispatchers.IO)`
- `getString(...)` becomes `context.getString(...)`
- `filesDir` becomes `context.filesDir`
- `packageName` becomes `context.packageName`
- `toast(resId)` becomes an `onError: (Int) -> Unit` callback so the class does no UI

```kotlin
package org.pictokeyboard.ime

import android.content.ClipDescription
import android.content.Context
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.pictokeyboard.R
import org.pictokeyboard.data.db.PictoEntity
import java.io.File

/**
 * Sends a pictogram into the focused field as an image, via the Commit Content
 * API -- the mechanism keyboards use for GIFs and stickers.
 *
 * This lives outside [PictoKeyboardService] because rendering a 512x512 card
 * and writing it to disk has no business happening on the main thread of an
 * InputMethodService: a slow write janks the keyboard of a user who may already
 * struggle to tap accurately.
 */
class PictoImageSharer(private val context: Context) {

    /**
     * Renders [picto] as a captioned card and commits it to [editorInfo]'s
     * field. [frameColor] frames the card like the on-screen key. [attribution],
     * when non-null, is drawn beneath the caption and copied into the clip
     * description so the ARASAAC licence credit travels with the picture.
     *
     * Calls [onError] with a string resource when the picto has no image yet or
     * the field cannot accept rich content.
     */
    suspend fun send(
        picto: PictoEntity,
        connection: InputConnection,
        editorInfo: EditorInfo,
        frameColor: Int,
        attribution: String?,
        onError: (Int) -> Unit,
    ) {
        val source = picto.imagePath?.let { File(it) }
        if (source == null || !source.exists()) {
            onError(R.string.img_not_ready)
            return
        }
        val supported = EditorInfoCompat.getContentMimeTypes(editorInfo)
        if (supported.isEmpty()) {
            onError(R.string.img_unsupported)
            return
        }
        fun accepts(mime: String) = supported.any { ClipDescription.compareMimeTypes(mime, it) }
        val isWhatsApp = editorInfo.packageName?.startsWith("com.whatsapp") == true
        val mime = when {
            isWhatsApp && accepts("image/webp") -> "image/webp"
            accepts("image/png") -> "image/png"
            accepts("image/webp") -> "image/webp"
            accepts("image/*") -> "image/png"
            else -> null
        }
        if (mime == null) {
            onError(R.string.img_unsupported)
            return
        }

        val caption = picto.label.ifBlank { picto.spokenText }.trim()
        val file = labeledImage(source, picto.id, caption, frameColor, attribution, mime)
        if (file == null) {
            onError(R.string.img_unsupported)
            return
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val clipLabel = picto.label.ifBlank { picto.spokenText }
        val description = ClipDescription(
            if (attribution != null) "$clipLabel — $attribution" else clipLabel,
            arrayOf(mime),
        )
        InputConnectionCompat.commitContent(
            connection,
            editorInfo,
            InputContentInfoCompat(uri, description, null),
            InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
            null,
        )
    }

    // labeledImage: paste the runCatching expression from
    // PictoKeyboardService.labeledImage here verbatim, with filesDir replaced
    // by context.filesDir and the one return@runCatching edit described below.
    // Do not adjust any of the size ratios -- they are load-bearing for the
    // WhatsApp sticker format.
    private suspend fun labeledImage(
        source: File,
        id: String,
        caption: String,
        frameColor: Int,
        attribution: String?,
        mime: String,
    ): File? = withContext(Dispatchers.IO) {
        runCatching {
            // <-- verbatim body from PictoKeyboardService.kt:290-386
            TODO("paste verbatim, then delete this line")
        }.getOrNull()
    }
}
```

**Note to the implementer:** the `TODO` above is a paste marker, not a design gap. The body is the existing `runCatching { … }.getOrNull()` expression at `PictoKeyboardService.kt:289-387`, which must be copied character-for-character; reproducing it here would invite retyping errors in code whose exact constants determine whether WhatsApp accepts the sticker. Copy, do not retype.

**One edit is mandatory, and it will not compile without it.** Line 290 currently reads:

```kotlin
val src = android.graphics.BitmapFactory.decodeFile(source.absolutePath) ?: return null
```

That `return null` is a *non-local* return out of the `runCatching` lambda, which is legal today only because `runCatching` is inline. Once the whole thing is inside `withContext(Dispatchers.IO) { … }` — a suspend lambda that does not permit non-local return — it stops compiling. Change it to:

```kotlin
val src = android.graphics.BitmapFactory.decodeFile(source.absolutePath) ?: return@runCatching null
```

This is behaviour-identical: today `return null` makes `labeledImage` return null; afterwards `runCatching` yields `Result.success(null)` and `.getOrNull()` returns null. Same value, same path.

Structure the result as:

```kotlin
): File? = withContext(Dispatchers.IO) {
    runCatching {
        // <-- verbatim body, with the one return@runCatching edit above
    }.getOrNull()
}
```

so `runCatching` stays *inside* `withContext` and the block's last expression is the `File?`.

- [ ] **Step 2: Wire it into the service**

In `ime/PictoKeyboardService.kt`:

Add the field near `tts`:

```kotlin
private lateinit var imageSharer: PictoImageSharer
```

In `onCreate`:

```kotlin
override fun onCreate() {
    super.onCreate()
    tts = TtsManager(this)
    imageSharer = PictoImageSharer(this)
}
```

Delete `sendPictoAsImage` (lines 211-271), `labeledImage` (273-387) and `toast` (389-391) entirely, and replace with:

```kotlin
/**
 * Long-press: send the pictogram as an image into the focused field. ARASAAC
 * pictos carry a baked-on licence credit; imported images are not ARASAAC's
 * and carry none.
 */
private fun sendPictoAsImage(picto: PictoEntity) {
    val connection = currentInputConnection ?: return
    val editorInfo = currentInputEditorInfo ?: return
    val frameColor = picto.colorArgbOverride
        ?: categories.firstOrNull { it.id == picto.categoryId }?.colorArgb
        ?: android.graphics.Color.LTGRAY
    val attribution =
        if (picto.arasaacId != null) getString(R.string.arasaac_share_attribution) else null
    scope.launch {
        imageSharer.send(picto, connection, editorInfo, frameColor, attribution) { resId ->
            android.widget.Toast.makeText(this@PictoKeyboardService, resId, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
```

Remove the now-unused imports from `PictoKeyboardService.kt`: `android.content.ClipDescription`, `androidx.core.view.inputmethod.EditorInfoCompat`, `androidx.core.view.inputmethod.InputConnectionCompat`, `androidx.core.view.inputmethod.InputContentInfoCompat`.

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL, and `PictoKeyboardService.kt` is now roughly 350 lines rather than 543.

- [ ] **Step 4: Verify by hand — this is not covered by unit tests**

Install and long-press a picto in Google Messages' compose field. Expected: the captioned card is attached, exactly as before. Long-press an ARASAAC picto and confirm the blue "ARASAAC · Sergio Palao · CC BY-NC-SA" line is still baked on — that line is a licence obligation and its loss would be a compliance regression, not a cosmetic one.

```bash
./gradlew :app:installDebug
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/pictokeyboard/ime/
git commit -m "Move picto image sharing out of the InputMethodService

Rendering a 512x512 card and writing it to disk ran on the main thread of
the IME. It now lives in PictoImageSharer and does its file I/O on
Dispatchers.IO.

The rendering itself is byte-identical -- the size ratios determine
whether WhatsApp accepts the result as a sticker. Verified by hand:
sticker send still works and the ARASAAC attribution is still baked on."
```

---

## Task 5: Diff the picto grid instead of rebinding it

`PictoAdapter.submit` calls `notifyDataSetChanged()`, so every settings change or database emission rebinds every visible tile — reloading every image through Coil and destroying any press state. Under #16 (press feedback) that becomes visible as a flicker on every tap.

**Files:**
- Create: `app/src/test/java/org/pictokeyboard/ime/PictoDiffTest.kt`
- Modify: `ime/PictoAdapter.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/org/pictokeyboard/ime/PictoDiffTest.kt`:

```kotlin
package org.pictokeyboard.ime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pictokeyboard.data.db.PictoEntity

class PictoDiffTest {

    private fun picto(id: String, label: String = "pan", position: Int = 0) =
        PictoEntity(
            id = id,
            categoryId = "food",
            label = label,
            spokenText = label,
            language = "es",
            position = position,
        )

    private val diff = PictoAdapter.DIFF

    @Test
    fun `items are the same when ids match`() {
        assertTrue(diff.areItemsTheSame(picto("1"), picto("1", label = "otro")))
    }

    @Test
    fun `items differ when ids differ`() {
        assertFalse(diff.areItemsTheSame(picto("1"), picto("2")))
    }

    @Test
    fun `contents are the same for an identical entity`() {
        assertTrue(diff.areContentsTheSame(picto("1"), picto("1")))
    }

    @Test
    fun `a changed label is a content change`() {
        assertFalse(diff.areContentsTheSame(picto("1"), picto("1", label = "leche")))
    }

    @Test
    fun `a changed position is a content change`() {
        assertFalse(diff.areContentsTheSame(picto("1"), picto("1", position = 3)))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*PictoDiffTest*'`
Expected: FAIL — `Unresolved reference: DIFF`

- [ ] **Step 3: Convert the adapter**

Rewrite `ime/PictoAdapter.kt`. The `VH` class and its `bind` body are unchanged; only the adapter base class, `submit`, and the item accessor change.

```kotlin
package org.pictokeyboard.ime

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import org.pictokeyboard.R
import org.pictokeyboard.data.arasaac.ArasaacUrls
import org.pictokeyboard.data.db.BorderStyles
import org.pictokeyboard.data.db.PictoEntity
import java.io.File

class PictoAdapter(
    private val onClick: (PictoEntity) -> Unit,
    private val onLongClick: (PictoEntity) -> Unit = {},
) : ListAdapter<PictoEntity, PictoAdapter.VH>(DIFF) {

    private var categoryColor: Int = Color.LTGRAY
    private var borderStyle: String = BorderStyles.SOLID
    private var borderWidthDp: Int = BorderStyles.DEFAULT_WIDTH_DP
    private var showLabels: Boolean = true

    fun submit(
        pictos: List<PictoEntity>,
        categoryColor: Int,
        showLabels: Boolean,
        borderStyle: String = BorderStyles.SOLID,
        borderWidthDp: Int = BorderStyles.DEFAULT_WIDTH_DP,
    ) {
        // These four are presentation, not list content, so a change to any of
        // them has to repaint every bound tile -- DiffUtil only sees the items.
        val styleChanged = this.categoryColor != categoryColor ||
            this.borderStyle != borderStyle ||
            this.borderWidthDp != borderWidthDp ||
            this.showLabels != showLabels
        this.categoryColor = categoryColor
        this.borderStyle = borderStyle
        this.borderWidthDp = borderWidthDp
        this.showLabels = showLabels
        submitList(pictos) {
            if (styleChanged) notifyItemRangeChanged(0, itemCount)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_picto, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position), categoryColor, borderStyle, borderWidthDp, showLabels)
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tile: SquareFrameLayout = view.findViewById(R.id.picto_tile)
        private val image: ImageView = view.findViewById(R.id.picto_image)
        private val label: TextView = view.findViewById(R.id.picto_label)

        fun bind(picto: PictoEntity, categoryColor: Int, borderStyle: String, borderWidthDp: Int, showLabels: Boolean) {
            // Borrowed pictos keep their original category's colour via the override.
            val color = picto.colorArgbOverride ?: categoryColor
            tile.background = ViewStyles.framedTile(
                colorArgb = color,
                strokeWidthPx = dp(borderWidthDp),
                cornerRadiusPx = dp(12).toFloat(),
                fillArgb = Color.WHITE,
                borderStyle = borderStyle,
            )

            val path = picto.imagePath
            if (path != null && File(path).exists()) {
                image.load(File(path)) {
                    crossfade(false)
                    placeholder(R.drawable.ic_picto_placeholder)
                    error(R.drawable.ic_picto_placeholder)
                }
            } else if (picto.arasaacId != null) {
                image.load(ArasaacUrls.image(picto.arasaacId))
            } else {
                image.setImageResource(R.drawable.ic_picto_placeholder)
            }

            label.text = picto.label
            label.visibility = if (showLabels && picto.label.isNotBlank()) View.VISIBLE else View.GONE

            itemView.setOnClickListener { onClick(picto) }
            itemView.setOnLongClickListener {
                onLongClick(picto)
                true
            }
        }

        private fun dp(value: Int): Int =
            (value * itemView.resources.displayMetrics.density).toInt()
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<PictoEntity>() {
            override fun areItemsTheSame(oldItem: PictoEntity, newItem: PictoEntity) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: PictoEntity, newItem: PictoEntity) =
                oldItem == newItem
        }
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :app:testDebugUnitTest --tests '*PictoDiffTest*'`
Expected: PASS, 5 tests

- [ ] **Step 5: Verify by hand**

`./gradlew :app:installDebug`, open the keyboard, switch categories and confirm the grid still updates; change "Show captions under pictos" in Settings and confirm labels appear/disappear on the next keyboard open (that is the path `styleChanged` protects).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/org/pictokeyboard/ime/PictoAdapter.kt \
        app/src/test/java/org/pictokeyboard/ime/PictoDiffTest.kt
git commit -m "Diff the picto grid instead of rebinding every tile

notifyDataSetChanged() rebound every visible tile on every emission,
reloading each image through Coil. Style changes still force a repaint
explicitly, since DiffUtil only sees list content."
```

---

## Task 6: Diff the category strip

Same defect in `CategoryAdapter`, with one wrinkle: selection is adapter state rather than entity state, so the diff callback cannot see it.

**Files:**
- Modify: `ime/CategoryAdapter.kt`

- [ ] **Step 1: Convert the adapter**

Rewrite `ime/CategoryAdapter.kt`. `VH.bind` is unchanged apart from the `ArasaacUrls` call already made in Task 1.

```kotlin
package org.pictokeyboard.ime

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import org.pictokeyboard.R
import org.pictokeyboard.data.arasaac.ArasaacUrls
import org.pictokeyboard.data.db.CategoryEntity
import java.io.File

class CategoryAdapter(private val onClick: (CategoryEntity) -> Unit) :
    ListAdapter<CategoryEntity, CategoryAdapter.VH>(DIFF) {

    private var selectedId: String? = null

    fun submit(categories: List<CategoryEntity>, selectedId: String?) {
        // Selection is adapter state, not entity state, so DiffUtil cannot see
        // it: repaint the old and new selected rows explicitly.
        val previousId = this.selectedId
        this.selectedId = selectedId
        submitList(categories) {
            if (previousId != selectedId) {
                listOfNotNull(previousId, selectedId).forEach { id ->
                    val index = currentList.indexOfFirst { it.id == id }
                    if (index >= 0) notifyItemChanged(index)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.bind(item, item.id == selectedId)
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val root: LinearLayout = view.findViewById(R.id.category_root)
        private val name: TextView = view.findViewById(R.id.category_name)
        private val icon: ImageView = view.findViewById(R.id.category_icon)

        fun bind(category: CategoryEntity, selected: Boolean) {
            name.text = category.name
            val color = category.colorArgb
            val fill = if (selected) color else ViewStyles.tint(color, 0x33)
            root.background = ViewStyles.framedTile(
                colorArgb = color,
                strokeWidthPx = dp(category.borderWidthDp),
                cornerRadiusPx = dp(10).toFloat(),
                fillArgb = fill,
                borderStyle = category.borderStyle,
            )
            name.setTextColor(
                if (selected) ViewStyles.contrastText(color) else 0xFF222222.toInt(),
            )

            val iconPath = category.iconImagePath
            val iconModel: Any? = when {
                iconPath != null && File(iconPath).exists() -> File(iconPath)
                category.iconArasaacId != null -> ArasaacUrls.image(category.iconArasaacId)
                else -> null
            }
            if (iconModel != null) {
                icon.visibility = View.VISIBLE
                icon.load(iconModel)
            } else {
                icon.visibility = View.GONE
            }

            root.setOnClickListener { onClick(category) }
        }

        private fun dp(value: Int): Int =
            (value * itemView.resources.displayMetrics.density).toInt()
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<CategoryEntity>() {
            override fun areItemsTheSame(oldItem: CategoryEntity, newItem: CategoryEntity) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: CategoryEntity, newItem: CategoryEntity) =
                oldItem == newItem
        }
    }
}
```

- [ ] **Step 2: Build and verify by hand**

Run: `./gradlew :app:installDebug`
Open the keyboard and tap between categories. Expected: the selected chip fills solid and the previous one returns to its tint — the exact behaviour as before. This is the step where a wrong `notifyItemChanged` shows up as a chip that never deselects, so check it deliberately.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/org/pictokeyboard/ime/CategoryAdapter.kt
git commit -m "Diff the category strip instead of rebinding it

Selection lives in the adapter rather than the entity, so DiffUtil cannot
observe it; the old and new selected rows are repainted explicitly."
```

---

## Task 7: Make the screens stateless

This is the keystone. Every screen currently takes `ConfigViewModel`, so no screen can be rendered in a `@Preview` or constructed in a test. The redesign in #15 needs previews on every screen, and it cannot get them until this is done.

**Pattern**, applied identically to each screen: split the existing `fun XScreen(viewModel: ConfigViewModel, …)` into two.

```kotlin
// Stateful: the only thing that knows about the ViewModel. No layout.
@Composable
fun XScreen(viewModel: ConfigViewModel, onBack: (() -> Unit)? = null) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    XScreenContent(
        settings = settings,
        onSetLanguage = viewModel::setLanguage,
        onBack = onBack,
    )
}

// Stateless: all the layout, previewable, testable.
@Composable
fun XScreenContent(
    settings: Settings,
    onSetLanguage: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) { … }
```

Rules from the `compose-ui` skill that apply to every screen you touch here:

- `modifier: Modifier = Modifier` is the **first optional parameter** and is applied to the root
- keep `collectAsStateWithLifecycle()`, never `collectAsState()`
- the stateless Composable gets the `@Preview`s
- **do not restyle anything** — this task moves parameters, nothing else. Visual work is #15.

Do the screens one at a time, committing after each. Order is smallest-first so the pattern is established on easy cases.

### 7a: SettingsScreen

**Files:** Modify `ui/screens/SettingsScreen.kt`

- [ ] **Step 1: Split the composable**

Rename the existing `SettingsScreen` body to `SettingsScreenContent` with this signature, and replace every `viewModel.x(...)` call in the body with the matching lambda:

```kotlin
@Composable
fun SettingsScreenContent(
    settings: Settings,
    onSetLanguage: (String) -> Unit,
    onSetColumns: (Int) -> Unit,
    onSetRows: (Int) -> Unit,
    onSetShowLabels: (Boolean) -> Unit,
    onSetAddSpace: (Boolean) -> Unit,
    onSetSpeak: (Boolean) -> Unit,
    onSetTtsRate: (Float) -> Unit,
    onSetTtsPitch: (Float) -> Unit,
    onSetBlindMode: (Boolean) -> Unit,
    onSetPin: (String, () -> Unit) -> Unit,
    onRemovePin: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
)
```

**The `exportLauncher` and `importLauncher` blocks must move OUT of the content Composable and into the stateful wrapper.** This is not a style preference — `rememberLauncherForActivityResult` requires a `LocalActivityResultRegistryOwner`, and a `@Preview` does not provide one, so leaving them in `SettingsScreenContent` makes every preview on this screen fail to render. That is the whole point of the task.

This is the same trap the project has hit before: the `ProvideAppLocale` context wrapper is documented to break the walk to the Activity and crash `rememberLauncherForActivityResult` if it is done wrong. Anything that reaches for the Activity belongs in the stateful layer.

So `onExport` and `onImport` become plain `() -> Unit` — "the user pressed Export" — and the wrapper owns the file-picker plumbing:

```kotlin
@Composable
fun SettingsScreen(viewModel: ConfigViewModel, onBack: (() -> Unit)? = null) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingExportJson by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val json = pendingExportJson
        if (uri != null && json != null) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            Toast.makeText(context, R.string.settings_export_done, Toast.LENGTH_SHORT).show()
        }
        pendingExportJson = null
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (text != null) {
                viewModel.importJson(text) { ok ->
                    val msg = if (ok) R.string.settings_import_done else R.string.settings_import_failed
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    SettingsScreenContent(
        settings = settings,
        onSetLanguage = viewModel::setLanguage,
        onSetColumns = viewModel::setColumns,
        onSetRows = viewModel::setRows,
        onSetShowLabels = viewModel::setShowLabels,
        onSetAddSpace = viewModel::setAddSpace,
        onSetSpeak = viewModel::setSpeak,
        onSetTtsRate = viewModel::setTtsRate,
        onSetTtsPitch = viewModel::setTtsPitch,
        onSetBlindMode = viewModel::setBlindMode,
        onSetPin = viewModel::setPin,
        onRemovePin = viewModel::removePin,
        onExport = {
            scope.launch {
                pendingExportJson = viewModel.exportJson()
                exportLauncher.launch("pictokeyboard-board.json")
            }
        },
        onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
        onBack = onBack,
    )
}
```

Inside `SettingsScreenContent`, the two Backup buttons become plain `onClick = onExport` and `onClick = onImport`, and the `context` / `scope` / `pendingExportJson` locals are deleted from it.

**Apply the same rule to `AddPictosScreen` in 7e** — it uses `rememberLauncherForActivityResult(GetContent("image/*"))` for image import. That launcher moves to the stateful wrapper too, and the content takes an `onPickImage: () -> Unit`. Its previews will not render otherwise.

- [ ] **Step 2: Add previews**

```kotlin
@Preview(name = "Settings", showBackground = true)
@Preview(name = "Settings · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Settings · 2x font", showBackground = true, fontScale = 2f)
@Composable
private fun SettingsScreenPreview() {
    PictoKeyboardTheme {
        SettingsScreenContent(
            settings = Settings(),
            onSetLanguage = {}, onSetColumns = {}, onSetRows = {},
            onSetShowLabels = {}, onSetAddSpace = {}, onSetSpeak = {},
            onSetTtsRate = {}, onSetTtsPitch = {}, onSetBlindMode = {},
            onSetPin = { _, _ -> }, onRemovePin = {},
            onExport = { "" }, onImport = { _, _ -> },
        )
    }
}
```

- [ ] **Step 3: Build and check the previews render**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. Open `SettingsScreen.kt` in Android Studio and confirm all three previews render. The `fontScale = 2f` one is the one that finds real bugs — if any row clips, note it for #15 but **do not fix it here**.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/org/pictokeyboard/ui/screens/SettingsScreen.kt
git commit -m "Make SettingsScreen stateless and previewable

Layout moves to SettingsScreenContent, which takes state and lambdas; the
ViewModel is confined to a thin wrapper. No visual change."
```

- [ ] **Step 5: Repeat for AboutScreen and UnlockScreen**

`AboutScreen` takes no ViewModel already — add previews and the `modifier` parameter only. `UnlockScreen` already takes `verify`/`onUnlocked` lambdas — add previews and `modifier` only. Commit both together:

```bash
git commit -m "Add previews to AboutScreen and UnlockScreen"
```

### 7b: DashboardScreen

**Files:** Modify `ui/screens/DashboardScreen.kt`

- [ ] **Step 1: Split**

```kotlin
@Composable
fun DashboardScreenContent(
    categoryCount: Int,
    pictoCount: Int,
    status: KeyboardStatus,
    onEnableKeyboard: () -> Unit,
    onSelectKeyboard: () -> Unit,
    onOpenBoard: () -> Unit,
    modifier: Modifier = Modifier,
)
```

`rememberKeyboardStatus()` stays in the stateful `DashboardScreen` — it reads `LocalContext` and device state, which must not reach into a previewable Composable. Pass the resulting `KeyboardStatus` down.

- [ ] **Step 2: Add previews covering both setup states**

```kotlin
@Preview(name = "Dashboard · not set up", showBackground = true)
@Composable
private fun DashboardNotReadyPreview() {
    PictoKeyboardTheme {
        DashboardScreenContent(
            categoryCount = 7, pictoCount = 84,
            status = KeyboardStatus(enabled = false, selected = false),
            onEnableKeyboard = {}, onSelectKeyboard = {}, onOpenBoard = {},
        )
    }
}

@Preview(name = "Dashboard · ready", showBackground = true)
@Preview(name = "Dashboard · ready dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Dashboard · ready 2x font", showBackground = true, fontScale = 2f)
@Composable
private fun DashboardReadyPreview() {
    PictoKeyboardTheme {
        DashboardScreenContent(
            categoryCount = 7, pictoCount = 84,
            status = KeyboardStatus(enabled = true, selected = true),
            onEnableKeyboard = {}, onSelectKeyboard = {}, onOpenBoard = {},
        )
    }
}
```

- [ ] **Step 3: Build, check previews, commit**

```bash
./gradlew :app:assembleDebug
git add app/src/main/java/org/pictokeyboard/ui/screens/DashboardScreen.kt
git commit -m "Make DashboardScreen stateless and previewable

Device-state reads stay in the stateful wrapper; the content Composable
takes a KeyboardStatus. No visual change."
```

### 7c: PictosScreen

**Files:** Modify `ui/screens/PictosScreen.kt`

- [ ] **Step 1: Split**

```kotlin
@Composable
fun PictosScreenContent(
    category: CategoryEntity?,
    pictos: List<PictoEntity>,
    showLabels: Boolean,
    onBack: () -> Unit,
    onAddPictos: () -> Unit,
    onUpdatePicto: (PictoEntity) -> Unit,
    onDeletePicto: (PictoEntity) -> Unit,
    onMovePicto: (List<PictoEntity>, PictoEntity, Boolean) -> Unit,
    modifier: Modifier = Modifier,
)
```

The `categoryColor` / `borderStyle` / `borderWidthDp` locals are derived from `category` inside the content Composable — do not add them as parameters, they are not independent state.

- [ ] **Step 2: Add previews including the empty state**

An empty category is a real state a caregiver hits constantly (every new blank category starts there) and it has never been checked. Add a preview with `pictos = emptyList()`.

- [ ] **Step 3: Build, check previews, commit**

```bash
git commit -m "Make PictosScreen stateless and previewable"
```

### 7d: CategoriesScreen

**Files:** Modify `ui/screens/CategoriesScreen.kt`

- [ ] **Step 1: Split**

```kotlin
@Composable
fun CategoriesScreenContent(
    categories: List<CategoryEntity>,
    language: String,
    suggestedName: String,
    loadSuggested: suspend () -> List<UsageEntity>,
    onBack: (() -> Unit)?,
    onOpenCategory: (String) -> Unit,
    onAddCategory: (String, Int, String, Int) -> Unit,
    onAddFromTemplate: (CategoryTemplate) -> Unit,
    onAddSuggested: (List<UsageEntity>) -> Unit,
    onUpdateCategory: (CategoryEntity) -> Unit,
    onDeleteCategory: (CategoryEntity) -> Unit,
    onReorder: (List<CategoryEntity>) -> Unit,
    modifier: Modifier = Modifier,
)
```

- [ ] **Step 2: Add previews, including empty**

- [ ] **Step 3: Build, check previews, commit**

```bash
git commit -m "Make CategoriesScreen stateless and previewable"
```

### 7e: AddPictosScreen

**Files:** Modify `ui/screens/AddPictosScreen.kt`

- [ ] **Step 1: Split**

```kotlin
@Composable
fun AddPictosScreenContent(
    categoryId: String,
    categories: List<CategoryEntity>,
    search: SearchState,
    defaultLanguage: String,
    onSearch: (String, String) -> Unit,
    onClearSearch: () -> Unit,
    onAddPictos: (String, List<ArasaacResult>, String, () -> Unit) -> Unit,
    onAddPicto: (String, ArasaacResult, String, String, String, ArasaacOptions, Int?, () -> Unit) -> Unit,
    onDecodeImage: suspend (Uri) -> Bitmap?,
    onAddCroppedImage: (String, Bitmap, String, String, String, Int?, () -> Unit) -> Unit,
    onPictosOnce: suspend (String) -> List<PictoEntity>,
    onCopyPictos: (String, List<Pair<PictoEntity, Int>>, () -> Unit) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
)
```

`onAddPicto` has eight parameters, which is a smell — but it mirrors the existing `ConfigViewModel.addPicto` exactly, and changing that signature would be a behaviour-adjacent redesign that belongs in #15, not here. Leave it.

- [ ] **Step 2: Add previews for each `SearchState`**

`SearchState` has five cases (`Idle`, `Loading`, `Results`, `Empty`, `Error`) and **not one of them has ever been visually verified**. Add a preview per case. The `Error` and `Empty` previews are the valuable ones.

- [ ] **Step 3: Build, check previews, commit**

```bash
git commit -m "Make AddPictosScreen stateless and previewable

Adds a preview per SearchState -- Loading, Empty and Error had never been
rendered anywhere."
```

### 7f: Update the NavHost

**Files:** Modify `ui/MainActivity.kt:139-194`

- [ ] **Step 1: Fix the leaked fully-qualified type**

Line 99 currently reads:

```kotlin
private fun AppNavigation(viewModel: ConfigViewModel, settings: org.pictokeyboard.data.prefs.Settings)
```

Add `import org.pictokeyboard.data.prefs.Settings` and use the bare name.

- [ ] **Step 2: Confirm the NavHost still compiles unchanged**

The stateful `XScreen(viewModel, …)` wrappers keep their existing signatures, so the `NavHost` needs no change beyond Step 1. If any call site broke, the wrapper signature drifted — fix the wrapper, not the call site.

- [ ] **Step 3: Build and commit**

```bash
./gradlew :app:assembleDebug
git add app/src/main/java/org/pictokeyboard/ui/MainActivity.kt
git commit -m "Import Settings rather than fully qualifying it in a signature"
```

---

## Task 8: Split the two oversized screen files

`AddPictosScreen.kt` is 645 lines and `CategoriesScreen.kt` is 486. Both hold a screen plus three or four dialogs. Splitting them is what makes the redesign in #15 reviewable — otherwise every visual change shows up as a diff against a 600-line file.

Pure file moves. No code changes beyond package declarations and imports.

**Files:**
- Create: `ui/screens/addpictos/SearchResultsGrid.kt`, `PictoDetailDialog.kt`, `CustomImageDialog.kt`, `ImportFromCategoriesDialog.kt`
- Create: `ui/screens/categories/CategoryRow.kt`, `CategoryDialogs.kt`
- Modify: `ui/screens/AddPictosScreen.kt`, `ui/screens/CategoriesScreen.kt`

- [ ] **Step 1: Move the AddPictos dialogs**

Move each of these into its own file under `ui/screens/addpictos/`, with `package org.pictokeyboard.ui.screens.addpictos`:

| From `AddPictosScreen.kt` | To |
|---|---|
| `ResultsGrid` (265-321) | `SearchResultsGrid.kt` |
| `PictoDetailDialog` (323-408), `SwatchRow` (410-434), `Swatch` (436-454), `skinSwatch` (627-635), `hairSwatch` (636-644) | `PictoDetailDialog.kt` |
| `CustomImageDialog` (456-517) | `CustomImageDialog.kt` |
| `ImportFromCategoriesDialog` (519-626) | `ImportFromCategoriesDialog.kt` |

Each was `private`; they become internal to the new package, so drop `private` and let them default to public within the module. Add the matching imports to `AddPictosScreen.kt`.

- [ ] **Step 2: Move the Categories dialogs**

| From `CategoriesScreen.kt` | To (`package org.pictokeyboard.ui.screens.categories`) |
|---|---|
| `NewCategoryChooserDialog` (313-381), `ChooserCard` (383-437) | `CategoryDialogs.kt` |
| `CategoryEditDialog` (439-486) | `CategoryDialogs.kt` |
| the row `Card { Row { … } }` body inside `itemsIndexed` | `CategoryRow.kt`, extracted as `CategoryRow(...)` |

- [ ] **Step 3: Verify the line counts came down**

Run:
```bash
wc -l app/src/main/java/org/pictokeyboard/ui/screens/*.kt \
      app/src/main/java/org/pictokeyboard/ui/screens/*/*.kt | sort -rn | head
```
Expected: no file over ~250 lines.

- [ ] **Step 4: Build and test**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/pictokeyboard/ui/screens/
git commit -m "Split the two oversized screen files by component

AddPictosScreen was 645 lines and CategoriesScreen 486, each holding a
screen plus three or four dialogs. Pure file moves -- no code changes
beyond package declarations and imports."
```

---

## Task 9: Full gate and PR

- [ ] **Step 1: Run everything**

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug detekt
```
Expected: all green. Detekt has a baseline recording pre-existing debt — **any new finding fails the build, and regenerating the baseline to silence one is not acceptable.** If detekt reports something new, fix the code.

- [ ] **Step 2: Confirm no behaviour drifted**

```bash
git diff main --stat
```
Every changed file should be explicable as one of: a moved function, an adapter base class, a Composable signature, an import. **If the diff touches a string resource, a dimension, a colour, or a layout XML, something has gone wrong** — this PR changes no user-visible behaviour.

- [ ] **Step 3: Exercise the app by hand**

Unit tests do not catch lifecycle, threading or IME integration regressions, which are precisely what this kind of work breaks. Install and walk:

1. keyboard opens in a text field; tapping a picto types and speaks it
2. switching categories updates the grid; the selected chip fills solid
3. long-pressing a picto sends the image, with the ARASAAC credit baked on
4. backspace deletes a whole word
5. blind mode toggles on a two-finger double-tap and its gestures work
6. every config screen opens, and category/picto create-edit-delete all work
7. Settings export and import still work — these depend on the `ActivityResultRegistryOwner` walk that `ProvideAppLocale` is documented to break if the context wrapper is done wrong

- [ ] **Step 4: Review before opening the PR**

```
Dispatch the ime-reviewer agent over the diff — it covers InputMethodService
lifecycle, InputConnection use, leaks and main-thread work, all of which
Task 4 touched directly.
```

- [ ] **Step 5: Open the PR**

```bash
git push -u origin refactor/14-ui-layer-restructure
gh pr create --title "Restructure the UI layer for testability" --body "$(cat <<'EOF'
Behaviour-preserving restructuring of the UI layer, so the visual redesign
(#15) and the new keyboard behaviour (#16) land on code that can be tested
and previewed.

## What changed

- ARASAAC image URLs built in one place, replacing a buried `ImageCache`
  companion *and* eight hand-built sites that bypassed it
- `PictoAdapter` and `CategoryAdapter` diff instead of `notifyDataSetChanged()`
- picto image rendering and file I/O moved out of the `InputMethodService`
  and onto `Dispatchers.IO`
- every screen split into a stateful wrapper and a previewable, testable
  content Composable
- `movedBy` lifted out of `ConfigViewModel`
- `AddPictosScreen` (645 lines) and `CategoriesScreen` (486) split by component

## What did not change

Anything the user can see. No string, dimension, colour or layout was
touched. The one known bug in this area — the dead "Visible rows" slider —
is deliberately left alone and tracked as #17.

## Tests

Repository went from 1 test file to 5. New characterization tests pin
whole-word backspace and list reordering before those seams moved.

Closes #14
EOF
)"
```

---

## Self-review

**Spec coverage.** This plan implements spec §5 items 1-6 and the file-split half of the quality floor. Items 7 (visual system) and 8 (new behaviour) are out of scope by design and are tracked as #15 and #16; the `gridRows` bug is #17. Spec §5's localization paragraph has no task here because this PR touches no strings — that is deliberate and stated in the PR body.

**Placeholder scan.** One `TODO(...)` appears in Task 4 Step 1. It is a deliberate paste marker with an explicit instruction attached, not an unspecified requirement: reproducing 99 lines of exact graphics constants in a plan invites transcription errors in code whose constants determine whether WhatsApp accepts the sticker. Every other step contains complete code.

**Type consistency.** `ArasaacUrls.image(id, size)`, `.customized(id, options)`, `.customizedOrPlain(id, options)` and the constants `FULL`/`THUMB` are used consistently in Tasks 1, 5 and 6. `customizedOrPlain` exists solely to absorb `ImageCache.imageUrl`'s branch, keeping its two callers a one-for-one substitution rather than pushing the conditional out to them. `movedBy(list, match, up)` keeps its exact original signature so both `ConfigViewModel` call sites resolve without edits. `PictoAdapter.DIFF` and `CategoryAdapter.DIFF` are both named `DIFF`. `KeyboardStatus(enabled, selected)` matches the existing declaration at `DashboardScreen.kt:70`. `SettingsScreenContent`'s `onSetPin: (String, () -> Unit) -> Unit` matches `ConfigViewModel.setPin(pin, onDone)`.

**Known risk.** Task 5's `styleChanged` repaint and Task 6's selection repaint are the two places where a diffing adapter can silently stop updating. Both have an explicit by-hand verification step for exactly that reason.
