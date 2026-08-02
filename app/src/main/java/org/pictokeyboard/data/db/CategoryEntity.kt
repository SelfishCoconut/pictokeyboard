package org.pictokeyboard.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A category groups pictograms and carries the frame [colorArgb] that is drawn
 * around every pictogram belonging to it, so the end user can associate pictos
 * with their category by colour.
 *
 * Every category belongs to exactly one [BoardEntity]. Deleting a board takes
 * its categories with it, and their pictos in turn.
 */
@Entity(
    tableName = "categories",
    indices = [Index("position"), Index("boardId")],
    foreignKeys = [
        ForeignKey(
            entity = BoardEntity::class,
            parentColumns = ["id"],
            childColumns = ["boardId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** The board this category belongs to. */
    @ColumnInfo(defaultValue = BoardEntity.DEFAULT_ID)
    val boardId: String = BoardEntity.DEFAULT_ID,
    /** ARGB frame colour for this category and its pictos. */
    val colorArgb: Int,
    /** Sort order in the left category strip. */
    val position: Int,
    /** True for categories seeded by the app; custom ones are false. */
    val builtin: Boolean = false,
    /** Optional ARASAAC id of the icon shown for the category itself. */
    val iconArasaacId: Int? = null,
    /** Local cached file path of the category icon, if any. */
    val iconImagePath: String? = null,
    /** Frame line style: one of [BorderStyles]. */
    @ColumnInfo(defaultValue = "solid")
    val borderStyle: String = BorderStyles.SOLID,
    /** Frame stroke thickness in dp. */
    @ColumnInfo(defaultValue = "3")
    val borderWidthDp: Int = BorderStyles.DEFAULT_WIDTH_DP,
)

/** Frame line styles a category can use, shared by the keyboard and config UI. */
object BorderStyles {
    const val SOLID = "solid"
    const val DASHED = "dashed"
    const val DOTTED = "dotted"

    const val DEFAULT_WIDTH_DP = 3

    /** Thickness presets offered when editing a category. */
    val WIDTHS_DP = listOf(2, 3, 5, 8)

    val ALL = listOf(SOLID, DASHED, DOTTED)
}
