package org.pictokeyboard.data.seed

import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.data.db.PictoEntity

/**
 * ARASAAC-style core categories with the conventional AAC frame colours, a
 * representative icon per category, and a starter set of pictograms per
 * category. Ids are stable so seeding is deterministic and sync-friendly. The
 * same ARASAAC image id works for any language; only the text is localised.
 */
object DefaultData {

    private data class CatSeed(
        val id: String,
        val es: String,
        val en: String,
        val color: Long,
        val iconArasaacId: Int,
    )

    /** A starter pictogram: ARASAAC id + localised words. */
    private data class P(
        val categoryId: String,
        val arasaacId: Int,
        val es: String,
        val en: String,
    )

    private val catSeeds = listOf(
        CatSeed("cat-people", "Personas", "People", 0xFFFFC107, 34560),
        CatSeed("cat-actions", "Acciones", "Actions", 0xFF4CAF50, 6465),
        CatSeed("cat-food", "Comida", "Food", 0xFFFF9800, 4610),
        CatSeed("cat-feelings", "Sentimientos", "Feelings", 0xFFF44336, 39091),
        CatSeed("cat-places", "Lugares", "Places", 0xFF2196F3, 5505),
        CatSeed("cat-objects", "Objetos", "Objects", 0xFF9C27B0, 9813),
        CatSeed("cat-time", "Tiempo", "Time", 0xFF9E9E9E, 2549),
    )

