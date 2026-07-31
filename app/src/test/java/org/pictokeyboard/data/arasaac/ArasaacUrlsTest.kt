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
}
