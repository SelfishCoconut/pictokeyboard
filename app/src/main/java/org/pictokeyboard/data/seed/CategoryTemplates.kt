package org.pictokeyboard.data.seed

/**
 * A pre-built category the admin can instantiate when creating a new category,
 * then tweak (rename, recolour, add/remove/reorder pictos) before it becomes a
 * normal custom category. Makes building a board far faster than adding every
 * picto by hand. The same ARASAAC image id works for any language; only the
 * displayed/spoken text is localised.
 */
data class CategoryTemplate(
    val id: String,
    val es: String,
    val en: String,
    val color: Long,
    val iconArasaacId: Int,
    val pictos: List<TemplatePicto>,
) {
    fun name(language: String): String = if (language == "en") en else es
}

/** One picto inside a [CategoryTemplate]: ARASAAC id + localised words. */
data class TemplatePicto(val arasaacId: Int, val es: String, val en: String)

/** The catalogue of category templates offered in the "New category" flow. */
object CategoryTemplates {

    val all: List<CategoryTemplate> = listOf(
        CategoryTemplate(
            id = "tpl-drinks",
            es = "Bebidas",
            en = "Drinks",
            color = 0xFF00BCD4,
            iconArasaacId = 32464,
            pictos = listOf(
                TemplatePicto(32464, "agua", "water"),
                TemplatePicto(2445, "leche", "milk"),
                TemplatePicto(11461, "zumo", "juice"),
                TemplatePicto(24479, "café", "coffee"),
                TemplatePicto(6625, "té", "tea"),
                TemplatePicto(4732, "refresco", "soda"),
                TemplatePicto(8503, "batido", "milkshake"),
            ),
        ),
        CategoryTemplate(
            id = "tpl-animals",
            es = "Animales",
            en = "Animals",
            color = 0xFF795548,
            iconArasaacId = 7202,
            pictos = listOf(
                TemplatePicto(7202, "perro", "dog"),
                TemplatePicto(7114, "gato", "cat"),
                TemplatePicto(2490, "pájaro", "bird"),
                TemplatePicto(2519, "pez", "fish"),
                TemplatePicto(2294, "caballo", "horse"),
                TemplatePicto(2609, "vaca", "cow"),
                TemplatePicto(2351, "conejo", "rabbit"),
                TemplatePicto(25187, "león", "lion"),
            ),
        ),
        CategoryTemplate(
            id = "tpl-clothes",
            es = "Ropa",
            en = "Clothing",
            color = 0xFFE91E63,
            iconArasaacId = 2309,
            pictos = listOf(
                TemplatePicto(2309, "camiseta", "shirt"),
                TemplatePicto(2565, "pantalón", "trousers"),
                TemplatePicto(2775, "zapatos", "shoes"),
                TemplatePicto(8122, "abrigo", "coat"),
                TemplatePicto(2298, "calcetines", "socks"),
                TemplatePicto(39395, "gorro", "hat"),
                TemplatePicto(2613, "vestido", "dress"),
            ),
        ),
        CategoryTemplate(
            id = "tpl-body",
            es = "Cuerpo",
            en = "Body",
            color = 0xFFFF5722,
            iconArasaacId = 2673,
            pictos = listOf(
                TemplatePicto(2673, "cabeza", "head"),
                TemplatePicto(2928, "mano", "hand"),
                TemplatePicto(25327, "pie", "foot"),
                TemplatePicto(6573, "ojo", "eye"),
                TemplatePicto(2663, "boca", "mouth"),
                TemplatePicto(2871, "oreja", "ear"),
                TemplatePicto(2887, "nariz", "nose"),
                TemplatePicto(2669, "brazo", "arm"),
            ),
        ),
        CategoryTemplate(
            id = "tpl-colors",
            es = "Colores",
            en = "Colours",
            color = 0xFF3F51B5,
            iconArasaacId = 2808,
            pictos = listOf(
                TemplatePicto(2808, "rojo", "red"),
                TemplatePicto(4869, "azul", "blue"),
                TemplatePicto(4887, "verde", "green"),
                TemplatePicto(2648, "amarillo", "yellow"),
                TemplatePicto(2886, "negro", "black"),
                TemplatePicto(8043, "blanco", "white"),
                TemplatePicto(2888, "naranja", "orange"),
            ),
        ),
    )
}