    private val pictoSeeds = listOf(
        P("cat-people", 6632, "yo", "I"),
        P("cat-people", 2458, "mamá", "mum"),
        P("cat-people", 31146, "papá", "dad"),
        P("cat-people", 7176, "niño", "boy"),
        P("cat-people", 27509, "niña", "girl"),
        P("cat-people", 25790, "amigo", "friend"),
        P("cat-actions", 6456, "comer", "eat"),
        P("cat-actions", 6061, "beber", "drink"),
        P("cat-actions", 23392, "jugar", "play"),
        P("cat-actions", 6479, "dormir", "sleep"),
        P("cat-actions", 11538, "querer", "want"),
        P("cat-actions", 8142, "ir", "go"),
        P("cat-food", 32464, "agua", "water"),
        P("cat-food", 2494, "pan", "bread"),
        P("cat-food", 2445, "leche", "milk"),
        P("cat-food", 2462, "manzana", "apple"),
        P("cat-food", 2530, "plátano", "banana"),
        P("cat-food", 8312, "galleta", "biscuit"),
        P("cat-feelings", 9907, "feliz", "happy"),
        P("cat-feelings", 35545, "triste", "sad"),
        P("cat-feelings", 35539, "enfadado", "angry"),
        P("cat-feelings", 35537, "cansado", "tired"),
        P("cat-feelings", 35535, "asustado", "scared"),
        P("cat-places", 6964, "casa", "home"),
        P("cat-places", 32446, "colegio", "school"),
        P("cat-places", 2859, "parque", "park"),
        P("cat-places", 6929, "baño", "toilet"),
        P("cat-places", 2299, "calle", "street"),
        P("cat-objects", 3241, "pelota", "ball"),
        P("cat-objects", 25191, "libro", "book"),
        P("cat-objects", 2339, "coche", "car"),
        P("cat-objects", 26479, "teléfono", "phone"),
        P("cat-objects", 3155, "silla", "chair"),
        P("cat-time", 7131, "hoy", "today"),
        P("cat-time", 38278, "mañana", "tomorrow"),
        P("cat-time", 32747, "ahora", "now"),
        P("cat-time", 37731, "día", "day"),
        P("cat-time", 26997, "noche", "night"),
        // --- Expanded starter set --------------------------------------------
        P("cat-people", 2423, "hermano", "brother"),
        P("cat-people", 2422, "hermana", "sister"),
        P("cat-people", 23718, "abuelo", "grandfather"),
        P("cat-people", 23710, "abuela", "grandmother"),
        P("cat-people", 6060, "bebé", "baby"),
        P("cat-people", 38351, "familia", "family"),
        P("cat-people", 6556, "profesor", "teacher"),
        P("cat-people", 6561, "médico", "doctor"),
        P("cat-actions", 5584, "sí", "yes"),
        P("cat-actions", 5526, "no", "no"),
        P("cat-actions", 32648, "ayudar", "help"),
        P("cat-actions", 7196, "parar", "stop"),
        P("cat-actions", 24825, "abrir", "open"),
        P("cat-actions", 24976, "cerrar", "close"),
        P("cat-actions", 6564, "mirar", "look"),
        P("cat-actions", 6572, "escuchar", "listen"),
        P("cat-actions", 34826, "lavar", "wash"),
        P("cat-actions", 6465, "correr", "run"),
        P("cat-actions", 39052, "saltar", "jump"),
        P("cat-actions", 28431, "dar", "give"),
        P("cat-actions", 32669, "venir", "come"),
        P("cat-actions", 6517, "hablar", "talk"),
        P("cat-food", 28339, "fruta", "fruit"),
        P("cat-food", 2888, "naranja", "orange"),
        P("cat-food", 11461, "zumo", "juice"),
        P("cat-food", 2427, "huevo", "egg"),
        P("cat-food", 2541, "queso", "cheese"),
        P("cat-food", 6911, "arroz", "rice"),
        P("cat-food", 4952, "pollo", "chicken"),
        P("cat-food", 2519, "pescado", "fish"),
        P("cat-food", 2618, "yogur", "yogurt"),
        P("cat-food", 25940, "chocolate", "chocolate"),
        P("cat-food", 2573, "sopa", "soup"),
        P("cat-food", 8652, "pasta", "pasta"),
        P("cat-feelings", 5397, "bien", "good"),
        P("cat-feelings", 5504, "mal", "bad"),
        P("cat-feelings", 7040, "enfermo", "sick"),
        P("cat-feelings", 2367, "dolor", "pain"),
        P("cat-feelings", 10261, "miedo", "afraid"),
        P("cat-feelings", 35531, "aburrido", "bored"),
        P("cat-feelings", 30391, "nervioso", "nervous"),
        P("cat-feelings", 31310, "tranquilo", "calm"),
        P("cat-places", 10752, "cocina", "kitchen"),
        P("cat-places", 5988, "dormitorio", "bedroom"),
        P("cat-places", 35695, "tienda", "shop"),
        P("cat-places", 6523, "hospital", "hospital"),
        P("cat-places", 2434, "jardín", "garden"),
        P("cat-places", 30518, "playa", "beach"),
        P("cat-objects", 3129, "mesa", "table"),
        P("cat-objects", 25900, "cama", "bed"),
        P("cat-objects", 7233, "ropa", "clothes"),
        P("cat-objects", 2775, "zapatos", "shoes"),
        P("cat-objects", 25498, "televisión", "television"),
        P("cat-objects", 7190, "ordenador", "computer"),
        P("cat-objects", 3244, "puerta", "door"),
        P("cat-objects", 8153, "llave", "key"),
        P("cat-objects", 4630, "dinero", "money"),
        P("cat-time", 38279, "ayer", "yesterday"),
        P("cat-time", 7268, "tarde", "afternoon"),
        P("cat-time", 37732, "semana", "week"),
        P("cat-time", 37724, "mes", "month"),
        P("cat-time", 6903, "año", "year"),
        P("cat-time", 7129, "hora", "hour"),
    )

    /** Default categories (image paths are filled in later by the repository). */
    fun categories(language: String): List<CategoryEntity> =
        catSeeds.mapIndexed { index, s ->
            CategoryEntity(
                id = s.id,
                name = if (language == "en") s.en else s.es,
                colorArgb = s.color.toInt(),
                position = index,
                builtin = true,
                iconArasaacId = s.iconArasaacId,
            )
        }

    /** Starter pictos with stable ids (image paths filled in by the repository). */
    fun pictos(language: String): List<PictoEntity> {
        val positions = mutableMapOf<String, Int>()
        return pictoSeeds.map { p ->
            val pos = positions.getOrDefault(p.categoryId, 0)
            positions[p.categoryId] = pos + 1
            val word = if (language == "en") p.en else p.es
            PictoEntity(
                id = "pic-seed-${p.arasaacId}",
                categoryId = p.categoryId,
                label = word,
                spokenText = word,
                language = language,
                arasaacId = p.arasaacId,
                imagePath = null,
                position = pos,
            )
        }
    }
}
