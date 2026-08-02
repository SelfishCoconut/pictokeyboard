package org.pictokeyboard.data.repo

import org.pictokeyboard.data.arasaac.ArasaacUrls
import org.pictokeyboard.data.db.BoardEntity
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.data.db.PictoEntity
import java.io.File

/**
 * The picto a caregiver chose to stand for something — a category, or a board —
 * before it is resolved to the (id, cached file) pair that thing actually stores.
 *
 * Both owners store the same two columns and both are drawn by the keyboard from
 * the same model, so they share one type and one picker rather than growing a
 * near-identical copy each (#54).
 *
 * A category is the primary navigation control on a keyboard whose user may not
 * read, and the board tab is the control above it, so [None] is a real, supported
 * state rather than an error — but it is a state the caregiver has to be able to
 * leave, which is what this type exists for. Categories created blank or from
 * usage start out with no picto, and so does every board.
 */
sealed interface IconChoice {

    /**
     * What to call this picto out loud: the ARASAAC keyword, or the label of the
     * picto that was promoted. Null when there is nothing to say — an imported
     * photo has no name, and a picto restored from the database has lost the one
     * it was picked under, since no column stores it.
     *
     * This exists for the caregiver who cannot see the preview. Without it every
     * picto announces identically and a mis-tap is indistinguishable from the
     * right choice, which makes the picker operable but not verifiable.
     */
    val label: String?

    /** No picto: the category or board is shown by its name alone, as today. */
    data object None : IconChoice {
        override val label: String? = null
    }

    /** An ARASAAC pictogram just picked by search; its image is not cached yet. */
    data class Arasaac(val id: Int, override val label: String? = null) : IconChoice

    /**
     * An image already in the local cache — a picto promoted to represent what
     * holds it, or a photo the caregiver imported. [arasaacId] is carried over
     * when the source was an ARASAAC picto, so the tile can still fall back to
     * the CDN if the cached file is ever lost.
     */
    data class Local(val path: String, val arasaacId: Int? = null, override val label: String? = null) : IconChoice
}

/**
 * The picker's starting point for this category.
 *
 * A category whose image was never cached — an interrupted first run, or an
 * install that has been offline since — comes back as [IconChoice.Arasaac]
 * rather than [IconChoice.Local], so that saving retries the download instead
 * of storing a path that was never written.
 */
fun CategoryEntity.currentIcon(): IconChoice = iconChoiceOf(iconImagePath, iconArasaacId)

/**
 * The picker's starting point for this board — the picto on its keyboard tab.
 *
 * Same rule as [CategoryEntity.currentIcon]: an id with no cached file means the
 * download still has to happen, so it must not come back as a path.
 */
fun BoardEntity.currentIcon(): IconChoice = iconChoiceOf(iconImagePath, iconArasaacId)

private fun iconChoiceOf(imagePath: String?, arasaacId: Int?): IconChoice = when {
    imagePath != null -> IconChoice.Local(imagePath, arasaacId)
    arasaacId != null -> IconChoice.Arasaac(arasaacId)
    else -> IconChoice.None
}

/**
 * Coil model for this choice: the cached file when there is one, the ARASAAC CDN
 * URL as a fallback, and null when there is no picto to draw. Matching what the
 * keyboard chip, the board tab and the category row already do keeps the editor's
 * preview honest about what the communicator will see.
 */
fun IconChoice.previewModel(): Any? = when (this) {
    IconChoice.None -> null
    // ArasaacUrls.image(id) defaults to FULL (500px) -- the same URL the removed
    // ImageCache.arasaacImageUrl built, so the rendered picto is unchanged.
    is IconChoice.Arasaac -> ArasaacUrls.image(id)
    is IconChoice.Local -> File(path)
}

/**
 * This picto as a choice of icon, or null if it has no image to offer — a row
 * that is still waiting on its first download and has never been online.
 *
 * Promoting a symbol that is already there is the fastest path to a picture, and
 * usually the right one: the picture for *Food* is nearly always already inside
 * *Food*, and the picture for a *Doctor* board is somewhere on it.
 */
fun PictoEntity.asIconChoice(): IconChoice? {
    // Same name the row announces, so picking one does not rename it mid-gesture.
    val name = label.ifBlank { spokenText }
    return when {
        imagePath != null -> IconChoice.Local(imagePath, arasaacId, name)
        arasaacId != null -> IconChoice.Arasaac(arasaacId, name)
        else -> null
    }
}
