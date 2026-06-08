package org.pictokeyboard.data.backup

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Serializable board snapshot. Stable UUIDs are preserved so this same shape
 * can later be exchanged with the psychologist web backend for sync.
 */
@JsonClass(generateAdapter = true)
data class BackupDto(
    @Json(name = "version") val version: Int = 1,
    @Json(name = "language") val language: String = "es",
    @Json(name = "categories") val categories: List<BackupCategory> = emptyList(),
    @Json(name = "pictos") val pictos: List<BackupPicto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class BackupCategory(
    val id: String,
    val name: String,
    val colorArgb: Int,
    val position: Int,
    val builtin: Boolean,
    val iconArasaacId: Int? = null,
    val borderStyle: String = "solid",
    val borderWidthDp: Int = 3,
)

@JsonClass(generateAdapter = true)
data class BackupPicto(
    val id: String,
    val categoryId: String,
    val label: String,
    val spokenText: String,
    val language: String,
    val arasaacId: Int? = null,
    val position: Int,
    val colorArgbOverride: Int? = null,
)
