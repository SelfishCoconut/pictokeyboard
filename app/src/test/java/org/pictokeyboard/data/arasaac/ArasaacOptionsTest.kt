package org.pictokeyboard.data.arasaac

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ArasaacOptions] decides two things that are easy to break silently: which
 * cache file a pictogram lands in, and which URL it is fetched from. A wrong
 * cache key orphans every previously downloaded image; a wrong query string
 * fetches the wrong artwork. Both fail without an exception, so they need
 * tests rather than inspection.
 */
class ArasaacOptionsTest {
    @Test
    fun `default options are not customized`() {
        assertFalse(ArasaacOptions().isCustomized)
    }

    @Test
    fun `any single option marks the pictogram customized`() {
        assertTrue(ArasaacOptions(skin = "white").isCustomized)
        assertTrue(ArasaacOptions(hair = "brown").isCustomized)
        assertTrue(ArasaacOptions(color = false).isCustomized)
    }

    @Test
    fun `default cache key is plain`() {
        // Guards the empty-options path: joinToString on an empty list would
        // otherwise produce "", which is not a usable filename fragment.
        assertEquals("plain", ArasaacOptions().cacheKey())
    }

    @Test
    fun `cache key encodes each option distinctly`() {
        assertEquals("white", ArasaacOptions(skin = "white").cacheKey())
        assertEquals("hbrown", ArasaacOptions(hair = "brown").cacheKey())
        assertEquals("bw", ArasaacOptions(color = false).cacheKey())
    }

    @Test
    fun `cache key combines options in a stable order`() {
        val key = ArasaacOptions(skin = "aztec", hair = "red", color = false).cacheKey()
        assertEquals("aztec-hred-bw", key)
        // Stability matters more than the exact format: the same options must
        // always produce the same key, or the cache silently misses forever.
        assertEquals(key, ArasaacOptions(skin = "aztec", hair = "red", color = false).cacheKey())
    }

    @Test
    fun `distinct options never collide on the same cache key`() {
        val keys = listOf(
            ArasaacOptions(),
            ArasaacOptions(skin = "white"),
            ArasaacOptions(hair = "white"),
            ArasaacOptions(color = false),
            ArasaacOptions(skin = "white", color = false),
        ).map { it.cacheKey() }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `default options produce no query string`() {
        assertEquals("", ArasaacOptions().query())
    }

    @Test
    fun `query string includes a leading question mark and joins with ampersand`() {
        assertEquals("?skin=white", ArasaacOptions(skin = "white").query())
        assertEquals(
            "?skin=black&hair=gray&color=false",
            ArasaacOptions(skin = "black", hair = "gray", color = false).query(),
        )
    }

    @Test
    fun `color true is omitted from the query because it is the server default`() {
        assertFalse(ArasaacOptions(color = true).query().contains("color"))
    }

    @Test
    fun `declared skin tones and hair colors are usable and unique`() {
        assertEquals(
            ArasaacOptions.SKIN_TONES.size,
            ArasaacOptions.SKIN_TONES.toSet().size,
        )
        assertEquals(
            ArasaacOptions.HAIR_COLORS.size,
            ArasaacOptions.HAIR_COLORS.toSet().size,
        )
        ArasaacOptions.SKIN_TONES.forEach {
            assertEquals("?skin=$it", ArasaacOptions(skin = it).query())
        }
    }
}
