package org.pictokeyboard.data.arasaac

import retrofit2.http.GET
import retrofit2.http.Path

interface ArasaacApi {
    /**
     * Searches ARASAAC pictograms by [text] in the given [language] (e.g. "es", "en").
     * https://api.arasaac.org/api/pictograms/{language}/search/{text}
     */
    @GET("api/pictograms/{language}/search/{text}")
    suspend fun search(
        @Path("language") language: String,
        @Path("text") text: String,
    ): List<ArasaacPictogramDto>
}
