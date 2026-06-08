package org.pictokeyboard.data.arasaac

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ArasaacPictogramDto(
    @Json(name = "_id") val id: Int,
    @Json(name = "keywords") val keywords: List<ArasaacKeywordDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class ArasaacKeywordDto(
    @Json(name = "keyword") val keyword: String? = null,
    @Json(name = "plural") val plural: String? = null,
)

/** Flattened search result used by the UI. */
data class ArasaacResult(
    val id: Int,
    val keyword: String,
    val imageUrl: String,
)
