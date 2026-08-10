package org.pictokeyboard.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pictokeyboard.ui.screens.CategoryPalette

/**
 * The colour tokens' contrast table, computed from the values that actually ship
 * rather than asserted in a document.
 *
 * The design says "these values are the contract; any change to a token re-runs
 * this table". This is that re-run. A token that cannot clear its threshold does
 * not ship, and the failure names the pair so the fix is obvious.
 *
 * Contrast is the one design property that is invisible to a sighted developer on
 * a good screen and completely disabling on a phone in sunlight -- which is where
 * this app gets used.
 */
class TokenContrastTest {

    /** Every palette the app can be in. New schemes join the invariants here. */
    private val allSchemes = listOf(
        "light" to LightTokens,
        "dark" to DarkTokens,
        "high contrast light" to HighContrastLightTokens,
        "high contrast dark" to HighContrastDarkTokens,
    )

    private fun ratio(foreground: Color, background: Color): Double =
        Wcag.contrastRatio(foreground.toArgb(), background.toArgb())

    private fun assertReadable(name: String, foreground: Color, background: Color, minimum: Double) {
        val actual = ratio(foreground, background)
        assertTrue(
            "$name is %.2f:1, below the required %.1f:1".format(actual, minimum),
            actual >= minimum,
        )
    }

    /** Every pair a screen can actually produce, in one scheme. */
    private fun assertSchemeIsReadable(scheme: String, c: PictoColors) {
        assertReadable("$scheme ink on paper", c.ink, c.paper, Wcag.BODY_TEXT)
        assertReadable("$scheme inkSoft on paper", c.inkSoft, c.paper, Wcag.BODY_TEXT)
        assertReadable("$scheme ink on card", c.ink, c.card, Wcag.BODY_TEXT)
        assertReadable("$scheme inkSoft on card", c.inkSoft, c.card, Wcag.BODY_TEXT)
        assertReadable("$scheme danger on paper", c.danger, c.paper, Wcag.BODY_TEXT)
        assertReadable("$scheme danger on card", c.danger, c.card, Wcag.BODY_TEXT)
        assertReadable("$scheme onAccent on accent", c.onAccent, c.accent, Wcag.BODY_TEXT)
        assertReadable("$scheme onDanger on danger", c.onDanger, c.danger, Wcag.BODY_TEXT)

        assertReadable("$scheme accent on paper", c.accent, c.paper, Wcag.LARGE_TEXT_AND_UI)
        assertReadable("$scheme accent on card", c.accent, c.card, Wcag.LARGE_TEXT_AND_UI)
        assertReadable("$scheme lineStrong on paper", c.lineStrong, c.paper, Wcag.LARGE_TEXT_AND_UI)
        assertReadable("$scheme lineStrong on card", c.lineStrong, c.card, Wcag.LARGE_TEXT_AND_UI)
    }

    @Test
    fun `the light scheme is readable`() {
        assertSchemeIsReadable("light", LightTokens)
    }

    @Test
    fun `the dark scheme is readable`() {
        assertSchemeIsReadable("dark", DarkTokens)
    }

    @Test
    fun `the high contrast schemes are readable`() {
        assertSchemeIsReadable("high contrast light", HighContrastLightTokens)
        assertSchemeIsReadable("high contrast dark", HighContrastDarkTokens)
    }

    /**
     * The point of the mode, stated as an assertion rather than a hope: turning
     * it on may not make **any** pair worse than the palette it replaces.
     *
     * Easy to break without noticing. A "high contrast" red picked for looking
     * serious, or a card colour nudged for depth, can quietly land below the
     * scheme it is supposed to improve on — and the user who turned this on is
     * the one least able to tell.
     */
    @Test
    fun `high contrast never loses contrast against the scheme it replaces`() {
        listOf(
            Triple("light", LightTokens, HighContrastLightTokens),
            Triple("dark", DarkTokens, HighContrastDarkTokens),
        ).forEach { (scheme, normal, hc) ->
            fun compare(pair: String, fg: (PictoColors) -> Color, bg: (PictoColors) -> Color) {
                val before = ratio(fg(normal), bg(normal))
                val after = ratio(fg(hc), bg(hc))
                assertTrue(
                    "$scheme $pair got worse in high contrast: %.2f:1 -> %.2f:1".format(before, after),
                    after >= before,
                )
            }
            compare("ink on paper", { it.ink }, { it.paper })
            compare("inkSoft on paper", { it.inkSoft }, { it.paper })
            compare("ink on card", { it.ink }, { it.card })
            compare("inkSoft on card", { it.inkSoft }, { it.card })
            compare("lineStrong on paper", { it.lineStrong }, { it.paper })
            compare("line on paper", { it.line }, { it.paper })
            compare("accent on paper", { it.accent }, { it.paper })
            compare("danger on paper", { it.danger }, { it.paper })
            compare("onAccent on accent", { it.onAccent }, { it.accent })
            compare("onDanger on danger", { it.onDanger }, { it.danger })
        }
    }

