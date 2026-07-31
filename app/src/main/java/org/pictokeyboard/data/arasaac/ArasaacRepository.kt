package org.pictokeyboard.data.arasaac

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Thin wrapper around the ARASAAC search API returning UI-ready results. */
class ArasaacRepository(private val api: ArasaacApi) {

    suspend fun search(query: String, language: String): Result<List<ArasaacResult>> =
        withContext(Dispatchers.IO) {
            runCatching {
                api.search(language, query.trim()).map { dto ->
                    ArasaacResult(
                        id = dto.id,
                        keyword = dto.keywords.firstOrNull()?.keyword?.takeIf { it.isNotBlank() }
                            ?: query.trim(),
                        imageUrl = ArasaacUrls.image(dto.id),
                    )
                }
            }
        }
}
