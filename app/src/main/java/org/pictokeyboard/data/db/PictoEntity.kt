package org.pictokeyboard.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single pictogram key. Tapping it on the keyboard inserts [spokenText] into
 * the focused text field and speaks it aloud through TTS in [language].
 */
@Entity(
    tableName = "pictos",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("categoryId"), Index("position")],
)
data class PictoEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    /** Caption shown under the picto (optional to display). */
    val label: String,
    /** Text inserted into the text field and spoken aloud. */
    val spokenText: String,
    /** BCP-47-ish language tag used for TTS: "es" or "en". */
    val language: String,
    /** Source ARASAAC pictogram id, when added from ARASAAC. */
    val arasaacId: Int? = null,
    /** Local cached image file path. Required for offline use. */
    val imagePath: String? = null,
    /** Sort order within the category grid. */
    val position: Int,
    /**
     * Optional ARGB frame colour for this picto, overriding its category's
     * colour. Set when the picto was borrowed from another category (keeps that
     * category's colour) or when an explicit colour was chosen for it.
     */
    val colorArgbOverride: Int? = null,
)
