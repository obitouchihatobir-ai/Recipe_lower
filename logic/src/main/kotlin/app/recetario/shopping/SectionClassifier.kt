package app.recetario.shopping

import app.recetario.core.text.normalize

/**
 * Diccionario básico de palabras clave (en singular y sin tildes). Se miran primero las entradas de varias palabras contenidas en el
 * nombre (la más larga primero), después la primera palabra y, como último recurso, el resto de palabras; si nada coincide, OTHER.
 */
object SectionClassifier {
    private val words: Map<String, ShopSection> = buildMap {
        fun add(section: ShopSection, list: String) = list.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            .forEach { put(it, section) }
        add(
            ShopSection.FRUITS_VEG,
            "calabaza, calabacin, cebolla, cebolleta, ajo, tomate, patata, papa, zanahoria, lechuga, espinaca, " +
                "pimiento, berenjena, pepino, apio, puerro, champinon, seta, brocoli, coliflor, col, repollo, " +
                "limon, lima, naranja, mandarina, manzana, pera, platano, banana, fresa, frambuesa, arandano, uva, " +
                "melon, sandia, melocoton, aguacate, mango, pina, kiwi, cereza, ciruela, higo, granada, perejil, " +
                "cilantro, albahaca, menta, hierbabuena, romero, tomillo, eneldo, jengibre, boniato, remolacha, " +
                "rabano, judia, guisante, alcachofa, esparrago, acelga, endibia, rucula, canonigo, maiz, nabo, " +
                "chalota, fruta, verdura, hortaliza, ensalada",
        )
        add(
            ShopSection.DAIRY,
            "leche, nata, crema, queso, yogur, yogurt, mantequilla, margarina, huevo, requeson, mozzarella, " +
                "parmesano, ricotta, mascarpone, kefir, cuajada, flan, natilla, burrata, feta",
        )
        add(
            ShopSection.MEAT_FISH,
            "pollo, pechuga, muslo, pavo, ternera, cerdo, cordero, conejo, carne, filete, solomillo, chuleta, " +
                "costilla, jamon, bacon, panceta, chorizo, salchicha, lomo, hamburguesa, albondiga, pescado, " +
                "salmon, merluza, bacalao, atun, lubina, dorada, sardina, anchoa, gamba, langostino, calamar, " +
                "pulpo, mejillon, almeja, marisco, sepia, rape, trucha, tocino, morcilla, butifarra, mortadela",
        )
        add(
            ShopSection.PANTRY,
            "arroz, pasta, espagueti, macarron, fideo, harina, azucar, sal, pimienta, aceite, vinagre, caldo, " +
                "pan, lenteja, garbanzo, alubia, frijol, avena, cereal, galleta, miel, mermelada, chocolate, cacao, " +
                "levadura, bicarbonato, canela, comino, pimenton, oregano, curry, laurel, nuez, nueces, almendra, " +
                "avellana, pistacho, cacahuete, pasa, salsa, mostaza, mayonesa, ketchup, conserva, lata, tomate frito, " +
                "especia, vainilla, gelatina, maicena, tapioca, cuscus, quinoa, tortilla, harina de trigo, sirope, " +
                "soja, tahini, coco, aceituna, pepinillo, alcaparra, fecula, panko, picatoste",
        )
        add(
            ShopSection.DRINKS,
            "agua, vino, cerveza, zumo, refresco, cola, gaseosa, sidra, cava, champan, ron, ginebra, vodka, " +
                "whisky, tequila, licor, brandy, conac, cafe, te, infusion, tonica, sangria, vermut, batido",
        )
    }

    /** Entradas de varias palabras, las más largas primero. */
    private val phrases: List<Pair<String, ShopSection>> =
        words.entries.filter { ' ' in it.key }.sortedByDescending { it.key.length }.map { it.key to it.value }

    fun classify(name: String): ShopSection {
        val tokens = normalize(name).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return ShopSection.OTHER
        val joined = " " + tokens.joinToString(" ") + " "
        for ((phrase, section) in phrases) if (" $phrase " in joined) return section
        lookup(tokens.first())?.let { return it }
        for (token in tokens.drop(1)) lookup(token)?.let { return it }
        return ShopSection.OTHER
    }

    /** Busca la palabra tal cual y sin la terminación de plural («es», «s»). */
    private fun lookup(word: String): ShopSection? {
        words[word]?.let { return it }
        if (word.length > 4 && word.endsWith("es")) words[word.dropLast(2)]?.let { return it }
        if (word.length > 3 && word.endsWith("s")) words[word.dropLast(1)]?.let { return it }
        return null
    }
}