    /**
     * `paper` and `ink` going *pure* is the headline promise of the mode, and it
     * is the one a later "let's warm it up slightly" would silently undo.
     */
    @Test
    fun `high contrast paper and ink are pure`() {
        assertEquals(Color.White, HighContrastLightTokens.paper)
        assertEquals(Color.Black, HighContrastLightTokens.ink)
        assertEquals(Color.Black, HighContrastDarkTokens.paper)
        assertEquals(Color.White, HighContrastDarkTokens.ink)
    }

    /**
     * Picto tiles stay white in dark mode because ARASAAC artwork is black line
     * work. That makes the *content* colour on a tile scheme-invariant too: the
     * dark scheme's `ink` on a white tile lands at 1.17:1 and disappears. This is
     * the pair the design's own table omitted.
     */
    @Test
    fun `tile content is readable in every scheme`() {
        allSchemes.forEach { (scheme, c) ->
            assertReadable("$scheme onTile on tile", c.onTile, c.tile, Wcag.BODY_TEXT)
            assertReadable("$scheme onTileSoft on tile", c.onTileSoft, c.tile, Wcag.BODY_TEXT)
        }
    }

    /**
     * High contrast must not reach the tile. It is the one surface whose colour
     * is a legibility constraint rather than a contrast budget: darkening it to
     * "improve contrast" would destroy the black line art it exists to carry,
     * which is the opposite of what the person turning this on wants.
     */
    @Test
    fun `the tile stays white in every scheme`() {
        allSchemes.forEach { (scheme, c) ->
            assertEquals("$scheme tile", Color.White, c.tile)
        }
    }

    @Test
    fun `pictoColors picks the palette its arguments describe`() {
        assertEquals(LightTokens, pictoColors(dark = false, highContrast = false))
        assertEquals(DarkTokens, pictoColors(dark = true, highContrast = false))
        assertEquals(HighContrastLightTokens, pictoColors(dark = false, highContrast = true))
        assertEquals(HighContrastDarkTokens, pictoColors(dark = true, highContrast = true))
    }

    /**
     * `line` is allowed to be near-invisible; that is what makes it decorative.
     * The invariant worth protecting is that `lineStrong` is genuinely stronger,
     * so the two tokens cannot drift into being interchangeable.
     */
    @Test
    fun `lineStrong outranks the decorative line in both schemes`() {
        listOf("light" to LightTokens, "dark" to DarkTokens).forEach { (scheme, c) ->
            val strong = ratio(c.lineStrong, c.paper)
            val decorative = ratio(c.line, c.paper)
            assertTrue(
                "$scheme lineStrong (%.2f:1) must outrank line (%.2f:1)".format(strong, decorative),
                strong > decorative,
            )
        }
    }

    /**
     * The selected category chip paints its label over an arbitrary user-chosen
     * hue, so the auto-contrast choice has to hold for every one of the 26 palette
     * values -- including the mid-tones, which is exactly where a 0.5 luminance
     * threshold picks white when only black is readable.
     */
    @Test
    fun `auto-contrast text is readable on every palette colour`() {
        CategoryPalette.forEach { swatch ->
            val argb = swatch.argb
            val background = argb.toInt()
            val actual = Wcag.contrastRatio(CategoryColors.contrastText(background), background)
            assertTrue(
                "auto-contrast on #%08X is %.2f:1, below %.1f:1".format(argb, actual, Wcag.BODY_TEXT),
                actual >= Wcag.BODY_TEXT,
            )
        }
    }

    /**
     * The stronger claim: auto-contrast is readable on *any* colour, not just the
     * 26 currently in the palette. Sweeping the grey ramp crosses the black/white
     * changeover, which is the only place the choice can go wrong.
     */
    @Test
    fun `auto-contrast text is readable across the whole grey ramp`() {
        (0..255).forEach { value ->
            val background = (0xFF shl 24) or (value shl 16) or (value shl 8) or value
            val actual = Wcag.contrastRatio(CategoryColors.contrastText(background), background)
            assertTrue(
                "auto-contrast on grey $value is %.2f:1, below %.1f:1".format(actual, Wcag.BODY_TEXT),
                actual >= Wcag.BODY_TEXT,
            )
        }
    }

    @Test
    fun `contrast ratio spans the WCAG range`() {
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        assertEquals(21.0, Wcag.contrastRatio(black, white), 0.01)
        assertEquals(1.0, Wcag.contrastRatio(white, white), 0.001)
    }

