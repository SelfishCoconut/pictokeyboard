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

    /** Static CDN URL for the plain pictogram [id] at [size] pixels. */
    fun image(id: Int, size: Int = FULL): String =
        "https://static.arasaac.org/pictograms/$id/${id}_$size.png"

    /** API URL rendering pictogram [id] with the skin/hair/colour in [options]. */
    fun customized(id: Int, options: ArasaacOptions): String =
        "https://api.arasaac.org/api/pictograms/$id${options.query()}"

    /**
     * The customized image when [options] asks for one, the plain CDN asset
     * otherwise. Only the API host renders customizations.
     */
    fun customizedOrPlain(id: Int, options: ArasaacOptions = ArasaacOptions()): String =
        if (options.isCustomized) customized(id, options) else image(id)
}
