package org.pictokeyboard.data.arasaac

/**
 * ARASAAC pictogram customization, mirroring the options on the ARASAAC website.
 * An empty set of options means the plain pictogram. The customized image is
 * served by `api.arasaac.org` (the static CDN only serves the plain file).
 */
data class ArasaacOptions(
    /** white | black | assian | mulatto | aztec, or null for default. */
    val skin: String? = null,
    /** blonde | brown | darkBrown | gray | darkGray | red | black, or null. */
    val hair: String? = null,
    /** false renders the pictogram in black & white. */
    val color: Boolean = true,
) {
    val isCustomized: Boolean get() = skin != null || hair != null || !color

    /** Stable, filesystem-safe key for caching this exact customization. */
    fun cacheKey(): String =
        listOfNotNull(skin, hair?.let { "h$it" }, if (!color) "bw" else null)
            .joinToString("-")
            .ifEmpty { "plain" }

    /** Query string for the api.arasaac.org image endpoint (leading "?" included). */
    fun query(): String {
        val params = buildList {
            skin?.let { add("skin=$it") }
            hair?.let { add("hair=$it") }
            if (!color) add("color=false")
        }
        return if (params.isEmpty()) "" else "?" + params.joinToString("&")
    }

    companion object {
        val SKIN_TONES = listOf("white", "mulatto", "aztec", "black", "assian")
        val HAIR_COLORS = listOf("blonde", "brown", "darkBrown", "gray", "darkGray", "red", "black")
    }
}
