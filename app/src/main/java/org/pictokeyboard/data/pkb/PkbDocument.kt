package org.pictokeyboard.data.pkb

import com.squareup.moshi.JsonClass

/**
 * Everything the device holds about boards, as it travels inside a `.pkb`.
 *
 * **Nothing here has an id.** Categories are nested inside their board and
 * pictos inside their category, so there are no foreign keys to carry and
 * nothing to collide with on the way in. That is what makes the additive import
 * of §6.3 structurally true rather than a rule somebody has to remember:
 * importing a file twice cannot overwrite the first import, because the second
 * one has no way of naming it.
 *
 * Photographs and recordings travel as [Sha256] digests of their bytes; the
 * bytes themselves are separate entries in the archive. This is the one thing
 * publishing to the catalogue must never do, and the one thing an export must
 * always do — see §4.2 for why that asymmetry is the point.
 */
@JsonClass(generateAdapter = true)
data class PkbDocument(
    val boards: List<PkbBoard> = emptyList(),
    /**
     * Voice settings, which describe the person rather than the situation and
     * should follow them to a new device.
     */
    val settings: PkbSettings? = null,
)

@JsonClass(generateAdapter = true)
data class PkbBoard(
    val name: String,
    val colorArgb: Int,
    val position: Int,
    val language: String,
    val iconArasaacId: Int? = null,
    /** Digest of the board's own picto, when it is a photograph. */
    val iconMedia: String? = null,
    val tags: String = "",
    val showInKeyboard: Boolean = true,
    val columns: Int = 4,
    val rows: Int = 4,
    val showLabels: Boolean = true,
    val borderStyle: String = "solid",
    val borderWidthDp: Int = 3,
    val source: String? = null,
    val sourceVersion: String? = null,
    val author: String? = null,
    val licence: String? = null,
    val categories: List<PkbCategory> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class PkbCategory(
    val name: String,
    val colorArgb: Int,
    val position: Int,
    val builtin: Boolean = false,
    val iconArasaacId: Int? = null,
    val iconMedia: String? = null,
    val borderStyle: String = "solid",
    val borderWidthDp: Int = 3,
    val pictos: List<PkbPicto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class PkbPicto(
    val label: String,
    val spokenText: String,
    val language: String,
    val position: Int,
    val arasaacId: Int? = null,
    /** Digest of this picto's photograph or drawing, when it has one. */
    val media: String? = null,
    val colorArgbOverride: Int? = null,
)

/**
 * The voice settings that follow a caregiver to a new device.
 *
 * **The PIN is not here, and neither is `hasPin`.** The hash and its salt are a
 * credential and do not belong in a file that gets emailed around; and carrying
 * `hasPin` without them would be worse than useless, because the new device
 * would believe a PIN was set and refuse to let anybody past a lock whose
 * answer it does not hold. Usage statistics are absent for the same reason they
 * never reached the server: they are a tap-by-tap record of a disabled person's
 * speech.
 */
@JsonClass(generateAdapter = true)
data class PkbSettings(
    val defaultLanguage: String = "es",
    val addSpaceAfter: Boolean = true,
    val speakOnTap: Boolean = true,
    val ttsRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val blindMode: Boolean = false,
)

/**
 * The header, read before anything else is trusted.
 *
 * [formatVersion] is checked first so that a file written by a newer app fails
 * with a message saying so, rather than importing the half of itself this
 * version happens to understand.
 */
@JsonClass(generateAdapter = true)
data class PkbManifest(
    val formatVersion: Int,
    val appVersion: String,
    val exportedAt: String,
    val boardCount: Int,
    val categoryCount: Int,
    val pictoCount: Int,
    val mediaCount: Int,
)
