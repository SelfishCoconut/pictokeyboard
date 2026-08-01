package org.pictokeyboard.data.repo

import org.pictokeyboard.data.arasaac.ImageCache
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.data.db.PictoEntity
import java.io.File

/**
 * The picto a caregiver chose for a category, before it is resolved to the
 * (id, cached file) pair the category actually stores.
 *
 * A category is the primary navigation control on a keyboard whose user may not
 * read, so [None] is a real, supported state rather than an error — but it is a
 * state the caregiver has to be able to leave, which is what this type exists
 * for. Categories created blank or from usage start out with no picto.
 */
sealed interface CategoryIcon {

    /** No picto: the category is shown by its name alone, as today. */
    data object None : CategoryIcon

    /** An ARASAAC pictogram just picked by search; its image is not cached yet. */
    data class Arasaac(val id: Int) : CategoryIcon

    /**
     * An image already in the local cache — one of the category's own pictos
     * promoted to represent it, or a photo the caregiver imported. [arasaacId] is
     * carried over when the source was an ARASAAC picto, so the chip can still
     * fall back to the CDN if the cached file is ever lost.
     */
    data class Local(val path: String, val arasaacId: Int? = null) : CategoryIcon
}

/**
 * The picker's starting point for this category.
 *
 * A category whose image was never cached — an interrupted first run, or a
 * install that has been offline since — comes back as [CategoryIcon.Arasaac]
 * rather than [CategoryIcon.Local], so that saving retries the download instead
 * of storing a path that was never written.
 */
fun CategoryEntity.currentIcon(): CategoryIcon = when {
    iconImagePath != null -> CategoryIcon.Local(iconImagePath, iconArasaacId)
    iconArasaacId != null -> CategoryIcon.Arasaac(iconArasaacId)
    else -> CategoryIcon.None
}

/**
 * Coil model for this choice: the cached file when there is one, the ARASAAC CDN
 * URL as a fallback, and null when there is no picto to draw. Matching what the
 * keyboard chip and the category row already do keeps the editor's preview
 * honest about what the communicator will see.
 */
fun CategoryIcon.previewModel(): Any? = when (this) {
    CategoryIcon.None -> null
    is CategoryIcon.Arasaac -> ImageCache.arasaacImageUrl(id)
    is CategoryIcon.Local -> File(path)
}

/**
 * This picto as a choice for its own category's icon, or null if it has no image
 * to offer — a row that is still waiting on its first download and has never
 * been online. Promoting a symbol the category already contains is the fastest
 * path to a picture, and usually the right one: the picture for *Food* is nearly
 * always already inside *Food*.
 */
fun PictoEntity.asCategoryIcon(): CategoryIcon? = when {
    imagePath != null -> CategoryIcon.Local(imagePath, arasaacId)
    arasaacId != null -> CategoryIcon.Arasaac(arasaacId)
    else -> null
}
