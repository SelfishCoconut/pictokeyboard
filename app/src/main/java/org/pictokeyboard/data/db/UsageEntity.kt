package org.pictokeyboard.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * How often a given word/picto has been used (typed/spoken), across all
 * categories and both keyboards. Keyed by a stable identity so the count
 * survives a picto being edited, moved or deleted: the ARASAAC id when there is
 * one, otherwise the spoken text + language. Drives the usage-ordered
 * "Suggested" category offered when creating a new category.
 */
@Entity(tableName = "usage")
data class UsageEntity(
    @PrimaryKey val id: String,
    val arasaacId: Int? = null,
    val label: String,
    val spokenText: String,
    val language: String,
    val count: Int = 0,
    val lastUsedAt: Long = 0L,
) {
    companion object {
        /** Stable identity used as the primary key. */
        fun keyFor(arasaacId: Int?, spokenText: String, language: String): String =
            arasaacId?.let { "arasaac:$it" } ?: "text:$language:${spokenText.lowercase()}"
    }
}