    @Test
    fun `the derived category alphas match the design percentages`() {
        val hue = 0xFFFF9800.toInt()
        assertEquals(0xFF, CategoryColors.fill(hue) ushr 24)
        assertEquals(0x0F, CategoryColors.wash(hue) ushr 24)
        assertEquals(0x1F, CategoryColors.tintSoft(hue) ushr 24)
        // The hue itself must survive the alpha change untouched -- users' saved
        // data references these exact RGB values.
        assertEquals(hue and 0x00FFFFFF, CategoryColors.wash(hue) and 0x00FFFFFF)
    }

    /**
     * A category's own hue, used to outline its chip on the spine.
     *
     * The easy direction — hue as *background*, black-or-white on top — was already
     * covered. This is the direction that actually failed: the hue as a 1.5dp line
     * against `paper`. Yellow manages 1.08:1 raw and white 1.13:1, so 13 of 26
     * palette values had no visible chip edge in light mode and a different 9 had
     * none in dark. [CategoryColors.outlineOn] is what fixes it, and this is the
     * assertion that says so.
     */
    @Test
    fun `every palette hue is visible as an outline in both schemes`() {
        listOf("light" to LightTokens, "dark" to DarkTokens).forEach { (scheme, c) ->
            val paper = c.paper.toArgb()
            CategoryPalette.forEach { swatch ->
                val argb = swatch.argb
                val outline = CategoryColors.outlineOn(argb.toInt(), paper)
                val actual = Wcag.contrastRatio(outline, paper)
                assertTrue(
                    "$scheme outline for #%08X is %.2f:1 on paper, below %.1f:1"
                        .format(argb, actual, Wcag.LARGE_TEXT_AND_UI),
                    actual >= Wcag.LARGE_TEXT_AND_UI,
                )
            }
        }
    }

    /**
     * The tokens are only half the story: the other half is the mapping onto
     * Material's roles, which is where a token lands somewhere it was never sized
     * for. Material's contract is that every `onX` is readable on its `X`, so this
     * walks the scheme the app actually builds and checks that contract holds.
     *
     * This is the test that would have caught `surfaceVariant` being handed the
     * decorative `line` (putting `onSurfaceVariant` at 4.36:1), rather than waiting
     * for someone to notice a grey-on-grey caption.
     */
    @Test
    fun `every Material content role is readable on its own container`() {
        listOf("light" to LightTokens.toColorScheme(false), "dark" to DarkTokens.toColorScheme(true))
            .forEach { (name, scheme) ->
                val pairs = listOf(
                    "onPrimary/primary" to (scheme.onPrimary to scheme.primary),
                    "onSecondary/secondary" to (scheme.onSecondary to scheme.secondary),
                    "onTertiary/tertiary" to (scheme.onTertiary to scheme.tertiary),
                    "onBackground/background" to (scheme.onBackground to scheme.background),
                    "onSurface/surface" to (scheme.onSurface to scheme.surface),
                    "onSurfaceVariant/surfaceVariant" to (scheme.onSurfaceVariant to scheme.surfaceVariant),
                    "onPrimaryContainer/primaryContainer" to
                        (scheme.onPrimaryContainer to scheme.primaryContainer),
                    "onSecondaryContainer/secondaryContainer" to
                        (scheme.onSecondaryContainer to scheme.secondaryContainer),
                    "onTertiaryContainer/tertiaryContainer" to
                        (scheme.onTertiaryContainer to scheme.tertiaryContainer),
                    "onError/error" to (scheme.onError to scheme.error),
                    "onErrorContainer/errorContainer" to (scheme.onErrorContainer to scheme.errorContainer),
                    "inverseOnSurface/inverseSurface" to (scheme.inverseOnSurface to scheme.inverseSurface),
                    "onSurface/surfaceContainer" to (scheme.onSurface to scheme.surfaceContainer),
                    "onSurfaceVariant/surfaceContainer" to (scheme.onSurfaceVariant to scheme.surfaceContainer),
                )
                pairs.forEach { (label, pair) ->
                    assertReadable("$name $label", pair.first, pair.second, Wcag.BODY_TEXT)
                }

                // `outline` bounds controls, so it owes 3:1 on every surface a
                // control can sit on.
                assertReadable("$name outline/surface", scheme.outline, scheme.surface, Wcag.LARGE_TEXT_AND_UI)
                assertReadable(
                    "$name outline/surfaceContainer",
                    scheme.outline,
                    scheme.surfaceContainer,
                    Wcag.LARGE_TEXT_AND_UI,
                )
            }
    }
}
